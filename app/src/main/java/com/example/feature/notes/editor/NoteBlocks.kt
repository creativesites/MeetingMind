package com.example.feature.notes.editor

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.core.common.Formatters
import com.example.core.model.Attachment
import com.example.core.model.BlockSource
import com.example.core.model.NoteBlock
import com.example.core.model.NoteBlockType
import com.example.core.notes.InlineStyle
import com.example.core.notes.RichText
import com.example.ui.theme.Accent
import com.example.ui.theme.AccentWash
import com.example.ui.theme.Ink
import com.example.ui.theme.InkFaint
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.Line
import com.example.ui.theme.LineSoft
import com.example.ui.theme.Speaker3
import com.example.ui.theme.SurfaceSunk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Every text field starts with this invisible character, and the caret is never allowed before
 * it. Soft keyboards report no event for Backspace in an empty field or at position 0, but they
 * do delete this character — which is how the editor knows to join the block to the one above.
 */
internal const val SENTINEL = '\u200B'

private val HighlightColor = Color(0xFFFFF1A6)

/** Space above a text block; headings get more so sections read as sections. */
internal fun blockTopPadding(type: NoteBlockType) = when (type) {
    NoteBlockType.HEADING_1 -> 14.dp
    NoteBlockType.HEADING_2 -> 10.dp
    NoteBlockType.HEADING_3 -> 8.dp
    else -> 2.dp
}

/** Text style for a block type; the one place block typography is decided. */
internal fun blockTextStyle(type: NoteBlockType, serif: Boolean): TextStyle {
    val body = if (serif) FontFamily.Serif else FontFamily.Default
    return when (type) {
        NoteBlockType.HEADING_1 -> TextStyle(fontSize = 23.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, color = Ink, letterSpacing = (-0.4).sp)
        NoteBlockType.HEADING_2 -> TextStyle(fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, color = Ink, letterSpacing = (-0.2).sp)
        NoteBlockType.HEADING_3 -> TextStyle(fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold, color = Accent)
        NoteBlockType.QUOTE -> TextStyle(fontSize = 16.sp, lineHeight = 25.sp, fontStyle = FontStyle.Italic, color = InkSecondary, fontFamily = body)
        else -> TextStyle(fontSize = 16.sp, lineHeight = 25.sp, color = Ink, fontFamily = body)
    }
}

/** Draws [RichText]'s ranges over the field's plain text, allowing for the sentinel. */
private class RichTransformation(private val content: RichText, private val struck: Boolean) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text.text)
        val max = text.text.length
        fun add(style: SpanStyle, start: Int, end: Int) {
            val s = (start + 1).coerceIn(0, max)
            val e = (end + 1).coerceIn(0, max)
            if (e > s) builder.addStyle(style, s, e)
        }
        for (span in content.spans) {
            val style = when (span.style) {
                InlineStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                InlineStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                InlineStyle.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
                InlineStyle.STRIKETHROUGH -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                InlineStyle.HIGHLIGHT -> SpanStyle(background = HighlightColor)
                InlineStyle.CODE -> SpanStyle(fontFamily = FontFamily.Monospace, background = SurfaceSunk, fontSize = 14.sp)
                InlineStyle.LINK -> SpanStyle(color = Accent, textDecoration = TextDecoration.Underline)
            }
            add(style, span.start, span.end)
        }
        if (struck) add(SpanStyle(textDecoration = TextDecoration.LineThrough, color = InkMuted), 0, content.text.length)
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

/**
 * One editable text block. Owns its [TextFieldValue]; reports text, caret and the special
 * Backspace-at-start to the editor.
 */
