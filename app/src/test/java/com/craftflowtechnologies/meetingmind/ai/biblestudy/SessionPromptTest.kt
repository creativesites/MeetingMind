package com.craftflowtechnologies.meetingmind.ai.biblestudy

import com.craftflowtechnologies.meetingmind.core.biblestudy.Need
import com.craftflowtechnologies.meetingmind.core.biblestudy.StepType
import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalProfile
import com.craftflowtechnologies.meetingmind.core.devotional.PersonalTouch
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Generic by default, personal by invitation, for guided sessions too (BIBLE_DEVOTIONAL_V2 §8). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionPromptTest {

    private val personal = DevotionalProfile(
        topics = setOf("Zebra-topic-grief"), season = "Zeppelin-season",
        aboutMe = "Flamingo-aboutme: night-shift nurse in Ulaanbaatar", personalTouch = PersonalTouch.OFF
    )
    private val markers = listOf("Zebra-topic-grief", "Zeppelin-season", "Flamingo-aboutme", "Ulaanbaatar", "Ottoline")
    private val start = LocalDate.of(2026, 10, 11)
    private fun brief(p: DevotionalProfile, step: StepType = StepType.OBSERVE, date: LocalDate? = start, need: Need? = null) =
        SessionBrief(ScriptureReferenceParser.parse("Psalm 23")!!, "The Lord is my shepherd", step, null, need, p, date, name = "Ottoline")

    @Test fun `with the personal touch off the prompt carries none of the profile`() {
        StepType.entries.forEach { step ->
            val prompt = SessionPrompt.prompt(brief(personal, step))
            markers.forEach { assertFalse("$it leaked into $step", prompt.contains(it)) }
            assertTrue(prompt.contains("Audience: a general reader"))
        }
        // The date makes no difference when it is off.
        (0L until 14L).forEach { d -> SessionPrompt.prompt(brief(personal, date = start.plusDays(d))).let { p -> markers.forEach { assertFalse(p.contains(it)) } } }
    }

    @Test fun `style settings and the chosen need always apply`() {
        val prompt = SessionPrompt.prompt(brief(personal.copy(language = "French"), need = Need.GRIEVING))
        assertTrue(prompt.contains("Write in French"))
        assertTrue(prompt.contains("Tradition:") && prompt.contains("Voice:"))
        assertTrue(prompt.contains("grieving"))
        markers.forEach { assertFalse(prompt.contains(it)) }
    }

    @Test fun `always includes the profile as background`() {
        val prompt = SessionPrompt.prompt(brief(personal.copy(personalTouch = PersonalTouch.ALWAYS)))
        markers.forEach { assertTrue("$it missing", prompt.contains(it)) }
        assertFalse(prompt.contains("Audience: a general reader"))
        assertTrue(prompt.contains("at most one question"))
    }

    @Test fun `now and then includes it only on the personal day`() {
        val p = personal.copy(personalTouch = PersonalTouch.NOW_AND_THEN)
        val personalDay = generateSequence(start) { it.plusDays(1) }.first { PersonalTouch.NOW_AND_THEN.appliesOn(it) }
        val ordinaryDay = generateSequence(start) { it.plusDays(1) }.first { !PersonalTouch.NOW_AND_THEN.appliesOn(it) }
        assertTrue(SessionPrompt.prompt(brief(p, date = personalDay)).contains("Flamingo-aboutme"))
        assertFalse(SessionPrompt.prompt(brief(p, date = ordinaryDay)).contains("Flamingo-aboutme"))
        assertFalse(SessionPrompt.prompt(brief(p, date = null)).contains("Flamingo-aboutme"))
    }

    @Test fun `the prompt tells the model to ask, never answer, and to cite verses`() {
        val prompt = SessionPrompt.prompt(brief(DevotionalProfile()))
        assertTrue(prompt.contains("never answer"))
        assertTrue(prompt.contains("cite"))
        assertTrue(prompt.contains("Passage: Psalm 23"))
    }

    // ---------------------------------------------------------------- the checks on the answer

    @Test fun `context without a real reference is dropped, and questions must be questions`() {
        val raw = """{"context":[
            {"text":"A shepherd's staff guided and protected sheep.","cite":"Psalm 23:4"},
            {"text":"No citation at all.","cite":""},
            {"text":"A made-up citation.","cite":"Hezekiah 4:4"}],
          "questions":["What does it mean that the Lord is a shepherd?","Write your own prayer now.","Why?"]}"""
        val got = SessionPrompt.parse(raw)!!
        assertEquals(1, got.context.size)
        assertEquals("Psalm 23:4", got.context[0].cite.display())
        assertEquals(listOf("What does it mean that the Lord is a shepherd?"), got.questions)
    }

    @Test fun `a voice of God sentence is taken out and a useless answer is nothing`() {
        val raw = """{"context":[{"text":"God is telling you to rest. The psalm begins with provision.","cite":"Psalm 23:1"}],"questions":[]}"""
        val got = SessionPrompt.parse(raw)!!
        assertEquals("The psalm begins with provision.", got.context[0].text)
        assertNull(SessionPrompt.parse("""{"context":[],"questions":[]}"""))
        assertNull(SessionPrompt.parse("not json"))
        assertNotNull(SessionPrompt.parse("""Sure! {"questions":["What do you notice about the verbs?"]}"""))
    }

    @Test fun `the prompt asset has every required section`() {
        val p = com.craftflowtechnologies.meetingmind.ai.faith.Prompts.get("bible_session")
        com.craftflowtechnologies.meetingmind.ai.faith.PromptAsset.REQUIRED_SECTIONS.forEach { p.section(it) }
    }
}
