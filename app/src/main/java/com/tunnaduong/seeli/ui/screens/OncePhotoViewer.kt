package com.tunnaduong.seeli.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.tunnaduong.seeli.core.AppStore
import com.tunnaduong.seeli.core.FriendPost
import com.tunnaduong.seeli.core.t
import com.tunnaduong.seeli.ui.tap
import com.tunnaduong.seeli.ui.ts
import kotlinx.coroutines.launch

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** Blocks screenshots and screen recording (and hides the app preview in Recents) while on screen. */
@Composable
fun SecureScreen() {
    val activity = LocalContext.current.activity() ?: return
    DisposableEffect(activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

/**
 * Full-screen look at a friend's photo: pinch to zoom, drag to pan, double-tap to toggle.
 * Lives only as long as the in-memory image, so leaving the app closes it.
 */
@Composable
fun OncePhotoViewer(post: FriendPost, onClose: () -> Unit) {
    val image = AppStore.onceImages[post.id]
    LaunchedEffect(image == null) { if (image == null) onClose() }
    if (image == null) return
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
        securePolicy = SecureFlagPolicy.SecureOn)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            ZoomableImage(image.asImageBitmap())
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(post.author.name, style = ts(15, FontWeight.SemiBold, Color.White))
                    Text(t("connect.once.protected"), style = ts(11, color = Color.White.copy(alpha = 0.75f)))
                }
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)).tap(onClick = onClose),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Close, t("common.close"), tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun ZoomableImage(bitmap: androidx.compose.ui.graphics.ImageBitmap) {
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    fun clamp() = scope.launch {
        val s = scale.value
        val maxX = size.width * (s - 1) / 2
        val maxY = size.height * (s - 1) / 2
        launch { offsetX.animateTo(offsetX.value.coerceIn(-maxX, maxX)) }
        launch { offsetY.animateTo(offsetY.value.coerceIn(-maxY, maxY)) }
    }

    Image(
        bitmap, null, contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { tapAt ->
                    scope.launch {
                        if (scale.value > 1f) {
                            launch { scale.animateTo(1f) }; launch { offsetX.animateTo(0f) }; launch { offsetY.animateTo(0f) }
                        } else {
                            val target = 2.5f
                            // Zoom toward the tapped point.
                            val cx = (size.width / 2f - tapAt.x) * (target - 1)
                            val cy = (size.height / 2f - tapAt.y) * (target - 1)
                            launch { scale.animateTo(target) }; launch { offsetX.animateTo(cx) }; launch { offsetY.animateTo(cy) }
                        }
                    }
                })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        val multi = event.changes.size > 1
                        if (multi || scale.value > 1f) {
                            scope.launch {
                                scale.snapTo((scale.value * zoom).coerceIn(1f, 5f))
                                offsetX.snapTo(offsetX.value + pan.x)
                                offsetY.snapTo(offsetY.value + pan.y)
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (scale.value <= 1f) scope.launch {
                        launch { scale.animateTo(1f) }; launch { offsetX.animateTo(0f) }; launch { offsetY.animateTo(0f) }
                    } else clamp()
                }
            }
            .graphicsLayer {
                scaleX = scale.value; scaleY = scale.value
                translationX = offsetX.value; translationY = offsetY.value
            },
    )
}
