package dev.ronova.zephyrus.engine

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import dev.ronova.zephyrus.container
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Периодическая фоновая проверка обновлений yt-dlp (примерно раз в 2 дня) средствами системного JobScheduler. */
object UpdateScheduler {
    private const val JOB_ID = 4201

    /** Включить или выключить расписание. Если задача уже стоит в очереди, второй раз не создаётся. */
    fun sync(context: Context, enabled: Boolean) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (!enabled) {
            scheduler.cancel(JOB_ID)
            return
        }
        if (scheduler.getPendingJob(JOB_ID) != null) return
        val info = JobInfo.Builder(JOB_ID, ComponentName(context, UpdateJobService::class.java))
            .setPeriodic(TimeUnit.DAYS.toMillis(2), TimeUnit.HOURS.toMillis(12))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setRequiresBatteryNotLow(true)
            .setPersisted(true)            // переживает перезагрузку (нужно разрешение RECEIVE_BOOT_COMPLETED)
            .build()
        try {
            scheduler.schedule(info)
        } catch (e: Exception) {
            // расписание не создано — останется обновление при запуске приложения
        }
    }
}

class UpdateJobService : JobService() {
    private var work: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val c = container
        work = c.scope.launch(Dispatchers.IO) {
            val finished = try {
                c.engine.backgroundUpdate()
            } catch (e: Exception) {
                false
            }
            jobFinished(params, !finished)     // false → система повторит задачу позже
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        work?.cancel()
        return true
    }
}
