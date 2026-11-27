package com.craftflowtechnologies.meetingmind.core.circles2

import android.content.Context
import org.json.JSONObject

/** Locally auto-saved post draft for a circle so accidental Back presses never wipe typed text. */
data class ComposerDraft(
    val typeWire: String,
    val body: String,
    val verse: String = "",
    val anonymous: Boolean = false,
    val fromAnswered: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun hasContent(): Boolean = body.isNotBlank() || verse.isNotBlank()

    fun toJson(): String = JSONObject()
        .put("typeWire", typeWire)
        .put("body", body)
        .put("verse", verse)
        .put("anonymous", anonymous)
        .put("fromAnswered", fromAnswered)
        .put("updatedAt", updatedAt)
        .toString()

    companion object {
        fun fromJson(raw: String?): ComposerDraft? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val o = JSONObject(raw)
                ComposerDraft(
                    typeWire = o.optString("typeWire", "note"),
                    body = o.optString("body", ""),
                    verse = o.optString("verse", ""),
                    anonymous = o.optBoolean("anonymous", false),
                    fromAnswered = o.optBoolean("fromAnswered", false),
                    updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                )
            }.getOrNull()
        }
    }
}

/**
 * Small per-install memory: which circles I'm in, my display name, which posts are mine (so an
 * anonymous request can still be edited or marked answered by its author), what I've prayed for and
 * when I last read each chat. Firestore's own cache covers the content; this covers "what is mine".
 * The server is still the authority: a stale id here just fails with "not a member".
 */
interface LocalCircleStore {
    var displayName: String
    fun circleIds(): List<String>
    fun addCircle(id: String)
    fun removeCircle(id: String)
    /**
     * Circles this phone knew but can no longer read with the account it has now: almost always a new sign-in after a
     * reinstall. Kept, never deleted, so the list can say "sign in to get them back" instead of losing them quietly.
     */
    fun earlierCircleIds(): List<String> = emptyList()
    /** Which app profile photo (path and time) was last sent as the Circles photo. Null until one is. */
    var appAvatarSynced: String?
        get() = null
        set(_) = Unit
    /** True once the person picked (or removed) a photo inside Circles: the app photo no longer overrides it. */
    var circlesAvatarOwn: Boolean
        get() = false
        set(_) = Unit
    fun addEarlierCircle(id: String) = Unit
    fun clearEarlierCircle(id: String) = Unit
    fun myPostIds(circleId: String): Set<String>
    fun addMyPost(circleId: String, postId: String)
    /** Posts I wrote anonymously: my own comments on these must go through the Worker (`commentAsAuthor`). */
    fun anonymousPostIds(circleId: String): Set<String>
    fun addAnonymousPost(circleId: String, postId: String)
    fun prayedIds(circleId: String): Set<String>
    fun addPrayed(circleId: String, postId: String)
    fun lastRead(circleId: String): Long
    fun setLastRead(circleId: String, at: Long)
    fun composerDraft(circleId: String): ComposerDraft? = null
    fun setComposerDraft(circleId: String, draft: ComposerDraft?) = Unit
    fun chatDraft(circleId: String): String = ""
    fun setChatDraft(circleId: String, text: String) = Unit
    fun commentDraft(circleId: String, postId: String): String = ""
    fun setCommentDraft(circleId: String, postId: String, text: String) = Unit
    fun streakDays(): Int = 0
    fun lastActivityDay(): Long = 0L
    fun currentStreak(nowMs: Long = System.currentTimeMillis()): Int {
        val today = nowMs / 86_400_000L
        val last = lastActivityDay()
        return if (last == 0L || today - last > 1L) 0 else streakDays()
    }
    fun checkedInToday(nowMs: Long = System.currentTimeMillis()): Boolean = lastActivityDay() == nowMs / 86_400_000L
    fun recordActivity(nowMs: Long = System.currentTimeMillis()): Int = streakDays()
    fun savedPostIds(circleId: String): Set<String> = emptySet()
    fun toggleSavedPost(circleId: String, postId: String): Boolean = false
    fun lastFeedVisit(circleId: String): Long = 0L
    fun setLastFeedVisit(circleId: String, at: Long) = Unit
    fun dismissedPromptDay(circleId: String): Long = 0L
    fun setDismissedPromptDay(circleId: String, day: Long) = Unit
}

class InMemoryLocalCircleStore : LocalCircleStore {
    override var displayName: String = ""
    private val circles = LinkedHashSet<String>()
    private val mine = HashMap<String, MutableSet<String>>()
    private val prayed = HashMap<String, MutableSet<String>>()
    private val anon = HashMap<String, MutableSet<String>>()
    private val read = HashMap<String, Long>()
    private val drafts = HashMap<String, ComposerDraft>()
    private val chatDrafts = HashMap<String, String>()
    private val commentDrafts = HashMap<String, String>()
    private var streak: Int = 0
    private var lastDay: Long = 0L
    private val saved = HashMap<String, MutableSet<String>>()
    private val feedVisits = HashMap<String, Long>()
    private val dismissedPrompts = HashMap<String, Long>()

