package tv.music.player.ui.settings

import android.content.Context
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tv.music.player.BuildConfig
import tv.music.player.core.companion.CompanionServer
import tv.music.player.core.crash.CrashLogger
import tv.music.player.core.focus.rememberTvFocusGroup
import tv.music.player.core.lx.LxFileStore
import tv.music.player.core.lx.LxJsEngine
import tv.music.player.core.scale.LocalTvScale
import tv.music.player.core.search.OnlineSearchApi
import tv.music.player.core.settings.AppSettings
import tv.music.player.core.settings.LyricStyle
import tv.music.player.core.settings.SettingsRepository
import tv.music.player.core.settings.formatLyricOffset
import tv.music.player.core.settings.lxQuality
import tv.music.player.core.update.UpdateStore
import tv.music.player.core.theme.TvColors
import tv.music.player.core.theme.TvType
import tv.music.player.ui.components.CapsuleHostState
import tv.music.player.ui.components.InfoRow
import tv.music.player.ui.components.KeyHintBar
import tv.music.player.ui.components.LocalCapsuleHost
import tv.music.player.ui.components.ScreenHeader
import tv.music.player.ui.components.SettingRow
import tv.music.player.ui.components.TvBackdrop
import tv.music.player.ui.components.TvSafeArea
import tv.music.player.ui.navigation.LocalActiveRoute
import tv.music.player.ui.navigation.TvRoute

private sealed interface SettingEntry {
    val key: String
    val title: String
    val subtitle: String

    data class Toggle(
        override val key: String,
        override val title: String,
        override val subtitle: String,
        val checked: Boolean,
        val onToggle: () -> Unit,
    ) : SettingEntry

    data class Choice(
        override val key: String,
        override val title: String,
        override val subtitle: String,
        val value: String,
        val onNext: () -> Unit,
        val onPrevious: () -> Unit,
    ) : SettingEntry

    data class Action(
        override val key: String,
        override val title: String,
        override val subtitle: String,
        val value: String,
        val onClick: () -> Unit,
    ) : SettingEntry

    data class Info(
        override val key: String,
        override val title: String,
        override val subtitle: String,
        val value: String,
    ) : SettingEntry
}

