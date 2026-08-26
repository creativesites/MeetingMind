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
    fun resolve(
        selectedModelId: String,
        modelStorage: ModelStorage,
        capability: ModelCapability = ModelCapability.SUMMARIZATION
    ): String {
        if (modelStorage.isInstalled(selectedModelId)) return selectedModelId

        return ModelCatalog.entries
            .filter { capability in it.capability }
            .filter { modelStorage.isInstalled(it.id) }
            .maxByOrNull { it.tier.ordinal }
            ?.id
            ?: selectedModelId
    }

    /**
     * The smallest INSTALLED model capable of [capability], regardless of what the user has
     * selected for the (usually larger) intelligence-extraction tier — a dedicated transcript
     * cleanup pass has no reason to default to the biggest thing installed. Returns null when
     * nothing capable is installed at all, distinct from [resolve]'s "fall back to the selected
     * id anyway" behavior: a caller doing capability-gated selection needs to know "truly
     * unavailable" rather than being handed back an id that still won't have the capability it
     * asked for.
     */
    fun resolveSmallestInstalled(modelStorage: ModelStorage, capability: ModelCapability): String? =
        ModelCatalog.entries
            .filter { capability in it.capability }
            .filter { modelStorage.isInstalled(it.id) }
            .minByOrNull { it.tier.ordinal }
            ?.id

    /**
     * The installed model whose tier is closest to [preferredTier] — never a hard requirement.
     * A mode that prefers [com.example.core.model.ModelTier.HIGH_QUALITY] but only finds
     * `LIGHTWEIGHT` installed still gets that model back rather than refusing to run at all; a
     * mode never assumes the biggest installed model is automatically the right choice either —
     * it degrades toward its own preferred tier, not toward whatever happens to be biggest.
     * Returns null only when nothing installed has [capability] at all.
     */
    fun resolveForModeOrNull(
        modelStorage: ModelStorage,
        capability: ModelCapability,
        preferredTier: com.example.core.model.ModelTier
    ): String? {
        val installed = ModelCatalog.entries
            .filter { capability in it.capability }
            .filter { modelStorage.isInstalled(it.id) }
        if (installed.isEmpty()) return null
        return installed.find { it.tier == preferredTier }?.id
            ?: installed.minByOrNull { kotlin.math.abs(it.tier.ordinal - preferredTier.ordinal) }?.id
    }
}
