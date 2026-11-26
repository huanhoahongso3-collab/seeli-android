package com.tunnaduong.seeli.ui.screens

import com.tunnaduong.seeli.core.Audience

import androidx.compose.material.icons.outlined.Public

import androidx.compose.material.icons.outlined.People

import androidx.compose.material.icons.outlined.HowToReg

import androidx.compose.material.icons.outlined.StarOutline

import androidx.compose.material.icons.outlined.Lock

import androidx.compose.material3.rememberModalBottomSheetState

import androidx.compose.material3.pulltorefresh.PullToRefreshBox

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.foundation.layout.imePadding
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBackIos
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tunnaduong.seeli.core.ApiClient
import com.tunnaduong.seeli.core.AppLanguage
import com.tunnaduong.seeli.core.AppStore
import com.tunnaduong.seeli.core.Comment
import com.tunnaduong.seeli.core.DateText
import com.tunnaduong.seeli.core.L10n
import com.tunnaduong.seeli.core.Moment
import com.tunnaduong.seeli.core.Route
import com.tunnaduong.seeli.core.t
import com.tunnaduong.seeli.core.tf
import com.tunnaduong.seeli.ui.Avatar
import com.tunnaduong.seeli.ui.EyeLogo
import com.tunnaduong.seeli.ui.HairLine
import com.tunnaduong.seeli.ui.Leading
import com.tunnaduong.seeli.ui.MetaText
import com.tunnaduong.seeli.ui.NoteText
import com.tunnaduong.seeli.ui.PrimaryButton
import com.tunnaduong.seeli.ui.Seeli
import com.tunnaduong.seeli.ui.SeeliScreen
import com.tunnaduong.seeli.ui.SeeliTextField
import com.tunnaduong.seeli.ui.seeliCard
import com.tunnaduong.seeli.ui.tap
import com.tunnaduong.seeli.ui.ts
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoriesScreen() {
    val scope = rememberCoroutineScope()
    var month by remember { mutableStateOf(YearMonth.now()) }
    var deleting by remember { mutableStateOf<Moment?>(null) }
    var viewingComments by remember { mutableStateOf<Moment?>(null) }
    val zone = ZoneId.systemDefault()
    val inMonth = AppStore.allMoments.filter { YearMonth.from(it.createdAt.atZone(zone)) == month }.sortedByDescending { it.createdAt }
    val isCurrent = month == YearMonth.now()
    // Opened from a notification: jump to that photo's month and show its comments.
    LaunchedEffect(AppStore.pendingCommentsMoment, AppStore.moments.size) {
        val m = AppStore.pendingCommentsMoment?.let { AppStore.moment(it) } ?: return@LaunchedEffect
        AppStore.pendingCommentsMoment = null
        month = YearMonth.from(m.createdAt.atZone(zone))
        viewingComments = m
    }

    var refreshing by remember { mutableStateOf(false) }
    SeeliScreen("MY MEMORIES", t("memories.subtitle")) {
        PullToRefreshBox(refreshing, onRefresh = {
            refreshing = true
            scope.launch { AppStore.pullRefresh(); refreshing = false }
        }, Modifier.weight(1f)) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            item {
                Row(Modifier.padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(DateText.monthTitle(month.year, month.monthValue), style = ts(26, FontWeight.SemiBold, Seeli.Ink))
                        Text(tf("memories.count", inMonth.size), style = ts(13, color = Seeli.Muted))
                    }
                    MonthButton(Icons.AutoMirrored.Outlined.ArrowBackIos, true) { month = month.minusMonths(1) }
                    Spacer(Modifier.width(8.dp))
                    MonthButton(Icons.AutoMirrored.Outlined.ArrowForwardIos, !isCurrent) { month = month.plusMonths(1) }
                }
                HairLine(Modifier.padding(bottom = 16.dp))
                if (inMonth.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 60.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Outlined.FormatQuote, null, tint = Seeli.Muted, modifier = Modifier.size(28.dp))
                        Text(t("memories.empty"), style = ts(13, color = Seeli.Muted), textAlign = TextAlign.Center)
                    }
                }
            }
            items(inMonth, key = { it.id }) { m ->
                MemoryCard(m, onOpen = { AppStore.push(Route.MomentDetail(m.id)) }, onComments = { viewingComments = m }, onDelete = { deleting = m })
                Spacer(Modifier.height(14.dp))
            }
            item {
                Text(t("memories.footer"), style = ts(12, color = Seeli.Muted), textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 30.dp))
            }
        }
        }
    }

    deleting?.let { d ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = Seeli.BG,
            title = { Text(t("memories.delete.title"), style = ts(17, FontWeight.SemiBold, Seeli.Ink)) },
            text = { Text(t("memories.delete.body"), style = ts(14, color = Seeli.Muted)) },
            confirmButton = { TextButton({ scope.launch { AppStore.deleteMoment(d.id) }; deleting = null }) { Text(t("common.delete"), color = Seeli.Error) } },
            dismissButton = { TextButton({ deleting = null }) { Text(t("common.cancel"), color = Seeli.Ink) } },
        )
    }
    viewingComments?.let { m ->
        ModalBottomSheet(onDismissRequest = { viewingComments = null }, containerColor = Seeli.BG,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            CommentThreadSheet(m.id, AppStore.account?.id ?: "", AppStore.account?.name ?: "", m.note)
        }
    }
}

