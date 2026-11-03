package tv.music.player.ui.search

import android.os.SystemClock
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tv.music.player.core.companion.CompanionBridge
import tv.music.player.core.focus.TvFocusGroup
import tv.music.player.core.focus.TvSurface
import tv.music.player.core.focus.rememberTvFocusGroup
import tv.music.player.core.playback.DemoCatalog
import tv.music.player.core.playback.PlaybackController
import tv.music.player.core.playback.DemoTrack
import tv.music.player.core.playback.formatDuration
import tv.music.player.core.search.OnlineSearchApi
import tv.music.player.core.search.PinyinT9
import tv.music.player.core.search.SearchHistoryStore
import tv.music.player.core.search.decodeTracks
import tv.music.player.core.search.encodeTracks
import tv.music.player.core.search.toDemoTrack
import tv.music.player.core.theme.LocalTvAccent
import tv.music.player.core.theme.TvColors
import tv.music.player.core.theme.TvType
import tv.music.player.ui.components.ActionChip
import tv.music.player.ui.components.TrackArtwork
import tv.music.player.ui.components.Keycap
import tv.music.player.ui.components.LocalCapsuleHost
import tv.music.player.ui.components.ScreenHeader
import tv.music.player.ui.components.SkeletonBox
import tv.music.player.ui.components.TvBackdrop
import tv.music.player.ui.components.TvIcon
import tv.music.player.ui.components.TvSafeArea
import tv.music.player.ui.navigation.LocalActiveRoute
import tv.music.player.ui.navigation.TvRoute

