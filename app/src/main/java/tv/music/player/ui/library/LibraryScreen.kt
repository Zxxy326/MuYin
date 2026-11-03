package tv.music.player.ui.library

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
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import tv.music.player.core.focus.TvFocusGroup
import tv.music.player.core.focus.TvSurface
import tv.music.player.core.focus.rememberTvFocusGroup
import tv.music.player.core.playback.DemoTrack
import tv.music.player.core.playback.PlaybackController
import tv.music.player.core.playback.formatDuration
import tv.music.player.core.theme.LocalTvAccent
import tv.music.player.core.theme.TvColors
import tv.music.player.core.theme.TvType
import tv.music.player.ui.components.TrackArtwork
import tv.music.player.ui.components.LocalCapsuleHost
import tv.music.player.ui.components.ScreenHeader
import tv.music.player.ui.components.TvBackdrop
import tv.music.player.ui.components.TvIcon
import tv.music.player.ui.components.TvSafeArea
import tv.music.player.ui.navigation.LocalActiveRoute
import tv.music.player.ui.navigation.TvRoute

@Composable
fun LibraryScreen(
    route: String,
    title: String,
    subtitle: String,
    emptyText: String,
    tracks: List<DemoTrack>,
    playback: PlaybackController,
    navController: NavHostController,
) {
    val group = rememberTvFocusGroup("library:$route")
    val capsule = LocalCapsuleHost.current
    val activeRoute = LocalActiveRoute.current
    val isActive = activeRoute == route

    LaunchedEffect(isActive) {
        if (isActive) group.restore(if (tracks.isEmpty()) "list:search" else "list:0")
    }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) {
        if (windowFocused && isActive) {
            group.restore(if (tracks.isEmpty()) "list:search" else "list:0")
        }
    }

    fun play(track: DemoTrack) {
        playback.play(track, tracks)
        capsule.show(if (track.isOnline) "正在解析音源…" else "正在播放")
        navController.navigate(TvRoute.Player.route) { launchSingleTop = true }
    }

    Box(Modifier.fillMaxSize()) {
        TvBackdrop()
        TvSafeArea {
            Column(Modifier.fillMaxSize()) {
                ScreenHeader(
                    title = title,
                    subtitle = subtitle,
                    group = group,
                    onBack = { navController.popBackStack() },
                    trailing = {
                        Text(
                            if (tracks.isEmpty()) "暂无歌曲" else "${tracks.size} 首",
                            style = TvType.caption,
                            color = TvColors.TextTertiary,
                        )
                    },
                )
                Spacer(Modifier.height(18.dp))
                if (tracks.isEmpty()) {
                    EmptyLibrary(emptyText = emptyText, group = group) {
                        navController.navigate(TvRoute.Search.route) { launchSingleTop = true }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(tracks) { index, track ->
                            LibraryRow(
                                track = track,
                                group = group,
                                focusKey = "list:$index",
                                onClick = { play(track) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(emptyText: String, group: TvFocusGroup, onSearch: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emptyText, style = TvType.body, color = TvColors.TextSecondary)
        Spacer(Modifier.height(18.dp))
        TvSurface(
            modifier = Modifier.width(180.dp).height(46.dp),
            shape = RoundedCornerShape(50),
            baseColor = LocalTvAccent.current.copy(alpha = 0.18f),
            focusedScale = 1.04f,
            glowSize = 12.dp,
            group = group,
            focusKey = "list:search",
            onClick = onSearch,
        ) {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvIcon(Icons.Rounded.Search, Modifier.size(20.dp), tint = LocalTvAccent.current)
                Spacer(Modifier.width(8.dp))
                Text("去搜索", style = TvType.label, color = LocalTvAccent.current)
            }
        }
    }
}

@Composable
private fun LibraryRow(
    track: DemoTrack,
    group: TvFocusGroup,
    focusKey: String,
    onClick: () -> Unit,
) {
    TvSurface(
        modifier = Modifier.fillMaxWidth().height(68.dp),
        shape = RoundedCornerShape(14.dp),
        baseColor = TvColors.Surface.copy(alpha = 0.62f),
        focusedScale = 1.03f,
        glowSize = 10.dp,
        group = group,
        focusKey = focusKey,
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackArtwork(track, Modifier.size(48.dp), corner = 10.dp)
            Spacer(Modifier.width(12.dp))
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
            TvIcon(Icons.Rounded.PlayArrow, Modifier.size(20.dp), tint = LocalTvAccent.current)
        }
    }
}
