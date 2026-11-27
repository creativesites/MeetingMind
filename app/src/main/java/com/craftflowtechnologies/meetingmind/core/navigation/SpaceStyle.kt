package com.craftflowtechnologies.meetingmind.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.Church
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.ui.theme.MM

/**
 * How each space looks and introduces itself, everywhere it appears: the spaces picker, the app
 * menu, the bottom bar and the "Your spaces" row on the homes. One place, so a space never has two
 * names, colours or icons. Colours are theme tokens, so they read in light and dark.
 */
object SpaceStyle {

    /** The order spaces are listed in. */
    val ORDER: List<NotebookSpace> = listOf(NotebookSpace.WORK, NotebookSpace.LEARNING, NotebookSpace.FAITH, NotebookSpace.PERSONAL)

    fun title(space: NotebookSpace): String = when (space) {
        NotebookSpace.WORK -> "Work"
        NotebookSpace.LEARNING -> "Learning"
        NotebookSpace.FAITH -> "Faith"
        NotebookSpace.PERSONAL -> "Personal"
    }

    /** What the space is for, in a few words. */
    fun line(space: NotebookSpace): String = when (space) {
        NotebookSpace.WORK -> "Meetings, tasks, projects and people"
        NotebookSpace.LEARNING -> "Lectures, classes, study and practice"
        NotebookSpace.FAITH -> "Sermons, the Bible, devotionals and prayer"
        NotebookSpace.PERSONAL -> "Reflect, practise and keep ideas"
    }

    /** What turning the space on adds to the app, so the choice is concrete. */
    fun adds(space: NotebookSpace): String = when (space) {
        NotebookSpace.WORK -> "Adds the Work home, Tasks, Inbox and meeting briefs"
        NotebookSpace.LEARNING -> "Adds Learn, daily recall, your library and practice"
        NotebookSpace.FAITH -> "Adds Faith, the Bible, the daily devotional and prayer"
        NotebookSpace.PERSONAL -> "Adds your garden: a daily question, practices and a seed jar"
    }

    fun icon(space: NotebookSpace): ImageVector = when (space) {
        NotebookSpace.WORK -> Icons.Rounded.BusinessCenter
        NotebookSpace.LEARNING -> Icons.Rounded.School
        NotebookSpace.FAITH -> Icons.Rounded.Church
        NotebookSpace.PERSONAL -> Icons.Rounded.SelfImprovement
    }

    /** The space's colour; the same family Circles uses for its space covers. */
    @Composable
    @ReadOnlyComposable
    fun tint(space: NotebookSpace): Color = when (space) {
        NotebookSpace.WORK -> MM.colors.accent
        NotebookSpace.LEARNING -> MM.colors.speaker2
        NotebookSpace.FAITH -> MM.colors.gold
        NotebookSpace.PERSONAL -> MM.colors.speaker3
    }
}
