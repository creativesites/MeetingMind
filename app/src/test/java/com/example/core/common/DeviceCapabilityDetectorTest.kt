package com.example.core.common

import com.example.ai.modelmanagement.ModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceCapabilityDetectorTest {

    @Test
    fun `recommends the Lightweight model well below the 1_5B recommendedRamMb threshold`() {
        assertEquals(
            ModelCatalog.qwen25_0_5bInstruct.id,
            DeviceCapabilityDetector.recommendedLlmModelId(totalRamGb = 2.0f)
        )
    }

    @Test
    fun `recommends the Recommended model well above the threshold`() {
        assertEquals(
            ModelCatalog.qwen25_1_5bInstruct.id,
            DeviceCapabilityDetector.recommendedLlmModelId(totalRamGb = 8.0f)
        )
    }

    @Test
    fun `recommends the Recommended model exactly at the threshold`() {
        val thresholdGb = ModelCatalog.qwen25_1_5bInstruct.recommendedRamMb / 1024f
        assertEquals(
            ModelCatalog.qwen25_1_5bInstruct.id,
            DeviceCapabilityDetector.recommendedLlmModelId(totalRamGb = thresholdGb)
        )
    }

    @Test
    fun `recommends the Lightweight model just under the threshold`() {
        val thresholdGb = ModelCatalog.qwen25_1_5bInstruct.recommendedRamMb / 1024f
        assertEquals(
            ModelCatalog.qwen25_0_5bInstruct.id,
            DeviceCapabilityDetector.recommendedLlmModelId(totalRamGb = thresholdGb - 0.1f)
        )
    }
}
