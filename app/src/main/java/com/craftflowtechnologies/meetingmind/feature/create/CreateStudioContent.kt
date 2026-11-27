package com.craftflowtechnologies.meetingmind.feature.create

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.core.circles2.Circle
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.companion.CreateMode
import com.craftflowtechnologies.meetingmind.core.create.CompanionAnchor
import com.craftflowtechnologies.meetingmind.core.create.CompanionPlacement
import com.craftflowtechnologies.meetingmind.core.create.CreateBackdrops
import com.craftflowtechnologies.meetingmind.core.create.CreateBackground
import com.craftflowtechnologies.meetingmind.core.create.CreateCardStyle
import com.craftflowtechnologies.meetingmind.core.create.CreateDesign
import com.craftflowtechnologies.meetingmind.core.create.CreateFontPair
import com.craftflowtechnologies.meetingmind.core.create.CreateFormat
import com.craftflowtechnologies.meetingmind.core.create.CreateSourceKind
import com.craftflowtechnologies.meetingmind.core.create.CreateStarter
import com.craftflowtechnologies.meetingmind.core.create.CreateStarters
import com.craftflowtechnologies.meetingmind.core.create.CreateVibe
import com.craftflowtechnologies.meetingmind.core.create.CreateViralHooks
import com.craftflowtechnologies.meetingmind.core.create.CreateViralPreset
import com.craftflowtechnologies.meetingmind.core.create.CreateViralPresets
import com.craftflowtechnologies.meetingmind.core.create.RemixAction
import com.craftflowtechnologies.meetingmind.core.share.BackgroundPack
import com.craftflowtechnologies.meetingmind.core.share.ShareTarget
import com.craftflowtechnologies.meetingmind.core.ui.mm.InsetPanel
import com.craftflowtechnologies.meetingmind.core.ui.mm.LocalAiOpenSettings
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SegmentedControl
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.core.ui.mm.looksUnconfiguredAiError
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/** What the studio can ask for. Tests and previews pass [None]. */
data class CreateActions(
    val onClose: () -> Unit = {},
    val onGallery: () -> Unit = {},
    val onStep: (CreateStep) -> Unit = {},
    val onPrompt: (String) -> Unit = {},
    val onSource: (CreateSourceKind) -> Unit = {},
    val onVibe: (CreateVibe) -> Unit = {},
    val onWrite: () -> Unit = {},
    val onOption: (Int) -> Unit = {},
    val onStatusAction: (StatusAction) -> Unit = {},
    val onStarter: (CreateStarter) -> Unit = {},
    val onRemix: (RemixAction) -> Unit = {},
    val onUndo: () -> Unit = {},
    val onEdit: (String) -> Unit = {},
    val onVerseInput: (String) -> Unit = {},
    val onVerseCommit: () -> Unit = {},
    val onFormat: (CreateFormat) -> Unit = {},
    val onDesign: (CreateDesign) -> Unit = {},
    val onBackground: (CreateBackground) -> Unit = {},
    val onPickPhoto: () -> Unit = {},
    val onShare: (ShareTarget) -> Unit = {},
    val onCopy: () -> Unit = {},
    /** Set when the card is being made for somewhere else (a circle's chat): the share step leads with this. */
    val sendLabel: String? = null,
    val onSend: (() -> Unit)? = null,
    /** Joined Circles available from standalone Share step. */
    val joinedCircles: List<Circle> = emptyList(),
    /** Sends the card directly to a chosen Circle as either an interactive Chat Card or Feed Post. */
    val onShareToCircle: (circle: Circle, postToFeed: Boolean) -> Unit = { _, _ -> },
    /** One-tap curated viral aesthetic preset. */
    val onApplyPreset: (CreateViralPreset) -> Unit = {},
    /** Copies viral caption (copy + hashtags + app link). */
    val onCopyCaption: () -> Unit = {}
) { companion object { val None = CreateActions() } }

/**
 * The companion offered on the card (§5.3). Null hides the switch. [allowed] are the only poses offered
 * (disallowed ones are hidden, not greyed); [mode] is the pose drawn and exported; [fellBack] is an override
 * the card no longer allows, shown as a quiet line.
 */
