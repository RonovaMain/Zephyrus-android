package dev.ronova.zephyrus.engine

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.ronova.zephyrus.R
import dev.ronova.zephyrus.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Служба переднего плана: пока идёт загрузка, система не убивает процесс, а в шторке виден прогресс. */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observing = false
    private var lastKey = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) container.downloads.cancelCurrent()
        // startForeground нужно вызвать сразу, иначе система остановит службу
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, build("Zephyrus", "Подготовка…", 0, true),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        if (!observing) {
            observing = true
            scope.launch {
                container.downloads.state.collect { st ->
                    if (st.idle) {
                        ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        render(st)
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun render(st: DlState) {
        val p = st.progress
        val pct = p.percent.toInt()
        val title = st.current?.let { if (it.mode == "audio") "Загрузка аудио" else "Загрузка видео" } ?: "В очереди"
        val queued = if (st.queue.isNotEmpty()) " · в очереди: ${st.queue.size}" else ""
        val text = buildString {
            append(p.status)
            if (!p.indeterminate) append(" · ").append(pct).append('%')
            if (p.speed != null && p.speed > 0) append(" · ").append(formatSpeed(p.speed))
            append(queued)
        }
        val key = "$title|$text"
        if (key == lastKey) return
        lastKey = key
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, build(title, text, pct, p.indeterminate))
    }

    private fun build(title: String, text: String, percent: Int, indeterminate: Boolean): Notification {
        val cancel = PendingIntent.getService(
            this, 1, Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, Notifications.CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(Notifications.openAppIntent(this))
            .addAction(0, "Отмена", cancel)
            .setProgress(100, percent, indeterminate)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 42
        private const val ACTION_CANCEL = "dev.ronova.zephyrus.CANCEL"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
        }
    }
}
