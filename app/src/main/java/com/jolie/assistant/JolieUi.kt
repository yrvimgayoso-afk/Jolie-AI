package com.jolie.assistant

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object JolieColors {
    val Red = Color(0xFFD71921)
    val RedSoft = Color(0xFFFF6B70)
    val Gray = Color(0xFFB8B8B8)
}

/** Testo nella lingua scelta (IT o RU). */
fun tr(lang: String, itText: String, ruText: String): String =
    if (lang == "RU") ruText else itText

@Composable
fun JolieTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = JolieColors.Red,
            background = Color.Black,
            surface = Color(0xFF111111),
            onBackground = Color.White,
            onSurface = Color.White
        ),
        content = content
    )
}

/** Sfondo nero con griglia di puntini e un bagliore rosso, stile Nothing OS. */
@Composable
fun JolieBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .drawBehind {
                val step = 14.dp.toPx()
                var y = 0f
                while (y < size.height) {
                    var x = 0f
                    while (x < size.width) {
                        drawCircle(Color.White.copy(alpha = 0.14f), 1.1f, Offset(x, y))
                        x += step
                    }
                    y += step
                }
                val glowCenter = Offset(size.width * 0.15f, size.height * 0.12f)
                val glowRadius = size.width * 0.9f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(JolieColors.Red.copy(alpha = 0.45f), Color.Transparent),
                        center = glowCenter,
                        radius = glowRadius
                    ),
                    radius = glowRadius,
                    center = glowCenter
                )
            },
        content = content
    )
}

/** "Vetro" simulato: riempimento trasparente + bordo luminoso. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.06f))
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.10f))
                ),
                shape
            ),
        content = content
    )
}

@Composable
fun MonoText(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 13,
    color: Color = Color.White,
    bold: Boolean = false,
    spacing: Float = 0f
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        letterSpacing = spacing.sp,
        lineHeight = (size * 1.5f).sp
    )
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false
) {
    val shape = RoundedCornerShape(24.dp)
    if (filled) {
        Box(
            modifier = modifier
                .heightIn(min = 48.dp)
                .clip(shape)
                .background(Color.White)
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            MonoText(text, size = 12, color = Color.Black, bold = true, spacing = 1f)
        }
    } else {
        GlassSurface(modifier = modifier, shape = shape) {
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                MonoText(text, size = 12, bold = true, spacing = 1f)
            }
        }
    }
}

/** L'orbe a puntini di Jolie. */
@Composable
fun Orb(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val step = 10.dp.toPx()
        val dot = 1.6.dp.toPx()
        val c = center
        var y = c.y - r
        while (y <= c.y + r) {
            var x = c.x - r
            while (x <= c.x + r) {
                val dx = x - c.x
                val dy = y - c.y
                if (dx * dx + dy * dy <= r * r) drawCircle(Color.White, dot, Offset(x, y))
                x += step
            }
            y += step
        }
        drawCircle(JolieColors.Red, r * 0.2f, c)
    }
}
