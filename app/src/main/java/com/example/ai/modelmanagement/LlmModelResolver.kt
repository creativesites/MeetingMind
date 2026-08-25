package com.example.ai.modelmanagement

import com.example.core.model.ModelCapability

/**
 * Resolves which Meeting Intelligence model a run should actually use.
 *
 * The stored preference alone is not enough: a user can select a model and later delete it to
 * reclaim space, at which point the preference still names a model whose files are gone. Without
 * this, processing would go on pointing at the deleted model and fail with "no model installed"
 * even though a perfectly good one is sitting on the device.
 *
 * Resolution never downloads anything and never silently substitutes a *different installed* model
 * when the selected one is present — it only steps in when the selection is genuinely unusable.
 */
object LlmModelResolver {

    /**
     * Returns the id of the LLM to run: [selectedModelId] when its files are really installed,
     * otherwise the best installed alternative (highest tier first, since a user who deleted their
     * pick is better served by the strongest thing they still have than by an arbitrary one).
     *
     * When nothing at all is installed, [selectedModelId] is returned unchanged so the resulting
     * "model not installed" error names the model the user actually chose rather than something
     * they never asked for.
     */
    fun resolve(selectedModelId: String, modelStorage: ModelStorage): String {
        if (modelStorage.isInstalled(selectedModelId)) return selectedModelId

        return ModelCatalog.entries
            .filter { ModelCapability.SUMMARIZATION in it.capability }
            .filter { modelStorage.isInstalled(it.id) }
            .maxByOrNull { it.tier.ordinal }
            ?.id
            ?: selectedModelId
    }
}
