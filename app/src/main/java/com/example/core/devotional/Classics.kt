package com.example.core.devotional

import android.content.Context
import com.example.core.scripture.ScriptureReference
import com.example.core.scripture.ScriptureReferenceParser
import org.json.JSONArray
import java.io.InputStream
import java.time.LocalDate
import java.util.zip.GZIPInputStream

/** One reading from a bundled public-domain devotional. */
data class ClassicReading(
    /** "MM-dd". */
    val date: String,
    val evening: Boolean,
    /** The words the author set at the head of the reading, as printed. */
    val keyText: String,
    val reference: ScriptureReference?,
    val referenceText: String,
    val paragraphs: List<String>
)

/**
 * Spurgeon's *Morning and Evening* (1865, public domain; text from the Christian Classics Ethereal
 * Library), bundled so a devotional is always there — offline, with no key and no model.
 */
class ClassicDevotionals(private val readings: List<ClassicReading>) {

    val title = "Morning and Evening"
    val author = "C. H. Spurgeon"
    val attribution = "From Morning and Evening by C. H. Spurgeon (1865), public domain."

    /** The reading for [date]; 29 February falls back to the 28th. */
    fun forDate(date: LocalDate, evening: Boolean = false): ClassicReading? {
        val key = "%02d-%02d".format(date.monthValue, date.dayOfMonth)
        return readings.firstOrNull { it.date == key && it.evening == evening }
            ?: if (key == "02-29") forDate(date.withDayOfMonth(28), evening) else null
    }

    val size get() = readings.size

    companion object {
        const val ASSET = "devotionals/spurgeon_morning_evening.json.gz"

        @Volatile private var cached: ClassicDevotionals? = null

        /** Loads once. A failed load isn't remembered, so one bad moment can't leave every later day empty. */
        fun get(context: Context): ClassicDevotionals =
            cached ?: synchronized(this) {
                cached ?: runCatching { context.assets.open(ASSET).use { parse(GZIPInputStream(it)) } }
                    .getOrNull()?.takeIf { it.size > 0 }?.also { cached = it }
                    ?: ClassicDevotionals(emptyList())
            }

        /** For tests, where app assets aren't packaged. */
        fun useForTest(classics: ClassicDevotionals?) { cached = classics }

        fun parse(input: InputStream): ClassicDevotionals {
            val array = JSONArray(input.bufferedReader(Charsets.UTF_8).readText())
            val list = (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val b = o.getJSONArray("b")
                val refText = o.optString("r")
                ClassicReading(
                    date = o.getString("d"),
                    evening = o.optString("t") == "pm",
                    keyText = o.optString("v"),
                    reference = ScriptureReferenceParser.parse(refText),
                    referenceText = refText,
                    paragraphs = (0 until b.length()).map { b.getString(it) }.filter { it.isNotBlank() }
                )
            }
            return ClassicDevotionals(list)
        }
    }
}

/** A public-domain quote, always shown with who said it and where. */
data class Quote(val text: String, val author: String, val source: String, val topics: Set<String>) {
    val attribution get() = "$author, $source"
}

object Quotes {
    @Volatile private var cached: List<Quote>? = null

    fun get(context: Context): List<Quote> = cached ?: runCatching {
        context.assets.open("devotionals/quotes.json").use { parse(it.bufferedReader().readText()) }
    }.getOrElse { emptyList() }.also { cached = it }

    fun parse(json: String): List<Quote> {
        val a = JSONArray(json)
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val t = o.optJSONArray("t")
            Quote(o.getString("q"), o.getString("a"), o.optString("s"), (0 until (t?.length() ?: 0)).map { t!!.getString(it) }.toSet())
        }
    }

    /**
     * One quote for the day: from the person's topics when any match, otherwise from all of them,
     * rotating by date so it changes daily but is stable within a day.
     */
    fun pick(quotes: List<Quote>, topics: Collection<String>, date: LocalDate): Quote? {
        if (quotes.isEmpty()) return null
        val matching = quotes.filter { q -> q.topics.any { it in topics } }.ifEmpty { quotes }
        return matching[(date.toEpochDay() % matching.size).toInt().let { if (it < 0) it + matching.size else it }]
    }
}

/**
 * Passages for each topic — the model is handed a real passage to write on rather than choosing
 * (or inventing) one. Every entry is checked by the parser in tests.
 */
