package tv.music.player.core.settings

enum class AccentMode(val label: String) {
    COVER("跟随封面"),
    TEAL("青绿"),
    AMBER("琥珀"),
    VIOLET("紫罗兰"),
    ROSE("玫瑰"),
}

enum class LyricSize(val label: String, val scale: Float) {
    SMALL("小 · 34sp", 0.8f),
    MEDIUM("中 · 48sp", 1.0f),
    LARGE("大 · 72sp", 1.35f),
}

enum class LyricStyle(
    val label: String,
    val hint: String,
    val supportsSplit: Boolean = false,
) {
    WAVE("动感涟漪", "封面取色，左右推出的同心光带"),
    SIMPLE("简约", "模糊封面，描边歌名与台阶歌词"),
    CLOCK("精简时钟", "模糊封面，大号时间背景板"),
}

enum class PlayerLayout(val label: String) {
    CENTERED("布局 A · 全屏歌词"),
    SPLIT("布局 B · 左封面右歌词"),
}

/** 正值表示歌词提前出现。 */
fun formatLyricOffset(offsetMs: Int): String {
    if (offsetMs == 0) return "0.0s"
    val abs = kotlin.math.abs(offsetMs)
    val seconds = "${abs / 1000}.${(abs % 1000) / 100}s"
    return if (offsetMs > 0) "提前 $seconds" else "延后 $seconds"
}

enum class AudioQuality(val label: String) {
    STANDARD("标准"),
    HIGH("高音质"),
    LOSSLESS("无损"),
}

val AudioQuality.lxQuality: String
    get() = when (this) {
        AudioQuality.STANDARD -> "128k"
        AudioQuality.HIGH -> "320k"
        AudioQuality.LOSSLESS -> "flac"
    }

data class AppSettings(
    val accentMode: AccentMode = AccentMode.COVER,
    val animationsEnabled: Boolean = true,
    val blurBackground: Boolean = true,
    val lyricSize: LyricSize = LyricSize.MEDIUM,
    val lyricStyle: LyricStyle = LyricStyle.WAVE,
    val showLyricTranslation: Boolean = true,
    val lyricOffsetMs: Int = 0,
    val playerLayout: PlayerLayout = PlayerLayout.CENTERED,
    val quality: AudioQuality = AudioQuality.HIGH,
    val gaplessPlayback: Boolean = true,
    val fadeInOut: Boolean = false,
    val audioFocus: Boolean = true,
    val companionEnabled: Boolean = false,
)