@Composable
private fun MonthButton(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).alpha(if (enabled) 1f else 0.4f).clip(CircleShape).background(Seeli.Card).tap(enabled, onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Seeli.Ink, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun MemoryCard(moment: Moment, onOpen: () -> Unit, onComments: () -> Unit, onDelete: () -> Unit) {
    val ja = L10n.language == AppLanguage.JA
    val noteText = moment.note.ifEmpty { t("memories.noWords") }
    Column(Modifier.fillMaxWidth().seeliCard(radius = 24.dp)) {
        // Sleeping moments can't be opened yet, but must not look disabled.
        Box(Modifier.tap { if (moment.isRevealed) onOpen() }) {
            if (ja) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.width(70.dp).heightIn(min = 210.dp)) {
                        Text("その日のことば", style = ts(9, color = Seeli.Muted))
                        Spacer(Modifier.weight(1f))
                        moment.placeName?.let { place ->
                            Column(Modifier.padding(start = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Outlined.Place, null, tint = Seeli.Muted, modifier = Modifier.size(14.dp))
                                MetaText(place, 10, 10)
                            }
                        }
                    }
                    Box(Modifier.weight(1f).heightIn(min = 210.dp).clip(RoundedCornerShape(16.dp)).background(Seeli.Card).padding(vertical = 16.dp),
                        contentAlignment = Alignment.TopCenter) { NoteText(noteText, 15, 9) }
                    MetaText(DateText.stamp(moment.createdAt), 10, 30)
                }
            } else {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(t("memories.wordsOfDay"), style = ts(10, color = Seeli.Muted))
                    Box(Modifier.fillMaxWidth().heightIn(min = 90.dp).clip(RoundedCornerShape(16.dp)).background(Seeli.Card).padding(18.dp)) {
                        NoteText(noteText, 16)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        moment.placeName?.let { place ->
                            Icon(Icons.Outlined.Place, null, tint = Seeli.Muted, modifier = Modifier.size(14.dp))
                            Text(" $place", style = ts(11, color = Seeli.Muted))
                        }
                        Spacer(Modifier.weight(1f))
                        Icon(audienceIcon(moment.sharedWith), moment.sharedWith.badge, tint = Seeli.Muted, modifier = Modifier.size(13.dp))
                        Text(" " + DateText.stamp(moment.createdAt), style = ts(11, color = Seeli.Muted))
                    }
                }
            }
        }
        HairLine(Modifier.padding(horizontal = 12.dp))
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.tap(enabled = moment.isPending != true, onClick = onComments), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.ChatBubbleOutline, null, tint = Seeli.Ink, modifier = Modifier.size(15.dp))
                Text(tf("memories.comments", moment.commentCount), style = ts(12, color = Seeli.Ink), maxLines = 1)
                val pending = moment.pendingCount ?: 0
                if (pending > 0) Text(tf("memories.pending", pending), style = ts(10, FontWeight.SemiBold, Color(0xFFA07A2C)),
                    modifier = Modifier.clip(CircleShape).background(Seeli.Sun.copy(alpha = 0.35f)).padding(horizontal = 8.dp, vertical = 3.dp))
            }
            Spacer(Modifier.weight(1f))
            val (icon, label, color) = when {
                moment.isRevealed -> Triple(Icons.Outlined.AutoAwesome, t("memories.reunited"), Seeli.Green)
                moment.isPending == true -> Triple(Icons.Outlined.CloudUpload, t("memories.uploading"), Seeli.Muted)
                else -> Triple(Icons.Outlined.Bedtime, t("memories.sleeping"), Seeli.Muted)
            }
            Icon(icon, null, tint = color, modifier = Modifier.size(13.dp))
            Text(" $label", style = ts(11, color = color), maxLines = 1)
            Box(Modifier.size(width = 36.dp, height = 30.dp).tap(onClick = onDelete), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.DeleteOutline, t("common.delete"), tint = Seeli.Muted, modifier = Modifier.size(17.dp))
            }
        }
    }
}

