package com.jolie.assistant

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

/** Orden de modelos Gemini: primero los mejores (con razonamiento), los "lite" al final. */
fun strongRank(n: String): Int {
    val lite = n.contains("lite")
    val flash = n.contains("flash")
    val pro = n.contains("pro")
    val preview = n.contains("preview") || n.contains("exp")
    return when {
        flash && !lite && !preview && !n.endsWith("-latest") -> 0
        flash && !lite && !preview -> 1
        flash && !lite -> 2
        pro && !preview -> 3
        pro -> 4
        lite && !preview -> 5
        lite -> 6
        else -> 8
    }
}

class MapInfo(val lat: Double, val lon: Double, val label: String)

/** Mapas gratuitos (OpenStreetMap) y ubicación del usuario para el chat. */
object ChatMaps {
    val byKey = mutableStateMapOf<String, MapInfo>()
    val needLocation = mutableStateMapOf<String, Boolean>()
    private val bitmaps = HashMap<String, Bitmap>()
    private val MAP_RE = Regex("\\[\\[MAP:\\s*(.*?)\\]\\]", RegexOption.DOT_MATCHES_ALL)
    private var cachedPlace = ""
    private var cachedAt = 0L

    fun keyOf(text: String): String = text.trim().take(24)

    fun hasLocationPermission(c: Context): Boolean =
        ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun lastLocation(c: Context): Location? {
        if (!hasLocationPermission(c)) return null
        return try {
            val lm = c.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var best: Location? = null
            for (p in lm.getProviders(true)) {
                val l = lm.getLastKnownLocation(p) ?: continue
                if (best == null || l.time > best.time) best = l
            }
            best
        } catch (_: Exception) {
            null
        }
    }

