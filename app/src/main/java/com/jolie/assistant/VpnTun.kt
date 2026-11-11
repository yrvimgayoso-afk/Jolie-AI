package com.jolie.assistant

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** Servicio VPN de Android: crea la interfaz TUN del teléfono y se la entrega a Xray. */
class JolieVpnService : VpnService() {
    companion object {
        @Volatile
        var instance: JolieVpnService? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onRevoke() {
        VpnTun.onRevoked()
        super.onRevoke()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** Crea la interfaz TUN y devuelve su descriptor (o -1). apps vacío = todas las apps menos Jolie. */
    fun openTun(apps: Set<String>): Int {
        return try {
            val b = Builder()
                .setSession("Jolie")
                .setMtu(1500)
                .addAddress("10.77.0.2", 30)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
            var added = 0
            for (p in apps) {
                try {
                    b.addAllowedApplication(p)
                    added++
                } catch (_: Exception) {
                }
            }
            if (added == 0) b.addDisallowedApplication(packageName)
            val pfd = b.establish()
            if (pfd == null) -1 else pfd.detachFd()
        } catch (_: Throwable) {
            -1
        }
    }
}

/** Descriptor del túnel que se pasa a Xray al arrancar, y entrada "tun" de su configuración. */
object TunState {
    @Volatile
    var fd: Int = 0

    fun inbounds(socks: JSONObject): JSONArray {
        val arr = JSONArray().put(socks)
        if (fd > 0) {
            arr.put(
                JSONObject()
                    .put("tag", "tun")
                    .put("protocol", "tun")
                    .put("settings", JSONObject().put("name", "xray0").put("MTU", 1500))
                    .put(
                        "sniffing",
                        JSONObject().put("enabled", true).put("destOverride", JSONArray().put("http").put("tls"))
                    )
            )
        }
        return arr
    }
}

/** Túnel de todo el teléfono: permiso del sistema, interfaz TUN y arranque de Xray con ella. */
object VpnTun {
    var active by mutableStateOf(false)
        private set

    @Volatile
    var permitted = false

    @Volatile
    var launcher: ((Intent) -> Unit)? = null

    private var pending: (() -> Unit)? = null

    /** Pide el permiso VPN de Android (si hace falta) y luego llama a onReady. */
    fun ensurePermission(context: Context, onReady: () -> Unit) {
        val intent = try {
            VpnService.prepare(context)
        } catch (_: Exception) {
            null
        }
        if (intent == null) {
            permitted = true
            onReady()
            return
        }
        val l = launcher
        if (l == null) {
            permitted = false
            onReady()
            return
        }
        pending = onReady
        l(intent)
    }

    fun onPermissionResult(ok: Boolean) {
        permitted = ok
        val p = pending
        pending = null
        p?.invoke()
    }

    fun onRevoked() {
        onMain { active = false }
        JolieProxy.stop()
    }

    fun close() {
        TunState.fd = 0
        onMain { active = false }
        try {
            JolieVpnService.instance?.stopSelf()
        } catch (_: Exception) {
        }
    }

    /** Convierte la conexión "solo Jolie" en túnel de todo el teléfono. Devuelve true si funcionó. */
    fun upgrade(app: Context, link: String, log: (String) -> Unit): Boolean {
        if (!permitted) {
            log("Tunnel di sistema: permesso VPN non concesso")
            return false
        }
        XrayEngine.stop()
        try {
            app.startService(Intent(app, JolieVpnService::class.java))
        } catch (e: Exception) {
            log("Servizio VPN: " + e.javaClass.simpleName)
            return false
        }
        var svc = JolieVpnService.instance
        var waited = 0
        while (svc == null && waited < 30) {
            Thread.sleep(100)
            waited++
            svc = JolieVpnService.instance
        }
        if (svc == null) {
            log("Servizio VPN non partito")
            return false
        }
        val fd = svc.openTun(VpnApps.selected(app))
        if (fd <= 0) {
            log("Interfaccia TUN non creata")
            return false
        }
        TunState.fd = fd
        val cfg = buildXrayConfig(link)
        val ok = cfg != null && XrayEngine.start(app, cfg)
        TunState.fd = 0
        if (!ok) {
            log("Tunnel: " + XrayEngine.lastError.take(140))
            val c2 = buildXrayConfig(link)
            if (c2 != null) XrayEngine.start(app, c2)
            close()
            return false
        }
        onMain { active = true }
        log("Tunnel di sistema attivo")
        return true
    }
}

/** Se pone una vez en pantalla: registra el lanzador del permiso VPN del sistema. */
@Composable
fun VpnPermissionHost() {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        VpnTun.onPermissionResult(res.resultCode == Activity.RESULT_OK)
    }
    DisposableEffect(Unit) {
        VpnTun.launcher = { intent -> launcher.launch(intent) }
        onDispose { VpnTun.launcher = null }
    }
}
