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
import tv.music.player.core.playback.DemoTrack
import tv.music.player.core.playback.PlaybackController
import tv.music.player.core.playback.PlaybackService
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

    private suspend fun resolveStreamUrl(track: DemoTrack, preferredQuality: String?): String? {
        if (!track.isOnline) return null
        if (!lxEngine.ready()) return null
        val quality = settings.settings.first().quality.lxQuality
        val cacheKey = "${track.onlineSource}:${track.onlineSongId}:$quality"
        val now = android.os.SystemClock.elapsedRealtime()
        streamUrlCache[cacheKey]?.let { (url, at) ->
            if (now - at < 4 * 60_000L) return url
        }
        val info = mapOf(
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
        val candidates = if (preferredQuality != null) {
            listOf(preferredQuality, "flac", "320k", "128k")
        } else {
            when (quality) {
                "flac", "320k" -> listOf("flac", "320k", "128k")
                else -> listOf("128k", "320k", "flac")
            }
        }
        for (candidate in candidates.distinct()) {
            val url = lxEngine.getPlayUrl(track.onlineSource, info, candidate)
            if (!url.isNullOrBlank() && isPlayable(url)) {
                val stamp = android.os.SystemClock.elapsedRealtime()
                streamUrlCache.entries.removeAll { stamp - it.value.second > 5 * 60_000L }
                streamUrlCache[cacheKey] = url to stamp
                return url
            }
        }
        return null
    }

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
            response.body.byteStream().use { it.read(ByteArray(2048)) > 0 }
        }
    }.getOrDefault(false)

    private companion object {
        const val UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
