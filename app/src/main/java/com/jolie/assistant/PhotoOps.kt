package com.jolie.assistant

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.hypot

const val MAX_SIDE = 1440

/** La foto in modifica: resta in memoria anche cambiando scheda. */
object PhotoStore {
    var base by mutableStateOf<Bitmap?>(null)
        private set
    private val undo = mutableListOf<Bitmap>()

    fun load(b: Bitmap) {
        undo.clear()
        base = b
    }

    fun set(b: Bitmap) {
        val cur = base
        if (cur != null) {
            undo.add(cur)
            if (undo.size > 5) undo.removeAt(0)
        }
        base = b
    }

    fun undoLast(): Boolean {
        if (undo.isEmpty()) return false
        base = undo.removeAt(undo.size - 1)
        return true
    }
}

fun scaleDown(src: Bitmap, maxSide: Int): Bitmap {
    val longest = maxOf(src.width, src.height)
    if (longest <= maxSide) return src
    val s = maxSide.toFloat() / longest
    return Bitmap.createScaledBitmap(
        src,
        (src.width * s).toInt().coerceAtLeast(1),
        (src.height * s).toInt().coerceAtLeast(1),
        true
    )
}

fun loadBitmap(context: Context, uri: Uri, maxSide: Int = MAX_SIDE): Bitmap? {
    return try {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > maxSide * 2 || bounds.outHeight / sample > maxSide * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val orientation = cr.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
        }
        val fixed = if (m.isIdentity) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        scaleDown(fixed, maxSide)
    } catch (_: Throwable) {
        null
    }
}

fun rotate90(src: Bitmap): Bitmap {
    val m = Matrix()
    m.postRotate(90f)
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}

fun flipH(src: Bitmap): Bitmap {
    val m = Matrix()
    m.postScale(-1f, 1f)
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}

/** Ritaglio al centro con un rapporto, per esempio 4:3. */
fun cropToRatio(src: Bitmap, rw: Int, rh: Int): Bitmap {
    val target = rw.toFloat() / rh
    val cur = src.width.toFloat() / src.height
    return if (cur > target) {
        val nw = (src.height * target).toInt().coerceIn(1, src.width)
        Bitmap.createBitmap(src, (src.width - nw) / 2, 0, nw, src.height)
    } else {
        val nh = (src.width / target).toInt().coerceIn(1, src.height)
        Bitmap.createBitmap(src, 0, (src.height - nh) / 2, src.width, nh)
    }
}

// ---------- Filtri e regolazioni ----------

class FilterPreset(val itName: String, val ruName: String, val matrix: FloatArray?)

private fun satM(s: Float): FloatArray {
    val m = ColorMatrix()
    m.setSaturation(s)
    return m.array.copyOf()
}

private fun contrastM(c: Float): FloatArray {
    val t = 128f * (1f - c)
    return floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f)
}

private fun diagM(r: Float, g: Float, b: Float): FloatArray =
    floatArrayOf(r, 0f, 0f, 0f, 0f, 0f, g, 0f, 0f, 0f, 0f, 0f, b, 0f, 0f, 0f, 0f, 0f, 1f, 0f)

private fun mul(a: FloatArray, b: FloatArray): FloatArray {
    val m = ColorMatrix(a)
    m.postConcat(ColorMatrix(b))
    return m.array.copyOf()
}

