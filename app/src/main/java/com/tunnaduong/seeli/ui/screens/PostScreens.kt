package com.tunnaduong.seeli.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tunnaduong.seeli.core.AppStore
import com.tunnaduong.seeli.core.Audience
import com.tunnaduong.seeli.core.Draft
import com.tunnaduong.seeli.core.LocationService
import com.tunnaduong.seeli.core.Route
import com.tunnaduong.seeli.core.t
import com.tunnaduong.seeli.ui.EyeLogo
import com.tunnaduong.seeli.ui.Leading
import com.tunnaduong.seeli.ui.PrimaryButton
import com.tunnaduong.seeli.ui.SectionTitle
import com.tunnaduong.seeli.ui.Seeli
import com.tunnaduong.seeli.ui.SeeliScreen
import com.tunnaduong.seeli.ui.SeeliTextField
import com.tunnaduong.seeli.ui.seeliCard
import com.tunnaduong.seeli.ui.tap
import com.tunnaduong.seeli.ui.ts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun seeliSwitchColors() = SwitchDefaults.colors(
    checkedTrackColor = Seeli.Green, checkedThumbColor = Color.White,
    uncheckedTrackColor = Seeli.Line, uncheckedThumbColor = Color.White, uncheckedBorderColor = Seeli.Line,
)

@Composable
fun PostScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val limit = 80
    var note by remember { mutableStateOf(AppStore.draft.note) }
    var recordPlace by remember { mutableStateOf(AppStore.draft.recordPlace) }
    var placeName by remember { mutableStateOf(AppStore.draft.placeName) }
    var locating by remember { mutableStateOf(false) }

    fun locate() {
        locating = true
        scope.launch { placeName = LocationService.currentPlaceName(context); locating = false }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) locate() else locating = false
    }
    fun requestPlace() {
        if (LocationService.hasPermission(context)) locate()
        else permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }
    LaunchedEffect(recordPlace) { if (recordPlace && placeName == null) requestPlace() }

    val placeSubtitle = when {
        !recordPlace -> t("post.place.off")
        locating -> t("post.place.locating")
        placeName != null -> placeName + " · " + t("post.place.private")
        else -> t("post.place.private")
    }

    SeeliScreen("POST", t("post.subtitle"), Leading.BACK) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Column(
                Modifier.fillMaxWidth().seeliCard(Seeli.Card, 24.dp, false).padding(vertical = 24.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { EyeLogo(22.dp, closed = true) }
                Text(t("post.hidden.title"), style = ts(17, FontWeight.SemiBold, Seeli.Ink))
                Text(t("post.hidden.body"), style = ts(12, color = Seeli.Muted).copy(lineHeight = 17.sp), textAlign = TextAlign.Center)
                Text(t("post.hidden.auto"), style = ts(11, color = Seeli.Muted), modifier = Modifier.padding(top = 6.dp))
            }

            SectionTitle(t("post.note.title"), t("post.note.subtitle"), Modifier.padding(top = 28.dp, bottom = 14.dp))

            Box(Modifier.fillMaxWidth().heightIn(min = 110.dp).seeliCard()) {
                SeeliTextField(note, { note = it.take(limit) }, t("post.note.placeholder"),
                    Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 18.dp), singleLine = false, minLines = 3, fontSize = 15)
                Text("${note.length} / $limit", style = ts(11, color = Seeli.Muted), modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp))
            }

            Row(Modifier.padding(top = 16.dp).fillMaxWidth().seeliCard().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Place, null, tint = Seeli.Green)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(t("post.place.title"), style = ts(15, FontWeight.Medium, Seeli.Ink))
                    Text(placeSubtitle, style = ts(12, color = Seeli.Muted))
                }
                Switch(recordPlace, { recordPlace = it }, colors = seeliSwitchColors())
            }
        }
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(onClick = {
                AppStore.draft.note = note
                AppStore.draft.recordPlace = recordPlace
                AppStore.draft.placeName = placeName
                AppStore.push(Route.AudienceRoute)
            }) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    Text(t("post.next"), modifier = Modifier.align(Alignment.Center))
                    Icon(Icons.Outlined.ChevronRight, null, modifier = Modifier.align(Alignment.CenterEnd).size(18.dp))
                }
            }
            Text(t("post.next.hint"), style = ts(11, color = Seeli.Muted))
        }
    }
}