    private fun httpGet(u: String): ByteArray {
        val c = JolieProxy.openConnection(URL(u))
        try {
            c.connectTimeout = 8000
            c.readTimeout = 12000
            c.setRequestProperty("User-Agent", "JolieApp/1.0 (personal assistant app)")
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    private fun geocode(q: String): MapInfo? {
        val u = "https://nominatim.openstreetmap.org/search?format=json&limit=1&q=" + URLEncoder.encode(q, "UTF-8")
        val arr = JSONArray(String(httpGet(u), Charsets.UTF_8))
        if (arr.length() == 0) return null
        val o = arr.getJSONObject(0)
        return MapInfo(o.getString("lat").toDouble(), o.getString("lon").toDouble(), o.optString("display_name", q).take(90))
    }

    private fun reverse(lat: Double, lon: Double): String {
        val u = "https://nominatim.openstreetmap.org/reverse?format=json&zoom=14&lat=$lat&lon=$lon"
        return JSONObject(String(httpGet(u), Charsets.UTF_8)).optString("display_name", "").take(90)
    }

    /** Frase que Jolie recibe en cada pregunta: cómo pedir mapas, ánimo para razonar y dónde está el usuario. */
    fun systemExtra(context: Context): String {
        val sb = StringBuilder()
        sb.append("Se l’utente chiede una mappa, indicazioni o dove si trova un posto, rispondi brevemente e aggiungi in fondo, su una riga a parte, [[MAP: nome del luogo con la città]] oppure [[MAP: here]] per mostrare dove si trova l’utente.\n")
        sb.append("Ragiona con attenzione e dai risposte complete e utili, anche lunghe quando serve: questa regola prevale su quelle sulla brevità. Se ti manca un dato per rispondere bene (per esempio la città o dove si trova l’utente), chiedilo.\n")
        val loc = lastLocation(context)
        if (loc != null) {
            val now = System.currentTimeMillis()
            if (cachedPlace.isEmpty() || now - cachedAt > 15 * 60000L) {
                cachedPlace = try {
                    reverse(loc.latitude, loc.longitude)
                } catch (_: Exception) {
                    ""
                }
                cachedAt = now
            }
            sb.append("Posizione approssimativa dell’utente: ")
                .append(if (cachedPlace.isNotEmpty()) cachedPlace else loc.latitude.toString() + ", " + loc.longitude)
                .append(".\n")
        } else {
            sb.append("La posizione dell’utente non è disponibile: se serve, chiedigli dove si trova.\n")
        }
        return sb.toString()
    }

    /** Quita la orden [[MAP: ...]] del mensaje y busca el sitio en segundo plano. */
    fun handle(context: Context, text: String): String {
        val m = MAP_RE.find(text) ?: return text
        val q = m.groupValues[1].trim()
        val clean = text.replace(m.value, "").trim()
        resolve(context.applicationContext, keyOf(clean), q)
        return clean
    }

    fun resolve(app: Context, key: String, q: String) {
        runAsync {
            val here = q.equals("here", true) || q.equals("qui", true) || q.equals("posizione", true)
            val info: MapInfo? = try {
                if (here) {
                    val loc = lastLocation(app)
                    if (loc == null) {
                        null
                    } else {
                        MapInfo(
                            loc.latitude,
                            loc.longitude,
                            try {
                                reverse(loc.latitude, loc.longitude)
                            } catch (_: Exception) {
                                "La tua posizione"
                            }
                        )
                    }
                } else {
                    geocode(q)
                }
            } catch (_: Exception) {
                null
            }
            onMain {
                if (info != null) {
                    byKey[key] = info
                    needLocation.remove(key)
                } else if (here) {
                    needLocation[key] = true
                }
            }
        }
    }

    /** Une 3x3 teselas de OpenStreetMap y dibuja un punto rojo en el sitio. */
    fun mapBitmap(info: MapInfo): Bitmap? {
        val key = info.lat.toString() + "," + info.lon
        synchronized(bitmaps) {
            val cached = bitmaps[key]
            if (cached != null) return cached
        }
        return try {
            val z = 15
            val n = 1 shl z
            val xf = (info.lon + 180.0) / 360.0 * n
            val latR = Math.toRadians(info.lat)
            val yf = (1.0 - Math.log(Math.tan(latR) + 1.0 / Math.cos(latR)) / Math.PI) / 2.0 * n
            val cx = xf.toInt()
            val cy = yf.toInt()
            val big = Bitmap.createBitmap(768, 768, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(big)
            for (dx in -1..1) {
                for (dy in -1..1) {
                    val bytes = httpGet("https://tile.openstreetmap.org/$z/${cx + dx}/${cy + dy}.png")
                    val t = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
                    canvas.drawBitmap(t, ((dx + 1) * 256).toFloat(), ((dy + 1) * 256).toFloat(), null)
                }
            }
            val px = ((xf - cx) * 256 + 256).toFloat()
            val py = ((yf - cy) * 256 + 256).toFloat()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = 0xFFFFFFFF.toInt()
            canvas.drawCircle(px, py, 16f, paint)
            paint.color = 0xFFD71921.toInt()
            canvas.drawCircle(px, py, 11f, paint)
            val left = (px - 300f).toInt().coerceIn(0, 768 - 600)
            val top = (py - 200f).toInt().coerceIn(0, 768 - 400)
            val out = Bitmap.createBitmap(big, left, top, 600, 400)
            synchronized(bitmaps) {
                if (bitmaps.size > 10) bitmaps.clear()
                bitmaps[key] = out
            }
            out
        } catch (_: Throwable) {
            null
        }
    }
}

private fun openInMaps(context: Context, info: MapInfo) {
    try {
        val label = Uri.encode(info.label.take(40))
        val uri = Uri.parse("geo:${info.lat},${info.lon}?q=${info.lat},${info.lon}($label)")
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
    }
}

/** Mapa bajo el mensaje de Jolie (si el mensaje lo pide), con botón para abrirlo en tu app de mapas. */
@Composable
fun ChatMapStrip(text: String) {
    val context = LocalContext.current
    val key = ChatMaps.keyOf(text)
    val info = ChatMaps.byKey[key]
    val needs = ChatMaps.needLocation[key] == true
    if (info == null && !needs) return

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (ChatMaps.hasLocationPermission(context)) ChatMaps.resolve(context.applicationContext, key, "here")
    }
    var bmp by remember(info) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(info) {
        if (info != null) {
            runAsync {
                val b = ChatMaps.mapBitmap(info)
                onMain { bmp = b }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (info != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF262626))
                    .clickable { openInMaps(context, info) },
                contentAlignment = Alignment.Center
            ) {
                val b = bmp
                if (b != null) {
                    Image(
                        bitmap = b.asImageBitmap(),
                        contentDescription = "Mappa",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            MonoText(info.label, size = 10, color = JolieColors.Gray)
            SmallButton("APRI IN MAPPE", { openInMaps(context, info) })
            MonoText("© OpenStreetMap", size = 9, color = JolieColors.Gray)
        } else {
            MonoText("Per mostrarti dove sei serve il permesso della posizione.", size = 11)
            SmallButton(
                "CONCEDI POSIZIONE",
                { perm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                primary = true
            )
        }
    }
}
