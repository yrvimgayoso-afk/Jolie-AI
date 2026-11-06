package com.jolie.assistant

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URL

/** Chiave e modello Gemini, salvati solo sul telefono. */
class AiPrefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("jolie_ai", Context.MODE_PRIVATE)

    var geminiKey: String
        get() = sp.getString("gemini_key", "") ?: ""
        set(value) { sp.edit().putString("gemini_key", value.trim()).apply() }

    var geminiModel: String
        get() = sp.getString("gemini_model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) {
            sp.edit().putString("gemini_model", value.trim().ifEmpty { DEFAULT_MODEL }).apply()
        }

    companion object {
        const val DEFAULT_MODEL = "gemini-flash-latest"
    }
}

class AiTurn(val fromUser: Boolean, val text: String)

class GeminiHttpException(val code: Int, message: String) : Exception(message)

private fun isTransient(code: Int): Boolean =
    code == 429 || code == 500 || code == 502 || code == 503 || code == 504

private fun isChatModel(n: String): Boolean {
    if (!n.startsWith("gemini")) return false
    val bad = listOf(
        "image", "tts", "audio", "live", "embed", "imagen", "veo", "aqa",
        "learnlm", "robotics", "computer", "native", "customtools"
    )
    return bad.none { n.contains(it) }
}

/** Più basso = provato prima: i modelli "lite" stabili sono i meno affollati. */
private fun modelRank(n: String): Int {
    val lite = n.contains("lite")
    val flash = n.contains("flash")
    val pro = n.contains("pro")
    val preview = n.contains("preview") || n.contains("exp")
    return when {
        lite && !preview -> 0
        flash && !preview && !n.endsWith("-latest") -> 1
        lite -> 2
        flash && !preview -> 3
        flash -> 4
        pro -> 6
        else -> 8
    }
}

/** Chiede a Google quali modelli esistono davvero per questa chiave (cache di 6 ore). */
private fun listChatModels(app: Context, apiKey: String): List<String> {
    val sp = app.getSharedPreferences("jolie_ai", Context.MODE_PRIVATE)
    val cached = sp.getString("models_cache", "") ?: ""
    val age = System.currentTimeMillis() - sp.getLong("models_time", 0L)
    if (cached.isNotEmpty() && age < 6L * 3600L * 1000L) return cached.split(",")
    val conn = JolieProxy.openConnection(URL("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200"))
    try {
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("x-goog-api-key", apiKey)
        if (conn.responseCode !in 200..299) return emptyList()
        val root = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val arr = root.optJSONArray("models") ?: return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i)
            val name = m.optString("name").removePrefix("models/")
            val methods = m.optJSONArray("supportedGenerationMethods")
            var ok = false
            if (methods != null) {
                for (j in 0 until methods.length()) {
                    if (methods.getString(j) == "generateContent") ok = true
                }
            }
            if (ok && isChatModel(name)) out.add(name)
        }
        sp.edit().putString("models_cache", out.joinToString(",")).putLong("models_time", System.currentTimeMillis()).apply()
        return out
    } catch (_: Exception) {
        return emptyList()
    } finally {
        conn.disconnect()
    }
}

/** Ordine dei modelli: l'ultimo che ha funzionato, poi quelli esistenti dal meno al più affollato. */
private fun modelChain(app: Context, apiKey: String, primary: String): List<String> {
    val sp = app.getSharedPreferences("jolie_ai", Context.MODE_PRIVATE)
    val lastOk = sp.getString("last_ok", "") ?: ""
    val available = listChatModels(app, apiKey)
    val out = ArrayList<String>()
    if (available.isEmpty()) {
        if (lastOk.isNotEmpty()) out.add(lastOk)
        if (primary.isNotEmpty() && primary != AiPrefs.DEFAULT_MODEL) out.add(primary)
        out.add("gemini-flash-lite-latest")
        out.add(AiPrefs.DEFAULT_MODEL)
    } else {
        if (lastOk.isNotEmpty() && available.contains(lastOk)) out.add(lastOk)
        if (primary.isNotEmpty() && primary != AiPrefs.DEFAULT_MODEL && available.contains(primary)) out.add(primary)
        out.addAll(available.sortedWith(compareBy<String>({ modelRank(it) }, { it })))
    }
    return out.distinct().take(4)
}

fun displayName(context: Context, uri: Uri): String {
    var name = "file"
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) name = c.getString(i) ?: "file"
        }
    } catch (_: Exception) {
    }
    return name
}

