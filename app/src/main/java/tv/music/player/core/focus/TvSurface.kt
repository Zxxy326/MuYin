package tv.music.player.core.focus

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tv.music.player.core.theme.LocalAnimationsEnabled
import tv.music.player.core.theme.LocalTvAccent
import tv.music.player.core.theme.TvColors

/**
 * TV 焦点容器的统一实现：
 * 焦点态 1.08× 缩放、3dp 主题色描边、16dp 同色外发光，失焦 200ms 回落（120ms 进入）。
 */
@Composable
fun TvSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    baseColor: Color = TvColors.Surface,
    focusedScale: Float = 1.08f,
    focusedBorderWidth: Dp = 3.dp,
    glowSize: Dp = 16.dp,
    focusedBorderColor: Color? = null,
    focusedGlowColor: Color? = null,
    showBorder: Boolean = true,
    enabled: Boolean = true,
    group: TvFocusGroup? = null,
    focusKey: String? = null,
    recordFocus: Boolean = true,
    onClick: (() -> Unit)? = null,
    onFocusedChange: ((Boolean) -> Unit)? = null,
    onKeyEvent: ((KeyEvent) -> Boolean)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val accent = LocalTvAccent.current
    val animations = LocalAnimationsEnabled.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (focused) focusedScale else 1f,
        animationSpec = tween(if (animations) 120 else 0, easing = FastOutSlowInEasing),
        label = "focus-scale",
    )
    val glow by animateDpAsState(
        targetValue = if (focused) glowSize else 0.dp,
        animationSpec = tween(if (animations) 160 else 0),
        label = "focus-glow",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (focused) focusedBorderWidth else 1.dp,
        animationSpec = tween(if (animations) 160 else 0),
        label = "focus-border",
    )
    val ringColor = focusedBorderColor ?: accent
    val glowColor = focusedGlowColor ?: accent
    val glowPx = with(LocalDensity.current) { glow.toPx() }
    val borderColor by animateColorAsState(
        targetValue = if (focused) ringColor else TvColors.Divider,
        animationSpec = tween(if (animations) 160 else 0),
        label = "focus-color",
    )

    if (onFocusedChange != null) {
        LaunchedEffect(focused) { onFocusedChange(focused) }
    }

    val focusModifier =
        if (group != null && !focusKey.isNullOrEmpty()) {
            Modifier.focusItem(group, focusKey, recordFocus)
        } else {
            Modifier
        }
    val keyModifier = if (onKeyEvent != null) Modifier.onKeyEvent { onKeyEvent(it) } else Modifier
    val interactionModifier = when {
        onClick != null -> Modifier.clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
        group != null && !focusKey.isNullOrEmpty() -> Modifier.focusable(
            interactionSource = interaction,
            enabled = enabled,
        )
        else -> Modifier
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                if (glowPx <= 0f) return@drawBehind
                val outline = shape.createOutline(size, layoutDirection, this)
                val steps = 6
                val stroke = glowPx / 5f
                for (i in 1..steps) {
                    val progress = (i - 0.5f) / steps
                    val color = glowColor.copy(alpha = 0.11f * (1f - progress) + 0.012f)
                    val distance = stroke * (i - 0.5f)
                    val topLeft = Offset(-distance, -distance)
                    val glowSize = Size(size.width + distance * 2, size.height + distance * 2)
                    when (outline) {
                        is Outline.Rounded -> drawRoundRect(
                            color = color,
                            topLeft = topLeft,
                            size = glowSize,
                            cornerRadius = CornerRadius(
                                outline.roundRect.topLeftCornerRadius.x + distance,
                                outline.roundRect.topLeftCornerRadius.y + distance,
                            ),
                            style = Stroke(stroke),
                        )
                        is Outline.Rectangle -> drawRect(
                            color = color,
                            topLeft = topLeft,
                            size = glowSize,
                            style = Stroke(stroke),
                        )
                        is Outline.Generic -> Unit
                    }
                }
            }
            .clip(shape)
            .background(baseColor)
            .then(if (showBorder) Modifier.border(borderWidth, borderColor, shape) else Modifier)
            .then(focusModifier)
            .then(keyModifier)
            .then(interactionModifier),
        content = content,
    )
}
