package com.craftflowtechnologies.meetingmind.feature.onboarding

import com.craftflowtechnologies.meetingmind.ai.asr.SupportedLanguages
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.AsrModelResolver
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.LlmModelResolver
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.ModelCatalog
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.ModelStorage
import com.craftflowtechnologies.meetingmind.core.common.DeviceCapabilityDetector
import com.craftflowtechnologies.meetingmind.core.model.ModelCapability
import com.craftflowtechnologies.meetingmind.core.setup.SetupGuide

/*
 * The pure parts of the end of onboarding: what the "You're set" step offers, the on-phone download sized for this
 * phone and language, and the recording language onboarding starts with. No Context, no disk: the view model passes
 * in the phone's memory, free storage and language.
 */

/** A first thing to do, offered on the "You're set" step. */
enum class ReadyAction {
    /** Opens the recorder. Works on both choices: on this phone, the notes wait for the download. */
    RECORD,
    /** Opens "Notes from a YouTube video". Needs the internet, so it is offered only on the Internet choice. */
    YOUTUBE,
    /** Opens the guided on-device setup (the download). */
    SET_UP_PHONE,
    /** Home, with nothing started. */
    LOOK_AROUND
}

/**
 * The "You're set" actions, main one first. Internet: a first note in a minute, by recording or from a YouTube link.
 * On this phone: the download comes first, because nothing is made into notes until it's done. YouTube is left out
 * there: the person chose to keep audio on the phone, and a video's notes are made over the internet.
 */
fun readyActionsFor(setup: SetupChoice, phoneReady: Boolean = false): List<ReadyAction> = when (setup) {
    SetupChoice.INTERNET -> listOf(ReadyAction.RECORD, ReadyAction.YOUTUBE, ReadyAction.LOOK_AROUND)
    // Everything is already on the phone (a reinstall that kept its models): nothing to download, so record first.
    SetupChoice.ON_DEVICE -> if (phoneReady) listOf(ReadyAction.RECORD, ReadyAction.LOOK_AROUND)
    else listOf(ReadyAction.SET_UP_PHONE, ReadyAction.RECORD, ReadyAction.LOOK_AROUND)
}

/** Room the phone keeps free after the download, so recordings still have space. */
const val PACK_HEADROOM_BYTES: Long = 500L * 1_000_000L

/**
 * The on-phone download, as the guided setup will fetch it for this phone and recording language.
 * [freeBytes] is the phone's free storage, or null when it couldn't be read.
 */
data class PhonePack(val bytes: Long, val freeBytes: Long?) {
    /** Free storage left once it's installed, or null when free storage is unknown. */
    val freeAfterBytes: Long? get() = freeBytes?.let { (it - bytes).coerceAtLeast(0L) }

    /** False when it would leave less than [PACK_HEADROOM_BYTES] free; null when free storage is unknown. */
    val fits: Boolean? get() = freeBytes?.let { it - bytes >= PACK_HEADROOM_BYTES }

    /** Nothing left to download: every part is already on the phone. */
    val ready: Boolean get() = bytes <= 0L
}

/**
 * What the guided setup still has to download, worked out by the setup guide itself ([SetupGuide.compute]) for the
 * same language, so the size onboarding promises is the size the setup fetches. A Bemba speaker sees the Omnilingual
 * pack, not the English one. A model already on the phone ([isInstalled]) is never counted again, and with any speech
 * model installed the setup keeps that one: onboarding never sizes, or pushes, a different one.
 */
fun phonePackFor(
    totalRamGb: Float,
    recordingLanguage: String?,
    freeBytes: Long?,
    isInstalled: (String) -> Boolean = { false }
): PhonePack {
    val state = SetupGuide.compute(totalRamGb, isInstalled, language = recordingLanguage)
    val bytes = state.parts.filterNot { it.installed }.sumOf { part -> part.models.filterNot { isInstalled(it.id) }.sumOf { it.sizeBytes } }
    return PhonePack(bytes = bytes, freeBytes = freeBytes)
}

/**
 * The speech model onboarding saves as the selection. The founder's rule: no screen pushes a model over one that is
 * already on the phone. With any speech model installed, the resolver's choice among the installed ones is kept
 * ([AsrModelResolver.selectionToSave]); only a phone with none gets the recommendation for its memory and language.
 */
fun onboardingSpeechModelId(installedIds: Set<String>, selectedId: String?, language: String?, ramMb: Int?): String {
    val installedSpeech = installedIds.filter { AsrModelResolver.isSpeechModel(it) }
    if (installedSpeech.isNotEmpty()) {
        AsrModelResolver.selectionToSave(installedIds, selectedId, language, ramMb)?.let { return it }
        // Null means the saved one is installed and kept; otherwise still one that's on the phone, never a download.
        return selectedId?.takeIf { it in installedSpeech } ?: installedSpeech.first()
    }
    return AsrModelResolver.recommended(ramMb, language).id
}

/**
 * The language model onboarding saves, under the same rule as speech: one already on the phone is kept
 * ([LlmModelResolver.resolve]: the saved one when it's installed, otherwise the strongest installed). Only a phone with
 * none gets the one that suits its memory.
 */
fun onboardingThinkerModelId(storage: ModelStorage, selectedId: String, totalRamGb: Float): String {
    val anyInstalled = ModelCatalog.entries.any { ModelCapability.SUMMARIZATION in it.capability && storage.isInstalled(it.id) }
    return if (anyInstalled) LlmModelResolver.resolve(selectedId, storage)
    else DeviceCapabilityDetector.recommendedLlmModelId(totalRamGb)
}

/** Every language a recording can be made in (the recording language list), as recording-language codes. */
private val RecordableLanguages: Set<String> = (
    SupportedLanguages.TIER_A_INTERNATIONAL_LANGUAGES +
        SupportedLanguages.SOUTHERN_AFRICAN_LANGUAGES +
        SupportedLanguages.WHISPER_PREFERRED_LANGUAGES +
        listOf(SupportedLanguages.TURKISH, SupportedLanguages.RUSSIAN, SupportedLanguages.POLISH)
    ).toSet()

/**
 * The recording language to start with, from the language the app is shown in ([appLanguageTag], the one picked in
 * onboarding, or the phone's own when none was picked). Someone who reads the app in Bemba most likely records in Bemba,
 * so the setup downloads the speech model that understands it. Null keeps the saved recording language: the language
 * can't be recorded in, or nothing is known.
 */
fun recordingLanguageFor(appLanguageTag: String?): String? {
    val code = AsrModelResolver.normalizeLanguage(appLanguageTag)
    return code.takeIf { it.isNotBlank() && it in RecordableLanguages }
}
