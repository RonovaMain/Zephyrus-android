package dev.ronova.zephyrus.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ronova.zephyrus.AppInfo
import dev.ronova.zephyrus.BuildConfig
import dev.ronova.zephyrus.container
import dev.ronova.zephyrus.engine.YtDlpArgs
import dev.ronova.zephyrus.ui.components.DropdownField
import dev.ronova.zephyrus.ui.components.GradientButton
import dev.ronova.zephyrus.ui.components.Pill
import dev.ronova.zephyrus.ui.components.SectionCard
import dev.ronova.zephyrus.ui.components.StatTile
import dev.ronova.zephyrus.ui.components.SwitchRow
import dev.ronova.zephyrus.ui.components.ZIcons
import dev.ronova.zephyrus.ui.components.ZephyrusLogo
import dev.ronova.zephyrus.ui.theme.AccentNames
import dev.ronova.zephyrus.ui.theme.brandScheme
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val c = context.container
    val cs = MaterialTheme.colorScheme
    val s by c.settings.state.collectAsStateWithLifecycle()
    val engine by c.engine.state.collectAsStateWithLifecycle()
    val dl by c.downloads.state.collectAsStateWithLifecycle()
    var resetKey by remember { mutableIntStateOf(0) }
    var confirmReset by remember { mutableStateOf(false) }
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun openUrl(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            // нет браузера
        }
    }

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = { Text("Настройки", fontWeight = FontWeight.Bold) },
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
            // ---------------- оформление ----------------
            item {
                SectionCard(title = "Оформление") {
                    val modes = listOf("system" to "Системная", "light" to "Светлая", "dark" to "Тёмная")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        modes.forEachIndexed { i, (value, label) ->
                            SegmentedButton(
                                selected = s.themeMode == value,
                                onClick = { c.settings.update { copy(themeMode = value) } },
                                shape = SegmentedButtonDefaults.itemShape(index = i, count = modes.size),
                            ) { Text(label) }
                        }
                    }
                    SwitchRow(
                        title = "Динамические цвета (Material You)",
                        description = if (dynamicAvailable) "Цвета подстраиваются под обои телефона"
                        else "Доступно на Android 12 и новее",
                        checked = s.dynamicColor && dynamicAvailable,
                        onChecked = { v -> c.settings.update { copy(dynamicColor = v) } },
                        enabled = dynamicAvailable,
                    )
                    if (!(s.dynamicColor && dynamicAvailable)) {
                        Text("Акцентный оттенок", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            AccentNames.forEach { (key, name) ->
                                AccentSwatch(
                                    key = key, name = name, selected = s.accent == key,
                                    onClick = { c.settings.update { copy(accent = key) } },
                                )
                            }
                        }
                    }
                }
            }

            // ---------------- загрузка ----------------
            item {
                SectionCard(title = "Загрузка по умолчанию") {
                    DropdownField(
                        label = "Контейнер видео",
                        options = YtDlpArgs.VIDEO_CONTAINERS.map { it.uppercase() to it },
                        selected = s.videoContainer,
                        onSelect = { v -> c.settings.update { copy(videoContainer = v) } },
                    )
                    DropdownField(
                        label = "Формат аудио",
                        options = YtDlpArgs.AUDIO_FORMATS.map { it.uppercase() to it },
                        selected = s.audioFormat,
                        onSelect = { v -> c.settings.update { copy(audioFormat = v) } },
                    )
                    SwitchRow("Встраивать обложку", s.embedThumbnail, { v -> c.settings.update { copy(embedThumbnail = v) } },
                        description = "Картинка видео или трека внутри файла")
                    SwitchRow("Записывать метаданные", s.embedMetadata, { v -> c.settings.update { copy(embedMetadata = v) } },
                        description = "Название, автор, описание, главы")
                    SwitchRow("Встраивать субтитры", s.embedSubs, { v -> c.settings.update { copy(embedSubs = v) } },
                        description = "Только для видео")
                    SettingTextField(
                        label = "Языки субтитров", value = s.subsLangs, placeholder = "ru,en",
                        enabled = s.embedSubs, resetKey = resetKey,
                    ) { v -> c.settings.update { copy(subsLangs = v) } }
                    SwitchRow("SponsorBlock", s.sponsorblock, { v -> c.settings.update { copy(sponsorblock = v) } },
                        description = "Вырезать рекламу и самопромо в YouTube-видео")
                    SettingTextField(
                        label = "Шаблон имени файла", value = s.filenameTemplate, placeholder = YtDlpArgs.DEFAULT_TEMPLATE,
                        resetKey = resetKey,
                    ) { v -> c.settings.update { copy(filenameTemplate = v) } }
                }
            }

            // ---------------- сеть ----------------
            item {
                SectionCard(title = "Сеть") {
                    SettingTextField(
                        label = "Ограничение скорости", value = s.rateLimit, placeholder = "без ограничений, например 5M",
                        resetKey = resetKey,
                    ) { v -> c.settings.update { copy(rateLimit = v) } }
                    Column {
                        Text("Потоков фрагментов: ${s.fragments}", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Ускоряет загрузку HLS и DASH (1–16)",
                            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                        )
                        Slider(
                            value = s.fragments.toFloat(),
                            onValueChange = { v -> c.settings.update { copy(fragments = v.roundToInt()) } },
                            valueRange = 1f..16f,
                            steps = 14,
                        )
                    }
                    SettingTextField(
                        label = "Прокси", value = s.proxy, placeholder = "http://host:port или socks5://…",
                        resetKey = resetKey,
                    ) { v -> c.settings.update { copy(proxy = v) } }
                }
            }

            // ---------------- уведомления и папка ----------------
            item {
                SectionCard(title = "Файлы и уведомления") {
                    SwitchRow("Уведомления", s.notifications, { v -> c.settings.update { copy(notifications = v) } },
                        description = "Сообщение, когда загрузка завершена")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Папка загрузок", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Загрузки/Zephyrus во внутренней памяти",
                                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(onClick = {
                            try {
                                context.startActivity(
                                    Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (e: Exception) {
                                // нет приложения «Загрузки»
                            }
                        }) { Text("Открыть") }
                    }
                }
            }

            // ---------------- движок ----------------
            item {
                SectionCard(title = "Движок") {
                    Row(Modifier.fillMaxWidth()) {
                        StatTile("yt-dlp", engine.version, Modifier.weight(1f))
                        StatTile("FFmpeg", "встроен в приложение", Modifier.weight(1f))
                    }
                    GradientButton(
                        text = "Обновить движок",
                        onClick = { c.engine.update() },
                        enabled = engine.ready && !engine.updating && dl.idle,
                        icon = ZIcons.Download,
                    )
                    if (engine.updating) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (engine.updateMessage.isNotEmpty()) {
                        Text(
                            engine.updateMessage,
                            color = when (engine.updateOk) {
                                true -> cs.primary
                                false -> cs.error
                                null -> cs.onSurfaceVariant
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (!dl.idle) {
                        Text(
                            "Обновление станет доступно, когда закончатся загрузки.",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        )
                    }
                    Text(
                        "yt-dlp обновляется кнопкой выше. FFmpeg входит в состав библиотеки и обновляется вместе с новой версией приложения.",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    )
                }
            }

            // ---------------- о программе ----------------
            item {
                SectionCard(title = "О программе") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ZephyrusLogo(72.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Zephyrus", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Pill("Версия ${BuildConfig.VERSION_NAME}")
                        }
                    }
                    Text(
                        "Медиа-загрузчик на базе yt-dlp и FFmpeg.",
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Автор", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        Pill(AppInfo.AUTHOR)
                        Box(Modifier.weight(1f))
                        OutlinedButton(onClick = { openUrl(AppInfo.GITHUB_URL) }) { Text("GitHub  ↗") }
                    }
                    Text(
                        "Лицензия ${AppInfo.LICENSE}. Используйте программу только для контента, на скачивание которого у вас есть право.",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    )
                    TextButton(onClick = { confirmReset = true }) { Text("Сбросить настройки", color = cs.error) }
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Сброс настроек") },
            text = { Text("Вернуть все настройки к значениям по умолчанию? История и профили cookies не затрагиваются.") },
            confirmButton = {
                TextButton(onClick = {
                    c.settings.reset()
                    resetKey++
                    confirmReset = false
                }) { Text("Сбросить") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun SettingTextField(
    label: String,
    value: String,
    placeholder: String,
    resetKey: Int,
    enabled: Boolean = true,
    onChange: (String) -> Unit,
) {
    // локальное состояние нужно, чтобы курсор не прыгал; после сброса настроек поле перечитывается
    var text by remember(resetKey) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(it)
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
    )
}

@Composable
private fun AccentSwatch(key: String, name: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = brandScheme(key, false)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(scheme.primaryContainer, scheme.tertiary)))
                .then(
                    if (selected) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), CircleShape) else Modifier
                )
                .clickable(onClick = onClick)
        )
        Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
