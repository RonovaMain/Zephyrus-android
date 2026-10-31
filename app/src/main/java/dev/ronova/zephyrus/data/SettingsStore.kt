package dev.ronova.zephyrus.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

data class Settings(
    val themeMode: String = "system",          // system | light | dark
    val dynamicColor: Boolean = true,          // Material You (Android 12+)
    val accent: String = "sakura",             // запасная палитра, если динамические цвета выключены
    val formatMode: String = "video",          // video | audio
    val videoQuality: String = "best",
    val audioQuality: String = "192",
    val videoContainer: String = "mp4",
    val audioFormat: String = "mp3",
    val embedThumbnail: Boolean = true,
    val embedMetadata: Boolean = true,
    val embedSubs: Boolean = false,
    val subsLangs: String = "ru,en",
    val sponsorblock: Boolean = false,
    val playlist: Boolean = false,
    val filenameTemplate: String = "%(title).150B [%(id)s].%(ext)s",
    val rateLimit: String = "",
    val fragments: Int = 4,
    val proxy: String = "",
    val notifications: Boolean = true,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val state = MutableStateFlow(load())

    fun update(block: Settings.() -> Settings) {
        val next = state.value.block()
        state.value = next
        save(next)
    }

    fun reset() {
        prefs.edit().clear().apply()
        state.value = Settings()
    }

    private fun load(): Settings {
        val d = Settings()
        fun s(key: String, def: String) = prefs.getString(key, def) ?: def
        return Settings(
            themeMode = s("themeMode", d.themeMode),
            dynamicColor = prefs.getBoolean("dynamicColor", d.dynamicColor),
            accent = s("accent", d.accent),
            formatMode = s("formatMode", d.formatMode),
            videoQuality = s("videoQuality", d.videoQuality),
            audioQuality = s("audioQuality", d.audioQuality),
            videoContainer = s("videoContainer", d.videoContainer),
            audioFormat = s("audioFormat", d.audioFormat),
            embedThumbnail = prefs.getBoolean("embedThumbnail", d.embedThumbnail),
            embedMetadata = prefs.getBoolean("embedMetadata", d.embedMetadata),
            embedSubs = prefs.getBoolean("embedSubs", d.embedSubs),
            subsLangs = s("subsLangs", d.subsLangs),
            sponsorblock = prefs.getBoolean("sponsorblock", d.sponsorblock),
            playlist = prefs.getBoolean("playlist", d.playlist),
            filenameTemplate = s("filenameTemplate", d.filenameTemplate),
            rateLimit = s("rateLimit", d.rateLimit),
            fragments = prefs.getInt("fragments", d.fragments),
            proxy = s("proxy", d.proxy),
            notifications = prefs.getBoolean("notifications", d.notifications),
        )
    }

    private fun save(s: Settings) {
        prefs.edit()
            .putString("themeMode", s.themeMode)
            .putBoolean("dynamicColor", s.dynamicColor)
            .putString("accent", s.accent)
            .putString("formatMode", s.formatMode)
            .putString("videoQuality", s.videoQuality)
            .putString("audioQuality", s.audioQuality)
            .putString("videoContainer", s.videoContainer)
            .putString("audioFormat", s.audioFormat)
            .putBoolean("embedThumbnail", s.embedThumbnail)
            .putBoolean("embedMetadata", s.embedMetadata)
            .putBoolean("embedSubs", s.embedSubs)
            .putString("subsLangs", s.subsLangs)
            .putBoolean("sponsorblock", s.sponsorblock)
            .putBoolean("playlist", s.playlist)
            .putString("filenameTemplate", s.filenameTemplate)
            .putString("rateLimit", s.rateLimit)
            .putInt("fragments", s.fragments)
            .putString("proxy", s.proxy)
            .putBoolean("notifications", s.notifications)
            .apply()
    }
}
