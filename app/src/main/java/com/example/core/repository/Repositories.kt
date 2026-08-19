package com.example.core.repository

import android.content.Context
import com.example.ai.embeddings.EmbeddingEngine
import com.example.ai.embeddings.LocalEmbeddingEngine
import com.example.core.database.ActionItemEntity
import com.example.core.database.ChatMessageEntity
import com.example.core.database.MeetMindDatabase
import com.example.core.database.MeetingEntity
import com.example.core.database.SpeakerEntity
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
        audioFilePath: String? = null
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
            summaryText = null
        )
        meetingDao.insertMeeting(entity)
        entity.toDomain()
    }

    suspend fun updateMeetingTitle(id: String, newTitle: String) = withContext(Dispatchers.IO) {
        val existing = meetingDao.getMeetingById(id) ?: return@withContext
        meetingDao.updateMeeting(existing.copy(title = newTitle, updatedAt = System.currentTimeMillis()))
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
            summaryPreview = summaryText
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
                        confidence = it.confidence
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
                confidence = it.confidence
            )
        }
        Transcript(meetingId = meetingId, segments = segments)
    }

    fun getSpeakers(meetingId: String): Flow<List<Speaker>> = speakerDao.getSpeakersForMeeting(meetingId).map { list ->
        list.map { Speaker(it.id, it.meetingId, it.originalLabel, it.customName, it.colorHex) }
    }.flowOn(Dispatchers.IO)

    suspend fun renameSpeaker(meetingId: String, speakerId: String, newName: String) = withContext(Dispatchers.IO) {
        transcriptDao.updateSpeakerName(meetingId, speakerId, newName)
        speakerDao.updateSpeaker(
            SpeakerEntity(
                id = speakerId,
                meetingId = meetingId,
                originalLabel = speakerId,
                customName = newName,
                colorHex = "#3B82F6"
            )
        )
    }

    fun getDecisions(meetingId: String): Flow<List<Decision>> = decisionDao.getDecisionsForMeeting(meetingId).map { list ->
        list.map { Decision(it.id, it.meetingId, it.text, it.confidence, it.timestampMs) }
    }.flowOn(Dispatchers.IO)

    fun getQuestions(meetingId: String): Flow<List<Question>> = questionDao.getQuestionsForMeeting(meetingId).map { list ->
        list.map { Question(it.id, it.meetingId, it.text, it.resolved, it.answer, it.timestampMs) }
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
        list.map { ActionItem(it.id, it.meetingId, it.task, it.assignee, it.deadline, it.confidence, it.isCompleted, it.sourceTimestampMs) }
    }.flowOn(Dispatchers.IO)

    fun getAllActionItems(): Flow<List<ActionItem>> = dao.getAllActionItems().map { list ->
        list.map { ActionItem(it.id, it.meetingId, it.task, it.assignee, it.deadline, it.confidence, it.isCompleted, it.sourceTimestampMs) }
    }.flowOn(Dispatchers.IO)

    suspend fun toggleCompleted(item: ActionItem) = withContext(Dispatchers.IO) {
        dao.updateActionItem(
            ActionItemEntity(
                id = item.id,
                meetingId = item.meetingId,
                task = item.task,
                assignee = item.assignee,
                deadline = item.deadline,
                confidence = item.confidence,
                isCompleted = !item.isCompleted,
                sourceTimestampMs = item.sourceTimestampMs
            )
        )
    }

    suspend fun updateActionItem(item: ActionItem) = withContext(Dispatchers.IO) {
        dao.updateActionItem(
            ActionItemEntity(
                id = item.id,
                meetingId = item.meetingId,
                task = item.task,
                assignee = item.assignee,
                deadline = item.deadline,
                confidence = item.confidence,
                isCompleted = item.isCompleted,
                sourceTimestampMs = item.sourceTimestampMs
            )
        )
    }

    suspend fun addActionItem(item: ActionItem) = withContext(Dispatchers.IO) {
        dao.insertActionItem(
            ActionItemEntity(
                id = item.id,
                meetingId = item.meetingId,
                task = item.task,
                assignee = item.assignee,
                deadline = item.deadline,
                confidence = item.confidence,
                isCompleted = item.isCompleted,
                sourceTimestampMs = item.sourceTimestampMs
            )
        )
    }

    suspend fun deleteActionItem(id: String) = withContext(Dispatchers.IO) {
        dao.deleteActionItemById(id)
    }
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
                    relevanceScore = 0.95f
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
                                relevanceScore = similarity
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
    val relevanceScore: Float
)

