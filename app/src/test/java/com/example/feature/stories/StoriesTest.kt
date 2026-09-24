package com.example.feature.stories

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.MeetMindDatabase
import com.example.core.identity.AppIdentity
import com.example.core.model.NotebookSpace
import com.example.core.share.BackgroundSpec
import com.example.core.share.ShareCardContent
import com.example.feature.share.ShareRequest
import com.example.feature.share.ShareStudioScreen
import com.example.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class StoriesTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: MeetMindDatabase
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java).allowMainThreadQueries().build()
        MeetMindDatabase.setInstanceForTest(db)
    }
    @After fun tearDown() { MeetMindDatabase.setInstanceForTest(null); db.close() }

    @Test fun `faith people get a quote story even on an empty day, professionals don't`() = runBlocking {
        val faith = StoryBuilder(context, db).build(AppIdentity(spaces = setOf(NotebookSpace.FAITH)), LocalDate.of(2026, 9, 24))
        assertTrue(faith.any { it.kind == StoryKind.QUOTE && it.footer != null && it.share != null })
        val work = StoryBuilder(context, db).build(AppIdentity(spaces = setOf(NotebookSpace.WORK)), LocalDate.of(2026, 9, 24))
        assertFalse(work.any { it.kind == StoryKind.QUOTE || it.kind == StoryKind.VERSE || it.kind == StoryKind.PRAYING_FOR })
    }

    @Test fun `seen stories are remembered for the day`() {
        StoriesSeen.mark(context, StoryKind.VERSE)
        assertTrue(StoryKind.VERSE.name in StoriesSeen.seen(context))
    }

    @Test fun `a story looks like a story`() {
        val stories = listOf(
            Story(StoryKind.VERSE, "Verse of the day", title = "Matthew 11:28 · BSB", body = "Come to Me, all you who are weary and burdened, and I will give you rest.",
                footer = "Berean Standard Bible", background = BackgroundSpec.Pack("dawn"), quoted = true,
                share = ShareCardContent(null, "x"), open = StoryOpen.Devotional, openLabel = "Read the chapter"),
            Story(StoryKind.DAY, "Your day", title = "3 things today", body = "", background = BackgroundSpec.Pack("night"), lines = listOf("9:00  ·  Team sync", "13:00  ·  Lunch with Sam", "18:30  ·  Bible study"))
        )
        compose.setContent { MeetMindTheme { StoryPager(stories, null, {}, {}, {}, autoAdvance = false) } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/story_verse.png")
    }

    @Test fun `the share studio`() {
        val request = ShareRequest(ShareCardContent("Verse of the day", "Come to Me, all you who are weary and burdened, and I will give you rest.", "Matthew 11:28 · BSB", "Berean Standard Bible"), theme = "rest")
        compose.setContent { MeetMindTheme { ShareStudioScreen(request, {}) } }
        compose.waitForIdle()
        Thread.sleep(1500)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/share_studio.png")
    }
}
