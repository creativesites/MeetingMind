package com.example.feature.meetingdetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Ink
import com.example.ui.theme.LineSoft

/**
 * One entry on the decision timeline (docs/recording-page-implementation.md §2.2, "step 2, the
 * decision timeline"): a 9dp Ink dot on a 1.5dp [LineSoft] vertical spine, 18dp gutter. Pass
 * [isLast] = true to omit the continuing line below the final entry.
 */
@Composable
fun TimelineSpineRow(
    isLast: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Row(modifier = modifier.height(IntrinsicSize.Min)) {
        Column(
            modifier = Modifier.width(9.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(9.dp)
                    .background(Ink, CircleShape)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .width(1.5.dp)
                        .background(LineSoft)
                )
            }
        }
        Box(modifier = Modifier.padding(start = 18.dp).fillMaxHeight()) {
            content()
        }
    }
}
