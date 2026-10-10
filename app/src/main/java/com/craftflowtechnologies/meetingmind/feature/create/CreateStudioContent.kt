package com.craftflowtechnologies.meetingmind.feature.create

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.create.CreateBackdrops
import com.craftflowtechnologies.meetingmind.core.create.CreateBackground
import com.craftflowtechnologies.meetingmind.core.create.CreateFontPair
import com.craftflowtechnologies.meetingmind.core.create.CreateFormat
import com.craftflowtechnologies.meetingmind.core.create.CreateSourceKind
import com.craftflowtechnologies.meetingmind.core.create.CreateStarter
import com.craftflowtechnologies.meetingmind.core.create.CreateStarters
import com.craftflowtechnologies.meetingmind.core.create.CreateVibe
import com.craftflowtechnologies.meetingmind.core.create.RemixAction
import com.craftflowtechnologies.meetingmind.core.create.CreateDesign
import com.craftflowtechnologies.meetingmind.core.share.BackgroundPack
import com.craftflowtechnologies.meetingmind.core.share.ShareTarget
import com.craftflowtechnologies.meetingmind.core.ui.mm.InsetPanel
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.ScreenHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SegmentedControl
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
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
    val onCopy: () -> Unit = {}
) { companion object { val None = CreateActions() } }

/** The companion offered on the card: its name, and the pose its mood maps to. Null hides the switch. */
data class CompanionOffer(val name: String, val poseLabel: String)

/**
 * The studio, stateless: a live card above, four steps below (Words, Format, Design, Share).
 * [preview] is the card exactly as it will be sent; [fits] is false when the passage is too long.
 */
