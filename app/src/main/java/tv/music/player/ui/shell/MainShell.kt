package tv.music.player.ui.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import tv.music.player.core.account.AccountStore
import tv.music.player.core.charts.ChartDef
import tv.music.player.core.charts.ChartRepository
import tv.music.player.core.charts.Charts
import tv.music.player.core.focus.TvFocusGroup
import tv.music.player.core.focus.TvSurface
import tv.music.player.core.focus.rememberTvFocusGroup
import tv.music.player.core.library.LibraryStore
import tv.music.player.core.library.libraryKey
import tv.music.player.core.lyrics.LyricsRepository
import tv.music.player.core.playback.DemoTrack
import tv.music.player.core.playback.PlaybackController
import tv.music.player.core.search.OnlineSearchApi
import tv.music.player.core.search.SearchHistoryStore
import tv.music.player.core.companion.CompanionBridge
import tv.music.player.core.theme.LocalTvAccent
import tv.music.player.core.theme.TvColors
import tv.music.player.core.theme.TvFonts
import tv.music.player.core.theme.rememberCoverArt
import tv.music.player.ui.components.MiniPlayerBar
import tv.music.player.ui.components.TrackArtwork
import tv.music.player.ui.components.TvBackdrop
import tv.music.player.ui.components.TvIcon
import tv.music.player.ui.home.HomeTab
import tv.music.player.ui.navigation.TvRoute
import tv.music.player.ui.playlists.PlaylistsScreen
import tv.music.player.ui.search.SearchScreen

private val TabShape = RoundedCornerShape(18.dp)
private val MiniOrder = listOf(
    "mini:info", "mini:title", "mini:prev", "mini:toggle", "mini:next", "mini:favorite",
)

private data class ShellTab(val label: String, val icon: ImageVector)
private val ShellTabs = listOf(
    ShellTab("首页", Icons.Rounded.Home),
    ShellTab("搜索", Icons.Rounded.Search),
    ShellTab("歌单", Icons.AutoMirrored.Rounded.QueueMusic),
    ShellTab("榜单", Icons.Rounded.BarChart),
    ShellTab("更多", Icons.Rounded.MoreHoriz),
)

private enum class ShellSection { Top, Content, Mini }

