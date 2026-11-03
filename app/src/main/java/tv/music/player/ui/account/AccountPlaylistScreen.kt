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
import tv.music.player.core.account.AccountPlaylist
import tv.music.player.core.account.AccountStore
import tv.music.player.core.focus.TvFocusGroup
import tv.music.player.core.focus.TvSurface
import tv.music.player.core.focus.rememberTvFocusGroup
import tv.music.player.core.playback.DemoTrack
import tv.music.player.core.playback.PlaybackController
import tv.music.player.core.theme.LocalTvAccent
import tv.music.player.core.theme.TvColors
import tv.music.player.core.theme.TvType
import tv.music.player.ui.components.ActionChip
import tv.music.player.ui.components.TrackArtwork
import tv.music.player.ui.components.KeyHintBar
import tv.music.player.ui.components.ScreenHeader
import tv.music.player.ui.components.TvBackdrop
import tv.music.player.ui.components.TvSafeArea
import tv.music.player.ui.navigation.LocalActiveRoute
import tv.music.player.ui.navigation.TvRoute

@Composable
fun AccountPlaylistScreen(
    store: AccountStore,
    playback: PlaybackController,
    provider: String,
    playlistId: String,
    navController: NavHostController,
) {
    val group = rememberTvFocusGroup("account-playlist")
    val accent = LocalTvAccent.current
    val state by store.state.collectAsStateWithLifecycle()
    val playlist = state.playlists.firstOrNull { it.id == playlistId && it.provider == provider }
    val activeRoute = LocalActiveRoute.current
    val isActive = activeRoute == TvRoute.AccountPlaylist.route
    val tracks = playlist?.let(store::playlistTracks).orEmpty()

    LaunchedEffect(playlistId) {
        playlist?.let { store.loadPlaylistTracks(it) }
    }
    val firstKey = if (tracks.isNotEmpty()) "playlist:track:0" else "playlist:retry"
    LaunchedEffect(isActive, tracks.size, state.loadingPlaylistId) {
        if (isActive) group.focusWhenReady(firstKey, timeoutMs = 4_000L)
    }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) {
        if (windowFocused && isActive) group.focusWhenReady(firstKey, timeoutMs = 2_000L)
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
                    title = playlist?.name ?: "歌单",
                    subtitle = playlist?.let {
                        "${if (it.provider == "qq") "QQ 音乐" else "网易云"} · ${it.trackCount} 首"
                    } ?: "我的歌单",
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
                    playlist == null -> Text(
                        "歌单不存在，请返回账号页重新加载。",
                        style = TvType.body,
                        color = TvColors.Danger,
                    )
                    state.loadingPlaylistId == playlist.id -> Column(
                        Modifier.fillMaxWidth().weight(1f).padding(top = 50.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("正在读取歌单歌曲…", style = TvType.body, color = TvColors.TextSecondary)
                    }
                    state.playlistError != null -> Column(
                        Modifier.fillMaxWidth().weight(1f).padding(top = 44.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(state.playlistError.orEmpty(), style = TvType.body, color = TvColors.Danger)
                        Spacer(Modifier.height(12.dp))
                        ActionChip(
                            label = "重试",
                            icon = Icons.Rounded.Refresh,
                            group = group,
                            focusKey = "playlist:retry",
                            onClick = {
                                store.clearPlaylistError()
                                playlist?.let { store.loadPlaylistTracks(it) }
                            },
                        )
                    }
                    tracks.isEmpty() -> Text(
                        "这个歌单没有可播放的歌曲。",
                        style = TvType.body,
                        color = TvColors.TextSecondary,
                    )
                    else -> LazyColumn(
                        Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        itemsIndexed(tracks, key = { index, track -> "${track.onlineSongId}:$index" }) { index, track ->
                            TrackRow(
                                index = index,
                                track = track,
                                group = group,
                                focusKey = "playlist:track:$index",
                                onClick = { play(track) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                KeyHintBar(
                    hints = listOf(
                        "OK" to "播放整单",
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
private fun TrackRow(
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
            TrackArtwork(
                track = track,
                modifier = Modifier.size(38.dp),
                corner = 9.dp,
            )
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
                    "${track.artist} · ${track.album}",
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

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "--:--"
    val total = ms / 1000L
    return "%d:%02d".format(total / 60L, total % 60L)
}
