package com.jolie.assistant

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.CalendarContract
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Cosas prácticas del calendario: calendario de Google por defecto, aviso previo, exportar .ics y abrir en Calendario. */
object CalendarExport {
    /** Prefiere el calendario de la cuenta de Google: así las citas se sincronizan solas con Google Calendar. */
    fun bestCalendarId(c: Context): Long? {
        try {
            val cols = arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars.IS_PRIMARY
            )
            val sel = CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + " >= ?"
            val args = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
            c.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, cols, sel, args, null)?.use { cur ->
                var google: Long? = null
                var primary: Long? = null
                var any: Long? = null
                while (cur.moveToNext()) {
                    val id = cur.getLong(0)
                    if (any == null) any = id
                    val isGoogle = cur.getString(1) == "com.google"
                    if (isGoogle && cur.getInt(2) == 1) return id
                    if (isGoogle && google == null) google = id
                    if (cur.getInt(2) == 1 && primary == null) primary = id
                }
                return google ?: primary ?: any
            }
        } catch (_: Exception) {
        }
        return null
    }

    /** Crea la cita con un aviso 15 minutos antes. */
    fun add(c: Context, title: String, startMs: Long, minutes: Int): Boolean {
        if (!CalendarOps.canWrite(c)) return false
        val calId = bestCalendarId(c) ?: return false
        return try {
            val v = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calId)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DTSTART, startMs)
                put(CalendarContract.Events.DTEND, startMs + minutes * 60000L)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }
            val uri = c.contentResolver.insert(CalendarContract.Events.CONTENT_URI, v) ?: return false
            try {
                val r = ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, ContentUris.parseId(uri))
                    put(CalendarContract.Reminders.MINUTES, 15)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                }
                c.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, r)
            } catch (_: Exception) {
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun esc(s: String): String =
        s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")

    fun buildIcs(events: List<CalEvent>): String {
        val utc = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val dateOnly = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val stamp = utc.format(Date())
        val sb = StringBuilder()
        sb.append("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Jolie//Agenda//IT\r\nCALSCALE:GREGORIAN\r\n")
        for (e in events) {
            sb.append("BEGIN:VEVENT\r\n")
            sb.append("UID:jolie-").append(e.id).append('-').append(e.begin).append("@jolie\r\n")
            sb.append("DTSTAMP:").append(stamp).append("\r\n")
            if (e.allDay) {
                sb.append("DTSTART;VALUE=DATE:").append(dateOnly.format(Date(e.begin))).append("\r\n")
                sb.append("DTEND;VALUE=DATE:").append(dateOnly.format(Date(e.end))).append("\r\n")
            } else {
                sb.append("DTSTART:").append(utc.format(Date(e.begin))).append("\r\n")
                sb.append("DTEND:").append(utc.format(Date(e.end))).append("\r\n")
            }
            sb.append("SUMMARY:").append(esc(e.title.ifEmpty { "Evento" })).append("\r\n")
            sb.append("END:VEVENT\r\n")
        }
        sb.append("END:VCALENDAR\r\n")
        return sb.toString()
    }

    /** Guarda un .ics en Descargas y lo abre (Google Calendar lo importa). Devuelve "ok:nombre", "empty", "sdk" o "error". */
    fun exportAndOpen(c: Context, events: List<CalEvent>): String {
        if (events.isEmpty()) return "empty"
        if (Build.VERSION.SDK_INT < 29) return "sdk"
        return try {
            val name = "Jolie_agenda_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) + ".ics"
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/calendar")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download")
            }
            val uri = c.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return "error"
            c.contentResolver.openOutputStream(uri)?.use { it.write(buildIcs(events).toByteArray(Charsets.UTF_8)) }
            val view = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "text/calendar")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                c.startActivity(view)
            } catch (_: Exception) {
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/calendar")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                c.startActivity(Intent.createChooser(send, "Jolie").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            "ok:$name"
        } catch (_: Exception) {
            "error"
        }
    }

    /** Abre la cita en la app de Calendario (Google Calendar). */
    fun openEvent(c: Context, id: Long) {
        try {
            c.startActivity(
                Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }
}
