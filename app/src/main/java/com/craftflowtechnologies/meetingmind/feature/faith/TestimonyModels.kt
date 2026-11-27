package com.craftflowtechnologies.meetingmind.feature.faith

import com.craftflowtechnologies.meetingmind.core.circles2.Circle
import com.craftflowtechnologies.meetingmind.core.circles2.Post
import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.model.Workflows
import com.craftflowtechnologies.meetingmind.core.notes.RichText
import com.craftflowtechnologies.meetingmind.core.repository.NoteContent
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.core.share.AppLinks
import com.craftflowtechnologies.meetingmind.core.share.ShareCardContent
import com.craftflowtechnologies.meetingmind.feature.share.ShareRequest
import java.util.concurrent.TimeUnit

/** Note metadata keys for structured testimonies and milestone tracking. */
object TestimonyMeta {
    const val CATEGORY = "testimony_category"
    const val VERSE_REF = "testimony_verse_ref"
    const val VERSE_TEXT = "testimony_verse_text"
    const val REJOICE_COUNT = "testimony_rejoice_count"
    const val USER_REJOICED = "testimony_user_rejoiced"
    const val LAST_REJOICED_AT = "testimony_last_rejoiced_at"
    const val SHARED_CIRCLES = "testimony_shared_circles"
    const val PRAYER_ID = "testimony_prayer_id"
}

enum class TestimonyCategory(val key: String, val label: String) {
    HEALING("healing", "Healing"),
    PROVISION("provision", "Provision"),
    BREAKTHROUGH("breakthrough", "Breakthrough"),
    FAMILY("family", "Family"),
    GUIDANCE("guidance", "Guidance"),
    GRATITUDE("gratitude", "Gratitude");

    companion object {
        fun fromKey(raw: String?): TestimonyCategory? =
            entries.firstOrNull { it.key.equals(raw?.trim(), ignoreCase = true) }
    }
}

enum class TestimonyFilterTab(val label: String) {
    ALL("All Stories"),
    ANSWERED_PRAYERS("Answered Prayers"),
    DRAFTS("Drafts"),
    IN_CIRCLES("In My Circles")
}

/**
 * The five structured beats of a Faith Testimony note (matching `Workflows.template(RecordingType.TESTIMONY)`).
 */
