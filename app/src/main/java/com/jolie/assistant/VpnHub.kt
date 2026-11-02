package com.jolie.assistant

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.util.concurrent.atomic.AtomicInteger

private val GREEN = Color(0xFF3DDC84)

@Composable
private fun OnResume(callback: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val latest by rememberUpdatedState(callback)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) latest()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

/** Indicatore in alto: mostra lo stato REALE della VPN del telefono. */
@Composable
fun VpnPill(lang: String) {
    val context = LocalContext.current
    var on by remember { mutableStateOf(isVpnActive(context)) }
    OnResume { on = isVpnActive(context) }
    GlassSurface(shape = RoundedCornerShape(17.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (on) GREEN else JolieColors.Gray))
            Spacer(Modifier.width(8.dp))
            MonoText(
                if (on) tr(lang, "VPN ATTIVA", "VPN ВКЛ.") else tr(lang, "VPN SPENTA", "VPN ВЫКЛ."),
                size = 11,
                spacing = 1f
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MonoText(title, size = 11, color = JolieColors.RedSoft, spacing = 1.5f)
            content()
        }
    }
}

@Composable
private fun LinkField(value: String, hint: String, onChange: (String) -> Unit) {
    GlassSurface(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            textStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
            cursorBrush = SolidColor(Color.White),
            singleLine = true,
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) MonoText(hint, size = 12, color = JolieColors.Gray)
                    inner()
                }
            }
        )
    }
}

private fun readClipboard(context: Context): String =
    try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        if (clip == null || clip.itemCount == 0) "" else clip.getItemAt(0).coerceToText(context)?.toString()?.trim() ?: ""
    } catch (_: Exception) {
        ""
    }

private fun copyToClipboard(context: Context, text: String) {
    try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("vpn", text))
    } catch (_: Exception) {
    }
}

