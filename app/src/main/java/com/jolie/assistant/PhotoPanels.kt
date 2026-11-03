package com.jolie.assistant

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Stato condiviso della schermata Foto: scheda scelta, regolazioni, tratti della gomma, testo per l'IA. */
class PhotoUi {
    var tool by mutableStateOf(0)
    var brightness by mutableStateOf(0f)
    var contrast by mutableStateOf(0f)
    var saturation by mutableStateOf(0f)
    var warmth by mutableStateOf(0f)
    var preset by mutableStateOf(0)
    var brush by mutableStateOf(0.4f)
    var aiPrompt by mutableStateOf("")
    var aiModel by mutableStateOf(IMAGE_MODELS[0])
    val strokes = mutableStateListOf<List<Pair<Float, Float>>>()
    var current by mutableStateOf<List<Pair<Float, Float>>>(emptyList())

    fun resetAdjust() {
        brightness = 0f
        contrast = 0f
        saturation = 0f
        warmth = 0f
        preset = 0
    }
}

@Composable
private fun SliderRow(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        MonoText(label, size = 10, color = JolieColors.Gray, modifier = Modifier.width(84.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = -100f..100f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = JolieColors.Red,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
            )
        )
    }
}

@Composable
private fun PhotoField(value: String, hint: String, onChange: (String) -> Unit) {
    GlassSurface(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            textStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
            cursorBrush = SolidColor(Color.White),
            singleLine = true,
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) MonoText(hint, size = 12, color = JolieColors.Gray)
                    inner()
                }
            }
        )
    }
}

/** Le opzioni del menu scelto (REGOLA, FILTRI, RITAGLIO, GOMMA, STILI, IA). */
@Composable
fun ToolPanel(
    ui: PhotoUi,
    lang: String,
    busy: Boolean,
    onBake: () -> Unit,
    onErase: () -> Unit,
    onAi: () -> Unit,
    onStyle: (StyleDef) -> Unit,
    onVideo: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 210.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (ui.tool) {
            0 -> {
                SliderRow(tr(lang, "LUCE", "СВЕТ"), ui.brightness) { ui.brightness = it }
                SliderRow(tr(lang, "CONTRASTO", "КОНТРАСТ"), ui.contrast) { ui.contrast = it }
                SliderRow(tr(lang, "COLORE", "ЦВЕТ"), ui.saturation) { ui.saturation = it }
                SliderRow(tr(lang, "CALORE", "ТЕПЛОТА"), ui.warmth) { ui.warmth = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton(tr(lang, "APPLICA", "ПРИМЕНИТЬ"), onBake, primary = true)
                    SmallButton(tr(lang, "AZZERA", "СБРОС"), { ui.resetAdjust() })
                }
            }
            1 -> {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PRESETS.forEachIndexed { i, p ->
                        OptionChip(tr(lang, p.itName, p.ruName), ui.preset == i, { ui.preset = i })
                    }
                }
                SmallButton(tr(lang, "APPLICA", "ПРИМЕНИТЬ"), onBake, primary = true)
            }
            2 -> {
                SectionLabel(tr(lang, "ORIENTAMENTO", "ПОВОРОТ"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OptionChip(tr(lang, "RUOTA", "ПОВЕРНУТЬ"), false, { PhotoStore.base?.let { PhotoStore.set(rotate90(it)) } })
                    OptionChip(tr(lang, "SPECCHIO", "ЗЕРКАЛО"), false, { PhotoStore.base?.let { PhotoStore.set(flipH(it)) } })
                }
                SectionLabel(tr(lang, "FORMATO", "ФОРМАТ"))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OptionChip("1:1", false, { PhotoStore.base?.let { PhotoStore.set(cropToRatio(it, 1, 1)) } })
                    OptionChip("4:3", false, { PhotoStore.base?.let { PhotoStore.set(cropToRatio(it, 4, 3)) } })
                    OptionChip("3:4", false, { PhotoStore.base?.let { PhotoStore.set(cropToRatio(it, 3, 4)) } })
                    OptionChip("16:9", false, { PhotoStore.base?.let { PhotoStore.set(cropToRatio(it, 16, 9)) } })
                }
            }
            3 -> {
                MonoText(
                    tr(
                        lang,
                        "Disegna con il dito sopra ciò che vuoi togliere, poi premi CANCELLA. Va bene per oggetti piccoli.",
                        "Проведите пальцем по тому, что нужно убрать, затем нажмите СТЕРЕТЬ. Подходит для мелких объектов."
                    ),
                    size = 11,
                    color = JolieColors.Gray
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MonoText(tr(lang, "PENNELLO", "КИСТЬ"), size = 10, color = JolieColors.Gray, modifier = Modifier.width(84.dp))
                    Slider(
                        value = ui.brush,
                        onValueChange = { ui.brush = it },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = JolieColors.Red,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                        )
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton(tr(lang, "CANCELLA", "СТЕРЕТЬ"), onErase, primary = true)
                    SmallButton(tr(lang, "ANNULLA TRATTO", "СБРОС МАЗКА"), { ui.strokes.clear() })
                }
            }
            4 -> {
                MonoText(
                    tr(
                        lang,
                        "Un tocco e lo stile viene applicato. Quasi tutti funzionano gratis sul telefono, anche senza internet.",
                        "Одно касание, и стиль применяется. Почти все работают бесплатно на телефоне, даже без интернета."
                    ),
                    size = 11,
                    color = JolieColors.Gray
                )
                val groups = listOf(
                    tr(lang, "PITTURA", "ЖИВОПИСЬ"),
                    tr(lang, "PIXEL ART", "ПИКСЕЛЬ-АРТ"),
                    tr(lang, "POP ART", "ПОП-АРТ")
                )
                for (g in 0..2) {
                    SectionLabel(groups[g])
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        STYLES.filter { it.group == g }.forEach { s ->
                            OptionChip(tr(lang, s.itName, s.ruName), false, { if (!busy) onStyle(s) })
                        }
                    }
                }
            }
            else -> {
                MonoText(
                    tr(
                        lang,
                        "Descrivi la modifica (meglio in inglese). Usa Gemini online: può non essere incluso nel piano gratuito.",
                        "Опишите правку (лучше по-английски). Используется Gemini онлайн: может не входить в бесплатный план."
                    ),
                    size = 11,
                    color = JolieColors.Gray
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OptionChip(tr(lang, "SFONDO", "ФОН"), false, {
                        ui.aiPrompt = "Remove the background and replace it with a clean white background. Keep the subject exactly the same."
                    })
                    OptionChip(tr(lang, "LUCE", "СВЕТ"), false, {
                        ui.aiPrompt = "Improve the lighting and colors in a natural way. Do not change the content."
                    })
                    OptionChip(tr(lang, "PERSONE", "ЛЮДИ"), false, {
                        ui.aiPrompt = "Remove the people in the background. Keep everything else exactly the same."
                    })
                }
                PhotoField(ui.aiPrompt, tr(lang, "Es. rendi il cielo più blu", "Напр. сделай небо синее"), { ui.aiPrompt = it })
                PhotoField(ui.aiModel, IMAGE_MODELS[0], { ui.aiModel = it })
                GlassButton(
                    tr(lang, "MODIFICA CON IA", "ИЗМЕНИТЬ С ИИ"),
                    onClick = onAi,
                    modifier = Modifier.fillMaxWidth(),
                    filled = true
                )
                if (busy) {
                    GlassButton(
                        tr(lang, "GUARDA UN VIDEO INTANTO", "ПОСМОТРЕТЬ ВИДЕО ПОКА ЖДЁТЕ"),
                        onClick = onVideo,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
