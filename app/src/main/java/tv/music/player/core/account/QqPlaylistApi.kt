package tv.music.player.core.account

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import tv.music.player.core.playback.DemoTrack
import tv.music.player.core.search.OnlineTrack
import tv.music.player.core.search.toDemoTrack
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * QQ 音乐歌单：沿用网页端 fcgi 接口，Cookie 里的 p_skey/skey 生成 g_tk，
 * 无需 musicu.fcg 的网页签名。Cookie 只在本机内存与加密存储中使用。
 */
class QqPlaylistApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchAccount(cookies: Map<String, String>): QqAccount = withContext(Dispatchers.IO) {
        val uin = cookieUin(cookies)
        if (uin.isBlank()) throw IOException("QQ Cookie 缺少 uin")
        val created = fetchCreated(cookies, uin)
        val collected = runCatching { fetchCollected(cookies, uin) }.getOrDefault(emptyList())
        QqAccount(
            profile = AccountProfile(
                provider = PROVIDER,
                uid = uin,
                nickname = created.nickname.ifBlank { "QQ 音乐用户" },
            ),
            playlists = (created.playlists + collected).distinctBy { it.id },
        )
    }

    suspend fun fetchPlaylistTracks(
        cookies: Map<String, String>,
        playlistId: String,
        fallbackName: String,
    ): List<DemoTrack> = withContext(Dispatchers.IO) {
        val gtk = gtk(cookies)
        val out = LinkedHashMap<String, DemoTrack>()
        var songBegin = 0
        var page = 0
        while (page < MAX_SONG_PAGES) {
            val url = "$CD_INFO_URL?type=1&json=1&utf8=1&onlysong=0" +
                "&disstid=$playlistId&song_begin=$songBegin&song_num=$SONG_PAGE_SIZE" +
                "&g_tk=$gtk&format=json&inCharset=utf-8&outCharset=utf-8&notice=0" +
                "&platform=yqq.json&needNewCode=0"
            val root = get(url, cookies)
            if (root.optInt("code", -1) != 0) throw IOException("QQ 歌单读取失败（${root.optInt("code")}）")
            val cd = root.optJSONArray("cdlist")?.optJSONObject(0) ?: break
            val songlist = cd.optJSONArray("songlist") ?: break
            val fetched = songlist.length()
            if (fetched == 0) break
            for (i in 0 until fetched) {
                val item = songlist.optJSONObject(i) ?: continue
                val track = item.toDemoTrack() ?: continue
                out[track.onlineSongId] = track
            }
            songBegin += fetched
            val total = cd.optInt("songnum", cd.optInt("total_song_num", -1))
            if (total > 0 && songBegin >= total) break
            if (fetched < SONG_PAGE_SIZE) break
            page += 1
        }
        if (out.isEmpty()) throw IOException("歌单「$fallbackName」没有可播放的歌曲")
        out.values.toList()
    }

    private fun fetchCreated(cookies: Map<String, String>, uin: String): CreatedResult {
        val gtk = gtk(cookies)
        val url = "$CREATED_URL?format=json&inCharset=utf-8&outCharset=utf-8&notice=0" +
            "&platform=yqq.json&needNewCode=1&uin=$uin&g_tk=$gtk&g_tk_new_20200303=$gtk" +
            "&hostuin=$uin&sin=0&size=100"
        val root = get(url, cookies)
        if (root.optInt("code", -1) != 0) throw IOException("QQ 歌单读取失败（${root.optInt("code")}）")
        val data = root.optJSONObject("data")
        val nickname = data?.optString("hostname").orEmpty().let(::decodeBase64IfNeeded)
        val array = data?.optJSONArray("disslist")
        val playlists = buildList {
            if (array != null) {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optLong("tid", item.optLong("dissid", 0L))
                    val name = decodeBase64IfNeeded(
                        item.optString("diss_name").ifBlank { item.optString("dissname") }
                    )
                    if (id <= 0L || name.isBlank()) continue
                    add(
                        AccountPlaylist(
                            provider = PROVIDER,
                            id = id.toString(),
                            name = name,
                            trackCount = item.optInt("song_cnt", item.optInt("songnum", 0)),
                            coverUrl = item.optString("diss_cover").ifBlank { item.optString("logo") },
                        )
                    )
                }
            }
        }
        return CreatedResult(nickname, playlists)
    }

    private fun fetchCollected(cookies: Map<String, String>, uin: String): List<AccountPlaylist> {
        val gtk = gtk(cookies)
        val url = "$COLLECTED_URL?format=json&inCharset=utf-8&outCharset=utf-8&notice=0" +
            "&platform=yqq&needNewCode=1&ct=20&uin=$uin&g_tk=$gtk&cid=205360956" +
            "&userid=$uin&reqtype=3&sin=0&ein=100"
        val root = get(url, cookies)
        if (root.optInt("code", -1) != 0) return emptyList()
        val array = root.optJSONObject("data")?.optJSONArray("cdlist") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optLong("dissid", 0L)
                val name = decodeBase64IfNeeded(item.optString("dissname"))
                if (id <= 0L || name.isBlank()) continue
                add(
                    AccountPlaylist(
                        provider = PROVIDER,
                        id = id.toString(),
                        name = name,
                        trackCount = item.optInt("songnum", 0),
                        coverUrl = item.optString("logo"),
                    )
                )
            }
        }
    }

    private fun get(url: String, cookies: Map<String, String>): JSONObject {
        val cookieHeader = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
        val request = Request.Builder()
            .url(url.toHttpUrl())
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Referer", "https://y.qq.com/")
            .header("Accept", "application/json")
            .apply { if (cookieHeader.isNotBlank()) header("Cookie", cookieHeader) }
            .build()
        val text = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("QQ 接口 HTTP ${response.code}")
            response.body.string()
        }
        return runCatching { JSONObject(text) }.getOrNull()
            ?: throw IOException("QQ 接口返回异常")
    }

    private fun cookieUin(cookies: Map<String, String>): String {
        val raw = cookies["qqmusic_uin"].orEmpty()
            .ifBlank { cookies["uin"].orEmpty() }
            .ifBlank { cookies["p_uin"].orEmpty() }
            .ifBlank { cookies["luin"].orEmpty() }
        return raw.filter { it.isDigit() }.trimStart('0').ifBlank { raw.filter { it.isDigit() } }
    }

    private fun gtk(cookies: Map<String, String>): Long {
        val skey = cookies["p_skey"].orEmpty().ifBlank { cookies["skey"].orEmpty() }
        var hash = 5381L
        for (c in skey) hash += (hash shl 5) + c.code.toLong()
        return hash and 0x7fffffffL
    }

    private fun JSONObject.toDemoTrack(): DemoTrack? {
        val mid = optString("songmid").ifBlank { optString("mid") }
        if (mid.isBlank()) return null
        val title = decodeBase64IfNeeded(optString("songname").ifBlank { optString("title") })
        if (title.isBlank()) return null
        val singers = mutableListOf<String>()
        optJSONArray("singer")?.let { array ->
            for (i in 0 until array.length()) {
                val singer = array.optJSONObject(i) ?: continue
                decodeBase64IfNeeded(singer.optString("name")).takeIf { it.isNotBlank() }?.let(singers::add)
            }
        }
        val albumObj = optJSONObject("album")
        val album = decodeBase64IfNeeded(
            optString("albumname").ifBlank { albumObj?.optString("name").orEmpty() }
        )
        val albumMid = optString("albummid").ifBlank { albumObj?.optString("mid").orEmpty() }
        val mediaMid = optString("strMediaMid")
            .ifBlank { optString("media_mid") }
            .ifBlank { optJSONObject("file")?.optString("media_mid").orEmpty() }
        val source = OnlineTrack(
            source = "qq",
            songmid = mid,
            title = title,
            artist = singers.joinToString("/").ifBlank { "未知歌手" },
            album = album,
            durationMs = optLong("interval") * 1000L,
            coverUrl = if (albumMid.isBlank()) "" else "https://y.gtimg.cn/music/photo_new/T002R300x300M000$albumMid.jpg",
            strMediaMid = mediaMid,
            albumMid = albumMid,
            songId = optLong("songid"),
        )
        return source.toDemoTrack()
    }

    private fun decodeBase64IfNeeded(input: String): String {
        if (input.isBlank()) return input
        if (!BASE64_PATTERN.matches(input) || input.length < 4 || input.length % 4 != 0) return input
        return runCatching {
            val text = String(Base64.decode(input, Base64.DEFAULT), Charsets.UTF_8)
            val readable = text.isNotBlank() &&
                !text.contains('\uFFFD') &&
                text.none { it.code < 0x20 && it != '\n' && it != '\t' }
            if (readable) text else input
        }.getOrDefault(input)
    }

    private data class CreatedResult(val nickname: String, val playlists: List<AccountPlaylist>)

    data class QqAccount(val profile: AccountProfile, val playlists: List<AccountPlaylist>)

    companion object {
        const val PROVIDER = "qq"
        private const val CREATED_URL = "https://c6.y.qq.com/rsc/fcgi-bin/fcg_user_created_diss"
        private const val COLLECTED_URL = "https://c.y.qq.com/fav/fcgi-bin/fcg_get_profile_order_asset.fcg"
        private const val CD_INFO_URL = "https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg"
        private const val SONG_PAGE_SIZE = 1000
        private const val MAX_SONG_PAGES = 8
        private val BASE64_PATTERN = Regex("^[A-Za-z0-9+/]+={0,2}$")
    }
}
