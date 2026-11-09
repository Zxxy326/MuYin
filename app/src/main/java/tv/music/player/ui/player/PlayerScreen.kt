package tv.music.player.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.music.player.core.download.DownloadEntry
import tv.music.player.core.download.DownloadStatus
import tv.music.player.core.download.DownloadStore
import tv.music.player.core.focus.TvFocusGroup
import tv.music.player.core.focus.TvSurface
import tv.music.player.core.focus.rememberTvFocusGroup
import tv.music.player.core.library.LibraryStore
import tv.music.player.core.library.libraryKey
import tv.music.player.core.lyrics.LyricLine
import tv.music.player.core.lyrics.LyricWord
import tv.music.player.core.lyrics.Lyrics
import tv.music.player.core.lyrics.LyricsRepository
import tv.music.player.core.lyrics.LyricCandidate
import tv.music.player.core.lyrics.LyricsState
import tv.music.player.core.playback.DemoTrack
import tv.music.player.core.playback.PlaybackController
import tv.music.player.core.playback.PlayerSnapshot
import tv.music.player.core.playback.formatDuration
import tv.music.player.core.settings.AppSettings
import tv.music.player.core.settings.LyricStyle
import tv.music.player.core.settings.PlayerLayout
import tv.music.player.core.settings.SettingsRepository
import tv.music.player.core.settings.formatLyricOffset
import tv.music.player.core.theme.LocalTvAccent
import tv.music.player.core.theme.TvColors
import tv.music.player.core.theme.TvFonts
import tv.music.player.core.theme.TvType
import tv.music.player.core.theme.rememberCoverArt
import tv.music.player.ui.components.ActionChip
import tv.music.player.ui.components.TrackArtwork
import tv.music.player.ui.components.KeyHintBar
import tv.music.player.ui.components.LocalCapsuleHost
import tv.music.player.ui.components.ProgressLine
import tv.music.player.ui.components.QueueRow
import tv.music.player.ui.components.TagChip
import tv.music.player.ui.components.TvIcon
import tv.music.player.ui.components.TvSafeArea

