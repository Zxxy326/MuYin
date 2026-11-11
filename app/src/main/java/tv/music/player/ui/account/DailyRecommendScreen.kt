package tv.music.player.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import tv.music.player.core.account.AccountStore
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
import tv.music.player.ui.components.TrackArtwork
import tv.music.player.ui.components.TvBackdrop
import tv.music.player.ui.components.TvSafeArea
import tv.music.player.ui.navigation.LocalActiveRoute
import tv.music.player.ui.navigation.TvRoute

/** 每日推荐：登录网易云后读取账号当天的 recommend/songs，而不是跳到歌单广场。 */
@Composable
fun DailyRecommendScreen(
    store: AccountStore,
    playback: PlaybackController,
    navController: NavHostController,
) {
    val group = rememberTvFocusGroup("daily-recommend")
    val accent = LocalTvAccent.current
    val capsule = LocalCapsuleHost.current
    val state by store.state.collectAsStateWithLifecycle()
    val activeRoute = LocalActiveRoute.current
    val isActive = activeRoute == TvRoute.DailyRecommend.route
    val loggedIn = state.netease != null
    val tracks = state.dailySongs

    LaunchedEffect(isActive, loggedIn) {
        if (isActive && loggedIn) store.loadDailyRecommend()
    }

    val firstKey = when {
        !loggedIn -> "daily:login"
        state.dailyError != null -> "daily:retry"
        tracks.isNotEmpty() -> "daily:playall"
        else -> "daily:refresh"
    }
    LaunchedEffect(isActive, firstKey) {
        if (isActive) group.focusWhenReady(firstKey, timeoutMs = 4_000L)
    }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) {
        if (windowFocused && isActive) group.focusWhenReady(firstKey, timeoutMs = 2_000L)
    }

    fun play(track: DemoTrack) {
        playback.play(track, tracks)
        capsule.show(if (track.isOnline) "正在解析音源…" else "正在播放")
        navController.navigate(TvRoute.Player.route) { launchSingleTop = true }
    }

    fun playAll() {
        if (tracks.isEmpty()) return
        play(tracks.first())
    }

    Box(Modifier.fillMaxSize()) {
        TvBackdrop()
        TvSafeArea {
            Column(Modifier.fillMaxSize()) {
                ScreenHeader(
                    title = "每日推荐",
                    subtitle = if (loggedIn) {
                        "网易云 · ${state.netease?.nickname.orEmpty()} 的今日推荐"
                    } else {
                        "登录网易云后，这里会显示你账号的每日推荐歌曲"
                    },
                    group = group,
                    onBack = { navController.popBackStack() },
                    trailing = {
                        Text(
                            if (tracks.isEmpty()) "" else "${tracks.size} 首",
                            style = TvType.caption,
                            color = TvColors.TextTertiary,
                        )
                    },
                )
                Spacer(Modifier.height(12.dp))
                when {
                    !loggedIn -> Column(
                        Modifier.fillMaxWidth().weight(1f).padding(top = 50.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "还没有登录网易云。登录后即可读取你账号当天的每日推荐。",
                            style = TvType.body,
                            color = TvColors.TextSecondary,
                        )
                        Spacer(Modifier.height(16.dp))
                        ActionChip(
                            label = "去登录",
                            icon = Icons.AutoMirrored.Rounded.Login,
                            group = group,
                            focusKey = "daily:login",
                            onClick = { navController.navigate(TvRoute.Account.route) { launchSingleTop = true } },
                        )
                    }
                    state.dailyLoading -> Column(
                        Modifier.fillMaxWidth().weight(1f).padding(top = 50.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("正在读取每日推荐…", style = TvType.body, color = TvColors.TextSecondary)
                    }
                    state.dailyError != null -> Column(
                        Modifier.fillMaxWidth().weight(1f).padding(top = 44.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(state.dailyError.orEmpty(), style = TvType.body, color = TvColors.Danger)
                        Spacer(Modifier.height(12.dp))
                        ActionChip(
                            label = "重试",
                            icon = Icons.Rounded.Refresh,
                            group = group,
                            focusKey = "daily:retry",
                            onClick = { store.loadDailyRecommend(force = true) },
                        )
                    }
                    tracks.isEmpty() -> Column(
                        Modifier.fillMaxWidth().weight(1f).padding(top = 44.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("今天还没有推荐歌曲。", style = TvType.body, color = TvColors.TextSecondary)
                        Spacer(Modifier.height(12.dp))
                        ActionChip(
                            label = "刷新",
                            icon = Icons.Rounded.Refresh,
                            group = group,
                            focusKey = "daily:refresh",
                            onClick = { store.loadDailyRecommend(force = true) },
                        )
                    }
                    else -> {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ActionChip(
                                label = "播放全部",
                                icon = Icons.Rounded.PlayArrow,
                                group = group,
                                focusKey = "daily:playall",
                                onClick = { playAll() },
                            )
                            ActionChip(
                                label = "刷新",
                                icon = Icons.Rounded.Refresh,
                                group = group,
                                focusKey = "daily:refresh",
                                onClick = { store.loadDailyRecommend(force = true) },
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        LazyColumn(
                            Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            itemsIndexed(tracks, key = { index, track -> "${track.onlineSongId}:$index" }) { index, track ->
                                DailyTrackRow(
                                    index = index,
                                    track = track,
                                    group = group,
                                    focusKey = "daily:track:$index",
                                    onClick = { play(track) },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                KeyHintBar(
                    hints = listOf(
                        "OK" to "播放",
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
private fun DailyTrackRow(
    index: Int,
    track: DemoTrack,
    group: TvFocusGroup,
    focusKey: String,
    onClick: () -> Unit,
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
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "%02d".format(index + 1),
                style = TvType.caption,
                color = TvColors.TextTertiary,
                modifier = Modifier.width(26.dp),
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
                    listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" · "),
                    style = TvType.caption,
                    color = TvColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(formatDuration(track.durationMs), style = TvType.caption, color = TvColors.TextTertiary)
            Spacer(Modifier.width(10.dp))
            Text("播放", style = TvType.caption, color = accent)
        }
    }
}