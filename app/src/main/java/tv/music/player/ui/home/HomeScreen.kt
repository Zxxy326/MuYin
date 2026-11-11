package tv.music.player.ui.home

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import tv.music.player.core.charts.Charts
import tv.music.player.core.focus.TvFocusGroup
import tv.music.player.core.focus.TvSurface
import tv.music.player.core.focus.rememberTvFocusGroup
import tv.music.player.core.library.LibraryStore
import tv.music.player.core.library.libraryKey
import tv.music.player.core.playback.DemoCatalog
import tv.music.player.core.playback.PlaybackController
import tv.music.player.core.playback.DemoTrack
import tv.music.player.core.theme.LocalTvAccent
import tv.music.player.core.theme.TvColors
import tv.music.player.core.theme.TvDimens
import tv.music.player.core.theme.TvType
import tv.music.player.ui.components.ChartCard
import tv.music.player.ui.components.KeyHintBar
import tv.music.player.ui.components.Keycap
import tv.music.player.ui.components.LocalCapsuleHost
import tv.music.player.ui.components.MainTabBar
import tv.music.player.ui.components.PosterCard
import tv.music.player.ui.components.QuickTile
import tv.music.player.ui.components.SectionHeader
import tv.music.player.ui.components.TvBackdrop
import tv.music.player.ui.components.TvIcon
import tv.music.player.ui.components.TvRail
import tv.music.player.ui.components.WideCard
import tv.music.player.ui.navigation.LocalActiveRoute
import tv.music.player.ui.navigation.TvRoute

