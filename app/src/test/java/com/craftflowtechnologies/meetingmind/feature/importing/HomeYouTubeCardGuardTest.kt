package com.craftflowtechnologies.meetingmind.feature.importing

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * "Notes from a YouTube video" looks the same on every home: each one draws the shared
 * core/ui YouTubeNotesCard, with its own tag, and none keeps a look of its own.
 */
class HomeYouTubeCardGuardTest {

    private val root = File("src/main/java/com/craftflowtechnologies/meetingmind")
    private fun source(path: String) = File(root, path).readText()

    /** Each home that can be chosen (plus Faith's space home) and the tag its card carries. */
    private val homes = mapOf(
        "feature/today/home/CalmHome.kt" to "calm_youtube",
        "feature/work/ProfessionalHome.kt" to "work_youtube",
        "feature/faith/FaithHome.kt" to "faith_youtube_sermon"
    )

    @Test fun `every home draws the shared card with its own tag`() {
        val problems = homes.flatMap { (path, tag) ->
            val text = source(path)
            listOfNotNull(
                "$path doesn't draw YouTubeNotesCard".takeIf { "YouTubeNotesCard(" !in text },
                "$path has no \"$tag\" tag".takeIf { "\"$tag\"" !in text }
            )
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun `Today's card is the shared card too`() {
        assertTrue("TodayScreen no longer shows YouTubeHomeCard", "YouTubeHomeCard(" in source("feature/today/TodayScreen.kt"))
        val link = source("feature/importing/YouTubeLink.kt")
        val body = link.substringAfter("fun YouTubeHomeCard(").substringBefore("\n}\n")
        assertTrue("YouTubeHomeCard should draw the shared YouTubeNotesCard", "YouTubeNotesCard(" in body)
    }

    /**
     * The Learning home (Study design) offers the video as a "From a video link" tile beside its
     * other ways in, not the shared card, so the tile is the one way in and it is tagged.
     */
    @Test fun `the Learning home offers the video link as its tile`() {
        val learn = source("feature/learn/ui/home/LearningHomeScreen.kt")
        assertTrue("Learning home should draw the video tile", "learning_tile_video" in learn)
        assertTrue("Learning home should open YouTube from its tile", "onClick = onOpenYouTube" in learn)
    }

    @Test fun `no home keeps an old YouTube row of its own`() {
        val problems = homes.keys.filter { path ->
            // The old Faith entry was a ListRow with a SmartDisplay icon; any such row means a second look.
            Regex("""ListRow\([^)]*YouTube""", RegexOption.DOT_MATCHES_ALL).containsMatchIn(source(path))
        }
        assertTrue("these homes still draw their own YouTube row: $problems", problems.isEmpty())
    }
}
