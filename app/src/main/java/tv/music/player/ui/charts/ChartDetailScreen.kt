package tv.music.player.ui.charts

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.music.player.core.charts.ChartDef
import tv.music.player.core.charts.ChartRepository
import tv.music.player.core.focus.TvFocusGroup
import tv.music.player.core.focus.TvSurface
import tv.music.player.core.focus.rememberTvFocusGroup
import tv.music.player.core.playback.DemoTrack
import tv.music.player.core.playback.PlaybackController
import tv.music.player.core.playback.formatDuration
import tv.music.player.core.theme.LocalTvAccent
import tv.music.player.core.theme.TvColors
import tv.music.player.core.theme.TvType
import tv.music.player.ui.components.ActionChip
import tv.music.player.ui.components.KeyHintBar
import tv.music.player.ui.components.LocalCapsuleHost
import tv.music.player.ui.components.ScreenHeader
import tv.music.player.ui.components.SkeletonBox
import tv.music.player.ui.components.TrackArtwork
import tv.music.player.ui.components.TvBackdrop
import tv.music.player.ui.components.TvIcon
import tv.music.player.ui.components.TvSafeArea
import tv.music.player.ui.navigation.LocalActiveRoute
import tv.music.player.ui.navigation.TvRoute

@Composable
fun ChartDetailScreen(
    chart: ChartDef,
    repository: ChartRepository,
    playback: PlaybackController,
    navController: NavHostController,
) {
    val group = rememberTvFocusGroup("chart:${chart.id}")
    val capsule = LocalCapsuleHost.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val activeRoute = LocalActiveRoute.current
    val isActive = activeRoute == TvRoute.ChartDetail.route

    var tracks by remember(chart.id) { mutableStateOf<List<DemoTrack>>(emptyList()) }
    var updateTime by remember(chart.id) { mutableStateOf("") }
    var total by remember(chart.id) { mutableIntStateOf(0) }
    var loading by remember(chart.id) { mutableStateOf(true) }
    var error by remember(chart.id) { mutableStateOf<String?>(null) }
    var attempt by remember(chart.id) { mutableIntStateOf(0) }
    var actionFor by remember(chart.id) { mutableStateOf<Int?>(null) }

    LaunchedEffect(chart.id, attempt) {
        loading = true
        error = null
        actionFor = null
        runCatching { repository.load(chart) }
            .onSuccess {
                tracks = it.tracks
                updateTime = it.updateTime
                total = it.total
                loading = false
            }
            .onFailure {
                error = it.message ?: "榜单加载失败"
                loading = false
            }
    }

    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(isActive, windowFocused, loading, error, tracks.size) {
        if (!isActive || !windowFocused || loading) return@LaunchedEffect
        delay(80)
        if (error != null) {
            group.focusWhenReady("chart:retry", timeoutMs = 1_200L)
        } else if (tracks.isNotEmpty()) {
            group.focusWhenReady("chart:row:0", timeoutMs = 1_500L)
        }
    }

    LaunchedEffect(actionFor) {
        if (actionFor != null) group.focusWhenReady("chart:action:next", timeoutMs = 1_000L)
    }

    fun focusRow(index: Int) {
        scope.launch {
            listState.animateScrollToItem(index.coerceAtLeast(0))
            group.focusWhenReady("chart:row:${index}", timeoutMs = 1_500L)
        }
    }

    fun play(track: DemoTrack) {
        playback.play(track, tracks)
        navController.navigate(TvRoute.Player.route) { launchSingleTop = true }
    }

    Box(Modifier.fillMaxSize()) {
        TvBackdrop()
        TvSafeArea {
            Column(Modifier.fillMaxSize()) {
                ScreenHeader(
                    title = chart.name,
                    subtitle = buildString {
                        append(chart.subtitle)
                        if (total > 0) append(" · 共 $total 首")
                        if (updateTime.isNotBlank()) append(" · $updateTime")
                    },
                    group = group,
                    onBack = { navController.popBackStack() },
                    trailing = {
                        Text(
                            if (tracks.isEmpty()) "" else "已加载 ${tracks.size} 首",
                            style = TvType.caption,
                            color = TvColors.TextTertiary,
                        )
                    },
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ActionChip(
                        label = "播放全部",
                        icon = Icons.Rounded.PlayArrow,
                        group = group,
                        focusKey = "chart:play",
                        primary = true,
                        onClick = { tracks.firstOrNull()?.let(::play) },
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "OK 从这首歌播放 · MENU 更多",
                        style = TvType.caption,
                        color = TvColors.TextTertiary,
                    )
                }
                Spacer(Modifier.height(10.dp))
                val actionIndex = actionFor
                val actionTrack = actionIndex?.let { tracks.getOrNull(it) }
                if (actionIndex != null && actionTrack != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionChip(
                            label = "下一首播放",
                            group = group,
                            focusKey = "chart:action:next",
                            onClick = {
                                actionFor = null
                                capsule.show(
                                    if (playback.playNext(actionTrack)) "已设为下一首" else "这首歌正在播放"
                                )
                                focusRow(actionIndex)
                            },
                        )
                        ActionChip(
                            label = "加入队列",
                            group = group,
                            focusKey = "chart:action:queue",
                            onClick = {
                                actionFor = null
                                capsule.show(
                                    if (playback.addToQueue(actionTrack)) "已加入队列" else "队列里已经有这首歌"
                                )
                                focusRow(actionIndex)
                            },
                        )
                        ActionChip(
                            label = "取消",
                            group = group,
                            focusKey = "chart:action:cancel",
                            onClick = {
                                actionFor = null
                                focusRow(actionIndex)
                            },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                when {
                    loading -> Column(
                        Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        repeat(6) {
                            SkeletonBox(Modifier.fillMaxWidth().height(62.dp), RoundedCornerShape(12.dp))
                        }
                    }
                    error != null -> Column(
                        Modifier.fillMaxWidth().weight(1f).padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(error.orEmpty(), style = TvType.body, color = TvColors.Danger)
                        Spacer(Modifier.height(14.dp))
                        ActionChip(
                            label = "重试",
                            icon = Icons.Rounded.Refresh,
                            group = group,
                            focusKey = "chart:retry",
                            onClick = { attempt += 1 },
                        )
                    }
                    tracks.isEmpty() -> Text(
                        "这个榜单暂时没有可播放的歌曲。",
                        style = TvType.body,
                        color = TvColors.TextSecondary,
                    )
                    else -> LazyColumn(
                        Modifier.fillMaxWidth().weight(1f),
                        state = listState,
                        contentPadding = PaddingValues(vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        itemsIndexed(tracks, key = { index, track -> "${track.onlineSongId}:$index" }) { index, track ->
                            ChartRow(
                                rank = index + 1,
                                track = track,
                                group = group,
                                focusKey = "chart:row:$index",
                                onClick = { play(track) },
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
                }
                Spacer(Modifier.height(8.dp))
                KeyHintBar(
                    hints = listOf(
                        "OK" to "播放",
                        "MENU" to "更多",
                        "返回" to "上一级",
                        "长按返回" to "回到首页",
                    )
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ChartRow(
    rank: Int,
    track: DemoTrack,
    group: TvFocusGroup,
    focusKey: String,
    onClick: () -> Unit,
    onKeyEvent: ((KeyEvent) -> Boolean)? = null,
) {
    val accent = LocalTvAccent.current
    TvSurface(
        modifier = Modifier.fillMaxWidth().height(62.dp),
        shape = RoundedCornerShape(12.dp),
        baseColor = TvColors.Surface.copy(alpha = 0.58f),
        focusedScale = 1.02f,
        glowSize = 9.dp,
        group = group,
        focusKey = focusKey,
        onClick = onClick,
        onKeyEvent = onKeyEvent,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (rank < 10) "0$rank" else "$rank",
                style = TvType.caption,
                color = if (rank <= 3) accent else TvColors.TextTertiary,
                modifier = Modifier.width(30.dp),
            )
            TrackArtwork(track = track, modifier = Modifier.size(38.dp), corner = 9.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = TvType.label,
                    color = TvColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.artist,
                    style = TvType.caption,
                    color = TvColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                formatDuration(track.durationMs),
                style = TvType.caption,
                color = TvColors.TextTertiary,
            )
            Spacer(Modifier.width(14.dp))
            TvIcon(Icons.Rounded.PlayArrow, Modifier.size(18.dp), tint = accent)
        }
    }
}
