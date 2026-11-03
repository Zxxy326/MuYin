package tv.music.player.core.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object TvType {
    private val family = TvFonts.family

    val hero = TextStyle(
        fontFamily = family,
        fontSize = 42.sp,
        lineHeight = 50.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.6).sp,
    )
    val display = TextStyle(
        fontFamily = family,
        fontSize = 34.sp,
        lineHeight = 42.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val headline = TextStyle(
        fontFamily = family,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val title = TextStyle(
        fontFamily = family,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val body = TextStyle(
        fontFamily = family,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Normal,
    )
    val label = TextStyle(
        fontFamily = family,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
    )
    val caption = TextStyle(
        fontFamily = family,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    )
    val keycap = TextStyle(
        fontFamily = family,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp,
    )
    val tracking = TextStyle(
        fontFamily = family,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 3.sp,
    )
}
