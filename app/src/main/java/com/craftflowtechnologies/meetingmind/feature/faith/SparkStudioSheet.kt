package com.craftflowtechnologies.meetingmind.feature.faith

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Undo
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.faith.FaithLevel
import com.craftflowtechnologies.meetingmind.core.faith.RemixAction
import com.craftflowtechnologies.meetingmind.core.faith.SparkFormat
import com.craftflowtechnologies.meetingmind.core.faith.SparkGenerator
import com.craftflowtechnologies.meetingmind.core.faith.SparkMoment
import com.craftflowtechnologies.meetingmind.core.faith.SparkPiece
import com.craftflowtechnologies.meetingmind.core.faith.SparkVibe
import com.craftflowtechnologies.meetingmind.core.share.BackgroundPack
import com.craftflowtechnologies.meetingmind.feature.share.ShareRequest
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.AccentWash
import com.craftflowtechnologies.meetingmind.ui.theme.FaithGold
import com.craftflowtechnologies.meetingmind.ui.theme.FaithGoldInk
import com.craftflowtechnologies.meetingmind.ui.theme.FaithGoldWash
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.Line
import com.craftflowtechnologies.meetingmind.ui.theme.LineFaint
import com.craftflowtechnologies.meetingmind.ui.theme.LineSoft
import com.craftflowtechnologies.meetingmind.ui.theme.OnAccent
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceRaised
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceSunk
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceTrack
import kotlinx.coroutines.launch

