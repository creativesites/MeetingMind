package com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rive

import android.content.Context
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm

/**
 * Whether a form's `.riv` is bundled in `assets/companion/`. Checked at runtime, so a missing
 * file means Canvas, never a crash or a build error. Cached for the process.
 */
object CompanionAssets {
    @Volatile private var bundled: Set<String>? = null

    fun isBundled(context: Context, form: CompanionForm): Boolean =
        CompanionRiveContract.fileName(form) in files(context)

    fun files(context: Context): Set<String> = bundled ?: runCatching {
        context.assets.list(CompanionRiveContract.AssetDir)?.toSet().orEmpty()
    }.getOrDefault(emptySet()).also { bundled = it }

    fun read(context: Context, form: CompanionForm): ByteArray? = runCatching {
        context.assets.open(CompanionRiveContract.assetPath(form)).use { it.readBytes() }
    }.getOrNull()
}
