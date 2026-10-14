package com.craftflowtechnologies.meetingmind.feature.readtome

import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.craftflowtechnologies.meetingmind.ai.voice.read.DeliveryPreset
import com.craftflowtechnologies.meetingmind.ai.voice.read.DeliveryResolver
import com.craftflowtechnologies.meetingmind.ai.voice.read.GeminiKeyTier
import com.craftflowtechnologies.meetingmind.ai.voice.read.PauseAfterQuestion
import com.craftflowtechnologies.meetingmind.ai.voice.read.ReadOffer
import com.craftflowtechnologies.meetingmind.ai.voice.read.ReadToMeSettings
import com.craftflowtechnologies.meetingmind.ai.voice.read.ReadToMeSettingsStore
import com.craftflowtechnologies.meetingmind.ai.voice.read.Recovery
import com.craftflowtechnologies.meetingmind.ai.voice.read.ReadingUiState
import com.craftflowtechnologies.meetingmind.ai.voice.read.SpeechProvider
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SegmentedControl
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.ui.theme.MM

private const val SLOW_AFTER_SECONDS = 25

private val PACES = listOf(0.9f to "Slower", 1.0f to "Normal", 1.15f to "Faster")

/** Pace to the nearest of the three offered. */
internal fun paceIndex(pace: Float): Int = PACES.indices.minByOrNull { kotlin.math.abs(PACES[it].first - pace) } ?: 1

