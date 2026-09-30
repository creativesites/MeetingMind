package com.craftflowtechnologies.meetingmind.core.work

import com.craftflowtechnologies.meetingmind.core.model.RecordingType

/*
 * Which signals to look for (docs/PLAN_PROFESSIONAL.md D6). Extraction stores every kind it finds
 * for a recording type; what the person sees by default is smaller (principle 5).
 */

/** COMMITMENT, DECISION, QUESTION and DEADLINE, for every work recording. */
val BaseSignalKinds: Set<ItemKind> = setOf(ItemKind.COMMITMENT, ItemKind.DECISION, ItemKind.QUESTION, ItemKind.DEADLINE)

private val WorkSignalKinds: Set<ItemKind> = BaseSignalKinds + setOf(
    ItemKind.RISK, ItemKind.REQUIREMENT, ItemKind.CONSTRAINT, ItemKind.ASSUMPTION, ItemKind.DEPENDENCY, ItemKind.OBJECTION,
    ItemKind.METRIC, ItemKind.SCOPE_CHANGE, ItemKind.APPROVAL
)

/** The kinds to ask for in a recording of this type. Empty for anything that isn't work. */
fun RecordingType.signalKinds(): Set<ItemKind> = when (this) {
    RecordingType.MEETING, RecordingType.CLIENT_CALL, RecordingType.PROJECT_BRIEF, RecordingType.DECISION_RECORD -> WorkSignalKinds
    RecordingType.ONE_ON_ONE, RecordingType.WEEKLY_REVIEW -> BaseSignalKinds + setOf(ItemKind.RISK, ItemKind.DEPENDENCY, ItemKind.SCOPE_CHANGE)
    // Blockers are the risks of a standup.
    RecordingType.STANDUP -> setOf(ItemKind.COMMITMENT, ItemKind.QUESTION, ItemKind.DEADLINE, ItemKind.RISK, ItemKind.DEPENDENCY)
    // What was said, and nothing more: no risks or requirements read into a consultation.
    RecordingType.CONSULTATION -> BaseSignalKinds
    else -> emptySet()
}

/** The kinds a work profile shows by default: the base four, plus risks and requirements where they matter. */
fun WorkProfile.defaultSignalKinds(): Set<ItemKind> = when (this) {
    WorkProfile.CLIENT_WORK, WorkProfile.PRODUCT, WorkProfile.LEGAL -> BaseSignalKinds + setOf(ItemKind.RISK, ItemKind.REQUIREMENT)
    else -> BaseSignalKinds
}

/** What goes into an extraction prompt to ask for signals. Empty when the recording type has none. */
object SignalPrompts {
    private val MEANING = mapOf(
        ItemKind.COMMITMENT to "someone promised to do something. speaker = who promised, counterparty = who it was promised to, due = when, if stated",
        ItemKind.DECISION to "the group agreed something. Set value to PROPOSED when it was only proposed and not agreed",
        ItemKind.QUESTION to "a question left open",
        ItemKind.DEADLINE to "a date something is due or happens. value = the date exactly as said",
        ItemKind.RISK to "something that could go wrong or is blocking",
        ItemKind.REQUIREMENT to "something the client or team requires",
        ItemKind.CONSTRAINT to "a limit on time, budget or scope",
        ItemKind.ASSUMPTION to "something taken as true that hasn't been checked",
        ItemKind.DEPENDENCY to "something waiting on another person or thing",
        ItemKind.OBJECTION to "a concern or objection someone raised",
        ItemKind.METRIC to "a number to reach or that was reported. value = the number as said",
        ItemKind.SCOPE_CHANGE to "something added, removed or changed in what will be delivered or when",
        ItemKind.APPROVAL to "someone approved or signed off on something"
    )

    /** The JSON field to add to the schema. */
    const val FIELD = "\"signals\":[{\"kind\":string,\"text\":string,\"sourceSegmentIds\":[string],\"confidence\":number,\"value\":string|null,\"speaker\":string|null,\"counterparty\":string|null,\"due\":string|null}]"

    fun section(type: RecordingType): String {
        val kinds = type.signalKinds()
        if (kinds.isEmpty()) return ""
        val lines = kinds.sortedBy { it.ordinal }.joinToString("\n") { "- ${it.name}: ${MEANING.getValue(it)}" }
        val consultation = if (type == RecordingType.CONSULTATION) "\nThis is a consultation: report only what was actually said. Never add a diagnosis, medication, dose, legal opinion or recommendation that was not spoken.\n" else ""
        return """

            Also report "signals": each of the following kinds that the transcript explicitly supports, one entry per occurrence, with a confidence between 0 and 1 and the ids of the lines it came from. Use only these kinds:
            $lines
            Report a signal only when it is stated; an empty array is a correct answer. Never invent a name, date, number or owner.$consultation
        """.trimIndent()
    }
}
