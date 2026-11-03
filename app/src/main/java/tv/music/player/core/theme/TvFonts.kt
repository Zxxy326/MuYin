@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package tv.music.player.core.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Typography
import tv.music.player.R

private fun fangYuan(weight: FontWeight, axisWeight: Int) = Font(
    resId = R.font.alimama_fangyuan,
    weight = weight,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(axisWeight),
        FontVariation.Setting("BEVL", 80f),
    ),
)

object TvFonts {
    /** 阿里妈妈方圆体（可变字体）：wght 200–700 + 圆角轴，按权重取实例。 */
    val family = FontFamily(
        fangYuan(FontWeight.Normal, 400),
        fangYuan(FontWeight.Medium, 500),
        fangYuan(FontWeight.SemiBold, 600),
        fangYuan(FontWeight.Bold, 700),
    )

    val typography: Typography = Typography().run {
        copy(
            displayLarge = displayLarge.copy(fontFamily = family),
            displayMedium = displayMedium.copy(fontFamily = family),
            displaySmall = displaySmall.copy(fontFamily = family),
            headlineLarge = headlineLarge.copy(fontFamily = family),
            headlineMedium = headlineMedium.copy(fontFamily = family),
            headlineSmall = headlineSmall.copy(fontFamily = family),
            titleLarge = titleLarge.copy(fontFamily = family),
            titleMedium = titleMedium.copy(fontFamily = family),
            titleSmall = titleSmall.copy(fontFamily = family),
            bodyLarge = bodyLarge.copy(fontFamily = family),
            bodyMedium = bodyMedium.copy(fontFamily = family),
            bodySmall = bodySmall.copy(fontFamily = family),
            labelLarge = labelLarge.copy(fontFamily = family),
            labelMedium = labelMedium.copy(fontFamily = family),
            labelSmall = labelSmall.copy(fontFamily = family),
        )
    }
}
