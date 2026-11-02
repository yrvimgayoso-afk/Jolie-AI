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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

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
fun AiChatScreen(lang: String) {
    val context = LocalContext.current
    val prefs = remember { AiPrefs(context) }
    val greeting = tr(
        lang,
        "Ciao, sono Jolie. Scrivimi, tocca l’orbe per parlarmi oppure allega una foto o un PDF con il tasto +.",
        "Привет, я Джоли. Напишите мне, нажмите на шар, чтобы говорить, или прикрепите фото или PDF кнопкой +."
    )
    remember {
        ChatStore.ensureLoaded(context, greeting)
        true
    }
    val messages = ChatStore.messages
    val loading = ChatStore.loading

    var input by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf(prefs.geminiKey) }
    var model by remember { mutableStateOf(prefs.geminiModel) }
    var showKeyCard by remember { mutableStateOf(prefs.geminiKey.isBlank()) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingName by remember { mutableStateOf("") }
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

    DisposableEffect(Unit) {
        ChatStore.speaker = { text -> speak(text) }
        onDispose { ChatStore.speaker = null }
    }

    fun send(textIn: String, spoken: Boolean) {
        val text = textIn.trim()
        val uri = pendingUri
        val name = pendingName
        if ((text.isEmpty() && uri == null) || ChatStore.loading) return
        if (apiKey.isBlank()) {
            showKeyCard = true
            return
        }
        val userText = if (text.isEmpty()) {
            tr(lang, "Leggi questo allegato e spiegamelo.", "Прочитай это вложение и объясни.")
        } else text
        input = ""
        pendingUri = null
        pendingName = ""
        ChatStore.send(context, apiKey, model, userText, uri, if (uri != null) name else null, lang, spoken)
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
            ChatStore.note(
                context,
                tr(
                    lang,
                    "Il riconoscimento vocale non è disponibile su questo telefono.",
                    "Распознавание речи недоступно на этом телефоне."
                )
            )
        }
    }

    LaunchedEffect(messages.size, loading) {
        val total = messages.size + if (loading) 1 else 0
        if (total > 0) listState.scrollToItem(total - 1)
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
            GlassSurface(Modifier.align(Alignment.TopStart), RoundedCornerShape(18.dp)) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clickable { ChatStore.clear(context, greeting) }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    MonoText(tr(lang, "NUOVA", "НОВЫЙ"), size = 11, spacing = 1f)
                }
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
                KeyCardAuto(lang, model) { k, m ->
                    apiKey = k
                    prefs.geminiKey = k
                    prefs.geminiModel = m
                    model = prefs.geminiModel
                    showKeyCard = false
                    ChatStore.note(
                        context,
                        tr(lang, "Chiave salvata. Ora puoi scrivermi.", "Ключ сохранён. Теперь можете писать мне.")
                    )
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
