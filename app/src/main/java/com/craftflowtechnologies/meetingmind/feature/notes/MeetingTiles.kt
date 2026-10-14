package com.craftflowtechnologies.meetingmind.feature.notes

import com.craftflowtechnologies.meetingmind.core.model.BlockSource
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import kotlin.math.ceil
import kotlin.math.max

/** Where a hero tile takes the person: the note at its section, or the note's assistant. */
enum class TileAction { KEY_POINTS, ACTION_ITEMS, SUMMARY, ASSISTANT }

/** One tile in the Latest meeting hero. [subtitle] is always real text, never a placeholder. */
data class MeetingTile(val action: TileAction, val label: String, val subtitle: String)

private const val WORDS_PER_MINUTE = 200

/**
 * The hero's tiles, read from a note's blocks. A tile is returned only when it has data:
 * - Key Insights: AI bullets (the summary's points, decisions and questions);
 * - Action Items: checklist blocks, "N tasks", or "N of M done" once some are ticked;
 * - Summary: AI paragraphs, with a read time;
 * - Assistant: when [aiAvailable] for this note.
 * Pure, so the rules are unit tested in [MeetingTilesTest].
 */
fun meetingTiles(blocks: List<NoteBlock>, aiAvailable: Boolean): List<MeetingTile> {
    val tiles = mutableListOf<MeetingTile>()

    val points = blocks.count { it.type == NoteBlockType.BULLET && it.source == BlockSource.AI && it.content.text.isNotBlank() }
    if (points > 0) tiles += MeetingTile(TileAction.KEY_POINTS, "Key Insights", plural(points, "key point", "key points"))

    val tasks = blocks.filter { it.type == NoteBlockType.CHECKLIST && it.content.text.isNotBlank() }
    if (tasks.isNotEmpty()) {
        val done = tasks.count { it.checked }
        val subtitle = if (done == 0) plural(tasks.size, "task", "tasks") else "$done of ${tasks.size} done"
        tiles += MeetingTile(TileAction.ACTION_ITEMS, "Action Items", subtitle)
    }

    val summary = blocks.filter { it.source == BlockSource.AI && it.type == NoteBlockType.PARAGRAPH && it.content.text.isNotBlank() }
    if (summary.isNotEmpty()) {
        val words = summary.sumOf { wordCount(it.content.text) }
        val minutes = max(1, ceil(words / WORDS_PER_MINUTE.toDouble()).toInt())
        tiles += MeetingTile(TileAction.SUMMARY, "Summary", "$minutes min read")
    }

    if (aiAvailable) tiles += MeetingTile(TileAction.ASSISTANT, "AI Assistant", "Ask anything")
    return tiles
}

/**
 * The two insights the hero shows as cards, most useful first: action items, the summary, key points, then the
 * assistant. Pure, so the choice is unit-tested in [HeroCardsTest].
 */
fun heroCards(tiles: List<MeetingTile>, max: Int = 2): List<MeetingTile> {
    val order = listOf(TileAction.ACTION_ITEMS, TileAction.SUMMARY, TileAction.KEY_POINTS, TileAction.ASSISTANT)
    return order.mapNotNull { action -> tiles.firstOrNull { it.action == action } }.take(max)
}

private fun plural(count: Int, one: String, many: String) = "$count ${if (count == 1) one else many}"

private fun wordCount(text: String) = text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
