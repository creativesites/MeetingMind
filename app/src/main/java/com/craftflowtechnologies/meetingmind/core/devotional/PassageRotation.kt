package com.craftflowtechnologies.meetingmind.core.devotional

import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReference
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser
import java.time.LocalDate

/**
 * The default passage cycle (founder rule, 2026-10-09: generic by default, personal by invitation).
 *
 * A curated list of 400+ passages across the whole canon — law, history, psalms, wisdom, prophets,
 * gospels, Acts, letters and Revelation — woven together so neighbouring days differ in testament and
 * genre, and walked one step per day. It knows nothing about the reader. Each entry may carry theme
 * tags after a "|" so "less of" can leave a theme out.
 */
object PassageRotation {

    private val gospels = listOf(
        "Matthew 5:1-12|Joy", "Matthew 5:13-16", "Matthew 5:43-48|Love", "Matthew 6:5-15|Prayer", "Matthew 6:19-21", "Matthew 6:25-34|Anxiety",
        "Matthew 7:7-12|Prayer", "Matthew 7:24-27", "Matthew 9:35-38", "Matthew 11:28-30|Rest", "Matthew 13:1-9", "Matthew 13:31-33",
        "Matthew 13:44-46", "Matthew 14:22-33|Faith", "Matthew 16:13-20", "Matthew 18:12-14", "Matthew 20:1-16", "Matthew 22:34-40|Love",
        "Matthew 25:14-30", "Matthew 25:31-40|Generosity", "Matthew 28:16-20", "Mark 1:14-20", "Mark 1:35-39|Prayer", "Mark 2:1-12",
        "Mark 4:35-41|Trust", "Mark 5:25-34|Healing", "Mark 6:30-44", "Mark 8:27-35", "Mark 9:33-37", "Mark 10:13-16", "Mark 10:46-52",
        "Mark 12:28-34|Love", "Mark 14:3-9", "Luke 1:26-38", "Luke 1:46-55|Joy", "Luke 2:1-20", "Luke 2:41-52", "Luke 4:16-21", "Luke 5:1-11",
        "Luke 6:27-38|Generosity", "Luke 7:36-50|Forgiveness", "Luke 8:4-15", "Luke 10:25-37|Love", "Luke 10:38-42|Rest", "Luke 11:1-13|Prayer",
        "Luke 12:22-34|Anxiety", "Luke 13:18-21", "Luke 14:12-24", "Luke 15:1-10", "Luke 15:11-32|Forgiveness", "Luke 17:11-19|Gratitude",
        "Luke 18:9-14", "Luke 19:1-10", "Luke 24:13-35", "John 1:1-14", "John 2:1-11|Joy", "John 3:1-17", "John 4:1-30", "John 6:25-40",
        "John 8:1-11|Forgiveness", "John 9:1-25", "John 10:1-18", "John 11:17-44|Grief", "John 12:1-8", "John 13:1-17", "John 14:1-14|Peace",
        "John 15:1-17|Love", "John 17:1-5", "John 20:11-18", "John 21:1-14"
    )

    private val oldTestamentStories = listOf(
        "Genesis 1:1-2:3|Wonder", "Genesis 2:4-25", "Genesis 3:1-15", "Genesis 6:9-22", "Genesis 9:8-17", "Genesis 12:1-9|Trust", "Genesis 15:1-6",
        "Genesis 18:1-15", "Genesis 22:1-14", "Genesis 24:12-27", "Genesis 28:10-22", "Genesis 32:22-31", "Genesis 37:1-11", "Genesis 39:1-6",
        "Genesis 45:1-15|Forgiveness", "Genesis 50:15-21|Forgiveness", "Exodus 2:1-10", "Exodus 3:1-15", "Exodus 14:10-31", "Exodus 16:1-18",
        "Exodus 17:8-13", "Exodus 33:12-23", "Numbers 13:25-33", "Joshua 1:1-9|Courage", "Joshua 3:1-17", "Joshua 6:1-20", "Judges 6:11-24",
        "Judges 7:1-22", "Ruth 1:1-18|Family", "Ruth 2:1-12", "Ruth 4:13-17", "1 Samuel 1:9-20", "1 Samuel 3:1-10", "1 Samuel 16:1-13",
        "1 Samuel 17:32-50|Courage", "1 Samuel 20:1-17|Friendship", "2 Samuel 9:1-13", "2 Samuel 12:1-13", "1 Kings 3:3-14|Wisdom", "1 Kings 17:8-16",
        "1 Kings 18:20-39", "1 Kings 19:1-18|Rest", "2 Kings 5:1-14", "2 Kings 6:8-17", "2 Chronicles 7:11-16", "2 Chronicles 20:1-22",
        "Ezra 3:10-13", "Nehemiah 2:11-18", "Nehemiah 8:1-12|Joy", "Esther 4:9-17|Courage", "Daniel 1:8-20", "Daniel 3:13-28", "Daniel 6:10-23",
        "Jonah 1:1-17", "Jonah 3:1-10", "Jonah 4:1-11", "Genesis 21:1-7|Joy", "Exodus 12:1-14", "Deuteronomy 34:1-12", "Judges 4:4-16"
    )

