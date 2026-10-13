package com.craftflowtechnologies.meetingmind.ai.voice.read

import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A note as Read to Me needs it: what to say and what is known about its privacy. */
data class LoadedNote(
    val title: String,
    val space: NotebookSpace?,
    val source: SpeechSource,
    val facts: NoteFacts,
    val language: String?
)

/** Everything the coordinator needs from the phone, so the state machine can be tested with fakes. */
interface ReadingHost {
    val store: ReadingStore
    val player: ReadPlayer
    suspend fun loadNote(noteId: String, options: ExtractOptions): LoadedNote?
    suspend fun environment(): Environment
    fun settings(): ReadToMeSettings
    fun updateSettings(change: (ReadToMeSettings) -> ReadToMeSettings): ReadToMeSettings
    /** The phone's voice. [requireOffline] when the voice must not use the network. */
    fun localVoice(settings: ReadToMeSettings, requireOffline: Boolean): SectionVoice
    /** The Gemini voice; every request is checked again against [routeAllowsCloud] and the live settings. */
    fun geminiVoice(settings: ReadToMeSettings, delivery: DeliverySpec, routeAllowsCloud: Boolean): SectionVoice
}

sealed interface ReadingUiState {
    data object Idle : ReadingUiState
    /** Making parts and playing as they arrive. [done] parts are ready out of [total]. */
    data class Working(val noteId: String, val provider: SpeechProvider, val done: Int, val total: Int, val note: String?) : ReadingUiState
    /** Every part is made; playback carries on in the player. */
    data class Finished(val noteId: String, val provider: SpeechProvider, val total: Int, val note: String?, val switchedToPhone: Boolean) : ReadingUiState
    data class NeedsChoice(val noteId: String, val message: String) : ReadingUiState
    data class NeedsDisclosure(val noteId: String, val usingBuiltInKey: Boolean, val keyTier: GeminiKeyTier) : ReadingUiState
    data class Failed(val noteId: String, val partIndex: Int, val total: Int, val message: String, val options: List<Recovery>, val provider: SpeechProvider) : ReadingUiState
    data object Locked : ReadingUiState
    data object Empty : ReadingUiState
    data object NotFound : ReadingUiState
}

/** What the sheet shows before the person starts. */
data class ReadOffer(
    val title: String,
    val sections: List<Pair<String, String>>,
    val provider: SpeechProvider?,
    val reason: String?,
    val locked: Boolean,
    val empty: Boolean,
    val hasSavedPlace: Boolean,
    val skippedCount: Int,
    val space: NotebookSpace?
)

/**
 * Runs readings for the whole app (one at a time, like the player they feed). It decides the voice
 * through [ReadingPreparation], asks the person when a choice is needed, makes the parts with a
 * [ReadingRunner], and reports a single [ReadingUiState]. A reading keeps one provider from start to
 * end: a failure offers retry or an explicit switch to the phone's voice, never a silent change.
 */
class ReadingCoordinator(private val host: ReadingHost, private val scope: CoroutineScope) {

    private data class Request(val noteId: String, val startSectionId: String?, val choice: ReadChoice, val resume: Boolean)

    /** What a failure needs to continue from. */
    private data class Stalled(val request: Request, val plan: ReadPlan, val voice: SectionVoice, val decision: RouteDecision, val index: Int, val note: String?)

    private val _state = MutableStateFlow<ReadingUiState>(ReadingUiState.Idle)
    val state: StateFlow<ReadingUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var lastRequest: Request? = null
    private var stalled: Stalled? = null

    /** What to show in the sheet for [noteId], without generating anything. */
    suspend fun offer(noteId: String): ReadOffer? {
        val settings = host.settings()
        val note = host.loadNote(noteId, settings.extractOptions()) ?: return null
        val prepared = ReadingPreparation.prepare(note.source, note.facts, host.environment(), settings, ReadChoice.DEFAULT)
        val (provider, reason) = when (prepared) {
            is Prepared.Ready -> prepared.decision.provider to prepared.explanation
            is Prepared.NeedsDisclosure -> SpeechProvider.GEMINI to null
            is Prepared.NeedsChoice -> null to prepared.message
            else -> null to null
        }
        val manifest = host.store.load(noteId)
        return ReadOffer(
            title = note.title,
            sections = note.source.sections.map { it.id to it.title },
            provider = provider,
            reason = reason,
            locked = prepared is Prepared.Locked,
            empty = prepared is Prepared.Empty,
            hasSavedPlace = manifest?.progress?.let { !it.completed } == true,
            skippedCount = note.source.skipped.size,
            space = note.space
        )
    }

    fun start(noteId: String, startSectionId: String? = null, choice: ReadChoice = ReadChoice.DEFAULT, resume: Boolean = false) =
        launch(Request(noteId, startSectionId, choice, resume))

    fun chooseCloudWithoutSensitive() = relaunch { it.copy(choice = ReadChoice.CLOUD_WITHOUT_SENSITIVE) }

    fun choosePhoneVoice() = relaunch { it.copy(choice = ReadChoice.PHONE_VOICE) }

    /** The person accepted what the cloud voice sends; remembered, then the reading goes ahead. */
    fun acceptDisclosure() {
        host.updateSettings { it.copy(disclosureAccepted = true) }
        relaunch { it }
    }

    fun retry() {
        val s = stalled ?: return relaunch { it }
        launchResume(s, s.voice)
    }

