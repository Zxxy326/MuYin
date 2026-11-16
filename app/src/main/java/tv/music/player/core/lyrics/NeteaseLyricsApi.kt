package tv.music.player.core.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 网易云歌词：按歌曲数字 ID 精确获取，避免走 QQ 搜索匹配带来的延迟与错配。 */
class NeteaseLyricsApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(songId: Long): Lyrics? = withContext(Dispatchers.IO) {
        val url = "https://music.163.com/api/song/lyric" +
            "?id=$songId&lv=-1&kv=-1&tv=-1&rv=-1"
        val request = Request.Builder()
            .url(url)
            .header("Referer", "https://music.163.com/")
            .header("User-Agent", UA)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("网易云歌词 HTTP ${response.code}")
            val root = JSONObject(response.body.string())
            if (root.optBoolean("nolyric") || root.optBoolean("uncollected")) return@withContext null
            val lrc = root.optJSONObject("lrc")?.optString("lyric").orEmpty()
            if (lrc.isBlank()) return@withContext null
            val translation = root.optJSONObject("tlyric")?.optString("lyric")
            val romanization = root.optJSONObject("romalrc")?.optString("lyric")
                ?: root.optJSONObject("klyric")?.optString("lyric")
            val parsed = LrcParser.parse(lrc, translation, romanization)
            when {
                parsed.instrumental -> parsed
                parsed.lines.isEmpty() -> null
                else -> parsed.copy(sourceLabel = "网易云歌词")
            }
        }
    }

    private companion object {
        const val UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
