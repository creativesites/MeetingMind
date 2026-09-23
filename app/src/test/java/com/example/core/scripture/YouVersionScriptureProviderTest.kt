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
    fun `a version the API won't serve is unavailable, one without a copyright line shows its title`() = runBlocking {
        val refused = YouVersionScriptureProvider("key", FakeHttp(mapOf("/v1/bibles/111" to (403 to "{}")))).passage(john316, 111)
        assertEquals(PassageResult.Reason.NOT_LICENSED, (refused as PassageResult.Unavailable).reason)

        // The live API returns the ASV with copyright and promotional_content both null.
        val http = FakeHttp(
            mapOf(
                "/v1/bibles/12" to (200 to """{"id":12,"abbreviation":"ASV","copyright":null,"promotional_content":null,"title":"American Standard Version"}"""),
                "/v1/bibles/12/passages/JHN.3.16?format=text" to (200 to """{"content":"For God so loved the world"}""")
            )
        )
        val found = YouVersionScriptureProvider("key", http).passage(john316, 12) as PassageResult.Found
        assertEquals("American Standard Version", found.passage.attribution)
    }

    private fun fixture(name: String) = javaClass.classLoader!!.getResource("youversion/$name")!!.readText()

    @Test
    fun `the translation list pages through and marks open versions`() = runBlocking {
        val http = FakeHttp(mapOf("/v1/bibles?language_ranges[]=en&page_size=99" to (200 to fixture("bibles_en.json"))))
        val bibles = YouVersionScriptureProvider("key", http).bibles()
        assertEquals(11, bibles.size)
        val bsb = bibles.first { it.id == 3034 }
        assertEquals("BSB", bsb.abbreviation)
        assertTrue(bsb.offlineAllowed)
        assertEquals(66, bsb.books.size)
        // The Orthodox Jewish Bible is copyrighted: readable online, never stored.
        assertTrue(bibles.first { it.abbreviation == "TOJB2011" }.offlineAllowed.not())
        assertTrue(bibles.all { it.attribution.isNotBlank() })
    }

    @Test
    fun `a second page is requested with the token`() = runBlocking {
        val page1 = """{"data":[{"id":1,"abbreviation":"AAA","title":"A"}],"next_page_token":"t/1+"}"""
        val page2 = """{"data":[{"id":2,"abbreviation":"BBB","title":"B"}],"next_page_token":null}"""
        val http = FakeHttp(
            mapOf(
                "/v1/bibles?language_ranges[]=en&page_size=99" to (200 to page1),
                "/v1/bibles?language_ranges[]=en&page_size=99&page_token=t%2F1%2B" to (200 to page2)
            )
        )
        assertEquals(listOf("AAA", "BBB"), YouVersionScriptureProvider("key", http).bibles().map { it.abbreviation })
    }

    @Test
    fun `a chapter is fetched as html and split into verses`() = runBlocking {
        val http = FakeHttp(mapOf(version, "/v1/bibles/3034/passages/PSA.23?format=html" to (200 to fixture("PSA.23_3034.json"))))
        val psalm = BibleBooks.byUsfm("PSA")!!
        val content = (YouVersionScriptureProvider("key", http).chapter(3034, psalm, 23) as ChapterResult.Found).content
        assertEquals(6, content.verses.size)
        assertEquals("BSB", content.abbreviation)
        assertTrue(content.verses[0].text.startsWith("A Psalm of David."))
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
