package com.example.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Ink
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.Line
import com.example.ui.theme.SurfaceSunk

enum class CreateAction { RECORD, NOTE, MEDIA, IMPORT }

/**
 * What the bar's New action opens. Record comes first and largest: it is still the thing people
 * reach for most, and it must stay one tap from the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSheet(onPick: (CreateAction) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding()) {
            Text("Create", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink, letterSpacing = (-0.3).sp)
            Spacer(Modifier.height(14.dp))
            Surface(onClick = { onPick(CreateAction.RECORD) }, shape = RoundedCornerShape(20.dp), color = Ink, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.14f), modifier = Modifier.size(46.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp)) }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Record", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text("A meeting, sermon, lecture or thought", fontSize = 13.sp, color = Color.White.copy(alpha = 0.72f))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CreateTile("Write a note", Icons.Filled.EditNote, Modifier.weight(1f)) { onPick(CreateAction.NOTE) }
                CreateTile("Photos & video", Icons.Filled.PhotoLibrary, Modifier.weight(1f)) { onPick(CreateAction.MEDIA) }
                CreateTile("Import audio", Icons.Filled.FileUpload, Modifier.weight(1f)) { onPick(CreateAction.IMPORT) }
            }
            Text(
                "Every recording gets its own note, so what you write and what was said stay together.",
                fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 14.dp, start = 2.dp)
            )
        }
    }
}

@Composable
private fun CreateTile(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = SurfaceSunk, border = BorderStroke(1.dp, Line), modifier = modifier) {
        Column(Modifier.padding(vertical = 16.dp, horizontal = 12.dp)) {
            Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(10.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = InkSecondary)
        }
    }
}
