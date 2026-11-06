package com.jolie.assistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Fotos gratuitas para el chat (Wikimedia Commons, sin clave). */
object ChatImages {
    val byText = mutableStateMapOf<String, List<String>>()
    private val cache = HashMap<String, Bitmap>()

    fun cached(url: String): Bitmap? = synchronized(cache) { cache[url] }

    fun search(key: String, query: String) {
        runAsync {
            val urls = try {
                wikimedia(query)
            } catch (_: Exception) {
                emptyList()
            }
            if (urls.isNotEmpty()) onMain { byText[key] = urls }
        }
    }

    private fun get(u: String): ByteArray {
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

    private fun wikimedia(q: String): List<String> {
        val u = "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search" +
            "&gsrnamespace=6&gsrlimit=12&prop=imageinfo&iiprop=url%7Cmime&iiurlwidth=480&gsrsearch=" +
            URLEncoder.encode("$q filetype:bitmap", "UTF-8")
        val pages = JSONObject(String(get(u), Charsets.UTF_8)).optJSONObject("query")?.optJSONObject("pages")
            ?: return emptyList()
        val out = ArrayList<String>()
        val keys = pages.keys()
        while (keys.hasNext()) {
            val info = pages.getJSONObject(keys.next()).optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
            val mime = info.optString("mime")
            val thumb = info.optString("thumburl")
            if ((mime == "image/jpeg" || mime == "image/png") && thumb.isNotEmpty()) out.add(thumb)
        }
        return out.take(6)
    }

    fun load(url: String): Bitmap? {
        cached(url)?.let { return it }
        return try {
            val bytes = get(url)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bmp != null) {
                synchronized(cache) {
                    if (cache.size > 40) cache.clear()
                    cache[url] = bmp
                }
            }
            bmp
        } catch (_: Throwable) {
            null
        }
    }
}

@Composable
private fun RemoteImage(url: String) {
    var bmp by remember(url) { mutableStateOf(ChatImages.cached(url)) }
    LaunchedEffect(url) {
        if (bmp == null) {
            runAsync {
                val b = ChatImages.load(url)
                onMain { bmp = b }
            }
        }
    }
    Box(
        modifier = Modifier
            .size(150.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF262626)),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = "Immagine",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Fila de fotos bajo un mensaje de Jolie (si el mensaje las tiene). */
@Composable
fun ChatImageStrip(text: String) {
    val urls = ChatImages.byText[text] ?: return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            urls.forEach { RemoteImage(it) }
        }
        MonoText("Wikimedia Commons", size = 9, color = JolieColors.Gray)
    }
}

/** Ordenes ocultas de Gemini ([[IMG: ...]] y [[CAL: ...]]) y contexto de fecha y calendario. */
object ChatExtras {
    private val IMG_RE = Regex("\\[\\[IMG:\\s*(.*?)\\]\\]", RegexOption.DOT_MATCHES_ALL)
    private val CAL_RE = Regex("\\[\\[CAL:\\s*(\\{.*?\\})\\s*\\]\\]", RegexOption.DOT_MATCHES_ALL)

    fun systemExtra(context: Context): String {
        val sb = StringBuilder("\n\n")
        val fmt = SimpleDateFormat("EEEE d MMMM yyyy, HH:mm", Locale.ITALIAN)
        sb.append("Data e ora attuali: ").append(fmt.format(Date()))
            .append(" (fuso orario ").append(TimeZone.getDefault().id).append(").\n")
        sb.append("Se l’utente chiede di vedere foto o immagini di qualcosa, rispondi con una frase breve e aggiungi in fondo, su una riga a parte, [[IMG: ricerca breve in inglese]]. Non dire mai che non puoi mostrare immagini.\n")
        sb.append("Se l’utente chiede di fissare un appuntamento, rispondi con una breve conferma e aggiungi in fondo, su una riga a parte, [[CAL: {\"title\":\"...\",\"start\":\"AAAA-MM-GGTHH:MM\",\"minutes\":60}]] con data e ora locali, ricavando giorni come domani o venerdì dalla data attuale.\n")
        val cal = CalendarOps.contextText(context)
        if (cal.isNotEmpty()) {
            sb.append("Calendario dell’utente nei prossimi 7 giorni (usalo per rispondere a domande su agenda e impegni):\n").append(cal)
        }
        return sb.toString()
    }

    /** Quita las órdenes ocultas del texto, crea el evento y lanza la búsqueda de fotos. */
    fun process(context: Context, text: String): String {
        var out = text
        val cal = CAL_RE.find(out)
        if (cal != null) {
            out = out.replace(cal.value, "").trim()
            out += "\n" + createEvent(context, cal.groupValues[1])
        }
        val img = IMG_RE.find(out)
        if (img != null) {
            val q = img.groupValues[1].trim()
            out = out.replace(img.value, "").trim()
            if (q.isNotEmpty()) ChatImages.search(out, q)
        }
        return out
    }

    private fun createEvent(context: Context, json: String): String {
        return try {
            val o = JSONObject(json)
            val title = o.optString("title", "").ifEmpty { "Appuntamento" }
            val minutes = o.optInt("minutes", 60).coerceIn(5, 1440)
            val start = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).parse(o.getString("start"))!!.time
            if (CalendarOps.add(context, title, start, minutes)) {
                val shown = SimpleDateFormat("EEE d MMM HH:mm", Locale.ITALIAN).format(Date(start))
                "✓ Aggiunto al calendario / Добавлено в календарь: $title, $shown"
            } else {
                "Non sono riuscita ad aggiungerlo: controlla il permesso del calendario. / Не удалось добавить: проверьте разрешение календаря."
            }
        } catch (_: Exception) {
            "Non ho capito data e ora: riprova. / Не поняла дату и время: повторите."
        }
    }
}
