package com.craftflowtechnologies.meetingmind.core.companion

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.companionMomentsStore: DataStore<Preferences> by preferencesDataStore(name = "meetmind_companion_moments")

/** Persists the [MomentLedger] (one timestamp per moment key) in its own DataStore file. */
class MomentLedgerStore(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.companionMomentsStore)

    val ledger: Flow<MomentLedger> = store.data.map { prefs ->
        MomentLedger(
            prefs.asMap().entries
                .filter { it.key.name.startsWith(Prefix) && it.value is Long }
                .associate { it.key.name.removePrefix(Prefix) to it.value as Long }
        )
    }

    suspend fun record(moment: Moment, clock: LocalClock) {
        store.edit { it[longPreferencesKey(Prefix + moment.key)] = clock.nowMs }
    }

    private companion object {
        const val Prefix = "moment."
    }
}
