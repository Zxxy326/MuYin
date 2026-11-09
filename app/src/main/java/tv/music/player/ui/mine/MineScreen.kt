package tv.music.player.ui.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import tv.music.player.core.focus.rememberTvFocusGroup
import tv.music.player.core.playback.PlaybackController
import tv.music.player.core.theme.TvDimens
import tv.music.player.ui.components.MainTabBar
import tv.music.player.ui.components.QuickTile
import tv.music.player.ui.components.TvBackdrop
import tv.music.player.ui.navigation.TvRoute

@Composable
fun MineScreen(
    playback: PlaybackController,
    navController: NavHostController,
) {
    val group = rememberTvFocusGroup("mine")
    val snapshot by playback.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { group.focusWhenReady("mine:0", timeoutMs = 1_200L) }

    val tileWidth = 158.dp
    Box(Modifier.fillMaxSize()) {
        TvBackdrop()
        Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = TvDimens.SafeVertical)) {
            MainTabBar(
                active = "mine",
                group = group,
                nowPlaying = snapshot.track.title.takeIf { snapshot.track.usesPlayer },
                onHome = { navController.navigate(TvRoute.Home.route) { popUpTo(TvRoute.Home.route); launchSingleTop = true } },
                onPlaylists = { navController.navigate(TvRoute.Playlists.route) { popUpTo(TvRoute.Home.route); launchSingleTop = true } },
                onMine = {},
                onPlayer = { navController.navigate(TvRoute.Player.route) },
                onSettings = { navController.navigate(TvRoute.Settings.route) },
                modifier = Modifier.padding(horizontal = 26.dp),
            )
            Spacer(Modifier.height(18.dp))
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 26.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    QuickTile(Icons.Rounded.Favorite, "我的收藏", group, "mine:0", { navController.navigate(TvRoute.Favorites.route) }, Modifier.width(tileWidth))
                    QuickTile(Icons.Rounded.History, "最近播放", group, "mine:1", { navController.navigate(TvRoute.Recent.route) }, Modifier.width(tileWidth))
                    QuickTile(Icons.Rounded.Folder, "本地音乐", group, "mine:2", { navController.navigate(TvRoute.LocalHub.route) }, Modifier.width(tileWidth))
                    QuickTile(Icons.Rounded.Download, "下载管理", group, "mine:3", { navController.navigate(TvRoute.Downloads.route) }, Modifier.width(tileWidth))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    QuickTile(Icons.Rounded.AccountCircle, "账号歌单", group, "mine:4", { navController.navigate(TvRoute.Account.route) }, Modifier.width(tileWidth))
                    QuickTile(Icons.Rounded.Cloud, "音源管理", group, "mine:5", { navController.navigate(TvRoute.SettingsGroup.of("sources")) }, Modifier.width(tileWidth))
                    QuickTile(Icons.Rounded.Settings, "设置", group, "mine:6", { navController.navigate(TvRoute.Settings.route) }, Modifier.width(tileWidth))
                }
            }
        }
    }
}