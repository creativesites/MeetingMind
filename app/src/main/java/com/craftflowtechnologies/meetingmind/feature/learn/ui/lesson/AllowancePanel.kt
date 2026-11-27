package com.craftflowtechnologies.meetingmind.feature.learn.ui.lesson

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The calm panel for a used-up managed allowance. It never blocks studying: the lecture's notes, practice and
 * listening still work, and the panel says so. [onSeePlans] is null when this build cannot sell plans; then there
 * is no button, only the reset date.
 */
@Composable
fun AllowanceUsedUpPanel(
    resetsAtMs: Long,
    onSeePlans: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val c = MM.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        Text(ALLOWANCE_TITLE, style = MM.type.body, color = c.ink)
        allowanceResetLine(resetsAtMs)?.let { Text(it, style = MM.type.caption, color = c.inkMuted) }
        onSeePlans?.let { SecondaryButton("See plans", onClick = it) }
    }
}

internal const val ALLOWANCE_TITLE = "This month's cloud allowance is used up. Your lessons and practice still work."

/** "Resets on 1 Dec." when the service said when; null otherwise. */
internal fun allowanceResetLine(resetsAtMs: Long, locale: Locale = Locale.getDefault()): String? {
    if (resetsAtMs <= 0L) return null
    val day = SimpleDateFormat("d MMM", locale).format(Date(resetsAtMs))
    return "Resets on $day."
}
