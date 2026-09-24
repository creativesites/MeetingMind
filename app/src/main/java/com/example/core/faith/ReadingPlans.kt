package com.example.core.faith

import com.example.core.scripture.BibleBook
import com.example.core.scripture.BibleBooks
import com.example.core.scripture.ScriptureReference
import com.example.core.scripture.ScriptureReferenceParser

/** A reading plan: a name, a line about it, and each day's passages. */
data class ReadingPlan(val id: String, val name: String, val line: String, val days: List<List<ScriptureReference>>) {
    val length get() = days.size
}

/**
 * The bundled reading plans (PLAN_V2 F6), built from the Bible's own shape rather than typed out,
 * so every day is a real chapter and the whole plan is always complete.
 */
object ReadingPlans {
    private fun chapters(books: List<BibleBook>) = books.flatMap { b -> (1..b.chapterCount).map { ScriptureReference(b, it) } }

    /** Splits [items] into [days] runs as evenly as possible, in order. */
    fun spread(items: List<ScriptureReference>, days: Int): List<List<ScriptureReference>> =
        (0 until days).map { d -> items.subList(items.size * d / days, items.size * (d + 1) / days) }.filter { it.isNotEmpty() }

    private val ot get() = BibleBooks.all.takeWhile { it.usfm != "MAT" }
    private val nt get() = BibleBooks.all.dropWhile { it.usfm != "MAT" }
    private fun book(usfm: String) = BibleBooks.byUsfm(usfm)!!
    private fun refs(vararg s: String) = s.map { ScriptureReferenceParser.parse(it)!! }

    val all: List<ReadingPlan> by lazy {
        listOf(
            ReadingPlan("year", "The Bible in a year", "Every chapter, Genesis to Revelation, in 365 days", spread(chapters(BibleBooks.all), 365)),
            ReadingPlan("ot-nt", "Old & New together", "A year through both Testaments side by side, with a psalm most days", run {
                val o = spread(chapters(ot), 365); val n = spread(chapters(nt), 365); val p = chapters(listOf(book("PSA")))
                (0 until 365).map { d -> o.getOrElse(d) { emptyList() } + n.getOrElse(d) { emptyList() } + listOfNotNull(p.getOrNull(d % p.size).takeIf { d < p.size }) }
            }),
            ReadingPlan("nt90", "New Testament in 90 days", "Three chapters a day through the New Testament", spread(chapters(nt), 90)),
            ReadingPlan("gospels30", "The Gospels in 30 days", "Matthew, Mark, Luke and John", spread(chapters(listOf("MAT", "MRK", "LUK", "JHN").map(::book)), 30)),
            ReadingPlan("psaprov", "Psalms & Proverbs", "Five psalms and a proverb each day for a month", run {
                val ps = chapters(listOf(book("PSA"))); val pr = chapters(listOf(book("PRO")))
                (0 until 31).map { d -> (0 until 5).mapNotNull { k -> ps.getOrNull(d + 31 * k) } + listOfNotNull(pr.getOrNull(d)) }
            }),
            ReadingPlan("advent", "Advent", "24 days of promise and waiting, to Christmas", refs(
                "Isaiah 40:1-11", "Isaiah 9:1-7", "Isaiah 11:1-10", "Jeremiah 33:14-16", "Micah 5:2-5", "Isaiah 7:10-14", "Malachi 3:1-4", "Luke 1:5-25",
                "Luke 1:26-38", "Luke 1:39-56", "Matthew 1:18-25", "Isaiah 35", "Luke 1:57-80", "Isaiah 61:1-4", "Zephaniah 3:14-20", "Psalm 80",
                "Isaiah 60:1-6", "John 1:1-14", "Galatians 4:4-7", "Romans 15:4-13", "Philippians 4:4-7", "Isaiah 52:7-10", "Hebrews 1:1-4", "Luke 2:1-20"
            ).map { listOf(it) }),
            ReadingPlan("lent", "Lent", "40 days toward the cross and the empty tomb", refs(
                "Joel 2:12-17", "Psalm 51", "Isaiah 58:1-12", "Matthew 6:1-18", "Matthew 4:1-11", "Genesis 12:1-4", "Psalm 121", "John 3:1-17",
                "Exodus 17:1-7", "John 4:5-42", "Psalm 95", "1 Samuel 16:1-13", "John 9", "Ephesians 5:8-14", "Ezekiel 37:1-14", "John 11:1-45",
                "Romans 8:6-11", "Psalm 130", "Luke 15:1-10", "Luke 15:11-32", "Micah 6:6-8", "Psalm 32", "Isaiah 43:16-21", "Philippians 3:4-14",
                "Hebrews 4:14-16", "Mark 10:32-45", "Isaiah 50:4-9", "Psalm 22", "Isaiah 53", "Philippians 2:5-11", "Mark 11:1-11", "John 12:1-8",
                "John 12:20-36", "John 13:1-17", "Luke 22:39-46", "John 18", "John 19:1-30", "Luke 23:44-56", "Psalm 16", "John 20:1-18"
            ).map { listOf(it) })
        )
    }

    fun byId(id: String) = all.firstOrNull { it.id == id }

    /** One label for a day's readings: "Genesis 1–3", "John 3; Psalm 23". Neighbouring chapters of a book join up. */
    fun describe(day: List<ScriptureReference>): String {
        val groups = mutableListOf<MutableList<ScriptureReference>>()
        for (r in day) {
            val last = groups.lastOrNull()?.lastOrNull()
            if (last != null && last.book == r.book && last.verseStart == null && r.verseStart == null && r.chapter == last.chapter + 1) groups.last() += r
            else groups += mutableListOf(r)
        }
        return groups.joinToString("; ") { g -> if (g.size == 1) g[0].display() else "${g.first().display()}–${g.last().chapter}" }
    }
}

/** Where someone is in a plan: which days are done, and what today is. */
data class PlanProgress(val plan: ReadingPlan, val startedEpochDay: Long, val done: Set<Int>) {
    /** The plan day the calendar says it is (0-based), capped at the end. */
    fun dayFor(epochDay: Long) = (epochDay - startedEpochDay).toInt().coerceIn(0, plan.length - 1)
    /** The first day not yet read — where to pick up. */
    val nextUnread: Int? get() = (0 until plan.length).firstOrNull { it !in done }
    /** Days behind the calendar (0 when on track or ahead). */
    fun behind(epochDay: Long) = (0 until dayFor(epochDay)).count { it !in done }
    val percent get() = done.size * 100 / plan.length.coerceAtLeast(1)
    val finished get() = done.size >= plan.length
}
