package com.craftflowtechnologies.meetingmind.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.work.ApprovalCard
import com.craftflowtechnologies.meetingmind.core.work.ApprovalStatus
import com.craftflowtechnologies.meetingmind.core.work.ExecutionResult
import com.craftflowtechnologies.meetingmind.core.work.Recipe
import com.craftflowtechnologies.meetingmind.core.work.RecipeDefaults
import com.craftflowtechnologies.meetingmind.core.work.RecipeEngine
import com.craftflowtechnologies.meetingmind.core.work.RecipePreferences
import com.craftflowtechnologies.meetingmind.core.work.RecipeSkill
import com.craftflowtechnologies.meetingmind.core.work.RecipeSkillStep
import com.craftflowtechnologies.meetingmind.core.work.WorkProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecipesUiState(
    val recipes: List<Recipe> = emptyList(),
    val pendingApprovals: List<ApprovalCard> = emptyList(),
    val currentWorkProfile: WorkProfile = WorkProfile.CLIENT_WORK
)

class RecipesViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = RecipePreferences(application)
    private val userPrefs = UserPreferencesManager(application)
    private val database = MeetMindDatabase.getInstance(application)
    private val engine = RecipeEngine(database)

    private val _pendingApprovals = MutableStateFlow<List<ApprovalCard>>(emptyList())

    val uiState: StateFlow<RecipesUiState> = combine(
        preferences.state,
        userPrefs.workSettings,
        _pendingApprovals
    ) { prefsState, workSettings, approvals ->
        val recipes = RecipeDefaults.BUILT_IN.map { baseRecipe ->
            val isRecipeEnabled = baseRecipe.id in prefsState.enabledRecipeIds
            val steps = baseRecipe.steps.map { step ->
                val stepKey = "${baseRecipe.id}:${step.skill.name}"
                val isStepEnabled = stepKey !in prefsState.disabledSteps
                step.copy(isEnabled = isStepEnabled)
            }
            baseRecipe.copy(isEnabled = isRecipeEnabled, steps = steps)
        }
        RecipesUiState(
            recipes = recipes,
            pendingApprovals = approvals.filter { it.status == ApprovalStatus.PENDING },
            currentWorkProfile = workSettings.profile
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        RecipesUiState(recipes = RecipeDefaults.BUILT_IN)
    )

    fun toggleRecipe(recipeId: String, enabled: Boolean) {
        viewModelScope.launch {
            preferences.setRecipeEnabled(recipeId, enabled)
        }
    }

    fun toggleStep(recipeId: String, skill: RecipeSkill, enabled: Boolean) {
        viewModelScope.launch {
            preferences.setStepEnabled(recipeId, skill, enabled)
        }
    }

    fun approveCard(card: ApprovalCard) {
        viewModelScope.launch {
            engine.approve(card)
            _pendingApprovals.value = _pendingApprovals.value.map {
                if (it.id == card.id) card.copy(status = ApprovalStatus.APPROVED) else it
            }
        }
    }

    fun rejectCard(card: ApprovalCard) {
        engine.reject(card)
        _pendingApprovals.value = _pendingApprovals.value.map {
            if (it.id == card.id) card.copy(status = ApprovalStatus.REJECTED) else it
        }
    }
}
