package com.craftflowtechnologies.meetingmind.feature.create

import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.core.create.CardVersion
import com.craftflowtechnologies.meetingmind.core.create.CreateBackdrops
import com.craftflowtechnologies.meetingmind.core.create.CreateBackground
import com.craftflowtechnologies.meetingmind.core.create.CreateCard
import com.craftflowtechnologies.meetingmind.core.create.CreateCardRenderer
import com.craftflowtechnologies.meetingmind.core.create.CreateDesign
import com.craftflowtechnologies.meetingmind.core.create.CreateFormat
import com.craftflowtechnologies.meetingmind.core.create.CreateGallery
import com.craftflowtechnologies.meetingmind.core.create.CreateGenerator
import com.craftflowtechnologies.meetingmind.core.create.CreateGuards
import com.craftflowtechnologies.meetingmind.core.create.CreateOutcome
import com.craftflowtechnologies.meetingmind.core.create.CreatePiece
import com.craftflowtechnologies.meetingmind.core.create.CreateRenderInput
import com.craftflowtechnologies.meetingmind.core.create.CreateRequest
import com.craftflowtechnologies.meetingmind.core.create.CreateSeed
import com.craftflowtechnologies.meetingmind.core.create.CreateSession
import com.craftflowtechnologies.meetingmind.core.create.CreateSourceKind
import com.craftflowtechnologies.meetingmind.core.create.CreateStarter
import com.craftflowtechnologies.meetingmind.core.create.CreateStarters
import com.craftflowtechnologies.meetingmind.core.create.CreateVibe
import com.craftflowtechnologies.meetingmind.core.create.CreateVibePolicy
import com.craftflowtechnologies.meetingmind.core.create.GeminiCreateModel
import com.craftflowtechnologies.meetingmind.core.create.ProviderScriptureLookup
import com.craftflowtechnologies.meetingmind.core.create.RemixAction
import com.craftflowtechnologies.meetingmind.core.create.RemixOutcome
import com.craftflowtechnologies.meetingmind.core.create.ResolvedScripture
import com.craftflowtechnologies.meetingmind.core.create.ScriptureFetch
import com.craftflowtechnologies.meetingmind.core.scripture.BibleVersions
import com.craftflowtechnologies.meetingmind.core.share.ShareActions
import com.craftflowtechnologies.meetingmind.core.share.ShareTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CreateStep(val label: String) { WORDS("Words"), FORMAT("Format"), DESIGN("Design"), SHARE("Share") }

enum class StatusTone { Info, Error }

/** One plain line under the writing controls, with at most one action. */
data class CreateStatus(val tone: StatusTone, val text: String, val action: StatusAction? = null)
enum class StatusAction(val label: String) { RETRY("Retry"), STARTERS("Show starters") }

/** Which share apps are on this phone. */
data class InstalledApps(val whatsapp: Boolean = false, val instagram: Boolean = false)

/** Everything the studio draws. */
data class CreateUi(
    val card: CreateCard = CreateCard(),
    val scripture: ResolvedScripture? = null,
    val step: CreateStep = CreateStep.WORDS,
    val vibes: List<CreateVibe> = emptyList(),
    val suggested: CreateVibe = CreateVibe.PEACEFUL,
    val prompt: String = "",
    val verseInput: String = "",
    val verseError: String? = null,
    val options: List<CreatePiece> = emptyList(),
    val chosenOption: Int = 0,
    val status: CreateStatus? = null,
    val starters: List<CreateStarter>? = null,
    val working: String? = null,
    val installed: InstalledApps = InstalledApps(),
    val message: String? = null,
    val sharing: Boolean = false,
    val savedCount: Int = 0
) {
    val canWrite: Boolean get() = working == null && !sharing
}

private data class Misc(
    val step: CreateStep = CreateStep.WORDS,
    val prompt: String = "",
    val verseInput: String = "",
    val verseError: String? = null,
    val options: List<CreatePiece> = emptyList(),
    val chosenOption: Int = 0,
    val status: CreateStatus? = null,
    val starters: List<CreateStarter>? = null,
    val message: String? = null,
    val sharing: Boolean = false,
    val vibeChosen: Boolean = false,
    val backgroundChosen: Boolean = false
)

