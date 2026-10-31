package dev.ronova.zephyrus.engine

import android.app.Application
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EngineState(
    val ready: Boolean = false,
    val error: String? = null,
    val version: String = "…",
    val updating: Boolean = false,
    val updateMessage: String = "",
    val updateOk: Boolean? = null,
)

/**
 * Движок: yt-dlp (с встроенным Python) и FFmpeg из библиотеки youtubedl-android.
 * yt-dlp можно обновить прямо из приложения — новая версия кладётся во внутреннее хранилище.
 * FFmpeg вшит в библиотеку и обновляется вместе с ней (версия библиотеки в app/build.gradle.kts).
 */
class Engine(private val app: Application, private val scope: CoroutineScope) {
    val state = MutableStateFlow(EngineState())

    fun start() {
        scope.launch(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
                state.update { it.copy(ready = true, error = null, version = readVersion()) }
            } catch (e: Exception) {
                state.update { it.copy(ready = false, error = e.message ?: e.toString()) }
            }
        }
    }

    suspend fun awaitReady(): Boolean {
        val s = state.first { it.ready || it.error != null }
        return s.ready
    }

    private fun readVersion(): String = try {
        YoutubeDL.getInstance().version(app) ?: "—"
    } catch (e: Exception) {
        "—"
    }

    fun update() {
        if (state.value.updating || !state.value.ready) return
        scope.launch(Dispatchers.IO) {
            state.update { it.copy(updating = true, updateMessage = "Проверка обновлений…", updateOk = null) }
            try {
                val status = YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel.STABLE)
                val upToDate = status == YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE
                state.update {
                    it.copy(
                        updating = false, version = readVersion(), updateOk = true,
                        updateMessage = if (upToDate) "Уже установлена последняя версия" else "yt-dlp обновлён до последней версии",
                    )
                }
            } catch (e: Exception) {
                state.update {
                    it.copy(updating = false, updateOk = false, updateMessage = e.message ?: "Не удалось обновить")
                }
            }
        }
    }
}
