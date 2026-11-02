package com.jolie.assistant

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.Executors

data class VpnNode(val link: String, val name: String, val protocol: String, val host: String, val port: Int)
class Probe(val label: String, val url: String)
class EnginePkg(val label: String, val pkg: String)

val PROBES = listOf(
    Probe("YouTube", "https://www.youtube.com/"),
    Probe("Gemini", "https://generativelanguage.googleapis.com/"),
    Probe("Telegram", "https://telegram.org/"),
    Probe("Google", "https://www.google.com/generate_204")
)

val ENGINES = listOf(
    EnginePkg("v2rayNG", "com.v2ray.ang"),
    EnginePkg("Hiddify", "app.hiddify.com"),
    EnginePkg("NekoBox", "moe.nb4a"),
    EnginePkg("sing-box", "io.nekohasekai.sfa")
)

private val mainHandler = Handler(Looper.getMainLooper())
private val pool = Executors.newFixedThreadPool(8)

fun onMain(block: () -> Unit) {
    mainHandler.post { block() }
}

fun runAsync(block: () -> Unit) {
    Thread { block() }.start()
}

fun runPooled(block: () -> Unit) {
    pool.execute { block() }
}

private fun b64(s: String): String {
    val t = s.trim().replace('-', '+').replace('_', '/')
    return String(Base64.decode(t, Base64.DEFAULT), Charsets.UTF_8)
}

/** Legge un link vless://, vmess://, trojan:// o ss:// e ne ricava nome, server e porta. */
fun parseNode(raw: String): VpnNode? {
    val link = raw.trim()
    val proto = link.substringBefore("://", "").lowercase()
    if (proto != "vless" && proto != "vmess" && proto != "trojan" && proto != "ss") return null
    val frag = link.substringAfter('#', "")
    val name = try {
        URLDecoder.decode(frag, "UTF-8")
    } catch (_: Exception) {
        frag
    }
    return try {
        if (proto == "vmess") {
            val json = JSONObject(b64(link.substringAfter("://").substringBefore('#')))
            VpnNode(link, json.optString("ps", "vmess"), "vmess", json.getString("add"), json.get("port").toString().toInt())
        } else {
            var rest = link.substringAfter("://").substringBefore('#')
            if (proto == "ss" && !rest.contains('@')) rest = b64(rest.substringBefore('?'))
            val hostPort = rest.substringAfterLast('@').substringBefore('?').substringBefore('/')
            val host = hostPort.substringBeforeLast(':').trim('[', ']')
            val port = hostPort.substringAfterLast(':').toInt()
            VpnNode(link, if (name.isNotEmpty()) name else host, proto, host, port)
        }
    } catch (_: Exception) {
        null
    }
}

/** Accetta uno o più link, oppure una lista (anche codificata base64) come quella di un abbonamento. */
fun parseNodes(text: String): List<VpnNode> {
    val ws = Regex("\\s+")
    val direct = text.trim().split(ws).mapNotNull { parseNode(it) }
    if (direct.isNotEmpty()) return direct.distinctBy { it.link }
    return try {
        b64(text.trim()).split(ws).mapNotNull { parseNode(it) }.distinctBy { it.link }
    } catch (_: Exception) {
        emptyList()
    }
}

fun fetchText(url: String): String {
    val c = URL(url).openConnection() as HttpURLConnection
    try {
        c.connectTimeout = 15000
        c.readTimeout = 15000
        if (c.responseCode !in 200..299) throw IOException("HTTP " + c.responseCode)
        return c.inputStream.bufferedReader().use { it.readText() }.take(2000000)
    } finally {
        c.disconnect()
    }
}

object VpnStore {
    private fun sp(context: Context) =
        context.applicationContext.getSharedPreferences("jolie_vpn", Context.MODE_PRIVATE)

    fun load(context: Context): List<VpnNode> =
        try {
            val arr = JSONArray(sp(context).getString("nodes", "[]") ?: "[]")
            (0 until arr.length()).mapNotNull { parseNode(arr.getString(it)) }
        } catch (_: Exception) {
            emptyList()
        }

    fun save(context: Context, nodes: List<VpnNode>) {
        val arr = JSONArray()
        nodes.forEach { arr.put(it.link) }
        sp(context).edit().putString("nodes", arr.toString()).apply()
    }
}

/** Tempo di connessione al server in millisecondi, oppure -1 se non risponde. */
fun tcpPing(host: String, port: Int, timeoutMs: Int = 3000): Int {
    val start = System.nanoTime()
    return try {
        Socket().use { s -> s.connect(InetSocketAddress(host, port), timeoutMs) }
        ((System.nanoTime() - start) / 1000000).toInt()
    } catch (_: Exception) {
        -1
    }
}

/** Vero se il sito risponde davvero (anche con un errore HTTP): significa che non è bloccato. */
fun probeUrl(url: String): Boolean {
    return try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 6000
        c.readTimeout = 6000
        c.instanceFollowRedirects = false
        val code = c.responseCode
        c.disconnect()
        code > 0
    } catch (_: Exception) {
        false
    }
}

/** Vero se sul telefono c'è una VPN attiva (di qualsiasi app). */
fun isVpnActive(context: Context): Boolean {
    return try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    } catch (_: Exception) {
        false
    }
}

fun installedEngines(context: Context): List<EnginePkg> =
    ENGINES.filter { context.packageManager.getLaunchIntentForPackage(it.pkg) != null }
