package com.jolie.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class HomeTab { CHAT, AGENDA, TRAD, FOTO, VPN }

private fun tabLabel(lang: String, tab: HomeTab): String = when (tab) {
    HomeTab.CHAT -> tr(lang, "CHAT", "ЧАТ")
    HomeTab.AGENDA -> tr(lang, "AGENDA", "ПЛАН")
    HomeTab.TRAD -> tr(lang, "TRAD.", "ПЕРЕВОД")
    HomeTab.FOTO -> tr(lang, "FOTO", "ФОТО")
    HomeTab.VPN -> "VPN"
}

@Composable
fun HomeShell(lang: String, onResetSetup: () -> Unit) {
    var tab by remember { mutableStateOf(HomeTab.CHAT) }

    JolieBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            // Barra superiore
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MonoText("JOLIE", size = 26, bold = true, spacing = 4f)
                GlassSurface(shape = RoundedCornerShape(17.dp)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(JolieColors.Gray))
                        Spacer(Modifier.width(8.dp))
                        // Onesto: la VPN vera non c'è ancora, quindi risulta spenta.
                        MonoText(tr(lang, "VPN SPENTA", "VPN ВЫКЛ."), size = 11, spacing = 1f)
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    HomeTab.CHAT -> AiChatScreen(lang)
                    HomeTab.AGENDA -> ComingSoon(
                        tr(lang, "AGENDA", "ПЛАН"),
                        tr(lang, "Agenda intelligente a voce: in arrivo nella fase 2.", "Умный календарь с голосом: в фазе 2.")
                    )
                    HomeTab.TRAD -> ComingSoon(
                        tr(lang, "TRADUTTORE", "ПЕРЕВОДЧИК"),
                        tr(lang, "Traduzione vocale IT ⇄ RU: in arrivo nella fase 3.", "Голосовой перевод IT ⇄ RU: в фазе 3.")
                    )
                    HomeTab.FOTO -> ComingSoon(
                        tr(lang, "FOTO STUDIO", "ФОТО СТУДИЯ"),
                        tr(lang, "Editor foto: in arrivo nella fase 4.", "Фоторедактор: в фазе 4.")
                    )
                    HomeTab.VPN -> VpnScreen(lang, onResetSetup)
                }
            }

            // Barra di navigazione "glass"
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                shape = RoundedCornerShape(32.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HomeTab.values().forEach { t ->
                        val selected = t == tab
                        Box(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(if (selected) Color.White else Color.Transparent)
                                .clickable { tab = t }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            MonoText(
                                tabLabel(lang, t),
                                size = 11,
                                color = if (selected) Color.Black else Color.White,
                                spacing = 1f
                            )
                        }
                    }
                }
            }
        }
    }
}

data class ChatMsg(val fromUser: Boolean, val text: String)

@Composable
fun ChatScreen(lang: String) {
    val messages = remember {
        mutableStateListOf(
            ChatMsg(
                false,
                tr(
                    lang,
                    "Ciao, sono Jolie. Questa è la versione di anteprima: l’IA vera, con foto e documenti, arriva nella fase 2.",
                    "Привет, я Джоли. Это предварительная версия: настоящий ИИ с фото и документами будет в фазе 2."
                )
            )
        )
    }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        listState.animateScrollToItem(messages.size - 1)
    }

    fun send() {
        val t = input.trim()
        if (t.isEmpty()) return
        messages.add(ChatMsg(true, t))
        messages.add(
            ChatMsg(
                false,
                tr(
                    lang,
                    "Anteprima: ho ricevuto il messaggio, ma per ora non rispondo con l’IA.",
                    "Предпросмотр: сообщение получено, но ИИ пока не отвечает."
                )
            )
        )
        input = ""
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Orb(Modifier.size(110.dp))
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages) { m ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start
                ) {
                    GlassSurface(
                        modifier = Modifier.widthIn(max = 290.dp),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        MonoText(m.text, modifier = Modifier.padding(14.dp))
                    }
                }
            }
        }

        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    cursorBrush = SolidColor(Color.White),
                    singleLine = true,
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (input.isEmpty()) {
                                MonoText(tr(lang, "Chiedi a Jolie…", "Спросите Джоли…"), size = 14, color = JolieColors.Gray)
                            }
                            inner()
                        }
                    }
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(JolieColors.Red)
                        .clickable { send() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("↑", color = Color.White, fontSize = 20.sp)
                }
            }
        }
    }
}

@Composable
fun ComingSoon(title: String, text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MonoText(title, size = 11, color = JolieColors.RedSoft, spacing = 1.5f)
                MonoText(text)
            }
        }
    }
}

@Composable
fun VpnScreen(lang: String, onResetSetup: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                MonoText("VPN", size = 11, color = JolieColors.RedSoft, spacing = 1.5f)
                MonoText(
                    tr(
                        lang,
                        "La VPN vera arriva nell’ultima fase. Finché non c’è, risulta spenta: nessuna falsa protezione.",
                        "Настоящий VPN появится в последней фазе. Пока он выключен: никакой ложной защиты."
                    )
                )
                GlassButton(
                    tr(lang, "RIFAI CONFIGURAZIONE INIZIALE", "ПОВТОРИТЬ НАСТРОЙКУ"),
                    onClick = onResetSetup,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
