package tv.music.player.core.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import tv.music.player.core.settings.AppSettings
import tv.music.player.core.smb.PreferredDataSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PlayerSnapshot(
    val track: DemoTrack = DemoCatalog.tracks.first(),
    val queue: List<DemoTrack> = DemoCatalog.tracks,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val resolvedDurationMs: Long = 0L,
    val buffering: Boolean = false,
) {
    val durationMs: Long get() = if (resolvedDurationMs > 0L) resolvedDurationMs else track.durationMs
    val progress: Float
        get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

/**
 * 在线曲目经 LX 脚本取流后交给 ExoPlayer；M0 的演示曲目仍用计时器驱动。
 */
class PlaybackController(
    context: Context,
    private val resolveUrl: suspend (track: DemoTrack, preferredQuality: String?, attempt: Int) -> String?,
    private val localPath: (DemoTrack) -> String? = { null },
    private val onTrackStarted: (DemoTrack) -> Unit = {},
    smbFactory: DataSource.Factory? = null,
    private val settingsProvider: () -> AppSettings = { AppSettings() },
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlayerSnapshot())
    val state: StateFlow<PlayerSnapshot> = _state.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private var ticker: Job? = null
    private var resolveJob: Job? = null
    private var previewRetryDone = false
    private var autoRetryCount = 0

    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent(HTTP_UA)
        .setAllowCrossProtocolRedirects(true)
    private val dataSourceFactory: DataSource.Factory = if (smbFactory != null) {
        PreferredDataSourceFactory(appContext, httpDataSourceFactory, smbFactory)
    } else {
        DefaultDataSource.Factory(appContext, httpDataSourceFactory)
    }
    private val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
    private val renderersFactory = object : DefaultRenderersFactory(appContext) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean,
        ): AudioSink = DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf(SpectrumProcessor()))
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioOffloadSupportProvider { _, _ -> AudioOffloadSupport.DEFAULT_UNSUPPORTED }
            .build()
    }
    val player: ExoPlayer = ExoPlayer.Builder(appContext, mediaSourceFactory)
        .setRenderersFactory(renderersFactory)
        .build()

    init {
        applyAudioBehavior()
        player.setHandleAudioBecomingNoisy(true)
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val snapshot = _state.value
                if (snapshot.track.usesPlayer) {
                    _state.value = snapshot.copy(isPlaying = isPlaying)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val snapshot = _state.value
                if (!snapshot.track.usesPlayer) return
                when (playbackState) {
                    Player.STATE_BUFFERING ->
                        _state.value = snapshot.copy(buffering = true)
                    Player.STATE_READY -> {
                        val duration = player.duration.takeIf { it > 0L && it != C.TIME_UNSET }
                        if (snapshot.track.localUri.isBlank() && localFileFor(snapshot.track) == null && !previewRetryDone && duration != null &&
                            snapshot.track.durationMs > 0L && duration < snapshot.track.durationMs * 0.75
                        ) {
                            previewRetryDone = true
                            _errors.tryEmit("检测到试听片段，正在切换完整音源…")
                            retryWithFullQuality(snapshot.track)
                            return
                        }
                        _state.value = snapshot.copy(
                            buffering = false,
                            resolvedDurationMs = duration ?: snapshot.resolvedDurationMs,
                        )
                    }
                    Player.STATE_ENDED -> next()
                    else -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                resolveJob?.cancel()
                val snapshot = _state.value
                val track = snapshot.track
                val canRetry = track.isOnline && track.localUri.isBlank() &&
                    localFileFor(track) == null && autoRetryCount < MAX_AUTO_RETRIES
                if (canRetry) {
                    _state.value = snapshot.copy(isPlaying = false, buffering = true)
                    _errors.tryEmit("音源连接异常，正在自动换源…")
                    resolveJob = scope.launch {
                        while (autoRetryCount < MAX_AUTO_RETRIES && isActive) {
                            autoRetryCount += 1
                            // 换源时优先拿兼容性更好的低码率链接，避免再拿到同款异常容器
                            val retryQuality = if (autoRetryCount == 1) "128k" else "320k"
                            val url = withContext(Dispatchers.IO) {
                                resolveUrl(track, retryQuality, autoRetryCount)
                            }
                            if (_state.value.track.id != track.id) return@launch
                            if (!url.isNullOrBlank()) {
                                startMedia(track, url)
                                return@launch
                            }
                        }
                        _state.value = _state.value.copy(isPlaying = false, buffering = false)
                        _errors.tryEmit("播放失败：${error.errorCodeName}")
                    }
                    return
                }
                _state.value = snapshot.copy(isPlaying = false, buffering = false)
                _errors.tryEmit("播放失败：${error.errorCodeName}")
            }
        })
    }

    fun toggle() {
        val snapshot = _state.value
        if (snapshot.track.usesPlayer) {
            if (player.playbackState == Player.STATE_IDLE || player.mediaItemCount == 0) {
                play(snapshot.track, snapshot.queue)
            } else if (player.isPlaying) {
                pauseWithFade()
            } else {
                player.play()
                fadeIn()
            }
            return
        }
        val playing = !snapshot.isPlaying
        _state.value = snapshot.copy(isPlaying = playing)
        if (playing) startTicker() else stopTicker()
    }

    fun play(track: DemoTrack, queue: List<DemoTrack> = _state.value.queue) {
        resolveJob?.cancel()
        previewRetryDone = false
        autoRetryCount = 0
        stopTicker()
        val localFile = localFileFor(track)
        val localUri = track.localUri.takeIf { it.isNotBlank() }
        _state.value = _state.value.copy(
            track = track,
            queue = queue,
            positionMs = 0L,
            resolvedDurationMs = 0L,
            buffering = track.isOnline && localFile == null && localUri == null,
            isPlaying = false,
        )
        if (localUri != null) {
            startLocalUri(track, localUri)
            return
        }
        if (localFile != null) {
            startLocalFile(track, localFile)
            return
        }
        if (!track.isOnline) {
            player.stop()
            player.clearMediaItems()
            _state.value = _state.value.copy(isPlaying = true)
            startTicker()
            onTrackStarted(track)
            return
        }

        resolveJob = scope.launch {
            var url = withContext(Dispatchers.IO) { resolveUrl(track, null, 0) }
            if (url.isNullOrBlank()) {
                _state.value = _state.value.copy(buffering = true)
                _errors.tryEmit("音源解析失败，正在自动换源…")
                while (autoRetryCount < MAX_AUTO_RETRIES && url.isNullOrBlank() && isActive) {
                    autoRetryCount += 1
                    val retryQuality = if (autoRetryCount == 1) "128k" else "320k"
                    url = withContext(Dispatchers.IO) { resolveUrl(track, retryQuality, autoRetryCount) }
                }
            }
            if (_state.value.track.id != track.id) return@launch
            if (url.isNullOrBlank()) {
                _state.value = _state.value.copy(isPlaying = false, buffering = false)
                _errors.tryEmit("未找到可用音源，请换一首或检查音源脚本")
                return@launch
            }
            startMedia(track, url)
        }
    }

    private fun retryWithFullQuality(track: DemoTrack) {
        resolveJob?.cancel()
        resolveJob = scope.launch {
            val url = withContext(Dispatchers.IO) { resolveUrl(track, "flac", 0) }
            if (url.isNullOrBlank()) {
                _state.value = _state.value.copy(isPlaying = false, buffering = false)
                _errors.tryEmit("只找到试听片段，未找到完整音源")
                return@launch
            }
            startMedia(track, url)
        }
    }

    private fun startMedia(track: DemoTrack, url: String) {
        applyAudioBehavior()
        player.setMediaItem(buildMediaItem(track, url))
        player.prepare()
        player.playWhenReady = true
        fadeIn()
        _state.value = _state.value.copy(buffering = false)
        startPlayerTicker()
        onTrackStarted(track)
        prefetchNext(track)
    }

    private fun startLocalFile(track: DemoTrack, file: File) {
        startLocalUri(track, Uri.fromFile(file).toString())
    }

    private fun startLocalUri(track: DemoTrack, uri: String) {
        applyAudioBehavior()
        player.stop()
        player.clearMediaItems()
        player.setMediaItem(buildMediaItem(track, uri))
        player.prepare()
        player.playWhenReady = true
        fadeIn()
        _state.value = _state.value.copy(buffering = false, isPlaying = true)
        startPlayerTicker()
        onTrackStarted(track)
        prefetchNext(track)
    }

    private fun applyAudioBehavior() {
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            settingsProvider().audioFocus,
        )
    }

    private var fadeJob: Job? = null

    private fun fadeIn() {
        fadeJob?.cancel()
        if (!settingsProvider().fadeInOut) {
            player.volume = 1f
            return
        }
        fadeJob = scope.launch {
            player.volume = 0f
            val steps = 10
            repeat(steps) { index ->
                delay(30L)
                player.volume = (index + 1) / steps.toFloat()
            }
            player.volume = 1f
        }
    }

    private fun pauseWithFade() {
        fadeJob?.cancel()
        if (!settingsProvider().fadeInOut) {
            player.volume = 1f
            player.pause()
            return
        }
        fadeJob = scope.launch {
            val from = player.volume.coerceAtLeast(0.05f)
            val steps = 8
            for (index in 1..steps) {
                player.volume = from * (1f - index / steps.toFloat())
                delay(25L)
            }
            player.pause()
            player.volume = 1f
        }
    }

    private var prefetchJob: Job? = null

    private fun prefetchNext(track: DemoTrack) {
        if (!settingsProvider().gaplessPlayback) return
        val snapshot = _state.value
        if (snapshot.queue.size < 2) return
        val index = snapshot.queue.indexOfFirst {
            if (it.isOnline && track.isOnline) {
                it.onlineSource == track.onlineSource && it.onlineSongId == track.onlineSongId
            } else {
                it.id == track.id
            }
        }
        if (index < 0) return
        val next = snapshot.queue[(index + 1) % snapshot.queue.size]
        if (!next.isOnline) return
        prefetchJob?.cancel()
        prefetchJob = scope.launch(Dispatchers.IO) {
            runCatching { resolveUrl(next, null, 0) }
        }
    }

    private fun buildMediaItem(track: DemoTrack, uri: String): MediaItem =
        MediaItem.Builder()
            .setUri(uri)
            .setMediaId(track.onlineSongId.ifBlank { "local:${track.id}" })
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .apply {
                        if (track.coverUrl.isNotBlank()) setArtworkUri(Uri.parse(track.coverUrl))
                    }
                    .build()
            )
            .build()

    private fun localFileFor(track: DemoTrack): File? =
        localPath(track)?.let { path -> runCatching { File(path) }.getOrNull() }
            ?.takeIf { it.isFile && it.length() > 0L }

    fun next() {
        val snapshot = _state.value
        if (snapshot.queue.isEmpty()) return
        val index = snapshot.queue.indexOfFirst { it.id == snapshot.track.id }
        val nextIndex = if (index < 0) 0 else (index + 1) % snapshot.queue.size
        play(snapshot.queue[nextIndex], snapshot.queue)
    }

    fun previous() {
        val snapshot = _state.value
        if (snapshot.positionMs > 4_000L) {
            if (snapshot.track.usesPlayer) player.seekTo(0L)
            _state.value = snapshot.copy(positionMs = 0L)
            return
        }
        if (snapshot.queue.isEmpty()) return
        val index = snapshot.queue.indexOfFirst { it.id == snapshot.track.id }
        val prevIndex = if (index <= 0) snapshot.queue.lastIndex else index - 1
        play(snapshot.queue[prevIndex], snapshot.queue)
    }

    fun seekBy(deltaMs: Long) {
        val snapshot = _state.value
        val target = (snapshot.positionMs + deltaMs).coerceIn(0L, snapshot.durationMs)
        if (snapshot.track.usesPlayer) {
            player.seekTo(target)
        }
        _state.value = snapshot.copy(positionMs = target)
    }

    fun seekTo(positionMs: Long) {
        val snapshot = _state.value
        val target = positionMs.coerceIn(0L, snapshot.durationMs)
        if (snapshot.track.usesPlayer) {
            player.seekTo(target)
        }
        _state.value = snapshot.copy(positionMs = target)
    }

    fun playFromQueue(track: DemoTrack) = play(track, _state.value.queue)

    fun playNext(track: DemoTrack): Boolean {
        val snapshot = _state.value
        if (snapshot.track.id == track.id) return false
        val queue = snapshot.queue.filterNot { it.id == track.id }.toMutableList()
        val current = queue.indexOfFirst { it.id == snapshot.track.id }
        queue.add((current + 1).coerceIn(0, queue.size), track)
        _state.value = snapshot.copy(queue = queue)
        return true
    }

    fun addToQueue(track: DemoTrack): Boolean {
        val snapshot = _state.value
        if (snapshot.queue.any { it.id == track.id }) return false
        _state.value = snapshot.copy(queue = snapshot.queue + track)
        return true
    }

    fun moveInQueue(track: DemoTrack, offset: Int): Boolean {
        val snapshot = _state.value
        val from = snapshot.queue.indexOfFirst { it.id == track.id }
        if (from < 0) return false
        val to = (from + offset).coerceIn(0, snapshot.queue.lastIndex)
        if (to == from) return false
        val queue = snapshot.queue.toMutableList()
        queue.add(to, queue.removeAt(from))
        _state.value = snapshot.copy(queue = queue)
        return true
    }

    fun moveToQueueTop(track: DemoTrack): Boolean {
        val snapshot = _state.value
        val from = snapshot.queue.indexOfFirst { it.id == track.id }
        if (from <= 0) return false
        val queue = snapshot.queue.toMutableList()
        queue.add(0, queue.removeAt(from))
        _state.value = snapshot.copy(queue = queue)
        return true
    }

    fun removeFromQueue(track: DemoTrack): Boolean {
        val snapshot = _state.value
        if (snapshot.track.id == track.id) return false
        val queue = snapshot.queue.filterNot { it.id == track.id }
        if (queue.size == snapshot.queue.size) return false
        _state.value = snapshot.copy(queue = queue)
        return true
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(250L)
                val snapshot = _state.value
                if (!snapshot.isPlaying || snapshot.track.usesPlayer) break
                val position = snapshot.positionMs + 250L
                if (position >= snapshot.durationMs) {
                    next()
                } else {
                    _state.value = snapshot.copy(positionMs = position)
                }
            }
        }
    }

    private fun startPlayerTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(400L)
                val snapshot = _state.value
                if (!snapshot.track.usesPlayer) break
                val duration = player.duration.takeIf { it > 0L && it != C.TIME_UNSET }
                    ?: snapshot.resolvedDurationMs
                _state.value = snapshot.copy(
                    positionMs = player.currentPosition.coerceAtLeast(0L),
                    resolvedDurationMs = duration,
                    isPlaying = player.isPlaying,
                    buffering = player.playbackState == Player.STATE_BUFFERING,
                )
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private companion object {
        const val HTTP_UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        const val MAX_AUTO_RETRIES = 2
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
