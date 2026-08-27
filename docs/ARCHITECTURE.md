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
│   └── ui/                        — Shared Compose components (DesignSystem primitives, MiniPlayerBar,
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

## 4a. Playback Architecture (Phase 15 §1 audit + fix)

**Why this exists**: the Phase 15 spec reported two P0 bugs — "transcript sync sometimes only works after the 3rd/4th playback" and "tapping a transcript segment while stopped updates the UI but audio starts from the beginning instead of the tapped timestamp." Both trace to the same root cause, audited below.

**Stack**: one `ExoPlayer` + one `MediaSession`, both owned by `PlaybackService` (a bound/foreground `MediaSessionService`). Every screen talks to it through `PlaybackController`, an app-level singleton wrapping a `MediaController`. This part of the design was already correct — the bug was not "multiple players," it was async-IPC ordering inside the singleton.

**Root cause 1 — the seek race**: `MediaController.setMediaItem()` / `prepare()` / `seekTo()` / `play()` are async Binder calls with no synchronous "now ready" return. The old `jumpToTimestamp()` called `playAudio()` (which issues `setMediaItem`+`prepare`+`play`) immediately followed by `seekTo()`. If the controller/session hadn't finished preparing the item yet, the `seekTo()` landed on a player with no item loaded and was silently dropped — playback then started from position 0. Whether this reproduced depended on how fast the session responded on a given call, which is why it looked like a flaky "only works after a few tries" bug rather than a deterministic one.

**Root cause 2 — phase computed from `isPlaying` alone**: `refreshFromPlayer()` derived `PlaybackPhase` from `Player.playbackState` and `isPlaying` without accounting for `playWhenReady`. During a brief mid-playback rebuffer, ExoPlayer reports `STATE_BUFFERING` with `isPlaying = false` even though `playWhenReady = true` (it's still trying to keep playing). The old logic read that as indistinguishable from "nothing has started," which fed back into transcript-sync state as if playback had stopped.

**Root cause 3 — no authoritative segment/speaker resolution**: `MeetingDetailScreen` derived the "active transcript segment" itself, in a `derivedStateOf` calling `findActiveTranscriptSegment(transcript.segments, playbackState.positionMs)` locally. This duplicated logic the spec requires to live in exactly one place (`PlaybackState`), and meant any screen doing its own derivation could drift from another.

**Fix — `PlaybackController.playAt(context, recordingId, title, file, positionMs)`**: the single atomic entry point for "play this recording, optionally starting at a specific position." It stores the target as `pendingSeekMs` and only issues `controller.seekTo(pendingSeekMs)` + `controller.play()` inside `Player.Listener.onPlaybackStateChanged` once `playbackState == Player.STATE_READY` — i.e. the seek is deferred until the player has actually confirmed it has something seekable loaded, eliminating the race rather than papering over it with a delay. `onPlayerError` clears any pending seek so a failed load can't apply a stale one later. `MeetingDetailScreen`'s `jumpToTimestamp()` now does nothing but call `PlaybackController.playAt(...)` — no more manual `seekTo` + conditional `togglePlayPause()` composition at the call site.

**Fix — buffering-aware phase**: `computePlaybackPhase(playbackState, isPlaying, playWhenReady, previousPhase)` is a pure function: `isPlaying == true` always maps to `PLAYING`; `STATE_BUFFERING` with `playWhenReady == true` also maps to `PLAYING` (mid-playback rebuffer, distinct from a fresh load); `STATE_BUFFERING` with `playWhenReady == false` maps to `LOADING`; `STATE_ENDED` always maps to `COMPLETED`; anything unrecognized preserves `previousPhase` rather than guessing. `PlaybackState` gained a separate `isBuffering: Boolean` field so a screen that cares can still show a spinner without that state corrupting `phase`.

**Fix — one authoritative segment/speaker resolution**: `PlaybackController.setSegments(recordingId, segments)` registers the loaded recording's transcript segments once; `refreshFromPlayer()` calls the existing, already-tested `findActiveTranscriptSegment` internally and publishes the result as `PlaybackState.currentSegmentId` / `currentSpeakerId`. `MeetingDetailScreen` now reads `transcript.segments.find { it.id == playbackState.currentSegmentId }` instead of re-deriving from `positionMs` itself.

**Fix — active-segment border spacing**: the left accent rule for the active transcript row was drawn at `x = 50.dp`, which sat inside the ~52dp text-start gutter and visually touched the paragraph text. Moved to `x = 42.dp`, restoring a deliberate ~9-10dp gap between rule and text within the 38dp gutter.

**Testability note**: this project has no mocking library and Media3's `MediaController` cannot be faked, so `computePlaybackPhase`, `isRecordingReadyToResume`, and `shouldLoopBack` were extracted as pure top-level functions in `PlaybackController.kt` taking only primitives — directly unit-testable on plain JVM without Robolectric. See `PlaybackControllerLogicTest.kt` (13 tests) for the regression coverage pinning down both root causes above, including the exact mid-playback-rebuffer case.

**Deferred to Phase 2+ (not fixed here)**: speaker-segmented player visualization (`SpeakerTimelineSegment`, ring rendering) — depends on consolidated speaker colors, which is itself a Phase 2 item (three divergent color sources currently exist: the pipeline's `SPEAKER_COLORS` hex list, hardcoded `color = Accent` in `AskAiPanel.kt` and `OverviewStepper.kt`'s avatar call sites, and `OverviewStepper.kt`'s `WhoTalkedStep`, which is the one correct pattern — it reads persisted `colorHex` with an index-based fallback). Single-speaker recordings currently get no persisted `SpeakerEntity` at all (`MeetingProcessingPipeline` leaves `speakerId` null when `speakerCount == 1`, and the entity-insertion step filters out null-speakerId segments) — also Phase 2.

## 4b. Speaker Identity (Phase 15 §2 audit + fix)

**Root cause 1 — single-speaker recordings had no persisted identity at all**: `MeetingProcessingPipeline`'s single-speaker fast path (§3 of this doc) left every segment's `speakerId`/`speakerName` null "by design," reasoning that ASR itself never assigns one. But the SpeakerEntity-insertion step (`diarizedSegments.filter { it.speakerId != null }`) then silently dropped every segment — a solo recording ended up with zero rows in `speakers`, so the UI's `speaker?.customName ?: "Unlabeled speaker"` fallback fired every time. Fixed: the single-speaker path now assigns a real id (`speaker_0`) and a name that is never guessed from the transcript — `"You"` when `Meeting.source == LOCAL_RECORDING` (the user's own recording), `"Speaker 1"` otherwise (an imported file/video, where "You" would misattribute someone else's voice).

**Root cause 2 — three divergent speaker-color sources**: the pipeline persisted colors from an ad-hoc 6-entry hex list (`#3B82F6, #8B5CF6, ...`) that didn't match the UI theme at all; two Compose call sites (`AskAiPanel`'s citation avatar, `OverviewStepper`'s decision-evidence avatar) hardcoded `color = Accent` for every speaker regardless of identity; and three more places in `MeetingDetailScreen`/`OverviewStepper` each independently re-declared `listOf(Accent, Speaker2, Speaker3, Speaker4)` as a fallback palette. Fixed: `core.model.SpeakerColors` is now the single hex source the pipeline persists from (`#6366F1, #A855F7, #10B981, #F59E0B` — matching `Accent/Speaker2/Speaker3/Speaker4` exactly), and `feature.meetingdetail.components.speakerColorFor(colorHex, fallbackIndex)` is the single Compose-side resolver every avatar call site now goes through — real persisted color first, index-based palette only as a fallback for an identity not yet reflected in `speakers`. See `SpeakerColorsTest` for the pinned hex values that keep the two in sync.

**Speaker management**: rename (`TranscriptRepository.renameSpeaker`), per-segment/per-run reassignment including on-the-fly speaker creation (`reassignSpeaker`), and undo/redo for both were already implemented from an earlier phase and needed no changes. Added in this phase: `TranscriptRepository.mergeSpeakers(meetingId, sourceId, targetId)` — collapses a diarization-split identity into another by moving every segment and deleting the now-empty source `SpeakerEntity` row, exposed through the existing rename dialog ("Or merge into an existing speaker…") rather than a new sheet, since the dialog already had everything the merge flow needs. Not undoable; the UI requires an explicit tap on the target speaker's name to confirm.

**Deferred to a later phase**: speaker-segmented player visualization (`SpeakerTimelineSegment`, ring rendering split by speaker turn) — this now has a correct, consolidated color source to build on, but the rendering itself is a separate UI feature not built in this pass.

## 4c. Transcript Editor (Phase 15 §3 audit + fix)

**Audit finding — most of §3's requirements were already built** in earlier phases and needed no new code: per-segment text editing with autosave-on-blur (`editSegmentText`), split/merge at cursor offset, speaker reassignment including on-the-fly speaker creation, document-level undo/redo (`EditAction` sealed interface + two stacks), and find/next/prev search. Selection, cut, copy, and paste within a focused segment are also already real — `TranscriptTab`'s editable segment is a plain Compose `BasicTextField`, which gets the OS's native text-selection toolbar (select, cut, copy, paste) for free; there was nothing to build there, only to confirm.

**What was actually missing — Replace/Replace All**: search could locate matches but not act on them. Added `TranscriptRepository.replaceAllInTranscript(meetingId, searchText, replaceText)` — a case-insensitive, literal (non-regex) replace across every segment in one pass, returning exactly the segments that changed so the caller can build one atomic undo entry (`EditAction.ReplaceAll`) rather than one undo step per segment. Wired into `TranscriptTab`'s existing search panel as a "Replace" toggle next to Prev/Next, rather than a separate mode.

**Deferred, not built this pass**: multi-segment selection/highlighting spanning paragraph boundaries (the current selection model is native-per-segment, which is what `BasicTextField` gives for free — a custom cross-segment selection UI is a materially larger feature, not a fix to something broken); AI actions on a text selection — `FloatingSelectionBar` already exists as an honest stub ("Fix errors" / "Clarity" / "Condense" show a "coming in a later phase" toast rather than fake output) and stays a stub until Phase 5/6 build the AI job infrastructure and real rewrite tools it needs to call into.

## 4d. Options Menu + Confirmation-Screen Insets Audit (Phase 15 §9)

**Audit finding — "confirmation screen insets" was already fixed, and there is no separate confirmation screen.** This codebase has no dedicated post-recording save/rename screen or import-confirmation screen — the nav graph goes `RECORDING → PROCESSING → MEETING_DETAIL` directly, and `MeetingDetailScreen` doubles as what the spec calls the "confirmation screen." Its inset bug (missing `statusBarsPadding()`/`navigationBarsPadding()` on the top/bottom bars) was already found and fixed earlier in this same phase (commit `6330733`, referenced from §4a/AI_ARCHITECTURE §15's `AskAiPanel` write-up as "the same class of inset bug fixed earlier on the recording confirmation screen"). `ImportScreen` and `ProcessingScreen` were checked as other candidates for the same bug class and both already handle insets correctly via `Scaffold`'s `innerPadding`. Nothing left to fix here.

**"Options bottom sheet" — real gap, now fixed.** The recording-detail top bar's "⋮" menu (8 actions: Edit Title, Re-clean Transcript, Copy Markdown Summary, Share Summary/Transcript/Action Items/Audio, Export…) was a bare Material3 `DropdownMenu` — a small anchored popup with no visual relationship to the rest of this screen's redesign, and the only "options" surface in the app still built that way (`AiToolsSheet`, the screen's other options surface, is already a `ModalBottomSheet`). Replaced with a new `MeetingOptionsSheet` composable (`feature/meetingdetail/components/MeetingOptionsSheet.kt`) using the exact same shape (`RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)`), `containerColor`, and `Ink`/`InkMuted`/`Accent` token palette as `AiToolsSheet`, so the two sheets on this screen now read as one design language instead of "sheet" next to "generic Android menu." All 8 actions and their existing enabled/disabled conditions (Re-clean disabled while reprocessing, Share Audio disabled with no audio file) carried over unchanged — this is a container swap, not a behavior change.

**Home screen's meeting-card "⋮" menu** (single "Delete Recording" item) was also audited and left as a `DropdownMenu` — it's already `MaterialTheme.colorScheme`-driven and a single-item confirm-style menu is a reasonable fit for a small anchored popup; converting it to a full-height sheet for one destructive action would be a regression in interaction cost, not an improvement, so it was deliberately left alone.

## 4e. Recording Capture Architecture (Phase 15 §Part 2 §3 — RecordingState + service ownership)

**Why this exists**: the capture-pipeline design spec (`design/capture-pipeline-implementation.md` §3) requires the recorder to be owned by the service, not the Activity/ViewModel, so a recording survives the screen being backgrounded or the process trimming the UI layer — and requires a live-capture lifecycle (`RecordingState`) kept deliberately separate from the persisted `MeetingStatus`. Neither was true before this phase.

**Audit finding — a real, previously-undetected bug**: the old `MeetingRecordingService` was a plain `startService()`-only service; `AudioRecorder` was constructed and owned by `RecordingViewModel` instead. The lock-screen notification's Pause/Resume action buttons sent `ACTION_PAUSE`/`ACTION_RESUME` intents into the service, but the service held no reference to the recorder that could act on them — `onStartCommand` only flipped a display-only flag used to redraw the notification text. Tapping Pause from the lock screen changed the notification's icon and label but never actually paused the microphone. This was found auditing the spec's "service owns the recorder" requirement, not reported by the user, and is fixed as a byproduct of the rewrite below.

**`RecordingState` vs `MeetingStatus`**: `core/audio/RecordingState.kt` is a new 8-value enum (`IDLE, PREPARING, RECORDING, PAUSED, STOPPING, SAVING, SAVED, FAILED`) describing only what the service is doing to the microphone/file right now; it stops existing once capture ends. `MeetingStatus` (`core/model/Enums.kt`) remains the persisted lifecycle of the `Meeting` row in Room, and gained two new values this phase — `SAVED` and `QUEUED` — for the deferred-processing flow Stage 4 will wire up. Only `RECORDING → SAVED` is reachable from the capture path; nothing in `RecordingState` ever implies `MeetingStatus.PROCESSING`. Confirmed via `--rerun` compile that `MeetingStatus` is never consumed as an exhaustive `when` expression anywhere in the codebase (all call sites use `==` comparisons or `else`-guarded `when` statements), so adding the two values required no call-site changes; also confirmed `MeetingEntity.status` is a plain unconstrained `TEXT` column, so no Room migration was needed for this despite the design spec's own note suggesting one.

**`MeetingRecordingService` is now a bound service**: it constructs and owns `AudioRecorder` and `MeetingRepository` itself in `onCreate()`, and exposes `state: StateFlow<RecordingState>`, `amplitude`, `durationMs`, and `focusInterrupted` StateFlows via a same-process `LocalBinder`/`onBind()` (no IPC — `RecordingViewModel` binds and reads the flows directly). `startRecording(meetingId, title, context)`, `pauseRecording()`, `resumeRecording()`, `discardRecording()`, and `stopRecording(onSaved)` are real bound methods now, not just intent handlers. `stopRecording` persists the finished `Meeting` row (via `meetingRepository.createInitialMeeting(...)`) from inside the service itself, on a `CoroutineScope(SupervisorJob() + Dispatchers.Main)`, before invoking the caller's `onSaved` callback and tearing the service down — per the spec's "data loss is existential" principle, the row is written by the component guaranteed to be alive when the recording stops, not one that depends on the Activity/ViewModel still being around. `ACTION_PAUSE`/`ACTION_RESUME`/`ACTION_STOP` (still needed because the lock-screen notification's buttons can only send intents, not call a bound method) now call these same real methods, fixing the audit finding above.

**Real audio-focus handling**: `AudioManager.OnAudioFocusChangeListener`, requested via `AudioFocusRequest` on API 26+ and the legacy `requestAudioFocus(listener, streamType, hint)` below it (matching this codebase's existing SDK-branching convention). A transient loss (e.g. an incoming call) auto-pauses the recording and sets `focusInterrupted = true`; per spec, the service never auto-resumes — the user must resume explicitly once focus is available again.

**`RecordingViewModel` rewritten to a thin binder**: it no longer constructs `AudioRecorder` itself. It binds to `MeetingRecordingService` via `ServiceConnection`/`bindService(..., BIND_AUTO_CREATE)`, and relays the service's `state`/`amplitude`/`durationMs`/`focusInterrupted` through `_boundService.flatMapLatest { it?.x ?: flowOf(default) }.stateIn(viewModelScope, SharingStarted.Eagerly, default)` — giving the UI sane pre-bind defaults with no separate "waiting to bind" UI state needed. `startRecording()` binds, then suspends on `_boundService.filterNotNull().first()` before calling into the now-guaranteed-bound service. `onCleared()` unbinds.

**Testability note**: `MeetingRecordingServiceTest.kt` (new) exercises the bound-service wiring — `Robolectric.buildService(...).create()` for initial `RecordingState.IDLE`, and `onBind()` returning a `LocalBinder` pointing at the same service instance — without touching real `MediaRecorder`. Starting/pausing/resuming/stopping an actual recording needs a live microphone and is not exercised here or anywhere in this suite; that gap is called out explicitly rather than hidden, same as the rest of this codebase's audio-path tests.

**Deferred to Stage 2's second half / later stages**: the crash-recovery journal (§3.7 of the spec — detecting and offering to resume a recording that was in flight when the process died) and pre-flight/in-flight storage + battery warnings (§3.8) are not built in this pass; markers (§3.4) and quick notes (§3.5) as extraction-hint capture data are Stage 3 (`#7a`/`#7b` screens), not this one; deferred processing via `MeetingStatus.SAVED`/`QUEUED` is scaffolded (the enum values exist) but not yet wired in — `stopRecording` still persists the row as `MeetingStatus.RECORDING` (via the pre-existing `createInitialMeeting`, unchanged by this phase) and the caller still navigates straight to `ProcessingScreen`, which drives it to `PROCESSING` immediately; nothing yet lands on `SAVED`/`QUEUED` for a user-chosen "process later" path — that's Stage 4.

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
