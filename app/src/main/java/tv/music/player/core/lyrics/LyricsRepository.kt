package tv.music.player.core.lyrics

import android.content.Context
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.music.player.core.playback.DemoCatalog
import tv.music.player.core.playback.DemoTrack
import tv.music.player.core.search.OnlineSearchApi

class LyricsRepository(
    private val context: Context? = null,
    private val api: QqLyricsApi = QqLyricsApi(),
    private val diskCache: LyricsDiskCache? = null,
    private val matchStore: LyricsMatchStore? = null,
    private val searchApi: OnlineSearchApi? = null,
) {
    private val cache = object : LruCache<String, Lyrics>(24) {}

    suspend fun load(track: DemoTrack, forceRefresh: Boolean = false): LyricsState = withContext(Dispatchers.IO) {
        if (track.isLocal) {
            val localKey = "local:${track.localUri}"
            if (!forceRefresh) cache.get(localKey)?.let { return@withContext LyricsState.Ready(it) }
            val sidecar = context?.let { LocalLyricsLoader.load(it, track) }
            val local = sidecar ?: loadLocalOnline(track)
            return@withContext if (local != null) {
                cache.put(localKey, local)
                LyricsState.Ready(local)
            } else {
                LyricsState.Unavailable("未找到同名 .lrc 歌词")
            }
        }
        if (!track.isOnline) {
            val demo = Lyrics(
                lines = DemoCatalog.lyrics.map { LyricLine(it.timeMs, it.text) },
                sourceLabel = "演示歌词",
            )
            return@withContext LyricsState.Ready(demo)
        }

        val songmid = track.onlineSongId
        if (songmid.isBlank()) return@withContext LyricsState.Unavailable("缺少歌曲 ID")
        val matched = matchStore?.get(songmid)
        val fetchMid = matched?.songMid ?: songmid
        val fetchSongId = matched?.songId ?: track.onlineSongNumericId

        if (!forceRefresh) {
            cache.get(fetchMid)?.let { return@withContext LyricsState.Ready(it) }
            diskCache?.read(fetchMid)?.let { cached ->
                cache.put(fetchMid, cached)
                return@withContext LyricsState.Ready(cached)
            }
        }
        try {
            val lyrics = api.fetch(fetchMid, fetchSongId)
            when {
                lyrics == null -> LyricsState.Unavailable("暂无歌词")
                lyrics.instrumental -> LyricsState.Unavailable("纯音乐，请欣赏")
                lyrics.lines.isEmpty() -> LyricsState.Unavailable("暂无歌词")
                else -> {
                    cache.put(fetchMid, lyrics)
                    diskCache?.write(fetchMid, lyrics)
                    LyricsState.Ready(lyrics)
                }
            }
        } catch (t: Throwable) {
            LyricsState.Failed(t.message?.takeIf { it.isNotBlank() } ?: "歌词获取失败")
        }
    }

    private suspend fun loadLocalOnline(track: DemoTrack): Lyrics? {
        val search = searchApi ?: return null
        val title = track.title.trim()
        val artist = track.artist.trim()
        if (title.isBlank() || artist.isBlank()) return null
        if (artist == "未知歌手" || artist == "外部文件夹") return null
        val result = runCatching { search.search("$title $artist", 1, 8) }.getOrNull() ?: return null
        val localTitle = normalize(title)
        val localArtist = normalize(artist)
        val candidate = result.list.firstOrNull { item ->
            val itemTitle = normalize(item.title)
            val itemArtist = normalize(item.artist)
            itemTitle == localTitle && itemArtist.isNotBlank() &&
                (itemArtist.contains(localArtist) || localArtist.contains(itemArtist))
        } ?: return null
        val lyrics = runCatching { api.fetch(candidate.songmid, candidate.songId) }.getOrNull() ?: return null
        if (lyrics.instrumental || lyrics.lines.isEmpty()) return null
        return lyrics.copy(sourceLabel = "QQ 音乐 · 本地匹配")
    }

    private fun normalize(text: String): String =
        text.lowercase().replace(Regex("[\\(（\\[【].*?[\\)）\\]】]"), "").replace(Regex("[\\s·・,，.。-]"), "")

    suspend fun searchCandidates(track: DemoTrack): List<LyricCandidate> = withContext(Dispatchers.IO) {
        val search = searchApi ?: return@withContext emptyList()
        val keyword = listOf(track.title, track.artist)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        if (keyword.isBlank()) return@withContext emptyList()
        runCatching { search.search(keyword, 1, 8) }
            .getOrNull()
            ?.list
            .orEmpty()
            .map { LyricCandidate(it.songmid, it.songId, it.title, it.artist) }
            .distinctBy { it.songMid }
            .take(6)
    }

    fun setMatch(originalSongmid: String, candidate: LyricCandidate) {
        matchStore?.put(originalSongmid, candidate)
    }

    fun clearMatch(originalSongmid: String) {
        matchStore?.clear(originalSongmid)
    }
}
