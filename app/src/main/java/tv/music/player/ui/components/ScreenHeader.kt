package tv.music.player.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import tv.music.player.core.focus.TvFocusGroup
import tv.music.player.core.focus.TvSurface
import tv.music.player.core.theme.TvColors
import tv.music.player.core.theme.TvFonts

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    group: TvFocusGroup,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backKey: String = "back",
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        TvSurface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            baseColor = Color.White.copy(alpha = 0.06f),
            focusedScale = 1.08f,
            glowSize = 10.dp,
            group = group,
            focusKey = backKey,
            recordFocus = false,
            onClick = onBack,
        ) {
            TvIcon(
                icon = Icons.Rounded.ArrowBack,
                modifier = Modifier.size(19.dp),
                tint = TvColors.TextPrimary,
                contentDescription = "返回",
            )
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                title,
                style = TextStyle(
                    fontFamily = TvFonts.family,
                    fontSize = 24.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                ),
                color = TvColors.TextPrimary,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = TextStyle(fontFamily = TvFonts.family, fontSize = 11.sp),
                    color = TvColors.TextTertiary,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (trailing != null) trailing()
    }
}
