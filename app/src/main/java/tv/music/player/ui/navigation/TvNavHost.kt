package tv.music.player.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import tv.music.player.core.charts.ChartRepository
import tv.music.player.core.charts.Charts
import tv.music.player.core.companion.CompanionBridge
import tv.music.player.core.companion.CompanionServer
import tv.music.player.core.account.AccountStore
import tv.music.player.core.download.DownloadStore
import tv.music.player.core.library.LibraryStore
import tv.music.player.core.local.LocalLibraryStore
import tv.music.player.core.source.SourceMarketStore
import tv.music.player.core.smb.SmbConfigStore
import tv.music.player.core.smb.SmbRepository
import tv.music.player.core.lx.LxFileStore
import tv.music.player.core.lx.LxJsEngine
import tv.music.player.core.lyrics.LyricsRepository
import tv.music.player.core.playback.PlaybackController
import tv.music.player.core.search.OnlineSearchApi
import tv.music.player.core.search.SearchHistoryStore
import tv.music.player.core.settings.AppSettings
import tv.music.player.core.settings.SettingsRepository
import tv.music.player.core.update.UpdateStore
import tv.music.player.ui.account.AccountPlaylistScreen
import tv.music.player.ui.account.AccountScreen
import tv.music.player.ui.account.DailyRecommendScreen
import tv.music.player.ui.charts.ChartDetailScreen
import tv.music.player.ui.home.HomeScreen
import tv.music.player.ui.library.DownloadsScreen
import tv.music.player.ui.local.DeviceMusicScreen
import tv.music.player.ui.local.LocalHubScreen
import tv.music.player.ui.local.SmbScreen
import tv.music.player.ui.mine.MineScreen
import tv.music.player.ui.playlists.PlaylistsScreen
import tv.music.player.ui.source.SourceMarketScreen
import tv.music.player.ui.source.SourceReleaseScreen
import tv.music.player.ui.library.LibraryScreen
import tv.music.player.ui.player.PlayerScreen
import tv.music.player.ui.search.SearchScreen
import tv.music.player.ui.settings.SettingsGroupScreen
import tv.music.player.ui.settings.SettingsScreen

