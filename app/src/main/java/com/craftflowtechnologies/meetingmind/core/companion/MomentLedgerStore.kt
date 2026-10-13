package com.craftflowtechnologies.meetingmind.core.companion

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.craftflowtechnologies.meetingmind.core.companion.moment.MomentEffects
import com.craftflowtechnologies.meetingmind.core.companion.moment.MomentFacts
import com.craftflowtechnologies.meetingmind.core.companion.moment.MomentPlanner
import com.craftflowtechnologies.meetingmind.core.companion.moment.PlannedMoment
import com.craftflowtechnologies.meetingmind.core.companion.moment.RotationState
import com.craftflowtechnologies.meetingmind.core.companion.moment.TicketAction
import com.craftflowtechnologies.meetingmind.core.companion.moment.UnveilTicket
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.companionMomentsStore: DataStore<Preferences> by preferencesDataStore(name = "meetmind_companion_moments")

/**
 * Persists the [MomentLedger] (one timestamp per moment key, plus the last few for daily budgets), the
 * Unveil [RotationState] and the [UnveilTicket]s in one DataStore file (signature moments §3.2, §3.6, §8).
 * Only ids, timestamps and small counters are stored; never note content.
 */
class MomentLedgerStore(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.companionMomentsStore)

    val ledger: Flow<MomentLedger> = store.data.map(::ledgerOf)

    val rotation: Flow<RotationState> = store.data.map { RotationState.decode(it[RotationKey]) }

    suspend fun record(moment: Moment, clock: LocalClock) {
        store.edit { recordInto(it, moment, clock) }
    }

    /**
     * Writes [ticket] when a note finishes (WorkManager `SUCCEEDED`), stamping when the previous note was
     * ready (for the back-to-back guard), and drops tickets that have expired. Returns the stored ticket.
     */
    suspend fun issueTicket(ticket: UnveilTicket): UnveilTicket {
        var stored = ticket
        store.edit { prefs ->
            pruneExpired(prefs, ticket.readyAtMs)
            stored = ticket.copy(previousReadyAtMs = ticket.previousReadyAtMs ?: prefs[LastNoteReadyKey])
            prefs[ticketKey(ticket.noteId)] = stored.encode()
            if (!ticket.hello) prefs[LastNoteReadyKey] = ticket.readyAtMs
        }
        return stored
    }

    /** The note's ticket, if it has a valid one, without consuming it. */
    suspend fun peekTicket(noteId: String, nowMs: Long): UnveilTicket? =
        UnveilTicket.decode(noteId, store.data.first()[ticketKey(noteId)])?.takeIf { it.isValid(nowMs) }

    /**
     * Plans the moment for one open of a note, in a single transaction: reads the note's ticket, the
     * ledger and the rotation, asks [MomentPlanner], then applies its effects (consume or defer the ticket,
     * record capped moments, store the new rotation). [facts] receives the stored parts and returns the
     * full [MomentFacts] (or call with `facts = { t, l, r -> MomentFacts(..., ticket = t, ledger = l, rotation = r) }`).
     *
     * Call it once per fresh open (`OpenSession.claim()`), off the main thread is not required (DataStore
     * suspends).
     */
    suspend fun planOpen(
        noteId: String,
        clock: LocalClock,
        facts: (ticket: UnveilTicket?, ledger: MomentLedger, rotation: RotationState) -> MomentFacts
    ): PlannedMoment {
        lateinit var out: PlannedMoment
        store.edit { prefs ->
            val ticket = UnveilTicket.decode(noteId, prefs[ticketKey(noteId)])
            val planned = MomentPlanner.plan(facts(ticket, ledgerOf(prefs), RotationState.decode(prefs[RotationKey])))
            applyInto(prefs, noteId, ticket, planned.effects, clock)
            out = planned
        }
        return out
    }

    /** Applies a plan's effects (for hosts that planned outside [planOpen]). */
    suspend fun apply(noteId: String, effects: MomentEffects, clock: LocalClock) {
        store.edit { prefs -> applyInto(prefs, noteId, UnveilTicket.decode(noteId, prefs[ticketKey(noteId)]), effects, clock) }
    }

    /** Companion Lab: forget the rotation (it restarts from zero, which is harmless). */
    suspend fun resetRotation() {
        store.edit { it.remove(RotationKey) }
    }

    private fun applyInto(prefs: MutablePreferences, noteId: String, ticket: UnveilTicket?, effects: MomentEffects, clock: LocalClock) {
        effects.record.forEach { recordInto(prefs, it, clock) }
        effects.rotation?.let { prefs[RotationKey] = it.encode() }
        when (effects.ticket) {
            TicketAction.NONE -> Unit
            TicketAction.CONSUME -> prefs.remove(ticketKey(noteId))
            TicketAction.DEFER -> if (ticket != null) prefs[ticketKey(noteId)] = ticket.copy(deferred = true).encode()
        }
    }

    private fun recordInto(prefs: MutablePreferences, moment: Moment, clock: LocalClock) {
        val updated = ledgerOf(prefs).record(moment, clock)
        prefs[longPreferencesKey(Prefix + moment.key)] = clock.nowMs
        updated.history[moment.key]?.let { prefs[stringPreferencesKey(BudgetPrefix + moment.key)] = it.joinToString(",") }
    }

    private fun pruneExpired(prefs: MutablePreferences, nowMs: Long) {
        prefs.asMap().keys.filter { it.name.startsWith(TicketPrefix) }.forEach { key ->
            val noteId = key.name.removePrefix(TicketPrefix)
            val t = UnveilTicket.decode(noteId, prefs[stringPreferencesKey(key.name)])
            if (t == null || !t.isValid(nowMs)) prefs.remove(key)
        }
    }

    private companion object {
        const val Prefix = "moment."
        const val BudgetPrefix = "budget."
        const val TicketPrefix = "ticket."
        val RotationKey = stringPreferencesKey("unveil.rotation")
        val LastNoteReadyKey = longPreferencesKey("noteready.last")

        fun ticketKey(noteId: String) = stringPreferencesKey(TicketPrefix + noteId)

        fun ledgerOf(prefs: Preferences): MomentLedger {
            val map = prefs.asMap()
            val last = map.entries
                .filter { it.key.name.startsWith(Prefix) && it.value is Long }
                .associate { it.key.name.removePrefix(Prefix) to it.value as Long }
            val history = map.entries
                .filter { it.key.name.startsWith(BudgetPrefix) && it.value is String }
                .associate { e ->
                    e.key.name.removePrefix(BudgetPrefix) to (e.value as String).split(',').mapNotNull { it.toLongOrNull() }
                }
            return MomentLedger(last, history)
        }
    }
}