@Composable
fun SearchScreen(
    playback: PlaybackController,
    onlineSearch: OnlineSearchApi,
    searchHistory: SearchHistoryStore,
    companionBridge: CompanionBridge,
    navController: NavHostController,
) {
    val group = rememberTvFocusGroup("search")
    val capsule = LocalCapsuleHost.current
    val scope = rememberCoroutineScope()
    val t9 = remember { PinyinT9() }

    var query by rememberSaveable { mutableStateOf("") }
    var pinyinMode by rememberSaveable { mutableStateOf(true) }
    var resultsJson by rememberSaveable { mutableStateOf<String?>(null) }
    val results = remember(resultsJson) { decodeTracks(resultsJson) }
    var total by rememberSaveable { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var currentTerm by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var isEnd by rememberSaveable { mutableStateOf(true) }
    var history by remember { mutableStateOf(searchHistory.load()) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    val externalQuery by companionBridge.pendingQuery.collectAsStateWithLifecycle()
    val resultsState = rememberLazyListState()
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var moreJob by remember { mutableStateOf<Job?>(null) }

    val caretAlpha by rememberInfiniteTransition(label = "caret").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "caret-alpha",
    )

    fun updateQuery(next: String) {
        query = next.take(SearchHistoryStore.MAX_TERM_LENGTH)
        if (resultsJson != null) resultsJson = null
    }

    fun typeDigit(digit: Char) {
        if (!pinyinMode) {
            updateQuery(query + digit)
            t9.reset()
            return
        }
        when (digit) {
            '0' -> {
                updateQuery(query + " ")
                t9.reset()
            }
            '1' -> {
                updateQuery(query + "'")
                t9.reset()
            }
            else -> updateQuery(t9.type(digit, query, SystemClock.elapsedRealtime()))
        }
    }

    fun appendLetter(letter: Char) {
        t9.reset()
        updateQuery(query + letter.lowercaseChar())
    }

    fun backspace() {
        query = query.dropLast(1)
        t9.reset()
        if (resultsJson != null) resultsJson = null
    }

    fun toggleInputMode() {
        pinyinMode = !pinyinMode
        t9.reset()
        suggestions = emptyList()
        capsule.show(if (pinyinMode) "输入方式：拼音九宫格" else "输入方式：数字")
    }

    fun runSearch() {
        val term = query.trim()
        if (term.isEmpty()) {
            capsule.show("先输入关键词：拼音九宫格或数字键")
            return
        }
        t9.reset()
        history = searchHistory.add(term)
        currentTerm = term
        page = 1
        isEnd = true
        loadingMore = false
        suggestions = emptyList()
        moreJob?.cancel()
        searchJob?.cancel()
        resultsJson = null
        total = 0
        loading = true
        searchJob = scope.launch {
            try {
                val searchResult = onlineSearch.search(term, 1, PAGE_SIZE)
                if (!isActive) return@launch
                val tracks = searchResult.list.map { it.toDemoTrack() }
                    .distinctBy { trackKey(it) }
                resultsJson = tracks.encodeTracks()
                total = searchResult.total
                isEnd = searchResult.isEnd || tracks.isEmpty()
                loading = false
                if (tracks.isEmpty()) {
                    capsule.show("没有找到「$term」")
                } else {
                    delay(60)
                    resultsState.scrollToItem(0)
                    group.focus("results:0")
                    capsule.show("QQ 音乐 · 找到 ${searchResult.total} 首")
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                if (!isActive) return@launch
                loading = false
                capsule.show("搜索失败，请检查网络后重试")
            }
        }
    }

    fun loadMore() {
        if (loading || loadingMore || isEnd || currentTerm.isBlank()) return
        val term = currentTerm
        val previousSize = results.orEmpty().size
        val nextPage = page + 1
        loadingMore = true
        moreJob?.cancel()
        moreJob = scope.launch {
            try {
                val searchResult = onlineSearch.search(term, nextPage, PAGE_SIZE)
                if (!isActive || term != currentTerm) return@launch
                val additions = searchResult.list.map { it.toDemoTrack() }
                val merged = (results.orEmpty() + additions).distinctBy { trackKey(it) }
                resultsJson = merged.encodeTracks()
                total = maxOf(total, searchResult.total)
                page = nextPage
                isEnd = searchResult.isEnd || additions.isEmpty() || merged.size == previousSize
                loadingMore = false
                delay(60)
                if (merged.size > previousSize) {
                    resultsState.scrollToItem(previousSize)
                    delay(40)
                    group.focus("results:$previousSize")
                    capsule.show(if (isEnd) "已加载全部 ${merged.size} 首" else "已加载 ${merged.size} 首")
                } else {
                    group.focus("results:${merged.lastIndex.coerceAtLeast(0)}")
                    capsule.show(if (isEnd) "已加载全部 ${merged.size} 首" else "没有更多新结果")
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                if (!isActive) return@launch
                loadingMore = false
                capsule.show("加载下一页失败，请重试")
            }
        }
    }

    LaunchedEffect(query, resultsJson) {
        val term = query.trim()
        if (resultsJson != null || term.length < 2 || !term.all { it in 'a'..'z' }) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(420)
        suggestions = onlineSearch.suggest(term)
    }

    val activeRoute = LocalActiveRoute.current
    val isActive = activeRoute == TvRoute.Search.route
    LaunchedEffect(isActive) {
        if (isActive) group.restore("t9:0")
    }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) {
        if (windowFocused && isActive) group.restore("t9:0")
    }
    LaunchedEffect(externalQuery, isActive) {
        val term = externalQuery ?: return@LaunchedEffect
        if (!isActive) return@LaunchedEffect
        query = term
        resultsJson = null
        t9.reset()
        delay(80)
        runSearch()
        companionBridge.consumeQuery()
    }

    val playTrack: (DemoTrack) -> Unit = { track ->
        playback.play(track, results.orEmpty())
        capsule.show("正在解析音源…")
        navController.navigate(TvRoute.Player.route) { launchSingleTop = true }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    false
                } else {
                    val digit = digitForKey(event.key)
                    val unicode = event.nativeKeyEvent.unicodeChar
                    when {
                        event.key == Key.Menu -> {
                            if (results == null) {
                                toggleInputMode()
                                true
                            } else {
                                false
                            }
                        }
                        event.key == Key.Backspace -> {
                            backspace()
                            true
                        }
                        event.key == Key.Spacebar -> {
                            updateQuery(query + " ")
                            t9.reset()
                            true
                        }
                        digit != null -> {
                            if (event.nativeKeyEvent.repeatCount == 0) typeDigit(digit)
                            true
                        }
                        unicode.toChar().isLetter() -> {
                            if (event.nativeKeyEvent.repeatCount == 0) appendLetter(unicode.toChar())
                            true
                        }
                        else -> false
                    }
                }
            }
    ) {
        TvBackdrop()
        TvSafeArea {
            Column(Modifier.fillMaxSize()) {
                ScreenHeader(
                    title = "搜索",
                    subtitle = if (pinyinMode) {
                        "拼音九宫格 · 全拼或首字母 · QQ 音乐在线搜索"
                    } else {
                        "数字九宫格 · 数字键直接输入 · QQ 音乐在线搜索"
                    },
                    group = group,
                    onBack = { navController.popBackStack() },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Keycap("MENU")
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when {
                                    results != null -> "歌曲操作"
                                    pinyinMode -> "拼音输入"
                                    else -> "数字输入"
                                },
                                style = TvType.caption,
                                color = TvColors.TextTertiary,
                            )
                        }
                    },
                )
                Spacer(Modifier.height(18.dp))
                Row(Modifier.weight(1f)) {
                    Column(
                        Modifier.width(460.dp).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        QueryPanel(
                            query = query,
                            caretAlpha = caretAlpha,
                            modeLabel = if (pinyinMode) "拼音" else "数字",
                        )
                        if (suggestions.isNotEmpty()) {
                            SuggestionWords(
                                group = group,
                                suggestions = suggestions.take(4),
                                onPick = {
                                    query = it
                                    resultsJson = null
                                    runSearch()
                                },
                            )
                        } else {
                            HotWords(
                                group = group,
                                onPick = {
                                    query = it
                                    resultsJson = null
                                    runSearch()
                                },
                            )
                        }
                        HistoryWords(
                            group = group,
                            history = history,
                            onPick = {
                                query = it
                                resultsJson = null
                                runSearch()
                            },
                            onClear = {
                                searchHistory.clear()
                                history = emptyList()
                                capsule.show("已清除搜索历史")
                            },
                        )
                        Text(
                            "输入后按搜索：QQ 音乐提供元数据，本机 LX 音源脚本解析播放地址。",
                            style = TvType.caption,
                            color = TvColors.TextTertiary,
                        )
                    }
                    Spacer(Modifier.width(24.dp))
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        when {
                            loading -> LoadingResults()
                            results != null -> ResultsPanel(
                                tracks = results,
                                total = total,
                                page = page,
                                isEnd = isEnd,
                                loadingMore = loadingMore,
                                listState = resultsState,
                                group = group,
                                onBackToKeyboard = {
                                    resultsJson = null
                                    group.focus("t9:0")
                                },
                                onLoadMore = ::loadMore,
                                onPlay = playTrack,
                                onPlayNext = { track ->
                                    capsule.show(
                                        if (playback.playNext(track)) "已设为下一首" else "这首歌正在播放"
                                    )
                                },
                                onAddToQueue = { track ->
                                    capsule.show(
                                        if (playback.addToQueue(track)) "已加入队列" else "队列里已经有这首歌"
                                    )
                                },
                            )
                            else -> T9Panel(
                                group = group,
                                pinyinMode = pinyinMode,
                                onDigit = { digit -> typeDigit(digit.first()) },
                                onBackspace = ::backspace,
                                onClear = {
                                    query = ""
                                    resultsJson = null
                                    t9.reset()
                                },
                                onSearch = ::runSearch,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueryPanel(query: String, caretAlpha: Float, modeLabel: String) {
    val accent = LocalTvAccent.current
    TvSurface(
        modifier = Modifier.fillMaxWidth().height(108.dp),
        shape = RoundedCornerShape(20.dp),
        baseColor = TvColors.Surface.copy(alpha = 0.78f),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("搜索词 · $modeLabel", style = TvType.caption, color = TvColors.TextTertiary)
                Spacer(Modifier.weight(1f))
                Text("最多 24 字符", style = TvType.caption, color = TvColors.TextTertiary)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = query.ifEmpty { "输入歌名 / 歌手" },
                    style = TvType.display,
                    color = if (query.isEmpty()) TvColors.TextTertiary else TvColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "|",
                    style = TvType.display,
                    color = accent.copy(alpha = caretAlpha),
                )
            }
        }
    }
}

@Composable
private fun HotWords(group: TvFocusGroup, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("热搜", style = TvType.title, color = TvColors.TextPrimary)
            Spacer(Modifier.width(8.dp))
            Text("热门搜索", style = TvType.caption, color = TvColors.TextTertiary)
        }
        DemoCatalog.hotWords.chunked(4).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEachIndexed { columnIndex, word ->
                    val index = rowIndex * 4 + columnIndex
                    SearchChip(
                        text = word,
                        group = group,
                        focusKey = "hot:$index",
                        onClick = { onPick(word) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionWords(
    group: TvFocusGroup,
    suggestions: List<String>,
    onPick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("猜你想搜", style = TvType.title, color = TvColors.TextPrimary)
            Spacer(Modifier.width(8.dp))
            Text("QQ 音乐联想", style = TvType.caption, color = TvColors.TextTertiary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEachIndexed { index, word ->
                SearchChip(
                    text = word,
                    group = group,
                    focusKey = "suggest:$index",
                    onClick = { onPick(word) },
                )
            }
        }
    }
}

@Composable
private fun HistoryWords(
    group: TvFocusGroup,
    history: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("最近搜索", style = TvType.title, color = TvColors.TextPrimary)
            Spacer(Modifier.width(8.dp))
            if (history.isNotEmpty()) {
                Text("${history.size} 条", style = TvType.caption, color = TvColors.TextTertiary)
            }
            Spacer(Modifier.weight(1f))
            ActionChip(
                label = "清除",
                group = group,
                focusKey = "history:clear",
                onClick = onClear,
            )
        }
        if (history.isEmpty()) {
            Text("暂无搜索记录", style = TvType.caption, color = TvColors.TextTertiary)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                history.take(4).forEachIndexed { index, word ->
                    SearchChip(
                        text = word,
                        group = group,
                        focusKey = "history:$index",
                        onClick = { onPick(word) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchChip(
    text: String,
    group: TvFocusGroup,
    focusKey: String,
    onClick: () -> Unit,
) {
    TvSurface(
        modifier = Modifier.width(100.dp).height(34.dp),
        shape = RoundedCornerShape(50),
        baseColor = TvColors.SurfaceElevated,
        focusedScale = 1.08f,
        glowSize = 10.dp,
        group = group,
        focusKey = focusKey,
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text,
                style = TvType.caption,
                color = TvColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private data class T9Key(val digit: String, val letters: String, val special: Boolean = false)

private val t9Keys = listOf(
    T9Key("1", ".,?!"),
    T9Key("2", "ABC"),
    T9Key("3", "DEF"),
    T9Key("4", "GHI"),
    T9Key("5", "JKL"),
    T9Key("6", "MNO"),
    T9Key("7", "PQRS"),
    T9Key("8", "TUV"),
    T9Key("9", "WXYZ"),
    T9Key("清空", "", special = true),
    T9Key("0", "空格"),
    T9Key("⌫", "删除", special = true),
)

private val pinyinKeys = listOf(
    T9Key("1", "分隔"),
    T9Key("2", "ABC"),
    T9Key("3", "DEF"),
    T9Key("4", "GHI"),
    T9Key("5", "JKL"),
    T9Key("6", "MNO"),
    T9Key("7", "PQRS"),
    T9Key("8", "TUV"),
    T9Key("9", "WXYZ"),
    T9Key("清空", "", special = true),
    T9Key("0", "空格"),
    T9Key("⌫", "删除", special = true),
)

@Composable
private fun T9Panel(
    group: TvFocusGroup,
    pinyinMode: Boolean,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
) {
    val keys = if (pinyinMode) pinyinKeys else t9Keys
    TvSurface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(22.dp),
        baseColor = TvColors.Surface.copy(alpha = 0.7f),
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (pinyinMode) "拼音九宫格" else "数字九宫格",
                    style = TvType.label,
                    color = TvColors.TextPrimary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (pinyinMode) "连按同键换字母 · MENU 切换" else "方向键移动 · OK 输入",
                    style = TvType.caption,
                    color = TvColors.TextTertiary,
                )
            }
            Spacer(Modifier.height(10.dp))
            keys.chunked(3).forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEachIndexed { columnIndex, key ->
                        val index = rowIndex * 3 + columnIndex
                        T9Button(
                            key = key,
                            group = group,
                            focusKey = "t9:$index",
                            onClick = {
                                when (key.digit) {
                                    "清空" -> onClear()
                                    "⌫" -> onBackspace()
                                    else -> onDigit(key.digit)
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.weight(1f))
            TvSurface(
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(50),
                baseColor = LocalTvAccent.current.copy(alpha = 0.18f),
                focusedScale = 1.02f,
                glowSize = 12.dp,
                group = group,
                focusKey = "t9:search",
                onClick = onSearch,
            ) {
                Row(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TvIcon(Icons.Rounded.Search, Modifier.size(20.dp), tint = LocalTvAccent.current)
                    Spacer(Modifier.width(8.dp))
                    Text("搜索", style = TvType.label, color = LocalTvAccent.current)
                }
            }
        }
    }
}

@Composable
private fun T9Button(
    key: T9Key,
    group: TvFocusGroup,
    focusKey: String,
    onClick: () -> Unit,
) {
    TvSurface(
        modifier = Modifier.width(108.dp).height(58.dp),
        shape = RoundedCornerShape(14.dp),
        baseColor = TvColors.SurfaceElevated,
        focusedScale = 1.06f,
        glowSize = 10.dp,
        group = group,
        focusKey = focusKey,
        onClick = onClick,
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                key.digit,
                style = if (key.special) TvType.label else TvType.title,
                color = if (key.special) TvColors.TextSecondary else TvColors.TextPrimary,
            )
            if (key.letters.isNotEmpty()) {
                Text(
                    key.letters,
                    style = TvType.caption.copy(fontSize = 9.sp, letterSpacing = 1.sp),
                    color = TvColors.TextTertiary,
                )
            }
        }
    }
}

@Composable
private fun ResultsPanel(
    tracks: List<DemoTrack>,
    total: Int,
    page: Int,
    isEnd: Boolean,
    loadingMore: Boolean,
    listState: LazyListState,
    group: TvFocusGroup,
    onBackToKeyboard: () -> Unit,
    onLoadMore: () -> Unit,
    onPlay: (DemoTrack) -> Unit,
    onPlayNext: (DemoTrack) -> Unit,
    onAddToQueue: (DemoTrack) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var actionFor by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(actionFor) {
        if (actionFor != null) group.focusWhenReady("results:action:next", timeoutMs = 1_000L)
    }
    fun focusRow(index: Int) {
        scope.launch {
            listState.animateScrollToItem(index.coerceAtLeast(0))
            group.focusWhenReady("results:$index", timeoutMs = 1_500L)
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("搜索结果", style = TvType.title, color = TvColors.TextPrimary)
            Spacer(Modifier.width(8.dp))
            Text(
                if (total > tracks.size) "已加载 ${tracks.size} / $total 首 · 第 $page 页"
                else "${tracks.size} 首 · QQ 音乐",
                style = TvType.caption,
                color = TvColors.TextTertiary,
            )
            Spacer(Modifier.weight(1f))
            ActionChip(
                label = "继续输入",
                group = group,
                focusKey = "results:back",
                onClick = onBackToKeyboard,
            )
        }
        Spacer(Modifier.height(10.dp))
        val actionIndex = actionFor
        val actionTrack = actionIndex?.let { tracks.getOrNull(it) }
        if (actionIndex != null && actionTrack != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionChip(
                    label = "下一首播放",
                    group = group,
                    focusKey = "results:action:next",
                    onClick = {
                        actionFor = null
                        onPlayNext(actionTrack)
                        focusRow(actionIndex)
                    },
                )
                ActionChip(
                    label = "加入队列",
                    group = group,
                    focusKey = "results:action:queue",
                    onClick = {
                        actionFor = null
                        onAddToQueue(actionTrack)
                        focusRow(actionIndex)
                    },
                )
                ActionChip(
                    label = "取消",
                    group = group,
                    focusKey = "results:action:cancel",
                    onClick = {
                        actionFor = null
                        focusRow(actionIndex)
                    },
                )
            }
            Spacer(Modifier.height(10.dp))
        }
        if (tracks.isEmpty()) {
            Text(
                "没有找到歌曲，换个关键词试试",
                style = TvType.body,
                color = TvColors.TextSecondary,
                modifier = Modifier.padding(top = 24.dp),
            )
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(tracks) { index, track ->
                    ResultRow(
                        track = track,
                        group = group,
                        focusKey = "results:$index",
                        onClick = { onPlay(track) },
                        onKeyEvent = { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Menu) {
                                actionFor = index
                                true
                            } else {
                                false
                            }
                        },
                    )
                }
                item(key = "results-more") {
                    if (!isEnd) {
                        TvSurface(
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            baseColor = TvColors.SurfaceElevated.copy(alpha = 0.7f),
                            focusedScale = 1.02f,
                            glowSize = 10.dp,
                            group = group,
                            focusKey = "results:more",
                            onClick = onLoadMore,
                        ) {
                            Row(
                                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (loadingMore) "正在加载第 ${page + 1} 页…" else "加载更多",
                                    style = TvType.label,
                                    color = LocalTvAccent.current,
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    if (total > tracks.size) "还有 ${total - tracks.size} 首" else "继续加载",
                                    style = TvType.caption,
                                    color = TvColors.TextTertiary,
                                )
                            }
                        }
                    } else {
                        Text(
                            "已加载全部 ${tracks.size} 首",
                            style = TvType.caption,
                            color = TvColors.TextTertiary,
                            modifier = Modifier.padding(vertical = 14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(
    track: DemoTrack,
    group: TvFocusGroup,
    focusKey: String,
    onClick: () -> Unit,
    onKeyEvent: ((KeyEvent) -> Boolean)? = null,
) {
    TvSurface(
        modifier = Modifier.fillMaxWidth().height(68.dp),
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
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
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
                    "${track.artist} · ${track.album}",
                    style = TvType.caption,
                    color = TvColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                formatDuration(track.durationMs),
                style = TvType.caption,
                color = TvColors.TextTertiary,
            )
            Spacer(Modifier.width(10.dp))
            TvIcon(Icons.Rounded.PlayArrow, Modifier.size(20.dp), tint = LocalTvAccent.current)
        }
    }
}

@Composable
private fun LoadingResults() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("搜索中", style = TvType.title, color = TvColors.TextPrimary)
            Spacer(Modifier.width(8.dp))
            Text("正在连接 QQ 音乐…", style = TvType.caption, color = TvColors.TextTertiary)
        }
        repeat(4) {
            SkeletonBox(Modifier.fillMaxWidth().height(68.dp), RoundedCornerShape(14.dp))
        }
    }
}

private fun digitForKey(key: Key): Char? = when (key) {
    Key.Zero, Key.NumPad0 -> '0'
    Key.One, Key.NumPad1 -> '1'
    Key.Two, Key.NumPad2 -> '2'
    Key.Three, Key.NumPad3 -> '3'
    Key.Four, Key.NumPad4 -> '4'
    Key.Five, Key.NumPad5 -> '5'
    Key.Six, Key.NumPad6 -> '6'
    Key.Seven, Key.NumPad7 -> '7'
    Key.Eight, Key.NumPad8 -> '8'
    Key.Nine, Key.NumPad9 -> '9'
    else -> null
}

private fun trackKey(track: DemoTrack): String = "${track.onlineSource}:${track.onlineSongId}"

private const val PAGE_SIZE = 30
