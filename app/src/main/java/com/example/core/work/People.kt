package com.example.core.work

import androidx.room.withTransaction
import com.example.core.database.MeetMindDatabase
import com.example.core.database.NotePersonCrossRef
import com.example.core.database.PersonEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.Locale
import java.util.UUID

enum class PersonKind { PERSON, ORG }

data class Person(
    val id: String,
    val kind: PersonKind,
    val name: String,
    val aliases: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val phones: List<String> = emptyList(),
    val orgId: String? = null,
    val role: String? = null,
    val preferredChannel: Channel? = null,
    val isSelf: Boolean = false,
    val notes: String? = null,
    val confidential: Boolean = false,
    val lastSeenAt: Long? = null
) {
    val initials: String get() = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
}

/**
 * People and organisations, built from MeetingMind's own history (PLAN_PROFESSIONAL.md §5.1):
 * named speakers, calendar attendees and names typed as owners. The phone's contacts are never read.
 *
 * Renaming or merging here carries the name into every recording the person spoke in (§5.5).
 */
class PeopleRepository(private val database: MeetMindDatabase) {
    private val dao = database.peopleDao()

    fun observePeople(): Flow<List<Person>> = dao.observeByKind(PersonKind.PERSON.name).map { list -> list.map { it.toDomain() } }
    fun observeOrganisations(): Flow<List<Person>> = dao.observeByKind(PersonKind.ORG.name).map { list -> list.map { it.toDomain() } }
    fun observe(id: String): Flow<Person?> = dao.observeById(id).map { it?.toDomain() }
    fun observeMembers(orgId: String): Flow<List<Person>> = dao.observeMembers(orgId).map { list -> list.map { it.toDomain() } }
    fun observeNoteIds(personId: String): Flow<List<String>> = dao.observeNoteIdsFor(personId)

    suspend fun get(id: String): Person? = withContext(Dispatchers.IO) { dao.getById(id)?.toDomain() }
    suspend fun all(): List<Person> = withContext(Dispatchers.IO) { dao.getAll().map { it.toDomain() } }

    /** The app's own user, created from their display name the first time it's needed. */
    suspend fun self(displayName: String?): Person = withContext(Dispatchers.IO) {
        dao.getSelf()?.let { existing ->
            val name = displayName?.trim()?.takeIf { it.isNotEmpty() }
            if (name != null && name != existing.name) {
                val updated = existing.copy(name = name, aliasesJson = addAlias(existing.aliasesJson, existing.name), updatedAt = now())
                dao.upsert(updated); return@withContext updated.toDomain()
            }
            return@withContext existing.toDomain()
        }
        val entity = newEntity(PersonKind.PERSON, displayName?.trim()?.ifEmpty { null } ?: "Me").copy(isSelf = true)
        dao.upsert(entity)
        entity.toDomain()
    }

