package com.tunnaduong.seeli.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tunnaduong.seeli.core.AppLanguage
import com.tunnaduong.seeli.core.L10n
import com.tunnaduong.seeli.core.isMostlyCJK

/** Soft hill illustration used as the visual for friends' moments. Same drawing as Landscape.swift. */
@Composable
fun Landscape(scene: Int = 0, modifier: Modifier = Modifier) {
    val (sky, sun, hills) = when (scene % 3) {
        1 -> Triple(Color(0xFFE9E4D8), Color(0xFFF2C48D), listOf(Color(0xFFB9C4B6), Color(0xFF8FA394), Color(0xFF6B7F72)))
        2 -> Triple(Color(0xFFDDE3E6), Color(0xFFEBDDB8), listOf(Color(0xFFA7B3B5), Color(0xFF86969A), Color(0xFF5F6E72)))
        else -> Triple(Color(0xFFDCE4DA), Seeli.Sun, listOf(Color(0xFFA9B99E), Color(0xFF86997A), Color(0xFF5F6E52)))
    }
    Canvas(modifier.fillMaxSize().clipToBounds()) {
        val w = size.width
        val h = size.height
        drawRect(sky)
        drawCircle(sun, radius = w * 0.1f, center = Offset(w * (if (scene % 2 == 0) 0.74f else 0.26f), h * 0.27f))
        hill(0.42f, 0.55f, hills[0])
        hill(0.58f, 0.25f, hills[1])
        hill(0.76f, 0.8f, hills[2])
        val wind = Path().apply {
            moveTo(w * 0.1f, h * 0.7f); quadraticTo(w * 0.13f, h * 0.57f, w * 0.22f, h * 0.52f)
            moveTo(w * 0.17f, h * 0.72f); quadraticTo(w * 0.2f, h * 0.6f, w * 0.27f, h * 0.56f)
        }
        drawPath(wind, Color.White.copy(alpha = 0.6f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
        // Tree, in a frame of w*0.08 × h*0.2 centred at (0.78w, 0.63h)
        val tw = w * 0.08f
        val th = h * 0.2f
        val left = w * 0.78f - tw / 2
        val top = h * 0.63f - th / 2
        val midX = left + tw / 2
        val cy = top + th * 0.3f
        val r = tw * 0.45f
        val tree = Path().apply {
            moveTo(midX, top + th * 0.25f); lineTo(midX, top + th)
            moveTo(midX - r, cy + r * 0.35f); quadraticTo(midX, cy - r * 1.6f, midX + r, cy + r * 0.35f)
            moveTo(left + tw * 0.15f, top + th * 0.55f); lineTo(left + tw * 0.85f, top + th * 0.55f)
            moveTo(left + tw * 0.15f, top + th * 0.72f); lineTo(left + tw * 0.85f, top + th * 0.72f)
        }
        drawPath(tree, hills[2].copy(alpha = 0.9f), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

private fun DrawScope.hill(peak: Float, phase: Float, color: Color) {
    val w = size.width
    val h = size.height
    val base = h * peak
    val p = Path().apply {
        moveTo(0f, base + h * 0.08f)
        cubicTo(w * phase * 0.6f, base - h * 0.22f, w * (0.4f + phase * 0.5f), base + h * 0.2f, w, base)
        lineTo(w, h); lineTo(0f, h); close()
    }
    drawPath(p, color)
}

// ---------------------------------------------------------------- Vertical text (tategaki)

private val rotated = setOf('ー', '―', '—', '…', '〜', '~', '-', '（', '）', '(', ')', '「', '」', '『', '』', '【', '】', '：', ':', '・')
private val smallPunct = setOf('、', '。', '，', '．', ',', '.')
private val smallKana = setOf('ゃ', 'ゅ', 'ょ', 'っ', 'ぁ', 'ぃ', 'ぅ', 'ぇ', 'ぉ', 'ャ', 'ュ', 'ョ', 'ッ', 'ァ', 'ィ', 'ゥ', 'ェ', 'ォ')

/** Japanese vertical writing: columns flow top→bottom, right→left. */
@Composable
fun VerticalText(
    text: String,
    size: Int = 16,
    perColumn: Int = 8,
    family: FontFamily = Seeli.Mincho,
    color: Color = Seeli.Ink,
    columnSpacing: Dp? = null,
) {
    val columns = buildList {
        for (line in text.split("\n")) {
            val chars = line.filter { it != ' ' && it != '　' }.toList()
            if (chars.isEmpty()) { add(emptyList()); continue }
            var i = 0
            while (i < chars.size) {
                var end = minOf(i + maxOf(1, perColumn), chars.size)
                // Kinsoku: keep punctuation from starting a column.
                if (end < chars.size && chars[end] in smallPunct) end += 1
                add(chars.subList(i, end))
                i = end
            }
        }
    }
    val cell = (size * 1.1f).dp
    Row(
        Modifier.semantics { contentDescription = text },
        horizontalArrangement = Arrangement.spacedBy(columnSpacing ?: (size * 0.7f).dp),
        verticalAlignment = Alignment.Top,
    ) {
        columns.reversed().forEach { column ->
            Column(Modifier.width(cell), verticalArrangement = Arrangement.spacedBy((size * 0.12f).dp)) {
                column.forEach { ch ->
                    val mod = when (ch) {
                        in rotated -> Modifier.rotate(90f)
                        in smallPunct -> Modifier.offset(x = (size * 0.35f).dp, y = (-size * 0.35f).dp)
                        in smallKana -> Modifier.offset(x = (size * 0.1f).dp, y = (-size * 0.1f).dp)
                        else -> Modifier
                    }
                    Box(Modifier.size(cell).then(mod), contentAlignment = Alignment.Center) {
                        Text(ch.toString(), style = ts(size, color = color, family = family).copy(lineHeight = size.sp))
                    }
                }
            }
        }
    }
}

/** A note vertically when it's Japanese, otherwise a quiet serif italic paragraph. */
@Composable
fun NoteText(text: String, size: Int = 16, perColumn: Int = 8, align: TextAlign = TextAlign.Start, modifier: Modifier = Modifier) {
    if (text.isMostlyCJK) {
        Box(modifier) { VerticalText(text, size, perColumn) }
    } else {
        Text(
            text,
            modifier = modifier,
            style = ts(size + 1, color = Seeli.Ink, family = FontFamily.Serif).copy(fontStyle = FontStyle.Italic, lineHeight = (size + 7).sp),
            textAlign = align,
        )
    }
}

/** Small metadata (date, place) — vertical in Japanese, horizontal in Vietnamese. */
@Composable
fun MetaText(text: String, size: Int = 11, perColumn: Int = 40) {
    if (L10n.language == AppLanguage.JA && text.isMostlyCJK) {
        VerticalText(text, size, perColumn, FontFamily.Default, Seeli.Muted)
    } else {
        Text(text, style = ts(size + 1, color = Seeli.Muted))
    }
}
