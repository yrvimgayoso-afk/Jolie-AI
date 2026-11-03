package com.jolie.assistant

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/** Modelli Gemini che sanno modificare immagini (si prova il successivo se uno è sovraccarico o assente). */
val IMAGE_MODELS = listOf("gemini-2.5-flash-image", "gemini-3.1-flash-image-preview")

fun editImageWithGemini(
    apiKey: String,
    model: String,
    bitmap: Bitmap,
    prompt: String,
    onDone: (Result<Bitmap>) -> Unit
) {
    val main = Handler(Looper.getMainLooper())
    val done = AtomicBoolean(false)
    val timeout = Runnable {
        if (done.compareAndSet(false, true)) onDone(Result.failure(IOException("TIMEOUT")))
    }
    main.postDelayed(timeout, 150000)
    Thread {
        val result = runCatching {
            val chain = (listOf(model.trim()) + IMAGE_MODELS).filter { it.isNotEmpty() }.distinct()
            var lastError: GeminiHttpException? = null
            for (round in 0..1) {
                for (m in chain) {
                    try {
                        return@runCatching callImageModel(apiKey, m, bitmap, prompt)
                    } catch (e: GeminiHttpException) {
                        if (e.code != 404 && e.code != 429 && e.code < 500) throw e
                        lastError = e
                    }
                }
                if (round == 0) Thread.sleep(2500)
            }
            throw (lastError ?: IOException("EMPTY"))
        }
        main.post {
            if (done.compareAndSet(false, true)) {
                main.removeCallbacks(timeout)
                onDone(result)
            }
        }
    }.start()
}

private fun callImageModel(apiKey: String, model: String, bitmap: Bitmap, prompt: String): Bitmap {
    val small = scaleDown(bitmap, 1280)
    val bos = ByteArrayOutputStream()
    small.compress(Bitmap.CompressFormat.JPEG, 90, bos)
    val b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)

    val parts = JSONArray()
        .put(JSONObject().put("inlineData", JSONObject().put("mimeType", "image/jpeg").put("data", b64)))
        .put(JSONObject().put("text", prompt))
    val body = JSONObject()
        .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
        .put("generationConfig", JSONObject().put("responseModalities", JSONArray().put("TEXT").put("IMAGE")))

    val cleanModel = model.trim().removePrefix("models/")
    val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent")
    val conn = url.openConnection() as HttpURLConnection
    try {
        conn.requestMethod = "POST"
        conn.connectTimeout = 20000
        conn.readTimeout = 120000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("x-goog-api-key", apiKey)
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        if (code !in 200..299) {
            val msg = try {
                JSONObject(text).getJSONObject("error").optString("message", text)
            } catch (_: Exception) {
                text
            }
            throw GeminiHttpException(code, msg)
        }

        val cands = JSONObject(text).optJSONArray("candidates")
        val partsArr = cands?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
        var textOut = ""
        if (partsArr != null) {
            for (i in 0 until partsArr.length()) {
                val p = partsArr.getJSONObject(i)
                val inline = p.optJSONObject("inlineData")
                if (inline != null) {
                    val bytes = Base64.decode(inline.getString("data"), Base64.DEFAULT)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) return bmp
                } else {
                    textOut += p.optString("text", "")
                }
            }
        }
        throw IOException("NOIMAGE:" + textOut.take(140))
    } finally {
        conn.disconnect()
    }
}