/** Holds one studio session: the card, what is being asked of the model, and sharing. */
@OptIn(ExperimentalCoroutinesApi::class)
class CreateViewModel(app: Application) : AndroidViewModel(app) {
    private val context = app.applicationContext
    private val gallery = CreateGallery(context)
    private val scriptureLookup = ProviderScriptureLookup(context)
    private val generator = CreateGenerator(GeminiCreateModel(context), scriptureLookup, versionId = { null })

    private val sessionFlow = MutableStateFlow(CreateSession(CreateCard(), generator))
    private val session get() = sessionFlow.value
    private val misc = MutableStateFlow(Misc())
    private val installed = MutableStateFlow(InstalledApps())

    private var grief = false

    val ui: StateFlow<CreateUi> = sessionFlow.flatMapLatest { s ->
        combine(s.card, s.scripture, s.working, misc, installed) { card, verse, working, m, apps ->
            CreateUi(
                card = card, scripture = verse, step = m.step,
                vibes = CreateVibePolicy.ordered(card.source, grief),
                suggested = CreateVibePolicy.suggestFromText(card.source, m.prompt.ifBlank { card.text }, grief),
                prompt = m.prompt, verseInput = m.verseInput, verseError = m.verseError,
                options = m.options, chosenOption = m.chosenOption, status = m.status, starters = m.starters,
                working = working, installed = apps, message = m.message, sharing = m.sharing
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CreateUi())

    /** Opens the studio for [seed], or reopens a saved card. */
    fun start(seed: CreateSeed, reopen: CreateCard? = null) {
        installed.value = detectApps()
        if (reopen != null) {
            sessionFlow.value = CreateSession(reopen, generator)
            misc.value = Misc(prompt = reopen.sourceText, verseInput = reopen.scriptureRef.orEmpty(), vibeChosen = true, backgroundChosen = true, step = CreateStep.DESIGN)
            viewModelScope.launch { session.loadScripture() }
            return
        }
        grief = seed.grief
        val vibe = CreateVibePolicy.enforce(seed.source, seed.vibe ?: CreateVibePolicy.suggestFromText(seed.source, seed.text, seed.grief), seed.grief)
        val card = CreateCard(
            source = seed.source, sourceText = seed.text.take(CreateCard.MAX_SOURCE), sourceRef = seed.reference,
            vibe = vibe, design = CreateDesign(background = CreateBackdrops.default(vibe))
        )
        val s = CreateSession(card, generator)
        sessionFlow.value = s
        misc.value = Misc(prompt = seed.text.take(CreateCard.MAX_SOURCE), verseInput = seed.reference.orEmpty(), vibeChosen = seed.vibe != null)
        viewModelScope.launch {
            when {
                // A note quote or selected text is the card, word for word.
                seed.source.verbatim && seed.text.isNotBlank() -> s.choose(CreatePiece(CreateGuards.leadingExcerpt(seed.text)), "Your words")
                // A verse is its reference; the text comes from the Bible library.
                seed.source == CreateSourceKind.VERSE && !seed.reference.isNullOrBlank() -> {
                    val f = s.setReference(seed.reference)
                    if (f !is ScriptureFetch.Found) misc.update { it.copy(verseError = verseMessage(f)) }
                }
            }
        }
    }

    private fun detectApps(): InstalledApps {
        fun has(pkg: String) = runCatching { context.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)
        return InstalledApps(whatsapp = has("com.whatsapp") || has("com.whatsapp.w4b"), instagram = has("com.instagram.android"))
    }

    fun setStep(step: CreateStep) = misc.update { it.copy(step = step, message = null) }

    fun setPrompt(text: String) {
        val t = text.take(CreateCard.MAX_SOURCE)
        misc.update { it.copy(prompt = t) }
        val c = session.card.value
        session.setSource(c.source, t, c.sourceRef)
        if (!misc.value.vibeChosen && c.source == CreateSourceKind.FREE_PROMPT) applyVibe(CreateVibePolicy.suggestFromText(c.source, t), chosen = false)
    }

    fun setSource(kind: CreateSourceKind) {
        val c = session.card.value
        session.setSource(kind, c.sourceText, c.sourceRef)
        applyVibe(CreateVibePolicy.enforce(kind, c.vibe, grief), chosen = misc.value.vibeChosen)
        misc.update { it.copy(options = emptyList(), status = null, starters = null) }
    }

    fun chooseVibe(vibe: CreateVibe) = applyVibe(vibe, chosen = true)

    private fun applyVibe(vibe: CreateVibe, chosen: Boolean) {
        val before = session.card.value
        val allowed = CreateVibePolicy.enforce(before.source, vibe, grief)
        session.setVibe(allowed)
        // Backgrounds follow the mood until the person picks their own.
        if (!misc.value.backgroundChosen && before.design.background is CreateBackground.Pack) {
            session.setDesign(before.design.copy(background = CreateBackdrops.default(allowed)))
        }
        misc.update { it.copy(vibeChosen = chosen || it.vibeChosen) }
    }

    fun write() {
        if (!ui.value.canWrite) return
        val c = session.card.value
        val m = misc.value
        val req = CreateRequest(
            source = c.source, text = m.prompt, reference = c.sourceRef ?: c.scriptureRef,
            vibe = if (m.vibeChosen) c.vibe else null, customVibe = if (c.vibe == CreateVibe.CUSTOM) m.prompt else null, grief = grief
        )
        misc.update { it.copy(status = null, starters = null, message = null) }
        viewModelScope.launch {
            when (val out = session.write(req)) {
                is CreateOutcome.Written -> {
                    if (!misc.value.vibeChosen) applyVibe(out.suggested, chosen = false)
                    misc.update {
                        it.copy(
                            options = out.options, chosenOption = 0,
                            status = if (out.verseDropped) CreateStatus(StatusTone.Info, "I couldn't load one of the verses, so it was left off.") else null
                        )
                    }
                    session.choose(out.options.first(), "Written")
                    misc.update { it.copy(verseInput = session.card.value.scriptureRef.orEmpty()) }
                }
                is CreateOutcome.Unavailable -> misc.update { it.copy(status = CreateStatus(StatusTone.Info, out.message, StatusAction.STARTERS)) }
                is CreateOutcome.Failed -> misc.update { it.copy(status = CreateStatus(StatusTone.Error, out.message, StatusAction.RETRY)) }
            }
        }
    }

    fun chooseOption(index: Int) {
        val piece = misc.value.options.getOrNull(index) ?: return
        misc.update { it.copy(chosenOption = index) }
        viewModelScope.launch {
            session.choose(piece, "Written")
            misc.update { it.copy(verseInput = session.card.value.scriptureRef.orEmpty()) }
        }
    }

    fun showStarters() {
        val c = session.card.value
        misc.update { it.copy(starters = CreateStarters.forVibe(c.vibe, c.source), status = null) }
    }

    fun useStarter(starter: CreateStarter) {
        viewModelScope.launch {
            session.choose(CreatePiece(starter.text), "Starter", starter.vibe)
            starter.verseRef?.let { ref ->
                // The reference is real; the text, if it can be loaded, comes from the Bible library.
                session.setReference(ref)
            }
            misc.update { it.copy(verseInput = session.card.value.scriptureRef.orEmpty(), starters = null) }
        }
    }

    fun remix(action: RemixAction) {
        viewModelScope.launch {
            when (val out = session.remix(action)) {
                is RemixOutcome.Done -> misc.update { it.copy(status = null, verseInput = session.card.value.scriptureRef.orEmpty()) }
                is RemixOutcome.Unavailable -> misc.update { it.copy(status = CreateStatus(StatusTone.Info, out.message)) }
                is RemixOutcome.Failed -> misc.update { it.copy(status = CreateStatus(StatusTone.Error, out.message)) }
            }
        }
    }

    fun undo() { viewModelScope.launch { session.undo(); misc.update { it.copy(verseInput = session.card.value.scriptureRef.orEmpty(), status = null) } } }

    fun edit(text: String) { viewModelScope.launch { session.edit(text) } }

    fun setVerseInput(text: String) = misc.update { it.copy(verseInput = text, verseError = null) }

    /** Called when the person finishes typing a reference: the text is fetched again. */
    fun commitVerse() {
        val input = misc.value.verseInput
        viewModelScope.launch {
            val f = session.setReference(input)
            misc.update { it.copy(verseError = if (input.isBlank() || f is ScriptureFetch.Found) null else verseMessage(f)) }
        }
    }

    private fun verseMessage(f: ScriptureFetch) = when (f) {
        is ScriptureFetch.Invalid -> "That isn't a verse I can find."
        is ScriptureFetch.Unavailable -> "Couldn't load ${f.reference}. Check your connection and try again."
        is ScriptureFetch.Found -> null
    }

    fun setFormat(format: CreateFormat) = session.setFormat(format)

    fun setDesign(update: (CreateDesign) -> CreateDesign, userChoseBackground: Boolean = false) {
        session.setDesign(update(session.card.value.design))
        if (userChoseBackground) misc.update { it.copy(backgroundChosen = true) }
    }

    fun setPhoto(path: String) = setDesign({ it.copy(background = CreateBackground.Photo(path)) }, userChoseBackground = true)

    fun dismissMessage() = misc.update { it.copy(message = null) }

    /** Renders at full size, saves the creation, then sends or saves it. */
    fun share(target: ShareTarget, companion: Bitmap?) {
        if (misc.value.sharing) return
        misc.update { it.copy(sharing = true, message = null) }
        viewModelScope.launch {
            val c = session.card.value
            val verse = session.scripture.value
            val file = withContext(Dispatchers.Default) {
                val bmp = CreateCardRenderer.render(context, CreateRenderInput(c.text, verse, c.format, c.design), 1f, companion.takeIf { c.design.includeCompanion })
                ShareActions.saveToCache(context, bmp, "meetingmind_create_${System.currentTimeMillis()}")
            }
            persist(companion)
            val result = runCatching { ShareActions.send(context, file, target, caption = shareCaption(c, verse)) }.getOrElse { "Couldn't open that app." }
            misc.update { it.copy(sharing = false, message = result ?: if (target == ShareTarget.ANY) null else "Done. It's in My creations too.") }
        }
    }

    /** The words as plain text, for Copy and as a caption. */
    fun plainText(): String = shareCaption(session.card.value, session.scripture.value)

    private fun shareCaption(c: CreateCard, v: ResolvedScripture?): String = buildString {
        append(c.text.trim())
        if (v != null) {
            if (isNotEmpty()) append("\n\n")
            append("“").append(v.text.trim('“', '”', '"')).append("”\n— ").append(v.reference).append(" (").append(v.versionAbbreviation).append(")")
        }
    }

    /** Saves the card and its small preview to "My creations". */
    fun saveNow(companion: Bitmap? = null) { viewModelScope.launch { persist(companion) } }

    private suspend fun persist(companion: Bitmap?) {
        val c = session.card.value
        val verse = session.scripture.value
        val saved = gallery.save(c.copy(scriptureVersionId = verse?.versionId ?: c.scriptureVersionId ?: BibleVersions.DEFAULT_ID.takeIf { c.scriptureRef != null }))
        if (saved) withContext(Dispatchers.Default) {
            val thumb = CreateCardRenderer.render(context, CreateRenderInput(c.text, verse, c.format, c.design), 0.3f, companion.takeIf { c.design.includeCompanion })
            gallery.saveThumb(c.id, thumb)
        }
    }

    /** Whether the words and passage fit the card; the studio warns when a passage is too long. */
    fun fits(): Boolean {
        val c = session.card.value
        return CreateCardRenderer.fits(context, CreateRenderInput(c.text, session.scripture.value, c.format, c.design))
    }

    override fun onCleared() { super.onCleared() }
}
