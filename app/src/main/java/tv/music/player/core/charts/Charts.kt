package tv.music.player.core.charts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import tv.music.player.core.account.NeteaseApi
import tv.music.player.core.playback.DemoTrack
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.abs

data class ChartDef(
    val id: String,
    val name: String,
    val provider: String,
    val sourceId: String,
    val subtitle: String,
)

object Charts {
    val all: List<ChartDef> = listOf(
        ChartDef("wy-19723756", "网易云飙升榜", "wy", "19723756", "网易云音乐 · 每日更新"),
        ChartDef("wy-3778678", "网易云热歌榜", "wy", "3778678", "网易云音乐 · 每日更新"),
        ChartDef("tx-26", "QQ 热歌榜", "tx", "26", "QQ 音乐 · 每日更新"),
        ChartDef("tx-62", "QQ 飙升榜", "tx", "62", "QQ 音乐 · 每日更新"),
    )

    fun byId(id: String): ChartDef? = all.firstOrNull { it.id == id }
}

data class ChartResult(
    val tracks: List<DemoTrack>,
    val updateTime: String,
    val total: Int,
)

class ChartRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val netease = NeteaseApi()

    suspend fun load(chart: ChartDef): ChartResult = withContext(Dispatchers.IO) {
        when (chart.provider) {
            "tx" -> loadQq(chart)
            "wy" -> loadNetease(chart)
            else -> throw IOException("不支持的榜单来源")
        }
    }

    private suspend fun loadNetease(chart: ChartDef): ChartResult {
        val tracks = netease.fetchPlaylistTracks(emptyMap(), chart.sourceId, chart.name)
        return ChartResult(tracks, "", tracks.size)
    }

    private fun loadQq(chart: ChartDef): ChartResult {
        val topId = chart.sourceId.toIntOrNull() ?: throw IOException("榜单参数错误")
        val url = TOP_LIST_URL.toHttpUrl().newBuilder()
            .addQueryParameter("topid", topId.toString())
            .addQueryParameter("format", "json")
            .addQueryParameter("inCharset", "utf8")
            .addQueryParameter("outCharset", "utf-8")
            .addQueryParameter("notice", "0")
            .addQueryParameter("platform", "yqq.json")
            .addQueryParameter("needNewCode", "0")
            .addQueryParameter("tpl", "3")
            .addQueryParameter("page", "detail")
            .addQueryParameter("type", "top")
            .addQueryParameter("song_begin", "0")
            .addQueryParameter("song_num", LIST_SIZE.toString())
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Referer", "https://y.qq.com/")
            .header("User-Agent", UA)
            .get()
            .build()
        val text = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("榜单接口 HTTP ${response.code}")
            response.body.string()
        }
        val root = runCatching { JSONObject(text) }.getOrElse {
            throw IOException("榜单接口返回了无法解析的数据")
        }
        val code = root.optInt("code", -1)
        if (code != 0) throw IOException("榜单接口错误（$code）")
        val list = root.optJSONArray("songlist") ?: JSONArray()
        val tracks = buildList {
            for (i in 0 until list.length()) {
                val item = list.optJSONObject(i)?.optJSONObject("data") ?: continue
                val mid = item.optString("songmid").trim()
                if (mid.isBlank()) continue
                val songId = item.optLong("songid", 0L)
                val albumMid = item.optString("albummid").trim()
                val mediaMid = item.optString("strMediaMid").trim()
                val artists = buildList {
                    val singers = item.optJSONArray("singer") ?: JSONArray()
                    for (j in 0 until singers.length()) {
                        val name = singers.optJSONObject(j)?.optString("name").orEmpty()
                        if (name.isNotBlank()) add(name)
                    }
                }.joinToString(", ").ifBlank { "未知歌手" }
                add(
                    DemoTrack(
                        id = -abs(mid.hashCode()).coerceAtLeast(1),
                        title = item.optString("songname").trim().ifBlank { "未知歌曲" },
                        artist = artists,
                        album = item.optString("albumname").trim(),
                        durationMs = item.optLong("interval", 0L) * 1000L,
                        colorSeed = (mid.hashCode() and 0x7fffffff) % 98,
                        coverUrl = if (albumMid.isBlank()) "" else
                            "https://y.gtimg.cn/music/photo_new/T002R300x300M000$albumMid.jpg",
                        onlineSource = "tx",
                        onlineSongId = mid,
                        onlineHash = mediaMid,
                        onlineAlbumMid = albumMid,
                        onlineStrMediaMid = mediaMid,
                        onlineSongNumericId = songId,
                    )
                )
            }
        }.distinctBy { it.onlineSongId }
        if (tracks.isEmpty()) throw IOException("榜单暂无歌曲")
        return ChartResult(
            tracks = tracks,
            updateTime = "",
            total = root.optInt("total_song_num", tracks.size),
        )
    }

    private companion object {
        const val TOP_LIST_URL = "https://c.y.qq.com/v8/fcg-bin/fcg_v8_toplist_cp.fcg"
        const val LIST_SIZE = 300
        const val UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
    }
}
