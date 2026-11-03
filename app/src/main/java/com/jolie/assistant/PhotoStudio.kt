package com.jolie.assistant

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import androidx.compose.ui.graphics.ColorMatrix as ComposeColorMatrix

@Composable
private fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    if (selected) {
        Box(
            modifier = modifier
                .heightIn(min = 44.dp)
                .clip(shape)
                .background(Color.White)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            MonoText(text, size = 10, color = Color.Black, bold = true, spacing = 1f)
        }
    } else {
        GlassSurface(modifier = modifier, shape = shape) {
            Box(
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                MonoText(text, size = 10, bold = true, spacing = 1f)
            }
        }
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
    GlassSurface(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp)) {
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

private fun friendlyImageError(lang: String, e: Throwable): String {
    if (e is GeminiHttpException) {
        val hint = when (e.code) {
            429, 403 -> tr(
                lang,
                "La modifica con IA potrebbe non essere inclusa nel piano gratuito, oppure Gemini non è raggiungibile da questa rete (in Russia serve la VPN).",
                "Редактирование с ИИ может не входить в бесплатный план, либо Gemini недоступен из этой сети (в России нужен VPN)."
            )
            404 -> tr(lang, "Modello immagini non trovato: cambia il nome del modello qui sotto.", "Модель изображений не найдена: измените название модели ниже.")
            else -> tr(lang, "Gemini è sovraccarico o ha avuto un errore. Riprova tra poco.", "Gemini перегружен или произошла ошибка. Повторите позже.")
        }
        return hint + " (" + e.code + ")"
    }
    val m = e.message ?: ""
    if (m.startsWith("NOIMAGE")) {
        return tr(lang, "L’IA non ha restituito un’immagine. Prova a descrivere meglio la modifica.", "ИИ не вернул изображение. Опишите правку подробнее.")
    }
    if (m == "TIMEOUT") {
        return tr(lang, "Tempo scaduto: Gemini non risponde da questa rete.", "Время вышло: Gemini не отвечает из этой сети.")
    }
    return tr(lang, "Errore di rete. Controlla la connessione (in Russia serve la VPN).", "Ошибка сети. Проверьте соединение (в России нужен VPN).") +
        " [" + e.javaClass.simpleName + "]"
}

private fun openYoutube(context: Context) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
    }
}

