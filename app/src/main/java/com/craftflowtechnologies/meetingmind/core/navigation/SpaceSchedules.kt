package com.craftflowtechnologies.meetingmind.core.navigation

import android.content.Context
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalProfile
import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalScheduler
import com.craftflowtechnologies.meetingmind.core.faith.ReminderScheduler
import com.craftflowtechnologies.meetingmind.core.faith.ReminderSettings
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import kotlinx.coroutines.flow.first

/**
 * Background schedules follow the person's spaces: with Faith turned off, no devotional is written
 * or announced and no prayer, reading or evening reminders fire. The person's own settings are
 * kept untouched, so turning Faith back on restores them exactly. Meeting prep is Work's, so it
 * stays as set.
 */
object SpaceSchedules {

    /** The devotional profile as it applies to someone with [spaces]. Pure. */
    fun devotionalFor(profile: DevotionalProfile, spaces: Set<NotebookSpace>): DevotionalProfile =
        if (NotebookSpace.FAITH in spaces) profile else profile.copy(enabled = false)

    /** The reminders as they apply to someone with [spaces]. Pure. */
    fun remindersFor(settings: ReminderSettings, spaces: Set<NotebookSpace>): ReminderSettings =
        if (NotebookSpace.FAITH in spaces) settings
        else settings.copy(prayerTimes = emptySet(), readingNudge = false, eveningReflection = false)

    /** Re-applies both schedules from the saved settings and the current spaces. */
    suspend fun sync(context: Context) {
        val app = context.applicationContext
        val prefs = UserPreferencesManager(app)
        val spaces = prefs.preferencesFlow.first().identity.spaces
        DevotionalScheduler.sync(app, devotionalFor(prefs.devotionalProfile.first(), spaces))
        ReminderScheduler.sync(app, remindersFor(prefs.reminderSettings.first(), spaces))
        runCatching {
            com.craftflowtechnologies.meetingmind.core.personal.PersonalRhythmScheduler.sync(
                app,
                NotebookSpace.PERSONAL in spaces
            )
        }
    }

    /** Saves [spaces] and brings every background schedule in line with them. */
    suspend fun setSpaces(context: Context, spaces: Set<NotebookSpace>) {
        UserPreferencesManager(context.applicationContext).setSpaces(spaces)
        runCatching { sync(context) }
    }
}
