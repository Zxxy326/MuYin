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
    private val resolveUrl: suspend (track: DemoTrack, preferredQuality: String?) -> String?,
    private val localPath: (DemoTrack) -> String? = { null },
    private val onTrackStarted: (DemoTrack) -> Unit = {},
    smbFactory: DataSource.Factory? = null,
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
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true,
        )
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
                _state.value = _state.value.copy(isPlaying = false, buffering = false)
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
                player.pause()
            } else {
                player.play()
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
            val url = withContext(Dispatchers.IO) { resolveUrl(track, null) }
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
            val url = withContext(Dispatchers.IO) { resolveUrl(track, "flac") }
            if (url.isNullOrBlank()) {
                _state.value = _state.value.copy(isPlaying = false, buffering = false)
                _errors.tryEmit("只找到试听片段，未找到完整音源")
                return@launch
            }
            startMedia(track, url)
        }
    }

    private fun startMedia(track: DemoTrack, url: String) {
        player.setMediaItem(buildMediaItem(track, url))
        player.prepare()
        player.playWhenReady = true
        _state.value = _state.value.copy(buffering = false)
        startPlayerTicker()
        onTrackStarted(track)
    }

    private fun startLocalFile(track: DemoTrack, file: File) {
        startLocalUri(track, Uri.fromFile(file).toString())
    }

    private fun startLocalUri(track: DemoTrack, uri: String) {
        player.stop()
        player.clearMediaItems()
        player.setMediaItem(buildMediaItem(track, uri))
        player.prepare()
        player.playWhenReady = true
        _state.value = _state.value.copy(buffering = false, isPlaying = true)
        startPlayerTicker()
        onTrackStarted(track)
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
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
