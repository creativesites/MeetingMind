package com.craftflowtechnologies.meetingmind.core.learning

import com.craftflowtechnologies.meetingmind.ai.cloud.UsageMeter
import com.craftflowtechnologies.meetingmind.ai.cloud.UsageState
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.common.describeFailure
import com.craftflowtechnologies.meetingmind.ai.learning.StudyKitResult
import com.craftflowtechnologies.meetingmind.core.analytics.Analytics
import com.craftflowtechnologies.meetingmind.core.analytics.Events

/**
 * How one Learning AI call ended, in terms the screens can act on. Studying what is already stored
 * (lessons, practice, sky, listen, notes) never depends on this: only the making of new AI output does.
 *
 * [AllowanceUsedUp] is the managed cloud allowance, not a broken key or a missing plan. Screens say
 * "Your lessons and practice still work" and offer plans, never a dead end.
 */
sealed interface LearningAiStatus {
    data object Ready : LearningAiStatus

    /** No model is available here (offline with no on-device model, or AI not set up). [message] says what to do. */
    data class NoAi(val message: String) : LearningAiStatus

    /** This period's managed allowance is used up. [resetsAtMs] is 0 when the service did not say when. */
    data class AllowanceUsedUp(val resetsAtMs: Long = 0L) : LearningAiStatus

    /** Anything else. [retryable] says whether trying again could plausibly work. */
    data class Failed(val message: String, val retryable: Boolean) : LearningAiStatus

    /** The fixed vocabulary used in analytics. Never free text. */
    val outcome: String
        get() = when (this) {
            Ready -> OUTCOME_READY
            is NoAi -> OUTCOME_NO_AI
            is AllowanceUsedUp -> OUTCOME_ALLOWANCE
            is Failed -> OUTCOME_FAILED
        }

    companion object {
        const val NO_AI_MESSAGE = "Set up AI in Settings to make this."
        const val OUTCOME_READY = "ready"
        const val OUTCOME_LOCAL = "made_on_phone"
        const val OUTCOME_NO_AI = "no_ai"
        const val OUTCOME_ALLOWANCE = "allowance_used_up"
        const val OUTCOME_FAILED = "failed"

        /**
         * Classifies one finished call. The allowance is read from the managed meter after the call
         * ([UsageState.atLimit]) or from the transport's own "allowance … used up" message (the on-device
         * and DeepSeek routes say it that way), so either source is enough.
         */
        fun <T> of(result: AiResult<T>, usage: UsageState = UsageMeter.state.value): LearningAiStatus = when (result) {
            is AiResult.Success -> Ready
            is AiResult.ModelUnavailable, is AiResult.DeviceUnsupported,
            is AiResult.InsufficientMemory, is AiResult.InsufficientStorage ->
                NoAi(result.describeFailure() ?: NO_AI_MESSAGE)
            is AiResult.Failed -> when {
                usage.atLimit || isAllowanceMessage(result.message) -> AllowanceUsedUp(usage.periodEndMs)
                else -> Failed(result.message, result.retryable)
            }
        }

        internal fun isAllowanceMessage(message: String): Boolean =
            message.contains("allowance", ignoreCase = true) && message.contains("used up", ignoreCase = true)
    }
}

/**
 * Analytics for Learning's AI. One event, `study_ai`, with a fixed feature name and outcome. Never a
 * note text, title, transcript, prompt or id.
 */
object StudyAiAnalytics {
    const val FEATURE_LESSON_KIT = "lesson_kit"
    const val FEATURE_EXPLAIN = "explain"
    const val FEATURE_LESSON_ASK = "lesson_ask"
    const val FEATURE_HOME_ASK = "home_ask"

    fun track(feature: String, status: LearningAiStatus, madeOnPhone: Boolean = false) {
        val outcome = if (madeOnPhone && status is LearningAiStatus.Ready) LearningAiStatus.OUTCOME_LOCAL else status.outcome
        Analytics.track(Events.STUDY_AI, "feature" to feature, "outcome" to outcome)
    }
}

/** The outcome of a lesson generation, for analytics. */
fun StudyKitResult.toLearningStatus(): LearningAiStatus = when (this) {
    is StudyKitResult.Ready, is StudyKitResult.NoTranscript -> LearningAiStatus.Ready
    is StudyKitResult.NeedsAi -> LearningAiStatus.NoAi(message)
    is StudyKitResult.AllowanceUsedUp -> LearningAiStatus.AllowanceUsedUp(resetsAtMs)
    is StudyKitResult.Failed -> LearningAiStatus.Failed(message, retryable = true)
}

/**
 * The button and copy for an allowance panel: "See plans" only when this build can sell plans. A build
 * with no billing shows the reset date and nothing to tap.
 */
fun plansAction(billingConfigured: Boolean, onOpenPlans: () -> Unit): (() -> Unit)? =
    if (billingConfigured) onOpenPlans else null
