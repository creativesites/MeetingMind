package com.craftflowtechnologies.meetingmind.core.companion

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.TimeZone

private val Context.companionSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "meetmind_companion_settings")

/** The rules around the companion settings that need no Android: names, and when "hide" ends (§7.1, §7.3). */
object CompanionSettingsRules {
    const val MaxNameLength = 14

    /** The stored value for "No companion". The key is absent for the default (Zuri). */
    internal const val NoneToken = "NONE"

    /** A typed name, trimmed and cut to 1–14 characters. Blank means "use the form's display name" (null). */
    fun normalizeName(raw: String?): String? = raw?.trim()?.take(MaxNameLength)?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * The next 07:00 local time strictly after [nowMs] ("Hide for now" lasts until then, §7.1).
     * Before 07:00 it is later today; from 07:00 on it is tomorrow.
     */
    fun nextMorning(nowMs: Long, zone: TimeZone = TimeZone.getDefault(), hour: Int = 7): Long {
        val cal = Calendar.getInstance(zone).apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= nowMs) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }
}

/** What the companion UI reads and writes. The DataStore-backed [CompanionSettingsStore] is the real one; tests use fakes. */
interface CompanionSettingsSource {
    /** The settings with the form already resolved against the roster (a stored form that no longer ships becomes Zuri). */
    val settings: Flow<CompanionSettings>

    /** Null is "No companion". */
    suspend fun setForm(form: CompanionForm?)
    suspend fun setName(name: String?)
    suspend fun setPresence(presence: Presence)
    suspend fun setHiddenUntil(untilMs: Long?)
    suspend fun setOnRecordButton(on: Boolean)
    suspend fun setQuietSermons(on: Boolean)
    suspend fun setCreateSuggest(on: Boolean)
}

/**
 * Persists [CompanionSettings] in their own DataStore file (§7.1). Defaults live in [CompanionSettings]:
 * Zuri, the form's own name, Around, not hidden, record button off, quiet sermons on, Create suggestions off.
 */
class CompanionSettingsStore(
    private val store: DataStore<Preferences>,
    private val roster: List<CompanionForm> = CompanionRoster.enabled
) : CompanionSettingsSource {

    constructor(context: Context) : this(context.applicationContext.companionSettingsDataStore)

    override val settings: Flow<CompanionSettings> = store.data.map { p ->
        val defaults = CompanionSettings()
        val storedForm: CompanionForm? = when (val raw = p[Form]) {
            null -> defaults.form
            CompanionSettingsRules.NoneToken -> null
            else -> CompanionForm.entries.firstOrNull { it.name == raw } ?: CompanionForm.ZURI
        }
        CompanionSettings(
            form = CompanionRoster.resolve(storedForm, roster),
            name = CompanionSettingsRules.normalizeName(p[Name]),
            presence = p[PresenceKey]?.let { raw -> Presence.entries.firstOrNull { it.name == raw } } ?: defaults.presence,
            hiddenUntilMs = p[HiddenUntil],
            onRecordButton = p[OnRecordButton] ?: defaults.onRecordButton,
            quietSermons = p[QuietSermons] ?: defaults.quietSermons,
            createSuggest = p[CreateSuggest] ?: defaults.createSuggest,
            seasonal = p[Seasonal] ?: defaults.seasonal
        )
    }

    override suspend fun setForm(form: CompanionForm?) {
        store.edit { it[Form] = form?.name ?: CompanionSettingsRules.NoneToken }
    }

    override suspend fun setName(name: String?) {
        store.edit { prefs ->
            val clean = CompanionSettingsRules.normalizeName(name)
            if (clean == null) prefs.remove(Name) else prefs[Name] = clean
        }
    }

    override suspend fun setPresence(presence: Presence) { store.edit { it[PresenceKey] = presence.name } }

    override suspend fun setHiddenUntil(untilMs: Long?) {
        store.edit { if (untilMs == null) it.remove(HiddenUntil) else it[HiddenUntil] = untilMs }
    }

    override suspend fun setOnRecordButton(on: Boolean) { store.edit { it[OnRecordButton] = on } }
    override suspend fun setQuietSermons(on: Boolean) { store.edit { it[QuietSermons] = on } }
    override suspend fun setCreateSuggest(on: Boolean) { store.edit { it[CreateSuggest] = on } }

    private companion object {
        val Form = stringPreferencesKey("companion.form")
        val Name = stringPreferencesKey("companion.name")
        val PresenceKey = stringPreferencesKey("companion.presence")
        val HiddenUntil = longPreferencesKey("companion.hiddenUntil")
        val OnRecordButton = booleanPreferencesKey("companion.onRecordButton")
        val QuietSermons = booleanPreferencesKey("companion.quietSermons")
        val CreateSuggest = booleanPreferencesKey("companion.createSuggest")
        val Seasonal = booleanPreferencesKey("companion.seasonal")
    }
}
