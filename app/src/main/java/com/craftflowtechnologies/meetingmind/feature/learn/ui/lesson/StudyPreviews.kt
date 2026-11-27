package com.craftflowtechnologies.meetingmind.feature.learn.ui.lesson

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.craftflowtechnologies.meetingmind.feature.learn.ExplainState
import com.craftflowtechnologies.meetingmind.feature.learn.LessonKitStatus
import com.craftflowtechnologies.meetingmind.feature.learn.ui.listen.ListenScreen
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme

private const val PreviewDark = android.content.res.Configuration.UI_MODE_NIGHT_YES

@Composable
private fun PreviewLesson(dark: Boolean, status: LessonKitStatus = LessonKitStatus.Ready) {
    MeetMindTheme(darkTheme = dark) {
        LessonScreen(
            state = StudySamples.lesson(status = status, kitOverride = if (status == LessonKitStatus.Ready) StudySamples.kit else null),
            explain = ExplainState(),
            openConceptId = null,
            onBack = {}, onPlayFrom = {}, onTogglePlay = {}, onHearSummary = {}, onOpenConcept = {},
            onCloseConcept = {}, onExplain = { _, _ -> }, onQuizConcept = {}, onReportConcept = {},
            onListen = {}, onPractice = {}, onAsk = {}, onGenerate = {}, onFixAudio = {}
        )
    }
}

@Preview(name = "Lesson, Paper", showBackground = true, heightDp = 1500, widthDp = 390)
@Composable
private fun LessonPaperPreview() = PreviewLesson(dark = false)

@Preview(name = "Lesson, dark", uiMode = PreviewDark, showBackground = true, heightDp = 1500, widthDp = 390)
@Composable
private fun LessonDarkPreview() = PreviewLesson(dark = true)

@Preview(name = "Concept sheet, Paper", showBackground = true, heightDp = 844, widthDp = 390)
@Composable
private fun ConceptSheetPreview() {
    MeetMindTheme(darkTheme = false) {
        ConceptSheet(
            concept = StudySamples.km,
            explain = StudySamples.drawnExplanation,
            onDismiss = {}, onPlayQuote = {}, onExplain = {}, onQuizMe = {}, onNotRight = {}
        )
    }
}

@Preview(name = "Listen, dark", uiMode = PreviewDark, showBackground = true, heightDp = 844, widthDp = 390)
@Composable
private fun ListenDarkPreview() {
    MeetMindTheme(darkTheme = true) {
        ListenScreen(
            state = StudySamples.listen(),
            onClose = {}, onTogglePlay = {}, onSeek = {}, onSkip = {}, onNextStressed = {},
            onSpeed = {}, onVoice = {}, onToggleStopAtSectionEnd = {}, onToggleQuizAfter = {}
        )
    }
}
