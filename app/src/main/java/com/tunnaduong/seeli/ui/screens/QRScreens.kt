package com.tunnaduong.seeli.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.QrCodeScanner
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.tunnaduong.seeli.core.AppStore
import com.tunnaduong.seeli.core.Friend
import com.tunnaduong.seeli.core.FriendInvite
import com.tunnaduong.seeli.core.QRCode
import com.tunnaduong.seeli.core.Route
import com.tunnaduong.seeli.core.t
import com.tunnaduong.seeli.ui.ErrorText
import com.tunnaduong.seeli.core.tf
import com.tunnaduong.seeli.ui.Avatar
import com.tunnaduong.seeli.ui.Leading
import com.tunnaduong.seeli.ui.PrimaryButton
import com.tunnaduong.seeli.ui.Seeli
import com.tunnaduong.seeli.ui.SeeliScreen
import com.tunnaduong.seeli.ui.SeeliTextField
import com.tunnaduong.seeli.ui.seeliCard
import com.tunnaduong.seeli.ui.tap
import com.tunnaduong.seeli.ui.ts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@Composable
fun QRScanScreen() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var available by remember { mutableStateOf(true) }
    var manual by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    var handled by remember { mutableStateOf(false) }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    fun handle(code: String) {
        if (handled) return
        val invite = FriendInvite.parse(code) ?: run { invalid = true; return }
        handled = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        AppStore.pendingInvite = invite
        scope.launch { delay(1000); handled = false }
    }

    fun start() {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val p = future.get(); provider = p
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val reader = MultiFormatReader().apply { setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))) }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { image -> decode(reader, image)?.let { v -> ContextCompat.getMainExecutor(context).execute { handle(v) } } }
                val selector = if (p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                p.unbindAll()
                p.bindToLifecycle(lifecycle, selector, preview, analysis)
            } catch (_: Exception) { available = false }
        }, ContextCompat.getMainExecutor(context))
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) start() else available = false }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) start()
        else permission.launch(Manifest.permission.CAMERA)
    }
    DisposableEffect(Unit) { onDispose { provider?.unbindAll(); executor.shutdown() } }

    Box(Modifier.fillMaxSize().background(Seeli.Night)) {
        if (available) AndroidView({ previewView }, Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(Icons.Outlined.Close, t("common.close")) { AppStore.pop() }
                Spacer(Modifier.weight(1f))
                Text("QR SCAN", style = ts(14, FontWeight.SemiBold, Color.White, 1.5.sp))
                Spacer(Modifier.weight(1f))
                RoundIcon(Icons.Outlined.QrCode2, t("myqr.title")) { AppStore.pop(); AppStore.push(Route.MyQR) }
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.align(Alignment.CenterHorizontally).size(250.dp).border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center) {
                if (!available) Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.QrCodeScanner, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(40.dp))
                    Text(t("scan.unavailable"), style = ts(12, color = Color.White.copy(alpha = 0.6f)), textAlign = TextAlign.Center)
                }
            }
            Text(t("scan.hint"), style = ts(13, color = Color.White.copy(alpha = 0.8f)), textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 22.dp, start = 24.dp, end = 24.dp))
            Spacer(Modifier.weight(1f))
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t("scan.manual"), style = ts(12, color = Color.White.copy(alpha = 0.6f)))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.1f)).padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart) {
                        SeeliTextField(manual, { manual = it; invalid = false }, "ABCD 1234", Modifier.fillMaxWidth(), color = Color.White,
                            placeholderColor = Color.White.copy(alpha = 0.4f), capitalization = KeyboardCapitalization.Characters)
                    }
                    Box(Modifier.height(46.dp).clip(RoundedCornerShape(12.dp)).background(Seeli.GreenPale).tap { handle(manual) }.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center) { Text(t("scan.add"), style = ts(14, FontWeight.SemiBold, Seeli.Night)) }
                }
                if (invalid) Text(t("scan.invalid"), style = ts(12, color = Seeli.Sun))
            }
        }
    }
}

