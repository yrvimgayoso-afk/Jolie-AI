package com.jolie.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Tarjetas y mensajes PLANOS: cuando se llama GlassSurface(modifier) { ... } solo con un modificador,
 * Kotlin elige esta versión (sin parámetros por defecto). El cristal queda solo para botones y barras.
 */
@Composable
fun GlassSurface(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF181818))
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape),
        content = content
    )
}