    private val psalms = listOf(
        "Psalm 1", "Psalm 8|Wonder", "Psalm 13|Trust", "Psalm 16|Joy", "Psalm 19|Wonder", "Psalm 23|Rest", "Psalm 24", "Psalm 25:1-10",
        "Psalm 27|Courage", "Psalm 29", "Psalm 30|Joy", "Psalm 32|Forgiveness", "Psalm 33", "Psalm 34|Gratitude", "Psalm 36:5-9", "Psalm 37:1-11|Patience",
        "Psalm 40:1-5", "Psalm 42|Hope", "Psalm 46|Peace", "Psalm 47", "Psalm 51|Forgiveness", "Psalm 56:3-13", "Psalm 62|Rest", "Psalm 63:1-8",
        "Psalm 65", "Psalm 66:1-12", "Psalm 67", "Psalm 71:1-8", "Psalm 73:21-28", "Psalm 77:11-20", "Psalm 84|Joy", "Psalm 85:8-13|Peace",
        "Psalm 86:5-13", "Psalm 90|Wisdom", "Psalm 91|Trust", "Psalm 92:1-4", "Psalm 95", "Psalm 96", "Psalm 98|Joy", "Psalm 100|Gratitude",
        "Psalm 103|Gratitude", "Psalm 104:1-23|Wonder", "Psalm 105:1-8", "Psalm 107:1-9|Gratitude", "Psalm 111", "Psalm 113", "Psalm 116|Gratitude",
        "Psalm 118:1-14", "Psalm 119:9-16", "Psalm 119:105-112", "Psalm 121|Trust", "Psalm 122", "Psalm 124", "Psalm 126|Joy", "Psalm 127|Family",
        "Psalm 130|Hope", "Psalm 131|Rest", "Psalm 133|Friendship", "Psalm 136:1-9|Gratitude", "Psalm 139:1-18|Identity", "Psalm 145|Gratitude",
        "Psalm 146", "Psalm 147:1-11", "Psalm 148", "Psalm 150|Joy"
    )

    private val letters = listOf(
        "Romans 1:16-17", "Romans 5:1-5|Hope", "Romans 6:1-11", "Romans 8:1-11", "Romans 8:14-17|Identity", "Romans 8:18-27|Hope", "Romans 8:28-39|Love",
        "Romans 12:1-8|Purpose", "Romans 12:9-21|Love", "Romans 13:8-10", "Romans 14:1-12", "Romans 15:1-7", "1 Corinthians 1:18-31", "1 Corinthians 2:1-10",
        "1 Corinthians 3:5-9", "1 Corinthians 9:24-27", "1 Corinthians 10:12-13", "1 Corinthians 12:12-27", "1 Corinthians 13|Love", "1 Corinthians 15:1-11",
        "1 Corinthians 15:50-58", "2 Corinthians 1:3-7|Healing", "2 Corinthians 3:12-18", "2 Corinthians 4:5-18|Hope", "2 Corinthians 5:14-21", "2 Corinthians 8:1-9|Generosity",
        "2 Corinthians 12:7-10", "Galatians 2:15-21", "Galatians 3:23-29", "Galatians 5:1", "Galatians 5:13-26", "Galatians 6:1-10", "Ephesians 1:3-14",
        "Ephesians 2:1-10", "Ephesians 3:14-21|Love", "Ephesians 4:1-16", "Ephesians 4:25-32|Forgiveness", "Ephesians 5:1-2", "Ephesians 6:10-18|Courage",
        "Philippians 1:3-11|Gratitude", "Philippians 2:1-11", "Philippians 3:7-14", "Philippians 4:4-9|Peace", "Colossians 1:15-20", "Colossians 2:6-10",
        "Colossians 3:1-17", "1 Thessalonians 1:2-10", "1 Thessalonians 4:13-18|Hope", "1 Thessalonians 5:12-24|Gratitude", "2 Thessalonians 3:1-5", "1 Timothy 1:12-17",
        "1 Timothy 6:6-12", "2 Timothy 1:3-14", "2 Timothy 2:1-7", "2 Timothy 3:14-17", "2 Timothy 4:6-8", "Titus 2:11-14", "Philemon 1:4-16", "1 Peter 1:3-9|Hope",
        "1 Peter 2:4-10", "1 Peter 3:8-16", "1 Peter 4:7-11", "2 Peter 1:3-11", "1 John 1:5-10|Forgiveness", "1 John 3:1-3|Identity", "1 John 3:16-24", "1 John 4:7-21|Love",
        "1 John 5:1-5", "3 John 1:1-8", "Jude 1:20-25"
    )

