package tv.music.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import tv.music.player.core.focus.TvFocusGroup
import tv.music.player.core.focus.TvSurface
import tv.music.player.core.theme.LocalTvAccent
import tv.music.player.core.theme.TvColors
import tv.music.player.core.theme.TvType

@Composable
fun Keycap(
    text: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    val color = if (accent) LocalTvAccent.current else TvColors.TextSecondary
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (accent) LocalTvAccent.current.copy(alpha = 0.16f) else TvColors.SurfaceHigh)
            .border(
                1.dp,
                if (accent) LocalTvAccent.current.copy(alpha = 0.4f) else TvColors.Divider,
                RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, style = TvType.keycap, color = color)
    }
}

@Composable
fun QuickTile(
    icon: ImageVector,
    label: String,
    group: TvFocusGroup,
    focusKey: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvSurface(
        modifier = modifier.height(78.dp),
        shape = RoundedCornerShape(16.dp),
        baseColor = TvColors.Surface.copy(alpha = 0.72f),
        group = group,
        focusKey = focusKey,
        onClick = onClick,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TvIcon(icon, Modifier.size(22.dp), tint = LocalTvAccent.current)
            Spacer(Modifier.height(6.dp))
            Text(label, style = TvType.label, color = TvColors.TextPrimary, maxLines = 1)
        }
    }
}

@Composable
fun ActionChip(
    label: String,
    group: TvFocusGroup,
    focusKey: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
) {
    val accent = LocalTvAccent.current
    TvSurface(
        modifier = modifier.height(34.dp),
        shape = RoundedCornerShape(50),
        baseColor = if (primary) Color(0xFFF2F4F8) else Color(0xFF05060A).copy(alpha = 0.45f),
        borderColor = if (primary) TvColors.Divider else Color.White.copy(alpha = 0.1f),
        focusedScale = 1.05f,
        glowSize = 12.dp,
        focusedBorderColor = if (primary) accent else null,
        focusedGlowColor = if (primary) accent else null,
        group = group,
        focusKey = focusKey,
        onClick = onClick,
    ) {
        Row(
            Modifier
                .fillMaxHeight()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                TvIcon(
                    icon,
                    Modifier.size(15.dp),
                    tint = if (primary) Color(0xFF08090E) else Color.White.copy(alpha = 0.8f),
                )
                Spacer(Modifier.width(7.dp))
            }
            Text(
                text = label,
                style = TvType.caption,
                color = if (primary) Color(0xFF08090E) else Color.White.copy(alpha = 0.84f),
                maxLines = 1,
            )
        }
    }
}

@Composable
fun TagChip(
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    val color = if (accent) LocalTvAccent.current else TvColors.TextSecondary
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (accent) LocalTvAccent.current.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(label, style = TvType.caption, color = color, maxLines = 1)
    }
}
