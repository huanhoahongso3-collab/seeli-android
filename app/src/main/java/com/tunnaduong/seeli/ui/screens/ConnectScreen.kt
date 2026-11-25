package com.tunnaduong.seeli.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tunnaduong.seeli.core.AppLanguage
import com.tunnaduong.seeli.core.AppStore
import com.tunnaduong.seeli.core.DateText
import com.tunnaduong.seeli.core.FriendPost
import com.tunnaduong.seeli.core.L10n
import com.tunnaduong.seeli.core.Route
import com.tunnaduong.seeli.core.t
import com.tunnaduong.seeli.core.tf
import com.tunnaduong.seeli.ui.Avatar
import com.tunnaduong.seeli.ui.Chip
import com.tunnaduong.seeli.ui.ErrorText
import com.tunnaduong.seeli.ui.HairLine
import com.tunnaduong.seeli.ui.Landscape
import com.tunnaduong.seeli.ui.MetaText
import com.tunnaduong.seeli.ui.NoteText
import com.tunnaduong.seeli.ui.PrimaryButton
import com.tunnaduong.seeli.ui.SectionTitle
import com.tunnaduong.seeli.ui.Seeli
import com.tunnaduong.seeli.ui.SeeliScreen
import com.tunnaduong.seeli.ui.SeeliTextField
import com.tunnaduong.seeli.ui.SoftButton
import com.tunnaduong.seeli.ui.seeliCard
import com.tunnaduong.seeli.ui.tap
import com.tunnaduong.seeli.ui.ts
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen() {
    val scope = rememberCoroutineScope()
    var commenting by remember { mutableStateOf<FriendPost?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { AppStore.refresh() }
    // Friends' photos are seen once: leaving this screen lets them go.
    DisposableEffect(Unit) { onDispose { AppStore.forgetOncePhotos() } }

    SeeliScreen("LET'S CONNECT", t("connect.subtitle")) {
        PullToRefreshBox(refreshing, onRefresh = {
            refreshing = true
            scope.launch { AppStore.pullRefresh(); refreshing = false }
        }, Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                item {
                    Row(Modifier.fillMaxWidth().seeliCard(Seeli.Card, 16.dp, false).tap { AppStore.push(Route.Scan) }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(Icons.Outlined.QrCode2, null, tint = Seeli.Green, modifier = Modifier.size(30.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(t("connect.qr.title"), style = ts(15, FontWeight.Medium, Seeli.Ink))
                            Text(t("connect.qr.body"), style = ts(11, color = Seeli.Muted))
                        }
                    }
                    SectionTitle(t("connect.feed.title"), t("connect.feed.subtitle"), Modifier.padding(top = 28.dp, bottom = 16.dp))
                }
                val posts = AppStore.visiblePosts
                if (posts.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().seeliCard().padding(vertical = 50.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Outlined.People, null, tint = Seeli.Muted, modifier = Modifier.size(30.dp))
                            Text(t("connect.empty"), style = ts(13, color = Seeli.Muted), textAlign = TextAlign.Center)
                        }
                    }
                } else {
                    items(posts, key = { it.id }) { post ->
                        FriendPostCard(post, onComment = { commenting = post }, onProfile = {
                            if (AppStore.friend(post.author.id) != null) AppStore.push(Route.FriendProfile(post.author.id))
                        })
                        Spacer(Modifier.height(20.dp))
                    }
                }
                item { Spacer(Modifier.height(30.dp)) }
            }
        }
    }

    commenting?.let { post ->
        ModalBottomSheet(onDismissRequest = { commenting = null }, containerColor = Seeli.BG) {
            PerspectiveSheet(post) { commenting = null }
        }
    }
}