@Composable
fun PlayerScreen(
    playback: PlaybackController,
    settings: AppSettings,
    settingsRepository: SettingsRepository,
    lyricsRepository: LyricsRepository,
    library: LibraryStore,
    downloads: DownloadStore,
    onBack: () -> Unit,
) {
    val snapshot by playback.state.collectAsStateWithLifecycle()
    val favorites by library.favorites.collectAsStateWithLifecycle()
    val isFavorite = favorites.any { it.libraryKey() == snapshot.track.libraryKey() }
    val downloadEntries by downloads.entries.collectAsStateWithLifecycle()
    val downloadEntry = downloadEntries.firstOrNull { it.key == snapshot.track.libraryKey() }
    val cover = rememberCoverArt(snapshot.track.artworkUrl)
    val accent = LocalTvAccent.current
    val capsule = LocalCapsuleHost.current
    val group = rememberTvFocusGroup("player")
    val stageRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    var controlsVisible by remember { mutableStateOf(false) }
    var chromeVisible by remember { mutableStateOf(true) }
    var chromeTick by remember { mutableIntStateOf(0) }
    var queueVisible by remember { mutableStateOf(false) }
    var lyricsPanelVisible by remember { mutableStateOf(false) }
    var lyricOption by remember { mutableIntStateOf(settings.lyricStyle.ordinal) }
    var longSeekHandled by remember { mutableStateOf(false) }
    var lyricsState by remember(snapshot.track.id) { mutableStateOf<LyricsState>(LyricsState.Loading) }
    var lyricsAttempt by remember(snapshot.track.id) { mutableIntStateOf(0) }
    var lyricsMatchVisible by remember { mutableStateOf(false) }
    var sourcePanelVisible by remember { mutableStateOf(false) }
    var lyricCandidates by remember { mutableStateOf<List<LyricCandidate>>(emptyList()) }
    var lyricMatchIndex by remember { mutableIntStateOf(0) }
    var lyricMatchLoading by remember { mutableStateOf(false) }
    var lyricMatchError by remember { mutableStateOf<String?>(null) }

    val style = settings.lyricStyle

    LaunchedEffect(snapshot.track.id, lyricsAttempt) {
        lyricsState = LyricsState.Loading
        lyricsState = lyricsRepository.load(snapshot.track, forceRefresh = lyricsAttempt > 0)
    }

    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(controlsVisible, queueVisible, lyricsPanelVisible, lyricsMatchVisible, sourcePanelVisible, windowFocused) {
        if (!windowFocused) return@LaunchedEffect
        when {
            lyricsMatchVisible -> return@LaunchedEffect
            lyricsPanelVisible -> return@LaunchedEffect
            sourcePanelVisible -> {
                delay(90)
                group.focusWhenReady("source:auto", timeoutMs = 1_200L)
            }
            queueVisible -> {
                delay(90)
                val key = snapshot.queue.firstOrNull()?.let { "queue:${it.id}" } ?: "queue:close"
                group.focusWhenReady(key, timeoutMs = 1_200L)
            }
            controlsVisible -> {
                delay(90)
                group.focus("controls:play")
            }
            else -> {
                delay(60)
                runCatching { stageRequester.requestFocus() }
            }
        }
    }

    LaunchedEffect(chromeTick, chromeVisible, controlsVisible, queueVisible, lyricsPanelVisible, lyricsMatchVisible, sourcePanelVisible) {
        if (!chromeVisible) return@LaunchedEffect
        if (queueVisible || lyricsPanelVisible || lyricsMatchVisible || sourcePanelVisible) return@LaunchedEffect
        delay(5_000L)
        chromeVisible = false
        controlsVisible = false
    }

    BackHandler(enabled = lyricsMatchVisible) { lyricsMatchVisible = false }
    BackHandler(enabled = sourcePanelVisible) { sourcePanelVisible = false }
    BackHandler(enabled = queueVisible) { queueVisible = false }
    BackHandler(enabled = lyricsPanelVisible) { lyricsPanelVisible = false }
    BackHandler(enabled = !queueVisible && !lyricsPanelVisible) { onBack() }

    fun saveSetting(message: String, block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }
                .onSuccess { capsule.show(message) }
                .onFailure { capsule.show("保存失败：${it.message.orEmpty()}") }
        }
    }

    fun toggleFavorite() {
        val added = library.toggleFavorite(snapshot.track)
        capsule.show(if (added) "已收藏：${snapshot.track.title}" else "已取消收藏")
    }

    fun handleDownload() {
        val track = snapshot.track
        if (!track.isOnline) {
            capsule.show("演示曲目没有可下载的音源")
            return
        }
        when (downloads.entryFor(track)?.status) {
            null -> {
                downloads.enqueue(track)
                capsule.show("开始下载：${track.title}")
            }
            DownloadStatus.FAILED -> {
                downloads.cancel(track)
                downloads.enqueue(track)
                capsule.show("重新下载：${track.title}")
            }
            DownloadStatus.DONE -> capsule.show("已下载 · 在首页「本地音乐」离线播放")
            DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> {
                val entry = downloads.entryFor(track)
                val percent = if (entry != null && entry.totalBytes > 0L) {
                    " ${(entry.progress * 100).toInt()}%"
                } else {
                    ""
                }
                capsule.show("正在下载$percent：${track.title}")
            }
        }
    }

    fun openLyricsPanel() {
        lyricOption = settings.lyricStyle.ordinal
        lyricsPanelVisible = true
    }

    fun openSourcePanel() {
        controlsVisible = false
        sourcePanelVisible = true
    }

    fun openLyricMatch() {
        if (!snapshot.track.isOnline) {
            capsule.show("演示曲目没有在线歌词可匹配")
            return
        }
        lyricsPanelVisible = false
        lyricsMatchVisible = true
        lyricMatchLoading = true
        lyricMatchError = null
        lyricCandidates = emptyList()
        lyricMatchIndex = 0
        val track = snapshot.track
        scope.launch {
            runCatching { lyricsRepository.searchCandidates(track) }
                .onSuccess { found ->
                    lyricCandidates = buildList {
                        add(LyricCandidate("", 0L, "自动匹配", "恢复 QQ 原生歌词"))
                        addAll(found)
                    }
                    lyricMatchLoading = false
                    if (found.isEmpty()) lyricMatchError = "没有找到可用的歌词版本"
                }
                .onFailure {
                    lyricMatchLoading = false
                    lyricMatchError = "搜索失败：${it.message.orEmpty()}"
                }
        }
    }

    fun chooseLyricCandidate() {
        if (lyricMatchLoading) return
        val candidate = lyricCandidates.getOrNull(lyricMatchIndex) ?: return
        if (candidate.songMid.isBlank()) {
            lyricsRepository.clearMatch(snapshot.track.onlineSongId)
            capsule.show("已恢复自动匹配")
        } else {
            lyricsRepository.setMatch(snapshot.track.onlineSongId, candidate)
            capsule.show("歌词已匹配：${candidate.title}")
        }
        lyricsMatchVisible = false
        lyricsAttempt++
    }

    fun activateLyricOption(index: Int) {
        val styleCount = LyricStyle.entries.size
        if (index < styleCount) {
            val style = LyricStyle.entries[index]
            saveSetting("歌词样式：${style.label}") { settingsRepository.setLyricStyle(style) }
            return
        }
        when (index - styleCount) {
            0 -> saveSetting(if (settings.showLyricTranslation) "已隐藏歌词翻译" else "已显示歌词翻译") {
                settingsRepository.setShowLyricTranslation(!settings.showLyricTranslation)
            }
            1 -> {
                val next = (settings.lyricOffsetMs + 500).coerceIn(-5_000, 5_000)
                saveSetting("歌词偏移：${formatLyricOffset(next)}") { settingsRepository.setLyricOffset(next) }
            }
            2 -> {
                val next = (settings.lyricOffsetMs - 500).coerceIn(-5_000, 5_000)
                saveSetting("歌词偏移：${formatLyricOffset(next)}") { settingsRepository.setLyricOffset(next) }
            }
            3 -> saveSetting("歌词偏移已归零") { settingsRepository.setLyricOffset(0) }
            4 -> {
                lyricsAttempt++
                capsule.show("正在重新获取歌词")
            }
            5 -> openLyricMatch()
        }
    }

    fun seekWithLongPress(direction: Int, repeatCount: Int) {
        if (repeatCount >= 3) {
            if (!longSeekHandled) {
                longSeekHandled = true
                if (direction < 0) playback.previous() else playback.next()
                capsule.show(if (direction < 0) "上一首" else "下一首")
            }
        } else {
            playback.seekBy(direction * 10_000L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    chromeVisible = true
                    chromeTick++
                }
            }
            .focusRequester(stageRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    chromeVisible = true
                    chromeTick++
                }
                if (lyricsMatchVisible) {
                    if (event.type != KeyEventType.KeyDown) {
                        false
                    } else {
                        when (event.key) {
                            Key.DirectionUp, Key.DirectionLeft -> {
                                lyricMatchIndex = (lyricMatchIndex - 1).coerceAtLeast(0)
                                true
                            }
                            Key.DirectionDown, Key.DirectionRight -> {
                                lyricMatchIndex = (lyricMatchIndex + 1)
                                    .coerceAtMost((lyricCandidates.size - 1).coerceAtLeast(0))
                                true
                            }
                            Key.DirectionCenter, Key.Enter -> {
                                chooseLyricCandidate()
                                true
                            }
                            Key.Menu -> {
                                lyricsMatchVisible = false
                                true
                            }
                            else -> false
                        }
                    }
                } else if (lyricsPanelVisible) {
                    if (event.type != KeyEventType.KeyDown) {
                        false
                    } else {
                        when (event.key) {
                            Key.DirectionLeft -> {
                                lyricOption = (lyricOption + LyricStyle.entries.size + 5) % (LyricStyle.entries.size + 6)
                                true
                            }
                            Key.DirectionRight -> {
                                lyricOption = (lyricOption + 1) % (LyricStyle.entries.size + 6)
                                true
                            }
                            Key.DirectionCenter, Key.Enter -> {
                                activateLyricOption(lyricOption)
                                true
                            }
                            Key.Menu -> {
                                lyricsPanelVisible = false
                                true
                            }
                            else -> false
                        }
                    }
                } else if (controlsVisible || queueVisible) {
                    false
                } else {
                    when {
                        event.type == KeyEventType.KeyUp &&
                            (event.key == Key.DirectionLeft || event.key == Key.DirectionRight) -> {
                            longSeekHandled = false
                            false
                        }
                        event.type != KeyEventType.KeyDown -> false
                        else -> when (event.key) {
                            Key.DirectionCenter, Key.Enter -> {
                                playback.toggle()
                                true
                            }
                            Key.DirectionLeft -> {
                                seekWithLongPress(-1, event.nativeKeyEvent.repeatCount)
                                true
                            }
                            Key.DirectionRight -> {
                                seekWithLongPress(1, event.nativeKeyEvent.repeatCount)
                                true
                            }
                            Key.DirectionUp -> {
                                controlsVisible = true
                                true
                            }
                            Key.DirectionDown -> {
                                queueVisible = true
                                true
                            }
                            Key.Menu -> {
                                openLyricsPanel()
                                true
                            }
                            else -> false
                        }
                    }
                }
            }
    ) {
        PlayerBackdrop(
            coverImage = cover,
            blur = settings.blurBackground,
            accent = accent,
        )

        TvSafeArea(fullBleed = true) {
            Box(Modifier.fillMaxSize()) {
                LyricsView(
                    snapshot = snapshot,
                    state = lyricsState,
                    settings = settings,
                    style = style,
                    coverImage = cover,
                )
            }
        }

        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { -it / 4 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 4 },
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.58f),
                            0.62f to Color.Black.copy(alpha = 0.20f),
                            1f to Color.Transparent,
                        )
                    )
                    .padding(horizontal = 48.dp, vertical = 27.dp),
            ) {
                PlayerTopBar(
                    snapshot = snapshot,
                    settings = settings,
                    favorite = isFavorite,
                    coverImage = cover,
                )
            }
        }

        AnimatedVisibility(
            visible = chromeVisible && !controlsVisible && !queueVisible &&
                !lyricsPanelVisible && !lyricsMatchVisible && !sourcePanelVisible,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(220)),
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.35f to Color.Black.copy(alpha = 0.34f),
                            1f to Color.Black.copy(alpha = 0.66f),
                        )
                    )
                    .padding(horizontal = 48.dp, vertical = 22.dp),
            ) {
                KeyHintBar(
                    hints = listOf(
                        "OK" to "播放 / 暂停",
                        "← →" to "快退 / 快进 10s",
                        "长按 ← →" to "上一首 / 下一首",
                        "↑" to "控制条",
                        "↓" to "播放队列",
                        "MENU" to "歌词设置",
                    )
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 48.dp, vertical = 20.dp),
            enter = slideInVertically(tween(200)) { it } + fadeIn(tween(160)),
            exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(120)),
        ) {
            ControlsPanel(
                snapshot = snapshot,
                settings = settings,
                favorite = isFavorite,
                download = downloadEntry,
                group = group,
                playback = playback,
                onToggleFavorite = ::toggleFavorite,
                onDownload = ::handleDownload,
                onOpenQueue = { queueVisible = true },
                onOpenLyrics = {
                    controlsVisible = false
                    openLyricsPanel()
                },
                onOpenSource = { openSourcePanel() },
                onClose = { controlsVisible = false },
            )
        }

        AnimatedVisibility(
            visible = sourcePanelVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 48.dp, vertical = 20.dp),
            enter = slideInVertically(tween(200)) { it } + fadeIn(tween(160)),
            exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(120)),
        ) {
            SourcePanel(
                track = snapshot.track,
                sources = playback.availableSources(),
                group = group,
                onSelect = { source ->
                    sourcePanelVisible = false
                    playback.reresolve(source)
                },
                onRefresh = {
                    sourcePanelVisible = false
                    playback.reresolve(null)
                },
                onClose = { sourcePanelVisible = false },
            )
        }

        AnimatedVisibility(
            visible = queueVisible,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(tween(220)) { it } + fadeIn(tween(160)),
            exit = slideOutHorizontally(tween(200)) { it } + fadeOut(tween(140)),
        ) {
            QueuePanel(
                snapshot = snapshot,
                group = group,
                playback = playback,
                onClose = { queueVisible = false },
            )
        }

        AnimatedVisibility(
            visible = lyricsPanelVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 48.dp, vertical = 20.dp),
            enter = slideInVertically(tween(200)) { it } + fadeIn(tween(160)),
            exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(120)),
        ) {
            LyricsSettingsPanel(
                settings = settings,
                lyricsState = lyricsState,
                selectedIndex = lyricOption,
            )
        }

        AnimatedVisibility(
            visible = lyricsMatchVisible,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 96.dp),
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(120)),
        ) {
            LyricMatchPanel(
                track = snapshot.track,
                candidates = lyricCandidates,
                selectedIndex = lyricMatchIndex,
                loading = lyricMatchLoading,
                error = lyricMatchError,
            )
        }
    }
}

