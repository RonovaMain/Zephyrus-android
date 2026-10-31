package dev.ronova.zephyrus.engine

import dev.ronova.zephyrus.data.Settings
import org.json.JSONArray
import org.json.JSONObject

/** Одна опция командной строки yt-dlp: флаг и (необязательно) значение. */
data class Opt(val flag: String, val value: String? = null)

data class FormatOption(val id: String, val label: String, val height: Int = 0, val muxed: Boolean = false)

data class InfoSummary(
    val title: String,
    val uploader: String,
    val durationSec: Long?,
    val thumbnail: String,
    val isPlaylist: Boolean,
    val count: Int,
    val videos: List<FormatOption>,
    val audios: List<FormatOption>,
)

data class ProgressUi(
    val percent: Float = 0f,
    val indeterminate: Boolean = true,
    val speed: Double? = null,
    val eta: Double? = null,
    val size: Double? = null,
    val status: String = "Подготовка…",
    val item: Int = 1,
    val itemCount: Int = 1,
)

/** Разбор вывода yt-dlp регулярными выражениями и расчёт общего прогресса 0..100. */
class ProgressParser(private val mode: String) {
    private var percent = 0f
    private var indeterminate = true
    private var speed: Double? = null
    private var eta: Double? = null
    private var size: Double? = null
    private var status = "Подготовка…"
    private var item = 1
    private var itemCount = 1
    private var streamsDone = 0
    private var expectedStreams: Int? = null
    var lastError: String = ""
        private set

    fun snapshot() = ProgressUi(percent, indeterminate, speed, eta, size, status, item, itemCount)

    /** Возвращает true, если строку стоит показать в журнале. */
    fun feed(raw: String): Boolean {
        val line = raw.trim()
        if (line.isEmpty()) return false

        PROG_RE.find(line)?.let {
            onProgress(it.groupValues)
            return false
        }
        PP_RE.find(line)?.let { m ->
            val name = PP_NAMES[m.groupValues[1]]
            if (name != null) {
                indeterminate = true
                status = name
                speed = null
                eta = null
            }
            return true
        }
        ITEM_RE.find(line)?.let {
            item = it.groupValues[1].toInt()
            itemCount = maxOf(1, it.groupValues[2].toInt())
            streamsDone = 0
            expectedStreams = null
            indeterminate = false
            percent = overall(0.0)
        }
        ERROR_RE.find(line)?.let { lastError = it.groupValues[1] }
        if (line.contains("has already been downloaded")) status = "Файл уже скачан"
        return true
    }

    private fun onProgress(g: List<String>) {
        indeterminate = false
        val done = g[1].toDoubleOrNull() ?: 0.0
        val total = g[2].toDoubleOrNull() ?: g[3].toDoubleOrNull()
        val fi = g[6].toDoubleOrNull()
        val fc = g[7].toDoubleOrNull()
        var frac: Double? = when {
            total != null && total > 0 -> minOf(1.0, done / total)
            fc != null && fc > 0 -> minOf(1.0, (fi ?: 0.0) / fc)
            else -> null
        }
        size = (if (total != null && total > 0) total else if (done > 0) done else null)
        speed = g[4].toDoubleOrNull()
        eta = g[5].toDoubleOrNull()

        val hasV = hasCodec(g[9])
        val hasA = hasCodec(g[10])
        if (expectedStreams == null) expectedStreams = if (mode == "audio" || (hasV && hasA)) 1 else 2
        status = when {
            mode == "audio" || (hasA && !hasV) -> "Загрузка аудио"
            hasV && !hasA -> "Загрузка видео"
            else -> "Загрузка"
        }
        val finished = g[8] == "finished"
        if (finished) frac = 1.0
        if (frac == null) {
            indeterminate = true
        } else {
            percent = overall(frac)
        }
        if (finished) streamsDone += 1
    }

    private fun overall(streamFrac: Double): Float {
        val expected = expectedStreams ?: if (mode == "audio") 1 else 2
        val itemFrac = (minOf(streamsDone, expected - 1) + streamFrac) / expected
        // последние ~2% оставляем на постобработку
        val value = ((item - 1) + itemFrac) / itemCount * 100.0
        return value.coerceIn(0.0, 98.0).toFloat()
    }

    private fun hasCodec(c: String) = c.isNotEmpty() && c != "none" && c != "NA"

