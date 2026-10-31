package dev.ronova.zephyrus.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ronova.zephyrus.container
import dev.ronova.zephyrus.ui.components.ZIcons
import dev.ronova.zephyrus.ui.download.DownloadScreen
import dev.ronova.zephyrus.ui.history.HistoryScreen
import dev.ronova.zephyrus.ui.sessions.SessionsScreen
import dev.ronova.zephyrus.ui.settings.SettingsScreen

private data class Tab(val title: String, val icon: ImageVector)

@Composable
fun ZephyrusRoot() {
    val c = LocalContext.current.container
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val shared by c.sharedUrl.collectAsStateWithLifecycle()
    // ссылка из «Поделиться» всегда открывает вкладку «Загрузка»
    LaunchedEffect(shared) { if (shared != null) tab = 0 }

    val tabs = listOf(
        Tab("Загрузка", ZIcons.Download),
        Tab("Сессии", ZIcons.Key),
        Tab("История", ZIcons.History),
        Tab("Настройки", Icons.Filled.Settings),
    )
    val cs = MaterialTheme.colorScheme

    Box(Modifier.fillMaxSize().background(cs.background)) {
        // мягкое фирменное свечение сверху
        Box(
            Modifier
                .fillMaxWidth()
                .height(340.dp)
                .background(Brush.verticalGradient(listOf(cs.primaryContainer.copy(alpha = 0.6f), Color.Transparent)))
        )
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                NavigationBar(containerColor = cs.surfaceContainer) {
                    tabs.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(t.icon, contentDescription = t.title) },
                            label = { Text(t.title) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(bottom = padding.calculateBottomPadding())) {
                Crossfade(targetState = tab, label = "tabs") { current ->
                    when (current) {
                        0 -> DownloadScreen()
                        1 -> SessionsScreen()
                        2 -> HistoryScreen(onGoDownload = { tab = 0 })
                        else -> SettingsScreen()
                    }
                }
            }
        }
    }
}
