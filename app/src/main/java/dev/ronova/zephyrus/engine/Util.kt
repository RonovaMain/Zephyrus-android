package dev.ronova.zephyrus.engine

import java.text.DateFormat
import java.util.Date

private val URL_RE = Regex("""^https?://\S+$""", RegexOption.IGNORE_CASE)
private val URL_IN_TEXT_RE = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

fun looksLikeUrl(text: String): Boolean = URL_RE.matches(text.trim())

/** Достаёт первую ссылку из текста (например, из «Поделиться»). */
fun extractUrl(text: String?): String? = text?.let { URL_IN_TEXT_RE.find(it)?.value?.trimEnd('.', ',', ')') }

fun formatSize(bytes: Double?): String {
    if (bytes == null || bytes <= 0) return "—"
    val units = arrayOf("Б", "КБ", "МБ", "ГБ", "ТБ")
    var v = bytes
    var i = 0
    while (v >= 1024 && i < units.size - 1) {
        v /= 1024
        i++
    }
    return if (i == 0) "%.0f %s".format(v, units[i]) else "%.1f %s".format(v, units[i])
}

fun formatSpeed(bytesPerSec: Double?): String =
    if (bytesPerSec == null || bytesPerSec <= 0) "—" else formatSize(bytesPerSec) + "/с"

fun formatEta(seconds: Double?): String {
    if (seconds == null || seconds < 0) return "—"
    val s = seconds.toLong()
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

fun formatDuration(seconds: Long?): String = if (seconds == null || seconds <= 0) "" else formatEta(seconds.toDouble())

fun formatDate(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
