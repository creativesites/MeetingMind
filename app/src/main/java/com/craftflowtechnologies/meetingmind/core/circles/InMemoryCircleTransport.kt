package com.craftflowtechnologies.meetingmind.core.circles

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Shared memory bus for testing multi-member circles without network or backend calls.
 */
class InMemoryCircleBus {
    private val circleEvents = mutableMapOf<String, MutableStateFlow<List<CircleTransportEvent>>>()

    @Synchronized
    fun getFlow(circleId: String): MutableStateFlow<List<CircleTransportEvent>> {
        return circleEvents.getOrPut(circleId) { MutableStateFlow(emptyList()) }
    }

    @Synchronized
    fun post(circleId: String, event: CircleTransportEvent) {
        val flow = getFlow(circleId)
        flow.value = flow.value + event
    }

    @Synchronized
    fun clear() {
        circleEvents.clear()
    }
}

/**
 * In-memory client transport backed by [InMemoryCircleBus].
 * Perfect for local unit testing and previewing multi-device sync logic.
 */
class InMemoryCircleTransport(
    override var currentUserId: String = UUID.randomUUID().toString(),
    private val bus: InMemoryCircleBus
) : CircleTransport {

    override suspend fun ensureAuthenticated(): String = currentUserId

    override suspend fun publishEvent(circleId: String, iv: String, ciphertext: String): Result<String> {
        val eventId = UUID.randomUUID().toString()
        val event = CircleTransportEvent(
            id = eventId,
            uid = currentUserId,
            timestamp = System.currentTimeMillis(),
            iv = iv,
            ciphertext = ciphertext
        )
        bus.post(circleId, event)
        return Result.success(eventId)
    }

    override fun observeEvents(circleId: String): Flow<List<CircleTransportEvent>> {
        return bus.getFlow(circleId).map { it.toList() }
    }
}
