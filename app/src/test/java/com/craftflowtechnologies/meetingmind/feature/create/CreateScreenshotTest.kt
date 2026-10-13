package com.craftflowtechnologies.meetingmind.feature.create

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.create.CardVersion
import com.craftflowtechnologies.meetingmind.core.create.CreateBackground
import com.craftflowtechnologies.meetingmind.core.create.CreateCard
import com.craftflowtechnologies.meetingmind.core.create.CreateCardRenderer
import com.craftflowtechnologies.meetingmind.core.create.CreateDesign
import com.craftflowtechnologies.meetingmind.core.create.CreateFontPair
import com.craftflowtechnologies.meetingmind.core.create.CreateFormat
import com.craftflowtechnologies.meetingmind.core.create.CreateRenderInput
import com.craftflowtechnologies.meetingmind.core.create.CreateSourceKind
import com.craftflowtechnologies.meetingmind.core.create.CreateVibe
import com.craftflowtechnologies.meetingmind.core.create.CreateVibePolicy
import com.craftflowtechnologies.meetingmind.core.create.ResolvedScripture
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Create studio (all four steps, all three formats), the Faith-home row and the gallery, in light and dark. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class CreateScreenshotTest {
    @get:Rule val rule = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val verse = ResolvedScripture(
        "Psalm 23:1", "The Lord is my shepherd; I shall not want.", 3034, "BSB",
        "The Holy Bible, Berean Standard Bible, BSB is produced in cooperation with Bible Hub."
    )

    private fun card(format: CreateFormat, vibe: CreateVibe = CreateVibe.PEACEFUL, design: CreateDesign = CreateDesign()) = CreateCard(
        id = "shot", source = CreateSourceKind.VERSE, sourceText = "Rest", sourceRef = "Psalm 23:1", vibe = vibe,
        current = CardVersion("You don't have to carry today alone.", "Psalm 23:1", "Written"),
        past = listOf(CardVersion("Carry less.", null, "Original")), format = format,
        design = design.copy(background = if (design.background == CreateBackground.Pack("dawn")) com.craftflowtechnologies.meetingmind.core.create.CreateBackdrops.default(vibe) else design.background)
    )

    private fun ui(card: CreateCard, step: CreateStep, extra: (CreateUi) -> CreateUi = { it }) = extra(
        CreateUi(
            card = card, scripture = verse, step = step,
            vibes = CreateVibePolicy.ordered(card.source), suggested = CreateVibe.PEACEFUL,
            prompt = "Rest", verseInput = "Psalm 23:1", installed = InstalledApps(whatsapp = true, instagram = true)
        )
    )

    private fun shoot(name: String, dark: Boolean, ui: CreateUi) {
        val preview = CreateCardRenderer.render(context, CreateRenderInput(ui.card.text, ui.scripture, ui.card.format, ui.card.design), 0.4f).asImageBitmap()
        rule.setContent {
            MeetMindTheme(darkTheme = dark) {
                CreateStudioContent(ui, preview, fits = true, companion = CompanionOffer("Zuri", "Peaceful"), actions = CreateActions.None)
            }
        }
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/create/${name}_${if (dark) "dark" else "light"}.png")
    }

    // Z-22: the Design step with the companion on (pose chips, the suggestion line, anchor chips, the reveal settled).
    private fun companionShot(name: String, dark: Boolean, source: CreateSourceKind, vibe: CreateVibe, design: CreateDesign = CreateDesign(includeCompanion = true)) {
        val c = card(CreateFormat.STORY, vibe, design).copy(source = source)
        val u = ui(c, CreateStep.DESIGN)
        val allowed = com.craftflowtechnologies.meetingmind.core.create.CompanionPose.allowed(source, vibe)
        val suggested = com.craftflowtechnologies.meetingmind.core.create.CompanionPose.suggested(source, vibe)
        val mode = com.craftflowtechnologies.meetingmind.core.create.CompanionPose.effective(source, vibe, design.companionPose)
        val bmp = com.craftflowtechnologies.meetingmind.core.ui.mm.companion.renderCompanionBitmap(
            com.craftflowtechnologies.meetingmind.core.companion.CompanionForm.ZURI, mode,
            com.craftflowtechnologies.meetingmind.ui.theme.companionPalette(com.craftflowtechnologies.meetingmind.ui.theme.MMAccent.Indigo.light, false),
            com.craftflowtechnologies.meetingmind.core.create.CompanionPlacement.maxExportBitmapPx
        ).asAndroidBitmap()
        val preview = CreateCardRenderer.render(context, CreateRenderInput(c.text, u.scripture, c.format, c.design), 0.4f, bmp).asImageBitmap()
        rule.setContent {
            MeetMindTheme(darkTheme = dark) {
                CreateStudioContent(
                    u, preview, fits = true,
                    companion = CompanionOffer("Zuri", mode.name, com.craftflowtechnologies.meetingmind.core.companion.CompanionForm.ZURI, mode, suggested, allowed,
                        prayerRequest = source == CreateSourceKind.PRAYER, sacred = source.faith),
                    actions = CreateActions.None
                )
            }
        }
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/create/${name}_${if (dark) "dark" else "light"}.png")
    }

    @Test fun design_companion_quote_light() = companionShot("design_companion_quote", false, CreateSourceKind.VERSE, CreateVibe.PEACEFUL)
    @Test fun design_companion_quote_dark() = companionShot("design_companion_quote", true, CreateSourceKind.VERSE, CreateVibe.PEACEFUL)
    @Test fun design_companion_prayer_light() = companionShot("design_companion_prayer", false, CreateSourceKind.PRAYER, CreateVibe.PRAYERFUL)
    @Test fun design_companion_prayer_dark() = companionShot("design_companion_prayer", true, CreateSourceKind.PRAYER, CreateVibe.PRAYERFUL)
    @Test fun design_companion_win_light() = companionShot("design_companion_win", false, CreateSourceKind.ACHIEVEMENT, CreateVibe.CELEBRATORY,
        CreateDesign(includeCompanion = true, companionPose = com.craftflowtechnologies.meetingmind.core.companion.CreateMode.JOYFUL, companionAnchor = com.craftflowtechnologies.meetingmind.core.create.CompanionAnchor.CORNER_END))
    @Test fun design_companion_win_dark() = companionShot("design_companion_win", true, CreateSourceKind.ACHIEVEMENT, CreateVibe.CELEBRATORY,
        CreateDesign(includeCompanion = true, companionPose = com.craftflowtechnologies.meetingmind.core.companion.CreateMode.JOYFUL, companionAnchor = com.craftflowtechnologies.meetingmind.core.create.CompanionAnchor.CORNER_END))

    @Test fun `a prayer request offers only Prayerful and Peaceful chips, the others are hidden`() {
        val c = card(CreateFormat.STORY, CreateVibe.PRAYERFUL, CreateDesign(includeCompanion = true)).copy(source = CreateSourceKind.PRAYER)
        val allowed = com.craftflowtechnologies.meetingmind.core.create.CompanionPose.allowed(CreateSourceKind.PRAYER, CreateVibe.PRAYERFUL)
        rule.setContent {
            MeetMindTheme {
                CreateStudioContent(ui(c, CreateStep.DESIGN), null, true,
                    CompanionOffer("Zuri", "Prayerful", com.craftflowtechnologies.meetingmind.core.companion.CompanionForm.ZURI,
                        com.craftflowtechnologies.meetingmind.core.companion.CreateMode.PRAYERFUL, com.craftflowtechnologies.meetingmind.core.companion.CreateMode.PRAYERFUL, allowed, prayerRequest = true, sacred = true),
                    CreateActions.None)
            }
        }
        rule.onNodeWithTag("pose_PRAYERFUL").assertExists()
        rule.onNodeWithTag("pose_PEACEFUL").assertExists()
        listOf("JOYFUL", "GRATEFUL", "REFLECTIVE", "CELEBRATORY").forEach { rule.onAllNodesWithTag("pose_$it").assertCountEquals(0) }
        rule.onNodeWithText("On prayer requests, only Prayerful or Peaceful.").assertExists()
    }

    // Words step, story
    @Test fun words_story_light() = shoot("words_story", false, ui(card(CreateFormat.STORY), CreateStep.WORDS))
    @Test fun words_story_dark() = shoot("words_story", true, ui(card(CreateFormat.STORY), CreateStep.WORDS))

    // The honest failure and the offline path
    @Test fun words_failed_light() = shoot("words_failed", false, ui(card(CreateFormat.STORY), CreateStep.WORDS) {
        it.copy(status = CreateStatus(StatusTone.Error, "Couldn't generate — try again or write your own.", StatusAction.RETRY))
    })
    @Test fun words_offline_starters_dark() = shoot("words_offline_starters", true, ui(card(CreateFormat.SQUARE, CreateVibe.FUNNY), CreateStep.WORDS) {
        it.copy(
            status = CreateStatus(StatusTone.Info, "Writing with AI needs Internet mode and a Gemini key. Write your own, or start from a starter.", StatusAction.STARTERS),
            starters = com.craftflowtechnologies.meetingmind.core.create.CreateStarters.forVibe(CreateVibe.FUNNY, CreateSourceKind.FREE_PROMPT)
        )
    })

    // Format step, one per format
    @Test fun format_story_light() = shoot("format_story", false, ui(card(CreateFormat.STORY), CreateStep.FORMAT))
    @Test fun format_square_light() = shoot("format_square", false, ui(card(CreateFormat.SQUARE), CreateStep.FORMAT))
    @Test fun format_portrait_dark() = shoot("format_portrait", true, ui(card(CreateFormat.PORTRAIT), CreateStep.FORMAT))

    // Design step
    @Test fun design_story_light() = shoot("design_story", false, ui(card(CreateFormat.STORY, CreateVibe.PEACEFUL), CreateStep.DESIGN))
    @Test fun design_square_dark() = shoot("design_square", true, ui(card(CreateFormat.SQUARE, CreateVibe.PRAYERFUL, CreateDesign(fontPair = CreateFontPair.EDITORIAL, centered = false)), CreateStep.DESIGN))
    @Test fun design_portrait_light() = shoot("design_portrait", false, ui(card(CreateFormat.PORTRAIT, CreateVibe.GRATEFUL, CreateDesign(fontPair = CreateFontPair.MODERN)), CreateStep.DESIGN))

    // Share step (watermark off)
    @Test fun share_story_light() = shoot("share_story", false, ui(card(CreateFormat.STORY), CreateStep.SHARE))
    @Test fun share_square_dark() = shoot("share_square", true, ui(card(CreateFormat.SQUARE, CreateVibe.JOYFUL), CreateStep.SHARE))
    @Test fun share_portrait_light_nodirect() = shoot("share_portrait_sheet_only", false, ui(card(CreateFormat.PORTRAIT), CreateStep.SHARE) { it.copy(installed = InstalledApps()) })

    // The quiet row on Faith home
    @Composable private fun RowFrame() = Column(Modifier.padding(MM.space.l)) {
        CreateRow(verse = verse, mood = CreateVibe.PEACEFUL, onOpen = {}, onSeeMore = {})
    }
    @Test fun create_row_light() { rule.setContent { MeetMindTheme(darkTheme = false) { RowFrame() } }; rule.onRoot().captureRoboImage("src/test/screenshots/create/create_row_light.png") }
    @Test fun create_row_dark() { rule.setContent { MeetMindTheme(darkTheme = true) { RowFrame() } }; rule.onRoot().captureRoboImage("src/test/screenshots/create/create_row_dark.png") }

    @Test fun gallery_light() {
        val cards = listOf(card(CreateFormat.STORY), card(CreateFormat.SQUARE, CreateVibe.GRATEFUL).copy(id = "b", pinned = true), card(CreateFormat.PORTRAIT, CreateVibe.JOYFUL).copy(id = "c"))
        rule.setContent { MeetMindTheme(darkTheme = false) { CreateGalleryContent(cards, { null }, {}, {}, {}, {}, {}) } }
        rule.onRoot().captureRoboImage("src/test/screenshots/create/gallery_light.png")
    }
    @Test fun gallery_empty_dark() {
        rule.setContent { MeetMindTheme(darkTheme = true) { CreateGalleryContent(emptyList(), { null }, {}, {}, {}, {}, {}) } }
        rule.onRoot().captureRoboImage("src/test/screenshots/create/gallery_empty_dark.png")
    }
}
