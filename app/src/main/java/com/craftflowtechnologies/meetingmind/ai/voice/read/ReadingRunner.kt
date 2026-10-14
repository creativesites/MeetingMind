package com.craftflowtechnologies.meetingmind.ai.voice.read

import android.net.Uri
import com.craftflowtechnologies.meetingmind.ai.voice.Pcm
import com.craftflowtechnologies.meetingmind.ai.voice.SpeechChunk
import com.craftflowtechnologies.meetingmind.core.audio.QueueItem
import kotlinx.coroutines.CancellationException

/** The shared player, as the runner needs it. */
interface ReadPlayer {
    fun play(queueId: String, items: List<QueueItem>, startIndex: Int, startPositionMs: Long, expected: Int)
    fun append(item: QueueItem)
    fun setExpected(count: Int)
}

/**
 * Hands every player call to the main thread, in order. The shared player (and the Media3 controller
 * behind it) may only be touched from there, while readings are made on background threads.
 */
class MainThreadReadPlayer(private val delegate: ReadPlayer, private val post: (Runnable) -> Unit) : ReadPlayer {
    override fun play(queueId: String, items: List<QueueItem>, startIndex: Int, startPositionMs: Long, expected: Int) =
        post { delegate.play(queueId, items, startIndex, startPositionMs, expected) }
    override fun append(item: QueueItem) = post { delegate.append(item) }
    override fun setExpected(count: Int) = post { delegate.setExpected(count) }
}

/** For a reading made in the background: parts are saved to disk and nothing is played. */
object SilentReadPlayer : ReadPlayer {
    override fun play(queueId: String, items: List<QueueItem>, startIndex: Int, startPositionMs: Long, expected: Int) = Unit
    override fun append(item: QueueItem) = Unit
    override fun setExpected(count: Int) = Unit
}

sealed interface VoiceResult {
    data class Done(val audio: Pcm, val model: String? = null) : VoiceResult
    data class Failed(val message: String, val recovery: RecoveryPlan) : VoiceResult
}

/** One provider's way of turning a part into audio. A reading uses one of these from start to end. */
interface SectionVoice {
    val provider: SpeechProvider
    /** Goes into the fingerprint: the engine or model family, not a specific fallback model. */
    val fingerprintModel: String
    val voiceId: String
    suspend fun synthesize(partId: String, fingerprint: String, chunks: List<SpeechChunk>, onProgress: (Float) -> Unit): VoiceResult
    fun discard(partId: String)
}

data class ReadPlan(
    val noteId: String,
    val parts: List<SpeechSection>,
    val mode: String,
    val delivery: DeliverySpec,
    val language: String?,
    /** Show "Read to Me" instead of the note's title on the lock screen and in the queue. */
    val genericTitle: Boolean,
    /** Audio of licensed text: kept briefly, outside backups. */
    val ephemeral: Boolean,
    val settings: ReadToMeSettings,
    /** Smart Read: shapes a part's wording just before it is spoken. Null for Read Exactly, which never changes a word. */
    val shape: (suspend (SpeechSection) -> SpeechSection)? = null,
    val scriptVersion: Int = 1
)

data class ResumeTarget(val partIndex: Int, val positionMs: Long)

sealed interface RunOutcome {
    data class Completed(val parts: Int) : RunOutcome
    /** Generation stopped at [partIndex] (0-based); everything before it is saved and queued. */
    data class Failed(val partIndex: Int, val message: String, val recovery: RecoveryPlan) : RunOutcome
}

/**
 * Makes a reading, one part at a time, in order: reuses a saved part while its fingerprint still
 * matches, otherwise has the voice make it; saves it; and hands it to the shared player so playback
 * can begin after the first part while the rest is made. The first failure stops the run and leaves
 * everything done so far in place; nothing is skipped and no other voice is tried.
 */
class ReadingRunner(private val store: ReadingStore, private val player: ReadPlayer, private val clock: () -> Long = System::currentTimeMillis) {