@Composable
internal fun RichBlockField(
    block: NoteBlock,
    serif: Boolean,
    placeholder: String?,
    focusRequest: NoteEditorViewModel.FocusRequest?,
    onText: (text: String, cursor: Int) -> Unit,
    onSelection: (start: Int, end: Int) -> Unit,
    onBackspaceAtStart: () -> Unit,
    onFocusLost: () -> Unit,
    modifier: Modifier = Modifier
) {
    val requester = remember(block.id) { FocusRequester() }
    var value by remember(block.id) {
        mutableStateOf(TextFieldValue(SENTINEL + block.content.text, TextRange(1 + block.content.text.length)))
    }
    // The block changed from outside (undo, a split, a join): show its text.
    if (value.text.drop(1) != block.content.text) {
        val max = block.content.text.length + 1
        value = TextFieldValue(SENTINEL + block.content.text, TextRange(value.selection.start.coerceIn(1, max), value.selection.end.coerceIn(1, max)))
    }

    LaunchedEffect(focusRequest) {
        val target = focusRequest?.target ?: return@LaunchedEffect
        if (target.blockId != block.id) return@LaunchedEffect
        val caret = (target.cursor + 1).coerceIn(1, block.content.text.length + 1)
        value = value.copy(text = SENTINEL + block.content.text, selection = TextRange(caret))
        runCatching { requester.requestFocus() }
    }

    val style = blockTextStyle(block.type, serif)
    Box(modifier = modifier) {
        if (block.content.isEmpty && placeholder != null) {
            Text(placeholder, style = style.copy(color = InkFaint, fontStyle = FontStyle.Normal))
        }
        BasicTextField(
            value = value,
            onValueChange = { next ->
                if (!next.text.startsWith(SENTINEL)) {
                    // The sentinel went: either Backspace at the start, or the whole field was
                    // selected and replaced. Tell them apart by what's left.
                    val remainder = next.text
                    if (remainder == block.content.text || remainder.isEmpty() && block.content.isEmpty) {
                        onBackspaceAtStart()
                        value = value.copy(selection = TextRange(1))
                    } else {
                        val caret = next.selection.start.coerceIn(0, remainder.length)
                        value = TextFieldValue(SENTINEL + remainder, TextRange(caret + 1))
                        onText(remainder, caret)
                    }
                    return@BasicTextField
                }
                val start = next.selection.start.coerceAtLeast(1)
                val end = next.selection.end.coerceAtLeast(1)
                val fixed = if (start != next.selection.start || end != next.selection.end) next.copy(selection = TextRange(start, end)) else next
                value = fixed
                val text = fixed.text.drop(1)
                if (text != block.content.text) onText(text, fixed.selection.end - 1)
                onSelection(fixed.selection.start - 1, fixed.selection.end - 1)
            },
            textStyle = style,
            cursorBrush = SolidColor(Accent),
            visualTransformation = RichTransformation(block.content, block.type == NoteBlockType.CHECKLIST && block.checked),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(requester)
                .onFocusChanged { state ->
                    if (state.isFocused) onSelection(value.selection.start - 1, value.selection.end - 1)
                    else onFocusLost()
                }
        )
    }
}

/** The left-hand gutter: a drag handle that also opens the block's menu. */
@Composable
internal fun BlockHandle(modifier: Modifier = Modifier) {
    Box(modifier = modifier.width(22.dp).heightIn(min = 26.dp), contentAlignment = Alignment.TopCenter) {
        Icon(Icons.Filled.DragIndicator, contentDescription = "Move or change block", tint = InkFaint, modifier = Modifier.padding(top = 3.dp).size(18.dp))
    }
}

/** A text block with its list marker, checkbox or quote bar. */
@Composable
internal fun TextBlock(
    block: NoteBlock,
    number: Int,
    serif: Boolean,
    placeholder: String?,
    focusRequest: NoteEditorViewModel.FocusRequest?,
    onText: (String, Int) -> Unit,
    onSelection: (Int, Int) -> Unit,
    onBackspaceAtStart: () -> Unit,
    onFocusLost: () -> Unit,
    onToggleChecked: () -> Unit
) {
    val topPad = blockTopPadding(block.type)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = topPad, bottom = 2.dp).padding(start = (block.indent * 22).dp),
        verticalAlignment = Alignment.Top
    ) {
        when (block.type) {
            NoteBlockType.BULLET -> Box(Modifier.width(24.dp).height(25.dp), contentAlignment = Alignment.Center) {
                val dot = if (block.indent % 2 == 0) Ink else Color.Transparent
                Box(Modifier.size(6.dp).clip(CircleShape).background(dot).border(1.2.dp, Ink, CircleShape))
            }
            NoteBlockType.NUMBERED -> Box(Modifier.width(26.dp).height(25.dp), contentAlignment = Alignment.TopStart) {
                Text("$number.", style = TextStyle(fontSize = 16.sp, lineHeight = 25.sp, color = InkSecondary, fontWeight = FontWeight.Medium))
            }
            NoteBlockType.CHECKLIST -> Box(Modifier.width(30.dp).height(25.dp).clickable(onClick = onToggleChecked), contentAlignment = Alignment.CenterStart) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (block.checked) Accent else Color.White,
                    border = if (block.checked) null else BorderStroke(1.5.dp, InkMuted),
                    modifier = Modifier.size(19.dp)
                ) {
                    if (block.checked) Icon(Icons.Filled.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.padding(2.dp))
                }
            }
            NoteBlockType.QUOTE -> Box(Modifier.padding(end = 12.dp).width(3.dp).height(25.dp).background(Accent, RoundedCornerShape(2.dp)))
            else -> Unit
        }
        Column(Modifier.weight(1f)) {
            RichBlockField(
                block = block,
                serif = serif,
                placeholder = placeholder,
                focusRequest = focusRequest,
                onText = onText,
                onSelection = onSelection,
                onBackspaceAtStart = onBackspaceAtStart,
                onFocusLost = onFocusLost
            )
            if (block.source != BlockSource.USER) SourceTag(block)
        }
    }
}

