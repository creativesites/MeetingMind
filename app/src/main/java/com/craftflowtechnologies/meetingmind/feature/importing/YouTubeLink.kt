package com.craftflowtechnologies.meetingmind.feature.importing

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.textclassifier.TextClassifier
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.ai.cloud.CloudAi
import com.craftflowtechnologies.meetingmind.core.identity.LocalAppIdentity
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.youtube.YouTubeUrlParser
import com.craftflowtechnologies.meetingmind.feature.navigation.Routes
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Notes from a YouTube video": one sheet, opened from Today, Create, the app menu, Faith,
 * Learning, a link shared from another app and a link on the clipboard. It asks for the link and
 * what kind of video it is, checks that Google's AI is set up, then hands over to the existing
 * import screen with the link loaded. Plain words and a generic play icon: no YouTube logo or
 * colours, nothing that suggests YouTube endorses the app.
 */
object YouTubeLinks {

    /** The kind pre-selected for where the sheet was opened from. GENERAL means "Auto": the AI decides. */
    fun kindFor(from: NotebookSpace?): RecordingType = when (from) {
        NotebookSpace.FAITH -> RecordingType.SERMON
        NotebookSpace.LEARNING -> RecordingType.LECTURE
        // From the garden: a talk or podcast, whose "Try this week" lines become seed packets.
        NotebookSpace.PERSONAL -> RecordingType.MOTIVATION
        else -> RecordingType.GENERAL
    }

    /** The kinds offered, in order. Sermon only with Faith on, Meeting only with Work on. */
    fun kindsFor(spaces: Set<NotebookSpace>): List<RecordingType> = buildList {
        add(RecordingType.GENERAL)
        add(RecordingType.LECTURE)
        if (NotebookSpace.FAITH in spaces) add(RecordingType.SERMON)
        add(RecordingType.MOTIVATION)
        add(RecordingType.LIFE_SKILLS)
        if (NotebookSpace.WORK in spaces) add(RecordingType.MEETING)
    }

    /** The pre-selected kind, kept only if it is offered (a Faith preset with Faith off falls back to Auto). */
    fun startKind(from: NotebookSpace?, spaces: Set<NotebookSpace>): RecordingType =
        kindFor(from).takeIf { it in kindsFor(spaces) } ?: RecordingType.GENERAL

    fun label(kind: RecordingType): String = if (kind == RecordingType.GENERAL) "Auto" else kind.displayName

    /** Where "Make notes" goes: the import screen with the link loaded and the kind chosen. */
    fun route(link: String, kind: RecordingType): String = Routes.importFileRoute(link, kind.name)

    /** The YouTube link a share from another app carries in its text, or null (shared files are not links). */
    fun sharedLink(action: String?, text: String?, hasStream: Boolean): String? =
        if (action == Intent.ACTION_SEND && !hasStream) YouTubeUrlParser.parse(text)?.canonicalWatchUrl else null

    fun sharedLink(intent: Intent?): String? {
        if (intent == null) return null
        @Suppress("DEPRECATION")
        val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        return sharedLink(intent.action, intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(), stream != null)
    }
}

/** Links shared into the app, waiting for the sheet to open them. Set from the activity, read by [YouTubeSheetHost]. */
object YouTubeLinkRequests {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()
    fun offer(link: String) { _pending.value = link }
    fun consume() { _pending.value = null }
}

/** Opens the sheet: `LocalYouTubeSheet.current.open(from = NotebookSpace.FAITH)`. A no-op outside [YouTubeSheetHost]. */
class YouTubeSheetController(private val show: (NotebookSpace?, String?) -> Unit) {
    fun open(from: NotebookSpace? = null, link: String? = null) = show(from, link)
}

val LocalYouTubeSheet = staticCompositionLocalOf { YouTubeSheetController { _, _ -> } }

class YouTubeSheetState {
    var open by mutableStateOf(false)
    var from by mutableStateOf<NotebookSpace?>(null)
    var link by mutableStateOf<String?>(null)
    val controller = YouTubeSheetController { f, l -> from = f; link = l; open = true }
}

@Composable
fun rememberYouTubeSheetState(): YouTubeSheetState = remember { YouTubeSheetState() }

