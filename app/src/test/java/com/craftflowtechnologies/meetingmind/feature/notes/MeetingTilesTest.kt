package com.craftflowtechnologies.meetingmind.feature.notes

import com.craftflowtechnologies.meetingmind.core.model.BlockSource
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.notes.RichText
import org.junit.Assert.assertEquals
import org.junit.Test

class MeetingTilesTest {

    private fun block(type: NoteBlockType, text: String, source: BlockSource = BlockSource.AI, checked: Boolean = false) = NoteBlock(
        id = "b" + text.hashCode() + type.name, noteId = "n", position = 0, type = type,
        content = RichText.plain(text), source = source, checked = checked
    )

    private fun words(n: Int) = List(n) { "word" }.joinToString(" ")

    @Test fun `no data gives no tiles`() {
        assertEquals(emptyList<MeetingTile>(), meetingTiles(emptyList(), aiAvailable = false))
    }

    @Test fun `user typed notes without AI content give no insight or summary tiles`() {
        val blocks = listOf(block(NoteBlockType.PARAGRAPH, "My own words", BlockSource.USER), block(NoteBlockType.BULLET, "my list", BlockSource.USER))
        assertEquals(emptyList<MeetingTile>(), meetingTiles(blocks, aiAvailable = false))
    }

    @Test fun `only the assistant when AI is available and nothing else exists`() {
        assertEquals(
            listOf(MeetingTile(TileAction.ASSISTANT, "AI Assistant", "Ask anything")),
            meetingTiles(emptyList(), aiAvailable = true)
        )
    }

    @Test fun `key insights count AI bullets with the right plural`() {
        val one = meetingTiles(listOf(block(NoteBlockType.BULLET, "Ship Friday")), aiAvailable = false)
        assertEquals(MeetingTile(TileAction.KEY_POINTS, "Key Insights", "1 key point"), one.single())
        val three = meetingTiles(List(3) { block(NoteBlockType.BULLET, "point $it") }, aiAvailable = false)
        assertEquals("3 key points", three.single().subtitle)
    }

    @Test fun `action items show a task count when none are done`() {
        val tiles = meetingTiles(listOf(block(NoteBlockType.CHECKLIST, "Send deck"), block(NoteBlockType.CHECKLIST, "Book room")), aiAvailable = false)
        assertEquals(MeetingTile(TileAction.ACTION_ITEMS, "Action Items", "2 tasks"), tiles.single())
    }

    @Test fun `a single task is singular`() {
        val tiles = meetingTiles(listOf(block(NoteBlockType.CHECKLIST, "Send deck")), aiAvailable = false)
        assertEquals("1 task", tiles.single().subtitle)
    }

    @Test fun `completed tasks show how many are done`() {
        val blocks = listOf(
            block(NoteBlockType.CHECKLIST, "Send deck", checked = true),
            block(NoteBlockType.CHECKLIST, "Book room"),
            block(NoteBlockType.CHECKLIST, "Call Ana", checked = true)
        )
        assertEquals("2 of 3 done", meetingTiles(blocks, aiAvailable = false).single().subtitle)
    }

    @Test fun `all tasks done still reads as done, not as an empty state`() {
        val blocks = listOf(block(NoteBlockType.CHECKLIST, "Send deck", checked = true), block(NoteBlockType.CHECKLIST, "Book", checked = true))
        assertEquals("2 of 2 done", meetingTiles(blocks, aiAvailable = false).single().subtitle)
    }

    @Test fun `blank checklist lines do not count as tasks`() {
        assertEquals(emptyList<MeetingTile>(), meetingTiles(listOf(block(NoteBlockType.CHECKLIST, "   ")), aiAvailable = false))
    }

    @Test fun `summary read time rounds up to whole minutes with a one minute floor`() {
        val short = meetingTiles(listOf(block(NoteBlockType.PARAGRAPH, words(30))), aiAvailable = false)
        assertEquals("1 min read", short.single().subtitle)
        val long = meetingTiles(listOf(block(NoteBlockType.PARAGRAPH, words(401))), aiAvailable = false)
        assertEquals("3 min read", long.single().subtitle)
    }

    @Test fun `all four tiles appear in order when every source has data`() {
        val blocks = listOf(
            block(NoteBlockType.BULLET, "Point"),
            block(NoteBlockType.CHECKLIST, "Task", checked = true),
            block(NoteBlockType.CHECKLIST, "Task two"),
            block(NoteBlockType.PARAGRAPH, words(250))
        )
        val tiles = meetingTiles(blocks, aiAvailable = true)
        assertEquals(
            listOf(TileAction.KEY_POINTS, TileAction.ACTION_ITEMS, TileAction.SUMMARY, TileAction.ASSISTANT),
            tiles.map { it.action }
        )
        assertEquals("1 of 2 done", tiles[1].subtitle)
        assertEquals("2 min read", tiles[2].subtitle)
    }
}
