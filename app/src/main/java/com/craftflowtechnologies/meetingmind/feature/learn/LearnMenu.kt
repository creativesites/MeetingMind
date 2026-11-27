package com.craftflowtechnologies.meetingmind.feature.learn

import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.navigation.MenuEntry
import com.craftflowtechnologies.meetingmind.core.navigation.MenuIcon
import com.craftflowtechnologies.meetingmind.core.navigation.MenuSection
import com.craftflowtechnologies.meetingmind.core.navigation.MenuTarget
import com.craftflowtechnologies.meetingmind.feature.navigation.Routes

/**
 * Learning's own menu section, shown first when the app menu is opened in Learning (the main menu
 * is the base; this replaces its short Learning group). Pure, so its rows are unit-tested.
 * Moved from the retired feature/learning/space/LearningSpaceScreen.kt.
 */
internal fun learningMenu(
    hasSessions: Boolean,
    onNewSession: () -> Unit,
    onAsk: () -> Unit,
    onDailyRecall: () -> Unit,
    onLibrary: () -> Unit,
    onProgress: () -> Unit
): MenuSection = MenuSection(
    "learn", "Learning", listOfNotNull(
        MenuEntry("learn.new", "New study session", MenuIcon.CREATE, MenuTarget.Run(onNewSession), "Record, use what you have, import or write"),
        if (hasSessions) MenuEntry("learn.ask", "Ask Learning", MenuIcon.AI, MenuTarget.Run(onAsk), "Answers from your lectures, with the moment each was said") else null,
        if (hasSessions) MenuEntry("learn.recall", "Daily recall", MenuIcon.PRACTICE, MenuTarget.Run(onDailyRecall), "Today's reviews, spaced so you remember") else null,
        MenuEntry("learn.library", "Library", MenuIcon.LIBRARY, MenuTarget.Run(onLibrary), "Courses and sessions"),
        MenuEntry("learn.progress", "Progress", MenuIcon.PULSE, MenuTarget.Run(onProgress), "What you know, built from your answers"),
        MenuEntry("learn.record", "Record a lecture", MenuIcon.RECORD, MenuTarget.Go(Routes.recordTypeRoute(RecordingType.LECTURE))),
        MenuEntry("learn.youtube", "Lecture from a YouTube video", MenuIcon.VIDEO, MenuTarget.YouTube(NotebookSpace.LEARNING), "Paste a link, get notes and practice cards"),
        MenuEntry("learn.import", "Learn from a file", MenuIcon.IMPORT, MenuTarget.Go(Routes.IMPORT), "Audio and documents")
    ), NotebookSpace.LEARNING
)