@Composable
fun PhotoStudioScreen(lang: String) {
    val context = LocalContext.current
    val photoPrefs = remember { context.getSharedPreferences("jolie_photo", Context.MODE_PRIVATE) }

    var tool by remember { mutableStateOf(0) }
    var brightness by remember { mutableStateOf(0f) }
    var contrast by remember { mutableStateOf(0f) }
    var saturation by remember { mutableStateOf(0f) }
    var warmth by remember { mutableStateOf(0f) }
    var preset by remember { mutableStateOf(0) }
    val strokes = remember { mutableStateListOf<List<Pair<Float, Float>>>() }
    var current by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    var brush by remember { mutableStateOf(0.4f) }
    var busy by remember { mutableStateOf(false) }
    var busyText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var aiPrompt by remember { mutableStateOf("") }
    var aiModel by remember { mutableStateOf(photoPrefs.getString("image_model", IMAGE_MODELS[0]) ?: IMAGE_MODELS[0]) }

    val base = PhotoStore.base
    val matrix = buildMatrix(PRESETS[preset].matrix, brightness, contrast, saturation, warmth)

    fun resetAdjust() {
        brightness = 0f
        contrast = 0f
        saturation = 0f
        warmth = 0f
        preset = 0
    }

    fun bake() {
        val b = PhotoStore.base ?: return
        PhotoStore.set(applyMatrix(b, matrix))
        resetAdjust()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            busyText = tr(lang, "Carico la foto…", "Загружаю фото…")
            runAsync {
                val bmp = loadBitmap(context, uri)
                onMain {
                    busy = false
                    if (bmp != null) {
                        PhotoStore.load(bmp)
                        strokes.clear()
                        resetAdjust()
                        status = ""
                    } else {
                        status = tr(lang, "Impossibile aprire questa foto.", "Не удалось открыть это фото.")
                    }
                }
            }
        }
    }

    fun applyErase() {
        val b = PhotoStore.base ?: return
        if (strokes.isEmpty() || busy) return
        busy = true
        busyText = tr(lang, "Cancello l’oggetto…", "Удаляю объект…")
        val snapshot = strokes.toList()
        val radius = (0.01f + brush * 0.07f) * max(b.width, b.height)
        runAsync {
            val result = try {
                inpaint(b, buildMask(b.width, b.height, snapshot, radius))
            } catch (_: Throwable) {
                null
            }
            onMain {
                busy = false
                if (result != null) {
                    PhotoStore.set(result)
                    strokes.clear()
                    status = ""
                } else {
                    status = tr(lang, "Operazione non riuscita.", "Не удалось выполнить.")
                }
            }
        }
    }

    fun runAi() {
        val b = PhotoStore.base ?: return
        val key = AiPrefs(context).geminiKey
        if (key.isBlank()) {
            status = tr(lang, "Prima imposta la chiave nella scheda CHAT (tasto CHIAVE).", "Сначала задайте ключ на вкладке ЧАТ (кнопка КЛЮЧ).")
            return
        }
        if (aiPrompt.isBlank() || busy) return
        photoPrefs.edit().putString("image_model", aiModel.trim()).apply()
        busy = true
        busyText = tr(lang, "Jolie sta lavorando… può volerci un minuto.", "Джоли работает… это может занять минуту.")
        status = ""
        editImageWithGemini(key, aiModel, b, aiPrompt) { r ->
            busy = false
            r.onSuccess {
                PhotoStore.set(it)
                strokes.clear()
                status = tr(lang, "Fatto.", "Готово.")
            }.onFailure {
                status = friendlyImageError(lang, it)
            }
        }
    }

    fun saveImage() {
        val b = PhotoStore.base ?: return
        if (busy) return
        val m = matrix
        busy = true
        busyText = tr(lang, "Salvo…", "Сохраняю…")
        runAsync {
            val ok = try {
                saveToGallery(context, applyMatrix(b, m))
            } catch (_: Throwable) {
                false
            }
            onMain {
                busy = false
                status = if (ok) {
                    tr(lang, "Salvata in Immagini/Jolie.", "Сохранено в Изображения/Jolie.")
                } else {
                    tr(lang, "Impossibile salvare la foto.", "Не удалось сохранить фото.")
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Chip(tr(lang, "FOTO", "ФОТО"), false, Modifier.weight(1f)) { picker.launch(arrayOf("image/*")) }
            Chip(tr(lang, "ANNULLA", "ОТМЕНА"), false, Modifier.weight(1f)) {
                status = if (PhotoStore.undoLast()) "" else tr(lang, "Niente da annullare.", "Нечего отменять.")
            }
            Chip(tr(lang, "SALVA", "СОХРАНИТЬ"), true, Modifier.weight(1f)) { saveImage() }
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (base == null) {
                GlassSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MonoText(tr(lang, "FOTO STUDIO", "ФОТО СТУДИЯ"), size = 11, color = JolieColors.RedSoft, spacing = 1.5f)
                        MonoText(
                            tr(
                                lang,
                                "Scegli una foto per regolarla, applicare filtri, ritagliarla, cancellare oggetti o modificarla con l’IA.",
                                "Выберите фото, чтобы настроить его, применить фильтры, обрезать, стереть объекты или изменить с помощью ИИ."
                            )
                        )
                        GlassButton(
                            tr(lang, "SCEGLI UNA FOTO", "ВЫБРАТЬ ФОТО"),
                            onClick = { picker.launch(arrayOf("image/*")) },
                            modifier = Modifier.fillMaxWidth(),
                            filled = true
                        )
                    }
                }
            } else {
                val image = remember(base) { base.asImageBitmap() }
                Image(
                    bitmap = image,
                    contentDescription = tr(lang, "Foto in modifica", "Редактируемое фото"),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.colorMatrix(ComposeColorMatrix(matrix))
                )
                if (tool == 3) {
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(base) {
                                val sc = minOf(size.width.toFloat() / base.width, size.height.toFloat() / base.height)
                                val ox = (size.width - base.width * sc) / 2f
                                val oy = (size.height - base.height * sc) / 2f
                                detectDragGestures(
                                    onDragStart = { o -> current = listOf(Pair((o.x - ox) / sc, (o.y - oy) / sc)) },
                                    onDragEnd = {
                                        if (current.isNotEmpty()) strokes.add(current)
                                        current = emptyList()
                                    },
                                    onDragCancel = { current = emptyList() },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        current = current + Pair((change.position.x - ox) / sc, (change.position.y - oy) / sc)
                                    }
                                )
                            }
                    ) {
                        val sc = minOf(size.width / base.width, size.height / base.height)
                        val ox = (size.width - base.width * sc) / 2f
                        val oy = (size.height - base.height * sc) / 2f
                        val radius = (0.01f + brush * 0.07f) * max(base.width, base.height) * sc
                        val paint = JolieColors.Red.copy(alpha = 0.55f)
                        val all = strokes.toList() + listOf(current)
                        for (s in all) {
                            if (s.isEmpty()) continue
                            if (s.size == 1) {
                                drawCircle(color = paint, radius = radius, center = Offset(ox + s[0].first * sc, oy + s[0].second * sc))
                            } else {
                                val p = Path()
                                p.moveTo(ox + s[0].first * sc, oy + s[0].second * sc)
                                for (i in 1 until s.size) p.lineTo(ox + s[i].first * sc, oy + s[i].second * sc)
                                drawPath(
                                    path = p,
                                    color = paint,
                                    style = Stroke(width = radius * 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        }
                    }
                }
            }
            if (busy) {
                GlassSurface {
                    MonoText(busyText, modifier = Modifier.padding(16.dp))
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Chip(tr(lang, "REGOLA", "НАСТР."), tool == 0) { tool = 0 }
            Chip(tr(lang, "FILTRI", "ФИЛЬТРЫ"), tool == 1) { tool = 1 }
            Chip(tr(lang, "RITAGLIO", "КАДР"), tool == 2) { tool = 2 }
            Chip(tr(lang, "GOMMA", "ЛАСТИК"), tool == 3) { tool = 3 }
            Chip(tr(lang, "IA", "ИИ"), tool == 4) { tool = 4 }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp)
                .verticalScroll(rememberScrollState())
                .padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when (tool) {
                0 -> {
                    SliderRow(tr(lang, "LUCE", "СВЕТ"), brightness) { brightness = it }
                    SliderRow(tr(lang, "CONTRASTO", "КОНТРАСТ"), contrast) { contrast = it }
                    SliderRow(tr(lang, "COLORE", "ЦВЕТ"), saturation) { saturation = it }
                    SliderRow(tr(lang, "CALORE", "ТЕПЛОТА"), warmth) { warmth = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(tr(lang, "APPLICA", "ПРИМЕНИТЬ"), true) { bake() }
                        Chip(tr(lang, "AZZERA", "СБРОС"), false) { resetAdjust() }
                    }
                }
                1 -> {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PRESETS.forEachIndexed { i, p ->
                            Chip(tr(lang, p.itName, p.ruName), preset == i) { preset = i }
                        }
                    }
                    Chip(tr(lang, "APPLICA", "ПРИМЕНИТЬ"), true) { bake() }
                }
                2 -> {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Chip(tr(lang, "RUOTA", "ПОВЕРНУТЬ"), false) { PhotoStore.base?.let { PhotoStore.set(rotate90(it)) } }
                        Chip(tr(lang, "SPECCHIO", "ЗЕРКАЛО"), false) { PhotoStore.base?.let { PhotoStore.set(flipH(it)) } }
                        Chip("1:1", false) { PhotoStore.base?.let { PhotoStore.set(cropToRatio(it, 1, 1)) } }
                        Chip("4:3", false) { PhotoStore.base?.let { PhotoStore.set(cropToRatio(it, 4, 3)) } }
                        Chip("3:4", false) { PhotoStore.base?.let { PhotoStore.set(cropToRatio(it, 3, 4)) } }
                        Chip("16:9", false) { PhotoStore.base?.let { PhotoStore.set(cropToRatio(it, 16, 9)) } }
                    }
                }
                3 -> {
                    MonoText(
                        tr(
                            lang,
                            "Disegna con il dito sopra ciò che vuoi togliere, poi premi CANCELLA. Va bene per oggetti piccoli; per quelli grandi usa IA.",
                            "Проведите пальцем по тому, что нужно убрать, затем нажмите СТЕРЕТЬ. Подходит для мелких объектов; для крупных используйте ИИ."
                        ),
                        size = 11,
                        color = JolieColors.Gray
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MonoText(tr(lang, "PENNELLO", "КИСТЬ"), size = 10, color = JolieColors.Gray, modifier = Modifier.width(84.dp))
                        Slider(
                            value = brush,
                            onValueChange = { brush = it },
                            valu
