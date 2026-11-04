package com.jolie.assistant

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.net.URLDecoder

const val SOCKS_PORT = 10808

private fun urlDecode(s: String): String = try {
    URLDecoder.decode(s, "UTF-8")
} catch (_: Exception) {
    s
}

private fun b64Text(s: String): String {
    val t = s.trim().replace('-', '+').replace('_', '/')
    return String(Base64.decode(t, Base64.DEFAULT), Charsets.UTF_8)
}

private fun queryParams(link: String): Map<String, String> {
    val q = link.substringAfter('?', "").substringBefore('#')
    val m = HashMap<String, String>()
    for (part in q.split('&')) {
        val i = part.indexOf('=')
        if (i > 0) m[part.substring(0, i)] = urlDecode(part.substring(i + 1))
    }
    return m
}

private fun streamSettings(net: String, security: String, p: Map<String, String>, host: String): JSONObject {
    val s = JSONObject()
    val network = if (net.isEmpty() || net == "raw") "tcp" else net
    s.put("network", network)
    when (network) {
        "ws" -> s.put(
            "wsSettings",
            JSONObject().put("path", p["path"] ?: "/").put("headers", JSONObject().put("Host", p["host"] ?: ""))
        )
        "grpc" -> s.put(
            "grpcSettings",
            JSONObject().put("serviceName", p["serviceName"] ?: "").put("multiMode", p["mode"] == "multi")
        )
        "httpupgrade" -> s.put(
            "httpupgradeSettings",
            JSONObject().put("path", p["path"] ?: "/").put("host", p["host"] ?: "")
        )
        "xhttp", "splithttp" -> s.put(
            "xhttpSettings",
            JSONObject().put("path", p["path"] ?: "/").put("host", p["host"] ?: "").put("mode", p["mode"] ?: "auto")
        )
    }
    s.put("security", security)
    if (security == "tls") {
        val tls = JSONObject()
            .put("serverName", p["sni"] ?: host)
            .put("fingerprint", p["fp"] ?: "chrome")
            .put("allowInsecure", p["allowInsecure"] == "1" || p["insecure"] == "1")
        val alpn = p["alpn"]
        if (!alpn.isNullOrEmpty()) tls.put("alpn", JSONArray(alpn.split(",")))
        s.put("tlsSettings", tls)
    } else if (security == "reality") {
        s.put(
            "realitySettings",
            JSONObject()
                .put("serverName", p["sni"] ?: "")
                .put("fingerprint", p["fp"] ?: "chrome")
                .put("publicKey", p["pbk"] ?: "")
                .put("shortId", p["sid"] ?: "")
                .put("spiderX", p["spx"] ?: "")
        )
    }
    return s
}

private fun outbound(protocol: String, settings: JSONObject, stream: JSONObject?): JSONObject {
    val o = JSONObject().put("tag", "proxy").put("protocol", protocol).put("settings", settings)
    if (stream != null) o.put("streamSettings", stream)
    return o
}

private fun vlessOutbound(link: String): JSONObject? {
    val node = parseNode(link) ?: return null
    val p = queryParams(link)
    val uuid = link.substringAfter("://").substringBefore('@')
    val user = JSONObject().put("id", uuid).put("encryption", p["encryption"] ?: "none")
    val flow = p["flow"]
    if (!flow.isNullOrEmpty()) user.put("flow", flow)
    val server = JSONObject().put("address", node.host).put("port", node.port).put("users", JSONArray().put(user))
    return outbound(
        "vless",
        JSONObject().put("vnext", JSONArray().put(server)),
        streamSettings(p["type"] ?: "tcp", p["security"] ?: "none", p, node.host)
    )
}

private fun trojanOutbound(link: String): JSONObject? {
    val node = parseNode(link) ?: return null
    val p = queryParams(link)
    val password = urlDecode(link.substringAfter("://").substringBefore('@'))
    val server = JSONObject().put("address", node.host).put("port", node.port).put("password", password)
    return outbound(
        "trojan",
        JSONObject().put("servers", JSONArray().put(server)),
        streamSettings(p["type"] ?: "tcp", p["security"] ?: "tls", p, node.host)
    )
}

private fun vmessOutbound(link: String): JSONObject? {
    val j = JSONObject(b64Text(link.substringAfter("://").substringBefore('#')))
    val host = j.getString("add")
    val port = j.get("port").toString().toInt()
    val user = JSONObject()
        .put("id", j.getString("id"))
        .put("alterId", j.optString("aid", "0").toIntOrNull() ?: 0)
        .put("security", j.optString("scy", "auto").ifEmpty { "auto" })
    val server = JSONObject().put("address", host).put("port", port).put("users", JSONArray().put(user))
    val p = HashMap<String, String>()
    p["path"] = j.optString("path", "/")
    p["host"] = j.optString("host", "")
    p["sni"] = j.optString("sni", "")
    p["alpn"] = j.optString("alpn", "")
    p["fp"] = j.optString("fp", "chrome").ifEmpty { "chrome" }
    val security = if (j.optString("tls", "") == "tls") "tls" else "none"
    if (p["sni"].isNullOrEmpty()) p["sni"] = p["host"].takeUnless { it.isNullOrEmpty() } ?: host
    return outbound(
        "vmess",
        JSONObject().put("vnext", JSONArray().put(server)),
        streamSettings(j.optString("net", "tcp"), security, p, host)
    )
}

