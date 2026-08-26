package com.example.feature.meetingdetail.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Ink
import com.example.ui.theme.LineFaint

/**
 * One row inside an AI-tools group (docs/recording-page-implementation.md §2.4): no icons, no
 * descriptions, no chevrons — just a label, generous touch target, and a hairline divider.
 */
@Composable
fun ToolRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 11.dp)
        ) {
            Text(text = label, fontSize = 15.5.sp, color = Ink)
        }
        if (showDivider) {
            HorizontalDivider(thickness = 1.dp, color = LineFaint)
        }
    }
}
