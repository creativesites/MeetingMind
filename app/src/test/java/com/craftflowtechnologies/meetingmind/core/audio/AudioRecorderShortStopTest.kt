package com.craftflowtechnologies.meetingmind.core.audio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioRecorderShortStopTest {

    @Test
    fun `stopRecording returns null and cleans up when output file is empty or non-existent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val recorder = AudioRecorder(context)

        // When stopRecording is called without a valid audio recording stream
        val result = recorder.stopRecording()

        // It must safely return null instead of a corrupt or zero-byte File pointer
        assertNull(result)
    }

    @Test
    fun `stopRecording cleans up 0-byte stub file and returns null`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val recorder = AudioRecorder(context)

        val meetingDir = File(context.filesDir, "meetings/test_short_stop").apply { mkdirs() }
        val emptyFile = File(meetingDir, "audio.m4a").apply {
            createNewFile() // 0 bytes
        }

        // Inject empty file into private field to simulate fast-abort before frames written
        val field = AudioRecorder::class.java.getDeclaredField("currentOutputFile")
        field.isAccessible = true
        field.set(recorder, emptyFile)

        val result = recorder.stopRecording()

        assertNull("Empty stub must return null", result)
        org.junit.Assert.assertFalse("Empty stub file must be deleted", emptyFile.exists())
    }
}