private fun shadowsocksOutbound(link: String): JSONObject? {
    val node = parseNode(link) ?: return null
    var rest = link.substringAfter("://").substringBefore('#').substringBefore('?')
    if (!rest.contains('@')) rest = b64Text(rest)
    val userInfo = rest.substringBeforeLast('@')
    val cred = if (userInfo.contains(':')) urlDecode(userInfo) else b64Text(userInfo)
    val server = JSONObject()
        .put("address", node.host)
        .put("port", node.port)
        .put("method", cred.substringBefore(':'))
        .put("password", cred.substringAfter(':'))
    return outbound("shadowsocks", JSONObject().put("servers", JSONArray().put(server)), null)
}

/** Trasforma un link (vless, vmess, trojan, ss) nella configurazione completa di Xray, con proxy locale SOCKS. */
fun buildXrayConfig(link: String): String? {
    val proto = link.substringBefore("://", "").lowercase()
    val out0 = try {
        when (proto) {
            "vless" -> vlessOutbound(link)
            "trojan" -> trojanOutbound(link)
            "vmess" -> vmessOutbound(link)
            "ss" -> shadowsocksOutbound(link)
            else -> null
        }
    } catch (_: Exception) {
        null
    }
    val out = out0 ?: return null
    val inbound = JSONObject()
        .put("tag", "socks")
        .put("listen", "127.0.0.1")
        .put("port", SOCKS_PORT)
        .put("protocol", "socks")
        .put("settings", JSONObject().put("auth", "noauth").put("udp", true))
        .put("sniffing", JSONObject().put("enabled", true).put("destOverride", JSONArray().put("http").put("tls")))
    val cfg = JSONObject()
        .put("log", JSONObject().put("loglevel", "warning"))
        .put("inbounds", JSONArray().put(inbound))
        .put("outbounds", JSONArray().put(out).put(JSONObject().put("tag", "direct").put("protocol", "freedom")))
        .put("dns", JSONObject().put("servers", JSONArray().put("1.1.1.1").put("8.8.8.8")))
    return cfg.toString()
}

/**
 * Motore Xray (libv2ray.aar). Se ne usa por reflexión para que la app compile aunque el archivo falte;
 * si la API no coincide, apiDump() muestra los métodos reales.
 */
object XrayEngine {
    private const val LIB = "libv2ray.Libv2ray"
    private const val CB = "libv2ray.CoreCallbackHandler"
    private var controller: Any? = null

    @Volatile
    var lastError = ""

    @Volatile
    var fatal = false

    fun available(): Boolean = try {
        Class.forName(LIB)
        true
    } catch (_: Throwable) {
        false
    }

    fun version(): String = try {
        val m = Class.forName(LIB).methods.firstOrNull { it.name == "checkVersionX" || it.name == "checkVersionXray" }
        (m?.invoke(null) as? String) ?: "?"
    } catch (_: Throwable) {
        "?"
    }

    fun apiDump(): String {
        val sb = StringBuilder()
        for (name in listOf(LIB, "libv2ray.CoreController", CB)) {
            try {
                val c = Class.forName(name)
                sb.append(name).append(":\n")
                c.methods.filter { it.declaringClass == c }.sortedBy { it.name }.forEach { m ->
                    sb.append("  ").append(m.name).append("(")
                        .append(m.parameterTypes.joinToString { it.simpleName }).append(")\n")
                }
            } catch (_: Throwable) {
                sb.append(name).append(": -\n")
            }
        }
        return sb.toString()
    }

    @Synchronized
    fun start(context: Context, config: String): Boolean {
        stop()
        fatal = false
        try {
            val lib = Class.forName(LIB)
            lib.methods.firstOrNull { it.name == "initCoreEnv" && it.parameterTypes.size == 2 }
                ?.invoke(null, context.filesDir.absolutePath, "jolie")
            val cb = Class.forName(CB)
            val handler = Proxy.newProxyInstance(cb.classLoader, arrayOf(cb), InvocationHandler { _, method, _ ->
                when (method.returnType) {
                    java.lang.Long.TYPE -> 0L
                    java.lang.Integer.TYPE -> 0
                    java.lang.Boolean.TYPE -> false
                    else -> null
                }
            })
            val make = lib.methods.first { it.name == "newCoreController" }
            val ctl = make.invoke(null, handler) ?: throw IllegalStateException("controller null")
            val startM = ctl.javaClass.methods.first { it.name == "startLoop" }
            val types = startM.parameterTypes
            if (types.size == 1) {
                startM.invoke(ctl, config)
            } else if (types.size == 2) {
                val second: Any = if (types[1] == java.lang.Long.TYPE) 0L else 0
                startM.invoke(ctl, config, second)
            } else {
                throw IllegalStateException("startLoop con " + types.size + " parametri")
            }
            controller = ctl
            lastError = ""
            return true
        } catch (e: Throwable) {
            val c = if (e is InvocationTargetException) (e.cause ?: e) else e
            lastError = c.javaClass.simpleName + ": " + (c.message ?: "")
            fatal = c is NoSuchElementException || c is ClassNotFoundException ||
                c is NoSuchMethodException || c is IllegalStateException || c is NoClassDefFoundError
            controller = null
            return false
        }
    }

    @Synchronized
    fun stop() {
        val c = controller ?: return
        controller = null
        try {
            c.javaClass.methods.firstOrNull { it.name == "stopLoop" }?.invoke(c)
        } catch (_: Throwable) {
        }
    }
}
