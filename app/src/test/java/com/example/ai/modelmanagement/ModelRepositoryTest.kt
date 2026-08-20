package com.example.ai.modelmanagement

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.common.AiResult
import com.example.core.database.MeetMindDatabase
import com.example.core.repository.ModelRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the real (non-simulated) model-management architecture: catalog seeding reflects
 * actual on-disk install state, and — for a model whose downloader isn't configured — install
 * honestly fails instead of faking success with delay() calls. Tests here never hit the live
 * internet: [OkHttpModelDownloader] network behavior is covered separately by
 * [OkHttpModelDownloaderTest] against a local MockWebServer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModelRepositoryTest {

    private lateinit var context: Context
    private lateinit var database: MeetMindDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `catalog seeding reflects the known model candidates with none installed`() = runBlocking {
        // No network involved in seeding, so the real default downloader is fine here.
        val repository = ModelRepository(database, LocalModelStorage(context))
        repository.ensureCatalogSeeded()
        val models = repository.models.first()

        assertEquals(ModelCatalog.entries.size, models.size)
        assertFalse("No model should be pre-marked installed", models.any { it.isInstalled })
    }

    @Test
    fun `installing with no downloader configured honestly reports failure, not fake success`() = runBlocking {
        val repository = ModelRepository(
            database = database,
            modelStorage = LocalModelStorage(context),
            modelDownloader = UnconfiguredModelDownloader()
        )
        val modelId = ModelCatalog.entries.first().id

        val result = repository.installModel(modelId)

        // Either honest failure is acceptable here (ModelUnavailable from the downloader, or
        // InsufficientStorage if the test environment's simulated free space is exhausted) —
        // what matters is that it is never AiResult.Success.
        assertFalse("Install must not silently succeed with no configured downloader", result is AiResult.Success)
        assertTrue(
            "Expected an honest failure variant, got $result",
            result is AiResult.ModelUnavailable || result is AiResult.InsufficientStorage
        )
    }

    @Test
    fun `unknown model id is reported as unavailable, not silently ignored`() = runBlocking {
        val repository = ModelRepository(database, LocalModelStorage(context))
        val result = repository.installModel("does-not-exist")
        assertTrue(result is AiResult.ModelUnavailable)
    }
}