val PRESETS = listOf(
    FilterPreset("ORIGINALE", "ОРИГИНАЛ", null),
    FilterPreset("B/N", "Ч/Б", satM(0f)),
    FilterPreset(
        "SEPPIA", "СЕПИЯ",
        floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    FilterPreset("VIVIDO", "ЯРКИЙ", mul(satM(1.5f), contrastM(1.1f))),
    FilterPreset("FREDDO", "ХОЛОДНЫЙ", diagM(0.92f, 1f, 1.1f)),
    FilterPreset("CALDO", "ТЁПЛЫЙ", diagM(1.1f, 1f, 0.9f)),
    FilterPreset("DRAMMA", "ДРАМА", mul(satM(0.8f), contrastM(1.3f))),
    FilterPreset("VINTAGE", "ВИНТАЖ", mul(satM(0.6f), diagM(1.08f, 1f, 0.88f)))
)

/** Unisce filtro e regolazioni (luce, contrasto, colore, calore) in un'unica matrice colore. */
fun buildMatrix(preset: FloatArray?, brightness: Float, contrast: Float, saturation: Float, warmth: Float): FloatArray {
    val m = ColorMatrix()
    if (preset != null) m.postConcat(ColorMatrix(preset))
    m.postConcat(ColorMatrix(satM((1f + saturation / 100f).coerceAtLeast(0f))))
    val scale = 1f + contrast / 100f
    val t = 128f * (1f - scale) + brightness * 2.55f
    val w = warmth * 0.4f
    m.postConcat(
        ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, t + w,
                0f, scale, 0f, 0f, t,
                0f, 0f, scale, 0f, t - w,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
    return m.array.copyOf()
}

fun applyMatrix(src: Bitmap, values: FloatArray): Bitmap {
    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val paint = Paint()
    paint.colorFilter = ColorMatrixColorFilter(ColorMatrix(values))
    Canvas(out).drawBitmap(src, 0f, 0f, paint)
    return out
}

// ---------- Gomma magica (locale, senza IA) ----------

fun buildMask(w: Int, h: Int, strokes: List<List<Pair<Float, Float>>>, radius: Float): BooleanArray {
    val mask = BooleanArray(w * h)
    val r = radius.coerceAtLeast(2f)
    fun stamp(cx: Float, cy: Float) {
        val x0 = (cx - r).toInt().coerceAtLeast(0)
        val x1 = (cx + r).toInt().coerceAtMost(w - 1)
        val y0 = (cy - r).toInt().coerceAtLeast(0)
        val y1 = (cy + r).toInt().coerceAtMost(h - 1)
        for (y in y0..y1) {
            for (x in x0..x1) {
                val dx = x - cx
                val dy = y - cy
                if (dx * dx + dy * dy <= r * r) mask[y * w + x] = true
            }
        }
    }
    for (s in strokes) {
        for (i in s.indices) {
            val (x, y) = s[i]
            stamp(x, y)
            if (i > 0) {
                val (px, py) = s[i - 1]
                val steps = (hypot(x - px, y - py) / (r / 2f)).toInt()
                for (k in 1 until steps) {
                    val t = k.toFloat() / steps
                    stamp(px + (x - px) * t, py + (y - py) * t)
                }
            }
        }
    }
    return mask
}

/** Riempie la zona selezionata "a strati" con il colore dei pixel vicini, poi la ammorbidisce. */
fun inpaint(src: Bitmap, mask: BooleanArray): Bitmap {
    val w = src.width
    val h = src.height
    val px = IntArray(w * h)
    src.getPixels(px, 0, w, 0, 0, w, h)
    val known = BooleanArray(w * h) { !mask[it] }
    val maskIdx = ArrayList<Int>()
    for (i in mask.indices) if (mask[i]) maskIdx.add(i)

    var remaining = ArrayList<Int>(maskIdx)
    var guard = 0
    while (remaining.isNotEmpty() && guard < 500) {
        guard++
        val filled = ArrayList<Int>()
        val colors = ArrayList<Int>()
        val next = ArrayList<Int>()
        for (i in remaining) {
            val x = i % w
            val y = i / w
            var r = 0
            var g = 0
            var b = 0
            var n = 0
            for (dy in -1..1) {
                for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val j = ny * w + nx
                    if (known[j]) {
                        val c = px[j]
                        r += (c shr 16) and 255
                        g += (c shr 8) and 255
                        b += c and 255
                        n++
                    }
                }
            }
            if (n > 0) {
                filled.add(i)
                colors.add(Color.rgb(r / n, g / n, b / n))
            } else {
                next.add(i)
            }
        }
        if (filled.isEmpty()) break
        for (k in filled.indices) {
            px[filled[k]] = colors[k]
            known[filled[k]] = true
        }
        remaining = next
    }

    repeat(3) {
        val copy = px.copyOf()
        for (i in maskIdx) {
            val x = i % w
            val y = i / w
            var r = 0
            var g = 0
            var b = 0
            var n = 0
            for (dy in -1..1) {
                for (dx in -1..1) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val c = copy[ny * w + nx]
                    r += (c shr 16) and 255
                    g += (c shr 8) and 255
                    b += c and 255
                    n++
                }
            }
            px[i] = Color.rgb(r / n, g / n, b / n)
        }
    }

    val out = src.copy(Bitmap.Config.ARGB_8888, true)
    out.setPixels(px, 0, w, 0, 0, w, h)
    return out
}

fun saveToGallery(context: Context, bmp: Bitmap): Boolean {
    return try {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Jolie_" + System.currentTimeMillis() + ".jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Jolie")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false
        context.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        true
    } catch (_: Exception) {
        false
    }
}
