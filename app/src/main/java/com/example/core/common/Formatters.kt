package com.example.core.common

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object Formatters {

    fun formatDurationHms(durationMs: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(durationMs)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60

        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    fun formatDurationSummary(durationMs: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
        return when {
            minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
            minutes > 0 -> "$minutes min"
            else -> "$seconds sec"
        }
    }

    fun formatDateRelative(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val oneDayMs = 24 * 60 * 60 * 1000L

        val sdfDate = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val sdfTime = SimpleDateFormat("h:mm a", Locale.getDefault())

        return when {
            diff < oneDayMs && isSameDay(now, timestamp) -> "Today, ${sdfTime.format(Date(timestamp))}"
            diff < 2 * oneDayMs -> "Yesterday, ${sdfTime.format(Date(timestamp))}"
            else -> sdfDate.format(Date(timestamp))
        }
    }

    fun formatDateHeader(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L

        return when {
            isSameDay(now, timestamp) -> "Today"
            isSameDay(now - oneDayMs, timestamp) -> "Yesterday"
            else -> SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
        }
    }

    private fun isSameDay(time1: Long, time2: Long): Boolean {
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        return fmt.format(Date(time1)) == fmt.format(Date(time2))
    }

    fun formatBytes(bytes: Long): String {
        val mb = bytes.toDouble() / (1024 * 1024)
        return if (mb >= 1000) {
            "%.1f GB".format(mb / 1024)
        } else {
            "%.0f MB".format(mb)
        }
    }

    fun getSpeakerColor(speakerIndex: Int): Color {
        val colors = listOf(
            SpeakerColor1,
            SpeakerColor2,
            SpeakerColor3,
            SpeakerColor4,
            SpeakerColor5,
            SpeakerColor6
        )
        return colors[Math.abs(speakerIndex) % colors.size]
    }
}