@Composable
fun CreateStudioContent(
    ui: CreateUi,
    preview: ImageBitmap?,
    fits: Boolean,
    companion: CompanionOffer?,
    actions: CreateActions,
    modifier: Modifier = Modifier
) {
    val c = MM.colors
    Column(modifier.fillMaxSize().background(MM.colors.background).statusBarsPadding().navigationBarsPadding()) {
        ScreenHeader(
            title = "Create",
            modifier = Modifier.padding(start = MM.space.s, end = MM.space.l),
            leading = { IconButton(onClick = actions.onClose) { Icon(Icons.Rounded.Close, "Close", tint = c.inkSecondary) } },
            actions = { TextAction("My creations", actions.onGallery) }
        )
        // The card, exactly as it will be sent.
        Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = MM.space.xl, vertical = MM.space.s), contentAlignment = Alignment.Center) {
            val ratio = ui.card.format.ratio
            Box(
                Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = ratio < 1f).clip(MM.radius.card)
                    .semantics { contentDescription = "Card preview" },
                contentAlignment = Alignment.Center
            ) {
                if (preview != null) Image(preview, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else Box(Modifier.fillMaxSize().background(MM.colors.surfaceSunk), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(MMSize.icon), strokeWidth = 2.dp, color = c.inkMuted)
                }
            }
        }
        Column(Modifier.fillMaxWidth().clip(MM.radius.sheet).background(MM.colors.surfaceRaised)) {
            SegmentedControl(
                options = CreateStep.entries.map { it.label },
                selectedIndex = ui.step.ordinal,
                onSelect = { actions.onStep(CreateStep.entries[it]) },
                modifier = Modifier.padding(horizontal = MM.space.l, vertical = MM.space.s)
            )
            Column(
                Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState())
                    .padding(horizontal = MM.space.l).padding(bottom = MM.space.l),
                verticalArrangement = Arrangement.spacedBy(MM.space.m)
            ) {
                when (ui.step) {
                    CreateStep.WORDS -> WordsStep(ui, actions)
                    CreateStep.FORMAT -> FormatStep(ui, fits, actions)
                    CreateStep.DESIGN -> DesignStep(ui, companion, actions)
                    CreateStep.SHARE -> ShareStep(ui, companion != null, actions)
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
            CreateSourceKind.FREE_PROMPT -> "An idea: “Something funny about Monday meetings”"
            else -> "The words to work from"
        },
        label = "Source text"
    )
    SectionLabel("Mood")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
        ui.vibes.forEach { v ->
            MMChip(if (v == ui.suggested && v != card.vibe) "${v.label} · suggested" else v.label, selected = v == card.vibe, onClick = { a.onVibe(v) })
        }
    }
    if (card.source == CreateSourceKind.PRAYER) {
        Text("Suggested: Prayerful. On prayer requests, only Prayerful or Peaceful.", style = MM.type.caption, color = MM.colors.inkMuted)
    } else {
        Text("Suggested: ${ui.suggested.label}. You can change it.", style = MM.type.caption, color = MM.colors.inkMuted)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s), verticalAlignment = Alignment.CenterVertically) {
        PrimaryButton(if (ui.working == "Writing") "Writing…" else "Write it for me", onClick = a.onWrite, enabled = ui.canWrite)
    }
    ui.status?.let { s ->
        StatusLine(
            kind = if (s.tone == StatusTone.Error) StatusKind.Error else StatusKind.Info, text = s.text,
            actionLabel = s.action?.label, onAction = s.action?.let { act -> { a.onStatusAction(act) } }
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
            MMChip(if (ui.working == r.label) "${r.label}…" else r.label, selected = false, onClick = { if (ui.canWrite) a.onRemix(r) })
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
private fun FormatStep(ui: CreateUi, fits: Boolean, a: CreateActions) {
    SegmentedControl(
        options = CreateFormat.entries.map { it.label }, selectedIndex = ui.card.format.ordinal,
        onSelect = { a.onFormat(CreateFormat.entries[it]) }
    )
    Text(ui.card.format.hint, style = MM.type.secondary, color = MM.colors.inkSecondary)
    if (ui.card.format == CreateFormat.STORY) {
        Text("Words stay clear of the bars WhatsApp status and Instagram stories draw at the top and bottom.", style = MM.type.caption, color = MM.colors.inkMuted)
    }
    if (!fits) StatusLine(StatusKind.Warning, "That passage is too long for this card. Choose a shorter verse range, or a taller format.")
}

// ───────────────────────────── Design ─────────────────────────────

@Composable
private fun DesignStep(ui: CreateUi, companion: CompanionOffer?, a: CreateActions) {
    val d = ui.card.design
    SectionLabel("Background")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
        item {
            Box(
                Modifier.size(56.dp, 76.dp).clip(MM.radius.small).background(MM.colors.surfaceSunk).clickable(role = Role.Button, onClick = a.onPickPhoto)
                    .semantics { contentDescription = "Use your own photo" },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.AddPhotoAlternate, null, tint = MM.colors.inkSecondary) }
        }
        if (d.background is CreateBackground.Photo) item { PhotoMarker() }
        items(CreateBackdrops.forVibe(ui.card.vibe) + CreateBackdrops.more(ui.card.vibe)) { id ->
            PackTile(id, selected = (d.background as? CreateBackground.Pack)?.id == id) { a.onBackground(CreateBackground.Pack(id)) }
        }
    }
    Text("Backgrounds are tuned to ${ui.card.vibe.label.lowercase()}. Text colour adjusts to your photo.", style = MM.type.caption, color = MM.colors.inkMuted)
    SectionLabel("Type")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
        CreateFontPair.entries.forEach { f -> MMChip(f.label, selected = d.fontPair == f, onClick = { a.onDesign(d.copy(fontPair = f)) }) }
    }
    SegmentedControl(listOf("Left", "Centre"), if (d.centered) 1 else 0, { a.onDesign(d.copy(centered = it == 1)) })
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Size", style = MM.type.secondary, color = MM.colors.inkSecondary, modifier = Modifier.width(56.dp))
        Slider(
            value = d.textScale, onValueChange = { a.onDesign(d.copy(textScale = it)) }, valueRange = 0.7f..1.4f, modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = MM.colors.accent, activeTrackColor = MM.colors.accent, inactiveTrackColor = MM.colors.track)
        )
    }
    if (companion != null) {
        ToggleRow("Include ${companion.name}", "Suggested pose: ${companion.poseLabel}. Off unless you want it.", d.includeCompanion) { a.onDesign(d.copy(includeCompanion = it)) }
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

@Composable
private fun ShareStep(ui: CreateUi, hasCompanion: Boolean, a: CreateActions) {
    val d = ui.card.design
    if (ui.installed.whatsapp) PrimaryButton("WhatsApp status", onClick = { a.onShare(ShareTarget.WHATSAPP) }, enabled = ui.canWrite, modifier = Modifier.fillMaxWidth(), leadingIcon = Icons.Rounded.Share)
    if (ui.installed.instagram) {
        val mod = Modifier.fillMaxWidth()
        if (ui.installed.whatsapp) SecondaryButton("Instagram story", onClick = { a.onShare(ShareTarget.INSTAGRAM_STORY) }, enabled = ui.canWrite, modifier = mod)
        else PrimaryButton("Instagram story", onClick = { a.onShare(ShareTarget.INSTAGRAM_STORY) }, enabled = ui.canWrite, modifier = mod)
    }
    val anyDirect = ui.installed.whatsapp || ui.installed.instagram
    if (anyDirect) SecondaryButton("Share elsewhere…", onClick = { a.onShare(ShareTarget.ANY) }, enabled = ui.canWrite, modifier = Modifier.fillMaxWidth())
    else PrimaryButton("Share…", onClick = { a.onShare(ShareTarget.ANY) }, enabled = ui.canWrite, modifier = Modifier.fillMaxWidth(), leadingIcon = Icons.Rounded.Share)
    Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
        SecondaryButton("Save to photos", onClick = { a.onShare(ShareTarget.SAVE) }, enabled = ui.canWrite, modifier = Modifier.weight(1f), leadingIcon = Icons.Rounded.Download)
        SecondaryButton("Copy text", onClick = a.onCopy, modifier = Modifier.weight(1f), leadingIcon = Icons.Rounded.ContentCopy)
    }
    ToggleRow("Add “Made with MeetingMind”", "A small mark at the foot of the card. Off by default.", d.watermark) { a.onDesign(d.copy(watermark = it)) }
    if (ui.card.format != CreateFormat.STORY) {
        Text("For a status or story, the Story format (9:16) fits best.", style = MM.type.caption, color = MM.colors.inkMuted)
    }
    Text("Every card is kept in My creations.", style = MM.type.caption, color = MM.colors.inkMuted)
    ui.message?.let { StatusLine(StatusKind.Info, it) }
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

