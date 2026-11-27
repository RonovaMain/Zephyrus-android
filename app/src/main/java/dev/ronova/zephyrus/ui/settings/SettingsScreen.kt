package dev.ronova.zephyrus.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.material3.FilledTonalButton
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
import dev.ronova.zephyrus.AppLocale
import dev.ronova.zephyrus.BuildConfig
import dev.ronova.zephyrus.container
import dev.ronova.zephyrus.engine.UpdateScheduler
import dev.ronova.zephyrus.engine.YtDlpArgs
import dev.ronova.zephyrus.engine.formatDate
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
import androidx.compose.ui.res.stringResource
import dev.ronova.zephyrus.R

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
                title = { Text(stringResource(R.string.nav_settings), fontWeight = FontWeight.Bold) },
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
                SectionCard(title = stringResource(R.string.appearance)) {
                    val modes = listOf("system" to stringResource(R.string.theme_system), "light" to stringResource(R.string.theme_light), "dark" to stringResource(R.string.theme_dark))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        modes.forEachIndexed { i, (value, label) ->
                            SegmentedButton(
                                selected = s.themeMode == value,
                                onClick = { c.settings.update { copy(themeMode = value) } },
                                shape = SegmentedButtonDefaults.itemShape(index = i, count = modes.size),
                            ) { Text(label) }
                        }
                    }
                    var language by remember { mutableStateOf(AppLocale.current(context)) }
                    DropdownField(
                        label = stringResource(R.string.language),
                        options = listOf(stringResource(R.string.language_system) to "") +
                            AppLocale.SUPPORTED.map { it.nativeName to it.tag },
                        selected = language,
                        onSelect = { tag ->
                            language = tag
                            context.findActivity()?.let { AppLocale.set(it, tag) }
                        },
                    )
                    SwitchRow(
                        title = stringResource(R.string.dynamic_title),
                        description = if (dynamicAvailable) stringResource(R.string.dynamic_desc)
                        else stringResource(R.string.dynamic_unavailable),
                        checked = s.dynamicColor && dynamicAvailable,
                        onChecked = { v -> c.settings.update { copy(dynamicColor = v) } },
                        enabled = dynamicAvailable,
                    )
                    if (!(s.dynamicColor && dynamicAvailable)) {
                        Text(stringResource(R.string.accent), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            AccentNames.forEach { (key, nameRes) ->
                                AccentSwatch(
                                    key = key, name = stringResource(nameRes), selected = s.accent == key,
                                    onClick = { c.settings.update { copy(accent = key) } },
                                )
                            }
                        }
                    }
                }
            }

            // ---------------- загрузка ----------------
            item {
                SectionCard(title = stringResource(R.string.downloads_defaults)) {
                    DropdownField(
                        label = stringResource(R.string.video_container),
                        options = YtDlpArgs.VIDEO_CONTAINERS.map { it.uppercase() to it },
                        selected = s.videoContainer,
                        onSelect = { v -> c.settings.update { copy(videoContainer = v) } },
                    )
                    DropdownField(
                        label = stringResource(R.string.audio_format),
                        options = YtDlpArgs.AUDIO_FORMATS.map { it.uppercase() to it },
                        selected = s.audioFormat,
                        onSelect = { v -> c.settings.update { copy(audioFormat = v) } },
                    )
                    SwitchRow(stringResource(R.string.embed_thumb), s.embedThumbnail, { v -> c.settings.update { copy(embedThumbnail = v) } },
                        description = stringResource(R.string.embed_thumb_desc))
                    SwitchRow(stringResource(R.string.embed_meta), s.embedMetadata, { v -> c.settings.update { copy(embedMetadata = v) } },
                        description = stringResource(R.string.embed_meta_desc))
                    SwitchRow(stringResource(R.string.embed_subs), s.embedSubs, { v -> c.settings.update { copy(embedSubs = v) } },
                        description = stringResource(R.string.embed_subs_desc))
                    SettingTextField(
                        label = stringResource(R.string.subs_langs), value = s.subsLangs, placeholder = "ru,en",
                        enabled = s.embedSubs, resetKey = resetKey,
                    ) { v -> c.settings.update { copy(subsLangs = v) } }
                    SwitchRow(stringResource(R.string.sponsorblock), s.sponsorblock, { v -> c.settings.update { copy(sponsorblock = v) } },
                        description = stringResource(R.string.sponsorblock_desc))
                    SettingTextField(
                        label = stringResource(R.string.filename_template), value = s.filenameTemplate, placeholder = YtDlpArgs.DEFAULT_TEMPLATE,
                        resetKey = resetKey,
                    ) { v -> c.settings.update { copy(filenameTemplate = v) } }
                }
            }

            // ---------------- сеть ----------------
            item {
                SectionCard(title = stringResource(R.string.network)) {
                    SettingTextField(
                        label = stringResource(R.string.rate_limit), value = s.rateLimit, placeholder = stringResource(R.string.rate_limit_hint),
                        resetKey = resetKey,
                    ) { v -> c.settings.update { copy(rateLimit = v) } }
                    Column {
                        Text(stringResource(R.string.fragments, s.fragments), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.fragments_desc),
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
                        label = stringResource(R.string.proxy), value = s.proxy, placeholder = stringResource(R.string.proxy_hint),
                        resetKey = resetKey,
                    ) { v -> c.settings.update { copy(proxy = v) } }
                }
            }

            // ---------------- уведомления и папка ----------------
            item {
                SectionCard(title = stringResource(R.string.files_notifications)) {
                    SwitchRow(stringResource(R.string.notifications), s.notifications, { v -> c.settings.update { copy(notifications = v) } },
                        description = stringResource(R.string.notifications_desc))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.download_folder), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.download_folder_desc),
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
                        }) { Text(stringResource(R.string.open)) }
                    }
                }
            }

            // ---------------- движок ----------------
            item {
                SectionCard(title = stringResource(R.string.engine)) {
                    Row(Modifier.fillMaxWidth()) {
                        StatTile("yt-dlp", engine.version, Modifier.weight(1f))
                        StatTile("FFmpeg", stringResource(R.string.ffmpeg_builtin), Modifier.weight(1f))
                    }
                    SwitchRow(
                        title = stringResource(R.string.auto_update),
                        checked = s.autoUpdate,
                        onChecked = { v ->
                            c.settings.update { copy(autoUpdate = v) }
                            UpdateScheduler.sync(context, v)
                        },
                        description = stringResource(R.string.auto_update_desc),
                    )
                    Text(
                        stringResource(
                            R.string.last_check,
                            if (engine.lastCheck > 0) formatDate(engine.lastCheck) else stringResource(R.string.never_checked),
                        ),
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    )
                    GradientButton(
                        text = stringResource(R.string.update_engine),
                        onClick = { c.engine.update() },
                        enabled = engine.ready && !engine.updating && dl.idle,
                        icon = ZIcons.Download,
                    )
                    if (engine.updating) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (engine.updateMessageRes != 0) {
                        val detail = if (engine.updateOk == false && engine.updateDetail.isNotEmpty()) "\n" + engine.updateDetail else ""
                        Text(
                            stringResource(engine.updateMessageRes) + detail,
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
                            stringResource(R.string.update_after_downloads),
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        )
                    }
                    Text(
                        stringResource(R.string.engine_note),
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    )
                }
            }

            // ---------------- о программе ----------------
            item {
                SectionCard(title = stringResource(R.string.about)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ZephyrusLogo(72.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Pill(stringResource(R.string.version_fmt, BuildConfig.VERSION_NAME))
                        }
                    }
                    Text(
                        stringResource(R.string.about_desc),
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.author), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        Pill(AppInfo.AUTHOR)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { openUrl(AppInfo.GITHUB_URL) }) { Text("GitHub  ↗") }
                        FilledTonalButton(
                            onClick = { openUrl(AppInfo.BOOSTY_URL) },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.support_project) + "  ↗", maxLines = 1) }
                    }
                    Text(
                        stringResource(R.string.license_note, AppInfo.LICENSE),
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    )
                    TextButton(onClick = { confirmReset = true }) { Text(stringResource(R.string.reset_settings), color = cs.error) }
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.reset_settings)) },
            text = { Text(stringResource(R.string.reset_text)) },
            confirmButton = {
                TextButton(onClick = {
                    c.settings.reset()
                    resetKey++
                    confirmReset = false
                }) { Text(stringResource(R.string.reset)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } },
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
