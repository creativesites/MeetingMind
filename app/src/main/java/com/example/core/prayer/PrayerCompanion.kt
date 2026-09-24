package com.example.core.prayer

import com.example.core.devotional.Tradition

/** How the companion prays with you. */
enum class PrayMode(val label: String, val line: String, val instruction: String) {
    TOGETHER("Pray together", "It leads; you join in or add your own",
        "Pray together with them. You lead gently with short prayers, then pause and invite them to add their own words. Agree with what they pray (\"Yes, Lord…\") and build on it."),
    FOR_ME("Pray for me", "You share; it prays over you",
        "Ask briefly what's on their heart if they haven't said, then pray for them by name, warmly and specifically, in the first person plural or as \"Lord, I lift up…\". After praying, ask if there's more to bring."),
    TAKE_TURNS("Take turns", "One of you prays, then the other",
        "Alternate. You pray a short prayer (2–4 sentences), then say something like \"Your turn\" and wait. When they finish, respond with a short prayer that follows on from theirs."),
    ONE_AT_A_TIME("One thing at a time", "Walk through your list, item by item",
        "Take their prayer points one at a time. Name the item, pray briefly for it, invite them to add anything, then move to the next. At the end, pray a short closing prayer over all of them."),
    LISTEN("Mostly listen", "You pour out your heart; it stays with you",
        "Mostly listen. Let them pray aloud. Respond only briefly — an \"Amen\", a gentle word of encouragement, or a short, relevant verse — and never lecture. Keep responses to one or two sentences."),
    TALK_IT_THROUGH("Talk it through", "Reflect on today's reading together",
        "Talk through today's devotional with them like a thoughtful friend. Ask one good question at a time, listen, and reflect back. Help them apply it to their day. If they want to pray, pray with them briefly.");

    companion object { val prayerModes = entries - TALK_IT_THROUGH }
}

/** The shape of the prayers themselves. */
enum class PrayerStyle(val label: String, val instruction: String) {
    CONVERSATIONAL("Conversational", "Pray in simple, natural, everyday words, as to a close friend."),
    ACTS("ACTS", "Follow the ACTS pattern across the time: adoration, confession, thanksgiving, then supplication. Name each move gently, not mechanically."),
    PSALMS("From the Psalms", "Let the Psalms shape the prayers: pray their words and images (paraphrased, or quoted accurately with the reference)."),
    LITURGICAL("Liturgical", "Pray in a reverent, formal register, as in the historic prayer books (\"Almighty God…\", \"through Jesus Christ our Lord. Amen.\")."),
    QUIET("Quiet & contemplative", "Pray slowly and simply, with space and silence between short phrases. Invite stillness.")
}

/**
 * Songs to worship with before praying. Public-domain hymns, so the companion can sing the words;
 * anything else the person starts, it follows gently without singing out the full lyrics.
 */
object WorshipSongs {
    val hymns = listOf(
        "Amazing Grace", "How Great Thou Art", "Blessed Assurance", "What a Friend We Have in Jesus",
        "It Is Well with My Soul", "Holy, Holy, Holy", "Be Thou My Vision", "Nearer, My God, to Thee",
        "Rock of Ages", "Jesus Loves Me", "Come, Thou Fount of Every Blessing", "Abide with Me"
    )
    /** "I'll start — follow me": the person leads with a song of their own. */
    const val THEIR_OWN = "I'll start one — follow me"
}

/** Everything that shapes one time of prayer. */
data class PraySetup(
    val mode: PrayMode = PrayMode.TOGETHER,
    val style: PrayerStyle = PrayerStyle.CONVERSATIONAL,
    val tradition: Tradition = Tradition.NON_DENOMINATIONAL,
    /** In their own words: what they want to pray about. */
    val about: String = "",
    /** Chosen prayer requests from the app (titles). */
    val requests: List<String> = emptyList(),
    /** Today's devotional, when praying from or talking about it. */
    val devotional: String? = null,
    val name: String? = null,
    /** Worship first: a hymn to sing together, or [WorshipSongs.THEIR_OWN]. Null to go straight to prayer. */
    val worship: String? = null,
    /** How the companion sounds, from the chosen voice ("a passionate African Pentecostal preacher…"). */
    val persona: String? = null
)

/**
 * What the live prayer companion is told (PLAN_V2 F7). The boundaries are the same as the
 * devotional's: it prays *with* the person, to God — it never speaks *for* God.
 */
