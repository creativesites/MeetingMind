package com.example.core.work

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Hands a composed message to the app that sends it (docs/PLAN_PROFESSIONAL.md §6.6). MeetingMind
 * drafts; the person's own WhatsApp, email or messages app sends. Nothing is sent without them.
 */
object Send {

    /** Returns false when no app on the phone could take it. */
    fun send(context: Context, channel: Channel, message: OutgoingMessage, recipients: List<Person>): Boolean {
        val intents = when (channel) {
            Channel.WHATSAPP -> whatsapp(message, recipients)
            Channel.EMAIL -> listOf(email(message, recipients))
            Channel.SMS -> listOf(sms(message, recipients))
            Channel.SHARE -> emptyList()
        } + share(message)
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: ActivityNotFoundException) {
                // Try the next way: a missing app falls back to the share sheet.
            } catch (_: SecurityException) {
            }
        }
        return false
    }

    private fun whatsapp(message: OutgoingMessage, recipients: List<Person>): List<Intent> {
        val phones = recipients.filter { !it.isSelf }.flatMap { it.phones.take(1) }
        val direct = phones.singleOrNull()?.let { phone ->
            // One person: open their chat with the text in the box.
            val digits = phone.filter { it.isDigit() }
            Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits?text=" + Uri.encode(message.body)))
        }
        val app = listOf("com.whatsapp", "com.whatsapp.w4b").map { pkg ->
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message.body).setPackage(pkg)
        }
        return listOfNotNull(direct) + app
    }

    private fun email(message: OutgoingMessage, recipients: List<Person>): Intent {
        val to = recipients.filter { !it.isSelf }.flatMap { it.emails.take(1) }.toTypedArray()
        return Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            .putExtra(Intent.EXTRA_EMAIL, to)
            .putExtra(Intent.EXTRA_SUBJECT, message.subject)
            .putExtra(Intent.EXTRA_TEXT, message.body)
    }

    private fun sms(message: OutgoingMessage, recipients: List<Person>): Intent {
        val to = recipients.filter { !it.isSelf }.flatMap { it.phones.take(1) }.joinToString(";")
        return Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$to")).putExtra("sms_body", message.body)
    }

    private fun share(message: OutgoingMessage): Intent = Intent.createChooser(
        Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, message.subject)
            .putExtra(Intent.EXTRA_TEXT, message.body),
        "Send follow-up"
    )
}
