package com.craftflowtechnologies.meetingmind.core.work

import androidx.room.withTransaction
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.NotePersonCrossRef
import com.craftflowtechnologies.meetingmind.core.database.PersonEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.Locale
import java.util.UUID

/**
 * People and organisations at work, on the app's one `people` table (docs/PLAN_PROFESSIONAL.md
 * §5.1). Built from MeetingMind's own history — named speakers, calendar guests, names typed as
 * owners — never from the phone's contacts. Renaming or merging carries the name into every
 * recording the person spoke in (§5.5).
 */
class WorkPeople(private val database: MeetMindDatabase) {
    private val people = database.peopleDao()
    private val work = database.workDao()

    fun observePeople(): Flow<List<WorkPerson>> = work.observeWorkPeople(PersonKind.PERSON.name, WorkTypeNames).map { l -> l.map { it.toWork() } }
    fun observeOrganisations(): Flow<List<WorkPerson>> = work.observeWorkPeople(PersonKind.ORG.name, WorkTypeNames).map { l -> l.map { it.toWork() } }
    fun observe(id: String): Flow<WorkPerson?> = work.observePerson(id).map { it?.takeIf { p -> p.deletedAt == null }?.toWork() }
    fun observeMembers(orgId: String): Flow<List<WorkPerson>> = work.observeMembers(orgId).map { l -> l.map { it.toWork() } }

    suspend fun get(id: String): WorkPerson? = io { people.getById(id)?.takeIf { it.deletedAt == null }?.toWork() }
    suspend fun all(): List<WorkPerson> = io { work.allPeople().map { it.toWork() } }

    /** The app's own user, made from their display name the first time it's needed. */
    suspend fun self(displayName: String?): WorkPerson = io {
        val name = displayName?.trim()?.takeIf { it.isNotEmpty() }
        work.self()?.let { existing ->
            if (name != null && name != existing.name) {
                val updated = existing.copy(name = name, aliasesJson = addAlias(existing.aliasesJson, existing.name), updatedAt = now())
                people.upsert(updated); return@io updated.toWork()
            }
            return@io existing.toWork()
        }
        // Someone already saved under the user's own name is the user.
        val match = name?.let { n -> work.allPeople().firstOrNull { sameName(it.name, n) } }
        val entity = (match ?: newEntity(PersonKind.PERSON, name ?: "Me")).copy(isSelf = true, space = "WORK", updatedAt = now())
        people.upsert(entity)
        entity.toWork()
    }