data class CompanionOffer(
    val name: String,
    val poseLabel: String,
    val form: CompanionForm? = null,
    val mode: CreateMode? = null,
    val suggested: CreateMode? = null,
    val allowed: List<CreateMode> = emptyList(),
    val fellBack: CreateMode? = null,
    val prayerRequest: Boolean = false,
    val sacred: Boolean = false
)

/**
 * Redesigned high-craft Create Studio: live preview canvas above a fluid four-step deck
 * (Words, Format, Design, Share), with one-tap viral card styles & presets and direct Circle sharing.
 */
@Composable
fun CreateStudioContent(
    ui: CreateUi,
    preview: ImageBitmap?,
    fits: Boolean,
    companion: CompanionOffer?,
    actions: CreateActions,
    modifier: Modifier = Modifier,
    /** Drawn over the preview in card pixels × [scale] (the companion's reveal). */
    previewOverlay: @Composable (scale: Float) -> Unit = {}
) {
    val c = MM.colors
    // The platform chrome on each format, shown on the preview while designing (§5.2).
    var safeZones by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Redesigned Studio Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MM.space.s, vertical = MM.space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = actions.onClose) {
                Icon(Icons.Rounded.Close, "Close", tint = c.inkSecondary)
            }
            Column(Modifier.weight(1f)) {
                Text("Create Studio", style = MM.type.heading.copy(fontFamily = FontFamily.Serif), color = c.ink)
                Text(
                    "${ui.card.vibe.label} · ${ui.card.format.label}",
                    style = MM.type.caption,
                    color = c.inkMuted
                )
            }
            if (ui.card.canUndo) {
                Surface(
                    onClick = actions.onUndo,
                    shape = MM.radius.pill,
                    color = c.surfaceSunk,
                    border = BorderStroke(MMSize.hairline, c.lineSoft)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = MM.space.s, vertical = MM.space.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                    ) {
                        Icon(Icons.Rounded.Undo, contentDescription = "Undo", tint = c.ink, modifier = Modifier.size(14.dp))
                        Text("Undo", style = MM.type.caption, color = c.ink)
                    }
                }
                Spacer(Modifier.width(MM.space.xs))
            }
            Surface(
                onClick = actions.onGallery,
                shape = MM.radius.pill,
                color = c.surfaceSunk,
                border = BorderStroke(MMSize.hairline, c.lineSoft)
            ) {
                Text(
                    "My creations",
                    style = MM.type.caption,
                    fontWeight = FontWeight.Medium,
                    color = c.accent,
                    modifier = Modifier.padding(horizontal = MM.space.m, vertical = MM.space.xs)
                )
            }
            Spacer(Modifier.width(MM.space.xs))
        }

        // Showcase Canvas Stage
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = MM.space.l, vertical = MM.space.xs),
            contentAlignment = Alignment.Center
        ) {
            val ratio = ui.card.format.ratio
            Surface(
                shape = MM.radius.card,
                color = c.surfaceSunk,
                shadowElevation = 6.dp,
                border = BorderStroke(MMSize.hairline, c.lineSoft),
                modifier = Modifier
                    .aspectRatio(ratio, matchHeightConstraintsFirst = ratio < 1f)
                    .semantics { contentDescription = "Card preview" }
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (preview != null) {
                        Image(preview, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    } else {
                        Box(Modifier.fillMaxSize().background(c.surfaceSunk), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(MMSize.icon), strokeWidth = 2.dp, color = c.inkMuted)
                        }
                    }
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val scale = with(LocalDensity.current) { maxWidth.toPx() } / ui.card.format.width
                        previewOverlay(scale)
                        if (safeZones && ui.step == CreateStep.DESIGN) {
                            SafeZones(
                                ui.card.format,
                                ui.card.design.includeCompanion && ui.card.design.companionAnchor == CompanionAnchor.CORNER_END
                            )
                        }
                    }
                }
            }
        }

        // Studio Bottom Deck
        Surface(
            shape = MM.radius.sheet,
            color = c.surfaceRaised,
            border = BorderStroke(MMSize.hairline, c.lineSoft),
            shadowElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
        ) {
            Column(Modifier.fillMaxWidth()) {
                SegmentedControl(
                    options = CreateStep.entries.map { it.label },
                    selectedIndex = ui.step.ordinal,
                    onSelect = { actions.onStep(CreateStep.entries[it]) },
                    modifier = Modifier.padding(horizontal = MM.space.l, vertical = MM.space.s)
                )
                HorizontalDivider(color = c.lineSoft)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 390.dp)
                        .verticalScroll(rememberScrollState())
                        .animateContentSize()
                        .padding(horizontal = MM.space.l, vertical = MM.space.m),
                    verticalArrangement = Arrangement.spacedBy(MM.space.m)
                ) {
                    when (ui.step) {
                        CreateStep.WORDS -> WordsStep(ui, actions)
                        CreateStep.FORMAT -> FormatStep(ui, fits, safeZones, actions) { safeZones = it }
                        CreateStep.DESIGN -> DesignStep(ui, companion, actions, safeZones) { safeZones = it }
                        CreateStep.SHARE -> ShareStep(ui, companion != null, actions)
                    }
                }
            }
        }
    }
}