/** A quiet line under words the user didn't write, so it's always clear whose they are. */
@Composable
private fun SourceTag(block: NoteBlock) {
    val label = when (block.source) {
        BlockSource.AI -> if (block.isUserEdited) "AI · edited by you" else "Written by AI from the recording"
        BlockSource.TRANSCRIPT -> "From the transcript"
        BlockSource.IMPORTED -> "Imported"
        BlockSource.SCRIPTURE -> "Scripture"
        BlockSource.USER -> return
    }
    Text(label, fontSize = 11.sp, color = InkMuted, modifier = Modifier.padding(top = 1.dp, bottom = 2.dp))
}

@Composable
internal fun DividerBlock() {
    HorizontalDivider(Modifier.padding(vertical = 14.dp), thickness = 1.dp, color = Line)
}

@Composable
internal fun ImageBlock(
    block: NoteBlock,
    attachment: Attachment?,
    onOpen: (Attachment) -> Unit,
    onCaption: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        if (attachment == null) {
            MissingMedia("This picture is no longer available")
            return@Column
        }
        val ratio = if ((attachment.width ?: 0) > 0 && (attachment.height ?: 0) > 0) {
            (attachment.width!!.toFloat() / attachment.height!!).coerceIn(0.5f, 2.2f)
        } else 4f / 3f
        AsyncImage(
            model = File(attachment.path),
            contentDescription = block.content.text.ifBlank { "Picture" },
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceSunk)
                .clickable { onOpen(attachment) }
        )
        CaptionField(block.content.text, onCaption)
    }
}

/** A video: its first frame until tapped, then it plays right here, with controls. */
@Composable
internal fun VideoBlock(block: NoteBlock, attachment: Attachment?, onOpen: (Attachment) -> Unit, onCaption: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        if (attachment == null) { MissingMedia("This video is no longer available"); return@Column }
        var playing by remember(attachment.path) { mutableStateOf(false) }
        val frame by produceState<Bitmap?>(null, attachment.path) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    MediaMetadataRetriever().run {
                        setDataSource(attachment.path)
                        val f = getFrameAtTime(1_000_000)
                        release()
                        f
                    }
                }.getOrNull()
            }
        }
        val ratio = if ((attachment.width ?: 0) > 0 && (attachment.height ?: 0) > 0) (attachment.width!!.toFloat() / attachment.height!!).coerceIn(0.5f, 2.2f) else 16f / 9f
        Box(
            Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(16.dp)).background(Ink),
            contentAlignment = Alignment.Center
        ) {
            if (playing) {
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx ->
                        android.widget.VideoView(ctx).apply {
                            setVideoPath(attachment.path)
                            val controller = android.widget.MediaController(ctx)
                            controller.setAnchorView(this)
                            setMediaController(controller)
                            setOnPreparedListener { it.start() }
                            setOnCompletionListener { playing = false }
                        }
                    },
                    onRelease = { it.stopPlayback() },
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)).clickable { onOpen(attachment) },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open full screen", tint = Color.White, modifier = Modifier.size(16.dp)) }
            } else {
                frame?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().fillMaxHeight()) }
                Surface(onClick = { playing = true }, shape = CircleShape, color = Color.White.copy(alpha = 0.92f), modifier = Modifier.size(54.dp)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play video", tint = Ink, modifier = Modifier.padding(12.dp))
                }
                attachment.durationMs?.let {
                    Text(
                        Formatters.formatDurationHms(it), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        CaptionField(block.content.text, onCaption)
    }
}