    private val prophets = listOf(
        "Isaiah 6:1-8", "Isaiah 9:2-7", "Isaiah 11:1-9", "Isaiah 25:6-9", "Isaiah 30:15-18|Rest", "Isaiah 35|Joy", "Isaiah 40:1-11|Hope", "Isaiah 40:27-31|Patience",
        "Isaiah 41:8-13|Courage", "Isaiah 43:1-7|Identity", "Isaiah 43:18-21", "Isaiah 49:13-16", "Isaiah 52:7-10", "Isaiah 53:1-6", "Isaiah 55:1-13", "Isaiah 58:6-12|Generosity",
        "Isaiah 61:1-4", "Isaiah 65:17-25", "Jeremiah 1:4-10", "Jeremiah 17:5-10|Trust", "Jeremiah 18:1-6", "Jeremiah 29:4-14|Hope", "Jeremiah 31:31-34", "Jeremiah 33:1-9",
        "Lamentations 3:19-33|Hope", "Ezekiel 11:17-20", "Ezekiel 34:11-16", "Ezekiel 36:24-28", "Ezekiel 37:1-14|Hope", "Daniel 2:20-23", "Hosea 2:14-20", "Hosea 6:1-3",
        "Hosea 11:1-9|Love", "Joel 2:12-14", "Joel 2:21-27", "Amos 5:21-24", "Micah 4:1-5|Peace", "Micah 6:6-8", "Micah 7:18-20|Forgiveness", "Nahum 1:7", "Habakkuk 2:1-4",
        "Habakkuk 3:17-19|Joy", "Zephaniah 3:14-20|Joy", "Haggai 2:1-9", "Zechariah 4:1-10", "Zechariah 9:9-12", "Malachi 3:1-4", "Malachi 4:1-3", "Isaiah 12|Joy",
        "Jeremiah 9:23-24", "Ezekiel 47:1-12"
    )

    private val wisdomAndLaw = listOf(
        "Proverbs 1:1-7|Wisdom", "Proverbs 2:1-11|Wisdom", "Proverbs 3:1-12|Trust", "Proverbs 4:20-27", "Proverbs 8:1-21|Wisdom", "Proverbs 9:1-12", "Proverbs 10:1-12", "Proverbs 11:24-30|Generosity",
        "Proverbs 12:15-25", "Proverbs 15:1-4", "Proverbs 16:1-9|Purpose", "Proverbs 17:1-17|Friendship", "Proverbs 18:10-24", "Proverbs 19:17-21", "Proverbs 22:1-9", "Proverbs 24:3-6",
        "Proverbs 27:1-17", "Proverbs 31:10-31", "Job 1:13-22", "Job 19:23-27|Hope", "Job 38:1-11|Wonder", "Job 42:1-6", "Ecclesiastes 1:1-11", "Ecclesiastes 3:1-14", "Ecclesiastes 4:9-12|Friendship",
        "Ecclesiastes 5:10-20", "Ecclesiastes 9:7-10|Joy", "Ecclesiastes 11:1-6", "Ecclesiastes 12:1-7", "Song of Solomon 2:8-14|Love", "Song of Solomon 8:6-7|Love",
        "Exodus 20:1-17", "Exodus 19:3-8", "Leviticus 19:9-18|Generosity", "Leviticus 25:8-12", "Numbers 6:22-27", "Deuteronomy 4:29-40", "Deuteronomy 6:4-9|Family", "Deuteronomy 8:1-10",
        "Deuteronomy 10:12-21", "Deuteronomy 30:11-20", "Deuteronomy 31:1-8|Courage", "Deuteronomy 32:1-4", "Numbers 11:10-17", "Numbers 14:5-9", "Leviticus 16:29-31", "Exodus 34:4-9", "Deuteronomy 15:7-11|Generosity",
        "Exodus 23:1-9"
    )

