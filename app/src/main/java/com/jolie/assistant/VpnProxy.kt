package com.jolie.assistant

import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private val LIST_FILES = listOf(
    "Vless-Reality-White-Lists-Rus-Mobile-2.txt",
    "BLACK_VLESS_RUS_mobile.txt",
    "BLACK_VLESS_RUS.txt"
)

/** Varios espejos de la misma lista pública: si uno está bloqueado se prueba el siguiente. */
private val LIST_MIRRORS = listOf(
    "https://raw.githubusercontent.com/igareck/vpn-configs-for-russia/refs/heads/main/",
    "https://cdn.jsdelivr.net/gh/igareck/vpn-configs-for-russia@main/",
    "https://gitlab.com/igareck/vpn-configs-for-russia/-/raw/main/",
    "https://codeberg.org/igareck/vpn-configs-for-russia/raw/branch/main/",
    "https://raw.githack.com/igareck/vpn-configs-for-russia/main/"
)

/** VPN automática de Jolie: busca nodos, los prueba, conecta y cambia sola cuando uno cae. */
object JolieProxy {
    enum class State { OFF, SEARCHING, CONNECTING, CONNECTED, ERROR }

    var state by mutableStateOf(State.OFF)
        private set
    var message by mutableStateOf("")
        private set
    var nodeName by mutableStateOf("")
        private set
    val log = mutableStateListOf<String>()

    @Volatile
    private var generation = 0

    @Volatile
    private var routing = false

    @Volatile
    private var lang = "IT"

    @Volatile
    private var engineFatal = false

    private var installed = false

    /** Mientras hay túnel, todo el tráfico de Jolie (chat, fotos IA) pasa por el proxy local. */
    fun install() {
        if (installed) return
        installed = true
        val previous = ProxySelector.getDefault()
        ProxySelector.setDefault(object : ProxySelector() {
            override fun select(uri: URI?): MutableList<Proxy> {
                val host = uri?.host ?: ""
                if (routing && host != "127.0.0.1" && host != "localhost") {
                    return mutableListOf(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", SOCKS_PORT)))
                }
                return previous?.select(uri) ?: mutableListOf(Proxy.NO_PROXY)
            }

            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
                previous?.connectFailed(uri, sa, ioe)
            }
        })
    }

