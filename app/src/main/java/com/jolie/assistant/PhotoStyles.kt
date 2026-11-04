package com.jolie.assistant

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.random.Random

/** group: 0 = pittura, 1 = pixel art, 2 = pop art. aiPrompt serve quando lo stile non si può fare in locale. */
class StyleDef(val id: String, val itName: String, val ruName: String, val group: Int, val aiPrompt: String)

val STYLES = ANIME_STYLES + listOf(
    StyleDef(
        "watercolor", "ACQUERELLO", "АКВАРЕЛЬ", 0,
        "Transform this photo into a delicate watercolor painting with soft washes, visible paper texture and gentle color bleeding. Keep the composition and the people recognizable."
    ),
    StyleDef(
        "oil", "OLIO", "МАСЛО", 0,
        "Transform this photo into a classical oil painting with rich colors and visible brush strokes. Keep the composition and the people recognizable."
    ),
    StyleDef(
        "impression", "IMPRESSIONISMO", "ИМПРЕССИОНИЗМ", 0,
        "Repaint this photo in the style of Van Gogh's post-impressionism, with thick swirling brush strokes and vivid colors. Keep the composition recognizable."
    ),
    StyleDef(
        "surreal", "SURREALISMO · IA", "СЮРРЕАЛИЗМ · ИИ", 0,
        "Transform this photo into a surrealist painting with dreamlike elements, soft melting shapes and a mysterious atmosphere. Keep the main subject recognizable."
    ),
    StyleDef(
        "pixel8", "8 BIT", "8 БИТ", 1,
        "Convert this photo into 8-bit pixel art with large visible pixels and a very limited color palette."
    ),
    StyleDef(
        "pixel16", "16 BIT", "16 БИТ", 1,
        "Convert this photo into 16-bit pixel art with defined edges and a reduced color palette."
    ),
    StyleDef(
        "pop60", "POP ART 60s", "ПОП-АРТ 60-Х", 2,
        "Convert this photo into 1960s pop art with flat saturated colors, bold black outlines and a comic book look."
    ),
    StyleDef(
        "pop80", "POP NEON", "ПОП-АРТ НЕОН", 2,
        "Convert this photo into 1980s neon pop art with flat saturated magenta, cyan and yellow colors."
    )
)

/** Applica lo stile sul telefono (gratis, anche senza internet). Restituisce null se serve l'IA online. */
fun applyLocalStyle(src: Bitmap, id: String): Bitmap? {
    return when (id) {
        "watercolor" -> noise(tone(kuwahara(scaleDown(src, 1000), 3), 8f, -8f, 10f), 5)
        "oil" -> tone(kuwahara(scaleDown(src, 1000), 5), 0f, 10f, 25f)
        "impression" -> noise(tone(kuwahara(scaleDown(src, 1000), 7), 3f, 15f, 40f), 6)
        "pixel8" -> pixelArt(src, 96, 4)
        "pixel16" -> pixelArt(src, 192, 6)
        "pop60" -> popArt(src, false)
        "pop80" -> popArt(src, true)
        "anime" -> animeStyle(src, false)
        "manga" -> animeStyle(src, true)
        else -> null
    }
}

private fun tone(b: Bitmap, brightness: Float, contrast: Float, saturation: Float): Bitmap =
    applyMatrix(b, buildMatrix(null, brightness, contrast, saturation, 0f))

private fun clamp255(v: Int): Int = if (v < 0) 0 else if (v > 255) 255 else v

private fun noise(src: Bitmap, amp: Int): Bitmap {
    val w = src.width
    val h = src.height
    val px = IntArray(w * h)
    src.getPixels(px, 0, w, 0, 0, w, h)
    val rnd = Random(7)
    for (i in px.indices) {
        val d = rnd.nextInt(-amp, amp + 1)
        val c = px[i]
        px[i] = Color.rgb(
            clamp255(((c shr 16) and 255) + d),
            clamp255(((c shr 8) and 255) + d),
            clamp255((c and 255) + d)
        )
    }
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    out.setPixels(px, 0, w, 0, 0, w, h)
    return out
}

