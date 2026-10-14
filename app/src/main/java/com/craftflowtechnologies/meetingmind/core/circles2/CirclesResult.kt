package com.craftflowtechnologies.meetingmind.core.circles2

/** Why a Circles call failed, in terms the UI can act on. */
enum class FailureKind { NotConnected, Offline, SignedOut, Rejected, RateLimited, NotFound, Server }

/** A failure with one plain sentence that is safe to show. */
data class CirclesFailure(val kind: FailureKind, val message: String, val code: String? = null, val requestId: String? = null, val httpStatus: Int? = null) {
    companion object {
        val notConnected = CirclesFailure(FailureKind.NotConnected, "Circles isn't connected yet.")
        val offline = CirclesFailure(FailureKind.Offline, "You're offline. Check your connection and try again.")
        val signedOut = CirclesFailure(FailureKind.SignedOut, "Couldn't sign in to Circles. Try again in a moment.")
        fun server(message: String = "Something went wrong. Please try again.") = CirclesFailure(FailureKind.Server, message)
    }
}

/** Result type for everything on a UI path. There is no throwing accessor on purpose. */
sealed interface CirclesResult<out T> {
    data class Ok<T>(val value: T) : CirclesResult<T>
    data class Err(val failure: CirclesFailure) : CirclesResult<Nothing>

    val okOrNull: T? get() = (this as? Ok)?.value
    val failureOrNull: CirclesFailure? get() = (this as? Err)?.failure

    fun <R> map(f: (T) -> R): CirclesResult<R> = when (this) {
        is Ok -> Ok(f(value))
        is Err -> this
    }

    suspend fun <R> flatMap(f: suspend (T) -> CirclesResult<R>): CirclesResult<R> = when (this) {
        is Ok -> f(value)
        is Err -> this
    }

    fun <R> fold(onOk: (T) -> R, onErr: (CirclesFailure) -> R): R = when (this) {
        is Ok -> onOk(value)
        is Err -> onErr(failure)
    }
}

fun <T> CirclesFailure.asResult(): CirclesResult<T> = CirclesResult.Err(this)

/** The state of a live Firestore listener. Errors are values, never exceptions. */
sealed interface DataState<out T> {
    data object Loading : DataState<Nothing>
    data class Ready<T>(val value: T, val fromCache: Boolean = false) : DataState<T>
    data class Failed(val failure: CirclesFailure) : DataState<Nothing>
}

fun <T> DataState<T>.valueOr(default: T): T = (this as? DataState.Ready)?.value ?: default

/**
 * Maps a failed anonymous sign-in to one plain sentence. Only the Firebase error code is used, so no user data
 * reaches the UI or the log. Codes arrive either as `ERROR_OPERATION_NOT_ALLOWED` or as `operation-not-allowed`;
 * both are matched.
 */
fun anonymousSignInFailure(errorCode: String?): CirclesFailure {
    val code = errorCode?.uppercase()?.removePrefix("ERROR_")?.replace('-', '_')
    return when (code) {
        "NETWORK_REQUEST_FAILED" -> CirclesFailure.offline
        "OPERATION_NOT_ALLOWED", "ADMIN_RESTRICTED_OPERATION" ->
            CirclesFailure(FailureKind.SignedOut, "Circles isn't set up to open accounts yet. Please try again later.", code = errorCode)
        else -> CirclesFailure(FailureKind.SignedOut, CirclesFailure.signedOut.message, code = errorCode)
    }
}
