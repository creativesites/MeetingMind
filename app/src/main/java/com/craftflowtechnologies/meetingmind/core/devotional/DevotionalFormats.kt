package com.craftflowtechnologies.meetingmind.core.devotional

/**
 * The shapes a devotional can take. Each tells the writer how to build the page; the person
 * chooses which ones rotate, or fixes one. Formats rooted in one tradition say so, and stay
 * available to everyone.
 */
enum class DevotionalFormat(val label: String, val description: String, val guidance: String, val tradition: String? = null) {
    REFLECTION("Reflection", "A warm reflection on the passage",
        "A reflection on the passage: what it says, what it means, what it asks of the reader today."),
    LECTIO_DIVINA("Lectio Divina", "Read, meditate, pray, rest",
        "Guide Lectio Divina in four movements with those headings woven into the reflection paragraphs: Lectio (read slowly, notice a word or phrase), Meditatio (ponder it), Oratio (respond in prayer), Contemplatio (rest in God's presence). Keep instructions gentle and brief.",
        "Benedictine and contemplative"),
    SOAP("SOAP", "Scripture, Observation, Application, Prayer",
        "Use SOAP: reflection paragraphs are the Observation (what the text says in context); the application items are the Application; the prayer is the Prayer. Refer to the Scripture by reference."),
    PRAY("P.R.A.Y.", "Praise, Repent, Ask, Yield",
        "Shape it as P.R.A.Y.: reflection paragraphs move through Praise (who God is in this passage), Repent (where we fall short), Ask (for ourselves and others), Yield (surrender to God's will). Gentle, never guilt-driven."),
    DAILY_EXAMEN("Daily Examen", "Look back on the day with God",
        "Guide an Ignatian Examen: become aware of God's presence, review the day with gratitude, pay attention to emotions, choose one feature of the day to pray from, look toward tomorrow. Invite, never judge.",
        "Ignatian"),
    DAILY_OFFICE("Daily Office", "Psalm, reading, prayers at set hours",
        "Shape it like a short office: an opening sentence of praise, the Psalm or reading (by reference), a brief reflection, intercessions as the application items, and a collect-style prayer.",
        "Anglican, Catholic, Orthodox"),
    CATECHISM("Catechism", "One question, one answer, explored",
        "Take one catechism question relevant to the passage from the reader's tradition (name the catechism accurately — e.g. Westminster Shorter, Heidelberg, Catechism of the Catholic Church — and do not quote its answer verbatim unless certain; paraphrase and name the source), and explore it through the passage."),
    CREED("Creed meditation", "One line of the Creed, slowly",
        "Meditate on one line of the Apostles' or Nicene Creed that connects with the passage: what Christians have meant by it, and how it shapes today."),
    HYMN_STORY("Hymn story", "The story behind a hymn",
        "Tell the story of one well-known public-domain hymn whose theme meets the passage (who wrote it, when, why — only facts you are sure of; say 'tradition holds' where uncertain). Do not quote more than one short line of any hymn."),
    BIBLE_CHARACTER("Bible character", "Walk with one person from Scripture",
        "Follow one person in Scripture connected to the passage: their story (by reference), their struggle, what God did, and what the reader can learn."),
    WORD_STUDY("Word study", "One word from the original language",
        "Explore one key word from the passage in its original language (Hebrew or Greek): its root sense and uses elsewhere in Scripture, with care — only well-established meanings, no invented etymologies."),
    PSALM_TO_PRAY("Psalm to pray", "Pray a Psalm line by line",
        "Guide the reader to pray a Psalm (choose one that fits if the passage is not a Psalm, and name it): take it section by section, turning each into the reader's own prayer."),
    BREATH_PRAYER("Breath prayer", "A short prayer to carry all day",
        "Offer one breath prayer drawn from the passage (a phrase to breathe in, a phrase to breathe out), explain it briefly, and suggest moments in the day to return to it. Keep it short.",
        "Contemplative"),
    CHURCH_HISTORY("Church history", "A moment from the church's story",
        "Tell one true moment from church history that illuminates the passage — a person, date and place you are certain of; if unsure of a detail, leave it out. Then bring it to today."),
    QUIET("Quiet", "Few words, much space",
        "Very few words: one short paragraph on the passage, one question, and silence. Leave room."),
    FAMILY("Family", "For parents and children together",
        "Write for a family reading together with children: simple words, a short story or picture, one question each for kids and adults, a one-sentence prayer everyone can say."),
    COUPLES("Couples", "For two people to read together",
        "Write for a couple reading together: a reflection, one question to discuss with each other, and a prayer to pray for one another.");