@Composable
private fun FriendPostCard(post: FriendPost, onComment: () -> Unit, onProfile: () -> Unit) {
    Column(Modifier.fillMaxWidth().seeliCard(radius = 26.dp).padding(14.dp)) {
        Box(Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(6.dp))) {
            OncePhotoTile(post)
            Box(Modifier.align(Alignment.TopEnd).padding(10.dp)) { Chip("MY CIRCLE") }
        }
        InfoBox(post, onProfile, Modifier.padding(top = 12.dp))
        HairLine(Modifier.padding(vertical = 16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val mine = post.myComment
            if (mine != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.Chat, null, tint = Seeli.Green, modifier = Modifier.size(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(t("connect.sent"), style = ts(11, color = Seeli.Muted))
                        Text(mine, style = ts(14, color = Seeli.Ink))
                    }
                }
                post.myCommentReply?.let { answer ->
                    Row(Modifier.padding(start = 30.dp).fillMaxWidth().seeliCard(Seeli.Card, 14.dp, false).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Avatar(post.author.initial, 22.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(tf("connect.reply.from", post.author.name), style = ts(11, color = Seeli.Muted))
                            Text(answer, style = ts(14, color = Seeli.Ink))
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.ChatBubbleOutline, null, tint = Seeli.Ink, modifier = Modifier.size(18.dp))
                    Text(t("connect.prompt"), style = ts(14, FontWeight.Medium, Seeli.Ink))
                }
            }
            SoftButton(if (mine == null) t("connect.send") else t("connect.edit"), onClick = onComment)
            Text(t("connect.privacy"), style = ts(11, color = Seeli.Muted), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun InfoBox(post: FriendPost, onProfile: () -> Unit, modifier: Modifier) {
    val ja = L10n.language == AppLanguage.JA
    Row(modifier.fillMaxWidth().heightIn(min = if (ja) 200.dp else 0.dp).seeliCard(Seeli.Card, 18.dp, false).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.tap(onClick = onProfile), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Avatar(post.author.initial, 30.dp)
                Text(post.author.name, style = ts(14, FontWeight.SemiBold, Seeli.Ink))
            }
            Text("@${post.author.handle}", style = ts(10, color = Seeli.Muted))
        }
        Column(Modifier.then(if (ja) Modifier else Modifier.widthIn(max = 170.dp)),
            horizontalAlignment = if (ja) Alignment.CenterHorizontally else Alignment.Start, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("MEMORY NOTE", style = ts(9, FontWeight.Medium, Seeli.Muted, 1.sp))
            NoteText(post.note, 15, 7)
            if (!ja) Text(DateText.stamp(post.createdAt), style = ts(11, color = Seeli.Muted))
        }
        if (ja) MetaText(DateText.stamp(post.createdAt), 11, 30)
    }
}

/**
 * A friend's photo shows right in the feed the first time it scrolls into view.
 * It stays only while you are on this screen; leave (or close the app) and it is gone for good.
 */
@Composable
private fun OncePhotoTile(post: FriendPost) {
    var loading by remember { mutableStateOf(false) }
    val image = AppStore.onceImages[post.id]
    LaunchedEffect(post.id) {
        if (post.viewedAt == null && AppStore.onceImages[post.id] == null) {
            loading = true
            AppStore.loadOncePhoto(post.id)
            loading = false
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Landscape(post.scene)
        AnimatedVisibility(image != null, enter = fadeIn(tween(350))) {
            image?.let {
                Box(Modifier.fillMaxSize()) {
                    Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Row(Modifier.align(Alignment.BottomStart).padding(10.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(Icons.Outlined.Visibility, null, tint = Color.White, modifier = Modifier.size(12.dp))
                        Text(t("connect.once.now"), style = ts(10, FontWeight.Medium, Color.White))
                    }
                }
            }
        }
        val seen = post.viewedAt
        when {
            image != null -> {}
            loading -> CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            seen != null -> {
                Box(Modifier.fillMaxSize().background(Seeli.Ink.copy(alpha = 0.45f)))
                Column(Modifier.padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.VisibilityOff, null, tint = Color.White, modifier = Modifier.size(26.dp))
                    Text(t("connect.once.seen"), style = ts(14, FontWeight.Medium, Color.White), textAlign = TextAlign.Center)
                    Text(tf("connect.once.seen.at", DateText.stamp(seen)), style = ts(11, color = Color.White.copy(alpha = 0.85f)))
                }
            }
        }
    }
}

@Composable
private fun PerspectiveSheet(post: FriendPost, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(post.myComment ?: "") }
    var busy by remember { mutableStateOf(false) }
    val empty = text.isBlank()
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(t("connect.prompt"), style = ts(18, FontWeight.SemiBold, Seeli.Ink))
        Text(post.note, style = ts(14, color = Seeli.Muted, family = Seeli.Mincho))
        Box(Modifier.fillMaxWidth().seeliCard(radius = 14.dp).padding(14.dp)) {
            SeeliTextField(text, { text = it }, t("connect.comment.placeholder"), Modifier.fillMaxWidth(), singleLine = false, minLines = 3)
        }
        Text(t("connect.privacy"), style = ts(11, color = Seeli.Muted))
        ErrorText(AppStore.lastError, Modifier)
        PrimaryButton(enabled = !empty && !busy, onClick = {
            busy = true
            scope.launch {
                if (AppStore.sendPerspective(post.id, text)) onDone()
                busy = false
            }
        }) { if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp)) else Text(t("connect.send")) }
    }
}
