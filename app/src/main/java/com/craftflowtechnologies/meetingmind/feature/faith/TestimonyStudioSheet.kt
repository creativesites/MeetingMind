package com.craftflowtechnologies.meetingmind.feature.faith

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.craftflowtechnologies.meetingmind.ui.theme.FaithGold
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.TestimonyColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class StudioWriteMode(val label: String) {
    GUIDED("Guided Beats"),
    FREEFORM("Freeform")
}

private enum class StudioSaveState(val label: String) {
    IDLE("Auto-save ready"),
    SAVING("Auto-saving…"),
    DRAFT_SAVED("Draft saved"),
    PUBLISHED("Saved to Wall")
}

/**
 * Editorial, zero-loss Testimony Studio modal sheet.
 * Every keystroke auto-saves to Room after 350ms and flushes non-destructively on Back, outside tap,
 * Close, or lifecycle pause — typed testimonies are never wiped unless the user taps Discard.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TestimonyStudioSheet(
    seed: TestimonyStudioSeed,
    viewModel: FaithViewModel,
    onDismiss: (savedAsDraft: Boolean, published: Boolean) -> Unit,
    onPublished: (noteId: String) -> Unit,
    onOpenFullEditor: (noteId: String) -> Unit,
    onSwitchToVoice: () -> Unit,
    onSaveAndShareToCircle: ((noteId: String) -> Unit)? = null
) {
    val c = MM.colors
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lifecycleOwner = LocalLifecycleOwner.current

    var title by remember(seed.noteId) { mutableStateOf(seed.title) }
    var situation by remember(seed.noteId) { mutableStateOf(seed.beats.situation) }
    var prayedFor by remember(seed.noteId) { mutableStateOf(seed.beats.prayedFor) }
    var whatHappened by remember(seed.noteId) { mutableStateOf(seed.beats.whatHappened) }
    var learned by remember(seed.noteId) { mutableStateOf(seed.beats.learned) }
    var category by remember(seed.noteId) { mutableStateOf(seed.category) }
    var verseRef by remember(seed.noteId) { mutableStateOf(seed.verseRef.orEmpty()) }
    var verseText by remember(seed.noteId) { mutableStateOf(seed.verseText.orEmpty()) }
    var storyDateMs by remember(seed.noteId) { mutableLongStateOf(seed.storyDateMs) }

    var writeMode by remember(seed.noteId) {
        mutableStateOf(
            if (seed.beats.situation.isBlank() && seed.beats.prayedFor.isBlank() &&
                seed.beats.learned.isBlank() && seed.beats.whatHappened.isNotBlank()
            ) StudioWriteMode.FREEFORM else StudioWriteMode.GUIDED
        )
    }

    var wasPublished by remember(seed.noteId) { mutableStateOf(!seed.isDraft) }
    var saveState by remember(seed.noteId) {
        mutableStateOf(if (seed.isDraft) StudioSaveState.DRAFT_SAVED else StudioSaveState.PUBLISHED)
    }
    var userEdited by remember(seed.noteId) { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var lookingUpVerse by remember { mutableStateOf(false) }

    fun currentBeats() = TestimonyBeats(
        situation = situation,
        prayedFor = prayedFor,
        whatHappened = whatHappened,
        learned = learned,
        scriptureNote = verseRef
    )

    fun hasAnyTypedContent(): Boolean {
        val beats = currentBeats()
        return title.isNotBlank() || beats.hasAnyContent || category != null ||
            verseRef.isNotBlank() || seed.linkedPrayerId != null
    }

    // 350ms debounced continuous auto-save into Room (strictly preserved)
    LaunchedEffect(
        seed.noteId, title, situation, prayedFor, whatHappened, learned,
        category, verseRef, verseText, storyDateMs
    ) {
        if (!userEdited) return@LaunchedEffect
        saveState = StudioSaveState.SAVING
        delay(350L)
        viewModel.saveStudioStory(
            noteId = seed.noteId,
            title = title,
            beats = currentBeats(),
            category = category,
            verseRef = verseRef.ifBlank { null },
            verseText = verseText.ifBlank { null },
            storyDateMs = storyDateMs,
            linkedPrayerId = seed.linkedPrayerId,
            publish = wasPublished
        )
        saveState = if (wasPublished) StudioSaveState.PUBLISHED else StudioSaveState.DRAFT_SAVED
    }

    // Flush non-blocking if backgrounded/paused mid-sentence (strictly preserved)
    DisposableEffect(lifecycleOwner, seed.noteId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.flushStudioOnExit(
                    noteId = seed.noteId,
                    title = title,
                    beats = currentBeats(),
                    category = category,
                    verseRef = verseRef.ifBlank { null },
                    verseText = verseText.ifBlank { null },
                    storyDateMs = storyDateMs,
                    linkedPrayerId = seed.linkedPrayerId,
                    wasPublished = wasPublished
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun handleNonDestructiveClose() {
        val hadContent = hasAnyTypedContent()
        viewModel.flushStudioOnExit(
            noteId = seed.noteId,
            title = title,
            beats = currentBeats(),
            category = category,
            verseRef = verseRef.ifBlank { null },
            verseText = verseText.ifBlank { null },
            storyDateMs = storyDateMs,
            linkedPrayerId = seed.linkedPrayerId,
            wasPublished = wasPublished
        ) {
            onDismiss(hadContent && !wasPublished, wasPublished)
        }
    }

    BackHandler { handleNonDestructiveClose() }

    val filledBeatCount = listOf(situation, prayedFor, whatHappened, learned).count { it.isNotBlank() }
    val canPublish = title.isNotBlank() || currentBeats().hasAnyContent

    ModalBottomSheet(
        onDismissRequest = { handleNonDestructiveClose() },
        sheetState = sheetState,
        containerColor = c.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Top bar with live Room save badge & Switch-to-Voice
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MM.space.m, vertical = MM.space.s),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { handleNonDestructiveClose() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Save draft and close", tint = c.ink)
                }
                Column(Modifier.weight(1f)) {
                    Text("Testimony Studio", style = MM.type.heading, color = c.ink)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    color = when (saveState) {
                                        StudioSaveState.SAVING -> FaithGold
                                        StudioSaveState.DRAFT_SAVED -> TestimonyColors.DraftAmber
                                        StudioSaveState.PUBLISHED, StudioSaveState.IDLE -> TestimonyColors.CategoryHealing
                                    }
                                )
                        )
                        Text(saveState.label, style = MM.type.caption, color = c.inkSecondary)
                    }
                }
                Surface(
                    onClick = {
                        viewModel.flushStudioOnExit(
                            noteId = seed.noteId,
                            title = title,
                            beats = currentBeats(),
                            category = category,
                            verseRef = verseRef.ifBlank { null },
                            verseText = verseText.ifBlank { null },
                            storyDateMs = storyDateMs,
                            linkedPrayerId = seed.linkedPrayerId,
                            wasPublished = wasPublished
                        ) {
                            onSwitchToVoice()
                        }
                    },
                    shape = MM.radius.pill,
                    color = c.surfaceSunk,
                    border = BorderStroke(1.dp, c.lineSoft)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = MM.space.m, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = null, tint = FaithGold, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(MM.space.xs))
                        Text("Speak", style = MM.type.caption, fontWeight = FontWeight.SemiBold, color = c.ink)
                    }
                }
                if (hasAnyTypedContent()) {
                    IconButton(onClick = { confirmDiscard = true }) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Discard draft", tint = c.inkMuted)
                    }
                }
            }

            HorizontalDivider(color = c.lineSoft)

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = MM.space.l, vertical = MM.space.m),
                verticalArrangement = Arrangement.spacedBy(MM.space.m)
            ) {
                // Linked Prayer badge if testifying to an answered prayer
                seed.linkedPrayerId?.let {
                    Surface(
                        shape = MM.radius.card,
                        color = FaithGold.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, FaithGold.copy(alpha = 0.30f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = MM.space.m, vertical = MM.space.s),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Verified, contentDescription = null, tint = FaithGold, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(MM.space.s))
                            Column(Modifier.weight(1f)) {
                                Text("Answered Prayer Story", style = MM.type.caption, fontWeight = FontWeight.SemiBold, color = FaithGold)
                                Text(
                                    seed.linkedPrayerTitle?.takeIf { it.isNotBlank() } ?: "Linked to your prayer request",
                                    style = MM.type.secondary,
                                    color = c.ink
                                )
                            }
                        }
                    }
                }

                // Segmented write mode + Guided progress
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(MM.radius.small)
                            .background(c.surfaceSunk)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        StudioWriteMode.entries.forEach { mode ->
                            val selected = writeMode == mode
                            Surface(
                                onClick = { writeMode = mode },
                                shape = MM.radius.small,
                                color = if (selected) c.surfaceRaised else Color.Transparent,
                                border = if (selected) BorderStroke(1.dp, c.lineSoft) else null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        mode.label,
                                        style = MM.type.caption,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (selected) c.ink else c.inkSecondary
                                    )
                                }
                            }
                        }
                    }
                    if (writeMode == StudioWriteMode.GUIDED) {
                        Surface(
                            shape = MM.radius.pill,
                            color = FaithGold.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, FaithGold.copy(alpha = 0.30f))
                        ) {
                            Text(
                                "$filledBeatCount/4 beats",
                                style = MM.type.caption,
                                fontWeight = FontWeight.SemiBold,
                                color = FaithGold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Serif Title input
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; userEdited = true },
                    label = { Text("Headline of faithfulness") },
                    placeholder = { Text("e.g. Peace before surgery, Provision right on time") },
                    singleLine = true,
                    textStyle = MM.type.heading.copy(fontFamily = FontFamily.Serif, color = c.ink),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FaithGold,
                        unfocusedBorderColor = c.lineSoft,
                        focusedLabelColor = FaithGold
                    ),
                    shape = MM.radius.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("testimony_studio_title")
                )

                // Category pills
                Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                    Text("Moment category", style = MM.type.caption, fontWeight = FontWeight.Medium, color = c.inkSecondary)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(MM.space.s),
                        verticalArrangement = Arrangement.spacedBy(MM.space.s)
                    ) {
                        TestimonyCategory.entries.forEach { cat ->
                            val selected = category == cat
                            val accent = testimonyCategoryAccent(cat)
                            Surface(
                                onClick = {
                                    category = if (selected) null else cat
                                    userEdited = true
                                },
                                shape = MM.radius.pill,
                                color = if (selected) accent.copy(alpha = 0.18f) else c.surfaceSunk,
                                border = BorderStroke(1.dp, if (selected) accent else c.lineSoft)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = MM.space.m, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        cat.label,
                                        style = MM.type.caption,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (selected) c.ink else c.inkSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // Story date quick selector
                val formattedDate = remember(storyDateMs) {
                    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(storyDateMs))
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                ) {
                    Text("When it happened:", style = MM.type.caption, color = c.inkSecondary)
                    DatePresetChip("Today", selected = isSameDay(storyDateMs, System.currentTimeMillis())) {
                        storyDateMs = System.currentTimeMillis()
                        userEdited = true
                    }
                    DatePresetChip("Earlier this week", selected = isWithinWeek(storyDateMs)) {
                        storyDateMs = System.currentTimeMillis() - 3L * 24 * 60 * 60 * 1000L
                        userEdited = true
                    }
                    Surface(
                        shape = MM.radius.pill,
                        color = c.surfaceSunk,
                        border = BorderStroke(1.dp, c.lineSoft)
                    ) {
                        Text(
                            formattedDate,
                            style = MM.type.caption,
                            fontWeight = FontWeight.Medium,
                            color = c.ink,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                if (writeMode == StudioWriteMode.GUIDED) {
                    BeatField(
                        stepNumber = "1",
                        label = "The situation",
                        placeholder = "What were you facing, waiting for, or carrying?",
                        value = situation,
                        onValueChange = { situation = it; userEdited = true }
                    )
                    BeatField(
                        stepNumber = "2",
                        label = "What you prayed",
                        placeholder = "What did you ask or surrender to God?",
                        value = prayedFor,
                        onValueChange = { prayedFor = it; userEdited = true }
                    )
                    BeatField(
                        stepNumber = "3",
                        label = "How God moved",
                        placeholder = "Tell the turning point and what happened…",
                        value = whatHappened,
                        minLines = 3,
                        onValueChange = { whatHappened = it; userEdited = true }
                    )
                    BeatField(
                        stepNumber = "4",
                        label = "What stayed with you",
                        placeholder = "What did this deepen in your faith?",
                        value = learned,
                        onValueChange = { learned = it; userEdited = true }
                    )
                } else {
                    OutlinedTextField(
                        value = whatHappened,
                        onValueChange = { whatHappened = it; userEdited = true },
                        label = { Text("Your story") },
                        placeholder = { Text("Pour out what God did — your words are auto-saved continuously…") },
                        minLines = 6,
                        textStyle = MM.type.body.copy(color = c.ink),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = FaithGold,
                            unfocusedBorderColor = c.lineSoft,
                            focusedLabelColor = FaithGold
                        ),
                        shape = MM.radius.small,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Anchor Scripture picker + preview
                Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    Text("Anchor Scripture (optional)", style = MM.type.caption, fontWeight = FontWeight.Medium, color = c.inkSecondary)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                    ) {
                        OutlinedTextField(
                            value = verseRef,
                            onValueChange = { verseRef = it; verseText = ""; userEdited = true },
                            placeholder = { Text("e.g. Psalm 23:1-3 or Romans 8:28") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Filled.MenuBook, contentDescription = null, tint = FaithGold) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = FaithGold,
                                unfocusedBorderColor = c.lineSoft
                            ),
                            shape = MM.radius.small,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            onClick = {
                                if (verseRef.isBlank() || lookingUpVerse) return@Surface
                                lookingUpVerse = true
                                scope.launch {
                                    val found = viewModel.lookupScripturePassage(verseRef)
                                    lookingUpVerse = false
                                    if (found != null) {
                                        verseRef = found.first
                                        verseText = found.second.orEmpty()
                                        userEdited = true
                                    }
                                }
                            },
                            shape = MM.radius.small,
                            color = FaithGold.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, FaithGold.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = MM.space.m, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Search, contentDescription = "Look up verse", tint = FaithGold, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (lookingUpVerse) "…" else "Attach", style = MM.type.caption, fontWeight = FontWeight.SemiBold, color = FaithGold)
                            }
                        }
                    }

                    AnimatedVisibility(visible = verseText.isNotBlank()) {
                        Surface(
                            shape = MM.radius.small,
                            color = if (c.isDark) TestimonyColors.ScriptureWashDark else TestimonyColors.ScriptureWashLight,
                            border = BorderStroke(1.dp, FaithGold.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "\"${verseText.trim()}\"",
                                style = MM.type.scripture.copy(fontStyle = FontStyle.Italic),
                                color = c.ink,
                                modifier = Modifier.padding(MM.space.m)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(MM.space.xs))
            }

            HorizontalDivider(color = c.lineSoft)

            // Sticky footer actions: Full Note | Save & Share to Circle | Save to Wall
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.surface)
                    .padding(horizontal = MM.space.m, vertical = MM.space.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.flushStudioOnExit(
                            noteId = seed.noteId,
                            title = title,
                            beats = currentBeats(),
                            category = category,
                            verseRef = verseRef.ifBlank { null },
                            verseText = verseText.ifBlank { null },
                            storyDateMs = storyDateMs,
                            linkedPrayerId = seed.linkedPrayerId,
                            wasPublished = wasPublished
                        ) {
                            onOpenFullEditor(seed.noteId)
                        }
                    },
                    shape = MM.radius.small,
                    border = BorderStroke(1.dp, c.lineSoft)
                ) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = "Full note", modifier = Modifier.size(15.dp), tint = c.inkSecondary)
                    Spacer(Modifier.width(MM.space.xs))
                    Text("Note", style = MM.type.secondary, color = c.ink)
                }

                if (onSaveAndShareToCircle != null) {
                    OutlinedButton(
                        onClick = {
                            wasPublished = true
                            scope.launch {
                                viewModel.saveStudioStory(
                                    noteId = seed.noteId,
                                    title = title,
                                    beats = currentBeats(),
                                    category = category,
                                    verseRef = verseRef.ifBlank { null },
                                    verseText = verseText.ifBlank { null },
                                    storyDateMs = storyDateMs,
                                    linkedPrayerId = seed.linkedPrayerId,
                                    publish = true
                                )
                                onSaveAndShareToCircle(seed.noteId)
                            }
                        },
                        enabled = canPublish,
                        shape = MM.radius.small,
                        border = BorderStroke(1.dp, TestimonyColors.CircleSage.copy(alpha = 0.55f)),
                        modifier = Modifier.testTag("testimony_studio_share_circle")
                    ) {
                        Icon(Icons.Filled.Groups, contentDescription = null, modifier = Modifier.size(16.dp), tint = TestimonyColors.CircleSage)
                        Spacer(Modifier.width(MM.space.xs))
                        Text("Circle", style = MM.type.secondary, fontWeight = FontWeight.SemiBold, color = TestimonyColors.CircleSage)
                    }
                }

                Button(
                    onClick = {
                        wasPublished = true
                        scope.launch {
                            viewModel.saveStudioStory(
                                noteId = seed.noteId,
                                title = title,
                                beats = currentBeats(),
                                category = category,
                                verseRef = verseRef.ifBlank { null },
                                verseText = verseText.ifBlank { null },
                                storyDateMs = storyDateMs,
                                linkedPrayerId = seed.linkedPrayerId,
                                publish = true
                            )
                            onPublished(seed.noteId)
                        }
                    },
                    enabled = canPublish,
                    shape = MM.radius.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FaithGold,
                        contentColor = TestimonyColors.HeroDeepIndigo
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("testimony_studio_save")
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(MM.space.xs))
                    Text(
                        if (seed.isDraft) "Save to Wall" else "Update Story",
                        style = MM.type.secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            containerColor = c.surfaceRaised,
            title = { Text("Discard this draft?", style = MM.type.heading, color = c.ink) },
            text = {
                Text(
                    "Your testimony has been auto-saved as a draft. Discard only if you want to remove it permanently.",
                    style = MM.type.secondary,
                    color = c.inkSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    viewModel.discardDraftTestimony(seed.noteId)
                    onDismiss(false, false)
                }) {
                    Text("Discard permanently", color = TestimonyColors.CategoryFamily, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text("Keep draft", color = c.ink)
                }
            }
        )
    }
}

@Composable
private fun BeatField(
    stepNumber: String,
    label: String,
    placeholder: String,
    value: String,
    minLines: Int = 2,
    onValueChange: (String) -> Unit
) {
    val c = MM.colors
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MM.radius.pill,
                color = FaithGold.copy(alpha = 0.16f)
            ) {
                Text(
                    stepNumber,
                    style = MM.type.caption,
                    fontWeight = FontWeight.Bold,
                    color = FaithGold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Spacer(Modifier.width(MM.space.s))
            Text(label, style = MM.type.bodyStrong, color = c.ink)
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, style = MM.type.secondary) },
            minLines = minLines,
            textStyle = MM.type.body.copy(color = c.ink),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = FaithGold,
                unfocusedBorderColor = c.lineSoft
            ),
            shape = MM.radius.small,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DatePresetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = MM.colors
    Surface(
        onClick = onClick,
        shape = MM.radius.pill,
        color = if (selected) FaithGold.copy(alpha = 0.16f) else c.surfaceSunk,
        border = BorderStroke(1.dp, if (selected) FaithGold else c.lineSoft)
    ) {
        Text(
            label,
            style = MM.type.caption,
            fontWeight = FontWeight.Medium,
            color = if (selected) FaithGold else c.inkSecondary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

fun testimonyCategoryAccent(category: TestimonyCategory): Color = when (category) {
    TestimonyCategory.HEALING -> TestimonyColors.CategoryHealing
    TestimonyCategory.PROVISION -> TestimonyColors.CategoryProvision
    TestimonyCategory.BREAKTHROUGH -> TestimonyColors.CategoryBreakthrough
    TestimonyCategory.FAMILY -> TestimonyColors.CategoryFamily
    TestimonyCategory.GUIDANCE -> TestimonyColors.CategoryGuidance
    TestimonyCategory.GRATITUDE -> TestimonyColors.CategoryGratitude
}

private fun isSameDay(aMs: Long, bMs: Long): Boolean {
    val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)
    return fmt.format(Date(aMs)) == fmt.format(Date(bMs))
}

private fun isWithinWeek(ms: Long): Boolean {
    val diff = System.currentTimeMillis() - ms
    return diff in (24L * 60 * 60 * 1000L)..(7L * 24 * 60 * 60 * 1000L)
}
