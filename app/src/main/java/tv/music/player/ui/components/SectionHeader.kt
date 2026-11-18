package tv.music.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import tv.music.player.core.theme.LocalTvAccent
import tv.music.player.core.theme.TvColors

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(3.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(LocalTvAccent.current)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = tv.music.player.core.theme.TvFonts.family,
                fontSize = 18.sp,
                lineHeight = 22.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
                shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 1.5f), 8f),
            ),
            color = TvColors.TextPrimary,
        )
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            Text(
                text = trailing,
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = tv.music.player.core.theme.TvFonts.family,
                    fontSize = 10.5.sp,
                    shadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 1f), 6f),
                ),
                color = Color.White.copy(alpha = 0.42f),
                modifier = Modifier.widthIn(max = 420.dp),
            )
        }
    }
}
