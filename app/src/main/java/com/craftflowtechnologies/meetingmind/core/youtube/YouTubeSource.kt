package com.craftflowtechnologies.meetingmind.core.youtube

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.craftflowtechnologies.meetingmind.core.model.BlockSource
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.notes.InlineSpan
import com.craftflowtechnologies.meetingmind.core.notes.InlineStyle
import com.craftflowtechnologies.meetingmind.core.notes.RichText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A note made from a YouTube video: playing the video, and saying where the note came from.
 *
 * - **Play.** The video opens in the YouTube app or the browser through a plain `ACTION_VIEW`, at
 *   the start or at a moment (`&t=<seconds>s`). No embedded player and no download, so the app stays
 *   within YouTube's terms and Play policy. No YouTube logo either: the label says "Play on YouTube".
 * - **Attribution.** A source line is the last block of the note: the video's title, its channel,
 *   the full link, when it was imported, and that the notes were written by AI from the video.
 *   It is an ordinary block, so exports, shares and Circles carry it. [ensure] puts it back at the
 *   bottom after re-processing and adds it to older YouTube notes when they are opened.
 *
 * The note's metadata holds what the import learned (no schema change): [META_URL], [META_CHANNEL],
 * [META_TITLE] and [META_IMPORTED_AT].
 */
object YouTubeSource {

    const val META_URL = "youtubeUrl"
    const val META_VIDEO_ID = "youtubeVideoId"
    const val META_CHANNEL = "youtubeChannel"
    const val META_TITLE = "youtubeTitle"
    const val META_IMPORTED_AT = "youtubeImportedAt"

    /** Section key of the source line, so it can be found and kept at the bottom. */
    const val SECTION_KEY = "youtube_source"

    /** What the source line says. */
    data class Info(val url: String, val title: String?, val channel: String?, val importedAt: Long)

    /** The canonical watch link at [ms] into the video (`&t=<seconds>s`); the plain link at the start. */
    fun watchUrlAt(parsed: ParsedYouTubeUrl, ms: Long?): String {
        val sec = ((ms ?: 0L) / 1000L).coerceAtLeast(0L)
        return if (sec > 0L) "${parsed.canonicalWatchUrl}&t=${sec}s" else parsed.canonicalWatchUrl
    }

    /** Opens the video in the YouTube app, or the browser when there is none. False if nothing can open it. */
    fun open(context: Context, parsed: ParsedYouTubeUrl, ms: Long? = null): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(watchUrlAt(parsed, ms))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { context.startActivity(intent); true } catch (_: ActivityNotFoundException) { false }
    }

    /**
     * The source of a note, from its metadata or else from its recording: a YouTube import keeps
     * the video's link where an audio recording keeps its file path.
     */
    fun infoFor(metadata: Map<String, String>, recordingPath: String?, recordingTitle: String?, createdAt: Long): Info? {
        val parsed = YouTubeUrlParser.parse(metadata[META_URL]) ?: YouTubeUrlParser.parse(recordingPath) ?: return null
        return Info(
            url = parsed.canonicalWatchUrl,
            title = metadata[META_TITLE]?.takeIf { it.isNotBlank() } ?: recordingTitle?.takeIf { it.isNotBlank() },
            channel = metadata[META_CHANNEL]?.takeIf { it.isNotBlank() },
            importedAt = metadata[META_IMPORTED_AT]?.toLongOrNull() ?: createdAt
        )
    }

    /** `Source: "Title", Channel (YouTube), https://…, imported 16 Nov 2026. Notes written by AI from the video.` */
    fun sourceText(info: Info, locale: Locale = Locale.UK): RichText {
        val date = SimpleDateFormat("d MMM yyyy", locale).format(Date(info.importedAt))
        val who = listOfNotNull(info.title?.let { "“$it”" }, info.channel?.let { "$it (YouTube)" } ?: "YouTube").joinToString(", ")
        val head = "Source: $who, "
        val tail = ", imported $date. Notes written by AI from the video."
        val text = head + info.url + tail
        return RichText.create(text, listOf(InlineSpan(head.length, head.length + info.url.length, InlineStyle.LINK, info.url)))
    }

    /**
     * [blocks] with the source line last (before any trailing empty lines the editor keeps for
     * typing). An existing line keeps its id and words, so an edit the person made stays; a missing
     * one is written from [info]. Returns [blocks] itself when nothing changes.
     */
    fun ensure(blocks: List<NoteBlock>, noteId: String, info: Info?, newId: () -> String): List<NoteBlock> {
        if (info == null) return blocks
        val existing = blocks.firstOrNull { it.sectionKey == SECTION_KEY }
        val source = existing ?: NoteBlock(
            id = newId(), noteId = noteId, position = 0, type = NoteBlockType.PARAGRAPH,
            content = sourceText(info), source = BlockSource.IMPORTED, sectionKey = SECTION_KEY
        )
        val rest = blocks.filter { it.sectionKey != SECTION_KEY }
        val trailingEmpty = rest.takeLastWhile { it.type == NoteBlockType.PARAGRAPH && it.content.text.isBlank() && it.source == BlockSource.USER }
        val body = rest.dropLast(trailingEmpty.size)
        val result = body + source + trailingEmpty
        val unchanged = existing != null && result.size == blocks.size && result.indices.all { result[it].id == blocks[it].id }
        return if (unchanged) blocks else result
    }
}
