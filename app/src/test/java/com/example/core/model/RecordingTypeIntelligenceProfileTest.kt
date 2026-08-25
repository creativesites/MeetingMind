package com.example.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the Phase 4 fix to the app's core mistake: treating every recording like a meeting.
 * [IntelligenceProfile] is the single source of truth for what a recording type's AI output
 * should contain — these tests pin down the specific inclusions/exclusions the product spec
 * calls out by name, so a future change can't silently start asking a Lecture for "decisions"
 * again without a test failing.
 */
class RecordingTypeIntelligenceProfileTest {

    @Test
    fun `a meeting gets the full schema`() {
        val profile = RecordingType.MEETING.intelligenceProfile()
        assertTrue(profile.extractDecisions)
        assertTrue(profile.extractActionItems)
        assertTrue(profile.extractQuestions)
        assertTrue(profile.extractFollowUps)
    }

    @Test
    fun `a lecture never gets decisions or action items`() {
        val profile = RecordingType.LECTURE.intelligenceProfile()
        assertFalse(profile.extractDecisions)
        assertFalse(profile.extractActionItems)
    }

    @Test
    fun `an idea gets nothing but the summary and key points - no decisions, actions, questions, or follow-ups`() {
        val profile = RecordingType.IDEA.intelligenceProfile()
        assertFalse(profile.extractDecisions)
        assertFalse(profile.extractActionItems)
        assertFalse(profile.extractQuestions)
        assertFalse(profile.extractFollowUps)
    }

    @Test
    fun `an interview gets questions and follow-ups but never decisions or action items`() {
        val profile = RecordingType.INTERVIEW.intelligenceProfile()
        assertFalse(profile.extractDecisions)
        assertFalse(profile.extractActionItems)
        assertTrue(profile.extractQuestions)
        assertTrue(profile.extractFollowUps)
    }

    @Test
    fun `a voice memo never gets decisions or action items`() {
        val profile = RecordingType.VOICE_MEMO.intelligenceProfile()
        assertFalse(profile.extractDecisions)
        assertFalse(profile.extractActionItems)
    }

    @Test
    fun `dictation never gets decisions, action items, questions, or follow-ups`() {
        val profile = RecordingType.DICTATION.intelligenceProfile()
        assertFalse(profile.extractDecisions)
        assertFalse(profile.extractActionItems)
        assertFalse(profile.extractQuestions)
        assertFalse(profile.extractFollowUps)
    }

    @Test
    fun `journal never gets corporate meeting terminology categories`() {
        val profile = RecordingType.JOURNAL.intelligenceProfile()
        assertFalse(profile.extractDecisions)
        assertFalse(profile.extractActionItems)
        assertFalse(profile.extractQuestions)
        assertFalse(profile.extractFollowUps)
    }

    @Test
    fun `custom leaves every category available since the user's own focus decides what matters`() {
        val profile = RecordingType.CUSTOM.intelligenceProfile()
        assertTrue(profile.extractDecisions)
        assertTrue(profile.extractActionItems)
        assertTrue(profile.extractQuestions)
        assertTrue(profile.extractFollowUps)
    }

    @Test
    fun `general suppresses nothing pre-emptively since the content is genuinely unknown`() {
        val profile = RecordingType.GENERAL.intelligenceProfile()
        assertTrue(profile.extractDecisions)
        assertTrue(profile.extractActionItems)
        assertTrue(profile.extractQuestions)
        assertTrue(profile.extractFollowUps)
    }

    @Test
    fun `every recording type has a real non-blank section title, topics label, and stage label`() {
        RecordingType.entries.forEach { type ->
            val profile = type.intelligenceProfile()
            assertTrue("$type sectionTitle must not be blank", profile.sectionTitle.isNotBlank())
            assertTrue("$type topicsLabel must not be blank", profile.topicsLabel.isNotBlank())
            assertTrue("$type analyzingStageLabel must not be blank", profile.analyzingStageLabel.isNotBlank())
        }
    }

    // --- suggestedSpeakerCount: a soft nudge, never a forced value ---

    @Test
    fun `solo-leaning types suggest exactly one speaker`() {
        listOf(RecordingType.IDEA, RecordingType.VOICE_MEMO, RecordingType.DICTATION, RecordingType.JOURNAL).forEach {
            assertEquals("$it should suggest a single speaker", 1, it.suggestedSpeakerCount())
        }
    }

    @Test
    fun `ambiguous or multi-speaker-leaning types suggest nothing, never a guess`() {
        listOf(RecordingType.MEETING, RecordingType.INTERVIEW, RecordingType.CONVERSATION, RecordingType.GENERAL, RecordingType.CUSTOM).forEach {
            assertEquals("$it should not force a speaker-count guess", null, it.suggestedSpeakerCount())
        }
    }
}
