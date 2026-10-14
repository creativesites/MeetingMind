package com.craftflowtechnologies.meetingmind.core.work

import androidx.room.withTransaction
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.notes.InlineSpan
import com.craftflowtechnologies.meetingmind.core.notes.RichText
import org.json.JSONObject

/**
 * Names are dynamic (docs/PLAN_PROFESSIONAL.md §5.5). When "Speaker 1" becomes "Sarah Chen", every
 * place that shows that person changes at once.
 *
 * Structured references (an item's owner) already resolve by id when they're read. This object
 * handles the rest: prose that processing wrote with the old name in it. Only text derived from the
 * recording is rewritten; the transcript's own words are evidence and the person's own writing is
 * theirs, so neither is touched.
 */
object SpeakerNames {

    private val GENERIC = Regex("(?i)^\\s*(speaker|spk|voice|person)[ _-]?(\\d+|[a-z]|one|two|three|four|five|six|seven|eight|nine|ten)\\s*$")
    private val UNKNOWN = Regex("(?i)^\\s*(unknown( speaker)?|unidentified( speaker)?)\\s*$")
    private val NUMBER_WORDS = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten")

    /** "Speaker 1", "SPEAKER_01", "speaker one", "Speaker A": labels diarization makes, not names. */
    fun isGenericLabel(name: String?): Boolean = name != null && (GENERIC.matches(name) || UNKNOWN.matches(name))

    /**
     * The pattern that finds [old] in prose. A real name is matched as a whole word and
     * case-sensitively, so renaming "Mark" leaves "mark the date" alone. A generic label is matched
     * in any of the forms a model writes it, and never inside a longer label.
     */
    fun patternFor(old: String): Regex? {
        val trimmed = old.trim()
        if (trimmed.isEmpty()) return null
        GENERIC.matchEntire(trimmed)?.let { m ->
            val token = m.groupValues[2].lowercase()
            val n = token.toIntOrNull() ?: NUMBER_WORDS.indexOf(token).takeIf { it >= 0 }
            val forms = if (n != null) listOf("0*$n", NUMBER_WORDS.getOrNull(n)).filterNotNull() else listOf(Regex.escape(token))
            val prefix = Regex.escape(m.groupValues[1])
            return Regex("(?i)(?<![\\p{L}\\p{N}])$prefix[ _-]?(?:${forms.joinToString("|")})(?![\\p{L}\\p{N}])")
        }
        return Regex("(?<![\\p{L}\\p{N}])${Regex.escape(trimmed)}(?![\\p{L}\\p{N}])")
    }

    fun replaceIn(text: String, old: String, new: String): String {
        val pattern = patternFor(old) ?: return text
        if (old.trim() == new.trim()) return text
        return pattern.replace(text) { new }
    }

    /** The same replacement, keeping bold, links and other styles on the right characters. */
    fun replaceIn(rich: RichText, old: String, new: String): RichText {
        val pattern = patternFor(old) ?: return rich
        val matches = pattern.findAll(rich.text).toList()
        if (matches.isEmpty() || old.trim() == new.trim()) return rich
        val out = StringBuilder()
        var cursor = 0
        // (old start, old end, new start, new end) for each replaced stretch.
        val edits = ArrayList<IntArray>(matches.size)
        for (m in matches) {
            out.append(rich.text, cursor, m.range.first)
            val newStart = out.length
            out.append(new)
            edits += intArrayOf(m.range.first, m.range.last + 1, newStart, out.length)
            cursor = m.range.last + 1
        }
        out.append(rich.text, cursor, rich.text.length)
        fun map(pos: Int, isEnd: Boolean): Int {
            var shift = 0
            for (e in edits) {
                if (pos <= e[0]) break
                if (pos < e[1]) return if (isEnd) e[3] else e[2]
                shift = e[3] - e[1]
            }
            return pos + shift
        }
        val spans = rich.spans.map { InlineSpan(map(it.start, false), map(it.end, true), it.style, it.url) }
        return RichText.create(out.toString(), spans)
    }

