package dev.ronova.zephyrus.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.ronova.zephyrus.R
import dev.ronova.zephyrus.engine.YtDlpArgs

/** Варианты качества видео: (подпись, значение). Подписи переводятся. */
@Composable
fun videoQualityOptions(): List<Pair<String, String>> {
    val best = stringResource(R.string.qmode_best)
    return YtDlpArgs.VIDEO_QUALITY_VALUES.map { v ->
        val label = when (v) {
            "best" -> best
            "2160" -> "4K · 2160p"
            "1440" -> "2K · 1440p"
            "1080" -> "Full HD · 1080p"
            "720" -> "HD · 720p"
            else -> "${v}p"
        }
        label to v
    }
}

/** Варианты качества аудио: (подпись, значение). */
@Composable
fun audioQualityOptions(): List<Pair<String, String>> {
    val best = stringResource(R.string.quality_best_vbr)
    val kbps = YtDlpArgs.AUDIO_QUALITY_VALUES.associateWith { stringResource(R.string.kbps_fmt, it) }
    return YtDlpArgs.AUDIO_QUALITY_VALUES.map { v -> (if (v == "0") best else kbps.getValue(v)) to v }
}