@Composable
fun AudienceScreen() {
    var audience by remember { mutableStateOf(AppStore.draft.audience) }
    SeeliScreen("AUDIENCE", t("audience.subtitle"), Leading.BACK) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitle(t("audience.title"), t("audience.hint"), Modifier.padding(bottom = 10.dp))
            Audience.entries.forEach { option -> AudienceOption(option, audience == option) { audience = option } }
            Row(Modifier.padding(top = 10.dp).fillMaxWidth().seeliCard(Seeli.Card, border = false).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Lock, null, tint = Seeli.Muted, modifier = Modifier.size(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(t("audience.lock.title"), style = ts(13, FontWeight.SemiBold, Seeli.Ink))
                    Text(t("audience.lock.body"), style = ts(12, color = Seeli.Muted))
                }
            }
        }
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PrimaryButton(t("audience.submit"), onClick = {
                AppStore.draft.audience = audience
                AppStore.commitDraft()
                AppStore.setPath(Route.Posted)
            })
            Text(t("audience.cancel"), style = ts(13, color = Seeli.Muted), modifier = Modifier.tap {
                AppStore.draft = Draft()
                AppStore.path.clear()
            })
        }
    }
}

@Composable
private fun AudienceOption(option: Audience, selected: Boolean, onClick: () -> Unit) {
    val disabled = option == Audience.EVERYONE
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth()
            .seeliCard(if (disabled) Seeli.Card else Seeli.Surface, border = !disabled)
            .border(if (selected) 1.5.dp else 0.dp, if (selected) Seeli.Green else Color.Transparent, shape)
            .tap(enabled = !disabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
            if (disabled) Icon(Icons.Outlined.Public, null, tint = Seeli.Muted, modifier = Modifier.size(22.dp))
            else {
                Box(Modifier.size(24.dp).border(1.5.dp, if (selected) Seeli.Green else Seeli.Ink.copy(alpha = 0.6f), CircleShape))
                if (selected) Box(Modifier.size(13.dp).clip(CircleShape).background(Seeli.Green))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(option.badge, style = ts(11, FontWeight.SemiBold, if (disabled) Seeli.Muted else Seeli.Ink.copy(alpha = 0.8f), 1.2.sp))
            Text(t(option.descriptionKey), style = ts(15, color = if (disabled) Seeli.Muted else Seeli.Ink))
        }
        if (disabled) Text(t("audience.soon"), style = ts(11, color = Seeli.Muted),
            modifier = Modifier.clip(CircleShape).background(Color.White).padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

/** After posting: let go, put the phone down. */
@Composable
fun PostedScreen() {
    var phase by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { for (step in 1..3) { delay(600); phase = step } }
    val circle by animateFloatAsState(if (phase >= 1) 1f else 0.6f, tween(700), label = "c")
    val a1 by animateFloatAsState(if (phase >= 1) 1f else 0f, tween(700), label = "a1")
    val a2 by animateFloatAsState(if (phase >= 2) 1f else 0f, tween(700), label = "a2")
    val a3 by animateFloatAsState(if (phase >= 3) 1f else 0f, tween(700), label = "a3")
    Column(Modifier.fillMaxSize().background(Seeli.BG).statusBarsPadding().navigationBarsPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(140.dp).scale(circle).clip(CircleShape).background(Seeli.Card), contentAlignment = Alignment.Center) {
            EyeLogo(46.dp, closed = phase >= 1, color = Seeli.Green)
        }
        Text(t("posted.title"), style = ts(26, FontWeight.SemiBold, Seeli.Ink), modifier = Modifier.padding(top = 36.dp).alpha(a1))
        Text(t("posted.body"), style = ts(14, color = Seeli.Muted).copy(lineHeight = 20.sp), textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 14.dp).alpha(a2))
        Text(t("posted.lookUp"), style = ts(17, color = Seeli.Ink, family = Seeli.Mincho), textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 36.dp).alpha(a3))
        Spacer(Modifier.weight(1f))
        PrimaryButton(t("posted.home"), Modifier.alpha(a3), onClick = { AppStore.path.clear() })
        Text("SEE MORE. LIVE MORE.", style = ts(10, FontWeight.Medium, Seeli.Muted, 2.sp), modifier = Modifier.padding(top = 16.dp))
    }
}