@Composable
private fun SplitCoverRail(
    snapshot: PlayerSnapshot,
    settings: AppSettings,
    coverImage: tv.music.player.core.theme.CoverArt,
    favorite: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = LocalTvAccent.current
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(240.dp)
                .clip(RoundedCornerShape(24.dp))
                .shadow(24.dp, RoundedCornerShape(24.dp), ambientColor = accent, spotColor = accent),
        ) {
            Image(
                bitmap = coverImage.image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.32f)),
                        )
                    )
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = snapshot.track.title,
            style = TvType.title,
            color = TvColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = snapshot.track.artist,
            style = TvType.label,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (snapshot.track.album.isNotBlank()) {
            Text(
                text = snapshot.track.album,
                style = TvType.caption,
                color = TvColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(12.dp))
        ProgressLine(snapshot.progress, Modifier.fillMaxWidth(), height = 6.dp)
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(snapshot.positionMs), style = TvType.caption, color = TvColors.TextTertiary)
            Text(formatDuration(snapshot.durationMs), style = TvType.caption, color = TvColors.TextTertiary)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TagChip("音质 ${settings.quality.label}", accent = true)
            TagChip("队列 ${snapshot.queue.size}")
            if (favorite) TagChip("已收藏")
        }
    }
}

@Composable
private fun PlayerBackdrop(
    coverImage: tv.music.player.core.theme.CoverArt,
    blur: Boolean,
    accent: Color,
) {
    Box(Modifier.fillMaxSize()) {
        Image(
            bitmap = coverImage.image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(if (blur) Modifier.blur(90.dp) else Modifier),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.10f),
                            Color.Black.copy(alpha = 0.30f),
                            Color.Black.copy(alpha = 0.74f),
                        )
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.18f), Color.Transparent)
                    )
                )
        )
    }
}

@Composable
private fun PlayerTopBar(
    snapshot: PlayerSnapshot,
    settings: AppSettings,
    favorite: Boolean,
    coverImage: tv.music.player.core.theme.CoverArt,
) {
    val accent = LocalTvAccent.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(
            bitmap = coverImage.image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(88.dp).clip(RoundedCornerShape(18.dp)),
        )
        Spacer(Modifier.width(18.dp))
        Column {
            Text(
                if (snapshot.track.isOnline) "正在播放 · 在线音源" else "正在播放 · 布局 A 全屏歌词",
                style = TvType.caption,
                color = accent,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                snapshot.track.title,
                style = TvType.display,
                color = TvColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${snapshot.track.artist} · ${snapshot.track.album}",
                style = TvType.label,
                color = TvColors.TextSecondary,
            )
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (snapshot.buffering) {
                    TagChip("解析中", accent = true)
                } else {
                    TagChip("音质 ${settings.quality.label}", accent = true)
                }
                TagChip("队列 ${snapshot.queue.size}")
                if (favorite) TagChip("已收藏")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "返回 上一级 · 长按返回 回到首页",
                style = TvType.caption,
                color = TvColors.TextTertiary,
            )
        }
    }
}

@Composable
private fun LyricsView(
    snapshot: PlayerSnapshot,
    state: LyricsState,
    settings: AppSettings,
    style: LyricStyle,
    coverImage: tv.music.player.core.theme.CoverArt,
) {
    val scale = settings.lyricSize.scale
    if (style == LyricStyle.CLOCK) {
        ClockLyricsEffect(snapshot.track, coverImage)
        return
    }
    when (state) {
        LyricsState.Loading -> LyricsMessage(
            title = if (snapshot.buffering) "正在解析音源…" else "正在获取歌词…",
            subtitle = null,
            scale = scale,
        )
        is LyricsState.Failed -> LyricsMessage(
            title = "歌词获取失败",
            subtitle = "${state.message} · 按 MENU 重新获取",
            scale = scale,
            accent = true,
        )
        is LyricsState.Unavailable -> LyricsMessage(
            title = state.message,
            subtitle = when {
                state.message.contains("纯音乐") -> "这首歌没有填词"
                state.message.contains("本地") || state.message.contains(".lrc") ->
                    "把同名 .lrc 放到歌曲旁边，按 MENU 重载"
                else -> "按 MENU 可重新获取"
            },
            scale = scale,
        )
        is LyricsState.Ready -> {
            val lyrics = state.lyrics
            when (style) {
                LyricStyle.WAVE -> WaveLyricsEffect(
                    lyrics = lyrics,
                    positionMs = snapshot.positionMs,
                    offsetMs = settings.lyricOffsetMs,
                    track = snapshot.track,
                    coverImage = coverImage,
                )
                LyricStyle.SIMPLE -> SimpleLyricsEffect(
                    lyrics = lyrics,
                    positionMs = snapshot.positionMs,
                    offsetMs = settings.lyricOffsetMs,
                    track = snapshot.track,
                    coverImage = coverImage,
                )
                LyricStyle.CLOCK -> Unit
            }
        }
    }
}

@Composable
private fun LyricsMessage(
    title: String,
    subtitle: String?,
    scale: Float,
    accent: Boolean = false,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = TextStyle(fontFamily = TvFonts.family, fontSize = (34f * scale).sp, fontWeight = FontWeight.Bold),
            color = if (accent) LocalTvAccent.current else Color.White,
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = subtitle,
                style = TextStyle(fontFamily = TvFonts.family, fontSize = (20f * scale).sp),
                color = Color.White.copy(alpha = 0.45f),
            )
        }
    }
}

