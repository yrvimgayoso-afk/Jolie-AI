package com.jolie.assistant

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val TUNNEL_GREEN = Color(0xFF3DDC84)

private val RECOMMENDED = listOf(
    "com.google.android.youtube",
    "com.instagram.android",
    "org.telegram.messenger",
    "com.whatsapp",
    "com.facebook.katana",
    "com.twitter.android",
    "com.android.chrome"
)

/** Apps que pasan por el túnel. Lista vacía = todas las apps del teléfono. */
object VpnApps {
    private fun sp(c: Context) =
        c.applicationContext.getSharedPreferences("jolie_vpn_apps", Context.MODE_PRIVATE)

    fun selected(c: Context): Set<String> = HashSet(sp(c).getStringSet("apps", emptySet()) ?: emptySet())

    fun save(c: Context, s: Collection<String>) {
        sp(c).edit().putStringSet("apps", HashSet(s)).apply()
    }

    fun launchable(c: Context): List<Pair<String, String>> {
        return try {
            val pm = c.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, 0)
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .filter { it.first != c.packageName }
                .distinctBy { it.first }
                .sortedBy { it.second.lowercase() }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

/** Tarjeta "qué pasa por el túnel": todas las apps o solo las elegidas. */
@Composable
fun VpnAppsCard(lang: String) {
    val context = LocalContext.current
    val installed = remember { VpnApps.launchable(context) }
    val chosen = remember { mutableStateListOf<String>().apply { addAll(VpnApps.selected(context)) } }
    var all by remember { mutableStateOf(chosen.isEmpty()) }
    var showAll by remember { mutableStateOf(false) }

    fun toggle(pkg: String) {
        if (chosen.contains(pkg)) chosen.remove(pkg) else chosen.add(pkg)
        VpnApps.save(context, chosen)
    }

    GlassSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel(tr(lang, "COSA PASSA DAL TUNNEL", "ЧТО ИДЁТ ЧЕРЕЗ ТУННЕЛЬ"))
            val names = installed.filter { chosen.contains(it.first) }.map { it.second }
            MonoText(
                when {
                    VpnTun.active && (all || chosen.isEmpty()) ->
                        tr(lang, "ATTIVO per tutte le app del telefono.", "АКТИВЕН для всех приложений телефона.")
                    VpnTun.active ->
                        tr(lang, "ATTIVO per: ", "АКТИВЕН для: ") + names.joinToString(", ")
                    else ->
                        tr(lang, "Accendi la VPN: il tunnel userà le app scelte qui sotto.", "Включите VPN: туннель будет работать для выбранных ниже приложений.")
                },
                size = 12,
                color = if (VpnTun.active) TUNNEL_GREEN else Color.White
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OptionChip(tr(lang, "TUTTE LE APP", "ВСЕ"), all, {
                    all = true
                    chosen.clear()
                    VpnApps.save(context, chosen)
                })
                OptionChip(tr(lang, "SCEGLI APP", "ВЫБРАТЬ"), !all, { all = false })
            }
            if (!all) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    installed.filter { RECOMMENDED.contains(it.first) }.forEach { app ->
                        OptionChip(app.second, chosen.contains(app.first), { toggle(app.first) })
                    }
                }
                SmallButton(
                    if (showAll) tr(lang, "NASCONDI ELENCO", "СКРЫТЬ СПИСОК") else tr(lang, "ELENCO COMPLETO", "ПОЛНЫЙ СПИСОК"),
                    { showAll = !showAll }
                )
                if (showAll) {
                    installed.forEach { app ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MonoText(app.second.take(28), size = 12, modifier = Modifier.weight(1f))
                            OptionChip(
                                if (chosen.contains(app.first)) tr(lang, "SÌ", "ДА") else tr(lang, "NO", "НЕТ"),
                                chosen.contains(app.first),
                                { toggle(app.first) }
                            )
                        }
                    }
                }
            }
            MonoText(
                tr(lang, "Le modifiche valgono alla prossima accensione.", "Изменения применяются при следующем включении."),
                size = 10,
                color = JolieColors.Gray
            )
        }
    }
}
