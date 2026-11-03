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
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.music.player.core.focus.TvFocusGroup
import tv.music.player.core.focus.TvSurface
import tv.music.player.core.focus.rememberTvFocusGroup
import tv.music.player.core.local.LocalLibraryStore
import tv.music.player.core.playback.DemoTrack
import tv.music.player.core.playback.PlaybackController
import tv.music.player.core.smb.SmbConfig
import tv.music.player.core.smb.SmbConfigStore
import tv.music.player.core.smb.SmbRepository
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
fun SmbScreen(
    configStore: SmbConfigStore,
    repository: SmbRepository,
    localLibrary: LocalLibraryStore,
    playback: PlaybackController,
    navController: NavHostController,
) {
    val group = rememberTvFocusGroup("smb")
    val capsule = LocalCapsuleHost.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = LocalTvAccent.current
    val config by configStore.config.collectAsStateWithLifecycle()
    val activeRoute = LocalActiveRoute.current
    val isActive = activeRoute == TvRoute.Smb.route

    val localTracks by localLibrary.tracks.collectAsStateWithLifecycle()
    val tracks = localTracks.filter { it.localSource == SmbRepository.SOURCE_SMB }
    var scanning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var armedClear by remember { mutableStateOf(false) }

    fun scan(target: SmbConfig) {
        if (scanning) return
        scope.launch {
            scanning = true
            error = null
            val result = runCatching { repository.scan(target) }
            scanning = false
            result.onSuccess { found ->
                localLibrary.replaceSourceTracks(SmbRepository.SOURCE_SMB, found)
                capsule.show(if (found.isEmpty()) "共享里没有音频文件" else "已添加 ${found.size} 首到设备歌曲")
            }.onFailure { t ->
                error = "SMB 扫描失败：${scanMessage(t)}"
                capsule.show(error.orEmpty())
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val raw = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            if (raw.isNullOrBlank()) {
                capsule.show("读取配置文件失败")
                return@launch
            }
            val parsed = runCatching { configStore.parse(raw) }.getOrElse {
                capsule.show("配置格式错误：${it.message ?: "无效 JSON"}")
                return@launch
            }
            if (!configStore.save(parsed)) {
                capsule.show("保存配置失败，请检查系统密钥")
                return@launch
            }
            localLibrary.replaceSourceTracks(SmbRepository.SOURCE_SMB, emptyList())
            error = null
            capsule.show("已导入「${parsed.name}」，开始扫描…")
            scan(parsed)
        }
    }

    fun play(track: DemoTrack) {
        playback.play(track, tracks)
        navController.navigate(TvRoute.Player.route) { launchSingleTop = true }
    }

    LaunchedEffect(isActive) {
        if (isActive) group.restore(if (config == null) "smb:import" else "smb:scan")
    }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) {
        if (windowFocused && isActive) {
            group.restore(if (config == null) "smb:import" else "smb:scan")
        }
    }
    LaunchedEffect(armedClear) {
        if (armedClear) {
            delay(3_000L)
            armedClear = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        TvBackdrop()
        TvSafeArea {
            Column(Modifier.fillMaxSize()) {
                ScreenHeader(
                    title = "SMB 共享",
                    subtitle = "导入 smb.json · 扫描共享 · 直接流式播放",
                    group = group,
                    onBack = { navController.popBackStack() },
                    trailing = {
                        Text(
                            when {
                                scanning -> "扫描中…"
                                tracks.isNotEmpty() -> "${tracks.size} 首"
                                else -> "SMB2/3"
                            },
                            style = TvType.caption,
                            color = if (scanning) accent else TvColors.TextTertiary,
                        )
                    },
                )
                Spacer(Modifier.height(14.dp))
                TvSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    baseColor = TvColors.Surface.copy(alpha = 0.66f),
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        if (config == null) {
                            Text("还没有 SMB 配置", style = TvType.label, color = TvColors.TextPrimary)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "在 U 盘 / 手机上新建 smb.json，填写 host、share、user、password，点下方导入；凭据会加密保存在本机。",
                                style = TvType.caption,
                                color = TvColors.TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "{\"name\":\"NAS\",\"host\":\"192.168.1.10\",\"share\":\"music\",\"path\":\"/\",\"user\":\"tv\",\"password\":\"******\"}",
                                style = TvType.caption,
                                color = TvColors.TextTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else {
                            val cfg = config!!
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TvIcon(Icons.Rounded.NetworkCheck, Modifier.size(20.dp), tint = accent)
                                Spacer(Modifier.width(8.dp))
                                Text(cfg.name, style = TvType.label, color = TvColors.TextPrimary)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "smb://${cfg.host}/${cfg.share}",
                                    style = TvType.caption,
                                    color = TvColors.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                buildString {
                                    append("用户：").append(cfg.user.ifBlank { "guest" })
                                    append(" · 端口：").append(cfg.port)
                                    append(" · 扫描结果会加入设备歌曲")
                                },
                                style = TvType.caption,
                                color = TvColors.TextTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (error != null) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    error.orEmpty(),
                                    style = TvType.caption,
                                    color = TvColors.Danger,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ActionChip(
                        label = if (config == null) "导入 smb.json" else "替换配置",
                        icon = Icons.Rounded.FolderOpen,
                        group = group,
                        focusKey = "smb:import",
                        onClick = { runCatching { importLauncher.launch(arrayOf("*/*")) } }
                    )
                    if (config != null) {
                        ActionChip(
                            label = if (scanning) "扫描中…" else "重新扫描",
                            icon = Icons.Rounded.Refresh,
                            group = group,
                            focusKey = "smb:scan",
                            onClick = { config?.let { if (!scanning) scan(it) } },
                        )
                        ActionChip(
                            label = if (armedClear) "再按一次清除" else "清除配置",
                            group = group,
                            focusKey = "smb:clear",
                            onClick = {
                                if (armedClear) {
                                    armedClear = false
                                    configStore.clear()
                                    localLibrary.replaceSourceTracks(SmbRepository.SOURCE_SMB, emptyList())
                                    error = null
                                    capsule.show("已清除 SMB 配置")
                                } else {
                                    armedClear = true
                                    capsule.show("再按一次清除 SMB 配置与凭据")
                                }
                            },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "仅读取，不复制文件",
                        style = TvType.caption,
                        color = TvColors.TextTertiary,
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (tracks.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().weight(1f).padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            when {
                                scanning -> "正在扫描共享目录…"
                                config == null -> "导入 smb.json 后即可扫描共享"
                                else -> "点“重新扫描”列出共享里的音频"
                            },
                            style = TvType.body,
                            color = TvColors.TextSecondary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "支持 SMB2/3；建议只给该账号共享目录的只读权限。",
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
                            SmbRow(
                                track = track,
                                group = group,
                                focusKey = "smb:$index",
                                onClick = { play(track) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                KeyHintBar(
                    hints = listOf(
                        "OK" to when {
                            config == null -> "导入"
                            tracks.isEmpty() -> "扫描"
                            else -> "播放"
                        },
                        "返回" to "上一级",
                        "长按返回" to "回到首页",
                    )
                )
                Spacer(Modifier.height(96.dp))
            }
        }
    }
}

private fun scanMessage(t: Throwable): String {
    val lower = t.message.orEmpty().lowercase()
    return when {
        lower.contains("logon failure") || lower.contains("logon_failure") ||
            lower.contains("status_logon_failure") -> "用户名或密码错误"
        lower.contains("access denied") || lower.contains("status_access_denied") ->
            "共享拒绝访问，请检查账号权限"
        lower.contains("unknown host") || lower.contains("unresolved") ||
            lower.contains("name not found") -> "找不到主机，请检查 host"
        lower.contains("failed to connect") || lower.contains("connection refused") ||
            lower.contains("timed out") || lower.contains("timeout") ||
            lower.contains("no route") -> "无法连接，请检查地址、端口与网络"
        else -> t.message.orEmpty().ifBlank { "无法连接" }.take(80)
    }
}

@Composable
private fun SmbRow(
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
                    "${track.localSource} · ${track.album}",
                    style = TvType.caption,
                    color = TvColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TvIcon(Icons.Rounded.PlayArrow, Modifier.size(20.dp), tint = accent)
        }
    }
}