private fun Modifier.lyricEdgeFade(): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.10f to Color.White,
                0.90f to Color.White,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

@Composable
private fun ScrollingLyrics(
    lyrics: Lyrics,
    positionMs: Long,
    offsetMs: Int,
    showTranslation: Boolean,
    scale: Float,
    currentSp: Float,
    otherSp: Float,
    animations: Boolean,
) {
    val currentIndex = lyrics.indexAt(positionMs, offsetMs.toLong())
    val scrollState = rememberScrollState()
    var viewportPx by remember { mutableIntStateOf(0) }
    val heights = remember(lyrics) { mutableStateMapOf<Int, Int>() }
    val halfSpacer = with(LocalDensity.current) { (viewportPx / 2).toDp() }

    LaunchedEffect(
        currentIndex,
        heights.size,
        heights[currentIndex],
        viewportPx,
        showTranslation,
        scale,
    ) {
        if (currentIndex < 0 || viewportPx == 0) return@LaunchedEffect
        val currentHeight = heights[currentIndex] ?: 0
        if (currentHeight == 0) return@LaunchedEffect
        var before = 0
        for (index in 0 until currentIndex) before += heights[index] ?: 0
        val target = (before + currentHeight / 2).coerceIn(0, scrollState.maxValue)
        if (animations) scrollState.animateScrollTo(target) else scrollState.scrollTo(target)
    }

    Box(Modifier.fillMaxSize().onSizeChanged { viewportPx = it.height }.lyricEdgeFade()) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (viewportPx > 0) Spacer(Modifier.height(halfSpacer))
            lyrics.lines.forEachIndexed { index, line ->
                LyricRow(
                    line = line,
                    index = index,
                    currentIndex = currentIndex,
                    showTranslation = showTranslation,
                    scale = scale,
                    currentSp = currentSp,
                    otherSp = otherSp,
                    onMeasured = { heights[index] = it },
                )
            }
            if (viewportPx > 0) Spacer(Modifier.height(halfSpacer))
        }
    }
}

@Composable
private fun LyricRow(
    line: LyricLine,
    index: Int,
    currentIndex: Int,
    showTranslation: Boolean,
    scale: Float,
    currentSp: Float,
    otherSp: Float,
    onMeasured: (Int) -> Unit,
) {
    val active = index == currentIndex
    val distance = if (currentIndex < 0) 99 else kotlin.math.abs(index - currentIndex)
    val targetAlpha = when (distance) {
        0 -> 1f
        1 -> 0.45f
        else -> 0.2f
    }
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(220),
        label = "lyric-alpha-$index",
    )
    val fontSize = (if (active) currentSp else otherSp) * scale
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { onMeasured(it.height) }
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = line.text,
            style = TextStyle(fontFamily = TvFonts.family, 
                fontSize = fontSize.sp,
                lineHeight = (fontSize * 1.3f).sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            ),
            color = Color.White.copy(alpha = alpha),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )
        val translation = line.translation
        if (showTranslation && !translation.isNullOrBlank()) {
            Text(
                text = translation,
                style = TextStyle(fontFamily = TvFonts.family, 
                    fontSize = (fontSize * 0.52f).sp,
                    lineHeight = (fontSize * 0.7f).sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = Color.White.copy(alpha = alpha * 0.62f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            )
        }
    }
}

