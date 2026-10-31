package dev.ronova.zephyrus.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ronova.zephyrus.container
import dev.ronova.zephyrus.data.HistoryItem
import dev.ronova.zephyrus.engine.DlJob
import dev.ronova.zephyrus.engine.FileStore
import dev.ronova.zephyrus.engine.formatDate
import dev.ronova.zephyrus.ui.components.Pill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onGoDownload: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val cs = MaterialTheme.colorScheme
    val history by c.db.history.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }

    val filtered = remember(history, query) {
        if (query.isBlank()) history
        else history.filter { it.title.contains(query, ignoreCase = true) || it.url.contains(query, ignoreCase = true) }
    }
    // проверяем, на месте ли файлы (пользователь мог удалить их в «Файлах»)
    val presence by produceState(initialValue = emptyMap<Long, Boolean>(), history, tick) {
        value = withContext(Dispatchers.IO) { history.associate { it.id to FileStore.exists(context, it.path) } }
    }
    val missing = filtered.count { presence[it.id] == false }

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = { Text("История", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { confirmClear = true }, enabled = history.isNotEmpty()) {
                        Icon(Icons.Filled.Delete, contentDescription = "Очистить историю")
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Всё, что вы скачали. Если файл удалён — запись бледнеет, и его можно скачать заново.",
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                        singleLine = true, shape = RoundedCornerShape(20.dp),
                        placeholder = { Text("Поиск по названию или ссылке") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Очистить") }
                        },
                    )
                    Text(
                        "Записей: ${filtered.size}" + if (missing > 0) " · файл удалён: $missing" else "",
                        style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                    )
                }
            }
            if (history.isEmpty()) {
                item {
                    Text(
                        "История пуста — скачайте что-нибудь, и оно появится здесь.",
                        style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 32.dp),
                    )
                }
            }
            items(filtered, key = { it.id }) { item ->
                val exists = presence[item.id] ?: true
                HistoryCard(
                    item = item, exists = exists,
                    onOpen = {
                        try {
                            context.startActivity(FileStore.openIntent(context, item.path))
                        } catch (e: Exception) {
                            // нет приложения для этого типа файла
                        }
                    },
                    onRedo = {
                        c.downloads.enqueue(
                            DlJob(
                                url = item.url, mode = item.mode, quality = item.quality, historyId = item.id,
                                label = if (item.quality.startsWith("fmt:")) item.format else "",
                            )
                        )
                        onGoDownload()
                    },
                    onDelete = { c.db.deleteHistory(item.id) },
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Очистить историю") },
            text = { Text("Удалить все записи истории? Сами файлы на устройстве останутся.") },
            confirmButton = {
                TextButton(onClick = {
                    c.db.clearHistory()
                    confirmClear = false
                }) { Text("Очистить") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun HistoryCard(item: HistoryItem, exists: Boolean, onOpen: () -> Unit, onRedo: () -> Unit, onDelete: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth().alpha(if (exists) 1f else 0.5f),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.Top) {
                Text(
                    item.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Close, contentDescription = "Удалить запись") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Pill(item.format)
                Text(formatDate(item.createdAt), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
            Text(item.url, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onOpen, enabled = exists) { Text("Открыть") }
                OutlinedButton(onClick = onRedo, enabled = !exists) { Text("Скачать повторно") }
            }
        }
    }
}