object TopicPassages {
    val byTopic: Map<String, List<String>> = mapOf(
        "Peace" to listOf("John 14:27", "Philippians 4:6-7", "Isaiah 26:3", "Psalm 4:8", "Colossians 3:15"),
        "Anxiety" to listOf("Matthew 6:25-34", "1 Peter 5:6-7", "Philippians 4:6-7", "Psalm 94:19", "Isaiah 41:10"),
        "Hope" to listOf("Romans 15:13", "Lamentations 3:21-24", "Jeremiah 29:11-13", "Romans 5:3-5", "Hebrews 6:19"),
        "Grief" to listOf("Psalm 34:18", "Matthew 5:4", "John 11:25-26", "Revelation 21:4", "2 Corinthians 1:3-4"),
        "Faith" to listOf("Hebrews 11:1-6", "Mark 9:23-24", "Romans 10:17", "James 2:14-17", "2 Corinthians 5:7"),
        "Forgiveness" to listOf("1 John 1:9", "Ephesians 4:31-32", "Psalm 103:10-12", "Colossians 3:13", "Matthew 18:21-22"),
        "Purpose" to listOf("Ephesians 2:10", "Micah 6:8", "Colossians 3:23-24", "Romans 12:1-2", "Proverbs 16:3"),
        "Patience" to listOf("James 1:2-4", "Psalm 37:7", "Galatians 6:9", "Romans 8:24-25", "Isaiah 40:31"),
        "Courage" to listOf("Joshua 1:9", "Deuteronomy 31:6", "Psalm 27:1", "2 Timothy 1:7", "Isaiah 43:1-2"),
        "Gratitude" to listOf("1 Thessalonians 5:16-18", "Psalm 100", "Psalm 103:1-5", "Colossians 3:15-17", "Psalm 107:1"),
        "Rest" to listOf("Matthew 11:28-30", "Psalm 23", "Psalm 62:1-2", "Hebrews 4:9-11", "Mark 6:31"),
        "Love" to listOf("1 Corinthians 13:4-7", "1 John 4:7-12", "Romans 8:38-39", "John 15:9-13", "Zephaniah 3:17"),
        "Prayer" to listOf("Matthew 6:9-13", "Philippians 4:6", "Luke 18:1-8", "Romans 8:26", "James 5:16"),
        "Wisdom" to listOf("James 1:5", "Proverbs 3:5-6", "Proverbs 2:1-6", "Psalm 90:12", "Colossians 1:9-10"),
        "Identity" to listOf("2 Corinthians 5:17", "1 John 3:1-2", "Psalm 139:13-16", "Galatians 2:20", "Ephesians 1:3-7"),
        "Joy" to listOf("Nehemiah 8:10", "Philippians 4:4", "Psalm 16:11", "John 15:11", "Habakkuk 3:17-18"),
        "Trust" to listOf("Proverbs 3:5-6", "Psalm 56:3-4", "Isaiah 26:4", "Psalm 37:3-5", "Jeremiah 17:7-8"),
        "Healing" to listOf("Psalm 147:3", "Isaiah 53:4-5", "Jeremiah 17:14", "James 5:14-15", "Psalm 30:2")
    )

    val bySeason: Map<LiturgicalSeason, List<String>> = mapOf(
        LiturgicalSeason.ADVENT to listOf("Isaiah 9:2-7", "Isaiah 40:1-5", "Luke 1:26-38", "Micah 5:2-5", "Luke 1:46-55"),
        LiturgicalSeason.CHRISTMAS to listOf("Luke 2:1-20", "John 1:1-14", "Matthew 1:18-25", "Galatians 4:4-7"),
        LiturgicalSeason.EPIPHANY to listOf("Matthew 2:1-12", "Isaiah 60:1-6", "John 2:1-11"),
        LiturgicalSeason.LENT to listOf("Psalm 51:1-12", "Joel 2:12-13", "Matthew 4:1-11", "Isaiah 58:6-9", "Luke 15:11-24"),
        LiturgicalSeason.HOLY_WEEK to listOf("Isaiah 53:3-6", "John 13:1-15", "Mark 14:32-42", "Philippians 2:5-11", "Luke 23:33-46"),
        LiturgicalSeason.EASTER to listOf("Matthew 28:1-10", "John 20:11-18", "Luke 24:13-35", "1 Corinthians 15:51-58", "1 Peter 1:3-5"),
        LiturgicalSeason.PENTECOST to listOf("Acts 2:1-21", "John 14:15-17", "Galatians 5:22-25")
    )

    /**
     * Today's passage: a season's reading on its feast and season days (for traditions that
     * follow the church year), else one from the person's topics, else null (the caller uses the
     * Verse of the Day or the classic's text). Stable within a day, rotating across days.
     */
    fun pick(date: LocalDate, topics: Collection<String>, less: Collection<String>, day: LiturgicalDay?): ScriptureReference? {
        val seasonal = day?.season?.let { bySeason[it] }
        val pool = when {
            seasonal != null && (day.feast != null || date.dayOfWeek == java.time.DayOfWeek.SUNDAY || topics.isEmpty()) -> seasonal
            else -> topics.filter { it !in less }.flatMap { byTopic[it].orEmpty() }
        }
        if (pool.isEmpty()) return null
        val index = Math.floorMod(date.toEpochDay(), pool.size.toLong()).toInt()
        return ScriptureReferenceParser.parse(pool[index])
    }

    /** The topics a passage was listed under, for "More / Less like this". */
    fun topicsOf(reference: ScriptureReference): Set<String> =
        byTopic.filterValues { list -> list.any { ScriptureReferenceParser.parse(it) == reference } }.keys
}
