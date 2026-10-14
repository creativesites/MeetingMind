package com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import com.craftflowtechnologies.meetingmind.R

/**
 * The First Keep chime (§2.5, decision 3): one soft two-note chime (`res/raw/first_keep_chime.ogg`,
 * 290 ms, under 5 KB; made by `tools/rive/companion/chime/make_first_keep_chime.py`), at 1.5 s of the
 * two once-ever First Keeps. No other moment makes a sound.
 */
object FirstKeepChime {

    /**
     * The whole gate, pure: the plan allows it (once-ever and not quiet), the user's "Sounds" switch is on,
     * the ringer is normal and Do Not Disturb is off.
     */
    fun allowed(planChime: Boolean, quiet: Boolean, sounds: Boolean, ringerNormal: Boolean, dndOff: Boolean): Boolean =
        planChime && !quiet && sounds && ringerNormal && dndOff

    /** Plays it if [allowed] with the phone's ringer and Do Not Disturb state. Never throws. */
    fun play(context: Context, planChime: Boolean, quiet: Boolean, sounds: Boolean) {
        runCatching {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val ringerNormal = audio?.ringerMode == AudioManager.RINGER_MODE_NORMAL
            val dndOff = nm == null || nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL ||
                nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            if (!allowed(planChime, quiet, sounds, ringerNormal, dndOff)) return
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val player = MediaPlayer.create(context, R.raw.first_keep_chime, attrs, audio?.generateAudioSessionId() ?: 0) ?: return
            player.setOnCompletionListener { it.release() }
            player.start()
        }
    }

    /** Where the chime falls on the First Keep's base timeline (the card lands). */
    const val AtBaseMs = 1500
}
