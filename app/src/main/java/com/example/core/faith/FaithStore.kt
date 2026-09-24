package com.example.core.faith

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Someone (or something) on the prayer list. */
data class PrayerPerson(val id: Long, val name: String, val note: String, val lastPrayedAt: Long?, val prayedCount: Int)

/**
 * Reading-plan progress and the prayer list (PLAN_V2 F6), in their own small database beside the
 * notes, so they can grow without touching the notes schema.
 */
class FaithStore(context: Context, name: String? = FILE) : SQLiteOpenHelper(context.applicationContext, name, null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE plans (plan_id TEXT PRIMARY KEY, started_day INTEGER NOT NULL, active INTEGER NOT NULL DEFAULT 1)")
        db.execSQL("CREATE TABLE plan_days (plan_id TEXT NOT NULL, day INTEGER NOT NULL, done_at INTEGER NOT NULL, PRIMARY KEY (plan_id, day))")
        db.execSQL("CREATE TABLE people (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, note TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, archived INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE prayed (person_id INTEGER NOT NULL, at INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    // ---------------------------------------------------------------- plans

    fun start(planId: String, epochDay: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("plan_days", "plan_id = ?", arrayOf(planId))
            db.insertWithOnConflict("plans", null, ContentValues().apply { put("plan_id", planId); put("started_day", epochDay); put("active", 1) }, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun stop(planId: String) { writableDatabase.execSQL("UPDATE plans SET active = 0 WHERE plan_id = ?", arrayOf(planId)) }

    fun setDone(planId: String, day: Int, done: Boolean) {
        if (done) writableDatabase.insertWithOnConflict("plan_days", null, ContentValues().apply { put("plan_id", planId); put("day", day); put("done_at", System.currentTimeMillis()) }, SQLiteDatabase.CONFLICT_IGNORE)
        else writableDatabase.delete("plan_days", "plan_id = ? AND day = ?", arrayOf(planId, day.toString()))
    }

    fun active(): List<PlanProgress> = readableDatabase.rawQuery("SELECT plan_id, started_day FROM plans WHERE active = 1 ORDER BY started_day", null).use { c ->
        buildList {
            while (c.moveToNext()) {
                val plan = ReadingPlans.byId(c.getString(0)) ?: continue
                add(PlanProgress(plan, c.getLong(1), doneDays(plan.id)))
            }
        }
    }

    private fun doneDays(planId: String): Set<Int> = readableDatabase.rawQuery("SELECT day FROM plan_days WHERE plan_id = ?", arrayOf(planId))
        .use { c -> buildSet { while (c.moveToNext()) add(c.getInt(0)) } }

    // ---------------------------------------------------------------- prayer list

    fun addPerson(name: String, note: String = ""): Long = writableDatabase.insert("people", null, ContentValues().apply {
        put("name", name.trim()); put("note", note.trim()); put("created_at", System.currentTimeMillis())
    })

    fun updatePerson(id: Long, name: String, note: String) {
        writableDatabase.execSQL("UPDATE people SET name = ?, note = ? WHERE id = ?", arrayOf(name.trim(), note.trim(), id))
    }

    fun removePerson(id: Long) { writableDatabase.execSQL("UPDATE people SET archived = 1 WHERE id = ?", arrayOf(id)) }

    fun markPrayed(id: Long, at: Long = System.currentTimeMillis()) {
        writableDatabase.insert("prayed", null, ContentValues().apply { put("person_id", id); put("at", at) })
    }

    fun people(): List<PrayerPerson> = readableDatabase.rawQuery(
        "SELECT p.id, p.name, p.note, (SELECT MAX(at) FROM prayed WHERE person_id = p.id), (SELECT COUNT(*) FROM prayed WHERE person_id = p.id) FROM people p WHERE p.archived = 0 ORDER BY p.created_at",
        null
    ).use { c -> buildList { while (c.moveToNext()) add(PrayerPerson(c.getLong(0), c.getString(1), c.getString(2), if (c.isNull(3)) null else c.getLong(3), c.getInt(4))) } }

    /** Days (as epoch days) on which anyone was prayed for — the quiet history. */
    fun prayedDays(zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Set<Long> = readableDatabase.rawQuery("SELECT at FROM prayed", null).use { c ->
        buildSet { while (c.moveToNext()) add(java.time.Instant.ofEpochMilli(c.getLong(0)).atZone(zone).toLocalDate().toEpochDay()) }
    }

    companion object {
        const val FILE = "faith_extras.db"
        @Volatile private var instance: FaithStore? = null
        fun get(context: Context): FaithStore = instance ?: synchronized(this) { instance ?: FaithStore(context).also { instance = it } }
    }
}

/**
 * Who to pray for today (PLAN_V2 F6): a few each day, those prayed for least recently first, so
 * everyone is carried in turn — stable through the day.
 */
object PrayerRotation {
    fun today(people: List<PrayerPerson>, epochDay: Long, count: Int = 3, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): List<PrayerPerson> {
        if (people.isEmpty()) return emptyList()
        val startOfToday = java.time.LocalDate.ofEpochDay(epochDay).atStartOfDay(zone).toInstant().toEpochMilli()
        // Anyone already prayed for today stays in today's list, so ticking one off doesn't swap it out.
        val prayedToday = people.filter { (it.lastPrayedAt ?: 0) >= startOfToday }
        val rest = people.filter { it !in prayedToday }.sortedWith(compareBy({ it.lastPrayedAt ?: 0L }, { it.id }))
        return (prayedToday + rest).take(count.coerceAtLeast(prayedToday.size))
    }
}