/** Filtro Kuwahara: appiattisce i colori in "pennellate", base di olio e acquerello. */
private fun kuwahara(src: Bitmap, r: Int): Bitmap {
    val w = src.width
    val h = src.height
    val px = IntArray(w * h)
    src.getPixels(px, 0, w, 0, 0, w, h)
    val ww = w + 1
    val size = ww * (h + 1)
    val sr = IntArray(size)
    val sg = IntArray(size)
    val sb = IntArray(size)
    val sl = IntArray(size)
    val sl2 = LongArray(size)
    for (y in 0 until h) {
        var rr = 0
        var gg = 0
        var bb = 0
        var ll = 0
        var ll2 = 0L
        for (x in 0 until w) {
            val c = px[y * w + x]
            val r0 = (c shr 16) and 255
            val g0 = (c shr 8) and 255
            val b0 = c and 255
            val l = (r0 * 299 + g0 * 587 + b0 * 114) / 1000
            rr += r0
            gg += g0
            bb += b0
            ll += l
            ll2 += (l * l).toLong()
            val i = (y + 1) * ww + (x + 1)
            val up = y * ww + (x + 1)
            sr[i] = sr[up] + rr
            sg[i] = sg[up] + gg
            sb[i] = sb[up] + bb
            sl[i] = sl[up] + ll
            sl2[i] = sl2[up] + ll2
        }
    }
    val out = IntArray(w * h)
    for (y in 0 until h) {
        for (x in 0 until w) {
            var bestVar = Double.MAX_VALUE
            var bR = 0
            var bG = 0
            var bB = 0
            for (q in 0..3) {
                val x0 = if (q % 2 == 0) x - r else x
                val x1 = if (q % 2 == 0) x + 1 else x + r + 1
                val y0 = if (q < 2) y - r else y
                val y1 = if (q < 2) y + 1 else y + r + 1
                val cx0 = x0.coerceAtLeast(0)
                val cx1 = x1.coerceAtMost(w)
                val cy0 = y0.coerceAtLeast(0)
                val cy1 = y1.coerceAtMost(h)
                val n = (cx1 - cx0) * (cy1 - cy0)
                if (n <= 0) continue
                val a = cy1 * ww + cx1
                val b = cy0 * ww + cx1
                val c = cy1 * ww + cx0
                val d = cy0 * ww + cx0
                val sumL = (sl[a] - sl[b] - sl[c] + sl[d]).toDouble()
                val sumL2 = (sl2[a] - sl2[b] - sl2[c] + sl2[d]).toDouble()
                val mean = sumL / n
                val variance = sumL2 / n - mean * mean
                if (variance < bestVar) {
                    bestVar = variance
                    bR = (sr[a] - sr[b] - sr[c] + sr[d]) / n
                    bG = (sg[a] - sg[b] - sg[c] + sg[d]) / n
                    bB = (sb[a] - sb[b] - sb[c] + sb[d]) / n
                }
            }
            out[y * w + x] = Color.rgb(bR, bG, bB)
        }
    }
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    bmp.setPixels(out, 0, w, 0, 0, w, h)
    return bmp
}

/** Pixel art: riduce l'immagine, limita i colori e la ingrandisce senza sfumature. */
private fun pixelArt(src: Bitmap, cells: Int, levels: Int): Bitmap {
    val w = src.width
    val h = src.height
    val cw = cells
    val ch = (cells.toFloat() * h / w).toInt().coerceAtLeast(1)
    val small = Bitmap.createScaledBitmap(src, cw, ch, true)
    val px = IntArray(cw * ch)
    small.getPixels(px, 0, cw, 0, 0, cw, ch)
    val step = 255f / (levels - 1)
    for (i in px.indices) {
        val c = px[i]
        px[i] = Color.rgb(
            quant((c shr 16) and 255, step),
            quant((c shr 8) and 255, step),
            quant(c and 255, step)
        )
    }
    val tiny = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
    tiny.setPixels(px, 0, cw, 0, 0, cw, ch)
    return Bitmap.createScaledBitmap(tiny, w, h, false)
}

private fun quant(v: Int, step: Float): Int = clamp255((Math.round(v / step) * step).toInt())

private fun level3(v: Int): Int = if (v < 85) 30 else if (v < 170) 140 else 245

/** Pop art: colori piatti e saturi con contorni neri (classico) oppure palette al neon. */
private fun popArt(src: Bitmap, neon: Boolean): Bitmap {
    val smooth = kuwahara(scaleDown(src, 1000), 2)
    val base = if (neon) smooth else applyMatrix(smooth, buildMatrix(null, 0f, 25f, 70f, 0f))
    val w = base.width
    val h = base.height
    val px = IntArray(w * h)
    base.getPixels(px, 0, w, 0, 0, w, h)
    val lum = IntArray(w * h)
    for (i in px.indices) {
        val c = px[i]
        lum[i] = (((c shr 16) and 255) * 299 + ((c shr 8) and 255) * 587 + (c and 255) * 114) / 1000
    }
    val palette = intArrayOf(
        Color.rgb(25, 10, 60),
        Color.rgb(240, 20, 150),
        Color.rgb(0, 200, 255),
        Color.rgb(255, 225, 40),
        Color.rgb(255, 255, 255)
    )
    val out = IntArray(w * h)
    for (i in px.indices) {
        if (neon) {
            out[i] = palette[(lum[i] * 5 / 256).coerceAtMost(4)]
        } else {
            val c = px[i]
            out[i] = Color.rgb(level3((c shr 16) and 255), level3((c shr 8) and 255), level3(c and 255))
        }
    }
    for (y in 1 until h - 1) {
        for (x in 1 until w - 1) {
            val i = y * w + x
            val gx = -lum[i - w - 1] - 2 * lum[i - 1] - lum[i + w - 1] + lum[i - w + 1] + 2 * lum[i + 1] + lum[i + w + 1]
            val gy = -lum[i - w - 1] - 2 * lum[i - w] - lum[i - w + 1] + lum[i + w - 1] + 2 * lum[i + w] + lum[i + w + 1]
            if (abs(gx) + abs(gy) > 160) out[i] = Color.BLACK
        }
    }
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    bmp.setPixels(out, 0, w, 0, 0, w, h)
    return bmp
}
