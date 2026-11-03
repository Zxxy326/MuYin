package tv.music.player.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.music.player.core.download.DownloadEntry
import tv.music.player.core.download.DownloadStatus
import tv.music.player.core.download.DownloadStore
import tv.music.player.core.focus.TvFocusGroup
import tv.music.player.core.focus.TvSurface
import tv.music.player.core.focus.rememberTvFocusGroup
import tv.music.player.core.library.libraryKey
import tv.music.player.core.playback.PlaybackController
import tv.music.player.core.theme.LocalTvAccent
import tv.music.player.core.theme.TvColors
import tv.music.player.core.theme.TvType
import tv.music.player.ui.components.KeyHintBar
import tv.music.player.ui.components.LocalCapsuleHost
import tv.music.player.ui.components.ScreenHeader
import tv.music.player.ui.components.TrackArtwork
import tv.music.player.ui.components.TvBackdrop
import tv.music.player.ui.components.TvIcon
import tv.music.player.ui.components.TvSafeArea
import tv.music.player.ui.navigation.LocalActiveRoute
import tv.music.player.ui.navigation.TvRoute
import java.util.Locale

@Composable
fun DownloadsScreen(
    downloads: DownloadStore,
    playback: PlaybackController,
    navController: NavHostController,
) {
    val entries by downloads.entries.collectAsStateWithLifecycle()
    val snapshot by playback.state.collectAsStateWithLifecycle()
    val group = rememberTvFocusGroup("downloads")
    val capsule = LocalCapsuleHost.current
    val accent = LocalTvAccent.current
    val activeRoute = LocalActiveRoute.current
    val isActive = activeRoute == TvRoute.Downloads.route
    var armedKey by remember { mutableStateOf<String?>(null) }

    val done = entries.filter { it.status == DownloadStatus.DONE }
    val playable = done.map { it.track }
    val totalBytes = done.sumOf { it.totalBytes }

    LaunchedEffect(isActive) {
        if (isActive) group.restore(if (entries.isEmpty()) "list:search" else "list:0")
    }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) {
        if (windowFocused && isActive) {
            group.restore(if (entries.isEmpty()) "list:search" else "list:0")
        }
    }
    LaunchedEffect(armedKey) {
        if (armedKey != null) {
            delay(2_500L)
            armedKey = null
        }
    }
    LaunchedEffect(entries.isEmpty()) {
        if (isActive && entries.isEmpty()) {
            delay(150L)
            group.focusWhenReady("list:search", timeoutMs = 1_500L)
        }
    }

    fun play(entry: DownloadEntry) {
        playback.play(entry.track, playable)
        capsule.show("正在播放本地文件")
        navController.navigate(TvRoute.Player.route) { launchSingleTop = true }
    }

    fun onMenu(entry: DownloadEntry) {
        when (entry.status) {
            DownloadStatus.DONE -> {
                if (snapshot.track.libraryKey() == entry.key) {
                    capsule.show("正在播放这首歌，先切歌再删除")
                    armedKey = null
                } else if (armedKey == entry.key) {
                    downloads.delete(entry.track)
                    armedKey = null
                    capsule.show("已删除下载：${entry.track.title}")
                } else {
                    armedKey = entry.key
                    capsule.show("再按一次 MENU 删除《${entry.track.title}》")
                }
            }
            DownloadStatus.FAILED -> {
                downloads.cancel(entry.track)
                armedKey = null
                capsule.show("已移除失败记录")
            }
            else -> {
                downloads.cancel(entry.track)
                armedKey = null
                capsule.show("已取消下载：${entry.track.title}")
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        TvBackdrop()
        TvSafeArea {
            Column(Modifier.fillMaxSize()) {
                ScreenHeader(
                    title = "本地音乐",
                    subtitle = "下载的歌曲 · 断网也能播放",
                    group = group,
                    onBack = { navController.popBackStack() },
                    trailing = {
                        Text(
                            if (entries.isEmpty()) "暂无下载"
                            else "${done.size} 首 · ${formatBytes(totalBytes)}",
                            style = TvType.caption,
                            color = TvColors.TextTertiary,
                        )
                    },
                )
                Spacer(Modifier.height(16.dp))
                if (entries.isEmpty()) {
                    EmptyDownloads(group = group, modifier = Modifier.weight(1f)) {
                        navController.navigate(TvRoute.Search.route) { launchSingleTop = true }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(entries, key = { _, entry -> entry.key }) { index, entry ->
                            DownloadRow(
                                entry = entry,
                                accentProgress = accent,
                                group = group,
                                focusKey = "list:$index",
                                onClick = {
                                    when (entry.status) {
                                        DownloadStatus.DONE -> play(entry)
                                        DownloadStatus.FAILED -> {
                                            downloads.cancel(entry.track)
                                            downloads.enqueue(entry.track)
                                            capsule.show("正在重试：${entry.track.title}")
                                        }
                                        else -> capsule.show("下载中，完成后即可离线播放")
                                    }
                                },
                                onKeyEvent = { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.Menu) {
                                        onMenu(entry)
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
                        "OK" to "播放 / 重试",
                        "MENU" to "删除 / 取消下载",
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
private fun EmptyDownloads(
    group: TvFocusGroup,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit,
) {
    Column(
        modifier.fillMaxWidth().padding(top = 70.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("还没有下载的歌曲", style = TvType.body, color = TvColors.TextSecondary)
        Spacer(Modifier.height(8.dp))
        Text(
            "播放任意在线歌曲，按 ↑ 打开控制条，点「下载」即可保存到本机。",
            style = TvType.caption,
            color = TvColors.TextTertiary,
        )
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
private fun DownloadRow(
    entry: DownloadEntry,
    accentProgress: Color,
    group: TvFocusGroup,
    focusKey: String,
    onClick: () -> Unit,
    onKeyEvent: (KeyEvent) -> Boolean,
) {
    TvSurface(
        modifier = Modifier.fillMaxWidth().height(76.dp),
        shape = RoundedCornerShape(14.dp),
        baseColor = TvColors.Surface.copy(alpha = 0.62f),
        focusedScale = 1.03f,
        glowSize = 10.dp,
        group = group,
        focusKey = focusKey,
        onClick = onClick,
        onKeyEvent = onKeyEvent,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackArtwork(entry.track, Modifier.size(52.dp), corner = 10.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.track.title,
                    style = TvType.label,
                    color = TvColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOf(entry.track.artist, entry.track.album)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                        .ifBlank { "未知歌手" },
                    style = TvType.caption,
                    color = TvColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.status == DownloadStatus.DOWNLOADING || entry.status == DownloadStatus.QUEUED) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(TvColors.Divider)
                    ) {
                        val fraction = if (entry.totalBytes > 0L) entry.progress else 0f
                        if (fraction > 0f) {
                            Box(
                                Modifier
                                    .fillMaxWidth(fraction)
                                    .fillMaxHeight()
                                    .background(accentProgress)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    statusText(entry),
                    style = TvType.caption,
                    color = when (entry.status) {
                        DownloadStatus.FAILED -> TvColors.Danger
                        DownloadStatus.DONE -> accentProgress
                        else -> TvColors.TextSecondary
                    },
                    maxLines = 1,
                )
                when (entry.status) {
                    DownloadStatus.DONE ->
                        Text(formatBytes(entry.totalBytes), style = TvType.caption, color = TvColors.TextTertiary)
                    DownloadStatus.FAILED ->
                        Text(
                            entry.error.orEmpty(),
                            style = TvType.caption,
                            color = TvColors.Danger,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    DownloadStatus.DOWNLOADING ->
                        Text(
                            formatBytes(entry.bytesRead),
                            style = TvType.caption,
                            color = TvColors.TextTertiary,
                        )
                    else -> Unit
                }
            }
            Spacer(Modifier.width(10.dp))
            if (entry.status == DownloadStatus.DONE) {
                TvIcon(Icons.Rounded.PlayArrow, Modifier.size(20.dp), tint = accentProgress)
            }
        }
    }
}

private fun statusText(entry: DownloadEntry): String = when (entry.status) {
    DownloadStatus.DONE -> "已下载"
    DownloadStatus.FAILED -> "下载失败"
    DownloadStatus.QUEUED -> "准备中…"
    DownloadStatus.DOWNLOADING ->
        if (entry.totalBytes > 0L) "下载中 ${(entry.progress * 100).toInt()}%" else "下载中…"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576L -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
    bytes >= 1_024L -> String.format(Locale.US, "%.0f KB", bytes / 1_024.0)
    else -> "$bytes B"
}
