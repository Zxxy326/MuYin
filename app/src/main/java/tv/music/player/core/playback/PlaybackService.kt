package tv.music.player.core.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import tv.music.player.MainActivity
import tv.music.player.TvMusicApplication

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val playback = (application as TvMusicApplication).container.playback
        val sessionPlayer = object : ForwardingPlayer(playback.player) {
            override fun play() = playback.requestPlayFromSession()

            override fun setPlayWhenReady(playWhenReady: Boolean) {
                if (playWhenReady) playback.requestPlayFromSession()
                else super.setPlayWhenReady(false)
            }

            override fun seekToNext() = playback.next()
            override fun seekToNextMediaItem() = playback.next()
            override fun seekToPrevious() = playback.previous()
            override fun seekToPreviousMediaItem() = playback.previous()

            override fun getAvailableCommands(): Player.Commands =
                super.getAvailableCommands().buildUpon()
                    .addAll(
                        Player.COMMAND_SEEK_TO_NEXT,
                        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                        Player.COMMAND_SEEK_TO_PREVIOUS,
                        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    )
                    .build()

            override fun isCommandAvailable(command: Int): Boolean = when (command) {
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
                else -> super.isCommandAvailable(command)
            }
        }
        val sessionIntent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        mediaSession = MediaSession.Builder(this, sessionPlayer)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
