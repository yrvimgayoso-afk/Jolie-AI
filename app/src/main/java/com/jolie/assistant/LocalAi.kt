package com.jolie.assistant

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.File
import java.net.URL
import java.nio.FloatBuffer

/**
 * Edición de fotos con IA GRATUITA que corre en el teléfono (sin claves ni internet, salvo la primera descarga del modelo).
 * Entiende frases como "quita el fondo", "desenfoca el fondo", "mejora la luz", "dibuja como anime", "pixel art"...
 */
object LocalAi {
    private const val BG_FILE = "u2netp.onnx"
    private val BG_URLS = listOf(
        "https://github.com/danielgatis/rembg/releases/download/v0.0.0/u2netp.onnx"
    )

    private fun appContext(): Context? = try {
        Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as? Application
    } catch (_: Throwable) {
        null
    }

    private fun toast(ctx: Context, text: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(ctx, text, Toast.LENGTH_LONG).show() }
    }

    /** Devuelve la foto editada, o null si la orden no se puede hacer en el teléfono (entonces se prueba Gemini). */
    fun tryEdit(src: Bitmap, prompt: String): Bitmap? {
        val p = prompt.lowercase()
        fun has(vararg w: String): Boolean = w.any { p.contains(it) }
        if (has("remove the people", "rimuovi le persone", "togli le persone", "elimina le persone")) return null
        return when {
            has("manga") -> animeStyle(src, true)
            has("anime", "cartoon", "cartone") -> animeStyle(src, false)
            has("pixel") -> applyLocalStyle(src, if (has("16")) "pixel16" else "pixel8")
            has("pop art", "pop-art") -> applyLocalStyle(src, if (has("neon")) "pop80" else "pop60")
            has("watercolor", "acquerell") -> applyLocalStyle(src, "watercolor")
            has("oil paint", "olio") -> applyLocalStyle(src, "oil")
            has("van gogh", "impression") -> applyLocalStyle(src, "impression")
            has("background", "sfondo") && has("blur", "sfoc", "bokeh", "desenfoc") -> backgroundEffect(src, true)
            has("background", "sfondo") -> backgroundEffect(src, false)
            has("lighting", "luce", "migliora", "enhance", "brighten", "colori", "colors") -> enhance(src)
            else -> null
        }
    }

    private fun ensureModel(ctx: Context, name: String, urls: List<String>): File? {
        val f = File(ctx.filesDir, name)
        if (f.exists() && f.length() > 1000000L) return f
        toast(ctx, "Scarico il modello IA (circa 5 MB)…")
        var err = ""
        for (u in urls) {
            try {
                val c = JolieProxy.openConnection(URL(u))
                c.connectTimeout = 15000
                c.readTimeout = 30000
                if (c.responseCode !in 200..299) {
                    err = "HTTP " + c.responseCode
                    c.disconnect()
                    continue
                }
                val tmp = File(ctx.filesDir, "$name.part")
                c.inputStream.use { input -> tmp.outputStream().use { out -> input.copyTo(out) } }
                c.disconnect()
                if (tmp.length() > 1000000L) {
                    tmp.renameTo(f)
                    return f
                }
                err = "file troppo piccolo"
                tmp.delete()
            } catch (e: Exception) {
                err = e.javaClass.simpleName
            }
        }
        toast(ctx, "Modello non scaricato ($err).")
        return null
    }

    /** Máscara del sujeto (blanco = sujeto) con U²-Net, a tamaño de la foto. */
    private fun subjectMask(file: File, src: Bitmap): Bitmap {
        val env = OrtEnvironment.getEnvironment()
        return env.createSession(file.absolutePath, OrtSession.SessionOptions()).use { session ->
            val name = session.inputNames.iterator().next()
            val sz = 320
            val small = Bitmap.createScaledBitmap(src, sz, sz, true)
            val px = IntArray(sz * sz)
            small.getPixels(px, 0, sz, 0, 0, sz, sz)
            var maxV = 1
            for (c in px) {
                maxV = maxOf(maxV, (c shr 16) and 255, (c shr 8) and 255, c and 255)
            }
            val n = sz * sz
            val data = FloatArray(3 * n)
            val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
            val std = floatArrayOf(0.229f, 0.224f, 0.225f)
            for (i in 0 until n) {
                val c = px[i]
                data[i] = (((c shr 16) and 255) / maxV.toFloat() - mean[0]) / std[0]
                data[n + i] = (((c shr 8) and 255) / maxV.toFloat() - mean[1]) / std[1]
                data[2 * n + i] = ((c and 255) / maxV.toFloat() - mean[2]) / std[2]
            }
            OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1, 3, sz.toLong(), sz.toLong())).use { t ->
                session.run(mapOf(name to t)).use { res ->
                    val fb = (res.get(0) as OnnxTensor).floatBuffer
                    var mn = Float.MAX_VALUE
                    var mx = -Float.MAX_VALUE
                    for (i in 0 until n) {
                        val v = fb.get(i)
                        if (v < mn) mn = v
                        if (v > mx) mx = v
                    }
                    val range = if (mx - mn < 1e-6f) 1f else mx - mn
                    val out = IntArray(n)
                    for (i in 0 until n) {
                        val a = ((fb.get(i) - mn) / range * 255f).toInt().coerceIn(0, 255)
                        out[i] = (0xFF shl 24) or (a shl 16) or (a shl 8) or a
                    }
                    val m = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888)
                    m.setPixels(out, 0, sz, 0, 0, sz, sz)
                    Bitmap.createScaledBitmap(m, src.width, src.height, true)
                }
            }
        }
    }

    /** Quita el fondo (lo pone blanco) o lo desenfoca, dejando nítido al sujeto. */
    private fun backgroundEffect(src: Bitmap, blur: Boolean): Bitmap? {
        val ctx = appContext() ?: return null
        val file = ensureModel(ctx, BG_FILE, BG_URLS) ?: return null
        return try {
            val w = src.width
            val h = src.height
            val mask = subjectMask(file, src)
            val spx = IntArray(w * h)
            src.getPixels(spx, 0, w, 0, 0, w, h)
            val mpx = IntArray(w * h)
            mask.getPixels(mpx, 0, w, 0, 0, w, h)
            val bgpx = IntArray(w * h)
            if (blur) {
                val small = Bitmap.createScaledBitmap(src, (w / 16).coerceAtLeast(1), (h / 16).coerceAtLeast(1), true)
                Bitmap.createScaledBitmap(small, w, h, true).getPixels(bgpx, 0, w, 0, 0, w, h)
            } else {
                java.util.Arrays.fill(bgpx, -1)
            }
            val out = IntArray(w * h)
            for (i in 0 until w * h) {
                val a0 = (mpx[i] and 255) / 255f
                val a = ((a0 - 0.3f) / 0.4f).coerceIn(0f, 1f)
                val s = spx[i]
                val b = bgpx[i]
                val r = (((s shr 16) and 255) * a + ((b shr 16) and 255) * (1f - a)).toInt()
                val g = (((s shr 8) and 255) * a + ((b shr 8) and 255) * (1f - a)).toInt()
                val bl = ((s and 255) * a + (b and 255) * (1f - a)).toInt()
                out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
            }
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.setPixels(out, 0, w, 0, 0, w, h)
            bmp
        } catch (e: Throwable) {
            toast(ctx, "IA fondo non riuscita (" + e.javaClass.simpleName + ").")
            null
        }
    }

    /** Auto-mejora: estira luces y sombras y sube un poco el color. */
    private fun enhance(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val hist = IntArray(256)
        for (c in px) {
            val l = (((c shr 16) and 255) * 299 + ((c shr 8) and 255) * 587 + (c and 255) * 114) / 1000
            hist[l]++
        }
        val total = w * h
        var lo = 0
        var acc = 0
        while (lo < 255 && acc + hist[lo] < total * 0.01) {
            acc += hist[lo]
            lo++
        }
        var hi = 255
        acc = 0
        while (hi > 0 && acc + hist[hi] < total * 0.01) {
            acc += hist[hi]
            hi--
        }
        if (hi - lo < 40) {
            lo = 0
            hi = 255
        }
        val s = (255f / (hi - lo)).coerceAtMost(1.6f)
        val m = ColorMatrix()
        m.setSaturation(1.15f)
        m.postConcat(
            ColorMatrix(
                floatArrayOf(
                    s, 0f, 0f, 0f, -lo * s,
                    0f, s, 0f, 0f, -lo * s,
                    0f, 0f, s, 0f, -lo * s,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        return applyMatrix(src, m.array)
    }
}
