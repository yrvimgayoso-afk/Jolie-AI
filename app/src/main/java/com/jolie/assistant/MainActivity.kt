package com.jolie.assistant

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContent {
            JolieTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF000000)
                ) {
                    JolieAppContainer(tts)
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("ru", "RU")
            tts?.setPitch(1.3f) // Tono juvenil amigable
            tts?.setSpeechRate(1.0f)
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
fun JolieTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF000000),
            surface = Color(0xFF111111),
            primary = Color(0xFFFF3333),
            onPrimary = Color.White,
            onBackground = Color.White,
            onSurface = Color.White
        ),
        content = content
    )
}

data class ChatMessage(val sender: String, val text: String)

enum class AppScreen {
    SETUP, CHAT, TRANSLATOR, AGENDA, PHOTO_EDITOR, VPN
}

@Composable
fun JolieAppContainer(tts: TextToSpeech?) {
    var currentScreen by remember { mutableStateOf(AppScreen.SETUP) }
    var vpnActive by remember { mutableStateOf(false) }

    if (currentScreen == AppScreen.SETUP) {
        JolieSetupScreen(onSetupComplete = {
            vpnActive = true
            currentScreen = AppScreen.CHAT
        })
    } else {
        Scaffold(
            containerColor = Color(0xFF000000),
            topBar = {
                Column(modifier = Modifier.background(Color(0xFF000000))) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "JOLIE // OS PRO",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                fontSize = 16.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = if (vpnActive) "VPN ANTI-CENSURA: ACTIVA" else "RED: DIRECTA",
                                color = if (vpnActive) Color(0xFF00FF66) else Color(0xFFFF3333),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(if (vpnActive) Color(0xFF00FF66) else Color(0xFFFF3333))
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        NavigationTabButton("Chat", currentScreen == AppScreen.CHAT) { currentScreen = AppScreen.CHAT }
                        NavigationTabButton("Trad.", currentScreen == AppScreen.TRANSLATOR) { currentScreen = AppScreen.TRANSLATOR }
                        NavigationTabButton("Agenda", currentScreen == AppScreen.AGENDA) { currentScreen = AppScreen.AGENDA }
                        NavigationTabButton("Editor", currentScreen == AppScreen.PHOTO_EDITOR) { currentScreen = AppScreen.PHOTO_EDITOR }
                        NavigationTabButton("VPN", currentScreen == AppScreen.VPN) { currentScreen = AppScreen.VPN }
                    }
                    Divider(color = Color(0xFF222222), thickness = 1.dp)
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                when (currentScreen) {
                    AppScreen.CHAT -> JolieChatScreen()
                    AppScreen.TRANSLATOR -> JolieTranslatorScreen(tts)
                    AppScreen.AGENDA -> JolieAgendaScreen()
                    AppScreen.PHOTO_EDITOR -> JoliePhotoEditorScreen()
                    AppScreen.VPN -> JolieVpnScreen(vpnActive, onToggleVpn = { vpnActive = it })
                    else -> {}
                }
            }
        }
    }
}

