package com.tunnaduong.seeli.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import com.tunnaduong.seeli.core.ActivityItem
import com.tunnaduong.seeli.core.Moment
import java.time.Instant
import java.time.temporal.ChronoUnit
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
import androidx.compose.material.icons.outlined.OpenInFull
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
    var ownThread by remember { mutableStateOf<Moment?>(null) }
    var viewing by remember { mutableStateOf<FriendPost?>(null) }
    var showOlder by remember { mutableStateOf(false) }
    var showAllActivity by remember { mutableStateOf(false) }
    // When this screen was last opened before now: newer things get a dot.
    val lastSeen = remember { AppStore.connectSeenAt }
    // Friends' photos can be seen, not captured.
    SecureScreen()
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        AppStore.markConnectSeen()
        AppStore.refresh()
    }
    // Opened from a notification: show that post's comments.
    LaunchedEffect(AppStore.pendingThreadPost, AppStore.friendPosts.size) {
        val id = AppStore.pendingThreadPost ?: return@LaunchedEffect
        AppStore.friendPosts.firstOrNull { it.id == id }?.let { AppStore.pendingThreadPost = null; commenting = it }
    }
    // Friends' photos are seen once: leaving this screen lets them go.
    DisposableEffect(Unit) { onDispose { AppStore.forgetOncePhotos() } }

    val posts = AppStore.friendPosts
    val newPosts = posts.filter { it.viewedAt == null }.sortedByDescending { it.createdAt }
    val activePosts = posts.filter { it.viewedAt != null && it.hasMyActivity }.sortedByDescending { it.lastActivityAt ?: it.createdAt }
    val olderPosts = posts.filter { it.viewedAt != null && !it.hasMyActivity }.sortedByDescending { it.createdAt }
    val weekAgo = Instant.now().minus(7, ChronoUnit.DAYS)
    val recent = AppStore.activity.filter { it.type != "friend_added" || it.at > weekAgo }.sortedByDescending { it.at }

    fun open(item: ActivityItem) {
        when (item.type) {
            "friend_request" -> AppStore.push(Route.Requests)
            "friend_added" -> if (AppStore.friend(item.user.id) != null) AppStore.push(Route.FriendProfile(item.user.id))
            else -> {
                val id = item.momentId ?: return
                AppStore.moments.firstOrNull { it.id == id }?.let { ownThread = it; return }
                AppStore.friendPosts.firstOrNull { it.id == id }?.let { commenting = it }
            }
        }
    }

    @Composable
    fun card(post: FriendPost) {
        FriendPostCard(post, isNew = post.createdAt > lastSeen && post.viewedAt == null,
            onOpen = { viewing = post }, onComment = { commenting = post }, onProfile = {
                if (AppStore.friend(post.author.id) != null) AppStore.push(Route.FriendProfile(post.author.id))
            })
    }

    SeeliScreen("LET'S CONNECT", t("connect.subtitle")) {
        PullToRefreshBox(refreshing, onRefresh = {
            refreshing = true
            scope.launch { AppStore.pullRefresh(); refreshing = false }
        }, Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                item {
                    Row(Modifier.fillMaxWidth().seeliCard(Seeli.Card, 16.dp, false).tap { AppStore.push(Route.Scan) }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(Icons.Outlined.QrCode2, null, tint = Seeli.Green, modifier = Modifier.size(28.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(t("connect.qr.title"), style = ts(14, FontWeight.Medium, Seeli.Ink))
                            Text(t("connect.qr.body"), style = ts(11, color = Seeli.Muted))
                        }
                    }
                    val incoming = AppStore.friendRequests.incoming
                    if (incoming.isNotEmpty()) {
                        Row(Modifier.padding(top = 12.dp).fillMaxWidth().seeliCard(radius = 16.dp).tap { AppStore.push(Route.Requests) }.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box {
                                Icon(Icons.Outlined.PersonAddAlt, null, tint = Seeli.Green, modifier = Modifier.size(24.dp))
                                CountBadge(incoming.size, Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-8).dp))
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(t("requests.banner.title"), style = ts(14, FontWeight.SemiBold, Seeli.Ink))
                                Text(incoming.take(3).joinToString(", ") { it.user.name }, style = ts(12, color = Seeli.Muted), maxLines = 1)
                            }
                            Icon(Icons.Outlined.ChevronRight, null, tint = Seeli.Muted)
                        }
                    }
                    if (recent.isNotEmpty()) {
                        Row(Modifier.padding(top = 24.dp, bottom = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(t("connect.activity"), style = ts(22, FontWeight.SemiBold, Seeli.Ink), modifier = Modifier.weight(1f))
                            if (recent.size > 4) Text(if (showAllActivity) t("connect.activity.less") else t("connect.activity.all"),
                                style = ts(12, FontWeight.Medium, Seeli.Green), modifier = Modifier.tap { showAllActivity = !showAllActivity })
                        }
                        Column(Modifier.fillMaxWidth().seeliCard(radius = 18.dp)) {
                            val items = if (showAllActivity) recent.take(30) else recent.take(4)
                            items.forEachIndexed { i, item ->
                                ActivityRow(item, isNew = item.at > lastSeen) { open(item) }
                                if (i < items.lastIndex) HairLine(Modifier.padding(start = 56.dp))
                            }
                        }
                    }
                }
                if (posts.isEmpty()) {
                    item {
                        SectionTitle(t("connect.feed.title"), t("connect.feed.subtitle"), Modifier.padding(top = 28.dp, bottom = 16.dp))
                        Column(Modifier.fillMaxWidth().seeliCard().padding(vertical = 50.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Outlined.People, null, tint = Seeli.Muted, modifier = Modifier.size(30.dp))
                            Text(t("connect.empty"), style = ts(13, color = Seeli.Muted), textAlign = TextAlign.Center)
                        }
                    }
                } else {
                    item {
                        SectionTitle(t("connect.section.new"), if (newPosts.isEmpty()) t("connect.section.new.empty") else null,
                            Modifier.padding(top = 28.dp, bottom = 16.dp))
                    }
                    items(newPosts, key = { it.id }) { post -> card(post); Spacer(Modifier.height(20.dp)) }
                    if (activePosts.isNotEmpty()) {
                        item { SectionTitle(t("connect.section.active"), null, Modifier.padding(top = 8.dp, bottom = 16.dp)) }
                        items(activePosts, key = { it.id }) { post -> card(post); Spacer(Modifier.height(20.dp)) }
                    }
                    if (olderPosts.isNotEmpty()) {
                        item {
                            Row(Modifier.padding(top = 4.dp, bottom = 16.dp).fillMaxWidth().seeliCard(Seeli.Card, 16.dp, false)
                                .tap { showOlder = !showOlder }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (showOlder) t("connect.older.hide") else tf("connect.older.show", olderPosts.size),
                                    style = ts(14, FontWeight.Medium, Seeli.Ink), modifier = Modifier.weight(1f))
                                Icon(if (showOlder) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = Seeli.Ink)
                            }
                        }
                        if (showOlder) items(olderPosts, key = { it.id }) { post -> card(post); Spacer(Modifier.height(20.dp)) }
                    }
                }
                item { Spacer(Modifier.height(30.dp)) }
            }
        }
    }

    viewing?.let { post -> OncePhotoViewer(post) { viewing = null } }

    commenting?.let { post ->
        ModalBottomSheet(onDismissRequest = { commenting = null }, containerColor = Seeli.BG,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            CommentThreadSheet(post.id, post.author.id, post.author.name, post.note)
        }
    }
    ownThread?.let { m ->
        ModalBottomSheet(onDismissRequest = { ownThread = null }, containerColor = Seeli.BG,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            CommentThreadSheet(m.id, AppStore.account?.id ?: "", AppStore.account?.name ?: "", m.note)
        }
    }
}