data class TestimonyBeats(
    val situation: String = "",
    val prayedFor: String = "",
    val whatHappened: String = "",
    val learned: String = "",
    val scriptureNote: String = ""
) {
    val hasAnyContent: Boolean
        get() = listOf(situation, prayedFor, whatHappened, learned, scriptureNote).any { it.isNotBlank() }

    /** Builds clean prose suitable for excerpts, sharing cards, and Circle posts. */
    fun assembledBody(fallbackPlainText: String = ""): String {
        val parts = listOfNotNull(
            situation.trim().takeIf { it.isNotEmpty() },
            prayedFor.trim().takeIf { it.isNotEmpty() }?.let { "What I prayed: $it" },
            whatHappened.trim().takeIf { it.isNotEmpty() },
            learned.trim().takeIf { it.isNotEmpty() }?.let { "What stayed with me: $it" }
        )
        return if (parts.isNotEmpty()) parts.joinToString("\n\n") else fallbackPlainText.trim()
    }

    /** Primary highlight shown on feed cards (prioritizes the outcome beat). */
    fun leadExcerpt(fallbackPlainText: String = ""): String =
        whatHappened.trim().ifEmpty {
            situation.trim().ifEmpty {
                learned.trim().ifEmpty {
                    prayedFor.trim().ifEmpty { fallbackPlainText.trim() }
                }
            }
        }

    companion object {
        private val sectionOrder = listOf("situation", "prayed_for", "what_happened", "learned", "scripture")

        fun fromBlocks(blocks: List<NoteBlock>): TestimonyBeats {
            val bySection = mutableMapOf<String, MutableList<String>>()
            val unkeyed = mutableListOf<String>()
            val templateTitles = Workflows.template(RecordingType.TESTIMONY).sections.map { it.title.trim() }.toSet()

            for (block in blocks.sortedBy { it.position }) {
                if (block.type == NoteBlockType.HEADING_2 || block.type == NoteBlockType.HEADING_1 || block.type == NoteBlockType.HEADING_3) {
                    continue
                }
                val text = block.content.text.trim()
                if (text.isEmpty() || text in templateTitles) continue
                val key = block.sectionKey?.takeIf { it in sectionOrder }
                if (key != null) {
                    bySection.getOrPut(key) { mutableListOf() }.add(text)
                } else if (block.type.isText || block.type == NoteBlockType.MARKDOWN) {
                    unkeyed.add(text)
                }
            }

            val happened = bySection["what_happened"].orEmpty().joinToString("\n")
                .ifBlank { unkeyed.joinToString("\n") }

            return TestimonyBeats(
                situation = bySection["situation"].orEmpty().joinToString("\n"),
                prayedFor = bySection["prayed_for"].orEmpty().joinToString("\n"),
                whatHappened = happened,
                learned = bySection["learned"].orEmpty().joinToString("\n"),
                scriptureNote = bySection["scripture"].orEmpty().joinToString("\n")
            )
        }

        /**
         * Merges [beats] into a note's blocks, keeping existing template section headings and preserving
         * any attached non-text blocks (audio recordings, images, scripture blocks, note links).
         */
        fun toBlocks(
            noteId: String,
            existingBlocks: List<NoteBlock>,
            beats: TestimonyBeats,
            idGenerator: (String) -> String = { prefix -> NoteRepository.newId(prefix) }
        ): List<NoteBlock> {
            val sections = Workflows.template(RecordingType.TESTIMONY).sections
            val beatValues = mapOf(
                "situation" to beats.situation.trim(),
                "prayed_for" to beats.prayedFor.trim(),
                "what_happened" to beats.whatHappened.trim(),
                "learned" to beats.learned.trim(),
                "scripture" to beats.scriptureNote.trim()
            )
            val result = mutableListOf<NoteBlock>()
            var pos = 0

            val existingBySection = existingBlocks.groupBy { it.sectionKey }
            for (section in sections) {
                val sectionBlocks = existingBySection[section.key].orEmpty().sortedBy { it.position }
                val heading = sectionBlocks.firstOrNull { it.type == NoteBlockType.HEADING_2 }
                    ?: NoteBlock(
                        id = idGenerator("block"),
                        noteId = noteId,
                        position = pos,
                        type = NoteBlockType.HEADING_2,
                        content = RichText.plain(section.title),
                        sectionKey = section.key
                    )
                result += heading.copy(noteId = noteId, position = pos++)

                val primaryBody = sectionBlocks.firstOrNull { it.type != NoteBlockType.HEADING_2 && it.type.isText }
                val value = beatValues[section.key].orEmpty()
                val bodyBlock = (primaryBody ?: NoteBlock(
                    id = idGenerator("block"),
                    noteId = noteId,
                    position = pos,
                    type = section.bodyType,
                    payload = section.hint?.let { mapOf(NoteBlock.PAYLOAD_HINT to it) }.orEmpty(),
                    sectionKey = section.key
                )).copy(
                    noteId = noteId,
                    position = pos++,
                    content = if (value.isEmpty()) RichText.EMPTY else RichText.plain(value)
                )
                result += bodyBlock

                // Keep non-text attachments belonging to this section (recordings, images, scripture embeds)
                sectionBlocks.filter { !it.type.isText && it.type != NoteBlockType.DIVIDER }
                    .forEach { result += it.copy(noteId = noteId, position = pos++) }
            }

            // Preserve any unkeyed non-text blocks (e.g. audio recordings added separately)
            existingBlocks
                .filter { it.sectionKey !in sectionOrder && !it.type.isText && it.type != NoteBlockType.DIVIDER }
                .sortedBy { it.position }
                .forEach { result += it.copy(noteId = noteId, position = pos++) }

            return result
        }
    }
}

