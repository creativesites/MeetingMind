package com.craftflowtechnologies.meetingmind.ai.voice.read.profile

import com.craftflowtechnologies.meetingmind.ai.voice.read.DeliveryPreset
import com.craftflowtechnologies.meetingmind.ai.voice.read.ReadToMeSettings
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadToMeProfileTest {
    private val pastor = ReadToMeSettings(preset = DeliveryPreset.WARM_PASTOR)
    private val sermon = ReadCategory.SERMON_STUDY

    private fun use(p: ReadToMeProfile, s: ReadToMeSettings, c: ReadCategory = sermon) = LearningRules.recordUse(p, c, LearningRules.deliberate(s))

    @Test fun `one or two uses learn nothing and the third promotes with a message`() {
        var p = ReadToMeProfile()
        repeat(2) { p = use(p, pastor).profile }
        assertTrue(p.learned.isEmpty())
        val third = use(p, pastor)
        assertEquals("WARM_PASTOR", third.profile.learned[sermon]?.get(ProfileField.PRESET))
        assertEquals(1, third.promotions.size)
        assertTrue(third.promotions.single().message.contains("Warm pastor"))
    }

    @Test fun `a different choice in between restarts the count`() {
        var p = ReadToMeProfile()
        p = use(p, pastor).profile
        p = use(p, pastor).profile
        p = use(p, ReadToMeSettings(preset = DeliveryPreset.STORYTELLER)).profile
        p = use(p, pastor).profile
        assertTrue(p.learned.isEmpty())
    }

    @Test fun `Automatic and stock values are not choices`() {
        assertTrue(LearningRules.deliberate(ReadToMeSettings()).isEmpty())
        var p = ReadToMeProfile()
        repeat(5) { p = use(p, ReadToMeSettings()).profile }
        assertTrue(p.learned.isEmpty())
    }

    @Test fun `categories are isolated`() {
        var p = ReadToMeProfile()
        repeat(3) { p = use(p, pastor, sermon).profile }
        assertEquals(DeliveryPreset.AUTO, LearningRules.apply(ReadToMeSettings(), ReadCategory.WORK, p).preset)
        assertEquals(DeliveryPreset.WARM_PASTOR, LearningRules.apply(ReadToMeSettings(), sermon, p).preset)
    }

    @Test fun `explicit choices beat learned ones`() {
        var p = ReadToMeProfile()
        repeat(3) { p = use(p, pastor).profile }
        val chosen = ReadToMeSettings(preset = DeliveryPreset.CALM_TEACHER)
        assertEquals(DeliveryPreset.CALM_TEACHER, LearningRules.apply(chosen, sermon, p).preset)
    }

    @Test fun `with learning off nothing is learned or applied`() {
        var p = ReadToMeProfile(adaptive = false)
        repeat(5) { p = use(p, pastor).profile }
        assertTrue(p.learned.isEmpty())
        val on = ReadToMeProfile(learned = mapOf(sermon to mapOf(ProfileField.PRESET to "WARM_PASTOR")), adaptive = false)
        assertEquals(DeliveryPreset.AUTO, LearningRules.apply(ReadToMeSettings(), sermon, on).preset)
    }

    @Test fun `undo restores the previous default and clears the streak`() {
        var p = ReadToMeProfile()
        repeat(3) { p = use(p, pastor).profile }
        val promo = LearningRules.recordUse(p, sermon, mapOf(ProfileField.PRESET to "STORYTELLER")).let { var q = p; repeat(3) { q = use(q, ReadToMeSettings(preset = DeliveryPreset.STORYTELLER)).profile }; q }
        assertEquals("STORYTELLER", promo.learned[sermon]?.get(ProfileField.PRESET))
        val undone = LearningRules.undo(promo, Promotion(sermon, ProfileField.PRESET, "STORYTELLER", "WARM_PASTOR"))
        assertEquals("WARM_PASTOR", undone.learned[sermon]?.get(ProfileField.PRESET))
        val cleared = LearningRules.undo(p, Promotion(sermon, ProfileField.PRESET, "WARM_PASTOR", null))
        assertNull(cleared.learned[sermon])
    }

    @Test fun `reset clears one category or everything`() {
        var p = ReadToMeProfile()
        repeat(3) { p = use(p, pastor, sermon).profile; p = use(p, pastor, ReadCategory.WORK).profile }
        assertTrue(LearningRules.reset(p, sermon).learned.keys == setOf(ReadCategory.WORK))
        assertTrue(LearningRules.reset(p).learned.isEmpty())
        assertTrue(LearningRules.reset(p).streaks.isEmpty())
    }

    @Test fun `the stored profile holds only names and numbers, never note text`() {
        var p = ReadToMeProfile()
        repeat(3) { p = LearningRules.recordUse(p, sermon, LearningRules.deliberate(ReadToMeSettings(preset = DeliveryPreset.WARM_PASTOR, energy = 4, pace = 1.15f, geminiVoice = "Charon"))).profile }
        val json = p.toJson()
        fun walk(v: Any?) {
            when (v) {
                is JSONObject -> v.keys().forEach { k -> assertTrue("odd key $k", k.matches(Regex("[A-Z_|]+|[a-z]+"))); walk(v.get(k)) }
                is String -> assertTrue("odd value $v", v.matches(Regex("[A-Z_]+|[0-9.]+|[A-Za-z]+")))
                else -> Unit
            }
        }
        walk(json)
    }

    @Test fun `round trip and damaged or older data`() {
        var p = ReadToMeProfile()
        repeat(3) { p = use(p, pastor).profile }
        assertEquals(p, ReadToMeProfile.fromJson(p.toJson()))
        assertEquals(ReadToMeProfile(), ReadToMeProfile.fromJson(null))
        assertEquals(ReadToMeProfile(), ReadToMeProfile.fromJson(JSONObject("{\"learned\": 5}")))
        assertTrue(ReadToMeProfile.fromJson(JSONObject("{\"adaptive\": false}")).adaptive.not())
    }

    @Test fun `every kind of note has a category`() {
        RecordingType.entries.forEach { ReadCategory.of(it) }
        assertEquals(sermon, ReadCategory.of(RecordingType.BIBLE_STUDY))
        assertFalse(ReadCategory.of(RecordingType.MEETING) == sermon)
    }
}
