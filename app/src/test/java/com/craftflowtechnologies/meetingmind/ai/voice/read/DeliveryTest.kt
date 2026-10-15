package com.craftflowtechnologies.meetingmind.ai.voice.read

import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryTest {
    private fun resolve(s: ReadToMeSettings = ReadToMeSettings(), space: NotebookSpace? = NotebookSpace.PERSONAL) = DeliveryResolver.resolve(s, space)

    @Test fun `automatic picks a neutral default for the kind of note`() {
        assertEquals(DeliveryPreset.CLEAR_PROFESSIONAL, resolve(space = NotebookSpace.WORK).preset)
        assertEquals(DeliveryPreset.ENGAGING_TEACHER, resolve(space = NotebookSpace.LEARNING).preset)
        assertEquals(DeliveryPreset.WARM_COMPANION, resolve(space = NotebookSpace.PERSONAL).preset)
        assertEquals(DeliveryPreset.WARM_COMPANION, resolve(space = NotebookSpace.FAITH).preset)
        assertEquals(DeliveryPreset.WARM_COMPANION, resolve(space = null).preset)
    }

    @Test fun `no faith preset is ever a default, whatever the content`() {
        NotebookSpace.entries.forEach { assertFalse(it.name, DeliveryResolver.defaultFor(it).faith) }
        assertFalse(DeliveryResolver.defaultFor(null).faith)
    }

    @Test fun `an explicit choice always wins over the default`() {
        val chosen = ReadToMeSettings(preset = DeliveryPreset.VIBRANT_PREACHER)
        NotebookSpace.entries.forEach { assertEquals(DeliveryPreset.VIBRANT_PREACHER, resolve(chosen, it).preset) }
    }

    @Test fun `faith presets are offered only where the Faith space is on`() {
        assertTrue(DeliveryResolver.offered(false).none { it.faith })
        assertTrue(DeliveryResolver.offered(true).containsAll(listOf(DeliveryPreset.CALM_TEACHER, DeliveryPreset.REFLECTIVE_DEVOTIONAL, DeliveryPreset.WARM_PASTOR, DeliveryPreset.VIBRANT_PREACHER)))
    }

    @Test fun `energy and pace are kept in range`() {
        val low = resolve(ReadToMeSettings(energy = -4, pace = 0.1f))
        val high = resolve(ReadToMeSettings(energy = 99, pace = 9f))
        assertEquals(1, low.energy); assertEquals(DeliveryResolver.MIN_PACE, low.pace)
        assertEquals(5, high.energy); assertEquals(DeliveryResolver.MAX_PACE, high.pace)
    }

    @Test fun `the Gemini style is bounded, mentions energy and pace only when they are not neutral, and asks for exact reading`() {
        val neutral = resolve(ReadToMeSettings(preset = DeliveryPreset.CALM_TEACHER)).geminiStyle()
        assertTrue(neutral.endsWith("Read the text exactly as written."))
        assertFalse(neutral.contains("energy")); assertFalse(neutral.contains("slower")); assertFalse(neutral.contains("brisker"))
        val shaped = DeliverySpec(DeliveryPreset.VIBRANT_PREACHER, 5, 1.2f).geminiStyle()
        assertTrue(shaped.contains("energy high", ignoreCase = true)); assertTrue(shaped.contains("brisker"))
        assertTrue(DeliverySpec(DeliveryPreset.WARM_COMPANION, 1, 0.85f).geminiStyle().let { it.contains("low") && it.contains("slower") })
        DeliveryPreset.entries.forEach { assertTrue(it.name, DeliverySpec(it, 5, 1.3f).geminiStyle().length < 400) }
    }

    @Test fun `a different delivery is a different fingerprint input`() {
        val base = DeliverySpec(DeliveryPreset.WARM_PASTOR, 3, 1f)
        assertNotEquals(base.canonical(), base.copy(preset = DeliveryPreset.CALM_TEACHER).canonical())
        assertNotEquals(base.canonical(), base.copy(energy = 4).canonical())
        assertNotEquals(base.canonical(), base.copy(pace = 1.1f).canonical())
        assertEquals(base.canonical(), base.copy().canonical())
    }

    @Test fun `the sheet says honestly what each voice can change`() {
        assertTrue(DeliveryResolver.capability(SpeechProvider.LOCAL).contains("pace"))
        assertTrue(DeliveryResolver.capability(SpeechProvider.LOCAL).contains("can change its pace but not"))
        assertTrue(DeliveryResolver.capability(SpeechProvider.GEMINI).contains("style"))
    }
}