    private val actsAndHebrews = listOf(
        "Acts 1:1-11", "Acts 2:1-13", "Acts 2:42-47", "Acts 3:1-10", "Acts 4:23-33|Courage", "Acts 5:17-32", "Acts 6:1-7", "Acts 7:54-60", "Acts 8:26-40", "Acts 9:1-19",
        "Acts 10:34-48", "Acts 11:19-26", "Acts 12:1-17", "Acts 13:1-3", "Acts 14:8-18", "Acts 16:6-15", "Acts 16:22-34", "Acts 17:22-31", "Acts 18:1-4", "Acts 20:17-35|Generosity",
        "Acts 26:12-23", "Acts 27:13-26", "Acts 28:30-31", "Hebrews 1:1-4", "Hebrews 2:14-18", "Hebrews 4:12-16", "Hebrews 10:19-25", "Hebrews 11:1-3|Faith", "Hebrews 11:8-16",
        "Hebrews 12:1-3|Patience", "Hebrews 13:1-8", "James 1:2-8|Patience", "James 1:17-27", "James 2:14-26", "James 3:1-12", "James 4:6-10", "James 5:13-18|Prayer",
        "Revelation 1:9-18", "Revelation 3:14-22", "Revelation 4:1-11", "Revelation 5:6-14", "Revelation 7:9-17", "Revelation 12:7-12", "Revelation 19:5-9", "Revelation 21:1-8|Hope",
        "Revelation 21:22-27", "Revelation 22:1-5", "Revelation 22:12-21", "Acts 15:1-11", "Hebrews 6:13-20|Hope"
    )

    private fun permute(items: List<String>, strideSeed: Int): List<String> {
        val n = items.size
        var s = strideSeed
        fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
        while (gcd(s, n) != 1) s++
        return List(n) { items[(it * s) % n] }
    }

    /** All entries, woven so each genre is spread evenly through the cycle. */
    val entries: List<String> by lazy {
        val groups = listOf(gospels, oldTestamentStories, psalms, letters, prophets, wisdomAndLaw, actsAndHebrews)
        groups.mapIndexed { g, list ->
            val shuffled = permute(list, (list.size * 0.38).toInt().coerceAtLeast(3))
            shuffled.mapIndexed { i, e -> Triple((i + 0.5) / shuffled.size + g * 1e-4, g, e) }
        }.flatten().sortedWith(compareBy({ it.first }, { it.second })).map { it.third }
    }

    val size: Int get() = entries.size

    /** The reference and the themes tagged on entry [index] (any integer; it wraps). */
    fun at(index: Long): Pair<ScriptureReference, Set<String>>? {
        val raw = entries[Math.floorMod(index, entries.size.toLong()).toInt()]
        val ref = ScriptureReferenceParser.parse(raw.substringBefore('|')) ?: return null
        val tags = raw.substringAfter('|', "").split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        return ref to tags
    }

    /**
     * Where [date] sits in the cycle. Consecutive days step by one entry, a different [variant]
     * ("another one") jumps far away.
     */
    fun indexFor(date: LocalDate, variant: Int = 0): Long = date.toEpochDay() + variant * 53L

    /**
     * The first passage from entry [index] onwards that is not in a theme the person asked for less of,
     * or a reference a [skip] says is unwanted. [weights] (topics/more-of, used only when the person has
     * invited personal touches) don't constrain the cycle here; see [DevotionalEngine].
     */
    fun next(index: Long, less: Collection<String>, skip: (ScriptureReference) -> Boolean = { false }): ScriptureReference? {
        for (step in 0 until entries.size) {
            val (ref, tags) = at(index + step) ?: continue
            if (less.any { it in tags } || TopicPassages.topicsOf(ref).any { it in less }) continue
            if (skip(ref)) continue
            return ref
        }
        return null
    }

    /** Whether [ref] is tagged with (or listed under) any of [themes]. */
    fun hasTheme(ref: ScriptureReference, themes: Collection<String>): Boolean {
        if (themes.isEmpty()) return false
        if (TopicPassages.topicsOf(ref).any { it in themes }) return true
        return entries.any { e -> e.substringAfter('|', "").split(',').any { it in themes } && ScriptureReferenceParser.parse(e.substringBefore('|')) == ref }
    }
}
