package com.example.core.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.embeddings.LocalEmbeddingEngine
import com.example.core.database.EmbeddingEntity
import com.example.core.database.MeetMindDatabase
import com.example.core.database.MeetingEntity
import com.example.core.database.TranscriptSegmentEntity
import com.example.core.model.RecordingType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A search result must show WHY it matched: the real transcript excerpt (never fabricated), the
 * real timestamp within the recording, the real speaker when diarization identified one, and the
 * recording's actual type — never a hardcoded "meeting" assumption, and never an "Open Meeting"
 * label on a Voice Memo or Interview.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchRepositoryTest {

    private lateinit var database: MeetMindDatabase
    private lateinit var repository: SearchRepository
    private val embeddingEngine = LocalEmbeddingEngine()

    @Before
    fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = SearchRepository(database, embeddingEngine)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun seedMeeting(id: String, title: String, recordingType: RecordingType) = runBlocking {
        database.meetingDao().insertMeeting(
            MeetingEntity(
                id = id,
                title = title,
                createdAt = 1_700_000_000_000L,
                durationMs = 600_000L,
                source = "LOCAL_RECORDING",
                audioFilePath = null,
                status = "READY",
                participantCount = 2,
                language = "en",
                summaryText = null,
                recordingType = recordingType.name
            )
        )
    }

    @Test
    fun `a keyword match returns the real transcript text as the snippet, not a fabricated summary`() = runBlocking {
        seedMeeting("m1", "Quarterly Planning", RecordingType.MEETING)
        database.transcriptDao().insertSegments(
            listOf(
                TranscriptSegmentEntity(
                    id = "s1",
                    meetingId = "m1",
                    speakerId = "spk_m1_1",
                    speakerName = "Speaker 2",
                    startMs = 134_000L,
                    endMs = 137_000L,
                    text = "we agreed to increase the marketing budget to K50,000",
                    confidence = 0.9f
                )
            )
        )

        val results = repository.searchHybrid("marketing budget")

        assertEquals(1, results.size)
        val result = results[0]
        assertEquals("we agreed to increase the marketing budget to K50,000", result.matchSnippet)
        assertEquals(134_000L, result.timestampMs)
        assertEquals("Speaker 2", result.speakerName)
        assertEquals(RecordingType.MEETING, result.recordingType)
        assertEquals(SearchMatchType.KEYWORD_TRANSCRIPT, result.matchType)
    }

    @Test
    fun `a keyword match on a segment with no diarized speaker leaves speakerName null, never fabricated`() = runBlocking {
        seedMeeting("m1", "Personal Note", RecordingType.VOICE_MEMO)
        database.transcriptDao().insertSegments(
            listOf(
                TranscriptSegmentEntity(
                    id = "s1",
                    meetingId = "m1",
                    speakerId = null,
                    speakerName = null,
                    startMs = 5_000L,
                    endMs = 8_000L,
                    text = "remember to buy groceries",
                    confidence = 0.7f
                )
            )
        )

        val results = repository.searchHybrid("groceries")

        assertEquals(1, results.size)
        assertNull(results[0].speakerName)
        assertEquals(RecordingType.VOICE_MEMO, results[0].recordingType)
    }

    @Test
    fun `an unrecognized stored recordingType falls back to GENERAL rather than crashing`() = runBlocking {
        seedMeeting("m1", "Old Recording", RecordingType.GENERAL)
        // Simulate a stale/corrupt stored value directly, bypassing the enum-typed seed helper.
        val meeting = database.meetingDao().getMeetingById("m1")!!
        database.meetingDao().updateMeeting(meeting.copy(recordingType = "NOT_A_REAL_TYPE"))
        database.transcriptDao().insertSegments(
            listOf(
                TranscriptSegmentEntity(
                    id = "s1",
                    meetingId = "m1",
                    speakerId = null,
                    speakerName = null,
                    startMs = 0L,
                    endMs = 1000L,
                    text = "hello world",
                    confidence = null
                )
            )
        )

        val results = repository.searchHybrid("hello")

        assertEquals(RecordingType.GENERAL, results[0].recordingType)
    }

    @Test
    fun `a semantic match returns the real embedded transcript chunk and its own timestamp`() = runBlocking {
        seedMeeting("m1", "Design Review", RecordingType.MEETING)
        database.transcriptDao().insertSegments(
            listOf(
                TranscriptSegmentEntity(
                    id = "s1",
                    meetingId = "m1",
                    speakerId = "spk_m1_0",
                    speakerName = "Speaker 1",
                    startMs = 42_000L,
                    endMs = 46_000L,
                    text = "the new layout improves accessibility significantly",
                    confidence = 0.85f
                )
            )
        )
        val chunkText = "the new layout improves accessibility significantly"
        // A query with no literal substring overlap with chunkText — this guarantees the keyword
        // path (plain LIKE '%query%') finds nothing, so any result can only have come from real
        // vector similarity, and its vector is the query's own embedding so similarity is exactly 1.0.
        val query = "does the redesign help screen reader users"
        val vector = embeddingEngine.embed(query)
        database.embeddingDao().insertEmbeddings(
            listOf(
                EmbeddingEntity(
                    id = "e1",
                    meetingId = "m1",
                    segmentId = "s1",
                    textChunk = chunkText,
                    vectorData = vector.joinToString(","),
                    startMs = 42_000L,
                    endMs = 46_000L
                )
            )
        )

        val results = repository.searchHybrid(query)

        val semanticResult = results.first { it.matchType == SearchMatchType.SEMANTIC_VECTOR }
        assertEquals(chunkText, semanticResult.matchSnippet)
        assertEquals(42_000L, semanticResult.timestampMs)
        assertEquals("Speaker 1", semanticResult.speakerName)
        assertTrue("semantic match must score highly for its own exact text", semanticResult.relevanceScore > 0.99f)
    }

    @Test
    fun `no results for a query that matches nothing`() = runBlocking {
        seedMeeting("m1", "Standup", RecordingType.MEETING)
        database.transcriptDao().insertSegments(
            listOf(
                TranscriptSegmentEntity(
                    id = "s1",
                    meetingId = "m1",
                    speakerId = null,
                    speakerName = null,
                    startMs = 0L,
                    endMs = 1000L,
                    text = "the sprint is on track",
                    confidence = null
                )
            )
        )

        val results = repository.searchHybrid("zzz_completely_unrelated_query_xyz")

        assertTrue(results.isEmpty())
    }

    @Test
    fun `a blank query returns no results without touching the database`() = runBlocking {
        val results = repository.searchHybrid("   ")
        assertTrue(results.isEmpty())
    }
}
