package com.craftflowtechnologies.meetingmind.feature.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

/**
 * The processing route is `processing/{meetingId}/{audioPath}/{durationMs}`. The NavHost matches each
 * `{...}` argument against a non-empty path segment, so a route with an empty audio path does not
 * match anything: navigating to it throws, and that throw used to crash the app when a recording
 * that was still processing was tapped with no audio path on record.
 */
class ProcessingRouteTest {

    private val nav = Regex("processing/([^/]+)/([^/]+)/([^/]+)")

    private fun matchOrFail(route: String) =
        nav.matchEntire(route) ?: throw AssertionError("Route \"$route\" does not match the processing pattern")

    @Test
    fun `a missing or blank audio path still produces a route the NavHost can match`() {
        for (path in listOf("", "   ")) {
            val route = Routes.processingRoute("meeting-1", path, 5_000L)
            val match = matchOrFail(route)
            assertEquals("meeting-1", match.groupValues[1])
            assertEquals(5_000L, match.groupValues[3].toLong())
        }
    }

    @Test
    fun `a real audio path survives the round trip through the route`() {
        val path = "/data/user/0/app/files/meetings/abc/audio one & two.m4a"
        val route = Routes.processingRoute("meeting-2", path, 12_000L)
        val segment = matchOrFail(route).groupValues[2]
        assertEquals(path, URLDecoder.decode(segment, "UTF-8"))
    }

    @Test
    fun `an absent audio path decodes back to nothing, not to the placeholder`() {
        val route = Routes.processingRoute("meeting-3", "", 0L)
        val segment = matchOrFail(route).groupValues[2]
        assertEquals("", Routes.processingAudioPath(segment))
        val real = matchOrFail(Routes.processingRoute("m", "/a/b.m4a", 0L)).groupValues[2]
        assertEquals("/a/b.m4a", Routes.processingAudioPath(real))
    }

    @Test
    fun `the placeholder never collides with a real absolute path`() {
        assertTrue(!Routes.NO_AUDIO_PATH_SEGMENT.startsWith("/"))
    }
}
