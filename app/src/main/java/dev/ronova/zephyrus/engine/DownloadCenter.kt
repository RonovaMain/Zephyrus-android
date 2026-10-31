package dev.ronova.zephyrus.engine

import android.app.Application
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dev.ronova.zephyrus.data.Database
import dev.ronova.zephyrus.data.SessionStore
import dev.ronova.zephyrus.data.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

data class DlJob(
    val id: Int = 0,
    val url: String,
    val mode: String,                 // video | audio
    val quality: String,              // best / 1080 / 192 / fmt:<spec>
    val playlist: Boolean = false,
    val session: String = "auto",     // auto | none | <id профиля>
    val label: String = "",           // подпись формата при ручном выборе
    val historyId: Long? = null,      // при повторной загрузке обновляем запись истории
)

data class DlState(
    val current: DlJob? = null,
    val progress: ProgressUi = ProgressUi(),
    val queue: List<DlJob> = emptyList(),
    val log: List<String> = emptyList(),
    val finished: Boolean = false,
    val ok: Boolean = false,
    val message: String = "",
    val lastFile: String? = null,     // content:// uri последнего готового файла
) {
    val idle: Boolean get() = current == null && queue.isEmpty()
}

/** Очередь загрузок: по одной за раз, в фоне, с отменой. Работает вместе с [DownloadService]. */
class DownloadCenter(
    private val app: Application,
    private val scope: CoroutineScope,
    private val settings: SettingsStore,
    private val engine: Engine,
    private val sessions: SessionStore,
    private val db: Database,
) {
    private val _state = MutableStateFlow(DlState())
    val state: StateFlow<DlState> = _state.asStateFlow()

    private val lock = Any()
    private val queue = ArrayDeque<DlJob>()
    private var nextId = 1
    private var running = false

    @Volatile private var cancelled = false
    @Volatile private var currentPid: String? = null
    private val infoCounter = AtomicInteger()

    // ------------------------------------------------------------------ очередь
    fun enqueue(job: DlJob) {
        val snapshot = synchronized(lock) {
            queue.addLast(job.copy(id = nextId++))
            queue.toList()
        }
        _state.update { it.copy(queue = snapshot) }
        DownloadService.start(app)
        ensureWorker()
    }

    fun clearQueue() {
        synchronized(lock) { queue.clear() }
        _state.update { it.copy(queue = emptyList()) }
    }

    fun cancelCurrent() {
        cancelled = true
        currentPid?.let {
            try {
                YoutubeDL.getInstance().destroyProcessById(it)
            } catch (e: Exception) {
                // процесс уже завершился
            }
        }
    }

    private fun ensureWorker() {
        synchronized(lock) {
            if (running) return
            running = true
        }
        scope.launch(Dispatchers.IO) { runLoop() }
    }

    private suspend fun runLoop() {
        while (true) {
            var snapshot: List<DlJob> = emptyList()
            val job = synchronized(lock) {
                val next = queue.removeFirstOrNull()
                if (next == null) running = false else snapshot = queue.toList()
                next
            } ?: break
            // current и queue меняем одним действием, чтобы сервис не решил, что всё закончилось
            _state.update {
                it.copy(
                    current = job, queue = snapshot, progress = ProgressUi(), log = emptyList(),
                    finished = false, ok = false, message = "", lastFile = null,
                )
            }
            process(job)
        }
    }

    // ------------------------------------------------------------------ загрузка
    private suspend fun process(job: DlJob) {
        cancelled = false
        val parser = ProgressParser(job.mode)
        if (!engine.awaitReady()) {
            finish(job, false, engine.state.value.error ?: "Движок не готов", emptyList())
            return
        }
        val s = settings.state.value
        val base = app.getExternalFilesDir(null) ?: app.filesDir
        val work = File(base, "dl/${job.id}").apply { deleteRecursively(); mkdirs() }
        val cookie = prepareCookies(job.url, job.session, "dl_${job.id}")
        val pid = "dl-${job.id}"
        currentPid = pid

        var error: String? = null
        try {
            val request = YoutubeDLRequest(job.url)
            for (o in YtDlpArgs.downloadOptions(job, s, work.absolutePath, cookie?.absolutePath)) {
                if (o.value == null) request.addOption(o.flag) else request.addOption(o.flag, o.value)
            }
            YoutubeDL.getInstance().execute(request, pid) { _, _, line -> onLine(parser, line) }
        } catch (e: Exception) {
            error = if (cancelled) "Загрузка отменена" else errorFrom(e, parser)
        } finally {
            currentPid = null
            cookie?.delete()
        }

        if (cancelled) {
            work.deleteRecursively()
            finish(job, false, "Загрузка отменена", emptyList())
            return
        }
        var saveError: String? = null
        val files: List<SavedFile> = try {
            FileStore.export(app, work)
        } catch (e: Exception) {
            saveError = e.message ?: "Не удалось сохранить файл"
            emptyList()
        } finally {
            work.deleteRecursively()
        }
        when {
            files.isNotEmpty() -> finish(job, true, if (error != null) "Загружено частично" else "Готово", files)
            else -> finish(job, false, saveError ?: error ?: "yt-dlp не создал файл", emptyList())
        }
    }

    private fun errorFrom(e: Exception, parser: ProgressParser): String {
        if (parser.lastError.isNotBlank()) return parser.lastError
        val fromMessage = e.message?.lineSequence()?.firstOrNull { it.startsWith("ERROR") }?.removePrefix("ERROR: ")
        return fromMessage ?: e.message?.lineSequence()?.firstOrNull { it.isNotBlank() } ?: "Ошибка загрузки"
    }

    private fun onLine(parser: ProgressParser, line: String) {
        val (log, snap) = synchronized(parser) { Pair(parser.feed(line), parser.snapshot()) }
        _state.update { st ->
            st.copy(progress = snap, log = if (log) (st.log + line.trim()).takeLast(150) else st.log)
        }
    }

    private fun finish(job: DlJob, ok: Boolean, message: String, files: List<SavedFile>) {
        val s = settings.state.value
        if (ok) {
            val fmt = job.label.ifEmpty { YtDlpArgs.qualityLabel(job.mode, job.quality, s.videoContainer, s.audioFormat) }
            files.forEachIndexed { i, f ->
                val historyId = job.historyId
                if (historyId != null && i == 0) {
                    db.updateHistory(historyId, f.title, fmt, f.uri)
                } else {
                    db.addHistory(f.title, job.url, job.mode, job.quality, fmt, f.uri)
                }
            }
        }
        _state.update {
            it.copy(
                current = null,
                progress = ProgressUi(percent = if (ok) 100f else 0f, indeterminate = false, status = message),
                finished = true, ok = ok, message = message, lastFile = files.firstOrNull()?.uri,
            )
        }
        if (s.notifications && message != "Загрузка отменена") {
            Notifications.notifyDone(
                app, job.id,
                if (ok) "Загрузка завершена" else "Ошибка загрузки",
                if (ok) files.firstOrNull()?.name ?: job.url else message,
            )
        }
    }

    private fun prepareCookies(url: String, session: String, tag: String): File? {
        if (session == "none") return null
        val profileId = if (session == "auto") sessions.profileForUrl(url)?.id else session
        if (profileId == null) return null
        return try {
            sessions.exportTemp(profileId, File(app.cacheDir, "cookies_$tag.txt"))
        } catch (e: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------ информация о ссылке
    /** Название, обложка и список реальных форматов. Отмена корутины убивает процесс yt-dlp. */
    suspend fun fetchInfo(url: String, playlist: Boolean, session: String): Result<InfoSummary> = coroutineScope {
        val pid = "info-${infoCounter.incrementAndGet()}"
        val cookie = prepareCookies(url, session, pid)
        val deferred = async(Dispatchers.IO) {
            runCatching {
                if (!engine.awaitReady()) error(engine.state.value.error ?: "Движок не готов")
                val request = YoutubeDLRequest(url)
                for (o in YtDlpArgs.infoOptions(playlist, cookie?.absolutePath, settings.state.value.proxy)) {
                    if (o.value == null) request.addOption(o.flag) else request.addOption(o.flag, o.value)
                }
                val out = YoutubeDL.getInstance().execute(request, pid, null).out
                YtDlpArgs.parseInfo(out)
            }
        }
        try {
            deferred.await()
        } catch (e: CancellationException) {
            try {
                YoutubeDL.getInstance().destroyProcessById(pid)
            } catch (ignored: Exception) {
            }
            throw e
        } finally {
            cookie?.delete()
        }
    }
}
