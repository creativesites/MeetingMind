package com.example.core.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.MeetMindDatabase
import com.example.core.model.VocabularySource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [VocabularyRepository] is a persisted lookup table, never a fine-tuning mechanism — see the
 * class doc. These tests pin down: repeated corrections strengthen an existing entry rather than
 * duplicating it, a no-op correction writes nothing, and retrieval is real lexical (fuzzy word)
 * matching rather than either an exact-only match or the whole table regardless of relevance.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VocabularyRepositoryTest {

    private lateinit var database: MeetMindDatabase
    private lateinit var repository: VocabularyRepository

    @Before
    fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = VocabularyRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `recording a new correction creates one entry with frequency 1 and full confidence`() = runBlocking {
        repository.recordCorrection("Sherpa Onix", "Sherpa-ONNX", VocabularySource.REPLACE_ALL)

        val entries = repository.getAll().first()
        assertEquals(1, entries.size)
        assertEquals("Sherpa Onix", entries[0].surfaceForm)
        assertEquals("Sherpa-ONNX", entries[0].canonicalForm)
        assertEquals(1, entries[0].frequency)
        assertEquals(1.0f, entries[0].confidence)
        assertEquals(VocabularySource.REPLACE_ALL, entries[0].source)
    }

    @Test
    fun `repeating the same correction increments frequency instead of duplicating the row`() = runBlocking {
        repository.recordCorrection("Sherpa Onix", "Sherpa-ONNX", VocabularySource.REPLACE_ALL)
        repository.recordCorrection("sherpa onix", "Sherpa-ONNX", VocabularySource.REPLACE_ALL)
        repository.recordCorrection("SHERPA ONIX", "Sherpa-ONNX", VocabularySource.REPLACE_ALL)

        val entries = repository.getAll().first()
        assertEquals(1, entries.size)
        assertEquals(3, entries[0].frequency)
    }

    @Test
    fun `a correction that changes nothing is a safe no-op`() = runBlocking {
        repository.recordCorrection("Winston", "Winston", VocabularySource.REPLACE_ALL)
        repository.recordCorrection("", "something", VocabularySource.REPLACE_ALL)
        repository.recordCorrection("something", "", VocabularySource.REPLACE_ALL)

        assertTrue(repository.getAll().first().isEmpty())
    }

    @Test
    fun `findRelevantTerms matches an exact word, case-insensitively`() = runBlocking {
        repository.recordCorrection("Kubernetes", "Kubernetes (K8s)", VocabularySource.REPLACE_ALL)

        val matches = repository.findRelevantTerms("we should deploy this on kubernetes next week")
        assertEquals(1, matches.size)
        assertEquals("Kubernetes (K8s)", matches[0].canonicalForm)
    }

    @Test
    fun `findRelevantTerms fuzzy-matches a near-miss word within tolerance`() = runBlocking {
        repository.recordCorrection("Kubernetes", "Kubernetes (K8s)", VocabularySource.REPLACE_ALL)

        // "Kubernettes" is one character away from "Kubernetes" — within the length-scaled
        // tolerance real ASR near-misses actually fall into.
        val matches = repository.findRelevantTerms("let's deploy on Kubernettes")
        assertEquals(1, matches.size)
    }

    @Test
    fun `findRelevantTerms matches a multi-word surfaceForm as an exact phrase, case-insensitively`() = runBlocking {
        repository.recordCorrection("Sherpa Onix", "Sherpa-ONNX", VocabularySource.REPLACE_ALL)

        val matches = repository.findRelevantTerms("we're using sherpa onix for speech recognition")
        assertEquals(1, matches.size)
        assertEquals("Sherpa-ONNX", matches[0].canonicalForm)
    }

    @Test
    fun `findRelevantTerms does not match a multi-word surfaceForm on partial word overlap`() = runBlocking {
        repository.recordCorrection("Sherpa Onix", "Sherpa-ONNX", VocabularySource.REPLACE_ALL)

        // "Sherpa" alone appears, but the full two-word phrase "Sherpa Onix" does not — a partial
        // overlap must not count as relevant, or the tool would fire on any mention of "Sherpa."
        val matches = repository.findRelevantTerms("our Sherpa guide led the trek")
        assertTrue(matches.isEmpty())
    }

    @Test
    fun `findRelevantTerms does not match unrelated text`() = runBlocking {
        repository.recordCorrection("Kubernetes", "Kubernetes (K8s)", VocabularySource.REPLACE_ALL)

        val matches = repository.findRelevantTerms("let's grab lunch at noon")
        assertTrue(matches.isEmpty())
    }

    @Test
    fun `findRelevantTerms never matches on a short, easily-false-positive term without an exact hit`() = runBlocking {
        repository.recordCorrection("Kim", "Kimberly Chen", VocabularySource.REPLACE_ALL)

        // "Tim" is one edit away from "Kim" but short terms require an exact match — otherwise
        // almost any 3-letter word in a transcript would false-positive against a short name.
        val matches = repository.findRelevantTerms("Tim said he'd send the file")
        assertTrue(matches.isEmpty())
    }

    @Test
    fun `findRelevantTerms with no vocabulary entries returns empty without querying anything`() = runBlocking {
        val matches = repository.findRelevantTerms("any text at all")
        assertTrue(matches.isEmpty())
    }
}
