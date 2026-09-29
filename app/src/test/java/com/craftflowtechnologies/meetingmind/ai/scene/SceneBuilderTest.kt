package com.craftflowtechnologies.meetingmind.ai.scene

import com.craftflowtechnologies.meetingmind.core.audio.AcousticActivity
import com.craftflowtechnologies.meetingmind.core.audio.AcousticSegment
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SceneBuilderTest {

    private var n = 0
    private fun seg(startS: Int, endS: Int, text: String) = TranscriptSegment("s${n++}", "m", startMs = startS * 1000L, endMs = endS * 1000L, text = text)

    private val service = listOf(
        seg(0, 30, "Good morning church, a few announcements. Youth group meets this Saturday and the offering will be taken next week."),
        seg(30, 60, "Please sign up at the back. Welcome to all our visitors."),
        seg(60, 120, "Amazing grace how sweet the sound that saved a wretch like me. Amazing grace how sweet the sound."),
        seg(120, 180, "I once was lost but now am found. Amazing grace how sweet the sound."),
        seg(180, 200, "Let us pray. Father we thank you for this morning."),
        seg(200, 215, "Open our hearts to your word, in Jesus' name, amen."),
        seg(215, 240, "Turn with me to John chapter 15, starting at verse 1. I am the true vine."),
        seg(240, 900, "What does it mean to abide? Jesus is not asking for effort but for nearness. The branch does not strain.")
    )
    private val acoustic = listOf(
        AcousticSegment(0, 60_000, AcousticActivity.SPEECH),
        AcousticSegment(60_000, 180_000, AcousticActivity.MUSIC),
        AcousticSegment(180_000, 900_000, AcousticActivity.SPEECH)
    )

    @Test fun `a service splits into announcements, worship, prayer, reading and sermon on the device alone`() {
        val ws = SceneBuilder.windows(acoustic, service, 900_000)
        val kinds = ws.map { it.guess }
        assertEquals(
            listOf(SemanticActivity.ANNOUNCEMENT, SemanticActivity.SONG, SemanticActivity.PRAYER, SemanticActivity.SCRIPTURE, SemanticActivity.SERMON),
            kinds.fold(mutableListOf<SemanticActivity>()) { acc, k -> if (acc.lastOrNull() != k) acc += k; acc }
        )
        val song = ws.first { it.guess == SemanticActivity.SONG }
        assertEquals(60_000, song.startMs); assertEquals(180_000, song.endMs)
        val prayer = ws.first { it.guess == SemanticActivity.PRAYER }
        assertEquals(180_000, prayer.startMs); assertEquals(215_000, prayer.endMs)
    }

    @Test fun `only candidate windows go to the AI, as short excerpts`() {
        val ws = SceneBuilder.windows(acoustic, service, 900_000)
        val prompt = SceneBuilder.prompt(ws, "Sermon")
        assertTrue(ws.size <= 6)
        assertTrue(prompt.contains("\"sound\":\"music\""))
        assertTrue(prompt.length < 6000)
    }

    @Test fun `a song title is kept only when it was sung or said`() {
        val sung = "Amazing grace how sweet the sound that saved a wretch like me."
        assertEquals("Amazing Grace", SceneBuilder.groundedTitle("Amazing Grace", sung))
        assertNull(SceneBuilder.groundedTitle("How Great Thou Art", sung))
        assertNull(SceneBuilder.groundedTitle("null", sung))
        assertNull(SceneBuilder.groundedTitle("Grace", sung)) // one word is not enough evidence
    }

    @Test fun `labels are names, never copied lyric lines`() {
        val lyrics = "I once was lost but now am found was blind but now I see"
        assertNull(SceneBuilder.cleanLabel("I once was lost but now am found", lyrics))
        assertEquals("Opening worship", SceneBuilder.cleanLabel("Opening worship", lyrics))
    }

    @Test fun `the AI can refine labels but not call music a sermon, and invented titles are dropped`() {
        val ws = SceneBuilder.windows(acoustic, service, 900_000)
        val songId = ws.first { it.guess == SemanticActivity.SONG }.id
        val sermonId = ws.last().id
        val raw = """{"windows":[
            {"id":$songId,"activity":"sermon","confidence":0.9,"label":"Talk","song_title":"How Great Thou Art"},
            {"id":$sermonId,"activity":"sermon","confidence":0.95,"label":"Abiding in the vine","song_title":null}]}"""
        val map = SceneBuilder.parse(raw, ws)
        val song = map.at(90_000)!!
        assertEquals(SemanticActivity.SONG, song.activity)
        assertNull(song.songTitle)
        assertEquals("Abiding in the vine", map.at(500_000)!!.label)
        assertEquals("ai", map.at(500_000)!!.labelledBy)
    }

    @Test fun `scene maps round-trip through their file`() {
        val map = SceneBuilder.fromWindows(SceneBuilder.windows(acoustic, service, 900_000))
        assertEquals(map.scenes, SceneMap.fromJson(map.toJson())!!.scenes)
    }
}
