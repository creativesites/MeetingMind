package com.example.core.identity

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.NotebookSpace

private data class SpaceTile(val space: NotebookSpace, val title: String, val line: String, val icon: ImageVector, val tint: Color)

private val tiles = listOf(
    SpaceTile(NotebookSpace.WORK, "Work", "Meetings, interviews, decisions", Icons.Filled.Work, Color(0xFF4F46E5)),
    SpaceTile(NotebookSpace.LEARNING, "Learning", "Lectures, classes, research", Icons.Filled.School, Color(0xFF0891B2)),
    SpaceTile(NotebookSpace.FAITH, "Faith", "Sermons, devotions, prayer, the Bible", Icons.Filled.Church, Color(0xFFB7791F)),
    SpaceTile(NotebookSpace.PERSONAL, "Personal", "Journal, ideas, life", Icons.Filled.SelfImprovement, Color(0xFF059669))
)

/** Which spaces the app shows. At least one stays on. */
@Composable
fun SpacesPicker(selected: Set<NotebookSpace>, onChange: (Set<NotebookSpace>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t ->
                    val on = t.space in selected
                    Surface(
                        onClick = {
                            val next = if (on) selected - t.space else selected + t.space
                            if (next.isNotEmpty()) onChange(next)
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = if (on) t.tint.copy(alpha = 0.08f) else Color.White,
                        border = BorderStroke(if (on) 2.dp else 1.dp, if (on) t.tint else Color(0xFFE5E7EB)),
                        modifier = Modifier.weight(1f).height(132.dp).testTag("space_${t.space.name.lowercase()}")
                    ) {
                        Box {
                            Column(Modifier.padding(14.dp)) {
                                Box(
                                    Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(listOf(t.tint.copy(alpha = 0.9f), t.tint.copy(alpha = 0.55f)))),
                                    contentAlignment = Alignment.Center
                                ) { Icon(t.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(21.dp)) }
                                Text(t.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827), modifier = Modifier.padding(top = 10.dp))
                                Text(t.line, fontSize = 12.sp, lineHeight = 16.sp, color = Color(0xFF6B7280))
                            }
                            if (on) Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = t.tint, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

/** How the app feels. */
@Composable
fun LookPicker(selected: LookAndFeel, onChange: (LookAndFeel) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LookAndFeel.entries.forEach { look ->
            val palette = AppLook.of(look)
            val on = look == selected
            Surface(
                onClick = { onChange(look) }, shape = RoundedCornerShape(18.dp), color = Color.White,
                border = BorderStroke(if (on) 2.dp else 1.dp, if (on) palette.accent else Color(0xFFE5E7EB)),
                modifier = Modifier.fillMaxWidth().testTag("look_${look.name.lowercase()}")
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(width = 54.dp, height = 40.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(palette.heroTop, palette.heroBottom)))) {
                        Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(12.dp).clip(CircleShape).background(palette.warm))
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(look.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827), fontFamily = palette.headingFont)
                        Text(look.description, fontSize = 12.sp, color = Color(0xFF6B7280))
                    }
                    if (on) Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = palette.accent, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