    /** After a Gemini failure: finish the reading with the phone's voice, only the parts not yet made. */
    fun switchToPhoneVoice() {
        val s = stalled ?: return
        val settings = host.settings()
        launchResume(s, host.localVoice(settings, requireOffline = true), switched = true)
    }

    fun cancel() {
        job?.cancel()
        job = null
        stalled = null
        _state.value = ReadingUiState.Idle
    }

    fun dismiss() {
        if (_state.value !is ReadingUiState.Working) _state.value = ReadingUiState.Idle
    }

    private fun relaunch(change: (Request) -> Request) {
        val r = lastRequest ?: return
        launch(change(r))
    }

    private fun launch(request: Request) {
        job?.cancel()
        stalled = null
        lastRequest = request
        job = scope.launch {
            try { begin(request) } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _state.value = ReadingUiState.Failed(request.noteId, 0, 0, "Read to Me couldn't start. ${e.message.orEmpty()}".trim(), listOf(Recovery.RETRY), SpeechProvider.LOCAL)
            }
        }
    }

    private fun launchResume(s: Stalled, voice: SectionVoice, switched: Boolean = false) {
        job?.cancel()
        stalled = null
        job = scope.launch {
            try { execute(s.request, s.plan, voice, s.decision, s.index, s.note, switched) } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _state.value = ReadingUiState.Failed(s.request.noteId, s.index, s.plan.parts.size, "Read to Me stopped. ${e.message.orEmpty()}".trim(), listOf(Recovery.RETRY), voice.provider)
            }
        }
    }

    private suspend fun begin(request: Request) {
        _state.value = ReadingUiState.Working(request.noteId, SpeechProvider.LOCAL, 0, 0, null)
        val settings = host.settings()
        val note = host.loadNote(request.noteId, settings.extractOptions()) ?: run { _state.value = ReadingUiState.NotFound; return }
        val env = host.environment()
        when (val prepared = ReadingPreparation.prepare(note.source, note.facts, env, settings, request.choice)) {
            is Prepared.Locked -> _state.value = ReadingUiState.Locked
            is Prepared.Empty -> _state.value = ReadingUiState.Empty
            is Prepared.NeedsChoice -> _state.value = ReadingUiState.NeedsChoice(request.noteId, prepared.message)
            is Prepared.NeedsDisclosure -> _state.value = ReadingUiState.NeedsDisclosure(request.noteId, prepared.usingBuiltInKey, prepared.keyTier)
            is Prepared.Ready -> {
                val source = ReadingPlan.fromSection(prepared.source, request.startSectionId)
                val parts = ReadingPlan.parts(source, settings.pauseAfterQuestion == PauseAfterQuestion.UNTIL_RESUME)
                val delivery = DeliveryResolver.resolve(settings, note.space)
                val plan = ReadPlan(
                    noteId = request.noteId, parts = parts, mode = MODE_EXACT, delivery = delivery, language = note.language,
                    genericTitle = note.facts.genericTitle, ephemeral = prepared.decision.ephemeralAudio, settings = settings
                )
                val voice = if (prepared.decision.provider == SpeechProvider.GEMINI) host.geminiVoice(settings, delivery, prepared.decision.cloudAllowed)
                else host.localVoice(settings, requireOffline = mustStayOffline(env, note.facts))
                execute(request, plan, voice, prepared.decision, alreadyQueued = 0, note = prepared.explanation, switched = false)
            }
        }
    }

    private fun mustStayOffline(env: Environment, facts: NoteFacts) =
        env.profile != ProcessingProfile.INTERNET || facts.notePrivate || facts.notebookConfidential || facts.workPrivacyOffline

    private suspend fun execute(request: Request, plan: ReadPlan, voice: SectionVoice, decision: RouteDecision, alreadyQueued: Int, note: String?, switched: Boolean) {
        val total = plan.parts.size
        val resume = if (request.resume && alreadyQueued == 0) resumeTarget(plan) else null
        val provider = voice.provider
        _state.value = ReadingUiState.Working(request.noteId, provider, alreadyQueued, total, note)
        val runner = ReadingRunner(host.store, host.player)
        when (val outcome = runner.run(plan, voice, resume, alreadyQueued) { done, all ->
            _state.value = ReadingUiState.Working(request.noteId, provider, done, all, note)
        }) {
            is RunOutcome.Completed -> _state.value = ReadingUiState.Finished(request.noteId, provider, total, note, switched || host.store.load(request.noteId)?.switchedToPhoneAtSection != null)
            is RunOutcome.Failed -> {
                stalled = Stalled(request, plan, voice, decision, outcome.partIndex, note)
                // Offered after any cloud failure, never after a phone-voice one: the reading has nowhere else to go.
                val options = if (provider == SpeechProvider.GEMINI) (outcome.recovery.options + Recovery.SWITCH_TO_PHONE_VOICE).distinct()
                else outcome.recovery.options.filter { it != Recovery.SWITCH_TO_PHONE_VOICE }
                _state.value = ReadingUiState.Failed(request.noteId, outcome.partIndex, total, outcome.recovery.message, options, provider)
            }
        }
    }

    private fun resumeTarget(plan: ReadPlan): ResumeTarget? {
        val progress = host.store.load(plan.noteId)?.progress ?: return null
        if (progress.completed) return null
        val index = progress.mediaId?.let { id -> plan.parts.indexOfFirst { id.contains(":${it.id}:") } }?.takeIf { it >= 0 } ?: progress.sectionIndex
        return if (index in plan.parts.indices) ResumeTarget(index, progress.positionMs) else null
    }

    companion object {
        const val MODE_EXACT = "exact"
    }
}