// ───────────────────────────── Words ─────────────────────────────

@Composable
private fun WordsStep(ui: CreateUi, a: CreateActions) {
    val card = ui.card
    val kinds = listOf(card.source, CreateSourceKind.FREE_PROMPT, CreateSourceKind.VERSE).distinct()
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
        kinds.forEach { k -> MMChip(k.label, selected = k == card.source, onClick = { a.onSource(k) }) }
    }

    TextBox(
        value = ui.prompt, onChange = a.onPrompt, minLines = 2,
        hint = when (card.source) {
            CreateSourceKind.VERSE -> "Choose a verse below, or add a thought to go with it"
            CreateSourceKind.FREE_PROMPT -> "An idea: “Something honest about slowing down”"
            else -> "The words to work from"
        },
        label = "Source text"
    )

    SectionLabel("Mood")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
        ui.vibes.forEach { v ->
            MMChip(
                if (v == ui.suggested && v != card.vibe) "${v.label} · suggested" else v.label,
                selected = v == card.vibe,
                onClick = { a.onVibe(v) }
            )
        }
    }
    if (card.source == CreateSourceKind.PRAYER) {
        Text("Suggested: Prayerful. On prayer requests, only Prayerful or Peaceful.", style = MM.type.caption, color = MM.colors.inkMuted)
    } else {
        Text("Suggested: ${ui.suggested.label}. You can change it.", style = MM.type.caption, color = MM.colors.inkMuted)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s), verticalAlignment = Alignment.CenterVertically) {
        PrimaryButton(if (ui.working == "Writing") "Writing…" else "Write it for me", onClick = a.onWrite, enabled = ui.canWrite)
        if (ui.starters == null && card.text.isBlank()) {
            SecondaryButton("Ideas", onClick = { a.onStatusAction(StatusAction.STARTERS) })
        }
    }

    ui.status?.let { s ->
        val openSettings = LocalAiOpenSettings.current
        val fallbackSettings = s.action == null && s.tone == StatusTone.Error && openSettings != null && looksUnconfiguredAiError(s.text)
        val actionLabel = s.action?.label ?: if (fallbackSettings) "AI settings" else null
        val onAction: (() -> Unit)? = if (s.action != null) {
            { a.onStatusAction(s.action) }
        } else if (fallbackSettings) {
            openSettings
        } else {
            null
        }
        StatusLine(
            kind = if (s.tone == StatusTone.Error) StatusKind.Error else StatusKind.Info,
            text = s.text,
            actionLabel = actionLabel,
            onAction = onAction
        )
    }

    ui.starters?.let { starters ->
        Text(CreateStarters.LABEL, style = MM.type.caption, color = MM.colors.inkMuted)
        starters.forEach { st -> OptionRow(st.text, st.verseRef, selected = false, onClick = { a.onStarter(st) }) }
    }

    if (ui.options.size > 1) {
        SectionLabel("Other versions")
        ui.options.forEachIndexed { i, o ->
            OptionRow(o.text, o.scripture?.reference, selected = i == ui.chosenOption, onClick = { a.onOption(i) })
        }
    }

    SectionLabel("Hook tag at top of card (optional)")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
        val currentHook = card.design.eyebrow.orEmpty()
        MMChip("None", selected = currentHook.isBlank(), onClick = {
            a.onDesign(card.design.copy(eyebrow = null))
        })
        CreateViralHooks.forVibe(card.vibe).forEach { hook ->
            val selected = currentHook.equals(hook, ignoreCase = true)
            MMChip(hook, selected = selected, onClick = {
                a.onDesign(
                    card.design.copy(
                        eyebrow = hook,
                        style = if (card.design.style == CreateCardStyle.CLEAN) CreateCardStyle.BADGE_HOOK else card.design.style
                    )
                )
            })
        }
    }
    TextBox(
        value = card.design.eyebrow.orEmpty(),
        onChange = { next ->
            a.onDesign(card.design.copy(eyebrow = next.take(36).ifBlank { null }))
        },
        minLines = 1,
        singleLine = true,
        hint = "Custom hook badge (e.g. WORD FOR TODAY)",
        label = "Hook badge"
    )

    SectionLabel("Your card — write your own, or edit")
    TextBox(value = card.text, onChange = a.onEdit, minLines = 3, hint = "Write the words for your card", label = "Card text")
    TextBox(
        value = ui.verseInput, onChange = a.onVerseInput, minLines = 1, hint = "Verse (optional), e.g. John 3:16",
        label = "Verse reference", singleLine = true, onDone = a.onVerseCommit
    )
    ui.verseError?.let { StatusLine(StatusKind.Warning, it) }
    ui.scripture?.let { Text("${it.reference} · ${it.versionAbbreviation} — text from your Bible", style = MM.type.caption, color = MM.colors.inkMuted) }

    SectionLabel("Remix")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MM.space.s), verticalAlignment = Alignment.CenterVertically) {
        RemixAction.entries.filter { it.allowedFor(card.source) }.forEach { r ->
            MMChip(
                label = if (ui.working == r.label) "${r.label}…" else r.label,
                selected = false,
                enabled = ui.canWrite,
                onClick = { a.onRemix(r) }
            )
        }
        if (card.canUndo) TextAction("Undo", a.onUndo)
    }
}

