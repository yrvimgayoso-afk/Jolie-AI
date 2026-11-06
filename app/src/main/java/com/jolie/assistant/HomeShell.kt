package com.jolie.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

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
    val context = LocalContext.current
    var tab by remember { mutableStateOf(HomeTab.CHAT) }
    remember {
        JolieProxy.install()
        true
    }

    JolieBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MonoText("JOLIE", size = 26, bold = true, spacing = 4f)
                // Tocando el indicador se enciende o apaga la VPN automática
                JolieVpnPill(lang) { JolieProxy.toggle(context, lang) }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    HomeTab.CHAT -> AiChatScreen(lang)
                    HomeTab.AGENDA -> AgendaScreen(lang,
                        tr(lang, "AGENDA", "ПЛАН"),
                        tr(lang, "Agenda intelligente a voce: in arrivo.", "Умный календарь с голосом: скоро.")
                    )
                    HomeTab.TRAD -> ComingSoon(
                        tr(lang, "TRADUTTORE", "ПЕРЕВОДЧИК"),
                        tr(lang, "Traduzione vocale IT ⇄ RU: in arrivo.", "Голосовой перевод IT ⇄ RU: скоро.")
                    )
                    HomeTab.FOTO -> PhotoStudioScreen(lang)
                    HomeTab.VPN -> VpnAutoScreen(lang, onResetSetup)
                }
            }

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
