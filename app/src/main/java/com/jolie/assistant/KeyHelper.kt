package com.jolie.assistant

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

private val GEMINI_KEY_REGEX = Regex("AIza[0-9A-Za-z_\\-]{30,}")

private fun clipboardText(context: Context): String {
    return try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        if (clip == null || clip.itemCount == 0) "" else {
            clip.getItemAt(0).coerceToText(context)?.toString()?.trim() ?: ""
        }
    } catch (_: Exception) {
        ""
    }
}

private fun clearClipboard(context: Context) {
    try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("", ""))
    } catch (_: Exception) {
    }
}

/** Quando l'utente torna in Jolie, cerca nella clipboard una chiave Gemini e la passa a onFound. */
@Composable
private fun ClipboardKeyWatcher(onFound: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val latest by rememberUpdatedState(onFound)
    DisposableEffect(owner) {
        val handler = Handler(Looper.getMainLooper())
        val check = Runnable {
            val found = GEMINI_KEY_REGEX.find(clipboardText(context))?.value
            if (found != null) latest(found)
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) handler.postDelayed(check, 700)
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            handler.removeCallbacks(check)
            owner.lifecycle.removeObserver(observer)
        }
    }
}

@Composable
private fun KeyField(value: String, hint: String, onChange: (String) -> Unit, secret: Boolean = false) {
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
            visualTransformation = if (secret) PasswordVisualTransformation()
            else androidx.compose.ui.text.input.VisualTransformation.None,
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) MonoText(hint, size = 13, color = JolieColors.Gray)
                    inner()
                }
            }
        )
    }
}

/** Scheda "chiave IA": un tocco apre Google AI Studio, al ritorno la chiave copiata viene salvata da sola. */
@Composable
fun KeyCardAuto(lang: String, initialModel: String, onSaved: (String, String) -> Unit) {
    val context = LocalContext.current
    var model by remember { mutableStateOf(initialModel) }
    var manualKey by remember { mutableStateOf("") }
    var waiting by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    if (waiting) {
        ClipboardKeyWatcher { found ->
            clearClipboard(context)
            onSaved(found, model)
        }
    }

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
                        "1. Tocca GENERA CHIAVE: si apre Google AI Studio.\n2. Accedi, tocca “Crea chiave API” e poi copia la chiave.\n3. Torna in Jolie: la salvo da sola.\nIn Russia serve la VPN.",
                        "1. Нажмите СОЗДАТЬ КЛЮЧ: откроется Google AI Studio.\n2. Войдите, нажмите «Создать ключ API» и скопируйте его.\n3. Вернитесь в Джоли: я сохраню ключ сама.\nВ России нужен VPN."
                    ),
                    size = 12
                )
                GlassButton(
                    tr(lang, "GENERA CHIAVE", "СОЗДАТЬ КЛЮЧ"),
                    onClick = {
                        status = ""
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                            waiting = true
                        } catch (_: Exception) {
                            status = tr(lang, "Impossibile aprire il browser.", "Не удалось открыть браузер.")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    filled = true
                )
                GlassButton(
                    tr(lang, "INCOLLA E SALVA", "ВСТАВИТЬ И СОХРАНИТЬ"),
                    onClick = {
                        val txt = clipboardText(context)
                        val key = GEMINI_KEY_REGEX.find(txt)?.value
                            ?: txt.takeIf { it.length in 20..200 && !it.contains(' ') && !it.contains('\n') }
                        if (key != null) {
                            clearClipboard(context)
                            onSaved(key, model)
                        } else {
                            status = tr(
                                lang,
                                "Negli appunti non c’è nessuna chiave. Copiala da Google AI Studio.",
                                "В буфере нет ключа. Скопируйте его в Google AI Studio."
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (status.isNotEmpty()) MonoText(status, size = 12, color = JolieColors.RedSoft)
                MonoText(tr(lang, "OPPURE INCOLLALA A MANO", "ИЛИ ВСТАВЬТЕ ВРУЧНУЮ"), size = 10, color = JolieColors.Gray, spacing = 1f)
                KeyField(manualKey, tr(lang, "Chiave", "Ключ"), { manualKey = it }, secret = true)
                KeyField(model, "gemini-flash-latest", { model = it })
                GlassButton(
                    tr(lang, "SALVA", "СОХРАНИТЬ"),
                    onClick = {
                        if (manualKey.isNotBlank()) onSaved(manualKey.trim(), model)
                        else status = tr(lang, "Incolla prima una chiave.", "Сначала вставьте ключ.")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
