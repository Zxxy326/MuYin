package tv.music.player.core.playback

import android.content.Context
import org.json.JSONObject
import tv.music.player.core.search.decodeTracks
import tv.music.player.core.search.encodeTracks
import tv.music.player.core.search.toDemoTrack
import tv.music.player.core.search.toJsonObject

data class SavedPlayback(
    val track: DemoTrack,
    val queue: List<DemoTrack>,
    val positionMs: Long,
)

/** 上次播放的歌曲、队列与进度，用于下次启动恢复封面与继续播放。 */
class PlaybackPersistence(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("playback_state", Context.MODE_PRIVATE)

    fun load(): SavedPlayback? {
        val raw = prefs.getString(KEY_STATE, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            val track = json.optJSONObject("track")?.toDemoTrack() ?: return@runCatching null
            val queue = decodeTracks(json.optString("queue")).orEmpty().ifEmpty { listOf(track) }
            SavedPlayback(
                track = track,
                queue = queue,
                positionMs = json.optLong("positionMs").coerceAtLeast(0L),
            )
        }.getOrNull()
    }

    fun save(track: DemoTrack, queue: List<DemoTrack>, positionMs: Long) {
        runCatching {
            val json = JSONObject()
                .put("track", track.toJsonObject())
                .put("queue", queue.encodeTracks())
                .put("positionMs", positionMs)
            prefs.edit().putString(KEY_STATE, json.toString()).apply()
        }
    }

    private companion object {
        const val KEY_STATE = "last_playback"
    }
}