    /**
     * Rewrites [old] as [new] in everything derived from [meetingId]: its summary, findings, tasks,
     * note, AI tool results and chat answers. [speakerId] also moves the owner name on findings
     * that point at that speaker. Call after the speaker row itself has been renamed.
     */
    suspend fun propagate(database: MeetMindDatabase, meetingId: String, old: String?, new: String, speakerId: String? = null) {
        if (old.isNullOrBlank() || old.trim() == new.trim() || patternFor(old) == null) return
        val now = System.currentTimeMillis()
        database.withTransaction {
            val meetingDao = database.meetingDao()
            val work = database.workDao()
            val meeting = meetingDao.getMeetingById(meetingId) ?: return@withTransaction
            meeting.summaryText?.let { summary ->
                val updated = replaceIn(summary, old, new)
                if (updated != summary) meetingDao.updateMeeting(meeting.copy(summaryText = updated, updatedAt = now))
            }
            fun owner(name: String?, sid: String?) = when {
                speakerId != null && sid == speakerId -> new
                name != null && name.trim() == old.trim() -> new
                else -> name
            }
            database.actionItemDao().getActionItemsForMeetingDirect(meetingId).forEach { a ->
                val text = replaceIn(a.task, old, new); val name = owner(a.assigneeName, a.assigneeSpeakerId)
                if (text != a.task || name != a.assigneeName) database.actionItemDao().updateActionItem(a.copy(task = text, assigneeName = name))
            }
            database.decisionDao().getDecisionsForMeetingDirect(meetingId).forEach { d ->
                val text = replaceIn(d.text, old, new)
                if (text != d.text) work.updateDecision(d.copy(text = text))
            }
            database.questionDao().getQuestionsForMeetingDirect(meetingId).forEach { q ->
                val text = replaceIn(q.text, old, new); val answer = q.answer?.let { replaceIn(it, old, new) }
                if (text != q.text || answer != q.answer) database.questionDao().updateQuestion(q.copy(text = text, answer = answer))
            }
            work.followUpsFor(meetingId).forEach { f ->
                val text = replaceIn(f.description, old, new)
                if (text != f.description) work.updateFollowUp(f.copy(description = text))
            }
            work.tasksForMeeting(meetingId).forEach { t ->
                val title = replaceIn(t.title, old, new); val notes = replaceIn(t.notes, old, new)
                if (title != t.title || notes != t.notes) database.taskDao().upsert(t.copy(title = title, notes = notes, updatedAt = now))
            }
            meeting.noteId?.let { noteId -> propagateInNote(database, noteId, old, new, now) }
            work.aiJobsFor(meetingId).forEach { job ->
                val result = job.resultPayloadJson ?: return@forEach
                val escaped = JSONObject.quote(new).removeSurrounding("\"")
                val updated = replaceIn(result, old, escaped)
                if (updated != result) database.aiJobDao().insertOrUpdate(job.copy(resultPayloadJson = updated, updatedAt = now))
            }
            work.chatFor(meetingId).filter { !it.isUser }.forEach { message ->
                val updated = replaceIn(message.content, old, new)
                if (updated != message.content) work.setChatContent(message.id, updated)
            }
        }
    }

    private suspend fun propagateInNote(database: MeetMindDatabase, noteId: String, old: String, new: String, now: Long) {
        val noteDao = database.noteDao()
        val note = noteDao.getById(noteId) ?: return
        val blocks = noteDao.getBlocks(noteId)
        val changed = blocks.mapNotNull { block ->
            val payload = runCatching { JSONObject(block.payloadJson) }.getOrNull()
            val speaker = payload?.optString("speaker")?.takeIf { it.isNotEmpty() }
            var payloadJson = block.payloadJson
            if (speaker != null && speaker.trim() == old.trim()) payloadJson = payload.put("speaker", new).toString()
            // Prose the person wrote stays theirs; transcript excerpts are evidence.
            val rewriteText = block.source != "USER" && block.source != "TRANSCRIPT"
            val rich = RichText.decode(block.text, block.spans)
            val updated = if (rewriteText) replaceIn(rich, old, new) else rich
            if (updated.text == block.text && payloadJson == block.payloadJson) null
            else block.copy(text = updated.text, spans = updated.encodeSpans(), payloadJson = payloadJson, updatedAt = now)
        }
        val metadata = runCatching { JSONObject(note.metadataJson) }.getOrNull()
        val participants = metadata?.optString("participants")?.takeIf { it.isNotBlank() }
        val newParticipants = participants?.split(", ")?.joinToString(", ") { if (it.trim() == old.trim()) new else it }
        if (changed.isEmpty() && newParticipants == participants) return
        if (changed.isNotEmpty()) noteDao.upsertBlocks(changed)
        val plain = replaceIn(note.plainText, old, new)
        noteDao.upsert(
            note.copy(
                plainText = plain,
                metadataJson = if (newParticipants != participants && metadata != null) metadata.put("participants", newParticipants).toString() else note.metadataJson,
                updatedAt = now
            )
        )
    }
}
