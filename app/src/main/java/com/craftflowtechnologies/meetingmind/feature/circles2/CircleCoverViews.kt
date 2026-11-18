package com.craftflowtechnologies.meetingmind.feature.circles2

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.circles2.CircleCovers
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The asset for a pack picture: `photo_206` is `backgrounds/bg_206.webp`, its thumbnail `bg_206_thumb.webp`. */
internal fun packAsset(photoId: String, thumb: Boolean): String =
    "backgrounds/bg_${photoId.removePrefix("photo_")}${if (thumb) "_thumb" else ""}.webp"

/** Decoded pack pictures, shared by every cover on screen. Thumbnails are small; full pictures are capped by size. */
private object PackBitmaps {
    private val cache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = (value.width * value.height * 4).coerceAtLeast(1)
    }

    suspend fun load(context: Context, photoId: String, thumb: Boolean): ImageBitmap? {
        val asset = packAsset(photoId, thumb)
        cache.get(asset)?.let { return it }
        return withContext(Dispatchers.IO) {
            // A missing or damaged asset draws the quiet gradient behind it instead of crashing.
            runCatching {
                context.assets.open(asset).use { input ->
                    BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { if (!thumb) inSampleSize = 2 })
                }?.asImageBitmap()
            }.getOrNull()?.also { cache.put(asset, it) }
        }
    }
}

/**
 * A circle's cover: its own photo, a pack picture it chose, or the pack picture that fits its type. [circleId] is null
 * when the person isn't a member yet (an invite preview): an uploaded photo can't be fetched then, so the type's
 * picture is shown instead. Draws nothing over the picture: callers add their own scrim for text.
 */
@Composable
fun CircleCoverImage(
    coverPath: String?,
    template: String?,
    space: String?,
    circleId: String?,
    modifier: Modifier = Modifier,
    thumb: Boolean = false,
    contentDescription: String? = null
) {
    val source = remember(coverPath, template, space, circleId) {
        val s = CircleCovers.resolve(coverPath, template, space)
        if (s is CircleCovers.Source.Uploaded && circleId == null) CircleCovers.Source.Pack(CircleCovers.defaultFor(template, space)) else s
    }
    Box(modifier.background(MM.colors.surfaceSunk)) {
        when (source) {
            is CircleCovers.Source.Uploaded -> if (circleId != null) MediaImage(circleId, source.path, Modifier.fillMaxSize(), contentDescription)
            is CircleCovers.Source.Pack -> PackImage(source.photoId, thumb, Modifier.fillMaxSize(), contentDescription)
        }
    }
}

@Composable
fun PackImage(photoId: String, thumb: Boolean, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, photoId, thumb) { value = PackBitmaps.load(context, photoId, thumb) }
    // Reduced motion: the picture appears at once instead of fading in.
    if (MM.motion.reduced) {
        bitmap?.let { Image(it, contentDescription, modifier, contentScale = ContentScale.Crop) }
    } else {
        Crossfade(bitmap, modifier, label = "pack-cover") { b ->
            if (b != null) Image(b, contentDescription, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Box(Modifier.fillMaxSize())
        }
    }
}

/** Opens the photo picker and reads the photo as a small JPEG. [onPicked] gets null when the photo couldn't be read. */
@Composable
fun rememberCoverPhotoPicker(onPicked: (PickedFile?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch { onPicked(runCatching { readPickedAsync(context, uri) }.getOrNull()?.takeIf { it.mime.startsWith("image/") }) }
    }
    return { launcher.launch("image/*") }
}

/** One thumbnail in a cover chooser. */
@Composable
private fun CoverTile(photoId: String, selected: Boolean, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .aspectRatio(4f / 3f)
            .clip(MM.radius.card)
            .border(BorderStroke(if (selected) 2.dp else 0.dp, if (selected) MM.colors.accent else MM.colors.lineSoft), MM.radius.card)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected; this.contentDescription = label }
    ) {
        PackImage(photoId, thumb = true, modifier = Modifier.fillMaxSize())
        if (selected) {
            Icon(
                Icons.Rounded.CheckCircle, null, tint = MM.colors.accent,
                modifier = Modifier.align(Alignment.TopEnd).padding(MM.space.xs).size(22.dp).background(MM.colors.surface, MM.radius.pill)
            )
        }
    }
}

@Composable
private fun UploadTile(busy: Boolean, chosenOwn: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick, enabled = !busy,
        shape = MM.radius.card,
        color = if (chosenOwn) MM.colors.accentWash else MM.colors.surfaceRaised,
        border = BorderStroke(if (chosenOwn) 2.dp else 1.dp, if (chosenOwn) MM.colors.accent else MM.colors.lineSoft),
        modifier = modifier.aspectRatio(4f / 3f)
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MM.colors.accent)
            else Icon(Icons.Rounded.AddPhotoAlternate, null, tint = MM.colors.accent)
            Text(
                if (busy) "Saving…" else if (chosenOwn) "Your photo" else "Upload your own",
                style = MM.type.caption, color = MM.colors.ink, modifier = Modifier.padding(top = MM.space.xs)
            )
        }
    }
}

/**
 * The cover choices in a row, for the create flow: Upload your own first, then the pack with the type's picture first.
 * [selectedPack] is the chosen pack picture, or null when the person chose their own photo ([chosenOwn]).
 */
@Composable
fun CoverPickerRow(
    template: String?,
    space: String?,
    selectedPack: String?,
    chosenOwn: Boolean,
    onPickPack: (String) -> Unit,
    onUpload: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = MM.space.l)
) {
    val choices = remember(template, space) { CircleCovers.choices(template, space) }
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(MM.space.s), contentPadding = contentPadding) {
        item(key = "upload") { UploadTile(busy = false, chosenOwn = chosenOwn, onClick = onUpload, modifier = Modifier.width(112.dp)) }
        items(choices, key = { it }) { id ->
            CoverTile(id, selected = !chosenOwn && id == selectedPack, label = "Cover picture ${choices.indexOf(id) + 1}", onClick = { onPickPack(id) }, modifier = Modifier.width(112.dp))
        }
    }
}

/** The cover chooser for an existing circle (Settings): the pack as a grid, plus Upload your own. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverChooserSheet(
    template: String?,
    space: String?,
    currentCover: String?,
    busy: Boolean,
    onPickPack: (String) -> Unit,
    onUpload: () -> Unit,
    onDismiss: () -> Unit
) {
    val choices = remember(template, space) { CircleCovers.choices(template, space) }
    val current = CircleCovers.packId(currentCover) ?: if (currentCover == null) CircleCovers.defaultFor(template, space) else null
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MM.colors.surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text("Choose a cover", style = MM.type.title, color = MM.colors.ink, modifier = Modifier.padding(horizontal = MM.space.l))
            Text(
                "Pick one of ours, or upload your own photo.",
                style = MM.type.secondary, color = MM.colors.inkSecondary,
                modifier = Modifier.padding(start = MM.space.l, end = MM.space.l, top = MM.space.xs, bottom = MM.space.m)
            )
            LazyVerticalGrid(
                GridCells.Fixed(3),
                contentPadding = PaddingValues(start = MM.space.l, end = MM.space.l, bottom = MM.space.l),
                horizontalArrangement = Arrangement.spacedBy(MM.space.s),
                verticalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                item(key = "upload") { UploadTile(busy = busy, chosenOwn = currentCover != null && current == null, onClick = onUpload) }
                items(choices, key = { it }) { id ->
                    CoverTile(id, selected = id == current, label = "Cover picture ${choices.indexOf(id) + 1}", onClick = { if (!busy) onPickPack(id) })
                }
            }
        }
    }
}
