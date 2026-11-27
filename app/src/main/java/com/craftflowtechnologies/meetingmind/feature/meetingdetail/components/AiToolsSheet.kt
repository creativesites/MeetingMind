package com.craftflowtechnologies.meetingmind.feature.meetingdetail.components

import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.model.TranscriptAiToolCategory
import com.craftflowtechnologies.meetingmind.core.model.TranscriptAiToolRegistry
import com.craftflowtechnologies.meetingmind.core.model.TranscriptAiToolType
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.LineSoft
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/**
 * The "✨ AI tools" bottom sheet (design/recording-page-implementation.md §2.4).
 *
 * The list comes straight from [TranscriptAiToolRegistry], so this file never hardcodes which
 * tools exist. Every row runs: the sheet no longer needs to distinguish a working tool from a
 * placeholder, because there are no placeholders. What backs a given tool — a use case, a database
 * read, or a model — is the worker's business, not this sheet's.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiToolsSheet(
    onDismiss: () -> Unit,
    /** Runs any tool in the registry. Dispatch by tool type happens in one place — the worker —
     * rather than being split between here and there. */
    onRunTool: (TranscriptAiToolType) -> Unit,
    scopeLabel: String = "Whole transcript",
    onChangeScope: (() -> Unit)? = null,
    /** Stated rather than assumed: with Internet mode on, these tools are not running on the
     * phone, and a footer that says they are would be false. */
    footerNote: String = "Everything here runs on this phone. Longer transcripts are handled in chunks, so a whole-transcript pass takes a minute or two.",
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    var expandedCategory by remember { mutableStateOf<TranscriptAiToolCategory?>(null) }
    val groups = remember { TranscriptAiToolRegistry.byCategory() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = SurfaceBase
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .animateContentSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "AI tools", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp, color = Ink)
                Text(
                    text = if (onChangeScope != null) "$scopeLabel ▾" else scopeLabel,
                    fontSize = 13.sp,
                    color = Accent,
                    modifier = if (onChangeScope != null) {
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(role = Role.Button, onClick = onChangeScope)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("ai_tools_scope")
                    } else Modifier
                )
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                TranscriptAiToolCategory.entries.forEach { category ->
                    val tools = groups[category].orEmpty()
                    val expanded = expandedCategory == category
                    Column(modifier = Modifier.fillMaxWidth().background(SurfaceBase)) {
                        HorizontalDivider(thickness = 1.dp, color = LineSoft)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = MMSize.minTouch)
                                .clickable(role = Role.Button) { expandedCategory = if (expanded) null else category }
                                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                                .padding(horizontal = 24.dp, vertical = 15.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = category.displayName(), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!expanded) {
                                    Text(text = "${tools.size}", fontSize = 12.5.sp, color = InkMuted)
                                }
                                Icon(
                                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = null,
                                    tint = InkMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        AnimatedVisibility(visible = expanded) {
                            Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 18.dp)) {
                                tools.forEachIndexed { index, tool ->
                                    ToolRow(
                                        label = tool.label,
                                        showDivider = index != tools.lastIndex,
                                        onClick = { onRunTool(tool) }
                                    )
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = LineSoft)
            }

            Text(
                text = footerNote,
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
