package com.jolie.assistant

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

fun requiredPermissions(): Array<String> {
    val list = mutableListOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
        Manifest.permission.CAMERA
    )
    if (Build.VERSION.SDK_INT >= 33) {
        list.add(Manifest.permission.READ_MEDIA_IMAGES)
        list.add(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    return list.toTypedArray()
}

private fun newTask(intent: Intent): Intent = intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

fun openAppDetails(context: Context) {
    try {
        context.startActivity(
            newTask(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + context.packageName)
                )
            )
        )
    } catch (_: Exception) {
    }
}

fun openBatterySettings(context: Context) {
    try {
        context.startActivity(newTask(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)))
    } catch (_: Exception) {
        openAppDetails(context)
    }
}

/** Prova le schermate "avvio automatico" di Xiaomi e realme/Oppo; se non esistono apre le info dell'app. */
fun openAutostartSettings(context: Context) {
    val candidates = listOf(
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
    )
    for (c in candidates) {
        try {
            context.startActivity(newTask(Intent().setComponent(c)))
            return
        } catch (_: Exception) {
        }
    }
    openAppDetails(context)
}

@Composable
fun SetupWizard(initialLang: String, onFinished: (String) -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(0) }
    var lang by remember { mutableStateOf(initialLang) }
    var denied by remember { mutableStateOf<Int?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        denied = result.values.count { !it }
    }

    JolieBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
            MonoText("JOLIE", size = 34, bold = true, spacing = 6f)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0..2) {
                    Spacer(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (i == step) JolieColors.Red else JolieColors.Gray.copy(alpha = 0.4f))
                    )
                }
            }
            Spacer(Modifier.height(24.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (step) {
                    0 -> {
                        MonoText(tr(lang, "SCEGLI LA LINGUA", "ВЫБЕРИТЕ ЯЗЫК"), size = 18, bold = true, spacing = 2f)
                        MonoText(
                            tr(lang, "Potrai cambiarla più tardi.", "Позже её можно изменить."),
                            color = JolieColors.Gray
                        )
                        GlassButton("ITALIANO", onClick = { lang = "IT" }, modifier = Modifier.fillMaxWidth(), filled = lang == "IT")
                        GlassButton("РУССКИЙ", onClick = { lang = "RU" }, modifier = Modifier.fillMaxWidth(), filled = lang == "RU")
                    }
                    1 -> {
                        MonoText(tr(lang, "PERMESSI", "РАЗРЕШЕНИЯ"), size = 18, bold = true, spacing = 2f)
                        MonoText(
                            tr(
                                lang,
                                "Jolie ha bisogno di questi accessi per funzionare:\n\n• Microfono: ascoltarti e tradurre\n• Calendario: leggere e creare appuntamenti\n• Fotocamera e foto: analizzare immagini e documenti\n• Notifiche: avvisi e promemoria\n\nIl permesso VPN ti sarà chiesto quando attivi la VPN.",
                                "Джоли нужны эти доступы:\n\n• Микрофон: слушать и переводить\n• Календарь: читать и создавать встречи\n• Камера и фото: анализ изображений и документов\n• Уведомления: напоминания\n\nРазрешение VPN будет запрошено при включении VPN."
                            )
                        )
                        GlassButton(
                            tr(lang, "CONCEDI TUTTO", "РАЗРЕШИТЬ ВСЁ"),
                            onClick = { launcher.launch(requiredPermissions()) },
                            modifier = Modifier.fillMaxWidth(),
                            filled = true
                        )
                        denied?.let { d ->
                            if (d == 0) {
                                MonoText(tr(lang, "Tutto concesso.", "Всё разрешено."), color = JolieColors.RedSoft)
                            } else {
                                MonoText(
                                    tr(
                                        lang,
                                        "$d permessi non concessi. Potrai attivarli dalle impostazioni del telefono.",
                                        "Не разрешено: $d. Их можно включить в настройках телефона."
                                    ),
                                    color = JolieColors.RedSoft
                                )
                            }
                        }
                    }
                    else -> {
                        MonoText(tr(lang, "ULTIMI PASSAGGI", "ПОСЛЕДНИЕ ШАГИ"), size = 18, bold = true, spacing = 2f)
                        MonoText(
                            tr(
                                lang,
                                "Realme e Xiaomi chiudono le app in background per risparmiare batteria. Così microfono e VPN si fermerebbero. Apri queste impostazioni e consenti a Jolie di restare attiva.",
                                "Realme и Xiaomi закрывают фоновые приложения для экономии батареи. Тогда микрофон и VPN остановятся. Откройте настройки и разрешите Джоли работать в фоне."
                            )
                        )
                        GlassButton(
                            tr(lang, "IMPOSTAZIONI BATTERIA", "НАСТРОЙКИ БАТАРЕИ"),
                            onClick = { openBatterySettings(context) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        GlassButton(
                            tr(lang, "AVVIO AUTOMATICO", "АВТОЗАПУСК"),
                            onClick = { openAutostartSettings(context) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (step > 0) {
                    GlassButton(tr(lang, "INDIETRO", "НАЗАД"), onClick = { step -= 1 })
                }
                GlassButton(
                    text = if (step < 2) tr(lang, "AVANTI", "ДАЛЕЕ") else tr(lang, "INIZIA", "НАЧАТЬ"),
                    onClick = { if (step < 2) step += 1 else onFinished(lang) },
                    modifier = Modifier.weight(1f),
                    filled = true
                )
            }
        }
    }
}
