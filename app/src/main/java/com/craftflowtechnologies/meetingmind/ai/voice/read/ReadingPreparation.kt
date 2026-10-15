package com.craftflowtechnologies.meetingmind.ai.voice.read

import com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile

/** What the person may choose when part of a note can't go to the cloud voice. */
enum class ReadChoice {
    /** No explicit choice yet. */
    DEFAULT,
    /** Read the rest with Gemini and leave the private or personal parts out. */
    CLOUD_WITHOUT_SENSITIVE,
    /** Read everything, including those parts, with the phone's voice. */
    PHONE_VOICE
}

/** Everything about the phone's settings that decides routing. */
data class Environment(
    val profile: ProcessingProfile,
    val geminiConfigured: Boolean,
    val entitled: Boolean,
    val keyTier: GeminiKeyTier,
    /** The key in use is the one built into the build, not the person's own. */
    val usingBuiltInKey: Boolean
)

/** What is known about the note that matters for privacy. */
data class NoteFacts(
    val notePrivate: Boolean,
    val notebookConfidential: Boolean,
    val workPrivacyOffline: Boolean,
    val faithLocked: Boolean
) {
    /** The lock screen and notification say "Read to Me" instead of the note's title. */
    val genericTitle: Boolean get() = notePrivate || notebookConfidential || workPrivacyOffline || faithLocked
}

sealed interface Prepared {
    /** Good to go with [decision] over [source] (which has the left-out parts removed if the person chose that). */
    data class Ready(val source: SpeechSource, val decision: RouteDecision, val explanation: String?) : Prepared

    /** The cloud voice would be used but the person hasn't yet accepted what that sends to Google. */
    data class NeedsDisclosure(val source: SpeechSource, val decision: RouteDecision, val usingBuiltInKey: Boolean, val keyTier: GeminiKeyTier) : Prepared

    /** Some parts can't go to the cloud: ask whether to leave them out or use the phone for all of it. */
    data class NeedsChoice(val source: SpeechSource, val decision: RouteDecision, val message: String) : Prepared

    data object Locked : Prepared
    data object Empty : Prepared
}

/**
 * Applies the routing rules and the person's choices to a note, before anything is generated.
 * Pure: it reads settings and the shape of the note, never its words.
 */
object ReadingPreparation {

    fun prepare(source: SpeechSource, facts: NoteFacts, env: Environment, settings: ReadToMeSettings, choice: ReadChoice): Prepared {
        if (facts.faithLocked) return Prepared.Locked
        if (source.isEmpty) return Prepared.Empty

        fun decide(src: SpeechSource, prefersLocal: Boolean) = SpeechRoute.decide(
            RouteInputs(
                processingProfile = env.profile, geminiConfigured = env.geminiConfigured, entitled = env.entitled,
                notePrivate = facts.notePrivate, notebookConfidential = facts.notebookConfidential,
                workPrivacyOffline = facts.workPrivacyOffline, faithLocked = facts.faithLocked,
                disclosureAccepted = settings.disclosureAccepted, prefersLocal = prefersLocal, source = src, keyTier = env.keyTier
            )
        )

        if (choice == ReadChoice.PHONE_VOICE) {
            val d = decide(source, prefersLocal = true)
            return Prepared.Ready(source, d, RouteReasons.describe(d, forcedByChoice = true))
        }

        var working = source
        var decision = decide(working, settings.prefersLocal)

        if (choice == ReadChoice.CLOUD_WITHOUT_SENSITIVE) {
            working = if (CloudBlock.PERSONAL_ON_UNCONFIRMED_PLAN in decision.cloudBlocks) working.withoutPersonal() else working.withoutPrivate()
            if (working.isEmpty) return Prepared.Empty
            decision = decide(working, settings.prefersLocal)
        } else if (decision.provider == SpeechProvider.LOCAL && !settings.prefersLocal && decision.canOfferWithoutPersonal) {
            return Prepared.NeedsChoice(source, decision, choiceMessage(decision, source))
        }

        if (decision.needsDisclosure) return Prepared.NeedsDisclosure(working, decision, env.usingBuiltInKey, env.keyTier)
        return Prepared.Ready(working, decision, RouteReasons.describe(decision, forcedByChoice = false))
    }

    private fun choiceMessage(decision: RouteDecision, source: SpeechSource): String {
        val personal = CloudBlock.PERSONAL_ON_UNCONFIRMED_PLAN in decision.cloudBlocks
        val private = CloudBlock.PRIVATE_SECTIONS in decision.cloudBlocks
        return when {
            private && personal -> "This note has private sections and your own prayers or reflections. They stay on your phone. Read the rest with Gemini and skip them, or read everything with your phone's voice."
            private -> "This note has private sections. They stay on your phone. Read the rest with Gemini and skip them, or read everything with your phone's voice."
            else -> "This note has your own prayers or reflections, and the Gemini plan behind the key isn't confirmed as paid, so they stay on your phone. Read the rest with Gemini and skip them, or read everything with your phone's voice."
        }
    }
}

/** Plain-language reasons the phone's voice is being used, for the sheet. */
object RouteReasons {
    fun describe(d: RouteDecision, forcedByChoice: Boolean): String? {
        if (d.provider == SpeechProvider.GEMINI) return null
        if (forcedByChoice) return "Reading everything with your phone's voice, as you chose."
        val reasons = d.cloudBlocks.mapNotNull {
            when (it) {
                CloudBlock.OFFLINE_MODE -> "Offline mode is on, so nothing leaves your phone."
                CloudBlock.NOT_CONFIGURED -> "No Gemini key is set."
                CloudBlock.NOT_ENTITLED -> "Your plan doesn't include the Gemini voice."
                CloudBlock.PRIVATE_NOTE -> "This note is private, so it stays on your phone."
                CloudBlock.CONFIDENTIAL_NOTEBOOK, CloudBlock.WORK_PRIVACY -> "This is confidential work, so it stays on your phone."
                CloudBlock.PRIVATE_SECTIONS -> "Part of this note is private, so it stays on your phone."
                CloudBlock.LICENSED_SCRIPTURE -> "This Bible translation is licensed for display on your phone, so it is read with your phone's voice."
                CloudBlock.PERSONAL_ON_UNCONFIRMED_PLAN -> "Your own prayers and reflections stay on your phone unless the Gemini plan is confirmed as paid."
            }
        }
        return if (reasons.isEmpty()) "Using your phone's voice." else reasons.joinToString(" ")
    }
}
