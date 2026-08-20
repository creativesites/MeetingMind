# MeetMind — Repository Audit

**Date:** 2026-08-19 (original audit); **updated 2026-08-20** with a P0 privacy/architecture reconciliation pass — see the update note immediately below.
**Scope:** Full inspection of the codebase delivered as a Google AI Studio ("Gemini")-generated Android project, imported into this repository as the starting point for the MeetMind MVP. This document reports only verified findings — every claim below was confirmed by reading the actual source, running the build, or both.

---

## Update — 2026-08-20 P0 Privacy Reconciliation

Everything in sections A–J below reflects the state of the repository **as originally audited on 2026-08-19**, before any fixes. It is kept intact as the historical record. Since then, a dedicated pass removed the cloud AI path and every fake/fabricated AI implementation identified below, and replaced them with an honest, privacy-safe, model-ready architecture (no real local models were integrated yet — that remains future work). Concretely:

- `GeminiApiClient` was deleted; no code path uploads audio or transcripts anywhere. Verified by `PrivacyNoCloudPathTest` (reflection-based: the class must not exist; the pipeline's default AI implementations must be the local `Unavailable*` classes).
- The `sin(time)` VAD, placeholder ASR text, turn-alternation "diarization," and regex-based "intelligence" were all removed. Every AI interface now returns `AiResult<T>` and defaults to an `Unavailable*` implementation that honestly reports `ModelUnavailable` instead of fabricating output. Covered by `AiAvailabilityTest`.
- `MeetingProcessingPipeline` now stops honestly (new `MeetingStatus.MODEL_REQUIRED`, audio preserved, no transcript persisted) when no ASR model is installed, instead of writing fake segments. Covered by `MeetingProcessingPipelineTest`.
- Model management (`ai/modelmanagement/`) is now a real architecture — real file storage, real SHA-256 verification, a static catalog with `downloadUrl/sha256 = null` (no invented URLs) — wired to `AiModelEntity`/`AiModelDao`. The only unimplemented piece is `ModelDownloader` itself, which honestly fails since no production model source has been selected yet. Covered by `ModelRepositoryTest`.
- The hardcoded "Winston Chen" mock sign-in is gone. Real Google Sign-In (Credential Manager → Firebase Auth) is implemented; when unconfigured, it reports `AuthAvailability.NotConfigured` rather than fabricating a signed-in user. Covered by `FirebaseAuthManagerTest`.
- `GEMINI_API_KEY` and the secrets-gradle-plugin/`.env` machinery were removed — the app requires no API key or cloud configuration to build or run.

See `docs/AI_ARCHITECTURE.md` §0 for the full technical breakdown and `docs/ROADMAP.md` for what's next (integrating real local VAD/ASR/diarization/LLM models is still entirely outstanding). The MVP completion estimate in §A below (~30–35%) predates this pass; the foundation and privacy posture are now sound, but no new AI *capability* was added — that estimate should not be revised upward until real local models are integrated.

---

## A. Executive Summary

**Estimated MVP completion: ~30–35%**

The project is a real, compiling Android/Compose application with a genuinely solid recording pipeline, a well-designed Room database, and a full set of wired-up screens. However, almost everything downstream of "record audio" — VAD, ASR, diarization, the LLM, embeddings, and AI model management — is either a **cloud call to Google's public Gemini API** or a **synthetic/fake local stand-in**, not the on-device AI pipeline the product spec requires. The app also actively **contradicts its own privacy promise**: onboarding and settings screens tell the user "Your recordings and transcripts never leave this phone," while the transcription/LLM code path uploads raw audio and full transcripts to `generativelanguage.googleapis.com` whenever an API key is present.

| Area | Maturity |
|---|---|
| Core recording (mic, pause/resume, foreground service) | **Real, solid** |
| Audio import (audio files) | **Real** |
| Audio import (video → audio extraction) | **Fake** (file is copied, not demuxed) |
| Database / persistence layer | **Real, well-designed** |
| UI/UX (screens, navigation, state) | **Real, wired to ViewModels** |
| VAD | **100% fake** (sine-wave function of time, never reads audio) |
| Local ASR | **Missing** (no model, no runtime; cloud Gemini or fabricated text) |
| Diarization | **Fake heuristic** (alternates speaker labels by turn count) |
| Local LLM | **Missing** (no model, no runtime; cloud Gemini or regex/keyword rules) |
| Embeddings | **Real but primitive** (hash-based bag-of-words, not a trained model) |
| Ask Meeting (RAG) | **Not RAG** — full-transcript cloud call, or keyword-quote fallback |
| Model management (download/install) | **100% fake** (`delay()` calls, no network, no files) |
| Firebase Auth | **Wired to real SDK but UI never uses it** — "Sign In" sets a hardcoded mock user |
| Privacy compliance | **Violated** — audio/transcript leave the device via the Gemini path |
| Build system | **Was broken** (no Gradle wrapper, wrong AGP/Gradle pairing) — **fixed in this pass** |

The good news: the parts that are real (recording, storage, database, navigation, UI state management) are competently built and form a legitimate foundation. The AI layer is correctly abstracted behind interfaces (`SpeechRecognizer`, `SpeakerDiarizer`, `EmbeddingEngine`, `MeetingIntelligenceEngine`, `VoiceActivityDetector`), which is exactly the shape the target architecture requires — the interfaces just need real local implementations behind them instead of the current placeholders.

---

## B. What Already Works (Verified)

- **Local microphone recording** (`AudioRecorder`, `core/audio/AudioRecorder.kt`): real `MediaRecorder` session (AAC, 16kHz, 128kbps) writing to app-private storage (`filesDir/meetings/{id}/audio.m4a`). Pause/resume backed by real `MediaRecorder.pause()/resume()` (API 24+), live amplitude read from `maxAmplitude`, running duration tracked correctly across pause segments.
- **Foreground recording service** (`MeetingRecordingService.kt`): real `Service` with `FOREGROUND_SERVICE_TYPE_MICROPHONE`, notification channel, partial wake lock (4h safety cap) so recording continues with the screen off, notification actions for pause/resume/stop.
- **Audio import (audio files)** (`AudioExtractor.kt`): real Storage Access Framework flow — queries `ContentResolver` for name/size, copies the source stream to app-private storage, reads duration via `MediaMetadataRetriever`.
- **Playback** (`AudioPlayerManager.kt`): real `MediaPlayer`-backed play/pause/seek with a progress-polling `StateFlow`.
- **Room database** (`core/database/*`): 11 well-normalized entities (meetings, transcript segments, speakers, action items, decisions, questions, topics, embeddings, AI models, processing jobs, chat messages) with correct `ForeignKey(CASCADE)` relationships and sensible indices, including a text index to support keyword search.
- **Navigation & screen wiring**: every screen in `feature/*` is backed by a real `AndroidViewModel` reading/writing Room via `Flow`/`StateFlow`, not static mock data. Onboarding → Home → Record/Import → Processing → Meeting Detail → Search/Settings/Models all navigate and pass real arguments (meeting id, audio path, duration).
- **DataStore preferences** (`UserPreferencesManager`): real, comprehensive (onboarding flag, model selection, battery saver, Wi-Fi-only downloads, sample rate, theme).
- **Device capability detection** (`DeviceCapabilityDetector`): real RAM/ABI/ARM64 detection used to recommend a model tier on the onboarding screen.
- **Keyword search**: real SQL `LIKE` search over transcript segments (`TranscriptDao.searchTranscriptSegments`).
- **Cloud transcription/intelligence (when a Gemini API key is configured)**: `GeminiApiClient` does perform real, non-fabricated audio transcription, summarization, and Q&A via the public Gemini API — this genuinely works, but it is cloud-based, which conflicts with the stated local-first requirement (see section D and section Q on privacy).
- **The build itself** (after this session's fixes — see section E): `./gradlew assembleDebug`, `testDebugUnitTest`, and `lintDebug` all pass. Kotlin compiles with zero errors (5 minor deprecation warnings). Lint reports 0 errors / 50 warnings (mostly stale dependency-version notices) / 1 hint.

---

## C. What Is Partially Implemented

- **Meeting Intelligence pipeline** (`MeetingProcessingPipeline`): correctly orchestrates VAD → ASR → diarization → summarization → embedding → persistence and writes every result into Room. The orchestration is real; the quality of what flows through it depends entirely on which backend answered (see section D).
- **Search**: keyword search works; "semantic" search is real code but built on a non-semantic hash embedding (see AI Assessment).
- **Ask Meeting**: message history is persisted correctly, and the UI/ViewModel wiring is real. But when offline (no Gemini key), the "answer" is just the single best keyword-matching transcript segment wrapped in a template sentence — not an LLM-synthesized, retrieval-grounded answer.
- **Domain/use-case layer** (`core/domain/UseCases.kt`): defines `StartRecordingUseCase`, `ImportRecordingUseCase`, `TranscribeMeetingUseCase`, `DeleteMeetingUseCase`, and `DownloadModelUseCase`, but **none of these five are actually called anywhere in the app** — `RecordingViewModel`, `ImportViewModel`, `ProcessingViewModel`, and `ModelManagerViewModel` all duplicate the same logic directly against the repositories instead. Only `SearchMeetingsUseCase` and `AskMeetingUseCase` are actually wired up. This is dead code masquerading as an architecture layer.
- **Authentication**: `FirebaseAuthManager` is wired to the real `FirebaseAuth` SDK with a correct null-safe fallback for local-only use, but no screen ever calls a real sign-in method (see section D).
- **Video import**: a file is copied and a meeting is created, so the *flow* completes, but no audio is actually extracted from the video container (see section D).

---

## D. What Is Fake / Placeholder — Be Brutally Honest

This is the most important section of the audit. Ranked roughly by product impact.

### 1. Voice Activity Detection — **100% fabricated, never reads the audio file**
`app/src/main/java/com/example/ai/vad/VoiceActivityDetector.kt`, class `EnergyAndSpectralVad`.
```kotlin
val energyScore = (0.35f + 0.45f * kotlin.math.sin(progressRatio * 6.28f).toFloat() + (i % 3) * 0.1f)
```
This computes a sine wave as a function of *elapsed time*, not the audio samples. `audioFile` is passed in but never opened, decoded, or read. Every recording of the same length produces the identical, deterministic "speech interval" pattern regardless of what was actually said or whether the room was silent.
**Should do:** decode PCM (via `MediaExtractor`/`MediaCodec` or a native decoder), compute real short-time energy/spectral features, and apply a real VAD algorithm (e.g., Silero VAD via ONNX Runtime Mobile, or WebRTC VAD).
**Difficulty to replace:** Medium — requires an on-device audio decode step that doesn't exist yet anywhere in the app, plus a VAD model or algorithm.

### 2. Audio Preprocessing — same fabrication pattern
`core/audio/AudioPreprocessor.kt`. `analyzeAndSegment()` computes `rmsEnergy` as `sin(ratio * π)` — again, a function of chunk position in time, not of any audio sample. Never reads file bytes beyond checking `file.length()`.

### 3. Local Speech Recognition — no local model exists at all
`app/src/main/java/com/example/ai/asr/SpeechRecognizer.kt`, class `RealSpeechRecognizer` (the name is misleading — nothing about the local path is a real recognizer).
- **Primary path**: uploads the entire recorded audio file, base64-encoded, to `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent` via raw `OkHttp` (see `GeminiApiClient.kt`). This is real transcription, but it is a **cloud API call**, not local inference, and the audio leaves the device.
- **Fallback path** (no API key, or Gemini call fails): produces segments with text like `"Voice audio segment (12s)"` — literally a placeholder string, not a transcription of anything that was said.
- There is no bundled or downloadable ASR model anywhere in the repository: no `.tflite`, `.onnx`, `.gguf`, or `.bin` model file, no `whisper.cpp`/ONNX Runtime/MediaPipe dependency in `build.gradle.kts`, no native (`.so`)/NDK code. The `modelId = "whisper_tiny"` string flows through the pipeline but is **never used to load or run anything** — it's inert metadata.
- `THIRD_PARTY_NOTICES.md` in the repo root claims the app "incorporates" Whisper, Silero VAD, and SmolLM/Qwen/Gemma — **none of these are actually present in the code**. This is aspirational documentation, not a description of what ships.
**Difficulty to replace:** High — requires bundling a real quantized Whisper (or similar) model, an inference runtime (whisper.cpp via NDK, or ONNX Runtime Mobile), and a real audio decode step.

### 4. Speaker Diarization — heuristic label alternation, not diarization
`ai/diarization/SpeakerDiarizer.kt`, class `AcousticClusterDiarizer` (name is misleading — there is no acoustic clustering).
```kotlin
if (gap > 1200L || (i % 2 == 0)) {
    currentSpeakerNum = (currentSpeakerNum % maxSpeakers) + 1
}
```
Speakers are assigned by turn index and pause length, not by any voice characteristic. A single person talking continuously with natural pauses will be split into "Speaker 1"/"Speaker 2" alternately. No embeddings, no clustering, no voiceprint.
**Difficulty to replace:** High — real diarization needs speaker embeddings (e.g., a small d-vector/x-vector model) plus a clustering step.

### 5. Local LLM — no model, no runtime; "intelligence" is either cloud or regex
`ai/llm/MeetingIntelligenceEngine.kt`, class `RealMeetingIntelligenceEngine`.
- **Primary path**: cloud call to `gemini-3.5-flash` for summary/decisions/action items/questions and for Ask-Meeting answers.
- **Fallback path** (offline): action items are "extracted" by checking if a segment's text contains any of `"will", "should", "need to", "action", "task", "follow up", "send", "review", "schedule"`; decisions by checking for `"agreed", "decided", "approved", ...`; questions by checking for a literal `"?"` character. Topics are the 3 most frequent words longer than 4 characters that aren't in a stop-word list. This is keyword pattern matching, not machine learning, and should not be described as "local AI" or "local LLM" — it is a rules engine.
- No local LLM runtime exists anywhere (no llama.cpp, no MediaPipe LLM Inference API, no ONNX/GGUF LLM file). The "Mobile Intelligence 1B" entry in the model list (see #6 below) is never loaded or run by anything.
**Difficulty to replace:** High — requires bundling a small quantized instruction-tuned LLM (1–3B params) and an on-device inference runtime capable of running on a phone within a few seconds/segment.

### 6. Model Management — entirely simulated, disconnected from the database
`core/repository/Repositories.kt`, class `ModelRepository`.
- The five "available models" (Whisper Tiny/Base/Small, "Mobile Intelligence 1B", "Semantic Vector Embedding 64D") are a **hardcoded in-memory list** with fabricated sizes, RAM requirements, and download URLs pointing at `https://models.meetmind.internal/...` — a domain that does not exist.
- `toggleInstall()` performs no network request and writes no file to disk. It "downloads" a model like this:
```kotlin
current[index] = item.copy(isDownloading = true, downloadProgress = 0.3f)
_models.value = current.toList()
kotlinx.coroutines.delay(400)
current[index] = item.copy(isDownloading = true, downloadProgress = 0.75f)
_models.value = current.toList()
kotlinx.coroutines.delay(300)
current[index] = item.copy(isInstalled = true, isDownloading = false, downloadProgress = 1.0f)
```
Two `delay()` calls and some boolean flips — there is no downloaded file, no checksum, no persistence (state resets on process death since it lives only in a `MutableStateFlow`, not Room).
- Room already defines `AiModelEntity`/`AiModelDao` for exactly this purpose, but `ModelRepository` never touches them. The schema and the actual repository are completely disconnected — evidence that this feature was scaffolded but never finished.
**Difficulty to replace:** Medium — needs a real downloader (WorkManager + `DownloadManager`/OkHttp streaming), checksum verification, and wiring to the existing `AiModelEntity` table (which is already correctly designed).

### 7. Video "import" does not extract audio
`core/audio/AudioExtractor.kt` imports `android.media.MediaExtractor` and `android.media.MediaFormat` — **but never calls them**. For a video file, `importAndExtract()` just copies the raw byte stream into a file misleadingly named `audio.<ext>`. No demuxing, no audio-track extraction, no transcoding. It happens to often still "work" downstream only because the Gemini cloud API and `MediaPlayer`/`MediaMetadataRetriever` can tolerate a video container passed to them, not because the app performed the extraction it claims to.
**Difficulty to replace:** Low–Medium — `MediaExtractor` + `MediaMuxer` to select the audio track and write it to a clean `.m4a` is a well-trodden, self-contained fix.

### 8. Authentication — "Sign In" sets a hardcoded fake user, not a real Firebase/Google flow
`feature/settings/SettingsScreen.kt`:
```kotlin
fun signInLocalUser() {
    authManager.setLocalMockProfile("Winston Chen", "winston.chen@meetmind.local")
}
```
This is the entire "Sign In" implementation — a hardcoded name and email, no Firebase Auth call, no Google Sign-In, no Credential Manager. This despite `androidx.credentials`, `androidx.credentials-play-services-auth`, and `com.google.android.libraries.identity.googleid` all being declared as dependencies and unused everywhere in the codebase. `FirebaseAuthManager` itself correctly wraps a real `FirebaseAuth` instance with a safe fallback, but nothing ever calls its real sign-in surface — there isn't one to call.
**Difficulty to replace:** Low–Medium — the dependencies are already present; this needs a `CredentialManager`/`GetGoogleIdOption` flow and a call to `FirebaseAuth.signInWithCredential`.

### 9. Firestore metadata sync — correctly scoped, but entirely dead code
`core/firebase/FirebaseManagers.kt`, class `FirestoreSyncManager.syncMeetingMetadata()` — correctly limited to lightweight metadata (title, timestamps, duration, participant count, source) with no audio/transcript content, which is exactly right per the privacy principle. However, it is **never called from anywhere in the codebase**. It is scaffolding, not a working feature.

### 10. Onboarding/Settings privacy claims are false given the actual code path
Onboarding step 0 states: *"Your recordings and transcripts never leave this phone... No Cloud Uploads... No API Keys Required."* Settings states: *"Audio recordings and full transcripts never leave your device. All ASR transcription, diarization, summarization, and vector search execute on your local CPU."* Both are contradicted by `GeminiApiClient`, which — whenever a `GEMINI_API_KEY` is configured (the default expectation when built through AI Studio's own secrets flow) — uploads the full audio recording and, separately, the full transcript text to Google's public Gemini API for transcription, summarization, and Q&A. This is not a subtle technicality: it is the app's primary, first-choice code path in three of the five AI subsystems. **This must be fixed before any real user relies on the stated privacy guarantee** — either by removing the cloud path for the local-first MVP, or by making it explicit, opt-in, and clearly disclosed if kept as an interim bridge to real on-device models.

---

## E. What Is Broken (Build/Runtime Issues)

The project **did not build as delivered**. Findings, in the order they were hit and fixed during this audit:

1. **No Gradle wrapper was present in the repository at all** (`gradlew`, `gradlew.bat`, `gradle/wrapper/*` were all missing). A project intended to be opened directly in Android Studio must ship a wrapper; without one, the build depends entirely on whatever Gradle happens to be installed on the machine, which is not reproducible.
2. **AGP/Gradle version mismatch**: `gradle/libs.versions.toml` pins `agp = "9.1.1"`, which requires **Gradle ≥ 9.3.1**. The only Gradle available in this environment was 8.14.3, which fails immediately with `Minimum supported Gradle version is 9.3.1`.
   - **Fix applied**: generated a proper Gradle wrapper pinned to `gradle-9.3.1-bin.zip` (`gradle wrapper --gradle-version 9.3.1 --distribution-type bin`) and committed it. `./gradlew` now self-bootstraps the correct Gradle version on any machine, exactly as intended.
3. **`debugConfig` signing config references a keystore file that does not exist in the repo** (`app/build.gradle.kts`): `storeFile = file("${rootDir}/debug.keystore")`. The file is correctly gitignored (it must never be committed), but nothing generates it, so a fresh clone fails at `validateSigningDebug` with `Keystore file '.../debug.keystore' not found`. The project's own `README.md` tells the developer to *remove* this line before running locally — i.e., the shipped configuration is known by its own authors to not work out of the box.
   - **Fix applied for verification only**: generated a local, gitignored debug keystore with the standard Android debug credentials (`storepass android`, alias `androiddebugkey`) to unblock the build in this environment. **Recommendation**: switch `debug { }` to use AGP's built-in default debug signing config (remove the custom `debugConfig` block and `signingConfig` override entirely) so a fresh clone builds without any manual step, which is standard practice and avoids this whole class of problem.
4. **No Android SDK is installed in this sandbox environment** by default; `ANDROID_HOME`/`local.properties` were absent. A minimal SDK (`platform-tools`, `platforms;android-36`, `build-tools;36.0.0`) was installed for this audit to allow a real build/lint/test run; this is an environment limitation, not a project defect, but it does mean a first-time contributor also needs the SDK installed (normal for Android, just noting it was not preconfigured here).

**After the above fixes:**
- `./gradlew assembleDebug` → **BUILD SUCCESSFUL**. Kotlin compiles with 0 errors, 5 deprecation warnings (`stopForeground(Boolean)`, `fallbackToDestructiveMigration()`, three deprecated Material icon variants — all trivial, non-functional).
- `./gradlew testDebugUnitTest` → **BUILD SUCCESSFUL**. (Caveat: the only tests present are the default Android Studio boilerplate — `ExampleUnitTest`, `ExampleRobolectricTest`, `GreetingScreenshotTest`, `ExampleInstrumentedTest` — none exercise any of this app's actual logic. See Roadmap.)
- `./gradlew lintDebug` → **BUILD SUCCESSFUL**, 0 errors / 50 warnings / 1 hint. Warnings are almost entirely stale-dependency-version notices (`GradleDependency`, `NewerVersionAvailable`) and a handful of unused drawable resources — nothing correctness- or security-related.
- No emulator/device was available in this environment, so the app could not be installed and manually exercised end-to-end. Everything reported above the "Build" line in this document was verified by reading the actual source, not by running the UI. Anything that can only be confirmed by manual interaction (gesture feel, exact lifecycle behavior across process death, real-device battery/thermal behavior) is marked **UNKNOWN** below rather than asserted.

---

## F. Architecture Assessment

**What's good and should remain:**
- The AI layer is already interface-first (`SpeechRecognizer`, `SpeakerDiarizer`, `EmbeddingEngine`, `MeetingIntelligenceEngine`, `VoiceActivityDetector`), exactly matching the target architecture requirement. Swapping the current placeholder/cloud implementations for real local ones is a matter of writing a new class behind each interface, not a rearchitecture.
- `MeetingSource` (`LOCAL_RECORDING`, `IMPORTED_AUDIO`, `IMPORTED_VIDEO`, `REMOTE_BOT`) and the `core/future/FutureMeetingInterfaces.kt` scaffolding (`MeetingProvider`, `CalendarProvider`, `RemoteMeeting`, `BotSession`) are a clean, inert placeholder for Phase 2 — they add no runtime cost today and correctly avoid implementing anything.
- Package layering (`ai/`, `core/{audio,common,database,datastore,domain,firebase,future,model,repository,ui}`, `feature/*`, `ui/theme`) is sensible and consistently followed for what it covers.
- The Room schema is genuinely well-designed and already anticipates features not yet wired up (`AiModelEntity`, `ProcessingJobEntity`) — a sign the intended architecture is sound even where the implementation lagged behind it.

**What should be refactored:**
- **No dependency injection.** Every `AndroidViewModel` manually constructs `MeetMindDatabase.getInstance(...)`, `MeetingRepository(...)`, `FirebaseAuthManager(...)`, etc., in its own `init`. This works at the current size but will not scale past a handful more screens, and it's already produced the next problem:
- **The use-case layer is half dead code.** `core/domain/UseCases.kt` defines 7 use cases; only 2 are actually called. Either wire the other 5 into the ViewModels that duplicate their logic, or delete them — right now they're misleading, since a reader would reasonably assume they represent the real call path.
- **The DB/repository disconnect** seen in `ModelRepository` (ignores `AiModelEntity`/`AiModelDao` entirely) and `ProcessingViewModel`/`MeetingProcessingPipeline` (writes `ProcessingJobEntity` but nothing ever resumes an interrupted job from it) suggests the schema was designed for a more complete implementation than what currently reads/writes it. Close this gap rather than removing the schema — the schema is correct.
- `GeminiApiClient` talks directly to the public REST endpoint with OkHttp + hand-rolled JSON, while `firebase-ai` (Firebase AI Logic SDK) is already a declared dependency and unused. If any cloud LLM bridge is kept intentionally during the transition to local models, it should go through Firebase AI Logic (which supports App Check attestation and does not require embedding a raw API key in the client), not a hand-rolled call with a client-side key.

**What will cause problems later:**
- `MeetMindDatabase` has `version = 1` with `fallbackToDestructiveMigration()` and `exportSchema = false`. Fine pre-release; must be replaced with real migrations and `exportSchema = true` (with schemas checked into version control) before the first real release, or every future schema change will silently wipe user data.
- Processing runs in a `viewModelScope` coroutine tied to the Compose screen's lifecycle, not a `WorkManager` job. A long transcription/summarization job is lost if the user backgrounds the app long enough for the process to be killed, despite `ProcessingJobEntity` existing specifically to make jobs resumable. This is a real reliability gap once real (slower) local ASR/LLM inference replaces the current fast fallback paths.
- Hardcoded package identity from the AI-Studio template (`namespace = "com.example"`, `applicationId = "com.aistudio.meetmind.qxynvp"`) should be renamed to a real, owned namespace before any release build or Play Store listing — cosmetic today, but easy to forget later.

---

## G. AI Assessment

| Subsystem | Runtime | Model | Where stored | Loaded how | Inference real? | Offline? | ARM64? | RAM | Missing-model behavior | Inference-failure behavior |
|---|---|---|---|---|---|---|---|---|---|---|
| VAD | None | None | N/A | N/A | **No** — sine wave over time | Yes (trivially, does nothing real) | N/A | ~0 | N/A | N/A |
| ASR (cloud path) | OkHttp → Gemini REST API | `gemini-2.5-flash` (Google-hosted) | Cloud | N/A | **Yes** | **No** — requires network + API key | N/A | N/A | N/A | Falls back to fabricated placeholder text |
| ASR (local fallback) | None | None | N/A | N/A | **No** — placeholder strings | Yes | N/A | ~0 | Always "missing" | N/A |
| Diarization | Kotlin heuristic | None | N/A | N/A | **No** — turn-alternation rule | Yes | Yes | ~0 | N/A | N/A |
| LLM (cloud path) | OkHttp → Gemini REST API | `gemini-3.5-flash` (Google-hosted) †| Cloud | N/A | **Yes** | **No** | N/A | N/A | N/A | Falls back to keyword-rule engine |
| LLM (local fallback) | Kotlin regex/keyword rules | None (rules, not ML) | N/A | N/A | **No** — not a model | Yes | Yes | ~0 | Always "missing" | N/A |
| Embeddings | Kotlin hash function | None (bag-of-words hash, 64-dim) | N/A | N/A | **Partially** — real, deterministic, on-device math, but not a trained semantic model | Yes | Yes | ~0 | Always "available" (it's not a model) | N/A |
| Model download/install | Kotlin `delay()` simulation | 5 fictitious catalog entries | Nowhere (not persisted to Room, no files written) | N/A | **No** | Yes (nothing happens) | N/A | N/A | N/A | N/A |

† `gemini-3.5-flash` could not be independently verified against any publicly documented Gemini model as of this audit's knowledge cutoff; it may be a newer/internal model name or a fabricated one from the AI Studio generator. **This should be verified against the live Gemini API model list before relying on it.**

**Licensing note:** `THIRD_PARTY_NOTICES.md` pre-declares MIT/Apache licenses for Whisper, Silero VAD, and SmolLM/Qwen/Gemma as if they were already integrated. Since none of these are actually present, this file is currently inaccurate; it should be treated as a template to fill in *after* real models are integrated, not as a record of what's shipping today.

**Bottom line:** there is no on-device AI inference anywhere in this codebase today, other than the primitive hash-based embedding and the keyword-rule "intelligence" fallback. Every subsystem that could plausibly be called "AI" is either a cloud API call or a deterministic non-ML placeholder.

---

## H. MVP Gap Matrix

| Area | Current State | Target | Status | Priority |
|---|---|---|---|---|
| Recording (mic, pause/resume, foreground service, screen-off) | Real `MediaRecorder` + foreground service + wake lock | Full local recorder | **COMPLETE** | — |
| Audio import | Real SAF copy + duration read | Local audio import | **COMPLETE** | — |
| Video import / audio extraction | File is copied, not demuxed; `MediaExtractor` imported but unused | Real audio-track extraction from video | **PLACEHOLDER** | P1 |
| Persistent audio storage | App-private `filesDir/meetings/{id}/` | Local file storage | **COMPLETE** | — |
| Database / persistence | 11-entity Room schema, FKs, indices | Local meeting memory | **COMPLETE** | — |
| VAD | Sine-wave function of time; never reads audio | Real local VAD (e.g. Silero) | **PLACEHOLDER** | P0 |
| ASR | Cloud Gemini call (works, but not local); fabricated text if offline | Real offline ASR | **PLACEHOLDER / cloud-dependent** | P0 |
| Diarization | Turn-alternation heuristic | Local speaker diarization | **PLACEHOLDER** | P1 |
| Local LLM | None; cloud Gemini or regex rules | Local quantized LLM | **MISSING** | P0 |
| Summary / title / topics | Cloud Gemini or crude frequency heuristic | Local LLM summary | **PARTIAL** | P0 |
| Action items / decisions / questions | Cloud Gemini or keyword-pattern rules | Local LLM extraction | **PARTIAL** | P0 |
| Embeddings | Real, local, hash-based (not semantic) | Local semantic embeddings | **PARTIAL** | P1 |
| Search (keyword) | Real SQL `LIKE` query | Local keyword search | **COMPLETE** | — |
| Search (semantic) | Real code, non-semantic hash similarity | Local semantic retrieval | **PARTIAL** | P1 |
| Ask Meeting | Cloud full-transcript stuffing, or keyword-quote fallback | RAG + local LLM, grounded w/ timestamps | **PARTIAL — not RAG** | P1 |
| Model management | Hardcoded list, fake `delay()`-based "download", disconnected from DB | Real download, install, delete, integrity check | **PLACEHOLDER** | P0 |
| Background processing / resumability | `viewModelScope` coroutine only; `ProcessingJobEntity` unused for resume | WorkManager-backed, resumable, retryable | **PARTIAL** | P1 |
| Firebase Auth (Google sign-in) | Real SDK wired, but UI calls a hardcoded mock profile | Real Google/Firebase auth | **PLACEHOLDER** | P1 |
| Firebase metadata sync | Correctly scoped (metadata-only) code exists but is never called | Lightweight metadata sync | **MISSING (dead code)** | P2 |
| Privacy (no audio/transcript leaves device) | **Violated** by the Gemini cloud path | Strictly local processing | **BROKEN** | P0 |
| Onboarding / Home / Settings / Model Manager UI | Real Compose screens, ViewModel-backed | Polished UX | **COMPLETE (visual layer)** | — |
| Dark mode | Present; some screens use hardcoded `Dark*` colors instead of `MaterialTheme.colorScheme` | Full theme support | **PARTIAL** | P2 |
| Automated tests | Only default AI-Studio boilerplate tests | Real unit/integration coverage | **MISSING** | P1 |
| CI/CD | None found | Build/test on every push | **MISSING** | P2 |
| Build system | Was broken (no wrapper, AGP/Gradle mismatch, missing keystore) | Reproducible build | **FIXED this session** | — |

---

## I. Recommended Roadmap

See `docs/ROADMAP.md` for the full prioritized breakdown. Summary of ordering logic: the build foundation is now fixed, so the next highest-priority work is **removing the false privacy claim** (either by gating/removing the cloud path or disclosing it honestly) and then **replacing the placeholder VAD → ASR → LLM → model-management chain with real local implementations**, in that order, since each downstream stage currently depends on the one before it having already been faked.

---

## J. Risks

- **Local ASR performance on-device**: even a "tiny" Whisper-class model is meaningfully heavier than anything currently running in this app (which does nothing on-device today). Real-time-factor, thermal throttling on sustained multi-minute meetings, and battery drain need to be benchmarked on real mid-tier hardware, not assumed from spec sheets.
- **Model size vs. APK/download size**: bundling several model tiers (ASR + LLM + embeddings + VAD) will push total download size into the hundreds of MB; the model-management screen needs to be real (see gap matrix) before this is viable, including Wi-Fi-only enforcement (the preference already exists but nothing consumes it yet).
- **RAM on low-end / entry-level devices**: `DeviceCapabilityDetector` already estimates a device tier and RAM, which is good groundwork, but no subsystem currently reacts to "low memory" by degrading gracefully (e.g., refusing to load a model that won't fit) — this needs to be built, not just detected.
- **Diarization quality**: real on-device diarization (embeddings + clustering) is one of the harder problems in this space even server-side; expectations should be set conservatively for a mobile MVP (e.g., 2–3 speaker cap, no cross-meeting speaker identity).
- **Long recordings**: nothing in the current pipeline chunks/streams audio during a long recording — everything processes the whole file after `stop()`. A 60–90 minute meeting will need chunked/streaming ASR to avoid multi-minute processing waits and memory spikes once real local inference replaces the instant fake/cloud paths.
- **Local LLM inference latency/quality tradeoff**: a model small enough to run acceptably on-device will produce meaningfully worse summaries/extractions than Gemini does today; product expectations for "AI quality" need to be reset around what's realistically achievable fully offline on a phone.
- **Model licensing**: Whisper (MIT) is safe; several small local LLMs suitable for mobile (e.g., Gemma) carry usage-restriction terms beyond plain Apache-2.0/MIT that need review before redistribution inside a commercial app — don't assume `THIRD_PARTY_NOTICES.md`'s current claims are accurate; re-verify against whatever model is actually integrated.
- **Firebase/privacy architecture**: the single biggest risk right now isn't technical, it's that the app **ships a privacy claim it doesn't honor**. If any build of this app reaches a real user with a configured Gemini key before the cloud path is gated or disclosed, that's a trust and potentially legal/compliance problem, not just a code-quality one.
- **Gradle/AGP version currency**: AGP 9.1.1 / Gradle 9.3.1 / Kotlin 2.2.10 are all very recent; keep an eye on ecosystem/library compatibility (Room, Navigation Compose, KSP) as this stack matures, since the project is currently riding the leading edge of the toolchain rather than a settled, widely-battle-tested combination.
