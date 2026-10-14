package com.craftflowtechnologies.meetingmind.core.create

/** A hand-written line to start from. Shown only on request and always labelled as a starter. */
data class CreateStarter(val vibe: CreateVibe, val text: String, val verseRef: String? = null)

/**
 * Curated starters for when there is no model (offline, no key). They are never swapped in for a
 * failed generation. Faith starters carry a reference only; the Bible library supplies the text.
 */
object CreateStarters {
    const val LABEL = "Starter — not written for you"

    val all: List<CreateStarter> = listOf(
        CreateStarter(CreateVibe.PRAYERFUL, "Lord, meet me where I am today.", "Psalm 145:18"),
        CreateStarter(CreateVibe.PRAYERFUL, "Quiet my heart. Hold what I can't."),
        CreateStarter(CreateVibe.PEACEFUL, "Not every storm is mine to calm. Some are mine to rest through.", "Psalm 46:10"),
        CreateStarter(CreateVibe.PEACEFUL, "Breathe in. You are held."),
        CreateStarter(CreateVibe.GRATEFUL, "Today I'm thankful for the small things I almost missed.", "Psalm 103:2"),
        CreateStarter(CreateVibe.GRATEFUL, "Thank you for every ordinary morning."),
        CreateStarter(CreateVibe.JOYFUL, "Joy isn't waiting for the perfect day. It's showing up in this one.", "Psalm 118:24"),
        CreateStarter(CreateVibe.JOYFUL, "Today's good news: you made it here."),
        CreateStarter(CreateVibe.REFLECTIVE, "Sometimes the answer is just a slower walk and a longer look.", "Psalm 119:105"),
        CreateStarter(CreateVibe.REFLECTIVE, "What did this season teach you that a smooth one couldn't?"),
        CreateStarter(CreateVibe.CELEBRATORY, "We made it to the good part. Celebrate it out loud.", "Psalm 126:3"),
        CreateStarter(CreateVibe.CELEBRATORY, "Small win, big grin. Write it down."),
        CreateStarter(CreateVibe.MOTIVATIONAL, "You don't need a perfect plan. You need the next small step."),
        CreateStarter(CreateVibe.MOTIVATIONAL, "Do it tired. Do it scared. Just do it."),
        CreateStarter(CreateVibe.MOTIVATIONAL, "Momentum beats motivation. Start for two minutes."),
        CreateStarter(CreateVibe.FUNNY, "My calendar is full of meetings that could have been a single, kind sentence."),
        CreateStarter(CreateVibe.FUNNY, "Monday called. I let it go to voicemail."),
        CreateStarter(CreateVibe.FUNNY, "I'm not late. I'm giving everyone time to appreciate the wait."),
        CreateStarter(CreateVibe.WISDOM, "Listen twice as long as you speak. You were given two ears and one mouth."),
        CreateStarter(CreateVibe.WISDOM, "A quiet no today saves a loud sorry tomorrow."),
        CreateStarter(CreateVibe.LOVE, "Love is mostly small things, done on purpose, over and over."),
        CreateStarter(CreateVibe.LOVE, "Be the person who makes it easier for others to be kind."),
        CreateStarter(CreateVibe.CUSTOM, "Write the line only you would write.")
    )

    fun forVibe(vibe: CreateVibe, source: CreateSourceKind): List<CreateStarter> {
        val allowed = CreateVibePolicy.allowed(source)
        val pool = all.filter { it.vibe == vibe }.ifEmpty { all.filter { it.vibe in allowed } }
        return pool.filter { it.vibe in allowed || it.vibe == vibe }.take(3)
    }
}
