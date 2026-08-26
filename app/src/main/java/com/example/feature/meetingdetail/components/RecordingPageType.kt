package com.example.feature.meetingdetail.components

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Ink
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSecondary

/**
 * Local type scale for the redesigned recording detail screen (docs/recording-page-implementation.md
 * §1.2). Deliberately not merged into the app-wide [com.example.ui.theme.Typography] — these sizes
 * (17.5sp answer prose, 27sp mono dial clock, etc.) are specific to this screen's language and don't
 * map onto Material's named roles without losing precision.
 */
object RecordingPageType {

    private val mono = FontFamily.Monospace

    val stepHeading = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 34.5.sp,
        letterSpacing = (-0.8).sp,
        color = Ink
    )

    val sheetTitle = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        letterSpacing = (-0.4).sp,
        color = Ink
    )

    val askAnswer = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 17.5.sp,
        lineHeight = 30.sp,
        color = Ink
    )

    val askQuestion = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.4).sp,
        color = InkMuted
    )

    val transcriptBody = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.5.sp,
        lineHeight = 28.sp
    )

    val transcriptBodyEditing = transcriptBody.copy(lineHeight = 29.sp)

    val stepBody = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 29.sp,
        color = InkSecondary
    )

    val decisionLine = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 26.sp,
        color = Ink
    )

    val listRow = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.75.sp,
        color = Ink
    )

    val cardTitle = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        color = Ink
    )

    val bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 22.sp,
        color = InkSecondary
    )

    val caption = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.25.sp,
        lineHeight = 19.5.sp,
        color = InkMuted
    )

    val sectionLabel = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 0.6.sp,
        color = InkMuted
    )

    val monoTimestamp = TextStyle(
        fontFamily = mono,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp,
        color = InkMuted
    )

    val monoTimestampActive = monoTimestamp.copy(fontWeight = FontWeight.SemiBold)

    val monoDialClock = TextStyle(
        fontFamily = mono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 27.sp,
        letterSpacing = (-0.5).sp,
        color = Ink
    )

    val stepCounter = TextStyle(
        fontFamily = mono,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        letterSpacing = 1.2.sp,
        color = InkMuted
    )
}
