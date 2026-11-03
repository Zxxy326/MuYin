package tv.music.player.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import tv.music.player.MainActivity
import tv.music.player.TvMusicApplication
import tv.music.player.core.companion.CompanionEvent
import tv.music.player.core.focus.TvFocusRegistry
import tv.music.player.core.scale.TvScaleProvider
import tv.music.player.core.settings.AppSettings
import tv.music.player.core.theme.TvColors
import tv.music.player.core.theme.TvTheme
import tv.music.player.core.theme.accentFor
import tv.music.player.core.theme.rememberCoverArt
import tv.music.player.ui.components.CapsuleHost
import tv.music.player.ui.components.LocalCapsuleHost
import tv.music.player.ui.components.MiniPlayerBar
import tv.music.player.ui.components.rememberCapsuleHostState
import tv.music.player.ui.navigation.LocalActiveRoute
import tv.music.player.ui.navigation.TvNavHost
import tv.music.player.ui.navigation.TvRoute

@Composable
fun TvMusicApp() {
    val context = LocalContext.current
    val container = (context.applicationContext as TvMusicApplication).container
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val playbackState by container.playback.state.collectAsStateWithLifecycle()
    val coverArt = rememberCoverArt(playbackState.track.artworkUrl)
    val accent by animateColorAsState(
        targetValue = accentFor(settings.accentMode, coverArt.accent),
        animationSpec = tween(if (settings.animationsEnabled) 420 else 0),
        label = "theme-accent",
    )

    val navController = rememberNavController()
    val capsuleHost = rememberCapsuleHostState()
    val availableUpdate by container.updates.availableUpdate.collectAsStateWithLifecycle()
    var announcedUpdateCode by remember { mutableStateOf(0L) }
    LaunchedEffect(availableUpdate?.versionCode) {
        val info = availableUpdate ?: return@LaunchedEffect
        if (info.versionCode > announcedUpdateCode) {
            announcedUpdateCode = info.versionCode
            capsuleHost.show("发现新版本 ${info.versionName} · 设置 → 更新 中下载", 8_000L)
        }
    }
    LaunchedEffect(container.playback) {
        container.playback.errors.collect { capsuleHost.show(it) }
    }
    LaunchedEffect(container.companionBridge) {
        container.companionBridge.events.collect { event ->
            when (event) {
                is CompanionEvent.Search -> {
                    navController.navigate(TvRoute.Search.route) { launchSingleTop = true }
                    capsuleHost.show("手机发送：${event.query}")
                }
                CompanionEvent.OpenPlayer -> {
                    navController.navigate(TvRoute.Player.route) { launchSingleTop = true }
                }
                is CompanionEvent.Message -> capsuleHost.show(event.text)
            }
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val homeGroup = remember { TvFocusRegistry.group("home") }
    val railBrowsing by remember {
        derivedStateOf { homeGroup.activeKey?.startsWith("rail:") == true }
    }
    val showMiniBar = (route == TvRoute.Home.route && !railBrowsing) ||
        route == TvRoute.Settings.route ||
        route == TvRoute.SettingsGroup.route

    TvScaleProvider {
        TvTheme(accent = accent, animationsEnabled = settings.animationsEnabled) {
            CompositionLocalProvider(
                LocalCapsuleHost provides capsuleHost,
                LocalActiveRoute provides route,
            ) {
                Box(Modifier.fillMaxSize().background(TvColors.Background)) {
                    TvNavHost(
                        navController = navController,
                        settings = settings,
                        settingsRepository = container.settings,
                        playback = container.playback,
                        onlineSearch = container.onlineSearch,
                        searchHistory = container.searchHistory,
                        companionBridge = container.companionBridge,
                        companionServer = container.companion,
                        library = container.library,
                        localLibrary = container.localLibrary,
                        smbConfig = container.smbConfig,
                        smbRepository = container.smbRepository,
                        sourceMarket = container.sourceMarket,
                        account = container.account,
                        downloads = container.downloads,
                        updates = container.updates,
                        lxEngine = container.lxEngine,
                        lxFileStore = container.lxFileStore,
                        lyricsRepository = container.lyrics,
                    )

                    if (showMiniBar) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(180.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, TvColors.Background.copy(alpha = 0.92f))
                                    )
                                )
                        )
                        MiniPlayerBar(
                            playback = container.playback,
                            lyricsRepository = container.lyrics,
                            library = container.library,
                            offsetMs = settings.lyricOffsetMs,
                            onOpenPlayer = {
                                navController.navigate(TvRoute.Player.route) { launchSingleTop = true }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .padding(horizontal = 48.dp, vertical = 20.dp),
                        )
                    }

                    CapsuleHost(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp)
                    )
                }
            }
        }
    }

    val activity = context as? MainActivity
    DisposableEffect(navController, activity) {
        activity?.onBackLongPress = {
            navController.popBackStack(TvRoute.Home.route, inclusive = false)
        }
        onDispose {
            activity?.let { it.onBackLongPress = null }
        }
    }
}
