package tv.music.player.core.lyrics

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * QQ 音乐歌词：musicu 接口一次性给出 LRC + 翻译，失败时退回旧版 fcg 接口。
 * 逐字 QRC 尚未启用，当前只取逐行时间轴。
 */
class QqLyricsApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .callTimeout(9, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun fetch(songmid: String, songId: Long = 0L): Lyrics? = withContext(Dispatchers.IO) {
        require(songmid.isNotBlank()) { "缺少歌曲 ID" }
        coroutineScope {
            val lineDeferred = async {
                runCatching { fetchMusicu(songmid) }
                    .onFailure { Log.w(TAG, "musicu lyric failed: ${it.message}") }
                    .getOrNull()
            }
            val qrcDeferred = if (songId > 0L) {
                async {
                    runCatching { fetchQrcXml(songId) }
                        .onFailure { Log.w(TAG, "qrc fetch failed: ${it.message}") }
                        .getOrNull()
                }
            } else {
                null
            }

            val line = lineDeferred.await()
            val qrcXml = qrcDeferred?.await()
            if (qrcXml != null) {
                val qrc = runCatching { QrcParser.parseXml(qrcXml, line) }
                    .onFailure { Log.w(TAG, "qrc parse failed: ${it.message}") }
                    .getOrNull()
                if (qrc != null && qrc.lines.isNotEmpty()) {
                    Log.d(TAG, "qrc mid=$songmid songId=$songId lines=${qrc.lines.size}")
                    return@coroutineScope qrc
                }
            }
            if (line != null && (line.lines.isNotEmpty() || line.instrumental)) {
                return@coroutineScope line
            }

            val legacy = runCatching { fetchLegacy(songmid) }
                .onFailure { Log.w(TAG, "legacy lyric failed: ${it.message}") }
                .getOrNull()
            if (legacy != null && legacy.lines.isNotEmpty()) return@coroutineScope legacy
            line
        }
    }

    private fun fetchQrcXml(songId: Long): String? {
        val url = "https://c.y.qq.com/qqmusic/fcgi-bin/lyric_download.fcg" +
            "?version=15&miniversion=82&lrctype=4&musicid=$songId"
        val request = Request.Builder()
            .url(url)
            .header("Referer", "https://y.qq.com/")
            .header("User-Agent", UA)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "qrc http ${response.code}")
                return null
            }
            val body = response.body.string()
            val cipher = QrcParser.extractCdataHex(body) ?: return null
            val xml = QrcParser.decryptAndInflate(cipher)
            return xml.takeIf { it.contains("LyricContent") }
        }
    }

    private fun fetchMusicu(songmid: String): Lyrics? {
        val body = JSONObject()
            .put("comm", JSONObject().put("ct", 11).put("cv", "1003006").put("v", "1003006").put("uin", "0"))
            .put(
                "req",
                JSONObject()
                    .put("module", "music.musichallSong.PlayLyricInfo")
                    .put("method", "GetPlayLyricInfo")
                    .put(
                        "param",
                        JSONObject()
                            .put("songMID", songmid)
                            .put("songID", 0)
                            .put("format", "json")
                            .put("inCharset", "utf8")
                            .put("outCharset", "utf8")
                            .put("trans", 1)
                            .put("roma", 1),
                    ),
            )
        val request = Request.Builder()
            .url("https://u.y.qq.com/cgi-bin/musicu.fcg")
            .header("Referer", "https://y.qq.com/")
            .header("User-Agent", UA)
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "musicu http ${response.code}")
                return null
            }
            val root = JSONObject(response.body.string())
            val data = root.optJSONObject("req")?.optJSONObject("data") ?: return null
            val lrc = data.decodeBase64("lyric")
            val trans = data.decodeBase64("trans")
            val roma = data.decodeBase64("roma")
            val lyrics = LrcParser.parse(lrc, trans, roma)
            Log.d(TAG, "musicu mid=$songmid lines=${lyrics.lines.size} instrumental=${lyrics.instrumental}")
            return lyrics
        }
    }

    private fun fetchLegacy(songmid: String): Lyrics? {
        val url = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg" +
            "?songmid=$songmid&format=json&nobase64=1&g_tk=5381&loginUin=0&hostUin=0" +
            "&inCharset=utf8&outCharset=utf-8&notice=0&platform=yqq.json&needNewCode=0"
        val request = Request.Builder()
            .url(url)
            .header("Referer", "https://y.qq.com/")
            .header("User-Agent", UA)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val root = JSONObject(response.body.string())
            if (root.optInt("retcode", -1) != 0) return null
            val lrc = root.optString("lyric")
            val trans = root.optString("trans")
            val lyrics = LrcParser.parse(lrc, trans)
            Log.d(TAG, "legacy mid=$songmid lines=${lyrics.lines.size} instrumental=${lyrics.instrumental}")
            return lyrics
        }
    }

    private fun JSONObject.decodeBase64(key: String): String {
        val raw = optString(key)
        if (raw.isBlank() || raw == "null") return ""
        return runCatching { String(Base64.decode(raw, Base64.DEFAULT), Charsets.UTF_8) }
            .getOrElse { raw }
    }

    private companion object {
        const val TAG = "TVMusicLyrics"
        const val UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
