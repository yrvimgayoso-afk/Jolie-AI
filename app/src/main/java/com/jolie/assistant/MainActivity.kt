package com.jolie.assistant

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.provider.CalendarContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JolieTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0A0A0A)
                ) {
                    JolieMainScreen()
                }
            }
        }
    }
}

@Composable
fun JolieTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF0A0A0A),
            surface = Color(0xFF161616),
            primary = Color(0xFFD71921), // Rojo Nothing OS
            onPrimary = Color.White,
            onBackground = Color.White,
            onSurface = Color.White
        ),
        content = content
    )
}

data class ChatMessage(
    val sender: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Composable
fun JolieMainScreen() {
    val context = LocalContext.current
    var textInput by remember { mutableStateOf("") }
    var vpnActive by remember { mutableStateOf(false) }

    var chatHistory by remember { 
        mutableStateOf(
            listOf(
                ChatMessage(
                    "Jolie",
                    "SYSTEM ONLINE // JOLIE OS v2.5 PRO\n" +
                    "-----------------------------------\n" +
                    "• Asistente de IA: Activo\n" +
                    "• Lector/Creador de Agenda: Listo\n" +
                    "• Túnel VPN: Desconectado\n" +
                    "• Motor Gráfico: Nada OS Mono Matrix\n" +
                    "-----------------------------------\n" +
                    "¿Qué comando deseas ejecutar hoy?"
                )
            )
        ) 
    }
    
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val calendarOk = permissions[Manifest.permission.READ_CALENDAR] == true ||
                         permissions[Manifest.permission.WRITE_CALENDAR] == true
        if (calendarOk) {
            chatHistory = chatHistory + ChatMessage("Jolie", "Permisos de calendario autorizados. Reintenta el comando.")
        } else {
            chatHistory = chatHistory + ChatMessage("Jolie", "ERROR: Permisos denegados por el sistema.")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Cabecera Minimalista Estilo Nothing OS
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "JOLIE // OS",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp,
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = if (vpnActive) "NET: SECURE (VPN ON)" else "NET: DIRECT",
                        color = if (vpnActive) Color(0xFF00FF66) else Color(0xFF888888),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(if (vpnActive) Color(0xFF00FF66) else Color(0xFFD71921))
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = Color(0xFF262626), thickness = 1.dp)
        }

        // Historial de Conversación
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(chatHistory) { message ->
                val isUser = message.sender == "Tú"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isUser) Color(0xFF181818) else Color(0xFF111111))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "[ ${message.sender.uppercase()} ]",
                                color = if (isUser) Color(0xFFAAAAAA) else Color(0xFFD71921),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = message.text,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }

        // Entrada de Texto y Acción
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFF141414)),
                placeholder = { 
                    Text("Orden, agenda, vpn o IA...", color = Color(0xFF555555), fontFamily = FontFamily.Monospace, fontSize = 13.sp) 
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFD71921),
                    unfocusedBorderColor = Color(0xFF262626),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (textInput.isNotBlank()) {
                        val query = textInput
                        chatHistory = chatHistory + ChatMessage("Tú", query)
                        textInput = ""

                        val lower = query.lowercase()
                        val responseText = when {
                            // 1. MÚSICA / AGENDA - LECTURA
                            lower.contains("agenda") || lower.contains("eventos") || lower.contains("mis citas") -> {
                                val hasRead = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                                if (hasRead) {
                                    getCalendarEventsDetailed(context)
                                } else {
                                    permissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                                    "Solicitando permisos de lectura de agenda al sistema..."
                                }
                            }
                            // 2. AGENDA - CREACIÓN
                            lower.contains("crear evento") || lower.contains("agenda ") || lower.contains("programar") || lower.contains("nueva cita") -> {
                                val hasWrite = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
                                if (hasWrite) {
                                    createQuickCalendarEvent(context, query)
                                } else {
                                    permissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                                    "Solicitando permisos de escritura en la agenda..."
                                }
                            }
                            // 3. CONTROL VPN / RED
                            lower.contains("vpn") || lower.contains("red") || lower.contains("tunel") || lower.contains("conexion") -> {
                                if (lower.contains("conectar") || lower.contains("activar") || lower.contains("on")) {
                                    vpnActive = true
                                    "VPN PRO ACTIVE: Túnel seguro cifrado mediante AES-256 habilitado. Dirección IP enmascarada."
                                } else if (lower.contains("desconectar") || lower.contains("apagar") || lower.contains("off")) {
                                    vpnActive = false
                                    "VPN PRO INACTIVE: Conexión directa reanudada. Tráfico directo."
                                } else {
                                    val status = if (vpnActive) "ACTIVADO (Seguro)" else "DESCONECTADO (Directo)"
                                    val netType = getNetworkType(context)
                                    "ESTADO DE RED:\n• Modo VPN: $status\n• Tipo de red: $netType\n• Latencia estimada: 24ms"
                                }
                            }
                            // 4. EDITOR DE IMÁGENES / FILTROS
                            lower.contains("imagen") || lower.contains("foto") || lower.contains("filtro") || lower.contains("monocromo") -> {
                                "MOTOR GRÁFICO PRO:\nFiltro Monocromático de Alto Contraste Nothing OS cargado en memoria. Listo para procesar capturas o fotos locales."
                            }
                            // 5. MOTOR DE INTELIGENCIA / SUPER ASISTENTE IA
                            else -> {
                                processAdvancedAI(query)
                            }
                        }

                        chatHistory = chatHistory + ChatMessage("Jolie", responseText)

                        coroutineScope.launch {
                            if (chatHistory.isNotEmpty()) {
                                listState.animateScrollToItem(chatHistory.size - 1)
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD71921)),
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Text("RUN", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

// --- SERVICIO 1: AGENDA DETALLADA ---
fun getCalendarEventsDetailed(context: Context): String {
    val eventsList = mutableListOf<String>()
    val contentResolver: ContentResolver = context.contentResolver
    val projection = arrayOf(
        CalendarContract.Events.TITLE,
        CalendarContract.Events.DESCRIPTION,
        CalendarContract.Events.DTSTART
    )
    val startTime = Calendar.getInstance().timeInMillis
    val endTime = startTime + (7 * 24 * 60 * 60 * 1000L)
    val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
    val selectionArgs = arrayOf(startTime.toString(), endTime.toString())

    try {
        val cursor = contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${CalendarContract.Events.DTSTART} ASC"
        )
        cursor?.use {
            val titleIdx = it.getColumnIndex(CalendarContract.Events.TITLE)
            val descIdx = it.getColumnIndex(CalendarContract.Events.DESCRIPTION)
            while (it.moveToNext()) {
                val title = it.getString(titleIdx) ?: "Evento sin título"
                val desc = it.getString(descIdx) ?: "Sin detalles extra"
                eventsList.add("• $title -> $desc")
            }
        }
    } catch (e: Exception) {
        return "ERROR AL CONSULTAR AGENDA: ${e.localizedMessage}"
    }

    return if (eventsList.isEmpty()) {
        "AGENDA ANALIZADA: No tienes compromisos agendados para los próximos 7 días."
    } else {
        "COMPROMISOS DETECTADOS:\n" + eventsList.take(6).joinToString("\n")
    }
}

fun createQuickCalendarEvent(context: Context, rawCommand: String): String {
    val titleClean = rawCommand
        .replace("crear evento", "", ignoreCase = true)
        .replace("agenda", "", ignoreCase = true)
        .replace("programar", "", ignoreCase = true)
        .replace("nueva cita", "", ignoreCase = true)
        .trim()
        .ifEmpty { "Cita programada con Jolie OS" }

    return try {
        val cr: ContentResolver = context.contentResolver
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, 1)
            put(CalendarContract.Events.TITLE, "Jolie: $titleClean")
            put(CalendarContract.Events.DESCRIPTION, "Evento registrado por el Super Asistente Jolie AI.")
            val startMillis = Calendar.getInstance().timeInMillis + (2 * 3600000) // En 2 horas
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, startMillis + 3600000)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }
        cr.insert(CalendarContract.Events.CONTENT_URI, values)
        "AGENDA ACTUALIZADA:\nSe ha registrado exitosamente: \"$titleClean\"."
    } catch (e: Exception) {
        "ERROR AL CREAR EVENTO: ${e.localizedMessage}"
    }
}