@Composable
private fun SubtitleLyrics(
    lyrics: Lyrics,
    positionMs: Long,
    offsetMs: Int,
    showTranslation: Boolean,
    scale: Float,
) {
    val index = lyrics.indexAt(positionMs, offsetMs.toLong()).coerceAtLeast(0)
    Box(
        modifier = Modifier.fillMaxSize().padding(bottom = 4.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Crossfade(
            targetState = index,
            animationSpec = tween(240),
            label = "subtitle-lyrics",
        ) { current ->
            val line = lyrics.lines.getOrNull(current) ?: return@Crossfade
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OutlinedLyric(line.text, (64f * scale).sp)
                if (showTranslation && !line.translation.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedLyric(line.translation, (30f * scale).sp, alpha = 0.9f)
                }
                val next = lyrics.lines.getOrNull(current + 1)
                if (next != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = next.text,
                        style = TextStyle(fontFamily = TvFonts.family, 
                            fontSize = (28f * scale).sp,
                            lineHeight = (34f * scale).sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = Color.White.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun OutlinedLyric(text: String, fontSize: androidx.compose.ui.unit.TextUnit, alpha: Float = 1f) {
    val base = TextStyle(fontFamily = TvFonts.family, 
        fontSize = fontSize,
        lineHeight = (fontSize.value * 1.16f).sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    val strokePx = with(LocalDensity.current) { 2.5.dp.toPx() }
    Box(contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = base.copy(drawStyle = Stroke(width = strokePx)),
            color = Color.Black.copy(alpha = alpha),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = text,
            style = base,
            color = Color.White.copy(alpha = alpha),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SyncedLyrics(
    lyrics: Lyrics,
    positionMs: Long,
    offsetMs: Int,
    animations: Boolean,
    content: @Composable (LyricLine, Boolean) -> Unit,
) {
    val currentIndex = lyrics.indexAt(positionMs, offsetMs.toLong())
    val scrollState = rememberScrollState()
    var viewportPx by remember { mutableIntStateOf(0) }
    val heights = remember(lyrics) { mutableStateMapOf<Int, Int>() }
    val halfSpacer = with(LocalDensity.current) { (viewportPx / 2).toDp() }

    LaunchedEffect(currentIndex, heights.size, heights[currentIndex], viewportPx) {
        if (currentIndex < 0 || viewportPx == 0) return@LaunchedEffect
        val currentHeight = heights[currentIndex] ?: 0
        if (currentHeight == 0) return@LaunchedEffect
        var before = 0
        for (index in 0 until currentIndex) before += heights[index] ?: 0
        val target = (before + currentHeight / 2).coerceIn(0, scrollState.maxValue)
        if (animations) scrollState.animateScrollTo(target) else scrollState.scrollTo(target)
    }

    Box(Modifier.fillMaxSize().onSizeChanged { viewportPx = it.height }.lyricEdgeFade()) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (viewportPx > 0) Spacer(Modifier.height(halfSpacer))
            lyrics.lines.forEachIndexed { index, line ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { heights[index] = it.height }
                        .padding(vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    content(line, index == currentIndex)
                }
            }
            if (viewportPx > 0) Spacer(Modifier.height(halfSpacer))
        }
    }
}

@Composable
private fun KaraokeLyrics(
    lyrics: Lyrics,
    positionMs: Long,
    offsetMs: Int,
    showTranslation: Boolean,
    scale: Float,
    animations: Boolean,
    bilingual: Boolean,
) {
    SyncedLyrics(lyrics, positionMs, offsetMs, animations) { line, active ->
        if (active) {
            KaraokeLine(
                line = line,
                positionMs = positionMs,
                offsetMs = offsetMs,
                showTranslation = showTranslation,
                scale = scale,
                bilingual = bilingual,
            )
        } else {
            PlainLyricLine(
                line = line,
                scale = scale,
                showTranslation = showTranslation && bilingual,
                active = false,
            )
        }
    }
}

@Composable
private fun KaraokeLine(
    line: LyricLine,
    positionMs: Long,
    offsetMs: Int,
    showTranslation: Boolean,
    scale: Float,
    bilingual: Boolean,
) {
    val accent = LocalTvAccent.current
    val measurer = rememberTextMeasurer()
    val fontSize = (42f * scale).sp
    val style = TextStyle(fontFamily = TvFonts.family, 
        fontSize = fontSize,
        lineHeight = (fontSize.value * 1.28f).sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
    ) {
        val widthPx = constraints.maxWidth
        val layout = remember(line.text, style, widthPx) {
            measurer.measure(line.text, style, constraints = Constraints(maxWidth = widthPx))
        }
        val heightDp = with(density) { layout.size.height.toDp() }
        val textX = ((widthPx - layout.size.width) / 2f).coerceAtLeast(0f)
        Canvas(Modifier.fillMaxWidth().height(heightDp)) {
            drawText(layout, color = Color.White.copy(alpha = 0.28f), topLeft = Offset(textX, 0f))
            val sung = karaokeChars(line, positionMs + offsetMs)
            if (sung > 0f) {
                drawKaraokeSweep(layout, sung, accent, textX)
            }
        }
    }
    if (showTranslation && !line.translation.isNullOrBlank()) {
        Spacer(Modifier.height(if (bilingual) 8.dp else 4.dp))
        Text(
            text = line.translation,
            style = TextStyle(fontFamily = TvFonts.family, 
                fontSize = ((if (bilingual) 25f else 22f) * scale).sp,
                lineHeight = ((if (bilingual) 32f else 28f) * scale).sp,
                fontWeight = FontWeight.Medium,
            ),
            color = if (bilingual) accent.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawKaraokeSweep(
    layout: androidx.compose.ui.text.TextLayoutResult,
    charsSung: Float,
    accent: Color,
    textX: Float,
) {
    for (lineIndex in 0 until layout.lineCount) {
        val lineStart = layout.getLineStart(lineIndex)
        val lineEnd = layout.getLineEnd(lineIndex)
        if (lineEnd <= lineStart) continue
        val top = layout.getLineTop(lineIndex)
        val bottom = layout.getLineBottom(lineIndex)
        when {
            charsSung >= lineEnd -> clipRect(left = textX, top = top, right = textX + layout.size.width, bottom = bottom) {
                drawText(layout, color = accent, topLeft = Offset(textX, 0f))
            }
            charsSung > lineStart -> {
                val charIndex = charsSung.toInt().coerceIn(lineStart, lineEnd - 1)
                val box = layout.getBoundingBox(charIndex)
                val boundary = textX + box.left + box.width * (charsSung - charIndex)
                clipRect(left = textX, top = top, right = boundary, bottom = bottom) {
                    drawText(layout, color = accent, topLeft = Offset(textX, 0f))
                }
            }
        }
    }
}

private fun karaokeChars(line: LyricLine, timeMs: Long): Float {
    val max = line.text.length.toFloat()
    if (max <= 0f) return 0f
    if (line.words.isEmpty()) {
        val duration = line.durationMs.takeIf { it > 0L } ?: 4_000L
        val fraction = ((timeMs - line.timeMs).toFloat() / duration).coerceIn(0f, 1f)
        return fraction * max
    }
    var sung = 0
    for (word in line.words) {
        val end = word.timeMs + word.durationMs
        if (timeMs < word.timeMs) return sung.toFloat().coerceAtMost(max)
        if (timeMs < end) {
            val fraction = if (word.durationMs <= 0L) 0f else
                (timeMs - word.timeMs).toFloat() / word.durationMs
            return (sung + fraction * word.text.length).coerceAtMost(max)
        }
        sung += word.text.length
    }
    return max
}

@Composable
private fun PlainLyricLine(
    line: LyricLine,
    scale: Float,
    showTranslation: Boolean,
    active: Boolean,
) {
    val fontSize = ((if (active) 40f else 24f) * scale).sp
    Text(
        text = line.text,
        style = TextStyle(fontFamily = TvFonts.family, 
            fontSize = fontSize,
            lineHeight = (fontSize.value * 1.3f).sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        ),
        color = Color.White.copy(alpha = if (active) 0.95f else 0.35f),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
    )
    if (showTranslation && !line.translation.isNullOrBlank()) {
        Text(
            text = line.translation,
            style = TextStyle(fontFamily = TvFonts.family, 
                fontSize = (18f * scale).sp,
                lineHeight = (24f * scale).sp,
                fontWeight = FontWeight.Normal,
            ),
            color = Color.White.copy(alpha = 0.38f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )
    }
}

@Composable
private fun BounceLyrics(
    lyrics: Lyrics,
    positionMs: Long,
    offsetMs: Int,
    showTranslation: Boolean,
    scale: Float,
    animations: Boolean,
) {
    SyncedLyrics(lyrics, positionMs, offsetMs, animations) { line, active ->
        if (active) {
            BounceLine(
                line = line,
                positionMs = positionMs,
                offsetMs = offsetMs,
                showTranslation = showTranslation,
                scale = scale,
                animations = animations,
            )
        } else {
            PlainLyricLine(line = line, scale = scale, showTranslation = false, active = false)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BounceLine(
    line: LyricLine,
    positionMs: Long,
    offsetMs: Int,
    showTranslation: Boolean,
    scale: Float,
    animations: Boolean,
) {
    val accent = LocalTvAccent.current
    val t = positionMs + offsetMs
    val words = if (line.words.isNotEmpty()) {
        line.words
    } else {
        listOf(LyricWord(line.timeMs, line.durationMs.takeIf { it > 0L } ?: 4_000L, line.text))
    }
    val activeIndex = words.indexOfLast { t >= it.timeMs && t < it.timeMs + it.durationMs }
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.Center,
    ) {
        words.forEachIndexed { index, word ->
            val active = index == activeIndex
            val emphasize = active && word.text.any { it.isLetterOrDigit() }
            val sung = word.timeMs <= t
            val bounce by animateFloatAsState(
                targetValue = if (emphasize) 1.18f else 1f,
                animationSpec = if (animations) {
                    spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)
                } else {
                    tween(0)
                },
                label = "bounce-$index",
            )
            val shape = RoundedCornerShape(8.dp)
            Box(
                Modifier
                    .graphicsLayer {
                        scaleX = bounce
                        scaleY = bounce
                    }
                    .background(
                        color = if (emphasize) accent.copy(alpha = 0.24f) else Color.Transparent,
                        shape = shape,
                    )
                    .padding(horizontal = 1.dp, vertical = 2.dp),
            ) {
                Text(
                    text = word.text,
                    style = TextStyle(fontFamily = TvFonts.family, 
                        fontSize = (36f * scale).sp,
                        lineHeight = (42f * scale).sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = when {
                        active -> accent
                        sung -> Color.White.copy(alpha = 0.92f)
                        else -> Color.White.copy(alpha = 0.3f)
                    },
                )
            }
        }
    }
    if (showTranslation && !line.translation.isNullOrBlank()) {
        Text(
            text = line.translation,
            style = TextStyle(fontFamily = TvFonts.family, 
                fontSize = (21f * scale).sp,
                lineHeight = (27f * scale).sp,
                fontWeight = FontWeight.Medium,
            ),
            color = Color.White.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 5.dp, start = 24.dp, end = 24.dp),
        )
    }
}

@Composable
private fun StyleWheel(selectedIndex: Int, currentStyle: LyricStyle) {
    val state = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cardWidth = 150.dp
        val side = ((maxWidth - cardWidth) / 2).coerceAtLeast(0.dp)
        LaunchedEffect(selectedIndex) {
            state.animateScrollToItem(selectedIndex)
        }
        LazyRow(
            state = state,
            modifier = Modifier.fillMaxWidth().height(86.dp),
            contentPadding = PaddingValues(horizontal = side),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            userScrollEnabled = false,
        ) {
            itemsIndexed(LyricStyle.entries.toList()) { index, style ->
                StyleCard(
                    style = style,
                    selected = index == selectedIndex,
                    active = style == currentStyle,
                    modifier = Modifier.width(cardWidth),
                )
            }
        }
    }
}

@Composable
private fun StyleCard(
    style: LyricStyle,
    selected: Boolean,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = LocalTvAccent.current
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .height(84.dp)
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.14f) else TvColors.SurfaceElevated)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) accent else TvColors.Divider,
                shape = shape,
            )
            .padding(7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            StylePreview(style)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = style.label + if (active) " · 当前" else "",
            style = TvType.caption,
            color = if (selected || active) accent else TvColors.TextSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun StylePreview(style: LyricStyle) {
    val accent = LocalTvAccent.current
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(TvColors.Background.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center,
    ) {
        when (style) {
            LyricStyle.WAVE -> Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), Color.Transparent))),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.7f)
                        .height(1.dp)
                        .background(accent.copy(alpha = 0.6f))
                )
                PreviewText("晴天", 12f, Color.White, bold = true)
            }
            LyricStyle.SIMPLE -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PreviewText("晴天", 13f, Color.White.copy(alpha = 0.8f), bold = true)
                PreviewText("为你翘课的那一天", 8f, Color.White.copy(alpha = 0.5f))
            }
            LyricStyle.CLOCK -> PreviewText("12:30", 18f, Color.White, bold = true)
        }
    }
}

@Composable
private fun PreviewText(text: String, size: Float, color: Color, bold: Boolean = false) {
    Text(
        text = text,
        style = TextStyle(fontFamily = TvFonts.family, fontSize = size.sp, lineHeight = (size * 1.15f).sp),
        color = color,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
    )
}

@Composable
private fun VinylLyrics(
    lyrics: Lyrics,
    positionMs: Long,
    offsetMs: Int,
    showTranslation: Boolean,
    scale: Float,
    animations: Boolean,
    coverImage: tv.music.player.core.theme.CoverArt,
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 44.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(0.44f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            VinylDisc(coverImage = coverImage, animations = animations)
        }
        Spacer(Modifier.width(44.dp))
        Box(Modifier.weight(0.56f).fillMaxHeight()) {
            SyncedLyrics(lyrics, positionMs, offsetMs, animations) { line, active ->
                VinylLyricLine(
                    line = line,
                    active = active,
                    showTranslation = showTranslation,
                    scale = scale,
                )
            }
        }
    }
}

