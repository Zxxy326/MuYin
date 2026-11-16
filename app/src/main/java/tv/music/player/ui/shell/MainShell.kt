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
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QueueMusic
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

private val ShellCorner = 12.dp
private val TabShape = RoundedCornerShape(15.dp)
private val MiniOrder = listOf(
    "mini:info", "mini:title", "mini:prev", "mini:toggle", "mini:next", "mini:favorite",
)

private data class ShellTab(val label: String, val icon: ImageVector)
private val ShellTabs = listOf(
    ShellTab("首页", Icons.Rounded.Home),
    ShellTab("搜索", Icons.Rounded.Search),
    ShellTab("歌单", Icons.Rounded.QueueMusic),
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
                else -> moreGroup.focusWhenReady("more:0", 500L)
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
                    Box(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 56.dp)) {
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
                    Box(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 56.dp)) {
                        PlaylistsScreen(
                            account = account,
                            playback = playback,
                            navController = navController,
                            embedded = true,
                        )
                    }
                }
                3 -> holder.SaveableStateProvider("charts") {
                    Box(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 56.dp)) {
                        ChartsTab(charts, playback, navController)
                    }
                }
                else -> holder.SaveableStateProvider("more") {
                    Box(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 56.dp)) {
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
            onGear = { tab = ShellTabs.lastIndex },
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
    clock: String,
    onTabFocused: (Int) -> Unit,
    onTabSelected: (Int) -> Unit,
    onGear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalTvAccent.current
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .height(44.dp)
            .shadow(20.dp, shape, clip = false)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xA6202938), Color(0xA60B1019))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.17f), shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.width(150.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasAvatar) {
                Image(
                    bitmap = avatar.image,
                    contentDescription = "账号头像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(28.dp).clip(CircleShape)
                        .border(1.5.dp, accent.copy(alpha = 0.7f), CircleShape),
                )
            } else {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "幕",
                        style = TextStyle(
                            fontFamily = TvFonts.family,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = Color(0xFF06110F),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "幕音",
                style = TextStyle(
                    fontFamily = TvFonts.family,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = TvColors.TextPrimary,
                maxLines = 1,
            )
        }
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShellTabs.forEachIndexed { index, item ->
                TvSurface(
                    modifier = Modifier.height(30.dp),
                    shape = TabShape,
                    baseColor = if (tab == index) accent.copy(alpha = 0.14f) else Color.Transparent,
                    focusedScale = 1.04f,
                    glowSize = 8.dp,
                    group = group,
                    focusKey = "shell:$index",
                    onFocusedChange = { if (it) onTabFocused(index) },
                    onClick = { onTabSelected(index) },
                ) {
                    Row(
                        Modifier.fillMaxHeight().padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TvIcon(
                            item.icon,
                            Modifier.size(14.dp),
                            tint = if (tab == index) accent.copy(alpha = 0.95f) else TvColors.TextSecondary,
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            item.label,
                            style = TextStyle(
                                fontFamily = TvFonts.family,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = if (tab == index) accent.copy(alpha = 0.95f) else TvColors.TextSecondary,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.width(2.dp))
            }
        }
        Row(
            Modifier.width(130.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                clock,
                style = TextStyle(
                    fontFamily = TvFonts.family,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = TvColors.TextPrimary.copy(alpha = 0.7f),
            )
            Spacer(Modifier.width(10.dp))
            TvSurface(
                modifier = Modifier.size(30.dp),
                shape = CircleShape,
                baseColor = Color.White.copy(alpha = 0.07f),
                focusedScale = 1.08f,
                glowSize = 8.dp,
                group = group,
                focusKey = "shell:gear",
                onFocusedChange = { if (it) onGear() },
                onClick = onGear,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    TvIcon(
                        Icons.Rounded.Settings,
                        Modifier.size(15.dp),
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
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    "榜单",
                    style = TextStyle(
                        fontFamily = TvFonts.family,
                        fontSize = 22.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = TvColors.TextPrimary,
                )
                Text(
                    "网易云 / QQ 音乐 · 焦点切榜，内容原地更新",
                    style = TextStyle(fontFamily = TvFonts.family, fontSize = 11.sp),
                    color = TvColors.TextSecondary,
                )
            }
            Spacer(Modifier.weight(1f))
            TvSurface(
                modifier = Modifier.height(32.dp),
                shape = RoundedCornerShape(ShellCorner),
                baseColor = accent.copy(alpha = 0.16f),
                focusedScale = 1.04f,
                glowSize = 10.dp,
                group = group,
                focusKey = "charts:playall",
                onClick = { tracks.firstOrNull()?.let(::play) },
            ) {
                Box(Modifier.fillMaxHeight().padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "播放全部",
                        style = TextStyle(
                            fontFamily = TvFonts.family,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = accent,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Charts.all.forEachIndexed { index, chart ->
                TvSurface(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = RoundedCornerShape(ShellCorner),
                    baseColor = if (selected == index) accent.copy(alpha = 0.15f) else TvColors.Surface.copy(alpha = 0.8f),
                    focusedScale = 1.02f,
                    glowSize = 8.dp,
                    group = group,
                    focusKey = "charts:$index",
                    onFocusedChange = { if (it) selected = index },
                    onClick = { selected = index },
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            chart.name,
                            style = TextStyle(
                                fontFamily = TvFonts.family,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = if (selected == index) accent else TvColors.TextPrimary,
                            maxLines = 1,
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
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                Charts.all[selected].name,
                style = TextStyle(
                    fontFamily = TvFonts.family,
                    fontSize = 16.sp,
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
        }
        Spacer(Modifier.height(4.dp))
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 4.dp),
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
        modifier = Modifier.fillMaxWidth().height(40.dp),
        shape = RoundedCornerShape(ShellCorner),
        baseColor = if (focused) TvColors.SurfaceHigh.copy(alpha = 0.7f) else Color.Transparent,
        focusedScale = 1f,
        focusedBorderWidth = 2.dp,
        glowSize = 6.dp,
        group = group,
        focusKey = "charts:track:$index",
        onFocusedChange = { focused = it },
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                (index + 1).toString().padStart(2, '0'),
                style = TextStyle(
                    fontFamily = TvFonts.family,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (focused) accent else TvColors.TextTertiary,
                modifier = Modifier.width(22.dp),
            )
            TrackArtwork(track = track, modifier = Modifier.size(26.dp), corner = 6.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = TextStyle(
                        fontFamily = TvFonts.family,
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = TvColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.artist,
                    style = TextStyle(fontFamily = TvFonts.family, fontSize = 10.sp, lineHeight = 13.sp),
                    color = TvColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MoreTab(account: AccountStore, navController: NavHostController) {
    val group = rememberTvFocusGroup("more")
    val accountState by account.state.collectAsStateWithLifecycle()
    val profile = accountState.netease ?: accountState.qq
    val accent = LocalTvAccent.current
    val entries = listOf(
        MoreEntry("个人界面", profile?.nickname?.takeIf { it.isNotBlank() } ?: "网易云 / QQ 账号与歌单", Icons.Rounded.Person) {
            navController.navigate(TvRoute.Mine.route) { launchSingleTop = true }
        },
        MoreEntry("音源管理", "本地脚本 · 市场安装", Icons.Rounded.QueueMusic) {
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
        MoreEntry("关于与更新", "版本信息 · 检查更新", Icons.Rounded.MoreHoriz) {
            navController.navigate(TvRoute.Settings.route) { launchSingleTop = true }
        },
    )

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    "更多",
                    style = TextStyle(
                        fontFamily = TvFonts.family,
                        fontSize = 22.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = TvColors.TextPrimary,
                )
                Text(
                    "账号、音源、收藏与设备管理",
                    style = TextStyle(fontFamily = TvFonts.family, fontSize = 11.sp),
                    color = TvColors.TextSecondary,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        entries.chunked(3).forEachIndexed { rowIndex, rowEntries ->
            Row(
                Modifier.fillMaxWidth().height(78.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowEntries.forEachIndexed { colIndex, entry ->
                    val index = rowIndex * 3 + colIndex
                    TvSurface(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        shape = RoundedCornerShape(ShellCorner),
                        baseColor = TvColors.Surface.copy(alpha = 0.82f),
                        focusedScale = 1.02f,
                        glowSize = 8.dp,
                        group = group,
                        focusKey = "more:$index",
                        onClick = entry.onClick,
                    ) {
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(34.dp).clip(RoundedCornerShape(9.dp))
                                    .background(accent.copy(alpha = 0.13f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                TvIcon(entry.icon, Modifier.size(17.dp), tint = accent)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    entry.label,
                                    style = TextStyle(
                                        fontFamily = TvFonts.family,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = TvColors.TextPrimary,
                                    maxLines = 1,
                                )
                                Text(
                                    entry.hint,
                                    style = TextStyle(fontFamily = TvFonts.family, fontSize = 10.sp),
                                    color = TvColors.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Text(
            "方向键移动焦点 · OK 进入 · 返回键回到顶栏",
            style = TextStyle(fontFamily = TvFonts.family, fontSize = 11.sp),
            color = TvColors.TextTertiary,
        )
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

@Composable
private fun rememberClock(): String {
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val time by produceState(initialValue = LocalTime.now().format(formatter)) {
        while (true) {
            delay(10_000L)
            value = LocalTime.now().format(formatter)
        }
    }
    return time
}
