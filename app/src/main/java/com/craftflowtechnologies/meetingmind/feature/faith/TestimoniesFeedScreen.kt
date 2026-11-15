package com.craftflowtechnologies.meetingmind.feature.faith

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesResult
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReference
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser
import com.craftflowtechnologies.meetingmind.feature.scripture.VerseSheet
import com.craftflowtechnologies.meetingmind.feature.share.ShareRequest
import com.craftflowtechnologies.meetingmind.ui.theme.FaithGold
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.TestimonyColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Legacy-compatible model retained for callers constructing simple testimony previews.
 */
data class SharedTestimony(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val author: String,
    val dateText: String,
    val text: String,
    val scripture: String? = null,
    val isPersonal: Boolean = false,
    val noteId: String? = null
)

@Composable
fun TestimoniesFeedScreen(
    viewModel: FaithViewModel,
    onNavigateBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onNewTestimony: () -> Unit,
    onShare: (ShareRequest) -> Unit
) {
    val c = MM.colors
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val personalStories by viewModel.personalTestimonies.collectAsState()
    val circleStories by viewModel.circleTestimonies.collectAsState()
    val openPrayers by viewModel.openRequests.collectAsState()
    val answeredPrayerCount by viewModel.answeredCount.collectAsState()
    val joinedCircles by viewModel.joinedCircles.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadCircleTestimonies()
    }

    var selectedTab by remember { mutableStateOf(TestimonyFilterTab.ALL) }
    var selectedCategory by remember { mutableStateOf<TestimonyCategory?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    var activeStudioSeed by remember { mutableStateOf<TestimonyStudioSeed?>(null) }
    var selectedStoryId by remember { mutableStateOf<String?>(null) }
    var shareCircleStory by remember { mutableStateOf<TestimonyStory?>(null) }
    var pendingShareNoteId by remember { mutableStateOf<String?>(null) }
    var versePreviewRef by remember { mutableStateOf<ScriptureReference?>(null) }

    val publishedPersonal = remember(personalStories) { personalStories.filter { !it.isDraft } }
    val draftStories = remember(personalStories) { personalStories.filter { it.isDraft } }
    val totalAmens = remember(publishedPersonal, circleStories) {
        publishedPersonal.sumOf { it.rejoiceCount } + circleStories.sumOf { it.rejoiceCount }
    }
    val sharedCount = remember(publishedPersonal) {
        publishedPersonal.count { it.sharedToCircles.isNotEmpty() }
    }

    LaunchedEffect(pendingShareNoteId, personalStories) {
        val targetId = pendingShareNoteId ?: return@LaunchedEffect
        val found = personalStories.firstOrNull { it.noteId == targetId || it.id == targetId }
        if (found != null) {
            shareCircleStory = found
            pendingShareNoteId = null
        }
    }

    val spotlightStory = remember(publishedPersonal) {
        val sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000L
        publishedPersonal.firstOrNull { it.storyDateMs < sevenDaysAgo }
            ?: publishedPersonal.takeIf { it.size >= 2 }?.lastOrNull()
    }

    val visibleStories = remember(
        selectedTab,
        selectedCategory,
        searchQuery,
        publishedPersonal,
        draftStories,
        circleStories
    ) {
        val base = when (selectedTab) {
            TestimonyFilterTab.ALL -> publishedPersonal
            TestimonyFilterTab.ANSWERED_PRAYERS -> publishedPersonal.filter {
                it.linkedPrayerId != null || it.prayerDurationDays != null
            }
            TestimonyFilterTab.DRAFTS -> draftStories
            TestimonyFilterTab.IN_CIRCLES -> circleStories
        }
        val byCat = if (selectedCategory == null) base else base.filter { it.category == selectedCategory }
        val q = searchQuery.trim()
        if (q.isEmpty()) byCat else byCat.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.fullText.contains(q, ignoreCase = true) ||
                it.verseRef?.contains(q, ignoreCase = true) == true ||
                it.circleName?.contains(q, ignoreCase = true) == true
        }
    }

    val selectedDetailStory = remember(selectedStoryId, personalStories, circleStories) {
        val id = selectedStoryId ?: return@remember null
        (personalStories + circleStories).firstOrNull { it.id == id }
    }

    fun openStudio(noteId: String? = null, linkedPrayerId: String? = null) {
        scope.launch {
            activeStudioSeed = viewModel.prepareStudioSeed(noteId = noteId, linkedPrayerId = linkedPrayerId)
        }
    }

    fun openScripture(refText: String) {
        val parsed = ScriptureReferenceParser.parse(refText)
        if (parsed != null) {
            versePreviewRef = parsed
        } else {
            scope.launch { snackbarHostState.showSnackbar("Could not open chapter for \"$refText\"") }
        }
    }

    Scaffold(
        containerColor = c.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(Modifier.fillMaxWidth().statusBarsPadding()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MM.space.s, vertical = MM.space.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = c.ink)
                    }
                    Spacer(Modifier.width(MM.space.xs))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Wall of Remembrance",
                            style = MM.type.heading.copy(fontFamily = FontFamily.Serif),
                            color = c.ink
                        )
                        Text(
                            "Stories of answered prayer & faithfulness",
                            style = MM.type.caption,
                            color = c.inkSecondary
                        )
                    }
                    IconButton(onClick = {
                        searchOpen = !searchOpen
                        if (!searchOpen) searchQuery = ""
                    }) {
                        Icon(
                            if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = "Search stories",
                            tint = if (searchOpen) FaithGold else c.inkSecondary
                        )
                    }
                    IconButton(onClick = onNewTestimony) {
                        Icon(Icons.Filled.Mic, contentDescription = "Speak a testimony", tint = FaithGold)
                    }
                }

                AnimatedVisibility(visible = searchOpen) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search stories, prayers, or verses…", style = MM.type.secondary) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = FaithGold) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = c.inkMuted)
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = FaithGold,
                            unfocusedBorderColor = c.lineSoft
                        ),
                        shape = MM.radius.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MM.space.m, vertical = MM.space.xs)
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { openStudio() },
                containerColor = FaithGold,
                contentColor = TestimonyColors.HeroDeepIndigo,
                shape = CircleShape,
                modifier = Modifier.testTag("testimonies_new_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add testimony")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = MM.space.m, vertical = MM.space.s),
            verticalArrangement = Arrangement.spacedBy(MM.space.m)
        ) {
            item(key = "hero") {
                WallOfRemembranceHero(
                    storyCount = publishedPersonal.size,
                    answeredCount = maxOf(answeredPrayerCount, publishedPersonal.count { it.linkedPrayerId != null }),
                    amenCount = totalAmens,
                    sharedCount = sharedCount,
                    onWriteStory = { openStudio() },
                    onSpeakStory = onNewTestimony
                )
            }

            // Draft Recovery Banner
            if (draftStories.isNotEmpty() && selectedTab != TestimonyFilterTab.DRAFTS) {
                val latestDraft = draftStories.first()
                item(key = "draft_banner_${latestDraft.id}") {
                    ContinueDraftBanner(
                        draft = latestDraft,
                        totalDrafts = draftStories.size,
                        onResume = { openStudio(noteId = latestDraft.noteId) },
                        onDiscard = { latestDraft.noteId?.let(viewModel::discardDraftTestimony) }
                    )
                }
            }

            // Spotlight Card
            if (selectedTab == TestimonyFilterTab.ALL && selectedCategory == null && searchQuery.isBlank() && spotlightStory != null) {
                item(key = "spotlight_${spotlightStory.id}") {
                    RememberSpotlightCard(
                        story = spotlightStory,
                        onOpen = { selectedStoryId = spotlightStory.id },
                        onCelebrateAgain = { viewModel.celebrateAgain(spotlightStory) }
                    )
                }
            }

            // Open prayers ready to be marked answered into a testimony
            if (openPrayers.isNotEmpty() && (selectedTab == TestimonyFilterTab.ALL || selectedTab == TestimonyFilterTab.ANSWERED_PRAYERS)) {
                item(key = "answered_prompt_strip") {
                    OpenPrayersTestifyStrip(
                        prayers = openPrayers.take(5),
                        onTestifyPrayer = { prayer -> openStudio(linkedPrayerId = prayer.id) }
                    )
                }
            }

            // Filter tabs
            item(key = "filter_tabs") {
                val counts = mapOf(
                    TestimonyFilterTab.ALL to publishedPersonal.size,
                    TestimonyFilterTab.ANSWERED_PRAYERS to publishedPersonal.count { it.linkedPrayerId != null || it.prayerDurationDays != null },
                    TestimonyFilterTab.DRAFTS to draftStories.size,
                    TestimonyFilterTab.IN_CIRCLES to circleStories.size
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                ) {
                    TestimonyFilterTab.entries.forEach { tab ->
                        val selected = selectedTab == tab
                        val count = counts[tab] ?: 0
                        Surface(
                            onClick = { selectedTab = tab },
                            shape = MM.radius.pill,
                            color = if (selected) c.ink else c.surfaceRaised,
                            border = BorderStroke(1.dp, if (selected) c.ink else c.lineSoft)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = MM.space.m, vertical = MM.space.s),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    tab.label,
                                    style = MM.type.caption,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (selected) c.onInk else c.ink
                                )
                                if (count > 0) {
                                    Surface(
                                        shape = MM.radius.pill,
                                        color = if (selected) FaithGold else c.surfaceSunk
                                    ) {
                                        Text(
                                            count.toString(),
                                            style = MM.type.caption,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selected) TestimonyColors.HeroDeepIndigo else c.inkSecondary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Category filter pills
            if (selectedTab == TestimonyFilterTab.ALL || selectedTab == TestimonyFilterTab.ANSWERED_PRAYERS) {
                item(key = "category_chips") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                    ) {
                        CategoryFilterChip(
                            label = "All moments",
                            dotColor = FaithGold,
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null }
                        )
                        TestimonyCategory.entries.forEach { cat ->
                            CategoryFilterChip(
                                label = cat.label,
                                dotColor = testimonyCategoryAccent(cat),
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = if (selectedCategory == cat) null else cat }
                            )
                        }
                    }
                }
            }

            if (visibleStories.isEmpty()) {
                item(key = "testimonies_empty") {
                    TestimoniesEmptyState(
                        tab = selectedTab,
                        hasCategoryFilter = selectedCategory != null || searchQuery.isNotBlank(),
                        onClearFilter = { selectedCategory = null; searchQuery = "" },
                        onWriteStory = { openStudio() },
                        onSpeakStory = onNewTestimony,
                        modifier = Modifier.testTag("testimonies_empty")
                    )
                }
            } else {
                items(visibleStories, key = { it.id }) { story ->
                    TestimonyStoryCard(
                        story = story,
                        onClick = {
                            if (story.isDraft && story.noteId != null) {
                                openStudio(noteId = story.noteId)
                            } else {
                                selectedStoryId = story.id
                            }
                        },
                        onToggleRejoice = { viewModel.toggleRejoice(story) },
                        onShareToCircle = if (story.isPersonal && !story.isDraft) ({ shareCircleStory = story }) else null,
                        onShareCard = { onShare(buildTestimonyShareCardRequest(story)) },
                        onOpenScripture = ::openScripture
                    )
                }
            }

            item { Spacer(Modifier.height(84.dp)) }
        }
    }

    activeStudioSeed?.let { seed ->
        TestimonyStudioSheet(
            seed = seed,
            viewModel = viewModel,
            onDismiss = { savedAsDraft, published ->
                activeStudioSeed = null
                if (savedAsDraft && !published) {
                    scope.launch { snackbarHostState.showSnackbar("Draft auto-saved — pick up anytime.") }
                }
            },
            onPublished = { publishedNoteId ->
                activeStudioSeed = null
                selectedStoryId = publishedNoteId
                scope.launch { snackbarHostState.showSnackbar("Testimony saved to your Wall of Remembrance.") }
            },
            onOpenFullEditor = { noteId ->
                activeStudioSeed = null
                onOpenNote(noteId)
            },
            onSwitchToVoice = {
                activeStudioSeed = null
                onNewTestimony()
            },
            onSaveAndShareToCircle = { savedNoteId ->
                activeStudioSeed = null
                val immediate = personalStories.firstOrNull { it.noteId == savedNoteId || it.id == savedNoteId }
                if (immediate != null) shareCircleStory = immediate else pendingShareNoteId = savedNoteId
            }
        )
    }

    selectedDetailStory?.let { story ->
        TestimonyDetailSheetWithAnon(
            story = story,
            joinedCircles = joinedCircles,
            onDismiss = { selectedStoryId = null },
            onEditInStudio = { noteId ->
                selectedStoryId = null
                openStudio(noteId = noteId)
            },
            onOpenNote = { noteId ->
                selectedStoryId = null
                onOpenNote(noteId)
            },
            onCelebrate = { viewModel.celebrateAgain(it) },
            onShareCard = { req ->
                selectedStoryId = null
                onShare(req)
            },
            onShareToCircle = { circle, targetStory, anonymous, onDone ->
                viewModel.shareTestimonyToCircle(circle, targetStory, anonymous = anonymous) { result ->
                    when (result) {
                        is CirclesResult.Ok -> onDone("Shared to ${circle.name}")
                        is CirclesResult.Err -> onDone(result.failure.message)
                    }
                }
            },
            onOpenScripture = ::openScripture
        )
    }

    shareCircleStory?.let { story ->
        TestimonyCircleShareSheet(
            story = story,
            joinedCircles = joinedCircles,
            onDismiss = { shareCircleStory = null },
            onShare = { circle, anonymous ->
                shareCircleStory = null
                viewModel.shareTestimonyToCircle(circle, story, anonymous = anonymous) { result ->
                    scope.launch {
                        when (result) {
                            is CirclesResult.Ok -> snackbarHostState.showSnackbar("Shared to ${circle.name}")
                            is CirclesResult.Err -> snackbarHostState.showSnackbar(result.failure.message)
                        }
                    }
                }
            }
        )
    }

    versePreviewRef?.let { ref ->
        VerseSheet(
            reference = ref,
            onDismiss = { versePreviewRef = null }
        )
    }
}

