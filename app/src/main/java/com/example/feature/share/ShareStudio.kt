package com.example.feature.share

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ai.common.AiResult
import com.example.core.share.BackgroundPack
import com.example.core.share.BackgroundSpec
import com.example.core.share.ImageBackgrounds
import com.example.core.share.ImageStyle
import com.example.core.share.ShareActions
import com.example.core.share.ShareCardContent
import com.example.core.share.ShareCardRenderer
import com.example.core.share.ShareFont
import com.example.core.share.ShareFormat
import com.example.core.share.ShareStyle
import com.example.core.share.ShareTarget
import com.example.ui.theme.Ink
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** What the studio was opened with. */
data class ShareRequest(
    val content: ShareCardContent,
    /** What an AI background should be about (the verse, the devotional's title). */
    val theme: String,
    val background: BackgroundSpec? = null,
    /** A devotional's voice, which can be shared as audio too. */
    val audioPath: String? = null,
    val caption: String? = null
)

/** Hands a request to the studio across navigation, without serialising it into the route. */
object ShareRequests {
    var pending: ShareRequest? = null
}

private val Gold = Color(0xFFB7791F)

/** Make a card and send it (PLAN_V2 F4). */
@Composable
fun ShareStudioScreen(request: ShareRequest, onNavigateBack: () -> Unit, renderLive: Boolean = true) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var style by remember { mutableStateOf(ShareStyle(background = request.background ?: BackgroundSpec.Pack(BackgroundPack.forDay(java.time.LocalDate.now().toEpochDay()).id))) }
    val generated = remember { mutableStateListOf<String>() }
    var libraryVersion by remember { mutableStateOf(0) }
    val library = remember(libraryVersion) { com.example.core.share.BackgroundLibrary.all(context) }
    var showLibrary by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }
    var showStyles by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }

    // A photo added here joins the library, so it's there next time too.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val img = withContext(Dispatchers.IO) { com.example.core.share.BackgroundLibrary.add(context, uri) }
            img?.let { libraryVersion++; style = style.copy(background = BackgroundSpec.Photo(it.file.path)) }
        }
    }

    val preview by produceState<Bitmap?>(null, request.content, style) {
        if (renderLive) value = withContext(Dispatchers.Default) { ShareCardRenderer.render(context, request.content, style, scale = 0.5f) }
    }

    fun send(target: ShareTarget) {
        if (sending) return
        sending = true
        scope.launch {
            val file = withContext(Dispatchers.Default) { ShareActions.saveToCache(context, ShareCardRenderer.render(context, request.content, style)) }
            message = runCatching { ShareActions.send(context, file, target, request.caption) }.getOrElse { "Couldn't open that app." }
            sending = false
        }
    }

    if (showLibrary) BackgroundsSheet(
        onDismiss = { showLibrary = false; libraryVersion++ },
        onPick = { img -> showLibrary = false; libraryVersion++; style = style.copy(background = BackgroundSpec.Photo(img.file.path)) }
    )
    Column(Modifier.fillMaxSize().background(Color(0xFF0E0B18)).statusBarsPadding().testTag("share_studio")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
            Text("Share", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.weight(1f))
        }
        // The card, exactly as it will be sent.
        Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            val ratio = style.format.width.toFloat() / style.format.height
            Box(Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = ratio < 1f).shadow(18.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(Color.DarkGray)) {
                preview?.let { Image(it.asImageBitmap(), contentDescription = "Card preview", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                    ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { if (renderLive) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp)) }
            }
        }
        message?.let { Text(it, fontSize = 13.sp, color = Color(0xFFF6D365), modifier = Modifier.padding(horizontal = 24.dp)) }

        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)).background(Color.White).navigationBarsPadding()) {
            Column(Modifier.fillMaxWidth().height(300.dp).verticalScroll(rememberScrollState()).padding(top = 14.dp)) {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShareFormat.entries.forEach { f -> Chip(f.label, style.format == f) { style = style.copy(format = f) } }
                }
                LazyRow(Modifier.padding(top = 12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { ActionTile(Icons.Filled.AutoAwesome, if (generating) "Making…" else "New picture", Gold, busy = generating) { showStyles = !showStyles } }
                    item { ActionTile(Icons.Filled.AddPhotoAlternate, "Add photo", Color(0xFF475569)) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } }
                    item { ActionTile(Icons.Filled.GridView, "All", Color(0xFF475569)) { showLibrary = true } }
                    items(generated) { path -> PhotoTile(path, (style.background as? BackgroundSpec.Photo)?.path == path) { style = style.copy(background = BackgroundSpec.Photo(path)) } }
                    items(library, key = { it.id }) { img -> PhotoTile(img.thumb.path, (style.background as? BackgroundSpec.Photo)?.path == img.file.path) { style = style.copy(background = BackgroundSpec.Photo(img.file.path)) } }
                    items(BackgroundPack.all) { bg -> PackTile(bg, (style.background as? BackgroundSpec.Pack)?.id == bg.id) { style = style.copy(background = BackgroundSpec.Pack(bg.id)) } }
                }
                if (showStyles) Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImageStyle.entries.forEach { s ->
                        Chip(s.label, false) {
                            showStyles = false; generating = true; message = null
                            scope.launch {
                                when (val r = ImageBackgrounds(context).generate(request.theme, s, style.format)) {
                                    is AiResult.Success -> { generated.add(0, r.value.path); style = style.copy(background = BackgroundSpec.Photo(r.value.path, generated = true)) }
                                    is AiResult.ModelUnavailable -> message = r.message
                                    is AiResult.Failed -> message = "Couldn't make a picture: ${r.message}"
                                    else -> message = "Couldn't make a picture."
                                }
                                generating = false
                            }
                        }
                    }
                }
                Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShareFont.entries.forEach { f -> Chip(f.label, style.font == f) { style = style.copy(font = f) } }
                }
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Size", fontSize = 13.sp, color = InkSecondary, modifier = Modifier.width(56.dp))
                    Slider(value = style.textScale, onValueChange = { style = style.copy(textScale = it) }, valueRange = 0.7f..1.4f, modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold))
                    IconButton(onClick = { style = style.copy(centered = !style.centered) }) {
                        Icon(if (style.centered) Icons.Filled.FormatAlignCenter else Icons.Filled.FormatAlignLeft, contentDescription = "Alignment", tint = Ink)
                    }
                }
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Shade", fontSize = 13.sp, color = InkSecondary, modifier = Modifier.width(56.dp))
                    Slider(value = style.scrim, onValueChange = { style = style.copy(scrim = it) }, valueRange = 0f..0.8f, modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold))
                    Chip("Mark", style.watermark) { style = style.copy(watermark = !style.watermark) }
                }
                Text("The source and translation are always printed on the card.", fontSize = 11.5.sp, color = InkMuted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Target("WhatsApp", Color(0xFF25D366), "W") { send(ShareTarget.WHATSAPP) }
                Target("Instagram", Color(0xFFE1306C), "IG") { send(ShareTarget.INSTAGRAM_STORY) }
                TargetIcon("Save", Icons.Filled.Download) { send(ShareTarget.SAVE) }
                request.audioPath?.let { path -> TargetIcon("Audio", Icons.Filled.GraphicEq) { message = ShareActions.shareAudio(context, File(path), request.caption) } }
                TargetIcon("More", Icons.Filled.Share) { send(ShareTarget.ANY) }
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = if (selected) Ink else Color.White, border = if (selected) null else BorderStroke(1.dp, Color(0xFFE2E8F0))) {
        Text(label, fontSize = 12.5.sp, color = if (selected) Color.White else InkSecondary, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
    }
}

