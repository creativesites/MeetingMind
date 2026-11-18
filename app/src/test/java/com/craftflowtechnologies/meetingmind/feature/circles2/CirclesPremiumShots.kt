package com.craftflowtechnologies.meetingmind.feature.circles2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.circles2.Circle
import com.craftflowtechnologies.meetingmind.core.circles2.ChatMessage
import com.craftflowtechnologies.meetingmind.core.circles2.Member
import com.craftflowtechnologies.meetingmind.core.circles2.Role
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionForceCanvas
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Shared helpers for the Circles premium-redesign screenshots: one capture per theme, light then dark, saved as
 * build/outputs/roborazzi/circles_premium_<name>_<light|dark>.png, plus sample data that never leaves the tests.
 */
internal fun ComposeContentTestRule.circlesPremiumShot(name: String, content: @Composable () -> Unit) {
    val dark = mutableStateOf(false)
    setContent {
        CompositionLocalProvider(LocalCompanionForceCanvas provides true, LocalCompanionReducedMotion provides true) {
            MeetMindTheme(darkTheme = dark.value) { content() }
        }
    }
    // Pack covers decode on Dispatchers.IO, which waitForIdle doesn't wait for: give the large hero decode a moment
    // so the first (light) capture shows the photo, not the empty sunk box behind it.
    waitForIdle()
    repeat(3) {
        Thread.sleep(150)
        waitForIdle()
    }
    for (d in listOf(false, true)) {
        dark.value = d
        waitForIdle()
        onRoot().captureRoboImage("build/outputs/roborazzi/circles_premium_${name}_${if (d) "dark" else "light"}.png")
    }
}

/**
 * Puts a generated photo in the Circles media cache under [path], so MediaImage and the video player find it on disk
 * and never reach for the network. [ext] is "bin" for photos and "mp4" for a video stand-in.
 */
internal fun seedCirclesMedia(path: String, ext: String = "bin", warm: Boolean = true) {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dir = File(context.cacheDir, "circles_media").apply { mkdirs() }
    val file = File(dir, path.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".$ext")
    val bmp = Bitmap.createBitmap(720, 480, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint().apply {
        shader = LinearGradient(
            0f, 0f, 720f, 480f,
            if (warm) android.graphics.Color.rgb(222, 160, 92) else android.graphics.Color.rgb(70, 110, 160),
            if (warm) android.graphics.Color.rgb(120, 70, 60) else android.graphics.Color.rgb(30, 40, 70),
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, 720f, 480f, paint)
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
    file.writeBytes(out.toByteArray())
}

internal object PremiumSamples {
    const val NOW = 1_700_000_000_000L
    const val MINUTE = 60_000L

    val members = listOf(
        Member("owner", "Olive Adeyemi", Role.Owner, title = "Pastor", joinedAt = NOW - 400L * 86_400_000L),
        Member("me", "Ann Mensah", Role.Admin, joinedAt = NOW - 200L * 86_400_000L),
        Member("u3", "Ben Carter", Role.Member, joinedAt = NOW - 90L * 86_400_000L),
        Member("u4", "Chidi Okafor", Role.Member, muted = true, joinedAt = NOW - 30L * 86_400_000L),
        Member("u5", "Grace Phiri", Role.Moderator, joinedAt = NOW - 120L * 86_400_000L),
        Member("u6", "Daniel Banda", Role.Member, external = true, joinedAt = NOW - 10L * 86_400_000L)
    )

    fun faithCircle(): Circle = testCircleForShots().copy(memberCount = 6, description = "Tuesday evenings at the Mensahs'. Prayer, the Word and supper.")

    fun workCircle(): Circle = testCircleForShots().copy(
        id = "w1", name = "Launch Team", template = "project_team", vocab = "Team", space = "work", memberCount = 6,
        settings = testCircleForShots().settings.copy(allowedTypes = com.craftflowtechnologies.meetingmind.core.circles2.PostType.entries.filter { !it.adminOnly })
    )

    fun message(id: String, uid: String, name: String, text: String, ago: Long, kind: com.craftflowtechnologies.meetingmind.core.circles2.MessageKind = com.craftflowtechnologies.meetingmind.core.circles2.MessageKind.Text) =
        ChatMessage(id, uid, name, kind, text, createdAt = NOW - ago * MINUTE)
}

/** The shared test circle from CirclesFakes, under a name that reads clearly in these files. */
internal fun testCircleForShots(): Circle = com.craftflowtechnologies.meetingmind.core.circles2.testCircle()
