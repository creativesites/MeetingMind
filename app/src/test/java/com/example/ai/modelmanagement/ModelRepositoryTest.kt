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
 * actual on-disk install state, and installing a model honestly fails (no production model
 * source is configured yet — see docs/AI_ARCHITECTURE.md) instead of faking a successful
 * install with delay() calls.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModelRepositoryTest {

    private lateinit var context: Context
    private lateinit var database: MeetMindDatabase
    private lateinit var repository: ModelRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ModelRepository(database, LocalModelStorage(context))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `catalog seeding reflects the known model candidates with none installed`() = runBlocking {
        repository.ensureCatalogSeeded()
        val models = repository.models.first()

        assertEquals(ModelCatalog.entries.size, models.size)
        assertFalse("No model should be pre-marked installed", models.any { it.isInstalled })
    }

    @Test
    fun `installing a model honestly reports unavailable instead of faking success`() = runBlocking {
        val modelId = ModelCatalog.entries.first().id
        val result = repository.installModel(modelId)

        assertTrue("Install must not silently succeed with no configured model source", result is AiResult.ModelUnavailable)
    }

    @Test
    fun `unknown model id is reported as unavailable, not silently ignored`() = runBlocking {
        val result = repository.installModel("does-not-exist")
        assertTrue(result is AiResult.ModelUnavailable)
    }
}
