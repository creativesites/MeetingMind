package com.craftflowtechnologies.meetingmind.ai.common

import android.util.Log

/**
 * Diagnostics for Read to Me (logcat tag `ReadToMe`). It records what happened and how long it took,
 * never a note's words, titles or the API key, so a stuck reading can be understood from a log alone.
 */
object ReadToMeLog {
    private const val TAG = "ReadToMe"
    fun d(message: String) { runCatching { Log.d(TAG, message) } }
    fun w(message: String) { runCatching { Log.w(TAG, message) } }
}
