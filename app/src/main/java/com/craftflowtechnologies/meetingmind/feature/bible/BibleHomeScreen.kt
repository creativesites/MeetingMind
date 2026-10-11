package com.craftflowtechnologies.meetingmind.feature.bible

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.craftflowtechnologies.meetingmind.core.biblestudy.Need
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReference
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser
import com.craftflowtechnologies.meetingmind.core.ui.EntryPoints
import com.craftflowtechnologies.meetingmind.core.ui.mm.HeroCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.HomeScaffold
import com.craftflowtechnologies.meetingmind.core.ui.mm.InsetPanel
import com.craftflowtechnologies.meetingmind.core.ui.mm.ListRow
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.ScreenHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/** What the Bible home can do. The screen passes these to the app's navigation. */
data class BibleHomeActions(
    val onBack: () -> Unit = {},
    val onSearch: () -> Unit = {},
    /** Open the reader at [ScriptureReference] (null: where the person left off, or the first-time start). */
    val onRead: (ScriptureReference?) -> Unit = {},
    val onGuideToday: (ScriptureReference) -> Unit = {},
    val onNeed: (Need) -> Unit = {},
    val onWalkThrough: (ScriptureReference) -> Unit = {},
    val onPlanDay: (PlanDay) -> Unit = {},
    val onAllPlans: () -> Unit = {},
    val onJournal: () -> Unit = {},
    val onAnother: () -> Unit = {}
)

private val comingMethods = listOf("Book overview", "Character study", "Word study", "Topical study", "Inductive study", "Small group prep")

@Composable
fun BibleHomeScreen(viewModel: BibleHomeViewModel, actions: BibleHomeActions) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    BibleHomeContent(state, actions.copy(onAnother = viewModel::another))
}

/** The stateless Bible home, so it can be shown in tests and previews. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BibleHomeContent(state: BibleHomeState, actions: BibleHomeActions) {
    val c = MM.colors
    var studySheet by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    HomeScaffold(
        modifier = Modifier.fillMaxSize().background(c.background).statusBarsPadding().testTag("bible_home"),
        header = {
            ScreenHeader(
                title = "Bible",
                leading = { IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = c.ink) } },
                actions = { IconButton(onClick = actions.onSearch, modifier = Modifier.testTag("bible_home_search")) { Icon(Icons.Rounded.Search, "Search the Bible", tint = c.ink) } }
            )
        },
        hero = { ContinueHero(state, actions) },
        sections = {
            state.today?.let { today -> item(key = "today") { TodayCard(today.reference, today.lines, actions) } }
            item(key = "feel") {
                Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                    SectionHeader("How I feel")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                        Need.entries.forEach { need -> MMChip(need.label, selected = false, onClick = { actions.onNeed(need) }) }
                    }
                }
            }
            item(key = "rows") {
                MMCard(contentPadding = MM.space.s) {
                    ListRow(
                        title = EntryPoints.bibleStudyPassage.label, subtitle = "Pick a reference and we'll walk through it",
                        modifier = Modifier.padding(horizontal = MM.space.s).testTag("bible_home_study"),
                        trailing = { Chevron() }, onClick = { studySheet = true }
                    )
                    HorizontalDivider(color = c.line)
                    if (state.plans.isEmpty()) {
                        ListRow(title = "Reading plans", subtitle = "Read a little each day", modifier = Modifier.padding(horizontal = MM.space.s), trailing = { Chevron() }, onClick = actions.onAllPlans)
                    } else state.plans.forEach { p ->
                        ListRow(
                            title = p.planName, subtitle = "Day ${p.dayIndex + 1} · ${p.label}",
                            modifier = Modifier.padding(horizontal = MM.space.s), trailing = { Chevron() }, onClick = { actions.onPlanDay(p) }
                        )
                    }
                    if (state.plans.isNotEmpty()) {
                        HorizontalDivider(color = c.line)
                        ListRow(title = "All plans", modifier = Modifier.padding(horizontal = MM.space.s), trailing = { Chevron() }, onClick = actions.onAllPlans)
                    }
                    if (state.journalCount > 0) {
                        HorizontalDivider(color = c.line)
                        ListRow(
                            title = "My study journal", meta = state.journalCount.toString(),
                            modifier = Modifier.padding(horizontal = MM.space.s).testTag("bible_home_journal"), trailing = { Chevron() }, onClick = actions.onJournal
                        )
                    }
                }
            }
            item(key = "more") {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = MM.space.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextAction(if (moreOpen) "Fewer ways to study" else "More ways to study", onClick = { moreOpen = !moreOpen }, modifier = Modifier.testTag("bible_home_more"))
                        Icon(if (moreOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = c.accent)
                    }
                    if (moreOpen) comingMethods.forEach { name ->
                        ListRow(title = name, meta = "Coming", modifier = Modifier.padding(horizontal = MM.space.s))
                    }
                }
            }
        }
    )
    if (studySheet) StudyPassageSheet(onDismiss = { studySheet = false }, onWalk = { studySheet = false; actions.onWalkThrough(it) })
}

@Composable
private fun Chevron() = Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MM.colors.inkMuted, modifier = Modifier.width(MMSize.icon))

/** Continue is the hero: the exact verse, or "Start reading" for someone who has never read here. */
@Composable
private fun ContinueHero(state: BibleHomeState, actions: BibleHomeActions) {
    val c = MM.colors
    val place = state.place
    HeroCard(onClick = { actions.onRead(place ?: state.start) }, modifier = Modifier.testTag("bible_home_continue")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoStories, null, tint = c.accent, modifier = Modifier.width(MMSize.icon))
            Spacer(Modifier.width(MM.space.m))
            Column(Modifier.weight(1f)) {
                Text(if (place == null) EntryPoints.bibleStartReading.label else EntryPoints.bibleContinue.label, style = MM.type.heading, color = c.ink)
                Text(
                    if (place == null) "Begin with the Gospel of John" else place.display(),
                    style = MM.type.secondary, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.accent)
        }
    }
}

