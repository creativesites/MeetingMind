package com.craftflowtechnologies.meetingmind.feature.onboarding

/** Which example recording to show. */
enum class SampleKind(val label: String) { MEETING("Team meeting"), SERMON("Sermon") }

data class SampleLine(val speaker: String, val time: String, val text: String)

/**
 * An example recording with its results already worked out. Everything here is written for the
 * example and ships inside the app: showing it needs no network, no model, no permission, and
 * nothing is sent anywhere. It is always labelled as an example and never counted as the person's own.
 */
data class SampleRecording(
    val title: String,
    val length: String,
    val lines: List<SampleLine>,
    val summary: String,
    val actions: List<String>,
    val question: String,
    val answer: String
)

object SampleContent {
    fun of(kind: SampleKind): SampleRecording = when (kind) {
        SampleKind.MEETING -> meeting
        SampleKind.SERMON -> sermon
    }

    private val meeting = SampleRecording(
        title = "Launch check-in",
        length = "4 min",
        lines = listOf(
            SampleLine("Maya", "0:04", "Okay, launch is two weeks out. Where are we on the new onboarding?"),
            SampleLine("Dev", "0:11", "The build is done. I still need a second pass on the permission screens."),
            SampleLine("Priya", "0:24", "I can test those Thursday. Can we also settle the pricing page copy?"),
            SampleLine("Maya", "0:37", "Let's keep it simple: two plans, yearly first. Priya, you draft the copy."),
            SampleLine("Dev", "0:52", "Then I'll hold the release until the copy is in, so Friday at the earliest."),
            SampleLine("Maya", "1:05", "Good. Decision made: we ship Friday, and Priya owns the copy by Wednesday.")
        ),
        summary = "The team is two weeks from launch. Onboarding is built and needs one more review of the permission screens. Pricing will be two plans with yearly shown first.",
        actions = listOf(
            "Priya: draft the pricing page copy by Wednesday",
            "Priya: test the permission screens on Thursday",
            "Dev: hold the release until the copy is in (Friday earliest)"
        ),
        question = "What did we decide?",
        answer = "Ship on Friday once the copy is in. Pricing is two plans with yearly first, and Priya owns the copy, due Wednesday."
    )

    private val sermon = SampleRecording(
        title = "Mercy at the table",
        length = "5 min",
        lines = listOf(
            SampleLine("Pastor", "0:05", "This morning we are in Luke fifteen, three stories about something lost and then found."),
            SampleLine("Pastor", "0:21", "Notice who is complaining at the start: people who think the table is only for the respectable."),
            SampleLine("Pastor", "0:40", "The shepherd leaves ninety-nine to find one. Heaven does not count that as a waste."),
            SampleLine("Pastor", "1:02", "And the father runs. In that world, a dignified man did not run. He ran anyway."),
            SampleLine("Pastor", "1:25", "So the question for us is not only whether we have come home, but whether we will celebrate when others do.")
        ),
        summary = "A message on Luke 15: God seeks the lost and celebrates their return. The listeners are challenged to welcome others the way the father welcomed his son.",
        actions = listOf(
            "Read Luke 15:1-32 again this week",
            "Pray for one person who feels far from home",
            "Invite someone to the table this Sunday"
        ),
        question = "What was the challenge at the end?",
        answer = "Not only to have come home, but to celebrate when others come home too, instead of standing outside like the complaining crowd."
    )
}
