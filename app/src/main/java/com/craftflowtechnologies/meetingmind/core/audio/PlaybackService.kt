package com.craftflowtechnologies.meetingmind.core.audio

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Owns the single, application-level [ExoPlayer] + [MediaSession] for all recording playback.
 * Media3's `MediaSessionService` handles the standard Android media architecture for us: the
 * foreground-service lifecycle (started while something is playing, allowed to stop once
 * nothing is), the playback notification, and exposing transport controls to the notification
 * shade, lock screen, and Bluetooth/headset controls — see docs/ARCHITECTURE.md "Playback
 * Architecture". No screen ever creates its own player; every UI talks to this one session
 * through [PlaybackController].
 *
 * Everything played here is speech (recordings, devotionals, readings), so the player asks for
 * audio focus as speech (it pauses for a call or another app and resumes when focus returns), pauses
 * when headphones are unplugged, and holds a wake lock so playback continues with the screen off.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(speechAttributes(), /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        val builder = MediaSession.Builder(this, player).setCallback(ResumptionCallback())
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            builder.setSessionActivity(PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        mediaSession = builder.build()
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
        scope.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    /** Resume after the process was killed: the last queue and position, from whatever the app registered. */
    private inner class ResumptionCallback : MediaSession.Callback {
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val source = PlaybackResumption.source
                ?: return Futures.immediateFailedFuture(UnsupportedOperationException("Nothing to resume."))
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            scope.launch {
                try {
                    val data = source.last()
                    if (data == null || data.items.isEmpty()) future.setException(UnsupportedOperationException("Nothing to resume."))
                    else future.set(MediaSession.MediaItemsWithStartPosition(data.items, data.startIndex, data.startPositionMs))
                } catch (e: Exception) {
                    future.setException(e)
                }
            }
            return future
        }
    }

    companion object {
        /** Speech, played as media: the right focus behaviour and routing for voices. */
        fun speechAttributes(): AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()
    }
}
