package tv.music.player.core.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay

class TvFocusGroup internal constructor(val id: String) {

    var lastFocusedKey: String? by mutableStateOf(null)
        private set

    private val requesters = mutableMapOf<String, FocusRequester>()

    var activeKey: String? by mutableStateOf(null)
        private set

    private var activated = false

    /**
     * 首次进入使用页面默认落点，之后才恢复上次焦点。
     * 否则系统在冷启动时自动给第一个可聚焦项的焦点会被误当成"用户记忆"。
     */
    fun takeRestoreTarget(defaultKey: String): String {
        val first = !activated
        activated = true
        return if (first) defaultKey else (lastFocusedKey ?: defaultKey)
    }

    fun register(key: String, requester: FocusRequester) {
        requesters[key] = requester
    }

    fun release(key: String, requester: FocusRequester) {
        if (requesters[key] === requester) requesters.remove(key)
    }

    fun onActiveFocus(key: String) {
        activeKey = key
    }

    fun onFocused(key: String) {
        lastFocusedKey = key
    }

    fun focus(key: String?): Boolean {
        if (key.isNullOrEmpty()) return false
        val requester = requesters[key] ?: return false
        val result = runCatching { requester.requestFocus() }
        return result.isSuccess && activeKey == key
    }

    /**
     * 冷启动时首帧布局可能晚于窗口焦点，requestFocus 会因节点尚未挂载而失败。
     * 只做短窗口重试，避免回来时把用户刚刚移动的焦点又抢走。
     */
    suspend fun focusWhenReady(key: String, timeoutMs: Long = 700L): Boolean {
        var waited = 0L
        while (waited <= timeoutMs) {
            withFrameNanos { }
            if (focus(key)) return true
            delay(60L)
            waited += 60L
        }
        return false
    }

    suspend fun restore(fallbackKey: String): Boolean {
        val target = takeRestoreTarget(fallbackKey)
        if (focusWhenReady(target)) return true
        return focusWhenReady(fallbackKey)
    }
}

object TvFocusRegistry {
    private val groups = mutableMapOf<String, TvFocusGroup>()

    fun group(id: String): TvFocusGroup = groups.getOrPut(id) { TvFocusGroup(id) }
}

@Composable
fun rememberTvFocusGroup(id: String): TvFocusGroup = remember(id) { TvFocusRegistry.group(id) }

fun Modifier.focusItem(
    group: TvFocusGroup,
    key: String,
    recordFocus: Boolean = true,
): Modifier = composed {
    val requester = remember(group, key) {
        FocusRequester().also { group.register(key, it) }
    }
    DisposableEffect(group, key, requester) {
        onDispose { group.release(key, requester) }
    }
    focusRequester(requester)
        .onFocusChanged { state ->
            if (state.isFocused) {
                group.onActiveFocus(key)
                if (recordFocus) group.onFocused(key)
            }
        }
}
