package com.jolie.assistant

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class CalEvent(val id: Long, val title: String, val begin: Long, val end: Long, val allDay: Boolean)

/** Lee, crea y borra eventos del calendario del teléfono (el mismo de Google Calendar). */
object CalendarOps {
    fun canRead(c: Context): Boolean =
        ContextCompat.checkSelfPermission(c, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun canWrite(c: Context): Boolean =
        ContextCompat.checkSelfPermission(c, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun events(c: Context, from: Long, to: Long): List<CalEvent> {
        if (!canRead(c)) return emptyList()
        val out = ArrayList<CalEvent>()
        try {
            val b = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(b, from)
            ContentUris.appendId(b, to)
            val cols = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY
            )
            c.contentResolver.query(b.build(), cols, null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { cur ->
                while (cur.moveToNext()) {
                    out.add(CalEvent(cur.getLong(0), cur.getString(1) ?: "", cur.getLong(2), cur.getLong(3), cur.getInt(4) == 1))
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun writableCalendarId(c: Context): Long? {
        try {
            val cols = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY)
            val sel = CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + " >= ?"
            val args = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
            c.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, cols, sel, args, null)?.use { cur ->
                var first: Long? = null
                while (cur.moveToNext()) {
                    val id = cur.getLong(0)
                    if (first == null) first = id
                    if (cur.getInt(1) == 1) return id
                }
                return first
            }
        } catch (_: Exception) {
        }
        return null
    }

    fun add(c: Context, title: String, startMs: Long, minutes: Int): Boolean {
        if (!canWrite(c)) return false
        val calId = writableCalendarId(c) ?: return false
        return try {
            val v = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calId)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DTSTART, startMs)
                put(CalendarContract.Events.DTEND, startMs + minutes * 60000L)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }
            c.contentResolver.insert(CalendarContract.Events.CONTENT_URI, v) != null
        } catch (_: Exception) {
            false
        }
    }

    fun delete(c: Context, id: Long): Boolean {
        if (!canWrite(c)) return false
        return try {
            c.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), null, null) > 0
        } catch (_: Exception) {
            false
        }
    }

    /** Testo con los eventos de los próximos 7 días, para que Gemini pueda contestar "¿qué tengo hoy?". */
    fun contextText(c: Context): String {
        val now = System.currentTimeMillis()
        val list = events(c, now - 3600000L, now + 7L * 86400000L)
        if (list.isEmpty()) return ""
        val dayFmt = SimpleDateFormat("EEE d MMM HH:mm", Locale.ITALIAN)
        val timeFmt = SimpleDateFormat("HH:mm", Locale.ITALIAN)
        val sb = StringBuilder()
        for (e in list.take(25)) {
            if (e.allDay) {
                sb.append("- tutto il giorno ").append(SimpleDateFormat("EEE d MMM", Locale.ITALIAN).format(Date(e.begin)))
            } else {
                sb.append("- ").append(dayFmt.format(Date(e.begin))).append("-").append(timeFmt.format(Date(e.end)))
            }
            sb.append(": ").append(e.title).append('\n')
        }
        return sb.toString()
    }
}