    override fun circleIds() = circles.toList()
    override fun addCircle(id: String) { circles += id }
    override fun removeCircle(id: String) { circles -= id }
    private val earlier = LinkedHashSet<String>()
    override var appAvatarSynced: String? = null
    override var circlesAvatarOwn: Boolean = false
    override fun earlierCircleIds() = earlier.toList()
    override fun addEarlierCircle(id: String) { earlier += id }
    override fun clearEarlierCircle(id: String) { earlier -= id }
    override fun myPostIds(circleId: String): Set<String> = mine[circleId].orEmpty()
    override fun addMyPost(circleId: String, postId: String) { mine.getOrPut(circleId) { mutableSetOf() } += postId }
    override fun anonymousPostIds(circleId: String): Set<String> = anon[circleId].orEmpty()
    override fun addAnonymousPost(circleId: String, postId: String) { anon.getOrPut(circleId) { mutableSetOf() } += postId }
    override fun prayedIds(circleId: String): Set<String> = prayed[circleId].orEmpty()
    override fun addPrayed(circleId: String, postId: String) { prayed.getOrPut(circleId) { mutableSetOf() } += postId }
    override fun lastRead(circleId: String) = read[circleId] ?: 0L
    override fun setLastRead(circleId: String, at: Long) { read[circleId] = at }
    override fun composerDraft(circleId: String): ComposerDraft? = drafts[circleId]
    override fun setComposerDraft(circleId: String, draft: ComposerDraft?) {
        if (draft == null) drafts.remove(circleId) else drafts[circleId] = draft
    }
    override fun chatDraft(circleId: String): String = chatDrafts[circleId].orEmpty()
    override fun setChatDraft(circleId: String, text: String) {
        if (text.isBlank()) chatDrafts.remove(circleId) else chatDrafts[circleId] = text
    }
    override fun commentDraft(circleId: String, postId: String): String = commentDrafts["${circleId}_$postId"].orEmpty()
    override fun setCommentDraft(circleId: String, postId: String, text: String) {
        val k = "${circleId}_$postId"
        if (text.isBlank()) commentDrafts.remove(k) else commentDrafts[k] = text
    }
    override fun streakDays(): Int = streak
    override fun lastActivityDay(): Long = lastDay
    override fun currentStreak(nowMs: Long): Int {
        val today = nowMs / 86_400_000L
        return if (lastDay == 0L || today - lastDay > 1L) 0 else streak
    }
    override fun checkedInToday(nowMs: Long): Boolean = lastDay == nowMs / 86_400_000L
    override fun recordActivity(nowMs: Long): Int {
        val today = nowMs / 86_400_000L
        val next = when (lastDay) {
            today -> if (streak < 1) 1 else streak
            today - 1L -> streak.coerceAtLeast(1) + 1
            else -> 1
        }
        streak = next
        lastDay = today
        return next
    }
    override fun savedPostIds(circleId: String): Set<String> = saved[circleId]?.toSet().orEmpty()
    override fun toggleSavedPost(circleId: String, postId: String): Boolean {
        val set = saved.getOrPut(circleId) { mutableSetOf() }
        return if (postId in set) {
            set.remove(postId)
            false
        } else {
            set.add(postId)
            true
        }
    }
    override fun lastFeedVisit(circleId: String): Long = feedVisits[circleId] ?: 0L
    override fun setLastFeedVisit(circleId: String, at: Long) { feedVisits[circleId] = at }
    override fun dismissedPromptDay(circleId: String): Long = dismissedPrompts[circleId] ?: 0L
    override fun setDismissedPromptDay(circleId: String, day: Long) { dismissedPrompts[circleId] = day }
}

class SharedPrefsLocalCircleStore(context: Context) : LocalCircleStore {
    private val prefs = context.applicationContext.getSharedPreferences("circles2_local", Context.MODE_PRIVATE)

    override var displayName: String
        get() = prefs.getString("name", "").orEmpty()
        set(value) { prefs.edit().putString("name", value.trim().take(40)).apply() }

    private fun set(key: String): MutableSet<String> = prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
    private fun put(key: String, value: Set<String>) { prefs.edit().putStringSet(key, value).apply() }

