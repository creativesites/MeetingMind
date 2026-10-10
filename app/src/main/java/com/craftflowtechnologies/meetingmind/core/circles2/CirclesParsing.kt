package com.craftflowtechnologies.meetingmind.core.circles2

import com.google.firebase.Timestamp
import java.util.Date

/** Firestore maps to models. Pure and forgiving: a malformed doc becomes null or a default, never a crash. */
object CirclesParsing {
    fun millis(v: Any?): Long = when (v) {
        is Timestamp -> v.toDate().time
        is Date -> v.time
        is Number -> v.toLong()
        else -> 0L
    }

    private fun str(m: Map<String, Any?>, k: String): String? = m[k] as? String
    private fun int(m: Map<String, Any?>, k: String): Int = (m[k] as? Number)?.toInt() ?: 0

    @Suppress("UNCHECKED_CAST")
    private fun map(v: Any?): Map<String, Any?> = (v as? Map<String, Any?>).orEmpty()

    @Suppress("UNCHECKED_CAST")
    private fun strings(v: Any?): List<String> = (v as? List<Any?>).orEmpty().filterIsInstance<String>()

    fun settings(m: Map<String, Any?>, fallbackTemplate: String?): CircleSettings {
        val types = strings(m["allowedTypes"]).mapNotNull { PostType.from(it) }.ifEmpty { CircleTemplate.byId(fallbackTemplate).types }
        val reactions = strings(m["reactions"]).mapNotNull { ReactionKind.from(it) }.ifEmpty { ReactionKind.entries }
        return CircleSettings(
            allowedTypes = types,
            whoCanInvite = WhoCanInvite.from(str(m, "whoCanInvite")),
            prayerApproval = m["prayerApproval"] as? Boolean ?: true,
            reactions = reactions,
            guidelines = str(m, "guidelines").orEmpty()
        )
    }

    fun circle(id: String, d: Map<String, Any?>?): Circle? {
        if (d == null || d["closed"] == true) return null
        val name = str(d, "name") ?: return null
        val template = str(d, "template") ?: "custom"
        return Circle(id, name, template, str(d, "vocab").orEmpty(), settings(map(d["settings"]), template), int(d, "memberCount"), str(d, "ownerUid").orEmpty())
    }

    fun member(uid: String, d: Map<String, Any?>): Member =
        Member(uid, str(d, "displayName")?.ifBlank { null } ?: "Member", Role.from(str(d, "role")), d["muted"] == true, millis(d["joinedAt"]))

    private fun counts(m: Map<String, Any?>): PostCounts {
        val r = map(m["reactions"])
        return PostCounts(
            prayed = int(m, "prayed").coerceAtLeast(0), comments = int(m, "comments").coerceAtLeast(0), updates = int(m, "updates").coerceAtLeast(0),
            reactions = ReactionKind.entries.mapNotNull { k -> (r[k.wire] as? Number)?.toInt()?.takeIf { it > 0 }?.let { k to it } }.toMap()
        )
    }

    fun post(id: String, d: Map<String, Any?>): Post? {
        val type = PostType.from(str(d, "type")) ?: return null
        val anonymous = d["anonymous"] == true
        return Post(
            id = id, type = type, body = str(d, "body").orEmpty(), verseRef = str(d, "verseRef")?.ifBlank { null },
            anonymous = anonymous, answered = str(d, "status") == "answered", deleted = d["deleted"] == true,
            // Defence in depth: even if a bad doc carried an author, an anonymous post never shows one.
            authorUid = if (anonymous) null else str(d, "authorUid"), authorName = if (anonymous) null else str(d, "authorName"),
            counts = counts(map(d["counts"])), createdAt = millis(d["createdAt"]), editedAt = d["editedAt"]?.let { millis(it) }?.takeIf { it > 0 }
        )
    }

    fun pending(id: String, d: Map<String, Any?>): PendingPost? {
        val type = PostType.from(str(d, "type")) ?: return null
        return PendingPost(id, type, str(d, "body").orEmpty(), str(d, "verseRef")?.ifBlank { null }, d["anonymous"] == true, millis(d["createdAt"]))
    }

    fun update(id: String, d: Map<String, Any?>) = PostUpdate(id, str(d, "body").orEmpty(), millis(d["createdAt"]))

    fun comment(id: String, d: Map<String, Any?>): Comment? {
        val uid = str(d, "authorUid")
        val requester = uid == null && d["author"] == true
        if (uid == null && !requester) return null
        val name = if (requester) "Requester" else str(d, "displayName")?.ifBlank { null } ?: "Member"
        return Comment(id, uid, name, str(d, "body").orEmpty(), str(d, "parentId"), millis(d["createdAt"]), byRequester = requester)
    }

    fun message(id: String, d: Map<String, Any?>): ChatMessage? {
        val uid = str(d, "authorUid") ?: return null
        val c = map(d["card"])
        val card = if (c.isNotEmpty()) CardPayload(str(c, "templateId").orEmpty(), str(c, "text").orEmpty(), str(c, "mood"), str(c, "verseRef")?.ifBlank { null }, str(c, "verseText")?.ifBlank { null }) else null
        return ChatMessage(
            id = id, authorUid = uid, authorName = str(d, "displayName")?.ifBlank { null } ?: "Member", kind = MessageKind.from(str(d, "kind")),
            text = str(d, "text").orEmpty(), replyTo = str(d, "replyTo"), card = card, pollId = str(d, "pollId"), chainId = str(d, "chainId"),
            celebration = str(d, "celebration"), companion = str(d, "companion"), createdAt = millis(d["createdAt"]),
            editedAt = d["editedAt"]?.let { millis(it) }?.takeIf { it > 0 }, deleted = d["deleted"] == true
        )
    }

    fun poll(id: String, d: Map<String, Any?>): Poll? {
        @Suppress("UNCHECKED_CAST")
        val opts = (d["options"] as? List<Any?>).orEmpty().mapNotNull { o -> map(o).let { m -> str(m, "id")?.let { PollOption(it, str(m, "text").orEmpty()) } } }
        if (opts.isEmpty()) return null
        return Poll(id, str(d, "question").orEmpty(), opts, d["multi"] == true, d["closed"] == true, str(d, "createdBy").orEmpty())
    }

    fun votes(docs: List<Pair<String, Map<String, Any?>>>): Map<String, List<String>> =
        docs.associate { (uid, d) -> uid to strings(d["choices"]).distinct() }.filterValues { it.isNotEmpty() }

    fun chain(id: String, d: Map<String, Any?>): Chain? {
        val title = str(d, "title") ?: return null
        return Chain(id, title, str(d, "postId"), millis(d["startsAt"]), millis(d["endsAt"]), int(d, "hours").takeIf { it > 0 } ?: 24, str(d, "createdByName").orEmpty())
    }

    fun slot(hourId: String, d: Map<String, Any?>): Slot? {
        val hour = hourId.toIntOrNull() ?: return null
        val uid = str(d, "authorUid") ?: return null
        return Slot(hour, uid, str(d, "displayName")?.ifBlank { null } ?: "Member")
    }

    /** Groups raw reaction docs (uid to emoji) into chips; most used first, order stable by the allowed list. */
    fun reactionChips(byUid: Map<String, String>, myUid: String): List<MessageReaction> =
        byUid.values.groupingBy { it }.eachCount().entries
            .sortedWith(compareBy({ CHAT_EMOJI.indexOf(it.key).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it.key }))
            .map { (emoji, n) -> MessageReaction(emoji, n, byUid[myUid] == emoji) }
}
