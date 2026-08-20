# MeetingMind — Architecture

This describes the actual architecture of the codebase as of the audit/reconciliation pass in `docs/AUDIT.md`. It documents what exists, not an aspirational design.

## 1. Stack

- Kotlin 2.2.10, Jetpack Compose (BOM 2024.09.00), Material 3
- AGP 9.1.1 (requires Gradle ≥ 9.3.1 — the repo now ships a wrapper pinned to 9.3.1)
- compileSdk 36.1 / targetSdk 36 / minSdk 24, Java 11 target
- Room 2.7.0 (KSP), Navigation Compose 2.8.9, DataStore Preferences 1.1.7
- Firebase BOM 34.17.0 (Auth, Firestore, `firebase-ai` — the last is declared but currently unused)
- OkHttp/Retrofit/Moshi (used directly for the Gemini REST calls, not for a general backend)
- No NDK/C++ code exists in the project today.

## 2. Package Layout

```
com.example
├── ai/                     — AI subsystem, interface-first
│   ├── asr/                — SpeechRecognizer interface + real SherpaParakeetSpeechRecognizer
│   ├── diarization/        — SpeakerDiarizer interface + real SherpaSpeakerDiarizer
│   ├── embeddings/         — EmbeddingEngine interface + implementation(s)
│   ├── llm/                — MeetingIntelligenceEngine/LanguageModel interfaces + real
│   │                          RealMeetingIntelligenceEngine/MediaPipeLanguageModel, plus
│   │                          TranscriptChunker/MeetingIntelligenceJsonParser
│   ├── vad/                — VoiceActivityDetector interface + real SileroVadDetector
│   ├── modelmanagement/     — ModelStorage/ModelVerifier/ModelDownloader/ModelCatalog,
│   │                          SherpaEngineManager/LlmEngineManager (native engine reuse),
│   │                          ArchiveExtractor
│   └── pipeline/            — MeetingProcessingPipeline: orchestrates the above
├── core/
│   ├── audio/               — AudioRecorder, AudioExtractor, AudioFormatConverter,
│   │                          MeetingRecordingService (foreground recording service),
│   │                          PlaybackService/PlaybackController/PlaybackState (Phase 3A —
│   │                          single app-wide Media3 MediaSessionService-backed player;
│   │                          replaced the old per-screen AudioPlayerManager)
│   ├── common/               — DeviceCapabilityDetector, Formatters
│   ├── database/             — Room entities, DAOs, MeetingMindDatabase (real Migrations 1→2, 2→3)
│   ├── datastore/            — UserPreferencesManager (DataStore Preferences)
│   ├── domain/                — Use cases (partially wired — see AUDIT.md §F)
│   ├── export/                 — MarkdownExporter/CsvExporter/PdfExporter/DocxExporter +
│   │                               ExportManager (Phase 3A — real export, no fake formatting)
│   ├── firebase/              — FirebaseAuthManager, FirestoreSyncManager (still dead code —
│   │                              see docs/DATA_SAFETY.md)
│   ├── future/                 — MeetingProvider/CalendarProvider — placeholders for a later,
│   │                              separate backend-integration phase (Calendar/Zoom/Meet/Teams),
│   │                              unrelated to and not implemented by the local-AI phases above
│   ├── model/                   — Domain models (Meeting, RecordingType, Transcript, ActionItem,
│   │                                Decision, Question, FollowUp, Speaker, ...)
│   ├── repository/               — MeetingRepository, TranscriptRepository, SearchRepository,
│   │                                ActionItemRepository, ModelRepository
│   ├── share/                     — ShareHelper/ShareContentFormatter (Phase 3A — Android
│   │                                 Sharesheet integration, never a hardcoded per-app integration)
│   └── ui/                        — Shared Compose components (OfflineShieldBadge, MiniPlayerBar,
│                                     ExportDialog, waveform, ...)
├── feature/                        — One package per screen: home, recording, importing,
│                                      processing, meetingdetail, search, settings, models,
│                                      onboarding, navigation (Routes)
└── ui/theme/                        — Material 3 theme, color tokens, typography
```

## 3. Layering & State Management

