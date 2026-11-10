package tv.music.player.ui.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import tv.music.player.core.account.AccountStore
import tv.music.player.core.charts.Charts
import tv.music.player.core.focus.TvSurface
import tv.music.player.core.focus.rememberTvFocusGroup
import tv.music.player.core.playback.PlaybackController
import tv.music.player.core.theme.TvColors
import tv.music.player.core.theme.TvDimens
import tv.music.player.core.theme.TvType
import tv.music.player.ui.components.ActionChip
import tv.music.player.ui.components.ChartCard
import tv.music.player.ui.components.MainTabBar
import tv.music.player.ui.components.SectionHeader
import tv.music.player.ui.components.TrackArtwork
import tv.music.player.ui.components.TvBackdrop
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
) {
    val group = rememberTvFocusGroup("playlists")
    val accountState by account.state.collectAsStateWithLifecycle()
    val snapshot by playback.state.collectAsStateWithLifecycle()
    val playlists = accountState.playlists
    val recommended = accountState.recommended
    val square = accountState.square
    val squareCat = accountState.squareCat
    val squareLoading = accountState.squareLoading
    val charts = Charts.all

    LaunchedEffect(Unit) {
        account.refresh()
        account.refreshRecommended()
        if (square.isEmpty()) account.loadSquare(squareCat)
        val target = when {
            recommended.isNotEmpty() -> "rec:0"
            playlists.isNotEmpty() -> "pl:0"
            else -> "chart:0"
        }
        group.focusWhenReady(target, timeoutMs = 1_500L)
    }

    Box(Modifier.fillMaxSize()) {
        TvBackdrop()
        Column(
            Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = TvDimens.SafeVertical)
        ) {
            MainTabBar(
                active = "playlists",
                group = group,
                nowPlaying = snapshot.track.title.takeIf { snapshot.track.usesPlayer },
                onHome = { navController.navigate(TvRoute.Home.route) { popUpTo(TvRoute.Home.route); launchSingleTop = true } },
                onPlaylists = {},
                onMine = { navController.navigate(TvRoute.Mine.route) { popUpTo(TvRoute.Home.route); launchSingleTop = true } },
                onPlayer = { navController.navigate(TvRoute.Player.route) },
                onSettings = { navController.navigate(TvRoute.Settings.route) },
                modifier = Modifier.padding(horizontal = 26.dp),
            )
            Spacer(Modifier.height(14.dp))
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (recommended.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionHeader(
                                "网易云推荐",
                                modifier = Modifier.padding(horizontal = 26.dp),
                                trailing = "每日推荐 / 雷达等 · 登录更准",
                            )
                            TvRail(items = recommended, keyPrefix = "rec", group = group, initialIndex = 0) { _, pl, focusKey ->
                                PlaylistCard(pl, group, focusKey) {
                                    navController.navigate(TvRoute.AccountPlaylist.of(pl.provider, pl.id))
                                }
                            }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader(
                            "歌单广场",
                            modifier = Modifier.padding(horizontal = 26.dp),
                            trailing = "网易云 · 分类热门",
                        )
                        TvRail(items = SQUARE_CATS, keyPrefix = "cat", group = group, initialIndex = 0) { _, cat, focusKey ->
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
                                style = TvType.caption, color = TvColors.TextTertiary,
                                modifier = Modifier.padding(horizontal = 26.dp),
                            )
                            square.isEmpty() -> Text(
                                "「$squareCat」暂无歌单，换个分类试试。",
                                style = TvType.caption, color = TvColors.TextTertiary,
                                modifier = Modifier.padding(horizontal = 26.dp),
                            )
                            else -> TvRail(items = square, keyPrefix = "sq", group = group, initialIndex = 0) { _, pl, focusKey ->
                                PlaylistCard(pl, group, focusKey) {
                                    navController.navigate(TvRoute.AccountPlaylist.of(pl.provider, pl.id))
                                }
                            }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader(
                            "我的歌单",
                            modifier = Modifier.padding(horizontal = 26.dp),
                            trailing = if (playlists.isEmpty()) "登录账号后同步 · 见「我的」" else "${playlists.size} 个",
                        )
                        if (playlists.isEmpty()) {
                            Text(
                                "还没有歌单。到「我的 → 账号」登录网易云 / QQ 后自动同步。",
                                style = TvType.caption,
                                color = TvColors.TextTertiary,
                                modifier = Modifier.padding(horizontal = 26.dp),
                            )
                        } else {
                            TvRail(items = playlists, keyPrefix = "pl", group = group, initialIndex = 0) { _, pl, focusKey ->
                                PlaylistCard(pl, group, focusKey) {
                                    navController.navigate(TvRoute.AccountPlaylist.of(pl.provider, pl.id))
                                }
                            }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader("热门榜单", modifier = Modifier.padding(horizontal = 26.dp), trailing = "QQ / 网易云 · 每日更新")
                        TvRail(items = charts, keyPrefix = "chart", group = group, initialIndex = 0) { index, chart, focusKey ->
                            ChartCard(
                                name = chart.name, subtitle = chart.subtitle, index = index,
                                group = group, focusKey = focusKey,
                                onClick = { navController.navigate(TvRoute.ChartDetail.of(chart.id)) },
                            )
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
    group: tv.music.player.core.focus.TvFocusGroup,
    focusKey: String,
    onClick: () -> Unit,
) {
    TvSurface(
        modifier = Modifier.size(width = 150.dp, height = 190.dp),
        shape = RoundedCornerShape(16.dp),
        baseColor = TvColors.Surface.copy(alpha = 0.6f),
        group = group,
        focusKey = focusKey,
        onClick = onClick,
    ) {
        Column(Modifier.padding(8.dp)) {
            TrackArtwork(
                track = tv.music.player.core.playback.DemoTrack(
                    id = pl.id.hashCode(), title = pl.name, artist = pl.provider,
                    album = "", durationMs = 0, colorSeed = pl.id.hashCode(),
                    coverUrl = pl.coverUrl,
                ),
                modifier = Modifier.fillMaxWidth().height(134.dp),
                corner = 12.dp,
            )
            Spacer(Modifier.height(8.dp))
            Text(pl.name, style = TvType.label, color = TvColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (pl.trackCount > 0) "${pl.trackCount} 首" else "推荐歌单",
                style = TvType.caption, color = TvColors.TextSecondary, maxLines = 1,
            )
        }
    }
}