/** A short audio clip, played in place. */
@Composable
internal fun AudioBlock(block: NoteBlock, attachment: Attachment?, onCaption: (String) -> Unit) {
    if (attachment == null) { MissingMedia("This audio clip is no longer available"); return }
    var playing by remember { mutableStateOf(false) }
    val player = remember(attachment.path) { MediaPlayer() }
    var prepared by remember { mutableStateOf(false) }
    var positionMs by remember { mutableIntStateOf(0) }
    DisposableEffect(player) { onDispose { runCatching { player.release() } } }
    LaunchedEffect(playing) {
        while (playing) {
            positionMs = runCatching { player.currentPosition }.getOrDefault(0)
            kotlinx.coroutines.delay(250)
        }
    }
    Surface(shape = RoundedCornerShape(16.dp), color = SurfaceSunk, border = BorderStroke(1.dp, LineSoft), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = {
                        runCatching {
                            if (!prepared) {
                                player.setDataSource(attachment.path)
                                player.prepare()
                                player.setOnCompletionListener { playing = false; positionMs = 0 }
                                prepared = true
                            }
                            if (playing) player.pause() else player.start()
                            playing = !playing
                        }
                    },
                    shape = CircleShape, color = Ink, modifier = Modifier.size(40.dp)
                ) {
                    Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (playing) "Pause" else "Play", tint = Color.White, modifier = Modifier.padding(9.dp))
                }
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (playing || positionMs > 0) "${Formatters.formatDurationHms(positionMs.toLong())} / ${Formatters.formatDurationHms(attachment.durationMs ?: 0)}"
                    else "Audio clip · ${Formatters.formatDurationHms(attachment.durationMs ?: 0)}",
                    fontSize = 14.sp, color = InkSecondary
                )
            }
            CaptionField(block.content.text, onCaption)
        }
    }
}

@Composable
private fun CaptionField(caption: String, onCaption: (String) -> Unit) {
    var value by remember { mutableStateOf(caption) }
    if (value != caption && caption.isNotEmpty() && value.isEmpty()) value = caption
    Box(Modifier.fillMaxWidth().padding(top = 6.dp, start = 2.dp)) {
        if (value.isEmpty()) Text("Add a caption", fontSize = 13.sp, color = InkFaint)
        BasicTextField(
            value = value,
            onValueChange = { value = it.replace("\n", " "); onCaption(value) },
            textStyle = TextStyle(fontSize = 13.sp, color = InkSecondary),
            cursorBrush = SolidColor(Accent),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MissingMedia(text: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = SurfaceSunk, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(text, fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(14.dp))
    }
}

/**
 * A recording in the note: play it, read its summary in full, and read the whole transcript right
 * here — tap any paragraph to hear it.
 */
@Composable
internal fun RecordingBlock(
    card: RecordingCard?,
    onPlay: (RecordingCard) -> Unit,
    onOpen: (RecordingCard) -> Unit,
    loadTranscript: suspend (String) -> List<com.example.core.model.TranscriptSegment> = { emptyList() },
    onPlayAt: (String, Long) -> Unit = { _, _ -> }
) {
    var summaryOpen by remember { mutableStateOf(false) }
    var transcriptOpen by remember { mutableStateOf(false) }
    var shown by remember { mutableIntStateOf(40) }
    Surface(
        shape = RoundedCornerShape(18.dp), color = Color.White, border = BorderStroke(1.dp, Line),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        if (card == null) {
            Text("This recording has been deleted.", fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(16.dp))
            return@Surface
        }
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = { onPlay(card) }, enabled = card.audioPath != null,
                    shape = CircleShape, color = Ink, modifier = Modifier.size(42.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play recording", tint = Color.White, modifier = Modifier.padding(10.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(card.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val status = when (card.status) {
                        "PROCESSING" -> "Processing…"
                        "ERROR" -> "Processing didn't finish"
                        "RECORDING" -> "Recording…"
                        else -> "Transcript ready"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Mic, contentDescription = null, tint = InkMuted, modifier = Modifier.size(13.dp))
                        Text(" ${Formatters.formatDurationHms(card.durationMs)} · $status", fontSize = 12.sp, color = InkMuted)
                    }
                }
                IconButtonSmall(Icons.AutoMirrored.Filled.OpenInNew, "Open the recording") { onOpen(card) }
            }
            card.summary?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it, fontSize = 14.sp, lineHeight = 21.sp, color = InkSecondary,
                    maxLines = if (summaryOpen) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp).clickable { summaryOpen = !summaryOpen }
                )
                if (it.length > 220) Toggle(if (summaryOpen) "Show less" else "Read the whole summary") { summaryOpen = !summaryOpen }
            }
            if (card.status == "READY" || card.status == "COMPLETED") {
                Toggle(if (transcriptOpen) "Hide transcript" else "Read the transcript here") { transcriptOpen = !transcriptOpen }
            }
            if (transcriptOpen) {
                val segments by produceState<List<com.example.core.model.TranscriptSegment>?>(null, card.meetingId) { value = loadTranscript(card.meetingId) }
                when {
                    segments == null -> Text("Loading…", fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(top = 8.dp))
                    segments!!.isEmpty() -> Text("No transcript yet.", fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(top = 8.dp))
                    else -> Column(Modifier.padding(top = 8.dp)) {
                        segments!!.take(shown).forEach { seg ->
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPlayAt(card.meetingId, seg.startMs) }
                                    .padding(vertical = 6.dp, horizontal = 4.dp)
                            ) {
                                Text(
                                    listOfNotNull(seg.speakerName, Formatters.formatDurationHms(seg.startMs)).joinToString(" · "),
                                    fontSize = 11.5.sp, color = Accent, fontWeight = FontWeight.Medium
                                )
                                Text(seg.cleanedText ?: seg.text, fontSize = 14.sp, lineHeight = 21.sp, color = Ink)
                            }
                        }
                        if (segments!!.size > shown) Toggle("Show ${minOf(40, segments!!.size - shown)} more of ${segments!!.size - shown} left") { shown += 40 }
                    }
                }
            }
        }
    }
}

