package com.craftflowtechnologies.meetingmind.feature.learn.ui.practice

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.craftflowtechnologies.meetingmind.feature.learn.AnswerFeedback
import com.craftflowtechnologies.meetingmind.feature.learn.NextStep
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeItem
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeState
import com.craftflowtechnologies.meetingmind.feature.learn.RecallButton
import com.craftflowtechnologies.meetingmind.feature.learn.RoundResult
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme

/**
 * Sample content from the approved mockups (design/study/Practice, Recall, RoundDone). Used by the previews below and
 * by the screenshot and behaviour tests, so what Studio shows is what the tests record.
 */
internal object PracticeSamples {
    private const val BIO = "BIO 201 · Inhibition"

    val choice = PracticeItem.Choice(
        activityId = "bio-inhibition-1",
        conceptId = "inhibition",
        sourceLabel = BIO,
        sourceStartMs = 41 * 60_000L + 30_000L,
        sourceEndMs = 41 * 60_000L + 38_000L,
        meetingId = "lecture-7",
        prompt = "Which inhibitor raises the apparent Km but leaves Vmax the same?",
        options = listOf("Competitive", "Non-competitive", "Uncompetitive", "Mixed"),
        correctIndex = 0,
        rightWhy = "It competes for the active site, so enough substrate wins it back. Top speed is untouched.",
        wrongWhys = mapOf(
            1 to "A non-competitive inhibitor binds away from the active site, so it lowers Vmax.",
            2 to "An uncompetitive inhibitor lowers both Km and Vmax.",
            3 to "A mixed inhibitor changes Vmax too."
        )
    )

    val recall = PracticeItem.Recall(
        activityId = "econ-supply-shock",
        conceptId = "supply-shock",
        sourceLabel = "ECON 101 · Lecture 4",
        sourceStartMs = 22 * 60_000L + 15_000L,
        sourceEndMs = 22 * 60_000L + 24_000L,
        meetingId = "lecture-4",
        prompt = "Why does a supply shock raise prices even when demand hasn't changed?",
        answer = "The supply curve shifts left. At the old price people still want the same amount, but less is on offer, " +
            "so the price climbs until the two meet again."
    )

    val recallHints: Map<RecallButton, String> = mapOf(
        RecallButton.MISSED to "again soon",
        RecallButton.GOT_IT to "in 3 days",
        RecallButton.EASY to "in a week"
    )

    /** Question 3 of 8, not answered yet. */
    val question = PracticeState(total = 8, index = 2, current = choice)

    /** Question 3 of 8, answered wrongly with option B. */
    val wrong = question.copy(
        feedback = AnswerFeedback(
            chosenIndex = 1,
            correct = false,
            why = "A non-competitive inhibitor binds away from the active site, so it lowers Vmax. Competitive is the one " +
                "that only raises the apparent Km.",
            nextLine = "This one comes back in 3 questions"
        )
    )

    /** Question 3 of 8, answered rightly. */
    val right = question.copy(
        feedback = AnswerFeedback(
            chosenIndex = 0,
            correct = true,
            why = choice.rightWhy,
            nextLine = "Next time: in 3 days"
        )
    )

    /** Card 5 of 8, answer showing. */
    val recallRevealed = PracticeState(total = 8, index = 4, current = recall, revealed = true, recallHints = recallHints)

    /** Card 5 of 8, answer hidden. */
    val recallHidden = recallRevealed.copy(revealed = false)

    val roundResult = RoundResult(
        total = 8,
        firstTryCorrect = 6,
        newStars = listOf("Inhibition", "Supply shock"),
        backTomorrow = 3,
        minutes = 4,
        daysThisWeek = 5,
        nextStep = NextStep(
            sessionId = "lecture-7",
            conceptId = "vmax",
            title = "Revisit \"Vmax stays the same\"",
            detail = "Missed twice · 2 min · from Lecture 7"
        ),
        allCleared = false
    )

    val done = PracticeState(total = 8, index = 7, result = roundResult)
}

@Composable
private fun SamplePractice(state: PracticeState, dark: Boolean) {
    MeetMindTheme(darkTheme = dark) {
        PracticeScreen(
            state = state,
            onAnswerChoice = {},
            onNotSure = {},
            onReveal = {},
            onRate = {},
            onContinue = {},
            onClose = {},
            onPlaySource = {},
            onDone = {},
            onOpenNextStep = {},
            onMakeCard = {}
        )
    }
}

@Preview(name = "Question light", showBackground = true)
@Composable
private fun QuestionLight() = SamplePractice(PracticeSamples.question, dark = false)

@Preview(name = "Question dark", showBackground = true)
@Composable
private fun QuestionDark() = SamplePractice(PracticeSamples.question, dark = true)

@Preview(name = "Wrong answer light", showBackground = true)
@Composable
private fun WrongLight() = SamplePractice(PracticeSamples.wrong, dark = false)

@Preview(name = "Wrong answer dark", showBackground = true)
@Composable
private fun WrongDark() = SamplePractice(PracticeSamples.wrong, dark = true)

@Preview(name = "Recall revealed light", showBackground = true)
@Composable
private fun RecallLight() = SamplePractice(PracticeSamples.recallRevealed, dark = false)

@Preview(name = "Recall revealed dark", showBackground = true)
@Composable
private fun RecallDark() = SamplePractice(PracticeSamples.recallRevealed, dark = true)

@Preview(name = "Round done light", showBackground = true)
@Composable
private fun DoneLight() = SamplePractice(PracticeSamples.done, dark = false)

@Preview(name = "Round done dark", showBackground = true)
@Composable
private fun DoneDark() = SamplePractice(PracticeSamples.done, dark = true)
