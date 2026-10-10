package com.craftflowtechnologies.meetingmind.feature.create

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.craftflowtechnologies.meetingmind.core.create.CreateBackdrops
import com.craftflowtechnologies.meetingmind.core.create.CreateBackground
import com.craftflowtechnologies.meetingmind.core.create.CreateCard
import com.craftflowtechnologies.meetingmind.core.create.CreateCardRenderer
import com.craftflowtechnologies.meetingmind.core.create.CreateDesign
import com.craftflowtechnologies.meetingmind.core.create.CreateFormat
import com.craftflowtechnologies.meetingmind.core.create.CreateGallery
import com.craftflowtechnologies.meetingmind.core.create.CreateRenderInput
import com.craftflowtechnologies.meetingmind.core.create.CreateSeed
import com.craftflowtechnologies.meetingmind.core.create.CreateVibe
import com.craftflowtechnologies.meetingmind.core.create.ResolvedScripture
import com.craftflowtechnologies.meetingmind.core.ui.mm.EmptyState
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.ScreenHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The one way to open Create from anywhere (Faith home, Today, Work, a devotional, the Circles UI).
 *
 * ```
 * val create = rememberCreateController()
 * CreateHost(create)                       // once, anywhere in the screen
 * Button(onClick = { create.open(CreateSeed(CreateSourceKind.TESTIMONY, text)) })
 * ```
 */
@Stable
class CreateController {
    internal var seed by mutableStateOf<CreateSeed?>(null)
    internal var reopen by mutableStateOf<CreateCard?>(null)
    internal var galleryOpen by mutableStateOf(false)
    internal var openId by mutableIntStateOf(0)

    /** Opens the studio. A blank [CreateSeed] opens an empty studio. */
    fun open(seed: CreateSeed = CreateSeed()) { reopen = null; this.seed = seed; openId++ }

    /** Reopens a saved creation to edit or share again. */
    fun edit(card: CreateCard) { reopen = card; seed = CreateSeed(card.source, card.sourceText, card.sourceRef); openId++ }

    /** Opens "My creations". */
    fun openGallery() { galleryOpen = true }

    internal fun closeStudio() { seed = null; reopen = null }
}

@Composable
fun rememberCreateController(): CreateController = remember { CreateController() }

/** Draws the studio and the gallery when the controller opens them. Place it once per screen. */
@Composable
fun CreateHost(controller: CreateController) {
    controller.seed?.let { seed ->
        CreateStudioDialog(
            seed = seed, reopen = controller.reopen, openId = controller.openId,
            onDismiss = controller::closeStudio,
            onOpenGallery = { controller.closeStudio(); controller.galleryOpen = true }
        )
    }
    if (controller.galleryOpen) CreateGalleryDialog(
        onDismiss = { controller.galleryOpen = false },
        onCreate = { controller.galleryOpen = false; controller.open() },
        onOpen = { card -> controller.galleryOpen = false; controller.edit(card) }
    )
}

/** What the entry points call it. Same object for Fellowship/Circles: `CreateEntry.Host` + `CreateEntry.controller()`. */
object CreateEntry {
    @Composable fun controller(): CreateController = rememberCreateController()
    @Composable fun Host(controller: CreateController) = CreateHost(controller)
}

// ───────────────────────────── The quiet row ─────────────────────────────

/**
 * The Faith home entry (BIBLE_DEVOTIONAL_V2 §5): a small live thumbnail of a card suggested for
 * today, the mood chip, one tap to the studio, and "See more" for the gallery.
 */
@Composable
fun CreateRow(
    verse: ResolvedScripture?,
    mood: CreateVibe,
    onOpen: () -> Unit,
    onSeeMore: () -> Unit,
    modifier: Modifier = Modifier,
    thumbnail: Bitmap? = null
) {
    val context = LocalContext.current
    val c = MM.colors
    val thumb = thumbnail?.asImageBitmap() ?: remember(verse?.reference, verse?.text, mood) {
        CreateCardRenderer.render(
            context,
            CreateRenderInput("", verse, CreateFormat.STORY, CreateDesign(background = CreateBackdrops.default(mood))),
            scale = 0.12f
        ).asImageBitmap()
    }
    MMCard(modifier = modifier, onClick = onOpen, contentPadding = MM.space.m) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                thumb, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 40.dp, height = 72.dp).clip(MM.radius.small)
            )
            Spacer(Modifier.width(MM.space.m))
            Column(Modifier.weight(1f)) {
                Text("Create", style = MM.type.heading, color = c.ink)
                Text(
                    if (verse != null) "A card for ${verse.reference}" else "Turn a thought into something to share",
                    style = MM.type.secondary, color = c.inkSecondary, maxLines = 1
                )
                Surface(shape = MM.radius.small, color = c.accentWash, modifier = Modifier.padding(top = MM.space.xs)) {
                    Text(mood.label, style = MM.type.caption, color = c.accent, modifier = Modifier.padding(horizontal = MM.space.s, vertical = MM.space.xs))
                }
            }
            TextAction("See more", onSeeMore)
        }
    }
}

