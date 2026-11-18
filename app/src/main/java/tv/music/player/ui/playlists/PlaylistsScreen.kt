package tv.music.player.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import tv.music.player.core.account.AccountStore
import tv.music.player.core.focus.TvFocusGroup
import tv.music.player.core.focus.TvSurface
import tv.music.player.core.focus.rememberTvFocusGroup
import tv.music.player.core.playback.PlaybackController
import tv.music.player.core.theme.LocalTvAccent
import tv.music.player.core.theme.TvColors
import tv.music.player.core.theme.TvDimens
import tv.music.player.core.theme.TvFonts
import tv.music.player.core.theme.rememberCoverArt
import tv.music.player.ui.components.ActionChip
import tv.music.player.ui.components.MainTabBar
import tv.music.player.ui.components.SectionHeader
import tv.music.player.ui.components.StageGlass
import tv.music.player.ui.components.StageGlassSoft
import tv.music.player.ui.components.StageHeading
import tv.music.player.ui.components.StageTextSecondary
import tv.music.player.ui.components.StageTextShadow
import tv.music.player.ui.components.StageTextTertiary
import tv.music.player.ui.components.TrackArtwork
import tv.music.player.ui.components.TvBackdrop
import tv.music.player.ui.components.TvIcon
import tv.music.player.ui.components.TvRail
import tv.music.player.ui.navigation.TvRoute

private val SQUARE_CATS = listOf(
    "华语", "流行", "摇滚", "民谣", "电子", "说唱", "轻音乐", "爵士", "古典", "古风",
    "影视原声", "ACG", "运动", "学习", "治愈", "夜晚", "驾车", "旅行", "经典", "翻唱",
)