- **UI**: Jetpack Compose screens in `feature/*`, each paired with an `AndroidViewModel` in the same file.
- **State**: `StateFlow`/`Flow` throughout — Room DAOs return `Flow`, DataStore returns `Flow`, ViewModels expose `StateFlow` via `stateIn`. No `LiveData`, no manual observer patterns.
- **Repositories**: thin mapping layer between Room entities and domain models (`core/model/*`). Repositories are constructed directly by ViewModels (see §5, DI).
- **Domain/use cases**: exist in `core/domain/UseCases.kt` but are only partially adopted — `SearchMeetingsUseCase` and `AskMeetingUseCase` are used by their respective ViewModels; the other five (`StartRecordingUseCase`, `StopRecordingUseCase`, `ImportRecordingUseCase`, `TranscribeMeetingUseCase`, `DeleteMeetingUseCase`, `DownloadModelUseCase`) are dead code today — the ViewModels that need this logic duplicate it inline instead of calling them. **This should be resolved by wiring the ViewModels to the use cases (preferred, since the use cases are correctly written) rather than deleting the use-case layer.**
- **AI layer**: every AI capability is defined as an interface — `SpeechRecognizer`, `SpeakerDiarizer`, `EmbeddingEngine`, `MeetingIntelligenceEngine`, `VoiceActivityDetector` — with a default implementation supplied via constructor default parameters (e.g., `MeetingProcessingPipeline(..., speechRecognizer: SpeechRecognizer = RealSpeechRecognizer(), ...)`). This is the correct shape for swapping in real local implementations later without touching call sites — see `docs/AI_ARCHITECTURE.md`.

## 4. Data Flow (Primary P0 Path)

```
RecordingViewModel.startRecording()
  → AudioRecorder.startRecording()         (real MediaRecorder session)
  → MeetingRecordingService.startService()  (foreground service, wake lock)
  → MeetingRepository.createInitialMeeting() (Room insert, status=RECORDING)

RecordingViewModel.finishRecording()
  → AudioRecorder.stopRecording() → File
  → navigate to Processing route with (meetingId, audioPath, durationMs)

ProcessingViewModel.startPipeline()
  → MeetingProcessingPipeline.processMeeting()
       1. VoiceActivityDetector.detectSpeechIntervals()  [REAL: SileroVadDetector once installed, else honestly ModelUnavailable — degrades gracefully, doesn't block]
       2. SpeechRecognizer.transcribe()                   [REAL: SherpaParakeetSpeechRecognizer once installed, else honestly ModelUnavailable — REQUIRED GATE: stops the pipeline honestly]
       3. SpeakerDiarizer.diarize()                        [REAL: SherpaSpeakerDiarizer once installed, else honestly ModelUnavailable — degrades gracefully, keeps ASR's own segments]
       4. MeetingIntelligenceEngine.processMeeting()        [REAL: RealMeetingIntelligenceEngine (MediaPipe + Qwen2.5) once installed, else honestly ModelUnavailable — degrades gracefully, doesn't block]
       5. EmbeddingEngine.embed() per segment + summary      [real, primitive — only runs over real transcript text]
       6. Persist whatever is real to Room; mark meeting READY, or MODEL_REQUIRED if ASR was unavailable
  → navigate to Meeting Detail (or show "model required" state — see docs/AI_ARCHITECTURE.md)
```

Every step in the pipeline is a call against an interface returning `AiResult<T>` (see `ai/common/AiTypes.kt`); only ASR unavailability stops the whole meeting (no transcript = nothing downstream can be real). Diarization and meeting intelligence being unavailable degrade gracefully once a transcript exists. As of Phase 2 (see `docs/AI_ARCHITECTURE.md` §0b/§0c), all four AI stages — VAD, ASR, diarization, and meeting intelligence — are backed by real local models (Silero VAD, Parakeet TDT 0.6B v3, sherpa-onnx speaker diarization, Qwen2.5-1.5B-Instruct via MediaPipe) the moment the user downloads them via the Model Manager; every implementation self-reports `AiResult.ModelUnavailable` honestly when its model isn't installed, so there is no separate "unavailable" default to switch between. The formerly-fake `AudioPreprocessor` step was removed entirely — its output was already dead/unused code even before its `sin()`-based computation was found to be fabricated.

## 5. Dependency Injection

There is no DI framework (no Hilt, no Koin). Every `AndroidViewModel` constructs its dependencies directly in its own initializer, e.g.:

```kotlin
class RecordingViewModel(application: Application) : AndroidViewModel(application) {
    private val database = MeetingMindDatabase.getInstance(application)
    private val meetingRepository = MeetingRepository(application, database)
    val recorder = AudioRecorder(application)
    ...
}
```

This is acceptable at the current scale (a handful of screens, no test doubles needed yet) but does not scale, and it's part of why the use-case layer ended up half-adopted — there's no central composition point forcing consistency. **Recommendation**: introduce a lightweight DI approach (manual `AppContainer` or Hilt) when the AI layer grows real, heavier dependencies (loaded model handles, native runtime instances) that should be singletons rather than re-constructed per screen.

## 6. Persistence

