package com.example.core.audio

import android.content.Intent
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Owns the single, application-level [ExoPlayer] + [MediaSession] for all recording playback.
 * Media3's `MediaSessionService` handles the standard Android media architecture for us: the
 * foreground-service lifecycle (started while something is playing, allowed to stop once
 * nothing is), the playback notification, and exposing transport controls to the notification
 * shade, lock screen, and Bluetooth/headset controls — see docs/ARCHITECTURE.md "Playback
 * Architecture". No screen ever creates its own player; every UI talks to this one session
 * through [PlaybackController].
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val session = mediaSession
        // If the user swipes the app away from Recents while nothing is actively playing, don't
        // keep an orphaned foreground service/notification alive.
        if (session == null || !session.player.playWhenReady || session.player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
