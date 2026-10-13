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
import com.craftflowtechnologies.meetingmind.ai.voice.read.GeminiReadingEngine
import com.craftflowtechnologies.meetingmind.ai.voice.read.GeminiSectionVoice
import com.craftflowtechnologies.meetingmind.ai.voice.read.LibraryScriptureSource
import com.craftflowtechnologies.meetingmind.ai.voice.read.LoadedNote
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

    override val player = object : ReadPlayer {
        override fun play(queueId: String, items: List<QueueItem>, startIndex: Int, startPositionMs: Long, expected: Int) =
            PlaybackController.playQueue(app, queueId, items, startIndex, startPositionMs, expected)
        override fun append(item: QueueItem) = PlaybackController.appendItem(item)
        override fun setExpected(count: Int) = PlaybackController.setExpectedCount(count)
    }

    override suspend fun loadNote(noteId: String, options: ExtractOptions): LoadedNote? {
        val db = MeetMindDatabase.getInstance(app)
        val doc = NoteRepository(app, db).getDocument(noteId) ?: return null
        val note = doc.note
        val resolved = ScriptureResolver(LibraryScriptureSource(ScriptureService(app))).resolve(doc.blocks, doc.scriptureRefs)
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
            language = null
        )
    }

    override suspend fun environment(): Environment = Environment(
        profile = prefs.preferencesFlow.first().processingProfile,
        geminiConfigured = credentials.getApiKey() != null,
        entitled = ReadToMeEntitlements.current.canUseGeminiVoice(),
        keyTier = if (credentials.keyPlanConfirmedPaid()) GeminiKeyTier.PAID else GeminiKeyTier.UNKNOWN,
        usingBuiltInKey = credentials.usingSystemKey()
    )

    override fun settings(): ReadToMeSettings = settingsStore.load()

    override fun updateSettings(change: (ReadToMeSettings) -> ReadToMeSettings): ReadToMeSettings = settingsStore.update(change)

    override fun localVoice(settings: ReadToMeSettings, requireOffline: Boolean): SectionVoice = LocalSectionVoice(
        engine = LocalSpeechEngine({ AndroidTtsClient(app) }, File(app.cacheDir, "read_to_me_tts").apply { mkdirs() }),
        locale = Locale.getDefault(), voiceName = settings.localVoice, rate = settings.pace, requireOffline = requireOffline
    )

    override fun geminiVoice(settings: ReadToMeSettings, delivery: DeliverySpec, routeAllowsCloud: Boolean): SectionVoice {
        val gate = CloudGate(routeAllowsCloud, { prefs.preferencesFlow.first().processingProfile })
        val client = GeminiSpeechClient(GeminiInteractions(credentials), gate)
        return GeminiSectionVoice(GeminiReadingEngine(client, File(app.cacheDir, "read_to_me_work").apply { mkdirs() }), settings.geminiVoice, delivery)
    }
}

/** The one coordinator for the app, like the player it feeds. */
object ReadToMe {
    @Volatile private var shared: ReadingCoordinator? = null

    fun coordinator(context: Context): ReadingCoordinator = shared ?: synchronized(this) {
        shared ?: ReadingCoordinator(AndroidReadingHost(context), CoroutineScope(SupervisorJob() + Dispatchers.Default)).also { shared = it }
    }
}