@Composable
fun NavigationTabButton(title: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .border(1.dp, if (selected) Color(0xFFFF3333) else Color(0xFF333333))
            .background(if (selected) Color(0xFF1A0505) else Color(0xFF0A0A0A))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = title,
            color = if (selected) Color(0xFFFF3333) else Color.White,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

// ==========================================
// PANTALLA 1: ONE-TAP SETUP
// ==========================================
@Composable
fun JolieSetupScreen(onSetupComplete: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .border(2.dp, Color(0xFFFF3333))
                .background(Color(0xFF111111)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🐶\n[J]",
                color = Color.White,
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "JOLIE // OS INITIALIZATION",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Optimizado para Realme C85 & Poco X4. Configuración de túneles anti-censura, bases de datos Room y motores AI locales.",
            color = Color.Gray,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(36.dp))

        Button(
            onClick = onSetupComplete,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3333)),
            shape = MaterialTheme.shapes.extraSmall,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(
                text = "ACEPTAR Y LISTO",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// ==========================================
// PANTALLA 2: CHAT INTELIGENTE (JOLIE)
// ==========================================
@Composable
fun JolieChatScreen() {
    var textInput by remember { mutableStateOf("") }
    var chatHistory by remember { 
        mutableStateOf(
            listOf(
                ChatMessage("Jolie", "¡Hola! Sistema optimizado para Realme y Poco. ¿Qué deseas consultar hoy?")
            )
        ) 
    }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .imePadding()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(chatHistory) { message ->
                val isUser = message.sender == "Tú"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (isUser) Color(0xFF333333) else Color(0xFFFF3333))
                        .background(if (isUser) Color(0xFF141414) else Color(0xFF0D0D0D))
                        .padding(14.dp)
                ) {
                    Column {
                        Text(
                            text = "[ ${message.sender.uppercase()} ]",
                            color = if (isUser) Color.Gray else Color(0xFFFF3333),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SelectionContainer {
                            Text(
                                text = message.text,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                modifier = Modifier.weight(1f).background(Color(0xFF111111)),
                placeholder = { Text("Escribe una orden...", color = Color.DarkGray, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFF3333),
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
                        val reply = "Procesado por Jolie AI: \"$query\"."
                        chatHistory = chatHistory + ChatMessage("Jolie", reply)
                        coroutineScope.launch {
                            if (chatHistory.isNotEmpty()) listState.animateScrollToItem(chatHistory.size - 1)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3333)),
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Text("ENVIAR", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

// ==========================================
// PANTALLA 3: TRADUCTOR BIDIRECCIONAL
// ==========================================
@Composable
fun JolieTranslatorScreen(tts: TextToSpeech?) {
    var sourceText by remember { mutableStateOf("") }
    var translatedText by remember { mutableStateOf("Traducción automática en tiempo real...") }
    var directionItToRu by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (directionItToRu) "MODO: ITALIANO ➔ RUSO" else "MODO: RUSO ➔ ITALIANO",
                color = Color(0xFFFF3333),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Button(
                onClick = { directionItToRu = !directionItToRu },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Text("INVERTIR", color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }

        OutlinedTextField(
            value = sourceText,
            onValueChange = { 
                sourceText = it
                translatedText = if (directionItToRu) {
                    "Traducción Rusa: \"$it\"."
                } else {
                    "Traduzione Italiana: \"$it\"."
                }
            },
            modifier = Modifier.fillMaxWidth().height(120.dp).background(Color(0xFF111111)),
            placeholder = { Text(if (directionItToRu) "Escribe en italiano..." else "Scrivi in russo...", color = Color.Gray, fontFamily = FontFamily.Monospace) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFFF3333),
                unfocusedBorderColor = Color(0xFF333333),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF333333))
                .background(Color(0xFF0A0A0A))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "[ RESULTADO ]",
                    color = Color(0xFFFF3333),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                SelectionContainer {
                    Text(
                        text = translatedText,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Button(
            onClick = {
                tts?.speak(translatedText, TextToSpeech.QUEUE_FLUSH, null, null)
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3333)),
            shape = MaterialTheme.shapes.extraSmall,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("REPRODUCIR VOZ (JOLIE TTS)", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}

// ==========================================
// PANTALLA 4: AGENDA & SECRETARIA
// ==========================================
@Composable
fun JolieAgendaScreen() {
    val context = LocalContext.current
    var eventTitle by remember { mutableStateOf("") }
    var eventList by remember { mutableStateOf(listOf("• Reunión Estratégica AI -> 10:00 AM", "• Sincronización VPN -> 04:30 PM")) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "SECRETARÍA & GOOGLE CALENDAR",
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )

        OutlinedTextField(
            value = eventTitle,
            onValueChange = { eventTitle = it },
            modifier = Modifier.fillMaxWidth().background(Color(0xFF111111)),
            placeholder = { Text("Nuevo evento...", color = Color.Gray, fontFamily = FontFamily.Monospace) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFFF3333),
                unfocusedBorderColor = Color(0xFF333333),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            singleLine = true
        )

        Button(
            onClick = {
                if (eventTitle.isNotBlank()) {
                    eventList = eventList + "• $eventTitle"
                    try {
                        val intent = Intent(Intent.ACTION_INSERT).apply {
                            data = CalendarContract.Events.CONTENT_URI
                            putExtra(CalendarContract.Events.TITLE, eventTitle)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                    eventTitle = ""
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3333)),
            shape = MaterialTheme.shapes.extraSmall,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("PROGRAMAR EN CALENDAR", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }

        Divider(color = Color(0xFF222222))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(eventList) { ev ->
                Box(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF111111)).padding(12.dp)
                ) {
                    Text(text = ev, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?text=Hola,%20reporte%20desde%20Jolie%20OS"))
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Text("WHATSAPP", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }

            Button(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:?subject=Reporte%20Jolie")).apply {
                            setPackage("com.google.android.gm")
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Text("GMAIL", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

// ==========================================
// PANTALLA 5: AI PHOTO STUDIO
// ==========================================
@Composable
fun JoliePhotoEditorScreen() {
    var selectedFilter by remember { mutableStateOf("Monocromo High-Contrast") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "AI PHOTO STUDIO // PRO",
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFFF3333))
                .background(Color(0xFF110505))
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🐶", fontSize = 24.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "¡Oye! He detectado un brillo extraño en la foto. ¿Quieres que aplique limpieza IA?",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF111111))
                .border(1.dp, Color(0xFF333333)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "[ VISTA PREVIA ]\nFiltro: $selectedFilter",
                color = Color.Gray,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                fontSize = 13.sp
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { selectedFilter = "Borrador Mágico & Inpainting" },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E1E)),
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("BORRADOR MÁGICO AI", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }

            Button(
                onClick = { selectedFilter = "Super-Resolución x4 Upscaling" },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E1E)),
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("MEJORA DE DETALLES & UPSCALING x4", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }
    }
}

// ==========================================
// PANTALLA 6: VPN INTELIGENTE
// ==========================================
@Composable
fun JolieVpnScreen(vpnActive: Boolean, onToggleVpn: (Boolean) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "TÚNEL ANTI-CENSURA / VPN",
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .size(120.dp)
                .border(2.dp, if (vpnActive) Color(0xFF00FF66) else Color(0xFFFF3333))
                .background(Color(0xFF111111)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (vpnActive) "SECURE\nACTIVE" else "DIRECT\nOFFLINE",
                color = if (vpnActive) Color(0xFF00FF66) else Color(0xFFFF3333),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { onToggleVpn(!vpnActive) },
            colors = ButtonDefaults.buttonColors(containerColor = if (vpnActive) Color(0xFFFF3333) else Color(0xFF00FF66)),
            shape = MaterialTheme.shapes.extraSmall,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(
                text = if (vpnActive) "DESCONECTAR TÚNEL" else "ACTIVAR VPN ANTI-BLOQUEO",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