/** Draws the sheet when open, and opens it for a link shared in from another app. */
@Composable
fun YouTubeSheetHost(state: YouTubeSheetState, navigate: (String) -> Unit) {
    val pending by YouTubeLinkRequests.pending.collectAsState()
    LaunchedEffect(pending) {
        pending?.let { state.controller.open(null, it); YouTubeLinkRequests.consume() }
    }
    if (state.open) YouTubeLinkSheet(
        from = state.from,
        initialLink = state.link,
        onDismiss = { state.open = false },
        onGo = { route -> state.open = false; navigate(route) }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun YouTubeLinkSheet(from: NotebookSpace?, initialLink: String?, onDismiss: () -> Unit, onGo: (String) -> Unit) {
    val context = LocalContext.current
    val spaces = LocalAppIdentity.current.spaces
    val colors = MM.colors
    // Pre-filled from the clipboard only when Android already says it holds a link; Paste reads it on request.
    var link by remember { mutableStateOf(initialLink ?: YouTubeClipboard.youTubeLink(context).orEmpty()) }
    val kinds = remember(spaces) { YouTubeLinks.kindsFor(spaces) }
    var kind by remember { mutableStateOf(YouTubeLinks.startKind(from, spaces)) }
    val ready by produceState<Boolean?>(null) { value = runCatching { CloudAi.transport(context).refreshConfigured() }.getOrDefault(false) }
    val parsed = remember(link) { YouTubeUrlParser.parse(link) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding().testTag("youtube_sheet")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VideoBadge()
                Spacer(Modifier.width(MM.space.m))
                Column(Modifier.weight(1f)) {
                    Text("Notes from a YouTube video", style = MM.type.title, color = colors.ink)
                    Text("Paste a link to a public video. Notes, key points and timestamps come back.", style = MM.type.caption, color = colors.inkSecondary)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Close", tint = colors.inkMuted) }
            }
            Spacer(Modifier.height(MM.space.l))
            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                singleLine = true,
                placeholder = { Text("youtube.com/watch?v=… or youtu.be/…", style = MM.type.body, color = colors.inkMuted) },
                trailingIcon = {
                    IconButton(onClick = { YouTubeClipboard.text(context)?.let { link = it.trim() } }, modifier = Modifier.testTag("youtube_sheet_paste")) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste", tint = colors.accent)
                    }
                },
                isError = link.isNotBlank() && parsed == null,
                supportingText = if (link.isNotBlank() && parsed == null) {
                    { Text("That isn't a YouTube video link.", style = MM.type.caption, color = colors.danger) }
                } else null,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.ink, unfocusedTextColor = colors.ink,
                    focusedBorderColor = colors.accent, unfocusedBorderColor = colors.line, cursorColor = colors.accent
                ),
                modifier = Modifier.fillMaxWidth().testTag("youtube_sheet_link")
            )
            Spacer(Modifier.height(MM.space.m))
            Text("WHAT IS IT?", style = MM.type.overline, color = colors.inkMuted)
            Spacer(Modifier.height(MM.space.xs))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                kinds.forEach { k ->
                    FilterChip(
                        selected = k == kind,
                        onClick = { kind = k },
                        label = { Text(YouTubeLinks.label(k), style = MM.type.secondary) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = colors.surfaceRaised, labelColor = colors.inkSecondary,
                            selectedContainerColor = colors.accentWash, selectedLabelColor = colors.ink
                        ),
                        modifier = Modifier.testTag("youtube_kind_${k.name.lowercase()}")
                    )
                }
            }
            if (kind == RecordingType.GENERAL) {
                Text("Auto lets the AI decide whether it's a sermon, lecture, talk or interview.", style = MM.type.caption, color = colors.inkMuted)
            }
            Spacer(Modifier.height(MM.space.l))
            if (ready == false) {
                Surface(shape = MM.radius.card, color = colors.surfaceSunk, border = BorderStroke(1.dp, colors.line), modifier = Modifier.fillMaxWidth().testTag("youtube_sheet_setup")) {
                    Column(Modifier.padding(MM.space.m)) {
                        Text("Needs Internet mode with Google's AI", style = MM.type.bodyStrong, color = colors.ink)
                        Text("Videos are read online, so turn on Google's AI first. Your recordings still stay on-device.", style = MM.type.caption, color = colors.inkSecondary)
                        TextButton(onClick = { onGo(Routes.MODELS) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                            Text("Set up Google's AI", style = MM.type.bodyStrong, color = colors.accent)
                        }
                    }
                }
                Spacer(Modifier.height(MM.space.m))
            }
            Button(
                onClick = { parsed?.let { onGo(YouTubeLinks.route(it.canonicalWatchUrl, kind)) } },
                enabled = parsed != null && ready != false,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("youtube_sheet_go")
            ) { Text("Make notes", style = MM.type.bodyStrong) }
        }
    }
}

