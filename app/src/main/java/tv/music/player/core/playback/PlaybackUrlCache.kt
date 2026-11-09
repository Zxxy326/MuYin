package tv.music.player.core.playback

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * 播放链接持久化缓存（对齐上游 v1.7.2）：按 songKey|source|quality 缓存 15 分钟，
 * 进程重启仍有效；不缓存 127.0.0.1/localhost 代理地址。
 */
class PlaybackUrlCache(context: Context, private val ttlMs: Long = 15 * 60_000L) {
    private val file = File(context.cacheDir, "playback_urls.json")
    private val lock = Any()
    private val entries = LinkedHashMap<String, Pair<String, Long>>()

    init {
        runCatching {
            val root = JSONObject(file.readText())
            val now = System.currentTimeMillis()
            root.keys().forEach { key ->
                val obj = root.optJSONObject(key) ?: return@forEach
                val url = obj.optString("url")
                val at = obj.optLong("at")
                if (url.isNotBlank() && now - at in 0..ttlMs) entries[key] = url to at
            }
        }.onFailure { Log.w(TAG, "load cache failed: ${it.message}") }
    }

    fun get(songKey: String, source: String, quality: String): String? = synchronized(lock) {
        val key = key(songKey, source, quality)
        val (url, at) = entries[key] ?: return null
        if (System.currentTimeMillis() - at > ttlMs) {
            entries.remove(key)
            return null
        }
        url
    }

    fun put(songKey: String, source: String, quality: String, url: String) {
        if (!StreamUrlValidator.isCacheable(url)) return
        synchronized(lock) {
            entries[key(songKey, source, quality)] = url to System.currentTimeMillis()
            while (entries.size > MAX_ENTRIES) entries.remove(entries.keys.first())
            persist()
        }
    }

    fun remove(songKey: String, source: String, quality: String) {
        synchronized(lock) {
            entries.remove(key(songKey, source, quality))
            persist()
        }
    }

    private fun key(songKey: String, source: String, quality: String) = "$songKey|$source|$quality"

    private fun persist() {
        runCatching {
            val root = JSONObject()
            val now = System.currentTimeMillis()
            entries.forEach { (k, v) ->
                if (now - v.second <= ttlMs) root.put(k, JSONObject().put("url", v.first).put("at", v.second))
            }
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(root.toString())
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        }.onFailure { Log.w(TAG, "persist cache failed: ${it.message}") }
    }

    private companion object {
        const val TAG = "PlaybackUrlCache"
        const val MAX_ENTRIES = 300
    }
}