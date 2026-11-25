package com.tunnaduong.seeli.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.tunnaduong.seeli.core.AppStore
import com.tunnaduong.seeli.core.PlaceholderPhoto
import com.tunnaduong.seeli.core.Route
import com.tunnaduong.seeli.core.t
import com.tunnaduong.seeli.ui.EyeLogo
import com.tunnaduong.seeli.ui.Seeli
import com.tunnaduong.seeli.ui.tap
import com.tunnaduong.seeli.ui.ts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

private enum class CamState { LOADING, READY, DENIED, UNAVAILABLE }

/** Camera: take the photo, then let it go. No preview of the result is ever shown. */
@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(CamState.LOADING) }
    var flash by remember { mutableStateOf(false) }
    var released by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        state = if (ok) CamState.LOADING else CamState.DENIED
        if (ok) bindCamera(context, lifecycle, previewView, capture, { provider = it }) { state = it }
    }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            bindCamera(context, lifecycle, previewView, capture, { provider = it }) { state = it }
        } else permission.launch(Manifest.permission.CAMERA)
    }
    DisposableEffect(Unit) { onDispose { provider?.unbindAll() } }

    val flashAlpha by animateFloatAsState(if (flash) 1f else 0f, tween(if (flash) 80 else 350), label = "flash")

    fun finish(data: ByteArray?) {
        AppStore.startDraft(data ?: PlaceholderPhoto.make())
        released = true
        scope.launch {
            delay(1200)
            provider?.unbindAll()
            if (AppStore.path.lastOrNull() == Route.Camera) AppStore.pop()
            AppStore.push(Route.Post)
        }
    }

    fun shoot() {
        busy = true
        flash = true
        scope.launch { delay(120); flash = false }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        if (state == CamState.READY) {
            capture.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) { finish(image.toJpeg()); image.close() }
                override fun onError(exception: ImageCaptureException) = finish(null)
            })
        } else finish(null)
    }

    Box(Modifier.fillMaxSize().background(Seeli.Night)) {
        when (state) {
            CamState.READY, CamState.LOADING -> {
                AndroidView({ previewView }, Modifier.fillMaxSize().statusBarsPadding().padding(start = 8.dp, end = 8.dp, top = 70.dp, bottom = 170.dp)
                    .clip(RoundedCornerShape(28.dp)))
                if (state == CamState.LOADING) CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
            }
            CamState.DENIED -> Column(Modifier.align(Alignment.Center).padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(t("camera.denied"), style = ts(14, color = Color.White.copy(alpha = 0.8f)), textAlign = TextAlign.Center)
                Text(t("camera.openSettings"), style = ts(14, color = Seeli.Sun), modifier = Modifier.tap {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                })
            }
            CamState.UNAVAILABLE -> Column(Modifier.align(Alignment.Center).padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.NoPhotography, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(34.dp))
                Text(t("camera.unavailable"), style = ts(14, color = Color.White.copy(alpha = 0.7f)), textAlign = TextAlign.Center)
            }
        }

        if (state == CamState.READY) {
            Canvas(Modifier.fillMaxSize().statusBarsPadding().padding(start = 40.dp, end = 40.dp, top = 110.dp, bottom = 210.dp)) {
                val l = 22.dp.toPx()
                val p = Path()
                listOf(Triple(Offset(0f, 0f), 1f, 1f), Triple(Offset(size.width, 0f), -1f, 1f),
                    Triple(Offset(0f, size.height), 1f, -1f), Triple(Offset(size.width, size.height), -1f, -1f)).forEach { (c, dx, dy) ->
                    p.moveTo(c.x, c.y + l * dy); p.lineTo(c.x, c.y); p.lineTo(c.x + l * dx, c.y)
                }
                drawPath(p, Color.White.copy(alpha = 0.7f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
        }

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).tap { AppStore.pop() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Close, t("common.close"), tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.weight(1f))
                Text("SEELI", style = ts(13, FontWeight.SemiBold, Color.White.copy(alpha = 0.8f), 2.sp))
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(44.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(t("camera.hint"), style = ts(12, color = Color.White.copy(alpha = 0.6f)), textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp))
            Box(
                Modifier.align(Alignment.CenterHorizontally).padding(bottom = 40.dp).size(78.dp).border(3.dp, Color.White, CircleShape)
                    .tap(enabled = !busy && state != CamState.LOADING && state != CamState.DENIED) { shoot() },
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(64.dp).clip(CircleShape).background(Color.White)) }
        }

        Box(Modifier.fillMaxSize().alpha(flashAlpha).background(Color.White))

        AnimatedVisibility(released, enter = fadeIn(tween(300))) {
            Column(Modifier.fillMaxSize().background(Seeli.Night), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
                EyeLogo(40.dp, closed = true, color = Color.White)
                Text(t("camera.released"), style = ts(20, FontWeight.Medium, Color.White))
            }
        }
    }
}

private fun bindCamera(
    context: Context,
    lifecycle: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    capture: ImageCapture,
    onProvider: (ProcessCameraProvider) -> Unit,
    onState: (CamState) -> Unit,
) {
    val future = ProcessCameraProvider.getInstance(context)
    future.addListener({
        try {
            val provider = future.get()
            onProvider(provider)
            val selector = when {
                provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                else -> { onState(CamState.UNAVAILABLE); return@addListener }
            }
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            provider.unbindAll()
            provider.bindToLifecycle(lifecycle, selector, preview, capture)
            onState(CamState.READY)
        } catch (_: Exception) {
            onState(CamState.UNAVAILABLE)
        }
    }, ContextCompat.getMainExecutor(context))
}

/** JPEG bytes from a captured frame, rotated upright. */
private fun ImageProxy.toJpeg(): ByteArray? = runCatching {
    val bmp = toBitmap()
    val rotation = imageInfo.rotationDegrees
    val upright = if (rotation == 0) bmp else android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height,
        android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }, true)
    ByteArrayOutputStream().use { s -> upright.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, s); s.toByteArray() }
}.getOrNull()