    override fun circleIds() = prefs.getString("ids", "").orEmpty().split(',').filter { it.isNotBlank() }
    override fun addCircle(id: String) { val l = circleIds(); if (id !in l) prefs.edit().putString("ids", (l + id).joinToString(",")).apply() }
    override fun removeCircle(id: String) { prefs.edit().putString("ids", (circleIds() - id).joinToString(",")).apply() }
    override fun earlierCircleIds() = prefs.getString("earlier_ids", "").orEmpty().split(',').filter { it.isNotBlank() }
    override var appAvatarSynced: String?
        get() = prefs.getString("app_avatar_synced", null)
        set(value) { prefs.edit().putString("app_avatar_synced", value).apply() }
    override var circlesAvatarOwn: Boolean
        get() = prefs.getBoolean("circles_avatar_own", false)
        set(value) { prefs.edit().putBoolean("circles_avatar_own", value).apply() }
    override fun addEarlierCircle(id: String) { val l = earlierCircleIds(); if (id !in l) prefs.edit().putString("earlier_ids", (l + id).takeLast(50).joinToString(",")).apply() }
    override fun clearEarlierCircle(id: String) { prefs.edit().putString("earlier_ids", (earlierCircleIds() - id).joinToString(",")).apply() }
    override fun myPostIds(circleId: String): Set<String> = set("mine_$circleId")
    override fun addMyPost(circleId: String, postId: String) = put("mine_$circleId", set("mine_$circleId").apply { add(postId) })
    override fun anonymousPostIds(circleId: String): Set<String> = set("anon_$circleId")
    override fun addAnonymousPost(circleId: String, postId: String) = put("anon_$circleId", set("anon_$circleId").apply { add(postId) })
    override fun prayedIds(circleId: String): Set<String> = set("prayed_$circleId")
    override fun addPrayed(circleId: String, postId: String) = put("prayed_$circleId", set("prayed_$circleId").apply { add(postId) })
    override fun lastRead(circleId: String) = prefs.getLong("read_$circleId", 0L)
    override fun setLastRead(circleId: String, at: Long) { prefs.edit().putLong("read_$circleId", at).apply() }
    override fun composerDraft(circleId: String): ComposerDraft? = ComposerDraft.fromJson(prefs.getString("draft_$circleId", null))
    override fun setComposerDraft(circleId: String, draft: ComposerDraft?) {
        prefs.edit().apply {
            if (draft == null) remove("draft_$circleId") else putString("draft_$circleId", draft.toJson())
        }.apply()
    }
    override fun chatDraft(circleId: String): String = prefs.getString("chat_draft_$circleId", "").orEmpty()
    override fun setChatDraft(circleId: String, text: String) {
        prefs.edit().apply {
            if (text.isBlank()) remove("chat_draft_$circleId") else putString("chat_draft_$circleId", text)
        }.apply()
    }
    override fun commentDraft(circleId: String, postId: String): String = prefs.getString("comment_draft_${circleId}_$postId", "").orEmpty()
    override fun setCommentDraft(circleId: String, postId: String, text: String) {
        prefs.edit().apply {
            val k = "comment_draft_${circleId}_$postId"
            if (text.isBlank()) remove(k) else putString(k, text)
        }.apply()
    }
    override fun streakDays(): Int = prefs.getInt("streak_days", 0)
    override fun lastActivityDay(): Long = prefs.getLong("last_activity_day", 0L)
    override fun currentStreak(nowMs: Long): Int {
        val today = nowMs / 86_400_000L
        val last = lastActivityDay()
        return if (last == 0L || today - last > 1L) 0 else streakDays()
    }
    override fun checkedInToday(nowMs: Long): Boolean = lastActivityDay() == nowMs / 86_400_000L
    override fun recordActivity(nowMs: Long): Int {
        val today = nowMs / 86_400_000L
        val last = lastActivityDay()
        val cur = streakDays()
        val next = when (last) {
            today -> if (cur < 1) 1 else cur
            today - 1L -> cur.coerceAtLeast(1) + 1
            else -> 1
        }
        prefs.edit()
            .putInt("streak_days", next)
            .putLong("last_activity_day", today)
            .apply()
        return next
    }
    override fun savedPostIds(circleId: String): Set<String> = set("saved_$circleId")
    override fun toggleSavedPost(circleId: String, postId: String): Boolean {
        val k = "saved_$circleId"
        val cur = set(k)
        val added = if (postId in cur) {
            cur.remove(postId)
            false
        } else {
            cur.add(postId)
            true
        }
        put(k, cur)
        return added
    }
    override fun lastFeedVisit(circleId: String): Long = prefs.getLong("feed_visit_$circleId", 0L)
    override fun setLastFeedVisit(circleId: String, at: Long) {
        prefs.edit().putLong("feed_visit_$circleId", at).apply()
    }
    override fun dismissedPromptDay(circleId: String): Long = prefs.getLong("prompt_dismissed_$circleId", 0L)
    override fun setDismissedPromptDay(circleId: String, day: Long) {
        prefs.edit().putLong("prompt_dismissed_$circleId", day).apply()
    }
}
