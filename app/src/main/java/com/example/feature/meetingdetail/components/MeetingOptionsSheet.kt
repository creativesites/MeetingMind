package com.example.feature.meetingdetail.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Accent
import com.example.ui.theme.Ink
import com.example.ui.theme.InkFaint
import com.example.ui.theme.InkMuted
import com.example.ui.theme.LineSoft

/**
 * The recording-detail "⋮" options menu (Phase 15 §9) — was a bare [androidx.compose.material3.DropdownMenu]
 * (a small anchored popup) with 8 rows and no visual relationship to the rest of this screen's
 * redesign; converted to a [ModalBottomSheet] using the same shape/containerColor/token palette
 * as [AiToolsSheet], the other sheet already on this screen, so the two read as one design
 * language instead of "sheet" and "generic Android menu" side by side.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingOptionsSheet(
    onDismiss: () -> Unit,
    onEditTitle: () -> Unit,
    onReCleanTranscript: () -> Unit,
    reCleanEnabled: Boolean,
    onCopyMarkdownSummary: () -> Unit,
    onShareSummary: () -> Unit,
    onShareTranscript: () -> Unit,
    onShareActionItems: () -> Unit,
    onShareAudio: () -> Unit,
    shareAudioEnabled: Boolean,
    onExport: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState()
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Color.White
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Text(
                text = "Options",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.4).sp,
                color = Ink,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 10.dp)
            )

            OptionRow(icon = Icons.Default.Edit, label = "Edit Title", onClick = { onDismiss(); onEditTitle() })
            OptionRow(
                icon = Icons.Default.AutoAwesome,
                label = "Re-clean Transcript",
                enabled = reCleanEnabled,
                onClick = { onDismiss(); onReCleanTranscript() }
            )
            OptionRow(icon = Icons.Default.ContentCopy, label = "Copy Markdown Summary", onClick = { onDismiss(); onCopyMarkdownSummary() })

            HorizontalDivider(color = LineSoft)

            OptionRow(icon = Icons.Default.Share, label = "Share Summary", onClick = { onDismiss(); onShareSummary() })
            OptionRow(icon = Icons.Default.Share, label = "Share Transcript", onClick = { onDismiss(); onShareTranscript() })
            OptionRow(icon = Icons.Default.Share, label = "Share Action Items", onClick = { onDismiss(); onShareActionItems() })
            OptionRow(
                icon = Icons.Default.Share,
                label = "Share Audio",
                enabled = shareAudioEnabled,
                onClick = { onDismiss(); onShareAudio() }
            )

            HorizontalDivider(color = LineSoft)

            OptionRow(icon = Icons.Default.Download, label = "Export…", onClick = { onDismiss(); onExport() })
        }
    }
}

@Composable
private fun OptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) InkMuted else InkFaint,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            fontSize = 15.sp,
            color = if (enabled) Ink else InkFaint,
            modifier = Modifier.padding(start = 18.dp)
        )
    }
}
