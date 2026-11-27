package com.craftflowtechnologies.meetingmind.feature.importing

import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.navigation.AppMenuModel
import com.craftflowtechnologies.meetingmind.core.navigation.MenuTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Notes from a YouTube video": the presets, the share rule, the clipboard rule and where it appears in the menu. */
class YouTubeLinkTest {

    private val all = NotebookSpace.entries.toSet()

    @Test fun `the sheet pre-selects the kind for where it was opened`() {
        assertEquals(RecordingType.SERMON, YouTubeLinks.startKind(NotebookSpace.FAITH, all))
        assertEquals(RecordingType.LECTURE, YouTubeLinks.startKind(NotebookSpace.LEARNING, all))
        assertEquals(RecordingType.GENERAL, YouTubeLinks.startKind(null, all))
        assertEquals("Auto", YouTubeLinks.label(RecordingType.GENERAL))
    }

    @Test fun `sermon is offered only with Faith on`() {
        val noFaith = setOf(NotebookSpace.WORK, NotebookSpace.LEARNING)
        assertFalse(RecordingType.SERMON in YouTubeLinks.kindsFor(noFaith))
        assertTrue(RecordingType.SERMON in YouTubeLinks.kindsFor(all))
        assertEquals(RecordingType.GENERAL, YouTubeLinks.startKind(NotebookSpace.FAITH, noFaith))
        assertEquals(RecordingType.GENERAL, YouTubeLinks.kindsFor(noFaith).first())
    }

    @Test fun `make notes goes to the import screen with the link and the kind`() {
        val route = YouTubeLinks.route("https://www.youtube.com/watch?v=dQw4w9WgXcQ", RecordingType.SERMON)
        assertTrue(route.startsWith("import_file/"))
        assertTrue(route.endsWith("?type=SERMON"))
        assertFalse("watch?v=" in route) // the link is encoded, so its own ? does not clash
    }

    @Test fun `a shared YouTube link is picked out of the shared text`() {
        val link = YouTubeLinks.sharedLink("android.intent.action.SEND", "Watch this https://youtu.be/dQw4w9WgXcQ?si=abc", hasStream = false)
        assertEquals("dQw4w9WgXcQ", link?.let { com.craftflowtechnologies.meetingmind.core.youtube.YouTubeUrlParser.parse(it)?.videoId })
        assertNull(YouTubeLinks.sharedLink("android.intent.action.SEND", "https://example.com/page", hasStream = false))
        assertNull(YouTubeLinks.sharedLink("android.intent.action.SEND", "https://youtu.be/dQw4w9WgXcQ", hasStream = true))
        assertNull(YouTubeLinks.sharedLink("android.intent.action.SEND_MULTIPLE", "https://youtu.be/dQw4w9WgXcQ", hasStream = false))
    }

    @Test fun `a copied link is offered once per video, and never once turned off`() {
        assertTrue(ClipboardOffer.shouldOffer("abc", lastOffered = null, enabled = true))
        assertFalse(ClipboardOffer.shouldOffer("abc", lastOffered = "abc", enabled = true))
        assertTrue(ClipboardOffer.shouldOffer("xyz", lastOffered = "abc", enabled = true))
        assertFalse(ClipboardOffer.shouldOffer("xyz", lastOffered = null, enabled = false))
        assertFalse(ClipboardOffer.shouldOffer(null, lastOffered = null, enabled = true))
    }

    private fun youTubeRows(spaces: Set<NotebookSpace>) =
        AppMenuModel.sections(spaces).flatMap { it.entries }.filter { it.target is MenuTarget.YouTube }

    @Test fun `the menu always has YouTube notes, with a sermon row only in Faith`() {
        assertEquals(listOf("everyday.youtube"), youTubeRows(setOf(NotebookSpace.PERSONAL)).map { it.id })
        val everything = youTubeRows(all)
        assertTrue(everything.any { it.id == "learn.youtube" && (it.target as MenuTarget.YouTube).from == NotebookSpace.LEARNING })
        assertTrue(everything.any { it.id == "faith.youtube" && (it.target as MenuTarget.YouTube).from == NotebookSpace.FAITH })
        assertFalse(youTubeRows(setOf(NotebookSpace.WORK, NotebookSpace.LEARNING)).any { it.id == "faith.youtube" })
    }
}
