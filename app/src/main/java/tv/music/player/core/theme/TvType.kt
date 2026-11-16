package tv.music.player.core.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object TvType {
    private val family = TvFonts.family

    val hero = TextStyle(
        fontFamily = family,
        fontSize = 46.sp,
        lineHeight = 52.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-1.0).sp,
    )
    val display = TextStyle(
        fontFamily = family,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.4).sp,
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
        fontSize = 17.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Normal,
    )
    val label = TextStyle(
        fontFamily = family,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
    )
    val caption = TextStyle(
        fontFamily = family,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.Normal,
    )
    val overline = TextStyle(
        fontFamily = family,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.6.sp,
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
