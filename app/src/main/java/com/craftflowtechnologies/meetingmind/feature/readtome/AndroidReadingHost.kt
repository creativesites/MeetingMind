package com.craftflowtechnologies.meetingmind.feature.readtome

import android.content.Context
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiCredentialStore
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiInteractions
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiSpeechClient
import com.craftflowtechnologies.meetingmind.ai.voice.read.AndroidTtsClient
import com.craftflowtechnologies.meetingmind.ai.voice.read.CloudGate
import com.craftflowtechnologies.meetingmind.ai.voice.read.DeliverySpec
import com.craftflowtechnologies.meetingmind.ai.voice.read.Environment
import com.craftflowtechnologies.meetingmind.ai.voice.read.ExtractOptions
import com.craftflowtechnologies.meetingmind.ai.voice.read.GeminiKeyTier
import com.craftflowtechnologies.meetingmind.ai.voice.read.GeminiKeyTierPolicy
import com.craftflowtechnologies.meetingmind.ai.voice.read.GeminiReadingEngine
import com.craftflowtechnologies.meetingmind.ai.voice.read.GeminiSectionVoice
import com.craftflowtechnologies.meetingmind.ai.voice.read.LibraryScriptureSource
import com.craftflowtechnologies.meetingmind.ai.voice.read.LoadedNote
import com.craftflowtechnologies.meetingmind.ai.voice.read.PassageSpeech
import com.craftflowtechnologies.meetingmind.ai.voice.read.SpeechSource
import com.craftflowtechnologies.meetingmind.ai.voice.read.MainThreadReadPlayer
import com.craftflowtechnologies.meetingmind.ai.voice.read.LocalSectionVoice
import com.craftflowtechnologies.meetingmind.ai.voice.read.LocalSpeechEngine
import com.craftflowtechnologies.meetingmind.ai.voice.read.NoteFacts
import com.craftflowtechnologies.meetingmind.ai.voice.read.NoteSpeechExtractor
import com.craftflowtechnologies.meetingmind.ai.voice.read.ReadPlayer
import com.craftflowtechnologies.meetingmind.ai.voice.read.ReadToMeEntitlements
import com.craftflowtechnologies.meetingmind.ai.voice.read.ReadToMeSettings
import com.craftflowtechnologies.meetingmind.ai.voice.read.ReadToMeSettingsStore
import com.craftflowtechnologies.meetingmind.ai.voice.read.ReadingCoordinator
import com.craftflowtechnologies.meetingmind.ai.voice.read.ReadingHost
import com.craftflowtechnologies.meetingmind.ai.voice.read.ReadingStore
import com.craftflowtechnologies.meetingmind.ai.voice.read.ScriptureLookup
import com.craftflowtechnologies.meetingmind.ai.voice.read.ScriptureResolver
import com.craftflowtechnologies.meetingmind.ai.voice.read.SectionVoice
import com.craftflowtechnologies.meetingmind.ai.voice.read.smart.RewriteModel
import com.craftflowtechnologies.meetingmind.ai.voice.read.smart.ScriptStore
import com.craftflowtechnologies.meetingmind.ai.voice.read.smart.SmartShaper
import com.craftflowtechnologies.meetingmind.core.audio.PlaybackController
import com.craftflowtechnologies.meetingmind.core.audio.QueueItem
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.Workflows
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureService
import com.craftflowtechnologies.meetingmind.core.work.WorkPrivacy
import com.craftflowtechnologies.meetingmind.feature.faith.FaithLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.Locale

/** The real phone behind [ReadingCoordinator]: the note database, the player, the voices and the settings. */
class AndroidReadingHost(context: Context) : ReadingHost {
    private val app = context.applicationContext
    private val settingsStore = ReadToMeSettingsStore(app)
    private val credentials = GeminiCredentialStore(app)
    private val prefs = UserPreferencesManager(app)

    override val store: ReadingStore = ReadingStore.get(app)