    /**
     * The person called [name] (or reached at [email]), made if nobody matches. Email wins over
     * name; names match case-insensitively against names and aliases. Generic labels ("Speaker 2")
     * are never people.
     */
    suspend fun resolve(name: String?, email: String? = null, create: Boolean = true): WorkPerson? = io {
        val cleanName = name?.trim()?.takeIf { it.isNotEmpty() && !SpeakerNames.isGenericLabel(it) && !looksLikeEmail(it) }
        val cleanEmail = email?.trim()?.lowercase(Locale.ROOT)?.takeIf { looksLikeEmail(it) }
            ?: name?.trim()?.lowercase(Locale.ROOT)?.takeIf { looksLikeEmail(it) }
        if (cleanName == null && cleanEmail == null) return@io null
        val everyone = work.allPeople().filter { it.kind == PersonKind.PERSON.name }
        val match = cleanEmail?.let { e -> everyone.firstOrNull { e in idList(it.emailsJson) } }
            ?: cleanName?.let { n -> everyone.firstOrNull { p -> sameName(p.name, n) || idList(p.aliasesJson).any { sameName(it, n) } } }
        if (match != null) {
            var updated = match
            if (cleanEmail != null && cleanEmail !in idList(match.emailsJson)) updated = updated.copy(emailsJson = jsonList(idList(match.emailsJson) + cleanEmail))
            if (cleanName != null && looksLikeEmail(match.name)) updated = updated.copy(name = cleanName)
            if (updated.space == null) updated = updated.copy(space = "WORK")
            if (updated != match) people.upsert(updated.copy(updatedAt = now()))
            return@io updated.toWork()
        }
        if (!create) return@io null
        val displayName = cleanName ?: cleanEmail!!.substringBefore('@').split('.', '_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
        var entity = newEntity(PersonKind.PERSON, displayName)
        if (cleanEmail != null) {
            entity = entity.copy(emailsJson = jsonList(listOf(cleanEmail)))
            organisationForEmail(cleanEmail)?.let { entity = entity.copy(orgId = it.id) }
        }
        people.upsert(entity)
        entity.toWork()
    }

    /** The organisation for a work email's domain ("sarah@acme.com" → Acme). Free mail has none. */
    suspend fun organisationForEmail(email: String): WorkPerson? = io {
        val domain = email.substringAfter('@', "").lowercase(Locale.ROOT).takeIf { it.contains('.') } ?: return@io null
        if (domain in FREE_MAIL || FREE_MAIL.any { domain.endsWith(".$it") }) return@io null
        val orgs = work.allPeople().filter { it.kind == PersonKind.ORG.name }
        orgs.firstOrNull { domain in idList(it.emailsJson) }?.let { return@io it.toWork() }
        val label = domain.substringBeforeLast('.').substringAfterLast('.').replaceFirstChar(Char::uppercase)
        val org = newEntity(PersonKind.ORG, label).copy(emailsJson = jsonList(listOf(domain)))
        people.upsert(org)
        org.toWork()
    }

    suspend fun createPerson(name: String, role: String? = null, email: String? = null, phone: String? = null, orgName: String? = null): WorkPerson = io {
        val p = resolve(name, email) ?: newEntity(PersonKind.PERSON, name.trim()).also { people.upsert(it) }.toWork()
        val org = orgName?.trim()?.takeIf { it.isNotEmpty() }?.let { createOrganisation(it) }
        val e = people.getById(p.id)!!
        people.upsert(
            e.copy(
                relationship = role?.trim()?.takeIf { it.isNotEmpty() } ?: e.relationship,
                phonesJson = phone?.let(::normalisePhone)?.let { jsonList(idList(e.phonesJson) + it) } ?: e.phonesJson,
                orgId = org?.id ?: e.orgId, updatedAt = now()
            )
        )
        people.getById(p.id)!!.toWork()
    }

    suspend fun createOrganisation(name: String): WorkPerson = io {
        work.allPeople().firstOrNull { it.kind == PersonKind.ORG.name && sameName(it.name, name) }?.let { return@io it.toWork() }
        newEntity(PersonKind.ORG, name.trim()).also { people.upsert(it) }.toWork()
    }

    /** Links a named speaker to a person and records that they were in the recording's note. */
    suspend fun linkSpeaker(meetingId: String, speakerId: String, name: String): WorkPerson? = io {
        val person = resolve(name) ?: run { work.linkSpeaker(speakerId, null); return@io null }
        work.linkSpeaker(speakerId, person.id)
        val meeting = database.meetingDao().getMeetingById(meetingId)
        meeting?.noteId?.let { addToNote(it, person.id) }
        touch(person.id, meeting?.createdAt)
        person
    }

    suspend fun addToNote(noteId: String, personId: String) = io { runCatching { people.link(NotePersonCrossRef(noteId, personId)) } }

    suspend fun update(person: WorkPerson) = io {
        val existing = people.getById(person.id) ?: return@io
        if (existing.name != person.name) rename(person.id, person.name)
        val fresh = people.getById(person.id) ?: return@io
        people.upsert(
            fresh.copy(
                relationship = person.role, notes = person.notes,
                emailsJson = jsonList(person.emails), phonesJson = jsonList(person.phones),
                orgId = person.orgId, preferredChannel = person.preferredChannel?.name,
                confidential = person.confidential, updatedAt = now()
            )
        )
    }

    /** Remembers the channel that worked, so next time it's offered first (§6.5). */
    suspend fun rememberChannel(personId: String, channel: Channel) = io {
        people.getById(personId)?.let { people.upsert(it.copy(preferredChannel = channel.name, updatedAt = now())) }
    }

    suspend fun addContact(personId: String, email: String?, phone: String?) = io {
        val p = people.getById(personId) ?: return@io
        var updated = p
        email?.trim()?.lowercase(Locale.ROOT)?.takeIf { looksLikeEmail(it) }?.let { updated = updated.copy(emailsJson = jsonList(idList(updated.emailsJson) + it)) }
        phone?.let(::normalisePhone)?.let { updated = updated.copy(phonesJson = jsonList(idList(updated.phonesJson) + it)) }
        if (updated != p) people.upsert(updated.copy(updatedAt = now()))
    }

    /**
     * Renames a person, and with them every speaker linked to them in every recording: the
     * transcript label, the summary, findings, tasks and notes all follow.
     */
    suspend fun rename(personId: String, newName: String) = io {
        val name = newName.trim().takeIf { it.isNotEmpty() } ?: return@io
        val p = people.getById(personId) ?: return@io
        if (p.name == name) return@io
        people.upsert(p.copy(name = name, aliasesJson = addAlias(p.aliasesJson, p.name), updatedAt = now()))
        renameLinkedSpeakers(personId, name)
    }

    private suspend fun renameLinkedSpeakers(personId: String, name: String) {
        val speakerDao = database.speakerDao()
        val transcriptDao = database.transcriptDao()
        work.speakersFor(personId).forEach { speaker ->
            val previous = speaker.customName.ifBlank { speaker.originalLabel }
            if (previous == name) return@forEach
            transcriptDao.updateSpeakerName(speaker.meetingId, speaker.id, name)
            speakerDao.updateSpeaker(speaker.copy(customName = name))
            SpeakerNames.propagate(database, speaker.meetingId, previous, name, speaker.id)
            if (speaker.originalLabel != previous && SpeakerNames.isGenericLabel(speaker.originalLabel)) {
                SpeakerNames.propagate(database, speaker.meetingId, speaker.originalLabel, name, speaker.id)
            }
        }
    }

    /** Two records that are the same person become one. Everything linked to [fromId] moves. */
    suspend fun merge(fromId: String, intoId: String) = io {
        if (fromId == intoId) return@io
        val from = people.getById(fromId) ?: return@io
        val into = people.getById(intoId) ?: return@io
        database.withTransaction {
            work.moveSpeakers(fromId, intoId)
            work.moveTasks(fromId, intoId)
            work.moveMembers(fromId, intoId)
            work.copyNoteLinks(fromId, intoId)
            database.itemDao().let { it.moveOwner(fromId, intoId); it.moveCounterparty(fromId, intoId); it.moveLinks(fromId, intoId); it.moveOrg(fromId, intoId) }
            people.upsert(
                into.copy(
                    aliasesJson = jsonList((idList(into.aliasesJson) + idList(from.aliasesJson) + from.name).filter { !sameName(it, into.name) }),
                    emailsJson = jsonList(idList(into.emailsJson) + idList(from.emailsJson)),
                    phonesJson = jsonList(idList(into.phonesJson) + idList(from.phonesJson)),
                    orgId = into.orgId ?: from.orgId,
                    relationship = into.relationship ?: from.relationship,
                    isSelf = into.isSelf || from.isSelf,
                    lastSeenAt = listOfNotNull(into.lastSeenAt, from.lastSeenAt).maxOrNull(),
                    space = into.space ?: from.space,
                    updatedAt = now()
                )
            )
            people.setDeleted(fromId, now())
        }
        renameLinkedSpeakers(intoId, into.name)
    }

    suspend fun delete(personId: String) = io {
        work.speakersFor(personId).forEach { work.linkSpeaker(it.id, null) }
        people.setDeleted(personId, now())
    }

    /**
     * Pairs that look like the same person: the same name once spaces, case and dots are ignored,
     * a shared email, or one being the other's first name. Shown as "Same person?".
     */
    suspend fun likelyDuplicates(): List<Pair<WorkPerson, WorkPerson>> = io {
        val list = work.allPeople().filter { it.kind == PersonKind.PERSON.name }.map { it.toWork() }
        val out = mutableListOf<Pair<WorkPerson, WorkPerson>>()
        for (i in list.indices) for (j in i + 1 until list.size) {
            val a = list[i]; val b = list[j]
            val sameKey = key(a.name) == key(b.name)
            val sharedEmail = a.emails.any { it in b.emails }
            val firstName = key(a.firstName) == key(b.firstName) && (a.name.trim().contains(' ') xor b.name.trim().contains(' '))
            if (sameKey || sharedEmail || firstName) out += a to b
        }
        out
    }

    /**
     * Runs once after upgrade: named speakers and calendar guests already in the database become
     * People, so the list isn't empty on the first day.
     */
    suspend fun backfillFromHistory() = io {
        database.workDao().allMeetings().forEach { m ->
            database.speakerDao().getSpeakersForMeetingDirect(m.id).forEach { s ->
                val name = s.customName.ifBlank { s.originalLabel }
                if (s.personId == null && !SpeakerNames.isGenericLabel(name)) linkSpeaker(m.id, s.id, name)
            }
        }
        database.noteDao().getAll().forEach { note ->
            val meta = runCatching { org.json.JSONObject(note.metadataJson) }.getOrNull() ?: return@forEach
            val emails = runCatching { org.json.JSONObject(meta.optString(ATTENDEE_EMAILS, "{}")) }.getOrNull()
            meta.optString("participants").split(", ").map { it.trim() }.filter { it.isNotEmpty() }.forEach { n ->
                resolve(n, emails?.optString(n)?.takeIf { it.isNotBlank() })?.let { p ->
                    addToNote(note.id, p.id)
                    touch(p.id, note.eventDate ?: note.createdAt)
                }
            }
        }
    }

    private suspend fun touch(personId: String, at: Long?) {
        val p = people.getById(personId) ?: return
        val seen = at ?: now()
        if ((p.lastSeenAt ?: 0) < seen) people.upsert(p.copy(lastSeenAt = seen))
    }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    companion object {
        const val ATTENDEE_EMAILS = "attendeeEmails"

        private val FREE_MAIL = setOf(
            "gmail.com", "googlemail.com", "yahoo.com", "yahoo.co.uk", "hotmail.com", "outlook.com", "live.com",
            "msn.com", "icloud.com", "me.com", "aol.com", "proton.me", "protonmail.com", "gmx.com", "mail.com",
            "yandex.com", "zoho.com"
        )

        fun looksLikeEmail(s: String): Boolean = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(s.trim())

        fun normalisePhone(raw: String): String? = raw.filter { it.isDigit() || it == '+' }.takeIf { it.count(Char::isDigit) >= 7 }

        internal fun key(name: String) = name.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"), "")
        internal fun sameName(a: String, b: String) = key(a).isNotEmpty() && key(a) == key(b)

        private fun now() = System.currentTimeMillis()

        private fun addAlias(json: String, alias: String): String =
            if (SpeakerNames.isGenericLabel(alias) || looksLikeEmail(alias)) json else jsonList(idList(json) + alias)

        private fun newEntity(kind: PersonKind, name: String) = PersonEntity(
            id = "person_${UUID.randomUUID()}", name = name, relationship = null, notes = "",
            createdAt = now(), updatedAt = now(), kind = kind.name, space = "WORK"
        )

        fun PersonEntity.toWork() = WorkPerson(
            id = id, kind = enumOr(kind, PersonKind.PERSON), name = name, role = relationship, aliases = idList(aliasesJson),
            emails = idList(emailsJson), phones = idList(phonesJson), orgId = orgId,
            preferredChannel = preferredChannel?.let { enumOr<Channel>(it, Channel.SHARE) }, isSelf = isSelf,
            notes = notes, confidential = confidential, lastSeenAt = lastSeenAt
        )
    }
}