private fun decode(reader: MultiFormatReader, image: ImageProxy): String? = try {
    val plane = image.planes[0]
    val buffer = plane.buffer
    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
    val source = PlanarYUVLuminanceSource(bytes, plane.rowStride, image.height, 0, 0, image.width, image.height, false)
    reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
} catch (_: Exception) {
    null
} finally {
    reader.reset()
    image.close()
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).tap(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun MyQRScreen() {
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { AppStore.refresh() }
    val account = AppStore.account
    val invite = AppStore.myInvite
    val qr = remember(invite.url) { if (invite.code.isNotEmpty()) QRCode.bitmap(invite.url).asImageBitmap() else null }
    SeeliScreen("MY QR", t("myqr.subtitle"), Leading.BACK) {
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Column(Modifier.padding(horizontal = 24.dp).fillMaxWidth().seeliCard(Seeli.Card, 28.dp, false).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Avatar(account?.initial ?: "", 56.dp)
                Text(account?.name ?: "", style = ts(20, FontWeight.SemiBold, Seeli.Ink))
                Text("@${account?.handle ?: ""}", style = ts(13, color = Seeli.Muted))
                qr?.let {
                    Box(Modifier.clip(RoundedCornerShape(18.dp)).background(Color.White).padding(16.dp)) {
                        Image(it, null, filterQuality = FilterQuality.None, modifier = Modifier.size(200.dp))
                    }
                }
                val code = invite.code
                SelectionContainer {
                    Text(if (code.length == 8) "${code.take(4)} ${code.takeLast(4)}" else code,
                        style = ts(18, FontWeight.Medium, Seeli.Ink, 2.sp, FontFamily.Monospace))
                }
            }
            Text(t("myqr.hint"), style = ts(13, color = Seeli.Muted), textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 40.dp).padding(top = 18.dp))
            Text(t("myqr.rotate"), style = ts(12, color = Seeli.Green), modifier = Modifier.padding(top = 10.dp).tap { scope.launch { AppStore.rotateInviteCode() } })
            Spacer(Modifier.weight(1f))
            PrimaryButton(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp), onClick = { AppStore.pop(); AppStore.push(Route.Scan) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.QrCodeScanner, null, modifier = Modifier.size(20.dp))
                    Text(t("myqr.scan"))
                }
            }
        }
    }
}

/** Shown as a bottom sheet when an invite QR / link arrives. Sends a friend request. */
@Composable
fun AddFriendSheet(invite: FriendInvite, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<AppStore.InvitePreview?>(null) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var added by remember { mutableStateOf<Friend?>(null) }
    var requested by remember { mutableStateOf(false) }
    LaunchedEffect(invite) {
        try { preview = AppStore.preview(invite) } catch (_: Exception) { failed = true }
    }
    val p = preview
    val status = when {
        failed -> t("addFriend.notFound")
        p == null -> ""
        added != null -> t("addFriend.done")
        requested || p.requested == true -> tf("addFriend.requested", p.user.name)
        p.isSelf -> t("addFriend.self")
        p.isFriend -> t("addFriend.already")
        p.requestedMe == true -> tf("addFriend.requestedMe", p.user.name)
        else -> t("addFriend.question")
    }
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("ADD FRIEND", style = ts(14, FontWeight.SemiBold, Seeli.Ink, 1.5.sp), modifier = Modifier.padding(top = 4.dp, bottom = 28.dp))
        when {
            p != null -> {
                Avatar(p.user.initial, 84.dp)
                Text(p.user.name, style = ts(22, FontWeight.SemiBold, Seeli.Ink), modifier = Modifier.padding(top = 14.dp))
                Text("@${p.user.handle}", style = ts(13, color = Seeli.Muted), modifier = Modifier.padding(top = 4.dp))
            }
            failed -> Icon(Icons.Outlined.HelpOutline, null, tint = Seeli.Muted, modifier = Modifier.size(44.dp))
            else -> CircularProgressIndicator(color = Seeli.Green)
        }
        Text(status, style = ts(13, color = Seeli.Muted), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 20.dp, bottom = 28.dp).padding(horizontal = 30.dp))
        if (p != null && !p.isSelf && !p.isFriend && added == null && !requested && p.requested != true) {
            PrimaryButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    when (val r = AppStore.addFriend(invite)) {
                        is AppStore.AddResult.Friends -> added = r.friend
                        AppStore.AddResult.Requested -> requested = true
                        null -> {}
                    }
                    busy = false
                }
            }) {
                if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                else Text(if (p.requestedMe == true) t("requests.accept") else t("addFriend.request"))
            }
            if (!busy) ErrorText(AppStore.lastError, Modifier.padding(top = 8.dp))
            Text(t("common.cancel"), style = ts(13, color = Seeli.Muted), modifier = Modifier.padding(top = 12.dp).tap(onClick = onDismiss))
        } else {
            PrimaryButton(t("common.close"), onClick = {
                onDismiss()
                added?.let { AppStore.setPath(Route.Connect, Route.FriendProfile(it.id)) }
            })
        }
    }
}
