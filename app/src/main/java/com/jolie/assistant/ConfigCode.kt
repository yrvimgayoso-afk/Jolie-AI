package com.jolie.assistant

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * Código de configuración: el usuario de Moscú no crea ninguna clave, solo pega este código.
 * Contiene la clave de IA y las apps del túnel. Enviarlo solo en privado.
 */
object ConfigCode {
    private const val PREFIX = "JOLIE1:"
    private const val FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING

    fun export(context: Context): String {
        val o = JSONObject()
        o.put("key", AiPrefs(context).geminiKey)
        o.put("apps", JSONArray(VpnApps.selected(context).toList()))
        return PREFIX + Base64.encodeToString(o.toString().toByteArray(Charsets.UTF_8), FLAGS)
    }

    fun importCode(context: Context, text: String): Boolean {
        val i = text.indexOf(PREFIX)
        if (i < 0) return false
        val raw = text.substring(i + PREFIX.length).trim().takeWhile { it.isLetterOrDigit() || it == '-' || it == '_' }
        return try {
            val json = JSONObject(String(Base64.decode(raw, FLAGS), Charsets.UTF_8))
            val key = json.optString("key", "")
            if (key.isNotEmpty()) AiPrefs(context).geminiKey = key
            val apps = json.optJSONArray("apps")
            if (apps != null) VpnApps.save(context, (0 until apps.length()).map { apps.getString(it) })
            true
        } catch (_: Exception) {
            false
        }
    }
}
