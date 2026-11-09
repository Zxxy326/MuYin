package tv.music.player.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import tv.music.player.core.focus.TvFocusGroup
import tv.music.player.core.focus.TvSurface
import tv.music.player.core.theme.LocalTvAccent
import tv.music.player.core.theme.TvColors
import tv.music.player.core.theme.TvType

/**
 * 主界面顶部标签栏：首页 / 歌单 / 我的 + 常驻「正在播放」入口 + 设置。
 * 「正在播放」始终在顶部，退出播放页后无需下滑即可一键回到播放界面。
 */
@Composable
fun MainTabBar(
    active: String,
    group: TvFocusGroup,
    nowPlaying: String?,
    onHome: () -> Unit,
    onPlaylists: () -> Unit,
    onMine: () -> Unit,
    onPlayer: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalTvAccent.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        ActionChip(label = "首页", group = group, focusKey = "tab:home", primary = active == "home", onClick = onHome)
        Spacer(Modifier.width(10.dp))
        ActionChip(label = "歌单", group = group, focusKey = "tab:playlists", primary = active == "playlists", onClick = onPlaylists)
        Spacer(Modifier.width(10.dp))
        ActionChip(label = "我的", group = group, focusKey = "tab:mine", primary = active == "mine", onClick = onMine)
        Spacer(Modifier.weight(1f))
        TvSurface(
            modifier = Modifier.height(42.dp).widthIn(max = 340.dp),
            shape = RoundedCornerShape(50),
            baseColor = accent.copy(alpha = 0.16f),
            focusedScale = 1.05f,
            glowSize = 12.dp,
            group = group,
            focusKey = "tab:player",
            onClick = onPlayer,
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvIcon(Icons.Rounded.MusicNote, Modifier.size(18.dp), tint = accent)
                Spacer(Modifier.width(8.dp))
                Text(
                    nowPlaying?.let { "正在播放 · $it" } ?: "播放页",
                    style = TvType.label,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        TvSurface(
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
            baseColor = TvColors.SurfaceElevated,
            focusedScale = 1.1f,
            glowSize = 12.dp,
            group = group,
            focusKey = "tab:settings",
            onClick = onSettings,
        ) {
            TvIcon(Icons.Rounded.Settings, Modifier.size(20.dp), tint = TvColors.TextPrimary, contentDescription = "设置")
        }
    }
}