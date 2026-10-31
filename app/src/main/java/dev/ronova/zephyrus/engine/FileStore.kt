package dev.ronova.zephyrus.engine

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.util.Locale

data class SavedFile(val name: String, val title: String, val uri: String)

/**
 * Готовые файлы yt-dlp сначала пишет в рабочую папку приложения, затем они переносятся
 * в общую папку «Загрузки/Zephyrus» через MediaStore — разрешения на память не нужны (Android 10+).
 */
object FileStore {
    private const val ROOT = "Download/Zephyrus"

    private val SKIP_EXT = setOf("part", "ytdl", "temp", "tmp", "jpg", "jpeg", "png", "webp", "vtt", "srt", "ass", "json", "description")

    private val MIME = mapOf(
        "mp4" to "video/mp4", "mkv" to "video/x-matroska", "webm" to "video/webm",
        "mp3" to "audio/mpeg", "m4a" to "audio/mp4", "opus" to "audio/ogg", "ogg" to "audio/ogg",
        "flac" to "audio/flac", "wav" to "audio/x-wav", "aac" to "audio/aac",
    )

    fun mimeFor(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return MIME[ext] ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    /** Название для истории: имя файла без расширения и без « [id]» в конце. */
    fun titleOf(fileName: String): String =
        fileName.substringBeforeLast('.').replace(Regex("""\s\[[^\]]{4,}]$"""), "").trim()

    /** Переносит готовые файлы из [workDir] в Загрузки/Zephyrus. Рабочую папку после этого очищает. */
    fun export(context: Context, workDir: File): List<SavedFile> {
        val files = workDir.walkTopDown()
            .filter { it.isFile && it.extension.lowercase(Locale.ROOT) !in SKIP_EXT }
            .sortedBy { it.path }
            .toList()
        val saved = ArrayList<SavedFile>()
        val resolver = context.contentResolver
        for (file in files) {
            val rel = file.parentFile?.relativeTo(workDir)?.path?.replace('\\', '/').orEmpty()
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(file.name))
                put(MediaStore.MediaColumns.RELATIVE_PATH, if (rel.isEmpty()) ROOT else "$ROOT/$rel")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: continue
            try {
                resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                    ?: throw IllegalStateException("Не удалось открыть файл для записи")
                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
                saved.add(SavedFile(file.name, titleOf(file.name), uri.toString()))
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        }
        workDir.deleteRecursively()
        return saved
    }

    /** Файл по-прежнему существует? (пользователь мог удалить его в «Файлах»). */
    fun exists(context: Context, uriString: String): Boolean = try {
        val uri = Uri.parse(uriString)
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
                ?.use { it.moveToFirst() } ?: false
        } else {
            File(uri.path ?: "").exists()
        }
    } catch (e: Exception) {
        false
    }

    fun openIntent(context: Context, uriString: String): Intent {
        val uri = Uri.parse(uriString)
        val type = try { context.contentResolver.getType(uri) } catch (e: Exception) { null } ?: "*/*"
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, type)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
