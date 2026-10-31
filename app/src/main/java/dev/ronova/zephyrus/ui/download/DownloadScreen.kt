package dev.ronova.zephyrus.ui.download

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ronova.zephyrus.container
import dev.ronova.zephyrus.data.siteLabel
import dev.ronova.zephyrus.engine.DlJob
import dev.ronova.zephyrus.engine.DlState
import dev.ronova.zephyrus.engine.FileStore
import dev.ronova.zephyrus.engine.InfoSummary
import dev.ronova.zephyrus.engine.YtDlpArgs
import dev.ronova.zephyrus.engine.extractUrl
import dev.ronova.zephyrus.engine.formatDuration
import dev.ronova.zephyrus.engine.formatEta
import dev.ronova.zephyrus.engine.formatSize
import dev.ronova.zephyrus.engine.formatSpeed
import dev.ronova.zephyrus.engine.looksLikeUrl
import dev.ronova.zephyrus.ui.components.DropdownField
import dev.ronova.zephyrus.ui.components.GradientButton
import dev.ronova.zephyrus.ui.components.ImageLoader
import dev.ronova.zephyrus.ui.components.SectionCard
import dev.ronova.zephyrus.ui.components.StatTile
import dev.ronova.zephyrus.ui.components.SwitchRow
import dev.ronova.zephyrus.ui.components.ZIcons
import dev.ronova.zephyrus.ui.components.ZephyrusLogo
import kotlinx.coroutines.delay

