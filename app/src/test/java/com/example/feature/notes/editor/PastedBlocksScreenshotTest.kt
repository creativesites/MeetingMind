package com.example.feature.notes.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.dp
import com.example.core.model.NoteBlockType
import com.example.core.notes.MarkdownImport
import com.example.ui.theme.MeetMindTheme
import com.example.ui.theme.SurfaceBase
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class PastedBlocksScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `pasted code and tables render as their own blocks`() {
        val blocks = MarkdownImport.parse(
            """
            | Owner | Task | Due |
            |---|---|---|
            | Winston | **API** update | Fri |
            | John | Review `deploy` | Thu |

            ```kotlin
            fun main() {
                println("hi")
            }
            ```
            """.trimIndent(), "n"
        )
        compose.setContent {
            MeetMindTheme {
                Column(Modifier.background(SurfaceBase).padding(16.dp)) {
                    blocks.forEach { b ->
                        when (b.type) {
                            NoteBlockType.CODE -> CodeBlock(b) {}
                            NoteBlockType.TABLE -> TableBlock(b)
                            NoteBlockType.EMBED -> EmbedBlock(b)
                            else -> {}
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/pasted_blocks.png")
        compose.onNodeWithText("Winston").assertIsDisplayed()
        compose.onNodeWithText("Copy").assertIsDisplayed()
    }
}
