package com.jolie.assistant

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

/** Estilos anime y manga (grupo 2, junto al pop art). ANIME usa IA gratuita en el teléfono. */
val ANIME_STYLES = listOf(
    StyleDef(
        "anime", "ANIME IA", "АНИМЕ ИИ", 2,
        "Convert this photo into a clean anime illustration with flat cel-shaded colors, soft highlights and thin dark outlines. Keep the person recognizable."
    ),
    StyleDef(
        "manga", "MANGA B/N", "МАНГА Ч/Б", 2,
        "Convert this photo into a black and white manga illustration with ink outlines and screentone shading."
    )
)

private fun cel(v: Int): Int = Math.round(v / 51f) * 51

/** Anime: primero IA en el teléfono (AnimeGANv2); si falla, efecto local. Manga: siempre local. */
fun animeStyle(src: Bitmap, manga: Boolean): Bitmap {
    if (!manga) {
        val ai = AnimeAi.convert(src)
        if (ai != null) return ai
    }
    return localAnime(src, manga)
}

/** Efecto local (sin IA): colores planos por niveles con contornos, o tramas de puntos en blanco y negro. */
private fun localAnime(src: Bitmap, manga: Boolean): Bitmap {
    val base = scaleDown(src, 1100)
    val w = base.width
    val h = base.height
    val small = Bitmap.createScaledBitmap(base, (w / 3).coerceAtLeast(1), (h / 3).coerceAtLeast(1), true)
    val soft0 = Bitmap.createScaledBitmap(small, w, h, true)
    val soft = if (manga) soft0 else applyMatrix(soft0, buildMatrix(null, 4f, 10f, 35f, 0f))
    val px = IntArray(w * h)
    soft.getPixels(px, 0, w, 0, 0, w, h)
    val lum = IntArray(w * h)
    for (i in px.indices) {
        val c = px[i]
        lum[i] = (((c shr 16) and 255) * 299 + ((c shr 8) and 255) * 587 + (c and 255) * 114) / 1000
    }
    val out = IntArray(w * h)
    for (y in 0 until h) {
        for (x in 0 until w) {
            val i = y * w + x
            if (manga) {
                val dark = (255 - lum[i]) / 255f
                val dx = (x % 4) - 1.5f
                val dy = (y % 4) - 1.5f
                out[i] = if (dx * dx + dy * dy < dark * 5f) Color.BLACK else Color.WHITE
            } else {
                val c = px[i]
                out[i] = Color.rgb(cel((c shr 16) and 255), cel((c shr 8) and 255), cel(c and 255))
            }
        }
    }
    val threshold = if (manga) 120 else 70
    for (y in 1 until h - 1) {
        for (x in 1 until w - 1) {
            val i = y * w + x
            val gx = -lum[i - w - 1] - 2 * lum[i - 1] - lum[i + w - 1] + lum[i - w + 1] + 2 * lum[i + 1] + lum[i + w + 1]
            val gy = -lum[i - w - 1] - 2 * lum[i - w] - lum[i - w + 1] + lum[i + w - 1] + 2 * lum[i + w] + lum[i + w + 1]
            if (abs(gx) + abs(gy) > threshold) out[i] = if (manga) Color.BLACK else Color.rgb(28, 22, 38)
        }
    }
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    bmp.setPixels(out, 0, w, 0, 0, w, h)
    return bmp
}
