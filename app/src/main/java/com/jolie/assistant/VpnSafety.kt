package com.jolie.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import org.json.JSONObject

enum class NodeSafety { HIGH, MEDIUM, LOW }

class SafetyInfo(val level: NodeSafety, val reasonIt: String, val reasonRu: String)

private val OK_GREEN = Color(0xFF3DDC84)

private fun decodeB64(s: String): String {
    val t = s.trim().replace('-', '+').replace('_', '/')
    return String(Base64.decode(t, Base64.DEFAULT), Charsets.UTF_8)
}

/** Valuta quanto è protetto un nodo guardando il suo link. */
fun nodeSafety(link: String): SafetyInfo {
    val proto = link.substringBefore("://", "").lowercase()
    val query = link.substringAfter('?', "").substringBefore('#')
    val params = HashMap<String, String>()
    for (part in query.split('&')) {
        val i = part.indexOf('=')
        if (i > 0) params[part.substring(0, i).lowercase()] = part.substring(i + 1).lowercase()
    }
    val insecure = params["allowinsecure"] == "1" || params["allowinsecure"] == "true" ||
        params["insecure"] == "1" || params["insecure"] == "true"
    if (insecure) {
        return SafetyInfo(
            NodeSafety.LOW,
            "Accetta certificati falsi: il traffico può essere intercettato.",
            "Принимает поддельные сертификаты: трафик могут перехватить."
        )
    }
    when (proto) {
        "vless" -> {
            val sec = params["security"] ?: "none"
            return when (sec) {
                "reality" -> SafetyInfo(NodeSafety.HIGH, "Cifratura Reality: il traffico sembra normale HTTPS.", "Шифрование Reality: трафик похож на обычный HTTPS.")
                "tls" -> SafetyInfo(NodeSafety.HIGH, "Cifratura TLS.", "Шифрование TLS.")
                else -> SafetyInfo(NodeSafety.LOW, "Nessuna cifratura TLS: il traffico è esposto.", "Нет шифрования TLS: трафик открыт.")
            }
        }
        "trojan" -> {
            return if (params["security"] == "none") {
                SafetyInfo(NodeSafety.LOW, "Nessuna cifratura TLS: il traffico è esposto.", "Нет шифрования TLS: трафик открыт.")
            } else {
                SafetyInfo(NodeSafety.HIGH, "Cifratura TLS.", "Шифрование TLS.")
            }
        }
        "vmess" -> {
            val tls = try {
                JSONObject(decodeB64(link.substringAfter("://").substringBefore('#'))).optString("tls", "")
            } catch (_: Exception) {
                ""
            }
            return if (tls == "tls") {
                SafetyInfo(NodeSafety.HIGH, "Cifratura TLS.", "Шифрование TLS.")
            } else {
                SafetyInfo(NodeSafety.MEDIUM, "Cifrato ma facile da riconoscere e bloccare.", "Зашифровано, но легко распознаётся и блокируется.")
            }
        }
        "ss" -> return SafetyInfo(NodeSafety.MEDIUM, "Cifrato ma facile da riconoscere e bloccare.", "Зашифровано, но легко распознаётся и блокируется.")
    }
    return SafetyInfo(NodeSafety.LOW, "Protocollo sconosciuto.", "Неизвестный протокол.")
}

private class TrustedApp(val name: String, val pkg: String, val noteIt: String, val noteRu: String)

private val TRUSTED_APPS = listOf(
    TrustedApp(
        "Psiphon", "com.psiphon3",
        "Nato per aggirare la censura. Gratuito e senza registrazione.",
        "Создан для обхода цензуры. Бесплатный, без регистрации."
    ),
    TrustedApp(
        "Proton VPN (gratis)", "ch.protonvpn.android",
        "Piano gratuito senza limite di traffico e senza log, app open source. Pochi server: in Russia prova il protocollo Stealth.",
        "Бесплатный тариф без лимита трафика и без логов, открытый код. Серверов мало: в России попробуйте протокол Stealth."
    ),
    TrustedApp(
        "Amnezia VPN", "org.amnezia.vpn",
        "Open source, pensato per i blocchi russi. Piano gratuito limitato; il massimo con un server tuo.",
        "Открытый код, создан для российских блокировок. Бесплатный тариф ограничен; лучше всего со своим сервером."
    ),
    TrustedApp(
        "Cloudflare WARP (1.1.1.1)", "com.cloudflare.onedotonedotonedotone",
        "Azienda molto nota e veloce, ma in Russia spesso instabile.",
        "Известная компания и быстро, но в России часто нестабилен."
    ),
    TrustedApp(
        "Windscribe", "com.windscribe.vpn",
        "10 GB gratis al mese.",
        "10 ГБ в месяц бесплатно."
    ),
    TrustedApp(
        "Hiddify", "app.hiddify.com",
        "Client open source: serve per usare liste di nodi.",
        "Клиент с открытым кодом: нужен для списков узлов."
    )
)