    override val player: ReadPlayer = MainThreadReadPlayer(object : ReadPlayer {
        override fun play(queueId: String, items: List<QueueItem>, startIndex: Int, startPositionMs: Long, expected: Int) =
            PlaybackController.playQueue(app, queueId, items, startIndex, startPositionMs, expected)
        override fun append(item: QueueItem) = PlaybackController.appendItem(item)
        override fun setExpected(count: Int) = PlaybackController.setExpectedCount(count)
    }, post = { r -> mainHandler.post(r) })
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    override suspend fun loadNote(noteId: String, options: ExtractOptions): LoadedNote? {
        if (noteId.startsWith(PASSAGE_PREFIX)) return loadPassage(noteId, options)
        val db = MeetMindDatabase.getInstance(app)
        val doc = NoteRepository(app, db).getDocument(noteId) ?: return null
        val note = doc.note
        // A slow or offline Bible lookup must not hold the reading: passages that don't arrive in time read as unavailable.
        val resolved = kotlinx.coroutines.withTimeoutOrNull(SCRIPTURE_TIMEOUT_MS) {
            ScriptureResolver(LibraryScriptureSource(ScriptureService(app))).resolve(doc.blocks, doc.scriptureRefs)
        } ?: emptyMap()
        val source = NoteSpeechExtractor.extract(
            noteTitle = note.title, notePrivate = note.isPrivate, blocks = doc.blocks,
            privateKeys = Workflows.template(note.workflow).privateKeys,
            scripture = ScriptureLookup { block -> resolved[block.id] }, options = options
        )
        val space = Workflows.space(note.workflow)
        val faithLocked = space == NotebookSpace.FAITH && note.isPrivate && prefs.preferencesFlow.first().faithLockEnabled && !FaithLock.isUnlocked()
        val notebookConfidential = note.notebookId?.let { id -> db.notebookDao().getById(id) }?.let { WorkPrivacy.mustStayOnDevice(app, noteId = noteId) } ?: false
        return LoadedNote(
            title = note.title.ifBlank { "Untitled note" },
            space = space,
            source = source,
            facts = NoteFacts(
                notePrivate = note.isPrivate,
                notebookConfidential = notebookConfidential,
                workPrivacyOffline = !notebookConfidential && WorkPrivacy.mustStayOnDevice(app, noteId = noteId),
                faithLocked = faithLocked
            ),
            language = null,
            category = com.craftflowtechnologies.meetingmind.ai.voice.read.profile.ReadCategory.of(note.workflow)
        )
    }

    /** One passage on its own (from the verse sheet or the Study workspace), read in the translation it was opened in. */
    private suspend fun loadPassage(id: String, options: ExtractOptions): LoadedNote? {
        val rest = id.removePrefix(PASSAGE_PREFIX)
        val version = rest.substringBefore(':').toIntOrNull()?.takeIf { it > 0 }
        val reference = com.craftflowtechnologies.meetingmind.core.scripture.YouVersionScriptureProvider.parsePassageId(rest.substringAfter(':')) ?: return null
        val ref = com.craftflowtechnologies.meetingmind.core.model.ScriptureRef(
            id = "r", noteId = id, blockId = "b", bookUsfm = reference.book.usfm, chapter = reference.chapter,
            verseStart = reference.verseStart, verseEnd = reference.verseEnd, versionId = version,
            origin = com.craftflowtechnologies.meetingmind.core.model.ScriptureOrigin.USER, createdAt = 0L
        )
        val block = com.craftflowtechnologies.meetingmind.core.model.NoteBlock(
            id = "b", noteId = id, position = 0, type = com.craftflowtechnologies.meetingmind.core.model.NoteBlockType.SCRIPTURE,
            payload = mapOf(com.craftflowtechnologies.meetingmind.core.model.NoteBlock.PAYLOAD_SCRIPTURE_REF_ID to "r", "reference" to reference.display()),
            source = com.craftflowtechnologies.meetingmind.core.model.BlockSource.SCRIPTURE
        )
        val resolved = kotlinx.coroutines.withTimeoutOrNull(SCRIPTURE_TIMEOUT_MS) {
            ScriptureResolver(LibraryScriptureSource(ScriptureService(app))).resolve(listOf(block), listOf(ref))
        } ?: emptyMap()
        // One part per verse (or per few verses in a long chapter), so the reader can follow the verse being read.
        val verses = resolved[block.id]?.takeIf { !it.userTyped }?.verses?.takeIf { it.isNotEmpty() }
        val source = if (verses != null) {
            SpeechSource(PassageSpeech.sections(verses.map { it.number to it.text }, resolved.getValue(block.id)!!.spokenReference, block.id, reference.book.name))
        } else {
            NoteSpeechExtractor.extract(reference.display(), false, listOf(block), emptySet(), ScriptureLookup { resolved[it.id] }, options)
        }
        return LoadedNote(reference.display(), NotebookSpace.FAITH, source, NoteFacts(false, false, false, false), null)
    }