@Composable
private fun OptionRow(text: String, verse: String?, selected: Boolean, onClick: () -> Unit) {
    val c = MM.colors
    Surface(
        onClick = onClick, shape = MM.radius.small, color = if (selected) c.accentWash else c.surfaceSunk,
        border = if (selected) BorderStroke(MMSize.hairline, c.accent) else null, modifier = Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch)
    ) {
        Column(Modifier.padding(MM.space.m)) {
            Text(text, style = MM.type.body, color = c.ink)
            if (verse != null) Text(verse, style = MM.type.caption, color = c.inkMuted)
        }
    }
}

// ───────────────────────────── Format ─────────────────────────────

@Composable
private fun FormatStep(
    ui: CreateUi,
    fits: Boolean,
    safeZones: Boolean,
    a: CreateActions,
    onSafeZones: (Boolean) -> Unit
) {
    val c = MM.colors
    SectionLabel("Choose platform frame")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MM.space.s)
    ) {
        CreateFormat.entries.forEach { f ->
            val selected = ui.card.format == f
            Surface(
                onClick = { a.onFormat(f) },
                shape = MM.radius.small,
                color = if (selected) c.accentWash else c.surfaceSunk,
                border = BorderStroke(if (selected) 1.5.dp else MMSize.hairline, if (selected) c.accent else c.lineSoft),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(MM.space.m),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(MM.space.xs)
                ) {
                    val previewBoxModifier = when (f) {
                        CreateFormat.STORY -> Modifier.size(18.dp, 32.dp)
                        CreateFormat.PORTRAIT -> Modifier.size(22.dp, 28.dp)
                        CreateFormat.SQUARE -> Modifier.size(26.dp, 26.dp)
                    }
                    Box(
                        previewBoxModifier
                            .clip(MM.radius.small)
                            .background(if (selected) c.accent.copy(alpha = 0.22f) else c.track)
                            .border(1.dp, if (selected) c.accent else c.line, MM.radius.small)
                    )
                    Text(f.label, style = MM.type.caption, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = c.ink)
                }
            }
        }
    }
    Text(ui.card.format.hint, style = MM.type.secondary, color = c.inkSecondary)
    if (ui.card.format == CreateFormat.STORY) {
        Text("Words stay clear of the bars WhatsApp status and Instagram stories draw at the top and bottom.", style = MM.type.caption, color = c.inkMuted)
    }
    if (!fits) StatusLine(StatusKind.Warning, "That passage is too long for this card. Choose a shorter verse range, or a taller format.")
    ToggleRow("Preview safe margins", "See where WhatsApp & Story chrome overlays top/bottom bars", safeZones, onSafeZones)
}