@Composable
fun HomeScreen(
    playback: PlaybackController,
    library: LibraryStore,
    navController: NavHostController,
) {
    val group = rememberTvFocusGroup("home")
    val listState = rememberLazyListState()
    val capsule = LocalCapsuleHost.current
    val recent by library.recent.collectAsStateWithLifecycle()
    val favorites by library.favorites.collectAsStateWithLifecycle()
    val continueTracks = remember(recent) {
        recent.take(6).ifEmpty { DemoCatalog.tracks.take(6) }
    }
    val guessTracks = remember(recent, favorites) { buildGuessTracks(recent, favorites) }
    val guessTrailing = if (recent.isEmpty()) "播放后自动推荐" else "根据最近播放 · 本地算法"
    val charts = Charts.all
    val snapshot by playback.state.collectAsStateWithLifecycle()

    val railOrder = listOf("continue", "guess", "charts")
    val continueState = rememberLazyListState()
    val guessState = rememberLazyListState()
    val chartsState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var railMoving by remember { mutableStateOf(false) }
    var quickIndex by remember { mutableStateOf(0) }

    fun railIndex(prefix: String): Int {
        val key = group.lastFocusedKey ?: return 0
        if (!key.startsWith("$prefix:")) return 0
        return key.removePrefix("$prefix:").toIntOrNull() ?: 0
    }

    fun railSize(id: String): Int = when (id) {
        "continue" -> continueTracks.size
        "guess" -> guessTracks.size
        else -> charts.size
    }

    fun railSection(id: String): Int = railOrder.indexOf(id).coerceAtLeast(0)

    fun railListState(id: String): LazyListState = when (id) {
        "continue" -> continueState
        "guess" -> guessState
        else -> chartsState
    }

    fun parseRailKey(key: String?): Pair<String, Int>? {
        if (key == null || !key.startsWith("rail:")) return null
        val parts = key.split(":")
        if (parts.size != 3) return null
        val index = parts[2].toIntOrNull() ?: return null
        return parts[1] to index
    }

    suspend fun scrollToRailItem(railId: String, index: Int) {
        listState.scrollToItem(railSection(railId))
        railListState(railId).scrollToItem((index - 1).coerceAtLeast(0))
    }

    suspend fun restoreHomeFocus() {
        withFrameNanos { }
        val target = group.takeRestoreTarget("search")
        val railTarget = parseRailKey(target)
        when {
            railTarget != null -> {
                scrollToRailItem(railTarget.first, railTarget.second)
                if (!group.focusWhenReady(target)) {
                    listState.scrollToItem(0)
                    delay(70)
                    group.focusWhenReady("search")
                }
            }
            else -> if (!group.focusWhenReady(target, timeoutMs = 4_000L)) {
                group.focusWhenReady("search", timeoutMs = 1_000L)
            }
        }
    }

    fun moveToRail(railId: String, index: Int) {
        if (railMoving) return
        railMoving = true
        val clamped = index.coerceIn(0, railSize(railId) - 1)
        scope.launch {
            try {
                scrollToRailItem(railId, clamped)
                group.focusWhenReady("rail:$railId:$clamped")
            } finally {
                railMoving = false
            }
        }
    }

    fun handleVerticalKey(activeKey: String?, delta: Int): Boolean {
        if (activeKey == null) return false
        if (activeKey.startsWith("quick:")) {
            val qi = activeKey.removePrefix("quick:").toIntOrNull() ?: 0
            quickIndex = qi
            if (delta < 0) {
                group.focus("search")
                return true
            }
            moveToRail("continue", qi)
            return true
        }
        if (activeKey == "search" || activeKey == "home:nowplaying") {
            if (delta < 0) {
                group.focus("home:settings")
                return true
            }
            group.focus("quick:$quickIndex")
            return true
        }
        if (activeKey == "home:settings") {
            if (delta > 0) {
                group.focus("search")
                return true
            }
            return false
        }
        val (railId, index) = parseRailKey(activeKey) ?: return false
        val position = railOrder.indexOf(railId)
        val nextId = railOrder.getOrNull(position + delta)
        if (nextId == null) {
            if (delta < 0) {
                group.focus("quick:$quickIndex")
            }
            return true
        }
        moveToRail(nextId, index)
        return true
    }

    val activeRoute = LocalActiveRoute.current
    val isActive = activeRoute == TvRoute.Home.route
    LaunchedEffect(isActive) {
        if (isActive) restoreHomeFocus()
    }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) {
        if (windowFocused && isActive) restoreHomeFocus()
    }

    val playTrack: (DemoTrack, List<DemoTrack>) -> Unit = { track, queue ->
        playback.play(track, queue)
        navController.navigate(TvRoute.Player.route) { launchSingleTop = true }
    }

    Box(Modifier.fillMaxSize()) {
        TvBackdrop()
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = TvDimens.SafeVertical)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) {
                            false
                        } else {
                            when {
                                event.key == Key.Menu -> {
                                    navController.navigate(TvRoute.Settings.route)
                                    true
                                }
                                event.key == Key.DirectionUp || event.key == Key.DirectionDown -> {
                                    val delta = if (event.key == Key.DirectionUp) -1 else 1
                                    handleVerticalKey(group.activeKey, delta)
                                }
                                else -> false
                            }
                        }
                    }
            ) {
                HomeHeaderRow(
                    group = group,
                    onSettings = { navController.navigate(TvRoute.Settings.route) },
                    modifier = Modifier.padding(horizontal = 26.dp),
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 26.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HomeSearchBar(
                        group = group,
                        onClick = { navController.navigate(TvRoute.Search.route) },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    NowPlayingBox(
                        group = group,
                        title = snapshot.track.title.takeIf { snapshot.track.usesPlayer },
                        onClick = { navController.navigate(TvRoute.Player.route) },
                        modifier = Modifier.width(230.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                HomeQuickRow(
                    group = group,
                    onDaily = { navController.navigate(TvRoute.Playlists.route) { popUpTo(TvRoute.Home.route); launchSingleTop = true } },
                    onFavorites = { navController.navigate(TvRoute.Favorites.route) },
                    onSquare = { navController.navigate(TvRoute.Playlists.route) { popUpTo(TvRoute.Home.route); launchSingleTop = true } },
                    onMine = { navController.navigate(TvRoute.Mine.route) { popUpTo(TvRoute.Home.route); launchSingleTop = true } },
                    onSources = { navController.navigate(TvRoute.SettingsGroup.of("sources")) },
                    modifier = Modifier.padding(horizontal = 26.dp),
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader(
                            "继续听",
                            modifier = Modifier.padding(horizontal = 26.dp),
                            trailing = if (recent.isEmpty()) "演示推荐 · 播放后自动记录" else "最近播放 · 前 6 首",
                        )
                        TvRail(
                            items = continueTracks,
                            keyPrefix = "rail:continue",
                            group = group,
                            initialIndex = railIndex("rail:continue"),
                            state = continueState,
                        ) { _, track, focusKey ->
                            WideCard(
                                track = track,
                                group = group,
                                focusKey = focusKey,
                                onClick = { playTrack(track, continueTracks) },
                            )
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader(
                            "猜你喜欢",
                            modifier = Modifier.padding(horizontal = 26.dp),
                            trailing = guessTrailing,
                        )
                        TvRail(
                            items = guessTracks,
                            keyPrefix = "rail:guess",
                            group = group,
                            initialIndex = railIndex("rail:guess"),
                            state = guessState,
                        ) { _, track, focusKey ->
                            PosterCard(
                                track = track,
                                group = group,
                                focusKey = focusKey,
                                onClick = { playTrack(track, guessTracks) },
                            )
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader(
                            "热门榜单",
                            modifier = Modifier.padding(horizontal = 26.dp),
                            trailing = "QQ 音乐 / 网易云 · 每日更新",
                        )
                        TvRail(
                            items = charts,
                            keyPrefix = "rail:charts",
                            group = group,
                            initialIndex = railIndex("rail:charts"),
                            state = chartsState,
                        ) { index, chart, focusKey ->
                            ChartCard(
                                name = chart.name,
                                subtitle = chart.subtitle,
                                index = index,
                                group = group,
                                focusKey = focusKey,
                                onClick = {
                                    navController.navigate(TvRoute.ChartDetail.of(chart.id))
                                },
                            )
                        }
                    }
                }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 26.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KeyHintBar(
                        hints = listOf(
                            "方向" to "移动焦点",
                            "OK" to "播放 / 进入",
                            "返回" to "上一级",
                            "长按返回" to "回到首页",
                            "MENU" to "设置",
                        )
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "实时取流 · LX 音源",
                        style = TvType.caption,
                        color = TvColors.TextTertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeaderRow(
    group: TvFocusGroup,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalTvAccent.current
    val greeting = remember {
        when (LocalTime.now().hour) {
            in 5..10 -> "早上好"
            in 11..13 -> "中午好"
            in 14..18 -> "下午好"
            else -> "晚上好"
        }
    }
    val clock = rememberClock()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$greeting，${if (greeting == "晚上好") "今晚听点什么？" else "今天想听点什么？"}",
            style = TvType.headline,
            color = TvColors.TextPrimary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(clock, style = TvType.headline, color = TvColors.TextPrimary)
        Spacer(Modifier.width(14.dp))
        TvSurface(
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            baseColor = TvColors.Surface.copy(alpha = 0.72f),
            focusedScale = 1.1f,
            glowSize = 12.dp,
            group = group,
            focusKey = "home:settings",
            onClick = onSettings,
        ) {
            TvIcon(Icons.Rounded.Settings, Modifier.size(20.dp), tint = TvColors.TextPrimary, contentDescription = "设置")
        }
    }
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

@Composable
private fun NowPlayingBox(
    group: TvFocusGroup,
    title: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalTvAccent.current
    TvSurface(
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(26.dp),
        baseColor = accent.copy(alpha = 0.16f),
        focusedScale = 1.03f,
        glowSize = 12.dp,
        group = group,
        focusKey = "home:nowplaying",
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvIcon(Icons.Rounded.MusicNote, Modifier.size(18.dp), tint = accent)
            Spacer(Modifier.width(8.dp))
            Text(
                title?.let { "正在播放 · $it" } ?: "正在播放",
                style = TvType.label,
                color = accent,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun HomeQuickRow(
    group: TvFocusGroup,
    onDaily: () -> Unit,
    onFavorites: () -> Unit,
    onSquare: () -> Unit,
    onMine: () -> Unit,
    onSources: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        QuickTile(Icons.Rounded.CalendarMonth, "每日推荐", group, "quick:0", onDaily, Modifier.weight(1f))
        QuickTile(Icons.Rounded.Favorite, "我的收藏", group, "quick:1", onFavorites, Modifier.weight(1f))
        QuickTile(Icons.Rounded.LibraryMusic, "歌单广场", group, "quick:2", onSquare, Modifier.weight(1f))
        QuickTile(Icons.Rounded.Person, "个人界面", group, "quick:3", onMine, Modifier.weight(1f))
        QuickTile(Icons.Rounded.Cloud, "音源管理", group, "quick:4", onSources, Modifier.weight(1f))
    }
}

@Composable
private fun HomeSearchBar(
    group: TvFocusGroup,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvSurface(
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(32.dp),
        baseColor = TvColors.Surface.copy(alpha = 0.9f),
        focusedScale = 1.015f,
        glowSize = 12.dp,
        group = group,
        focusKey = "search",
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvIcon(Icons.Rounded.Search, Modifier.size(24.dp), tint = LocalTvAccent.current)
            Spacer(Modifier.width(14.dp))
            Text(
                "搜索歌曲、歌手、专辑",
                style = TvType.body,
                color = TvColors.TextSecondary,
            )
            Spacer(Modifier.weight(1f))
            Keycap("OK", accent = true)
            Spacer(Modifier.width(6.dp))
            Text("进入 T9", style = TvType.caption, color = TvColors.TextTertiary)
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
    return (matched + fill).distinctBy { it.libraryKey() }.take(8)
}