    /**
     * The person called [name] (or reached at [email]), made if nobody matches. Email wins over
     * name; names match case-insensitively against names and aliases. Generic labels ("Speaker 2")
     * are never people.
     */
    suspend fun resolve(name: String?, email: String? = null, create: Boolean = true): Person? = withContext(Dispatchers.IO) {
        val cleanName = name?.trim()?.takeIf { it.isNotEmpty() && !SpeakerNames.isGenericLabel(it) && !looksLikeEmail(it) }
        val cleanEmail = email?.trim()?.lowercase(Locale.ROOT)?.takeIf { looksLikeEmail(it) }
            ?: name?.trim()?.lowercase(Locale.ROOT)?.takeIf { looksLikeEmail(it) }
        if (cleanName == null && cleanEmail == null) return@withContext null
        val everyone = dao.getAll().filter { it.kind == PersonKind.PERSON.name }
        val match = cleanEmail?.let { e -> everyone.firstOrNull { e in strings(it.emailsJson) } }
            ?: cleanName?.let { n -> everyone.firstOrNull { p -> sameName(p.name, n) || strings(p.aliasesJson).any { sameName(it, n) } } }
        if (match != null) {
            var updated = match
            if (cleanEmail != null && cleanEmail !in strings(match.emailsJson)) updated = updated.copy(emailsJson = add(match.emailsJson, cleanEmail))
            // A real name learned later replaces an address used as a name.
            if (cleanName != null && looksLikeEmail(match.name)) updated = updated.copy(name = cleanName)
            if (updated != match) dao.upsert(updated.copy(updatedAt = now()))
            return@withContext updated.toDomain()
        }
        if (!create) return@withContext null
        val displayName = cleanName ?: cleanEmail!!.substringBefore('@').split('.', '_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
        var entity = newEntity(PersonKind.PERSON, displayName)
        if (cleanEmail != null) {
            entity = entity.copy(emailsJson = add("[]", cleanEmail))
            organisationForEmail(cleanEmail)?.let { entity = entity.copy(orgId = it.id) }
        }
        dao.upsert(entity)
        entity.toDomain()
    }

    /** The organisation for a work email's domain ("sarah@acme.com" → Acme). Free mail has none. */
    suspend fun organisationForEmail(email: String): Person? = withContext(Dispatchers.IO) {
        val domain = email.substringAfter('@', "").lowercase(Locale.ROOT).takeIf { it.contains('.') } ?: return@withContext null
        if (domain in FREE_MAIL || FREE_MAIL.any { domain.endsWith(".$it") }) return@withContext null
        val orgs = dao.getAll().filter { it.kind == PersonKind.ORG.name }
        orgs.firstOrNull { domain in strings(it.emailsJson) }?.let { return@withContext it.toDomain() }
        val label = domain.substringBeforeLast('.').substringAfterLast('.').replaceFirstChar(Char::uppercase)
        val org = newEntity(PersonKind.ORG, label).copy(emailsJson = add("[]", domain))
        dao.upsert(org)
        org.toDomain()
    }

    suspend fun createOrganisation(name: String): Person = withContext(Dispatchers.IO) {
        dao.getAll().firstOrNull { it.kind == PersonKind.ORG.name && sameName(it.name, name) }?.let { return@withContext it.toDomain() }
        newEntity(PersonKind.ORG, name.trim()).also { dao.upsert(it) }.toDomain()
    }

    /** Links a named speaker to a person and records that they spoke in the recording's note. */
    suspend fun linkSpeaker(meetingId: String, speakerId: String, name: String): Person? = withContext(Dispatchers.IO) {
        val person = resolve(name) ?: run { dao.linkSpeaker(speakerId, null); return@withContext null }
        dao.linkSpeaker(speakerId, person.id)
        val meeting = database.meetingDao().getMeetingById(meetingId)
        meeting?.noteId?.let { addToNote(it, person.id, "SPEAKER") }
        touch(person.id, meeting?.createdAt)
        person
    }

    suspend fun addToNote(noteId: String, personId: String, role: String) = withContext(Dispatchers.IO) {
        runCatching { dao.addNotePerson(NotePersonCrossRef(noteId, personId, role, now())) }
    }

    suspend fun update(person: Person) = withContext(Dispatchers.IO) {
        val existing = dao.getById(person.id) ?: return@withContext
        if (existing.name != person.name) rename(person.id, person.name)
        val fresh = dao.getById(person.id) ?: return@withContext
        dao.upsert(
            fresh.copy(
                emailsJson = JSONArray(person.emails.distinct()).toString(),
                phonesJson = JSONArray(person.phones.distinct()).toString(),
                orgId = person.orgId, role = person.role, preferredChannel = person.preferredChannel?.name,
                notes = person.notes, confidential = person.confidential, updatedAt = now()
            )
        )
    }

    /** Remembers the channel that worked, so next time it's offered first (§6.5). */
    suspend fun rememberChannel(personId: String, channel: Channel) = withContext(Dispatchers.IO) {
        dao.getById(personId)?.let { dao.upsert(it.copy(preferredChannel = channel.name, updatedAt = now())) }
    }

    suspend fun addContact(personId: String, email: String?, phone: String?) = withContext(Dispatchers.IO) {
        val p = dao.getById(personId) ?: return@withContext
        var updated = p
        email?.trim()?.lowercase(Locale.ROOT)?.takeIf { looksLikeEmail(it) }?.let { updated = updated.copy(emailsJson = add(updated.emailsJson, it)) }
        phone?.let(::normalisePhone)?.let { updated = updated.copy(phonesJson = add(updated.phonesJson, it)) }
        if (updated != p) dao.upsert(updated.copy(updatedAt = now()))
    }

    /**
     * Renames a person, and with them every speaker linked to them in every recording: the
     * transcript label, the summary, items and notes all follow.
     */
    suspend fun rename(personId: String, newName: String) = withContext(Dispatchers.IO) {
        val name = newName.trim().takeIf { it.isNotEmpty() } ?: return@withContext
        val p = dao.getById(personId) ?: return@withContext
        if (p.name == name) return@withContext
        dao.upsert(p.copy(name = name, aliasesJson = addAlias(p.aliasesJson, p.name), updatedAt = now()))
        renameLinkedSpeakers(personId, name)
    }

    private suspend fun renameLinkedSpeakers(personId: String, name: String) {
        val speakerDao = database.speakerDao()
        val transcriptDao = database.transcriptDao()
        dao.getSpeakersFor(personId).forEach { speaker ->
            val previous = speaker.customName.ifBlank { speaker.originalLabel }
            if (previous == name) return@forEach
            transcriptDao.updateSpeakerName(speaker.meetingId, speaker.id, name)
            speakerDao.updateSpeaker(speaker.copy(customName = name))
            SpeakerNames.propagate(database, speaker.meetingId, previous, name)
            if (speaker.originalLabel != previous && SpeakerNames.isGenericLabel(speaker.originalLabel)) {
                SpeakerNames.propagate(database, speaker.meetingId, speaker.originalLabel, name)
            }
        }
    }

    /** Two records that are the same person become one. Everything linked to [fromId] moves. */
    suspend fun merge(fromId: String, intoId: String) = withContext(Dispatchers.IO) {
        if (fromId == intoId) return@withContext
        val from = dao.getById(fromId) ?: return@withContext
        val into = dao.getById(intoId) ?: return@withContext
        database.withTransaction {
            dao.moveSpeakers(fromId, intoId)
            dao.moveItems(fromId, intoId)
            dao.moveMembers(fromId, intoId)
            dao.copyNoteLinks(fromId, intoId)
            dao.upsert(
                into.copy(
                    aliasesJson = (strings(into.aliasesJson) + strings(from.aliasesJson) + from.name).distinct()
                        .filter { !sameName(it, into.name) }.let { JSONArray(it).toString() },
                    emailsJson = JSONArray((strings(into.emailsJson) + strings(from.emailsJson)).distinct()).toString(),
                    phonesJson = JSONArray((strings(into.phonesJson) + strings(from.phonesJson)).distinct()).toString(),
                    orgId = into.orgId ?: from.orgId,
                    role = into.role ?: from.role,
                    isSelf = into.isSelf || from.isSelf,
                    lastSeenAt = listOfNotNull(into.lastSeenAt, from.lastSeenAt).maxOrNull(),
                    updatedAt = now()
                )
            )
            dao.deleteById(fromId)
        }
        renameLinkedSpeakers(intoId, into.name)
    }

    suspend fun delete(personId: String) = withContext(Dispatchers.IO) {
        dao.getSpeakersFor(personId).forEach { dao.linkSpeaker(it.id, null) }
        dao.deleteById(personId)
    }

    /**
     * Pairs that look like the same person: the same name once spaces, case and dots are ignored,
     * or one name being the other's first name with a shared email domain. Shown as "Same person?".
     */
    suspend fun likelyDuplicates(): List<Pair<Person, Person>> = withContext(Dispatchers.IO) {
        val people = dao.getAll().filter { it.kind == PersonKind.PERSON.name }.map { it.toDomain() }
        val out = mutableListOf<Pair<Person, Person>>()
        for (i in people.indices) for (j in i + 1 until people.size) {
            val a = people[i]; val b = people[j]
            val sameKey = key(a.name) == key(b.name)
            val sharedEmail = a.emails.any { it in b.emails }
            val firstName = key(a.name.substringBefore(' ')) == key(b.name.substringBefore(' ')) &&
                (a.name.trim().contains(' ') xor b.name.trim().contains(' '))
            if (sameKey || sharedEmail || firstName) out += a to b
        }
        out
    }

    /**
     * Runs once after upgrade: named speakers and calendar attendees already in the database become
     * People, so the list isn't empty on the first day.
     */
    suspend fun backfillFromHistory() = withContext(Dispatchers.IO) {
        val meetings = database.meetingDao().getAllMeetingsDirect()
        meetings.forEach { m ->
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
                    addToNote(note.id, p.id, "ATTENDEE")
                    touch(p.id, note.eventDate ?: note.createdAt)
                }
            }
        }
    }

    private suspend fun touch(personId: String, at: Long?) {
        val p = dao.getById(personId) ?: return
        val seen = at ?: now()
        if ((p.lastSeenAt ?: 0) < seen) dao.upsert(p.copy(lastSeenAt = seen))
    }

    companion object {
        const val ATTENDEE_EMAILS = "attendeeEmails"

        private val FREE_MAIL = setOf(
            "gmail.com", "googlemail.com", "yahoo.com", "yahoo.co.uk", "hotmail.com", "outlook.com", "live.com",
            "msn.com", "icloud.com", "me.com", "aol.com", "proton.me", "protonmail.com", "gmx.com", "mail.com",
            "yandex.com", "zoho.com"
        )

        fun looksLikeEmail(s: String): Boolean = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(s.trim())

        fun normalisePhone(raw: String): String? {
            val digits = raw.filter { it.isDigit() || it == '+' }
            return digits.takeIf { it.count(Char::isDigit) >= 7 }
        }

        internal fun key(name: String) = name.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"), "")
        internal fun sameName(a: String, b: String) = key(a).isNotEmpty() && key(a) == key(b)

        private fun now() = System.currentTimeMillis()

        private fun strings(json: String?): List<String> = runCatching {
            val a = JSONArray(json ?: "[]"); (0 until a.length()).map { a.getString(it) }
        }.getOrDefault(emptyList())

        private fun add(json: String, value: String): String = JSONArray((strings(json) + value).distinct()).toString()

        private fun addAlias(json: String, alias: String): String =
            if (SpeakerNames.isGenericLabel(alias) || looksLikeEmail(alias)) json else add(json, alias)

        private fun newEntity(kind: PersonKind, name: String) = PersonEntity(
            id = UUID.randomUUID().toString(), kind = kind.name, name = name, aliasesJson = "[]", emailsJson = "[]",
            phonesJson = "[]", orgId = null, role = null, preferredChannel = null, isSelf = false, notes = null,
            confidential = false, createdAt = now(), updatedAt = now(), lastSeenAt = null
        )

        fun PersonEntity.toDomain() = Person(
            id = id, kind = enumOr(kind, PersonKind.PERSON), name = name, aliases = strings(aliasesJson),
            emails = strings(emailsJson), phones = strings(phonesJson), orgId = orgId, role = role,
            preferredChannel = preferredChannel?.let { enumOr<Channel>(it, Channel.SHARE) }, isSelf = isSelf,
            notes = notes, confidential = confidential, lastSeenAt = lastSeenAt
        )
    }
}
