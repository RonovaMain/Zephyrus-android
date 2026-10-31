package dev.ronova.zephyrus

import android.app.Application
import android.content.Context
import dev.ronova.zephyrus.data.Database
import dev.ronova.zephyrus.data.SessionStore
import dev.ronova.zephyrus.data.SettingsStore
import dev.ronova.zephyrus.engine.DownloadCenter
import dev.ronova.zephyrus.engine.Engine
import dev.ronova.zephyrus.engine.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

/** Сведения о приложении. Ссылку на репозиторий при желании поменяйте здесь. */
object AppInfo {
    const val AUTHOR = "RonovaDev"
    const val GITHUB_URL = "https://github.com/RonovaMain/Zephyrus-android"
    const val LICENSE = "GPL-3.0"
}

/** Все сервисы приложения в одном месте. Живёт, пока жив процесс. */
class AppContainer(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsStore(app)
    val db = Database(app)
    val sessions = SessionStore(app, db)
    val engine = Engine(app, scope)
    val downloads = DownloadCenter(app, scope, settings, engine, sessions, db)

    /** Ссылка, пришедшая через «Поделиться». */
    val sharedUrl = MutableStateFlow<String?>(null)

    fun start() {
        Notifications.ensureChannels(app)
        db.refresh()
        engine.start()
    }
}

class ZephyrusApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
    }
}

val Context.container: AppContainer
    get() = (applicationContext as ZephyrusApp).container
