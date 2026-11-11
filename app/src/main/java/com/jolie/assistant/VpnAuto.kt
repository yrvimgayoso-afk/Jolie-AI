package com.jolie.assistant

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val ON_GREEN = Color(0xFF3DDC84)
private val AMBER = Color(0xFFFFC107)

/** Indicador de arriba: tocarlo enciende o apaga la VPN automática. */
@Composable
fun JolieVpnPill(lang: String, onClick: () -> Unit) {
    VpnPermissionHost()
    val st = JolieProxy.state
    val label = when (st) {
        JolieProxy.State.OFF -> tr(lang, "VPN SPENTA", "VPN ВЫКЛ.")
        JolieProxy.State.SEARCHING -> tr(lang, "CERCO…", "ПОИСК…")
        JolieProxy.State.CONNECTING -> tr(lang, "CERCO…", "ПОИСК…")
        JolieProxy.State.CONNECTED -> tr(lang, "VPN ATTIVA", "VPN ВКЛ.")
        JolieProxy.State.ERROR -> tr(lang, "VPN ERRORE", "VPN ОШИБКА")
    }
    val dot = when (st) {
        JolieProxy.State.OFF -> JolieColors.Gray
        JolieProxy.State.CONNECTED -> ON_GREEN
        JolieProxy.State.ERROR -> JolieColors.RedSoft
        else -> AMBER
    }
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        GlassSurface(shape = RoundedCornerShape(17.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(8.dp))
                MonoText(label, size = 11, spacing = 1f)
            }
        }
    }
}

@Composable
private fun BusyRing(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "vpn")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300), RepeatMode.Restart),
        label = "phase"
    )
    Canvas(modifier) {
        val r = size.minDimension / 2f
        drawCircle(
            color = JolieColors.Red.copy(alpha = 1f - phase),
            radius = r * (0.78f + 0.22f * phase),
            center = center,
            style = Stroke(width = 3.dp.toPx())
        )
    }
}

@Composable
private fun PowerIcon(color: Color) {
    Canvas(Modifier.size(56.dp)) {
        val s = size.minDimension
        val w = s * 0.1f
        drawArc(
            color = color,
            startAngle = -60f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset(s * 0.12f, s * 0.18f),
            size = Size(s * 0.76f, s * 0.76f),
            style = Stroke(width = w, cap = StrokeCap.Round)
        )
        drawLine(
            color = color,
            start = Offset(s / 2f, s * 0.05f),
            end = Offset(s / 2f, s * 0.5f),
            strokeWidth = w,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun PowerButton(lang: String) {
    val context = LocalContext.current
    val st = JolieProxy.state
    val on = st == JolieProxy.State.CONNECTED
    val busy = st == JolieProxy.State.SEARCHING || st == JolieProxy.State.CONNECTING
    Box(modifier = Modifier.size(190.dp), contentAlignment = Alignment.Center) {
        if (busy) BusyRing(Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(if (on) JolieColors.Red else Color.White.copy(alpha = 0.08f))
                .border(2.dp, if (on) JolieColors.RedSoft else Color.White.copy(alpha = 0.35f), CircleShape)
                .clickable { JolieProxy.toggle(context, lang) },
            contentAlignment = Alignment.Center
        ) {
            PowerIcon(Color.White)
        }
    }
}

private fun copyText(context: Context, text: String) {
    try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("jolie-vpn", text))
    } catch (_: Exception) {
    }
}

private fun pasteText(context: Context): String =
    try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        if (clip == null || clip.itemCount == 0) "" else clip.getItemAt(0).coerceToText(context)?.toString() ?: ""
    } catch (_: Exception) {
        ""
    }