@Composable
private fun Toggle(label: String, onClick: () -> Unit) {
    Text(label, fontSize = 13.sp, color = Accent, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 4.dp, horizontal = 2.dp))
}

@Composable
private fun IconButtonSmall(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, tint = InkMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun ExcerptBlock(block: NoteBlock, onOpen: () -> Unit) {
    val speaker = block.payload[NoteBlock.PAYLOAD_SPEAKER]
    val at = block.payload[NoteBlock.PAYLOAD_START_MS]?.toLongOrNull()?.let { Formatters.formatDurationHms(it) }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(12.dp)).background(AccentWash).clickable(onClick = onOpen).padding(12.dp)) {
        Icon(Icons.Filled.FormatQuote, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            var open by remember { mutableStateOf(false) }
            Text(block.content.text, fontSize = 15.sp, lineHeight = 23.sp, color = Ink, fontStyle = FontStyle.Italic,
                maxLines = if (open || block.content.text.length < 400) Int.MAX_VALUE else 6, overflow = TextOverflow.Ellipsis)
            if (block.content.text.length >= 400) Toggle(if (open) "Show less" else "Read all") { open = !open }
            Text(listOfNotNull(speaker, at).joinToString(" · ").ifBlank { "From the recording" }, fontSize = 12.sp, color = Accent, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
internal fun NoteLinkBlock(title: String, onOpen: () -> Unit) {
    Row(
        Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onOpen).padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Link, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(title, fontSize = 16.sp, color = Accent, fontWeight = FontWeight.Medium, textDecoration = TextDecoration.Underline)
    }
}

/** A scripture reference. Live verse text arrives with the scripture module (M5). */
@Composable
internal fun ScriptureBlock(block: NoteBlock) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceSunk).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.MenuBook, contentDescription = null, tint = Speaker3, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(block.payload["reference"] ?: block.content.text.ifBlank { "Scripture" }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
    }
}

internal val blockPlaceholderTypes = mapOf(
    NoteBlockType.HEADING_1 to "Heading",
    NoteBlockType.HEADING_2 to "Heading",
    NoteBlockType.HEADING_3 to "Subheading",
    NoteBlockType.BULLET to "List",
    NoteBlockType.NUMBERED to "List",
    NoteBlockType.CHECKLIST to "To-do",
    NoteBlockType.QUOTE to "Quote"
)