@Composable
fun VpnHubScreen(lang: String, onResetSetup: () -> Unit) {
    val context = LocalContext.current
    val nodes = remember { mutableStateListOf<VpnNode>().apply { addAll(VpnStore.load(context)) } }
    val pings = remember { mutableStateMapOf<String, Int>() }
    val probes = remember { mutableStateMapOf<String, Boolean>() }
    var vpnOn by remember { mutableStateOf(isVpnActive(context)) }
    var engines by remember { mutableStateOf(installedEngines(context)) }
    var checking by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }

    OnResume {
        vpnOn = isVpnActive(context)
        engines = installedEngines(context)
    }

    fun addNodes(found: List<VpnNode>) {
        var added = 0
        for (n in found) {
            if (nodes.size < 200 && nodes.none { it.link == n.link }) {
                nodes.add(n)
                added++
            }
        }
        VpnStore.save(context, nodes.toList())
        status = if (found.isEmpty()) {
            tr(lang, "Nessun nodo valido trovato (servono link vless, vmess, trojan o ss).", "Подходящих узлов не найдено (нужны ссылки vless, vmess, trojan или ss).")
        } else {
            tr(lang, "Aggiunti $added nodi.", "Добавлено узлов: $added.")
        }
        input = ""
    }

    fun addFromText(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        if (t.startsWith("http://") || t.startsWith("https://")) {
            status = tr(lang, "Scarico la lista…", "Загружаю список…")
            runAsync {
                val result = try {
                    parseNodes(fetchText(t))
                } catch (_: Exception) {
                    null
                }
                onMain {
                    if (result == null) {
                        status = tr(lang, "Impossibile scaricare la lista (controlla il link o la rete).", "Не удалось загрузить список (проверьте ссылку или сеть).")
                    } else {
                        addNodes(result)
                    }
                }
            }
        } else {
            addNodes(parseNodes(t))
        }
    }

    fun runChecks() {
        if (checking) return
        checking = true
        probes.clear()
        val counter = AtomicInteger(0)
        for (p in PROBES) {
            runAsync {
                val ok = probeUrl(p.url)
                onMain {
                    probes[p.label] = ok
                    if (counter.incrementAndGet() == PROBES.size) checking = false
                    vpnOn = isVpnActive(context)
                }
            }
        }
    }

    fun testAll() {
        if (nodes.isEmpty() || testing) return
        testing = true
        pings.clear()
        val list = nodes.toList()
        val counter = AtomicInteger(0)
        for (n in list) {
            runPooled {
                val ms = tcpPing(n.host, n.port)
                onMain {
                    pings[n.link] = ms
                    if (counter.incrementAndGet() == list.size) testing = false
                }
            }
        }
    }

    val best = nodes.filter { (pings[it.link] ?: -1) >= 0 }.minByOrNull { pings[it.link] ?: Int.MAX_VALUE }

    fun copyBest() {
        val b = best
        if (b == null) {
            status = tr(lang, "Prima premi TESTA TUTTI: serve almeno un nodo che risponde.", "Сначала нажмите ПРОВЕРИТЬ ВСЕ: нужен хотя бы один отвечающий узел.")
            return
        }
        copyToClipboard(context, b.link)
        status = tr(
            lang,
            "Copiato: ${b.name.take(30)}. Nel motore VPN tocca + e poi “Importa dagli appunti”.",
            "Скопировано: ${b.name.take(30)}. В VPN-клиенте нажмите + и «Импорт из буфера»."
        )
    }

    fun openEngine() {
        val e = engines.firstOrNull()
        try {
            if (e == null) {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/2dust/v2rayNG/releases"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } else {
                val intent = context.packageManager.getLaunchIntentForPackage(e.pkg)
                if (intent != null) context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        } catch (_: Exception) {
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Section(tr(lang, "STATO REALE", "РЕАЛЬНОЕ СОСТОЯНИЕ")) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MonoText(tr(lang, "VPN del telefono", "VPN телефона"), size = 12)
                MonoText(
                    if (vpnOn) tr(lang, "ATTIVA", "ВКЛ.") else tr(lang, "SPENTA", "ВЫКЛ."),
                    size = 12,
                    color = if (vpnOn) GREEN else JolieColors.Gray
                )
            }
            PROBES.forEach { p ->
                val r = probes[p.label]
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MonoText(p.label, size = 12)
                    MonoText(
                        when {
                            r == null -> if (checking) "…" else "—"
                            r -> tr(lang, "RAGGIUNGIBILE", "ДОСТУПЕН")
                            else -> tr(lang, "BLOCCATO", "ЗАБЛОКИРОВАН")
                        },
                        size = 12,
                        color = when {
                            r == null -> JolieColors.Gray
                            r -> GREEN
                            else -> JolieColors.RedSoft
                        }
                    )
                }
            }
            GlassButton(
                if (checking) tr(lang, "CONTROLLO…", "ПРОВЕРКА…") else tr(lang, "CONTROLLA ORA", "ПРОВЕРИТЬ"),
                onClick = { runChecks() },
                modifier = Modifier.fillMaxWidth(),
                filled = true
            )
        }

        Section(tr(lang, "NODI VPN", "УЗЛЫ VPN")) {
            MonoText(
                tr(
                    lang,
                    "Incolla un link (vless, vmess, trojan, ss) o l’indirizzo di una lista di nodi.",
                    "Вставьте ссылку (vless, vmess, trojan, ss) или адрес списка узлов."
                ),
                size = 12
            )
            LinkField(input, "vless://… oppure https://…", { input = it })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton(tr(lang, "AGGIUNGI", "ДОБАВИТЬ"), onClick = { addFromText(input) }, modifier = Modifier.weight(1f))
                GlassButton(tr(lang, "DAGLI APPUNTI", "ИЗ БУФЕРА"), onClick = { addFromText(readClipboard(context)) }, modifier = Modifier.weight(1f))
            }
            nodes.take(30).forEach { n ->
                val p = pings[n.link]
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        MonoText(n.name.take(30), size = 12)
                        MonoText(n.protocol.uppercase() + " · " + n.host.take(24) + ":" + n.port, size = 10, color = JolieColors.Gray)
                    }
                    MonoText(
                        if (p == null) "—" else if (p < 0) tr(lang, "NO", "НЕТ") else "$p ms",
                        size = 11,
                        color = if (p == null) JolieColors.Gray else if (p < 0) JolieColors.RedSoft else GREEN
                    )
                    Box(
                        Modifier
                            .size(44.dp)
                            .clickable {
                                nodes.remove(n)
                                pings.remove(n.link)
                                VpnStore.save(context, nodes.toList())
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✕", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
            if (nodes.size > 30) MonoText("+" + (nodes.size - 30), size = 11, color = JolieColors.Gray)
            if (nodes.isNotEmpty()) {
                GlassButton(
                    if (testing) tr(lang, "TEST IN CORSO…", "ПРОВЕРКА…") else tr(lang, "TESTA TUTTI", "ПРОВЕРИТЬ ВСЕ"),
                    onClick = { testAll() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Section(tr(lang, "COLLEGARSI", "ПОДКЛЮЧЕНИЕ")) {
            MonoText(
                if (engines.isEmpty()) {
                    tr(lang, "Nessun motore VPN installato. Il tunnel dentro Jolie arriva con la prossima versione; per ora serve v2rayNG o simili.", "VPN-клиент не установлен. Встроенный туннель появится в следующей версии; пока нужен v2rayNG или похожий.")
                } else {
                    tr(lang, "Motore trovato: " + engines.joinToString { it.label }, "Найден клиент: " + engines.joinToString { it.label })
                },
                size = 12
            )
            GlassButton(tr(lang, "COPIA IL NODO MIGLIORE", "СКОПИРОВАТЬ ЛУЧШИЙ УЗЕЛ"), onClick = { copyBest() }, modifier = Modifier.fillMaxWidth(), filled = true)
            GlassButton(
                if (engines.isEmpty()) tr(lang, "SCARICA MOTORE", "СКАЧАТЬ КЛИЕНТ") else tr(lang, "APRI MOTORE VPN", "ОТКРЫТЬ VPN-КЛИЕНТ"),
                onClick = { openEngine() },
                modifier = Modifier.fillMaxWidth()
            )
            if (status.isNotEmpty()) MonoText(status, size = 12, color = JolieColors.RedSoft)
        }

        Section(tr(lang, "ATTENZIONE", "ВНИМАНИЕ")) {
            MonoText(
                tr(
                    lang,
                    "I nodi gratuiti pubblici sono gestiti da sconosciuti: possono vedere il traffico non cifrato. Non usarli per banca, password o dati personali. Un server tuo è molto più sicuro.",
                    "Бесплатные публичные узлы содержат неизвестные владельцы: они могут видеть незашифрованный трафик. Не используйте их для банков, паролей и личных данных. Собственный сервер намного безопаснее."
                ),
                size = 12
            )
        }

        GlassButton(
            tr(lang, "RIFAI CONFIGURAZIONE INIZIALE", "ПОВТОРИТЬ НАСТРОЙКУ"),
            onClick = onResetSetup,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
    }
}
