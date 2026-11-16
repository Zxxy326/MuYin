package tv.music.player.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
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
import tv.music.player.core.lyrics.LyricsRepository
import tv.music.player.core.lyrics.LyricsState
import tv.music.player.core.playback.DemoCatalog
import tv.music.player.core.playback.DemoTrack
import tv.music.player.core.playback.PlaybackController
import tv.music.player.core.theme.LocalTvAccent
import tv.music.player.core.theme.TvColors
import tv.music.player.core.theme.TvFonts
import tv.music.player.core.theme.rememberTrackCoverArt
import tv.music.player.ui.components.TvIcon
import tv.music.player.ui.navigation.TvRoute

private val ActionOrder = listOf("hero:play", "hero:prev", "hero:next", "hero:fav", "hero:player")
private val TextShadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 2f), 12f)
private val CreditLine = Regex(
    """^(作词|作曲|编曲|制作人|监制|混音|母带|录音|吉他|贝斯|鼓|和声|弦乐|统筹|出品|发行|词|曲|OP|SP)\s*[:：]""",
    RegexOption.IGNORE_CASE,
)

@Composable
fun HomeTab(
    playback: PlaybackController,
    library: LibraryStore,
    lyricsRepository: LyricsRepository,
    lyricOffsetMs: Int,
    navController: NavHostController,
    onBackToTabs: () -> Unit,
) {
    val group = rememberTvFocusGroup("home")
    val scope = rememberCoroutineScope()
    val recent by library.recent.collectAsStateWithLifecycle()
    val favorites by library.favorites.collectAsStateWithLifecycle()
    val snapshot by playback.state.collectAsStateWithLifecycle()

    val isLive = snapshot.track.usesPlayer
    val isPlaying = isLive && snapshot.isPlaying
    val heroTrack = if (isLive) snapshot.track else recent.firstOrNull() ?: DemoCatalog.tracks.first()
    val heroKey = heroTrack.libraryKey()
    val isFavorite = favorites.any { it.libraryKey() == heroKey }
    val rawGuess = remember(recent, favorites) { buildGuessTracks(recent, favorites) }
    val guessTracks = remember(rawGuess, heroKey) {
        rawGuess.filter { it.libraryKey() != heroKey }.ifEmpty { rawGuess }
    }
    val guessSubtitle = if (recent.isEmpty()) "播放后自动推荐" else "根据最近播放 · ${guessTracks.size} 首"

    var lyricsState by remember(heroTrack.id) { mutableStateOf<LyricsState?>(null) }
    LaunchedEffect(heroTrack.id) {
        lyricsState = lyricsRepository.load(heroTrack)
    }
    val lyricLine = if (isLive) {
        (lyricsState as? LyricsState.Ready)?.lyrics?.let { lyrics ->
            val index = lyrics.indexAt(snapshot.positionMs, lyricOffsetMs.toLong()).coerceAtLeast(0)
            lyrics.lines.getOrNull(index)?.text?.takeIf { it.isNotBlank() }
        }
    } else null

    val meta = if (heroTrack.album.isBlank() || heroTrack.album == heroTrack.title) {
        heroTrack.artist
    } else {
        "${heroTrack.artist} · ${heroTrack.album}"
    }
    val kicker = when {
        isPlaying -> "正在播放"
        isLive -> "已暂停"
        recent.isNotEmpty() -> "继续聆听"
        else -> "为你推荐"
    }
    val ctaLabel = when {
        isPlaying -> "正在播放"
        isLive -> "继续播放"
        else -> "开始播放"
    }
    val description = when {
        isLive -> lyricLine?.takeIf { !CreditLine.containsMatchIn(it) }
            ?: "来自 ${heroTrack.album.ifBlank { heroTrack.artist }}"
        recent.isNotEmpty() -> "从上次听到的地方继续 · 按 OK 播放"
        else -> "从推荐歌曲开始 · 按 OK 播放"
    }

    fun startHero() {
        playback.play(heroTrack, (listOf(heroTrack) + guessTracks).distinctBy { it.libraryKey() })
    }

    fun primary() {
        if (isLive) playback.toggle() else startHero()
    }

    fun previous() {
        if (isLive) playback.previous() else startHero()
    }

    fun next() {
        if (isLive) playback.next() else startHero()
    }

    fun playFromRail(track: DemoTrack) {
        playback.play(track, guessTracks)
        navController.navigate(TvRoute.Player.route) { launchSingleTop = true }
    }

    val railState = rememberLazyListState()
    var railMoving by remember { mutableStateOf(false) }

    fun focusRailAnimated(index: Int) {
        if (guessTracks.isEmpty() || railMoving) return
        val target = index.coerceIn(0, guessTracks.lastIndex)
        railMoving = true
        scope.launch {
            try {
                if (railState.layoutInfo.visibleItemsInfo.none { it.index == target }) {
                    railState.scrollToItem(target)
                }
                group.focusWhenReady("rail:$target", 900L)
            } finally {
                railMoving = false
            }
        }
    }

    fun focusAction(key: String, delta: Int): Boolean {
        val index = ActionOrder.indexOf(key)
        if (index < 0) return false
        val target = (index + delta).coerceIn(0, ActionOrder.lastIndex)
        if (target == index) return true
        group.focus(ActionOrder[target])
        return true
    }

    fun handleKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val key = group.activeKey ?: return false
        return when {
            key in ActionOrder -> when (event.key) {
                Key.DirectionUp -> { onBackToTabs(); true }
                Key.DirectionDown -> { focusRailAnimated(0); true }
                Key.DirectionLeft -> focusAction(key, -1)
                Key.DirectionRight -> focusAction(key, 1)
                else -> false
            }
            key.startsWith("rail:") -> {
                val index = key.removePrefix("rail:").toIntOrNull() ?: 0
                when (event.key) {
                    Key.DirectionUp -> { group.focus("hero:play"); true }
                    Key.DirectionDown -> true
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
        Crossfade(
            targetState = heroTrack,
            animationSpec = tween(500),
            label = "home-poster",
        ) { track ->
            Image(
                bitmap = rememberTrackCoverArt(track).image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color(0xE605060A),
                    0.38f to Color(0xB805060A),
                    0.72f to Color(0x2605060A),
                    1f to Color(0x6605060A),
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0x8C05060A),
                    0.3f to Color(0x1405060A),
                    0.58f to Color(0x0D05060A),
                    0.8f to Color(0x8C05060A),
                    1f to Color(0xF205060A),
                )
            )
        )

        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
            Spacer(Modifier.height(112.dp))
            Column(Modifier.widthIn(max = 620.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(5.dp).clip(CircleShape)
                            .background(if (isPlaying) LocalTvAccent.current else Color.White.copy(alpha = 0.4f))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        kicker,
                        style = TextStyle(
                            fontFamily = TvFonts.family,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 2.2.sp,
                            shadow = TextShadow,
                        ),
                        color = Color.White.copy(alpha = 0.72f),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    heroTrack.title,
                    style = TextStyle(
                        fontFamily = TvFonts.family,
                        fontSize = 46.sp,
                        lineHeight = 52.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1.0).sp,
                        shadow = TextShadow,
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    meta,
                    style = TextStyle(
                        fontFamily = TvFonts.family,
                        fontSize = 13.sp,
                        shadow = TextShadow,
                    ),
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    description,
                    style = TextStyle(
                        fontFamily = TvFonts.family,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        shadow = TextShadow,
                    ),
                    color = Color.White.copy(alpha = 0.68f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TvSurface(
                        modifier = Modifier.width(132.dp).height(44.dp),
                        shape = RoundedCornerShape(22.dp),
                        baseColor = Color.Black.copy(alpha = 0.34f),
                        focusedScale = 1.03f,
                        glowSize = 14.dp,
                        group = group,
                        focusKey = "hero:play",
                        onClick = ::primary,
                    ) {
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TvIcon(
                                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                Modifier.size(17.dp),
                                tint = Color.White,
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                ctaLabel,
                                style = TextStyle(
                                    fontFamily = TvFonts.family,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = Color.White,
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    HeroIconButton(
                        icon = Icons.Rounded.SkipPrevious,
                        description = "上一首",
                        focusKey = "hero:prev",
                        group = group,
                        onClick = ::previous,
                    )
                    Spacer(Modifier.width(8.dp))
                    HeroIconButton(
                        icon = Icons.Rounded.SkipNext,
                        description = "下一首",
                        focusKey = "hero:next",
                        group = group,
                        onClick = ::next,
                    )
                    Spacer(Modifier.width(8.dp))
                    HeroIconButton(
                        icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        description = if (isFavorite) "取消收藏" else "收藏",
                        focusKey = "hero:fav",
                        group = group,
                        tint = if (isFavorite) Color(0xFFFF7A8C) else Color.White,
                        onClick = { library.toggleFavorite(heroTrack) },
                    )
                    Spacer(Modifier.width(18.dp))
                    HeroIconButton(
                        icon = Icons.Rounded.OpenInFull,
                        description = "播放页",
                        focusKey = "hero:player",
                        group = group,
                        onClick = {
                            navController.navigate(TvRoute.Player.route) { launchSingleTop = true }
                        },
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "猜你喜欢",
                    style = TextStyle(
                        fontFamily = TvFonts.family,
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp,
                        shadow = TextShadow,
                    ),
                    color = Color.White,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    guessSubtitle,
                    style = TextStyle(
                        fontFamily = TvFonts.family,
                        fontSize = 11.sp,
                        shadow = TextShadow,
                    ),
                    color = Color.White.copy(alpha = 0.58f),
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(
                state = railState,
                modifier = Modifier.fillMaxWidth().height(128.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                itemsIndexed(guessTracks) { index, track ->
                    PosterTrackCard(
                        index = index,
                        track = track,
                        group = group,
                        onClick = { playFromRail(track) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun HeroIconButton(
    icon: ImageVector,
    description: String,
    focusKey: String,
    group: TvFocusGroup,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    TvSurface(
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        baseColor = Color.Black.copy(alpha = 0.32f),
        focusedScale = 1.1f,
        glowSize = 12.dp,
        group = group,
        focusKey = focusKey,
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            TvIcon(icon, Modifier.size(19.dp), tint = tint, contentDescription = description)
        }
    }
}

@Composable
private fun PosterTrackCard(
    index: Int,
    track: DemoTrack,
    group: TvFocusGroup,
    onClick: () -> Unit,
) {
    val accent = LocalTvAccent.current
    var focused by remember(track.libraryKey()) { mutableStateOf(false) }
    val cover = rememberTrackCoverArt(track)
    TvSurface(
        modifier = Modifier.width(150.dp).height(128.dp),
        shape = RoundedCornerShape(12.dp),
        baseColor = if (focused) Color.White.copy(alpha = 0.08f) else Color.Transparent,
        focusedScale = 1.035f,
        glowSize = 14.dp,
        group = group,
        focusKey = "rail:$index",
        onFocusedChange = { focused = it },
        onClick = onClick,
    ) {
        Column(Modifier.fillMaxSize().padding(4.dp)) {
            Box(
                Modifier.fillMaxWidth().height(84.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color.White.copy(alpha = 0.05f))
            ) {
                Image(
                    bitmap = cover.image,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (focused) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(6.dp)
                            .size(24.dp).clip(CircleShape)
                            .background(Color(0xCC05070C)),
                        contentAlignment = Alignment.Center,
                    ) {
                        TvIcon(Icons.Rounded.PlayArrow, Modifier.size(14.dp), tint = accent)
                    }
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                track.title,
                style = TextStyle(
                    fontFamily = TvFonts.family,
                    fontSize = 13.5.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    shadow = TextShadow,
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist,
                style = TextStyle(
                    fontFamily = TvFonts.family,
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp,
                    shadow = TextShadow,
                ),
                color = Color.White.copy(alpha = 0.62f),
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