@Composable
fun PlaylistsScreen(
    account: AccountStore,
    playback: PlaybackController,
    navController: NavHostController,
    embedded: Boolean = false,
) {
    val group = rememberTvFocusGroup("playlists")
    val accountState by account.state.collectAsStateWithLifecycle()
    val playlists = accountState.playlists
    val recommended = accountState.recommended
    val square = accountState.square
    val squareCat = accountState.squareCat
    val squareLoading = accountState.squareLoading
    val gutter = if (embedded) 0.dp else 26.dp
    val railGap = if (embedded) 14.dp else 16.dp
    val railPadding = if (embedded) {
        PaddingValues(horizontal = 10.dp, vertical = 18.dp)
    } else {
        PaddingValues(vertical = 20.dp, horizontal = 26.dp)
    }

    var refreshedOnce by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!refreshedOnce) {
            refreshedOnce = true
            account.refresh()
            account.refreshRecommended()
        }
        if (square.isEmpty()) account.loadSquare(squareCat)
        val target = when {
            recommended.isNotEmpty() -> "rec:0"
            playlists.isNotEmpty() -> "pl:0"
            else -> "cat:0"
        }
        if (!embedded) group.focusWhenReady(target, timeoutMs = 1_500L)
    }

    Box(Modifier.fillMaxSize()) {
        if (!embedded) TvBackdrop()
        Column(
            Modifier.fillMaxSize().then(
                if (embedded) Modifier
                else Modifier.padding(horizontal = 22.dp, vertical = TvDimens.SafeVertical)
            )
        ) {
            if (!embedded) MainTabBar(
                active = "playlists",
                group = group,
                nowPlaying = null,
                onHome = { navController.navigate(TvRoute.Home.route) { popUpTo(TvRoute.Home.route); launchSingleTop = true } },
                onPlaylists = {},
                onMine = { navController.navigate(TvRoute.Mine.route) { popUpTo(TvRoute.Home.route); launchSingleTop = true } },
                onPlayer = { navController.navigate(TvRoute.Player.route) },
                onSettings = { navController.navigate(TvRoute.Settings.route) },
                modifier = Modifier.padding(horizontal = 26.dp),
            )
            if (!embedded) Spacer(Modifier.height(14.dp))
            StageHeading(
                kicker = "PLAYLISTS",
                title = "歌单",
                subtitle = "分类热门 · 我的收藏与推荐",
                modifier = Modifier.padding(horizontal = gutter),
            ) {
                Text(
                    if (playlists.isEmpty()) "登录后同步我的歌单" else "${playlists.size} 个我的歌单",
                    style = TextStyle(
                        fontFamily = TvFonts.family,
                        fontSize = 10.5.sp,
                        shadow = StageTextShadow,
                    ),
                    color = StageTextTertiary,
                )
            }
            Spacer(Modifier.height(4.dp))
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (recommended.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            SectionHeader(
                                "网易云推荐",
                                modifier = Modifier.padding(horizontal = gutter),
                                trailing = "每日推荐 / 雷达等 · 登录更准",
                            )
                            TvRail(
                                items = recommended,
                                keyPrefix = "rec",
                                group = group,
                                initialIndex = 0,
                                spacing = railGap,
                                contentPadding = railPadding,
                            ) { _, pl, focusKey ->
                                PlaylistCard(pl, group, focusKey) {
                                    navController.navigate(TvRoute.AccountPlaylist.of(pl.provider, pl.id))
                                }
                            }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        SectionHeader(
                            "歌单广场",
                            modifier = Modifier.padding(horizontal = gutter),
                            trailing = "网易云 · 分类热门",
                        )
                        TvRail(
                            items = SQUARE_CATS,
                            keyPrefix = "cat",
                            group = group,
                            initialIndex = 0,
                            spacing = 8.dp,
                            contentPadding = railPadding,
                        ) { _, cat, focusKey ->
                            ActionChip(
                                label = cat,
                                group = group,
                                focusKey = focusKey,
                                primary = cat == squareCat,
                                onClick = { account.loadSquare(cat) },
                            )
                        }
                        when {
                            squareLoading -> Text(
                                "正在加载「$squareCat」歌单…",
                                style = TextStyle(
                                    fontFamily = TvFonts.family,
                                    fontSize = 11.sp,
                                    shadow = StageTextShadow,
                                ),
                                color = StageTextTertiary,
                                modifier = Modifier.padding(horizontal = gutter, vertical = 8.dp),
                            )
                            square.isEmpty() -> Text(
                                "「$squareCat」暂无歌单，换个分类试试。",
                                style = TextStyle(
                                    fontFamily = TvFonts.family,
                                    fontSize = 11.sp,
                                    shadow = StageTextShadow,
                                ),
                                color = StageTextTertiary,
                                modifier = Modifier.padding(horizontal = gutter, vertical = 8.dp),
                            )
                            else -> TvRail(
                                items = square,
                                keyPrefix = "sq",
                                group = group,
                                initialIndex = 0,
                                spacing = railGap,
                                contentPadding = railPadding,
                            ) { _, pl, focusKey ->
                                PlaylistCard(pl, group, focusKey) {
                                    navController.navigate(TvRoute.AccountPlaylist.of(pl.provider, pl.id))
                                }
                            }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        SectionHeader(
                            "我的歌单",
                            modifier = Modifier.padding(horizontal = gutter),
                            trailing = if (playlists.isEmpty()) "登录账号后同步" else "${playlists.size} 个",
                        )
                        if (playlists.isEmpty()) {
                            Box(
                                Modifier.fillMaxWidth().height(60.dp)
                                    .padding(top = 8.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(StageGlass)
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    "还没有歌单。到「更多 → 管理账号」登录网易云 / QQ 后自动同步。",
                                    style = TextStyle(
                                        fontFamily = TvFonts.family,
                                        fontSize = 11.sp,
                                        shadow = StageTextShadow,
                                    ),
                                    color = StageTextSecondary,
                                )
                            }
                        } else {
                            TvRail(
                                items = playlists,
                                keyPrefix = "pl",
                                group = group,
                                initialIndex = 0,
                                spacing = railGap,
                                contentPadding = railPadding,
                            ) { _, pl, focusKey ->
                                PlaylistCard(pl, group, focusKey) {
                                    navController.navigate(TvRoute.AccountPlaylist.of(pl.provider, pl.id))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistCard(
    pl: tv.music.player.core.account.AccountPlaylist,
    group: TvFocusGroup,
    focusKey: String,
    onClick: () -> Unit,
) {
    val accent = LocalTvAccent.current
    var focused by remember(pl.id) { mutableStateOf(false) }
    TvSurface(
        modifier = Modifier.size(width = 150.dp, height = 188.dp),
        shape = RoundedCornerShape(16.dp),
        baseColor = Color.Transparent,
        borderColor = Color.Transparent,
        focusedScale = 1.03f,
        glowSize = 12.dp,
        group = group,
        focusKey = focusKey,
        onFocusedChange = { focused = it },
        onClick = onClick,
    ) {
        Column(Modifier.fillMaxSize().padding(4.dp)) {
            Box(
                Modifier.size(142.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.04f))
            ) {
                TrackArtwork(
                    track = tv.music.player.core.playback.DemoTrack(
                        id = pl.id.hashCode(), title = pl.name, artist = pl.provider,
                        album = "", durationMs = 0, colorSeed = pl.id.hashCode(),
                        coverUrl = pl.coverUrl,
                    ),
                    modifier = Modifier.fillMaxSize(),
                    corner = 0.dp,
                )
                if (focused) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(8.dp)
                            .size(28.dp).clip(CircleShape)
                            .background(Color(0xCC05070C))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        TvIcon(Icons.Rounded.PlayArrow, Modifier.size(15.dp), tint = accent)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                pl.name,
                style = TextStyle(
                    fontFamily = TvFonts.family,
                    fontSize = 13.5.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    shadow = StageTextShadow,
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (pl.trackCount > 0) "${pl.trackCount} 首" else "推荐歌单",
                style = TextStyle(
                    fontFamily = TvFonts.family,
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp,
                    shadow = StageTextShadow,
                ),
                color = StageTextSecondary,
                maxLines = 1,
            )
        }
    }
}
