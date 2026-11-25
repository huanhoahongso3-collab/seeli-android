package com.tunnaduong.seeli.core

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.tunnaduong.seeli.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiError(val code: String, val status: Int) : Exception(code) {
    /** User-facing text, from the same `error.*` keys as iOS. */
    val description: String
        get() {
            val key = "error.$code"
            val text = t(key)
            return if (text == key) t("error.generic") else text
        }

    companion object {
        val network get() = ApiError("network", 0)
    }
}

fun Throwable.userMessage(): String = (this as? ApiError)?.description ?: t("error.generic")

/** Talks to the SEELI backend (seeli-api). The token lives in encrypted preferences. */
object ApiClient {
    private const val OVERRIDE_KEY = "seeli.apiURL"
    private const val TOKEN_KEY = "seeli.token"

    val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Photos for one-time views: no cache, nothing written to disk. */
    val ephemeral: OkHttpClient = http.newBuilder().cache(null).build()

    private lateinit var prefs: SharedPreferences
    private lateinit var secure: SharedPreferences

    /** Called when the server says the token is no longer valid. */
    var onUnauthorized: (() -> Unit)? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences("seeli.prefs", Context.MODE_PRIVATE)
        secure = try {
            val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(
                context, "seeli.secure", key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (_: Exception) {
            context.getSharedPreferences("seeli.secure.fallback", Context.MODE_PRIVATE)
        }
    }

    // ---- Server URL ----

    val defaultBaseURL: String get() = BuildConfig.SEELI_API_URL

    /** Settings → Server can override the URL (handy for local testing). */
    var baseURLString: String
        get() = prefs.getString(OVERRIDE_KEY, null) ?: defaultBaseURL
        set(value) {
            val clean = value.trim().trim('/')
            if (clean.isEmpty() || clean == defaultBaseURL) prefs.edit().remove(OVERRIDE_KEY).apply()
            else prefs.edit().putString(OVERRIDE_KEY, clean).apply()
        }

    private val base get() = "$baseURLString/v1/"

    // ---- Token ----

    var token: String?
        get() = secure.getString(TOKEN_KEY, null)
        set(value) {
            if (value == null) secure.edit().remove(TOKEN_KEY).apply()
            else secure.edit().putString(TOKEN_KEY, value).apply()
        }

    // ---- Requests ----

    suspend inline fun <reified T> request(method: String, path: String, body: Map<String, Any?>? = null): T =
        requestWith(method, path, body, serializer<T>())

    suspend fun send(method: String, path: String, body: Map<String, Any?>? = null) {
        execute(method, path, body)
    }

    suspend fun <T> requestWith(method: String, path: String, body: Map<String, Any?>?, strategy: DeserializationStrategy<T>): T {
        val text = execute(method, path, body)
        return try {
            json.decodeFromString(strategy, text)
        } catch (_: Exception) {
            throw ApiError("generic", 200)
        }
    }

    private suspend fun execute(method: String, path: String, body: Map<String, Any?>?): String {
        val reqBody = body?.let { JSONObject(it.filterValues { v -> v != null }).toString().toRequestBody(JSON) }
            ?: if (method == "GET" || method == "DELETE") null else "".toRequestBody(null)
        val req = Request.Builder().url(base + path).method(method, reqBody).build()
        return call(req)
    }

    suspend fun uploadMoment(p: PendingUpload, jpeg: ByteArray): Moment {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("note", p.note)
            .apply { p.placeName?.let { addFormDataPart("placeName", it) } }
            .addFormDataPart("audience", p.audience.raw)
            .addFormDataPart("createdAt", InstantSerializer.run { p.createdAt.toString() })
            .addFormDataPart("demo", if (p.demo) "true" else "false")
            .addFormDataPart("photo", "photo.jpg", jpeg.toRequestBody("image/jpeg".toMediaType()))
            .build()
        val req = Request.Builder().url(base + "moments").post(body).build()
        val text = call(req, longTimeout = true)
        return try { json.decodeFromString(Moment.serializer(), text) } catch (_: Exception) { throw ApiError("generic", 201) }
    }

    private suspend fun call(request: Request, longTimeout: Boolean = false): String = withContext(Dispatchers.IO) {
        val req = request.newBuilder().header("Accept", "application/json").apply {
            token?.let { header("Authorization", "Bearer $it") }
        }.build()
        val client = if (longTimeout) http.newBuilder().readTimeout(120, TimeUnit.SECONDS).writeTimeout(120, TimeUnit.SECONDS).build() else http
        val response = try { client.newCall(req).execute() } catch (_: IOException) { throw ApiError.network }
        response.use { r ->
            val text = r.body?.string() ?: ""
            if (!r.isSuccessful) {
                val code = runCatching { JSONObject(text).optString("error").ifEmpty { null } }.getOrNull()
                if (r.code == 401) withContext(Dispatchers.Main) { onUnauthorized?.invoke() }
                throw ApiError(code ?: "generic", r.code)
            }
            text
        }
    }

    /** Downloads image bytes (signed photo URLs need no auth header). */
    suspend fun fetchBytes(url: String, client: OkHttpClient = http): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).header("Cache-Control", "no-store").build()).execute().use { r ->
                if (r.code == 200) r.body?.bytes() else null
            }
        }.getOrNull()
    }

    private val JSON = "application/json".toMediaType()
}

object PhotoPrep {
    /** Resize to max 1600px, respect EXIF rotation, compress — keeps uploads small for the Pi. */
    fun jpeg(data: ByteArray, maxSide: Int = 1600): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0) return data
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return data
        val scale = minOf(1f, maxSide.toFloat() / maxOf(decoded.width, decoded.height))
        val m = Matrix().apply {
            postScale(scale, scale)
            postRotate(exifRotation(data).toFloat())
        }
        val out = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
        return ByteArrayOutputStream().use { s -> out.compress(Bitmap.CompressFormat.JPEG, 80, s); s.toByteArray() }
    }

    private fun exifRotation(data: ByteArray): Int = runCatching {
        when (ExifInterface(data.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }.getOrDefault(0)
}
