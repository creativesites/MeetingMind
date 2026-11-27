package com.craftflowtechnologies.meetingmind.core.learning

import com.craftflowtechnologies.meetingmind.feature.learn.LessonSection
import com.craftflowtechnologies.meetingmind.feature.learn.LessonTerm
import com.craftflowtechnologies.meetingmind.feature.learn.OptionWhy
import com.craftflowtechnologies.meetingmind.feature.learn.QuickCheck
import com.craftflowtechnologies.meetingmind.feature.learn.StressReason
import com.craftflowtechnologies.meetingmind.feature.learn.StressedMoment
import com.craftflowtechnologies.meetingmind.feature.learn.StudyKit
import com.craftflowtechnologies.meetingmind.feature.learn.TimeRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class StudyKitStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun kit(sessionId: String = "sess-1") = StudyKit(
        sessionId = sessionId,
        summary = "Enzymes speed reactions. Inhibitors block the active site.",
        stressed = listOf(
            StressedMoment(20_000, 30_000, "Remember this for the exam.", StressReason.NAMED_EXAM, conceptId = "c1"),
            StressedMoment(60_000, 61_000, "Key idea.", StressReason.CALLED_IMPORTANT, conceptId = null)
        ),
        sections = listOf(
            LessonSection(
                index = 0,
                title = "Inhibition",
                body = "Competitive inhibitors bind to the active site.",
                terms = listOf(LessonTerm("active site", "c1")),
                quickCheck = QuickCheck("What do inhibitors block?", "Substrate binding", conceptId = "c1"),
                startMs = 10_000L
            ),
            LessonSection(index = 1, title = "Rates", body = "Vmax is the maximum rate.", startMs = null)
        ),
        hardToHear = listOf(TimeRange(20_000, 30_000)),
        optionWhys = listOf(OptionWhy("act-1", 1, "Heat is not an inhibitor here.")),
        rightWhys = mapOf("act-1" to "Inhibitors occupy the active site."),
        modelId = "model-x",
        createdAt = 1_234_567L
    )

    @Test
    fun `a kit round-trips through its file unchanged`() {
        val store = StudyKitStore(File(folder.root, "study/kits"))
        val original = kit()

        assertTrue(store.save(original))
        val loaded = store.load("sess-1")

        assertEquals(original, loaded)
    }

    @Test
    fun `a missing kit reads as absent, and delete removes it`() {
        val store = StudyKitStore(File(folder.root, "kits"))
        assertNull(store.load("nope"))
        store.save(kit("sess-2"))
        store.delete("sess-2")
        assertNull(store.load("sess-2"))
    }

    @Test
    fun `a kit with another schema version or a broken file reads as absent`() {
        val dir = File(folder.root, "kits")
        val store = StudyKitStore(dir)
        store.save(kit("sess-3"))
        val file = store.fileFor("sess-3")
        file.writeText(file.readText().replace("\"version\":1", "\"version\":99"))
        assertNull(store.load("sess-3"))

        file.writeText("{ not json")
        assertNull(store.load("sess-3"))
        assertFalse(File(dir, "sess-3.json.tmp").exists())
    }

    @Test
    fun `file names are safe for any session id`() {
        val store = StudyKitStore(File(folder.root, "kits"))
        assertEquals("a_b_c.json", store.fileFor("a/b c").name)
        assertTrue(store.fileFor("../escape").parentFile!!.canonicalPath.startsWith(folder.root.canonicalPath))
    }
}
