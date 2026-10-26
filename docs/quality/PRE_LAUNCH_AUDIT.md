# MeetingMind — Comprehensive Pre-Launch Stability & Quality Audit

**Date:** 2026-10-21  
**Target Version:** 1.0-v47 (versionCode 47)  
**Evaluator:** Antigravity CTO / QA Architecture  
**Status:** In Progress (Remediation & Verification Phase)

---

## 1. Executive Summary & Objective

This pre-launch stability and quality audit provides an evidence-based assessment of the MeetingMind Android codebase (`com.craftflowtechnologies.meetingmind`). The objective is to identify, reproduce, prioritize, and remediate crashes, architectural vulnerabilities, incomplete user journeys, data-loss vectors, and performance bottlenecks prior to production release on Google Play.

MeetingMind is a privacy-first, local-first meeting intelligence, notes, faith, and collaboration platform designed for offline operation with optional hybrid cloud capabilities (Google Gemini, DeepSeek, Cloudflare Workers).

---

## 2. Phase 1 — Project Architecture Baseline

### 2.1 Module Structure & Build System
- **Single Android Application Module:** `:app`
- **Build System:** Gradle 9.3.1 with Android Gradle Plugin (AGP) 9.1.1
- **Kotlin:** 2.2.10 with Jetpack Compose Compiler plugin
- **KSP:** 2.3.5 for Room annotation processing and schema validation
- **Compose BOM:** 2024.09.00 (Material3 1.3+, Navigation Compose 2.8.9)
- **SDK Targets:**
  - `minSdk`: 24 (Android 7.0 Nougat)
  - `targetSdk`: 36 (Android 16)
  - `compileSdk`: 36 (release 36 minorApiLevel 1)
- **ABI Split Strategy:**
  - `arm64-v8a` (Primary target for 64-bit ARM modern devices)
  - `armeabi-v7a` (Legacy 32-bit ARM support)
  - Universal APK disabled to maintain lean distribution footprint (<86MB per architecture despite bundled native inference runtimes).

### 2.2 Architecture & Subsystems
- **UI Architecture:** Unidirectional Data Flow (UDF) with Jetpack Compose, ViewModels (`StateFlow`), and Material3 Design Tokens (`MM.colors`, `FixedColors`).
- **Dependency Injection:** Lightweight Factory/Singleton architecture without reflection overhead; `MeetMindDatabase.getInstance(context)`, Repository providers, and `ViewModelProvider.Factory`.
- **Persistence Layer:**
  - Room 2.7.0 with SQLite FTS4 full-text search triggers on notes and transcripts.
  - Active Database Version: **27**, with 26 explicit migration paths (`MIGRATION_1_2` through `MIGRATION_26_27`).
  - Pre-migration backup guard (`DatabaseGuard.copyBeforeMigration`) ensuring rollbacks in case of migration failure.
  - Jetpack DataStore Preferences (v1.1.7) for application preferences, locale persistence, and AI configuration.
- **Audio Capture & Hardware Interface:**
  - `MeetingRecordingService`: Foreground Service with `foregroundServiceType="microphone"`.
  - Audio focus management (`AudioManager.OnAudioFocusChangeListener`) handling transient interruptions (e.g., phone calls) by pausing without unwanted auto-resumption.
  - Two-tier notification controls (Recording status + Work MarkActions lock-screen actions).
  - Recovery journaling (`RecordingJournalStore` heartbeat every 10s) to rescue audio files in the event of abnormal termination.
- **Speech Recognition & AI Pipeline:**
  - Voice Activity Detection (VAD): Silero VAD (ONNX runtime).
  - Local ASR: Sherpa-ONNX 1.13.6 runtime executing Parakeet TDT V3 INT8 (English), Whisper Turbo/Large-v3 INT8 (Multilingual), and Meta Omnilingual CTC 300M/1B INT8 (Southern African languages: Shona, Bemba, Nyanja, Ndebele, Zulu).
  - Speaker Diarization: Pyannote segmentation-3.0 with cosine speaker clustering and budget-constrained execution (`SherpaSpeakerDiarizer`).
  - Local LLM: MediaPipe Tasks GenAI (0.10.35) executing quantized `.task` models (Qwen 2.5 0.5B/1.5B, DeepSeek R1 Distill Qwen 1.5B).
  - Cloud AI: Google Gemini 1.5 Pro/Flash via `GeminiHttpTransport` with Bring-Your-Own-Key (BYOK) or managed proxy routing (`GEMINI_PROXY_URL`).
