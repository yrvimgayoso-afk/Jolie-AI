package com.jolie.assistant

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

data class AiMsg(val fromUser: Boolean, val text: String, val attachment: String? = null)

private const val SYSTEM_TEXT =
    "Sei Jolie, una segretaria personale intelligente, gentile e concisa. " +
        "Rispondi sempre nella lingua in cui ti scrive l’utente (italiano o russo). " +
        "Se ricevi la foto di un compito, di un esame o di un testo, leggila con attenzione, " +
        "spiega il ragionamento passo per passo e poi dai la risposta. " +
        "Usa frasi brevi e niente formattazione complicata, perché le risposte possono essere lette ad alta voce."

private fun cleanMd(s: String): String =
    s.replace("**", "")
        .replace(Regex("(?m)^#+\\s*"), "")
        .replace(Regex("(?m)^\\*\\s+"), "• ")

private fun friendlyError(lang: String, e: Throwable): String {
    if (e is GeminiHttpException) {
        val hint = when (e.code) {
            400 -> tr(lang, "Richiesta non valida o chiave non corretta.", "Неверный запрос или ключ.")
            403 -> tr(lang, "Chiave rifiutata o paese non supportato (in Russia serve la VPN).", "Ключ отклонён или страна не поддерживается (в России нужен VPN).")
            404 -> tr(lang, "Modello non trovato: tocca CHIAVE e cambia il nome del modello.", "Модель не найдена: нажмите КЛЮЧ и измените название модели.")
            429 -> tr(lang, "Limite gratuito raggiunto: riprova tra poco.", "Бесплатный лимит исчерпан: повторите позже.")
            else -> tr(lang, "Errore del servizio.", "Ошибка сервиса.")
        }
        return hint + "\n(" + e.code + ") " + (e.message ?: "").take(160)
    }
    val m = e.message ?: ""
    if (e is IllegalArgumentException && m == "FORMAT") {
        return tr(lang, "Formato non supportato. Usa immagini, PDF o testo semplice.", "Формат не поддерживается. Используйте изображения, PDF или текст.")
    }
    if (e is IllegalArgumentException && m == "SIZE") {
        return tr(lang, "File troppo grande (massimo circa 14 MB).", "Файл слишком большой (максимум около 14 МБ).")
    }
    if (m.startsWith("BLOCKED")) {
        return tr(lang, "Gemini ha bloccato la richiesta.", "Gemini заблокировал запрос.")
    }
    return tr(
        lang,
        "Nessuna risposta: controlla la connessione. Se sei in Russia attiva una VPN.",
        "Нет ответа: проверьте соединение. В России включите VPN."
    ) + "\n[" + e.javaClass.simpleName + "]"
}

/** Conversazione che resta in memoria (e sul telefono) anche cambiando scheda o chiudendo l'app. */
object ChatStore {
    val messages = mutableStateListOf<AiMsg>()
    private val history = mutableListOf<AiTurn>()

    var loading by mutableStateOf(false)
        private set

    var speaker: ((String) -> Unit)? = null

    private var loaded = false
    private val main = Handler(Looper.getMainLooper())

    private fun sp(context: Context) =
        context.applicationContext.getSharedPreferences("jolie_chat", Context.MODE_PRIVATE)

    fun ensureLoaded(context: Context, greeting: String) {
        if (loaded) return
        loaded = true
        try {
            val m = JSONArray(sp(context).getString("messages", "[]") ?: "[]")
            for (i in 0 until m.length()) {
                val o = m.getJSONObject(i)
                messages.add(
                    AiMsg(o.getBoolean("u"), o.getString("t"), if (o.has("a")) o.getString("a") else null)
                )
            }
            val h = JSONArray(sp(context).getString("history", "[]") ?: "[]")
            for (i in 0 until h.length()) {
                val o = h.getJSONObject(i)
                history.add(AiTurn(o.getBoolean("u"), o.getString("t")))
            }
        } catch (_: Exception) {
        }
        if (messages.isEmpty()) messages.add(AiMsg(false, greeting))
    }

    private fun save(context: Context) {
        try {
            val m = JSONArray()
            for (x in messages.takeLast(40)) {
                val o = JSONObject().put("u", x.fromUser).put("t", x.text)
                if (x.attachment != null) o.put("a", x.attachment)
                m.put(o)
            }
            val h = JSONArray()
            for (x in history.takeLast(20)) h.put(JSONObject().put("u", x.fromUser).put("t", x.text))
            sp(context).edit().putString("messages", m.toString()).putString("history", h.toString()).apply()
        } catch (_: Exception) {
        }
    }

    fun clear(context: Context, greeting: String) {
        if (loading) return
        messages.clear()
        history.clear()
        messages.add(AiMsg(false, greeting))
        save(context)
    }

    fun note(context: Context, text: String) {
        messages.add(AiMsg(false, text))
        save(context)
    }

    fun send(
        context: Context,
        apiKey: String,
        model: String,
        userText: String,
        attachment: Uri?,
        attachmentName: String?,
        lang: String,
        spoken: Boolean
    ) {
        if (loading) return
        val app = context.applicationContext
        messages.add(AiMsg(true, userText, attachmentName))
        save(app)
        loading = true
        val snapshot = history.takeLast(20)
        val done = AtomicBoolean(false)
        val timeout = Runnable {
            if (done.compareAndSet(false, true)) {
                loading = false
                messages.add(
                    AiMsg(
                        false,
                        tr(
                            lang,
                            "Gemini non risponde da questa rete (blocco o connessione lenta). Se sei in Russia attiva una VPN e riprova.",
                            "Gemini не отвечает в этой сети (блокировка или медленное соединение). В России включите VPN и повторите."
                        )
                    )
                )
                save(app)
            }
        }
        main.postDelayed(timeout, 60000)
        askGemini(app, apiKey, model, SYSTEM_TEXT, snapshot, userText, attachment) { result ->
            if (!done.compareAndSet(false, true)) return@askGemini
            main.removeCallbacks(timeout)
            loading = false
            result.onSuccess { reply ->
                history.add(AiTurn(true, if (attachmentName != null) "[$attachmentName] $userText" else userText))
                history.add(AiTurn(false, reply))
                val shown = cleanMd(reply)
                messages.add(AiMsg(false, shown))
                if (spoken) speaker?.invoke(shown)
            }.onFailure { e ->
                messages.add(AiMsg(false, friendlyError(lang, e)))
            }
            save(app)
        }
    }
}
