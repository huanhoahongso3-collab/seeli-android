package com.tunnaduong.seeli.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tunnaduong.seeli.core.t
import com.tunnaduong.seeli.ui.EyeLogo
import com.tunnaduong.seeli.ui.PrimaryButton
import com.tunnaduong.seeli.ui.Seeli
import com.tunnaduong.seeli.ui.ts

/** "What's new" — shown once after updating to a version with notable changes. */
object WhatsNew {
    /** Bump when there is a new list to show. */
    const val CURRENT = "1.3"
    private const val KEY = "seeli.whatsNewSeen"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("seeli.prefs", Context.MODE_PRIVATE)
    fun isPending(ctx: Context) = prefs(ctx).getString(KEY, null) != CURRENT
    fun markSeen(ctx: Context) = prefs(ctx).edit().putString(KEY, CURRENT).apply()

    val items: List<Pair<String, ImageVector>> = listOf(
        "comments" to Icons.Outlined.Forum,
        "audience" to Icons.Outlined.StarOutline,
        "requests" to Icons.Outlined.PersonAddAlt,
        "connect" to Icons.Outlined.People,
        "once" to Icons.Outlined.Visibility,
        "dark" to Icons.Outlined.DarkMode,
        "gestures" to Icons.Outlined.Swipe,
    )
}

@Composable
fun WhatsNewSheet(onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Column(Modifier.padding(top = 8.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EyeLogo(28.dp, color = Seeli.Green)
                Text(t("whatsnew.title"), style = ts(30, FontWeight.SemiBold, Seeli.Ink))
                Text(t("whatsnew.subtitle"), style = ts(14, color = Seeli.Muted))
            }
            WhatsNew.items.forEach { (id, icon) ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Seeli.Card), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = Seeli.Green, modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(t("whatsnew.$id.title"), style = ts(16, FontWeight.SemiBold, Seeli.Ink))
                        Text(t("whatsnew.$id.body"), style = ts(14, color = Seeli.Muted))
                    }
                }
            }
        }
        PrimaryButton(t("whatsnew.done"), modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp), onClick = onDone)
    }
}