// ───────────────────────────── My creations ─────────────────────────────

class CreateGalleryViewModel(app: Application) : AndroidViewModel(app) {
    private val gallery = CreateGallery(app.applicationContext)
    val cards: StateFlow<List<CreateCard>?> = kotlinx.coroutines.flow.flow { gallery.all.collect { emit(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun thumb(id: String): File? = gallery.thumbFile(id)?.takeIf { it.exists() }
    fun pin(card: CreateCard) { viewModelScope.launch { gallery.setPinned(card.id, !card.pinned) } }
    fun delete(card: CreateCard) { viewModelScope.launch { gallery.delete(card.id) } }
}

@Composable
fun CreateGalleryDialog(onDismiss: () -> Unit, onCreate: () -> Unit, onOpen: (CreateCard) -> Unit) {
    val vm: CreateGalleryViewModel = viewModel()
    val cards by vm.cards.collectAsState()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        CreateGalleryContent(
            cards = cards, thumb = vm::thumb, onClose = onDismiss, onCreate = onCreate,
            onOpen = onOpen, onPin = vm::pin, onDelete = vm::delete
        )
    }
}

/** "My creations": your saved cards, newest first, pinned on top. [cards] null means still loading. */
@Composable
fun CreateGalleryContent(
    cards: List<CreateCard>?,
    thumb: (String) -> File?,
    onClose: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (CreateCard) -> Unit,
    onPin: (CreateCard) -> Unit,
    onDelete: (CreateCard) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().background(MM.colors.background).statusBarsPadding().navigationBarsPadding()) {
        ScreenHeader(
            "My creations", Modifier.padding(start = MM.space.s, end = MM.space.l),
            leading = { IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close", tint = MM.colors.inkSecondary) } },
            actions = { TextAction("New", onCreate) }
        )
        when {
            cards == null -> Unit
            cards.isEmpty() -> EmptyState(
                "Nothing here yet", "Cards you make are kept here, so you can edit or share them again.",
                action = { PrimaryButton("Create a card", onCreate) }
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(MM.space.l),
                horizontalArrangement = Arrangement.spacedBy(MM.space.m), verticalArrangement = Arrangement.spacedBy(MM.space.m)
            ) {
                items(cards, key = { it.id }) { card -> GalleryTile(card, thumb(card.id), onOpen, onPin, onDelete) }
            }
        }
    }
}

@Composable
private fun GalleryTile(card: CreateCard, thumb: File?, onOpen: (CreateCard) -> Unit, onPin: (CreateCard) -> Unit, onDelete: (CreateCard) -> Unit) {
    val c = MM.colors
    Column {
        Box(
            Modifier.fillMaxWidth().aspectRatio(card.format.ratio).clip(MM.radius.card).background(c.surfaceSunk)
                .semantics { contentDescription = "Open: " + card.text.take(40) },
        ) {
            Surface(onClick = { onOpen(card) }, color = c.surfaceSunk, modifier = Modifier.fillMaxSize()) {
                if (thumb != null) AsyncImage(thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Box(Modifier.fillMaxSize().padding(MM.space.m), contentAlignment = Alignment.Center) {
                    Text(card.text.ifBlank { card.scriptureRef.orEmpty() }, style = MM.type.secondary, color = c.inkSecondary, maxLines = 6)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(card.vibe.label, style = MM.type.caption, color = c.inkMuted, modifier = Modifier.weight(1f))
            IconButton(onClick = { onPin(card) }, modifier = Modifier.size(MMSize.minTouch)) {
                Icon(Icons.Rounded.PushPin, if (card.pinned) "Unpin" else "Pin", tint = if (card.pinned) c.accent else c.inkMuted)
            }
            IconButton(onClick = { onDelete(card) }, modifier = Modifier.size(MMSize.minTouch)) {
                Icon(Icons.Rounded.Delete, "Delete", tint = c.inkMuted)
            }
        }
    }
}
