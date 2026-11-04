package tv.music.player.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tv.music.player.core.focus.TvFocusGroup

@Composable
fun <T> TvRail(
    items: List<T>,
    keyPrefix: String,
    group: TvFocusGroup,
    initialIndex: Int,
    modifier: Modifier = Modifier,
    state: LazyListState? = null,
    content: @Composable (index: Int, item: T, focusKey: String) -> Unit,
) {
    val fallbackState = rememberLazyListState(
        initialFirstVisibleItemIndex = (initialIndex - 1).coerceAtLeast(0)
    )
    val listState = state ?: fallbackState
    LazyRow(
        state = listState,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 26.dp, horizontal = 26.dp),
    ) {
        itemsIndexed(items) { index, item ->
            content(index, item, "$keyPrefix:$index")
        }
    }
}
