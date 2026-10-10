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
    val mode: CreateMode = CreateVibePolicy.companionMode(ui.card.source, ui.card.vibe)
    val companionBitmap: Bitmap? = if (form != null && ui.card.design.includeCompanion) remember(form, mode, palette) {
        renderCompanionBitmap(form, mode, palette, 360).asAndroidBitmap()
    } else null
    val offer = form?.let { CompanionOffer(settings.displayName(it), mode.name.lowercase().replaceFirstChar { c -> c.uppercase() }) }

    val rendered by produceRendered(ui, companionBitmap)
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
                onSend = sendTarget?.let { target -> { target.onShare(CreateShareResult(ui.card, ui.scripture)); close() } },
                onCopy = { copy(context, vm.plainText()); vm.dismissMessage() }
            )
        )
    }
}

/** The live preview and whether the passage fits, rendered off the main thread whenever the card changes. */
@Composable
private fun produceRendered(ui: CreateUi, companion: Bitmap?): State<Pair<ImageBitmap?, Boolean>> {
    val context = LocalContext.current
    val c = ui.card
    return produceState<Pair<ImageBitmap?, Boolean>>(null to true, c.text, ui.scripture, c.format, c.design, companion) {
        value = withContext(Dispatchers.Default) {
            val input = CreateRenderInput(c.text, ui.scripture, c.format, c.design)
            val bmp = CreateCardRenderer.render(context, input, 0.4f, companion.takeIf { c.design.includeCompanion })
            bmp.asImageBitmap() to CreateCardRenderer.fits(context, input)
        }
    }
}

private fun copy(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("Card text", text))
}