/**
 * Total redesign of the Inspiration Studio: "Spark".
 * An interactive studio generating 3 punchy, shareable variations with live remixing,
 * moment contextualization, faith tuning, and seamless 9:16 story export.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SparkStudioSheet(
    initialVibe: SparkVibe = SparkVibe.DEEP,
    onDismiss: () -> Unit,
    onShareStory: (ShareRequest) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val generator = remember { SparkGenerator(context) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedVibe by remember { mutableStateOf(initialVibe) }
    var selectedFormat by remember { mutableStateOf(SparkFormat.ONE_LINER) }
    var faithLevel by remember { mutableStateOf(FaithLevel.SUBTLE) }
    var selectedMoment by remember { mutableStateOf<SparkMoment?>(null) }
    var customTopic by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var isRemixing by remember { mutableStateOf(false) }

    val pieces = remember { mutableStateListOf<SparkPiece>() }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pieces.size.coerceAtLeast(1) })

    fun doGenerate(vibe: SparkVibe = selectedVibe, format: SparkFormat = selectedFormat) {
        if (isGenerating) return
        isGenerating = true
        scope.launch {
            try {
                val newPieces = generator.generate(
                    vibe = vibe,
                    format = format,
                    faithLevel = faithLevel,
                    moment = selectedMoment,
                    customTopic = customTopic.takeIf { it.isNotBlank() }
                )
                // Keep pinned pieces if any
                val pinned = pieces.filter { it.isPinned }
                pieces.clear()
                pieces.addAll(pinned)
                val remainingCount = (3 - pinned.size).coerceAtLeast(1)
                pieces.addAll(newPieces.take(remainingCount))
            } finally {
                isGenerating = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (pieces.isEmpty()) {
            val initial = generator.getOfflineCurated(selectedVibe, selectedFormat, faithLevel)
            pieces.addAll(initial)
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(FaithGoldWash),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(selectedVibe.emoji, fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Spark Studio",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Serif,
                                color = Ink
                            )
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = AccentWash,
                                border = BorderStroke(1.dp, Accent.copy(alpha = 0.25f))
                            ) {
                                Text(
                                    "LIVE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Accent,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Deep, funny, fire & real content worth sharing",
                            fontSize = 12.sp,
                            color = InkMuted
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = InkSecondary)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Step 1: Vibe Selector Chips
            Text(
                text = "1. CHOOSE VIBE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = InkMuted
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(SparkVibe.entries) { vibe ->
                    val isSelected = selectedVibe == vibe
                    val bg = if (isSelected) Accent else SurfaceSunk
                    val textColor = if (isSelected) OnAccent else Ink
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = bg,
                        border = BorderStroke(1.dp, if (isSelected) Accent else LineSoft),
                        modifier = Modifier.clickable {
                            selectedVibe = vibe
                            doGenerate(vibe = vibe)
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(vibe.emoji, fontSize = 14.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = vibe.label,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = textColor
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Step 2: Format Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "2. FORMAT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = InkMuted
                )

                // Faith Level Selector (None / Subtle / Clear)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceSunk)
                        .padding(2.dp)
                ) {
                    FaithLevel.entries.forEach { level ->
                        val isSel = faithLevel == level
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) SurfaceRaised else Color.Transparent)
                                .clickable {
                                    faithLevel = level
                                    doGenerate()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = level.label,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) Ink else InkMuted
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SparkFormat.entries.forEach { format ->
                    val isSelected = selectedFormat == format
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) SurfaceBase else SurfaceSunk,
                        border = BorderStroke(1.dp, if (isSelected) Line else LineFaint),
                        modifier = Modifier.clickable {
                            selectedFormat = format
                            doGenerate(format = format)
                        }
                    ) {
                        Text(
                            text = format.label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) Ink else InkSecondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Optional Moments
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(SparkMoment.entries) { m ->
                    val isSelected = selectedMoment == m
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) FaithGoldWash else SurfaceSunk,
                        border = BorderStroke(1.dp, if (isSelected) FaithGold.copy(alpha = 0.5f) else LineFaint),
                        modifier = Modifier.clickable {
                            selectedMoment = if (isSelected) null else m
                            doGenerate()
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(m.emoji, fontSize = 12.sp)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = m.label,
                                fontSize = 11.5.sp,
                                color = if (isSelected) FaithGoldInk else InkSecondary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Custom prompt field
            OutlinedTextField(
                value = customTopic,
                onValueChange = { customTopic = it },
                placeholder = { Text("Custom topic or context (e.g. peace before interview)", fontSize = 12.sp, color = InkMuted) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    if (customTopic.isNotBlank()) {
                        IconButton(onClick = { doGenerate() }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Go", tint = Accent)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = LineSoft,
                    focusedTextColor = Ink,
                    unfocusedTextColor = Ink
                )
            )

            Spacer(Modifier.height(18.dp))

            // Step 3: Swipeable 3-Card Carousel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "3 VARIATIONS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = InkMuted
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Swipe to see all",
                        fontSize = 11.sp,
                        color = InkMuted
                    )
                }

                // Page indicator dots
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(pieces.size) { idx ->
                        val isCurr = pagerState.currentPage == idx
                        Box(
                            modifier = Modifier
                                .size(if (isCurr) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(if (isCurr) Accent else Line)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            if (isGenerating && pieces.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceBase),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Accent, strokeWidth = 2.5.dp)
                        Spacer(Modifier.height(12.dp))
                        Text("Drafting 3 fresh sparks…", fontSize = 13.sp, color = InkSecondary)
                    }
                }
            } else if (pieces.isNotEmpty()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth()
                ) { page ->
                    val piece = pieces.getOrNull(page)
                    if (piece != null) {
                        SparkCardView(
                            piece = piece,
                            isRemixing = isRemixing,
                            onTogglePin = {
                                val idx = pieces.indexOfFirst { it.id == piece.id }
                                if (idx != -1) {
                                    pieces[idx] = piece.copy(isPinned = !piece.isPinned)
                                }
                            },
                            onTextEdited = { newText ->
                                val idx = pieces.indexOfFirst { it.id == piece.id }
                                if (idx != -1) {
                                    pieces[idx] = piece.withTextUpdate(newText)
                                }
                            }
                        )
                    }
                }

                val currentPiece = pieces.getOrNull(pagerState.currentPage)

                if (currentPiece != null) {
                    Spacer(Modifier.height(14.dp))

                    // Live Remix Chips for active piece
                    Text(
                        text = "REMIX THIS CARD",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = InkMuted
                    )
                    Spacer(Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        if (currentPiece.canUndo()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = SurfaceTrack,
                                    border = BorderStroke(1.dp, Line),
                                    modifier = Modifier.clickable {
                                        val idx = pagerState.currentPage
                                        pieces[idx] = currentPiece.undo()
                                    }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Undo, contentDescription = "Undo", tint = Ink, modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Undo", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                                    }
                                }
                            }
                        }

                        items(RemixAction.entries) { action ->
                            // Don't show Add Verse if faith is None
                            if (faithLevel == FaithLevel.NONE && action == RemixAction.ADD_VERSE) return@items
                            if (currentPiece.verseRef == null && action == RemixAction.REMOVE_VERSE) return@items
                            if (currentPiece.verseRef != null && action == RemixAction.ADD_VERSE) return@items

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SurfaceSunk,
                                border = BorderStroke(1.dp, LineSoft),
                                modifier = Modifier.clickable {
                                    if (!isRemixing) {
                                        isRemixing = true
                                        scope.launch {
                                            try {
                                                val remixed = generator.remix(currentPiece, action)
                                                val idx = pagerState.currentPage
                                                pieces[idx] = remixed
                                            } finally {
                                                isRemixing = false
                                            }
                                        }
                                    }
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(action.emoji, fontSize = 12.sp)
                                    Spacer(Modifier.width(4.dp))
                                    Text(action.label, fontSize = 12.sp, color = Ink)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                onDismiss()
                                onShareStory(currentPiece.toShareRequest())
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Share to Stories (9:16)", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = {
                                val clip = ClipData.newPlainText("Spark Quote", currentPiece.text + (currentPiece.verseRef?.let { "\n— $it" } ?: ""))
                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, LineSoft)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = InkSecondary, modifier = Modifier.size(16.dp))
                        }

                        OutlinedButton(
                            onClick = { doGenerate() },
                            enabled = !isGenerating,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, LineSoft)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Shuffle", tint = InkSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SparkCardView(
    piece: SparkPiece,
    isRemixing: Boolean,
    onTogglePin: () -> Unit,
    onTextEdited: (String) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember(piece.text) { mutableStateOf(piece.text) }

    val pack = remember(piece.vibe) { BackgroundPack.byId(piece.vibe.defaultPackId) }
    val colors = remember(pack) { pack.colors.map { Color(it) } }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = SurfaceBase,
        border = BorderStroke(1.dp, LineSoft),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Card Top Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AccentWash,
                        border = BorderStroke(1.dp, Accent.copy(alpha = 0.25f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(piece.vibe.emoji, fontSize = 11.sp)
                            Spacer(Modifier.width(4.dp))
                            Text(piece.vibe.label.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Accent)
                        }
                    }

                    Spacer(Modifier.width(6.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SurfaceTrack
                    ) {
                        Text(
                            text = piece.format.badge,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = InkSecondary,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isEditing = !isEditing }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (isEditing) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = if (isEditing) Accent else InkMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(onClick = onTogglePin, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (piece.isPinned) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Pin",
                            tint = if (piece.isPinned) Color(0xFFEF4444) else InkMuted,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // 9:16 Mini Card Preview Frame
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            colors = if (colors.size >= 2) colors else listOf(Color(0xFF2B2140), Color(0xFF8E4B6B))
                        )
                    )
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.Center
                ) {
                    if (isEditing) {
                        OutlinedTextField(
                            value = editText,
                            onValueChange = {
                                editText = it
                                onTextEdited(it)
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Serif
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.White.copy(alpha = 0.8f),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                cursorColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = if (piece.format == SparkFormat.ONE_LINER || piece.format == SparkFormat.HOT_TAKE)
                                "“${piece.text}”" else piece.text,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Serif,
                            lineHeight = 24.sp,
                            color = Color.White
                        )
                    }

                    if (!piece.verseRef.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(3.dp, 14.dp)
                                    .background(FaithGold)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = piece.verseRef,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD28A)
                            )
                        }
                        if (!piece.verseText.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "“${piece.verseText}”",
                                fontSize = 11.5.sp,
                                fontStyle = FontStyle.Italic,
                                lineHeight = 16.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "MeetingMind Spark",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }

                if (isRemixing) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}