    companion object {
        private val PROG_RE = Regex(
            """^ZPROG\|([^|]*)\|([^|]*)\|([^|]*)\|([^|]*)\|([^|]*)\|([^|]*)\|([^|]*)\|([^|]*)\|([^|]*)\|([^|]*)"""
        )
        private val PP_RE = Regex("""^\[(\w+)]""")
        private val ITEM_RE = Regex("""Downloading (?:item|video) (\d+) of (\d+)""")
        private val ERROR_RE = Regex("""^ERROR: (.+)$""")

        private val PP_NAMES = mapOf(
            "Merger" to "Объединение видео и аудио",
            "ExtractAudio" to "Конвертация в аудио",
            "VideoConvertor" to "Конвертация видео",
            "VideoRemuxer" to "Преобразование контейнера",
            "EmbedThumbnail" to "Встраивание обложки",
            "ThumbnailsConvertor" to "Подготовка обложки",
            "Metadata" to "Запись метаданных",
            "FFmpegMetadata" to "Запись метаданных",
            "EmbedSubtitle" to "Встраивание субтитров",
            "ModifyChapters" to "Вырезание сегментов SponsorBlock",
            "MoveFiles" to "Завершение",
        )
    }
}

object YtDlpArgs {
    const val DEFAULT_TEMPLATE = "%(title).150B [%(id)s].%(ext)s"

    private const val PROGRESS_TEMPLATE =
        "download:ZPROG|%(progress.downloaded_bytes)s|%(progress.total_bytes)s|%(progress.total_bytes_estimate)s" +
            "|%(progress.speed)s|%(progress.eta)s|%(progress.fragment_index)s|%(progress.fragment_count)s" +
            "|%(progress.status)s|%(info.vcodec)s|%(info.acodec)s"

    val VIDEO_QUALITIES = listOf(
        "Лучшее доступное" to "best", "4K · 2160p" to "2160", "2K · 1440p" to "1440",
        "Full HD · 1080p" to "1080", "HD · 720p" to "720", "480p" to "480", "360p" to "360",
    )
    val AUDIO_QUALITIES = listOf(
        "Лучшее (VBR)" to "0", "320 кбит/с" to "320", "256 кбит/с" to "256",
        "192 кбит/с" to "192", "128 кбит/с" to "128",
    )
    val VIDEO_CONTAINERS = listOf("mp4", "mkv", "webm")
    val AUDIO_FORMATS = listOf("mp3", "m4a", "opus", "flac", "wav")

    fun qualityLabel(mode: String, quality: String, container: String, audioFormat: String): String {
        if (quality.startsWith("fmt:")) {
            return (if (mode == "audio") audioFormat else container).uppercase() + " · ручной выбор"
        }
        if (mode == "audio") {
            val q = if (quality == "0") "лучшее" else "$quality кбит/с"
            return audioFormat.uppercase() + " · " + q
        }
        val q = if (quality == "best") "лучшее" else quality + "p"
        return container.uppercase() + " · " + q
    }

    fun downloadOptions(job: DlJob, s: Settings, outDir: String, cookieFile: String?): List<Opt> {
        val o = ArrayList<Opt>()
        fun add(flag: String, value: String? = null) { o.add(Opt(flag, value)) }

        add("--ignore-config"); add("--newline"); add("--no-mtime"); add("--no-cache-dir")
        add("--windows-filenames")
        add("--progress-template", PROGRESS_TEMPLATE)
        add("--retries", "10"); add("--fragment-retries", "10"); add("--socket-timeout", "30")
        add("-P", outDir)

        if (job.playlist) {
            add("--yes-playlist")
            add("-o", "%(playlist_title|Playlist)s/%(playlist_index)03d - %(title).150B.%(ext)s")
        } else {
            add("--no-playlist")
            add("-o", s.filenameTemplate.ifBlank { DEFAULT_TEMPLATE })
        }

        val manual = if (job.quality.startsWith("fmt:")) job.quality.substring(4) else ""
        if (job.mode == "audio") {
            add("-f", if (manual.isNotEmpty()) "$manual/ba/b" else "ba/b")
            add("-x")
            add("--audio-format", s.audioFormat)
            if (manual.isEmpty() && s.audioFormat in listOf("mp3", "m4a", "opus")) {
                add("--audio-quality", if (job.quality == "0") "0" else "${job.quality}K")
            }
            if (s.embedThumbnail && s.audioFormat != "wav") {
                add("--embed-thumbnail"); add("--convert-thumbnails", "jpg")
            }
        } else {
            val merge = if (s.videoContainer == "mkv") "mkv" else "${s.videoContainer}/mkv"
            if (manual.isNotEmpty()) {
                // видео и звук выбраны вручную: yt-dlp скачает их отдельно, FFmpeg склеит
                add("-f", "$manual/bv*+ba/b")
            } else {
                val cap = if (job.quality == "best") "res" else "res:${job.quality}"
                val codecs = when (s.videoContainer) {
                    "mp4" -> ",vcodec:h264,acodec:m4a"
                    "webm" -> ",vcodec:vp9,acodec:opus"
                    else -> ""
                }
                add("-f", "bv*+ba/b")
                add("-S", cap + codecs)
            }
            add("--merge-output-format", merge)
            if (s.embedThumbnail && s.videoContainer != "webm") {
                add("--embed-thumbnail"); add("--convert-thumbnails", "jpg")
            }
            if (s.embedSubs) {
                add("--embed-subs"); add("--sub-langs", s.subsLangs.ifBlank { "ru,en" })
            }
            add("--embed-chapters")
        }

        if (s.embedMetadata) add("--embed-metadata")
        if (s.sponsorblock) add("--sponsorblock-remove", "sponsor,selfpromo,interaction")
        if (s.rateLimit.isNotBlank()) add("-r", s.rateLimit.trim())
        if (s.fragments > 1) add("-N", s.fragments.toString())
        if (s.proxy.isNotBlank()) add("--proxy", s.proxy.trim())
        if (cookieFile != null) add("--cookies", cookieFile)
        return o
    }

