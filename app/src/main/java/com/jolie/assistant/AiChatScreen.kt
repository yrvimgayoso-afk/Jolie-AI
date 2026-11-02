package com.jolie.assistant

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

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

@Composable
private fun PulseRing(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "orb")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Restart),
        label = "phase"
    )
    Canvas(modifier) {
        val r = size.minDimension / 2f
        drawCircle(
            color = JolieColors.Red.copy(alpha = 1f - phase),
            radius = r * 0.2f + r * 0.75f * phase,
            center = center,
            style = Stroke(width = 3.dp.toPx())
        )
    }
}

@Composable
private fun FieldBox(value: String, hint: String, onChange: (String) -> Unit, secret: Boolean = false) {
    GlassSurface(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            textStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
            cursorBrush = SolidColor(Color.White),
            singleLine = true,
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) MonoText(hint, size = 13, color = JolieColors.Gray)
                    inner()
                }
            }
        )
    }
}

@Composable
fun AiChatScreen(lang: String) {
    val context = LocalContext.current
    val prefs = remember { AiPrefs(context) }
    val messages = remember {
        mutableStateListOf(
            AiMsg(
                false,
                tr(
                    lang,
                    "Ciao, sono Jolie. Scrivimi, tocca l’orbe per parlarmi oppure allega una foto o un PDF con il tasto +.",
                    "Привет, я Джоли. Напишите мне, нажмите на шар, чтобы говорить, или прикрепите фото или PDF кнопкой +."
                )
            )
        )
    }
    val history = remember { mutableListOf<AiTurn>() }
    var input by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf(prefs.geminiKey) }
    var model by remember { mutableStateOf(prefs.geminiModel) }
    var showKeyCard by remember { mutableStateOf(prefs.geminiKey.isBlank()) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingName by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Voce di Jolie (sintesi vocale del telefono)
    val ttsHolder = remember { arrayOfNulls<TextToSpeech>(1) }
    DisposableEffect(Unit) {
        ttsHolder[0] = TextToSpeech(context, TextToSpeech.OnInitListener { })
        onDispose {
            ttsHolder[0]?.stop()
            ttsHolder[0]?.shutdown()
        }
    }

    fun speak(text: String) {
        val t = ttsHolder[0] ?: return
        val isRu = text.any { it in '\u0400'..'\u04FF' }
        t.setLanguage(if (isRu) Locale("ru", "RU") else Locale.ITALY)
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jolie")
    }

    fun send(textIn: String, spoken: Boolean) {
        val text = textIn.trim()
        val uri = pendingUri
        val name = pendingName
        if ((text.isEmpty() && uri == null) || loading) return
        if (apiKey.isBlank()) {
            showKeyCard = true
            return
        }
        val userText = if (text.isEmpty()) {
            tr(lang, "Leggi questo allegato e spiegamelo.", "Прочитай это вложение и объясни.")
        } else text
        messages.add(AiMsg(true, userText, if (uri != null) name else null))
        input = ""
        pendingUri = null
        pendingName = ""
        loading = true
        val snapshot = history.toList()
        askGemini(context, apiKey, model, SYSTEM_TEXT, snapshot, userText, uri) { result ->
            loading = false
            result.onSuccess { reply ->
                history.add(AiTurn(true, if (uri != null) "[$name] $userText" else userText))
                history.add(AiTurn(false, reply))
                val shown = cleanMd(reply)
                messages.add(AiMsg(false, shown))
                if (spoken) speak(shown)
            }.onFailure { e ->
                messages.add(AiMsg(false, friendlyError(lang, e)))
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingUri = uri
            pendingName = displayName(context, uri)
        }
    }

    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val heard = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!heard.isNullOrBlank()) send(heard, true)
        }
    }

    fun startVoice() {
        ttsHolder[0]?.stop()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (lang == "RU") "ru-RU" else "it-IT")
            putExtra(RecognizerIntent.EXTRA_PROMPT, tr(lang, "Parla con Jolie…", "Говорите…"))
        }
        try {
            voiceLauncher.launch(intent)
        } catch (e: Exception) {
            messages.add(
                AiMsg(
                    false,
                    tr(
                        lang,
                        "Il riconoscimento vocale non è disponibile su questo telefono.",
                        "Распознавание речи недоступно на этом телефоне."
                    )
                )
            )
        }
    }

    LaunchedEffect(messages.size, loading) {
        val total = messages.size + if (loading) 1 else 0
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    Column(Modifier.fillMaxSize()) {
        // Orbe = pulsante del microfono
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .clickable(onClickLabel = tr(lang, "Parla con Jolie", "Говорить с Джоли")) { startVoice() },
                contentAlignment = Alignment.Center
            ) {
                Orb(Modifier.fillMaxSize())
                if (loading) PulseRing(Modifier.fillMaxSize())
            }
            GlassSurface(Modifier.align(Alignment.TopEnd), RoundedCornerShape(18.dp)) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clickable { showKeyCard = !showKeyCard }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    MonoText(tr(lang, "CHIAVE", "КЛЮЧ"), size = 11, spacing = 1f)
                }
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            MonoText(
                tr(lang, "Tocca l’orbe per parlare", "Нажмите на шар, чтобы говорить"),
                size = 11,
                color = JolieColors.Gray
            )
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            if (showKeyCard) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            MonoText(
                                tr(lang, "CHIAVE GRATUITA DI GEMINI", "БЕСПЛАТНЫЙ КЛЮЧ GEMINI"),
                                size = 11,
                                color = JolieColors.RedSoft,
                                spacing = 1.5f
                            )
                            MonoText(
                                tr(
                                    lang,
                                    "1. Tocca APRI AI STUDIO e crea una chiave gratis.\n2. Copiala e incollala qui sotto.\nIn Russia Gemini funziona solo con una VPN attiva.",
                                    "1. Нажмите ОТКРЫТЬ AI STUDIO и создайте бесплатный ключ.\n2. Скопируйте и вставьте его ниже.\nВ России Gemini работает только с включённым VPN."
                                ),
                                size = 12
                            )
                            GlassButton(
                                tr(lang, "APRI AI STUDIO", "ОТКРЫТЬ AI STUDIO"),
                                onClick = {
                                    try {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    } catch (_: Exception) {
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            FieldBox(apiKey, tr(lang, "Incolla la chiave qui", "Вставьте ключ сюда"), { apiKey = it }, secret = true)
                            FieldBox(model, "gemini-flash-latest", { model = it })
                            GlassButton(
                                tr(lang, "SALVA", "СОХРАНИТЬ"),
                                onClick = {
                                    prefs.geminiKey = apiKey
                                    prefs.geminiModel = model
                                    model = prefs.geminiModel
                                    showKeyCard = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                filled = true
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages) { m ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start
                        ) {
                            GlassSurface(Modifier.widthIn(max = 300.dp)) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (m.attachment != null) {
                                        MonoText(
                                            "[" + tr(lang, "ALLEGATO", "ВЛОЖЕНИЕ") + "] " + m.attachment,
                                            size = 11,
                                            color = JolieColors.Gray
                                        )
                                    }
                                    MonoText(m.text)
                                }
                            }
                        }
                    }
                    if (loading) {
                        item {
                            GlassSurface {
                                MonoText(
                                    tr(lang, "Jolie sta pensando…", "Джоли думает…"),
                                    modifier = Modifier.padding(14.dp),
                                    color = JolieColors.Gray
                                )
                            }
                        }
                    }
                }
            }
        }

        if (pendingUri != null) {
            GlassSurface(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MonoText(pendingName, size = 12, modifier = Modifier.weight(1f))
                    Box(
                        Modifier
                            .size(48.dp)
                            .clickable {
                                pendingUri = null
                                pendingName = ""
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✕", color = Color.White, fontSize = 16.sp)
                    }
                }
            }
        }

        GlassSurface(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            RoundedCornerShape(28.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable { picker.launch(arrayOf("image/*", "application/pdf", "text/plain")) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", color = Color.White, fontSize = 24.sp)
                }
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontFamily = FontFamily.Monospace),
                    cursorBrush = SolidColor(Color.White),
                    singleLine = true,
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (input.isEmpty()) {
                                MonoText(tr(lang, "Chiedi a Jolie…", "Спросите Джоли…"), size = 14, color = JolieColors.Gray)
                            }
                            inner()
                        }
                    }
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(JolieColors.Red)
                        .clickable { send(input, false) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (loading) "…" else "↑", color = Color.White, fontSize = 20.sp)
                }
            }
        }
    }
}
