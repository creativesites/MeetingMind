package com.craftflowtechnologies.meetingmind.ai.voice.read

import com.craftflowtechnologies.meetingmind.core.model.BlockSource
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.notes.RichText

/** Builds blocks for Read to Me tests; positions follow call order unless given. */
internal class Blocks {
    private var next = 0
    val all = mutableListOf<NoteBlock>()

    fun add(
        type: NoteBlockType,
        text: String = "",
        key: String? = null,
        source: BlockSource = BlockSource.USER,
        edited: Boolean = false,
        payload: Map<String, String> = emptyMap(),
        indent: Int = 0,
        checked: Boolean = false,
        id: String = "b${next}"
    ): NoteBlock = NoteBlock(
        id = id, noteId = "n1", position = next++, type = type, content = RichText.plain(text),
        payload = payload, source = source, sectionKey = key, isUserEdited = edited, indent = indent, checked = checked
    ).also { all += it }

    fun scripture(reference: String, key: String? = null, extra: Map<String, String> = emptyMap(), id: String = "b${next}") =
        add(NoteBlockType.SCRIPTURE, reference, key, BlockSource.SCRIPTURE, payload = mapOf("reference" to reference) + extra, id = id)
}

internal fun lookupOf(vararg pairs: Pair<String, ResolvedScripture?>): ScriptureLookup {
    val map = pairs.toMap()
    return ScriptureLookup { block -> map[block.payload["reference"]] }
}

internal val john316 = ResolvedScripture(
    spokenReference = "John chapter 3, verses 16 to 17",
    translationName = "Berean Standard Bible",
    verses = listOf(
        ResolvedScripture.Verse(16, "For God so loved the world that He gave His one and only Son, that everyone who believes in Him shall not perish but have eternal life."),
        ResolvedScripture.Verse(17, "For God did not send His Son into the world to condemn the world, but to save the world through Him.")
    ),
    openForCloud = true
)

internal fun extract(
    blocks: Blocks,
    lookup: ScriptureLookup = ScriptureLookup { null },
    options: ExtractOptions = ExtractOptions(),
    notePrivate: Boolean = false,
    privateKeys: Set<String> = emptySet()
) = NoteSpeechExtractor.extract("My study", notePrivate, blocks.all, privateKeys, lookup, options)