    fun infoOptions(playlist: Boolean, cookieFile: String?, proxy: String): List<Opt> {
        val o = ArrayList<Opt>()
        o.add(Opt("--ignore-config")); o.add(Opt("-J")); o.add(Opt("--no-warnings")); o.add(Opt("--no-cache-dir"))
        o.add(Opt("--socket-timeout", "20"))
        o.add(Opt(if (playlist) "--flat-playlist" else "--no-playlist"))
        if (proxy.isNotBlank()) o.add(Opt("--proxy", proxy.trim()))
        if (cookieFile != null) o.add(Opt("--cookies", cookieFile))
        return o
    }

    /** (спецификация формата для yt-dlp, подпись для истории) при ручном выборе. */
    fun manualSpec(
        audioMode: Boolean, container: String, video: FormatOption?, audio: FormatOption?, audioPickShown: Boolean,
    ): Pair<String, String> {
        val audioId = audio?.id.orEmpty()
        val audioText = if (audioId.isNotEmpty() && audio != null) audio.label.substringBefore(" · ") else "звук авто"
        if (audioMode) return Pair(audioId.ifEmpty { "ba" }, audioText)
        if (video == null) return Pair("bv*+ba/b", container.uppercase())
        val cont = container.uppercase()
        val vText = video.label.substringBefore(" · ")
        return when {
            video.muxed -> Pair(video.id, "$cont · $vText")
            audioPickShown && audioId.isNotEmpty() -> Pair("${video.id}+$audioId", "$cont · $vText + $audioText")
            else -> Pair("${video.id}+ba/${video.id}", "$cont · $vText + звук авто")
        }
    }

    // ------------------------------------------------------------------ информация о ссылке
    fun parseInfo(json: String): InfoSummary {
        val start = json.indexOf('{')
        require(start >= 0) { "Пустой ответ yt-dlp" }
        val d = JSONObject(json.substring(start))
        val entries = d.optJSONArray("entries")
        val isPlaylist = d.str("_type") == "playlist"

        var thumb = d.str("thumbnail").orEmpty()
        val thumbs = d.optJSONArray("thumbnails")
        if (thumbs != null) {
            for (i in thumbs.length() - 1 downTo 0) {
                val url = thumbs.optJSONObject(i)?.str("url").orEmpty()
                if (url.contains(".jpg")) { thumb = url; break }
            }
        }
        if (isPlaylist && thumb.isEmpty() && entries != null && entries.length() > 0) {
            val first = entries.optJSONObject(0)
            thumb = first?.str("thumbnail").orEmpty()
        }
        val (videos, audios) = if (isPlaylist) Pair(emptyList<FormatOption>(), emptyList<FormatOption>()) else summarizeFormats(d.optJSONArray("formats"))
        return InfoSummary(
            title = d.str("title") ?: d.str("id") ?: "Без названия",
            uploader = d.str("uploader") ?: d.str("channel") ?: "",
            durationSec = d.num("duration")?.toLong(),
            thumbnail = thumb,
            isPlaylist = isPlaylist,
            count = d.num("playlist_count")?.toInt() ?: entries?.length() ?: 0,
            videos = videos,
            audios = audios,
        )
    }

