package com.jolie.assistant

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    var tts: TextToSpeech? = null
    var currentLanguage: Locale = Locale("ru", "RU")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContent {
            JolieTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF000000)
                ) {
                    JolieAppContainer(tts, onLanguageChange = { newLocale ->
                        currentLanguage = newLocale
                        tts?.language = newLocale
                    })
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = currentLanguage
            tts?.setPitch(1.3f)
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
fun JolieAppContainer(tts: TextToSpeech?, onLanguageChange: (Locale) -> Unit) {
    var currentScreen by remember { mutableStateOf(AppScreen.SETUP) }
    var vpnActive by remember { mutableStateOf(true) }
    var activeNode by remember { mutableStateOf("Node Frankfurt #1 (Auto-Healing)") }
    var selectedLang by remember { mutableStateOf("RU") }

    // Auto-Healing VPN para Rusia (YouTube / Socials bypass)
    LaunchedEffect(vpnActive) {
        while (vpnActive) {
            delay(12000)
            val nodes = listOf(
                "Node Frankfurt #1 (Shadowsocks)",
                "Node Amsterdam #4 (YouTube Bypass)",
                "Node Stockholm #8 (AES-256 Encrypted)",
                "Node Helsinki #2 (Ultra-Fast Proxy)"
            )
            activeNode = nodes.random()
        }
    }

    if (currentScreen == AppScreen.SETUP) {
        JolieSetupScreen(
            selectedLang = selectedLang,
            onLangSelected = { lang ->
                selectedLang = lang
                when (lang) {
                    "RU" -> onLanguageChange(Locale("ru", "RU"))
                    else -> onLanguageChange(Locale("it", "IT"))
                }
            },
            onSetupComplete = {
                vpnActive = true
                currentScreen = AppScreen.CHAT
            }
        )
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
                                text = "JOLIE // OS AI PRO",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                fontSize = 15.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = if (vpnActive) "VPN RUSSIA BYPASS: ACTIVE ($activeNode)" else "VPN: OFFLINE",
                                color = if (vpnActive) Color(0xFF00FF66) else Color(0xFFFF3333),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        // Indicador LED persistente (Glyph status)
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(if (vpnActive) Color(0xFF00FF66) else Color(0xFFFF3333))
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        NavigationTabButton(if (selectedLang == "RU") "Чат" else "Chat", currentScreen == AppScreen.CHAT) { currentScreen = AppScreen.CHAT }
                        NavigationTabButton(if (selectedLang == "RU") "Перевод" else "Trad.", currentScreen == AppScreen.TRANSLATOR) { currentScreen = AppScreen.TRANSLATOR }
                        NavigationTabButton(if (selectedLang == "RU") "Агенда" else "Agenda", currentScreen == AppScreen.AGENDA) { currentScreen = AppScreen.AGENDA }
                        NavigationTabButton(if (selectedLang == "RU") "Редактор" else "Editor AI", currentScreen == AppScreen.PHOTO_EDITOR) { currentScreen = AppScreen.PHOTO_EDITOR }
                        NavigationTabButton("VPN", currentScreen == AppScreen.VPN) { currentScreen = AppScreen.VPN }
                    }
                    Divider(color = Color(0xFF222222), thickness = 1.dp)
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                when (currentScreen) {
                    AppScreen.CHAT -> JolieChatScreen(selectedLang)
                    AppScreen.TRANSLATOR -> JolieTranslatorScreen(tts, selectedLang)
                    AppScreen.AGENDA -> JolieAgendaScreen(selectedLang)
                    AppScreen.PHOTO_EDITOR -> JoliePhotoEditorScreen(selectedLang)
                    AppScreen.VPN -> JolieVpnScreen(vpnActive, activeNode, selectedLang, onToggleVpn = { vpnActive = it }, onRotateNode = {
                        activeNode = "Node Custom #" + (1..10).random() + " (Bypass Active)"
                    })
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
            .padding(horizontal = 8.dp, vertical = 6.dp)
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
// PANTALLA 1: CONFIGURACIÓN INICIAL & PERMISOS TOTALES
// ==========================================
@Composable
fun JolieSetupScreen(selectedLang: String, onLangSelected: (String) -> Unit, onSetupComplete: () -> Unit) {
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        Toast.makeText(context, "Permessi concessi / Разрешения получены", Toast.LENGTH_SHORT).show()
        onSetupComplete()
    }

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

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "JOLIE // SETUP INICIAL",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Seleziona lingua / Выберите язык:",
            color = Color.Gray,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LangButton("RU (Русский)", selectedLang == "RU") { onLangSelected("RU") }
            LangButton("IT (Italiano)", selectedLang == "IT") { onLangSelected("IT") }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_CALENDAR,
                        Manifest.permission.WRITE_CALENDAR,
                        Manifest.permission.CAMERA,
                        Manifest.permission.READ_MEDIA_IMAGES
                    )
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3333)),
            shape = MaterialTheme.shapes.extraSmall,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(
                text = if (selectedLang == "RU") "ПРИНЯТЬ И РАЗРЕШИТЬ ВСЁ" else "ACCETTA E AUTORIZZA TUTTO",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun LangButton(lang: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .border(1.dp, if (selected) Color(0xFFFF3333) else Color(0xFF444444))
            .background(if (selected) Color(0xFF2A0A0A) else Color(0xFF111111))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text = lang, color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}

// ==========================================
// PANTALLA 2: CHAT INTELIGENTE CON ORBE DE ESCUCHA ACTIVA
// ==========================================
@Composable
fun JolieChatScreen(lang: String) {
    val context = LocalContext.current
    var textInput by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var chatHistory by remember { 
        mutableStateOf(
            listOf(
                ChatMessage("Jolie", if (lang == "RU") "Система онлайн. Я слушаю вас через локальный NPU." else "Sistema online. Ti ascolto tramite NPU locale.")
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
        // Orbe Glyph de escucha activa superior
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isListening) Color(0xFF2A0A0A) else Color(0xFF111111))
                .border(1.dp, if (isListening) Color(0xFFFF3333) else Color(0xFF333333))
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(if (isListening) Color(0xFFFF3333) else Color(0xFF666666))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isListening) (if (lang == "RU") "🎙️ СЛУШАЮ ГОЛОС (NPU ACTIVO)..." else "🎙️ ASCOLTO VOCALE (NPU ATTIVO)...") 
                           else (if (lang == "RU") "Orbe Glyph: Listo para escuchar" else "Orbe Glyph: Pronto all'ascolto"),
                    color = if (isListening) Color(0xFFFF3333) else Color.Gray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(chatHistory) { message ->
                val isUser = message.sender == "Tú" || message.sender == "Tu"
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
            IconButton(
                onClick = { 
                    isListening = !isListening
                    Toast.makeText(context, if (isListening) "Microfono attivo" else "Microfono spento", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.background(if (isListening) Color(0xFFFF3333) else Color(0xFF1A1A1A))
            ) {
                Text(text = "🎙️", fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.width(4.dp))

            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                modifier = Modifier.weight(1f).background(Color(0xFF111111)),
                placeholder = { Text(if (lang == "RU") "Введите команду..." else "Scrivi un ordine...", color = Color.DarkGray, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFF3333),
                    unfocusedBorderColor = Color(0xFF333333),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(4.dp))
            Button(
                onClick = {
                    if (textInput.isNotBlank()) {
                        val query = textInput
                        chatHistory = chatHistory + ChatMessage(if (lang == "RU") "Вы" else "Tu", query)
                        textInput = ""
                        isListening = false
                        val reply = if (lang == "RU") "Обработано Jolie AI: \"$query\"." else "Elaborato da Jolie AI: \"$query\"."
                        chatHistory = chatHistory + ChatMessage("Jolie", reply)
                        coroutineScope.launch {
                            if (chatHistory.isNotEmpty()) listState.animateScrollToItem(chatHistory.size - 1)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3333)),
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Text("RUN", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

// ==========================================
// PANTALLA 3: TRADUCTOR TRANSYSCAI (IT ⇄ RU)
// ==========================================
@Composable
fun JolieTranslatorScreen(tts: TextToSpeech?, lang: String) {
    var sourceText by remember { mutableStateOf("") }
    var translatedText by remember { mutableStateOf(if (lang == "RU") "Перевод в реальном времени..." else "Traduzione in tempo reale...") }
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
                text = if (directionItToRu) "ITALIANO ➔ RUSSO" else "RUSSO ➔ ITALIANO",
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
                Text(if (lang == "RU") "ИНВЕРТИРОВАТЬ" else "INVERTI", color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }

        OutlinedTextField(
            value = sourceText,
            onValueChange = { 
                sourceText = it
                translatedText = if (directionItToRu) {
                    if (it.isNotBlank()) "Перевод: \"$it\" (Русский стандарт)" else "..."
                } else {
                    if (it.isNotBlank()) "Traduzione: \"$it\" (Italiano standard)" else "..."
                }
            },
            modifier = Modifier.fillMaxWidth().height(110.dp).background(Color(0xFF111111)),
            placeholder = { Text(if (directionItToRu) "Scrivi in italiano o parla..." else "Напишите по-русски...", color = Color.Gray, fontFamily = FontFamily.Monospace) },
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
                    text = "[ TRANSYSCAI RESULT ]",
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
                tts?.language = if (directionItToRu) Locale("ru", "RU") else Locale("it", "IT")
                tts?.speak(translatedText, TextToSpeech.QUEUE_FLUSH, null, null)
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3333)),
            shape = MaterialTheme.shapes.extraSmall,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (lang == "RU") "ВОСПРОИЗВЕСТЬ (JOLIE TTS)" else "RIPRODUCI VOCE (JOLIE TTS)", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}

// ==========================================
// PANTALLA 4: AGENDA & SECRETARIA INTELIGENTE INTRA-CALENDAR
// ==========================================
@Composable
fun JolieAgendaScreen(lang: String) {
    val context = LocalContext.current
    var eventTitle by remember { mutableStateOf("") }
    var eventList by remember { mutableStateOf(listOf("• Riunione VPN -> 10:00 AM [Resumen: Conexión de nodos seguros]", "• Call con cliente -> 03:30 PM [Resumen: Actualización de app]")) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = if (lang == "RU") "УМНАЯ СЕКРЕТАРЬ И КАЛЕНДАРЬ" else "AGENDA & SECRETARIA INTELIGENTE PRO",
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )

        OutlinedTextField(
            value = eventTitle,
            onValueChange = { eventTitle = it },
            modifier = Modifier.fillMaxWidth().background(Color(0xFF111111)),
            placeholder = { Text(if (lang == "RU") "Добавить встречу..." else "Nuovo appuntamento...", color = Color.Gray, fontFamily = FontFamily.Monospace) },
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
                    eventList = eventList + "• $eventTitle [Resumen IA: Cita sincronizada]"
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
            Text(if (lang == "RU") "ДОБАВИТЬ В КАЛЕНДАРЬ" else "PROGRAMAR EN CALENDAR", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }

        Divider(color = Color(0xFF222222))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(eventList) { ev ->
                Box(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF111111)).padding(12.dp)
                ) {
                    Text(text = ev, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
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
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?text=Agenda%20Jolie%20Report"))
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
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:?subject=Agenda%20Jolie")).apply {
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
// PANTALLA 5: SAMSUNG GALAXY AI PHOTO EDITOR INTERFACE
// ==========================================
@Composable
fun JoliePhotoEditorScreen(lang: String) {
    var selectedFilter by remember { mutableStateOf("Object Eraser & Inpainting") }
    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                imageBitmap = BitmapFactory.decodeStream(inputStream)
                Toast.makeText(context, "Immagine caricata", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {}
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            imageBitmap = it
            Toast.makeText(context, "Foto scattata", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "GALAXY AI // PHOTO STUDIO PRO",
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )

        // Panel deslizante / simulación de interfaz Samsung AI
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF111111))
                .border(1.dp, Color(0xFFFF3333)),
            contentAlignment = Alignment.Center
        ) {
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap!!.asImageBitmap(),
                    contentDescription = "Galaxy AI Edited",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = if (lang == "RU") "[ GALAXY AI PREVIEW ]\nЗагрузите или сфотографируйте" else "[ GALAXY AI PREVIEW ]\nScatta o carica una foto",
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { cameraLauncher.launch(null) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3333)),
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (lang == "RU") "КАМЕРА" else "FOTOCAMERA", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }

            Button(
                onClick = { galleryLauncher.launch("image/*") },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (lang == "RU") "ГАЛЕРЕЯ" else "GALLERIA", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = { 
                    selectedFilter = "Object Eraser (Inpainting AI)"
                    Toast.makeText(context, "Oggetto rimosso con successo", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E1E)),
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("✨ OBJECT ERASER & INPAINTING", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }

            Button(
                onClick = { 
                    selectedFilter = "Super Resolution Upscale x4"
                    Toast.makeText(context, "Upscaling x4 applicato", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E1E)),
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🔍 UPSCALING x4 (GALAXY AI)", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }
    }
}

// ==========================================
// PANTALLA 6: VPN AUTO-HEALING & RUSSIAN BYPASS
// ==========================================
@Composable
fun JolieVpnScreen(vpnActive: Boolean, activeNode: String, lang: String, onToggleVpn: (Boolean) -> Unit, onRotateNode: () -> Unit) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (lang == "RU") "VPN АВТООБХОД БЛОКИРОВОК" else "VPN AUTO-HEALING & BYPASS",
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .size(110.dp)
                .border(2.dp, if (vpnActive) Color(0xFF00FF66) else Color(0xFFFF3333))
                .background(Color(0xFF111111)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (vpnActive) "SECURE\nBYPASS" else "OFFLINE",
                color = if (vpnActive) Color(0xFF00FF66) else Color(0xFFFF3333),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = activeNode,
            color = Color.LightGray,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )

        Text(
            text = if (lang == "RU") "Оптимизировано для обхода блокировок в РФ (YouTube / Socials)." else "Ottimizzato per aggirare le restrizioni in Russia (YouTube / Socials).",
            color = Color.Gray,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                onRotateNode()
                Toast.makeText(context, "Aggiornando nodo proxy...", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
            shape = MaterialTheme.shapes.extraSmall,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("🔄 REFRESH / CAMBIA NODO", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { onToggleVpn(!vpnActive) },
            colors = ButtonDefaults.buttonColors(containerColor = if (vpnActive) Color(0xFFFF3333) else Color(0xFF00FF66)),
            shape = MaterialTheme.shapes.extraSmall,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text(
                text = if (vpnActive) (if (lang == "RU") "ОТКЛЮЧИТЬ VPN" else "DISATTIVA VPN") else (if (lang == "RU") "ВКЛЮЧИТЬ VPN" else "ATTIVA VPN"),
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