    companion object {
        val DEFAULT_ROTATION = setOf(REFLECTION, LECTIO_DIVINA, SOAP, PRAY, BIBLE_CHARACTER, WORD_STUDY, PSALM_TO_PRAY, BREATH_PRAYER, HYMN_STORY)

        /**
         * Today's format: from [pool], never the same as [last] when there is a choice, and the one
         * unused for longest (per [recent], newest first) so every format comes round in turn.
         */
        fun pick(pool: Set<DevotionalFormat>, dayIndex: Long, last: DevotionalFormat?, recent: List<DevotionalFormat> = emptyList()): DevotionalFormat {
            val list = entries.filter { it in pool }.ifEmpty { listOf(REFLECTION) }
            if (list.size == 1) return list.first()
            val candidates = list.filter { it != last }
            fun age(f: DevotionalFormat) = recent.indexOf(f).let { if (it < 0) Int.MAX_VALUE else it }
            val oldest = candidates.maxOf { age(it) }
            val tied = candidates.filter { age(it) == oldest }
            return tied[Math.floorMod(dayIndex, tied.size.toLong()).toInt()]
        }
    }
}

/** Who it's written for. */
enum class DevotionalAudience(val label: String, val guidance: String) {
    ADULT("Adult", "an adult reader"),
    NEW_BELIEVER("New to faith", "someone new to faith — explain terms, assume no background, warm and welcoming"),
    TEEN("Teen", "a teenager — direct, honest, real-life, no talking down"),
    STUDENT("Student", "a university student — thoughtful, intellectually honest"),
    LEADER("Leader", "a church leader or small-group leader — depth, and something they could share with others"),
    FAMILY("Family", "a family with children reading together"),
    COUPLE("Couple", "a couple reading together")
}

/** How rich the language is. */
enum class ReadingLevel(val label: String, val guidance: String) {
    SIMPLE("Simple", "short sentences and everyday words"),
    STANDARD("Standard", "clear, natural prose"),
    RICH("Rich", "rich, literary prose with depth")
}

/** A starting point that sets tradition, formats and voice together. Everything stays editable. */
enum class TraditionPreset(val label: String, val description: String) {
    CATHOLIC("Catholic", "Liturgical seasons, Lectio Divina, the Examen, the Creed"),
    ANGLICAN("Anglican", "The Daily Office, the Psalms, the church year"),
    ORTHODOX("Orthodox", "The Psalms, the Fathers, prayer of the heart"),
    REFORMED("Reformed", "Catechism, careful exposition, Psalms"),
    EVANGELICAL("Evangelical", "Scripture first, SOAP, application"),
    PENTECOSTAL("Pentecostal / Charismatic", "The Spirit's work, expectant prayer, testimony"),
    CONTEMPLATIVE("Contemplative", "Silence, breath prayer, Lectio Divina"),
    FAMILY("Family", "Short, simple, for parents and children together");

    fun applyTo(p: DevotionalProfile): DevotionalProfile = when (this) {
        CATHOLIC -> p.copy(tradition = Tradition.CATHOLIC, tone = DevotionalTone.PASTOR, audience = DevotionalAudience.ADULT,
            formats = setOf(DevotionalFormat.REFLECTION, DevotionalFormat.LECTIO_DIVINA, DevotionalFormat.DAILY_EXAMEN, DevotionalFormat.CREED, DevotionalFormat.CATECHISM, DevotionalFormat.BIBLE_CHARACTER, DevotionalFormat.CHURCH_HISTORY), preset = this)
        ANGLICAN -> p.copy(tradition = Tradition.ANGLICAN, tone = DevotionalTone.TEACHER, audience = DevotionalAudience.ADULT,
            formats = setOf(DevotionalFormat.DAILY_OFFICE, DevotionalFormat.PSALM_TO_PRAY, DevotionalFormat.REFLECTION, DevotionalFormat.CREED, DevotionalFormat.HYMN_STORY, DevotionalFormat.CHURCH_HISTORY), preset = this)
        ORTHODOX -> p.copy(tradition = Tradition.ORTHODOX, tone = DevotionalTone.POET, audience = DevotionalAudience.ADULT,
            formats = setOf(DevotionalFormat.PSALM_TO_PRAY, DevotionalFormat.BREATH_PRAYER, DevotionalFormat.REFLECTION, DevotionalFormat.CREED, DevotionalFormat.CHURCH_HISTORY, DevotionalFormat.BIBLE_CHARACTER), preset = this)
        REFORMED -> p.copy(tradition = Tradition.REFORMED, tone = DevotionalTone.TEACHER, audience = DevotionalAudience.ADULT,
            formats = setOf(DevotionalFormat.REFLECTION, DevotionalFormat.CATECHISM, DevotionalFormat.WORD_STUDY, DevotionalFormat.PSALM_TO_PRAY, DevotionalFormat.SOAP, DevotionalFormat.HYMN_STORY), preset = this)
        EVANGELICAL -> p.copy(tradition = Tradition.EVANGELICAL, tone = DevotionalTone.FRIEND, audience = DevotionalAudience.ADULT,
            formats = setOf(DevotionalFormat.REFLECTION, DevotionalFormat.SOAP, DevotionalFormat.PRAY, DevotionalFormat.BIBLE_CHARACTER, DevotionalFormat.WORD_STUDY, DevotionalFormat.HYMN_STORY), preset = this)
        PENTECOSTAL -> p.copy(tradition = Tradition.PENTECOSTAL, tone = DevotionalTone.PASTOR, audience = DevotionalAudience.ADULT,
            formats = setOf(DevotionalFormat.REFLECTION, DevotionalFormat.PRAY, DevotionalFormat.BIBLE_CHARACTER, DevotionalFormat.PSALM_TO_PRAY, DevotionalFormat.SOAP), preset = this)
        CONTEMPLATIVE -> p.copy(tone = DevotionalTone.POET, audience = DevotionalAudience.ADULT,
            formats = setOf(DevotionalFormat.LECTIO_DIVINA, DevotionalFormat.BREATH_PRAYER, DevotionalFormat.QUIET, DevotionalFormat.DAILY_EXAMEN, DevotionalFormat.PSALM_TO_PRAY), preset = this)
        FAMILY -> p.copy(tone = DevotionalTone.FRIEND, audience = DevotionalAudience.FAMILY, readingLevel = ReadingLevel.SIMPLE, minutes = 3,
            formats = setOf(DevotionalFormat.FAMILY, DevotionalFormat.BIBLE_CHARACTER, DevotionalFormat.REFLECTION), preset = this)
    }
}

