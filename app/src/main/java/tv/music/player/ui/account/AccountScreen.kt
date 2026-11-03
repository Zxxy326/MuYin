package tv.music.player.ui.account

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.music.player.core.account.AccountPlaylist
import tv.music.player.core.account.AccountStore
import tv.music.player.core.account.NeteaseApi
import tv.music.player.core.account.QrLoginState
import tv.music.player.core.focus.TvFocusGroup
import tv.music.player.core.focus.TvSurface
import tv.music.player.core.focus.rememberTvFocusGroup
import tv.music.player.core.theme.LocalTvAccent
import tv.music.player.core.theme.TvColors
import tv.music.player.core.theme.TvType
import tv.music.player.core.theme.rememberCoverArt
import tv.music.player.ui.components.ActionChip
import tv.music.player.ui.components.CapsuleHostState
import tv.music.player.ui.components.ArtworkPlaceholder
import tv.music.player.ui.components.KeyHintBar
import tv.music.player.ui.components.LocalCapsuleHost
import tv.music.player.ui.components.ScreenHeader
import tv.music.player.ui.components.TvBackdrop
import tv.music.player.ui.components.TvIcon
import tv.music.player.ui.components.TvSafeArea
import tv.music.player.ui.navigation.LocalActiveRoute
import tv.music.player.ui.navigation.TvRoute