@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(modifier.heightIn(min = 18.dp).widthIn(min = 18.dp).clip(CircleShape).background(Color(0xFFC4553F))
        .padding(horizontal = if (count > 9) 5.dp else 0.dp), contentAlignment = Alignment.Center) {
        Text(if (count > 99) "99+" else "$count", style = ts(10, FontWeight.Bold, Color.White))
    }
}

@Composable
private fun ActivityRow(item: ActivityItem, isNew: Boolean, onClick: () -> Unit) {
    val title = when (item.type) {
        "comment" -> if (item.pending == true) tf("activity.comment.pending", item.user.name) else tf("activity.comment", item.user.name)
        "comment_reply" -> tf("activity.reply", item.user.name)
        "friend_request" -> tf("activity.request", item.user.name)
        "friend_added" -> tf("activity.friend", item.user.name)
        else -> item.user.name
    }
    Row(Modifier.fillMaxWidth().tap(onClick = onClick).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Avatar(item.user.initial, 34.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = ts(13, color = Seeli.Ink))
            item.text?.takeIf { it.isNotEmpty() }?.let { Text("“$it”", style = ts(12, color = Seeli.Muted), maxLines = 2) }
            Text(DateText.stamp(item.at), style = ts(10, color = Seeli.Muted))
        }
        if (isNew) Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(Seeli.Green))
    }
}