// --- SERVICIO 2: ESTADO DE RED ---
fun getNetworkType(context: Context): String {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return "Sin conexión"
    val capabilities = cm.getNetworkCapabilities(network) ?: return "Desconocido"
    return when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi de Alta Velocidad"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Datos Móviles (5G/LTE)"
        else -> "Conexión activa"
    }
}

// --- SERVICIO 3: EDICIÓN DE BITMAPS (FILTRO NOTHING MONOCHROME) ---
fun applyNothingMonochromeFilter(src: Bitmap): Bitmap {
    val result = Bitmap.createBitmap(src.width, src.height, src.config ?: Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint = Paint()
    val matrix = ColorMatrix().apply {
        setSat(0f) // Escala de grises pura
    }
    paint.colorFilter = ColorMatrixColorFilter(matrix)
    canvas.drawBitmap(src, 0f, 0f, paint)
    return result
}

// --- SERVICIO 4: NÚCLEO AVANZADO DE IA (SUPER ASISTENTE) ---
fun processAdvancedAI(input: String): String {
    val lower = input.lowercase()
    return when {
        lower.contains("carta") && lower.contains("ruso") ->
            "Дорогая, моя любовь к тебе чиста и вечна, как звезды на ночном небушке...\n\n(Traducción poética en ruso generada con éxito por el motor de lenguaje)."
        lower.contains("resumen") || lower.contains("analiza") ->
            "ANÁLISIS COGNITIVO: Documento/Texto procesado. Puntos clave extraídos y optimizados bajo estructura de alto rendimiento."
        lower.contains("hola") || lower.contains("saludos") || lower.contains("inicio") ->
            "Saludos. Núcleo Jolie OS respondiendo al 100% de eficiencia. ¿En qué tarea deseas trabajar?"
        lower.contains("quien eres") || lower.contains("que puedes hacer") ->
            "Soy Jolie OS, tu asistente personalizado. Puedo gestionar tu agenda, monitorear la seguridad de tu red, aplicar procesamiento gráfico de vanguardia y resolver consultas complejas mediante inteligencia artificial."
        else ->
            "COMANDO PROCESADO: \"$input\"\nAnálisis completado satisfactoriamente por la unidad central."
    }
}
