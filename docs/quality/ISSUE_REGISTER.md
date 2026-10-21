# MeetingMind — Pre-Launch Issue Register

**Version:** 1.0-v47  
**Updated:** 2026-10-21  
**Tracking Mode:** Actionable Pre-Launch Remediation

---

## Severity Definitions
- **P0 — Critical:** Data loss, severe security exposure, or failures rendering the app unusable.
- **P1 — High:** Frequent crashes, corruption, broken core journeys, or major reliability failures.
- **P2 — Medium:** Significant feature defects, recoverable failures, or major usability gaps.
- **P3 — Low:** Minor defects, polish issues, and nonessential improvements.

---

## Tracked Issues

### ISS-01: Circles API Abstract Member Incomplete Implementation
- **Feature/Subsystem:** Faith & Circles (`com.craftflowtechnologies.meetingmind.core.circles2`)
- **Severity / Likelihood:** P1 / High
- **Description:** `createEmailInvite` declared in `CirclesApi` interface was omitted from `NotConfiguredCirclesApi` and `HttpCirclesApi`, failing compilation on builds and test execution.
- **User Impact:** Build failure preventing unit testing and continuous integration of the circles subsystem.
- **Reproduction Steps:** Run `./gradlew compileDebugKotlin` or any test suite importing `CirclesApi`.
- **Evidence & Root Cause:** Adding `suspend fun createEmailInvite(...)` to the interface without implementing it in the mock/unconfigured singleton or real HTTP client.
- **Proposed Fix:** Implement `override suspend fun createEmailInvite(...)` in `NotConfiguredCirclesApi` (returning `no<Invite>()`) and `HttpCirclesApi` (posting to `createEmailInvite` endpoint).
- **Implementation Status:** Fixed and verified compiling (`BUILD SUCCESSFUL`).
- **Regression Test:** `compileDebugKotlin` and `CirclesApiTest`.
- **Verification Result:** PASS (Clean compilation).

---

### ISS-02: Conditional `collectAsState()` in MainActivity Home Circles Setup
- **Feature/Subsystem:** Navigation & Home Screen (`MainActivity.kt`, `HomeBento.kt`)
- **Severity / Likelihood:** P1 / Medium
- **Description:** `val homeCircles = if (homeCirclesVm != null) homeCirclesVm.state.collectAsState().value else HomeCircles.Loading` conditionally invokes Compose state collection inside an `if` branch.
- **User Impact:** In Jetpack Compose, calling `collectAsState()` conditionally violates positional memoization if `homeStyle` changes dynamically. Furthermore, for non-standard home styles (`CALM`, `FOCUS`), `homeCircles` is frozen at `HomeCircles.Loading`, potentially starving UI components expecting terminal states.
- **Reproduction Steps:** Set `Appearance.homeStyle` to `CALM`, navigate to Settings, switch to `TODAY`.
- **Evidence & Root Cause:** Positional composable invocation inside branching statement in `MainActivity.kt:444`.
- **Proposed Fix:** Decouple `homeCirclesVm` observation safely or use a safe non-branching fallback `homeCirclesVm?.state?.collectAsState()?.value ?: HomeCircles.Empty`.
- **Implementation Status:** Fix Ready.
- **Regression Test:** `HomeCirclesTest`, `HomeScreenRobolectricTest`.
- **Verification Result:** Pending Verification.

---

### ISS-03: Zero-Duration / Instant-Stop MediaRecorder Corruption
- **Feature/Subsystem:** Audio Capture (`AudioRecorder.kt`, `MeetingRecordingService.kt`)
- **Severity / Likelihood:** P1 / Medium
- **Description:** If a user starts recording and stops within <500ms, `MediaRecorder.stop()` throws `java.lang.RuntimeException: stop failed`. The catch block catches this exception and logs it, but still returns `currentOutputFile`, which is 0 bytes or an empty container header. `MeetingRecordingService` proceeds to persist an empty `MeetingEntity`, which later crashes or stalls `MeetingProcessingPipeline` during duration sniffing / VAD.
- **User Impact:** Orphaned empty recording rows that permanently hang at 0% or fail with unhandled decoders.
- **Reproduction Steps:** Tap Record FAB and immediately tap Stop in <0.3s.
- **Evidence & Root Cause:** `AudioRecorder.stopRecording()` returns `currentOutputFile` unconditionally even when `stop()` threw or file size is <= 1024 bytes.
- **Proposed Fix:** Validate output file size and duration before returning. If `mediaRecorder.stop()` fails or output file is empty/corrupt, delete the scratch file and return `null`. In `MeetingRecordingService`, if `file == null`, abort Room insertion, clean journal, and notify user gracefully ("Recording was too short to save").
- **Implementation Status:** Fix Ready.
- **Regression Test:** `AudioRecorderZeroDurationTest`.
- **Verification Result:** Pending Verification.

---

