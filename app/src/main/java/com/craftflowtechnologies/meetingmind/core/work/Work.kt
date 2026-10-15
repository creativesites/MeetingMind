package com.craftflowtechnologies.meetingmind.core.work

import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.model.Workflows
import org.json.JSONArray

/*
 * The work model (docs/PLAN_PROFESSIONAL.md §5). People see four nouns — My tasks, Waiting on,
 * Decisions and Open questions — built on the app's own tables: tasks and people for the first
 * two, a recording's findings for the others.
 */

/** How a follow-up leaves MeetingMind (PLAN_PROFESSIONAL.md §6.5). */
enum class Channel(val label: String) { WHATSAPP("WhatsApp"), EMAIL("Email"), SMS("Message"), SHARE("More apps") }

enum class PersonKind { PERSON, ORG }

/** Recording and note types that belong to Work. */
val WorkTypes: List<RecordingType> = RecordingType.entries.filter { Workflows.space(it) == NotebookSpace.WORK }
val WorkTypeNames: List<String> = WorkTypes.map { it.name }

/** A person or organisation, as the Work screens see them. */
data class WorkPerson(
    val id: String,
    val kind: PersonKind = PersonKind.PERSON,
    val name: String,
    val role: String? = null,
    val aliases: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val phones: List<String> = emptyList(),
    val orgId: String? = null,
    val preferredChannel: Channel? = null,
    val isSelf: Boolean = false,
    val notes: String = "",
    val confidential: Boolean = false,
    val lastSeenAt: Long? = null
) {
    val initials: String get() = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    val firstName: String get() = name.trim().substringBefore(' ')
}

enum class FindingKind { ACTION, FOLLOW_UP, DECISION, QUESTION }

/**
 * Something a recording found, waiting in its Wrap-up: an action, a follow-up, a decision or a
 * question. The owner's name is read through the speaker, so a rename shows at once (§5.5).
 */
data class Finding(
    val id: String,
    val meetingId: String,
    val kind: FindingKind,
    val text: String,
    val ownerSpeakerId: String? = null,
    val ownerName: String? = null,
    /** The owner is the app's own user (their speaker is linked to them). */
    val ownerIsSelf: Boolean = false,
    val dueText: String? = null,
    val dueAt: Long? = null,
    val confidence: Float? = null,
    val sourceSegmentIds: List<String> = emptyList(),
    val startMs: Long? = null,
    val answer: String? = null,
    /** A task already made from it. */
    val taskId: String? = null
) {
    val hasOwner: Boolean get() = ownerSpeakerId != null || !ownerName.isNullOrBlank()
    /** An unassigned action counts as the person's own: nothing from a meeting falls between lists. */
    val isMine: Boolean get() = ownerIsSelf || !hasOwner
}

internal inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
    name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: fallback

internal fun idList(json: String?): List<String> = runCatching {
    val a = JSONArray(json ?: "[]"); (0 until a.length()).map { a.getString(it) }
}.getOrDefault(emptyList())

internal fun jsonList(values: List<String>): String = JSONArray(values.distinct()).toString()
