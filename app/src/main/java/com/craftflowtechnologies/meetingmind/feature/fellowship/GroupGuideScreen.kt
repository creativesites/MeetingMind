package com.craftflowtechnologies.meetingmind.feature.fellowship

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craftflowtechnologies.meetingmind.ai.notes.GroupGuideExport
import com.craftflowtechnologies.meetingmind.ai.notes.StudyGuideFormatter
import com.craftflowtechnologies.meetingmind.core.share.GroupGuidePdf
import com.craftflowtechnologies.meetingmind.core.share.ShareActions
import com.craftflowtechnologies.meetingmind.core.share.ShareHelper
import com.craftflowtechnologies.meetingmind.core.share.ShareTarget
import com.craftflowtechnologies.meetingmind.ui.theme.FaithGold
import com.craftflowtechnologies.meetingmind.ui.theme.FaithGoldInk
import com.craftflowtechnologies.meetingmind.ui.theme.FaithGoldWash
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkFaint
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.Line
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceSunk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Make a discussion guide from a sermon or Bible-study note, edit it, and send it to a group. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupGuideScreen(noteId: String, noteTitle: String, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application
    val vm: GroupGuideViewModel = viewModel(key = "guide-$noteId", factory = GroupGuideViewModel.Factory(app, noteId))
    val state by vm.state.collectAsState()
    val sections by vm.sections.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var sending by remember { mutableStateOf(false) }

    fun title() = GroupGuideExport.title(noteTitle)
    fun sharesDir() = File(context.cacheDir, "shares")

    GroupGuideContent(
        noteTitle = noteTitle, state = state, sections = sections, snackbar = snackbar,
        onBack = onNavigateBack, onCreate = vm::generate, onCancel = vm::cancel,
        onEdit = vm::edit, onRemove = vm::remove, onAdd = vm::add, onSend = { sending = true }
    )

    if (sending) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { sending = false }, sheetState = sheet, containerColor = SurfaceBase) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                Text("Send the guide", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif, color = Ink, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
                SendRow(Icons.AutoMirrored.Filled.Send, "Message", "WhatsApp, Messages or any app on your phone") {
                    sending = false
                    ShareHelper.shareText(context, title(), StudyGuideFormatter.toWhatsApp(title(), vm.draft()), "Share discussion guide")
                }
                SendRow(Icons.Filled.ContentCopy, "Copy for WhatsApp", "Keeps the bold headings when you paste") {
                    sending = false
                    clipboard.setText(AnnotatedString(StudyGuideFormatter.toWhatsApp(title(), vm.draft())))
                    scope.launch { snackbar.showSnackbar("Copied. Paste it into your group chat.") }
                }
                SendRow(Icons.Filled.PictureAsPdf, "PDF", "A clean page to print or email") {
                    sending = false
                    scope.launch {
                        val file = withContext(Dispatchers.IO) { GroupGuidePdf.write(sharesDir(), title(), vm.draft()) }
                        ShareActions.send(context, file, ShareTarget.ANY, mime = "application/pdf")
                    }
                }
                SendRow(Icons.Filled.Description, "Markdown file", "For notes apps and shared folders") {
                    sending = false
                    scope.launch {
                        val file = withContext(Dispatchers.IO) {
                            sharesDir().mkdirs()
                            File(sharesDir(), GroupGuideExport.fileName(title()) + ".md").also { it.writeText(GroupGuideExport.toMarkdown(title(), vm.draft())) }
                        }
                        ShareActions.send(context, file, ShareTarget.ANY, mime = "text/markdown")
                    }
                }
            }
        }
    }
}

