package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craftflowtechnologies.meetingmind.core.circles2.CircleTemplate
import com.craftflowtechnologies.meetingmind.core.circles2.Circles2
import com.craftflowtechnologies.meetingmind.core.circles2.PostType
import com.craftflowtechnologies.meetingmind.core.circles2.WhoCanInvite
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.SegmentedControl
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.ui.theme.CirclesFixedColors
import com.craftflowtechnologies.meetingmind.ui.theme.FixedWhite
import com.craftflowtechnologies.meetingmind.ui.theme.LearningFixedColors
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import com.craftflowtechnologies.meetingmind.ui.theme.TestimonyColors

private data class SpaceVisualTheme(
    val key: String,
    val label: String,
    val eyebrow: String,
    val headline: String,
    val tagline: String,
    val icon: ImageVector,
    val accent: Color,
    val accentWash: Color,
    val onAccent: Color,
    val bannerColors: List<Color>,
    val namePlaceholder: String,
    val descriptionPlaceholder: String,
    val privacyCopy: String,
    val ctaLabel: String,
    val ctaBusyLabel: String
)

@Composable
private fun rememberSpaceTheme(spaceTab: String): SpaceVisualTheme {
    val c = MM.colors
    return when (spaceTab) {
        "work" -> SpaceVisualTheme(
            key = "work",
            label = "Work",
            eyebrow = "TEAM WORKSPACE",
            headline = "Collaborate with clarity",
            tagline = "Align projects, decisions, standups, and meeting briefs in one focused team room.",
            icon = Icons.Rounded.Business,
            accent = c.accent,
            accentWash = c.accentWash,
            onAccent = c.onAccent,
            bannerColors = listOf(CirclesFixedColors.CoverStart, CirclesFixedColors.AccentDeep, CirclesFixedColors.CoverEnd),
            namePlaceholder = "e.g. Core Engineering or Q4 Launch",
            descriptionPlaceholder = "What this workspace focuses on or who should join",
            privacyCopy = "Private workspace — local meeting notes stay on your device until you share them.",
            ctaLabel = "Create team workspace",
            ctaBusyLabel = "Creating workspace…"
        )
        "learning" -> SpaceVisualTheme(
            key = "learning",
            label = "Study",
            eyebrow = "STUDY CIRCLE",
            headline = "Master material together",
            tagline = "Share 1-page cram sheets, lecture unpacks, and exam prep drills with classmates.",
            icon = Icons.Rounded.School,
            accent = LearningFixedColors.SapphireMid,
            accentWash = LearningFixedColors.SapphireMid.copy(alpha = if (c.isDark) 0.22f else 0.12f),
            onAccent = FixedWhite,
            bannerColors = listOf(LearningFixedColors.SapphireDeep, LearningFixedColors.SapphireMid, LearningFixedColors.TealAccent),
            namePlaceholder = "e.g. Organic Chem 201 or Bar Prep",
            descriptionPlaceholder = "Course code, upcoming exam, or syllabus focus",
            privacyCopy = "Focused study room — personal study notes remain private on your device.",
            ctaLabel = "Create study circle",
            ctaBusyLabel = "Creating study circle…"
        )
        "faith" -> SpaceVisualTheme(
            key = "faith",
            label = "Faith",
            eyebrow = "FELLOWSHIP CIRCLE",
            headline = "Stay close in prayer & Word",
            tagline = "Gather your small group, family, or ministry around prayer requests, devotionals, and study.",
            icon = Icons.Rounded.Favorite,
            accent = c.gold,
            accentWash = c.goldWash,
            onAccent = FixedWhite,
            bannerColors = listOf(TestimonyColors.HeroDeepIndigo, TestimonyColors.HeroVelvetPlum, TestimonyColors.HeroDawnBronze),
            namePlaceholder = "e.g. Tuesday Night Cell",
            descriptionPlaceholder = "When you gather, or a verse that anchors the group",
            privacyCopy = "Confidential sanctuary — prayer requests can wait for admin review before appearing.",
            ctaLabel = "Create faith circle",
            ctaBusyLabel = "Creating faith circle…"
        )
        else -> SpaceVisualTheme(
            key = "general",
            label = "Community",
            eyebrow = "COMMUNITY CIRCLE",
            headline = "A calm space for your group",
            tagline = "Keep a private corner for friends, book clubs, cohorts, and everyday milestones.",
            icon = Icons.Rounded.Groups,
            accent = c.speaker3,
            accentWash = c.speaker3.copy(alpha = if (c.isDark) 0.22f else 0.13f),
            onAccent = FixedWhite,
            bannerColors = listOf(LearningFixedColors.TealAccent, CirclesFixedColors.CoverStart),
            namePlaceholder = "e.g. Sunday Book Club",
            descriptionPlaceholder = "What brings this group together",
            privacyCopy = "Invite-only circle — no public directory; people join only with your link or code.",
            ctaLabel = "Create community circle",
            ctaBusyLabel = "Creating community circle…"
        )
    }
}