@Composable
private fun VinylDisc(coverImage: tv.music.player.core.theme.CoverArt, animations: Boolean) {
    val accent = LocalTvAccent.current
    val angle = if (animations) {
        val transition = rememberInfiniteTransition(label = "vinyl")
        val rotation by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing)),
            label = "vinyl-rotation",
        )
        rotation
    } else {
        0f
    }
    Box(Modifier.size(360.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            drawCircle(Color(0xFF0B0B0D), radius = radius)
            drawCircle(accent.copy(alpha = 0.12f), radius = radius * 0.98f, style = Stroke(width = 2f))
            for (i in 1..7) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.045f),
                    radius = radius * (0.52f + i * 0.06f),
                    style = Stroke(width = 1.2f),
                )
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(size.width * 0.35f, size.height * 0.3f),
                    radius = radius * 0.8f,
                ),
                radius = radius,
            )
        }
        Box(
            Modifier
                .size(222.dp)
                .graphicsLayer { rotationZ = angle }
                .clip(RoundedCornerShape(50))
                .shadow(18.dp, RoundedCornerShape(50), ambientColor = accent, spotColor = accent),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = coverImage.image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(50)),
            )
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color.Black.copy(alpha = 0.18f), radius = size.minDimension / 2f)
                drawCircle(Color.Transparent, radius = size.minDimension * 0.07f)
            }
            Box(Modifier.size(18.dp).clip(RoundedCornerShape(50)).background(Color(0xFF101012)))
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(accent.copy(alpha = 0.7f)))
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 36.dp, end = 8.dp)
                .width(6.dp)
                .height(112.dp)
                .graphicsLayer { rotationZ = -24f }
                .background(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0.12f))),
                    RoundedCornerShape(50),
                ),
        )
    }
}

@Composable
private fun VinylLyricLine(
    line: LyricLine,
    active: Boolean,
    showTranslation: Boolean,
    scale: Float,
) {
    val accent = LocalTvAccent.current
    val fontSize = ((if (active) 40f else 23f) * scale).sp
    Text(
        text = line.text,
        style = TextStyle(fontFamily = TvFonts.family, 
            fontSize = fontSize,
            lineHeight = (fontSize.value * 1.28f).sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        ),
        color = if (active) accent else Color.White.copy(alpha = 0.32f),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
    )
    if (active && showTranslation && !line.translation.isNullOrBlank()) {
        Text(
            text = line.translation,
            style = TextStyle(fontFamily = TvFonts.family, 
                fontSize = (21f * scale).sp,
                lineHeight = (27f * scale).sp,
                fontWeight = FontWeight.Medium,
            ),
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun ImmersiveLyrics(
    lyrics: Lyrics,
    positionMs: Long,
    offsetMs: Int,
    showTranslation: Boolean,
    scale: Float,
    animations: Boolean,
) {
    val accent = LocalTvAccent.current
    val glowAlpha = if (animations) {
        val transition = rememberInfiniteTransition(label = "immersive-glow")
        val pulse by transition.animateFloat(
            initialValue = 0.14f,
            targetValue = 0.26f,
            animationSpec = infiniteRepeatable(tween(4_200, easing = LinearEasing), RepeatMode.Reverse),
            label = "immersive-pulse",
        )
        pulse
    } else {
        0.2f
    }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = glowAlpha), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.52f),
                    radius = size.minDimension * 0.72f,
                ),
                radius = size.minDimension * 0.72f,
                center = Offset(size.width * 0.5f, size.height * 0.52f),
            )
        }
        SyncedLyrics(lyrics, positionMs, offsetMs, animations) { line, active ->
            if (active) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = line.text,
                        style = TextStyle(fontFamily = TvFonts.family, 
                            fontSize = (52f * scale).sp,
                            lineHeight = (62f * scale).sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            shadow = Shadow(
                                color = accent.copy(alpha = 0.55f),
                                blurRadius = 28f,
                            ),
                        ),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                    )
                    if (showTranslation && !line.translation.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = line.translation,
                            style = TextStyle(fontFamily = TvFonts.family, 
                                fontSize = (24f * scale).sp,
                                lineHeight = (31f * scale).sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = accent.copy(alpha = 0.9f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
                        )
                    }
                }
            } else {
                Text(
                    text = line.text,
                    style = TextStyle(fontFamily = TvFonts.family, 
                        fontSize = (22f * scale).sp,
                        lineHeight = (28f * scale).sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = Color.White.copy(alpha = 0.26f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 60.dp),
                )
            }
        }
    }
}