private fun openPlay(context: Context, pkg: String) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
    }
}

@Composable
private fun InfoSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MonoText(title, size = 11, color = JolieColors.RedSoft, spacing = 1.5f)
            content()
        }
    }
}

@Composable
private fun TrustedScreen(lang: String) {
    val context = LocalContext.current
    val nodes = remember { mutableStateListOf<VpnNode>().apply { addAll(VpnStore.load(context)) } }
    val infos = nodes.map { it to nodeSafety(it.link) }
    val risky = infos.filter { it.second.level == NodeSafety.LOW }
    val high = infos.count { it.second.level == NodeSafety.HIGH }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        InfoSection(tr(lang, "PER INIZIARE (GRATIS)", "НАЧАТЬ (БЕСПЛАТНО)")) {
            MonoText(
                tr(
                    lang,
                    "1. Installa una delle app qui sotto e accendila.\n2. Torna in Jolie: l’indicatore in alto diventa verde.\n3. Nella scheda NODI premi CONTROLLA ORA.\n\nUna VPN attiva sul telefono protegge anche la chat di Jolie. Nessuna VPN gratuita è garantita in Russia: la disponibilità cambia ogni giorno e le più grandi vengono bloccate per prime, quindi tienine due o tre.",
                    "1. Установите одно из приложений ниже и включите его.\n2. Вернитесь в Джоли: индикатор сверху станет зелёным.\n3. На вкладке УЗЛЫ нажмите ПРОВЕРИТЬ.\n\nVPN на телефоне защищает и чат Джоли. Ни один бесплатный VPN не гарантирован в России: доступность меняется каждый день, а крупные блокируют первыми, поэтому держите два-три."
                ),
                size = 12
            )
        }

        TRUSTED_APPS.forEach { app ->
            InfoSection(app.name.uppercase()) {
                MonoText(tr(lang, app.noteIt, app.noteRu), size = 12)
                GlassButton(
                    tr(lang, "APRI SU GOOGLE PLAY", "ОТКРЫТЬ В GOOGLE PLAY"),
                    onClick = { openPlay(context, app.pkg) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        InfoSection(tr(lang, "SICUREZZA DEI NODI SALVATI", "БЕЗОПАСНОСТЬ СОХРАНЁННЫХ УЗЛОВ")) {
            if (nodes.isEmpty()) {
                MonoText(tr(lang, "Nessun nodo salvato.", "Сохранённых узлов нет."), size = 12, color = JolieColors.Gray)
            } else {
                MonoText(
                    tr(
                        lang,
                        "Nodi: ${nodes.size}. Ben protetti: $high. A rischio: ${risky.size}.",
                        "Узлов: ${nodes.size}. Защищённых: $high. Рискованных: ${risky.size}."
                    ),
                    size = 12,
                    color = if (risky.isEmpty()) OK_GREEN else JolieColors.RedSoft
                )
                risky.take(8).forEach { (n, info) ->
                    MonoText(n.name.take(28), size = 12)
                    MonoText(tr(lang, info.reasonIt, info.reasonRu), size = 10, color = JolieColors.Gray)
                }
                if (risky.isNotEmpty()) {
                    GlassButton(
                        tr(lang, "RIMUOVI I NODI A RISCHIO", "УДАЛИТЬ РИСКОВАННЫЕ УЗЛЫ"),
                        onClick = {
                            val safe = nodes.filter { nodeSafety(it.link).level != NodeSafety.LOW }
                            nodes.clear()
                            nodes.addAll(safe)
                            VpnStore.save(context, safe)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        filled = true
                    )
                }
            }
            MonoText(
                tr(
                    lang,
                    "Anche un nodo ben protetto lascia vedere a chi lo gestisce a quali siti ti colleghi. Per banca e password usa solo un server tuo. Scarica le app solo da Google Play o dal GitHub ufficiale, mai da APK in canali Telegram sconosciuti.",
                    "Даже защищённый узел позволяет владельцу видеть, к каким сайтам вы подключаетесь. Для банков и паролей используйте только свой сервер. Скачивайте приложения только из Google Play или с официального GitHub, никогда не из APK в незнакомых Telegram-каналах."
                ),
                size = 11,
                color = JolieColors.Gray
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Scheda VPN: due pagine, "CONSIGLIATI" (app affidabili e sicurezza) e "NODI" (la gestione dei nodi). */
@Composable
fun VpnTabScreen(lang: String, onResetSetup: () -> Unit) {
    var page by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassButton(tr(lang, "CONSIGLIATI", "СОВЕТУЕМ"), onClick = { page = 0 }, modifier = Modifier.weight(1f), filled = page == 0)
            GlassButton(tr(lang, "NODI", "УЗЛЫ"), onClick = { page = 1 }, modifier = Modifier.weight(1f), filled = page == 1)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (page == 0) TrustedScreen(lang) else VpnHubScreen(lang, onResetSetup)
        }
    }
}
