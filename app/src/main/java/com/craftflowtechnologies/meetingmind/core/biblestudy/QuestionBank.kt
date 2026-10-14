package com.craftflowtechnologies.meetingmind.core.biblestudy

import com.craftflowtechnologies.meetingmind.core.scripture.BibleBook
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReference

/**
 * The curated questions behind every guided session (ONE_PLACE §3.2). They are what the session asks
 * when there is no model or no connection, so it always works. They ask; they never answer.
 */
object QuestionBank {

    private val observe = listOf(
        "What word or phrase stands out to you, and why?",
        "Who is speaking in this passage, and who are they speaking to?",
        "What does this passage say about God?",
        "What does it say about people?",
        "Is there anything here that surprises or puzzles you?",
        "What is repeated, or what seems to matter most to the writer?",
        "What is happening, and what has just happened before it?",
        "What would you notice if you were reading this for the first time?"
    )

    private val apply = listOf(
        "Is there something here to believe, to stop, or to start?",
        "What is one small thing you could do today because of this passage?",
        "Who in your life could you share this with, or pray for because of it?",
        "Where does this meet something you are facing right now?",
        "What would change if you took this passage at its word this week?",
        "Is there a promise here to hold on to, or a command to follow?"
    )

    private val verse = listOf(
        "What does this verse say, in your own words?",
        "Which word in this verse would you underline?",
        "How does this verse connect to the one before it?",
        "What does this verse tell you about God, or about people?",
        "What question would you like to ask about this verse?"
    )

    private val needObserve = mapOf(
        Need.ANXIOUS to listOf("What does this passage invite you to do with what is weighing on you?", "What is God like in these verses?"),
        Need.GRATEFUL to listOf("What in this passage is there to give thanks for?", "Who or what does it point your thanks toward?"),
        Need.GRIEVING to listOf("Which words feel closest to what you are carrying?", "How does this passage treat sorrow? Does it hurry past it?"),
        Need.DECISION to listOf("What does this passage say about how to choose well?", "Who does it say to ask, and what does it say to trust?"),
        Need.LONELY to listOf("What does this passage say about who is with you?", "Which words speak most directly to being alone?"),
        Need.TEMPTED to listOf("What does this passage say about where temptation comes from, and how to meet it?", "What help does it point to?"),
        Need.TIRED to listOf("What does this passage say about rest, and where does it come from?", "What does it ask you to put down?"),
        Need.JOYFUL to listOf("What is the joy in this passage rooted in?", "What would it look like to share this joy today?")
    )

    private val needApply = mapOf(
        Need.ANXIOUS to "What is one worry you could name and hand over today?",
        Need.GRATEFUL to "Who could you thank today, out loud or in writing?",
        Need.GRIEVING to "What would be a gentle thing to do for yourself or someone else today?",
        Need.DECISION to "What is the next small step, and who could you ask for advice?",
        Need.LONELY to "Is there one person you could reach out to today?",
        Need.TEMPTED to "What is one practical step that would make this easier to resist?",
        Need.TIRED to "What is one thing you could set down, or one pause you could take today?",
        Need.JOYFUL to "How could you pass this joy on today?"
    )

    private val needPrayer = mapOf(
        Need.ANXIOUS to "Lord, you know what is on my mind. I give it to you, one thing at a time. Help me rest in your care. Amen.",
        Need.GRATEFUL to "Lord, thank you. For what I can name and what I have forgotten to notice. Keep my heart open to your goodness. Amen.",
        Need.GRIEVING to "Lord, you are near to the brokenhearted. Stay close to me in this sorrow, and hold what I cannot. Amen.",
        Need.DECISION to "Lord, I need wisdom. Show me what is true, and give me the courage to take the next step. Amen.",
        Need.LONELY to "Lord, you are with me even when no one else is. Make me aware of your presence, and bring me people to walk with. Amen.",
        Need.TEMPTED to "Lord, you know where I am weak. Be my strength, and show me the way out. Amen.",
        Need.TIRED to "Lord, I am tired. Give me your rest, and teach me to walk at your pace. Amen.",
        Need.JOYFUL to "Lord, thank you for this joy. Let it point me to you, and let me share it freely. Amen."
    )

    private const val GENERIC_PRAYER = "Lord, thank you for your word. Help me to understand it, and to live it today. Amen."
    private const val GENERIC_LIVE = "Choose one small thing from this reading to carry into today, and do it."