### ISS-04: Credential DataStore Included in System Auto-Backup Rules
- **Feature/Subsystem:** Security & Privacy (`GeminiCredentialStore.kt`, `data_extraction_rules.xml`, `backup_rules.xml`)
- **Severity / Likelihood:** P1 / Medium
- **Description:** `meetmind_credentials` DataStore storing user-supplied BYOK Gemini API keys is located in `files/datastore/meetmind_credentials.preferences_pb`. While database and audio directories are excluded, the credentials DataStore is not explicitly excluded in `data_extraction_rules.xml` and `backup_rules.xml`.
- **User Impact:** Cloud backup (Google One / Android Auto Backup) could transmit the user's unencrypted BYOK Gemini key to Google Cloud backups without user awareness.
- **Reproduction Steps:** Inspect `app/src/main/res/xml/data_extraction_rules.xml`.
- **Evidence & Root Cause:** Missing `<exclude domain="file" path="datastore/meetmind_credentials.preferences_pb" />` and `<exclude domain="sharedpref" path="meetmind_credentials" />`.
- **Proposed Fix:** Add explicit exclusions for `meetmind_credentials` in both cloud-backup and device-transfer blocks of `data_extraction_rules.xml` and `backup_rules.xml`.
- **Implementation Status:** Fix Ready.
- **Regression Test:** XML schema and manifest validation.
- **Verification Result:** Pending Verification.

---

### ISS-05: Missing Native Memory Pressure Eviction on Low-RAM Devices
- **Feature/Subsystem:** AI Pipeline & Inference (`MeetingProcessingPipeline.kt`, `LocalModelStorage.kt`)
- **Severity / Likelihood:** P1 / High (on <=4GB RAM devices)
- **Description:** In `MeetingProcessingPipeline`, ASR (Sherpa ONNX Parakeet or Whisper Turbo ~1GB), Diarization (Pyannote ~100MB), and LLM (MediaPipe Tasks GenAI ~1.5GB) execute sequentially. While scoped, native heap allocations managed by C++ runtimes (ONNX runtime, MediaPipe native libraries) do not instantly get reclaimed by the JVM garbage collector upon exiting scope. On 4GB devices (e.g., Galaxy S20), peak RSS can exceed 2.8GB, triggering OS Out-Of-Memory low-memory killer (LMK) eviction.
- **User Impact:** App abruptly terminates in background while processing meetings on 4GB devices.
- **Reproduction Steps:** Run full offline pipeline on a device or emulator constrained to 3GB RAM.
- **Evidence & Root Cause:** Absence of explicit native memory release / unmapping between ASR completion and LLM loading.
- **Proposed Fix:** Ensure explicit engine release (`recognizer.release()`, explicit engine closure in `SherpaEngineManager` and `LlmEngineManager`) and invoke `System.gc()` before launching the LLM inference stage.
- **Implementation Status:** Under Investigation.
- **Regression Test:** `PipelineMemoryManagementTest`.
- **Verification Result:** Pending Verification.

---

### ISS-06: Circles Home Tile Stuck in Loading on Network Timeout
- **Feature/Subsystem:** Circles & Home Bento (`HomeCircles.kt`, `HomeCirclesViewModel.kt`)
- **Severity / Likelihood:** P2 / High
- **Description:** In `HomeCirclesViewModel`, `load()` sets a 3500ms timeout on `repo.refresh()`. If network latency exceeds 3500ms or Firebase anonymous auth takes longer than expected, the timeout fires, but `note` in `CirclesRepository` remains `null`. As a consequence, `HomeCircles.from(Ready(emptyList(), null), refreshed = true)` evaluates to `HomeCircles.Empty` or stays in `Loading` if `refreshed` does not propagate. The user sees "Circles" header with blank or empty placeholder rather than an informative connection failure with a retry action.
- **User Impact:** User perceives the Circles tile on Home as broken or unresponsive.
- **Reproduction Steps:** Simulate network delay of 4000ms and observe Home Circles card.
- **Evidence & Root Cause:** Hardcoded 3500ms `withTimeoutOrNull` discarding the error details on timeout.
- **Proposed Fix:** In `HomeCirclesViewModel`, catch `TimeoutCancellationException` explicitly and set state to `HomeCircles.Failed(CirclesFailure.offline)` or provide a clear retryable state.
- **Implementation Status:** Fix Ready.
- **Regression Test:** `HomeCirclesViewModelTest`.
- **Verification Result:** Pending Verification.

---

### ISS-07: Deprecated Vector Asset Icons Causing AAPT2 / Lint Warnings
- **Feature/Subsystem:** Compose UI / Theme (`Icons.Rounded`, `Icons.Filled`)
- **Severity / Likelihood:** P3 / High
- **Description:** Multiple UI files reference deprecated directional icons (`Forward`, `Send`, `KeyboardArrowLeft`, `KeyboardArrowRight`, `MenuBook`, `OpenInNew`) instead of `AutoMirrored` counterparts.
- **User Impact:** Potential incorrect layout orientation in Right-to-Left (RTL) locales (such as Arabic).
- **Reproduction Steps:** Switch app language to Arabic and inspect StepperFooter navigation arrows.
- **Evidence & Root Cause:** Kotlin compiler warnings emitted during `compileDebugKotlin`.
- **Proposed Fix:** Migrate deprecated icon references to `Icons.AutoMirrored` equivalents.
- **Implementation Status:** Scheduled for polish.
- **Regression Test:** Lint and visual audit.
- **Verification Result:** Documented.
