package com.example.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ai.modelmanagement.ModelCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "meetmind_preferences")

data class AppPreferencesState(
    val onboardingCompleted: Boolean = false,
    val selectedAsrModelId: String = ModelCatalog.parakeetTdtV3Int8.id,
    val selectedLlmModelId: String = ModelCatalog.qwen25_1_5bInstruct.id,
    val batterySaverMode: Boolean = false,
    val wifiOnlyDownload: Boolean = true,
    val audioQualitySampleRate: Int = 16000,
    val autoStopSilenceMinutes: Int = 15,
    val cloudSyncEnabled: Boolean = false,
    val themeMode: String = "SYSTEM"
)

class UserPreferencesManager(private val context: Context) {
    private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    private val SELECTED_ASR_MODEL = stringPreferencesKey("selected_asr_model")
    private val SELECTED_LLM_MODEL = stringPreferencesKey("selected_llm_model")
    private val BATTERY_SAVER = booleanPreferencesKey("battery_saver")
    private val WIFI_ONLY_DOWNLOAD = booleanPreferencesKey("wifi_only_download")
    private val AUDIO_SAMPLE_RATE = intPreferencesKey("audio_sample_rate")
    private val AUTO_STOP_MINUTES = intPreferencesKey("auto_stop_minutes")
    private val CLOUD_SYNC_ENABLED = booleanPreferencesKey("cloud_sync_enabled")
    private val THEME_MODE = stringPreferencesKey("theme_mode")

    val preferencesFlow: Flow<AppPreferencesState> = context.dataStore.data.map { prefs ->
        AppPreferencesState(
            onboardingCompleted = prefs[ONBOARDING_COMPLETED] ?: false,
            selectedAsrModelId = prefs[SELECTED_ASR_MODEL] ?: ModelCatalog.parakeetTdtV3Int8.id,
            selectedLlmModelId = prefs[SELECTED_LLM_MODEL] ?: ModelCatalog.qwen25_1_5bInstruct.id,
            batterySaverMode = prefs[BATTERY_SAVER] ?: false,
            wifiOnlyDownload = prefs[WIFI_ONLY_DOWNLOAD] ?: true,
            audioQualitySampleRate = prefs[AUDIO_SAMPLE_RATE] ?: 16000,
            autoStopSilenceMinutes = prefs[AUTO_STOP_MINUTES] ?: 15,
            cloudSyncEnabled = prefs[CLOUD_SYNC_ENABLED] ?: false,
            themeMode = prefs[THEME_MODE] ?: "SYSTEM"
        )
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setSelectedAsrModel(modelId: String) {
        context.dataStore.edit { it[SELECTED_ASR_MODEL] = modelId }
    }

    suspend fun setSelectedLlmModel(modelId: String) {
        context.dataStore.edit { it[SELECTED_LLM_MODEL] = modelId }
    }

    suspend fun setBatterySaverMode(enabled: Boolean) {
        context.dataStore.edit { it[BATTERY_SAVER] = enabled }
    }

    suspend fun setWifiOnlyDownload(wifiOnly: Boolean) {
        context.dataStore.edit { it[WIFI_ONLY_DOWNLOAD] = wifiOnly }
    }

    suspend fun setCloudSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { it[CLOUD_SYNC_ENABLED] = enabled }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[THEME_MODE] = mode }
    }
}