@Composable
fun MainShell(
    playback: PlaybackController,
    library: LibraryStore,
    account: AccountStore,
    lyricsRepository: LyricsRepository,
    lyricOffsetMs: Int,
    onlineSearch: OnlineSearchApi,
    searchHistory: SearchHistoryStore,
    companionBridge: CompanionBridge,
    charts: ChartRepository,
    navController: NavHostController,
) {
    val shellGroup = rememberTvFocusGroup("shell")
    val homeGroup = rememberTvFocusGroup("home")
    val searchGroup = rememberTvFocusGroup("search")
    val playlistsGroup = rememberTvFocusGroup("playlists")
    val chartsGroup = rememberTvFocusGroup("charts")
    val moreGroup = rememberTvFocusGroup("more")
    val miniGroup = rememberTvFocusGroup("mini")
    val scope = rememberCoroutineScope()
    val holder = rememberSaveableStateHolder()
    val accountState by account.state.collectAsStateWithLifecycle()
    val profile = accountState.netease ?: accountState.qq
    val avatarUrl = profile?.avatarUrl.orEmpty()
    val avatar = rememberCoverArt(avatarUrl.ifBlank { null })
    val clock = rememberClock()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var section by remember { mutableStateOf(ShellSection.Top) }

    fun focusTab(index: Int) {
        shellGroup.focus("shell:${index.coerceIn(0, ShellTabs.lastIndex)}")
    }

    fun enterPage() {
        val index = tab
        scope.launch {
            val ok = when (index) {
                0 -> homeGroup.focusWhenReady("hero:play", 500L)
                1 -> searchGroup.focusWhenReady("hot:0", 400L) ||
                    searchGroup.focusWhenReady("t9:4", 400L) ||
                    searchGroup.focusWhenReady("results:0", 400L)
                2 -> playlistsGroup.focusWhenReady("rec:0", 400L) ||
                    playlistsGroup.focusWhenReady("cat:0", 400L) ||
                    playlistsGroup.focusWhenReady("chart:0", 400L)
                3 -> chartsGroup.focusWhenReady("charts:0", 500L)
                else -> moreGroup.focusWhenReady("more:0", 500L) ||
                    moreGroup.focusWhenReady("more:account", 400L)
            }
            if (!ok) focusTab(index)
        }
    }

    fun handleKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (section == ShellSection.Content && event.key == Key.Back) {
            focusTab(tab)
            return true
        }
        when (section) {
            ShellSection.Top -> {
                val key = shellGroup.activeKey ?: return false
                val tabIndex = key.removePrefix("shell:").toIntOrNull()
                if (tabIndex != null) {
                    return when (event.key) {
                        Key.DirectionLeft -> {
                            if (tabIndex > 0) focusTab(tabIndex - 1)
                            true
                        }
                        Key.DirectionRight -> {
                            if (tabIndex < ShellTabs.lastIndex) focusTab(tabIndex + 1)
                            else shellGroup.focus("shell:gear")
                            true
                        }
                        Key.DirectionDown -> {
                            enterPage()
                            true
                        }
                        Key.DirectionUp -> true
                        else -> false
                    }
                }
                if (key == "shell:gear") {
                    return when (event.key) {
                        Key.DirectionLeft -> { focusTab(ShellTabs.lastIndex); true }
                        Key.DirectionRight, Key.DirectionUp -> true
                        Key.DirectionDown -> { enterPage(); true }
                        else -> false
                    }
                }
                return false
            }
            ShellSection.Mini -> {
                val current = MiniOrder.indexOf(miniGroup.activeKey).coerceAtLeast(0)
                return when (event.key) {
                    Key.DirectionUp, Key.Back -> { focusTab(tab); true }
                    Key.DirectionDown -> true
                    Key.DirectionLeft -> { miniGroup.focus(MiniOrder[(current - 1).coerceAtLeast(0)]); true }
                    Key.DirectionRight -> { miniGroup.focus(MiniOrder[(current + 1).coerceAtMost(MiniOrder.lastIndex)]); true }
                    else -> false
                }
            }
            ShellSection.Content -> return false
        }
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        shellGroup.focusWhenReady("shell:${tab.coerceIn(0, ShellTabs.lastIndex)}", 800L)
    }

    Box(Modifier.fillMaxSize().onPreviewKeyEvent { handleKey(it) }) {
        TvBackdrop()
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = 88.dp)
                .onFocusChanged { if (it.hasFocus) section = ShellSection.Content }
        ) {
            when (tab) {
                0 -> holder.SaveableStateProvider("home") {
                    Box(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    HomeTab(
                        playback = playback,
                        library = library,
                        navController = navController,
                        onBackToTabs = { focusTab(tab) },
                        onFocusMini = { scope.launch { miniGroup.focusWhenReady("mini:toggle", 600L) } },
                    )
                    }
                }
                1 -> holder.SaveableStateProvider("search") {
                    Box(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 68.dp)) {
                        SearchScreen(
                            playback = playback,
                            onlineSearch = onlineSearch,
                            searchHistory = searchHistory,
                            companionBridge = companionBridge,
                            navController = navController,
                            embedded = true,
                        )
                    }
                }
                2 -> holder.SaveableStateProvider("playlists") {
                    Box(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 68.dp)) {
                        PlaylistsScreen(
                            account = account,
                            playback = playback,
                            navController = navController,
                            embedded = true,
                        )
                    }
                }
                3 -> holder.SaveableStateProvider("charts") {
                    Box(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 68.dp)) {
                        ChartsTab(charts, playback, navController)
                    }
                }
                else -> holder.SaveableStateProvider("more") {
                    Box(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 68.dp)) {
                        MoreTab(account, navController)
                    }
                }
            }
        }

        TopBar(
            avatar = avatar,
            hasAvatar = avatarUrl.isNotBlank(),
            tab = tab,
            group = shellGroup,
            clock = clock,
            onTabFocused = { tab = it },
            onTabSelected = { tab = it },
            onGear = {
                navController.navigate(TvRoute.Settings.route) { launchSingleTop = true }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                .onFocusChanged { if (it.hasFocus) section = ShellSection.Top },
        )

        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                .align(Alignment.BottomCenter)
                .onFocusChanged { if (it.hasFocus) section = ShellSection.Mini }
        ) {
            MiniPlayerBar(
                playback = playback,
                lyricsRepository = lyricsRepository,
                library = library,
                offsetMs = lyricOffsetMs,
                onOpenPlayer = {
                    navController.navigate(TvRoute.Player.route) { launchSingleTop = true }
                },
            )
        }
    }
}

