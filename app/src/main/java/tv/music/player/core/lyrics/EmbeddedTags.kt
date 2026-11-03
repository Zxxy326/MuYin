package tv.music.player.core.lyrics

import android.content.Context
import android.net.Uri
import android.util.LruCache
import java.io.File
import java.io.InputStream

/**
 * 读取音频文件内嵌标签：ID3v2（MP3）与 FLAC 的歌词 / 封面。
 * 用于 MediaMetadataRetriever 读不到封面的 SAF/U 盘文件，以及内嵌歌词。
 */
object EmbeddedTags {

    data class Tags(val lyrics: String? = null, val picture: ByteArray? = null)

    private val cache = object : LruCache<String, Tags>(8) {}

    fun read(context: Context, uriString: String): Tags {
        if (uriString.isBlank()) return Tags()
        cache.get(uriString)?.let { return it }
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return Tags()
        val tags = runCatching {
            val stream = when (uri.scheme) {
                "file" -> File(uri.path.orEmpty()).inputStream()
                "content" -> context.contentResolver.openInputStream(uri)
                else -> null
            } ?: return Tags()
            stream.use(::parse)
        }.getOrDefault(Tags())
        cache.put(uriString, tags)
        return tags
    }

    private fun parse(stream: InputStream): Tags {
        val head = ByteArray(10)
        if (!readFully(stream, head)) return Tags()
        return when {
            head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() &&
                head[2] == '3'.code.toByte() -> parseId3(stream, head)
            head[0] == 'f'.code.toByte() && head[1] == 'L'.code.toByte() &&
                head[2] == 'a'.code.toByte() && head[3] == 'C'.code.toByte() -> parseFlac(stream)
            else -> Tags()
        }
    }

    private fun parseId3(stream: InputStream, header: ByteArray): Tags {
        val major = header[3].toInt() and 0xFF
        if (major !in 2..4) return Tags()
        val flags = header[5].toInt() and 0xFF
        if (flags and 0x80 != 0) return Tags()
        val size = syncSafe(header, 6)
        if (size <= 0 || size > MAX_TAG_BYTES) return Tags()
        val body = ByteArray(size)
        if (!readFully(stream, body)) return Tags()
        var lyrics: String? = null
        var picture: ByteArray? = null
        var pos = 0
        while (pos + 6 <= body.size) {
            if (body[pos].toInt() == 0) break
            val idLen = if (major == 2) 3 else 4
            val headerLen = if (major == 2) 6 else 10
            if (pos + headerLen > body.size) break
            val id = String(body, pos, idLen, Charsets.ISO_8859_1)
            if (!id.all { it.isLetterOrDigit() }) break
            val frameSize = when {
                major == 2 -> be24(body, pos + 3)
                major == 4 -> syncSafe(body, pos + 4)
                else -> be32(body, pos + 4)
            }
            if (frameSize <= 0 || pos + headerLen + frameSize > body.size) break
            val dataStart = pos + headerLen
            val dataEnd = dataStart + frameSize
            when {
                (id == "APIC" || id == "PIC") && picture == null ->
                    picture = id3Picture(body, dataStart, dataEnd, id == "PIC")
                (id == "USLT" || id == "ULT") && lyrics.isNullOrBlank() ->
                    lyrics = id3Lyrics(body, dataStart, dataEnd)
                id == "TXXX" && lyrics.isNullOrBlank() -> {
                    val userText = id3UserText(body, dataStart, dataEnd)
                    if (userText != null && userText.first.uppercase() in LYRIC_KEYS &&
                        userText.second.isNotBlank()
                    ) {
                        lyrics = userText.second
                    }
                }
            }
            pos = dataEnd
        }
        return Tags(lyrics?.trim()?.takeIf { it.isNotBlank() }, picture)
    }

    private fun id3Picture(data: ByteArray, start: Int, end: Int, v22: Boolean): ByteArray? {
        if (end - start < 5) return null
        val encoding = data[start].toInt() and 0xFF
        var pos = start + 1
        if (v22) {
            pos += 4
        } else {
            while (pos < end && data[pos].toInt() != 0) pos++
            pos += 2
        }
        pos = skipTerminator(data, pos, end, encoding)
        if (pos >= end) return null
        val length = end - pos
        if (length <= 0 || length > MAX_PICTURE_BYTES) return null
        return data.copyOfRange(pos, end)
    }

    private fun id3Lyrics(data: ByteArray, start: Int, end: Int): String? {
        if (end - start < 5) return null
        val encoding = data[start].toInt() and 0xFF
        var pos = start + 4
        pos = skipTerminator(data, pos, end, encoding)
        if (pos >= end) return null
        return decodeText(data, pos, end, encoding)
    }

    private fun id3UserText(data: ByteArray, start: Int, end: Int): Pair<String, String>? {
        if (end <= start) return null
        val encoding = data[start].toInt() and 0xFF
        val descEnd = terminatorIndex(data, start + 1, end, encoding)
        if (descEnd >= end) return null
        val description = decodeText(data, start + 1, descEnd, encoding).orEmpty()
        val valueStart = descEnd + if (encoding == 1 || encoding == 2) 2 else 1
        if (valueStart >= end) return null
        return description to decodeText(data, valueStart, end, encoding).orEmpty()
    }

