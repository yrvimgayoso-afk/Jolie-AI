package com.jolie.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

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
            primary = Color(0xFFD71921), // Rojo acento Nothing OS
            onPrimary = Color.White,
            onBackground = Color.White,
            onSurface = Color.White
        ),
        content = content
    )
}

@Composable
fun JolieMainScreen() {
    var textInput by remember { mutableStateOf("") }
    var chatHistory by remember { 
        mutableStateOf(
            listOf(
                ChatMessage("Jolie", "Sistema operativo v1.0 en línea. Núcleo IA activo. ¿Qué comando deseas ejecutar?")
            )
        ) 
    }
    
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Cabecera Minimalista Nothing OS
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
                        .background(Color(0xFFD71921)) // Indicador LED rojo
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = Color(0xFF262626), thickness = 1.dp)
        }

        // Historial de Conversación Desplazable
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

        // Barra de Comandos / Entrada
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
                placeholder = { Text("Escribe una orden o prompt...", color = Color.Gray, fontFamily = FontFamily.Monospace) },
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
                        val updatedHistory = chatHistory + ChatMessage("Tú", query)
                        
                        // Procesamiento del Super Asistente (Lógica inteligente local / Simulador de respuestas complejas)
                        val assistantResponse = generateJolieResponse(query)
                        
                        chatHistory = updatedHistory + ChatMessage("Jolie", assistantResponse)
                        textInput = ""
                        
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

// Motor de Inteligencia Interno de Jolie (Basado en los maxi-prompts)
fun generateJolieResponse(input: String): String {
    val lower = input.lowercase()
    return when {
        lower.contains("carta") && lower.contains("ruso") -> 
            "Дорогая, моя любовь к тебе чиста и вечна, как звезды на ночном небушке... (Traducción y redacción completada con éxito bajo parámetros de alta sensibilidad poética)."
        lower.contains("vpn") || lower.contains("red") -> 
            "Módulo de Red: Túnel seguro cifrado listo. Estado del nodo: Estable (Simulación Nothing OS Active)."
        lower.contains("imagen") || lower.contains("foto") -> 
            "Procesador gráfico: No se detectó imagen de entrada. Sube una fotografía para aplicar filtros monocromáticos y estéticos."
        lower.contains("hola") || lower.contains("saludos") -> 
            "Saludos. Los sistemas operativos y de asistencia cognitiva se encuentran al 100% de su capacidad."
        else -> 
            "Comando procesado: \"$input\". Análisis completado con éxito por el núcleo de Jolie AI."
    }
}