/** The moment an old photo comes back (~30 days later), or revisiting one. */
@Composable
fun ReunionScreen(momentID: String) {
    val scope = rememberCoroutineScope()
    val moment = AppStore.moment(momentID)
    var revealed by remember { mutableStateOf(false) }
    var reflection by remember { mutableStateOf(moment?.reflection ?: "") }
    var saved by remember { mutableStateOf(moment?.reflection != null) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    var loading by remember { mutableStateOf(true) }
    var slow by remember { mutableStateOf(false) }

    LaunchedEffect(momentID) {
        val m = moment ?: return@LaunchedEffect
        val first = m.reunitedAt == null
        // Reuniting also returns a fresh, signed photo URL.
        val fresh = AppStore.reunite(momentID) ?: m
        fresh.photoUrl?.let { url ->
            ApiClient.fetchBytes(url)?.let { b -> BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() }?.let { image = it }
        }
        loading = false
        slow = first
        revealed = true
    }
    val dur = if (slow) 2200 else 0
    val blur by animateFloatAsState(if (revealed) 0f else 30f, tween(dur, if (slow) 400 else 0), label = "b")
    val alpha by animateFloatAsState(if (revealed) 1f else 0.4f, tween(dur, if (slow) 400 else 0), label = "a")
    val scale by animateFloatAsState(if (revealed) 1f else 1.08f, tween(dur, if (slow) 400 else 0), label = "s")
    val detailsAlpha by animateFloatAsState(if (revealed) 1f else 0f, tween(dur, if (slow) 400 else 0), label = "d")

    SeeliScreen("REUNION", t("reunion.subtitle"), Leading.HOME) {
        if (moment == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(t("friend.gone"), color = Seeli.Muted) }
            return@SeeliScreen
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 30.dp)) {
            Text(tf("reunion.daysAgo", DateText.daysAgo(moment.createdAt, AppStore.now)), style = ts(13, color = Seeli.Muted))
            Text(DateText.long(moment.createdAt), style = ts(22, FontWeight.SemiBold, Seeli.Ink), modifier = Modifier.padding(top = 4.dp))

            Box(Modifier.padding(top = 18.dp).fillMaxWidth().height(380.dp).clip(RoundedCornerShape(22.dp)).background(Seeli.Card),
                contentAlignment = Alignment.Center) {
                val img = image
                when {
                    img != null -> Image(img, null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().scale(scale).alpha(alpha).blur(blur.dp))
                    loading -> CircularProgressIndicator(color = Seeli.Green)
                    else -> EyeLogo(30.dp, closed = true, color = Seeli.Muted)
                }
            }

            Row(Modifier.padding(top = 14.dp).alpha(detailsAlpha).fillMaxWidth().seeliCard(Seeli.Card, border = false).padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t("reunion.then"), style = ts(10, FontWeight.Medium, Seeli.Muted, 1.sp))
                    NoteText(moment.note.ifEmpty { t("memories.noWords") }, 15, 9)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    moment.placeName?.let { place ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Place, null, tint = Seeli.Muted, modifier = Modifier.size(13.dp))
                            Text(" $place", style = ts(11, color = Seeli.Muted))
                        }
                    }
                    Text(DateText.stamp(moment.createdAt), style = ts(11, color = Seeli.Muted))
                }
            }

            Column(Modifier.padding(top = 28.dp).alpha(detailsAlpha), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(t("reunion.now"), style = ts(18, FontWeight.SemiBold, Seeli.Ink))
                Text(t("reunion.now.hint"), style = ts(12, color = Seeli.Muted))
                Box(Modifier.fillMaxWidth().seeliCard(radius = 14.dp).padding(14.dp)) {
                    SeeliTextField(reflection, { reflection = it; saved = false }, t("reunion.placeholder"), Modifier.fillMaxWidth(),
                        singleLine = false, minLines = 3)
                }
                PrimaryButton(if (saved) t("reunion.saved") else t("reunion.save"), enabled = reflection.isNotEmpty(), onClick = {
                    scope.launch { if (AppStore.saveReflection(momentID, reflection)) saved = true }
                })
            }
        }
    }
}

fun audienceIcon(a: Audience) = when (a) {
    Audience.ONLY_ME -> Icons.Outlined.Lock
    Audience.CLOSE_FRIENDS -> Icons.Outlined.StarOutline
    Audience.SELECTED -> Icons.Outlined.HowToReg
    Audience.CIRCLE -> Icons.Outlined.People
    Audience.EVERYONE -> Icons.Outlined.Public
}
