package com.jolie.assistant

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.File
import java.net.URL
import java.nio.FloatBuffer

/**
 * IA anime GRATUITA que se ejecuta en el propio teléfono (AnimeGANv2, estilo Hayao Miyazaki).
 * Descarga el modelo (unos 8 MB) la primera vez; luego funciona sin internet, también en Moscú.
 */
object AnimeAi {
    @Volatile
    var lastError = ""

    private const val MODEL_FILE = "AnimeGANv2_Hayao.onnx"
        private val URLS = listOf(
        "https://huggingface.co/vumichien/AnimeGANv2_Hayao/resolve/main/AnimeGANv2_Hayao.onnx",
        "https://hf-mirror.com/vumichien/AnimeGANv2_Hayao/resolve/main/AnimeGANv2_Hayao.onnx",
        "https://huggingface.co/frankminors123/AnimeGAN_Hayao_Style_ONNX/resolve/main/generator_hayao.onnx",
        "https://github.com/TachibanaYoshino/AnimeGANv2/raw/master/pb_and_onnx_model/AnimeGANv2_Hayao.onnx",
        "https://cdn.jsdelivr.net/gh/TachibanaYoshino/AnimeGANv2@master/pb_and_onnx_model/AnimeGANv2_Hayao.onnx",
        "https://raw.githubusercontent.com/TachibanaYoshino/AnimeGANv2/master/pb_and_onnx_model/AnimeGANv2_Hayao.onnx"
    )

    private fun appContext(): Context? = try {
        Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as? Application
    } catch (_: Throwable) {
        null
    }

    private fun toast(ctx: Context, text: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(ctx, text, Toast.LENGTH_LONG).show() }
    }

    private fun ensureModel(ctx: Context): File? {
        val f = File(ctx.filesDir, MODEL_FILE)
        if (f.exists() && f.length() > 1000000L) return f
        toast(ctx, "Scarico il modello IA anime (circa 8 MB)…")
        for (u in URLS) {
            try {
                val c = JolieProxy.openConnection(URL(u))
                c.connectTimeout = 15000
                c.readTimeout = 30000
                val code = c.responseCode
                if (code !in 200..299) {
                    lastError = "HTTP $code"
                    c.disconnect()
                    continue
                }
                val tmp = File(ctx.filesDir, "$MODEL_FILE.part")
                c.inputStream.use { input -> tmp.outputStream().use { out -> input.copyTo(out) } }
                c.disconnect()
                if (tmp.length() > 1000000L) {
                    tmp.renameTo(f)
                    return f
                }
                lastError = "file troppo piccolo (" + tmp.length() + " byte)"
                tmp.delete()
            } catch (e: Exception) {
                lastError = e.javaClass.simpleName + ": " + (e.message ?: "")
            }
        }
        return null
    }

    /** Devuelve la imagen en estilo anime, o null si la IA no está disponible (se usa entonces el efecto local). */
    fun convert(src: Bitmap): Bitmap? {
        val ctx = appContext()
        if (ctx == null) {
            lastError = "contesto non disponibile"
            return null
        }
        val file = ensureModel(ctx)
        if (file == null) {
            toast(ctx, "Modello IA non scaricato (" + lastError.take(60) + "): uso l’effetto locale.")
            return null
        }
        return try {
            runModel(file, src)
        } catch (e: Throwable) {
            lastError = e.javaClass.simpleName + ": " + (e.message ?: "")
            toast(ctx, "IA anime non riuscita (" + lastError.take(80) + "): uso l’effetto locale.")
            null
        }
    }

    private fun runModel(file: File, src: Bitmap): Bitmap {
        val env = OrtEnvironment.getEnvironment()
        return env.createSession(file.absolutePath, OrtSession.SessionOptions()).use { session ->
            val name = session.inputNames.iterator().next()
            val info = session.inputInfo[name]!!.info as TensorInfo
            val shape = info.shape
            val nchw = shape.size == 4 && shape[1] == 3L && shape[3] != 3L
            val fixedH = if (nchw) shape[2] else shape[1]
            val fixedW = if (nchw) shape[3] else shape[2]

            val scale = minOf(1f, 640f / maxOf(src.width, src.height))
            var w = ((src.width * scale).toInt() / 32 * 32).coerceAtLeast(32)
            var h = ((src.height * scale).toInt() / 32 * 32).coerceAtLeast(32)
            if (fixedW > 0 && fixedH > 0) {
                w = fixedW.toInt()
                h = fixedH.toInt()
            }
            val scaled = Bitmap.createScaledBitmap(src, w, h, true)
            val px = IntArray(w * h)
            scaled.getPixels(px, 0, w, 0, 0, w, h)
            val n = w * h
            val data = FloatArray(n * 3)
            for (i in 0 until n) {
                val c = px[i]
                val r = ((c shr 16) and 255) / 127.5f - 1f
                val g = ((c shr 8) and 255) / 127.5f - 1f
                val b = (c and 255) / 127.5f - 1f
                if (nchw) {
                    data[i] = r
                    data[n + i] = g
                    data[2 * n + i] = b
                } else {
                    data[i * 3] = r
                    data[i * 3 + 1] = g
                    data[i * 3 + 2] = b
                }
            }
            val dims = if (nchw) longArrayOf(1, 3, h.toLong(), w.toLong()) else longArrayOf(1, h.toLong(), w.toLong(), 3)
            OnnxTensor.createTensor(env, FloatBuffer.wrap(data), dims).use { tensor ->
                session.run(mapOf(name to tensor)).use { result ->
                    val fb = (result.get(0) as OnnxTensor).floatBuffer
                    var mn = Float.MAX_VALUE
                    var mx = -Float.MAX_VALUE
                    for (i in 0 until n * 3) {
                        val v = fb.get(i)
                        if (v < mn) mn = v
                        if (v > mx) mx = v
                    }
                    val mode = if (mn < -0.05f) 0 else if (mx <= 1.5f) 1 else 2
                    fun cv(v: Float): Int {
                        val x = when (mode) {
                            0 -> (v + 1f) * 127.5f
                            1 -> v * 255f
                            else -> v
                        }
                        return x.toInt().coerceIn(0, 255)
                    }
                    val outPx = IntArray(n)
                    for (i in 0 until n) {
                        val r: Int
                        val g: Int
                        val b: Int
                        if (nchw) {
                            r = cv(fb.get(i))
                            g = cv(fb.get(n + i))
                            b = cv(fb.get(2 * n + i))
                        } else {
                            r = cv(fb.get(i * 3))
                            g = cv(fb.get(i * 3 + 1))
                            b = cv(fb.get(i * 3 + 2))
                        }
                        outPx[i] = Color.rgb(r, g, b)
                    }
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.setPixels(outPx, 0, w, 0, 0, w, h)
                    Bitmap.createScaledBitmap(bmp, src.width, src.height, true)
                }
            }
        }
    }
}
