package tv.music.player.core.lyrics

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.music.player.core.playback.DemoTrack
import java.io.File

/**
 * 本地歌曲的同名歌词：
 * - `file://` 直接读同目录 `.lrc`；
 * - SAF 文件夹导入的 `content://` 按文档 ID 找同级 `.lrc`，权限持久；
 * - MediaStore 扫描的歌曲尝试 `_data` 相邻文件（Android 10 以下可读，11+ 受分区存储限制）。
 */
object LocalLyricsLoader {

    suspend fun load(context: Context, track: DemoTrack): Lyrics? = withContext(Dispatchers.IO) {
        if (track.localUri.isBlank()) return@withContext null
        val uri = runCatching { Uri.parse(track.localUri) }.getOrNull() ?: return@withContext null
        val raw = when {
            uri.scheme == "file" -> readSidecar(File(uri.path.orEmpty()))
            uri.authority == MediaStore.AUTHORITY -> readMediaStoreSibling(context, uri)
            uri.scheme == "content" -> readSafSibling(context, uri)
            else -> null
        } ?: return@withContext null
        val lyrics = LrcParser.parse(raw)
        if (lyrics.lines.isEmpty()) null else lyrics.copy(sourceLabel = "本地歌词")
    }

    private fun readMediaStoreSibling(context: Context, uri: Uri): String? {
        val path = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.Audio.Media.DATA),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        return if (path.isNullOrBlank()) null else readSidecar(File(path))
    }

    private fun readSafSibling(context: Context, uri: Uri): String? {
        val authority = uri.authority ?: return null
        val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
        val slash = docId.lastIndexOf('/')
        if (slash <= 0) return null
        val parentId = docId.substring(0, slash)
        val name = docId.substring(slash + 1)
        val base = name.substringBeforeLast('.', name)
        if (base.isBlank()) return null
        val siblingId = "$parentId/$base.lrc"
        val sibling = runCatching { DocumentsContract.buildDocumentUriUsingTree(uri, siblingId) }
            .getOrElse { DocumentsContract.buildDocumentUri(authority, siblingId) }
        return runCatching {
            context.contentResolver.openInputStream(sibling)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        }.getOrNull()
    }

    private fun readSidecar(audio: File): String? {
        if (audio.path.isBlank()) return null
        val base = audio.name.substringBeforeLast('.', audio.name)
        val candidates = listOf(
            File(audio.parentFile, "$base.lrc"),
            File(audio.parentFile, "$base.LRC"),
        )
        for (file in candidates) {
            if (!file.isFile) continue
            val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
            if (!text.isNullOrBlank()) return text
        }
        return null
    }
}
