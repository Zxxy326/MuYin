package tv.music.player.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import tv.music.player.core.playback.DemoTrack
import tv.music.player.core.theme.LocalTvAccent
import tv.music.player.core.theme.TvFonts
import tv.music.player.core.theme.rememberTrackCoverArt

val StageTextShadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 1.5f), 10f)
val StageTitleShadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 2f), 12f)

/** 所有一级页面共用的海报背景：当前/最近封面全屏铺底 + 与首页一致的压暗层。 */
@Composable
fun StageBackdrop(
    track: DemoTrack?,
    modifier: Modifier = Modifier,
    dim: Float = 0f,
) {
    Box(modifier.fillMaxSize()) {
        if (track != null) {
            Crossfade(
                targetState = track,
                animationSpec = tween(600),
                label = "stage-backdrop",
            ) { item ->
                Image(
                    bitmap = rememberTrackCoverArt(item).image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            TvBackdrop()
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color(0xE605060A),
                    0.38f to Color(0xB805060A),
                    0.72f to Color(0x2605060A),
                    1f to Color(0x6605060A),
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0x8C05060A),
                    0.3f to Color(0x1405060A),
                    0.58f to Color(0x0D05060A),
                    0.8f to Color(0x8C05060A),
                    1f to Color(0xF205060A),
                )
            )
        )
        if (dim > 0f) {
            Box(Modifier.fillMaxSize().background(Color(0xFF05060A).copy(alpha = dim)))
        }
    }
}

/** 与首页 hero 同语言的页头：小标签 + 大标题 + 一句说明，右侧可放统计或主按钮。 */
@Composable
fun StageHeading(
    kicker: String,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(4.dp).clip(CircleShape)
                        .background(LocalTvAccent.current.copy(alpha = 0.9f))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    kicker,
                    style = TextStyle(
                        fontFamily = TvFonts.family,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.2.sp,
                        shadow = StageTextShadow,
                    ),
                    color = Color.White.copy(alpha = 0.58f),
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    title,
                    style = TextStyle(
                        fontFamily = TvFonts.family,
                        fontSize = 30.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.6).sp,
                        shadow = StageTitleShadow,
                    ),
                    color = Color.White,
                    maxLines = 1,
                )
                if (subtitle != null) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        subtitle,
                        style = TextStyle(
                            fontFamily = TvFonts.family,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            shadow = StageTextShadow,
                        ),
                        color = Color.White.copy(alpha = 0.52f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

/** 海报上的次级文字/提示色，保证在封面底图上可读。 */
val StageTextSecondary = Color.White.copy(alpha = 0.62f)
val StageTextTertiary = Color.White.copy(alpha = 0.4f)

/** 海报上统一的黑玻璃面板色，替换原来的白透卡片。 */
val StageGlass = Color(0xFF05060A).copy(alpha = 0.52f)
val StageGlassSoft = Color(0xFF05060A).copy(alpha = 0.36f)
