package dev.ronova.zephyrus.engine

import android.app.Application
import android.content.Context
import androidx.annotation.StringRes
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import dev.ronova.zephyrus.R
import dev.ronova.zephyrus.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class EngineState(
    val ready: Boolean = false,
    val error: String? = null,
    val version: String = "…",
    val updating: Boolean = false,
    @StringRes val updateMessageRes: Int = 0,
    val updateDetail: String = "",
    val updateOk: Boolean? = null,
    val lastCheck: Long = 0L,          // когда последний раз успешно проверяли обновления (мс), 0 — ещё ни разу
)

/**
 * Движок: yt-dlp (с встроенным Python) и FFmpeg из библиотеки youtubedl-android.
 *
 * Автообновление yt-dlp:
 *  - при первом запуске (пока не было ни одной успешной проверки);
 *  - далее примерно раз в 2 дня: при запуске приложения, если прошло достаточно времени,
 *    и в фоне через [UpdateScheduler]/[UpdateJobService].
 * FFmpeg вшит в библиотеку и обновляется вместе с ней (версия в app/build.gradle.kts).
 */
class Engine(
    private val app: Application,
    private val scope: CoroutineScope,
    private val settings: SettingsStore,
) {
    private val prefs = app.getSharedPreferences("engine", Context.MODE_PRIVATE)

    val state = MutableStateFlow(EngineState(lastCheck = prefs.getLong(KEY_LAST_CHECK, 0L)))

    /** Подставляется контейнером: идут ли сейчас загрузки (во время загрузки yt-dlp обновлять нельзя). */
    var isBusy: () -> Boolean = { false }

    fun start() {
        scope.launch(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
            } catch (e: Exception) {
                state.update { it.copy(ready = false, error = e.message ?: e.toString()) }
                return@launch
            }
            val due = isUpdateDue()
            // «updating» выставляем вместе с «ready», чтобы загрузка не стартовала в момент замены yt-dlp
            state.update {
                it.copy(
                    ready = true, error = null, version = readVersion(), updating = due,
                    updateMessageRes = if (due) R.string.update_checking else 0, updateOk = null, updateDetail = "",
                )
            }
            if (due) performUpdate()
        }
    }

    private fun isUpdateDue(): Boolean {
        val last = state.value.lastCheck
        if (last == 0L) return true                       // первый запуск
        if (!settings.state.value.autoUpdate) return false
        return System.currentTimeMillis() - last >= CHECK_INTERVAL
    }

    /** Ждём, пока движок будет готов и не обновляется. false — движок не запустился. */
    suspend fun awaitReady(): Boolean {
        val s = state.first { (it.ready && !it.updating) || it.error != null }
        return s.ready
    }

    private fun readVersion(): String = try {
        YoutubeDL.getInstance().version(app) ?: "—"
    } catch (e: Exception) {
        "—"
    }

    /** Ручное обновление (кнопка в настройках). */
    fun update() {
        if (state.value.updating || !state.value.ready || isBusy()) return
        state.update { it.copy(updating = true, updateMessageRes = R.string.update_checking, updateOk = null, updateDetail = "") }
        scope.launch(Dispatchers.IO) { performUpdate() }
    }

    /**
     * Фоновая проверка по расписанию. true — задачу повторять не нужно (обновились, актуально или отключено),
     * false — попробовать позже (идут загрузки или нет сети).
     */
    suspend fun backgroundUpdate(): Boolean {
        if (!settings.state.value.autoUpdate) return true
        val first = state.first { it.ready || it.error != null }
        if (!first.ready) return false
        state.first { !it.updating }                      // первый запуск мог ещё обновлять
        if (System.currentTimeMillis() - state.value.lastCheck < CHECK_INTERVAL / 2) return true
        if (isBusy()) return false
        state.update { it.copy(updating = true, updateMessageRes = R.string.update_checking, updateOk = null, updateDetail = "") }
        withContext(Dispatchers.IO) { performUpdate() }
        return state.value.updateOk == true
    }

    /** Блокирующая замена yt-dlp. Вызывать только на IO. */
    private fun performUpdate() {
        try {
            val status = YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel.STABLE)
            val upToDate = status == YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE
            val now = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
            state.update {
                it.copy(
                    updating = false, version = readVersion(), updateOk = true, lastCheck = now, updateDetail = "",
                    updateMessageRes = if (upToDate) R.string.update_uptodate else R.string.update_done,
                )
            }
        } catch (e: Exception) {
            state.update {
                it.copy(
                    updating = false, updateOk = false, updateMessageRes = R.string.update_failed,
                    updateDetail = e.message?.lineSequence()?.firstOrNull { l -> l.isNotBlank() }.orEmpty(),
                )
            }
        }
    }

    companion object {
        private const val KEY_LAST_CHECK = "lastUpdateCheck"
        val CHECK_INTERVAL: Long = TimeUnit.DAYS.toMillis(2)
    }
}
