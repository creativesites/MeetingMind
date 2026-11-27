package com.craftflowtechnologies.meetingmind.ai.llm

import android.app.ActivityManager
import android.content.Context

/**
 * Whether an on-device model can be loaded without the system killing the app. A native
 * allocation that fails is not a catchable Java error: the process is killed or aborts. So a
 * model whose weights are larger than half the phone's memory is refused up front, and the
 * person sees "not enough memory" instead of a crash.
 */
object LocalModelFit {
    /** True when [modelBytes] fits in half of [totalRamBytes]. Unknown RAM (0 or less) is allowed. */
    fun fits(modelBytes: Long, totalRamBytes: Long): Boolean =
        totalRamBytes <= 0L || modelBytes <= totalRamBytes / 2

    fun totalRamBytes(context: Context): Long = runCatching {
        val am = context.getSystemService(ActivityManager::class.java) ?: return@runCatching 0L
        ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.totalMem
    }.getOrDefault(0L)
}
