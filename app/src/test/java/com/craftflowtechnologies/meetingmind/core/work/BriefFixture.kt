package com.craftflowtechnologies.meetingmind.core.work

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.llm.LanguageModel

/** A model that answers with a fixed reply, and remembers what it was asked and whether the material was sensitive. */
class FakeWorkModel(var reply: String) : LanguageModel {
    val prompts = mutableListOf<String>()
    override suspend fun generate(prompt: String, maxOutputTokens: Int): AiResult<String> { prompts += prompt; return AiResult.Success(reply) }
}

class FakeWorkModels(val model: FakeWorkModel?) : WorkModels {
    val asked = mutableListOf<Boolean>()
    override suspend fun forPack(sensitive: Boolean): LanguageModel? { asked += sensitive; return model }
}

/**
 * A project with history: a decision that moved (superseded), a promise each way, a risk, a
 * question, and one still proposed. Items have real quotes from a recording.
 */
fun PulseFixture.projectWorld(): Map<String, com.craftflowtechnologies.meetingmind.core.database.ItemEntity> {
    val day = 86_400_000L
    val a = item(ItemKind.DECISION, ItemStatus.ACTIVE, "Launch on October 14", created = now - 20 * day, quote = "We launch on October 14.", startMs = 61_000)
    val b = items.let { repo -> kotlinx.coroutines.runBlocking {
        clockValue = now - 3 * day
        repo.supersede(a.id, com.craftflowtechnologies.meetingmind.core.database.ItemEntity("", "DECISION", "ACTIVE", "Launch on October 21", projectId = "nb", orgId = "org1", meetingId = "m1", reviewed = true, createdAt = now - 3 * day, updatedAt = now - 3 * day),
            listOf(com.craftflowtechnologies.meetingmind.core.database.ItemEvidenceEntity("", "", "m1", null, null, "[]", 130_000, 134_000, "Actually, October 21 works better.")))!!
    } }
    clockValue = now - day
    val docs = theirs("API docs", due = today + 2 * day)
    val keys = item(ItemKind.COMMITMENT, ItemStatus.OPEN, "Send the deck", Direction.MINE, today - day, quote = "I'll send the deck.", startMs = 20_000)
    val risk = item(ItemKind.RISK, ItemStatus.OPEN, "Vendor may be late", quote = "The vendor is slipping.", startMs = 200_000)
    val question = item(ItemKind.QUESTION, ItemStatus.OPEN, "Which region?")
    val proposed = item(ItemKind.DECISION, ItemStatus.PROPOSED, "Adopt headless CMS", quote = "Maybe headless.", startMs = 300_000)
    val done = item(ItemKind.COMMITMENT, ItemStatus.COMPLETED, "Sign the contract", Direction.MINE)
    clockValue = now
    return mapOf("old" to a, "new" to b, "docs" to docs, "deck" to keys, "risk" to risk, "question" to question, "proposed" to proposed, "done" to done)
}