/**
 * Unified presentation model for the Wall of Remembrance: covers personal testimonies (both
 * published & drafts) and Circle testimonies shared in the user's Circles.
 */
data class TestimonyStory(
    val id: String,
    val noteId: String?,
    val postId: String?,
    val circleId: String?,
    val circleName: String?,
    val title: String,
    val excerpt: String,
    val fullText: String,
    val authorLabel: String,
    val category: TestimonyCategory?,
    val verseRef: String?,
    val verseText: String?,
    val storyDateMs: Long,
    val updatedAtMs: Long,
    val rejoiceCount: Int,
    val userRejoiced: Boolean,
    val isDraft: Boolean,
    val isPersonal: Boolean,
    val sharedToCircles: List<String>,
    val linkedPrayerId: String?,
    val linkedPrayerTitle: String?,
    val prayerStartedAtMs: Long?,
    val prayerDurationDays: Int?,
    val hasAudio: Boolean,
    val beats: TestimonyBeats
)

object TestimonyMapper {
    fun fromNote(
        note: Note,
        blocks: List<NoteBlock>,
        linkedPrayer: Note? = null,
        hasAudio: Boolean = false
    ): TestimonyStory {
        val beats = TestimonyBeats.fromBlocks(blocks)
        val rawBody = beats.assembledBody(fallbackPlainText = stripTemplateHeadings(note.plainText))
        val lead = beats.leadExcerpt(fallbackPlainText = stripTemplateHeadings(note.plainText))
        val storyDateMs = note.eventDate ?: note.createdAt
        val verseRef = note.metadata[TestimonyMeta.VERSE_REF]?.takeIf { it.isNotBlank() }
            ?: beats.scriptureNote.takeIf { it.isNotBlank() && it.length <= 60 }
        val verseText = note.metadata[TestimonyMeta.VERSE_TEXT]?.takeIf { it.isNotBlank() }
        val rejoiceCount = note.metadata[TestimonyMeta.REJOICE_COUNT]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val userRejoiced = note.metadata[TestimonyMeta.USER_REJOICED] == "1" || rejoiceCount > 0
        val sharedCircles = note.metadata[TestimonyMeta.SHARED_CIRCLES]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        val prayerStart = linkedPrayer?.createdAt
        val durationDays = prayerDurationDays(prayerStart, storyDateMs)

        return TestimonyStory(
            id = note.id,
            noteId = note.id,
            postId = null,
            circleId = null,
            circleName = null,
            title = note.title.trim(),
            excerpt = lead,
            fullText = rawBody,
            authorLabel = "You",
            category = TestimonyCategory.fromKey(note.metadata[TestimonyMeta.CATEGORY]),
            verseRef = verseRef,
            verseText = verseText,
            storyDateMs = storyDateMs,
            updatedAtMs = note.updatedAt,
            rejoiceCount = rejoiceCount,
            userRejoiced = userRejoiced,
            isDraft = NoteContent.isDraft(note),
            isPersonal = true,
            sharedToCircles = sharedCircles,
            linkedPrayerId = linkedPrayer?.id ?: note.metadata[TestimonyMeta.PRAYER_ID]?.takeIf { it.isNotBlank() },
            linkedPrayerTitle = linkedPrayer?.title?.takeIf { it.isNotBlank() },
            prayerStartedAtMs = prayerStart,
            prayerDurationDays = durationDays,
            hasAudio = hasAudio,
            beats = beats
        )
    }

