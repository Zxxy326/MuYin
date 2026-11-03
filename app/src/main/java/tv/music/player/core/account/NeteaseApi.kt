package tv.music.player.core.account

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import tv.music.player.core.playback.DemoTrack
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.abs

data class AccountProfile(
    val provider: String,
    val uid: String,
    val nickname: String,
    val avatarUrl: String = "",
)

data class AccountPlaylist(
    val provider: String,
    val id: String,
    val name: String,
    val trackCount: Int,
    val coverUrl: String = "",
)

data class QrSession(val key: String, val url: String)

sealed interface QrPollResult {
    data object Waiting : QrPollResult
    data object Scanned : QrPollResult
    data object Expired : QrPollResult
    data class Success(
        val cookies: Map<String, String>,
        val nickname: String,
        val avatarUrl: String,
        val uid: String,
    ) : QrPollResult
    data class Failed(val message: String) : QrPollResult
}

/** 网易云音乐登录、账号与歌单接口。只在应用进程内请求，不记录 Cookie。 */
class NeteaseApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun createQr(): QrSession = withContext(Dispatchers.IO) {
        val url = "https://music.163.com/api/login/qrcode/unikey?type=1".toHttpUrl()
        val json = getJson(url, emptyMap())
        val code = json.optInt("code", -1)
        if (code != 200) throw IOException("网易云二维码接口返回 $code")
        val key = json.optString("unikey").trim()
        if (key.isBlank()) throw IOException("网易云没有返回二维码 key")
        QrSession(key, "https://music.163.com/login?codekey=${URLEncoder.encode(key, "UTF-8")}")
    }

    suspend fun pollQr(key: String): QrPollResult = withContext(Dispatchers.IO) {
        val url = "https://music.163.com/api/login/qrcode/client/login".toHttpUrl()
            .newBuilder()
            .addQueryParameter("type", "1")
            .addQueryParameter("key", key)
            .build()
        val response = execute(url, emptyMap())
        response.use { resp ->
            val body = resp.body.string()
            val json = runCatching { JSONObject(body) }.getOrElse {
                return@withContext QrPollResult.Failed("网易云返回了无法解析的响应")
            }
            when (json.optInt("code", -1)) {
                800 -> QrPollResult.Expired
                801 -> QrPollResult.Waiting
                802 -> QrPollResult.Scanned
                803 -> {
                    val cookies = cookiesFrom(resp.headers.values("Set-Cookie"))
                    if (cookies["MUSIC_U"].isNullOrBlank()) {
                        QrPollResult.Failed("登录成功但未获取到会话 Cookie")
                    } else {
                        QrPollResult.Success(
                            cookies = cookies,
                            nickname = json.optString("nickname"),
                            avatarUrl = json.optString("avatarUrl"),
                            uid = json.optLong("userId", 0L).takeIf { it > 0 }?.toString().orEmpty(),
                        )
                    }
                }
                else -> {
                    val message = json.optString("message").ifBlank { "二维码状态 ${json.optInt("code")}" }
                    QrPollResult.Failed(message)
                }
            }
        }
    }

    suspend fun fetchProfile(cookies: Map<String, String>): AccountProfile = withContext(Dispatchers.IO) {
        val json = getJson("https://music.163.com/api/nuser/account/get".toHttpUrl(), cookies)
        val profile = json.optJSONObject("profile")
            ?: throw IOException("未获取到网易云账号信息，Cookie 可能已失效")
        val uid = profile.optLong("userId", 0L)
        if (uid <= 0L) throw IOException("网易云账号信息缺少 userId")
        AccountProfile(
            provider = PROVIDER,
            uid = uid.toString(),
            nickname = profile.optString("nickname").ifBlank { "网易云用户" },
            avatarUrl = profile.optString("avatarUrl"),
        )
    }

    suspend fun fetchPlaylists(cookies: Map<String, String>, uid: String): List<AccountPlaylist> =
        withContext(Dispatchers.IO) {
            val url = "https://music.163.com/api/user/playlist".toHttpUrl()
                .newBuilder()
                .addQueryParameter("uid", uid)
                .addQueryParameter("limit", "1000")
                .addQueryParameter("offset", "0")
                .addQueryParameter("includeVideo", "true")
                .build()
            val json = getJson(url, cookies)
            val array = json.optJSONArray("playlist") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optLong("id", 0L)
                    if (id <= 0L) continue
                    val name = item.optString("name").trim()
                    if (name.isBlank()) continue
                    add(
                        AccountPlaylist(
                            provider = PROVIDER,
                            id = id.toString(),
                            name = name,
                            trackCount = item.optInt("trackCount", 0),
                            coverUrl = httpsUrl(item.optString("coverImgUrl")),
                        )
                    )
                }
            }
        }

    suspend fun fetchPlaylistTracks(
        cookies: Map<String, String>,
        playlistId: String,
        fallbackName: String,
    ): List<DemoTrack> = withContext(Dispatchers.IO) {
        val url = "https://music.163.com/api/v6/playlist/detail".toHttpUrl()
            .newBuilder()
            .addQueryParameter("id", playlistId)
            .addQueryParameter("n", "1000")
            .addQueryParameter("s", "0")
            .build()
        val json = getJson(url, cookies)
        val playlist = json.optJSONObject("playlist")
            ?: throw IOException("没有找到这个歌单")
        val tracks = playlist.optJSONArray("tracks")
        val result = mutableListOf<DemoTrack>()
        val seen = mutableSetOf<Long>()
        if (tracks != null) {
            for (i in 0 until tracks.length()) {
                val track = songToTrack(tracks.optJSONObject(i) ?: continue) ?: continue
                if (seen.add(track.onlineSongNumericId)) result += track
            }
        }
        if (result.isEmpty()) {
            val ids = playlist.optJSONArray("trackIds") ?: JSONArray()
            val chunks = mutableListOf<Long>()
            for (i in 0 until ids.length()) {
                val id = ids.optJSONObject(i)?.optLong("id", 0L) ?: 0L
                if (id > 0L) chunks += id
            }
            chunks.chunked(200).forEach { group ->
                val idsParam = group.joinToString(",", "[", "]")
                val detailUrl = "https://music.163.com/api/song/detail".toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("ids", idsParam)
                    .build()
                val detail = runCatching { getJson(detailUrl, cookies) }.getOrNull() ?: return@forEach
                val songs = detail.optJSONArray("songs") ?: return@forEach
                for (i in 0 until songs.length()) {
                    val track = songToTrack(songs.optJSONObject(i) ?: continue) ?: continue
                    if (seen.add(track.onlineSongNumericId)) result += track
                }
            }
        }
        if (result.isEmpty()) {
            throw IOException("「$fallbackName」没有可播放的歌曲")
        }
        result
    }

    private fun songToTrack(song: JSONObject): DemoTrack? {
        val songId = song.optLong("id", 0L)
        if (songId <= 0L) return null
        val title = song.optString("name").trim().ifBlank { return null }
        val artists = song.optJSONArray("ar") ?: song.optJSONArray("artists")
        val artist = buildList {
            if (artists != null) {
                for (i in 0 until artists.length()) {
                    val name = artists.optJSONObject(i)?.optString("name").orEmpty()
                    if (name.isNotBlank()) add(name)
                }
            }
        }.joinToString(", ").ifBlank { "未知歌手" }
        val albumObj = song.optJSONObject("al") ?: song.optJSONObject("album")
        val album = albumObj?.optString("name").orEmpty()
        val cover = httpsUrl(albumObj?.optString("picUrl").orEmpty())
        val duration = song.optLong("dt", song.optLong("duration", 0L)).coerceAtLeast(0L)
        val hash = abs(songId.hashCode()).coerceAtLeast(1)
        return DemoTrack(
            id = -hash,
            title = title,
            artist = artist,
            album = album,
            durationMs = duration,
            colorSeed = hash % 98,
            coverUrl = cover,
            onlineSource = "wy",
            onlineSongId = songId.toString(),
            onlineSongNumericId = songId,
        )
    }

    private fun httpsUrl(value: String): String {
        val url = value.trim()
        return if (url.startsWith("http://")) "https://" + url.removePrefix("http://") else url
    }

    private fun getJson(url: HttpUrl, cookies: Map<String, String>): JSONObject {
        val response = execute(url, cookies)
        response.use { resp ->
            val body = resp.body.string()
            return runCatching { JSONObject(body) }.getOrElse {
                throw IOException("网易云返回了无法解析的响应")
            }
        }
    }

    private fun execute(url: HttpUrl, cookies: Map<String, String>) =
        client.newCall(
            Request.Builder()
                .url(url)
                .header("Cookie", buildCookieHeader(cookies))
                .header("Referer", "https://music.163.com/")
                .header("User-Agent", UA)
                .get()
                .build()
        ).execute().let { response ->
            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                throw IOException("网易云请求失败：HTTP $code")
            }
            response
        }

    private fun buildCookieHeader(cookies: Map<String, String>): String {
        val parts = mutableListOf("os=pc", "appver=8.9.70")
        cookies.forEach { (name, value) ->
            if (name.isNotBlank() && value.isNotBlank()) parts += "$name=$value"
        }
        return parts.joinToString("; ")
    }

    private fun cookiesFrom(setCookies: List<String>): Map<String, String> {
        val result = linkedMapOf<String, String>()
        setCookies.forEach { raw ->
            val pair = raw.substringBefore(';').trim()
            val index = pair.indexOf('=')
            if (index <= 0) return@forEach
            val name = pair.substring(0, index).trim()
            val value = pair.substring(index + 1).trim()
            if (name.isNotBlank() && value.isNotBlank()) result[name] = value
        }
        return result
    }

    companion object {
        const val PROVIDER = "netease"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