/** The screen without its ViewModel, so it can be drawn and tested with any state. */
@Composable
internal fun GroupGuideContent(
    noteTitle: String,
    state: GuideState,
    sections: List<GuideSection>,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onCancel: () -> Unit,
    onEdit: (Int, Int, String) -> Unit,
    onRemove: (Int, Int) -> Unit,
    onAdd: (Int) -> Unit,
    onSend: () -> Unit
) {
    Scaffold(
        containerColor = SurfaceBase,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { FellowshipTopBar("Group guide", noteTitle.ifBlank { "Your sermon note" }, onBack) },
        bottomBar = {
            if (state is GuideState.Ready) {
                Surface(color = SurfaceBase, border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FellowshipPrimaryButton("Send", onSend, Modifier.weight(1f).testTag("guide_send"), icon = Icons.Filled.Share)
                        FellowshipSecondaryButton("Start over", onCreate, Modifier.weight(1f), icon = Icons.Filled.Refresh)
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                GuideState.Idle -> GuideIntro(onCreate)
                is GuideState.Working -> GuideWorking(onCancel)
                is GuideState.Failed -> FellowshipEmptyState(
                    Icons.Filled.ErrorOutline, "The guide couldn't be written", state.message,
                    Modifier.padding(top = 24.dp), "Try again", onCreate
                )
                is GuideState.Ready -> GuideEditor(sections, onEdit, onRemove, onAdd)
            }
        }
    }
}

@Composable
private fun GuideIntro(onCreate: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)) {
        item {
            Surface(shape = RoundedCornerShape(22.dp), color = SurfaceSunk, border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text("WHAT YOU'LL GET", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, color = FaithGoldInk)
                    Text("A guide your group can pick up and use", fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif, color = Ink, modifier = Modifier.padding(top = 8.dp))
                    listOf("An icebreaker to start the conversation", "The Scripture to read together", "Discussion questions drawn from the message", "Ways to live it out, and prayer points").forEach {
                        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Top) {
                            Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(FaithGold))
                            Text(it, fontSize = 14.5.sp, lineHeight = 21.sp, color = InkSecondary, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }
        }
        item { FellowshipPrimaryButton("Create guide", onCreate, Modifier.padding(horizontal = 20.dp, vertical = 22.dp).fillMaxWidth().testTag("guide_create"), icon = Icons.Filled.Edit) }
        item { FellowshipNote("It reads this note only. The guide is written by AI from your own words, so you can change anything before you send it.") }
    }
}

@Composable
private fun GuideWorking(onCancel: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(40.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = FaithGold, trackColor = Line, strokeWidth = 3.dp, modifier = Modifier.size(44.dp))
        Text("Writing your guide", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif, color = Ink, modifier = Modifier.padding(top = 24.dp))
        Text("This keeps going if you leave. Come back whenever you like.", fontSize = 14.sp, lineHeight = 20.sp, color = InkSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        Text("Stop", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Accent, modifier = Modifier.padding(top = 22.dp).clip(RoundedCornerShape(50)).clickable(onClick = onCancel).padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

@Composable
private fun GuideEditor(sections: List<GuideSection>, onEdit: (Int, Int, String) -> Unit, onRemove: (Int, Int) -> Unit, onAdd: (Int) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Edit, contentDescription = null, tint = FaithGold, modifier = Modifier.size(14.dp))
                Text("Written by AI from your note. Tap any line to change it.", fontSize = 12.5.sp, color = InkSecondary, modifier = Modifier.padding(start = 8.dp))
            }
        }
        itemsIndexed(sections, key = { _, s -> s.key }) { si, section ->
            Surface(shape = RoundedCornerShape(22.dp), color = SurfaceSunk, border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(section.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif, color = Ink)
                    val numbered = GroupGuideExport.isNumbered(section.key)
                    section.items.forEachIndexed { ii, item ->
                        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Top) {
                            Box(Modifier.padding(top = 1.dp).size(24.dp).clip(CircleShape).background(FaithGoldWash), contentAlignment = Alignment.Center) {
                                if (numbered) Text("${ii + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FaithGoldInk)
                                else Box(Modifier.size(6.dp).clip(CircleShape).background(FaithGold))
                            }
                            BasicTextField(
                                value = item, onValueChange = { onEdit(si, ii, it) },
                                textStyle = TextStyle(fontSize = 15.5.sp, lineHeight = 22.sp, color = Ink),
                                cursorBrush = SolidColor(Accent),
                                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                                decorationBox = { inner -> if (item.isEmpty()) Text("Write something…", fontSize = 15.5.sp, color = InkMuted); inner() }
                            )
                            IconButton(onClick = { onRemove(si, ii) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove", tint = InkFaint, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Text("Add a line", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Accent, modifier = Modifier.padding(top = 14.dp).clip(RoundedCornerShape(50)).clickable { onAdd(si) }.padding(vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun SendRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = SurfaceBase, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(FaithGoldWash), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = FaithGold, modifier = Modifier.size(21.dp))
            }
            Column(Modifier.padding(start = 14.dp)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Text(body, fontSize = 12.5.sp, color = InkMuted)
            }
        }
    }
}
