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
    val Background = Color(0xFF05060A)
    val Surface = Color(0xFF0E1118)
    val SurfaceElevated = Color(0xFF151924)
    val SurfaceHigh = Color(0xFF1E2431)
    val TextPrimary = Color(0xFFF5F7FB)
    val TextSecondary = Color(0xFFA3ABBA)
    val TextTertiary = Color(0xFF6C7586)
    val Divider = Color(0x14FFFFFF)
    val Scrim = Color(0xD905070C)
    val Danger = Color(0xFFFF8A9A)
}

val LocalTvAccent = staticCompositionLocalOf { Color(0xFF9ED8CC) }
val LocalAnimationsEnabled = staticCompositionLocalOf { true }

fun accentFor(mode: AccentMode, coverAccent: Color): Color = when (mode) {
    AccentMode.COVER -> coverAccent
    AccentMode.TEAL -> Color(0xFF9ED8CC)
    AccentMode.AMBER -> Color(0xFFF2CDA2)
    AccentMode.VIOLET -> Color(0xFFC3B4F2)
    AccentMode.ROSE -> Color(0xFFF2B9C4)
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
            onPrimary = Color(0xFF08090E),
            primaryContainer = accent.copy(alpha = 0.22f),
            onPrimaryContainer = Color(0xFFF2FFFC),
            inversePrimary = accent,
            secondary = accent.copy(alpha = 0.75f),
            onSecondary = Color(0xFF08090E),
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
