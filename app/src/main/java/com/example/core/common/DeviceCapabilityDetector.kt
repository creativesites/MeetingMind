package com.example.core.common

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
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

        // Recommended Models based on hardware profile
        val recommendedAsr = when {
            totalRamGb >= 7.5f -> "whisper_base"
            else -> "whisper_tiny"
        }

        val recommendedLlm = when {
            totalRamGb >= 7.5f -> "mobile_nlp_reasoning"
            else -> "mobile_nlp_fast"
        }

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

    fun getAvailableStorageMb(): Long {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024)
        } catch (e: Exception) {
            1024L
        }
    }
}