private fun templateIcon(id: String): ImageVector = when (id) {
    "project_team" -> Icons.Rounded.Flag
    "client_engagement" -> Icons.Rounded.Business
    "exec_board" -> Icons.Rounded.Assignment
    "department" -> Icons.Rounded.Groups
    "study_group" -> Icons.Rounded.School
    "course_cohort" -> Icons.Rounded.AutoStories
    "exam_prep_room" -> Icons.Rounded.Psychology
    "small_group" -> Icons.Rounded.Groups
    "prayer_partners" -> Icons.Rounded.VolunteerActivism
    "bible_study" -> Icons.Rounded.AutoStories
    "family" -> Icons.Rounded.Favorite
    "youth" -> Icons.Rounded.Celebration
    "ministry_team" -> Icons.Rounded.Lightbulb
    "book_club" -> Icons.Rounded.AutoStories
    "friends_group" -> Icons.Rounded.Favorite
    "community_club" -> Icons.Rounded.Groups
    else -> Icons.Rounded.Tune
}

private fun postTypesForSpace(spaceTab: String): List<PostType> = when (spaceTab) {
    "work" -> listOf(
        PostType.Task,
        PostType.Decision,
        PostType.Brief,
        PostType.Standup,
        PostType.Blocker,
        PostType.Announcement,
        PostType.Note
    )
    "learning" -> listOf(
        PostType.Study,
        PostType.Achievement,
        PostType.Encouragement,
        PostType.Announcement,
        PostType.Note
    )
    "general" -> listOf(
        PostType.Achievement,
        PostType.Encouragement,
        PostType.Reading,
        PostType.Announcement,
        PostType.Note
    )
    else -> listOf(
        PostType.Prayer,
        PostType.Testimony,
        PostType.Study,
        PostType.Devotional,
        PostType.Reading,
        PostType.Encouragement,
        PostType.Achievement,
        PostType.Announcement,
        PostType.Note
    )
}

