package com.craftflowtechnologies.meetingmind.ai.learning

import com.craftflowtechnologies.meetingmind.core.common.Formatters
import com.craftflowtechnologies.meetingmind.core.learning.SpokenNotationNormalizer
import com.craftflowtechnologies.meetingmind.core.learning.SubjectDomain
import com.craftflowtechnologies.meetingmind.core.model.BlockSource
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import com.craftflowtechnologies.meetingmind.core.notes.RichText
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.core.work.Mark
import com.craftflowtechnologies.meetingmind.core.work.MarkKind

object LectureNoteBuilder {

    data class GeneratedLecture(
        val blocks: List<NoteBlock>,
        val keys: Set<String>,
        val endKeys: Set<String>
    )

    fun build(
        noteId: String,
        meetingId: String,
        segments: List<TranscriptSegment>,
        domain: SubjectDomain,
        synthesis: LectureSynthesis?,
        marks: List<Mark>
    ): GeneratedLecture {
        val blocks = mutableListOf<NoteBlock>()
        val keys = mutableSetOf<String>()
        val endKeys = mutableSetOf<String>()

        fun newId() = NoteRepository.newId("block")

        // 1. Exam Hints section (from marks or synthesis)
        val examMarks = marks.filter { it.kind == MarkKind.EXAM_HINT }
        val examHints = synthesis?.examHints.orEmpty()
        if (examMarks.isNotEmpty() || examHints.isNotEmpty()) {
            val sectionKey = "lecture_exam_hints"
            keys.add(sectionKey)
            blocks.add(
                NoteBlock(
                    id = newId(),
                    noteId = noteId,
                    position = blocks.size,
                    type = NoteBlockType.HEADING_2,
                    source = BlockSource.AI,
                    content = RichText.plain("High-Yield Exam Hints"),
                    sectionKey = sectionKey
                )
            )
            examMarks.forEach { m ->
                val at = Formatters.formatDurationHms(m.atMs)
                val text = m.text?.ifBlank { null } ?: "Exam emphasis marked at $at"
                blocks.add(
                    NoteBlock(
                        id = newId(),
                        noteId = noteId,
                        position = blocks.size,
                        type = NoteBlockType.CALLOUT,
                        source = BlockSource.AI,
                        content = RichText.plain(text),
                        sectionKey = sectionKey,
                        payload = mapOf(
                            NoteBlock.PAYLOAD_CALLOUT_KIND to "EXAM_TIP",
                            NoteBlock.PAYLOAD_CALLOUT_TITLE to "Lecturer Emphasis · $at",
                            NoteBlock.PAYLOAD_MEETING_ID to meetingId,
                            NoteBlock.PAYLOAD_START_MS to m.atMs.toString(),
                            "examHighYield" to "1"
                        )
                    )
                )
            }
            examHints.forEach { hint ->
                blocks.add(
                    NoteBlock(
                        id = newId(),
                        noteId = noteId,
                        position = blocks.size,
                        type = NoteBlockType.CALLOUT,
                        source = BlockSource.AI,
                        content = RichText.plain(hint),
                        sectionKey = sectionKey,
                        payload = mapOf(
                            NoteBlock.PAYLOAD_CALLOUT_KIND to "EXAM_TIP",
                            NoteBlock.PAYLOAD_CALLOUT_TITLE to "Key Exam Target",
                            "examHighYield" to "1"
                        )
                    )
                )
            }
        }

        // 2. Unresolved "Lost Me" Moments section
        val confusedMarks = marks.filter { it.kind == MarkKind.CONFUSED }
        val confusionUnpacks = synthesis?.confusionUnpacks.orEmpty()
        if (confusedMarks.isNotEmpty() || confusionUnpacks.isNotEmpty()) {
            val sectionKey = "lecture_confusion_unpacks"
            keys.add(sectionKey)
            blocks.add(
                NoteBlock(
                    id = newId(),
                    noteId = noteId,
                    position = blocks.size,
                    type = NoteBlockType.HEADING_2,
                    source = BlockSource.AI,
                    content = RichText.plain("Unpacked Explanations (From 'Lost me' moments)"),
                    sectionKey = sectionKey
                )
            )
            confusedMarks.forEach { m ->
                val at = Formatters.formatDurationHms(m.atMs)
                val text = m.text?.ifBlank { null } ?: "Clarification for point at $at"
                blocks.add(
                    NoteBlock(
                        id = newId(),
                        noteId = noteId,
                        position = blocks.size,
                        type = NoteBlockType.CALLOUT,
                        source = BlockSource.ENRICHED,
                        content = RichText.plain(text),
                        sectionKey = sectionKey,
                        payload = mapOf(
                            NoteBlock.PAYLOAD_CALLOUT_KIND to "EXPLANATION",
                            NoteBlock.PAYLOAD_CALLOUT_TITLE to "Clarification · $at",
                            NoteBlock.PAYLOAD_MEETING_ID to meetingId,
                            NoteBlock.PAYLOAD_START_MS to m.atMs.toString(),
                            NoteBlock.PAYLOAD_ENRICH_LABEL to "Unpack explanation",
                            NoteBlock.PAYLOAD_UNPACKED to "0"
                        )
                    )
                )
            }
            confusionUnpacks.forEach { unpack ->
                blocks.add(
                    NoteBlock(
                        id = newId(),
                        noteId = noteId,
                        position = blocks.size,
                        type = NoteBlockType.CALLOUT,
                        source = BlockSource.ENRICHED,
                        content = RichText.plain(unpack),
                        sectionKey = sectionKey,
                        payload = mapOf(
                            NoteBlock.PAYLOAD_CALLOUT_KIND to "EXPLANATION",
                            NoteBlock.PAYLOAD_CALLOUT_TITLE to "Step-by-Step Unpack",
                            NoteBlock.PAYLOAD_ENRICH_LABEL to "Unpack concept",
                            NoteBlock.PAYLOAD_UNPACKED to "0"
                        )
                    )
                )
            }
        }

        // 3. Domain Template Sections
        val templates = domain.templateSections()
        templates.forEach { tmpl ->
            keys.add(tmpl.key)
            blocks.add(
                NoteBlock(
                    id = newId(),
                    noteId = noteId,
                    position = blocks.size,
                    type = NoteBlockType.HEADING_2,
                    source = BlockSource.AI,
                    content = RichText.plain(tmpl.title),
                    sectionKey = tmpl.key
                )
            )

            if (tmpl.key == "concepts" || tmpl.key == "definitions" || tmpl.key == "definitions_axioms") {
                if (synthesis != null && synthesis.concepts.isNotEmpty()) {
                    synthesis.concepts.forEach { concept ->
                        blocks.add(
                            NoteBlock(
                                id = newId(),
                                noteId = noteId,
                                position = blocks.size,
                                type = tmpl.bodyType,
                                source = BlockSource.AI,
                                content = RichText.plain("${concept.name}: ${concept.definition}"),
                                sectionKey = tmpl.key
                            )
                        )
                        if (!concept.formulaLatex.isNullOrBlank()) {
                            blocks.add(
                                NoteBlock(
                                    id = newId(),
                                    noteId = noteId,
                                    position = blocks.size,
                                    type = NoteBlockType.MATH,
                                    source = BlockSource.AI,
                                    content = RichText.plain(concept.formulaLatex),
                                    sectionKey = tmpl.key
                                )
                            )
                        }
                    }
                } else {
                    // Offline fallback: extract key terms from vocabularyHints present in segments
                    val allText = segments.joinToString(" ") { it.text }
                    val matchedVocab = domain.vocabularyHints().filter { allText.contains(it, ignoreCase = true) }
                    if (matchedVocab.isNotEmpty()) {
                        matchedVocab.take(6).forEach { term ->
                            blocks.add(
                                NoteBlock(
                                    id = newId(),
                                    noteId = noteId,
                                    position = blocks.size,
                                    type = NoteBlockType.BULLET,
                                    source = BlockSource.AI,
                                    content = RichText.plain(term),
                                    sectionKey = tmpl.key
                                )
                            )
                        }
                    } else {
                        blocks.add(
                            NoteBlock(
                                id = newId(),
                                noteId = noteId,
                                position = blocks.size,
                                type = NoteBlockType.PARAGRAPH,
                                source = BlockSource.AI,
                                content = RichText.plain("Key lecture concepts captured from recording."),
                                sectionKey = tmpl.key
                            )
                        )
                    }
                }
            } else if (tmpl.key == "my_notes" || tmpl.key == "questions") {
                endKeys.add(tmpl.key)
                blocks.add(
                    NoteBlock(
                        id = newId(),
                        noteId = noteId,
                        position = blocks.size,
                        type = tmpl.bodyType,
                        source = BlockSource.USER,
                        content = RichText.plain(""),
                        sectionKey = tmpl.key,
                        payload = mapOf(NoteBlock.PAYLOAD_HINT to (tmpl.hint ?: "Write your notes here…"))
                    )
                )
            } else {
                // Other sections (e.g. worked examples, derivations)
                val normalizedSummary = SpokenNotationNormalizer.normalize(
                    synthesis?.summary?.takeIf { it.isNotBlank() } ?: segments.take(4).joinToString(" ") { it.text },
                    domain
                )
                blocks.add(
                    NoteBlock(
                        id = newId(),
                        noteId = noteId,
                        position = blocks.size,
                        type = tmpl.bodyType,
                        source = BlockSource.AI,
                        content = RichText.plain(normalizedSummary.take(400)),
                        sectionKey = tmpl.key
                    )
                )
            }
        }

        return GeneratedLecture(blocks, keys, endKeys)
    }
}
