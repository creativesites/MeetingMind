package com.craftflowtechnologies.meetingmind.core.work

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.recipesDataStore: DataStore<Preferences> by preferencesDataStore(name = "meetmind_recipes")

data class RecipePreferencesState(
    val enabledRecipeIds: Set<String> = setOf(
        RecipeDefaults.ID_WRAP_UP,
        RecipeDefaults.ID_CLIENT_MEETING,
        RecipeDefaults.ID_FRIDAY_REVIEW
    ),
    val disabledSteps: Set<String> = emptySet()
)

/**
 * Manages local persistence for recipe toggles and step customisations.
 */
class RecipePreferences(private val context: Context) {

    val state: Flow<RecipePreferencesState> = context.recipesDataStore.data.map { prefs ->
        val enabled = prefs[KEY_ENABLED_RECIPES] ?: setOf(
            RecipeDefaults.ID_WRAP_UP,
            RecipeDefaults.ID_CLIENT_MEETING,
            RecipeDefaults.ID_FRIDAY_REVIEW
        )
        val disabledSteps = prefs[KEY_DISABLED_STEPS] ?: emptySet()
        RecipePreferencesState(
            enabledRecipeIds = enabled,
            disabledSteps = disabledSteps
        )
    }

    suspend fun isRecipeEnabled(recipeId: String): Boolean {
        val current = state.first()
        return recipeId in current.enabledRecipeIds
    }

    suspend fun setRecipeEnabled(recipeId: String, enabled: Boolean) {
        context.recipesDataStore.edit { prefs ->
            val current = prefs[KEY_ENABLED_RECIPES]?.toMutableSet() ?: mutableSetOf(
                RecipeDefaults.ID_WRAP_UP,
                RecipeDefaults.ID_CLIENT_MEETING,
                RecipeDefaults.ID_FRIDAY_REVIEW
            )
            if (enabled) {
                current.add(recipeId)
            } else {
                current.remove(recipeId)
            }
            prefs[KEY_ENABLED_RECIPES] = current
        }
    }

    suspend fun isStepEnabled(recipeId: String, skill: RecipeSkill): Boolean {
        val current = state.first()
        val stepKey = "$recipeId:${skill.name}"
        return stepKey !in current.disabledSteps
    }

    suspend fun setStepEnabled(recipeId: String, skill: RecipeSkill, enabled: Boolean) {
        val stepKey = "$recipeId:${skill.name}"
        context.recipesDataStore.edit { prefs ->
            val current = prefs[KEY_DISABLED_STEPS]?.toMutableSet() ?: mutableSetOf()
            if (enabled) {
                current.remove(stepKey)
            } else {
                current.add(stepKey)
            }
            prefs[KEY_DISABLED_STEPS] = current
        }
    }

    companion object {
        private val KEY_ENABLED_RECIPES = stringSetPreferencesKey("enabled_recipes")
        private val KEY_DISABLED_STEPS = stringSetPreferencesKey("disabled_recipe_steps")
    }
}
