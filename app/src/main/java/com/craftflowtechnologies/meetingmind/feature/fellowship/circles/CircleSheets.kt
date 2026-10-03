package com.craftflowtechnologies.meetingmind.feature.fellowship.circles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.feature.fellowship.FellowshipChip
import com.craftflowtechnologies.meetingmind.feature.fellowship.FellowshipNote
import com.craftflowtechnologies.meetingmind.feature.fellowship.FellowshipPrimaryButton
import com.craftflowtechnologies.meetingmind.feature.fellowship.fellowshipFieldColors
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.FaithGold
import com.craftflowtechnologies.meetingmind.ui.theme.FaithGoldWash
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkFaint
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.Line
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceRaised
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceSunk

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCircleSheet(
    cachedDisplayName: String,
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String, displayName: String, emoji: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf(cachedDisplayName) }
    var selectedEmoji by remember { mutableStateOf("🕊️") }
    val emojis = listOf("🕊️", "🤝", "📖", "🙏", "☀️", "🌱")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurfaceRaised
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 36.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Create Private Circle",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Serif,
                    color = Ink
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = InkMuted)
                }
            }
            Text(
                "An encrypted, invite-only group (5–15 people). Prayers and sermon studies stay completely private to members.",
                fontSize = 13.sp,
                color = InkMuted,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
            )

            // Emoji selector
            Row(
                Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (emoji in emojis) {
                    val isSelected = emoji == selectedEmoji
                    Surface(
                        onClick = { selectedEmoji = emoji },
                        shape = CircleShape,
                        color = if (isSelected) FaithGoldWash else SurfaceSunk,
                        border = BorderStroke(1.dp, if (isSelected) FaithGold else Line),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(emoji, fontSize = 20.sp)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Circle name (e.g. Tuesday Fellowship)") },
                colors = fellowshipFieldColors(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Short purpose (optional)") },
                colors = fellowshipFieldColors(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Your display name in this group (required)") },
                colors = fellowshipFieldColors(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
            )

            val canSubmit = name.trim().isNotBlank() && displayName.trim().isNotBlank()

            FellowshipPrimaryButton(
                text = "Create circle",
                onClick = {
                    if (canSubmit) {
                        onCreate(name.trim(), description.trim(), displayName.trim(), selectedEmoji)
                    }
                },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinCircleSheet(
    cachedDisplayName: String,
    initialInviteUri: String = "",
    onDismiss: () -> Unit,
    onJoin: (inviteCodeOrUri: String, displayName: String) -> Unit
) {
    var inviteCode by remember { mutableStateOf(initialInviteUri) }
    var displayName by remember { mutableStateOf(cachedDisplayName) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurfaceRaised
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 36.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Join Fellowship Circle",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Serif,
                    color = Ink
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = InkMuted)
                }
            }
            Text(
                "Paste the invite link or code shared by a group leader. The encryption key in the link grants you access.",
                fontSize = 13.sp,
                color = InkMuted,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
            )

            OutlinedTextField(
                value = inviteCode,
                onValueChange = { inviteCode = it },
                label = { Text("Invite link (mindcircle://join?... or code)") },
                colors = fellowshipFieldColors(),
                maxLines = 3,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Your display name (required)") },
                colors = fellowshipFieldColors(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
            )

            val canSubmit = inviteCode.trim().isNotBlank() && displayName.trim().isNotBlank()

            FellowshipPrimaryButton(
                text = "Join circle",
                onClick = {
                    if (canSubmit) {
                        onJoin(inviteCode.trim(), displayName.trim())
                    }
                },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostPrayerSheet(
    authorDisplayName: String,
    onDismiss: () -> Unit,
    onPost: (requestText: String, isUrgent: Boolean) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var isUrgent by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurfaceRaised
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 36.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Share Prayer Request",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Serif,
                    color = Ink
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = InkMuted)
                }
            }
            Text(
                "Posting as $authorDisplayName. Members can tap 'Prayed for this' to support you.",
                fontSize = 13.sp,
                color = InkMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
            )

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("What can your circle pray for?") },
                colors = fellowshipFieldColors(),
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
            )

            Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                FellowshipChip("Urgent request", selected = isUrgent, onClick = { isUrgent = !isUrgent })
            }

            val canSubmit = text.trim().isNotBlank()
            FellowshipPrimaryButton(
                text = "Share with circle",
                onClick = { if (canSubmit) onPost(text.trim(), isUrgent) },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostTestimonySheet(
    authorDisplayName: String,
    initialTitle: String = "",
    initialStory: String = "",
    prayerRequestId: String? = null,
    onDismiss: () -> Unit,
    onPost: (title: String, story: String, scripture: String?) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var story by remember { mutableStateOf(initialStory) }
    var scripture by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurfaceRaised
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 36.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (prayerRequestId != null) "Share Answered Prayer" else "Share Testimony",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Serif,
                    color = Ink
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = InkMuted)
                }
            }
            Text(
                "Posting as $authorDisplayName. Encourage your circle with what God has done.",
                fontSize = 13.sp,
                color = InkMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title or breakthrough") },
                colors = fellowshipFieldColors(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )

            OutlinedTextField(
                value = story,
                onValueChange = { story = it },
                label = { Text("The story or praise report") },
                colors = fellowshipFieldColors(),
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )

            OutlinedTextField(
                value = scripture,
                onValueChange = { scripture = it },
                label = { Text("Scripture reference (optional, e.g. Psalm 34:4)") },
                colors = fellowshipFieldColors(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
            )

            val canSubmit = title.trim().isNotBlank() && story.trim().isNotBlank()
            FellowshipPrimaryButton(
                text = "Share testimony",
                onClick = { if (canSubmit) onPost(title.trim(), story.trim(), scripture.trim().takeIf { it.isNotBlank() }) },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSermonPickerSheet(
    notes: List<Note>,
    onDismiss: () -> Unit,
    onSelectNote: (Note) -> Unit
) {
    val candidates = remember(notes) {
        notes.filter {
            it.deletedAt == null && it.archivedAt == null &&
                (it.workflow == RecordingType.SERMON || it.workflow == RecordingType.BIBLE_STUDY)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurfaceRaised
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 36.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Share Sermon or Study",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Serif,
                    color = Ink
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = InkMuted)
                }
            }
            Text(
                "Pick from your recorded sermons or Bible studies. Only the title, scripture, and discussion guide are shared.",
                fontSize = 13.sp,
                color = InkMuted,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            FellowshipNote(
                text = "Your private reflections, personal journal entries, and confessions never leave this phone.",
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (candidates.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceSunk,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                ) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No sermons or studies found", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        Text("Record a sermon on the Faith screen first.", fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().height(260.dp)) {
                    items(candidates, key = { it.id }) { note ->
                        Surface(
                            onClick = { onSelectNote(note) },
                            shape = RoundedCornerShape(14.dp),
                            color = SurfaceSunk,
                            border = BorderStroke(1.dp, Line),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(36.dp).clip(CircleShape).background(FaithGoldWash),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Church, contentDescription = null, tint = FaithGold, modifier = Modifier.size(18.dp))
                                }
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(note.title.ifBlank { "Untitled study" }, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink)
                                    val preacher = note.metadata["speaker"] ?: note.metadata["preacher"]
                                    val meta = listOfNotNull(preacher, note.metadata["passage"]).joinToString(" • ")
                                    if (meta.isNotBlank()) {
                                        Text(meta, fontSize = 12.sp, color = InkMuted)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
