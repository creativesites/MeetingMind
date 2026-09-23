package com.example.core.scripture

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class YouVersionScriptureProviderTest {

    private class FakeHttp(private val routes: Map<String, Pair<Int, String>>, private val offline: Boolean = false) : YouVersionHttp {
        val calls = mutableListOf<String>()
        override suspend fun get(path: String): Pair<Int, String> {
            calls += path
            if (offline) throw IOException("no network")
            return routes[path] ?: (404 to "{}")
        }
    }

    private val version = "/v1/bibles/3034" to (200 to """{"id":3034,"abbreviation":"BSB","copyright":"The Holy Bible, Berean Standard Bible, BSB is produced in cooperation with Bible Hub."}""")
    private val john316 = ScriptureReferenceParser.parse("John 3:16")!!

    @Test
    fun `text comes back with its attribution and is cached`() = runBlocking {
        val http = FakeHttp(mapOf(version, "/v1/bibles/3034/passages/JHN.3.16?format=text" to (200 to """{"id":"JHN.3.16","content":"For God so loved\n the world…","reference":"John 3:16"}""")))
        val provider = YouVersionScriptureProvider("key", http)

        val result = provider.passage(john316, 3034) as PassageResult.Found
        assertEquals("For God so loved the world…", result.passage.text)
        assertEquals("BSB", result.passage.versionAbbreviation)
        assertTrue(result.passage.attribution.startsWith("The Holy Bible"))

        provider.passage(john316, 3034)
        assertEquals(2, http.calls.size) // version + passage, once each
    }

    @Test
    fun `a range falls back to the short form, then verse by verse`() = runBlocking {
        val ref = ScriptureReferenceParser.parse("John 3:16-17")!!
        val http = FakeHttp(
            mapOf(
                version,
                "/v1/bibles/3034/passages/JHN.3.16?format=text" to (200 to """{"content":"For God so loved the world"}"""),
                "/v1/bibles/3034/passages/JHN.3.17?format=text" to (200 to """{"content":"For God did not send His Son"}""")
            )
        )
        val result = YouVersionScriptureProvider("key", http).passage(ref, 3034) as PassageResult.Found
        assertEquals("For God so loved the world For God did not send His Son", result.passage.text)
        assertTrue(http.calls.any { it.contains("JHN.3.16-JHN.3.17") })
        assertTrue(http.calls.any { it.contains("JHN.3.16-17") })
    }

    @Test
    fun `a version without attribution is treated as unavailable`() = runBlocking {
        val http = FakeHttp(mapOf("/v1/bibles/3034" to (200 to """{"id":3034,"abbreviation":"BSB"}""")))
        val result = YouVersionScriptureProvider("key", http).passage(john316, 3034)
        assertEquals(PassageResult.Reason.NOT_LICENSED, (result as PassageResult.Unavailable).reason)
    }

    @Test
    fun `offline and missing key are reported honestly`() = runBlocking {
        val offline = YouVersionScriptureProvider("key", FakeHttp(emptyMap(), offline = true)).passage(john316, 3034)
        assertEquals(PassageResult.Reason.OFFLINE, (offline as PassageResult.Unavailable).reason)

        val unconfigured = YouVersionScriptureProvider("", FakeHttp(emptyMap())).passage(john316, 3034)
        assertEquals(PassageResult.Reason.NOT_CONFIGURED, (unconfigured as PassageResult.Unavailable).reason)
    }

    @Test
    fun `verse of the day and passage ids round trip`() = runBlocking {
        val http = FakeHttp(mapOf("/v1/verse_of_the_days/42" to (200 to """{"day":42,"passage_id":"ROM.8.28"}""")))
        assertEquals("Romans 8:28", YouVersionScriptureProvider("key", http).verseOfTheDay(42)?.display())
        assertEquals("John 3:16–18", YouVersionScriptureProvider.parsePassageId("JHN.3.16-JHN.3.18")?.display())
        assertEquals("John 3:16–18", YouVersionScriptureProvider.parsePassageId("JHN.3.16-18")?.display())
        assertEquals("Psalm 23", YouVersionScriptureProvider.parsePassageId("PSA.23")?.display())
    }
}
