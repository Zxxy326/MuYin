package tv.music.player.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import tv.music.player.core.theme.TvDimens

@Composable
fun TvSafeArea(
    modifier: Modifier = Modifier,
    fullBleed: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        (if (fullBleed) {
            modifier.fillMaxSize()
        } else {
            modifier.fillMaxSize().padding(
                horizontal = TvDimens.SafeHorizontal,
                vertical = TvDimens.SafeVertical,
            )
        }),
        content = content,
    )
}
