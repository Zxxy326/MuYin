package tv.music.player.core.lyrics

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.DataFormatException

private val CDATA_HEX = Regex("""<!\[CDATA\[([0-9A-Fa-f]+)]]>""")
private val LYRIC_CONTENT = Regex("""LyricContent="([\s\S]*?)""" + "\"")
private val LINE_HEADER = Regex("""^\[(\d+),(\d+)]""")
private val WORD_TAG = Regex("""\((\d+),(\d+)(?:,\d+)?\)""")
private val OFFSET_TAG = Regex("""\[offset:([+-]?\d+)]""")

object QrcParser {

    fun parseXml(xml: String, lineLyrics: Lyrics? = null): Lyrics {
        val content = decodeEntities(
            LYRIC_CONTENT.find(xml)?.groupValues?.get(1) ?: return Lyrics(),
        )
        return parseContent(content, lineLyrics)
    }

    fun parseContent(content: String, lineLyrics: Lyrics? = null): Lyrics {
        val translations = lineLyrics?.lines.orEmpty()
        val fileOffset = OFFSET_TAG.find(content)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val lines = ArrayList<LyricLine>()

        content.lineSequence().forEach { raw ->
            val line = raw.trimEnd('\r')
            val header = LINE_HEADER.find(line) ?: return@forEach
            val start = header.groupValues[1].toLongOrNull() ?: return@forEach
            val duration = header.groupValues[2].toLongOrNull() ?: 0L
            val body = line.substring(header.range.last + 1)
            val rawWords = parseWords(body)
            if (rawWords.isEmpty()) return@forEach
            val relativeWords = rawWords.first().timeMs < start - 200L
            val baseWords = if (relativeWords) {
                rawWords.map { it.copy(timeMs = it.timeMs + start) }
            } else {
                rawWords
            }
            val text = baseWords.joinToString("") { it.text }.trim()
            if (text.isBlank()) return@forEach
            val lineDuration = if (duration > 0L) {
                duration
            } else {
                baseWords.lastOrNull()?.let { (it.timeMs + it.durationMs - start).coerceAtLeast(0L) } ?: 0L
            }
            val translation = translationAt(translations, start)
            lines += LyricLine(
                timeMs = (start + fileOffset).coerceAtLeast(0),
                text = text,
                translation = translation,
                durationMs = lineDuration,
                words = if (fileOffset == 0L) {
                    baseWords
                } else {
                    baseWords.map { it.copy(timeMs = it.timeMs + fileOffset) }
                },
            )
        }

        if (lines.isEmpty()) return Lyrics()
        lines.sortBy { it.timeMs }
        if (lines.all { line -> INSTRUMENTAL.any { line.text.contains(it) } }) {
            return Lyrics(instrumental = true)
        }
        return Lyrics(lines = lines, sourceLabel = "QQ QRC")
    }

    fun decryptAndInflate(cipher: ByteArray): String {
        val plain = QqDes.decryptQrc(cipher)
        val inflated = inflateZlib(plain)
        return decodeText(inflated)
    }

    fun extractCdataHex(xml: String): ByteArray? {
        val hex = CDATA_HEX.find(xml)?.groupValues?.get(1) ?: return null
        if (hex.length % 16 != 0) return null
        return ByteArray(hex.length / 2) { index ->
            hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun parseWords(body: String): List<LyricWord> {
        val tags = WORD_TAG.findAll(body).toList()
        if (tags.isEmpty()) return emptyList()
        val words = ArrayList<LyricWord>(tags.size)
        var cursor = 0
        tags.forEach { tag ->
            val text = body.substring(cursor, tag.range.first)
            val time = tag.groupValues[1].toLongOrNull() ?: return@forEach
            val duration = tag.groupValues[2].toLongOrNull() ?: 0L
            words += LyricWord(time, duration, text)
            cursor = tag.range.last + 1
        }
        return words
    }

    private fun translationAt(translations: List<LyricLine>, timeMs: Long): String? {
        if (translations.isEmpty()) return null
        var best: String? = null
        var bestDelta = 500L
        for (line in translations) {
            if (line.translation.isNullOrBlank()) continue
            val delta = kotlin.math.abs(line.timeMs - timeMs)
            if (delta <= bestDelta) {
                bestDelta = delta
                best = line.translation
            }
        }
        return best
    }

    private fun inflateZlib(data: ByteArray): ByteArray {
        val start = (0..minOf(8, data.lastIndex)).firstOrNull { index ->
            val first = data[index].toInt() and 0xFF
            val second = if (index + 1 < data.size) data[index + 1].toInt() and 0xFF else 0
            first == 0x78 && second in setOf(0x01, 0x5E, 0x9C, 0xDA)
        } ?: 0
        return try {
            QqDes.inflate(data.copyOfRange(start, data.size))
        } catch (e: DataFormatException) {
            throw IllegalStateException("QRC 解压失败", e)
        }
    }

    private fun decodeText(bytes: ByteArray): String {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            String(bytes, Charset.forName("GB18030"))
        }
    }

    private fun decodeEntities(value: String): String = value
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")

    private val INSTRUMENTAL = listOf("纯音乐，请欣赏", "纯音乐,请欣赏", "纯音乐", "此歌曲为没有填词的纯音乐", "暂无歌词")
}
