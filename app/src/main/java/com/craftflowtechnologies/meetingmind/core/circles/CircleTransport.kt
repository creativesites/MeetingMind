package com.craftflowtechnologies.meetingmind.core.circles

import kotlinx.coroutines.flow.Flow

/**
 * Raw transport event received from or sent to a relay (Firestore or In-Memory).
 * The server/relay only ever sees [uid], [timestamp], [iv], and [ciphertext].
 */
data class CircleTransportEvent(
    val id: String,
    val uid: String,
    val timestamp: Long,
    val iv: String,
    val ciphertext: String
)

/**
 * Pluggable transport layer for Private Circles.
 * Decouples cryptography and state replay from the underlying network implementation.
 */
interface CircleTransport {
    /** Current authenticated user ID (anonymous or named). */
    val currentUserId: String

    /** Guarantees the transport is connected/authenticated before making requests. */
    suspend fun ensureAuthenticated(): String

    /**
     * Publishes an encrypted event to `circles/{circleId}/events`.
     * Returns the generated event ID.
     */
    suspend fun publishEvent(circleId: String, iv: String, ciphertext: String): Result<String>

    /**
     * Real-time stream of all events published in the specified circle, ordered by timestamp.
     */
    fun observeEvents(circleId: String): Flow<List<CircleTransportEvent>>
}
