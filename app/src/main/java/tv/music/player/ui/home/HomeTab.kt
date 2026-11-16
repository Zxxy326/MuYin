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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
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

private val CardCorner = 16.dp
private val CoverCorner = 12.dp
private val CoverSize = 140.dp

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
    val guessSubtitle = if (recent.isEmpty()) "播放后自动推荐" else "根据最近播放 · ${guessTracks.size} 首"

    val railState = rememberLazyListState()
    var railMoving by remember { mutableStateOf(false) }

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

    fun focusRailAnimated(index: Int) {
        if (guessTracks.isEmpty() || railMoving) return
        val target = index.coerceIn(0, guessTracks.lastIndex)
        railMoving = true
        scope.launch {
            try {
                val visible = railState.layoutInfo.visibleItemsInfo
                if (visible.none { it.index == target }) railState.scrollToItem(target)
                group.focusWhenReady("rail:$target", 900L)
            } finally {
                railMoving = false
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
                Key.DirectionDown -> { focusRailAnimated(0); true }
                Key.DirectionLeft, Key.DirectionRight -> true
                else -> false
            }
            key.startsWith("rail:") -> {
                val index = key.removePrefix("rail:").toIntOrNull() ?: 0
                when (event.key) {
                    Key.DirectionUp -> { group.focus("home:playall"); true }
                    Key.DirectionDown -> { onFocusMini(); true }
                    Key.DirectionLeft -> {
                        if (index > 0) focusRailAnimated(index - 1)
                        true
                    }
                    Key.DirectionRight -> {
                        if (index < guessTracks.lastIndex) focusRailAnimated(index + 1)
                        true
                    }
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
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().height(40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "猜你喜欢",
                        style = TextStyle(
                            fontFamily = TvFonts.family,
                            fontSize = 20.sp,
                            lineHeight = 23.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.2).sp,
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
                    shape = RoundedCornerShape(14.dp),
                    baseColor = Color.White.copy(alpha = 0.06f),
                    focusedScale = 1.03f,
                    glowSize = 10.dp,
                    group = group,
                    focusKey = "home:playall",
                    onClick = ::playAllGuess,
                ) {
                    Row(
                        Modifier.fillMaxHeight().padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TvIcon(
                            Icons.Rounded.PlayArrow,
                            Modifier.size(13.dp),
                            tint = LocalTvAccent.current,
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "播放全部",
                            style = TextStyle(
                                fontFamily = TvFonts.family,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = TvColors.TextPrimary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            LazyRow(
                state = railState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                itemsIndexed(guessTracks) { index, track ->
                    GuessCard(
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

@Composable
private fun HomeHero(
    track: DemoTrack,
    cover: CoverArt,
    group: TvFocusGroup,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalTvAccent.current
    val shape = RoundedCornerShape(18.dp)
    val meta = remember(track.artist, track.album) {
        if (track.album.isBlank() || track.album == track.title) track.artist else "${track.artist} · ${track.album}"
    }
    val watermark = remember(track.title) { track.title.take(10).uppercase() }
    BoxWithConstraints(
        modifier.clip(shape).background(Color(0xFF0B0E15))
    ) {
        val markSize = (CoverSize.value * 0.8f).sp
        Image(
            bitmap = cover.image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(22.dp),
        )
        Box(Modifier.fillMaxSize().background(
            Brush.horizontalGradient(
                0f to Color(0xF205060A),
                0.42f to Color(0xD905060A),
                0.72f to Color(0x7805060A),
                1f to Color(0xB305060A),
            )
        ))
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(0f to Color(0x1405060A), 1f to Color(0xB305060A))
        ))
        Text(
            text = watermark,
            style = TextStyle(
                fontFamily = TvFonts.family,
                fontSize = markSize,
                lineHeight = markSize,
                fontWeight = FontWeight.Bold,
            ),
            color = Color.White.copy(alpha = 0.06f),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = CoverSize + 32.dp),
        )
        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 24.dp, top = 62.dp, end = CoverSize + 48.dp)
        ) {
            Text(
                text = "继续聆听",
                style = TextStyle(
                    fontFamily = TvFonts.family,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.2.sp,
                ),
                color = accent.copy(alpha = 0.92f),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = track.title,
                style = TextStyle(
                    fontFamily = TvFonts.family,
                    fontSize = 42.sp,
                    lineHeight = 46.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.8).sp,
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
            Spacer(Modifier.height(12.dp))
            TvSurface(
                modifier = Modifier.height(40.dp),
                shape = RoundedCornerShape(14.dp),
                baseColor = accent,
                focusedScale = 1.04f,
                glowSize = 14.dp,
                group = group,
                focusKey = "hero:play",
                onClick = onContinue,
            ) {
                Row(
                    Modifier.fillMaxHeight().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TvIcon(
                        Icons.Rounded.PlayArrow,
                        Modifier.size(15.dp),
                        tint = Color(0xFF08090E),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "继续播放",
                        style = TextStyle(
                            fontFamily = TvFonts.family,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = Color(0xFF08090E),
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
                .padding(top = 62.dp, end = 20.dp)
                .size(CoverSize)
                .clip(RoundedCornerShape(CoverCorner))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(CoverCorner)),
        )
    }
}

@Composable
private fun GuessCard(
    index: Int,
    track: DemoTrack,
    group: TvFocusGroup,
    onClick: () -> Unit,
) {
    val accent = LocalTvAccent.current
    var focused by remember(track.libraryKey()) { mutableStateOf(false) }
    val cover = rememberTrackCoverArt(track)
    TvSurface(
        modifier = Modifier.width(140.dp).fillMaxHeight(),
        shape = RoundedCornerShape(CardCorner),
        baseColor = if (focused) Color.White.copy(alpha = 0.05f) else Color.Transparent,
        focusedScale = 1.03f,
        glowSize = 12.dp,
        group = group,
        focusKey = "rail:$index",
        onFocusedChange = { focused = it },
        onClick = onClick,
    ) {
        Column(Modifier.fillMaxSize().padding(4.dp)) {
            Box(
                Modifier.size(132.dp)
                    .clip(RoundedCornerShape(CoverCorner))
                    .background(Color.White.copy(alpha = 0.04f))
            ) {
                Image(
                    bitmap = cover.image,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (focused) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(8.dp)
                            .size(28.dp).clip(CircleShape)
                            .background(Color(0xCC05070C))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        TvIcon(Icons.Rounded.PlayArrow, Modifier.size(15.dp), tint = accent)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = track.title,
                style = TextStyle(
                    fontFamily = TvFonts.family,
                    fontSize = 13.5.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = TvColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                style = TextStyle(fontFamily = TvFonts.family, fontSize = 10.5.sp, lineHeight = 14.sp),
                color = TvColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
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