    override suspend fun environment(): Environment = Environment(
        profile = prefs.preferencesFlow.first().processingProfile,
        geminiConfigured = credentials.getApiKey() != null,
        entitled = ReadToMeEntitlements.current.canUseGeminiVoice(),
        keyTier = GeminiKeyTierPolicy.tier(gateOn = com.craftflowtechnologies.meetingmind.BuildConfig.READ_TO_ME_TIER_GATE, confirmedPaid = credentials.keyPlanConfirmedPaid()),
        usingBuiltInKey = credentials.usingSystemKey()
    )

    private val profileStore = com.craftflowtechnologies.meetingmind.ai.voice.read.profile.ReadToMeProfileStore(app)
    override fun profile() = profileStore.load()
    override fun updateProfile(change: (com.craftflowtechnologies.meetingmind.ai.voice.read.profile.ReadToMeProfile) -> com.craftflowtechnologies.meetingmind.ai.voice.read.profile.ReadToMeProfile) = profileStore.update(change)

    override fun settings(): ReadToMeSettings = settingsStore.load()

    override fun updateSettings(change: (ReadToMeSettings) -> ReadToMeSettings): ReadToMeSettings = settingsStore.update(change)

    override fun localVoice(settings: ReadToMeSettings, requireOffline: Boolean): SectionVoice = LocalSectionVoice(
        engine = LocalSpeechEngine({ AndroidTtsClient(app) }, File(app.cacheDir, "read_to_me_tts").apply { mkdirs() }),
        locale = Locale.getDefault(), voiceName = settings.localVoice, rate = settings.pace, requireOffline = requireOffline
    )

    override fun shaper(noteId: String, allowCloud: Boolean, passages: List<String>): SmartShaper {
        val model = if (!allowCloud) null else {
            val lm = com.craftflowtechnologies.meetingmind.ai.cloud.GeminiLanguageModel(
                com.craftflowtechnologies.meetingmind.ai.cloud.GeminiHttpTransport(credentials),
                systemInstruction = "You rewrite short passages from a person's notes so they sound natural read aloud. Follow the rules in the request exactly and reply with only the rewritten text."
            )
            val gate = CloudGate(true, { prefs.preferencesFlow.first().processingProfile })
            RewriteModel { prompt ->
                if (gate.blockedReason() != null) null
                else (lm.generate(prompt, 1024) as? com.craftflowtechnologies.meetingmind.ai.common.AiResult.Success)?.value
            }
        }
        return SmartShaper(model, ScriptStore(store.scriptFile(noteId)), passages)
    }

    override fun geminiVoice(settings: ReadToMeSettings, delivery: DeliverySpec, routeAllowsCloud: Boolean): SectionVoice {
        val gate = CloudGate(routeAllowsCloud, { prefs.preferencesFlow.first().processingProfile })
        // One request may not hold a reading for minutes: it times out and is retried or reported.
        val http = GeminiInteractions.mediaClient().newBuilder().callTimeout(90, java.util.concurrent.TimeUnit.SECONDS).readTimeout(80, java.util.concurrent.TimeUnit.SECONDS).build()
        val client = GeminiSpeechClient(GeminiInteractions(credentials, http), gate)
        return GeminiSectionVoice(GeminiReadingEngine(client, File(app.cacheDir, "read_to_me_work").apply { mkdirs() }), settings.geminiVoice, delivery)
    }
}

private const val SCRIPTURE_TIMEOUT_MS = 10_000L
private const val PASSAGE_PREFIX = "passage:"

/** The one coordinator for the app, like the player it feeds. */
object ReadToMe {
    /** Makes the reading in the background (see [ReadingWorker]); it can be listened to later. */
    fun enqueueBackground(context: Context, noteId: String) = ReadingWorker.enqueue(context, noteId)

    /** The reading id for one passage in one translation; [ReadToMeSheet] and the coordinator take it where a note id goes. */
    fun passageId(reference: com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReference, versionId: Int?) =
        com.craftflowtechnologies.meetingmind.ai.voice.read.PassageSpeech.passageId(reference, versionId)

    @Volatile private var shared: ReadingCoordinator? = null

    fun coordinator(context: Context): ReadingCoordinator = shared ?: synchronized(this) {
        shared ?: ReadingCoordinator(AndroidReadingHost(context), CoroutineScope(SupervisorJob() + Dispatchers.Default)).also { shared = it }
    }
}
