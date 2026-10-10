package com.craftflowtechnologies.meetingmind.core.circles2

import android.content.Context

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
    fun myPostIds(circleId: String): Set<String>
    fun addMyPost(circleId: String, postId: String)
    /** Posts I wrote anonymously: my own comments on these must go through the Worker (`commentAsAuthor`). */
    fun anonymousPostIds(circleId: String): Set<String>
    fun addAnonymousPost(circleId: String, postId: String)
    fun prayedIds(circleId: String): Set<String>
    fun addPrayed(circleId: String, postId: String)
    fun lastRead(circleId: String): Long
    fun setLastRead(circleId: String, at: Long)
}

class InMemoryLocalCircleStore : LocalCircleStore {
    override var displayName: String = ""
    private val circles = LinkedHashSet<String>()
    private val mine = HashMap<String, MutableSet<String>>()
    private val prayed = HashMap<String, MutableSet<String>>()
    private val anon = HashMap<String, MutableSet<String>>()
    private val read = HashMap<String, Long>()
    override fun circleIds() = circles.toList()
    override fun addCircle(id: String) { circles += id }
    override fun removeCircle(id: String) { circles -= id }
    override fun myPostIds(circleId: String): Set<String> = mine[circleId].orEmpty()
    override fun addMyPost(circleId: String, postId: String) { mine.getOrPut(circleId) { mutableSetOf() } += postId }
    override fun anonymousPostIds(circleId: String): Set<String> = anon[circleId].orEmpty()
    override fun addAnonymousPost(circleId: String, postId: String) { anon.getOrPut(circleId) { mutableSetOf() } += postId }
    override fun prayedIds(circleId: String): Set<String> = prayed[circleId].orEmpty()
    override fun addPrayed(circleId: String, postId: String) { prayed.getOrPut(circleId) { mutableSetOf() } += postId }
    override fun lastRead(circleId: String) = read[circleId] ?: 0L
    override fun setLastRead(circleId: String, at: Long) { read[circleId] = at }
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
    override fun myPostIds(circleId: String): Set<String> = set("mine_$circleId")
    override fun addMyPost(circleId: String, postId: String) = put("mine_$circleId", set("mine_$circleId").apply { add(postId) })
    override fun anonymousPostIds(circleId: String): Set<String> = set("anon_$circleId")
    override fun addAnonymousPost(circleId: String, postId: String) = put("anon_$circleId", set("anon_$circleId").apply { add(postId) })
    override fun prayedIds(circleId: String): Set<String> = set("prayed_$circleId")
    override fun addPrayed(circleId: String, postId: String) = put("prayed_$circleId", set("prayed_$circleId").apply { add(postId) })
    override fun lastRead(circleId: String) = prefs.getLong("read_$circleId", 0L)
    override fun setLastRead(circleId: String, at: Long) { prefs.edit().putLong("read_$circleId", at).apply() }
}
