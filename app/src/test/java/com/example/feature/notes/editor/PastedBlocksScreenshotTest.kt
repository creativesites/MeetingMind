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
    fun `a long paste is one block that shows formatted and folds`() {
        val md = buildString {
            append("## Launch plan\n\nHere's a plan that keeps **scope tight** and ships by *Friday*.\n\n")
            append("- [ ] Winston: update the API\n- [x] John: review deployment\n\n")
            append("1. Draft the API\n2. Review with `John`\n\n> Ship small, ship often.\n\n")
            (1..30).forEach { append("Paragraph $it of a long answer.\n\n") }
        }
        val block = com.example.core.model.NoteBlock("m", "n", 0, NoteBlockType.MARKDOWN, com.example.core.notes.RichText.plain(md))
        compose.setContent {
            MeetMindTheme {
                Column(Modifier.background(com.example.ui.theme.LocalMMColors.current.background).padding(16.dp)) {
                    MarkdownBlock(block, serif = false, editing = false, onEdit = {}, onText = {}, onToggleExpanded = {})
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/markdown_block.png")
        compose.onNodeWithText("Launch plan").assertIsDisplayed()
        compose.onNodeWithText("Show all", substring = true).assertIsDisplayed()
    }

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
