package com.craftflowtechnologies.meetingmind.core.work

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.tools.TranscriptToolPrompts
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Prepare's optional prose: a "Last time" line and agenda wording, each citing what it rests on. */
data class PrepProse(val lastTime: BriefSentence?, val agenda: List<BriefSentence>) {
    val isEmpty: Boolean get() = lastTime == null && agenda.isEmpty()
}

/**
 * Prepare (docs/PLAN_PROFESSIONAL.md D5.4), the model half. The structure stays the database's; this
 * adds one line about last time and agenda phrasing, under the same rules as a brief: every sentence
 * cites an id in the pack, and one that doesn't is dropped. With no model there is simply no prose.
 */
class PrepareWriter(
    private val database: MeetMindDatabase,
    private val models: WorkModels?,
    private val privacy: PackPrivacy = PackPrivacy { _, _ -> false },
    private val clock: () -> Long = System::currentTimeMillis
) {
    suspend fun write(scope: PackScope, prep: PrepPack): PrepProse = withContext(Dispatchers.IO) {
        if (prep.isEmpty) return@withContext PrepProse(null, emptyList())
        val pack = ContextPackBuilder(database, privacy, clock).build(scope)
        if (pack.items.isEmpty()) return@withContext PrepProse(null, emptyList())
        val model = models?.forPack(pack.sensitive) ?: return@withContext PrepProse(null, emptyList())
        val raw = (model.generate(prompt(pack, prep), maxOutputTokens = 350) as? AiResult.Success)?.value ?: return@withContext PrepProse(null, emptyList())
        PrepProse(
            lastTime = BriefGrounding.keep(BriefGrounding.parse(raw, "lastTime"), pack.ids).firstOrNull(),
            agenda = BriefGrounding.keep(BriefGrounding.parse(raw, "agenda"), pack.ids).take(PrepareLimits.AGENDA)
        )
    }

    private fun prompt(pack: ContextPack, prep: PrepPack) = buildString {
        appendLine(TranscriptToolPrompts.FIDELITY_CONTRACT.trim())
        appendLine()
        appendLine("Help someone prepare for a conversation about \"${pack.title}\", using ONLY the facts below.")
        appendLine("\"lastTime\": one sentence on where the last conversation left off. \"agenda\": up to ${PrepareLimits.AGENDA} short agenda lines, each about a single open item.")
        appendLine("Every sentence must cite the ids, in square brackets in the facts, of the items or quotes it rests on. A sentence with no citation will be thrown away. Never state a date, name, number or decision that is not in the facts.")
        appendLine("Answer with JSON only: {\"lastTime\":[{\"text\":string,\"cites\":[string]}],\"agenda\":[{\"text\":string,\"cites\":[string]}]}")
        appendLine()
        prep.lastMeeting?.let { appendLine("Last meeting: ${it.title}" + (it.itemId?.let { id -> " [$id]" } ?: "")) }
        appendLine("Facts:")
        append(pack.render())
    }
}

object PrepareLimits { const val AGENDA = 6 }