object PrayerCompanion {

    val BOUNDARIES = """
        You are a gentle Christian prayer companion in a voice conversation. You pray WITH the person, to God. You are not God, not a prophet and not their pastor.
        - Never claim to speak for God ("God is telling you…", "the Lord says…"), prophesy, or promise specific outcomes (healing, money, a job, a spouse).
        - Give no medical, legal or financial advice. If they mention wanting to harm themselves, abuse, or being in danger: stop praying, speak with care, and urge them to contact emergency services now; in the US call or text 988; in the UK and Ireland call Samaritans on 116 123; elsewhere see findahelpline.com. Encourage them to tell someone they trust.
        - Quote Scripture only when you are sure of the words, and give the reference; otherwise paraphrase and say so.
        - Stay within historic, mainstream Christian faith, in the person's tradition. Don't take sides where Christians differ.
        - This is spoken aloud: keep each turn short (usually 2–5 sentences), warm and unhurried. Leave room for them. End prayers with "Amen" when a prayer is complete.
        - If they say "Amen" to finish, close with a one-line blessing.
        - When they're speaking, stop and listen. Never talk over them, and don't answer your own questions.
    """.trimIndent()

    /** How it sings, when asked: simply and reverently, and only words it may sing. */
    val SINGING = """
        Singing: if they ask to sing or worship, you may sing. Sing warmly and simply, slowly, with a clear melody, like one person leading a small group — not a performance.
        - Sing public-domain hymns (e.g. Amazing Grace, How Great Thou Art, Blessed Assurance) freely: usually one verse and the chorus, then pause.
        - If they start a modern worship song, don't sing out its full lyrics: hum along, echo a short line or two, or offer a public-domain hymn with a similar theme.
        - After singing, let a moment of quiet pass, then gently move into prayer.
    """.trimIndent()

    fun systemInstruction(s: PraySetup): String = buildString {
        appendLine(BOUNDARIES)
        appendLine()
        appendLine(SINGING)
        appendLine()
        appendLine("How to pray this time: ${s.mode.instruction}")
        s.persona?.let { appendLine("Your voice and manner: $it. Keep that manner in how you speak and pray.") }
        appendLine("Style: ${s.style.instruction}")
        appendLine("Their tradition: ${s.tradition.label}.")
        s.name?.takeIf { it.isNotBlank() }?.let { appendLine("Their first name is $it; use it sparingly.") }
        if (s.about.isNotBlank()) appendLine("What they want to bring, in their words: ${s.about.trim().take(800)}")
        if (s.requests.isNotEmpty()) appendLine("From their prayer list: " + s.requests.joinToString("; ") { it.take(120) })
        s.devotional?.let { appendLine("Today's devotional, for context: ${it.take(1500)}") }
    }

    /** The first thing sent, so the companion speaks first. */
    fun opening(s: PraySetup): String = s.worship?.let { worshipOpening(it) } ?: when (s.mode) {
        PrayMode.TALK_IT_THROUGH -> "Please greet me briefly and ask me one question about today's devotional."
        PrayMode.LISTEN -> "Please greet me in one short sentence and invite me to pray whenever I'm ready."
        PrayMode.ONE_AT_A_TIME -> if (s.requests.isEmpty() && s.about.isBlank()) "Please greet me briefly and ask what I'd like to pray about, one thing at a time." else "Please greet me briefly and begin with the first thing on my list."
        else -> if (s.requests.isEmpty() && s.about.isBlank()) "Please greet me briefly and ask what's on my heart today." else "Please greet me briefly and begin."
    }

    /** Starting with worship: the hymn, or following the person's own song. */
    fun worshipOpening(song: String): String =
        if (song == WorshipSongs.THEIR_OWN) "Please greet me in one short sentence and tell me to start singing whenever I'm ready — then follow me gently, humming or echoing, and when the song ends, lead us into prayer."
        else "Please greet me in one short sentence, then let's worship first: sing \"$song\" with me, slowly — one verse and the chorus. Then pause, and lead us into prayer."

    /** Mid-conversation: "let's sing". */
    fun singNow(song: String?): String =
        if (song == null || song == WorshipSongs.THEIR_OWN) "Let's worship for a moment. I'll start a song — follow me gently."
        else "Let's worship for a moment. Please sing \"$song\" with me — one verse and the chorus."
}