@Composable
fun SettingsGroupScreen(
    groupId: String,
    settings: AppSettings,
    settingsRepository: SettingsRepository,
    lxEngine: LxJsEngine,
    lxFileStore: LxFileStore,
    onlineSearch: OnlineSearchApi,
    companionServer: CompanionServer,
    updateStore: UpdateStore,
    onOpenDeviceMusic: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSourceMarket: () -> Unit,
    onOpenSmb: () -> Unit,
    onOpenAccount: () -> Unit,
    onBack: () -> Unit,
) {
    val info = SettingsGroups.byId(groupId)
    val group = rememberTvFocusGroup("settings-group:$groupId")
    val capsule = LocalCapsuleHost.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scale = LocalTvScale.current
    var lxRefreshToken by remember { mutableIntStateOf(0) }
    val entries = buildEntries(
        groupId = groupId,
        settings = settings,
        repository = settingsRepository,
        scope = scope,
        capsule = capsule,
        context = context,
        scale = scale,
        onOpenDeviceMusic = onOpenDeviceMusic,
        onOpenDownloads = onOpenDownloads,
        onOpenSourceMarket = onOpenSourceMarket,
        onOpenSmb = onOpenSmb,
        onOpenAccount = onOpenAccount,
    )
    val firstFocusKey = when (groupId) {
        "companion" -> "companion:toggle"
        "update" -> "update:check"
        else -> entries.firstOrNull { it !is SettingEntry.Info }?.key?.let { "item:$it" }
    }

    val sourcePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val displayName = runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull().orEmpty().ifBlank { "userapi.js" }
            val base = displayName.substringBeforeLast('.', displayName)
            val written = lxFileStore.writeFromUri(uri, base)
            if (written == null) {
                capsule.show("导入失败：文件无法读取或为空")
                return@launch
            }
            val reloaded = lxEngine.reload()
            if (!reloaded) {
                lxFileStore.deleteByName(written)
                lxEngine.reload()
                lxRefreshToken++
                capsule.show("导入失败：脚本无效，已回滚")
                return@launch
            }
            lxRefreshToken++
            capsule.show("已导入 $written · 音源已重载")
        }
    }

    val activeRoute = LocalActiveRoute.current
    val isActive = activeRoute == TvRoute.SettingsGroup.route
    LaunchedEffect(isActive) {
        if (isActive) group.restore(firstFocusKey ?: "item:")
    }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) {
        if (windowFocused && isActive) group.restore(firstFocusKey ?: "item:")
    }

    TvSafeArea {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = info.title,
                subtitle = info.subtitle,
                group = group,
                onBack = onBack,
                trailing = {
                    Text(
                        when (groupId) {
                            "companion" -> "手机扫码即用"
                            "update" -> "检查 · 下载 · 安装"
                            else -> "${entries.count { it !is SettingEntry.Info }} 项可调"
                        },
                        style = TvType.caption,
                        color = TvColors.TextTertiary,
                    )
                },
            )
            Spacer(Modifier.height(14.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                if (groupId == "companion") {
                    item(key = "companion-card") {
                        CompanionCard(
                            server = companionServer,
                            settings = settings,
                            settingsRepository = settingsRepository,
                            group = group,
                            capsule = capsule,
                        )
                    }
                }
                if (groupId == "update") {
                    item(key = "update-card") {
                        UpdateCard(
                            store = updateStore,
                            group = group,
                            capsule = capsule,
                        )
                    }
                }
                if (groupId == "sources") {
                    item(key = "lx-sources-card") {
                        LxSourcesCard(
                            lxEngine = lxEngine,
                            group = group,
                            capsule = capsule,
                            refreshToken = lxRefreshToken,
                        )
                    }
                    item(key = "source-test-card") {
                        SourceTestCard(
                            lxEngine = lxEngine,
                            onlineSearch = onlineSearch,
                            group = group,
                            capsule = capsule,
                            quality = settings.quality.lxQuality,
                            onImport = { sourcePicker.launch(arrayOf("*/*")) },
                        )
                    }
                }
                items(entries, key = { it.key }) { entry ->
                    when (entry) {
                        is SettingEntry.Toggle -> SettingRow(
                            title = entry.title,
                            subtitle = entry.subtitle,
                            group = group,
                            focusKey = "item:${entry.key}",
                            onClick = entry.onToggle,
                            toggle = entry.checked,
                        )
                        is SettingEntry.Choice -> SettingRow(
                            title = entry.title,
                            subtitle = entry.subtitle,
                            group = group,
                            focusKey = "item:${entry.key}",
                            onClick = entry.onNext,
                            valueText = entry.value,
                            onKeyEvent = { event ->
                                if (event.type != KeyEventType.KeyDown) {
                                    false
                                } else {
                                    when (event.key) {
                                        Key.DirectionLeft -> {
                                            entry.onPrevious()
                                            true
                                        }
                                        Key.DirectionRight -> {
                                            entry.onNext()
                                            true
                                        }
                                        else -> false
                                    }
                                }
                            },
                        )
                        is SettingEntry.Action -> SettingRow(
                            title = entry.title,
                            subtitle = entry.subtitle,
                            group = group,
                            focusKey = "item:${entry.key}",
                            onClick = entry.onClick,
                            valueText = entry.value,
                            showChevron = true,
                        )
                        is SettingEntry.Info -> InfoRow(
                            title = entry.title,
                            value = entry.value,
                            subtitle = entry.subtitle,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            KeyHintBar(
                hints = listOf(
                    "OK" to "切换 / 执行",
                    "← →" to "调整选项",
                    "返回" to "上一级",
                    "长按返回" to "回到首页",
                )
            )
            Spacer(Modifier.height(96.dp))
        }
    }
}

private fun buildEntries(
    groupId: String,
    settings: AppSettings,
    repository: SettingsRepository,
    scope: CoroutineScope,
    capsule: CapsuleHostState,
    context: Context,
    scale: Float,
    onOpenDeviceMusic: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSourceMarket: () -> Unit,
    onOpenSmb: () -> Unit,
    onOpenAccount: () -> Unit,
): List<SettingEntry> {

    fun save(message: String, block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }
                .onSuccess { capsule.show(message) }
                .onFailure { capsule.show("保存失败：${it.message.orEmpty()}") }
        }
    }

    return when (groupId) {
        "appearance" -> listOf(
            SettingEntry.Choice(
                key = "accent",
                title = "主题色",
                subtitle = "跟随封面主色或固定主题色，切换即时生效",
                value = settings.accentMode.label,
                onNext = {
                    val next = settings.accentMode.next()
                    save("主题色：${next.label}") { repository.setAccentMode(next) }
                },
                onPrevious = {
                    val previous = settings.accentMode.previous()
                    save("主题色：${previous.label}") { repository.setAccentMode(previous) }
                },
            ),
            SettingEntry.Toggle(
                key = "animations",
                title = "界面动画",
                subtitle = "焦点缩放、页面转场与主题色过渡",
                checked = settings.animationsEnabled,
                onToggle = {
                    save(if (settings.animationsEnabled) "动画已关闭" else "动画已开启") {
                        repository.setAnimationsEnabled(!settings.animationsEnabled)
                    }
                },
            ),
            SettingEntry.Toggle(
                key = "blur",
                title = "模糊背景",
                subtitle = "播放页封面模糊铺底；关闭可省 GPU",
                checked = settings.blurBackground,
                onToggle = {
                    save(if (settings.blurBackground) "已切纯色背景" else "已开启模糊背景") {
                        repository.setBlurBackground(!settings.blurBackground)
                    }
                },
            ),
            SettingEntry.Choice(
                key = "lyricStyle",
                title = "歌词样式",
                subtitle = "3 种横屏动效：动感涟漪 / 简约 / 精简时钟",
                value = settings.lyricStyle.label,
                onNext = {
                    val next = settings.lyricStyle.next()
                    save("歌词样式：${next.label}") { repository.setLyricStyle(next) }
                },
                onPrevious = {
                    val previous = settings.lyricStyle.previous()
                    save("歌词样式：${previous.label}") { repository.setLyricStyle(previous) }
                },
            ),
            SettingEntry.Toggle(
                key = "lyricTranslation",
                title = "歌词翻译",
                subtitle = "QQ 歌词自带翻译时显示在副行",
                checked = settings.showLyricTranslation,
                onToggle = {
                    save(if (settings.showLyricTranslation) "已隐藏歌词翻译" else "已显示歌词翻译") {
                        repository.setShowLyricTranslation(!settings.showLyricTranslation)
                    }
                },
            ),
            SettingEntry.Choice(
                key = "lyricOffset",
                title = "歌词偏移",
                subtitle = "±0.5s 步进、范围 ±5s；正值歌词提前",
                value = formatLyricOffset(settings.lyricOffsetMs),
                onNext = {
                    val next = (settings.lyricOffsetMs + 500).coerceIn(-5_000, 5_000)
                    save("歌词偏移：${formatLyricOffset(next)}") { repository.setLyricOffset(next) }
                },
                onPrevious = {
                    val previous = (settings.lyricOffsetMs - 500).coerceIn(-5_000, 5_000)
                    save("歌词偏移：${formatLyricOffset(previous)}") { repository.setLyricOffset(previous) }
                },
            ),
        )

        "playback" -> listOf(
            SettingEntry.Choice(
                key = "quality",
                title = "音质",
                subtitle = "标准 / 高音质 / 无损，取流失败自动降级",
                value = settings.quality.label,
                onNext = {
                    val next = settings.quality.next()
                    save("音质：${next.label}") { repository.setQuality(next) }
                },
                onPrevious = {
                    val previous = settings.quality.previous()
                    save("音质：${previous.label}") { repository.setQuality(previous) }
                },
            ),
            SettingEntry.Toggle(
                key = "gapless",
                title = "无缝播放",
                subtitle = "提前解析下一首，缩短在线歌曲的切歌等待",
                checked = settings.gaplessPlayback,
                onToggle = {
                    save(if (settings.gaplessPlayback) "已关闭无缝播放" else "已开启无缝播放") {
                        repository.setGapless(!settings.gaplessPlayback)
                    }
                },
            ),
            SettingEntry.Toggle(
                key = "fade",
                title = "淡入淡出",
                subtitle = "播放 / 暂停与切歌的短淡入淡出",
                checked = settings.fadeInOut,
                onToggle = {
                    save(if (settings.fadeInOut) "已关闭淡入淡出" else "已开启淡入淡出") {
                        repository.setFadeInOut(!settings.fadeInOut)
                    }
                },
            ),
            SettingEntry.Toggle(
                key = "audioFocus",
                title = "音频焦点",
                subtitle = "被其他应用抢占时自动暂停",
                checked = settings.audioFocus,
                onToggle = {
                    save(if (settings.audioFocus) "已关闭音频焦点" else "已开启音频焦点") {
                        repository.setAudioFocus(!settings.audioFocus)
                    }
                },
            ),
        )

        "sources" -> listOf(
            SettingEntry.Action(
                key = "market",
                title = "音源市场",
                subtitle = "远程清单、SHA-256 校验、一键安装",
                value = "已接入",
                onClick = onOpenSourceMarket,
            ),
            SettingEntry.Info(
                key = "scriptDir",
                title = "脚本目录",
                value = "files/lx_user_js",
                subtitle = "导入的 .js 与内置脚本都会在这里加载",
            ),
        )

        "account" -> listOf(
            SettingEntry.Action(
                key = "netease",
                title = "网易云音乐",
                subtitle = "扫码登录、Cookie 导入与我的歌单",
                value = "已接入",
                onClick = onOpenAccount,
            ),
            SettingEntry.Action(
                key = "qq",
                title = "QQ 音乐",
                subtitle = "Cookie 导入、我的歌单与整单播放",
                value = "Cookie",
                onClick = onOpenAccount,
            ),
            SettingEntry.Action(
                key = "cookie",
                title = "Cookie 文件",
                subtitle = "U 盘 cookie.json / cookie.txt，凭据加密保存",
                value = "导入",
                onClick = onOpenAccount,
            ),
        )

        "local" -> listOf(
            SettingEntry.Action(
                key = "deviceMusic",
                title = "设备歌曲",
                subtitle = "MediaStore 扫描 + U 盘 / 文件夹导入",
                value = "已接入",
                onClick = onOpenDeviceMusic,
            ),
            SettingEntry.Action(
                key = "downloads",
                title = "下载管理",
                subtitle = "已下载歌曲 · 断网也能播放",
                value = "已接入",
                onClick = onOpenDownloads,
            ),
            SettingEntry.Action(
                key = "smb",
                title = "SMB 共享",
                subtitle = "jcifs-ng 直连 NAS，凭据加密保存",
                value = "已接入",
                onClick = onOpenSmb,
            ),
        )

        "companion" -> emptyList()
        "update" -> emptyList()

        "about" -> listOf(
            SettingEntry.Info(
                key = "version",
                title = "版本",
                value = "${BuildConfig.VERSION_NAME} · M3b2",
                subtitle = "账号歌单 · 本地库 · 音源市场",
            ),
            SettingEntry.Info(
                key = "scale",
                title = "界面缩放",
                value = "%.2f×".format(scale),
                subtitle = "960×540dp 基准 · 4K 额外 1.15× 补偿",
            ),
            SettingEntry.Info(
                key = "performance",
                title = "性能红线",
                value = "≥55fps · <350MB",
                subtitle = "1080p 均值 58fps；4K ≥45fps",
            ),
            SettingEntry.Action(
                key = "license",
                title = "开源许可",
                value = "GPL-3.0",
                subtitle = "源码随仓库发布，保留上游署名",
                onClick = { capsule.show("GPL-3.0 · 完整许可见仓库 LICENSE") },
            ),
            SettingEntry.Action(
                key = "upstream",
                title = "上游署名",
                value = "PixelPlayer",
                subtitle = "r3n011/XiangsuPlayerHQ · GPL-3.0",
                onClick = { capsule.show("上游：r3n011/XiangsuPlayerHQ（GPL-3.0）") },
            ),
            SettingEntry.Action(
                key = "crash",
                title = "崩溃日志",
                value = "查看",
                subtitle = CrashLogger.latestLog(context)?.name ?: "暂无崩溃记录",
                onClick = {
                    val directory = CrashLogger.logDirectory(context).absolutePath
                    capsule.show("日志目录：$directory", durationMs = 3_800L)
                },
            ),
        )

        else -> listOf(
            SettingEntry.Info(
                key = "unknown",
                title = "未知分组",
                value = groupId,
                subtitle = "请返回上一级重新选择",
            )
        )
    }
}

private inline fun <reified T : Enum<T>> T.next(): T {
    val values = enumValues<T>()
    return values[(ordinal + 1) % values.size]
}

private inline fun <reified T : Enum<T>> T.previous(): T {
    val values = enumValues<T>()
    return values[(ordinal - 1 + values.size) % values.size]
}