class ModelRepository {
    private val _models = MutableStateFlow<List<AiModelInfo>>(
        listOf(
            AiModelInfo(
                id = "whisper_tiny",
                name = "Whisper Tiny (Mobile Quantized)",
                capability = setOf(ModelCapability.TRANSCRIPTION),
                sizeBytes = 75 * 1024 * 1024L,
                minimumRamMb = 2048,
                recommendedRamMb = 4096,
                downloadUrl = "https://models.meetmind.internal/whisper-tiny-q4.bin",
                version = "1.2.0",
                isInstalled = true,
                description = "Ultra-fast lightweight model optimized for all ARM64 Android devices. Minimal battery consumption.",
                parameterCount = "39M",
                quantization = "q4_0"
            ),
            AiModelInfo(
                id = "whisper_base",
                name = "Whisper Base (High Accuracy)",
                capability = setOf(ModelCapability.TRANSCRIPTION),
                sizeBytes = 142 * 1024 * 1024L,
                minimumRamMb = 4096,
                recommendedRamMb = 6144,
                downloadUrl = "https://models.meetmind.internal/whisper-base-q4.bin",
                version = "1.2.0",
                isInstalled = false,
                description = "Balanced speech recognition model with superior speaker separation and punctuation accuracy.",
                parameterCount = "74M",
                quantization = "q4_0"
            ),
            AiModelInfo(
                id = "whisper_small",
                name = "Whisper Small (Studio Quality)",
                capability = setOf(ModelCapability.TRANSCRIPTION),
                sizeBytes = 466 * 1024 * 1024L,
                minimumRamMb = 6144,
                recommendedRamMb = 8192,
                downloadUrl = "https://models.meetmind.internal/whisper-small-q4.bin",
                version = "1.2.0",
                isInstalled = false,
                description = "High precision multi-accent model for complex technical and multi-speaker conferences.",
                parameterCount = "244M",
                quantization = "q4_0"
            ),
            AiModelInfo(
                id = "mobile_nlp_fast",
                name = "Mobile Intelligence 1B",
                capability = setOf(ModelCapability.SUMMARIZATION),
                sizeBytes = 120 * 1024 * 1024L,
                minimumRamMb = 3072,
                recommendedRamMb = 4096,
                downloadUrl = "https://models.meetmind.internal/nlp-1b-q4.bin",
                version = "1.0.4",
                isInstalled = true,
                description = "On-device language intelligence for fast executive summaries, key decisions, and action item detection.",
                parameterCount = "1.1B",
                quantization = "q4_k_m"
            ),
            AiModelInfo(
                id = "dense_embeddings_64d",
                name = "Semantic Vector Embedding 64D",
                capability = setOf(ModelCapability.EMBEDDINGS),
                sizeBytes = 18 * 1024 * 1024L,
                minimumRamMb = 1024,
                recommendedRamMb = 2048,
                downloadUrl = "https://models.meetmind.internal/embed-64d.bin",
                version = "1.0.0",
                isInstalled = true,
                description = "Local semantic indexer enabling offline vector RAG and conversational query retrieval.",
                parameterCount = "12M",
                quantization = "fp16"
            )
        )
    )

    val models: StateFlow<List<AiModelInfo>> = _models.asStateFlow()

    suspend fun toggleInstall(modelId: String) = withContext(Dispatchers.IO) {
        val current = _models.value.toMutableList()
        val index = current.indexOfFirst { it.id == modelId }
        if (index != -1) {
            val item = current[index]
            if (item.isInstalled) {
                current[index] = item.copy(isInstalled = false, downloadProgress = 0f)
            } else {
                // Simulate download progress
                current[index] = item.copy(isDownloading = true, downloadProgress = 0.3f)
                _models.value = current.toList()
                kotlinx.coroutines.delay(400)
                current[index] = item.copy(isDownloading = true, downloadProgress = 0.75f)
                _models.value = current.toList()
                kotlinx.coroutines.delay(300)
                current[index] = item.copy(isInstalled = true, isDownloading = false, downloadProgress = 1.0f)
            }
            _models.value = current.toList()
        }
    }
}
