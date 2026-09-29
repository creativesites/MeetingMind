package com.example.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioBenchmarkTest {
    private val labels = AudioBenchmark.parseLabels("""{"segments":[
        {"start":"0:00","end":"1:00","activity":"music"},
        {"start":"1:00","end":"3:00","activity":"SPEECH"},
        {"start":90,"end":"1:30","activity":"crowd"},
        {"start":"3:00","end":"3:10","activity":"bogus"}]}""")

    @Test fun readsLabelsInAnyTimeFormatAndDropsUnknownActivities() {
        assertEquals(3, labels.size)
        assertEquals(60_000L, labels[0].endMs)
        assertEquals(90_000L, AudioBenchmark.mmss("1:30"))
        assertEquals(3_690_000L, AudioBenchmark.mmss("1:01:30"))
        assertEquals(90_000L, AudioBenchmark.mmss(90))
    }

    @Test fun scoresAgreementPerActivityAndNamesTheMistakes() {
        val predicted = listOf(
            AcousticSegment(0, 50_000, AcousticActivity.MUSIC),      // 10s of music called speech below
            AcousticSegment(50_000, 180_000, AcousticActivity.SPEECH)
        )
        val r = AudioBenchmark.score("a.m4a", predicted, labels.filter { it.activity != AcousticActivity.CROWD })
        assertEquals(180, r.seconds)
        assertEquals(170.0 / 180, r.accuracy, 1e-9)
        assertEquals(50.0 / 60, r.perActivity.getValue(AcousticActivity.MUSIC).recall, 1e-9)
        assertEquals(1.0, r.perActivity.getValue(AcousticActivity.MUSIC).precision, 1e-9)
        assertEquals(10, r.confusions[AcousticActivity.MUSIC to AcousticActivity.SPEECH])
    }

    @Test fun reportsOnAFolderAndSkipsAudioWithoutLabels() {
        val dir = File.createTempFile("bench", "d").also { it.delete(); it.mkdirs() }
        File(dir, "one.m4a").writeBytes(ByteArray(10)); File(dir, "one.labels.json").writeText("""{"segments":[{"start":0,"end":60,"activity":"SPEECH"}]}""")
        File(dir, "two.m4a").writeBytes(ByteArray(10))
        val report = AudioBenchmark.runFolder(dir) { listOf(AcousticSegment(0, 60_000, AcousticActivity.SPEECH)) }
        assertTrue(report.contains("1 recording(s), 60 labelled seconds"))
        assertTrue(report.contains("100%"))
        assertTrue(File(dir, "report.md").exists())
        assertTrue(AudioBenchmark.report(emptyList()).contains("No recordings"))
    }
}
