package com.example.feature.meetingdetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.TranscriptAiToolCategory
import com.example.core.model.TranscriptAiToolReadiness
import com.example.core.model.TranscriptAiToolRegistry
import com.example.core.model.TranscriptAiToolType
import com.example.ui.theme.Accent
import com.example.ui.theme.Ink
import com.example.ui.theme.InkMuted
import com.example.ui.theme.LineSoft

/**
 * The "✨ AI tools" bottom sheet (docs/recording-page-implementation.md §2.4). Data comes straight
 * from [TranscriptAiToolRegistry] — the 19-tool taxonomy already existed in this codebase as
 * architecture prep; this is its first real UI. Only [TranscriptAiToolType.CLEAN_TRANSCRIPT] is
 * [TranscriptAiToolReadiness.READY] today, so it's the only row that actually runs something;
 * everything else says honestly what state it's in rather than pretending to work.
 *
 * Scope is fixed to "Whole transcript" this phase — the §2.4 scope control
 * (selection/from-here-on/one-speaker) is a later pass.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiToolsSheet(
    onDismiss: () -> Unit,
    onRunCleanTranscript: () -> Unit,
    onDataAlreadyAvailable: (TranscriptAiToolType) -> Unit = {},
    onNotBuiltYet: (TranscriptAiToolType) -> Unit = {},
    sheetState: SheetState = rememberModalBottomSheetState()
) {
    var expandedCategory by remember { mutableStateOf<TranscriptAiToolCategory?>(null) }
    val groups = remember { TranscriptAiToolRegistry.byCategory() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Color.White
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(text = "AI tools", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp, color = Ink)
                Text(text = "Whole transcript ▾", fontSize = 13.sp, color = Accent)
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                TranscriptAiToolCategory.entries.forEach { category ->
                    val tools = groups[category].orEmpty()
                    val expanded = expandedCategory == category
                    Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
                        HorizontalDivider(thickness = 1.dp, color = LineSoft)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedCategory = if (expanded) null else category }
                                .padding(horizontal = 24.dp, vertical = 17.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = category.displayName(), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                            if (!expanded) {
                                Text(text = "${tools.size}", fontSize = 12.5.sp, color = InkMuted)
                            }
                        }
                        if (expanded) {
                            Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 18.dp)) {
                                tools.forEachIndexed { index, tool ->
                                    ToolRow(
                                        label = tool.label,
                                        showDivider = index != tools.lastIndex,
                                        onClick = {
                                            when (tool.readiness) {
                                                TranscriptAiToolReadiness.READY -> {
                                                    if (tool == TranscriptAiToolType.CLEAN_TRANSCRIPT) onRunCleanTranscript()
                                                }
                                                TranscriptAiToolReadiness.DATA_EXISTS_NEEDS_UI -> onDataAlreadyAvailable(tool)
                                                TranscriptAiToolReadiness.NOT_STARTED -> onNotBuiltYet(tool)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = LineSoft)
            }

            Text(
                text = "Everything here runs on this phone. Longer transcripts are handled in chunks, so a whole-transcript pass takes a minute or two.",
                fontSize = 12.sp,
                lineHeight = 19.sp,
                color = InkMuted,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
        }
    }
}

private fun TranscriptAiToolCategory.displayName(): String = when (this) {
    TranscriptAiToolCategory.TRANSCRIPT -> "Transcript"
    TranscriptAiToolCategory.ANALYSIS -> "Analysis"
    TranscriptAiToolCategory.UTILITIES -> "Utilities"
}