@Composable
fun TvNavHost(
    navController: NavHostController,
    settings: AppSettings,
    settingsRepository: SettingsRepository,
    playback: PlaybackController,
    onlineSearch: OnlineSearchApi,
    charts: ChartRepository,
    searchHistory: SearchHistoryStore,
    companionBridge: CompanionBridge,
    companionServer: CompanionServer,
    library: LibraryStore,
    localLibrary: LocalLibraryStore,
    smbConfig: SmbConfigStore,
    smbRepository: SmbRepository,
    sourceMarket: SourceMarketStore,
    account: AccountStore,
    downloads: DownloadStore,
    updates: UpdateStore,
    lxEngine: LxJsEngine,
    lxFileStore: LxFileStore,
    lyricsRepository: LyricsRepository,
) {
    NavHost(
        navController = navController,
        startDestination = TvRoute.Home.route,
        enterTransition = { fadeIn(tween(180)) },
        exitTransition = { fadeOut(tween(120)) },
        popEnterTransition = { fadeIn(tween(180)) },
        popExitTransition = { fadeOut(tween(120)) },
    ) {
        composable(TvRoute.Home.route) {
            HomeScreen(
                playback = playback,
                library = library,
                navController = navController,
            )
        }
        composable(TvRoute.Playlists.route) {
            PlaylistsScreen(
                account = account,
                playback = playback,
                navController = navController,
            )
        }
        composable(TvRoute.Mine.route) {
            MineScreen(
                playback = playback,
                navController = navController,
            )
        }
        composable(TvRoute.Search.route) {
            SearchScreen(
                playback = playback,
                onlineSearch = onlineSearch,
                searchHistory = searchHistory,
                companionBridge = companionBridge,
                navController = navController,
            )
        }
        composable(
            route = TvRoute.ChartDetail.route,
            arguments = listOf(navArgument("chartId") { type = NavType.StringType }),
        ) { entry ->
            val chart = Charts.byId(entry.arguments?.getString("chartId").orEmpty())
            if (chart == null) {
                androidx.compose.runtime.LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                ChartDetailScreen(
                    chart = chart,
                    repository = charts,
                    playback = playback,
                    navController = navController,
                )
            }
        }
        composable(TvRoute.Player.route) {
            PlayerScreen(
                playback = playback,
                settings = settings,
                settingsRepository = settingsRepository,
                lyricsRepository = lyricsRepository,
                library = library,
                downloads = downloads,
                onBack = { navController.popBackStack() },
            )
        }
        composable(TvRoute.Favorites.route) {
            val favorites by library.favorites.collectAsState()
            LibraryScreen(
                route = TvRoute.Favorites.route,
                title = "我的收藏",
                subtitle = "播放页 ↑ 打开控制条 · 选择收藏即可加入这里",
                emptyText = "还没有收藏。播放喜欢的歌，在控制条里按收藏。",
                tracks = favorites,
                playback = playback,
                navController = navController,
            )
        }
        composable(TvRoute.Recent.route) {
            val recent by library.recent.collectAsState()
            LibraryScreen(
                route = TvRoute.Recent.route,
                title = "最近播放",
                subtitle = "开始播放后自动记录 · 最多保留 30 首",
                emptyText = "还没有播放记录。去搜索并播放一首歌吧。",
                tracks = recent,
                playback = playback,
                navController = navController,
            )
        }
        composable(TvRoute.LocalHub.route) {
            LocalHubScreen(
                localLibrary = localLibrary,
                downloads = downloads,
                navController = navController,
            )
        }
        composable(TvRoute.DeviceMusic.route) {
            DeviceMusicScreen(
                localLibrary = localLibrary,
                playback = playback,
                navController = navController,
            )
        }
        composable(TvRoute.Smb.route) {
            SmbScreen(
                configStore = smbConfig,
                repository = smbRepository,
                localLibrary = localLibrary,
                playback = playback,
                navController = navController,
            )
        }
        composable(TvRoute.Account.route) {
            AccountScreen(
                store = account,
                onOpenPlaylist = { playlist ->
                    navController.navigate(TvRoute.AccountPlaylist.of(playlist.provider, playlist.id))
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = TvRoute.AccountPlaylist.route,
            arguments = listOf(
                navArgument("provider") { type = NavType.StringType },
                navArgument("id") { type = NavType.StringType },
            ),
        ) { entry ->
            AccountPlaylistScreen(
                store = account,
                playback = playback,
                provider = entry.arguments?.getString("provider").orEmpty(),
                playlistId = entry.arguments?.getString("id").orEmpty(),
                navController = navController,
            )
        }
        composable(TvRoute.DailyRecommend.route) {
            DailyRecommendScreen(
                store = account,
                playback = playback,
                navController = navController,
            )
        }
        composable(TvRoute.SourceMarket.route) {
            SourceMarketScreen(
                store = sourceMarket,
                onOpenRelease = { tag -> navController.navigate(TvRoute.MarketRelease.of(tag)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = TvRoute.MarketRelease.route,
            arguments = listOf(navArgument("tag") { type = NavType.StringType }),
        ) { entry ->
            SourceReleaseScreen(
                store = sourceMarket,
                tag = entry.arguments?.getString("tag").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(TvRoute.Downloads.route) {
            DownloadsScreen(
                downloads = downloads,
                playback = playback,
                navController = navController,
            )
        }
        composable(TvRoute.Settings.route) {
            SettingsScreen(settings = settings, navController = navController)
        }
        composable(
            route = TvRoute.SettingsGroup.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
        ) { entry ->
            SettingsGroupScreen(
                groupId = entry.arguments?.getString("groupId").orEmpty(),
                settings = settings,
                settingsRepository = settingsRepository,
                lxEngine = lxEngine,
                lxFileStore = lxFileStore,
                onlineSearch = onlineSearch,
                companionServer = companionServer,
                updateStore = updates,
                onOpenDeviceMusic = { navController.navigate(TvRoute.DeviceMusic.route) },
                onOpenDownloads = { navController.navigate(TvRoute.Downloads.route) },
                onOpenSourceMarket = { navController.navigate(TvRoute.SourceMarket.route) },
                onOpenSmb = { navController.navigate(TvRoute.Smb.route) },
                onOpenAccount = { navController.navigate(TvRoute.Account.route) },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
