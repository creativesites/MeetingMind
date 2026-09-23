package com.example.ai.asr

import com.example.ai.transcript.AsrWindow
import com.example.ai.transcript.CanonicalWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AsrCheckpointStoreTest {

    @get:Rule val temp = TemporaryFolder()

    private val windows = listOf(AsrWindow(0, 0, 25_000), AsrWindow(1, 24_000, 50_000), AsrWindow(2, 49_000, 70_000))
    private fun word(t: String, s: Long) = CanonicalWord(id = "x", text = t, startMs = s, endMs = s + 300)

    @Test
    fun `finished windows come back after a restart`() {
        val store = AsrCheckpointStore(temp.root)
        store.append("m1", "parakeet", windows, 0, listOf(word("Grace", 100), word("and peace, friends", 500)))
        store.append("m1", "parakeet", windows, 1, emptyList())

        val loaded = AsrCheckpointStore(temp.root).load("m1", "parakeet", windows)
        assertEquals(setOf(0, 1), loaded.keys)
        assertEquals(listOf("Grace", "and peace, friends"), loaded.getValue(0).map { it.text })
        assertEquals(500L, loaded.getValue(0)[1].startMs)
        assertTrue(loaded.getValue(1).isEmpty())
    }

    @Test
    fun `a different model or window layout ignores old results`() {
        val store = AsrCheckpointStore(temp.root)
        store.append("m1", "parakeet", windows, 0, listOf(word("a", 0)))
        assertTrue(store.load("m1", "other-model", windows).isEmpty())
        assertTrue(store.load("m1", "parakeet", windows.dropLast(1)).isEmpty())
    }

    @Test
    fun `a line cut off mid write is decoded again`() {
        val store = AsrCheckpointStore(temp.root)
        store.append("m1", "parakeet", windows, 0, listOf(word("a", 0)))
        temp.root.listFiles()!!.single().appendText("1\t100,200,hal")
        assertEquals(setOf(0), store.load("m1", "parakeet", windows).keys)
    }

    @Test
    fun `clear removes the checkpoint`() {
        val store = AsrCheckpointStore(temp.root)
        store.append("m1", "parakeet", windows, 0, listOf(word("a", 0)))
        store.clear("m1")
        assertTrue(store.load("m1", "parakeet", windows).isEmpty())
    }
}
