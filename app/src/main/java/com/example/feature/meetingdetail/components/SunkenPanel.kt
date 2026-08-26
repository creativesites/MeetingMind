package com.example.feature.meetingdetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.theme.SurfaceSunk

/**
 * The recurring "inline panel" surface used throughout the redesign — the word editor
 * (docs/recording-page-implementation.md §2.6), the cleanup pass, the "on this selection" tools
 * panel (§2.5), citation detail (§2.8), diff summary (§2.7). Always [SurfaceSunk] on a 20dp radius,
 * never a dialog, never elevated.
 */
@Composable
fun SunkenPanel(
    modifier: Modifier = Modifier,
    padding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(16.dp),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceSunk, RoundedCornerShape(20.dp))
            .padding(padding)
    ) {
        content()
    }
}