@Composable
private fun AutoPage(lang: String) {
    val context = LocalContext.current
    var engineOk by remember { mutableStateOf(XrayEngine.available()) }
    var probe by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }
    var cfgStatus by remember { mutableStateOf("") }
    val st = JolieProxy.state

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PowerButton(lang)
        MonoText(
            when (st) {
                JolieProxy.State.OFF -> tr(lang, "SPENTA · tocca per attivare", "ВЫКЛ. · нажмите, чтобы включить")
                JolieProxy.State.CONNECTED -> tr(lang, "ATTIVA", "ВКЛЮЧЕНО")
                JolieProxy.State.ERROR -> tr(lang, "ERRORE", "ОШИБКА")
                else -> tr(lang, "IN CORSO…", "ИДЁТ ПОИСК…")
            },
            size = 13,
            bold = true,
            spacing = 2f
        )
        val msg = if (st == JolieProxy.State.CONNECTED && VpnTun.active) {
            tr(lang, "Connesso. Il tunnel è attivo per le app scelte.", "Подключено. Туннель активен для выбранных приложений.")
        } else {
            JolieProxy.message
        }
        if (msg.isNotEmpty()) MonoText(msg, size = 12, color = JolieColors.Gray)
        if (JolieProxy.nodeName.isNotEmpty()) {
            MonoText(tr(lang, "Nodo: ", "Узел: ") + JolieProxy.nodeName.take(36), size = 11, color = JolieColors.Gray)
        }

        VpnAppsCard(lang)

        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(tr(lang, "CONFIGURAZIONE SENZA CHIAVI", "НАСТРОЙКА БЕЗ КЛЮЧЕЙ"))
                MonoText(
                    tr(
                        lang,
                        "Copia il codice e mandalo in privato all’altro telefono: lì basta incollarlo. Contiene la chiave IA, non pubblicarlo.",
                        "Скопируйте код и отправьте его лично на другой телефон: там достаточно вставить. В коде ключ ИИ, не публикуйте его."
                    ),
                    size = 11
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton(tr(lang, "COPIA CODICE", "КОПИРОВАТЬ КОД"), {
                        copyText(context, ConfigCode.export(context))
                        cfgStatus = tr(lang, "Codice copiato.", "Код скопирован.")
                    }, primary = true)
                    SmallButton(tr(lang, "INCOLLA CODICE", "ВСТАВИТЬ КОД"), {
                        cfgStatus = if (ConfigCode.importCode(context, pasteText(context))) {
                            tr(lang, "Configurazione importata.", "Настройка импортирована.")
                        } else {
                            tr(lang, "Negli appunti non c’è un codice Jolie.", "В буфере нет кода Джоли.")
                        }
                    })
                }
                if (cfgStatus.isNotEmpty()) MonoText(cfgStatus, size = 11, color = JolieColors.RedSoft)
            }
        }

        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(tr(lang, "MOTORE", "ДВИЖОК"))
                MonoText(
                    if (engineOk) {
                        tr(lang, "Presente " + XrayEngine.version(), "Установлен " + XrayEngine.version())
                    } else {
                        tr(lang, "Assente: la build non ha incluso libv2ray.aar", "Отсутствует: сборка не включила libv2ray.aar")
                    },
                    size = 12,
                    color = if (engineOk) ON_GREEN else JolieColors.RedSoft
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton(tr(lang, "PROVA MOTORE", "ПРОВЕРИТЬ ДВИЖОК"), {
                        runAsync {
                            val text = "v" + XrayEngine.version() + "\n" + XrayEngine.apiDump()
                            onMain {
                                engineOk = XrayEngine.available()
                                probe = text
                            }
                        }
                    })
                    SmallButton(
                        if (copied) tr(lang, "COPIATO", "СКОПИРОВАНО") else tr(lang, "COPIA INFORME", "КОПИРОВАТЬ ОТЧЁТ"),
                        {
                            copyText(context, JolieProxy.report(context))
                            copied = true
                        },
                        primary = true
                    )
                }
                if (probe.isNotEmpty()) MonoText(probe, size = 10, color = JolieColors.Gray)
            }
        }

        if (JolieProxy.log.isNotEmpty()) {
            GlassSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionLabel(tr(lang, "REGISTRO", "ЖУРНАЛ"))
                    JolieProxy.log.takeLast(10).forEach { MonoText(it, size = 10, color = JolieColors.Gray) }
                }
            }
        }

        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(tr(lang, "ATTENZIONE", "ВНИМАНИЕ"))
                MonoText(
                    tr(
                        lang,
                        "Non usare questa VPN per banche, password o dati personali: il tunnel può essere poco sicuro. In Russia le liste gratuite cambiano ogni giorno e nessun sistema garantisce il 100%.",
                        "Не используйте этот VPN для банков, паролей и личных данных: туннель может быть небезопасным. В России бесплатные списки меняются каждый день, и 100% гарантии нет."
                    ),
                    size = 12
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Pantalla VPN: pestaña AUTOMATICA (un botón de encendido) y AVANZATE (apps recomendadas y nodos a mano). */
@Composable
fun VpnAutoScreen(lang: String, onResetSetup: () -> Unit) {
    var page by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabStrip(
            labels = listOf(tr(lang, "AUTOMATICA", "АВТО"), tr(lang, "AVANZATE", "РАСШИРЕННЫЕ")),
            selected = page,
            onSelect = { page = it }
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            if (page == 0) AutoPage(lang) else VpnTabScreen(lang, onResetSetup)
        }
    }
}