// ───────────────────────────── Design ─────────────────────────────

@Composable
private fun DesignStep(ui: CreateUi, companion: CompanionOffer?, a: CreateActions, safeZones: Boolean = false, onSafeZones: (Boolean) -> Unit = {}) {
    val d = ui.card.design
    val c = MM.colors

    SectionLabel("Viral card presets")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
        items(CreateViralPresets.all, key = { it.id }) { preset ->
            val active = (d.background as? CreateBackground.Pack)?.id == preset.packId && d.style == preset.style
            PresetTile(preset = preset, selected = active) {
                a.onApplyPreset(preset)
            }
        }
    }

    SectionLabel("Card frame & style")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
        CreateCardStyle.entries.forEach { st ->
            MMChip(st.label, selected = d.style == st, onClick = { a.onDesign(d.copy(style = st)) })
        }
    }
    Text(d.style.hint, style = MM.type.caption, color = c.inkMuted)

    SectionLabel("Background")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
        item {
            Box(
                Modifier.size(56.dp, 76.dp).clip(MM.radius.small).background(c.surfaceSunk).clickable(role = Role.Button, onClick = a.onPickPhoto)
                    .semantics { contentDescription = "Use your own photo" },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.AddPhotoAlternate, null, tint = c.inkSecondary) }
        }
        if (d.background is CreateBackground.Photo) item { PhotoMarker() }
        items(CreateBackdrops.forVibe(ui.card.vibe) + CreateBackdrops.more(ui.card.vibe)) { id ->
            PackTile(id, selected = (d.background as? CreateBackground.Pack)?.id == id) { a.onBackground(CreateBackground.Pack(id)) }
        }
    }
    Text("Backgrounds are tuned to ${ui.card.vibe.label.lowercase()}. Text colour adjusts to your photo.", style = MM.type.caption, color = c.inkMuted)

    SectionLabel("Type")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
        CreateFontPair.entries.forEach { f -> MMChip(f.label, selected = d.fontPair == f, onClick = { a.onDesign(d.copy(fontPair = f)) }) }
    }
    SegmentedControl(listOf("Left", "Centre"), if (d.centered) 1 else 0, { a.onDesign(d.copy(centered = it == 1)) })
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Size", style = MM.type.secondary, color = c.inkSecondary, modifier = Modifier.width(56.dp))
        Slider(
            value = d.textScale, onValueChange = { a.onDesign(d.copy(textScale = it)) }, valueRange = 0.7f..1.4f, modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.track)
        )
    }
    if (companion != null) {
        ToggleRow(
            stringResource(R.string.companion_moment_card_include, companion.name),
            if (d.includeCompanion) poseLine(companion) else stringResource(R.string.companion_moment_card_suggested, companion.suggested?.label() ?: companion.poseLabel),
            d.includeCompanion
        ) { a.onDesign(d.copy(includeCompanion = it)) }
        if (d.includeCompanion) CompanionChoices(ui, companion, a)
    }
    ToggleRow(stringResource(R.string.companion_moment_card_safe_zones), ui.card.format.hint, safeZones, onSafeZones)
}

@Composable
private fun PresetTile(preset: CreateViralPreset, selected: Boolean, onClick: () -> Unit) {
    val c = MM.colors
    val bg = remember(preset.packId) { BackgroundPack.byId(preset.packId) }
    Surface(
        onClick = onClick,
        shape = MM.radius.small,
        color = if (selected) c.accentWash else c.surfaceSunk,
        border = BorderStroke(if (selected) 1.5.dp else MMSize.hairline, if (selected) c.accent else c.lineSoft)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MM.space.s, vertical = MM.space.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MM.space.s)
        ) {
            Canvas(Modifier.size(22.dp, 28.dp).clip(MM.radius.small)) {
                drawIntoCanvas { BackgroundPack.draw(it.nativeCanvas, bg, size.width.toInt(), size.height.toInt()) }
            }
            Column {
                Text(preset.label, style = MM.type.caption, fontWeight = FontWeight.SemiBold, color = c.ink)
                Text(preset.style.label, style = MM.type.caption, color = c.inkMuted)
            }
        }
    }
}

