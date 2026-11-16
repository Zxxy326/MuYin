package tv.music.player.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import tv.music.player.core.focus.TvFocusGroup
import tv.music.player.core.focus.TvSurface
import tv.music.player.core.focus.rememberTvFocusGroup
import tv.music.player.core.library.LibraryStore
import tv.music.player.core.library.libraryKey
import tv.music.player.core.playback.DemoCatalog
import tv.music.player.core.playback.DemoTrack
import tv.music.player.core.playback.PlaybackController
import tv.music.player.core.theme.CoverArt
import tv.music.player.core.theme.LocalTvAccent
import tv.music.player.core.theme.TvColors
import tv.music.player.core.theme.TvFonts
import tv.music.player.core.theme.rememberTrackCoverArt
import tv.music.player.ui.components.TrackArtwork
import tv.music.player.ui.components.TvIcon
import tv.music.player.ui.navigation.TvRoute

private val CardCorner = 12.dp
private val WyRed = Color(0xFFFF6F6F)
private val TxBlue = Color(0xFF7FB8FF)
private val LocalGreen = Color(0xFF6FD68A)

@Composable
fun HomeTab(
    playback: PlaybackController,
    library: LibraryStore,
    navController: NavHostController,
    onBackToTabs: () -> Unit,
    onFocusMini: () -> Unit,
) {
    val group = rememberTvFocusGroup("home")
    val scope = rememberCoroutineScope()
    val recent by library.recent.collectAsStateWithLifecycle()
    val favorites by library.favorites.collectAsStateWithLifecycle()
    val snapshot by playback.state.collectAsStateWithLifecycle()

    val rawGuess = remember(recent, favorites) { buildGuessTracks(recent, favorites) }
    val heroTrack = snapshot.track.takeIf { it.usesPlayer }
        ?: recent.firstOrNull()
        ?: DemoCatalog.tracks.firstOrNull()
    val heroCover: CoverArt = rememberTrackCoverArt(heroTrack ?: DemoCatalog.tracks.first())
    val heroKey = heroTrack?.libraryKey()
    val guessTracks = remember(rawGuess, heroKey) {
        if (heroKey == null) rawGuess
        else rawGuess.filter { it.libraryKey() != heroKey }.ifEmpty { rawGuess }
    }
    val guessSubtitle = if (recent.isEmpty()) "播放后自动推荐" else "根据最近播放 · 本地算法 · ${guessTracks.size} 首"

    val rowState = rememberLazyListState()
    var rowMoving by remember { mutableStateOf(false) }

    fun goPlayer() {
        navController.navigate(TvRoute.Player.route) { launchSingleTop = true }
    }

    fun playTrack(track: DemoTrack, queue: List<DemoTrack>) {
        playback.play(track, queue)
        goPlayer()
    }

    fun playAllGuess() {
        val first = guessTracks.firstOrNull() ?: return
        playTrack(first, guessTracks)
    }

    fun continueHero() {
        val track = heroTrack ?: return
        val isCurrent = snapshot.track.usesPlayer && track.libraryKey() == snapshot.track.libraryKey()
        if (isCurrent) {
            if (!snapshot.isPlaying) playback.toggle()
            goPlayer()
        } else {
            playTrack(track, listOf(track) + guessTracks)
        }
    }

    fun focusRowAnimated(index: Int) {
        if (guessTracks.isEmpty() || rowMoving) return
        val target = index.coerceIn(0, guessTracks.lastIndex)
        rowMoving = true
        scope.launch {
            try {
                val visible = rowState.layoutInfo.visibleItemsInfo
                if (visible.none { it.index == target }) rowState.scrollToItem(target)
                group.focusWhenReady("row:$target", 900L)
            } finally {
                rowMoving = false
            }
        }
    }

    fun handleKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val key = group.activeKey ?: return false
        return when {
            key == "hero:play" -> when (event.key) {
                Key.DirectionUp -> { onBackToTabs(); true }
                Key.DirectionDown -> { group.focus("home:playall"); true }
                Key.DirectionLeft, Key.DirectionRight -> true
                else -> false
            }
            key == "home:playall" -> when (event.key) {
                Key.DirectionUp -> { group.focus("hero:play"); true }
                Key.DirectionDown -> { focusRowAnimated(0); true }
                Key.DirectionLeft -> { group.focus("home:feature"); true }
                Key.DirectionRight -> true
                else -> false
            }
            key == "home:feature" -> when (event.key) {
                Key.DirectionUp -> { group.focus("home:playall"); true }
                Key.DirectionDown -> { onFocusMini(); true }
                Key.DirectionRight -> { focusRowAnimated(0); true }
                Key.DirectionLeft -> true
                else -> false
            }
            key.startsWith("row:") -> {
                val index = key.removePrefix("row:").toIntOrNull() ?: 0
                when (event.key) {
                    Key.DirectionUp -> {
                        if (index == 0) group.focus("home:playall") else focusRowAnimated(index - 1)
                        true
                    }
                    Key.DirectionDown -> {
                        if (index >= guessTracks.lastIndex) onFocusMini() else focusRowAnimated(index + 1)
                        true
                    }
                    Key.DirectionLeft -> { group.focus("home:feature"); true }
                    Key.DirectionRight -> true
                    else -> false
                }
            }
            else -> false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { handleKey(it) }
    ) {
        Column(Modifier.fillMaxSize()) {
            if (heroTrack != null) {
                HomeHero(
                    track = heroTrack,
                    cover = heroCover,
                    group = group,
                    onContinue = ::continueHero,
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().height(42.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "猜你喜欢",
                        style = TextStyle(
                            fontFamily = TvFonts.family,
                            fontSize = 20.sp,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = TvColors.TextPrimary,
                    )
                    Text(
                        text = guessSubtitle,
                        style = TextStyle(
                            fontFamily = TvFonts.family,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                        ),
                        color = TvColors.TextSecondary,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.weight(1f))
                TvSurface(
                    modifier = Modifier.height(34.dp),
                    shape = RoundedCornerShape(CardCorner),
                    baseColor = LocalTvAccent.current.copy(alpha = 0.16f),
                    focusedScale = 1.04f,
                    glowSize = 10.dp,
                    group = group,
                    focusKey = "home:playall",
                    onClick = ::playAllGuess,
                ) {
                    Box(
                        Modifier.fillMaxHeight().padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "播放全部",
                            style = TextStyle(
                                fontFamily = TvFonts.family,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = LocalTvAccent.current,
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().weight(1f)) {
                TvSurface(
                    modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                    shape = RoundedCornerShape(CardCorner),
                    baseColor = Color.Transparent,
                    focusedScale = 1.02f,
                    glowSize = 12.dp,
                    group = group,
                    focusKey = "home:feature",
                    onClick = ::playAllGuess,
                ) {
                    Image(
                        bitmap = heroCover.image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.width(14.dp))
                LazyColumn(
                    state = rowState,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = PaddingValues(0.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(guessTracks) { index, track ->
                        GuessRow(
                            index = index,
                            track = track,
                            group = group,
                            onClick = { playTrack(track, guessTracks) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHero(
    track: DemoTrack,
    cover: CoverArt,
    group: TvFocusGroup,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalTvAccent.current
    val shape = RoundedCornerShape(CardCorner)
    val meta = remember(track.artist, track.album) {
        if (track.album.isBlank() || track.album == track.title) track.artist else "${track.artist} · ${track.album}"
    }
    val watermark = remember(track.title) { track.title.take(10).uppercase() }
    BoxWithConstraints(
        modifier.clip(shape).background(Color(0xFF0B0F16))
    ) {
        val coverSize = (maxHeight * 0.65f).coerceAtMost(150.dp)
        val markSize = (coverSize.value * 0.8f).sp
        Image(
            bitmap = cover.image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(18.dp),
        )
        Box(Modifier.fillMaxSize().background(
            Brush.horizontalGradient(
                0f to Color(0xF205080D),
                0.45f to Color(0xC705080D),
                0.72f to Color(0x6605080D),
                1f to Color(0x9905080D),
            )
        ))
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(0f to Color(0x1A05080D), 1f to Color(0x9905080D))
        ))
        Text(
            text = watermark,
            style = TextStyle(
                fontFamily = TvFonts.family,
                fontSize = markSize,
                lineHeight = markSize,
                fontWeight = FontWeight.Bold,
            ),
            color = Color.White.copy(alpha = 0.07f),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = coverSize + 22.dp),
        )
        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 20.dp, top = 56.dp, end = coverSize + 30.dp)
        ) {
            Text(
                text = "继续聆听",
                style = TextStyle(
                    fontFamily = TvFonts.family,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = accent.copy(alpha = 0.92f),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = track.title,
                style = TextStyle(
                    fontFamily = TvFonts.family,
                    fontSize = 34.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = TvColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = meta,
                style = TextStyle(fontFamily = TvFonts.family, fontSize = 13.sp),
                color = TvColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            TvSurface(
                modifier = Modifier.height(38.dp),
                shape = RoundedCornerShape(CardCorner),
                baseColor = accent,
                focusedScale = 1.05f,
                glowSize = 12.dp,
                focusedBorderColor = Color.White.copy(alpha = 0.9f),
                focusedGlowColor = Color.White.copy(alpha = 0.38f),
                group = group,
                focusKey = "hero:play",
                onClick = onContinue,
            ) {
                Box(
                    Modifier.fillMaxHeight().padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "继续播放",
                        style = TextStyle(
                            fontFamily = TvFonts.family,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = Color(0xFF06110F),
                    )
                }
            }
        }
        Image(
            bitmap = cover.image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 54.dp, end = 18.dp)
                .size(coverSize)
                .clip(shape)
                .border(1.dp, Color.White.copy(alpha = 0.16f), shape),
        )
    }
}

@Composable
private fun GuessRow(
    index: Int,
    track: DemoTrack,
    group: TvFocusGroup,
    onClick: () -> Unit,
) {
    val accent = LocalTvAccent.current
    var focused by remember(track.libraryKey()) { mutableStateOf(false) }
    val source = remember(track) { sourceLabel(track) }
    TvSurface(
        modifier = Modifier.fillMaxWidth().height(44.dp),
        shape = RoundedCornerShape(CardCorner),
        baseColor = if (focused) TvColors.SurfaceHigh.copy(alpha = 0.72f) else Color.Transparent,
        focusedScale = 1f,
        focusedBorderWidth = 2.dp,
        glowSize = 8.dp,
        group = group,
        focusKey = "row:$index",
        onFocusedChange = { focused = it },
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = (index + 1).toString().padStart(2, '0'),
                style = TextStyle(
                    fontFamily = TvFonts.family,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (focused) accent else TvColors.TextTertiary,
                modifier = Modifier.width(22.dp),
            )
            TrackArtwork(track = track, modifier = Modifier.size(28.dp), corner = 6.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = TextStyle(
                        fontFamily = TvFonts.family,
                        fontSize = 15.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = TvColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artist,
                    style = TextStyle(
                        fontFamily = TvFonts.family,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                    ),
                    color = TvColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (source.first.isNotBlank()) {
                Text(
                    text = source.first,
                    style = TextStyle(
                        fontFamily = TvFonts.family,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = source.second,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun sourceLabel(track: DemoTrack): Pair<String, Color> = when {
    track.isLocal -> "本地" to LocalGreen
    track.onlineSource == "wy" -> "网易云" to WyRed
    track.onlineSource == "tx" -> "QQ 音乐" to TxBlue
    track.onlineSource.isNotBlank() -> track.onlineSource.uppercase() to TvColors.TextSecondary
    else -> "" to TvColors.TextTertiary
}

private fun buildGuessTracks(
    recent: List<DemoTrack>,
    favorites: List<DemoTrack>,
): List<DemoTrack> {
    if (recent.isEmpty() && favorites.isEmpty()) {
        return DemoCatalog.tracks.drop(4).take(8)
    }
    val recentKeys = recent.map { it.libraryKey() }.toSet()
    val artistHits = recent.map { it.artist }.filter { it.isNotBlank() }.distinct().take(6).toSet()
    val pool = (favorites + recent + DemoCatalog.tracks).distinctBy { it.libraryKey() }
    val matched = pool.filter { it.artist in artistHits && it.libraryKey() !in recentKeys }
    val fill = DemoCatalog.tracks.filter { it.libraryKey() !in recentKeys }
    val merged = (matched + fill).distinctBy { it.libraryKey() }
    if (merged.isNotEmpty()) return merged.take(8)
    return (favorites + recent).distinctBy { it.libraryKey() }.take(8)
}