private sealed interface InfoUi {
    data object Idle : InfoUi
    data object Loading : InfoUi
    data class Ready(val info: InfoSummary) : InfoUi
    data class Failed(val message: String) : InfoUi
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen() {
    val context = LocalContext.current
    val c = context.container
    val cs = MaterialTheme.colorScheme
    val settings by c.settings.state.collectAsStateWithLifecycle()
    val dl by c.downloads.state.collectAsStateWithLifecycle()
    val engine by c.engine.state.collectAsStateWithLifecycle()
    val profiles by c.db.profiles.collectAsStateWithLifecycle()
    val shared by c.sharedUrl.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    var url by rememberSaveable { mutableStateOf("") }
    var info by remember { mutableStateOf<InfoUi>(InfoUi.Idle) }
    var manual by rememberSaveable { mutableStateOf(false) }
    var videoSel by rememberSaveable { mutableStateOf("") }
    var audioSel by rememberSaveable { mutableStateOf("") }
    var session by rememberSaveable { mutableStateOf("auto") }
    var error by remember { mutableStateOf<String?>(null) }
    var showLog by remember { mutableStateOf(false) }

    // ссылка из «Поделиться»
    LaunchedEffect(shared) {
        val s = shared
        if (s != null) {
            url = s
            c.sharedUrl.value = null
        }
    }

    // анализ ссылки с задержкой, чтобы не запускать yt-dlp на каждую букву
    LaunchedEffect(url, settings.playlist, session, engine.ready) {
        error = null
        val u = url.trim()
        if (!looksLikeUrl(u) || !engine.ready) {
            info = InfoUi.Idle
            return@LaunchedEffect
        }
        info = InfoUi.Loading
        delay(700)
        val result = c.downloads.fetchInfo(u, settings.playlist, session)
        info = result.fold(
            onSuccess = { InfoUi.Ready(it) },
            onFailure = { InfoUi.Failed(it.message ?: "Не удалось получить информацию о ссылке") },
        )
    }

    val ready = info as? InfoUi.Ready
    val videos = ready?.info?.videos.orEmpty()
    val audios = ready?.info?.audios.orEmpty()
    val audioMode = settings.formatMode == "audio"
    val manualAvailable = if (audioMode) audios.isNotEmpty() else videos.isNotEmpty()
    val manualOn = manual && manualAvailable
    val muxedOnly = videos.isNotEmpty() && videos.first().muxed
    val showAudioPick = audioMode || (!muxedOnly && audios.isNotEmpty())

    LaunchedEffect(ready?.info) {
        videoSel = videos.firstOrNull()?.id.orEmpty()
        audioSel = ""
    }

    fun start() {
        val u = url.trim()
        if (!looksLikeUrl(u)) {
            error = "Вставьте корректную ссылку, начинающуюся с http:// или https://"
            return
        }
        var quality = if (audioMode) settings.audioQuality else settings.videoQuality
        var label = ""
        if (manualOn) {
            val (spec, text) = YtDlpArgs.manualSpec(
                audioMode, settings.videoContainer,
                videos.firstOrNull { it.id == videoSel }, audios.firstOrNull { it.id == audioSel }, showAudioPick,
            )
            quality = "fmt:$spec"
            label = text
        }
        c.downloads.enqueue(
            DlJob(
                url = u, mode = if (audioMode) "audio" else "video", quality = quality,
                playlist = settings.playlist, session = session, label = label,
            )
        )
        url = ""
        info = InfoUi.Idle
    }

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ZephyrusLogo(36.dp)
                        Text("Загрузка", fontWeight = FontWeight.Bold)
                    }
                },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = Color.Transparent, scrolledContainerColor = cs.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // состояние движка
            if (engine.error != null) {
                item {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = cs.errorContainer, contentColor = cs.onErrorContainer),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Движок не запустился", fontWeight = FontWeight.SemiBold)
                            Text(engine.error.orEmpty(), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else if (!engine.ready) {
                item {
                    SectionCard {
                        Text("Движок запускается…", style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }

            // ввод и параметры
            item {
                SectionCard {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Ссылка") },
                        placeholder = { Text("Видео, плейлист или трек") },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        trailingIcon = {
                            if (url.isEmpty()) {
                                IconButton(onClick = {
                                    val text = clipboard.getText()?.text.orEmpty()
                                    if (text.isNotBlank()) url = extractUrl(text) ?: text.trim()
                                }) { Icon(ZIcons.Paste, contentDescription = "Вставить") }
                            } else {
                                IconButton(onClick = { url = "" }) { Icon(Icons.Filled.Close, contentDescription = "Очистить") }
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { start() }),
                        isError = error != null,
                    )
                    val err = error
                    if (err != null) Text(err, color = cs.error, style = MaterialTheme.typography.bodySmall)

                    AnimatedVisibility(visible = info !is InfoUi.Idle) { PreviewCard(info) }

                    // видео или аудио
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        val modes = listOf(
                            "video" to "Видео ${settings.videoContainer.uppercase()}",
                            "audio" to "Аудио ${settings.audioFormat.uppercase()}",
                        )
                        modes.forEachIndexed { i, (mode, label) ->
                            SegmentedButton(
                                selected = settings.formatMode == mode,
                                onClick = { c.settings.update { copy(formatMode = mode) } },
                                shape = SegmentedButtonDefaults.itemShape(index = i, count = modes.size),
                            ) { Text(label) }
                        }
                    }

                    // лучшее доступное или ручной выбор
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !manualOn,
                            onClick = { manual = false },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        ) { Text("Лучшее доступное") }
                        SegmentedButton(
                            selected = manualOn,
                            onClick = { manual = true },
                            enabled = manualAvailable,
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        ) { Text("Выбрать вручную") }
                    }
                    if (!manualAvailable) {
                        Text(
                            "Ручной выбор видео и звука появится после анализа ссылки.",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        )
                    }

                    if (!manualOn) {
                        DropdownField(
                            label = "Качество (максимум)",
                            options = if (audioMode) YtDlpArgs.AUDIO_QUALITIES else YtDlpArgs.VIDEO_QUALITIES,
                            selected = if (audioMode) settings.audioQuality else settings.videoQuality,
                            onSelect = { v ->
                                c.settings.update { if (audioMode) copy(audioQuality = v) else copy(videoQuality = v) }
                            },
                        )
                    } else {
                        if (!audioMode) {
                            DropdownField(
                                label = "Качество видео",
                                options = videos.map { it.label to it.id },
                                selected = videoSel,
                                onSelect = { videoSel = it },
                            )
                        }
                        if (showAudioPick) {
                            DropdownField(
                                label = "Качество звука",
                                options = listOf("Лучший звук (авто)" to "") + audios.map { it.label to it.id },
                                selected = audioSel,
                                onSelect = { audioSel = it },
                            )
                        }
                        Text(
                            when {
                                audioMode -> "Выбранная дорожка будет скачана и конвертирована в ${settings.audioFormat.uppercase()} через FFmpeg."
                                muxedOnly -> "Для этого сайта доступны только готовые форматы — звук уже внутри видео."
                                else -> "Видео и звук скачиваются отдельно и склеиваются FFmpeg в один файл ${settings.videoContainer.uppercase()}. " +
                                    "Если лучшее качество плохое — выберите строку ниже."
                            },
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        )
                    }

                    DropdownField(
                        label = "Сессия (cookies)",
                        options = listOf("Авто — по сайту ссылки" to "auto", "Без cookies" to "none") +
                            profiles.map { "${siteLabel(it.site)} · ${it.name}" to it.id },
                        selected = session,
                        onSelect = { session = it },
                    )
                    SwitchRow(
                        title = "Плейлист целиком",
                        description = "Скачать все видео из списка",
                        checked = settings.playlist,
                        onChecked = { v -> c.settings.update { copy(playlist = v) } },
                    )
                    GradientButton(
                        text = "Скачать",
                        onClick = { start() },
                        enabled = engine.ready,
                        icon = ZIcons.Download,
                    )
                }
            }

            // прогресс
            item {
                AnimatedVisibility(visible = dl.current != null || dl.finished) {
                    ProgressCard(dl, showLog, onToggleLog = { showLog = !showLog })
                }
            }

            // очередь
            if (dl.queue.isNotEmpty()) {
                item {
                    SectionCard(title = "В очереди: ${dl.queue.size}") {
                        dl.queue.forEach { job ->
                            Text(
                                (if (job.mode == "audio") "Аудио · " else "Видео · ") + job.url,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        TextButton(onClick = { c.downloads.clearQueue() }) { Text("Очистить очередь") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(info: InfoUi) {
    val cs = MaterialTheme.colorScheme
    when (info) {
        InfoUi.Idle -> Unit
        InfoUi.Loading -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Получаем информацию о ссылке…", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        is InfoUi.Failed -> Text(info.message, color = cs.error, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        is InfoUi.Ready -> {
            val data = info.info
            val bitmap by produceState<Bitmap?>(initialValue = null, data.thumbnail) {
                value = if (data.thumbnail.isNotEmpty()) ImageLoader.load(data.thumbnail) else null
            }
            Surface(shape = RoundedCornerShape(22.dp), color = cs.surfaceContainerHigh) {
                Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.width(132.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(cs.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        val bmp = bitmap
                        if (bmp != null) {
                            Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            ZephyrusLogo(36.dp)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(data.title, maxLines = 3, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                        val meta = buildList {
                            if (data.uploader.isNotEmpty()) add(data.uploader)
                            if (data.isPlaylist) add("плейлист · ${data.count} видео")
                            else formatDuration(data.durationSec).takeIf { it.isNotEmpty() }?.let { add(it) }
                        }.joinToString("  •  ")
                        if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressCard(dl: DlState, showLog: Boolean, onToggleLog: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val cs = MaterialTheme.colorScheme
    val p = dl.progress
    val animated by animateFloatAsState(targetValue = p.percent / 100f, label = "progress")

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (dl.finished && dl.current == null) (if (dl.ok) "Готово" else "Ошибка") else p.status,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (p.itemCount > 1) {
                Text("Элемент ${p.item} из ${p.itemCount}", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
        }
        if (dl.current != null && p.indeterminate) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)), strokeCap = StrokeCap.Round)
        } else {
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                strokeCap = StrokeCap.Round,
            )
        }
        Row(Modifier.fillMaxWidth()) {
            StatTile("Прогресс", if (dl.current == null && !dl.ok) "—" else "%.1f%%".format(p.percent), Modifier.weight(1f))
            StatTile("Скорость", formatSpeed(p.speed), Modifier.weight(1f))
            StatTile("Осталось", formatEta(p.eta), Modifier.weight(1f))
            StatTile("Размер", formatSize(p.size), Modifier.weight(1f))
        }
        if (dl.finished && dl.current == null && dl.message.isNotEmpty()) {
            Text(dl.message, color = if (dl.ok) cs.primary else cs.error, style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onToggleLog) { Text(if (showLog) "Скрыть журнал" else "Журнал") }
            Box(Modifier.weight(1f))
            if (dl.current != null) {
                OutlinedButton(onClick = { c.downloads.cancelCurrent() }) { Text("Отмена") }
            } else if (dl.ok && dl.lastFile != null) {
                FilledTonalButton(onClick = {
                    try {
                        context.startActivity(FileStore.openIntent(context, dl.lastFile))
                    } catch (e: Exception) {
                        // нет приложения для этого типа файла
                    }
                }) { Text("Открыть") }
            }
        }
        AnimatedVisibility(visible = showLog) {
            Surface(shape = RoundedCornerShape(16.dp), color = cs.surfaceContainerHighest) {
                Column(Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState()).padding(12.dp)) {
                    dl.log.takeLast(60).forEach {
                        Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = cs.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