    private fun parseFlac(stream: InputStream): Tags {
        var lyrics: String? = null
        var picture: ByteArray? = null
        var scanned = 0
        while (scanned < MAX_FLAC_SCAN) {
            val blockHeader = ByteArray(4)
            if (!readFully(stream, blockHeader)) break
            val last = blockHeader[0].toInt() and 0x80 != 0
            val type = blockHeader[0].toInt() and 0x7F
            val length = be24(blockHeader, 1)
            if (length <= 0 || length > MAX_BLOCK_BYTES) break
            if (type == 4 || type == 6) {
                val block = ByteArray(length)
                if (!readFully(stream, block)) break
                if (type == 4 && lyrics.isNullOrBlank()) lyrics = vorbisLyrics(block)
                if (type == 6 && picture == null) picture = flacPicture(block)
            } else if (!skipFully(stream, length.toLong())) {
                break
            }
            scanned += 4 + length
            if (last) break
        }
        return Tags(lyrics?.trim()?.takeIf { it.isNotBlank() }, picture)
    }

    private fun vorbisLyrics(block: ByteArray): String? {
        var pos = 0
        if (block.size < 8) return null
        val vendorLength = le32(block, pos)
        pos += 4
        if (vendorLength < 0 || pos + vendorLength + 4 > block.size) return null
        pos += vendorLength
        val count = le32(block, pos)
        pos += 4
        if (count < 0 || count > 4096) return null
        repeat(count) {
            if (pos + 4 > block.size) return null
            val length = le32(block, pos)
            pos += 4
            if (length < 0 || pos + length > block.size) return null
            val entry = String(block, pos, length, Charsets.UTF_8)
            pos += length
            val eq = entry.indexOf('=')
            if (eq <= 0) return@repeat
            val key = entry.substring(0, eq).uppercase()
            if (key in LYRIC_KEYS) {
                val value = entry.substring(eq + 1).trim()
                if (value.isNotBlank()) return value
            }
        }
        return null
    }

    private fun flacPicture(block: ByteArray): ByteArray? {
        var pos = 0
        if (block.size < 32) return null
        pos += 4
        val mimeLength = be32(block, pos)
        pos += 4
        if (mimeLength < 0 || pos + mimeLength + 4 > block.size) return null
        pos += mimeLength
        val descLength = be32(block, pos)
        pos += 4
        if (descLength < 0 || pos + descLength + 20 > block.size) return null
        pos += descLength
        pos += 16
        val dataLength = be32(block, pos)
        pos += 4
        if (dataLength <= 0 || dataLength > MAX_PICTURE_BYTES || pos + dataLength > block.size) return null
        return block.copyOfRange(pos, pos + dataLength)
    }

    private fun decodeText(data: ByteArray, start: Int, end: Int, encoding: Int): String? {
        if (end <= start) return null
        return when (encoding) {
            1, 2 -> {
                var from = start
                if (encoding == 1 && end - start >= 2) {
                    val b0 = data[start].toInt() and 0xFF
                    val b1 = data[start + 1].toInt() and 0xFF
                    if ((b0 == 0xFF && b1 == 0xFE) || (b0 == 0xFE && b1 == 0xFF)) from += 2
                }
                val charset = if (encoding == 2) Charsets.UTF_16BE else Charsets.UTF_16LE
                String(data, from, end - from, charset)
            }
            3 -> String(data, start, end - start, Charsets.UTF_8)
            else -> String(data, start, end - start, Charsets.ISO_8859_1)
        }
    }

    private fun skipTerminator(data: ByteArray, from: Int, end: Int, encoding: Int): Int {
        val index = terminatorIndex(data, from, end, encoding)
        if (index >= end) return end
        return index + if (encoding == 1 || encoding == 2) 2 else 1
    }

    private fun terminatorIndex(data: ByteArray, from: Int, end: Int, encoding: Int): Int {
        var pos = from
        if (encoding == 1 || encoding == 2) {
            if ((pos - from) % 2 == 1) pos += 1
            while (pos + 1 < end) {
                if (data[pos].toInt() == 0 && data[pos + 1].toInt() == 0) return pos
                pos += 2
            }
            return end
        }
        while (pos < end) {
            if (data[pos].toInt() == 0) return pos
            pos++
        }
        return end
    }

    private fun syncSafe(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return -1
        return ((bytes[offset].toInt() and 0x7F) shl 21) or
            ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
            ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
            (bytes[offset + 3].toInt() and 0x7F)
    }

    private fun be24(bytes: ByteArray, offset: Int): Int {
        if (offset + 3 > bytes.size) return -1
        return ((bytes[offset].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            (bytes[offset + 2].toInt() and 0xFF)
    }

    private fun be32(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return -1
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun le32(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return -1
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun readFully(stream: InputStream, buffer: ByteArray): Boolean {
        var read = 0
        while (read < buffer.size) {
            val count = stream.read(buffer, read, buffer.size - read)
            if (count < 0) return false
            read += count
        }
        return true
    }

    private fun skipFully(stream: InputStream, count: Long): Boolean {
        var remaining = count
        val buffer = ByteArray(8 * 1024)
        while (remaining > 0) {
            val read = stream.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) return false
            remaining -= read
        }
        return true
    }

    private const val MAX_TAG_BYTES = 16L * 1024L * 1024L
    private const val MAX_BLOCK_BYTES = 12 * 1024 * 1024
    private const val MAX_FLAC_SCAN = 64L * 1024L * 1024L
    private const val MAX_PICTURE_BYTES = 8 * 1024 * 1024
    private val LYRIC_KEYS = setOf(
        "LYRICS", "LYRIC", "USLT", "ULT", "UNSYNCEDLYRICS", "UNSYNCED LYRICS",
    )
}