@Composable
private fun ActionTile(icon: ImageVector, label: String, tint: Color, busy: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.width(64.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp, 76.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = tint) else Icon(icon, contentDescription = null, tint = tint)
        }
        Text(label, fontSize = 10.5.sp, color = InkSecondary, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun PhotoTile(path: String, selected: Boolean, onClick: () -> Unit) {
    AsyncImage(
        model = File(path), contentDescription = null, contentScale = ContentScale.Crop,
        modifier = Modifier.size(56.dp, 76.dp).clip(RoundedCornerShape(12.dp)).border(if (selected) 3.dp else 0.dp, Gold, RoundedCornerShape(12.dp)).clickable(onClick = onClick)
    )
}

@Composable
private fun PackTile(bg: com.example.core.share.PackBackground, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(56.dp, 76.dp).clip(RoundedCornerShape(12.dp)).border(if (selected) 3.dp else 0.dp, Gold, RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
            drawIntoCanvas { BackgroundPack.draw(it.nativeCanvas, bg, size.width.toInt(), size.height.toInt()) }
        }
    }
}

@Composable
private fun Target(label: String, color: Color, mark: String, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Text(mark, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Text(label, fontSize = 11.sp, color = InkSecondary, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun TargetIcon(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFF1F5F9)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Ink)
        }
        Text(label, fontSize = 11.sp, color = InkSecondary, modifier = Modifier.padding(top = 4.dp))
    }
}
