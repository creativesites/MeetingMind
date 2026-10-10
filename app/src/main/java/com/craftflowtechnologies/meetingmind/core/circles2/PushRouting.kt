package com.craftflowtechnologies.meetingmind.core.circles2

/** The push types the Worker sends (`data.kind`). Unknown kinds are shown as plain notifications that just open Circles. */
enum class PushKind(val wire: String) {
    Post("post"), Pending("pending"), Approved("approved"), Rejected("rejected"), Answered("answered"),
    Digest("digest"), Poll("poll"), Chain("chain"), Celebration("celebration"), Unknown("");

    companion object { fun from(wire: String?): PushKind = entries.firstOrNull { it.wire.isNotEmpty() && it.wire == wire } ?: Unknown }
}

/** Where a notification or link lands. */
enum class CircleDestination { CirclesList, Feed, Chat }

/**
 * Where to go. [circleId] is null for pushes that aren't about one circle (the daily digest): those open the
 * Circles list. [postId] opens that post's thread on the Feed.
 */
data class CircleTarget(val circleId: String?, val destination: CircleDestination, val postId: String? = null)

/** Pure decoding of push data and links; the Android glue (intents, notifications) lives in feature/ and the service. */
object PushRouting {
    private val ID = Regex("^[A-Za-z0-9_-]{1,64}$")
    const val SCHEME = "meetingmind"
    const val HOST = "c"
    private const val OPEN = "open"

    private fun id(v: String?): String? = v?.takeIf { ID.matches(it) }

    /** From an FCM `data` map (or the extras of a tapped system notification). Null for data that isn't ours. */
    fun fromData(data: Map<String, String?>): CircleTarget? {
        val kind = PushKind.from(data["kind"])
        val circle = id(data["circleId"])
        val post = id(data["postId"])
        return when (kind) {
            PushKind.Unknown -> if (data["kind"] == null) null else CircleTarget(null, CircleDestination.CirclesList)
            PushKind.Digest -> CircleTarget(null, CircleDestination.CirclesList)
            // Everything else is about one circle; a push with a bad circle id still opens the list rather than nothing.
            PushKind.Post, PushKind.Approved, PushKind.Answered ->
                if (circle == null) CircleTarget(null, CircleDestination.CirclesList) else CircleTarget(circle, CircleDestination.Feed, post)
            PushKind.Pending, PushKind.Rejected ->
                if (circle == null) CircleTarget(null, CircleDestination.CirclesList) else CircleTarget(circle, CircleDestination.Feed)
            PushKind.Poll, PushKind.Chain, PushKind.Celebration ->
                if (circle == null) CircleTarget(null, CircleDestination.CirclesList) else CircleTarget(circle, CircleDestination.Chat)
        }
    }

    /** `meetingmind://c/open?circle=ID[&post=ID][&tab=chat]` — the link our own notifications carry. */
    fun uri(t: CircleTarget): String = buildString {
        append("$SCHEME://$HOST/$OPEN")
        val q = buildList {
            t.circleId?.let { add("circle=$it") }
            t.postId?.let { add("post=$it") }
            if (t.destination == CircleDestination.Chat) add("tab=chat")
        }
        if (q.isNotEmpty()) append('?').append(q.joinToString("&"))
    }

    /** Parses [uri]. Null when it isn't an "open" link (an invite link `meetingmind://c/GRACE-7K2Q` is handled elsewhere). */
    fun fromUri(uri: String?): CircleTarget? {
        val u = uri?.trim() ?: return null
        val prefix = "$SCHEME://$HOST/$OPEN"
        if (!u.startsWith(prefix)) return null
        val rest = u.removePrefix(prefix)
        if (rest.isNotEmpty() && rest[0] != '?') return null
        val params = rest.removePrefix("?").split('&').filter { it.contains('=') }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
        val circle = id(params["circle"])
        val post = id(params["post"])
        return when {
            circle == null -> CircleTarget(null, CircleDestination.CirclesList)
            params["tab"] == "chat" -> CircleTarget(circle, CircleDestination.Chat)
            else -> CircleTarget(circle, CircleDestination.Feed, post)
        }
    }

    /** Notification text for a data-only push, or for when the OS gave us none. Never contains post text. */
    fun fallbackText(kind: PushKind): Pair<String, String> = when (kind) {
        PushKind.Post -> "Circles" to "Someone shared something new"
        PushKind.Pending -> "Circles" to "A prayer request is waiting for approval"
        PushKind.Approved -> "Circles" to "Your prayer request was shared with the circle"
        PushKind.Rejected -> "Circles" to "Your prayer request wasn't shared this time"
        PushKind.Answered -> "Circles" to "A prayer you prayed for was answered"
        PushKind.Digest -> "Prayer" to "People prayed for you today"
        PushKind.Poll -> "Circles" to "A new poll"
        PushKind.Chain -> "Circles" to "A 24-hour prayer chain started"
        PushKind.Celebration -> "Circles" to "Something to celebrate"
        PushKind.Unknown -> "Circles" to "Open Circles"
    }

    /** One notification per post (so an update replaces it) and one per kind and circle otherwise. */
    fun notificationId(data: Map<String, String?>): Int =
        listOf(data["kind"], data["circleId"], data["postId"] ?: data["messageId"] ?: data["chainId"]).joinToString("|").hashCode()
}
