package com.example.core.work

import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** A message ready to leave MeetingMind: [subject] only means something for email. */
data class OutgoingMessage(val subject: String, val body: String)

/**
 * Picks the channel a follow-up is offered on first (docs/PLAN_PROFESSIONAL.md §6.5). The other
 * channels always stay one tap away.
 */
object ChannelChooser {
    fun pick(recipients: List<Person>, settings: WorkSettings, whatsappFirstRegion: Boolean = WorkSettings.whatsappFirstRegion()): Channel {
        settings.defaultChannel?.let { return it }
        // 1. What worked with these people before, when they agree.
        recipients.mapNotNull { it.preferredChannel }.distinct().singleOrNull()?.let { return it }
        val anyPhone = recipients.any { it.phones.isNotEmpty() }
        val anyEmail = recipients.any { it.emails.isNotEmpty() }
        // 2. What we can actually reach them on.
        if (recipients.isNotEmpty()) {
            if (anyPhone && !anyEmail) return Channel.WHATSAPP
            if (anyEmail && !anyPhone) {
                // A group with work addresses reads as email even where WhatsApp is common.
                return Channel.EMAIL
            }
        }
        // 3. The region's habit.
        return if (whatsappFirstRegion) Channel.WHATSAPP else Channel.EMAIL
    }
}

/**
 * Writes the follow-up from what was confirmed in the Wrap-up. Deterministic and offline: the
 * words come from the items themselves, never from a model, so nothing can be added that wasn't
 * agreed. One content, rendered per channel (WhatsApp formatting, or an email with a subject).
 */
object FollowUpWriter {

    data class Input(
        val meetingTitle: String,
        val meetingAt: Long,
        val recipients: List<Person>,
        val decisions: List<WorkItem>,
        val myTasks: List<WorkItem>,
        val theirTasks: List<WorkItem>,
        val questions: List<WorkItem>,
        val senderName: String?,
        val settings: WorkSettings,
        val tone: Tone = settings.tone
    )

    fun compose(input: Input, channel: Channel, now: Long = System.currentTimeMillis()): OutgoingMessage {
        val wa = channel == Channel.WHATSAPP || channel == Channel.SMS
        fun bold(s: String) = if (channel == Channel.WHATSAPP) "*$s*" else s
        val tone = input.tone
        val firstNames = input.recipients.filter { !it.isSelf }.map { it.name.trim().substringBefore(' ') }.filter { it.isNotBlank() }.distinct()
        val greeting = when {
            firstNames.isEmpty() -> if (tone == Tone.FORMAL) "Hello," else "Hi all,"
            firstNames.size <= 3 -> (if (tone == Tone.FORMAL) "Dear " else "Hi ") + joinNames(firstNames) + ","
            else -> if (tone == Tone.FORMAL) "Dear all," else "Hi all,"
        }
        val intro = when (tone) {
            Tone.FORMAL -> "Thank you for your time ${whenPhrase(input.meetingAt, now)}. Please find a summary of what we agreed below."
            Tone.FRIENDLY -> "Thanks for your time ${whenPhrase(input.meetingAt, now)}. Here's what we agreed:"
            Tone.BRIEF -> "Recap from ${input.meetingTitle.ifBlank { "our meeting" }}:"
        }
        val lines = mutableListOf(greeting, "", intro)
        fun section(title: String, items: List<String>) {
            if (items.isEmpty()) return
            lines += ""
            lines += bold(title)
            items.forEach { lines += (if (wa) "• " else "- ") + it }
        }
        section(if (tone == Tone.BRIEF) "Decided" else "Decisions", input.decisions.map { it.text.trimEnd('.') })
        section(if (tone == Tone.BRIEF) "I'll do" else "What I'll do", input.myTasks.map { it.text.trimEnd('.') + due(it) })
        section(
            if (tone == Tone.BRIEF) "Over to you" else if (firstNames.size == 1) "What you'll do" else "Next steps from your side",
            input.theirTasks.map { t ->
                val who = t.ownerName?.takeIf { firstNames.size > 1 }?.let { "$it — " }.orEmpty()
                who + t.text.trimEnd('.') + due(t)
            }
        )
        section(if (tone == Tone.BRIEF) "Open" else "Still open", input.questions.map { it.text.trim().let { q -> if (q.endsWith("?")) q else "$q?" } })
        lines += ""
        lines += when (tone) {
            Tone.FORMAL -> "Please let me know if I have missed or misunderstood anything."
            Tone.FRIENDLY -> "Let me know if I missed anything."
            Tone.BRIEF -> null
        } ?: ""
        if (lines.last().isNotEmpty()) lines += ""
        val signOff = input.settings.signOff.trim().ifEmpty { "Thanks" }
        lines += signOff + (input.senderName?.let { if (wa) ", ${it.substringBefore(' ')}" else ",\n$it" } ?: "")
        if (!wa && input.settings.signature.isNotBlank()) lines += input.settings.signature.trim()
        if (input.settings.showFooter) {
            lines += ""
            lines += if (wa) "_Notes by MeetingMind_" else "Notes by MeetingMind"
        }
        val body = lines.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
        val subject = "Follow-up: " + input.meetingTitle.ifBlank { "our meeting" }
        return OutgoingMessage(subject, body)
    }

    /** A short nudge for something someone owes (Waiting on → Nudge). */
    fun nudge(item: WorkItem, senderName: String?, tone: Tone, channel: Channel): OutgoingMessage {
        val first = item.ownerName?.substringBefore(' ')
        val hi = if (tone == Tone.FORMAL) "Dear ${first ?: "all"}," else "Hi${first?.let { " $it" }.orEmpty()},"
        val body = buildString {
            append(hi).append("\n\n")
            append(if (tone == Tone.FORMAL) "I wanted to follow up on " else "Just checking in on ")
            append(item.text.trimEnd('.').replaceFirstChar { it.lowercase(Locale.getDefault()) })
            item.dueText?.let { append(" (").append(it).append(")") }
            append(if (tone == Tone.FORMAL) ". Could you let me know where this stands?" else ". How's it going?")
            senderName?.let { append("\n\n").append(if (tone == Tone.FORMAL) "Kind regards,\n" else "Thanks, ").append(if (tone == Tone.FORMAL) it else it.substringBefore(' ')) }
        }
        return OutgoingMessage("Following up: " + item.text.take(60), body)
    }

    private fun joinNames(names: List<String>) = when (names.size) {
        1 -> names[0]
        2 -> "${names[0]} and ${names[1]}"
        else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
    }

    private fun due(item: WorkItem): String {
        val at = item.dueAt ?: return item.dueText?.let { " ($it)" }.orEmpty()
        return " (by " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(at)) + ")"
    }

    private fun whenPhrase(at: Long, now: Long): String {
        val a = Calendar.getInstance().apply { timeInMillis = at }
        val n = Calendar.getInstance().apply { timeInMillis = now }
        val sameDay = a.get(Calendar.YEAR) == n.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == n.get(Calendar.DAY_OF_YEAR)
        if (sameDay) return if (a.get(Calendar.HOUR_OF_DAY) < 12) "this morning" else "today"
        n.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = a.get(Calendar.YEAR) == n.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == n.get(Calendar.DAY_OF_YEAR)
        return if (yesterday) "yesterday" else "on " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(at))
    }
}