@Composable
private fun FriendPostCard(post: FriendPost, isNew: Boolean, onOpen: () -> Unit, onComment: () -> Unit, onProfile: () -> Unit) {
    Column(Modifier.fillMaxWidth().seeliCard(radius = 26.dp).padding(14.dp)) {
        Box(Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(6.dp))) {
            OncePhotoTile(post, onOpen)
            if (isNew) Box(Modifier.align(Alignment.TopEnd).padding(10.dp)) { Chip("NEW") }
        }
        InfoBox(post, onProfile, Modifier.padding(top = 12.dp))
        HairLine(Modifier.padding(vertical = 16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val mine = post.myComment
            if (mine != null) {
                // Tap the conversation to keep it going.
                Column(Modifier.fillMaxWidth().tap(onClick = onComment), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.Chat, null, tint = Seeli.Green, modifier = Modifier.size(20.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(t("connect.sent"), style = ts(11, color = Seeli.Muted))
                            Text(mine, style = ts(14, color = Seeli.Ink), maxLines = 3)
                        }
                    }
                    post.myCommentReply?.let { answer ->
                        Row(Modifier.padding(start = 30.dp).fillMaxWidth().seeliCard(Seeli.Card, 14.dp, false).padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Avatar(post.author.initial, 22.dp)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(tf("connect.reply.from", post.author.name), style = ts(11, color = Seeli.Muted))
                                Text(answer, style = ts(14, color = Seeli.Ink), maxLines = 3)
                            }
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.ChatBubbleOutline, null, tint = Seeli.Ink, modifier = Modifier.size(18.dp))
                    Text(t("connect.prompt"), style = ts(14, FontWeight.Medium, Seeli.Ink))
                }
            }
            val count = post.commentCount ?: 0
            SoftButton(if (count > 0) tf("connect.comments.open", count) else t("connect.send"), onClick = onComment)
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
 * A friend's photo opens only when you tap it (once, after a confirmation), then shows full screen.
 * It stays in the card while you are on this screen; leave (or close the app) and it is gone for good.
 */
@Composable
private fun OncePhotoTile(post: FriendPost, onOpen: () -> Unit) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    val image = AppStore.onceImages[post.id]
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Landscape(post.scene)
        AnimatedVisibility(image != null, enter = fadeIn(tween(350))) {
            image?.let {
                Box(Modifier.fillMaxSize().tap(onClick = onOpen)) {
                    Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Row(Modifier.align(Alignment.BottomStart).padding(10.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(Icons.Outlined.OpenInFull, null, tint = Color.White, modifier = Modifier.size(12.dp))
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
            else -> Box(Modifier.fillMaxSize().tap { confirming = true }, contentAlignment = Alignment.Center) {
                Column(Modifier.clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.28f)).padding(horizontal = 22.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.TouchApp, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    Text(t("connect.once.tap"), style = ts(15, FontWeight.SemiBold, Color.White))
                    Text(t("connect.once.hint"), style = ts(11, color = Color.White.copy(alpha = 0.85f)))
                }
            }
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            containerColor = Seeli.BG,
            title = { Text(t("connect.once.confirm.title"), style = ts(17, FontWeight.SemiBold, Seeli.Ink)) },
            text = { Text(t("connect.once.confirm.body"), style = ts(14, color = Seeli.Muted)) },
            confirmButton = {
                TextButton({
                    confirming = false
                    loading = true
                    scope.launch {
                        AppStore.loadOncePhoto(post.id)
                        loading = false
                        if (AppStore.onceImages[post.id] != null) onOpen()
                    }
                }) { Text(t("connect.once.confirm.ok"), color = Seeli.Green) }
            },
            dismissButton = { TextButton({ confirming = false }) { Text(t("common.cancel"), color = Seeli.Ink) } },
        )
    }
}
