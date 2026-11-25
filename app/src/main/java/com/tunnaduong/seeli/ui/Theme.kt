package com.tunnaduong.seeli.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Seeli {
    val BG = Color(0xFFF6F5F0)
    val Card = Color(0xFFECEBE4)
    val Surface = Color(0xFFFFFFFE)
    val Green = Color(0xFF5F6B4E)
    val GreenSoft = Color(0xFFA9B79C)
    val GreenPale = Color(0xFFC9D3BE)
    val Ink = Color(0xFF2F302B)
    val Muted = Color(0xFF8B8C84)
    val Line = Color(0xFFE2E1DA)
    val Sun = Color(0xFFF5D38A)
    val Night = Color(0xFF1F211D)
    val Error = Color(0xFFB0574A)

    /** Mincho / serif for "words": memory notes, vertical text. */
    val Mincho = FontFamily.Serif
}

fun ts(size: Int, weight: FontWeight = FontWeight.Normal, color: Color = Color.Unspecified, tracking: TextUnit = TextUnit.Unspecified, family: FontFamily? = null) =
    TextStyle(fontSize = size.sp, fontWeight = weight, color = color, letterSpacing = tracking, fontFamily = family)

@Composable
fun SeeliTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Seeli.Green, onPrimary = Color.White, background = Seeli.BG, surface = Seeli.BG,
            onSurface = Seeli.Ink, onBackground = Seeli.Ink, secondary = Seeli.Green,
            surfaceContainerHigh = Seeli.BG, surfaceContainerLow = Seeli.BG, surfaceContainer = Seeli.BG,
        ),
    ) {
        CompositionLocalProvider(LocalContentColor provides Seeli.Ink) { content() }
    }
}

/** `.seeliCard()` — rounded paper card with a hairline border. */
fun Modifier.seeliCard(color: Color = Seeli.Surface, radius: Dp = 20.dp, border: Boolean = true): Modifier {
    val shape = RoundedCornerShape(radius)
    return this.clip(shape).background(color, shape).then(if (border) Modifier.border(1.dp, Seeli.Line, shape) else Modifier)
}

/** Click without the Material ripple, like a SwiftUI plain button. */
@Composable
fun Modifier.tap(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)

@Composable
fun PrimaryButton(
    text: String? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: (@Composable () -> Unit)? = null,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .scale(if (pressed) 0.99f else 1f)
            .alpha(if (pressed) 0.85f else 1f)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) Seeli.Green else Seeli.GreenSoft)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            ProvideTextStyle(ts(16, FontWeight.SemiBold, Color.White)) {
                if (content != null) content() else androidx.compose.material3.Text(text ?: "")
            }
        }
    }
}

@Composable
fun SoftButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier
            .fillMaxWidth()
            .height(50.dp)
            .alpha(if (pressed) 0.7f else 1f)
            .clip(RoundedCornerShape(16.dp))
            .background(Seeli.Card)
            .clickable(source, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text(text, style = ts(15, FontWeight.Medium, Seeli.Green))
    }
}
