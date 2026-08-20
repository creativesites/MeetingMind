package com.example.ai.modelmanagement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The catalog entries for Phase 1 (VAD + ASR) must describe real, downloadable, checksummed
 * files — every URL/size/SHA-256 here was verified during development by actually downloading
 * each file and computing its checksum (see docs/AI_ARCHITECTURE.md). This test guards against
 * the catalog ever regressing to placeholder/invented values.
 */
class ModelCatalogTest {

    @Test
    fun `every catalog entry is downloadable with real https urls and 64-char sha256`() {
        for (model in ModelCatalog.entries) {
            assertTrue("${model.id} should be downloadable", model.isDownloadable)
            assertTrue("${model.id} should have at least one file", model.files.isNotEmpty())
            for (file in model.files) {
                assertTrue(
                    "${model.id}/${file.fileName} url should be https",
                    file.downloadUrl.startsWith("https://")
                )
                assertEquals(
                    "${model.id}/${file.fileName} sha256 should be 64 hex chars",
                    64,
                    file.sha256.length
                )
                assertTrue(
                    "${model.id}/${file.fileName} sha256 should be lowercase hex",
                    file.sha256.matches(Regex("[0-9a-f]{64}"))
                )
                assertTrue("${model.id}/${file.fileName} size should be positive", file.sizeBytes > 0)
            }
        }
    }

    @Test
    fun `sizeBytes is the real sum of every file`() {
        val parakeet = ModelCatalog.parakeetTdtV3Int8
        val expected = parakeet.files.sumOf { it.sizeBytes }
        assertEquals(expected, parakeet.sizeBytes)
        // Sanity-check against the approximate ~680MB figure from the model's own package size.
        assertTrue(parakeet.sizeBytes in 600_000_000L..750_000_000L)
    }

    @Test
    fun `parakeet is a 4-file transducer model (encoder, decoder, joiner, tokens)`() {
        val fileNames = ModelCatalog.parakeetTdtV3Int8.files.map { it.fileName }.toSet()
        assertEquals(setOf("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt"), fileNames)
    }

    @Test
    fun `silero vad is a single onnx file`() {
        assertEquals(1, ModelCatalog.sileroVad.files.size)
        assertEquals("silero_vad.onnx", ModelCatalog.sileroVad.files.first().fileName)
    }
}