    fun fromCirclePost(post: Post, circle: Circle): TestimonyStory {
        val cleaned = post.body.trim()
        val firstLine = cleaned.lineSequence().firstOrNull()?.trim().orEmpty()
        val derivedTitle = if (firstLine.length in 4..68 && cleaned.contains('\n')) firstLine else "Testimony from ${circle.name}"
        val bodyWithoutHeading = if (derivedTitle == firstLine) cleaned.removePrefix(firstLine).trim() else cleaned
        val amens = post.counts.reactions.values.sum() + post.counts.prayed
        return TestimonyStory(
            id = "circle_${circle.id}_${post.id}",
            noteId = null,
            postId = post.id,
            circleId = circle.id,
            circleName = circle.name,
            title = derivedTitle,
            excerpt = bodyWithoutHeading.ifBlank { cleaned },
            fullText = cleaned,
            authorLabel = post.authorName?.takeIf { it.isNotBlank() } ?: circle.groupWord,
            category = null,
            verseRef = post.verseRef?.takeIf { it.isNotBlank() },
            verseText = null,
            storyDateMs = post.createdAt,
            updatedAtMs = post.editedAt ?: post.createdAt,
            rejoiceCount = amens,
            userRejoiced = false,
            isDraft = false,
            isPersonal = false,
            sharedToCircles = listOf(circle.name),
            linkedPrayerId = null,
            linkedPrayerTitle = null,
            prayerStartedAtMs = null,
            prayerDurationDays = null,
            hasAudio = post.attachments.any { it.durationMs != null },
            beats = TestimonyBeats(whatHappened = bodyWithoutHeading.ifBlank { cleaned })
        )
    }

    private fun stripTemplateHeadings(plainText: String): String {
        val titles = Workflows.template(RecordingType.TESTIMONY).sections.map { it.title.trim() }.toSet()
        return plainText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it !in titles }
            .joinToString("\n")
    }
}

fun prayerDurationDays(prayerStartMs: Long?, answeredMs: Long): Int? {
    if (prayerStartMs == null || answeredMs < prayerStartMs) return null
    return TimeUnit.MILLISECONDS.toDays(answeredMs - prayerStartMs).toInt()
}

fun formatDurationLabel(days: Int?): String? = when {
    days == null -> null
    days <= 0 -> "Answered same day"
    days == 1 -> "Answered in 1 day"
    days < 14 -> "Answered in $days days"
    days < 60 -> "Answered in ${days / 7} weeks"
    days < 365 -> "Answered in ${days / 30} months"
    else -> {
        val years = days / 365
        if (years == 1) "Answered after 1 year" else "Answered after $years years"
    }
}

fun buildTestimonyShareText(story: TestimonyStory, includeAppLink: Boolean = true): String {
    val header = story.title.ifBlank { "Testimony of God's Faithfulness" }
    val body = story.fullText.ifBlank { story.excerpt }.ifBlank { "God is faithful." }
    val verse = story.verseRef?.let { "\n\n— $it" }.orEmpty()
    val raw = "$header\n\n$body$verse\n\n#MeetingMind #Testimony"
    return if (includeAppLink) AppLinks.withAppLink(raw) else raw
}

fun buildTestimonyShareCardRequest(story: TestimonyStory): ShareRequest {
    val eyebrow = buildString {
        append("TESTIMONY")
        story.category?.let { append(" · ").append(it.label.uppercase()) }
    }
    val title = story.title.ifBlank { "God Is Faithful" }
    val body = story.excerpt.ifBlank { story.fullText }.ifBlank { "Praise report of answered prayer and God's faithfulness." }
    val cardText = "$title\n\n${body.take(240)}"
    val caption = buildTestimonyShareText(story, includeAppLink = false)
    return ShareRequest(
        content = ShareCardContent(
            eyebrow = eyebrow,
            text = cardText,
            reference = story.verseRef ?: formatDurationLabel(story.prayerDurationDays) ?: "Wall of Remembrance",
            quoted = true
        ),
        theme = "$title, praise report, warm dawn",
        caption = caption
    )
}