@Composable
fun CreateCircleScreen(onBack: () -> Unit, onCreated: (String) -> Unit, initialSpace: String? = null) {
    val context = LocalContext.current
    val repo = remember { Circles2.repository(context) }
    val vm: CreateCircleViewModel = viewModel(
        key = "create-circle-${initialSpace.orEmpty()}",
        factory = CreateCircleViewModel.Factory(repo, initialSpace)
    )
    val state by vm.state.collectAsState()
    val askNotifications = rememberNotificationAsk()
    CreateCircleContent(
        state = state,
        onBack = onBack,
        onTemplate = vm::pickTemplate,
        onSpaceTab = vm::setSpaceTab,
        onName = vm::setName,
        onVocab = vm::setVocab,
        onDisplayName = vm::setDisplayName,
        onToggleType = vm::toggleType,
        onApproval = vm::setApproval,
        onWhoCanInvite = vm::setWhoCanInvite,
        onCreate = {
            vm.create { id ->
                if (repo.isFirstCircle()) askNotifications { onCreated(id) } else onCreated(id)
            }
        },
        onDescription = vm::setDescription,
        onJoinApproval = vm::setJoinApproval,
        onCoverPack = vm::pickCoverPack,
        onCoverPhoto = vm::pickCoverPhoto
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateCircleContent(
    state: CreateUiState,
    onBack: () -> Unit,
    onTemplate: (CircleTemplate) -> Unit,
    onSpaceTab: (String) -> Unit,
    onName: (String) -> Unit,
    onVocab: (String) -> Unit,
    onDisplayName: (String) -> Unit,
    onToggleType: (PostType) -> Unit,
    onApproval: (Boolean) -> Unit,
    onWhoCanInvite: (WhoCanInvite) -> Unit,
    onCreate: () -> Unit,
    onDescription: (String) -> Unit = {},
    onJoinApproval: (Boolean) -> Unit = {},
    onCoverPack: (String) -> Unit = {},
    onCoverPhoto: (PickedFile) -> Unit = {}
) {
    val c = MM.colors
    var coverUnreadable by remember { mutableStateOf(false) }
    val pickCoverPhoto = rememberCoverPhotoPicker { picked -> coverUnreadable = picked == null; picked?.let(onCoverPhoto) }
    val haptic = LocalHapticFeedback.current
    val theme = rememberSpaceTheme(state.spaceTab)
    val accent by animateColorAsState(theme.accent, MM.motion.standard(), label = "verticalAccent")
    val accentWash by animateColorAsState(theme.accentWash, MM.motion.standard(), label = "verticalWash")
    val templates = remember(state.spaceTab) { CircleTemplate.forSpace(state.spaceTab) }
    val spacePostTypes = remember(state.spaceTab) { postTypesForSpace(state.spaceTab) }
    var customVocabOpen by rememberSaveable { mutableStateOf(false) }
    var governanceExpanded by rememberSaveable(state.spaceTab) { mutableStateOf(state.spaceTab == "faith") }

    val spaces = remember {
        listOf(
            "work" to ("Work" to Icons.Rounded.Business),
            "learning" to ("Study" to Icons.Rounded.School),
            "faith" to ("Faith" to Icons.Rounded.Favorite),
            "general" to ("Community" to Icons.Rounded.Groups)
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .imePadding()
    ) {
        // Editorial Top Bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = MM.space.s, vertical = MM.space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(MMSize.minTouch)
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = c.inkSecondary)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "New circle",
                    style = MM.type.title.copy(fontWeight = FontWeight.Bold),
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    theme.headline,
                    style = MM.type.caption,
                    color = c.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                shape = MM.radius.pill,
                color = accentWash,
                border = BorderStroke(MMSize.hairline, accent.copy(alpha = 0.28f))
            ) {
                Row(
                    Modifier.padding(horizontal = MM.space.m, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                ) {
                    Icon(theme.icon, null, tint = accent, modifier = Modifier.size(15.dp))
                    Text(
                        theme.label,
                        style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = accent
                    )
                }
            }
        }

        // Vertical Switcher Rail
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = MM.space.l, vertical = MM.space.xs),
            horizontalArrangement = Arrangement.spacedBy(MM.space.s)
        ) {
            spaces.forEach { (key, meta) ->
                val (label, icon) = meta
                val selected = state.spaceTab == key
                val pillFill by animateColorAsState(
                    if (selected) accentWash else c.surface,
                    MM.motion.quick(),
                    label = "spacePill_$key"
                )
                Surface(
                    shape = MM.radius.pill,
                    color = pillFill,
                    border = BorderStroke(
                        width = if (selected) 1.5.dp else MMSize.hairline,
                        color = if (selected) accent else c.line
                    ),
                    modifier = Modifier
                        .heightIn(min = 42.dp)
                        .clip(MM.radius.pill)
                        .selectable(selected = selected, role = Role.Tab) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            customVocabOpen = false
                            onSpaceTab(key)
                        }
                        .testTag("space_tab_$key")
                ) {
                    Row(
                        Modifier.padding(horizontal = MM.space.m, vertical = MM.space.s),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = if (selected) accent else c.inkSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            label,
                            style = MM.type.secondary.copy(
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                            ),
                            color = if (selected) accent else c.inkSecondary,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Scrollable Studio Form
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = MM.space.l, vertical = MM.space.m),
            verticalArrangement = Arrangement.spacedBy(MM.space.l)
        ) {
            // Live Editorial Preview Card
            item(key = "live_preview") {
                LiveCirclePreviewCard(state = state, theme = theme)
            }

            // Cover: one of ours (the type's picture first) or their own photo
            item(key = "cover_section") {
                Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    SectionHeader(title = "Cover")
                    CoverPickerRow(
                        template = state.template.id, space = state.template.space,
                        selectedPack = state.shownCoverPack, chosenOwn = state.coverPhoto != null,
                        onPickPack = { coverUnreadable = false; onCoverPack(it) }, onUpload = pickCoverPhoto,
                        contentPadding = PaddingValues(0.dp)
                    )
                    if (coverUnreadable) StatusLine(StatusKind.Warning, "That photo couldn't be read. Try another, or pick one of ours.")
                }
            }

            // 01 · Blueprint Templates
            item(key = "header_templates") {
                SectionHeader(
                    title = "01 · Blueprint",
                    count = templates.size
                )
            }

            items(templates, key = { it.id }) { tpl ->
                val selected = tpl.id == state.template.id
                TemplateBlueprintCard(
                    template = tpl,
                    selected = selected,
                    accent = accent,
                    accentWash = accentWash,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        customVocabOpen = false
                        onTemplate(tpl)
                    }
                )
            }

            // 02 · Identity & Naming
            item(key = "identity_section") {
                Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    SectionHeader(title = "02 · Identity")
                    MMCard(contentPadding = MM.space.l) {
                        Column(verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${state.vocab.ifBlank { "Circle" }} details",
                                    style = MM.type.bodyStrong,
                                    color = c.ink
                                )
                                Text(
                                    "${state.name.length}/60",
                                    style = MM.type.caption,
                                    color = c.inkMuted
                                )
                            }

                            CircleField(
                                value = state.name,
                                onValueChange = onName,
                                label = "Circle name",
                                placeholder = theme.namePlaceholder,
                                accentColor = accent,
                                leadingIcon = {
                                    Icon(theme.icon, null, tint = accent, modifier = Modifier.size(MMSize.iconSmall))
                                }
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                                Text(
                                    "What members call this group",
                                    style = MM.type.caption,
                                    color = c.inkSecondary
                                )
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(MM.space.s),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    state.template.vocab.forEach { word ->
                                        val isWordSelected = state.vocab == word && !customVocabOpen
                                        MMChip(
                                            label = word,
                                            selected = isWordSelected,
                                            onClick = {
                                                customVocabOpen = false
                                                onVocab(word)
                                            }
                                        )
                                    }
                                    MMChip(
                                        label = "Custom",
                                        selected = customVocabOpen || state.vocab !in state.template.vocab,
                                        onClick = { customVocabOpen = !customVocabOpen }
                                    )
                                }
                                AnimatedVisibility(
                                    visible = customVocabOpen || state.vocab !in state.template.vocab,
                                    enter = expandVertically(MM.motion.quick()) + fadeIn(),
                                    exit = shrinkVertically(MM.motion.quick()) + fadeOut()
                                ) {
                                    CircleField(
                                        value = state.vocab,
                                        onValueChange = onVocab,
                                        label = "Custom group label",
                                        placeholder = "e.g. Squad, Cohort, Parish",
                                        accentColor = accent,
                                        modifier = Modifier.padding(top = MM.space.xs),
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Edit, null, tint = c.inkSecondary, modifier = Modifier.size(MMSize.iconSmall))
                                        }
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Purpose (optional)", style = MM.type.caption, color = c.inkSecondary)
                                    if (state.description.isNotEmpty()) {
                                        Text("${state.description.length}/300", style = MM.type.caption, color = c.inkMuted)
                                    }
                                }
                                CircleField(
                                    value = state.description,
                                    onValueChange = onDescription,
                                    label = "Short description",
                                    placeholder = theme.descriptionPlaceholder,
                                    minLines = 2,
                                    maxLines = 4,
                                    accentColor = accent
                                )
                            }

                            HorizontalDivider(color = c.lineSoft)

                            Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                                CircleField(
                                    value = state.displayName,
                                    onValueChange = onDisplayName,
                                    label = "Your display name",
                                    placeholder = "Shown on your posts and chat messages",
                                    accentColor = accent,
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Person, null, tint = c.inkSecondary, modifier = Modifier.size(MMSize.iconSmall))
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 03 · Feed & Governance
            item(key = "governance_section") {
                Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    SectionHeader(title = "03 · Feed & Privacy")
                    MMCard(contentPadding = MM.space.l) {
                        Column(verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
                            Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("What members can share", style = MM.type.bodyStrong, color = c.ink)
                                    Text(
                                        "${state.types.size} active",
                                        style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                                        color = accent
                                    )
                                }
                                Text(
                                    "Pick the post cards available in the ${theme.label.lowercase()} composer.",
                                    style = MM.type.caption,
                                    color = c.inkSecondary
                                )
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(MM.space.s),
                                verticalArrangement = Arrangement.spacedBy(MM.space.xs)
                            ) {
                                spacePostTypes.forEach { t ->
                                    val active = t in state.types
                                    PostTypeToggleChip(
                                        type = t,
                                        selected = active,
                                        accent = accent,
                                        accentWash = accentWash,
                                        onClick = { onToggleType(t) }
                                    )
                                }
                            }

                            HorizontalDivider(color = c.lineSoft)

                            // Governance Drawer Header
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(MM.radius.small)
                                    .clickable(role = Role.Button) { governanceExpanded = !governanceExpanded }
                                    .padding(vertical = MM.space.xs),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Membership & moderation", style = MM.type.bodyStrong, color = c.ink)
                                    val summary = buildString {
                                        append(state.whoCanInvite.label)
                                        append(" invite")
                                        if (state.joinApproval) append(" · Join approval on")
                                        if (state.spaceTab == "faith" && PostType.Prayer in state.types && state.prayerApproval) {
                                            append(" · Prayer review on")
                                        }
                                    }
                                    Text(summary, style = MM.type.caption, color = c.inkSecondary)
                                }
                                Icon(
                                    if (governanceExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = if (governanceExpanded) "Collapse governance options" else "Expand governance options",
                                    tint = c.inkSecondary
                                )
                            }

                            AnimatedVisibility(
                                visible = governanceExpanded,
                                enter = expandVertically(MM.motion.standard()) + fadeIn(),
                                exit = shrinkVertically(MM.motion.standard()) + fadeOut()
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
                                    Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                                        Text("Who can invite new people", style = MM.type.caption, color = c.inkSecondary)
                                        SegmentedControl(
                                            options = WhoCanInvite.entries.map { it.label },
                                            selectedIndex = WhoCanInvite.entries.indexOf(state.whoCanInvite),
                                            onSelect = { onWhoCanInvite(WhoCanInvite.entries[it]) }
                                        )
                                    }

                                    ToggleRow(
                                        title = "Approve new members before entry",
                                        subtitle = "People requesting via link or code wait for an admin before seeing posts or chat.",
                                        checked = state.joinApproval,
                                        onChange = onJoinApproval
                                    )

                                    if (state.spaceTab == "faith" && PostType.Prayer in state.types) {
                                        ToggleRow(
                                            title = "Admins approve prayer requests first",
                                            subtitle = "Anonymous prayer requests stay anonymous to admins too and hold until reviewed.",
                                            checked = state.prayerApproval,
                                            onChange = onApproval
                                        )
                                    }
                                }
                            }

                            // Privacy Assurance Inset
                            Surface(
                                shape = MM.radius.small,
                                color = c.surfaceSunk,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.padding(MM.space.m),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                                ) {
                                    Icon(Icons.Rounded.Lock, null, tint = accent, modifier = Modifier.size(16.dp))
                                    Text(
                                        theme.privacyCopy,
                                        style = MM.type.caption,
                                        color = c.inkSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "bottom_pad") {
                Spacer(Modifier.height(MM.space.m))
            }
        }

        // Sticky Elevated Action Bar
        Surface(
            color = c.surface,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                HorizontalDivider(color = c.lineSoft)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MM.space.l, vertical = MM.space.m),
                    verticalArrangement = Arrangement.spacedBy(MM.space.s)
                ) {
                    AnimatedVisibility(visible = state.error != null) {
                        StatusLine(StatusKind.Warning, state.error.orEmpty())
                    }
                    Button(
                        onClick = onCreate,
                        enabled = !state.busy,
                        shape = MM.radius.pill,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = theme.onAccent,
                            disabledContainerColor = c.track,
                            disabledContentColor = c.inkMuted
                        ),
                        contentPadding = PaddingValues(horizontal = MM.space.xl, vertical = MM.space.m),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("create_circle_submit")
                    ) {
                        if (state.busy) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = theme.onAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(MM.space.s))
                        }
                        Text(
                            if (state.busy) theme.ctaBusyLabel else theme.ctaLabel,
                            style = MM.type.bodyStrong
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveCirclePreviewCard(
    state: CreateUiState,
    theme: SpaceVisualTheme
) {
    val displayTitle = state.name.trim().ifBlank { state.template.label }
    val vocab = state.vocab.trim().ifBlank { state.template.vocab.firstOrNull() ?: "Circle" }
    val desc = state.description.trim().ifBlank { theme.tagline }
    Surface(
        shape = MM.radius.card,
        color = theme.bannerColors.first(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(theme.bannerColors))
        ) {
            // The cover they'll get: their own photo, or the pack picture chosen (the type's own by default).
            val own = state.coverPhoto
            val ownBitmap = remember(own) {
                own?.let { runCatching { android.graphics.BitmapFactory.decodeByteArray(it.bytes, 0, it.bytes.size)?.asImageBitmap() }.getOrNull() }
            }
            if (ownBitmap != null) {
                androidx.compose.foundation.Image(ownBitmap, null, Modifier.matchParentSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            } else {
                PackImage(state.shownCoverPack, thumb = false, modifier = Modifier.matchParentSize())
            }
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(MM.colors.scrim.copy(alpha = 0.25f), MM.colors.scrim.copy(alpha = 0.7f))
                        )
                    )
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(MM.space.l),
                verticalArrangement = Arrangement.spacedBy(MM.space.m)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = MM.radius.pill,
                        color = FixedWhite.copy(alpha = 0.16f)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                        ) {
                            Icon(theme.icon, null, tint = FixedWhite, modifier = Modifier.size(13.dp))
                            Text(
                                theme.eyebrow,
                                style = MM.type.overline.copy(fontWeight = FontWeight.Bold),
                                color = FixedWhite
                            )
                        }
                    }
                    Surface(
                        shape = MM.radius.pill,
                        color = FixedWhite.copy(alpha = 0.16f)
                    ) {
                        Text(
                            "LIVE PREVIEW",
                            style = MM.type.overline.copy(fontWeight = FontWeight.SemiBold),
                            color = FixedWhite.copy(alpha = 0.9f),
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MM.space.m)
                ) {
                    InitialsAvatar(
                        name = displayTitle,
                        size = 52.dp
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            displayTitle,
                            style = MM.type.heading.copy(fontWeight = FontWeight.Bold),
                            color = FixedWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            desc,
                            style = MM.type.caption,
                            color = FixedWhite.copy(alpha = 0.86f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                ) {
                    PreviewMetaPill(vocab)
                    PreviewMetaPill(if (state.joinApproval) "Approval required" else "Direct join")
                    PreviewMetaPill("${state.types.size} ${if (state.types.size == 1) "post type" else "post types"}")
                    if (state.displayName.isNotBlank()) {
                        PreviewMetaPill("Host: ${state.displayName.trim()}")
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewMetaPill(label: String) {
    Surface(
        shape = MM.radius.pill,
        color = FixedWhite.copy(alpha = 0.14f)
    ) {
        Text(
            label,
            style = MM.type.caption.copy(fontWeight = FontWeight.Medium),
            color = FixedWhite,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplateBlueprintCard(
    template: CircleTemplate,
    selected: Boolean,
    accent: Color,
    accentWash: Color,
    onClick: () -> Unit
) {
    val c = MM.colors
    val cardBg by animateColorAsState(
        if (selected) accentWash else c.surface,
        MM.motion.quick(),
        label = "tplBg_${template.id}"
    )
    val borderColor by animateColorAsState(
        if (selected) accent else if (c.isDark) c.lineSoft else c.line,
        MM.motion.quick(),
        label = "tplBorder_${template.id}"
    )

    Surface(
        shape = MM.radius.card,
        color = cardBg,
        border = BorderStroke(if (selected) 1.5.dp else MMSize.hairline, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MM.radius.card)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .testTag("template_card_${template.id}")
    ) {
        Column(
            Modifier.padding(MM.space.m),
            verticalArrangement = Arrangement.spacedBy(MM.space.s)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MM.space.m)
            ) {
                Surface(
                    shape = MM.radius.small,
                    color = if (selected) accent else c.surfaceSunk,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            templateIcon(template.id),
                            contentDescription = null,
                            tint = if (selected) FixedWhite else accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        template.label,
                        style = MM.type.bodyStrong,
                        color = c.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        template.blurb,
                        style = MM.type.secondary,
                        color = c.inkSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = if (selected) "Selected" else "Select template",
                    tint = if (selected) accent else c.inkMuted,
                    modifier = Modifier.size(22.dp)
                )
            }

            AnimatedVisibility(
                visible = selected,
                enter = expandVertically(MM.motion.quick()) + fadeIn(),
                exit = shrinkVertically(MM.motion.quick()) + fadeOut()
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(MM.space.xs),
                    verticalArrangement = Arrangement.spacedBy(MM.space.xs),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    template.types.take(5).forEach { postType ->
                        Surface(
                            shape = MM.radius.pill,
                            color = c.surface.copy(alpha = if (c.isDark) 0.72f else 0.9f),
                            border = BorderStroke(MMSize.hairline, accent.copy(alpha = 0.28f))
                        ) {
                            Text(
                                postType.label,
                                style = MM.type.caption,
                                color = c.ink,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PostTypeToggleChip(
    type: PostType,
    selected: Boolean,
    accent: Color,
    accentWash: Color,
    onClick: () -> Unit
) {
    val c = MM.colors
    val fill by animateColorAsState(
        if (selected) accentWash else c.surfaceSunk,
        MM.motion.quick(),
        label = "postTypeChip_${type.wire}"
    )
    Surface(
        shape = MM.radius.small,
        color = fill,
        border = BorderStroke(
            width = if (selected) 1.dp else MMSize.hairline,
            color = if (selected) accent else c.lineSoft
        ),
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(MM.radius.small)
            .clickable(role = Role.Checkbox, onClick = onClick)
    ) {
        Row(
            Modifier.padding(horizontal = MM.space.m, vertical = MM.space.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
        ) {
            if (selected) {
                Icon(Icons.Rounded.Check, null, tint = accent, modifier = Modifier.size(16.dp))
            }
            Text(
                type.label,
                style = MM.type.caption.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = if (selected) accent else c.inkSecondary
            )
        }
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = MM.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MMSize.minTouch)
            .clip(MM.radius.small)
            .clickable { onChange(!checked) }
            .padding(vertical = MM.space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MM.type.body, color = c.ink)
            if (subtitle != null) Text(subtitle, style = MM.type.caption, color = c.inkSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.onAccent,
                checkedTrackColor = c.accent,
                uncheckedThumbColor = c.surface,
                uncheckedTrackColor = c.track
            )
        )
    }
}
