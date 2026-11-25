package com.tunnaduong.seeli.core

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.tunnaduong.seeli.BuildConfig
import java.security.SecureRandom
import android.util.Base64

/** The identity token the server verifies, plus what only the device knows. */
data class SocialCredential(
    val provider: String, // "google" (Sign in with Apple needs a web Services ID on Android)
    val idToken: String,
    val nonce: String,
    val name: String? = null,
)

class SocialAuthException(val cancelled: Boolean, val notConfigured: Boolean = false) : Exception() {
    val description: String? get() = when {
        cancelled -> null
        notConfigured -> t("error.provider_disabled")
        else -> t("error.generic")
    }
}

/** Google sign-in through Android's Credential Manager (system account picker, no browser). */
object GoogleSignIn {
    val clientID: String get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
    val isConfigured: Boolean get() = clientID.endsWith(".apps.googleusercontent.com")

    suspend fun start(context: Context): SocialCredential {
        if (!isConfigured) throw SocialAuthException(cancelled = false, notConfigured = true)
        val nonce = ByteArray(32).also { SecureRandom().nextBytes(it) }
            .let { Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING) }
        val option = GetSignInWithGoogleOption.Builder(clientID).setNonce(nonce).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = try {
            CredentialManager.create(context).getCredential(context, request)
        } catch (_: GetCredentialCancellationException) {
            throw SocialAuthException(cancelled = true)
        } catch (_: NoCredentialException) {
            throw SocialAuthException(cancelled = true)
        } catch (_: Exception) {
            throw SocialAuthException(cancelled = false)
        }
        val cred = result.credential
        if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val google = GoogleIdTokenCredential.createFrom(cred.data)
            return SocialCredential("google", google.idToken, nonce, google.displayName)
        }
        throw SocialAuthException(cancelled = false)
    }
}