/** "Suggested: Peaceful", or "Suggested: Peaceful · you chose Reflective" after an override (§5.3). */
@Composable
private fun poseLine(o: CompanionOffer): String {
    val suggested = o.suggested ?: return o.poseLabel
    val mode = o.mode ?: suggested
    return if (mode == suggested) stringResource(R.string.companion_moment_card_suggested, suggested.label())
    else stringResource(R.string.companion_moment_card_suggested_chose, suggested.label(), mode.label())
}

/**
 * The pose chips (only the allowed poses: the rest are hidden, not greyed), the restriction line when the set
 * is restricted, the quiet fallback line, the anchor chips, and on a prayer request the words promise.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompanionChoices(ui: CreateUi, o: CompanionOffer, a: CreateActions) {
    val d = ui.card.design
    FlowRow(horizontalArrangement = Arrangement.spacedBy(MM.space.s), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        o.allowed.forEach { m ->
            MMChip(
                m.label(), selected = o.mode == m,
                // Choosing the suggestion clears the override, so the pose follows the vibe again.
                onClick = { a.onDesign(d.copy(companionPose = if (m == o.suggested) null else m)) },
                modifier = Modifier.testTag("pose_${m.name}")
            )
        }
    }
    val restriction = when {
        o.prayerRequest -> stringResource(R.string.companion_moment_card_prayer_only)
        o.sacred && o.allowed.size < CreateMode.entries.size -> stringResource(R.string.companion_moment_card_scripture_calm, o.name)
        else -> null
    }
    restriction?.let { Text(it, style = MM.type.caption, color = MM.colors.inkMuted) }
    o.fellBack?.let { Text(stringResource(R.string.companion_moment_card_not_offered, it.label()), style = MM.type.caption, color = MM.colors.inkMuted) }
    if (o.prayerRequest) Text(stringResource(R.string.companion_moment_card_words_promise), style = MM.type.caption, color = MM.colors.inkMuted)
    Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
        CompanionPlacement.anchors(ui.card.format).forEach { anchor ->
            MMChip(
                stringResource(if (anchor == CompanionAnchor.BELOW) R.string.companion_moment_card_anchor_below else R.string.companion_moment_card_anchor_corner),
                selected = d.companionAnchor == anchor, onClick = { a.onDesign(d.copy(companionAnchor = anchor)) },
                modifier = Modifier.testTag("anchor_${anchor.name}")
            )
        }
    }
}

/** The safe-zone overlay: the platform chrome bands (§5.2), drawn over the preview. Decorative. */
@Composable
private fun SafeZones(format: CreateFormat, corner: Boolean) {
    val band = MM.colors.danger.copy(alpha = 0.16f)
    val edge = MM.colors.danger.copy(alpha = 0.55f)
    Canvas(Modifier.fillMaxSize().testTag("safe_zones")) {
        val r = CompanionPlacement.safeRect(
            format, size.width, size.height,
            if (corner) CompanionAnchor.CORNER_END else CompanionAnchor.BELOW
        )
        drawRect(band, size = androidx.compose.ui.geometry.Size(size.width, r.top))
        drawRect(band, topLeft = androidx.compose.ui.geometry.Offset(0f, r.bottom), size = androidx.compose.ui.geometry.Size(size.width, size.height - r.bottom))
        drawRect(band, topLeft = androidx.compose.ui.geometry.Offset(0f, r.top), size = androidx.compose.ui.geometry.Size(r.left, r.height))
        drawRect(band, topLeft = androidx.compose.ui.geometry.Offset(r.right, r.top), size = androidx.compose.ui.geometry.Size(size.width - r.right, r.height))
        drawRect(
            edge, topLeft = androidx.compose.ui.geometry.Offset(r.left, r.top), size = androidx.compose.ui.geometry.Size(r.width, r.height),
            style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
        )
    }
}

