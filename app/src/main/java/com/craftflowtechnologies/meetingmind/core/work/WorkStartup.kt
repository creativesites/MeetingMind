package com.craftflowtechnologies.meetingmind.core.work

import android.content.Context
import android.util.Log
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One-time work after the upgrade to schema 17 (docs/PLAN_PROFESSIONAL.md §5.1). */
object WorkStartup {
    private const val PREFS = "work_state"
    private const val PEOPLE_BACKFILLED = "people_backfilled_v2"
    private const val ITEMS_BACKFILLED = "items_backfilled_v1"

    suspend fun run(context: Context) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(PEOPLE_BACKFILLED, false)) {
            runCatching { WorkPeople(MeetMindDatabase.getInstance(context)).backfillFromHistory() }
                .onSuccess { prefs.edit().putBoolean(PEOPLE_BACKFILLED, true).apply() }
                .onFailure { Log.w("WorkStartup", "People backfill will retry next launch: ${it.message}") }
        }
        // Schema 18: findings and waiting-on tasks become items, once. People come first, so owners resolve.
        if (!prefs.getBoolean(ITEMS_BACKFILLED, false)) {
            runCatching { ItemBackfill.run(MeetMindDatabase.getInstance(context)) }
                .onSuccess { prefs.edit().putBoolean(ITEMS_BACKFILLED, true).apply() }
                .onFailure { Log.w("WorkStartup", "Items backfill will retry next launch: ${it.message}") }
        }
    }
}
