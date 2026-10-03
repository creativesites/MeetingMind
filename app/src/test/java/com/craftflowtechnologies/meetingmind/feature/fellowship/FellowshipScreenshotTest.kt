package com.craftflowtechnologies.meetingmind.feature.fellowship

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.craftflowtechnologies.meetingmind.core.model.Circle
import com.craftflowtechnologies.meetingmind.core.model.CircleMember
import com.craftflowtechnologies.meetingmind.core.model.CirclePrayer
import com.craftflowtechnologies.meetingmind.core.model.CirclePrayerStatus
import com.craftflowtechnologies.meetingmind.core.model.CircleSermon
import com.craftflowtechnologies.meetingmind.core.model.CircleTestimony
import com.craftflowtechnologies.meetingmind.core.model.MemberRole
import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.model.NoteStatus
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.feature.fellowship.circles.CircleScreenContent
import com.craftflowtechnologies.meetingmind.feature.fellowship.circles.CirclesHubSection
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h2600dp-xxhdpi", sdk = [34])
class FellowshipScreenshotTest {
    @get:Rule val compose = createComposeRule()

    // Test-only sample notes: these never reach the app.
    private fun note(id: String, title: String, type: RecordingType, status: NoteStatus = NoteStatus.OPEN) = Note(
        id = id, title = title, workflow = type, notebookId = null, createdAt = 1_700_000_000_000, updatedAt = 1_700_000_000_000,
        eventDate = null, pinned = false, isPrivate = false, status = status, answeredAt = null, metadata = emptyMap(),
        plainText = "Lord, be near to my family this week."
    )

    private val notes = listOf(
        note("1", "Sunday: Walking in Grace", RecordingType.SERMON),
        note("2", "Romans 8 study", RecordingType.BIBLE_STUDY),
        note("3", "For my brother's job interview", RecordingType.PRAYER_REQUEST)
    )

    private fun shot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        val dark = androidx.compose.runtime.mutableStateOf(true)
        compose.setContent { MeetMindTheme(darkTheme = dark.value) { content() } }
        for (d in listOf(true, false)) {
            dark.value = d
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/fellowship_${name}_${if (d) "dark" else "light"}.png")
        }
    }

    @Test fun hub() = shot("hub") { FellowshipHubScreen(onNavigateBack = {}, onPick = {}) }

    private val testCircle = Circle(
        id = "circle-1",
        name = "Tuesday Men's Fellowship",
        description = "Weekly prayer & sermon reflection",
        avatarEmoji = "🕊️",
        inviteCode = "mindcircle://join?id=circle-1&name=Tuesday+Men's+Fellowship&key=abc",
        encryptionKeyBase64 = "key",
        createdByMemberId = "u1",
        createdAt = 1_700_000_000_000,
        memberCount = 4
    )

    @Test fun hubWithCircles() = shot("hub_with_circles") {
        FellowshipHubScreen(
            onNavigateBack = {},
            onPick = {},
            circlesSection = {
                CirclesHubSection(
                    circles = listOf(testCircle),
                    cachedDisplayName = "David",
                    onOpenCircle = {},
                    onCreateCircle = { _, _, _, _ -> },
                    onJoinCircle = { _, _ -> }
                )
            }
        )
    }

    @Test fun pickerEmpty() = shot("picker_empty") {
        FellowshipPickerScreen(FellowshipKind.PRAYER, emptyList(), {}, {}, {})
    }

    @Test fun pickerPopulated() = shot("picker_full") {
        FellowshipPickerScreen(FellowshipKind.STUDY, notes, {}, {}, {})
    }

    @Test fun draft() = shot("draft") {
        FellowshipDraftScreen(FellowshipKind.PRAYER, notes[2], {}, {})
    }

    private val sections = listOf(
        GuideSection("a", "Key ideas", listOf("Grace is a gift, not a wage.", "God's love does not depend on our performance.")),
        GuideSection("b", "Discussion questions", listOf("Where do you try to earn approval?", "What would resting in grace look like this week?"))
    )

    private fun guide(name: String, state: GuideState, s: List<GuideSection> = emptyList()) = shot("guide_$name") {
        GroupGuideContent("Sunday: Walking in Grace", state, s, SnackbarHostState(), {}, {}, {}, { _, _, _ -> }, { _, _ -> }, {}, {})
    }

    @Test fun guideIdle() = guide("idle", GuideState.Idle)
    @Test fun guideWorking() = guide("working", GuideState.Working(null))
    @Test fun guideFailed() = guide("failed", GuideState.Failed("The model could not be reached."))
    @Test fun guideReady() = guide("ready", GuideState.Ready("j"), sections)

    private val circleMembers = listOf(
        CircleMember("u1", "circle-1", "Pastor Mark", MemberRole.ADMIN, 1_700_000_000_000, isSelf = false),
        CircleMember("u2", "circle-1", "David K", MemberRole.MEMBER, 1_700_010_000_000, isSelf = true)
    )

    private val circlePrayers = listOf(
        CirclePrayer(
            id = "p1", circleId = "circle-1", authorName = "Pastor Mark", authorId = "u1",
            requestText = "Please pray for our community outreach event this Saturday morning.",
            isUrgent = true, status = CirclePrayerStatus.ACTIVE, prayerCount = 3, prayedByMe = true,
            createdAt = 1_700_020_000_000
        )
    )

    private val circleTestimonies = listOf(
        CircleTestimony(
            id = "t1", circleId = "circle-1", authorName = "David K", authorId = "u2",
            title = "Praise God: surgery went smoothly!",
            storyText = "The doctors said recovery is ahead of schedule. Thank you all for praying!",
            scriptureRef = "Psalm 103:1-3", prayerRequestId = null, praiseCount = 4, praisedByMe = true,
            createdAt = 1_700_030_000_000
        )
    )

    private val circleSermons = listOf(
        CircleSermon(
            id = "s1", circleId = "circle-1", title = "Walking in Grace", preacher = "Pastor Mark",
            scripturePassage = "Romans 8:1-17", sermonDate = "Last Sunday",
            discussionGuideJson = "{}", transcriptSummary = "Key points: No condemnation in Christ Jesus. Walking according to the Spirit.",
            audioDurationSec = 2400L, createdAt = 1_700_000_000_000
        )
    )

    @Test fun circlePrayerWall() = shot("circle_prayer_wall") {
        CircleScreenContent(
            circle = testCircle,
            members = circleMembers,
            prayers = circlePrayers,
            testimonies = circleTestimonies,
            sermons = circleSermons,
            cachedDisplayName = "David K",
            onNavigateBack = {},
            onPrayFor = {},
            onMarkAnswered = {},
            onPraiseTestimony = {},
            onOpenPostPrayer = {},
            onOpenPostTestimony = {},
            onOpenShareSermon = {},
            onLeaveCircle = {},
            onShareInvite = {},
            onCopyInvite = {}
        )
    }

    @Test fun noInvisibleTextPatterns() {
        val dir = File("src/main/java/com/craftflowtechnologies/meetingmind/feature/fellowship")
        val bad = listOf(".forTheme()", "Color(0x", "Color.White", "Color.Black", "OnInk", "\"Me\"")
        val offenders = dir.walkTopDown().filter { f -> f.extension == "kt" && f.name != "FellowshipUi.kt" }.toList()
            .flatMap { f -> bad.filter { f.readText().contains(it) }.map { "${f.name}: $it" } }
        assertTrue("Forbidden: $offenders", offenders.isEmpty())
    }
}