@Composable
private fun PhotoMarker() {
    Box(
        Modifier.size(56.dp, 76.dp).clip(MM.radius.small).background(MM.colors.surfaceSunk).border(2.dp, MM.colors.accent, MM.radius.small),
        contentAlignment = Alignment.Center
    ) { Text("Photo", style = MM.type.caption, color = MM.colors.accent) }
}

@Composable
private fun PackTile(id: String, selected: Boolean, onClick: () -> Unit) {
    val bg = BackgroundPack.byId(id)
    Canvas(
        Modifier.size(56.dp, 76.dp).clip(MM.radius.small)
            .then(if (selected) Modifier.border(2.dp, MM.colors.accent, MM.radius.small) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = bg.name + if (selected) ", selected" else "" }
    ) { drawIntoCanvas { BackgroundPack.draw(it.nativeCanvas, bg, size.width.toInt(), size.height.toInt()) } }
}

// ───────────────────────────── Share ─────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareStep(ui: CreateUi, hasCompanion: Boolean, a: CreateActions) {
    val d = ui.card.design
    val c = MM.colors
    var circlePickerOpen by remember { mutableStateOf(false) }

    val send = a.onSend
    val primaryTaken = send != null
    if (send != null) {
        PrimaryButton("Send to ${a.sendLabel ?: "the chat"}", onClick = send, enabled = ui.canWrite, modifier = Modifier.fillMaxWidth())
    }

    // Direct Share to Circles — available on every created card!
    Surface(
        onClick = { circlePickerOpen = true },
        enabled = ui.canWrite,
        shape = MM.radius.card,
        color = if (primaryTaken) c.surfaceSunk else c.accentWash,
        border = BorderStroke(1.dp, c.accent.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth().testTag("create_share_circle")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MM.space.m, vertical = MM.space.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MM.space.s)
        ) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Groups, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Share to a Circle", style = MM.type.bodyStrong, color = c.ink)
                Text(
                    if (a.joinedCircles.isEmpty()) "Send card directly to your Circle chat or feed"
                    else "Send as an interactive card to any of your ${a.joinedCircles.size} Circles",
                    style = MM.type.caption,
                    color = c.inkSecondary
                )
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
        }
    }

    if (ui.installed.whatsapp) {
        SecondaryButton("WhatsApp status", onClick = { a.onShare(ShareTarget.WHATSAPP) }, enabled = ui.canWrite, modifier = Modifier.fillMaxWidth(), leadingIcon = Icons.Rounded.Share)
    }
    if (ui.installed.instagram) {
        SecondaryButton("Instagram story", onClick = { a.onShare(ShareTarget.INSTAGRAM_STORY) }, enabled = ui.canWrite, modifier = Modifier.fillMaxWidth(), leadingIcon = Icons.Rounded.Share)
    }
    val anyDirect = ui.installed.whatsapp || ui.installed.instagram || primaryTaken
    if (anyDirect) {
        SecondaryButton("Share elsewhere…", onClick = { a.onShare(ShareTarget.ANY) }, enabled = ui.canWrite, modifier = Modifier.fillMaxWidth())
    } else {
        PrimaryButton("Share image…", onClick = { a.onShare(ShareTarget.ANY) }, enabled = ui.canWrite, modifier = Modifier.fillMaxWidth(), leadingIcon = Icons.Rounded.Share)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
        SecondaryButton("Save image", onClick = { a.onShare(ShareTarget.SAVE) }, enabled = ui.canWrite, modifier = Modifier.weight(1f), leadingIcon = Icons.Rounded.Download)
        SecondaryButton("Copy caption", onClick = a.onCopyCaption, modifier = Modifier.weight(1f), leadingIcon = Icons.Rounded.ContentCopy)
    }
    TextAction("Copy card words only", onClick = a.onCopy)

    ToggleRow("Add “Made with MeetingMind”", "A small mark at the foot of the card. Off by default.", d.watermark) { a.onDesign(d.copy(watermark = it)) }
    if (ui.card.format != CreateFormat.STORY) {
        Text("For a status or story, the Story format (9:16) fits best.", style = MM.type.caption, color = c.inkMuted)
    }
    Text("Every card is kept in My creations.", style = MM.type.caption, color = c.inkMuted)
    ui.message?.let { StatusLine(StatusKind.Info, it) }

    if (circlePickerOpen) {
        CreateCircleShareSheet(
            circles = a.joinedCircles,
            onDismiss = { circlePickerOpen = false },
            onShare = { circle, postToFeed ->
                circlePickerOpen = false
                a.onShareToCircle(circle, postToFeed)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateCircleShareSheet(
    circles: List<Circle>,
    onDismiss: () -> Unit,
    onShare: (circle: Circle, postToFeed: Boolean) -> Unit
) {
    val c = MM.colors
    var postToFeed by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceRaised,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = MM.space.l, vertical = MM.space.l),
            verticalArrangement = Arrangement.spacedBy(MM.space.m)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Groups, contentDescription = null, tint = c.accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(MM.space.s))
                Column(Modifier.weight(1f)) {
                    Text("Share Card to Circle", style = MM.type.heading, color = c.ink)
                    Text("Choose where your card goes", style = MM.type.caption, color = c.inkSecondary)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = c.inkSecondary)
                }
            }

            SegmentedControl(
                options = listOf("Circle chat card", "Circle feed post"),
                selectedIndex = if (postToFeed) 1 else 0,
                onSelect = { postToFeed = it == 1 }
            )

            Text(
                if (postToFeed) "Publishes your card words and scripture reference as a post on the Circle feed."
                else "Sends an interactive rendered card directly inside the Circle's chat.",
                style = MM.type.caption,
                color = c.inkSecondary
            )

            if (circles.isEmpty()) {
                Surface(
                    shape = MM.radius.card,
                    color = c.surfaceSunk,
                    border = BorderStroke(MMSize.hairline, c.lineSoft),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(MM.space.l), verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                        Text("No Circles yet", style = MM.type.bodyStrong, color = c.ink)
                        Text("Join or create a Circle from the Circles tab first, then share cards directly here.", style = MM.type.secondary, color = c.inkSecondary)
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(MM.space.s)
                ) {
                    circles.forEach { circle ->
                        Surface(
                            onClick = { onShare(circle, postToFeed) },
                            shape = MM.radius.card,
                            color = c.surfaceSunk,
                            border = BorderStroke(MMSize.hairline, c.lineSoft),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = MM.space.m, vertical = MM.space.m),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (postToFeed) Icons.Rounded.Forum else Icons.Rounded.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = c.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(MM.space.s))
                                Column(Modifier.weight(1f)) {
                                    Text(circle.name, style = MM.type.bodyStrong, color = c.ink)
                                    Text("${circle.memberCount} members", style = MM.type.caption, color = c.inkSecondary)
                                }
                                Text(
                                    if (postToFeed) "Post" else "Send card",
                                    style = MM.type.caption,
                                    fontWeight = FontWeight.SemiBold,
                                    color = c.accent
                                )
                            }
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Close", color = c.ink) }
            }
        }
    }
}