/** Today's reading: why it matters in two lines, Guide me (primary) and Just read. */
@Composable
private fun TodayCard(reference: ScriptureReference, lines: List<String>, actions: BibleHomeActions) {
    val c = MM.colors
    MMCard(modifier = Modifier.testTag("bible_home_today")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Today's reading", style = MM.type.caption, color = c.inkMuted, modifier = Modifier.weight(1f))
            TextAction("Another", onClick = actions.onAnother)
        }
        Text(reference.display(), style = MM.type.title, color = c.ink)
        Column(Modifier.padding(top = MM.space.xs, bottom = MM.space.m)) {
            lines.take(2).forEach { Text(it, style = MM.type.secondary, color = c.inkSecondary) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s), verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(EntryPoints.bibleGuideMe.label, onClick = { actions.onGuideToday(reference) }, modifier = Modifier.testTag("bible_home_guide"))
            SecondaryButton(EntryPoints.bibleJustRead.label, onClick = { actions.onRead(reference) }, modifier = Modifier.testTag("bible_home_read"))
        }
    }
}

/** Pick a reference; "Walk me through it" starts the session, where SOAP or Verse by verse is chosen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudyPassageSheet(onDismiss: () -> Unit, onWalk: (ScriptureReference) -> Unit) {
    val c = MM.colors
    var text by remember { mutableStateOf("") }
    val parsed = remember(text) { ScriptureReferenceParser.parse(text) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceRaised) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = MM.space.l).padding(bottom = MM.space.l),
            verticalArrangement = Arrangement.spacedBy(MM.space.m)
        ) {
            Text(EntryPoints.bibleStudyPassage.label, style = MM.type.title, color = c.ink)
            InsetPanel {
                BasicTextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    textStyle = MM.type.body.copy(color = c.ink), cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { parsed?.let(onWalk) }),
                    modifier = Modifier.fillMaxWidth().testTag("bible_home_ref"),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth().padding(vertical = MM.space.s)) {
                            if (text.isEmpty()) Text("A book and chapter, like John 3 or Romans 8:28-39", style = MM.type.body, color = c.inkMuted)
                            inner()
                        }
                    }
                )
            }
            Text(
                when {
                    text.isBlank() -> " "
                    parsed != null -> parsed.display()
                    else -> "That doesn't look like a reference yet."
                },
                style = MM.type.secondary, color = if (parsed != null) c.inkSecondary else c.inkMuted
            )
            PrimaryButton(EntryPoints.bibleWalkMe.label, onClick = { parsed?.let(onWalk) }, enabled = parsed != null, modifier = Modifier.testTag("bible_home_walk"))
        }
    }
}
