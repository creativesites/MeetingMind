package com.craftflowtechnologies.meetingmind.ai.voice.read

import android.net.Uri
import com.craftflowtechnologies.meetingmind.core.audio.QueueItem
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainThreadReadPlayerTest {
    private val item = QueueItem("m1", "T", Uri.parse("file:///x.m4a"))

    @Test fun `player calls from a background thread reach the delegate on the target thread in order`() {
        val target = Executors.newSingleThreadExecutor { r -> Thread(r, "fake-main") }
        val seen = mutableListOf<Pair<String, String>>()
        val delegate = object : ReadPlayer {
            override fun play(queueId: String, items: List<QueueItem>, startIndex: Int, startPositionMs: Long, expected: Int) { seen += "play" to Thread.currentThread().name }
            override fun append(item: QueueItem) { seen += "append" to Thread.currentThread().name }
            override fun setExpected(count: Int) { seen += "expected" to Thread.currentThread().name }
        }
        val player = MainThreadReadPlayer(delegate) { target.execute(it) }
        val caller = Executors.newSingleThreadExecutor { r -> Thread(r, "worker") }
        caller.submit { player.play("q", listOf(item), 0, 0, 2); player.append(item); player.setExpected(2) }.get(5, TimeUnit.SECONDS)
        target.submit { }.get(5, TimeUnit.SECONDS)
        assertEquals(listOf("play", "append", "expected"), seen.map { it.first })
        assertEquals(setOf("fake-main"), seen.map { it.second }.toSet())
        target.shutdown(); caller.shutdown()
    }
}
