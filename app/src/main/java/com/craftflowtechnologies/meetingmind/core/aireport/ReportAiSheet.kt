package com.craftflowtechnologies.meetingmind.core.aireport

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/** The privacy line every report sheet shows. A test keeps it in step with [AiReport.KEYS]. */
const val REPORT_PRIVACY_LINE =
    "We'll send only the passage you're reporting, your reason and comment, which feature wrote it, " +
        "the AI model and the app version. Nothing else from your notes or recordings."

/** Which report is open. Keep it outside a menu, so the sheet survives the menu closing. */
@Stable
class AiReportState {
    var source by mutableStateOf<AiReportSource?>(null)
        private set
    internal var onHide: (() -> Unit)? = null
        private set

    fun open(source: AiReportSource, onHide: (() -> Unit)? = null) {
        this.onHide = onHide
        this.source = source
    }

    fun close() {
        source = null
        onHide = null
    }
}

@Composable
fun rememberAiReportState(): AiReportState = remember { AiReportState() }

/** Shows the report sheet while [state] has one open. Put it once per screen. */
@Composable
fun AiReportSheetHost(state: AiReportState) {
    val source = state.source ?: return
    ReportAiSheet(source = source, onDismiss = state::close, onHide = state.onHide)
}

/** The small flag beside an AI answer's Copy. 48 dp target. */
@Composable
fun ReportAiButton(onClick: () -> Unit, modifier: Modifier = Modifier, tint: androidx.compose.ui.graphics.Color = MM.colors.inkMuted) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .sizeIn(minWidth = MMSize.minTouch, minHeight = MMSize.minTouch)
            .semantics { contentDescription = "Report this AI content" }
    ) {
        Icon(Icons.Outlined.Flag, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
    }
}

/** The flag and its own sheet, for a screen that only needs one report action. [source] is read when tapped. */
@Composable
fun ReportAiAction(source: () -> AiReportSource, modifier: Modifier = Modifier, onHide: (() -> Unit)? = null) {
    val state = rememberAiReportState()
    ReportAiButton(onClick = { state.open(source(), onHide) }, modifier = modifier)
    AiReportSheetHost(state)
}

/** What a reported answer or block becomes when the person chose to hide it. */
@Composable
fun HiddenAfterReportLine(onShow: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = MMSize.minTouch), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Flag, contentDescription = null, tint = MM.colors.inkFaint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(MM.space.s))
        Text("Hidden after you reported it", style = MM.type.caption, color = MM.colors.inkMuted, modifier = Modifier.weight(1f))
        TextAction("Show", onShow)
    }
}

/** "Report this" in an existing overflow menu. Close the menu in [onClick] and open an [AiReportState]. */
@Composable
fun ReportAiMenuItem(onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text("Report this", style = MM.type.body, color = MM.colors.ink) },
        leadingIcon = { Icon(Icons.Outlined.Flag, contentDescription = null, tint = MM.colors.inkSecondary) },
        onClick = onClick
    )
}

/**
 * Reasons, an optional comment, an optional "hide", and Send, or Copy when the report must stay on
 * the phone. It closes at once; sending happens in the background.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportAiSheet(source: AiReportSource, onDismiss: () -> Unit, onHide: (() -> Unit)? = null) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val c = MM.colors
    var prepared by remember(source) { mutableStateOf<PreparedReport?>(null) }
    var reason by remember(source) { mutableStateOf<AiReportReason?>(null) }
    var comment by remember(source) { mutableStateOf("") }
    var hide by remember(source) { mutableStateOf(false) }
    LaunchedEffect(source) { prepared = AiReports.prepare(context, source) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceRaised,
        shape = MM.radius.sheet
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = MM.space.l).padding(bottom = MM.space.xl),
            verticalArrangement = Arrangement.spacedBy(MM.space.m)
        ) {
            Text("Report this", style = MM.type.title, color = c.ink)
            Text("Tell us what's wrong with what the AI wrote.", style = MM.type.secondary, color = c.inkSecondary)

            Column(Modifier.selectableGroup()) {
                AiReportReason.entries.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch)
                            .selectable(selected = reason == r, role = Role.RadioButton, onClick = { reason = r }),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = reason == r, onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = c.accent, unselectedColor = c.inkMuted)
                        )
                        Spacer(Modifier.width(MM.space.s))
                        Text(r.label, style = MM.type.body, color = c.ink)
                    }
                }
            }

            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it.take(AiReport.MAX_COMMENT) },
                label = { Text("Anything to add? (optional)") },
                minLines = 2, maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.accent, unfocusedBorderColor = c.line, cursorColor = c.accent,
                    focusedTextColor = c.ink, unfocusedTextColor = c.ink,
                    focusedLabelColor = c.accent, unfocusedLabelColor = c.inkSecondary
                )
            )

            if (onHide != null) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch)
                        .toggleable(value = hide, role = Role.Switch, onValueChange = { hide = it }),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Hide this from now on", style = MM.type.body, color = c.ink, modifier = Modifier.weight(1f))
                    Switch(
                        checked = hide, onCheckedChange = null,
                        colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = c.onAccent)
                    )
                }
            }

            val delivery = prepared?.delivery
            if (delivery is ReportDelivery.CopyOnly) {
                StatusLine(
                    StatusKind.Info,
                    "This stays on your phone because ${delivery.why}. Copy the report and send it to us when you choose."
                )
            }
            Text(REPORT_PRIVACY_LINE, style = MM.type.caption, color = c.inkMuted)

            val ready = prepared != null && reason != null
            PrimaryButton(
                text = if (delivery is ReportDelivery.CopyOnly) "Copy report" else "Send report",
                enabled = ready,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val p = prepared ?: return@PrimaryButton
                    val r = reason ?: return@PrimaryButton
                    val report = AiReports.build(source, r, comment, p)
                    if (p.delivery is ReportDelivery.CopyOnly) {
                        clipboard.setText(AnnotatedString(report.copyText(AiReports.SUPPORT_EMAIL)))
                        Toast.makeText(context, "Report copied", Toast.LENGTH_SHORT).show()
                    } else {
                        AiReports.submitInBackground(context, report)
                        Toast.makeText(context, "Thanks, we'll look into it.", Toast.LENGTH_SHORT).show()
                    }
                    if (hide) onHide?.invoke()
                    onDismiss()
                }
            )
        }
    }
}