- **Faith & Community (Circles v2):**
  - Cloudflare Worker backend (`server/circles-api`) with Firebase Authentication (anonymous and Google Sign-in) and Cloudflare R2 media storage.
  - Local client store (`LocalCircleStore`) and real-time Firestore synchronization (`FirestoreCirclesData`).

### 2.3 Quality Gates & Baseline Test Infrastructure
- **Total Unit Test Files:** 371 test classes in `app/src/test/java`.
- **Architectural Guards:**
  - `ColorLiteralGuardTest`: Enforces strict usage of semantic design tokens, banning hardcoded hex colors outside `ui/theme`.
  - `PromptsIntegrityTest`: Guarantees on-disk prompt assets match catalog references.
  - `LocaleResourcesIntegrityTest`: Verifies string resource parity across all 9 supported locales (en, fr, es, pt, sw, ny, sn, zu, b+bem).
  - Room Migration Suite: Explicit integration tests for schema transitions (14->15 through 26->27).
- **Baseline Build Verification:**
  - `compileDebugKotlin`: Completed successfully (clean state).
  - Targeted test suites verified: `ColorLiteralGuardTest` (PASSED).

---

## 3. Immediate Audit Findings & Risks

| Category | Risk Summary | Initial Severity |
| :--- | :--- | :--- |
| **API Contract & Compilation** | Incomplete interface implementation of `createEmailInvite` on `HttpCirclesApi` and `NotConfiguredCirclesApi` causing compilation failures when modified. | **P1** |
| **Compose State & Recomposition** | `MainActivity` conditional collection of `homeCirclesVm.state.collectAsState()` inside an `if` branch violating Compose positional memoization and freezing Home Circles on custom home styles. | **P1** |
| **Audio Capture** | MediaRecorder 0-byte or rapid-stop failure edge cases returning non-null empty audio files causing downstream pipeline failures. | **P2** |
| **Offline Inference Memory** | Consecutive native model allocation (Sherpa ONNX + MediaPipe Tasks GenAI) on devices with <= 4GB RAM risking OS LowMemoryKiller (LMK) eviction. | **P1** |
| **Background Service Execution** | Android 14+ (API 34) Foreground Service type policies (`microphone`, `dataSync`, `mediaPlayback`) requiring precise manifest declarations and runtime permission ordering. | **P1** |
| **Data Safety & BYOK** | Unencrypted storage of optional BYOK keys in DataStore vs Android Keystore/EncryptedSharedPreferences. | **P2** |

---

## 4. Recommended Audit & Remediation Sequence

1. **Phase 2 (Crashes First):**
   - Resolve compilation and interface parity issues in Circles API.
   - Fix Compose conditional state collection in `MainActivity.kt`.
   - Inspect and harden `AudioRecorder` and `MeetingRecordingService` against zero-duration stops, mic permission revocations, and notification service errors.
2. **Phase 3 (Critical User Journeys):**
   - Onboarding flow: Language selection, model downloads, and permission gracefulness.
   - Recording to Note: Complete lifecycle from record press to transcription, diarization, and NoteUnveil moment.
   - Work & Circles: Feed rendering, post creation, and offline caching.
3. **Phase 4 (Data Integrity):**
   - Verify Room database migrations, CASCADE delete constraints, and FTS synchronization triggers.
4. **Phase 5 & 6 (Lifecycle, Concurrency & Performance):**
   - Audit WorkManager jobs, Foreground Service type declarations, and memory management during model handoffs.
5. **Phase 7 & 8 (Security & Release Readiness):**
   - Verify API credentials isolation, targetSdk 36 compliance, and release packaging rules.
