package com.jolie.assistant

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun startOfDay(ms: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = ms
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

@Composable
private fun AgendaField(value: String, hint: String, onChange: (String) -> Unit) {
    GlassSurface(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            textStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
            cursorBrush = SolidColor(Color.White),
            singleLine = true,
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) MonoText(hint, size = 13, color = JolieColors.Gray)
                    inner()
                }
            }
        )
    }
}

/** Agenda real: lee el calendario del teléfono, y permite añadir y borrar citas. */
@Composable
fun AgendaScreen(lang: String, unusedTitle: String = "", unusedText: String = "") {
    val context = LocalContext.current
    var day by remember { mutableStateOf(startOfDay(System.currentTimeMillis())) }
    var tick by remember { mutableStateOf(0) }
    var granted by remember { mutableStateOf(CalendarOps.canRead(context) && CalendarOps.canWrite(context)) }
    var showForm by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf(60) }
    var status by remember { mutableStateOf("") }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = CalendarOps.canRead(context) && CalendarOps.canWrite(context)
        tick++
    }

    val locale = if (lang == "RU") Locale("ru") else Locale.ITALIAN
    val events = remember(day, tick, granted) { CalendarOps.events(context, day, day + 86400000L) }
    val dayText = SimpleDateFormat("EEEE d MMMM", locale).format(Date(day)).uppercase(locale)
    val timeFmt = SimpleDateFormat("HH:mm", locale)

    fun save() {
        val m = Regex("^(\\d{1,2}):(\\d{2})$").find(time.trim())
        if (title.isBlank() || m == null) {
            status = tr(lang, "Scrivi titolo e ora (es. 15:30).", "Введите название и время (напр. 15:30).")
            return
        }
        val h = m.groupValues[1].toInt()
        val mi = m.groupValues[2].toInt()
        if (h > 23 || mi > 59) {
            status = tr(lang, "Ora non valida.", "Неверное время.")
            return
        }
        val start = day + h * 3600000L + mi * 60000L
        if (CalendarOps.add(context, title.trim(), start, minutes)) {
            title = ""
            time = ""
            showForm = false
            status = ""
            tick++
        } else {
            status = tr(lang, "Impossibile salvare: controlla il permesso del calendario.", "Не удалось сохранить: проверьте разрешение календаря.")
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmallButton("<", { day -= 86400000L }, Modifier.width(52.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                MonoText(dayText, size = 12, bold = true, spacing = 1f)
            }
            SmallButton(">", { day += 86400000L }, Modifier.width(52.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallButton(tr(lang, "OGGI", "СЕГОДНЯ"), { day = startOfDay(System.currentTimeMillis()) }, Modifier.weight(1f))
            SmallButton(tr(lang, "NUOVO", "НОВОЕ"), { showForm = !showForm }, Modifier.weight(1f), primary = true)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!granted) {
                GlassSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionLabel(tr(lang, "PERMESSO CALENDARIO", "РАЗРЕШЕНИЕ КАЛЕНДАРЯ"))
                        MonoText(
                            tr(lang, "Jolie ha bisogno di leggere e scrivere nel calendario del telefono.", "Джоли нужен доступ к календарю телефона."),
                            size = 12
                        )
                        GlassButton(
                            tr(lang, "CONCEDI", "РАЗРЕШИТЬ"),
                            onClick = { perm.launch(arrayOf(android.Manifest.permission.READ_CALENDAR, android.Manifest.permission.WRITE_CALENDAR)) },
                            modifier = Modifier.fillMaxWidth(),
                            filled = true
                        )
                    }
                }
            }

            if (showForm) {
                GlassSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionLabel(tr(lang, "NUOVO APPUNTAMENTO", "НОВАЯ ВСТРЕЧА"))
                        AgendaField(title, tr(lang, "Titolo", "Название"), { title = it })
                        AgendaField(time, tr(lang, "Ora, es. 15:30", "Время, напр. 15:30"), { time = it })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(30, 60, 90, 120).forEach { m ->
                                OptionChip(m.toString() + " min", minutes == m, { minutes = m })
                            }
                        }
                        GlassButton(tr(lang, "SALVA", "СОХРАНИТЬ"), onClick = { save() }, modifier = Modifier.fillMaxWidth(), filled = true)
                    }
                }
            }
            if (status.isNotEmpty()) MonoText(status, size = 12, color = JolieColors.RedSoft)

            if (granted) {
                MonoText(
                    if (events.isEmpty()) {
                        tr(lang, "Nessun impegno in questo giorno.", "В этот день ничего нет.")
                    } else {
                        tr(lang, events.size.toString() + " impegni", events.size.toString() + " событий")
                    },
                    size = 12,
                    color = JolieColors.Gray
                )
            }
            events.forEach { e ->
                GlassSurface(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                            MonoText(
                                if (e.allDay) {
                                    tr(lang, "TUTTO IL GIORNO", "ВЕСЬ ДЕНЬ")
                                } else {
                                    timeFmt.format(Date(e.begin)) + " – " + timeFmt.format(Date(e.end))
                                },
                                size = 11,
                                color = JolieColors.RedSoft
                            )
                            MonoText(e.title.ifEmpty { "-" }, size = 13)
                        }
                        Box(
                            Modifier
                                .size(48.dp)
                                .clickable {
                                    CalendarOps.delete(context, e.id)
                                    tick++
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✕", color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }
            MonoText(
                tr(
                    lang,
                    "Puoi anche dirlo in chat: “fissa dentista venerdì alle 15”.",
                    "Можно сказать в чате: «запиши стоматолога в пятницу в 15»."
                ),
                size = 11,
                color = JolieColors.Gray
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
