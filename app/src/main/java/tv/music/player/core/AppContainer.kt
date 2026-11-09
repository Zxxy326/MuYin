package tv.music.player.core

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.launch
import tv.music.player.core.account.AccountStore
import tv.music.player.core.charts.ChartRepository
import tv.music.player.core.companion.CompanionBridge
import tv.music.player.core.companion.CompanionServer
import tv.music.player.core.download.DownloadStore
import tv.music.player.core.library.LibraryStore
import tv.music.player.core.local.LocalLibraryStore
import tv.music.player.core.lx.LxFileStore
import tv.music.player.core.lx.LxJsEngine
import tv.music.player.core.lyrics.LyricsDiskCache
import tv.music.player.core.lyrics.LyricsMatchStore
import tv.music.player.core.lyrics.LyricsRepository
import tv.music.player.core.lyrics.SourceLyrics
import tv.music.player.core.playback.DemoTrack
import tv.music.player.core.playback.PlaybackController
import tv.music.player.core.playback.PlaybackService
import tv.music.player.core.playback.PlaybackUrlCache
import tv.music.player.core.playback.StreamUrlValidator
import tv.music.player.core.search.OnlineSearchApi
import tv.music.player.core.search.SearchHistoryStore
import tv.music.player.core.settings.SettingsRepository
import tv.music.player.core.source.SourceMarketStore
import tv.music.player.core.smb.SmbConfigStore
import tv.music.player.core.smb.SmbDataSource
import tv.music.player.core.smb.SmbRepository
import tv.music.player.core.update.UpdateStore
import tv.music.player.core.settings.lxQuality
import androidx.media3.datasource.DataSource
import java.io.File
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext
    val settings = SettingsRepository(appContext)
    @Volatile
    private var latestSettings = tv.music.player.core.settings.AppSettings()

    val lxFileStore = LxFileStore(appContext)
    val lxEngine = LxJsEngine(lxFileStore)
    val onlineSearch = OnlineSearchApi()
    val charts = ChartRepository()
    val searchHistory = SearchHistoryStore(appContext)
    val lyrics = LyricsRepository(
        context = appContext,
        diskCache = LyricsDiskCache(File(appContext.cacheDir, "lyrics")),
        matchStore = LyricsMatchStore(appContext),
        searchApi = onlineSearch,
        sourceLyricsProvider = { track ->
            if (!track.isOnline) {
                null
            } else {
                val quality = settings.settings.first().quality.lxQuality
                lxEngine.getLyric(track.onlineSource, songInfo(track), quality)?.let { lyric ->
                    SourceLyrics(
                        lrc = lyric.lyric,
                        translation = lyric.tlyric,
                        romanization = lyric.rlyric,
                    )
                }
            }
        },
    )

    val library = LibraryStore(appContext)
    val localLibrary = LocalLibraryStore(appContext)
    val sourceMarket = SourceMarketStore(appContext, lxFileStore, lxEngine)
    val downloads = DownloadStore(appContext) { track ->
        val quality = settings.settings.first().quality.lxQuality
        resolveStreamUrl(track, quality)
    }
    val smbConfig = SmbConfigStore(appContext)
    val smbRepository = SmbRepository()
    val smbFactory = DataSource.Factory { SmbDataSource(smbConfig, smbRepository) }
    val playback = PlaybackController(
        context = appContext,
        resolveUrl = ::resolveStreamUrl,
        localPath = downloads::pathFor,
        onTrackStarted = library::recordPlayed,
        smbFactory = smbFactory,
        settingsProvider = { latestSettings },
        sourcesProvider = { availableSources() },
    )
    val account = AccountStore(appContext)
    val companionBridge = CompanionBridge()
    val companion = CompanionServer(appContext, playback, companionBridge)
    val updates = UpdateStore(appContext)

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null

    fun connectMediaSession() {
        if (mediaControllerFuture != null) return
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        mediaControllerFuture = MediaController.Builder(appContext, token).buildAsync()
    }

    init {
        appScope.launch {
            val ok = lxEngine.ready()
            Log.i(
                "TVMusicLx",
                "ready=$ok scripts=${lxEngine.scriptInfos().size} sources=${lxEngine.getSources().keys} " +
                    "error=${lxEngine.lastError}",
            )
        }
        appScope.launch {
            settings.settings.collectLatest { current ->
                latestSettings = current
                if (current.companionEnabled) companion.start() else companion.stop()
            }
        }
        appScope.launch {
            delay(2_500L)
            updates.autoCheckOnLaunch()
        }
    }

    private val streamUrlCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long>>()
    private val failedScriptsByTrack = java.util.concurrent.ConcurrentHashMap<String, MutableSet<String>>()
    private val lastScriptByTrack = java.util.concurrent.ConcurrentHashMap<String, String>()
    val playbackUrlCache = PlaybackUrlCache(appContext)

    /** 供播放器「选择音源」列出：JS 注册源 + 内置兜底源。 */
    fun availableSources(): List<String> =
        (lxEngine.getSources().keys + listOf("wy", "tx")).distinct()

    /** 封面缺失时用 JS 音源 pic 动作补全（对齐上游 v1.7.2）。 */
    suspend fun resolvePic(track: DemoTrack): String? {
        if (!track.isOnline) return null
        val quality = settings.settings.first().quality.lxQuality
        return runCatching { lxEngine.getPic(track.onlineSource, songInfo(track), quality) }.getOrNull()
    }

    /** 内置网易云兜底：官方 outer/url 直链（无需登录，VIP 曲目可能 404 由探测过滤）。 */
    private suspend fun resolveBuiltInWy(track: DemoTrack): String? {
        val id = track.onlineSongId.toLongOrNull() ?: return null
        if (id <= 0) return null
        val url = "https://music.163.com/song/media/outer/url?id=$id.mp3"
        return withContext(Dispatchers.IO) {
            if (StreamUrlValidator.isUsable("wy", url) && isPlayable(url)) url else null
        }
    }

    private fun songInfo(track: DemoTrack): Map<String, Any?> = mapOf(
        "id" to track.onlineSongId,
        "songmid" to track.onlineSongId,
        "songId" to track.onlineSongId,
        "musicid" to track.onlineSongNumericId,
        "hash" to track.onlineHash.ifBlank { track.onlineSongId },
        "name" to track.title,
        "singer" to track.artist,
        "artist" to track.artist,
        "album" to track.album,
        "albumName" to track.album,
        "albumMid" to track.onlineAlbumMid,
        "strMediaMid" to track.onlineStrMediaMid,
        "interval" to track.durationMs / 1000L,
        "duration" to track.durationMs / 1000L,
        "pic" to track.coverUrl,
        "cover" to track.coverUrl,
    )

    private suspend fun resolveStreamUrl(
        track: DemoTrack,
        preferredQuality: String?,
        attempt: Int = 0,
        preferredSource: String? = null,
        forceRefresh: Boolean = false,
    ): String? {
        if (!track.isOnline) return null
        if (!lxEngine.ready()) return null
        val quality = settings.settings.first().quality.lxQuality
        val qualityKey = preferredQuality ?: quality
        val songKey = "${track.onlineSource}:${track.onlineSongId}"
        val cacheKey = "$songKey:$qualityKey"
        val trackKey = songKey
        val now = android.os.SystemClock.elapsedRealtime()
        val headSource = preferredSource?.takeIf { it.isNotBlank() } ?: track.onlineSource
        if (!forceRefresh && attempt == 0) {
            playbackUrlCache.get(songKey, headSource, qualityKey)?.let { return it }
            streamUrlCache[cacheKey]?.let { (url, at) ->
                if (now - at < 4 * 60_000L) return url
            }
            failedScriptsByTrack.remove(trackKey)
        } else {
            streamUrlCache.remove(cacheKey)
            if (forceRefresh) playbackUrlCache.remove(songKey, headSource, qualityKey)
            lastScriptByTrack[trackKey]?.let { file ->
                failedScriptsByTrack.getOrPut(trackKey) { java.util.concurrent.ConcurrentHashMap.newKeySet() } += file
            }
            if (attempt >= 3) failedScriptsByTrack.remove(trackKey)
        }
        val info = songInfo(track)
        val candidates = if (preferredQuality != null) {
            listOf(preferredQuality, "flac", "320k", "128k")
        } else {
            when (quality) {
                "flac", "320k" -> listOf("flac", "320k", "128k")
                else -> listOf("128k", "320k", "flac")
            }
        }
        val excluded = failedScriptsByTrack[trackKey].orEmpty()
        val sources = listOfNotNull(preferredSource?.takeIf { it.isNotBlank() }, track.onlineSource).distinct()
        for (source in sources) {
            for (candidate in candidates.distinct()) {
                val resolved = lxEngine.getPlayUrlWithOrigin(
                    source = source,
                    songInfo = info,
                    quality = candidate,
                    excludeFiles = excluded,
                    validate = { url ->
                        StreamUrlValidator.isUsable(source, url) &&
                            withContext(Dispatchers.IO) { isPlayable(url) }
                    },
                )
                if (resolved != null) {
                    rememberUrl(cacheKey, songKey, source, qualityKey, resolved.url)
                    lastScriptByTrack[trackKey] = resolved.fileName
                    failedScriptsByTrack.remove(trackKey)
                    return resolved.url
                }
                if (source == "wy") {
                    resolveBuiltInWy(track)?.let { url ->
                        rememberUrl(cacheKey, songKey, "wy", qualityKey, url)
                        return url
                    }
                }
            }
        }
        if (attempt >= 1) {
            crossSourceFallback(track, preferredQuality)?.let { url ->
                val stamp = android.os.SystemClock.elapsedRealtime()
                streamUrlCache[cacheKey] = url to stamp
                return url
            }
        }
        return null
    }

    private fun rememberUrl(cacheKey: String, songKey: String, source: String, quality: String, url: String) {
        val stamp = android.os.SystemClock.elapsedRealtime()
        streamUrlCache.entries.removeAll { stamp - it.value.second > 5 * 60_000L }
        streamUrlCache[cacheKey] = url to stamp
        playbackUrlCache.put(songKey, source, quality, url)
    }

    /**
     * 本平台脚本全部失败时，按歌名/歌手到其它平台搜索同名歌曲取流。
     * 只在重试阶段调用，避免正常点歌时拖慢解析。
     */
    private suspend fun crossSourceFallback(track: DemoTrack, preferredQuality: String?): String? {
        val title = track.title.trim()
        if (title.isBlank()) return null
        val keyword = listOf(title, track.artist.trim()).filter { it.isNotBlank() }.joinToString(" ")
        val quality = preferredQuality ?: settings.settings.first().quality.lxQuality
        val qualityChain = when (quality) {
            "flac" -> listOf("flac", "320k", "128k")
            "320k" -> listOf("320k", "128k", "flac")
            else -> listOf("128k", "320k", "flac")
        }
        val platforms = listOf("tx", "wy", "kw", "kg", "mg").filter { it != track.onlineSource }
        for (source in platforms) {
            val result = withTimeoutOrNull(8_000L) {
                runCatching { lxEngine.search(keyword, source, 1, 8) }.getOrNull()
            } ?: continue
            val candidates = result.list.filter { matchesTrack(it, track) }
            for (item in candidates) {
                val info = mapOf(
                    "id" to item.id.ifBlank { item.songmid },
                    "songmid" to item.songmid.ifBlank { item.id },
                    "songId" to item.id,
                    "hash" to item.hash.ifBlank { item.songmid },
                    "name" to item.name,
                    "singer" to item.singer,
                    "artist" to item.singer,
                    "album" to item.albumName,
                    "albumName" to item.albumName,
                    "interval" to item.duration / 1000L,
                    "duration" to item.duration / 1000L,
                    "pic" to item.pic,
                    "cover" to item.pic,
                )
                for (q in qualityChain) {
                    val resolved = lxEngine.getPlayUrlWithOrigin(
                        source = source,
                        songInfo = info,
                        quality = q,
                        validate = { url ->
                            StreamUrlValidator.isUsable(source, url) &&
                                withContext(Dispatchers.IO) { isPlayable(url) }
                        },
                    )
                    if (resolved != null) {
                        Log.i("TVMusicLx", "cross-source ${track.onlineSource}->$source '${item.name}/${item.singer}'")
                        return resolved.url
                    }
                    if (source == "wy") {
                        val id = item.id.toLongOrNull() ?: item.songmid.toLongOrNull()
                        if (id != null && id > 0) {
                            val url = "https://music.163.com/song/media/outer/url?id=$id.mp3"
                            if (StreamUrlValidator.isUsable("wy", url) && withContext(Dispatchers.IO) { isPlayable(url) }) {
                                return url
                            }
                        }
                    }
                }
            }
        }
        return null
    }

    private fun matchesTrack(item: tv.music.player.core.lx.LxSongInfo, track: DemoTrack): Boolean {
        val a = normalizeQuery(item.name)
        val b = normalizeQuery(track.title)
        if (a.isBlank() || b.isBlank()) return false
        if (a != b && !a.contains(b) && !b.contains(a)) return false
        val x = normalizeQuery(item.singer)
        val y = normalizeQuery(track.artist)
        if (!(y.isBlank() || x.isBlank() || x.contains(y) || y.contains(x))) return false
        // 同曲校验：时长偏差过大视为不同版本（Live/剪辑），避免播错歌
        if (item.duration > 0 && track.durationMs > 0 &&
            kotlin.math.abs(item.duration - track.durationMs) > 20_000L
        ) return false
        return true
    }

    private fun normalizeQuery(text: String): String =
        text.lowercase()
            .replace(Regex("[\\(（\\[【].*?[\\)）\\]】]"), "")
            .replace(Regex("[\\s·・,，.。-]"), "")

    private fun isPlayable(url: String): Boolean = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-2047")
            .header("User-Agent", UA)
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w("TVMusicLx", "stream probe ${response.code} ${url.take(100)}")
                return false
            }
            val type = response.header("Content-Type").orEmpty()
            val looksLikeMedia = type.startsWith("audio/") ||
                type.startsWith("video/") ||
                type.contains("octet-stream") ||
                type.contains("mpegurl") ||
                type.isBlank()
            if (!looksLikeMedia) {
                Log.w("TVMusicLx", "stream probe type=$type ${url.take(100)}")
                return false
            }
            val head = ByteArray(64)
            var size = 0
            response.body.byteStream().use { stream ->
                while (size < head.size) {
                    val read = stream.read(head, size, head.size - size)
                    if (read < 0) break
                    size += read
                }
            }
            val sniffed = looksLikeAudio(head, size)
            if (!sniffed) {
                Log.w(
                    "TVMusicLx",
                    "stream probe unrecognized body ${head.take(size).joinToString("") { "%02x".format(it) }} ${url.take(100)}",
                )
            }
            sniffed
        }
    }.getOrDefault(false)

    /** 只放行能识别出音频/容器头的响应，避免把错误页或加密文件交给播放器。 */
    private fun looksLikeAudio(bytes: ByteArray, size: Int): Boolean {
        if (size < 4) return false
        fun ascii(offset: Int, text: String): Boolean {
            if (offset + text.length > size) return false
            return text.indices.all { bytes[offset + it] == text[it].code.toByte() }
        }
        fun byte(offset: Int): Int = bytes[offset].toInt() and 0xFF
        return when {
            ascii(0, "ID3") || ascii(0, "fLaC") || ascii(0, "OggS") -> true
            ascii(0, "RIFF") || ascii(0, "FLV") || ascii(0, "MAC ") -> true
            ascii(0, "wvpk") || ascii(0, "ADIF") -> true
            ascii(4, "ftyp") -> true
            ascii(0, "#EXTM3U") -> true
            byte(0) == 0xFF && (byte(1) and 0xE0) == 0xE0 -> true
            byte(0) == 0x47 -> true
            byte(0) == 0x0B && byte(1) == 0x77 -> true
            byte(0) == 0x1A && byte(1) == 0x45 && byte(2) == 0xDF && byte(3) == 0xA3 -> true
            else -> false
        }
    }

    private companion object {
        const val UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