    private class VideoCand(val opt: FormatOption, val fps: Int, val tbr: Double)
    private class AudioCand(val opt: FormatOption, val abr: Double)

    private fun hasCodec(c: String?) = !c.isNullOrEmpty() && c != "none" && c != "NA"

    private fun codecName(code: String?): String {
        val c = (code ?: "").lowercase()
        val table = listOf(
            "avc" to "H.264", "h264" to "H.264", "vp09" to "VP9", "vp9" to "VP9", "av01" to "AV1",
            "hev" to "HEVC", "hvc" to "HEVC", "h265" to "HEVC", "vp8" to "VP8", "mp4a" to "AAC",
            "opus" to "Opus", "vorbis" to "Vorbis", "ac-3" to "AC3", "ec-3" to "E-AC3", "flac" to "FLAC", "mp3" to "MP3",
        )
        for ((prefix, name) in table) if (c.startsWith(prefix)) return name
        return if (c.isNotEmpty() && c != "none") c.substringBefore('.').uppercase() else ""
    }

    private fun sizeText(f: JSONObject): String {
        val n = f.num("filesize") ?: f.num("filesize_approx") ?: return ""
        val mb = n / 1024.0 / 1024.0
        return if (mb >= 1024) "~%.1f ГБ".format(mb / 1024.0) else "~%.0f МБ".format(mb)
    }

    /** Два списка для ручного выбора: видео (отдельно) и звук (отдельно). Если сайт даёт только готовые форматы — они попадают в видео с muxed=true. */
    fun summarizeFormats(formats: JSONArray?): Pair<List<FormatOption>, List<FormatOption>> {
        val videoOnly = LinkedHashMap<String, VideoCand>()
        val muxed = LinkedHashMap<String, VideoCand>()
        val audio = LinkedHashMap<String, AudioCand>()
        val count = formats?.length() ?: 0
        for (i in 0 until count) {
            val f = formats?.optJSONObject(i) ?: continue
            val id = f.str("format_id") ?: continue
            if (f.str("protocol") == "mhtml" || f.str("format_note") == "storyboard") continue
            val vc = f.str("vcodec")
            val ac = f.str("acodec")
            val height = f.num("height")?.toInt() ?: 0
            if (hasCodec(vc) && height > 0) {
                val fps = Math.round(f.num("fps") ?: 0.0).toInt()
                val fpsText = if (fps > 30) fps.toString() else ""
                val tbr = f.num("tbr") ?: f.num("vbr") ?: 0.0
                var label = "${height}p$fpsText · ${codecName(vc)} · ${f.str("ext").orEmpty()}"
                val size = sizeText(f)
                if (size.isNotEmpty()) label += " · $size"
                val withAudio = hasCodec(ac)
                val cand = VideoCand(FormatOption(id, label, height, withAudio), fps, tbr)
                val target = if (withAudio) muxed else videoOnly
                val key = "$height|$fpsText|${codecName(vc)}|${f.str("ext")}"
                val old = target[key]
                if (old == null || tbr > old.tbr) target[key] = cand
            } else if (hasCodec(ac) && !hasCodec(vc)) {
                val abr = f.num("abr") ?: f.num("tbr") ?: 0.0
                val lang = f.str("language").orEmpty()
                val codec = codecName(ac)
                var label = if (abr > 0) "%.0f кбит/с · %s · %s".format(abr, codec, f.str("ext").orEmpty())
                else "$codec · ${f.str("ext").orEmpty()}"
                if (lang.isNotEmpty()) label += " · $lang"
                val size = sizeText(f)
                if (size.isNotEmpty()) label += " · $size"
                val key = "${Math.round(abr / 8)}|$codec|$lang"
                val old = audio[key]
                if (old == null || abr > old.abr) audio[key] = AudioCand(FormatOption(id, label), abr)
            }
        }
        val order = compareByDescending<VideoCand> { it.opt.height }.thenByDescending { it.fps }.thenByDescending { it.tbr }
        var videos = videoOnly.values.sortedWith(order).map { it.opt }
        if (videos.isEmpty()) videos = muxed.values.sortedWith(order).map { it.opt }
        val audios = audio.values.sortedByDescending { it.abr }.take(14).map { it.opt }
        return Pair(videos, audios)
    }

    private fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key)

    private fun JSONObject.num(key: String): Double? =
        if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }
}
