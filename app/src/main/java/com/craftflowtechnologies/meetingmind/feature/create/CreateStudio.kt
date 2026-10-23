package com.craftflowtechnologies.meetingmind.feature.create

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craftflowtechnologies.meetingmind.core.companion.CreateMode
import com.craftflowtechnologies.meetingmind.core.create.CompanionPlacement
import com.craftflowtechnologies.meetingmind.core.create.CompanionPose
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.offset
import com.craftflowtechnologies.meetingmind.core.create.CreateCard
import com.craftflowtechnologies.meetingmind.core.create.CreateCardRenderer
import com.craftflowtechnologies.meetingmind.core.create.CreateRenderInput
import com.craftflowtechnologies.meetingmind.core.create.CreateSeed
import com.craftflowtechnologies.meetingmind.core.create.CreateVibePolicy
import com.craftflowtechnologies.meetingmind.core.share.BackgroundLibrary
import com.craftflowtechnologies.meetingmind.core.share.ShareTarget
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.displayName
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rememberCompanionSettings
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.renderCompanionBitmap
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.companionPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The studio as a full-screen dialog: owns the view model, the preview, the photo picker and the companion export. */
@Composable
fun CreateStudioDialog(seed: CreateSeed, reopen: CreateCard? = null, openId: Int = 0, onDismiss: () -> Unit, onOpenGallery: () -> Unit = {}, sendTarget: SendTarget? = null) {
    val context = LocalContext.current
    val vm: CreateViewModel = viewModel()
    remember(openId) { vm.start(seed, reopen); openId }
    val ui by vm.ui.collectAsState()
    val scope = rememberCoroutineScope()

    // The companion is off unless switched on; its pose follows the mood and is drawn by the Canvas renderer, so export never depends on animation.
    val settings by rememberCompanionSettings()
    val form = settings.form
    val palette = companionPalette(MM.colors)
    // The pose (§5.3): the person's override when the policy allows it, else the suggestion, which follows the vibe.
    val goodFriday = remember { com.craftflowtechnologies.meetingmind.feature.moments.NoteFactsRules.goodFriday(java.time.LocalDate.now()) }
    val design = ui.card.design
    val allowed = CompanionPose.allowed(ui.card.source, ui.card.vibe, vm.grief, goodFriday)
    val suggested = CompanionPose.suggested(ui.card.source, ui.card.vibe, vm.grief, goodFriday)
    val mode: CreateMode = CompanionPose.effective(ui.card.source, ui.card.vibe, design.companionPose, vm.grief, goodFriday)
    // The exact static export pose at 2× the largest slot; the renderer downsamples it into the card (§5.5).
    val companionBitmap: Bitmap? = if (form != null && design.includeCompanion) remember(form, mode, palette) {
        renderCompanionBitmap(form, mode, palette, CompanionPlacement.maxExportBitmapPx).asAndroidBitmap()
    } else null
    val offer = form?.let {
        CompanionOffer(
            name = settings.displayName(it), poseLabel = mode.label(), form = it, mode = mode, suggested = suggested, allowed = allowed,
            fellBack = CompanionPose.fellBack(ui.card.source, ui.card.vibe, design.companionPose, vm.grief, goodFriday),
            prayerRequest = ui.card.source.policy == com.craftflowtechnologies.meetingmind.core.companion.CreateSource.PRAYER_REQUEST,
            sacred = ui.card.source.faith
        )
    }
    // "Include {companion}" starts off on every new card; the Advanced setting pre-enables it for achievements only.
    LaunchedEffect(openId) {
        if (reopen == null && com.craftflowtechnologies.meetingmind.core.companion.CreateModePolicy.includeByDefault(ui.card.source.policy, settings.createSuggest)) {
            vm.setDesign({ it.copy(includeCompanion = true) })
        }
    }

    // The 1.5 s reveal (§5.4) plays over the preview when the companion is switched on or the format changes
    // while it is on; it settles into exactly the export pose, and the preview with the drawn companion takes over.
    var revealing by remember { mutableStateOf(false) }
    val revealKey = "${ui.card.id}/${ui.card.format}/${design.companionAnchor}"
    val lastReveal = remember { arrayOfNulls<String>(1) }
    val firstComposition = remember { booleanArrayOf(true) }
    LaunchedEffect(design.includeCompanion, revealKey, form) {
        val key = if (design.includeCompanion && form != null) revealKey else null
        if (key != null && lastReveal[0] != null && key != lastReveal[0]) revealing = true
        if (key != null && lastReveal[0] == null && !firstComposition[0]) revealing = true
        firstComposition[0] = false
        lastReveal[0] = key
    }

    val rendered by produceRendered(ui, companionBitmap.takeIf { !revealing })
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val img = withContext(Dispatchers.IO) { BackgroundLibrary.add(context, uri) }
            img?.let { vm.setPhoto(it.file.path) }
        }
    }

    fun close() { vm.saveNow(companionBitmap); onDismiss() }

    Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        CreateStudioContent(
            ui = ui, preview = rendered.first, fits = rendered.second, companion = offer,
            previewOverlay = { scale ->
                val o = offer
                // Shown while it plays, and until the re-rendered preview carries the companion itself.
                if (o?.form != null && design.includeCompanion && (revealing || !rendered.third)) {
                    val r = CompanionPlacement.place(ui.card.format, ui.card.format.width.toFloat(), ui.card.format.height.toFloat(), design.centered, design.companionAnchor)
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.CardReveal(
                        form = o.form, mode = mode, size = with(density) { (r.width * scale).toDp() },
                        playKey = revealKey, prayerRequest = o.prayerRequest,
                        announcement = androidx.compose.ui.res.stringResource(com.craftflowtechnologies.meetingmind.R.string.companion_moment_card_added, o.name, mode.label()),
                        onSettled = { revealing = false },
                        modifier = Modifier.offset { androidx.compose.ui.unit.IntOffset((r.left * scale).toInt(), (r.top * scale).toInt()) }
                    )
                }
            },
            modifier = Modifier.background(MM.colors.background),
            actions = CreateActions(
                onClose = ::close,
                onGallery = { vm.saveNow(companionBitmap); onOpenGallery() },
                onStep = vm::setStep, onPrompt = vm::setPrompt, onSource = vm::setSource, onVibe = vm::chooseVibe,
                onWrite = vm::write, onOption = vm::chooseOption,
                onStatusAction = { a -> if (a == StatusAction.RETRY) vm.write() else vm.showStarters() },
                onStarter = vm::useStarter, onRemix = vm::remix, onUndo = vm::undo, onEdit = vm::edit,
                onVerseInput = vm::setVerseInput, onVerseCommit = vm::commitVerse,
                onFormat = vm::setFormat,
                onDesign = { d -> vm.setDesign({ d }) },
                onBackground = { b -> vm.setDesign({ it.copy(background = b) }, userChoseBackground = true) },
                onPickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onShare = { t -> vm.share(t, companionBitmap) },
                sendLabel = sendTarget?.label,
                onSend = sendTarget?.let { target -> {
                    val text = ui.card.text.trim().ifEmpty { ui.prompt.trim() }.ifEmpty { ui.card.sourceText.trim() }
                    val cardToSend = if (ui.card.text.isBlank() && text.isNotBlank()) ui.card.copy(current = ui.card.current.copy(text = text)) else ui.card
                    target.onShare(CreateShareResult(cardToSend, ui.scripture))
                    close()
                } },
                onCopy = { copy(context, vm.plainText()); vm.dismissMessage() }
            )
        )
    }
}

/** The live preview and whether the passage fits, rendered off the main thread whenever the card changes. */
@Composable
private fun produceRendered(ui: CreateUi, companion: Bitmap?): State<Triple<ImageBitmap?, Boolean, Boolean>> {
    val context = LocalContext.current
    val c = ui.card
    return produceState<Triple<ImageBitmap?, Boolean, Boolean>>(Triple(null, true, false), c.text, ui.scripture, c.format, c.design, companion) {
        value = withContext(Dispatchers.Default) {
            val input = CreateRenderInput(c.text, ui.scripture, c.format, c.design)
            val drawn = companion.takeIf { c.design.includeCompanion }
            val bmp = CreateCardRenderer.render(context, input, 0.4f, drawn)
            // The third value: whether this preview already carries the companion (the reveal hands over to it).
            Triple(bmp.asImageBitmap(), CreateCardRenderer.fits(context, input), drawn != null)
        }
    }
}

private fun copy(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("Card text", text))
}

/** A pose's display name ("Prayerful"). */
internal fun CreateMode.label(): String = name.lowercase().replaceFirstChar { it.uppercase() }
