package com.example.core.repository

import android.content.Context
import com.example.ai.common.AiResult
import com.example.ai.common.describeFailure
import com.example.ai.embeddings.EmbeddingEngine
import com.example.ai.embeddings.LocalEmbeddingEngine
import com.example.ai.modelmanagement.LocalModelStorage
import com.example.ai.modelmanagement.ModelCatalog
import com.example.ai.modelmanagement.ModelDownloader
import com.example.ai.modelmanagement.ModelStorage
import com.example.ai.modelmanagement.ModelVerifier
import com.example.ai.modelmanagement.OkHttpModelDownloader
import com.example.ai.modelmanagement.Sha256ModelVerifier
import com.example.core.common.DeviceCapabilityDetector
import com.example.core.database.ActionItemEntity
import com.example.core.database.AiModelEntity
import com.example.core.database.ChatMessageEntity
import com.example.core.database.MeetMindDatabase
import com.example.core.database.MeetingEntity
import com.example.core.database.SpeakerEntity
import com.example.core.database.TranscriptSegmentEntity
import com.example.core.firebase.FirebaseAuthManager
import com.example.core.firebase.FirebaseUserModel
import com.example.core.model.ActionItem
import com.example.core.model.AiModelInfo
import com.example.core.model.ChatMessage
import com.example.core.model.Decision
import com.example.core.model.Meeting
import com.example.core.model.MeetingSource
import com.example.core.model.MeetingStatus
import com.example.core.model.ModelCapability
import com.example.core.model.Question
import com.example.core.model.Speaker
import com.example.core.model.Topic
import com.example.core.model.Transcript
import com.example.core.model.TranscriptSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class MeetingRepository(
    private val context: Context,
    private val database: MeetMindDatabase
) {
    private val meetingDao = database.meetingDao()
    private val actionItemDao = database.actionItemDao()
    private val decisionDao = database.decisionDao()

    val allMeetings: Flow<List<Meeting>> = meetingDao.getAllMeetings().map { entities ->
        entities.map { it.toDomain() }
    }.flowOn(Dispatchers.IO)

    fun getMeetingById(id: String): Flow<Meeting?> = meetingDao.getMeetingByIdFlow(id).map { entity ->
        entity?.toDomain()
    }.flowOn(Dispatchers.IO)

    suspend fun getMeetingByIdDirect(id: String): Meeting? = withContext(Dispatchers.IO) {
        meetingDao.getMeetingById(id)?.toDomain()
    }

    suspend fun createInitialMeeting(
        id: String = UUID.randomUUID().toString(),
        title: String,
        source: MeetingSource,
        audioFilePath: String? = null,
        recordingType: com.example.core.model.RecordingType = com.example.core.model.RecordingType.GENERAL,
        customContext: String? = null,
        speakerCountPreference: Int? = null
    ): Meeting = withContext(Dispatchers.IO) {
        val entity = MeetingEntity(
            id = id,
            title = title,
            createdAt = System.currentTimeMillis(),
            durationMs = 0L,
            source = source.name,
            audioFilePath = audioFilePath,
            status = MeetingStatus.RECORDING.name,
            participantCount = 1,
            language = "en",
            summaryText = null,
            recordingType = recordingType.name,
            customContext = customContext,
            speakerCountPreference = speakerCountPreference
        )
        meetingDao.insertMeeting(entity)
        entity.toDomain()
    }

    suspend fun updateMeetingTitle(id: String, newTitle: String) = withContext(Dispatchers.IO) {
        val existing = meetingDao.getMeetingById(id) ?: return@withContext
        meetingDao.updateMeeting(existing.copy(title = newTitle, updatedAt = System.currentTimeMillis()))
    }

    /** Persists the user's answer to the before-processing "How many speakers?" prompt — reached
     * only when [com.example.core.model.RecordingContext.speakerCountPreference] wasn't already
     * captured at recording/import time, so the pipeline can skip diarization for a confirmed
     * single speaker and never has to ask again. */
    suspend fun updateSpeakerCountPreference(id: String, speakerCount: Int?) = withContext(Dispatchers.IO) {
        val existing = meetingDao.getMeetingById(id) ?: return@withContext
        meetingDao.updateMeeting(existing.copy(speakerCountPreference = speakerCount, updatedAt = System.currentTimeMillis()))
    }

    /** Applies a [com.example.core.model.RecordingContext] captured after the meeting row already
     * exists — e.g. import, which creates the meeting the moment a file is selected but only
     * collects type/speaker context afterward, once there's something real to attach it to. */
    suspend fun updateRecordingContext(id: String, context: com.example.core.model.RecordingContext) = withContext(Dispatchers.IO) {
        val existing = meetingDao.getMeetingById(id) ?: return@withContext
        meetingDao.updateMeeting(
            existing.copy(
                recordingType = context.recordingType.name,
                customContext = context.customContext,
                speakerCountPreference = context.speakerCountPreference,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteMeeting(id: String) = withContext(Dispatchers.IO) {
        // Delete audio files from app-private storage
        val meetingDir = File(context.filesDir, "meetings/$id")
        if (meetingDir.exists()) {
            meetingDir.deleteRecursively()
        }
        meetingDao.deleteMeetingById(id)
    }

    suspend fun deleteAllMeetings() = withContext(Dispatchers.IO) {
        val meetingsDir = File(context.filesDir, "meetings")
        if (meetingsDir.exists()) {
            meetingsDir.deleteRecursively()
        }
        meetingDao.deleteAllMeetings()
    }

    private fun MeetingEntity.toDomain(): Meeting {
        return Meeting(
            id = id,
            title = title,
            createdAt = createdAt,
            durationMs = durationMs,
            source = try { MeetingSource.valueOf(source) } catch (e: Exception) { MeetingSource.LOCAL_RECORDING },
            audioFilePath = audioFilePath,
            status = try { MeetingStatus.valueOf(status) } catch (e: Exception) { MeetingStatus.READY },
            participantCount = participantCount,
            language = language,
            summaryPreview = summaryText,
            recordingType = try { com.example.core.model.RecordingType.valueOf(recordingType) } catch (e: Exception) { com.example.core.model.RecordingType.GENERAL },
            customContext = customContext,
            speakerCountPreference = speakerCountPreference
        )
    }
}

class TranscriptRepository(private val database: MeetMindDatabase) {
    private val transcriptDao = database.transcriptDao()
    private val speakerDao = database.speakerDao()
    private val decisionDao = database.decisionDao()
    private val questionDao = database.questionDao()
    private val topicDao = database.topicDao()
    private val chatMessageDao = database.chatMessageDao()

    fun getTranscript(meetingId: String): Flow<Transcript> {
        return transcriptDao.getSegmentsForMeeting(meetingId).map { segments ->
            Transcript(
                meetingId = meetingId,
                segments = segments.map {
                    TranscriptSegment(
                        id = it.id,
                        meetingId = it.meetingId,
                        speakerId = it.speakerId,
                        speakerName = it.speakerName,
                        startMs = it.startMs,
                        endMs = it.endMs,
                        text = it.text,
                        confidence = it.confidence,
                        isUserEdited = it.isUserEdited,
                        cleanedText = it.cleanedText,
                        sourceSegmentIds = it.sourceSegmentIdsJson.toIdList()
                    )
                }
            )
        }.flowOn(Dispatchers.IO)
    }

    suspend fun getTranscriptDirect(meetingId: String): Transcript = withContext(Dispatchers.IO) {
        val segments = transcriptDao.getSegmentsForMeetingDirect(meetingId).map {
            TranscriptSegment(
                id = it.id,
                meetingId = it.meetingId,
                speakerId = it.speakerId,
                speakerName = it.speakerName,
                startMs = it.startMs,
                endMs = it.endMs,
                text = it.text,
                confidence = it.confidence,
                isUserEdited = it.isUserEdited
            )
        }
        Transcript(meetingId = meetingId, segments = segments)
    }

    /** Persists a user's hand-correction to one segment's text — the transcript is user-owned
     * data, never immutable AI output. Speaker/timestamp metadata on the segment is untouched. */
    suspend fun updateSegmentText(segmentId: String, newText: String) = withContext(Dispatchers.IO) {
        transcriptDao.updateSegmentText(segmentId, newText)
    }

    /** Persists [com.example.ai.pipeline.TranscriptCleanupEngine]'s output for one segment — a
     * no-op if that segment has since been hand-edited by the user (see [TranscriptDao.updateCleanedText]). */
    suspend fun updateCleanedText(segmentId: String, cleanedText: String?) = withContext(Dispatchers.IO) {
        transcriptDao.updateCleanedText(segmentId, cleanedText)
    }

    private val followUpDao = database.followUpDao()

    fun getSpeakers(meetingId: String): Flow<List<Speaker>> = speakerDao.getSpeakersForMeeting(meetingId).map { list ->
        list.map { Speaker(it.id, it.meetingId, it.speakerIndex, it.originalLabel, it.customName, it.colorHex, it.confidence) }
    }.flowOn(Dispatchers.IO)

    /**
     * Renaming only ever touches [SpeakerEntity.customName] / the denormalized display copy on
     * transcript segments — it must never overwrite [SpeakerEntity.originalLabel] or
     * [SpeakerEntity.speakerIndex]/[SpeakerEntity.confidence], which are the real diarization
     * identity a rename must not disturb.
     */
    suspend fun renameSpeaker(meetingId: String, speakerId: String, newName: String) = withContext(Dispatchers.IO) {
        transcriptDao.updateSpeakerName(meetingId, speakerId, newName)
        val existing = speakerDao.getSpeakersForMeetingDirect(meetingId).find { it.id == speakerId }
        if (existing != null) {
            speakerDao.updateSpeaker(existing.copy(customName = newName))
        }
    }

    /** Splits [segmentId] into two segments at [charOffset] characters into its current text
     * (docs/recording-page-implementation.md §3.2 item 7). The new segment's timestamp is
     * linearly interpolated across the original segment's duration by character offset — a rough
     * estimate, since no word-level timestamps exist to align to instead. Returns the new
     * segment (for the caller to register an undo action) or null if the split isn't possible
     * (offset at an edge, or either half would be blank). */
    suspend fun splitSegment(segmentId: String, charOffset: Int): TranscriptSegment? = withContext(Dispatchers.IO) {
        val original = transcriptDao.getSegmentById(segmentId) ?: return@withContext null
        val text = original.text
        if (charOffset <= 0 || charOffset >= text.length) return@withContext null
        val firstText = text.substring(0, charOffset).trimEnd()
        val secondText = text.substring(charOffset).trimStart()
        if (firstText.isBlank() || secondText.isBlank()) return@withContext null
        val duration = (original.endMs - original.startMs).coerceAtLeast(0)
        val splitMs = original.startMs + duration * charOffset / text.length.coerceAtLeast(1)
        val newId = java.util.UUID.randomUUID().toString()
        val newEntity = original.copy(id = newId, startMs = splitMs, text = secondText, isUserEdited = true, cleanedText = null)
        val updatedOriginal = original.copy(endMs = splitMs, text = firstText, isUserEdited = true, cleanedText = null)
        transcriptDao.insertSegments(listOf(updatedOriginal, newEntity))
        TranscriptSegment(
            id = newId,
            meetingId = newEntity.meetingId,
            speakerId = newEntity.speakerId,
            speakerName = newEntity.speakerName,
            startMs = newEntity.startMs,
            endMs = newEntity.endMs,
            text = newEntity.text,
            confidence = newEntity.confidence,
            isUserEdited = true,
            cleanedText = null,
            sourceSegmentIds = newEntity.sourceSegmentIdsJson.toIdList()
        )
    }

    /** Reverses [splitSegment]: deletes the segment the split created and restores the original
     * segment's pre-split text/endMs. */
    suspend fun undoSplitSegment(originalId: String, originalText: String, originalEndMs: Long, newSegmentId: String) = withContext(Dispatchers.IO) {
        val original = transcriptDao.getSegmentById(originalId) ?: return@withContext
        transcriptDao.insertSegments(listOf(original.copy(text = originalText, endMs = originalEndMs)))
        transcriptDao.deleteSegmentById(newSegmentId)
    }

    /** What [mergeSegmentWithPrevious] changed, kept only so [undoMergeSegment] can reverse it. */
    data class MergeResult(val removed: TranscriptSegment, val keptId: String, val keptTextBefore: String, val keptEndMsBefore: Long)

    /** Merges [segmentId] into the immediately preceding segment in the meeting, only when they
     * share a speaker (docs/recording-page-implementation.md §3.2 item 8: "deleting at the start
     * of a segment merges it into the previous one — same speaker only"). Returns null (a no-op)
     * when there is no eligible previous segment. */
    suspend fun mergeSegmentWithPrevious(meetingId: String, segmentId: String): MergeResult? = withContext(Dispatchers.IO) {
        val all = transcriptDao.getSegmentsForMeetingDirect(meetingId).sortedBy { it.startMs }
        val index = all.indexOfFirst { it.id == segmentId }
        if (index <= 0) return@withContext null
        val current = all[index]
        val previous = all[index - 1]
        if (previous.speakerId != current.speakerId) return@withContext null
        val merged = previous.copy(
            endMs = current.endMs,
            text = (previous.text.trimEnd() + " " + current.text.trimStart()).trim(),
            isUserEdited = true,
            cleanedText = null
        )
        transcriptDao.insertSegments(listOf(merged))
        transcriptDao.deleteSegmentById(current.id)
        MergeResult(
            removed = TranscriptSegment(
                id = current.id,
                meetingId = current.meetingId,
                speakerId = current.speakerId,
                speakerName = current.speakerName,
                startMs = current.startMs,
                endMs = current.endMs,
                text = current.text,
                confidence = current.confidence,
                isUserEdited = current.isUserEdited,
                cleanedText = current.cleanedText,
                sourceSegmentIds = current.sourceSegmentIdsJson.toIdList()
            ),
            keptId = previous.id,
            keptTextBefore = previous.text,
            keptEndMsBefore = previous.endMs
        )
    }

    /** Reverses [mergeSegmentWithPrevious]: restores the removed segment's row and the kept
     * segment's pre-merge text/endMs. */
    suspend fun undoMergeSegment(result: MergeResult) = withContext(Dispatchers.IO) {
        val keptEntity = transcriptDao.getSegmentById(result.keptId) ?: return@withContext
        val removed = result.removed
        transcriptDao.insertSegments(
            listOf(
                keptEntity.copy(text = result.keptTextBefore, endMs = result.keptEndMsBefore),
                TranscriptSegmentEntity(
                    id = removed.id,
                    meetingId = removed.meetingId,
                    speakerId = removed.speakerId,
                    speakerName = removed.speakerName,
                    startMs = removed.startMs,
                    endMs = removed.endMs,
                    text = removed.text,
                    confidence = removed.confidence,
                    isUserEdited = removed.isUserEdited,
                    cleanedText = removed.cleanedText,
                    sourceSegmentIdsJson = removed.sourceSegmentIds.toIdsJson()
                )
            )
        )
    }

    /** Reassigns [segmentIds] to [speakerId]/[speakerName] — either an existing speaker or a
     * brand-new one, in which case a [SpeakerEntity] is created with [newSpeakerColorHex]
     * (docs/recording-page-implementation.md §3.2 item 9). */
    suspend fun reassignSpeaker(
        meetingId: String,
        segmentIds: List<String>,
        speakerId: String,
        speakerName: String,
        newSpeakerColorHex: String
    ) = withContext(Dispatchers.IO) {
        val existingSpeakers = speakerDao.getSpeakersForMeetingDirect(meetingId)
        if (existingSpeakers.none { it.id == speakerId }) {
            speakerDao.insertSpeakers(
                listOf(
                    SpeakerEntity(
                        id = speakerId,
                        meetingId = meetingId,
                        speakerIndex = existingSpeakers.size,
                        originalLabel = speakerName,
                        customName = speakerName,
                        colorHex = newSpeakerColorHex
                    )
                )
            )
        }
        segmentIds.forEach { segId -> transcriptDao.reassignSegmentSpeaker(segId, speakerId, speakerName) }
    }

    /** Raw speaker write with no "create the speaker if missing" behaviour — used to undo
     * [reassignSpeaker] back to whatever the segments carried before, including null (never
     * diarized). */
    suspend fun setSegmentSpeakers(segmentIds: List<String>, speakerId: String?, speakerName: String?) = withContext(Dispatchers.IO) {
        segmentIds.forEach { segId -> transcriptDao.reassignSegmentSpeaker(segId, speakerId, speakerName) }
    }

    fun getDecisions(meetingId: String): Flow<List<Decision>> = decisionDao.getDecisionsForMeeting(meetingId).map { list ->
        list.map {
            Decision(
                id = it.id,
                meetingId = it.meetingId,
                text = it.text,
                type = try { com.example.core.model.DecisionType.valueOf(it.type) } catch (e: Exception) { com.example.core.model.DecisionType.DISCUSSION },
                confidence = it.confidence,
                sourceSegmentIds = it.sourceSegmentIdsJson.toIdList()
            )
        }
    }.flowOn(Dispatchers.IO)

    fun getQuestions(meetingId: String): Flow<List<Question>> = questionDao.getQuestionsForMeeting(meetingId).map { list ->
        list.map {
            Question(
                id = it.id,
                meetingId = it.meetingId,
                text = it.text,
                askedBySpeakerId = it.askedBySpeakerId,
                resolved = it.resolved,
                answer = it.answer,
                sourceSegmentIds = it.sourceSegmentIdsJson.toIdList()
            )
        }
    }.flowOn(Dispatchers.IO)

    fun getFollowUps(meetingId: String): Flow<List<com.example.core.model.FollowUp>> = followUpDao.getFollowUpsForMeeting(meetingId).map { list ->
        list.map {
            com.example.core.model.FollowUp(
                id = it.id,
                meetingId = it.meetingId,
                description = it.description,
                ownerSpeakerId = it.ownerSpeakerId,
                deadline = it.deadline,
                sourceSegmentIds = it.sourceSegmentIdsJson.toIdList()
            )
        }
    }.flowOn(Dispatchers.IO)

    fun getTopics(meetingId: String): Flow<List<Topic>> = topicDao.getTopicsForMeeting(meetingId).map { list ->
        list.map { Topic(it.id, it.meetingId, it.name, it.relevance) }
    }.flowOn(Dispatchers.IO)

    fun getChatMessages(meetingId: String): Flow<List<ChatMessage>> = chatMessageDao.getChatMessagesForMeeting(meetingId).map { list ->
        list.map { entity ->
            val timestamps = entity.sourceTimestampsJson.split(",").mapNotNull { it.trim().toLongOrNull() }
            val quotes = if (entity.sourceQuotesJson.isNotBlank()) entity.sourceQuotesJson.split("|||") else emptyList()
            ChatMessage(
                id = entity.id,
                meetingId = entity.meetingId,
                isUser = entity.isUser,
                content = entity.content,
                timestamp = entity.timestamp,
                sourceTimestamps = timestamps,
                sourceQuotes = quotes
            )
        }
    }.flowOn(Dispatchers.IO)

    suspend fun saveChatMessage(chatMessage: ChatMessage) = withContext(Dispatchers.IO) {
        chatMessageDao.insertMessage(
            ChatMessageEntity(
                id = chatMessage.id,
                meetingId = chatMessage.meetingId,
                isUser = chatMessage.isUser,
                content = chatMessage.content,
                timestamp = chatMessage.timestamp,
                sourceTimestampsJson = chatMessage.sourceTimestamps.joinToString(","),
                sourceQuotesJson = chatMessage.sourceQuotes.joinToString("|||")
            )
        )
    }
}

class ActionItemRepository(private val database: MeetMindDatabase) {
    private val dao = database.actionItemDao()

    fun getActionItemsForMeeting(meetingId: String): Flow<List<ActionItem>> = dao.getActionItemsForMeeting(meetingId).map { list ->
        list.map { it.toDomain() }
    }.flowOn(Dispatchers.IO)

    fun getAllActionItems(): Flow<List<ActionItem>> = dao.getAllActionItems().map { list ->
        list.map { it.toDomain() }
    }.flowOn(Dispatchers.IO)

    suspend fun toggleCompleted(item: ActionItem) = withContext(Dispatchers.IO) {
        dao.updateActionItem(item.copy(isCompleted = !item.isCompleted).toEntity())
    }

    suspend fun updateActionItem(item: ActionItem) = withContext(Dispatchers.IO) {
        dao.updateActionItem(item.toEntity())
    }

    suspend fun addActionItem(item: ActionItem) = withContext(Dispatchers.IO) {
        dao.insertActionItem(item.toEntity())
    }

    suspend fun deleteActionItem(id: String) = withContext(Dispatchers.IO) {
        dao.deleteActionItemById(id)
    }

    private fun ActionItemEntity.toDomain() = ActionItem(
        id = id,
        meetingId = meetingId,
        task = task,
        assigneeSpeakerId = assigneeSpeakerId,
        assigneeName = assigneeName,
        deadline = deadline,
        confidence = confidence,
        isCompleted = isCompleted,
        sourceSegmentIds = sourceSegmentIdsJson.toIdList()
    )

    private fun ActionItem.toEntity() = ActionItemEntity(
        id = id,
        meetingId = meetingId,
        task = task,
        assigneeSpeakerId = assigneeSpeakerId,
        assigneeName = assigneeName,
        deadline = deadline,
        confidence = confidence,
        isCompleted = isCompleted,
        sourceSegmentIdsJson = sourceSegmentIds.toIdsJson()
    )
}

internal fun String.toIdList(): List<String> {
    if (isBlank()) return emptyList()
    return try {
        val array = org.json.JSONArray(this)
        (0 until array.length()).map { array.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }
}

internal fun List<String>.toIdsJson(): String {
    val array = org.json.JSONArray()
    forEach { array.put(it) }
    return array.toString()
}

class SearchRepository(
    private val database: MeetMindDatabase,
    private val embeddingEngine: EmbeddingEngine = LocalEmbeddingEngine()
) {
    private val meetingDao = database.meetingDao()
    private val transcriptDao = database.transcriptDao()
    private val embeddingDao = database.embeddingDao()

    suspend fun searchHybrid(query: String): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val qTrim = query.trim()
        if (qTrim.isEmpty()) return@withContext emptyList()

        val results = mutableListOf<SearchResultItem>()

        // 1. Keyword search over transcripts
        val matchedSegments = transcriptDao.searchTranscriptSegments(qTrim)
        for (seg in matchedSegments) {
            val meeting = meetingDao.getMeetingById(seg.meetingId) ?: continue
            results.add(
                SearchResultItem(
                    meetingId = meeting.id,
                    meetingTitle = meeting.title,
                    meetingDate = meeting.createdAt,
                    matchSnippet = seg.text,
                    timestampMs = seg.startMs,
                    matchType = SearchMatchType.KEYWORD_TRANSCRIPT,
                    relevanceScore = 0.95f,
                    speakerName = seg.speakerName,
                    recordingType = meeting.recordingTypeOrGeneral()
                )
            )
        }

        // 2. Semantic search over embedding vectors
        val queryEmbedding = embeddingEngine.embed(qTrim)
        val allEmbeddings = embeddingDao.getAllEmbeddings()

        for (emb in allEmbeddings) {
            val vecValues = emb.vectorData.split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray()
            if (vecValues.isNotEmpty()) {
                val similarity = embeddingEngine.cosineSimilarity(queryEmbedding, vecValues)
                if (similarity > 0.45f) {
                    val meeting = meetingDao.getMeetingById(emb.meetingId) ?: continue
                    // Avoid duplicate identical snippet
                    if (results.none { it.meetingId == meeting.id && it.matchSnippet == emb.textChunk }) {
                        results.add(
                            SearchResultItem(
                                meetingId = meeting.id,
                                meetingTitle = meeting.title,
                                meetingDate = meeting.createdAt,
                                matchSnippet = emb.textChunk,
                                timestampMs = emb.startMs,
                                matchType = SearchMatchType.SEMANTIC_VECTOR,
                                relevanceScore = similarity,
                                speakerName = emb.segmentId?.let { transcriptDao.getSegmentById(it)?.speakerName },
                                recordingType = meeting.recordingTypeOrGeneral()
                            )
                        )
                    }
                }
            }
        }

        // Rank by relevance score
        results.sortedByDescending { it.relevanceScore }
    }
}

/** Parses [MeetingEntity.recordingType] defensively — same fallback-to-GENERAL pattern as
 * [MeetingRepository.toDomain] — so an unrecognized/corrupt stored value never crashes search. */
private fun MeetingEntity.recordingTypeOrGeneral(): com.example.core.model.RecordingType =
    try {
        com.example.core.model.RecordingType.valueOf(recordingType)
    } catch (e: Exception) {
        com.example.core.model.RecordingType.GENERAL
    }

enum class SearchMatchType {
    KEYWORD_TRANSCRIPT,
    SEMANTIC_VECTOR
}

data class SearchResultItem(
    val meetingId: String,
    val meetingTitle: String,
    val meetingDate: Long,
    val matchSnippet: String,
    val timestampMs: Long,
    val matchType: SearchMatchType,
    val relevanceScore: Float,
    // Null when the matching segment has no diarized speaker — never a fabricated name.
    val speakerName: String? = null,
    val recordingType: com.example.core.model.RecordingType = com.example.core.model.RecordingType.GENERAL
)

/**
 * Real model-management architecture: the catalog of known model candidates
 * ([com.example.ai.modelmanagement.ModelCatalog]) merged with actual on-disk install state
 * ([ModelStorage]) and persisted in Room ([AiModelDao]) — never simulated.
 *
 * No model in the catalog is downloadable today (no production model has been selected/hosted
 * yet — see docs/AI_ARCHITECTURE.md), so [installModel] always returns a real
 * [com.example.ai.common.AiResult.ModelUnavailable] rather than faking a successful install.
 * When a real [ModelDownloader] is wired in later, this is the only class that needs to change.
 */
class ModelRepository(
    private val database: MeetMindDatabase,
    private val modelStorage: ModelStorage,
    // Real, network-capable by default now that ModelCatalog contains real downloadable models
    // (Silero VAD, Parakeet TDT). Pass UnconfiguredModelDownloader() explicitly for any future
    // catalog entry that has no production source yet.
    private val modelDownloader: ModelDownloader = OkHttpModelDownloader(),
    private val modelVerifier: ModelVerifier = Sha256ModelVerifier()
) {
    private val aiModelDao = database.aiModelDao()

    val models: Flow<List<AiModelInfo>> = aiModelDao.getAllModels().map { entities ->
        if (entities.isEmpty()) {
            ModelCatalog.entries
        } else {
            entities.map { it.toDomain() }
        }
    }.flowOn(Dispatchers.IO)

    /** Ensures the catalog has been written to Room at least once, reflecting real on-disk install state. */
    suspend fun ensureCatalogSeeded() = withContext(Dispatchers.IO) {
        val existingIds = aiModelDao.getAllModels().first().map { it.id }.toSet()
        val missing = ModelCatalog.entries.filter { it.id !in existingIds }
        if (missing.isNotEmpty()) {
            aiModelDao.insertModels(missing.map { it.copy(isInstalled = modelStorage.isInstalled(it.id)).toEntity() })
        }
    }

    /**
     * Downloads every file in the model's manifest (streaming, resumable per-file, SHA-256
     * verified), then — only if every file verified successfully — atomically marks the model
     * installed. A failure partway through leaves already-verified sibling files in place so a
     * retry can resume from there instead of re-downloading everything.
     */
    suspend fun installModel(
        modelId: String,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): AiResult<Unit> = withContext(Dispatchers.IO) {
        val manifest = ModelCatalog.entries.find { it.id == modelId }
            ?: return@withContext AiResult.ModelUnavailable(modelId, "Unknown model id: $modelId")

        if (!manifest.isDownloadable) {
            return@withContext AiResult.ModelUnavailable(
                modelId,
                "No downloadable model source is configured yet for \"${manifest.name}\"."
            )
        }

        val targetDir = modelStorage.getModelDirectory(modelId)
        val alreadyPresentBytes = manifest.files.sumOf { spec ->
            File(targetDir, spec.fileName).let { if (it.exists()) it.length().coerceAtMost(spec.sizeBytes) else 0L }
        }
        // Archive-packaged files briefly need room for both the downloaded archive and the
        // extracted target file at once — budget for that peak, not just the final install size.
        val transientArchiveOverheadBytes = manifest.files.sumOf { spec ->
            if (spec.archiveEntryPath != null) ((spec.downloadSizeBytes ?: spec.sizeBytes) - spec.sizeBytes).coerceAtLeast(0L) else 0L
        }
        val remainingBytes = (manifest.sizeBytes + transientArchiveOverheadBytes - alreadyPresentBytes).coerceAtLeast(0L)
        val availableMb = DeviceCapabilityDetector.getAvailableStorageMb()
        val requiredMb = (remainingBytes / (1024 * 1024)) + 1 // round up, small safety margin
        if (availableMb < requiredMb) {
            return@withContext AiResult.InsufficientStorage(requiredMb = requiredMb, availableMb = availableMb)
        }

        var bytesDoneBeforeCurrentFile = alreadyPresentBytes
        for (spec in manifest.files) {
            val finalFile = File(targetDir, spec.fileName)
            if (finalFile.exists() && finalFile.length() == spec.sizeBytes && modelVerifier.verify(finalFile, spec.sha256)) {
                onProgress(bytesDoneBeforeCurrentFile, manifest.sizeBytes)
                continue
            }

            val partFile = File(targetDir, "${spec.fileName}.part")
            if (spec.archiveEntryPath != null) {
                // spec.downloadUrl points at a .tar.bz2 archive; download it to a transient
                // location (sized per downloadSizeBytes, the real transfer size — not
                // spec.sizeBytes, which describes the final extracted file), extract the one
                // entry we need, then discard the archive. Verification below still applies to
                // the extracted file, never to the raw archive bytes.
                val archiveFile = File(targetDir, "${spec.fileName}.archive.part")
                val archiveSpec = spec.copy(sizeBytes = spec.downloadSizeBytes ?: spec.sizeBytes)
                val downloadResult = modelDownloader.download(archiveSpec, archiveFile) { fileBytes, _ ->
                    onProgress(bytesDoneBeforeCurrentFile + fileBytes, manifest.sizeBytes)
                }
                when (downloadResult) {
                    is AiResult.Success -> Unit
                    is AiResult.ModelUnavailable -> return@withContext downloadResult
                    is AiResult.DeviceUnsupported -> return@withContext downloadResult
                    is AiResult.InsufficientMemory -> return@withContext downloadResult
                    is AiResult.InsufficientStorage -> return@withContext downloadResult
                    is AiResult.Failed -> return@withContext downloadResult
                }
                try {
                    com.example.ai.modelmanagement.ArchiveExtractor.extractTarBz2Entry(archiveFile, spec.archiveEntryPath, partFile)
                } catch (e: Exception) {
                    archiveFile.delete()
                    partFile.delete()
                    return@withContext AiResult.Failed("Failed to extract \"${spec.fileName}\" from the downloaded archive: ${e.message}", e)
                }
                archiveFile.delete()
            } else {
                val downloadResult = modelDownloader.download(spec, partFile) { fileBytes, _ ->
                    onProgress(bytesDoneBeforeCurrentFile + fileBytes, manifest.sizeBytes)
                }
                // Preserve the downloader's own failure variant (e.g. ModelUnavailable when no
                // source is configured) instead of collapsing everything into a generic Failed.
                when (downloadResult) {
                    is AiResult.Success -> Unit
                    is AiResult.ModelUnavailable -> return@withContext downloadResult
                    is AiResult.DeviceUnsupported -> return@withContext downloadResult
                    is AiResult.InsufficientMemory -> return@withContext downloadResult
                    is AiResult.InsufficientStorage -> return@withContext downloadResult
                    is AiResult.Failed -> return@withContext downloadResult
                }
            }

            if (!modelVerifier.verify(partFile, spec.sha256)) {
                partFile.delete() // corrupted-download cleanup — never activate an unverified file
                return@withContext AiResult.Failed(
                    "\"${spec.fileName}\" failed SHA-256 verification after download and was discarded."
                )
            }

            if (finalFile.exists()) finalFile.delete()
            if (!partFile.renameTo(finalFile)) {
                partFile.delete()
                return@withContext AiResult.Failed("Failed to install \"${spec.fileName}\" after verification.")
            }
            bytesDoneBeforeCurrentFile += spec.sizeBytes
            onProgress(bytesDoneBeforeCurrentFile, manifest.sizeBytes)
        }

        // Only reachable once every file above is present and verified.
        (modelStorage as? LocalModelStorage)?.markInstalled(modelId)
        aiModelDao.updateModel(manifest.copy(isInstalled = true, isDownloading = false, downloadProgress = 1f).toEntity())
        AiResult.Success(Unit)
    }

    suspend fun deleteModel(modelId: String) = withContext(Dispatchers.IO) {
        modelStorage.delete(modelId)
        val existing = aiModelDao.getModelById(modelId)
        if (existing != null) {
            aiModelDao.updateModel(existing.copy(isInstalled = false, isDownloading = false, downloadProgress = 0f))
        }
    }

    private fun AiModelEntity.toDomain(): AiModelInfo = AiModelInfo(
        id = id,
        name = name,
        capability = capabilities.split(",").filter { it.isNotBlank() }.mapNotNull {
            try { ModelCapability.valueOf(it) } catch (e: Exception) { null }
        }.toSet(),
        files = filesJson.toModelFiles(),
        minimumRamMb = minimumRamMb,
        recommendedRamMb = recommendedRamMb,
        version = version,
        isInstalled = modelStorage.isInstalled(id) || isInstalled,
        isDownloading = isDownloading,
        downloadProgress = downloadProgress,
        description = description,
        parameterCount = parameterCount,
        quantization = quantization,
        contextLengthTokens = contextLengthTokens,
        // Tier is a property of the model's static definition, not a stat that changes per
        // install — resolved from the catalog by id rather than persisted, so it can't drift out
        // of sync with ModelCatalog after a schema/tier change. Falls back to RECOMMENDED for a
        // row whose id the current catalog no longer recognizes.
        tier = ModelCatalog.entries.find { it.id == id }?.tier ?: com.example.core.model.ModelTier.RECOMMENDED
    )

    private fun AiModelInfo.toEntity(): AiModelEntity = AiModelEntity(
        id = id,
        name = name,
        capabilities = capability.joinToString(",") { it.name },
        filesJson = files.toJson(),
        minimumRamMb = minimumRamMb,
        recommendedRamMb = recommendedRamMb,
        version = version,
        isInstalled = isInstalled,
        isDownloading = isDownloading,
        downloadProgress = downloadProgress,
        description = description,
        parameterCount = parameterCount,
        quantization = quantization,
        contextLengthTokens = contextLengthTokens
    )

    private fun List<com.example.core.model.ModelFileSpec>.toJson(): String {
        val array = org.json.JSONArray()
        for (spec in this) {
            array.put(
                org.json.JSONObject().apply {
                    put("fileName", spec.fileName)
                    put("downloadUrl", spec.downloadUrl)
                    put("sha256", spec.sha256)
                    put("sizeBytes", spec.sizeBytes)
                    spec.downloadSizeBytes?.let { put("downloadSizeBytes", it) }
                    spec.archiveEntryPath?.let { put("archiveEntryPath", it) }
                }
            )
        }
        return array.toString()
    }

    private fun String.toModelFiles(): List<com.example.core.model.ModelFileSpec> {
        if (isBlank()) return emptyList()
        return try {
            val array = org.json.JSONArray(this)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                com.example.core.model.ModelFileSpec(
                    fileName = obj.getString("fileName"),
                    downloadUrl = obj.getString("downloadUrl"),
                    sha256 = obj.getString("sha256"),
                    sizeBytes = obj.getLong("sizeBytes"),
                    downloadSizeBytes = if (obj.has("downloadSizeBytes")) obj.getLong("downloadSizeBytes") else null,
                    archiveEntryPath = if (obj.has("archiveEntryPath")) obj.getString("archiveEntryPath") else null
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
