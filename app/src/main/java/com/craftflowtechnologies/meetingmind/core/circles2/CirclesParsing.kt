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

    fun settings(m: Map<String, Any?>, fallbackTemplate: String?, fallbackSpace: String? = null): CircleSettings {
        val sp = str(m, "space") ?: fallbackSpace ?: if (fallbackTemplate in setOf("project_team", "client_engagement", "exec_board", "department", "custom_work")) "work" else "faith"
        val isWork = sp.equals("work", ignoreCase = true)
        val defaultReactions = if (isWork) listOf(ReactionKind.Ack, ReactionKind.Approved, ReactionKind.Priority, ReactionKind.Reviewing, ReactionKind.Win) else listOf(ReactionKind.Praying, ReactionKind.Amen, ReactionKind.Heart, ReactionKind.Celebrate)
        val types = strings(m["allowedTypes"]).mapNotNull { PostType.from(it) }.ifEmpty { CircleTemplate.byId(fallbackTemplate).types }
        val reactions = strings(m["reactions"]).mapNotNull { ReactionKind.from(it) }.ifEmpty { defaultReactions }
        return CircleSettings(
            allowedTypes = types,
            whoCanInvite = WhoCanInvite.from(str(m, "whoCanInvite")),
            prayerApproval = m["prayerApproval"] as? Boolean ?: !isWork,
            joinApproval = m["joinApproval"] as? Boolean ?: false,
            reactions = reactions,
            guidelines = str(m, "guidelines").orEmpty(),
            space = sp
        )
    }

    fun circle(id: String, d: Map<String, Any?>?): Circle? {
        if (d == null || d["closed"] == true) return null
        val name = str(d, "name") ?: return null
        val template = str(d, "template") ?: "custom"
        val space = str(d, "space") ?: (map(d["settings"])["space"] as? String) ?: if (template in setOf("project_team", "client_engagement", "exec_board", "department", "custom_work")) "work" else "faith"
        return Circle(
            id, name, template, str(d, "vocab").orEmpty(), settings(map(d["settings"]), template, space), int(d, "memberCount"), str(d, "ownerUid").orEmpty(),
            description = str(d, "description").orEmpty(), pinnedPostId = str(d, "pinnedPostId")?.ifBlank { null },
            coverPath = str(d, "coverPath")?.ifBlank { null }, pinnedMessageId = str(d, "pinnedMessageId")?.ifBlank { null },
            space = space
        )
    }

    fun member(uid: String, d: Map<String, Any?>): Member =
        Member(
            uid, str(d, "displayName")?.ifBlank { null } ?: "Member", Role.from(str(d, "role")), d["muted"] == true, millis(d["joinedAt"]),
            str(d, "title")?.ifBlank { null }, millis(d["lastReadAt"]), (d["avatarVersion"] as? Number)?.toLong(),
            external = d["external"] == true
        )

    private fun counts(m: Map<String, Any?>): PostCounts {
        val r = map(m["reactions"])
        return PostCounts(
            prayed = int(m, "prayed").coerceAtLeast(0), comments = int(m, "comments").coerceAtLeast(0), updates = int(m, "updates").coerceAtLeast(0),
            reactions = ReactionKind.entries.mapNotNull { k -> (r[k.wire] as? Number)?.toInt()?.takeIf { it > 0 }?.let { k to it } }.toMap()
        )
    }

    fun postMeta(v: Any?): PostMeta? {
        val m = map(v)
        if (m.isEmpty()) return null
        val tags = strings(m["tags"])
        @Suppress("UNCHECKED_CAST")
        val checklist = (m["checklist"] as? List<*>)?.mapIndexedNotNull { index, item ->
            val im = map(item)
            val text = str(im, "text")?.ifBlank { null } ?: return@mapIndexedNotNull null
            val id = str(im, "id")?.ifBlank { null } ?: "c$index"
            ChecklistItem(id = id, text = text, done = im["done"] == true)
        }.orEmpty()
        val meta = PostMeta(
            sourceKind = str(m, "sourceKind")?.ifBlank { null },
            sourceId = str(m, "sourceId")?.ifBlank { null },
            assigneeUid = str(m, "assigneeUid")?.ifBlank { null },
            assigneeName = str(m, "assigneeName")?.ifBlank { null },
            dueText = str(m, "dueText")?.ifBlank { null },
            dueAt = m["dueAt"]?.let { millis(it) }?.takeIf { it > 0 },
            priority = WorkPriority.from(str(m, "priority")),
            blockedOn = str(m, "blockedOn")?.ifBlank { null },
            decisionOwner = str(m, "decisionOwner")?.ifBlank { null },
            meetingDate = str(m, "meetingDate")?.ifBlank { null },
            tags = tags,
            checklist = checklist,
            decisionStatus = DecisionStatus.from(str(m, "decisionStatus")),
            replacesPostId = str(m, "replacesPostId")?.ifBlank { null },
            replacedByPostId = str(m, "replacedByPostId")?.ifBlank { null }
        )
        return if (meta.isEmpty) null else meta
    }

    fun post(id: String, d: Map<String, Any?>): Post? {
        val type = PostType.from(str(d, "type")) ?: return null
        val anonymous = d["anonymous"] == true
        val attachments = (d["attachments"] as? List<*>)?.mapNotNull { attachment(map(it)) }.orEmpty()
        val deleted = d["deleted"] == true
        // A live post needs words or a photo; a deleted one is kept as a tombstone.
        if (!deleted && str(d, "body").isNullOrBlank() && attachments.isEmpty()) return null
        val status = str(d, "status") ?: if (d["answered"] == true) "answered" else "open"
        val answered = (str(d, "status") == "answered") || status == "answered"
        return Post(
            id = id, type = type, body = str(d, "body").orEmpty(), verseRef = str(d, "verseRef")?.ifBlank { null },
            anonymous = anonymous, answered = answered, deleted = deleted,
            // Defence in depth: even if a bad doc carried an author, an anonymous post never shows one.
            authorUid = if (anonymous) null else str(d, "authorUid"), authorName = if (anonymous) null else str(d, "authorName"),
            counts = counts(map(d["counts"])), createdAt = millis(d["createdAt"]), editedAt = d["editedAt"]?.let { millis(it) }?.takeIf { it > 0 },
            attachments = attachments,
            meta = postMeta(d["meta"]),
            status = status
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
            editedAt = d["editedAt"]?.let { millis(it) }?.takeIf { it > 0 }, deleted = d["deleted"] == true,
            attachment = attachment(map(d["attachment"])), forwardedFrom = str(d, "forwardedFrom")?.ifBlank { null }, mentions = strings(d["mentions"])
        )
    }

    /** An image, voice or file reference. Null when the map has no path. */
    fun attachment(a: Map<String, Any?>): MediaAttachment? {
        val path = str(a, "path") ?: return null
        return MediaAttachment(path, str(a, "mime").orEmpty(), (a["bytes"] as? Number)?.toLong() ?: 0L, (a["durationMs"] as? Number)?.toLong(), str(a, "name"))
    }

    fun typingUser(uid: String, d: Map<String, Any?>): TypingUser =
        TypingUser(uid, str(d, "displayName")?.ifBlank { null } ?: "Someone", millis(d["updatedAt"]))

    fun poll(id: String, d: Map<String, Any?>): Poll? {
        @Suppress("UNCHECKED_CAST")
        val opts = (d["options"] as? List<Any?>).orEmpty().mapNotNull { o -> map(o).let { m -> str(m, "id")?.let { PollOption(it, str(m, "text").orEmpty()) } } }
        if (opts.isEmpty()) return null
        val closesAt = d["closesAt"]?.let { millis(it) }?.takeIf { it > 0 }
        val correctOptionId = str(d, "correctOptionId")?.ifBlank { null }
        val explanation = str(d, "explanation")?.ifBlank { null }
        return Poll(
            id = id,
            question = str(d, "question").orEmpty(),
            options = opts,
            multi = d["multi"] == true,
            closed = d["closed"] == true,
            createdBy = str(d, "createdBy").orEmpty(),
            closesAt = closesAt,
            correctOptionId = correctOptionId,
            explanation = explanation
        )
    }

    fun votes(docs: List<Pair<String, Map<String, Any?>>>): Map<String, List<String>> =
        docs.associate { (uid, d) -> uid to strings(d["choices"]).distinct() }.filterValues { it.isNotEmpty() }

    fun chain(id: String, d: Map<String, Any?>): Chain? {
        val title = str(d, "title") ?: return null
        return Chain(id, title, str(d, "postId"), millis(d["startsAt"]), millis(d["endsAt"]), int(d, "hours").takeIf { it > 0 } ?: 24, str(d, "createdByName").orEmpty())
    }

    fun event(id: String, d: Map<String, Any?>): CircleEvent? {
        val title = str(d, "title") ?: return null
        return CircleEvent(
            id, title, str(d, "description").orEmpty(), str(d, "location").orEmpty(), str(d, "onlineUrl")?.ifBlank { null },
            millis(d["startsAt"]), millis(d["endsAt"]), str(d, "timeZone").orEmpty(), str(d, "status") ?: "scheduled", str(d, "createdByName").orEmpty(),
            bringList = bringList(d["bringList"])
        )
    }

    /** The Table's items. Malformed items (no id or text) are dropped rather than shown blank. */
    fun bringList(v: Any?): List<BringItem> = (v as? List<*>).orEmpty().mapNotNull { item ->
        val m = map(item)
        val itemId = str(m, "id")?.ifBlank { null } ?: return@mapNotNull null
        val text = str(m, "text")?.ifBlank { null } ?: return@mapNotNull null
        BringItem(itemId, text, str(m, "claimedByUid")?.ifBlank { null }, str(m, "claimedByName")?.ifBlank { null })
    }

    /** The preview of an invite code. Null when the Worker's answer has no circle name. */
    fun invitePreview(d: Map<String, Any?>): InvitePreview? {
        val name = str(d, "name")?.ifBlank { null } ?: return null
        return InvitePreview(
            circleId = str(d, "circleId")?.ifBlank { null },
            name = name,
            space = str(d, "space")?.ifBlank { null } ?: "faith",
            template = str(d, "template")?.ifBlank { null },
            memberCount = int(d, "memberCount"),
            coverPath = str(d, "coverPath")?.ifBlank { null },
            inviterName = str(d, "inviterName")?.ifBlank { null }
        )
    }

    fun joinRequest(uid: String, d: Map<String, Any?>): JoinRequest =
        JoinRequest(uid, str(d, "displayName")?.ifBlank { null } ?: "Someone", millis(d["createdAt"]))

    fun rsvp(uid: String, d: Map<String, Any?>): Rsvp? {
        val answer = RsvpAnswer.from(str(d, "status")) ?: return null
        return Rsvp(uid, str(d, "displayName")?.ifBlank { null } ?: "Member", answer)
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
