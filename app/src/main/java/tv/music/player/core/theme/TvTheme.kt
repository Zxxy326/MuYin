package tv.music.player.core.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import tv.music.player.core.settings.AccentMode

object TvColors {
    val Background = Color(0xFF0A0C10)
    val Surface = Color(0xFF141821)
    val SurfaceElevated = Color(0xFF1D2330)
    val SurfaceHigh = Color(0xFF252C3B)
    val TextPrimary = Color(0xFFF4F6FB)
    val TextSecondary = Color(0xFF9AA4B8)
    val TextTertiary = Color(0xFF6B7689)
    val Divider = Color(0x1FFFFFFF)
    val Scrim = Color(0xCC05070A)
    val Danger = Color(0xFFFF8A80)
}

val LocalTvAccent = staticCompositionLocalOf { Color(0xFF35D6C4) }
val LocalAnimationsEnabled = staticCompositionLocalOf { true }

fun accentFor(mode: AccentMode, coverAccent: Color): Color = when (mode) {
    AccentMode.COVER -> coverAccent
    AccentMode.TEAL -> Color(0xFF35D6C4)
    AccentMode.AMBER -> Color(0xFFFFB25C)
    AccentMode.VIOLET -> Color(0xFFB99CFF)
    AccentMode.ROSE -> Color(0xFFFF86A8)
}

@Composable
fun TvTheme(
    accent: Color,
    animationsEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    val scheme = remember(accent) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color(0xFF06110F),
            primaryContainer = accent.copy(alpha = 0.22f),
            onPrimaryContainer = Color(0xFFF2FFFC),
            inversePrimary = accent,
            secondary = accent.copy(alpha = 0.75f),
            onSecondary = Color(0xFF06110F),
            secondaryContainer = TvColors.SurfaceElevated,
            onSecondaryContainer = TvColors.TextPrimary,
            tertiary = Color(0xFFFFD9A8),
            onTertiary = Color(0xFF201203),
            tertiaryContainer = Color(0xFF3A2A17),
            onTertiaryContainer = Color(0xFFFFE7C7),
            background = TvColors.Background,
            onBackground = TvColors.TextPrimary,
            surface = TvColors.Surface,
            onSurface = TvColors.TextPrimary,
            surfaceVariant = TvColors.SurfaceElevated,
            onSurfaceVariant = TvColors.TextSecondary,
            surfaceTint = accent,
            inverseSurface = Color(0xFFE7EAF0),
            inverseOnSurface = Color(0xFF1A1D23),
            error = TvColors.Danger,
            onError = Color(0xFF3A0904),
            errorContainer = Color(0xFF5C1A14),
            onErrorContainer = Color(0xFFFFDAD5),
            border = Color(0x33FFFFFF),
            borderVariant = TvColors.Divider,
            scrim = TvColors.Scrim,
        )
    }
    MaterialTheme(colorScheme = scheme, typography = TvFonts.typography) {
        CompositionLocalProvider(
            LocalTvAccent provides accent,
            LocalAnimationsEnabled provides animationsEnabled,
        ) {
            content()
        }
    }
}
