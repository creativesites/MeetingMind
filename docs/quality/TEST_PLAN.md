# MeetingMind — Pre-Launch Test Plan & Verification Strategy

**Target Version:** 1.0-v47  
**Date:** 2026-10-21  
**Scope:** Core Stability, Crash Prevention, Data Integrity, AI Inference, and Offline-First Resilience

---

## 1. Test Architecture & Quality Gates

MeetingMind enforces automated quality gates across multiple tiers:

```
[Roborazzi / UI Golden Tests]  -> Visual & Design System Integrity
         │
[Robolectric Integration]      -> Navigation, Room Migrations, ViewModels, Dialogs
         │
[Pure JVM Unit Tests]          -> Business Logic, Parsers, Tokenizers, State Transitions
         │
[Architectural Guardrails]     -> ColorLiteralGuard, PromptsIntegrity, Resource Locales
```

### 1.1 Memory & Execution Rules
Per `agents/TESTING.md`:
- **Single Test / Area:** `./gradlew --no-daemon -q -Dorg.gradle.jvmargs=-Xmx2g --max-workers=2 testDebugUnitTest --tests '*<Pattern>*'`
- **Full Test Suite:** `./gradlew --no-daemon -q -Dorg.gradle.jvmargs=-Xmx6g --max-workers=4 testDebugUnitTest`
- **Known Gotchas:**
  1. *Paused Looper:* Any wait depending on `Dispatchers.IO` posting back to the main looper will time out. Must call `ShadowLooper.idleMainLooper(50, TimeUnit.MILLISECONDS)`.
  2. *Companion Frame Clock:* Companion's frame clock continuously requests frames. Must wrap with `CompositionLocalProvider(LocalCompanionReducedMotion provides true, LocalCompanionForceCanvas provides true)`.
  3. *Shared DataStore:* `GeminiCredentialStore` uses process-wide DataStore; tests that save keys must clear in teardown.
  4. *Built-in System Key:* Tests testing "no AI configured" must set `GeminiCredentialStore.systemKey = null` in setup and restore in teardown.
  5. *ColorLiteralGuard:* No hardcoded `Color(0x...)` outside `ui/theme/`.

---

## 2. Test Suites by Critical User Journey

### Journey A: Onboarding & First Launch
- **Coverage:** `OnboardingTest`, `SetupViewModelTest`, `AppLanguageTest`, `LocaleResourcesIntegrityTest`.
- **Scenarios:**
  - Fresh launch: Splash -> Continuous Companion Header -> Language selection dialog -> Sample transcript playback -> Completed state.
  - Skip shortcuts: Tapping "Skip to notes" immediately dismisses onboarding and seeds default notes without crashing.
  - Process death: Interrupted onboarding resumes at the last uncompleted step without re-prompting previously granted permissions.
  - Language change: Switching between 9 languages (en, fr, es, pt, sw, ny, sn, zu, b+bem) does not cause activity recreation loops (`shouldApplySavedLanguage` guard).

### Journey B: Audio Recording & Transcription
- **Coverage:** `MeetingRecordingServiceTest`, `AudioRecorderTest`, `NoSpeechRecordingTest`, `CoverageTest`, `TranscriptionRunnerTest`.
- **Scenarios:**
  - Normal flow: Start recording -> Pause -> Resume -> Stop -> Duration & amplitude tracking.
  - Rapid stop: Instant stop (<500ms) handles `MediaRecorder.stop()` failure safely, discarding 0-byte files and preventing corrupt database records.
  - Audio focus interruption: Incoming phone call triggers `AUDIOFOCUS_LOSS_TRANSIENT`. Recording pauses; UI surfaces explicit resume prompt (no silent auto-resume).
  - Background recording: Phone locked or app backgrounded; Foreground service with `microphone` type maintains wake lock and updates notification chronometer.
  - Recovery journal: Crash simulation during active capture; on next app launch `RecordingJournalStore` rescues the `.m4a` file from `recovery.json` and enqueues transcription.

### Journey C: Offline-First AI Pipeline
- **Coverage:** `MeetingProcessingPipelineTest`, `SherpaParakeetSpeechRecognizerTest`, `SherpaWhisperSpeechRecognizerTest`, `SherpaOmnilingualSpeechRecognizerTest`, `SherpaSpeakerDiarizerTest`, `Migration26To27Test`.
- **Scenarios:**
  - Model missing: Pipeline stops at ASR, retains raw audio file intact, and marks meeting `MODEL_REQUIRED` with download action.
  - Diarization timeout: Audio with no detected speaker turns finishes cleanly within time budget (`runDiarizationWithBudget`), marking job COMPLETED without hanging at 52%.
  - Memory handoff: ASR model releases native buffers before LLM initializes to prevent OOM on 4GB devices.
  - Non-destructive translation: Translating transcript generates `TranscriptTranslationEntity` rows in Room v27 without overwriting or mutating original verbatim transcript segments.

### Journey D: Notes, Meetings & Work Management
- **Coverage:** `NoteEditorTest`, `NoteMomentHostTest`, `WorkPageTasksTest`, `ProfessionalHomeTest`.
- **Scenarios:**
  - Note creation & editing: Title, blocks, tags, attachments, and status updates persist immediately across configuration changes.
  - NoteUnveil & FirstKeep moments: Note completion triggers celebratory moment; opening existing note triggers unveil animation without UI flicker.
  - FTS search: Searching terms across notes and transcript segments matches correctly via SQLite FTS4 virtual tables.

### Journey E: Faith & Devotionals
- **Coverage:** `DevotionalTest`, `ClassicsAssetLoadTest`, `FaithWeekTest`, `VerseSheetTest`.
- **Scenarios:**
  - Bundled fallback: Devotional always has content from bundled public-domain classics; never presents an empty state.
  - Exact alarms: Daily devotional reminder schedules via `AlarmManager.setExactAndAllowWhileIdle()` when permission granted, degrading gracefully to standard alarms if denied.
  - Scripture reference lookup: Tapping scripture links navigates to `VerseSheet` and resolves text cleanly offline.

### Journey F: Circles Collaboration (v2)
- **Coverage:** `CirclesRepositoryTest`, `HttpCirclesApiTest`, `HomeCirclesViewModelTest`, `CirclesParsingTest`.
- **Scenarios:**
  - Anonymous auth & profile: Generates UID via Firebase Auth and caches display name in `LocalCircleStore`.
  - Offline resilience: If Cloudflare Worker or network is unavailable, Home Circles tile presents informative status with "Try again" link.
  - Post creation: Supports text, scripture references, and photo attachments with local optimistic update.

---

## 3. Physical Device Testing Checklist

Unit tests with Robolectric provide strong coverage for business logic and Room, but physical hardware verification is required for:
1. **Live Microphone Revocation:** Revoking RECORD_AUDIO in Android Settings while `MeetingRecordingService` is actively capturing.
2. **Foreground Service Notice:** Android 14+ lock-screen notification display and action button taps (Pause, Resume, Stop).
3. **Exact Alarm Delivery in Doze Mode:** Device screen off and battery optimization active at 00:01 devotional delivery time.
4. **Hardware Native ASR Speed & Thermals:** Running Whisper Turbo and Meta Omnilingual INT8 models on real Snapdragon and Exynos chipsets for 30+ minutes.
5. **Google Sign-In Release Keystore SHA-1:** Verifying OAuth token resolution in release builds signed with upload keystore.