@Composable
private fun TopBar(
    avatar: tv.music.player.core.theme.CoverArt,
    hasAvatar: Boolean,
    tab: Int,
    group: TvFocusGroup,
    clock: ClockFace,
    onTabFocused: (Int) -> Unit,
    onTabSelected: (Int) -> Unit,
    onGear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalTvAccent.current
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier
            .height(52.dp)
            .shadow(24.dp, shape, clip = false)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xEB10141D), Color(0xEB05070C))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.width(178.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasAvatar) {
                Image(
                    bitmap = avatar.image,
                    contentDescription = "账号头像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(32.dp).clip(CircleShape)
                        .border(1.5.dp, accent.copy(alpha = 0.55f), CircleShape),
                )
            } else {
                Box(
                    Modifier.size(32.dp).clip(CircleShape)
                        .background(accent.copy(alpha = 0.16f))
                        .border(1.dp, accent.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "幕",
                        style = TextStyle(
                            fontFamily = TvFonts.family,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = accent,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    "幕音",
                    style = TextStyle(
                        fontFamily = TvFonts.family,
                        fontSize = 16.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.2.sp,
                    ),
                    color = TvColors.TextPrimary,
                    maxLines = 1,
                )
                Text(
                    "MUYIN · TV",
                    style = TextStyle(
                        fontFamily = TvFonts.family,
                        fontSize = 8.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.0.sp,
                    ),
                    color = TvColors.TextTertiary,
                    maxLines = 1,
                )
            }
        }
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShellTabs.forEachIndexed { index, item ->
                val active = tab == index
                TvSurface(
                    modifier = Modifier.height(36.dp),
                    shape = TabShape,
                    baseColor = if (active) accent.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.025f),
                    focusedScale = 1.02f,
                    glowSize = 10.dp,
                    group = group,
                    focusKey = "shell:$index",
                    onFocusedChange = { if (it) onTabFocused(index) },
                    onClick = { onTabSelected(index) },
                ) {
                    Row(
                        Modifier.fillMaxHeight().padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TvIcon(
                            item.icon,
                            Modifier.size(15.dp),
                            tint = if (active) accent else TvColors.TextSecondary,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            item.label,
                            style = TextStyle(
                                fontFamily = TvFonts.family,
                                fontSize = 13.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                            ),
                            color = if (active) accent else TvColors.TextSecondary,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.width(3.dp))
            }
        }
        Row(
            Modifier.width(150.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    clock.time,
                    style = TextStyle(
                        fontFamily = TvFonts.family,
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum",
                    ),
                    color = TvColors.TextPrimary.copy(alpha = 0.85f),
                )
                Text(
                    clock.weekday,
                    style = TextStyle(
                        fontFamily = TvFonts.family,
                        fontSize = 9.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.2.sp,
                    ),
                    color = TvColors.TextTertiary,
                )
            }
            Spacer(Modifier.width(10.dp))
            TvSurface(
                modifier = Modifier.size(34.dp),
                shape = CircleShape,
                baseColor = Color.White.copy(alpha = 0.055f),
                focusedScale = 1.08f,
                glowSize = 8.dp,
                group = group,
                focusKey = "shell:gear",
                onClick = onGear,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    TvIcon(
                        Icons.Rounded.Settings,
                        Modifier.size(16.dp),
                        tint = TvColors.TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartsTab(
    charts: ChartRepository,
    playback: PlaybackController,
    navController: NavHostController,
) {
    val group = rememberTvFocusGroup("charts")
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var tracks by remember { mutableStateOf<List<DemoTrack>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val accent = LocalTvAccent.current

    LaunchedEffect(selected) {
        loading = true
        tracks = runCatching { charts.load(Charts.all[selected]).tracks }.getOrDefault(emptyList())
        loading = false
    }

    fun play(track: DemoTrack) {
        playback.play(track, tracks)
        navController.navigate(TvRoute.Player.route) { launchSingleTop = true }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    "榜单",
                    style = TextStyle(
                        fontFamily = TvFonts.family,
                        fontSize = 24.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp,
                    ),
                    color = TvColors.TextPrimary,
                )
                Text(
                    "焦点切榜 · 内容原地更新",
                    style = TextStyle(fontFamily = TvFonts.family, fontSize = 11.sp),
                    color = TvColors.TextSecondary,
                )
            }
            Spacer(Modifier.weight(1f))
            TvSurface(
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(14.dp),
                baseColor = accent,
                focusedScale = 1.03f,
                glowSize = 12.dp,
                group = group,
                focusKey = "charts:playall",
                onClick = { tracks.firstOrNull()?.let(::play) },
            ) {
                Row(
                    Modifier.fillMaxHeight().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TvIcon(Icons.Rounded.PlayArrow, Modifier.size(15.dp), tint = Color(0xFF08090E))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "播放全部",
                        style = TextStyle(
                            fontFamily = TvFonts.family,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = Color(0xFF08090E),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(58.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Charts.all.forEachIndexed { index, chart ->
                val active = selected == index
                TvSurface(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = RoundedCornerShape(16.dp),
                    baseColor = if (active) accent.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.035f),
                    focusedScale = 1.015f,
                    glowSize = 10.dp,
                    group = group,
                    focusKey = "charts:$index",
                    onFocusedChange = { if (it) selected = index },
                    onClick = { selected = index },
                ) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "0${index + 1}",
                            style = TextStyle(
                                fontFamily = TvFonts.family,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum",
                            ),
                            color = if (active) accent.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.18f),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                chart.name,
                                style = TextStyle(
                                    fontFamily = TvFonts.family,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = if (active) accent else TvColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                chart.providerLabel(),
                                style = TextStyle(fontFamily = TvFonts.family, fontSize = 10.sp),
                                color = TvColors.TextSecondary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                Charts.all[selected].name,
                style = TextStyle(
                    fontFamily = TvFonts.family,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = TvColors.TextPrimary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (loading) "加载中…" else "共 ${tracks.size} 首",
                style = TextStyle(fontFamily = TvFonts.family, fontSize = 11.sp),
                color = TvColors.TextTertiary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "OK 播放",
                style = TextStyle(
                    fontFamily = TvFonts.family,
                    fontSize = 10.sp,
                    letterSpacing = 0.6.sp,
                ),
                color = TvColors.TextTertiary,
            )
        }
        Spacer(Modifier.height(6.dp))
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = 4.dp),
        ) {
            itemsIndexed(tracks) { index, track ->
                ChartTrackRow(
                    index = index,
                    track = track,
                    group = group,
                    onClick = { play(track) },
                )
            }
        }
    }
}

@Composable
private fun ChartTrackRow(
    index: Int,
    track: DemoTrack,
    group: TvFocusGroup,
    onClick: () -> Unit,
) {
    var focused by remember(track.libraryKey()) { mutableStateOf(false) }
    val accent = LocalTvAccent.current
    TvSurface(
        modifier = Modifier.fillMaxWidth().height(44.dp),
        shape = RoundedCornerShape(12.dp),
        baseColor = if (focused) accent.copy(alpha = 0.08f) else Color.Transparent,
        focusedScale = 1f,
        focusedBorderWidth = 2.dp,
        glowSize = 6.dp,
        group = group,
        focusKey = "charts:track:$index",
        onFocusedChange = { focused = it },
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                (index + 1).toString().padStart(2, '0'),
                style = TextStyle(
                    fontFamily = TvFonts.family,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum",
                ),
                color = if (focused) accent else TvColors.TextTertiary,
                modifier = Modifier.width(26.dp),
            )
            TrackArtwork(track = track, modifier = Modifier.size(28.dp), corner = 6.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
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
                    track.artist,
                    style = TextStyle(fontFamily = TvFonts.family, fontSize = 10.5.sp, lineHeight = 14.sp),
                    color = TvColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                formatDuration(track.durationMs),
                style = TextStyle(
                    fontFamily = TvFonts.family,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = "tnum",
                ),
                color = TvColors.TextTertiary,
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return ""
    val total = ms / 1000
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

@Composable
private fun MoreTab(account: AccountStore, navController: NavHostController) {
    val group = rememberTvFocusGroup("more")
    val accountState by account.state.collectAsStateWithLifecycle()
    val netease = accountState.netease
    val qq = accountState.qq
    val profile = netease ?: qq
    val avatarUrl = profile?.avatarUrl.orEmpty()
    val avatar = rememberCoverArt(avatarUrl.ifBlank { null })
    val accent = LocalTvAccent.current
    val connected = netease != null || qq != null
    val connectLabel = when {
        netease != null -> "网易云已连接"
        qq != null -> "QQ 音乐已连接"
        else -> "未登录"
    }
    val entries = listOf(
        MoreEntry("音源管理", "本地脚本 · 市场安装", Icons.AutoMirrored.Rounded.QueueMusic) {
            navController.navigate(TvRoute.SettingsGroup.of("sources")) { launchSingleTop = true }
        },
        MoreEntry("设置", "歌词、动效、更新与关于", Icons.Rounded.Settings) {
            navController.navigate(TvRoute.Settings.route) { launchSingleTop = true }
        },
        MoreEntry("我的收藏", "喜欢的歌与我的歌单", Icons.Rounded.Favorite) {
            navController.navigate(TvRoute.Favorites.route) { launchSingleTop = true }
        },
        MoreEntry("下载与本地", "下载歌曲 · 设备音乐 · SMB", Icons.Rounded.Download) {
            navController.navigate(TvRoute.Downloads.route) { launchSingleTop = true }
        },
    )

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    "更多",
                    style = TextStyle(
                        fontFamily = TvFonts.family,
                        fontSize = 24.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp,
                    ),
                    color = TvColors.TextPrimary,
                )
                Text(
                    "账号、音源、收藏与设备",
                    style = TextStyle(fontFamily = TvFonts.family, fontSize = 11.sp),
                    color = TvColors.TextSecondary,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                if (connected) "${accountState.playlists.size} 个歌单已同步" else "未登录也能本地播放",
                style = TextStyle(fontFamily = TvFonts.family, fontSize = 11.sp),
                color = TvColors.TextTertiary,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().height(206.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.width(280.dp).fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.02f))
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(16.dp))
                    .padding(18.dp)
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (avatarUrl.isNotBlank()) {
                            Image(
                                bitmap = avatar.image,
                                contentDescription = "账号头像",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(58.dp).clip(CircleShape)
                                    .border(1.5.dp, accent.copy(alpha = 0.55f), CircleShape),
                            )
                        } else {
                            Box(
                                Modifier.size(58.dp).clip(CircleShape)
                                    .background(accent.copy(alpha = 0.14f))
                                    .border(1.dp, accent.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "幕",
                                    style = TextStyle(
                                        fontFamily = TvFonts.family,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = accent,
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                "ACCOUNT",
                                style = TextStyle(
                                    fontFamily = TvFonts.family,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.6.sp,
                                ),
                                color = TvColors.TextTertiary,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                profile?.nickname?.takeIf { it.isNotBlank() } ?: "本地听众",
                                style = TextStyle(
                                    fontFamily = TvFonts.family,
                                    fontSize = 18.sp,
                                    lineHeight = 22.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = TvColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(6.dp).clip(CircleShape)
                                .background(if (connected) accent else TvColors.TextTertiary)
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            connectLabel,
                            style = TextStyle(fontFamily = TvFonts.family, fontSize = 11.sp),
                            color = TvColors.TextSecondary,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TvSurface(
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        shape = RoundedCornerShape(12.dp),
                        baseColor = Color.White.copy(alpha = 0.075f),
                        focusedScale = 1.02f,
                        glowSize = 8.dp,
                        group = group,
                        focusKey = "more:account",
                        onClick = {
                            navController.navigate(TvRoute.Account.route) { launchSingleTop = true }
                        },
                    ) {
                        Row(
                            Modifier.fillMaxHeight().padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (connected) "管理账号" else "登录账号",
                                style = TextStyle(
                                    fontFamily = TvFonts.family,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = TvColors.TextPrimary,
                            )
                            Spacer(Modifier.weight(1f))
                            TvIcon(
                                Icons.Rounded.ChevronRight,
                                Modifier.size(15.dp),
                                tint = TvColors.TextTertiary,
                            )
                        }
                    }
                }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                entries.chunked(2).forEachIndexed { rowIndex, rowEntries ->
                    Row(
                        Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        rowEntries.forEachIndexed { colIndex, entry ->
                            val index = rowIndex * 2 + colIndex
                            MoreToolCard(
                                entry = entry,
                                focusKey = "more:$index",
                                group = group,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "方向键移动焦点 · OK 进入 · 返回键回到顶栏",
            style = TextStyle(fontFamily = TvFonts.family, fontSize = 11.sp),
            color = TvColors.TextTertiary,
        )
    }
}

@Composable
private fun MoreToolCard(
    entry: MoreEntry,
    focusKey: String,
    group: TvFocusGroup,
    modifier: Modifier = Modifier,
) {
    val accent = LocalTvAccent.current
    TvSurface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        baseColor = Color.White.copy(alpha = 0.035f),
        focusedScale = 1.02f,
        glowSize = 10.dp,
        group = group,
        focusKey = focusKey,
        onClick = entry.onClick,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.12f))
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                TvIcon(entry.icon, Modifier.size(19.dp), tint = accent)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.label,
                    style = TextStyle(
                        fontFamily = TvFonts.family,
                        fontSize = 15.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = TvColors.TextPrimary,
                    maxLines = 1,
                )
                Text(
                    entry.hint,
                    style = TextStyle(fontFamily = TvFonts.family, fontSize = 10.5.sp, lineHeight = 14.sp),
                    color = TvColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TvIcon(
                Icons.Rounded.ChevronRight,
                Modifier.size(16.dp),
                tint = TvColors.TextTertiary,
            )
        }
    }
}

private data class MoreEntry(
    val label: String,
    val hint: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

private fun ChartDef.providerLabel(): String = when (provider) {
    "wy" -> "网易云 · 每日更新"
    "tx" -> "QQ 音乐 · 每日更新"
    else -> subtitle
}

private data class ClockFace(val time: String, val weekday: String)

@Composable
private fun rememberClock(): ClockFace {
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val weekdays = remember { listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日") }
    val initial = remember {
        ClockFace(
            LocalTime.now().format(timeFormatter),
            weekdays[LocalDate.now().dayOfWeek.value - 1],
        )
    }
    val face by produceState(initialValue = initial) {
        while (true) {
            delay(10_000L)
            value = ClockFace(
                LocalTime.now().format(timeFormatter),
                weekdays[LocalDate.now().dayOfWeek.value - 1],
            )
        }
    }
    return face
}
