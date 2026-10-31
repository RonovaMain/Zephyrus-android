package dev.ronova.zephyrus.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow

data class HistoryItem(
    val id: Long,
    val title: String,
    val url: String,
    val createdAt: Long,
    val mode: String,       // video | audio
    val quality: String,    // 1080 / best / 192 / fmt:...
    val format: String,     // человекочитаемо: «MP4 · 1080p»
    val path: String,       // content:// uri сохранённого файла
)

data class ProfileRow(
    val id: String,
    val site: String,       // ключ сайта или custom:<домен>
    val name: String,
    val hosts: String,      // домены через запятую, для автоподстановки
    val createdAt: Long,
    val active: Boolean,
    val cookieCount: Int,
)

/** SQLite без лишних библиотек: история загрузок и метаданные cookie-профилей. */
class Database(context: Context) : SQLiteOpenHelper(context, "zephyrus.db", null, 1) {

    val history = MutableStateFlow<List<HistoryItem>>(emptyList())
    val profiles = MutableStateFlow<List<ProfileRow>>(emptyList())

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                url TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                mode TEXT NOT NULL,
                quality TEXT NOT NULL,
                format TEXT NOT NULL,
                path TEXT NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE profiles (
                id TEXT PRIMARY KEY,
                site TEXT NOT NULL,
                name TEXT NOT NULL,
                hosts TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                active INTEGER NOT NULL DEFAULT 0,
                cookie_count INTEGER NOT NULL DEFAULT 0)"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    /** Перечитать таблицы в StateFlow — интерфейс обновится сам. */
    fun refresh() {
        history.value = queryHistory()
        profiles.value = queryProfiles()
    }

    // ---------------------------------------------------------------- история
    private fun queryHistory(): List<HistoryItem> {
        val out = ArrayList<HistoryItem>()
        readableDatabase.query("history", null, null, null, null, null, "id DESC").use { c ->
            while (c.moveToNext()) {
                out.add(
                    HistoryItem(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        title = c.str("title"),
                        url = c.str("url"),
                        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
                        mode = c.str("mode"),
                        quality = c.str("quality"),
                        format = c.str("format"),
                        path = c.str("path"),
                    )
                )
            }
        }
        return out
    }

    fun addHistory(title: String, url: String, mode: String, quality: String, format: String, path: String): Long {
        val v = ContentValues().apply {
            put("title", title); put("url", url); put("created_at", System.currentTimeMillis())
            put("mode", mode); put("quality", quality); put("format", format); put("path", path)
        }
        val id = writableDatabase.insert("history", null, v)
        refresh()
        return id
    }

    fun updateHistory(id: Long, title: String, format: String, path: String) {
        val v = ContentValues().apply {
            put("title", title); put("format", format); put("path", path)
            put("created_at", System.currentTimeMillis())
        }
        writableDatabase.update("history", v, "id=?", arrayOf(id.toString()))
        refresh()
    }

    fun getHistory(id: Long): HistoryItem? = history.value.firstOrNull { it.id == id }

    fun deleteHistory(id: Long) {
        writableDatabase.delete("history", "id=?", arrayOf(id.toString()))
        refresh()
    }

    fun clearHistory() {
        writableDatabase.delete("history", null, null)
        refresh()
    }

    // ---------------------------------------------------------------- профили
    private fun queryProfiles(): List<ProfileRow> {
        val out = ArrayList<ProfileRow>()
        readableDatabase.query("profiles", null, null, null, null, null, "site, created_at").use { c ->
            while (c.moveToNext()) {
                out.add(
                    ProfileRow(
                        id = c.str("id"),
                        site = c.str("site"),
                        name = c.str("name"),
                        hosts = c.str("hosts"),
                        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
                        active = c.getInt(c.getColumnIndexOrThrow("active")) == 1,
                        cookieCount = c.getInt(c.getColumnIndexOrThrow("cookie_count")),
                    )
                )
            }
        }
        return out
    }

    fun insertProfile(id: String, site: String, name: String, hosts: List<String>, cookieCount: Int) {
        val hasActive = profiles.value.any { it.site == site && it.active }
        val v = ContentValues().apply {
            put("id", id); put("site", site); put("name", name)
            put("hosts", hosts.distinct().sorted().joinToString(","))
            put("created_at", System.currentTimeMillis())
            put("active", if (hasActive) 0 else 1)
            put("cookie_count", cookieCount)
        }
        writableDatabase.insert("profiles", null, v)
        refresh()
    }

    fun renameProfile(id: String, name: String) {
        val v = ContentValues().apply { put("name", name) }
        writableDatabase.update("profiles", v, "id=?", arrayOf(id))
        refresh()
    }

    fun setActiveProfile(id: String) {
        val row = profiles.value.firstOrNull { it.id == id } ?: return
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("UPDATE profiles SET active=0 WHERE site=?", arrayOf(row.site))
            db.execSQL("UPDATE profiles SET active=1 WHERE id=?", arrayOf(id))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        refresh()
    }

    fun deleteProfile(id: String) {
        val row = profiles.value.firstOrNull { it.id == id }
        val db = writableDatabase
        db.delete("profiles", "id=?", arrayOf(id))
        if (row != null && row.active) {
            val next = profiles.value.firstOrNull { it.site == row.site && it.id != id }
            if (next != null) db.execSQL("UPDATE profiles SET active=1 WHERE id=?", arrayOf(next.id))
        }
        refresh()
    }
}

private fun Cursor.str(column: String): String = getString(getColumnIndexOrThrow(column)) ?: ""