    suspend fun run(
        plan: ReadPlan,
        voice: SectionVoice,
        resume: ResumeTarget? = null,
        /** Parts before this index are already in the player's queue (a switch of voice part-way). */
        alreadyQueued: Int = 0,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
        /** How far along the part being made is, 0 to 1. */
        onPartProgress: (Float) -> Unit = {}
    ): RunOutcome {
        val total = plan.parts.size
        val settings = FingerprintSettings(plan.mode, voice.provider, voice.fingerprintModel, voice.voiceId, plan.delivery.canonical(), plan.language, plan.scriptVersion)
        val previous = store.load(plan.noteId)
        val entries = LinkedHashMap<String, SectionEntry>()
        previous?.sections?.forEach { if (it.status == SectionStatus.READY) entries[it.sectionId] = it }
        val fingerprints = plan.parts.map { ReadFingerprint.section(it, settings) }

        fun fresh(i: Int): SectionEntry? =
            entries[plan.parts[i].id]?.takeIf { it.fingerprint == fingerprints[i] && store.audioFile(plan.noteId, it) != null }

        // Resume only when everything before the resume point is still good; otherwise start over.
        val resumeAt = resume?.takeIf { r -> r.partIndex in 0 until total && (0 until r.partIndex).all { fresh(it) != null } }
        val queueId = ReadingStore.queueId(plan.noteId)
        val queued = ArrayList<QueueItem>()
        var started = alreadyQueued > 0
        var switchedAt = previous?.switchedToPhoneAtSection
        if (alreadyQueued > 0 && voice.provider == SpeechProvider.LOCAL && switchedAt == null) switchedAt = alreadyQueued

        fun save(model: String?) {
            val sections = plan.parts.mapIndexed { i, part ->
                // Audio made for an older version of the part is not kept as if it were current.
                entries[part.id]?.takeIf { it.fingerprint == fingerprints[i] }
                    ?: SectionEntry(part.id, titleOf(plan, part), fingerprints[i], SectionStatus.PENDING, blockIds = part.blockIds, references = part.references)
            }
            store.update(plan.noteId) { current ->
                ReadingManifest(
                    noteId = plan.noteId, mode = plan.mode, provider = voice.provider.name, model = model, voiceId = voice.voiceId,
                    delivery = plan.delivery.canonical(), language = plan.language, genericTitle = plan.genericTitle,
                    createdAt = current?.createdAt ?: clock(), updatedAt = clock(), expectedSections = total, sections = sections,
                    // Playback saves where the person is; generation must never overwrite that.
                    progress = current?.progress, switchedToPhoneAtSection = switchedAt ?: current?.switchedToPhoneAtSection
                )
            }
        }

        var model: String? = previous?.model
        try {
            for (i in alreadyQueued until total) {
                val part = plan.parts[i]
                val entry = fresh(i) ?: run {
                    onProgress(i, total)
                    when (val r = voice.synthesize(part.id, fingerprints[i], ReadingPlan.chunks(plan.shape?.invoke(part) ?: part, plan.settings), onPartProgress)) {
                        is VoiceResult.Failed -> {
                            entries[part.id] = SectionEntry(part.id, titleOf(plan, part), fingerprints[i], SectionStatus.FAILED, blockIds = part.blockIds, references = part.references, error = r.message)
                            save(model)
                            return RunOutcome.Failed(i, r.message, r.recovery)
                        }
                        is VoiceResult.Done -> {
                            model = r.model ?: model
                            store.commitAudio(
                                plan.noteId, part.id, titleOf(plan, part), fingerprints[i], r.audio, part.blockIds, part.references,
                                plan.ephemeral, ReadingPlan.pausesAfter(part, plan.settings)
                            ).also { voice.discard(part.id) }
                        }
                    }
                }
                entries[part.id] = entry
                save(model)
                val file = store.audioFile(plan.noteId, entry) ?: continue
                val item = QueueItem(
                    mediaId = ReadingStore.mediaId(plan.noteId, part.id, entry.fingerprint),
                    title = entry.title, uri = Uri.fromFile(file), blockIds = part.blockIds, sectionId = part.id, pauseAfter = entry.pauseAfter
                )
                queued += item
                if (!started) {
                    if (resumeAt != null && i < resumeAt.partIndex) { onProgress(i + 1, total); continue }
                    val startIndex = if (resumeAt != null) resumeAt.partIndex else 0
                    player.play(queueId, queued.toList(), startIndex.coerceAtMost(queued.lastIndex), if (resumeAt != null) resumeAt.positionMs else 0L, total)
                    started = true
                } else {
                    player.append(item)
                }
                onProgress(i + 1, total)
            }
            save(model)
            store.pruneUnreferenced(plan.noteId)
            player.setExpected(total)
            return RunOutcome.Completed(total)
        } catch (e: CancellationException) {
            // Keep what is done; a later run picks up from the saved parts.
            runCatching { save(model) }
            throw e
        }
    }

    private fun titleOf(plan: ReadPlan, part: SpeechSection) = if (plan.genericTitle) "Read to Me" else part.title
}
