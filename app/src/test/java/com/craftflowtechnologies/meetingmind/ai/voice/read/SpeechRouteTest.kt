package com.craftflowtechnologies.meetingmind.ai.voice.read

import com.craftflowtechnologies.meetingmind.core.model.BlockSource
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType.*
import com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechRouteTest {

    private val plain = extract(Blocks().apply { add(PARAGRAPH, "a normal note") })

    private fun inputs(
        profile: ProcessingProfile = ProcessingProfile.INTERNET,
        configured: Boolean = true,
        entitled: Boolean = true,
        notePrivate: Boolean = false,
        confidential: Boolean = false,
        workOffline: Boolean = false,
        faithLocked: Boolean = false,
        disclosed: Boolean = true,
        prefersLocal: Boolean = false,
        source: SpeechSource = plain
    ) = RouteInputs(profile, configured, entitled, notePrivate, confidential, workOffline, faithLocked, disclosed, prefersLocal, source)

    @Test fun `internet mode with a key reads with Gemini`() {
        val d = SpeechRoute.decide(inputs())
        assertEquals(SpeechProvider.GEMINI, d.provider)
        assertTrue(d.cloudAllowed)
        assertFalse(d.needsDisclosure)
    }

    @Test fun `the disclosure is asked for before the first cloud reading`() {
        assertTrue(SpeechRoute.decide(inputs(disclosed = false)).needsDisclosure)
    }

    @Test fun `every mode but internet stays on the phone, whatever else is set`() {
        ProcessingProfile.entries.filter { it != ProcessingProfile.INTERNET }.forEach { p ->
            val d = SpeechRoute.decide(inputs(profile = p, configured = true, disclosed = true))
            assertEquals(p.name, SpeechProvider.LOCAL, d.provider)
            assertTrue(CloudBlock.OFFLINE_MODE in d.cloudBlocks)
            assertFalse(d.needsDisclosure)
        }
    }

    @Test fun `no key, no plan, or the person's choice means the phone voice`() {
        assertEquals(SpeechProvider.LOCAL, SpeechRoute.decide(inputs(configured = false)).provider)
        assertTrue(CloudBlock.NOT_CONFIGURED in SpeechRoute.decide(inputs(configured = false)).cloudBlocks)
        assertTrue(CloudBlock.NOT_ENTITLED in SpeechRoute.decide(inputs(entitled = false)).cloudBlocks)
        val prefers = SpeechRoute.decide(inputs(prefersLocal = true))
        assertEquals(SpeechProvider.LOCAL, prefers.provider)
        assertTrue(prefers.cloudAllowed)
    }

    @Test fun `private and confidential material never reaches the cloud voice`() {
        assertTrue(CloudBlock.PRIVATE_NOTE in SpeechRoute.decide(inputs(notePrivate = true)).cloudBlocks)
        assertTrue(CloudBlock.CONFIDENTIAL_NOTEBOOK in SpeechRoute.decide(inputs(confidential = true)).cloudBlocks)
        assertTrue(CloudBlock.WORK_PRIVACY in SpeechRoute.decide(inputs(workOffline = true)).cloudBlocks)
        listOf(inputs(notePrivate = true), inputs(confidential = true), inputs(workOffline = true)).forEach {
            val d = SpeechRoute.decide(it)
            assertEquals(SpeechProvider.LOCAL, d.provider)
            assertTrue(d.genericLockScreenTitle)
        }
    }

    @Test fun `private sections inside a normal note keep the whole reading on the phone, with a way to read the rest`() {
        val b = Blocks().apply {
            add(HEADING_2, "Notes", "my_notes"); add(PARAGRAPH, "shareable", "my_notes", edited = true)
            add(HEADING_2, "My prayer", "my_prayer"); add(PARAGRAPH, "private", "my_prayer", edited = true)
        }
        val src = extract(b, privateKeys = setOf("my_prayer"))
        val d = SpeechRoute.decide(inputs(source = src))
        assertEquals(SpeechProvider.LOCAL, d.provider)
        assertEquals(setOf(CloudBlock.PRIVATE_SECTIONS), d.cloudBlocks)
        assertTrue(d.canOfferWithoutPrivate)
        assertEquals(SpeechProvider.GEMINI, SpeechRoute.decide(inputs(source = src.withoutPrivate())).provider)
    }

    @Test fun `unmarked personal notes and prayers may use the cloud voice once disclosed`() {
        val b = Blocks().apply {
            add(HEADING_2, "Prayer", "prayer"); add(PARAGRAPH, "Lord, help me", "prayer", edited = true)
            add(HEADING_3, "What stands out?", "q1"); add(PARAGRAPH, "his grace", "q1", edited = true)
        }
        val src = extract(b)
        assertTrue(src.sections.flatMap { it.units }.any { it.role == SpeechRole.PRAYER })
        assertTrue(src.sections.flatMap { it.units }.any { it.role == SpeechRole.PERSONAL })
        assertEquals(SpeechProvider.GEMINI, SpeechRoute.decide(inputs(source = src)).provider)
    }

    @Test fun `licensed scripture keeps the reading on the phone with short-lived audio`() {
        val b = Blocks().apply { scripture("John 3:16-17") }
        val src = extract(b, lookupOf("John 3:16-17" to john316.copy(openForCloud = false)))
        val d = SpeechRoute.decide(inputs(source = src))
        assertEquals(SpeechProvider.LOCAL, d.provider)
        assertTrue(CloudBlock.LICENSED_SCRIPTURE in d.cloudBlocks)
        assertTrue(d.ephemeralAudio)
        assertFalse(SpeechRoute.decide(inputs(source = extract(b, lookupOf("John 3:16-17" to john316)))).ephemeralAudio)
    }

    @Test fun `a closed faith lock blocks the reading and hides the title`() {
        val d = SpeechRoute.decide(inputs(faithLocked = true))
        assertTrue(d.locked)
        assertTrue(d.genericLockScreenTitle)
    }

    @Test fun `Gemini is chosen only when every gate is open - exhaustive`() {
        val bools = listOf(false, true)
        for (profile in ProcessingProfile.entries) for (configured in bools) for (entitled in bools)
            for (priv in bools) for (conf in bools) for (work in bools) for (prefersLocal in bools) {
                val d = SpeechRoute.decide(inputs(profile, configured, entitled, priv, conf, work, prefersLocal = prefersLocal))
                if (d.provider == SpeechProvider.GEMINI) {
                    assertEquals(ProcessingProfile.INTERNET, profile)
                    assertTrue(configured && entitled && !priv && !conf && !work && !prefersLocal)
                    assertTrue(d.cloudAllowed)
                }
            }
    }

    @Test fun `routing reads the shape of the note and never its words`() {
        // Two notes with the same shape and different words route identically.
        val a = extract(Blocks().apply { add(PARAGRAPH, "alpha", "key_message", BlockSource.AI) })
        val b = extract(Blocks().apply { add(PARAGRAPH, "completely different text", "key_message", BlockSource.AI) })
        assertEquals(SpeechRoute.decide(inputs(source = a)), SpeechRoute.decide(inputs(source = b)))
    }
}