@Composable
private fun VideoBadge(size: Int = 44) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(MM.colors.accentWash), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.SmartDisplay, contentDescription = null, tint = MM.colors.accent, modifier = Modifier.size((size / 2).dp))
    }
}

/**
 * The always-visible entry on Today and the other homes: one tap to the sheet. It is the shared
 * [com.craftflowtechnologies.meetingmind.core.ui.YouTubeNotesCard], so it looks the same everywhere.
 */
@Composable
fun YouTubeHomeCard(
    modifier: Modifier = Modifier,
    title: String = "Notes from a YouTube video",
    benefit: String = "Paste a link, get study notes with timestamps"
) {
    val sheet = LocalYouTubeSheet.current
    com.craftflowtechnologies.meetingmind.core.ui.YouTubeNotesCard(
        onClick = { sheet.open() },
        modifier = modifier.testTag("home_youtube_card"),
        title = title,
        benefit = benefit
    )
}

/** Whether a copied link should be offered. Pure. Each video is offered once; the offer can be turned off. */
object ClipboardOffer {
    fun shouldOffer(videoId: String?, lastOffered: String?, enabled: Boolean): Boolean =
        enabled && !videoId.isNullOrBlank() && videoId != lastOffered
}

/**
 * Reads a YouTube link from the clipboard. Android 12+ tells the person each time an app reads
 * the clipboard, so the text is read only when Android has already classified it as a link.
 */
object YouTubeClipboard {
    private const val PREFS = "youtube_clipboard"
    private const val KEY_LAST = "last_offered"
    private const val KEY_ENABLED = "offer_enabled"

    fun text(context: Context): String? = runCatching {
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return null
        cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
    }.getOrNull()

    fun youTubeLink(context: Context): String? {
        if (!looksLikeLink(context)) return null
        return YouTubeUrlParser.parse(text(context))?.canonicalWatchUrl
    }

    private fun looksLikeLink(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return false
        val desc = cm.primaryClipDescription ?: return false
        if (!desc.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) && !desc.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        desc.classificationStatus == ClipDescription.CLASSIFICATION_COMPLETE &&
            desc.getConfidenceScore(TextClassifier.TYPE_URL) >= 0.5f
    }.getOrDefault(false)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun lastOffered(context: Context): String? = prefs(context).getString(KEY_LAST, null)
    fun markOffered(context: Context, videoId: String) = prefs(context).edit().putString(KEY_LAST, videoId).apply()
    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)
    fun setEnabled(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
}

/**
 * On Today: a YouTube link the person just copied offers itself, once per video. It is checked when
 * the window gains focus (Android only lets a focused app read the clipboard).
 */
@Composable
fun YouTubeClipboardBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sheet = LocalYouTubeSheet.current
    val focused = LocalWindowInfo.current.isWindowFocused
    var offer by remember { mutableStateOf<Pair<String, String>?>(null) } // video id to link
    LaunchedEffect(focused) {
        if (!focused || offer != null) return@LaunchedEffect
        val link = YouTubeClipboard.youTubeLink(context) ?: return@LaunchedEffect
        val id = YouTubeUrlParser.parse(link)?.videoId
        if (ClipboardOffer.shouldOffer(id, YouTubeClipboard.lastOffered(context), YouTubeClipboard.enabled(context))) offer = id!! to link
    }
    val current = offer ?: return
    val colors = MM.colors
    fun done() { YouTubeClipboard.markOffered(context, current.first); offer = null }
    Surface(shape = MM.radius.card, color = colors.accentWash, border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.35f)), modifier = modifier.fillMaxWidth().testTag("home_youtube_clipboard")) {
        Column(Modifier.padding(MM.space.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VideoBadge(36)
                Spacer(Modifier.width(MM.space.m))
                Column(Modifier.weight(1f)) {
                    Text("You copied a YouTube link", style = MM.type.bodyStrong, color = colors.ink)
                    Text("Make notes from that video?", style = MM.type.caption, color = colors.inkSecondary)
                }
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { YouTubeClipboard.setEnabled(context, false); done() }) {
                    Text("Don't ask again", style = MM.type.secondary, color = colors.inkMuted)
                }
                TextButton(onClick = { done() }) { Text("Not now", style = MM.type.secondary, color = colors.inkSecondary) }
                TextButton(onClick = { done(); sheet.open(link = current.second) }, modifier = Modifier.testTag("home_youtube_clipboard_go")) {
                    Text("Make notes", style = MM.type.bodyStrong, color = colors.accent)
                }
            }
        }
    }
}