@Composable
fun AccountScreen(
    store: AccountStore,
    onOpenPlaylist: (AccountPlaylist) -> Unit,
    onBack: () -> Unit,
) {
    val group = rememberTvFocusGroup("account")
    val capsule = LocalCapsuleHost.current
    val accent = LocalTvAccent.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by store.state.collectAsStateWithLifecycle()
    val activeRoute = LocalActiveRoute.current
    val isActive = activeRoute == TvRoute.Account.route
    var armedLogout by remember { mutableStateOf<String?>(null) }
    var cookieDialog by remember { mutableStateOf(false) }

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
                capsule.show("读取 Cookie 文件失败")
            } else if (raw.length > MAX_COOKIE_FILE) {
                capsule.show("Cookie 文件过大")
            } else {
                store.importCookies(raw)
            }
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            capsule.show(it)
            store.clearMessage()
        }
    }
    LaunchedEffect(armedLogout) {
        if (armedLogout != null) {
            delay(3_000L)
            armedLogout = null
        }
    }
    val qrKey = when (state.qr) {
        is QrLoginState.Waiting, QrLoginState.Loading -> "account:qr:restart"
        is QrLoginState.Failed -> "account:qr:restart"
        else -> null
    }
    val firstKey = when {
        qrKey != null -> qrKey
        state.netease != null -> "account:netease:refresh"
        else -> "account:netease:qr"
    }
    LaunchedEffect(isActive, firstKey, state.qr is QrLoginState.Waiting) {
        if (isActive) group.focusWhenReady(firstKey, timeoutMs = 2_000L)
    }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) {
        if (windowFocused && isActive) group.focusWhenReady(firstKey, timeoutMs = 2_000L)
    }

    Box(Modifier.fillMaxSize()) {
        TvBackdrop()
        TvSafeArea {
            Column(Modifier.fillMaxSize()) {
                ScreenHeader(
                    title = "账号与歌单",
                    subtitle = "网易云扫码 · 双平台 Cookie · 我的歌单",
                    group = group,
                    onBack = onBack,
                    trailing = {
                        Text(
                            when {
                                state.loading -> "同步中…"
                                state.netease != null -> "已登录 ${state.netease?.nickname.orEmpty()}"
                                else -> "未登录"
                            },
                            style = TvType.caption,
                            color = TvColors.TextTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ProviderCard(
                        title = "网易云音乐",
                        subtitle = state.netease?.nickname ?: "扫码登录 / Cookie 导入",
                        seed = 23,
                        provider = "网易云",
                        group = group,
                        primary = state.netease == null,
                        actions = {
                            if (state.netease == null) {
                                ActionChip(
                                    label = "扫码登录",
                                    icon = Icons.Rounded.AccountCircle,
                                    group = group,
                                    focusKey = "account:netease:qr",
                                    primary = true,
                                    onClick = { store.startNeteaseQr() },
                                )
                            } else {
                                ActionChip(
                                    label = "刷新歌单",
                                    icon = Icons.Rounded.Refresh,
                                    group = group,
                                    focusKey = "account:netease:refresh",
                                    onClick = { store.refresh() },
                                )
                                ActionChip(
                                    label = if (armedLogout == "netease") "再按退出" else "退出",
                                    icon = Icons.Rounded.Logout,
                                    group = group,
                                    focusKey = "account:netease:logout",
                                    onClick = {
                                        if (armedLogout == "netease") {
                                            armedLogout = null
                                            store.logout(NeteaseApi.PROVIDER)
                                        } else {
                                            armedLogout = "netease"
                                            capsule.show("再按一次退出网易云")
                                        }
                                    },
                                )
                            }
                            ActionChip(
                                label = "Cookie",
                                icon = Icons.Rounded.FileOpen,
                                group = group,
                                focusKey = "account:netease:cookie",
                                onClick = { runCatching { importLauncher.launch(arrayOf("*/*")) } },
                            )
                            ActionChip(
                                label = "粘贴 Cookie",
                                icon = Icons.Rounded.ContentPaste,
                                group = group,
                                focusKey = "account:netease:paste",
                                onClick = { cookieDialog = true },
                            )
                        },
                    )
                    ProviderCard(
                        title = "QQ 音乐",
                        subtitle = state.qq?.nickname ?: "Cookie 导入 · 歌单同步",
                        seed = 67,
                        provider = "QQ",
                        group = group,
                        primary = false,
                        actions = {
                            ActionChip(
                                label = "导入 Cookie",
                                icon = Icons.Rounded.FileOpen,
                                group = group,
                                focusKey = "account:qq:cookie",
                                onClick = { runCatching { importLauncher.launch(arrayOf("*/*")) } },
                            )
                            ActionChip(
                                label = "粘贴 Cookie",
                                icon = Icons.Rounded.ContentPaste,
                                group = group,
                                focusKey = "account:qq:paste",
                                onClick = { cookieDialog = true },
                            )
                            if (state.qq != null) {
                                ActionChip(
                                    label = if (armedLogout == "qq") "再按移除" else "移除",
                                    icon = Icons.Rounded.Logout,
                                    group = group,
                                    focusKey = "account:qq:logout",
                                    onClick = {
                                        if (armedLogout == "qq") {
                                            armedLogout = null
                                            store.logout("qq")
                                        } else {
                                            armedLogout = "qq"
                                            capsule.show("再按一次移除 QQ Cookie")
                                        }
                                    },
                                )
                            }
                        },
                    )
                }
                if (state.qr !is QrLoginState.Idle) {
                    Spacer(Modifier.height(10.dp))
                    QrPanel(
                        qr = state.qr,
                        group = group,
                        onRestart = { store.startNeteaseQr() },
                        onCancel = { store.cancelQr() },
                    )
                } else {
                    Spacer(Modifier.height(10.dp))
                    PlaylistSection(
                        playlists = state.playlists,
                        loading = state.loading,
                        error = state.error,
                        group = group,
                        onOpen = onOpenPlaylist,
                    )
                }
                if (state.qr is QrLoginState.Idle) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Cookie 文件：{\"netease\":\"MUSIC_U=...\",\"qq\":\"qm_keyst=...\"}；QQ 需包含 uin 与 qm_keyst。",
                        style = TvType.caption,
                        color = TvColors.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                KeyHintBar(
                    hints = listOf(
                        "OK" to "执行 / 打开歌单",
                        "返回" to "上一级",
                        "长按返回" to "回到首页",
                    )
                )
                Spacer(Modifier.height(12.dp))
            }
        }
        if (cookieDialog) {
            CookiePasteDialog(
                onDismiss = { cookieDialog = false },
                onImport = { raw ->
                    if (raw.length > MAX_COOKIE_FILE) {
                        capsule.show("Cookie 内容过长")
                    } else {
                        store.importCookies(raw)
                    }
                },
                capsule = capsule,
            )
        }
    }
}

@Composable
private fun RowScope.ProviderCard(
    title: String,
    subtitle: String,
    seed: Int,
    provider: String,
    group: TvFocusGroup,
    primary: Boolean,
    actions: @Composable () -> Unit,
) {
    val accent = LocalTvAccent.current
    TvSurface(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(16.dp),
        baseColor = TvColors.Surface.copy(alpha = 0.66f),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArtworkPlaceholder(seed, Modifier.size(42.dp), corner = 11.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = TvType.label, color = TvColors.TextPrimary)
                    Text(
                        subtitle,
                        style = TvType.caption,
                        color = if (primary) accent else TvColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(provider, style = TvType.caption, color = TvColors.TextTertiary)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                actions()
            }
        }
    }
}

@Composable
private fun ColumnScope.QrPanel(
    qr: QrLoginState,
    group: TvFocusGroup,
    onRestart: () -> Unit,
    onCancel: () -> Unit,
) {
    val accent = LocalTvAccent.current
    val url = (qr as? QrLoginState.Waiting)?.url
    val bitmap = remember(url) { url?.let(::generateQrBitmap)?.asImageBitmap() }
    TvSurface(
        modifier = Modifier.fillMaxWidth().weight(1f),
        shape = RoundedCornerShape(18.dp),
        baseColor = TvColors.Surface.copy(alpha = 0.62f),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(160.dp).background(Color.White, RoundedCornerShape(12.dp)).padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "网易云登录二维码",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Text(
                        if (qr is QrLoginState.Loading) "获取中…" else "二维码不可用",
                        style = TvType.caption,
                        color = Color.Black,
                    )
                }
            }
            Spacer(Modifier.width(22.dp))
            Column(Modifier.weight(1f)) {
                Text("网易云扫码登录", style = TvType.label, color = TvColors.TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text(
                    when (qr) {
                        QrLoginState.Loading -> "正在获取二维码…"
                        is QrLoginState.Waiting -> qr.status
                        is QrLoginState.Failed -> qr.message
                        is QrLoginState.Success -> "已登录：${qr.nickname}"
                        else -> "请使用网易云音乐 App 扫码"
                    },
                    style = TvType.body,
                    color = if (qr is QrLoginState.Failed) TvColors.Danger else accent,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "手机打开网易云音乐 → 扫一扫，确认后电视会自动登录。二维码约 3 分钟有效。",
                    style = TvType.caption,
                    color = TvColors.TextSecondary,
                    maxLines = 2,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionChip(
                        label = if (qr is QrLoginState.Waiting) "刷新二维码" else "重新获取",
                        icon = Icons.Rounded.Refresh,
                        group = group,
                        focusKey = "account:qr:restart",
                        onClick = onRestart,
                    )
                    ActionChip(
                        label = "取消",
                        group = group,
                        focusKey = "account:qr:cancel",
                        onClick = onCancel,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.PlaylistSection(
    playlists: List<AccountPlaylist>,
    loading: Boolean,
    error: String?,
    group: TvFocusGroup,
    onOpen: (AccountPlaylist) -> Unit,
) {
    Column(Modifier.fillMaxWidth().weight(1f)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("我的歌单", style = TvType.label, color = TvColors.TextPrimary)
            Spacer(Modifier.width(10.dp))
            Text("${playlists.size} 个", style = TvType.caption, color = TvColors.TextTertiary)
        }
        Spacer(Modifier.height(6.dp))
        when {
            error != null -> Text(error, style = TvType.caption, color = TvColors.Danger)
            loading -> Text("正在读取歌单…", style = TvType.caption, color = TvColors.TextSecondary)
            playlists.isEmpty() -> Text(
                "登录网易云后，这里会显示你的歌单；也可以用 Cookie 文件导入。",
                style = TvType.caption,
                color = TvColors.TextTertiary,
            )
            else -> LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                items(playlists, key = { "${it.provider}:${it.id}" }) { playlist ->
                    PlaylistRow(playlist, group, onClick = { onOpen(playlist) })
                }
            }
        }
    }
}

private fun providerLabel(provider: String): String =
    if (provider == "qq") "QQ 音乐" else "网易云"

@Composable
private fun PlaylistRow(
    playlist: AccountPlaylist,
    group: TvFocusGroup,
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
        focusKey = "account:pl:${playlist.provider}:${playlist.id}",
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val cover = rememberCoverArt(playlist.coverUrl)
            Image(
                bitmap = cover.image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(9.dp)),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    playlist.name,
                    style = TvType.label,
                    color = TvColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${playlist.trackCount} 首 · ${providerLabel(playlist.provider)}",
                    style = TvType.caption,
                    color = TvColors.TextSecondary,
                )
            }
            Text("打开", style = TvType.caption, color = accent)
        }
    }
}

