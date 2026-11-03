package tv.music.player.ui.local

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.collect
import tv.music.player.core.focus.TvFocusGroup
import tv.music.player.core.focus.TvSurface
import tv.music.player.core.focus.rememberTvFocusGroup
import tv.music.player.core.local.AudioPermission
import tv.music.player.core.local.LocalLibraryStore
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
import tv.music.player.ui.components.TvIcon
import tv.music.player.ui.components.TvSafeArea
import tv.music.player.ui.navigation.LocalActiveRoute
import tv.music.player.ui.navigation.TvRoute

@Composable
fun DeviceMusicScreen(
    localLibrary: LocalLibraryStore,
    playback: PlaybackController,
    navController: NavHostController,
) {
    val group = rememberTvFocusGroup("device-music")
    val capsule = LocalCapsuleHost.current
    val context = LocalContext.current
    val accent = LocalTvAccent.current
    val tracks by localLibrary.tracks.collectAsStateWithLifecycle()
    val scanning by localLibrary.scanning.collectAsStateWithLifecycle()
    val activeRoute = LocalActiveRoute.current
    val isActive = activeRoute == TvRoute.DeviceMusic.route

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) localLibrary.scanDevice()
        else capsule.show("未授予音频读取权限，可改用「添加文件夹」")
    }
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) localLibrary.addTree(uri)
    }

    fun scan() {
        if (AudioPermission.granted(context)) {
            localLibrary.scanDevice()
        } else {
            permissionLauncher.launch(AudioPermission.name)
        }
    }

    fun play(track: DemoTrack) {
        playback.play(track, tracks)
        navController.navigate(TvRoute.Player.route) { launchSingleTop = true }
    }

    LaunchedEffect(isActive) {
        if (isActive) group.restore(if (tracks.isEmpty()) "device:scan" else "list:0")
    }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) {
        if (windowFocused && isActive) {
            group.restore(if (tracks.isEmpty()) "device:scan" else "list:0")
        }
    }
    LaunchedEffect(Unit) {
        if (tracks.isEmpty() && AudioPermission.granted(context)) localLibrary.scanDevice()
    }
    LaunchedEffect(Unit) {
        localLibrary.message.collect { message ->
            if (!message.isNullOrBlank()) {
                capsule.show(message)
                localLibrary.clearMessage()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        TvBackdrop()
        TvSafeArea {
            Column(Modifier.fillMaxSize()) {
                ScreenHeader(
                    title = "设备歌曲",
                    subtitle = "MediaStore 扫描 · 文件夹导入 · 本地直接播放",
                    group = group,
                    onBack = { navController.popBackStack() },
                    trailing = {
                        Text(
                            if (scanning) "扫描中…" else "${tracks.size} 首",
                            style = TvType.caption,
                            color = if (scanning) accent else TvColors.TextTertiary,
                        )
                    },
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ActionChip(
                        label = if (scanning) "扫描中…" else "扫描设备",
                        icon = Icons.Rounded.Refresh,
                        group = group,
                        focusKey = "device:scan",
                        onClick = { if (!scanning) scan() },
                    )
                    ActionChip(
                        label = "添加文件夹",
                        icon = Icons.Rounded.FolderOpen,
                        group = group,
                        focusKey = "device:folder",
                        onClick = {
                            runCatching { folderLauncher.launch(null) }
                                .onFailure { capsule.show("没有可用的文件夹选择器") }
                        },
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "U 盘 / 外接盘 / NAS 目录",
                        style = TvType.caption,
                        color = TvColors.TextTertiary,
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (tracks.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().weight(1f).padding(top = 50.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("还没有设备歌曲", style = TvType.body, color = TvColors.TextSecondary)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "点「扫描设备」读取系统媒体库，或用「添加文件夹」选择 U 盘 / NAS 目录；本地文件不会复制。",
                            style = TvType.caption,
                            color = TvColors.TextTertiary,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(tracks, key = { _, track -> track.localUri }) { index, track ->
                            LocalRow(
                                track = track,
                                group = group,
                                focusKey = "list:$index",
                                onClick = { play(track) },
                            )
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
                Spacer(Modifier.height(96.dp))
            }
        }
    }
}

@Composable
private fun LocalRow(
    track: DemoTrack,
    group: TvFocusGroup,
    focusKey: String,
    onClick: () -> Unit,
) {
    val accent = LocalTvAccent.current
    TvSurface(
        modifier = Modifier.fillMaxWidth().height(70.dp),
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
                    listOf(track.artist, track.localSource)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = TvType.caption,
                    color = TvColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                if (track.durationMs > 0L) formatDuration(track.durationMs) else "—",
                style = TvType.caption,
                color = TvColors.TextTertiary,
            )
            Spacer(Modifier.width(10.dp))
            TvIcon(Icons.Rounded.PlayArrow, Modifier.size(20.dp), tint = accent)
        }
    }
}
