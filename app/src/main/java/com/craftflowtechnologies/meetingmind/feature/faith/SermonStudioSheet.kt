package com.craftflowtechnologies.meetingmind.feature.faith

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.faith.MotivationalSermon
import com.craftflowtechnologies.meetingmind.core.faith.MotivationalSermonGenerator
import com.craftflowtechnologies.meetingmind.core.faith.SermonTheme
import com.craftflowtechnologies.meetingmind.feature.share.ShareRequest
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.AccentWash
import com.craftflowtechnologies.meetingmind.ui.theme.FaithGold
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.LineFaint
import com.craftflowtechnologies.meetingmind.ui.theme.LineSoft
import com.craftflowtechnologies.meetingmind.ui.theme.OnAccent
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceRaised
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceSunk
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceTrack
import kotlinx.coroutines.launch

/**
 * Bottom sheet for generating motivational mini-sermons and directly launching 9:16 Story sharing.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SermonStudioSheet(
    onDismiss: () -> Unit,
    onShareStory: (ShareRequest) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val generator = remember { MotivationalSermonGenerator(context) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedTheme by remember { mutableStateOf(SermonTheme.HOPE) }
    var customTopic by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var currentSermon by remember { mutableStateOf<MotivationalSermon?>(generator.getCuratedOrFallback(SermonTheme.HOPE.label)) }

    fun generate() {
        if (isGenerating) return
        isGenerating = true
        scope.launch {
            try {
                val topic = customTopic.trim().ifEmpty { selectedTheme.label }
                val result = generator.generate(topic)
                currentSermon = result
            } finally {
                isGenerating = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceRaised,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 36.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Motivational Sermon & Stories",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Serif,
                        color = Ink
                    )
                    Text(
                        text = "AI-assisted inspiration formatted for 9:16 vertical stories",
                        fontSize = 12.sp,
                        color = InkMuted
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = InkSecondary)
                }
            }

            Spacer(Modifier.height(14.dp))

            // Topic selection chips
            Text(
                text = "CHOOSE INSPIRATION THEME",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = InkMuted
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SermonTheme.entries.forEach { theme ->
                    val isSelected = selectedTheme == theme && customTopic.isEmpty()
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) Accent else SurfaceSunk,
                        border = BorderStroke(1.dp, if (isSelected) Accent else LineSoft),
                        modifier = Modifier.clickable {
                            selectedTheme = theme
                            customTopic = ""
                            generate()
                        }
                    ) {
                        Text(
                            text = theme.label,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) OnAccent else InkSecondary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Custom topic input
            OutlinedTextField(
                value = customTopic,
                onValueChange = { customTopic = it },
                placeholder = { Text("Or type a specific need (e.g. peace before interview)", fontSize = 13.sp, color = InkMuted) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = LineSoft,
                    focusedTextColor = Ink,
                    unfocusedTextColor = Ink
                )
            )

            if (customTopic.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { generate() },
                    enabled = !isGenerating,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = OnAccent, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Composing sermon…", fontSize = 14.sp)
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Generate Custom Sermon", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // Sermon Card Presentation
            currentSermon?.let { sermon ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceBase,
                    border = BorderStroke(1.dp, LineSoft),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = sermon.title,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Serif,
                                color = Ink,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AccentWash,
                                border = BorderStroke(1.dp, Accent.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = sermon.scriptureReference,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = FaithGold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = sermon.scriptureText,
                            fontSize = 13.sp,
                            fontStyle = FontStyle.Italic,
                            lineHeight = 18.sp,
                            color = InkSecondary
                        )

                        Spacer(Modifier.height(14.dp))

                        // Key Quote Story Preview Box
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceSunk,
                            border = BorderStroke(1.dp, LineFaint),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "STORY TAKEAWAY",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    color = FaithGold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "“${sermon.keyQuote}”",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 20.sp,
                                    color = Ink
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // The 3 points
                        Text(
                            text = "KEY TAKEAWAYS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = InkMuted
                        )
                        Spacer(Modifier.height(6.dp))
                        sermon.points.forEachIndexed { idx, point ->
                            Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(SurfaceTrack),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "${idx + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = InkSecondary)
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = point.removePrefix("${idx + 1}.").trim(),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = Ink
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Declaration
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AccentWash.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Declaration: ${sermon.declaration}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Ink,
                                modifier = Modifier.padding(10.dp)
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        // Action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    onDismiss()
                                    onShareStory(sermon.toStoryShareRequest())
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Share to Stories", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }

                            OutlinedButton(
                                onClick = { generate() },
                                enabled = !isGenerating,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, LineSoft),
                                modifier = Modifier.heightIn(min = 40.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Another", tint = InkSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
