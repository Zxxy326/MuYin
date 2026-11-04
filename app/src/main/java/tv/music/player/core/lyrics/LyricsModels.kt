package tv.music.player.core.lyrics

data class LyricWord(
    val timeMs: Long,
    val durationMs: Long,
    val text: String,
)

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val translation: String? = null,
    val durationMs: Long = 0L,
    val words: List<LyricWord> = emptyList(),
)

data class Lyrics(
    val lines: List<LyricLine> = emptyList(),
    val instrumental: Boolean = false,
    val sourceLabel: String = "QQ 音乐",
) {
    fun indexAt(positionMs: Long, offsetMs: Long = 0L): Int {
        if (lines.isEmpty()) return -1
        val target = positionMs + offsetMs
        var low = 0
        var high = lines.lastIndex
        var result = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timeMs <= target) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }
}

/** 音源脚本自带的歌词（优先于 QQ/网易云接口）。 */
data class SourceLyrics(
    val lrc: String,
    val translation: String? = null,
    val romanization: String? = null,
    val label: String = "音源歌词",
)

sealed interface LyricsState {
    data object Loading : LyricsState
    data class Ready(val lyrics: Lyrics) : LyricsState
    data class Unavailable(val message: String) : LyricsState
    data class Failed(val message: String) : LyricsState
}