@Composable
private fun LyricMatchPanel(
    track: DemoTrack,
    candidates: List<LyricCandidate>,
    selectedIndex: Int,
    loading: Boolean,
    error: String?,
) {
    val accent = LocalTvAccent.current
    TvSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        baseColor = TvColors.Surface.copy(alpha = 0.98f),
        focusedScale = 1.0f,
        glowSize = 0.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 20.dp)) {
            Text("手动匹配歌词", style = TvType.title, color = TvColors.TextPrimary)
            Text(
                "搜索：${track.title} · ${track.artist}",
                style = TvType.caption,
                color = TvColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))
            when {
                loading -> Text("正在搜索可匹配的版本…", style = TvType.label, color = TvColors.TextSecondary)
                error != null -> Text(error, style = TvType.label, color = accent)
                candidates.isEmpty() -> Text("没有可匹配的版本", style = TvType.label, color = TvColors.TextSecondary)
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    candidates.take(7).forEachIndexed { index, candidate ->
                        val selected = index == selectedIndex
                        val shape = RoundedCornerShape(12.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(
                                    if (selected) accent.copy(alpha = 0.16f) else TvColors.SurfaceElevated,
                                )
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) accent else TvColors.Divider,
                                    shape = shape,
                                )
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    candidate.title,
                                    style = TvType.label,
                                    color = if (selected) accent else TvColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    candidate.artist,
                                    style = TvType.caption,
                                    color = TvColors.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                if (candidate.songMid.isBlank()) "自动" else candidate.songMid,
                                style = TvType.caption,
                                color = TvColors.TextTertiary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "↑ ↓ 选择 · OK 使用 · 返回 取消；选中“自动匹配”可恢复原生歌词。",
                style = TvType.caption,
                color = TvColors.TextTertiary,
            )
        }
    }
}

@Composable
private fun LyricsSettingsPanel(
    settings: AppSettings,
    lyricsState: LyricsState,
    selectedIndex: Int,
) {
    TvSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        baseColor = TvColors.Surface.copy(alpha = 0.96f),
        focusedScale = 1.0f,
        glowSize = 0.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("歌词", style = TvType.title, color = TvColors.TextPrimary)
                    Text(
                        "${settings.lyricStyle.label} · ${settings.lyricStyle.hint}",
                        style = TvType.caption,
                        color = TvColors.TextSecondary,
                    )
                }
                TagChip("偏移 ${formatLyricOffset(settings.lyricOffsetMs)}", accent = true)
                Spacer(Modifier.width(12.dp))
                TagChip(
                    when (lyricsState) {
                        LyricsState.Loading -> "加载中"
                        is LyricsState.Ready -> "${lyricsState.lyrics.lines.size} 行"
                        is LyricsState.Failed -> "获取失败"
                        is LyricsState.Unavailable -> "暂无歌词"
                    }
                )
                Spacer(Modifier.width(16.dp))
                Text("MENU / 返回 关闭", style = TvType.caption, color = TvColors.TextTertiary)
            }
            Spacer(Modifier.height(16.dp))
            StyleWheel(selectedIndex = selectedIndex.coerceIn(0, LyricStyle.entries.size - 1), currentStyle = settings.lyricStyle)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val actionBase = LyricStyle.entries.size
                LyricChoice(
                    label = if (settings.showLyricTranslation) "翻译 开" else "翻译 关",
                    selected = selectedIndex == actionBase,
                    active = settings.showLyricTranslation,
                )
                LyricChoice(label = "提前 0.5s", selected = selectedIndex == actionBase + 1)
                LyricChoice(label = "延后 0.5s", selected = selectedIndex == actionBase + 2)
                LyricChoice(label = "归零", selected = selectedIndex == actionBase + 3)
                LyricChoice(label = "重新获取", selected = selectedIndex == actionBase + 4)
                LyricChoice(label = "手动匹配", selected = selectedIndex == actionBase + 5)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "← → 转动样式轮盘并选择操作 · OK 执行 · MENU / 返回 关闭；偏移 ±0.5s 步进；手动匹配可换歌词版本。",
                style = TvType.caption,
                color = TvColors.TextTertiary,
            )
        }
    }
}

@Composable
private fun LyricChoice(label: String, selected: Boolean, active: Boolean = false) {
    val accent = LocalTvAccent.current
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .background(if (active) accent.copy(alpha = 0.18f) else TvColors.SurfaceElevated)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) accent else TvColors.Divider,
                shape = shape,
            )
            .padding(horizontal = 18.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            style = TvType.label,
            color = if (active || selected) accent else TvColors.TextPrimary,
            maxLines = 1,
        )
    }
}


