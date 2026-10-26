package com.craftflowtechnologies.meetingmind.core.notes

import com.craftflowtechnologies.meetingmind.ai.youtube.CondensedVideo
import com.craftflowtechnologies.meetingmind.core.model.BlockSource
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository

/**
 * A condensed YouTube video as note blocks: the video, its summary and key points, then its
 * transcript. The transcript blocks are marked [NoteBlock.PAYLOAD_NO_SHARE], so sharing and
 * exporting the note leave them out and keep the rest.
 */
object YouTubeNoteBlocks {

    fun blocks(url: String, video: CondensedVideo): List<NoteBlock> = buildList {
        add(block(NoteBlockType.EMBED, source = BlockSource.USER, payload = mapOf(
            NoteBlock.PAYLOAD_EMBED_KIND to NoteBlock.EMBED_YOUTUBE,
            NoteBlock.PAYLOAD_URL to url,
            NoteBlock.PAYLOAD_ALT to video.title
        )))
        if (video.summary.isNotBlank()) {
            add(heading("Summary"))
            add(text(NoteBlockType.PARAGRAPH, video.summary))
        }
        if (video.keyPoints.isNotEmpty()) {
            add(heading("Key points"))
            video.keyPoints.forEach { add(text(NoteBlockType.BULLET, it)) }
        }
        if (video.transcript.isNotEmpty()) {
            add(heading("Transcript", secret = true))
            video.transcript.forEach { line ->
                val words = if (line.timestamp.isBlank()) line.text else "${line.timestamp}  ${line.text}"
                add(text(NoteBlockType.PARAGRAPH, words, source = BlockSource.TRANSCRIPT, secret = true))
            }
        }
    }

    private fun heading(text: String, secret: Boolean = false) =
        text(NoteBlockType.HEADING_2, text, secret = secret)

    private fun text(
        type: NoteBlockType,
        text: String,
        source: BlockSource = BlockSource.AI,
        secret: Boolean = false
    ) = block(
        type = type,
        source = source,
        content = RichText.plain(text),
        payload = if (secret) mapOf(NoteBlock.PAYLOAD_NO_SHARE to "1") else emptyMap()
    )

    private fun block(
        type: NoteBlockType,
        source: BlockSource,
        content: RichText = RichText.EMPTY,
        payload: Map<String, String> = emptyMap()
    ) = NoteBlock(
        // The note id and position are set when the note is created.
        id = NoteRepository.newId("block"),
        noteId = "",
        position = 0,
        type = type,
        content = content,
        payload = payload,
        source = source
    )
}