/** Chiede a Gemini in background (modelli reali, max 50 secondi) e risponde sul thread principale. */
fun askGemini(
    context: Context,
    apiKey: String,
    model: String,
    systemText: String,
    history: List<AiTurn>,
    userText: String,
    attachment: Uri?,
    onDone: (Result<String>) -> Unit
) {
    val app = context.applicationContext
    Thread {
        val result = runCatching {
            val systemText = systemText + ChatExtras.systemExtra(app)
            val deadline = System.currentTimeMillis() + 50000
            val chain = modelChain(app, apiKey, model.trim())
            val attempts = ArrayList<String>()
            var lastHttp: GeminiHttpException? = null
            for (round in 0..1) {
                for (m in chain) {
                    val remaining = (deadline - System.currentTimeMillis()).toInt()
                    if (remaining < 4000) break
                    try {
                        val reply = callGemini(
                            app, apiKey, m, systemText, history, userText, attachment, minOf(remaining, 20000)
                        )
                        app.getSharedPreferences("jolie_ai", Context.MODE_PRIVATE)
                            .edit().putString("last_ok", m).apply()
                        return@runCatching reply
                    } catch (e: GeminiHttpException) {
                        if (!isTransient(e.code) && e.code != 404) throw e
                        lastHttp = e
                        attempts.add(m.removePrefix("gemini-") + ":" + e.code)
                    } catch (e: SocketTimeoutException) {
                        attempts.add(m.removePrefix("gemini-") + ":timeout")
                    }
                }
                if (deadline - System.currentTimeMillis() < 6000) break
                if (round == 0) Thread.sleep(1500)
            }
            val e = lastHttp
            if (e != null && attempts.all { it.endsWith(":404") || it.endsWith(":429") }) throw e
            throw GeminiHttpException(
                503,
                "Gemini lento o sovraccarico. / Gemini медленный или перегружен. " + attempts.joinToString(" ")
            )
        }
        Handler(Looper.getMainLooper()).post { onDone(result) }
    }.start()
}

private fun callGemini(
    context: Context,
    apiKey: String,
    model: String,
    systemText: String,
    history: List<AiTurn>,
    userText: String,
    attachment: Uri?,
    timeoutMs: Int
): String {
    val contents = JSONArray()
    for (t in history) {
        contents.put(
            JSONObject()
                .put("role", if (t.fromUser) "user" else "model")
                .put("parts", JSONArray().put(JSONObject().put("text", t.text)))
        )
    }

    val parts = JSONArray()
    if (attachment != null) {
        val cr = context.contentResolver
        val mime = cr.getType(attachment) ?: throw IllegalArgumentException("FORMAT")
        val okType = mime.startsWith("image/") || mime == "application/pdf" || mime == "text/plain"
        if (!okType) throw IllegalArgumentException("FORMAT")
        val bytes = cr.openInputStream(attachment)?.use { it.readBytes() }
            ?: throw IOException("Impossibile leggere il file")
        if (bytes.size > 14_000_000) throw IllegalArgumentException("SIZE")
        parts.put(
            JSONObject().put(
                "inlineData",
                JSONObject()
                    .put("mimeType", mime)
                    .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
            )
        )
    }
    parts.put(JSONObject().put("text", userText))
    contents.put(JSONObject().put("role", "user").put("parts", parts))

    val body = JSONObject()
        .put("contents", contents)
        .put(
            "systemInstruction",
            JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemText)))
        )

    val cleanModel = model.trim().removePrefix("models/")
    val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent")
    val conn = JolieProxy.openConnection(url)
    try {
        conn.requestMethod = "POST"
        conn.connectTimeout = 8000
        conn.readTimeout = timeoutMs
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("x-goog-api-key", apiKey)
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        if (code !in 200..299) throw GeminiHttpException(code, extractError(text))
        return parseReply(text)
    } finally {
        conn.disconnect()
    }
}

private fun extractError(body: String): String =
    try {
        JSONObject(body).getJSONObject("error").optString("message", body)
    } catch (_: Exception) {
        body
    }

private fun parseReply(text: String): String {
    val root = JSONObject(text)
    val cands = root.optJSONArray("candidates")
    if (cands == null || cands.length() == 0) {
        val reason = root.optJSONObject("promptFeedback")?.optString("blockReason") ?: ""
        throw IOException("BLOCKED:$reason")
    }
    val partsArr = cands.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
    val sb = StringBuilder()
    if (partsArr != null) {
        for (i in 0 until partsArr.length()) {
            val p = partsArr.getJSONObject(i)
            if (!p.optBoolean("thought", false)) sb.append(p.optString("text", ""))
        }
    }
    val out = sb.toString().trim()
    if (out.isEmpty()) throw IOException("EMPTY")
    return out
}
