package com.craftflowtechnologies.meetingmind.ai.voice.read

import com.craftflowtechnologies.meetingmind.core.model.BlockSource
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyRoleClassifierTest {

    private fun role(build: Blocks.() -> Unit, heading: String? = null): SpeechRole {
        val b = Blocks().apply(build)
        return StudyRoleClassifier.classify(b.all.last(), heading)
    }

    @Test fun `block types decide scripture, quotes and transcript`() {
        assertEquals(SpeechRole.SCRIPTURE, role({ scripture("John 3:16") }))
        assertEquals(SpeechRole.QUOTE, role({ add(QUOTE, "x") }))
        assertEquals(SpeechRole.TRANSCRIPT, role({ add(TRANSCRIPT_EXCERPT, "x", "quotes", BlockSource.TRANSCRIPT) }))
    }

    @Test fun `the Bible study workflow template keys`() {
        assertEquals(SpeechRole.OBSERVATION, role({ add(BULLET, "a", "observations") }))
        assertEquals(SpeechRole.INTERPRETATION, role({ add(PARAGRAPH, "a", "interpretation") }))
        assertEquals(SpeechRole.APPLICATION, role({ add(PARAGRAPH, "a", "application") }))
        assertEquals(SpeechRole.QUESTION, role({ add(BULLET, "a", "questions") }))
    }

    @Test fun `sermon note keys`() {
        assertEquals(SpeechRole.COMMENTARY, role({ add(NUMBERED, "a", "key_points", BlockSource.AI) }))
        assertEquals(SpeechRole.QUESTION, role({ add(BULLET, "a", "reflection_questions", BlockSource.AI) }))
        assertEquals(SpeechRole.PRAYER, role({ add(BULLET, "a", "prayer_points", BlockSource.AI) }))
        assertEquals(SpeechRole.PRAYER, role({ add(PARAGRAPH, "a", "my_prayer") }))
        assertEquals(SpeechRole.PERSONAL, role({ add(CHECKLIST, "a", "my_response") }))
    }

    @Test fun `devotional note keys`() {
        assertEquals(SpeechRole.COMMENTARY, role({ add(PARAGRAPH, "a", "daily_reflection", BlockSource.AI) }))
        assertEquals(SpeechRole.APPLICATION, role({ add(CHECKLIST, "a", "daily_application", BlockSource.AI) }))
        assertEquals(SpeechRole.PRAYER, role({ add(PARAGRAPH, "a", "daily_prayer", BlockSource.AI) }))
        assertEquals(SpeechRole.QUESTION, role({ add(PARAGRAPH, "a", "daily_question", BlockSource.AI) }))
        assertEquals(SpeechRole.PERSONAL, role({ add(PARAGRAPH, "a", "daily_my_response", edited = true) }))
    }

    @Test fun `study template sections take their meaning from the heading`() {
        assertEquals(SpeechRole.PRAYER, role({ add(PARAGRAPH, "a", "study_soap_3") }, heading = "Prayer"))
        assertEquals(SpeechRole.INTERPRETATION, role({ add(PARAGRAPH, "a", "study_inductive_1") }, heading = "Interpret"))
        assertEquals(SpeechRole.OBSERVATION, role({ add(PARAGRAPH, "a", "study_soap_1") }, heading = "Observation"))
        assertEquals(SpeechRole.OTHER, role({ add(PARAGRAPH, "a", "study_topical_2") }, heading = "Background"))
    }

    @Test fun `guided journal question headings and answers`() {
        assertEquals(SpeechRole.QUESTION, role({ add(HEADING_3, "What stands out?", "q1") }))
        // The answer is the person's own, whatever words the question used.
        assertEquals(SpeechRole.PERSONAL, role({ add(PARAGRAPH, "I see grace", "q1", edited = true) }, heading = "How does this apply to your prayer life?"))
        assertEquals(SpeechRole.QUESTION, role({ add(HEADING_3, "What does it ask of you?", "application") }))
    }

    @Test fun `a heading that is not a guided question stays a heading`() {
        assertEquals(SpeechRole.HEADING, role({ add(HEADING_3, "Discussion questions", "sg_questions") }))
        assertEquals(SpeechRole.HEADING, role({ add(HEADING_2, "Prayer", "prayer") }))
    }

    @Test fun `free blocks are plain unless a model wrote them`() {
        assertEquals(SpeechRole.OTHER, role({ add(PARAGRAPH, "just my note") }))
        assertEquals(SpeechRole.COMMENTARY, role({ add(PARAGRAPH, "summary", source = BlockSource.AI) }))
    }

    @Test fun `AI text the person edited is no longer AI-written`() {
        val b = Blocks().apply { add(PARAGRAPH, "x", source = BlockSource.AI, edited = true) }
        assertFalse(StudyRoleClassifier.isAiGenerated(b.all.single()))
        val c = Blocks().apply { add(PARAGRAPH, "x", source = BlockSource.AI) }
        assertTrue(StudyRoleClassifier.isAiGenerated(c.all.single()))
    }

    @Test fun `scripture, quotes, transcript, prayers and personal notes are locked in Smart Read`() {
        val locked = setOf(SpeechRole.SCRIPTURE, SpeechRole.QUOTE, SpeechRole.TRANSCRIPT, SpeechRole.PRAYER, SpeechRole.PERSONAL)
        SpeechRole.entries.forEach { assertEquals(it.name, it in locked, it.lockedInSmartRead) }
    }

    @Test fun `labels are a closed fixed set`() {
        assertEquals("Question for reflection.", RoleLabels.lead(SpeechRole.QUESTION, false, LabelStyle.FULL))
        assertEquals("AI-written.", RoleLabels.lead(SpeechRole.COMMENTARY, true, LabelStyle.FULL))
        assertEquals("Commentary.", RoleLabels.lead(SpeechRole.COMMENTARY, false, LabelStyle.FULL))
        assertEquals("Your note.", RoleLabels.lead(SpeechRole.PERSONAL, false, LabelStyle.FULL))
        assertEquals("Question.", RoleLabels.lead(SpeechRole.QUESTION, false, LabelStyle.SHORT))
        SpeechRole.entries.forEach { assertEquals(null, RoleLabels.lead(it, true, LabelStyle.OFF)) }
    }
}
