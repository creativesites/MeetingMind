package com.craftflowtechnologies.meetingmind.feature.personal.lantern

import com.craftflowtechnologies.meetingmind.feature.personal.garden.GardenQuestions

/**
 * The lantern's rules: when the companion offers a follow-up, when "Type instead" is still offered,
 * and how the light answers the voice. Pure, so they are unit-tested on the JVM.
 *
 * The follow-up is a gentle second question from a fixed set, chosen for the day's question. It
 * runs on the phone, never writes the person's answer, and is never AI (agents/PRODUCT.md: Zuri
 * asks, never writes the answer).
 */
object LanternRules {

    /** The follow-up appears after this much speaking, so it never interrupts the first thought. */
    const val FOLLOW_UP_AFTER_MS = 40_000L

    /** "Type instead" is offered only before much has been said; after that the recording is kept. */
    const val TYPE_INSTEAD_UNTIL_MS = 10_000L

    /** Below this, a recording is too short to keep and finishing simply closes the lantern. */
    const val SHORTEST_KEEP_MS = 2_000L

    private val followUps: Map<String, String> = mapOf(
        "What took more energy today than it should have?" to "What would you do differently tomorrow morning?",
        "What gave you energy today?" to "How could you make room for more of that?",
        "What would you do differently tomorrow morning?" to "What is one small thing that would make that easier?",
        "What surprised you today?" to "What did it teach you?",
        "Who made today better, and do they know?" to "How could you tell them?",
        "What are you glad you said, or didn't say?" to "What helped you get that right?",
        "What did you learn about yourself today?" to "Where else does that show up?",
        "What are you still carrying from today?" to "What would help you set it down tonight?",
        "What small thing went right?" to "What part did you play in it?",
        "When did you feel most like yourself today?" to "What was different about that moment?",
        "What can you let go of tonight?" to "What would letting go make room for?",
        "What are you grateful for from today?" to "Who or what made it possible?",
        "What did you avoid today, and why?" to "What is the smallest step toward it?",
        "What do you want to remember about today?" to "Why does it matter to you?",
        "What would make today feel well spent?" to "What could get in the way, and how will you handle it?",
        "What are you carrying into today?" to "What would make it lighter?",
        "What is one thing you want to protect today?" to "What will you say no to, to protect it?",
        "Who could use a word from you today?" to "What would you like them to hear?",
        "What would you like to notice today?" to "When in the day will you look for it?",
        "What are you looking forward to, even a little?" to "How can you enjoy it more fully?",
        "What can wait, so that something else can't?" to "What happens if it waits a week?",
        "How do you want to feel at the end of today?" to "What would help you get there?",
        "What is asking for your attention most?" to "Is it the thing that matters most?",
        "What small thing would make today kinder?" to "Who would it be kinder to?",
        "What did you sleep on, and how does it look now?" to "What changed overnight?",
        "What is one thing you could finish today?" to "What would finishing it free you to do?"
    )

    private const val GENERIC = "Say a little more about how that felt."

    /** The second question for [question]; every bundled question has its own. */
    fun followUp(question: String): String = followUps[question] ?: GENERIC

    fun showFollowUp(speakingMs: Long): Boolean = speakingMs >= FOLLOW_UP_AFTER_MS

    fun canTypeInstead(durationMs: Long): Boolean = durationMs < TYPE_INSTEAD_UNTIL_MS

    /** How large the light is for [amplitude] (0 to 1): a calm 1.0 at rest, up to 1.18 when speaking. */
    fun orbScale(amplitude: Float): Float = 1f + 0.18f * amplitude.coerceIn(0f, 1f)

    /** Every bundled question has a written follow-up (checked by a test, so new questions get one). */
    fun coversAllQuestions(): Boolean = (GardenQuestions.morning + GardenQuestions.evening).all { it in followUps }
}
