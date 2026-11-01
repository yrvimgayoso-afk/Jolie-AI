package com.jolie.assistant

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
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
            primary = Color(0xFFD71921),
            onPrimary = Color.White,
            onBackground = Color.White,
            onSurface = Color.White
        ),
        content = content
    )
}

@Composable
fun JolieMainScreen() {
    val context = LocalContext.current
    var textInput by remember { mutableStateOf("") }
    var chatHistory by remember { 
        mutableStateOf(
            listOf(
                ChatMessage("Jolie", "Sistema operativo v1.0 en línea. Módulo de Agenda activado. ¿Qué deseas hacer?")
            )
        ) 
    }
    
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Solicitud dinámica de permisos para Android
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val agendaEvents = getCalendarEvents(context)
            chatHistory = chatHistory + ChatMessage("Jolie", agendaEvents)
        } else {
            chatHistory = chatHistory + ChatMessage("Jolie", "Error: Permiso de calendario denegado por el usuario.")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "JOLIE // SUPER AI",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Monospace
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFFD71921))
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = Color(0xFF262626), thickness = 1.dp)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(chatHistory) { message ->
                val isUser = message.sender == "Tú"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isUser) Color(0xFF1A1A1A) else Color(0xFF121212))
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "[ ${message.sender.uppercase()} ]",
                            color = if (isUser) Color(0xFF888888) else Color(0xFFD71921),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = message.text,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFF161616)),
                placeholder = { Text("Escribe un comando...", color = Color.Gray, fontFamily = FontFamily.Monospace) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFD71921),
                    unfocusedBorderColor = Color(0xFF333333),
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

                        if (query.lowercase().contains("agenda") || query.lowercase().contains("evento")) {
                            val permissionCheck = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.READ_CALENDAR
                            )
                            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                                val agendaEvents = getCalendarEvents(context)
                                chatHistory = chatHistory + ChatMessage("Jolie", agendaEvents)
                            } else {
                                launcher.launch(Manifest.permission.READ_CALENDAR)
                            }
                        } else {
                            val response = generateJolieResponse(query)
                            chatHistory = chatHistory + ChatMessage("Jolie", response)
                        }

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
                Text("EJECUTAR", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

data class ChatMessage(val sender: String, val text: String)

// Función nativa para leer los eventos de la agenda de Android
fun getCalendarEvents(context: Context): String {
    val eventsList = mutableListOf<String>()
    val contentResolver: ContentResolver = context.contentResolver
    
    val projection = arrayOf(
        CalendarContract.Events.TITLE,
        CalendarContract.Events.DTSTART
    )
    
    val startTime = Calendar.getInstance().timeInMillis
    val endTime = startTime + (7 * 24 * 60 * 60 * 1000L) // Próximos 7 días
    
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
            val titleIndex = it.getColumnIndex(CalendarContract.Events.TITLE)
            while (it.moveToNext()) {
                val title = it.getString(titleIndex)
                if (!title.isNull_or_blank()) {
                    eventsList.add("• $title")
                }
            }
        }
    } catch (e: Exception) {
        return "Error al acceder a la agenda: ${e.localizedMessage}"
    }

    return if (eventsList.isEmpty()) {
        "Agenda analizada: No se encontraron eventos programados para los próximos 7 días."
    } else {
        "Agenda detectada:\n" + eventsList.take(5).joinToString("\n")
    }
}

// Extensión auxiliar para evitar errores de compilación
fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()

fun generateJolieResponse(input: String): String {
    val lower = input.lowercase()
    return when {
        lower.contains("carta") && lower.contains("ruso") -> 
            "Дорогая, моя любовь к тебе чиста и вечна, как звезды на ночном небушке..."
        lower.contains("hola") || lower.contains("saludos") -> 
            "Saludos. Sistemas al 100%."
        else -> 
            "Comando procesado: \"$input\". Análisis completado con éxito."
    }
}