private fun generateQrBitmap(text: String): Bitmap? = runCatching {
    val size = 384
    val matrix = QRCodeWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        ),
    )
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            pixels[y * size + x] = if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE
        }
    }
    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, size, 0, 0, size, size)
    }
}.getOrNull()

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CookiePasteDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
    capsule: CapsuleHostState,
) {
    val dialogGroup = rememberTvFocusGroup("cookie-paste")
    val accent = LocalTvAccent.current
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var text by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var fieldFocused by remember { mutableStateOf(false) }
    var imeWasVisible by remember { mutableStateOf(false) }
    val fieldRequester = remember { FocusRequester() }
    val inputRequester = remember { FocusRequester() }
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(Unit) { dialogGroup.focusWhenReady("cookie:clipboard", timeoutMs = 800L) }
    LaunchedEffect(editing) {
        if (editing) {
            inputRequester.requestFocus()
            keyboard?.show()
        }
    }
    LaunchedEffect(imeVisible) {
        if (imeVisible) {
            imeWasVisible = true
        } else if (editing && imeWasVisible) {
            imeWasVisible = false
            editing = false
            fieldRequester.requestFocus()
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            TvSurface(
                modifier = Modifier.width(820.dp),
                shape = RoundedCornerShape(20.dp),
                baseColor = TvColors.SurfaceElevated,
                focusedScale = 1f,
                glowSize = 0.dp,
                showBorder = false,
            ) {
                Column(Modifier.fillMaxWidth().padding(22.dp)) {
                    Text("粘贴 Cookie", style = TvType.title, color = TvColors.TextPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "支持整段请求头（网易云 MUSIC_U=…，QQ qm_keyst=…），也支持 {\"netease\":\"…\",\"qq\":\"…\"}",
                        style = TvType.caption,
                        color = TvColors.TextTertiary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(TvColors.Surface)
                            .border(
                                1.dp,
                                if (fieldFocused) accent else TvColors.Divider,
                                RoundedCornerShape(12.dp),
                            )
                            .focusRequester(fieldRequester)
                            .onFocusChanged { fieldFocused = it.hasFocus }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { editing = true }
                            .padding(12.dp)
                    ) {
                        if (!editing) {
                            Text(
                                text.ifBlank { "在这里粘贴 Cookie，或先复制到剪贴板再按“读剪贴板”" },
                                style = TvType.body,
                                color = if (text.isBlank()) TvColors.TextTertiary else TvColors.TextPrimary,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else {
                            if (text.isBlank()) {
                                Text(
                                    "在这里粘贴 Cookie，或先复制到剪贴板再按“读剪贴板”",
                                    style = TvType.body,
                                    color = TvColors.TextTertiary,
                                )
                            }
                            BasicTextField(
                                value = text,
                                onValueChange = { text = it.take(MAX_COOKIE_FILE) },
                                textStyle = TvType.body.copy(color = TvColors.TextPrimary),
                                cursorBrush = SolidColor(accent),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .focusRequester(inputRequester),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionChip(
                            label = "读剪贴板",
                            icon = Icons.Rounded.ContentPaste,
                            group = dialogGroup,
                            focusKey = "cookie:clipboard",
                            onClick = {
                                val clip = clipboard.getText()?.text?.trim().orEmpty()
                                if (clip.isBlank()) {
                                    capsule.show("剪贴板是空的")
                                } else {
                                    text = clip.take(MAX_COOKIE_FILE)
                                    capsule.show("已读取剪贴板")
                                }
                            },
                        )
                        ActionChip(
                            label = "导入",
                            group = dialogGroup,
                            focusKey = "cookie:import",
                            primary = true,
                            onClick = {
                                val value = text.trim()
                                if (value.isBlank()) {
                                    capsule.show("请先粘贴 Cookie")
                                } else {
                                    onImport(value)
                                    onDismiss()
                                }
                            },
                        )
                        ActionChip(
                            label = "取消",
                            group = dialogGroup,
                            focusKey = "cookie:cancel",
                            onClick = onDismiss,
                        )
                    }
                }
            }
        }
    }
}

private const val MAX_COOKIE_FILE = 64 * 1024
