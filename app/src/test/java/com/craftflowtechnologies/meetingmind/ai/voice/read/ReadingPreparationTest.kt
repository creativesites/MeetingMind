package com.craftflowtechnologies.meetingmind.ai.voice.read

import com.craftflowtechnologies.meetingmind.core.model.BlockSource
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType.*
import com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingPreparationTest {
    private val env = Environment(ProcessingProfile.INTERNET, geminiConfigured = true, entitled = true, keyTier = GeminiKeyTier.PAID, usingBuiltInKey = false)
    private val facts = NoteFacts(notePrivate = false, notebookConfidential = false, workPrivacyOffline = false, faithLocked = false)
    private val accepted = ReadToMeSettings(disclosureAccepted = true)

    private val plain = extract(Blocks().apply { add(PARAGRAPH, "A normal note") })
    private val mixed = extract(
        Blocks().apply {
            add(HEADING_2, "Notes", "my_notes"); add(PARAGRAPH, "shareable", "my_notes", edited = true)
            add(HEADING_2, "My prayer", "my_prayer"); add(PARAGRAPH, "private words", "my_prayer", edited = true)
        },
        privateKeys = setOf("my_prayer")
    )

    private fun prep(src: SpeechSource = plain, f: NoteFacts = facts, e: Environment = env, s: ReadToMeSettings = accepted, c: ReadChoice = ReadChoice.DEFAULT) =
        ReadingPreparation.prepare(src, f, e, s, c)

    @Test fun `a locked note is not read, an empty one has nothing to read`() {
        assertEquals(Prepared.Locked, prep(f = facts.copy(faithLocked = true)))
        assertEquals(Prepared.Empty, prep(src = extract(Blocks().apply { add(PARAGRAPH, " ") })))
    }

    @Test fun `a clean note in internet mode reads with Gemini`() {
        val r = prep() as Prepared.Ready
        assertEquals(SpeechProvider.GEMINI, r.decision.provider)
        assertNull(r.explanation)
    }

    @Test fun `the disclosure is asked for first, with the key's situation`() {
        val d = prep(s = ReadToMeSettings(disclosureAccepted = false), e = env.copy(usingBuiltInKey = true, keyTier = GeminiKeyTier.UNKNOWN)) as Prepared.NeedsDisclosure
        assertTrue(d.usingBuiltInKey)
        assertEquals(GeminiKeyTier.UNKNOWN, d.keyTier)
    }

    @Test fun `offline mode reads on the phone and says why, with no disclosure`() {
        val r = prep(e = env.copy(profile = ProcessingProfile.OFFLINE), s = ReadToMeSettings()) as Prepared.Ready
        assertEquals(SpeechProvider.LOCAL, r.decision.provider)
        assertTrue(r.explanation!!.contains("Offline mode"))
    }

    @Test fun `private and confidential material is explained and stays local`() {
        val r = prep(f = facts.copy(notePrivate = true)) as Prepared.Ready
        assertEquals(SpeechProvider.LOCAL, r.decision.provider)
        assertTrue(r.explanation!!.contains("private"))
        val c = prep(f = facts.copy(workPrivacyOffline = true)) as Prepared.Ready
        assertTrue(c.explanation!!.contains("confidential"))
    }

    @Test fun `private sections ask whether to leave them out`() {
        val n = prep(src = mixed) as Prepared.NeedsChoice
        assertTrue(n.message.contains("private sections"))
        assertEquals(SpeechProvider.LOCAL, n.decision.provider)
    }

    @Test fun `choosing to leave them out reads the rest with Gemini without the private text`() {
        val r = prep(src = mixed, c = ReadChoice.CLOUD_WITHOUT_SENSITIVE) as Prepared.Ready
        assertEquals(SpeechProvider.GEMINI, r.decision.provider)
        assertFalse(r.source.sections.flatMap { it.units }.any { it.text.contains("private words") })
        assertTrue(r.source.sections.flatMap { it.units }.any { it.text == "shareable" })
    }

    @Test fun `choosing the phone voice keeps all the text and reads it locally`() {
        val r = prep(src = mixed, c = ReadChoice.PHONE_VOICE) as Prepared.Ready
        assertEquals(SpeechProvider.LOCAL, r.decision.provider)
        assertTrue(r.source.sections.flatMap { it.units }.any { it.text.contains("private words") })
        assertTrue(r.explanation!!.contains("as you chose"))
    }

    @Test fun `leaving parts out still needs the disclosure before the first cloud reading`() {
        assertTrue(prep(src = mixed, s = ReadToMeSettings(disclosureAccepted = false), c = ReadChoice.CLOUD_WITHOUT_SENSITIVE) is Prepared.NeedsDisclosure)
    }

    @Test fun `leaving out everything that was readable is an empty reading`() {
        val onlyPrivate = extract(Blocks().apply { add(HEADING_2, "My prayer", "my_prayer"); add(PARAGRAPH, "private", "my_prayer", edited = true) }, privateKeys = setOf("my_prayer"))
        assertEquals(Prepared.Empty, prep(src = onlyPrivate, c = ReadChoice.CLOUD_WITHOUT_SENSITIVE))
    }

    private val personal = extract(Blocks().apply {
        add(HEADING_2, "Notes", "key_message"); add(PARAGRAPH, "model prose", "key_message", BlockSource.AI)
        add(HEADING_3, "What stands out?", "q1"); add(PARAGRAPH, "his grace", "q1", edited = true)
    })

    @Test fun `personal writing waits for a confirmed plan and offers to leave it out`() {
        val unknown = env.copy(keyTier = GeminiKeyTier.UNKNOWN)
        val n = prep(src = personal, e = unknown) as Prepared.NeedsChoice
        assertTrue(n.message.contains("isn't confirmed as paid"))
        val r = prep(src = personal, e = unknown, c = ReadChoice.CLOUD_WITHOUT_SENSITIVE) as Prepared.Ready
        assertEquals(SpeechProvider.GEMINI, r.decision.provider)
        assertFalse(r.source.sections.flatMap { it.units }.any { it.text == "his grace" })
        assertTrue(r.source.sections.flatMap { it.units }.any { it.text == "model prose" })
    }

    @Test fun `a confirmed paid plan reads personal writing with Gemini`() {
        assertEquals(SpeechProvider.GEMINI, (prep(src = personal) as Prepared.Ready).decision.provider)
    }

    @Test fun `someone who prefers the phone voice is not asked anything`() {
        val r = prep(src = mixed, s = accepted.copy(prefersLocal = true)) as Prepared.Ready
        assertEquals(SpeechProvider.LOCAL, r.decision.provider)
        assertTrue(r.source.sections.flatMap { it.units }.any { it.text.contains("private words") })
    }

    @Test fun `licensed scripture reads on the phone with an explanation`() {
        val src = extract(Blocks().apply { scripture("John 3:16-17") }, lookupOf("John 3:16-17" to john316.copy(openForCloud = false)))
        val r = prep(src = src) as Prepared.Ready
        assertEquals(SpeechProvider.LOCAL, r.decision.provider)
        assertTrue(r.explanation!!.contains("licensed"))
        assertTrue(r.decision.ephemeralAudio)
    }

    @Test fun `generic titles apply to private, confidential and locked notes`() {
        assertFalse(facts.genericTitle)
        assertTrue(facts.copy(notePrivate = true).genericTitle)
        assertTrue(facts.copy(workPrivacyOffline = true).genericTitle)
        assertTrue(facts.copy(faithLocked = true).genericTitle)
        assertNotNull(RouteReasons.describe(SpeechRoute.decide(RouteInputs(ProcessingProfile.OFFLINE, true, true, false, false, disclosureAccepted = true, source = plain)), false))
    }
}
