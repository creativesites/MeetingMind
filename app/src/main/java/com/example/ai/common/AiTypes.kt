package com.example.ai.common

/**
 * Result type for every AI subsystem call (VAD, ASR, diarization, LLM, embeddings).
 *
 * This exists so the app can never fabricate an AI result: a caller either gets a real
 * [Success] value produced by a real model, or one of the honest failure variants below.
 * No implementation may return [Success] with invented/placeholder content.
 */
sealed interface AiResult<out T> {
    data class Success<T>(val value: T) : AiResult<T>

    /** No model is installed for this capability yet. */
    data class ModelUnavailable(val modelId: String, val message: String) : AiResult<Nothing>

    /** This device cannot run the required model (e.g. unsupported ABI). */
    data class DeviceUnsupported(val reason: String) : AiResult<Nothing>

    /** The device does not have enough free RAM to load the required model. */
    data class InsufficientMemory(val requiredMb: Int, val availableMb: Int) : AiResult<Nothing>

    /** The model was available but inference failed at runtime. */
    data class Failed(val message: String, val cause: Throwable? = null) : AiResult<Nothing>
}

inline fun <T, R> AiResult<T>.map(transform: (T) -> R): AiResult<R> = when (this) {
    is AiResult.Success -> AiResult.Success(transform(value))
    is AiResult.ModelUnavailable -> this
    is AiResult.DeviceUnsupported -> this
    is AiResult.InsufficientMemory -> this
    is AiResult.Failed -> this
}

/** A short, user-facing description of why an [AiResult] failure occurred. */
fun AiResult<*>.describeFailure(): String? = when (this) {
    is AiResult.Success -> null
    is AiResult.ModelUnavailable -> message
    is AiResult.DeviceUnsupported -> "This device is not supported: $reason"
    is AiResult.InsufficientMemory -> "Not enough free memory (${availableMb}MB available, ${requiredMb}MB required)."
    is AiResult.Failed -> message
}

/**
 * Whether a given AI capability (VAD, ASR, diarization, LLM, embeddings) is currently usable
 * on this device, independent of any specific call. Screens use this to decide whether to
 * show real controls or an honest "not installed" state before the user even tries to act.
 */
sealed interface AiAvailability {
    data object Available : AiAvailability
    data class ModelNotInstalled(val modelId: String) : AiAvailability
    data class ModelDownloading(val modelId: String, val progress: Float) : AiAvailability
    data class DeviceNotSupported(val reason: String) : AiAvailability
    data class InsufficientMemory(val requiredMb: Int, val availableMb: Int) : AiAvailability
    data class Error(val message: String) : AiAvailability
}

/** Thrown only in places that must surface an unavailable-model condition as an exception. */
class AiUnavailableException(message: String) : Exception(message)
