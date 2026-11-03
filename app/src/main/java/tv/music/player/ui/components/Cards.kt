package tv.music.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import tv.music.player.core.focus.TvFocusGroup
import tv.music.player.core.focus.TvSurface
import tv.music.player.core.playback.DemoTrack
import tv.music.player.core.theme.LocalTvAccent
import tv.music.player.core.theme.TvColors
import tv.music.player.core.theme.TvDimens
import tv.music.player.core.theme.TvType

@Composable
fun PosterCard(
    track: DemoTrack,
    group: TvFocusGroup,
    focusKey: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = TvDimens.PosterWidth,
) {
    TvSurface(
        modifier = modifier.width(width),
        shape = RoundedCornerShape(16.dp),
        baseColor = TvColors.Surface.copy(alpha = 0.55f),
        group = group,
        focusKey = focusKey,
        onClick = onClick,
    ) {
        Column(Modifier.padding(8.dp)) {
            TrackArtwork(
                track = track,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(width - 16.dp),
                corner = 12.dp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                track.title,
                style = TvType.label,
                color = TvColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist,
                style = TvType.caption,
                color = TvColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun WideCard(
    track: DemoTrack,
    group: TvFocusGroup,
    focusKey: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    showPlayIcon: Boolean = true,
) {
    val accent = LocalTvAccent.current
    TvSurface(
        modifier = modifier.width(TvDimens.WideCardWidth).height(96.dp),
        shape = RoundedCornerShape(16.dp),
        baseColor = if (selected) accent.copy(alpha = 0.13f) else TvColors.Surface.copy(alpha = 0.62f),
        group = group,
        focusKey = focusKey,
        onClick = onClick,
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackArtwork(track, Modifier.size(74.dp), corner = 10.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = TvType.label,
                    color = if (selected) accent else TvColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${track.artist} · ${track.album}",
                    style = TvType.caption,
                    color = TvColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showPlayIcon) {
                TvIcon(
                    icon = if (selected) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
                    modifier = Modifier.size(22.dp),
                    tint = if (selected) accent else TvColors.TextTertiary,
                )
            }
        }
    }
}

@Composable
fun ChartCard(
    name: String,
    subtitle: String,
    index: Int,
    group: TvFocusGroup,
    focusKey: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalTvAccent.current
    val colors = artworkColors(name.hashCode())
    TvSurface(
        modifier = modifier.width(216.dp).height(112.dp),
        shape = RoundedCornerShape(16.dp),
        baseColor = Color.Transparent,
        group = group,
        focusKey = focusKey,
        onClick = onClick,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(colors.first.copy(alpha = 0.85f), colors.second)
                    )
                )
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "TOP ${index + 1}",
                    style = TvType.keycap,
                    color = accent,
                )
                Column {
                    Text(name, style = TvType.title, color = TvColors.TextPrimary, maxLines = 1)
                    Text(subtitle, style = TvType.caption, color = TvColors.TextSecondary, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun QueueRow(
    track: DemoTrack,
    index: Int,
    isCurrent: Boolean,
    group: TvFocusGroup,
    focusKey: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onKeyEvent: ((KeyEvent) -> Boolean)? = null,
) {
    val accent = LocalTvAccent.current
    TvSurface(
        modifier = modifier.fillMaxWidth().height(62.dp),
        shape = RoundedCornerShape(12.dp),
        baseColor = if (isCurrent) accent.copy(alpha = 0.12f) else Color.Transparent,
        focusedScale = 1.03f,
        glowSize = 10.dp,
        group = group,
        focusKey = focusKey,
        onClick = onClick,
        onKeyEvent = onKeyEvent,
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (index < 9) "0${index + 1}" else "${index + 1}",
                style = TvType.caption,
                color = if (isCurrent) accent else TvColors.TextTertiary,
                modifier = Modifier.width(30.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = TvType.label,
                    color = if (isCurrent) accent else TvColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.artist,
                    style = TvType.caption,
                    color = TvColors.TextSecondary,
                    maxLines = 1,
                )
            }
            val sourceLabel = when {
                track.isLocal -> "本地"
                track.onlineSource == "tx" -> "QQ"
                track.onlineSource == "wy" -> "网易"
                track.onlineSource == "kg" -> "酷狗"
                track.onlineSource == "kw" -> "酷我"
                track.onlineSource == "mg" -> "咪咕"
                track.onlineSource.isNotBlank() -> track.onlineSource.uppercase()
                else -> ""
            }
            if (sourceLabel.isNotEmpty()) {
                Text(
                    sourceLabel,
                    style = TvType.caption.copy(fontSize = 10.sp),
                    color = TvColors.TextTertiary,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            if (isCurrent) {
                TvIcon(Icons.Rounded.GraphicEq, Modifier.size(18.dp), tint = accent)
            }
        }
    }
}

@Composable
fun QueueHeaderArtwork(track: DemoTrack, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxHeight()) {
        TrackArtwork(track, Modifier.fillMaxSize(), corner = 12.dp)
    }
}
