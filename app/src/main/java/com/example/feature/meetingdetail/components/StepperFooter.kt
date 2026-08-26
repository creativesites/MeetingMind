package com.example.feature.meetingdetail.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Ink
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.Line

/**
 * The Overview stepper's footer: 52dp prev/next circles with dot indicators between them
 * (docs/recording-page-implementation.md §2.2). The active dot animates its width over 200ms
 * rather than snapping.
 */
@Composable
fun StepperFooter(
    stepCount: Int,
    currentStep: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        StepperCircle(
            onClick = onPrev,
            enabled = currentStep > 0,
            filled = false
        ) {
            Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Previous", tint = InkSecondary)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(stepCount) { index ->
                val active = index == currentStep
                val width by animateDpAsState(
                    targetValue = if (active) 22.dp else 7.dp,
                    animationSpec = tween(200),
                    label = "stepDotWidth"
                )
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .height(7.dp)
                        .width(width)
                        .background(if (active) Ink else Line, RoundedCornerShape(4.dp))
                )
            }
        }

        StepperCircle(
            onClick = onNext,
            enabled = currentStep < stepCount - 1,
            filled = true
        ) {
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Next", tint = Color.White)
        }
    }
}

@Composable
private fun StepperCircle(
    onClick: () -> Unit,
    enabled: Boolean,
    filled: Boolean,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(52.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .then(
                if (filled) Modifier.background(Ink, CircleShape)
                else Modifier
                    .background(Color.White, CircleShape)
                    .border(1.dp, Line, CircleShape)
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