`MeetingMindDatabase` (Room, version 3 as of Phase 3A, `exportSchema = false`) — 12 entities covering meetings, transcript segments, speakers, action items, decisions, questions, follow-ups, topics, embeddings, AI model catalog, processing jobs, and chat messages. Schema is well-normalized with correct `ForeignKey(CASCADE)` relationships and indices (including a text index supporting keyword search). `fallbackToDestructiveMigration()` remains as a safety net for any future untracked version jump, but both real steps so far are explicit `Migration` objects that preserve existing on-device data: `MIGRATION_1_2` (see `docs/AI_ARCHITECTURE.md` §0c) and `MIGRATION_2_3` (adds `meetings.recordingType`/`meetings.customContext`, existing rows default to `GENERAL`). `exportSchema` remaining `false` (see `docs/AUDIT.md` §F) is still an open gap for a real release.

Audio files live in app-private storage: `context.filesDir/meetings/{meetingId}/audio.<ext>` — never in shared/external storage, never uploaded by default.

## 7. Background Work

As of Phase 3A, recording→AI processing runs as real WorkManager background work
(`MeetingProcessingWorker`, a `CoroutineWorker` wrapping `MeetingProcessingPipeline`), enqueued
uniquely (`WorkManager.enqueueUniqueWork(..., ExistingWorkPolicy.APPEND_OR_REPLACE, ...)`) so only
one AI-heavy job runs at a time and a recording requested mid-processing queues behind it rather
than running concurrently. It survives the `ProcessingScreen`/its ViewModel being destroyed by
navigation, app minimization, or the screen locking — a real requirement once local
VAD/ASR/diarization/LLM inference can take minutes. Progress is reported through a real
stage-based (never fabricated-percentage) low-priority notification that never surfaces
transcript/summary content, only the recording title and current stage. `ProcessingJobEntity`
still exists in Room for the same purpose it always had (observable job state), now genuinely
useful since the underlying work can actually outlive the observing screen.

Audio playback is a separate, always-on-when-active background concern: a single app-wide
`PlaybackService` (Media3 `MediaSessionService`) owns the one `ExoPlayer` instance the whole app
shares via `PlaybackController`, giving real Android media-notification/lock-screen/Bluetooth
controls "for free" and making duplicate/orphaned playback structurally impossible — every screen
talks to the same player, and starting a new recording or a new playback session stops any
existing one first. See `docs/ROADMAP.md` for what's still outstanding around either flow.

## 8. Firebase's Role (as designed vs. as implemented)

**As designed** (and correctly reflected in `FirebaseAuthManager`'s null-safe fallback and `FirestoreSyncManager`'s metadata-only payload): Firebase is auth + optional lightweight metadata only, never content.

**As implemented today**: Firebase Auth now uses a real Credential Manager → Google ID token → `FirebaseAuth.signInWithCredential` flow (`FirebaseAuthManager.signInWithGoogle`), invoked from Settings' "Sign In with Google" button. If Firebase/Google Sign-In isn't configured for a given build (no `google-services.json` and/or no web client ID in `res/values/strings.xml:google_sign_in_web_client_id`), `authAvailability` reports `AuthAvailability.NotConfigured` and the sign-in button is hidden rather than fabricating a signed-in user — recording and local processing are never gated on authentication (see `docs/ARCHITECTURE.md` local-first policy below). `FirestoreSyncManager` is still dead code (never called) — wiring it in behind the existing `cloudSyncEnabled` preference remains a P2 roadmap item, not a correctness problem, since the app functions entirely offline regardless.

**Local-first authentication policy**: sign-in is never required to record, import, or (once installed) locally process a meeting. It exists solely to unlock optional future account/sync features.

## 9. What Should Not Change

- The interface-first AI layer shape.
- The Room schema (extend it, don't replace it — it already anticipates features not yet wired up).
- The `MeetingSource` enum / `core/future` scaffolding for Phase 2 sources.
- The overall package layout.

## 10. What Should Change (Foundational, Not Feature Work)

1. Real implementations behind the AI interfaces (see `docs/AI_ARCHITECTURE.md`) — **done as of Phase 2**.
2. Wire the dead use-case layer into its intended call sites, or remove it — currently misleading.
3. Move long-running processing to WorkManager, backed by the already-existing `ProcessingJobEntity` — **done as of Phase 3A** (see §7).
4. Real Google Sign-In behind the already-present Credential Manager dependencies.
5. Real model download/install/delete behind the already-existing `AiModelEntity`/`AiModelDao`.
6. Real audio-track extraction for video import (`MediaExtractor`/`MediaMuxer` — already imported, unused).