@Composable
private fun ControlsPanel(
    snapshot: PlayerSnapshot,
    settings: AppSettings,
    favorite: Boolean,
    download: DownloadEntry?,
    group: TvFocusGroup,
    playback: PlaybackController,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenSource: () -> Unit,
    onClose: () -> Unit,
) {
    TvSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        baseColor = TvColors.Surface.copy(alpha = 0.95f),
        focusedScale = 1.0f,
        glowSize = 0.dp,
        onKeyEvent = { event ->
            if (event.type != KeyEventType.KeyDown) {
                false
            } else {
                when (event.key) {
                    Key.DirectionUp -> {
                        val active = group.activeKey.orEmpty()
                        if (active == "controls:queue" || active == "controls:source") {
                            onClose()
                        } else {
                            group.focus("controls:queue")
                        }
                        true
                    }
                    Key.DirectionDown -> {
                        onClose()
                        true
                    }
                    Key.Menu -> {
                        onOpenLyrics()
                        true
                    }
                    else -> false
                }
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrackArtwork(snapshot.track, Modifier.size(58.dp), corner = 12.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        snapshot.track.title,
                        style = TvType.title,
                        color = TvColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${snapshot.track.artist} · ${snapshot.track.album}",
                        style = TvType.caption,
                        color = TvColors.TextSecondary,
                    )
                }
                TagChip("音质 ${settings.quality.label}", accent = true)
                if (favorite) {
                    Spacer(Modifier.width(8.dp))
                    TagChip("已收藏")
                }
                Spacer(Modifier.width(10.dp))
                ActionChip(
                    label = "队列 ${snapshot.queue.size}",
                    icon = Icons.Rounded.QueueMusic,
                    group = group,
                    focusKey = "controls:queue",
                    onClick = onOpenQueue,
                )
                Spacer(Modifier.width(10.dp))
                ActionChip(
                    label = "音源",
                    group = group,
                    focusKey = "controls:source",
                    onClick = onOpenSource,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatDuration(snapshot.positionMs),
                    style = TvType.caption,
                    color = TvColors.TextSecondary,
                )
                Spacer(Modifier.width(14.dp))
                ProgressLine(snapshot.progress, Modifier.weight(1f), height = 6.dp)
                Spacer(Modifier.width(14.dp))
                Text(
                    formatDuration(snapshot.durationMs),
                    style = TvType.caption,
                    color = TvColors.TextSecondary,
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (snapshot.track.isOnline) {
                    TransportButton(
                        icon = if (download?.status == DownloadStatus.DONE) {
                            Icons.Rounded.DownloadDone
                        } else {
                            Icons.Rounded.Download
                        },
                        contentDescription = when (download?.status) {
                            null -> "下载"
                            DownloadStatus.QUEUED -> "准备下载"
                            DownloadStatus.DOWNLOADING -> "正在下载"
                            DownloadStatus.DONE -> "已下载"
                            DownloadStatus.FAILED -> "重新下载"
                        },
                        group = group,
                        focusKey = "controls:download",
                        onClick = onDownload,
                    )
                }
                TransportButton(
                    icon = if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (favorite) "取消收藏" else "收藏",
                    group = group,
                    focusKey = "controls:favorite",
                    onClick = onToggleFavorite,
                )
                TransportButton(
                    icon = Icons.Rounded.Replay10,
                    contentDescription = "快退 10 秒",
                    group = group,
                    focusKey = "controls:replay",
                    onClick = { playback.seekBy(-10_000L) },
                )
                TransportButton(
                    icon = Icons.Rounded.SkipPrevious,
                    contentDescription = "上一首",
                    group = group,
                    focusKey = "controls:prev",
                    onClick = playback::previous,
                )
                TransportButton(
                    icon = if (snapshot.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (snapshot.isPlaying) "暂停" else "播放",
                    group = group,
                    focusKey = "controls:play",
                    onClick = playback::toggle,
                    size = 68.dp,
                    primary = true,
                )
                TransportButton(
                    icon = Icons.Rounded.SkipNext,
                    contentDescription = "下一首",
                    group = group,
                    focusKey = "controls:next",
                    onClick = playback::next,
                )
                TransportButton(
                    icon = Icons.Rounded.Forward10,
                    contentDescription = "快进 10 秒",
                    group = group,
                    focusKey = "controls:forward",
                    onClick = { playback.seekBy(10_000L) },
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "返回 / ↓ 收起控制条 · 方向键在控制条内移动",
                style = TvType.caption,
                color = TvColors.TextTertiary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SourcePanel(
    track: DemoTrack,
    sources: List<String>,
    group: TvFocusGroup,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
) {
    TvSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        baseColor = TvColors.Surface.copy(alpha = 0.95f),
        focusedScale = 1.0f,
        glowSize = 0.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("播放音源", style = TvType.title, color = TvColors.TextPrimary)
                Spacer(Modifier.width(12.dp))
                Text(
                    "当前：${track.onlineSource.ifBlank { "本地" }} · 选择后重新解析并保留进度",
                    style = TvType.caption,
                    color = TvColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionChip(
                    label = "自动 / 重新解析",
                    group = group,
                    focusKey = "source:auto",
                    primary = true,
                    onClick = onRefresh,
                )
                ActionChip(
                    label = "关闭",
                    group = group,
                    focusKey = "source:close",
                    onClick = onClose,
                )
            }
            Spacer(Modifier.height(10.dp))
            sources.chunked(4).forEach { rowSources ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowSources.forEach { source ->
                        ActionChip(
                            label = sourceLabel(source),
                            group = group,
                            focusKey = "source:$source",
                            onClick = { onSelect(source) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

private fun sourceLabel(source: String): String = when (source) {
    "tx" -> "QQ音乐"
    "wy" -> "网易云"
    "kw" -> "酷我"
    "kg" -> "酷狗"
    "mg" -> "咪咕"
    else -> source
}

@Composable
private fun TransportButton(
    icon: ImageVector,
    contentDescription: String,
    group: TvFocusGroup,
    focusKey: String,
    onClick: () -> Unit,
    size: Dp = 54.dp,
    primary: Boolean = false,
) {
    val accent = LocalTvAccent.current
    TvSurface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        baseColor = if (primary) accent else TvColors.SurfaceHigh,
        focusedScale = 1.14f,
        glowSize = 14.dp,
        focusedBorderColor = if (primary) Color.White.copy(alpha = 0.92f) else null,
        focusedGlowColor = if (primary) Color.White.copy(alpha = 0.45f) else null,
        group = group,
        focusKey = focusKey,
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            TvIcon(
                icon = icon,
                modifier = Modifier.size(if (primary) 32.dp else 23.dp),
                tint = if (primary) Color(0xFF06110F) else TvColors.TextPrimary,
                contentDescription = contentDescription,
            )
        }
    }
}

@Composable
private fun QueuePanel(
    snapshot: PlayerSnapshot,
    group: TvFocusGroup,
    playback: PlaybackController,
    onClose: () -> Unit,
) {
    val capsule = LocalCapsuleHost.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var actionFor by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(actionFor) {
        if (actionFor != null) group.focusWhenReady("queue:action:up", timeoutMs = 1_000L)
    }
    fun focusRow(trackId: Int, index: Int) {
        scope.launch {
            listState.animateScrollToItem(index.coerceAtLeast(0))
            group.focusWhenReady("queue:$trackId", timeoutMs = 1_500L)
        }
    }
    TvSurface(
        modifier = Modifier.fillMaxHeight().width(392.dp),
        shape = RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp),
        baseColor = TvColors.Surface.copy(alpha = 0.98f),
        focusedScale = 1.0f,
        glowSize = 0.dp,
    ) {
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("播放队列", style = TvType.headline, color = TvColors.TextPrimary)
                Spacer(Modifier.width(8.dp))
                Text("${snapshot.queue.size} 首", style = TvType.caption, color = TvColors.TextTertiary)
                Spacer(Modifier.weight(1f))
                ActionChip(
                    label = "关闭",
                    group = group,
                    focusKey = "queue:close",
                    onClick = onClose,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "OK 切歌 · MENU 调整队列 · 返回收起",
                style = TvType.caption,
                color = TvColors.TextTertiary,
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(snapshot.queue, key = { _, track -> track.id }) { index, track ->
                    QueueRow(
                        track = track,
                        index = index,
                        isCurrent = track.id == snapshot.track.id,
                        group = group,
                        focusKey = "queue:${track.id}",
                        onClick = {
                            playback.playFromQueue(track)
                            onClose()
                        },
                        onKeyEvent = { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Menu) {
                                actionFor = index
                                true
                            } else {
                                false
                            }
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val actionIndex = actionFor
            val actionTrack = actionIndex?.let { snapshot.queue.getOrNull(it) }
            if (actionIndex != null && actionTrack != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionChip(
                        label = "上移",
                        group = group,
                        focusKey = "queue:action:up",
                        onClick = {
                            if (playback.moveInQueue(actionTrack, -1)) {
                                actionFor = null
                                focusRow(actionTrack.id, actionIndex - 1)
                            } else {
                                capsule.show("已经是第一首")
                            }
                        },
                    )
                    ActionChip(
                        label = "下移",
                        group = group,
                        focusKey = "queue:action:down",
                        onClick = {
                            if (playback.moveInQueue(actionTrack, 1)) {
                                actionFor = null
                                focusRow(actionTrack.id, actionIndex + 1)
                            } else {
                                capsule.show("已经是最后一首")
                            }
                        },
                    )
                    ActionChip(
                        label = "置顶",
                        group = group,
                        focusKey = "queue:action:top",
                        onClick = {
                            if (playback.moveToQueueTop(actionTrack)) {
                                actionFor = null
                                focusRow(actionTrack.id, 0)
                            } else {
                                capsule.show("已经在队列顶部")
                            }
                        },
                    )
                    ActionChip(
                        label = "删除",
                        group = group,
                        focusKey = "queue:action:delete",
                        onClick = {
                            if (playback.removeFromQueue(actionTrack)) {
                                actionFor = null
                                val next = actionIndex.coerceAtMost(snapshot.queue.size - 2).coerceAtLeast(0)
                                val target = snapshot.queue.filterNot { it.id == actionTrack.id }.getOrNull(next)
                                if (target != null) {
                                    focusRow(target.id, next)
                                } else {
                                    scope.launch { group.focusWhenReady("queue:close", timeoutMs = 1_000L) }
                                }
                            } else {
                                capsule.show("正在播放的歌曲不能删除")
                            }
                        },
                    )
                }
            } else {
                Text(
                    "在歌曲上按 MENU 可上移、下移、置顶或删除",
                    style = TvType.caption,
                    color = TvColors.TextTertiary,
                )
            }
        }
    }
}
