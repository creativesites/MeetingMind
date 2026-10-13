package com.craftflowtechnologies.meetingmind.ai.voice.read

import com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile

enum class SpeechProvider { GEMINI, LOCAL }

/** Why a reading can't use the cloud voice. Shown to the person, so each one is a plain reason. */
enum class CloudBlock {
    OFFLINE_MODE, NOT_CONFIGURED, NOT_ENTITLED,
    PRIVATE_NOTE, CONFIDENTIAL_NOTEBOOK, WORK_PRIVACY,
    PRIVATE_SECTIONS, LICENSED_SCRIPTURE
}

data class RouteInputs(
    val processingProfile: ProcessingProfile,
    val geminiConfigured: Boolean,
    /** Whether the person's plan includes the Gemini voice. Stubbed true until billing exists. */
    val entitled: Boolean = true,
    val notePrivate: Boolean,
    val notebookConfidential: Boolean,
    /** [com.craftflowtechnologies.meetingmind.core.work.WorkPrivacy] forced this item offline. */
    val workPrivacyOffline: Boolean = false,
    /** The note is behind a closed faith lock: nothing is generated or played. */
    val faithLocked: Boolean = false,
    /** The person accepted "Read to Me sends the note text to Google Gemini". */
    val disclosureAccepted: Boolean,
    /** The person asked for the phone's voice even when the cloud voice is allowed. */
    val prefersLocal: Boolean = false,
    val source: SpeechSource
)

data class RouteDecision(
    val provider: SpeechProvider,
    /** What stops the cloud voice. Empty means it is allowed (the person may still prefer the phone). */
    val cloudBlocks: Set<CloudBlock>,
    /** The cloud voice will be used but the person hasn't yet accepted the disclosure: ask first. */
    val needsDisclosure: Boolean,
    /** A closed faith lock: no generation, no playback. */
    val locked: Boolean,
    /** Generated audio of licensed text is kept only briefly, outside backups. */
    val ephemeralAudio: Boolean,
    /** The notification and lock screen show "Read to Me", not the note's title. */
    val genericLockScreenTitle: Boolean
) {
    val cloudAllowed: Boolean get() = cloudBlocks.isEmpty()

    /** Only private sections stand in the way: reading the rest with the cloud voice is a real choice. */
    val canOfferWithoutPrivate: Boolean get() = cloudBlocks == setOf(CloudBlock.PRIVATE_SECTIONS)
}

/**
 * Which voice may read this, decided once, in one place (docs/mvp/READ_TO_ME_PLAN.md §9, §16.8).
 * Pure: it reads settings and the shape of the note, never its words. Offline stays offline, and
 * private or confidential material never reaches the cloud voice.
 */
object SpeechRoute {

    fun decide(i: RouteInputs): RouteDecision {
        val blocks = buildSet {
            if (i.processingProfile != ProcessingProfile.INTERNET) add(CloudBlock.OFFLINE_MODE)
            if (!i.geminiConfigured) add(CloudBlock.NOT_CONFIGURED)
            if (!i.entitled) add(CloudBlock.NOT_ENTITLED)
            if (i.notePrivate) add(CloudBlock.PRIVATE_NOTE)
            if (i.notebookConfidential) add(CloudBlock.CONFIDENTIAL_NOTEBOOK)
            if (i.workPrivacyOffline) add(CloudBlock.WORK_PRIVACY)
            if (i.source.containsPrivate && !i.notePrivate) add(CloudBlock.PRIVATE_SECTIONS)
            if (i.source.containsLicensedScripture) add(CloudBlock.LICENSED_SCRIPTURE)
        }
        val provider = if (blocks.isEmpty() && !i.prefersLocal) SpeechProvider.GEMINI else SpeechProvider.LOCAL
        return RouteDecision(
            provider = provider,
            cloudBlocks = blocks,
            needsDisclosure = provider == SpeechProvider.GEMINI && !i.disclosureAccepted,
            locked = i.faithLocked,
            ephemeralAudio = i.source.containsLicensedScripture,
            genericLockScreenTitle = i.notePrivate || i.notebookConfidential || i.workPrivacyOffline || i.faithLocked
        )
    }
}