/**
 * The Read to Me sheet for one note: which voice will read it and why, where to start, how it should
 * sound, and then progress or what went wrong. Everything shown here comes from the coordinator; the
 * sheet decides nothing about privacy.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ReadToMeSheet(noteId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val coordinator = remember { ReadToMe.coordinator(context) }
    val settingsStore = remember { ReadToMeSettingsStore(context) }
    var settings by remember { mutableStateOf(settingsStore.load()) }
    var offer by remember { mutableStateOf<ReadOffer?>(null) }
    var startFrom by remember { mutableStateOf<String?>(null) }
    var started by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var backgroundQueued by remember { mutableStateOf(false) }
    val promotions by coordinator.promotions.collectAsState()
    val state by coordinator.state.collectAsState()

    LaunchedEffect(noteId, settings.prefersLocal, settings.disclosureAccepted) { offer = coordinator.offer(noteId) }

    fun change(update: (ReadToMeSettings) -> ReadToMeSettings) { settings = settingsStore.update(update) }
    if (showSettings) ReadToMeSettingsSheet(onDismiss = { showSettings = false })

    ModalBottomSheet(
        onDismissRequest = { coordinator.dismiss(); onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MM.colors.surface
    ) {
        Column(
            Modifier.padding(horizontal = MM.space.xl).padding(bottom = MM.space.l).navigationBarsPadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MM.space.m)
        ) {
            Text("Read to me", style = MM.type.title, color = MM.colors.ink)
            offer?.title?.let { Text(it, style = MM.type.secondary, color = MM.colors.inkSecondary, maxLines = 1) }

            val own = started || state.noteId() == noteId
            val shown = if (own) state else ReadingUiState.Idle
            when (shown) {
                is ReadingUiState.Idle -> {
                    SetupContent(
                        offer, settings, startFrom, { startFrom = it }, ::change,
                        onStart = { resume ->
                            started = true
                            coordinator.start(noteId, startFrom, resume = resume)
                        },
                        onBackground = {
                            ReadToMe.enqueueBackground(context, noteId)
                            backgroundQueued = true
                        },
                        backgroundQueued = backgroundQueued
                    )
                    TextAction("Read to Me settings", onClick = { showSettings = true })
                }
                is ReadingUiState.Working -> {
                    var now by remember { mutableStateOf(System.currentTimeMillis()) }
                    LaunchedEffect(shown.startedAtMs) { while (true) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(1_000) } }
                    val waited = if (shown.startedAtMs > 0) ((now - shown.startedAtMs) / 1000).coerceAtLeast(0) else 0
                    val fraction = if (shown.total == 0) 0f else ((shown.done + shown.partFraction) / shown.total).coerceIn(0f, 1f)
                    Text(
                        when {
                            shown.total == 0 -> "Getting ready…"
                            shown.done == 0 -> "Preparing the first part… ${waited}s"
                            else -> "Preparing ${minOf(shown.done + 1, shown.total)} of ${shown.total} · ${waited}s"
                        },
                        style = MM.type.body, color = MM.colors.ink
                    )
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth(), color = MM.colors.accent, trackColor = MM.colors.track)
                    shown.note?.let { StatusLine(StatusKind.Info, it) }
                    if (shown.provider == SpeechProvider.GEMINI && shown.done == 0 && waited >= SLOW_AFTER_SECONDS) {
                        StatusLine(StatusKind.Warning, "Gemini is taking a while. You can keep waiting or use your phone's voice instead.")
                        SecondaryButton("Use my phone's voice", onClick = coordinator::choosePhoneVoice, modifier = Modifier.fillMaxWidth())
                    }
                    Text("Playing starts after the first part. You can close this; the rest keeps getting ready.", style = MM.type.caption, color = MM.colors.inkMuted)
                    SecondaryButton("Stop preparing", onClick = { coordinator.cancel(); started = false }, modifier = Modifier.fillMaxWidth())
                }
                is ReadingUiState.Finished -> {
                    StatusLine(StatusKind.Info, "Playing with ${if (shown.provider == SpeechProvider.GEMINI) "the Gemini voice" else "your phone's voice"}. The player at the bottom has the controls.")
                    if (shown.switchedToPhone) StatusLine(StatusKind.Warning, "Part of this reading uses your phone's voice because you switched.")
                    shown.note?.let { StatusLine(StatusKind.Info, it) }
                    promotions.forEach { p ->
                        StatusLine(StatusKind.Info, p.message, actionLabel = "Undo", onAction = { coordinator.undoPromotion(p) })
                    }
                    ChangesSection(noteId)
                    PrimaryButton("Done", onClick = { coordinator.dismiss(); onDismiss() }, modifier = Modifier.fillMaxWidth())
                }
                is ReadingUiState.NeedsChoice -> {
                    StatusLine(StatusKind.Warning, shown.message)
                    PrimaryButton("Read the rest with Gemini", onClick = coordinator::chooseCloudWithoutSensitive, modifier = Modifier.fillMaxWidth())
                    SecondaryButton("Read everything with my phone's voice", onClick = coordinator::choosePhoneVoice, modifier = Modifier.fillMaxWidth())
                }
                is ReadingUiState.NeedsDisclosure -> {
                    Text(disclosureText(shown.usingBuiltInKey, shown.keyTier), style = MM.type.body, color = MM.colors.ink)
                    PrimaryButton("Accept and listen", onClick = coordinator::acceptDisclosure, modifier = Modifier.fillMaxWidth())
                    SecondaryButton("Use my phone's voice instead", onClick = coordinator::choosePhoneVoice, modifier = Modifier.fillMaxWidth())
                }
                is ReadingUiState.Failed -> {
                    StatusLine(StatusKind.Error, shown.message)
                    if (shown.total > 0) Text("${shown.partIndex} of ${shown.total} parts are ready and kept.", style = MM.type.caption, color = MM.colors.inkMuted)
                    shown.options.forEach { option ->
                        when (option) {
                            Recovery.RETRY, Recovery.RETRY_LATER, Recovery.CHECK_CONNECTION -> PrimaryButton("Try again", onClick = coordinator::retry, modifier = Modifier.fillMaxWidth())
                            Recovery.SWITCH_TO_PHONE_VOICE -> SecondaryButton("Finish with my phone's voice", onClick = coordinator::switchToPhoneVoice, modifier = Modifier.fillMaxWidth())
                            Recovery.INSTALL_VOICE_DATA -> SecondaryButton("Install voice data", onClick = {
                                context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }, modifier = Modifier.fillMaxWidth())
                            Recovery.FIX_API_KEY -> Text("Check your Gemini key in Settings.", style = MM.type.secondary, color = MM.colors.inkSecondary)
                            Recovery.CHECK_PLAN -> Text("Check your plan in Settings.", style = MM.type.secondary, color = MM.colors.inkSecondary)
                        }
                    }
                }
                is ReadingUiState.Locked -> StatusLine(StatusKind.Info, "This note is locked. Unlock your Faith notes to listen to it.")
                is ReadingUiState.Empty -> StatusLine(StatusKind.Info, "There's nothing to read yet. Images, embeds and empty sections aren't read.")
                is ReadingUiState.NotFound -> StatusLine(StatusKind.Error, "That note couldn't be found.")
            }
        }
    }
}

private fun ReadingUiState.noteId(): String? = when (this) {
    is ReadingUiState.Working -> noteId
    is ReadingUiState.Finished -> noteId
    is ReadingUiState.NeedsChoice -> noteId
    is ReadingUiState.NeedsDisclosure -> noteId
    is ReadingUiState.Failed -> noteId
    else -> null
}

private fun disclosureText(builtInKey: Boolean, tier: GeminiKeyTier): String = buildString {
    append("To make the audio, Read to Me sends this note's text to Google Gemini. ")
    append(if (builtInKey) "It uses the app's own Gemini key. " else "It uses your own Gemini key. ")
    append("Private notes and sections are never sent; they use your phone's voice. ")
    if (tier != GeminiKeyTier.PAID) append("Google may use text sent on a free Gemini plan to improve its products, so your own prayers and reflections stay on your phone unless you confirm a paid plan in Settings. ")
    append("You can use your phone's voice instead.")
}

@Composable
private fun SetupContent(
    offer: ReadOffer?,
    settings: ReadToMeSettings,
    startFrom: String?,
    onStartFrom: (String?) -> Unit,
    change: ((ReadToMeSettings) -> ReadToMeSettings) -> Unit,
    onStart: (resume: Boolean) -> Unit,
    onBackground: () -> Unit,
    backgroundQueued: Boolean
) {
    if (offer == null) { Text("Looking at the note…", style = MM.type.secondary, color = MM.colors.inkSecondary); return }
    val provider = offer.provider
    when {
        offer.locked -> { StatusLine(StatusKind.Info, "This note is locked. Unlock your Faith notes to listen to it."); return }
        offer.empty -> { StatusLine(StatusKind.Info, "There's nothing to read yet. Images, embeds and empty sections aren't read."); return }
    }

    Text(
        when (provider) {
            SpeechProvider.GEMINI -> "Voice: Gemini. The note's text is sent to Google to make the audio."
            SpeechProvider.LOCAL -> "Voice: your phone. Nothing leaves this phone."
            null -> "Voice: you'll choose when you start."
        },
        style = MM.type.bodyStrong, color = MM.colors.ink
    )
    offer.reason?.let { StatusLine(StatusKind.Info, it) }
    if (provider != null) Text(DeliveryResolver.capability(provider), style = MM.type.caption, color = MM.colors.inkMuted)
    if (provider == SpeechProvider.GEMINI || provider == SpeechProvider.LOCAL) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Always use my phone's voice", style = MM.type.body, color = MM.colors.ink, modifier = Modifier.weight(1f))
            Switch(checked = settings.prefersLocal, onCheckedChange = { v -> change { it.copy(prefersLocal = v) } })
        }
    }
    if (offer.skippedCount > 0) Text("${offer.skippedCount} items such as images or embeds aren't read.", style = MM.type.caption, color = MM.colors.inkMuted)

    Text("Wording", style = MM.type.bodyStrong, color = MM.colors.ink)
    SegmentedControl(listOf("Smart Read", "Read Exactly"), if (settings.smartRead) 0 else 1, { i -> change { it.copy(smartRead = i == 0) } })
    Text(
        when {
            !settings.smartRead -> "Reads your words exactly as written."
            provider == SpeechProvider.GEMINI && settings.disclosureAccepted -> "Smoother wording for listening. Scripture, quotes, transcripts and your own prayers and notes are never reworded."
            else -> "Smoother wording for listening. Without the Gemini voice it only spells out shorthand such as “e.g.”. Scripture, quotes and your own writing are never reworded."
        },
        style = MM.type.caption, color = MM.colors.inkMuted
    )

    if (offer.sections.size > 1) {
        Text("Start from", style = MM.type.bodyStrong, color = MM.colors.ink)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
            MMChip("Beginning", selected = startFrom == null, onClick = { onStartFrom(null) })
            offer.sections.forEach { (id, title) -> MMChip(title.ifBlank { "Untitled" }.take(28), selected = startFrom == id, onClick = { onStartFrom(id) }) }
        }
    }

    Text("How it sounds", style = MM.type.bodyStrong, color = MM.colors.ink)
    val faith = offer.space == NotebookSpace.FAITH
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
        DeliveryResolver.offered(faith).forEach { preset ->
            MMChip(preset.label, selected = settings.preset == preset, onClick = { change { it.copy(preset = preset) } })
        }
    }
    if (settings.preset == DeliveryPreset.AUTO) Text("Automatic uses the style you usually pick for this kind of note, or a plain, neutral one.", style = MM.type.caption, color = MM.colors.inkMuted)
    SegmentedControl(PACES.map { it.second }, paceIndex(settings.pace), { i -> change { it.copy(pace = PACES[i].first) } })

    if (faith) {
        Text("Scripture and study", style = MM.type.bodyStrong, color = MM.colors.ink)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Read verse numbers", style = MM.type.body, color = MM.colors.ink, modifier = Modifier.weight(1f))
            Switch(checked = settings.readVerseNumbers, onCheckedChange = { v -> change { it.copy(readVerseNumbers = v) } })
        }
        Text("After a reflection question", style = MM.type.secondary, color = MM.colors.inkSecondary)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
            PauseAfterQuestion.entries.forEach { p -> MMChip(p.label, selected = settings.pauseAfterQuestion == p, onClick = { change { it.copy(pauseAfterQuestion = p) } }) }
        }
    }

    if (offer.savedTotal > 0) {
        val madeAt = offer.savedMadeAt?.let { android.text.format.DateUtils.getRelativeTimeSpanString(it).toString() }
        StatusLine(
            StatusKind.Info,
            when {
                offer.savedReady == offer.savedTotal -> "Saved reading ready${madeAt?.let { " · made $it" }.orEmpty()}. It plays without a new voice request."
                else -> "Saved reading: ${offer.savedReady} of ${offer.savedTotal} parts are current. The rest are made when you listen."
            }
        )
    }
    PrimaryButton("Listen", onClick = { onStart(false) }, modifier = Modifier.fillMaxWidth())
    if (offer.hasSavedPlace) TextAction("Continue where I left off", onClick = { onStart(true) })
    if (backgroundQueued) {
        StatusLine(StatusKind.Info, "Making it in the background. It keeps going if you close this, and you'll get a note when it's ready.")
    } else {
        SecondaryButton("Make it in the background", onClick = onBackground, modifier = Modifier.fillMaxWidth())
    }
}

/** "See what changed": what Smart Read did to the wording, and anything it left as written and why. */
@Composable
private fun ChangesSection(noteId: String) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val changes = remember(noteId) { com.craftflowtechnologies.meetingmind.ai.voice.read.smart.ScriptStore(com.craftflowtechnologies.meetingmind.ai.voice.read.ReadingStore.get(context).scriptFile(noteId)).all() }
    val shown = changes.filter { it.kind != com.craftflowtechnologies.meetingmind.ai.voice.read.smart.ChangeKind.AS_WRITTEN || it.reason != null }
    if (shown.isEmpty()) return
    val reworded = shown.count { it.kind != com.craftflowtechnologies.meetingmind.ai.voice.read.smart.ChangeKind.AS_WRITTEN }
    TextAction(if (open) "Hide what changed" else "See what changed ($reworded reworded, ${shown.size - reworded} read as written)", onClick = { open = !open })
    if (open) Column(verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
        shown.forEach { c ->
            val asWritten = c.kind == com.craftflowtechnologies.meetingmind.ai.voice.read.smart.ChangeKind.AS_WRITTEN
            Text(
                if (asWritten) "Read as written${c.reason?.let { " ($it)" }.orEmpty()}" else if (c.kind == com.craftflowtechnologies.meetingmind.ai.voice.read.smart.ChangeKind.SPOKEN_FORM) "Spelled out" else "Reworded",
                style = MM.type.caption, color = MM.colors.accent
            )
            Text("Your words: ${c.before}", style = MM.type.secondary, color = MM.colors.inkSecondary)
            if (!asWritten) Text("Read as: ${c.after}", style = MM.type.secondary, color = MM.colors.ink)
        }
    }
}
