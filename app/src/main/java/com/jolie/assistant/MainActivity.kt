package com.jolie.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JolieTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0A0A0A) // Fondo oscuro estilo Nothing OS
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
            primary = Color(0xFFD71921), // Rojo acento Nothing
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
    var chatHistory by remember { mutableStateOf(listOf("Jolie: Sistema en línea. ¿Qué deseas hacer?")) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Cabecera Minimalista
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "JOLIE // OS",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                    fontSize = 18.sp
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFFD71921)) // Indicador rojo Nothing
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFF262626), thickness = 1.dp)
        }

        // Historial de conversación / Contenedor central
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            chatHistory.forEach { message ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(Color(0xFF161616))
                        .padding(16.dp)
                ) {
                    Text(
                        text = message,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Barra de Entrada Estilo Dot Matrix / Minimal
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
                placeholder = { Text("Escribe un comando...", color = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.White,
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
                        val userMsg = textInput
                        chatHistory = chatHistory + "Tú: $userMsg"
                        textInput = ""
                        chatHistory = chatHistory + "Jolie: Entendido. Analizando consulta..."
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD71921)),
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Text("ENVIAR", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
