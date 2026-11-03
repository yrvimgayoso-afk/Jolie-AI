package com.jolie.assistant

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlin.math.max
import androidx.compose.ui.graphics.ColorMatrix as ComposeColorMatrix

private fun friendlyImageError(lang: String, e: Throwable): String {
    if (e is GeminiHttpException) {
        val hint = when (e.code) {
            429, 403 -> tr(
                lang,
                "L’IA online per le immagini non è disponibile nel piano gratuito (o Gemini non è raggiungibile: in Russia serve la VPN). Gli stili del menu STILI funzionano comunque sul telefono.",
                "ИИ для изображений онлайн недоступен в бесплатном плане (или Gemini недоступен: в России нужен VPN). Стили в меню СТИЛИ работают на телефоне."
            )
            404 -> tr(lang, "Modello immagini non trovato: cambia il nome del modello nel menu IA.", "Модель изображений не найдена: измените название модели в меню ИИ.")
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
    val ui = remember {
        PhotoUi().also { it.aiModel = photoPrefs.getString("image_model", IMAGE_MODELS[0]) ?: IMAGE_MODELS[0] }
    }
    var busy by remember { mutableStateOf(false) }
    var busyText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var camUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val base = PhotoStore.base
    val matrix = buildMatrix(PRESETS[ui.preset].matrix, ui.brightness, ui.contrast, ui.saturation, ui.warmth)

    fun bake() {
        val b = PhotoStore.base ?: return
        PhotoStore.set(applyMatrix(b, matrix))
        ui.resetAdjust()
    }

    fun loadPhoto(uri: Uri) {
        busy = true
        busyText = tr(lang, "Carico la foto…", "Загружаю фото…")
        runAsync {
            val bmp = loadBitmap(context, uri)
            onMain {
                busy = false
                if (bmp != null) {
                    PhotoStore.load(bmp)
                    ui.strokes.clear()
                    ui.resetAdjust()
                    status = ""
                } else {
                    status = tr(lang, "Impossibile aprire questa foto.", "Не удалось открыть это фото.")
                }
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) loadPhoto(uri)
    }

    val camLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = camUri
        camUri = null
        if (u != null) {
            if (ok) {
                loadPhoto(u)
            } else {
                try {
                    context.contentResolver.delete(u, null, null)
                } catch (_: Exception) {
                }
            }
        }
    }

    fun launchCamera() {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Jolie_cam_" + System.currentTimeMillis() + ".jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Jolie")
        }
        val uri = try {
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        } catch (_: Exception) {
            null
        }
        if (uri == null) {
            status = tr(lang, "Impossibile preparare la fotocamera.", "Не удалось подготовить камеру.")
            return
        }
        camUri = uri
        try {
            camLauncher.launch(uri)
        } catch (_: Exception) {
            camUri = null
            status = tr(lang, "Impossibile aprire la fotocamera.", "Не удалось открыть камеру.")
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            launchCamera()
        } else {
            status = tr(lang, "Serve il permesso della fotocamera (Impostazioni del telefono).", "Нужно разрешение на камеру (настройки телефона).")
        }
    }

    fun startCamera() {
        val ok = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (ok) launchCamera() else permLauncher.launch(Manifest.permission.CAMERA)
    }

    fun applyErase() {
        val b = PhotoStore.base ?: return
        if (ui.strokes.isEmpty() || busy) return
        busy = true
        busyText = tr(lang, "Cancello l’oggetto…", "Удаляю объект…")
        val snapshot = ui.strokes.toList()
        val radius = (0.01f + ui.brush * 0.07f) * max(b.width, b.height)
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
                    ui.strokes.clear()
                    status = ""
                } else {
                    status = tr(lang, "Operazione non riuscita.", "Не удалось выполнить.")
                }
            }
        }
    }

    fun runAiPrompt(prompt: String) {
        val b = PhotoStore.base ?: return
        val key = AiPrefs(context).geminiKey
        if (key.isBlank()) {
            status = tr(lang, "Prima imposta la chiave nella scheda CHAT (tasto CHIAVE).", "Сначала задайте ключ на вкладке ЧАТ (кнопка КЛЮЧ).")
            return
        }
        if (prompt.isBlank() || busy) return
        photoPrefs.edit().putString("image_model", ui.aiModel.trim()).apply()
        busy = true
        busyText = tr(lang, "Jolie sta lavorando… può volerci un minuto.", "Джоли работает… это может занять минуту.")
        status = ""
        editImageWithGemini(key, ui.aiModel, b, prompt) { r ->
            busy = false
            r.onSuccess {
                PhotoStore.set(it)
                ui.strokes.clear()
                status = tr(lang, "Fatto.", "Готово.")
            }.onFailure {
                status = friendlyImageError(lang, it)
            }
        }
    }

    fun runStyle(s: StyleDef) {
        val b = PhotoStore.base
        if (b == null) {
            status = tr(lang, "Prima scegli o scatta una foto.", "Сначала выберите или снимите фото.")
            return
        }
        if (busy) return
        busy = true
        busyText = tr(lang, "Applico lo stile…", "Применяю стиль…")
        status = ""
        runAsync {
            val local = try {
                applyLocalStyle(b, s.id)
            } catch (_: Throwable) {
                null
            }
            onMain {
                busy = false
                if (local != null) {
                    PhotoStore.set(local)
                    ui.strokes.clear()
                } else {
                    runAiPrompt(s.aiPrompt)
                }
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
            SmallButton(tr(lang, "SCEGLI", "ВЫБРАТЬ"), { picker.launch(arrayOf("image/*")) }, Modifier.weight(1f))
            SmallButton(tr(lang, "SCATTA", "СНЯТЬ"), { startCamera() }, Modifier.weight(1f))
            SmallButton(tr(lang, "ANNULLA", "ОТМЕНА"), {
                status = if (PhotoStore.undoLast()) "" else tr(lang, "Niente da annullare.", "Нечего отменять.")
            }, Modifier.weight(1f))
            SmallButton(tr(lang, "SALVA", "СОХРАНИТЬ"), { saveImage() }, Modifier.weight(1f), primary = true)
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (base == null) {
                GlassSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MonoText(tr(lang, "FOTO STUDIO", "ФОТО СТУДИЯ"), size = 11, color = JolieColors.RedSoft, spacing = 1.5f)
                        MonoText(
                            tr(
                                lang,
                                "Scatta o scegli una foto per regolarla, applicare filtri e stili, ritagliarla, cancellare oggetti o modificarla con l’IA.",
                                "Снимите или выберите фото, чтобы настроить его, применить фильтры и стили, обрезать, стереть объекты или изменить с помощью ИИ."
                            )
                        )
                        GlassButton(
                            tr(lang, "SCATTA UNA FOTO", "СДЕЛАТЬ ФОТО"),
                            onClick = { startCamera() },
                            modifier = Modifier.fillMaxWidth(),
                            filled = true
                        )
                        GlassButton(
                            tr(lang, "SCEGLI DALLA GALLERIA", "ВЫБРАТЬ ИЗ ГАЛЕРЕИ"),
                            onClick = { picker.launch(arrayOf("image/*")) },
                            modifier = Modifier.fillMaxWidth()
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
                if (ui.tool == 3) {
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(base) {
                                val sc = minOf(size.width.toFloat() / base.width, size.height.toFloat() / base.height)
                                val ox = (size.width - base.width * sc) / 2f
                                val oy = (size.height - base.height * sc) / 2f
                                detectDragGestures(
                                    onDragStart = { o -> ui.current = listOf(Pair((o.x - ox) / sc, (o.y - oy) / sc)) },
                                    onDragEnd = {
                                        if (ui.current.isNotEmpty()) ui.strokes.add(ui.current)
                                        ui.current = emptyList()
                                    },
                                    onDragCancel = { ui.current = emptyList() },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        ui.current = ui.current + Pair((change.position.x - ox) / sc, (change.position.y - oy) / sc)
                                    }
                                )
                            }
                    ) {
                        val sc = minOf(size.width / base.width, size.height / base.height)
                        val ox = (size.width - base.width * sc) / 2f
                        val oy = (size.height - base.height * sc) / 2f
                        val radius = (0.01f + ui.brush * 0.07f) * max(base.width, base.height) * sc
                        val paint = JolieColors.Red.copy(alpha = 0.55f)
                        val all = ui.strokes.toList() + listOf(ui.current)
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

        TabStrip(
            labels = listOf(
                tr(lang, "REGOLA", "НАСТР."),
                tr(lang, "FILTRI", "ФИЛЬТРЫ"),
                tr(lang, "RITAGLIO", "КАДР"),
                tr(lang, "GOMMA", "ЛАСТИК"),
                tr(lang, "STILI", "СТИЛИ"),
                tr(lang, "IA", "ИИ")
            ),
            selected = ui.tool,
            onSelect = { ui.tool = it },
            modifier = Modifier.padding(top = 4.dp)
        )

        ToolPanel(
            ui = ui,
            lang = lang,
            busy = busy,
            onBake = { bake() },
            onErase = { applyErase() },
            onAi = { runAiPrompt(ui.aiPrompt) },
            onStyle = { runStyle(it) },
            onVideo = { openYoutube(context) }
        )
        if (status.isNotEmpty()) MonoText(status, size = 11, color = JolieColors.RedSoft, modifier = Modifier.padding(top = 4.dp))
    }
}