// ───────────────────────────── Small parts ─────────────────────────────

@Composable
private fun SectionLabel(text: String) = Text(text, style = MM.type.caption, color = MM.colors.inkMuted, modifier = Modifier.padding(top = MM.space.xs))

@Composable
internal fun TextBox(
    value: String, onChange: (String) -> Unit, hint: String, label: String,
    minLines: Int = 1, singleLine: Boolean = false, onDone: (() -> Unit)? = null
) {
    InsetPanel(Modifier.semantics { contentDescription = label }) {
        Box {
            if (value.isEmpty()) Text(hint, style = MM.type.body, color = MM.colors.inkMuted)
            BasicTextField(
                value = value, onValueChange = onChange, minLines = minLines, singleLine = singleLine,
                textStyle = MM.type.body.copy(color = MM.colors.ink), cursorBrush = SolidColor(MM.colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = if (onDone != null) ImeAction.Done else ImeAction.Default),
                keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ToggleRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = MM.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch).toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MM.type.bodyStrong, color = c.ink)
            Text(body, style = MM.type.caption, color = c.inkMuted)
        }
        Spacer(Modifier.width(MM.space.m))
        Switch(
            checked = checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.onAccent, checkedTrackColor = c.accent,
                uncheckedThumbColor = c.inkMuted, uncheckedTrackColor = c.track, uncheckedBorderColor = c.line
            )
        )
    }
}