@Composable
private fun WallOfRemembranceHero(
    storyCount: Int,
    answeredCount: Int,
    amenCount: Int,
    sharedCount: Int,
    onWriteStory: () -> Unit,
    onSpeakStory: () -> Unit
) {
    Surface(
        shape = MM.radius.card,
        color = Color.Transparent,
        border = BorderStroke(1.dp, TestimonyColors.HeroGoldGlow.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            TestimonyColors.HeroDeepIndigo,
                            TestimonyColors.HeroVelvetPlum,
                            TestimonyColors.HeroDawnBronze
                        )
                    )
                )
                .padding(MM.space.l)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(TestimonyColors.HeroGoldGlow.copy(alpha = 0.20f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = TestimonyColors.HeroGoldGlow,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(MM.space.s))
                    Text(
                        "FAITHFULNESS JOURNAL",
                        style = MM.type.caption,
                        fontWeight = FontWeight.Bold,
                        color = TestimonyColors.HeroGoldGlow
                    )
                }

                Text(
                    "Remember every way God has come through",
                    style = MM.type.title.copy(fontFamily = FontFamily.Serif),
                    color = TestimonyColors.HeroOnDark
                )

                Text(
                    "Kept private on your phone unless you encourage a Circle or create a share card.",
                    style = MM.type.secondary,
                    color = TestimonyColors.HeroSubtleOnDark
                )

                // Four-column milestone counters
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MM.radius.small)
                        .background(TestimonyColors.HeroOnDark.copy(alpha = 0.09f))
                        .padding(horizontal = MM.space.m, vertical = MM.space.s),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    HeroMetric(count = storyCount, label = "Stories")
                    HeroMetric(count = answeredCount, label = "Answered")
                    HeroMetric(count = amenCount, label = "Amens")
                    HeroMetric(count = sharedCount, label = "Shared")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                ) {
                    Button(
                        onClick = onWriteStory,
                        shape = MM.radius.small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TestimonyColors.HeroGoldGlow,
                            contentColor = TestimonyColors.HeroDeepIndigo
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Write Story", style = MM.type.secondary, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = onSpeakStory,
                        shape = MM.radius.small,
                        border = BorderStroke(1.dp, TestimonyColors.HeroOnDark.copy(alpha = 0.35f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TestimonyColors.HeroOnDark)
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(16.dp), tint = TestimonyColors.HeroGoldGlow)
                        Spacer(Modifier.width(6.dp))
                        Text("Speak", style = MM.type.secondary, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroMetric(count: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            count.toString(),
            style = MM.type.heading.copy(fontFamily = FontFamily.Serif),
            fontWeight = FontWeight.Bold,
            color = TestimonyColors.HeroGoldGlow
        )
        Text(label, style = MM.type.caption, color = TestimonyColors.HeroSubtleOnDark)
    }
}

@Composable
private fun ContinueDraftBanner(
    draft: TestimonyStory,
    totalDrafts: Int,
    onResume: () -> Unit,
    onDiscard: () -> Unit
) {
    val c = MM.colors
    Surface(
        onClick = onResume,
        shape = MM.radius.card,
        color = TestimonyColors.DraftWash.copy(alpha = if (c.isDark) 0.14f else 0.72f),
        border = BorderStroke(1.dp, TestimonyColors.DraftAmber.copy(alpha = 0.38f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MM.space.m, vertical = MM.space.m),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.EditNote, contentDescription = null, tint = TestimonyColors.DraftAmber, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(MM.space.s))
            Column(Modifier.weight(1f)) {
                Text(
                    if (totalDrafts > 1) "Unfinished drafts ($totalDrafts)" else "Unfinished draft saved",
                    style = MM.type.caption,
                    fontWeight = FontWeight.Bold,
                    color = TestimonyColors.DraftAmber
                )
                Text(
                    draft.title.ifBlank { draft.excerpt.ifBlank { "Tap to continue where you left off" } },
                    style = MM.type.secondary,
                    fontWeight = FontWeight.Medium,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onDiscard) {
                Text("Discard", style = MM.type.caption, color = c.inkMuted)
            }
            Surface(shape = MM.radius.pill, color = TestimonyColors.DraftAmber) {
                Text(
                    "Resume",
                    style = MM.type.caption,
                    fontWeight = FontWeight.SemiBold,
                    color = TestimonyColors.HeroOnDark,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun RememberSpotlightCard(
    story: TestimonyStory,
    onOpen: () -> Unit,
    onCelebrateAgain: () -> Unit
) {
    val c = MM.colors
    val dateLabel = remember(story.storyDateMs) {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(story.storyDateMs))
    }
    Surface(
        onClick = onOpen,
        shape = MM.radius.card,
        color = if (c.isDark) TestimonyColors.SpotlightTintDark else TestimonyColors.SpotlightTintLight,
        border = BorderStroke(1.dp, FaithGold.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(MM.space.m), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.History, contentDescription = null, tint = FaithGold, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "REMEMBER WHAT GOD DID · $dateLabel",
                    style = MM.type.caption,
                    fontWeight = FontWeight.Bold,
                    color = FaithGold
                )
            }
            Text(
                story.title.ifBlank { "A moment of faithfulness" },
                style = MM.type.heading.copy(fontFamily = FontFamily.Serif),
                color = c.ink
            )
            if (story.excerpt.isNotBlank()) {
                Text(
                    story.excerpt,
                    style = MM.type.secondary,
                    color = c.inkSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Tap to read story →", style = MM.type.caption, fontWeight = FontWeight.Medium, color = FaithGold)
                Surface(
                    onClick = onCelebrateAgain,
                    shape = MM.radius.pill,
                    color = FaithGold.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, FaithGold.copy(alpha = 0.40f))
                ) {
                    Text(
                        if (story.rejoiceCount > 0) "Still grateful · ${story.rejoiceCount}" else "Still grateful",
                        style = MM.type.caption,
                        fontWeight = FontWeight.SemiBold,
                        color = FaithGold,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OpenPrayersTestifyStrip(
    prayers: List<com.craftflowtechnologies.meetingmind.core.model.Note>,
    onTestifyPrayer: (com.craftflowtechnologies.meetingmind.core.model.Note) -> Unit
) {
    val c = MM.colors
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        Text(
            "Did God answer a prayer you've been carrying?",
            style = MM.type.caption,
            fontWeight = FontWeight.Medium,
            color = c.inkSecondary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(MM.space.s)
        ) {
            prayers.forEach { prayer ->
                Surface(
                    onClick = { onTestifyPrayer(prayer) },
                    shape = MM.radius.small,
                    color = c.surfaceRaised,
                    border = BorderStroke(1.dp, FaithGold.copy(alpha = 0.30f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = MM.space.m, vertical = MM.space.s),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Verified, contentDescription = null, tint = FaithGold, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            prayer.title.ifBlank { "Prayer request" },
                            style = MM.type.secondary,
                            fontWeight = FontWeight.Medium,
                            color = c.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Mark answered", style = MM.type.caption, fontWeight = FontWeight.SemiBold, color = FaithGold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterChip(
    label: String,
    dotColor: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    val c = MM.colors
    Surface(
        onClick = onClick,
        shape = MM.radius.pill,
        color = if (selected) dotColor.copy(alpha = 0.16f) else c.surfaceSunk,
        border = BorderStroke(1.dp, if (selected) dotColor else c.lineSoft)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(7.dp).background(dotColor, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MM.type.caption,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) c.ink else c.inkSecondary
            )
        }
    }
}

@Composable
private fun TestimoniesEmptyState(
    tab: TestimonyFilterTab,
    hasCategoryFilter: Boolean,
    onClearFilter: () -> Unit,
    onWriteStory: () -> Unit,
    onSpeakStory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = MM.colors
    val (headline, body) = when {
        hasCategoryFilter -> "No matching stories yet" to "Clear your filters to see all testimonies, or record a new story."
        tab == TestimonyFilterTab.DRAFTS -> "No unfinished drafts" to "Any testimony you start will auto-save here automatically if you step away."
        tab == TestimonyFilterTab.ANSWERED_PRAYERS -> "No prayer-linked testimonies yet" to "Tap any open prayer request above — or mark a prayer answered — to connect your journey."
        tab == TestimonyFilterTab.IN_CIRCLES -> "No Circle testimonies yet" to "Share any story directly into a Circle below, or read testimonies posted by Circle members here."
        else -> "Start your Wall of Remembrance" to "Record a voice story or write down a moment of God's faithfulness. Everything stays private unless you share it."
    }

    Surface(
        shape = MM.radius.card,
        color = c.surfaceRaised,
        border = BorderStroke(1.dp, c.lineSoft),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = MM.space.l, vertical = MM.space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MM.space.s)
        ) {
            Icon(Icons.Filled.Celebration, contentDescription = null, tint = FaithGold, modifier = Modifier.size(30.dp))
            Text(
                headline,
                style = MM.type.heading.copy(fontFamily = FontFamily.Serif),
                color = c.ink,
                textAlign = TextAlign.Center
            )
            Text(body, style = MM.type.secondary, color = c.inkSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(MM.space.xs))
            if (hasCategoryFilter) {
                OutlinedButton(onClick = onClearFilter, shape = MM.radius.pill) {
                    Text("Show all moments", color = c.ink)
                }
            } else if (tab != TestimonyFilterTab.IN_CIRCLES) {
                Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    Button(
                        onClick = onWriteStory,
                        shape = MM.radius.small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FaithGold,
                            contentColor = TestimonyColors.HeroDeepIndigo
                        )
                    ) {
                        Text("Write Story", fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(onClick = onSpeakStory, shape = MM.radius.small) {
                        Icon(Icons.Filled.Mic, contentDescription = null, tint = FaithGold, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Speak", color = c.ink)
                    }
                }
            }
        }
    }
}

@Composable
private fun TestimonyStoryCard(
    story: TestimonyStory,
    onClick: () -> Unit,
    onToggleRejoice: () -> Unit,
    onShareToCircle: (() -> Unit)?,
    onShareCard: () -> Unit,
    onOpenScripture: (String) -> Unit
) {
    val c = MM.colors
    val dateLabel = remember(story.storyDateMs) {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(story.storyDateMs))
    }
    val durationLabel = remember(story.prayerDurationDays) { formatDurationLabel(story.prayerDurationDays) }
    val accent = story.category?.let(::testimonyCategoryAccent)
        ?: if (story.circleName != null) TestimonyColors.CircleSage else FaithGold

    Surface(
        onClick = onClick,
        shape = MM.radius.card,
        color = c.surfaceRaised,
        border = BorderStroke(
            width = 1.dp,
            color = if (story.isDraft) TestimonyColors.DraftAmber.copy(alpha = 0.45f) else c.lineSoft
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Subtle leading story-category accent ribbon
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(if (story.isDraft) TestimonyColors.DraftAmber else accent)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(MM.space.m),
                verticalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                // Meta row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(Modifier.size(8.dp).background(accent, CircleShape))
                        Text(
                            when {
                                story.isDraft -> "Draft"
                                story.circleName != null -> "${story.authorLabel} · ${story.circleName}"
                                story.category != null -> story.category.label
                                else -> "Your Testimony"
                            },
                            style = MM.type.caption,
                            fontWeight = FontWeight.SemiBold,
                            color = accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text("· $dateLabel", style = MM.type.caption, color = c.inkMuted, maxLines = 1)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (story.hasAudio) {
                            Icon(Icons.Filled.GraphicEq, contentDescription = "Voice story", tint = FaithGold, modifier = Modifier.size(15.dp))
                        }
                        if (story.sharedToCircles.isNotEmpty() && story.isPersonal) {
                            Surface(
                                shape = MM.radius.pill,
                                color = TestimonyColors.CircleSage.copy(alpha = 0.14f)
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(Icons.Filled.Groups, contentDescription = null, tint = TestimonyColors.CircleSage, modifier = Modifier.size(12.dp))
                                    Text(
                                        "${story.sharedToCircles.size}",
                                        style = MM.type.caption,
                                        fontWeight = FontWeight.Bold,
                                        color = TestimonyColors.CircleSage
                                    )
                                }
                            }
                        }
                    }
                }

                // Headline
                Text(
                    story.title.ifBlank { "Untitled testimony draft" },
                    style = MM.type.heading.copy(fontFamily = FontFamily.Serif),
                    color = c.ink
                )

                // Structured Beat arc preview (Before -> How God Moved) or Excerpt
                val beats = story.beats
                if (beats.situation.isNotBlank() && beats.whatHappened.isNotBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Before: ${beats.situation}",
                            style = MM.type.secondary,
                            color = c.inkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            beats.whatHappened,
                            style = MM.type.secondary,
                            color = c.inkSecondary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else if (story.excerpt.isNotBlank()) {
                    Text(
                        story.excerpt,
                        style = MM.type.secondary,
                        color = c.inkSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Duration + Scripture pills
                if (durationLabel != null || story.verseRef != null) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                    ) {
                        durationLabel?.let { label ->
                            Surface(
                                shape = MM.radius.pill,
                                color = FaithGold.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, FaithGold.copy(alpha = 0.28f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = FaithGold, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(label, style = MM.type.caption, fontWeight = FontWeight.Medium, color = FaithGold)
                                }
                            }
                        }

                        story.verseRef?.let { ref ->
                            Surface(
                                onClick = { onOpenScripture(ref) },
                                shape = MM.radius.pill,
                                color = c.surfaceSunk,
                                border = BorderStroke(1.dp, c.lineSoft)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.MenuBook, contentDescription = null, tint = FaithGold, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(ref, style = MM.type.caption, fontWeight = FontWeight.Medium, color = c.ink)
                                }
                            }
                        }
                    }
                }

                // Footer actions
                if (story.isDraft) {
                    Text(
                        "Tap to finish writing or publish",
                        style = MM.type.caption,
                        fontWeight = FontWeight.Medium,
                        color = TestimonyColors.DraftAmber
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                    ) {
                        Surface(
                            onClick = onToggleRejoice,
                            shape = MM.radius.pill,
                            color = if (story.userRejoiced) FaithGold.copy(alpha = 0.16f) else c.surfaceSunk,
                            border = BorderStroke(1.dp, if (story.userRejoiced) FaithGold else c.lineSoft)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = MM.space.m, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (story.userRejoiced) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = null,
                                    tint = if (story.userRejoiced) FaithGold else c.inkSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    buildString {
                                        append(if (story.userRejoiced) "Amen" else "Amen")
                                        if (story.rejoiceCount > 0) append(" · ${story.rejoiceCount}")
                                    },
                                    style = MM.type.caption,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (story.userRejoiced) FaithGold else c.inkSecondary
                                )
                            }
                        }

                        if (onShareToCircle != null) {
                            Surface(
                                onClick = onShareToCircle,
                                shape = MM.radius.pill,
                                color = TestimonyColors.CircleSage.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, TestimonyColors.CircleSage.copy(alpha = 0.35f)),
                                modifier = Modifier.testTag("testimony_card_share_circle")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = MM.space.m, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Groups, contentDescription = null, tint = TestimonyColors.CircleSage, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Circle", style = MM.type.caption, fontWeight = FontWeight.SemiBold, color = TestimonyColors.CircleSage)
                                }
                            }
                        }

                        Surface(
                            onClick = onShareCard,
                            shape = MM.radius.pill,
                            color = c.surfaceSunk,
                            border = BorderStroke(1.dp, c.lineSoft)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = MM.space.m, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Image, contentDescription = null, tint = c.inkSecondary, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Card", style = MM.type.caption, fontWeight = FontWeight.Medium, color = c.inkSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}
