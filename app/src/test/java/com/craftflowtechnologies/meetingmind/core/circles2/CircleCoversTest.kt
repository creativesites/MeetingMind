package com.craftflowtechnologies.meetingmind.core.circles2

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CircleCoversTest {
    @Test fun `no cover falls back to the picture for the circle's type, then its space`() {
        assertEquals(CircleCovers.Source.Pack("photo_510"), CircleCovers.resolve(null, "bible_study", "faith"))
        assertEquals(CircleCovers.Source.Pack("photo_987"), CircleCovers.resolve("", "project_team", "work"))
        assertEquals(CircleCovers.Source.Pack("photo_476"), CircleCovers.resolve(null, "something_new", "learning"))
        assertEquals(CircleCovers.Source.Pack("photo_994"), CircleCovers.resolve(null, null, null))
    }

    @Test fun `a pack reference draws the pack picture, an upload draws the upload, and a bad reference falls back`() {
        assertEquals(CircleCovers.Source.Pack("photo_206"), CircleCovers.resolve("pack:photo_206", "project_team", "work"))
        assertEquals(CircleCovers.Source.Uploaded("circles/c1/media/u1"), CircleCovers.resolve("circles/c1/media/u1", "family", "faith"))
        assertEquals(CircleCovers.Source.Pack("photo_468"), CircleCovers.resolve("pack:../secret", "family", "faith"))
        assertNull(CircleCovers.packId("pack:photo_"))
        assertEquals("pack:photo_1056", CircleCovers.packRef("photo_1056"))
    }

    @Test fun `every circle type has a default from the pack, and every space gets its own`() {
        CircleTemplate.allTemplates.forEach { t ->
            assertTrue("${t.id} has no pack picture", CircleCovers.defaultFor(t.id, t.space) in CircleCovers.packIds)
        }
        val bySpace = listOf("faith", "work", "learning", "general").map { CircleCovers.defaultFor(null, it) }
        assertEquals(4, bySpace.distinct().size)
    }

    @Test fun `choices start with the type's picture and offer the whole pack once`() {
        val c = CircleCovers.choices("exam_prep_room", "learning")
        assertEquals("photo_1000", c.first())
        assertEquals(c.distinct(), c)
        assertEquals(CircleCovers.packIds.toSet(), c.toSet())
    }

    @Test fun `the pack ids match the shipped image pack, each with its file and thumbnail`() {
        val dir = listOf(File("src/main/assets/backgrounds"), File("app/src/main/assets/backgrounds")).first { it.isDirectory }
        val index = JSONArray(File(dir, "index.json").readText())
        val ids = (0 until index.length()).map { index.getJSONObject(it).getString("id") }
        assertEquals(ids, CircleCovers.packIds)
        ids.forEach { id ->
            val n = id.removePrefix("photo_")
            assertTrue(File(dir, "bg_$n.webp").isFile)
            assertTrue(File(dir, "bg_${n}_thumb.webp").isFile)
        }
    }

    @Test fun `a pack cover is saved as a reference with no upload, and a photo is uploaded first`() = runBlocking {
        val api = FakeApi()
        val repo = CirclesRepository(api, FakeData(), FakeAuth(), InMemoryLocalCircleStore(), clock = { 1L })
        assertTrue(repo.setCover("c1", CoverChoice.Pack("photo_206")) is CirclesResult.Ok)
        assertEquals(listOf("updateCircle"), api.calls)
        assertEquals("pack:photo_206", api.lastCoverPath)

        api.calls.clear()
        assertTrue(repo.setCover("c1", CoverChoice.Photo(ByteArray(10), "image/jpeg", "me.jpg")) is CirclesResult.Ok)
        assertEquals(listOf("createUploadTicket", "uploadMedia", "updateCircle"), api.calls)
        assertEquals("circles/c1/media/uuid-1", api.lastCoverPath)
    }

    @Test fun `a cover that fails says so calmly and changes nothing`() = runBlocking {
        val api = FakeApi().apply { nextFailure = CirclesFailure.offline }
        val repo = CirclesRepository(api, FakeData(), FakeAuth(), InMemoryLocalCircleStore(), clock = { 1L })
        val r = repo.setCover("c1", CoverChoice.Photo(ByteArray(10), "image/jpeg", null))
        assertTrue(r is CirclesResult.Err)
        assertNull(api.lastCoverPath)
        assertEquals("The cover didn't save because you're offline. Try again when you're connected.", coverFailureLine((r as CirclesResult.Err).failure))
        assertEquals("That cover couldn't be saved yet. Try again later.", coverFailureLine(CirclesFailure(FailureKind.Rejected, "x", "bad_request")))
    }
}