    fun openConnection(url: URL): HttpURLConnection {
        val conn = if (routing) {
            url.openConnection(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", SOCKS_PORT)))
        } else {
            url.openConnection()
        }
        return conn as HttpURLConnection
    }

    fun toggle(context: Context, language: String) {
        if (state == State.OFF || state == State.ERROR) VpnTun.ensurePermission(context) { start(context, language) } else stop()
    }

    fun start(context: Context, language: String) {
        lang = language
        engineFatal = false
        generation++
        val gen = generation
        state = State.SEARCHING
        message = tr(lang, "Cerco una connessione…", "Ищу подключение…")
        nodeName = ""
        val app = context.applicationContext
        runAsync { supervise(app, gen) }
    }

    fun stop() {
        generation++
        routing = false
        state = State.OFF
        message = ""
        nodeName = ""
        runAsync { XrayEngine.stop(); VpnTun.close() }
    }

    private fun ui(gen: Int, block: () -> Unit) {
        onMain { if (gen == generation) block() }
    }

    private fun addLog(gen: Int, text: String) {
        ui(gen) {
            log.add(text)
            while (log.size > 40) log.removeAt(0)
        }
    }

    private fun sleep(ms: Long, gen: Int) {
        var left = ms
        while (left > 0 && gen == generation) {
            Thread.sleep(500)
            left -= 500
        }
    }

    /** Comprueba que internet funciona de verdad a través del proxy local. */
    private fun verify(): Boolean {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", SOCKS_PORT))
        for (u in listOf("https://www.google.com/generate_204", "https://www.youtube.com/generate_204")) {
            try {
                val c = URL(u).openConnection(proxy) as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 8000
                c.instanceFollowRedirects = false
                val code = c.responseCode
                c.disconnect()
                if (code in 200..399) return true
            } catch (_: Exception) {
            }
        }
        return false
    }

    private fun safetyRank(n: VpnNode): Int = when (nodeSafety(n.link).level) {
        NodeSafety.HIGH -> 0
        NodeSafety.MEDIUM -> 1
        NodeSafety.LOW -> 2
    }

    /** Descarga listas públicas, añade los nodos guardados, mide cuáles responden y los ordena. */
    private fun discover(app: Context, gen: Int): List<VpnNode> {
        ui(gen) {
            state = State.SEARCHING
            message = tr(lang, "Cerco nodi in rete…", "Ищу узлы в сети…")
        }
        val found = ArrayList<VpnNode>()
        val deadline = System.currentTimeMillis() + 45000
        for (file in LIST_FILES) {
            for (mirror in LIST_MIRRORS) {
                if (gen != generation || System.currentTimeMillis() > deadline) break
                try {
                    val nodes = parseNodes(fetchText(mirror + file))
                    if (nodes.isNotEmpty()) {
                        found.addAll(nodes)
                        addLog(gen, file + ": " + nodes.size)
                        break
                    }
                } catch (_: Exception) {
                }
            }
        }
        if (found.isEmpty()) {
            addLog(gen, tr(lang, "Liste online non raggiungibili: uso i nodi salvati.", "Онлайн-списки недоступны: использую сохранённые узлы."))
        }
        val sample = (VpnStore.load(app) + found.shuffled()).distinctBy { it.link }.take(90)
        val latch = CountDownLatch(sample.size)
        val pings = ConcurrentHashMap<String, Int>()
        for (n in sample) {
            runPooled {
                pings[n.link] = tcpPing(n.host, n.port, 2500)
                latch.countDown()
            }
        }
        latch.await(40, TimeUnit.SECONDS)
        val alive = sample.filter { (pings[it.link] ?: -1) >= 0 }
        addLog(gen, tr(lang, "Rispondono " + alive.size + " nodi su " + sample.size, "Отвечают " + alive.size + " из " + sample.size))
        return alive.sortedWith(compareBy<VpnNode>({ safetyRank(it) }, { pings[it.link] ?: 9999 })).take(14)
    }

    /** Prueba los nodos uno por uno desde "from". Devuelve el índice del que funciona o -1. */
    private fun tryNodes(app: Context, gen: Int, nodes: List<VpnNode>, from: Int): Int {
        for (k in nodes.indices) {
            if (gen != generation || engineFatal) return -1
            val i = (from + k) % nodes.size
            val n = nodes[i]
            ui(gen) {
                state = State.CONNECTING
                nodeName = n.name
                message = tr(lang, "Provo il nodo " + (k + 1) + " di " + nodes.size + "…", "Пробую узел " + (k + 1) + " из " + nodes.size + "…")
            }
            val cfg = buildXrayConfig(n.link) ?: continue
            if (!XrayEngine.start(app, cfg)) {
                addLog(gen, "Motore: " + XrayEngine.lastError)
                if (XrayEngine.fatal) {
                    engineFatal = true
                    return -1
                }
                continue
            }
            sleep(900, gen)
            if (gen == generation && verify()) {
                VpnTun.upgrade(app, n.link) { addLog(gen, it) }
                routing = true
                addLog(gen, "OK: " + n.name.take(30))
                ui(gen) {
                    state = State.CONNECTED
                    nodeName = n.name
                    message = tr(
                        lang,
                        "Connesso. Chat e foto IA di Jolie passano dal tunnel.",
                        "Подключено. Чат и ИИ-фото Джоли идут через туннель."
                    )
                }
                return i
            }
            addLog(gen, "No: " + n.name.take(30))
            XrayEngine.stop()
        }
        return -1
    }

    private fun supervise(app: Context, gen: Int) {
        if (!XrayEngine.available()) {
            ui(gen) {
                state = State.ERROR
                message = tr(
                    lang,
                    "Motore Xray assente in questa versione dell’app. Premi PROVA MOTORE.",
                    "Движок Xray отсутствует в этой версии. Нажмите ПРОВЕРИТЬ ДВИЖОК."
                )
            }
            return
        }
        while (gen == generation) {
            routing = false
            val top = discover(app, gen)
            var idx = if (top.isEmpty()) -1 else tryNodes(app, gen, top, 0)
            if (gen != generation) return
            if (idx < 0) {
                ui(gen) {
                    state = State.ERROR
                    message = if (engineFatal) {
                        tr(lang, "Motore non compatibile: premi PROVA MOTORE.", "Движок несовместим: нажмите ПРОВЕРИТЬ ДВИЖОК.")
                    } else {
                        tr(lang, "Nessun nodo funziona ora. Riprovo tra 30 secondi.", "Ни один узел не работает. Повтор через 30 секунд.")
                    }
                }
                if (engineFatal) return
                sleep(30000, gen)
                continue
            }
            var fails = 0
            while (gen == generation) {
                sleep(25000, gen)
                if (gen != generation) return
                if (verify()) {
                    fails = 0
                    continue
                }
                fails++
                if (fails < 2) continue
                routing = false
                ui(gen) {
                    state = State.CONNECTING
                    message = tr(lang, "Connessione persa: cerco un altro nodo…", "Связь потеряна: ищу другой узел…")
                }
                idx = tryNodes(app, gen, top, idx + 1)
                if (idx < 0) break
                fails = 0
            }
        }
    }

    /** Informe de diagnóstico para copiar y enviar (útil para quien prueba en Moscú). */
    fun report(context: Context): String {
        val sb = StringBuilder()
        sb.append("Jolie VPN report\n")
        sb.append("Phone: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            .append(" Android ").append(Build.VERSION.RELEASE).append('\n')
        sb.append("State: ").append(state).append(" | ").append(message).append('\n')
        sb.append("Node: ").append(nodeName).append('\n')
        sb.append("Engine: ").append(if (XrayEngine.available()) "present " + XrayEngine.version() else "missing").append('\n')
        sb.append("Engine error: ").append(XrayEngine.lastError).append('\n')
        sb.append("System VPN: ").append(isVpnActive(context)).append('\n')
        sb.append("Log:\n")
        log.forEach { sb.append(it).append('\n') }
        sb.append(XrayEngine.apiDump())
        return sb.toString()
    }
}