    private val needLive = mapOf(
        Need.ANXIOUS to "Each time worry returns today, say one line from this passage instead.",
        Need.GRATEFUL to "Tell one person today what you are thankful for about them.",
        Need.GRIEVING to "Be gentle with yourself today. Let one kind thing be enough.",
        Need.DECISION to "Write down the decision in one sentence, and the next step in another.",
        Need.LONELY to "Send one message to someone today, however small.",
        Need.TEMPTED to "Decide now what you will do when the moment comes.",
        Need.TIRED to "Take ten minutes today with nothing to achieve.",
        Need.JOYFUL to "Share what made you glad with someone today."
    )

    /** [count] questions of [type] for a session. Stable for a given [seed], so a step does not change when revisited. */
    fun questions(type: StepType, need: Need? = null, seed: Int = 0, count: Int = 1, ordinal: Int = 0): List<String> {
        val base = when (type) {
            StepType.OBSERVE -> (need?.let { needObserve[it] }.orEmpty()) + observe
            StepType.APPLY -> listOfNotNull(need?.let { needApply[it] }) + apply
            StepType.VERSE -> verse
            else -> emptyList()
        }
        if (base.isEmpty()) return emptyList()
        return List(count.coerceAtMost(base.size)) { i -> base[Math.floorMod(seed + ordinal * 2 + i, base.size)] }
    }

    fun question(type: StepType, need: Need?, seed: Int, ordinal: Int): String =
        questions(type, need, seed, 1, ordinal).firstOrNull() ?: ""

    fun prayer(need: Need?): String = need?.let { needPrayer[it] } ?: GENERIC_PRAYER
    fun liveIt(need: Need?): String = need?.let { needLive[it] } ?: GENERIC_LIVE

    /** What the prayer step says to the person who wants to pray in their own words. */
    const val PRAYER_PROMPT = "Pray in your own words, or use this one."

    /** A stable number from a passage, so its questions are the same each time. */
    fun seedFor(passage: ScriptureReference): Int = (passage.usfm.hashCode() * 31 + passage.chapter * 7 + (passage.verseStart ?: 0)) and 0x7fffffff
}

/**
 * Two plain lines for the Today card and the "Why it matters" step when there is no model: what kind of
 * writing this is, and what it is about. They describe the passage's place in the Bible, not what it
 * means for the reader.
 */
object WhyLines {

    private val gospels = setOf("MAT", "MRK", "LUK", "JHN")
    private val law = setOf("GEN", "EXO", "LEV", "NUM", "DEU")
    private val history = setOf("JOS", "JDG", "RUT", "1SA", "2SA", "1KI", "2KI", "1CH", "2CH", "EZR", "NEH", "EST")
    private val wisdom = setOf("JOB", "PRO", "ECC", "SNG")
    private val prophets = setOf("ISA", "JER", "LAM", "EZK", "DAN", "HOS", "JOL", "AMO", "OBA", "JON", "MIC", "NAM", "HAB", "ZEP", "HAG", "ZEC", "MAL")

    fun kindLine(book: BibleBook): String = when {
        book.usfm == "PSA" -> "A psalm: honest prayer and praise you can borrow as your own."
        book.usfm in gospels -> "From the Gospels: Jesus, in what he did and what he said."
        book.usfm == "ACT" -> "From Acts: the early church finding its feet."
        book.usfm == "REV" -> "From Revelation: a vision meant to give hope."
        book.usfm in law -> "From the first books of the Bible, where the story of God and his people begins."
        book.usfm in history -> "From the story of God's people through the Old Testament."
        book.usfm in wisdom -> "From the Bible's wisdom writing, about how to live well."
        book.usfm in prophets -> "From the prophets, who spoke to God's people in hard and hopeful times."
        book.isNewTestament -> "From a letter to the early church, written to real people with real questions."
        else -> "From the Old Testament."
    }

    /** [tags] are the rotation's theme tags, if any ("Anxiety", "Rest"). */
    fun lines(reference: ScriptureReference, tags: Set<String> = emptySet(), need: NeedPassage? = null): List<String> {
        if (need != null) return listOf(need.why)
        val theme = tags.firstOrNull()?.let { "A reading on ${it.lowercase()}." } ?: "A short reading, with a few gentle questions to think it over."
        return listOf(kindLine(reference.book), theme)
    }
}
