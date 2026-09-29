package com.example.core.work

import android.content.Context
import android.util.Log
import com.example.core.database.MeetMindDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One-time work after an upgrade to the work schema (PLAN_PROFESSIONAL.md §5.1). */
object WorkStartup {
    private const val PREFS = "work_state"
    private const val PEOPLE_BACKFILLED = "people_backfilled_v1"

    suspend fun run(context: Context) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(PEOPLE_BACKFILLED, false)) return@withContext
        runCatching { PeopleRepository(MeetMindDatabase.getInstance(context)).backfillFromHistory() }
            .onSuccess { prefs.edit().putBoolean(PEOPLE_BACKFILLED, true).apply() }
            .onFailure { Log.w("WorkStartup", "People backfill will retry next launch: ${it.message}") }
    }
}
