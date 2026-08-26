package com.example.feature.meetingdetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Ink
import com.example.ui.theme.InkMuted
import com.example.ui.theme.LineSoft

enum class RecordingDetailTab(val label: String) {
    OVERVIEW("Overview"),
    TRANSCRIPT("Transcript"),
    ASK_AI("Ask AI")
}

/**
 * The three-tab bar pinned to the bottom of every recording-detail screen state
 * (docs/recording-page-implementation.md §2.1). Replaces the old scrollable Material tab row
 * whose contents varied by recording type — this bar is always exactly Overview/Transcript/Ask AI.
 */
@Composable
fun BottomTabBar(
    current: RecordingDetailTab,
    onSelect: (RecordingDetailTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().background(Color.White)) {
        HorizontalDivider(thickness = 1.dp, color = LineSoft)
        Row(Modifier.fillMaxWidth()) {
            RecordingDetailTab.entries.forEach { tab ->
                val active = tab == current
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(tab) }
                        .then(
                            if (active) {
                                Modifier.drawBehind {
                                    val strokeWidth = 2.dp.toPx()
                                    drawLine(
                                        color = Ink,
                                        start = Offset(0f, size.height - strokeWidth / 2f),
                                        end = Offset(size.width, size.height - strokeWidth / 2f),
                                        strokeWidth = strokeWidth
                                    )
                                }
                            } else Modifier
                        )
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab.label,
                        fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) Ink else InkMuted
                    )
                }
            }
        }
    }
}
