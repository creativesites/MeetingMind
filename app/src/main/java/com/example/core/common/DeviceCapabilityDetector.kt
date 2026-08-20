package com.example.core.common

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.example.ai.modelmanagement.ModelCatalog
import com.example.core.model.DeviceCapabilities

object DeviceCapabilityDetector {

    fun detect(context: Context): DeviceCapabilities {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)

        val totalRamBytes = if (memoryInfo.totalMem > 0) memoryInfo.totalMem else Runtime.getRuntime().maxMemory()
        val availRamBytes = if (memoryInfo.availMem > 0) memoryInfo.availMem else Runtime.getRuntime().freeMemory()
        val totalRamGb = (totalRamBytes.toDouble() / (1024 * 1024 * 1024)).toFloat().coerceAtLeast(1.0f)
        val availRamGb = (availRamBytes.toDouble() / (1024 * 1024 * 1024)).toFloat().coerceAtLeast(0.5f)

        val cpuArch = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        val isArm64 = Build.SUPPORTED_64_BIT_ABIS.any { it.contains("arm64") || it.contains("aarch64") } ||
                Build.SUPPORTED_ABIS.any { it.contains("arm64") || it.contains("aarch64") } ||
                cpuArch.contains("arm64") || cpuArch.contains("aarch64") || cpuArch.contains("x86_64")

        // Performance Tier
        val tier = when {
            totalRamGb >= 8.0f -> "High Performance"
            totalRamGb >= 5.5f -> "Standard Mobile"
            else -> "Entry Level / Battery Saver"
        }

        // Only one real on-device ASR model exists (see ModelCatalog) — nothing to choose between.
        val recommendedAsr = ModelCatalog.parakeetTdtV3Int8.id
        val recommendedLlm = recommendedLlmModelId(totalRamGb)

        return DeviceCapabilities(
            totalRamGb = "%.1f".format(totalRamGb).toFloat(),
            availableRamGb = "%.1f".format(availRamGb).toFloat(),
            cpuArch = cpuArch,
            isArm64 = isArm64,
            recommendedAsrModelId = recommendedAsr,
            recommendedLlmModelId = recommendedLlm,
            devicePerformanceTier = tier
        )
    }

    /**
     * Two real Meeting Intelligence models exist as of Phase 3C: Recommended (1.5B, needs
     * [ModelCatalog.qwen25_1_5bInstruct]'s recommendedRamMb) and Lightweight (0.5B, for devices
     * under that bar). This is only ever a starting suggestion the user can override in Model
     * Manager — it never auto-downloads anything. Pulled out as a pure function (no Context) so
     * the threshold logic itself is directly unit-testable without Robolectric.
     */
    fun recommendedLlmModelId(totalRamGb: Float): String {
        val totalRamMb = totalRamGb * 1024f
        return if (totalRamMb < ModelCatalog.qwen25_1_5bInstruct.recommendedRamMb) {
            ModelCatalog.qwen25_0_5bInstruct.id
        } else {
            ModelCatalog.qwen25_1_5bInstruct.id
        }
    }

    fun getAvailableStorageMb(): Long {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024)
        } catch (e: Exception) {
            1024L
        }
    }
}