/** A multi-day series: one passage a day, each day continuing the last. */
data class DevotionalSeries(val id: String, val title: String, val description: String, val passages: List<String>) {
    companion object {
        val catalog = listOf(
            DevotionalSeries("philippians7", "7 Days in Philippians", "Joy that doesn't depend on circumstances",
                listOf("Philippians 1:3-11", "Philippians 1:12-26", "Philippians 2:1-11", "Philippians 2:12-18", "Philippians 3:7-14", "Philippians 4:4-9", "Philippians 4:10-20")),
            DevotionalSeries("fruit", "Fruit of the Spirit", "Ten days: the fruit, then one each",
                listOf("Galatians 5:22-23", "1 Corinthians 13:4-7", "John 15:9-11", "John 14:27", "James 5:7-11", "Ephesians 4:32", "Luke 6:35-36", "Lamentations 3:22-24", "Matthew 11:28-30", "Proverbs 16:32")),
            DevotionalSeries("psalms10", "Ten Psalms to Pray", "The prayer book of the Bible",
                listOf("Psalm 1", "Psalm 8", "Psalm 16", "Psalm 23", "Psalm 27", "Psalm 46", "Psalm 51", "Psalm 91", "Psalm 103", "Psalm 139")),
            DevotionalSeries("sermon5", "The Sermon on the Mount", "Jesus' way of life, in five days",
                listOf("Matthew 5:1-12", "Matthew 5:13-20", "Matthew 6:5-15", "Matthew 6:25-34", "Matthew 7:24-29")),
            DevotionalSeries("iam7", "The \"I Am\" Sayings", "Seven ways Jesus described himself",
                listOf("John 6:35-40", "John 8:12", "John 10:7-10", "John 10:11-18", "John 11:25-26", "John 14:6", "John 15:1-8")),
            DevotionalSeries("advent", "Advent", "Waiting and hope through December",
                listOf("Isaiah 9:2-7", "Isaiah 40:1-5", "Isaiah 11:1-10", "Micah 5:2-5", "Luke 1:26-38", "Luke 1:39-56", "Luke 1:67-79", "Matthew 1:18-25", "Luke 2:1-7", "Luke 2:8-20", "John 1:1-14", "Galatians 4:4-7")),
            DevotionalSeries("lent", "Lent", "Forty days toward Easter, in fourteen readings",
                listOf("Joel 2:12-13", "Matthew 4:1-11", "Psalm 51:1-12", "Isaiah 58:6-9", "Luke 15:11-24", "John 3:14-21", "Psalm 130", "John 12:20-33", "Philippians 2:5-11", "Isaiah 53:3-6", "John 13:1-15", "Mark 14:32-42", "Luke 23:33-46", "Matthew 28:1-10"))
        )

        /** A series through one book of the Bible, a chapter a day. */
        fun throughBook(book: String, chapters: Int) = DevotionalSeries("book:$book", "Through $book", "A chapter a day", (1..chapters).map { "$book $it" })
    }
}

/** Where someone is in a series. */
data class SeriesProgress(val seriesId: String, val title: String, val passages: List<String>, val startedEpochDay: Long) {
    fun dayIndex(today: Long) = (today - startedEpochDay).toInt()
    fun passageFor(today: Long) = passages.getOrNull(dayIndex(today))
    fun finished(today: Long) = dayIndex(today) >= passages.size
}
