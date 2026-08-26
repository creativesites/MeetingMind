# MeetingMind — AI Architecture

This documents the **actual** AI runtimes/models present in the codebase today, and the **target** local-first architecture they need to be replaced with.

## 0. Status Update — P0 Privacy Reconciliation

The state described in §D/§G of `docs/AUDIT.md` (cloud Gemini calls, `sin(time)` fake VAD, regex "intelligence", `delay()`-based fake model downloads, hardcoded mock sign-in) has been **removed**. This section reflects the architecture as it stands now; the original audit findings below (§1 historical table) are kept as a record of what was found, not what ships today.

**What changed:**
- `ai/gemini/GeminiApiClient.kt` was deleted entirely — no class in the app calls any cloud AI endpoint. `PrivacyNoCloudPathTest` asserts this by reflection (the class must not exist) and by checking `MeetingProcessingPipeline`'s default constructor parameters.
- Every AI interface (`VoiceActivityDetector`, `SpeechRecognizer`, `SpeakerDiarizer`, `MeetingIntelligenceEngine`, and the new lower-level `LanguageModel`) now returns `AiResult<T>` — `Success`, `ModelUnavailable`, `DeviceUnsupported`, `InsufficientMemory`, or `Failed` — instead of a plain value. No implementation may return `Success` with fabricated content.
- The default implementation behind every one of those interfaces is an `Unavailable*` class (`UnavailableVoiceActivityDetector`, `UnavailableSpeechRecognizer`, `UnavailableSpeakerDiarizer`, `UnavailableMeetingIntelligenceEngine`, `UnavailableLanguageModel`) that honestly returns `AiResult.ModelUnavailable` — no sine waves, no placeholder transcript strings, no keyword-rule "intelligence."
- `MeetingProcessingPipeline` now branches on `AiResult`: if ASR is unavailable, processing stops, the recording is left exactly as it was, and the meeting is marked `MeetingStatus.MODEL_REQUIRED` (a new, distinct status — not `ERROR`, since nothing failed). If diarization or meeting intelligence are unavailable but ASR succeeded, the pipeline degrades gracefully (keeps ASR's own segments, leaves summary/decisions/action items/questions empty) rather than blocking the whole meeting or fabricating those fields.
- Model management (`ModelRepository`, package `ai/modelmanagement/`) is now a real architecture: `ModelStorage` (real app-private file storage + install marker), `ModelVerifier` (real SHA-256 checksum), `ModelDownloader` (interface; default `UnconfiguredModelDownloader` honestly fails since no production model source is hosted yet), and a static `ModelCatalog` of candidate models with `downloadUrl = null` / `sha256 = null` — **no invented URLs**. Installed state is read from real on-disk state + Room (`AiModelEntity`/`AiModelDao`), not an in-memory `delay()`-simulated flag.
- Firebase Authentication now uses a real Credential Manager → Google ID token → `FirebaseAuth.signInWithCredential` flow (`FirebaseAuthManager.signInWithGoogle`). The hardcoded "Winston Chen" mock profile is gone. If Firebase isn't configured (no `google-services.json`) or no web client ID is set, sign-in honestly reports `AuthAvailability.NotConfigured` instead of fabricating a signed-in user — recording and local processing are never gated on this.
- `GEMINI_API_KEY` / the secrets-gradle-plugin / `.env` machinery were removed entirely — the app requires no API key and no cloud configuration to build or run.

**What has NOT changed:** there is still no real local ASR/VAD/diarization/LLM model integrated. That is intentionally deferred to the next phase (see §3 below) — this pass only established an honest, privacy-safe, model-ready architecture, per its own scope.

## 0b. Status Update — Phase 1: Real Offline Speech Pipeline

This phase replaced the *architecture* built in §0 with the first *real* local AI: on-device VAD and ASR. Nothing here touches diarization, the LLM, embeddings, or Ask Meeting — those remain exactly as described in §0 (`UnavailableSpeakerDiarizer`, `UnavailableMeetingIntelligenceEngine`), which is intentional and out of scope for this phase. See "Critical Non-Goals" below.

**Pipeline implemented:**
```
Recorded/imported audio file
  -> AudioFormatConverter (MediaExtractor + MediaCodec decode -> mono downmix -> linear resample)
  -> 16 kHz mono Float32 PCM
  -> SileroVadDetector (real sherpa-onnx Vad)           -> speech intervals
  -> SherpaParakeetSpeechRecognizer (real sherpa-onnx OfflineRecognizer, per interval)
  -> TranscriptSegment(startMs, endMs, text, speakerId=null, confidence=null)
  -> Room (transcript_segments)
  -> Meeting Detail UI (Transcript tab)
```

### sherpa-onnx: version and Android integration (verified, not assumed)

- **Version pinned: 1.13.6** (latest GitHub release as of this phase, tagged `v1.13.6`).
- **Integration mechanism**: sherpa-onnx does **not** publish to Maven Central, and its own `jitpack.yml` reveals that even its official JitPack build is just a `wget` of the prebuilt GitHub Release AAR followed by `mvn install:install-file` — i.e. upstream itself treats the release AAR as the artifact, not something JitPack compiles from source. This project mirrors that directly: `settings.gradle.kts` declares an `ivy` repository pointed at `https://github.com/k2-fsa/sherpa-onnx/releases/download`, and `app/build.gradle.kts` declares `implementation("k2-fsa:sherpa-onnx:1.13.6@aar")`. This was verified end-to-end in this environment: the dependency resolves and `:app:compileDebugKotlin` succeeds against the real `com.k2fsa.sherpa.onnx.*` classes.
- **What's inside the AAR** (confirmed by downloading and inspecting `sherpa-onnx-1.13.6.aar`, 49,097,942 bytes): `classes.jar` (the Kotlin API — `OfflineRecognizer`, `OfflineStream`, `Vad`, `FeatureConfig`, model config data classes, etc., package `com.k2fsa.sherpa.onnx`) plus `jni/{arm64-v8a,armeabi-v7a,x86,x86_64}/lib{onnxruntime,sherpa-onnx-c-api,sherpa-onnx-cxx-api,sherpa-onnx-jni}.so`. `minSdkVersion` in the AAR's manifest is 21, comfortably under this app's `minSdk = 24`.
- **Why this model/runtime**: sherpa-onnx is the only actively-maintained toolkit that ships (a) a real Android AAR with prebuilt native libs for all four common ABIs, (b) first-class Silero VAD support, and (c) first-class NeMo transducer (TDT) support — covering both required capabilities for this phase from one dependency, one native library set, one Kotlin API.

### VAD: Silero VAD via sherpa-onnx `Vad`

- Model: `silero_vad.onnx`, downloaded from `https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx`. Size **643,854 bytes**; SHA-256 **`9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6`** — both verified by actually downloading the file and hashing it during this phase's development, not copied from an untrusted source.
- Real Kotlin API: `com.k2fsa.sherpa.onnx.Vad` + `VadModelConfig(sileroVadModelConfig = SileroVadModelConfig(model = <path>, threshold, minSilenceDuration, minSpeechDuration, windowSize, maxSpeechDuration), sampleRate = 16000, numThreads = 1, provider = "cpu")`.
- Frame size: `windowSize = 512` samples at 16 kHz = 32 ms/frame, per the library's own documented default. `maxSpeechDuration` raised to 30s (library example default is 5s, tuned for short voice commands, not meeting-length utterances).
- Implementation: `ai/vad/SileroVadDetector.kt`. Feeds real 16 kHz mono PCM frames (from `AudioFormatConverter`) through `vad.acceptWaveform()`, drains `SpeechSegment`s via `vad.front()/pop()`, converts sample offsets to millisecond timestamps. No synthetic/time-based detection anywhere in this path.

### ASR: NVIDIA Parakeet TDT 0.6B v3 (INT8) via sherpa-onnx `OfflineRecognizer`

- Model: `csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8` on Hugging Face (converted from `nvidia/parakeet-tdt-0.6b-v3`). English + 24 European languages.
- Four files, every size/SHA-256 below verified by downloading each file and hashing it during this phase:

  | File | Size (bytes) | SHA-256 |
  |---|---|---|
  | `encoder.int8.onnx` | 652,184,281 | `acfc2b4456377e15d04f0243af540b7fe7c992f8d898d751cf134c3a55fd2247` |
  | `decoder.int8.onnx` | 11,845,275 | `179e50c43d1a9de79c8a24149a2f9bac6eb5981823f2a2ed88d655b24248db4e` |
  | `joiner.int8.onnx` | 6,355,277 | `3164c13fc2821009440d20fcb5fdc78bff28b4db2f8d0f0b329101719c0948b3` |
  | `tokens.txt` | 93,939 | `d58544679ea4bc6ac563d1f545eb7d474bd6cfa467f0a6e2c1dc1c7d37e3c35d` |

  Total: 670,478,772 bytes (~639 MiB / ~670 MB decimal) — close to, and now more precise than, the task's ~680MB estimate.
- Real Kotlin API/config: `OfflineRecognizerConfig(featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80), modelConfig = OfflineModelConfig(transducer = OfflineTransducerModelConfig(encoder, decoder, joiner), tokens = <path>, modelType = "nemo_transducer", numThreads = 2, provider = "cpu"), decodingMethod = "greedy_search")`. `modelType = "nemo_transducer"` and `featureDim = 80` (the library's own default — sherpa-onnx's own documented NeMo-transducer example does not override `--num-mel-bins`) were confirmed against sherpa-onnx's own documentation and Kotlin example (`kotlin-api-examples/test_offline_asr.kt`), not invented.
- Per VAD-detected speech interval, one `OfflineStream` is created (`recognizer.createStream()`), fed that interval's samples (`stream.acceptWaveform(samples, 16000)`), decoded (`recognizer.decode(stream)`), and read back (`recognizer.getResult(stream).text`) — mapped to one `TranscriptSegment` per non-empty result. `speakerId`/`speakerName`/`confidence` are `null`: sherpa-onnx's `OfflineRecognizerResult` has no numeric confidence field, and this phase does not implement diarization, so no speaker identity is fabricated.
- Implementation: `ai/asr/SherpaParakeetSpeechRecognizer.kt`.

### Model loading and reuse: `SherpaEngineManager`

Loading the Parakeet encoder alone is a ~650MB native allocation. `ai/modelmanagement/SherpaEngineManager.kt` is a process-wide singleton (guarded by a `Mutex`) that loads at most one `OfflineRecognizer` and one `Vad` instance, reusing them across every VAD-segment/ASR-stream call within (and across) a processing job instead of reloading per segment. The mutex also serializes access, which both respects that sherpa-onnx's native objects are not documented as thread-safe and prevents two concurrent transcription jobs from each loading their own copy of the model.

### Audio preparation layer: `AudioFormatConverter`

Real explicit conversion — never assumes the source file is already 16 kHz mono PCM16:
```
File -> MediaExtractor (select first audio track) -> MediaCodec (decode to PCM16)
     -> mono downmix (channel average) -> linear-interpolation resample to 16 kHz
     -> Float32 normalized to [-1, 1]
```
The original recording file is only ever read, never modified. Implementation: `core/audio/AudioFormatConverter.kt`. The DSP core (`downmixToMono`, `linearResample`) is unit-tested directly (`AudioFormatConverterTest`); the full MediaCodec decode path cannot be meaningfully unit-tested on the JVM — see "Known Limitations" below.

### Model download architecture (now real, not just structural)

- `ai/modelmanagement/ModelDownloader.kt` now has a real implementation, `OkHttpModelDownloader`: streams to disk, reports progress, resumes via HTTP `Range` when a `.part` file already has bytes, retries up to 3 times on `IOException`, and respects coroutine cancellation (checked every chunk via `ensureActive()`).
- `ModelRepository.installModel()` downloads every file in a model's manifest into `<file>.part`, verifies each with `Sha256ModelVerifier` (real SHA-256, never skipped), and only renames `.part` → final name after verification succeeds — a file is **never** activated unverified, and a corrupted download is deleted rather than kept. Only once every file in the manifest is present and verified does `ModelStorage.markInstalled()` get called. A failure partway through leaves already-verified sibling files in place so a retry resumes rather than restarts.
- Insufficient-storage detection: before downloading, `ModelRepository` compares the remaining bytes needed against `DeviceCapabilityDetector.getAvailableStorageMb()` and returns `AiResult.InsufficientStorage` (a new `AiResult`/case, alongside the existing RAM-focused `InsufficientMemory`) rather than starting a download that can't finish.
- `ModelCatalog` (`ai/modelmanagement/ModelCatalog.kt`) now has two real entries — Silero VAD and Parakeet TDT v3 — with real URLs and the verified checksums/sizes above. `ModelDownloader`/`UnconfiguredModelDownloader` remains available for any future catalog entry without a real source yet (none currently).
- `okhttp` was re-added as a dependency specifically for this — it is used *only* by `ModelDownloader` for model file downloads, never for AI inference calls. `PrivacyNoCloudPathTest` and the structural design (neither `SileroVadDetector` nor `SherpaParakeetSpeechRecognizer` import okhttp or any networking type) keep this boundary enforced.

### Memory / performance considerations

- Parakeet's INT8 encoder alone is ~622MB on disk; `minimumRamMb = 2048` / `recommendedRamMb = 4096` in its catalog entry are conservative estimates based on typical ONNX Runtime INT8 transducer memory overhead (model weights + activation buffers), not a measured on-device figure — **real device memory profiling is still needed** (see Known Limitations).
- `numThreads = 2` for the recognizer is a fixed, reasonable default (not dynamically tuned from `DeviceCapabilityDetector`); revisit once real-device timing data exists.
- The engine is loaded once per process (via `SherpaEngineManager`) and reused across every segment in a meeting, avoiding the far larger cost of reloading a 650MB model per VAD segment.

### Known Limitations (be honest about what could and couldn't be verified here)

- **No Android device or emulator was available in this development environment.** Every claim above about sherpa-onnx's Kotlin API, the AAR's contents, and the model files' sizes/checksums was verified directly (downloading the real AAR/model files and inspecting/hashing them, and successfully compiling against the real `com.k2fsa.sherpa.onnx.*` classes) — but **actual on-device inference (does Parakeet really transcribe correctly, does Silero VAD really segment speech correctly, real latency, real memory footprint, real battery/thermal behavior) has not been run and cannot be claimed as verified.** See the Phase 1 completion report for the explicit "BUILD VERIFIED" vs. "REAL DEVICE INFERENCE VERIFIED" distinction.
- Robolectric (JVM-only Android test simulation) cannot load the sherpa-onnx native `.so` libraries (they are compiled for Android's ABIs/runtime, not the host JVM), so `SileroVadDetectorTest`/`SherpaParakeetSpeechRecognizerTest` only cover the "no model installed" honest-failure path, which is the one path reachable without touching native code. This is a real, meaningful test (proves no fabrication when unavailable) but it is **not** a substitute for a real-device inference test.
- The MediaCodec decode path in `AudioFormatConverter` is untested beyond its pure DSP helpers, for the same JVM/native-shadow limitation.
- Model RAM requirements in the catalog are estimates pending real-device measurement.
- Resume-on-download was implemented against HTTP `Range` semantics and verified against a local mock server (`OkHttpModelDownloaderTest`), but real-world CDN behavior (Hugging Face's xet-backed CDN, GitHub Releases' Azure-blob-backed CDN) was only spot-checked manually during development, not exercised by an automated test against the live services.

### Critical Non-Goals for This Phase (unchanged from the task scope)

Not implemented, and not started: speaker diarization (still `UnavailableSpeakerDiarizer`), local LLM / summaries / action-item extraction / decisions (still `UnavailableMeetingIntelligenceEngine`), embeddings beyond the existing hash-based placeholder, Ask Meeting, calendar integration, Zoom/Meet/Teams, backend meeting bots. These remain exactly as documented in §0/§3 below.

## 0c. Status Update — Phase 2: Real Speaker Diarization + Local Meeting Intelligence

This phase adds the two remaining "unavailable-by-default" AI stages from §0b: real speaker diarization and a real local LLM behind `MeetingIntelligenceEngine`. Nothing here touches calendar/Zoom/Meet/Teams/backend bots — those remain entirely unimplemented, per the task's non-goals.

**Baseline before this phase**: commit `39236b2`. Verified on a physical Android phone: app install/launch/navigation, recording lifecycle, audio import, model management UI, and Silero VAD download all work; Parakeet TDT real-device inference had **not yet** been verified at the start of this phase (~639MB model not yet downloaded on the test device). This phase does not change that status — see "Real Device Validation Status" below.

### Pipeline (updated)

```
Recorded/imported audio file
  -> AudioFormatConverter (unchanged from Phase 1)
  -> 16 kHz mono Float32 PCM
  -> SileroVadDetector                                    -> speech intervals
  -> SherpaParakeetSpeechRecognizer                        -> TranscriptSegment[] (speakerId=null)
  -> SherpaSpeakerDiarizer (real sherpa-onnx OfflineSpeakerDiarization, run on the same PCM)
       -> raw (start, end, speakerIndex) segments
       -> reconcileTranscriptWithSpeakers(): timestamp-overlap reconciliation against the ASR segments
       -> TranscriptSegment[] (speakerId="spk_{meetingId}_{index}", speakerName="Speaker {index+1}")
  -> RealMeetingIntelligenceEngine (MediaPipe LlmInference, Qwen2.5-1.5B-Instruct)
       -> TranscriptChunker splits by real model context length
       -> per-chunk strict-JSON extraction (decisions/actionItems/questions/followUps)
       -> MeetingIntelligenceJsonParser validates + drops anything malformed or ungrounded
       -> one synthesis call -> title/summary/keyPoints
  -> Room (transcript_segments with real speakerId, speakers, decisions, action_items, questions, follow_ups)
  -> Meeting Detail UI (Transcript/Overview/Action Items/Decisions tabs)
```

Every stage's native models are released before the next stage's load (`SherpaEngineManager.releaseAll()` after ASR and again after diarization; `LlmEngineManager.release()` after the LLM step) — at most one heavy model family (Parakeet, or the diarization pair, or the 1.5B LLM) is resident in memory at a time. See "Resource Management" below.

### Speaker Diarization: sherpa-onnx `OfflineSpeakerDiarization`

Inspected directly from the sherpa-onnx 1.13.6 source tree (`android/SherpaOnnxAar/.../OfflineSpeakerDiarization.kt`, `android/SherpaOnnxSpeakerDiarization/` example app, `sherpa-onnx/c-api/docs/speaker-diarization.dox`) rather than assumed — the same AAR already integrated in Phase 1 bundles this class; no new native dependency was needed.

Real API: `OfflineSpeakerDiarization(config: OfflineSpeakerDiarizationConfig)`, where `config` combines a **segmentation model** (finds speaker-change boundaries), an **embedding model** (turns each segment into a vector), and a **clustering config**. `diarizer.process(samples: FloatArray): Array<OfflineSpeakerDiarizationSegment>` returns `(start: Float sec, end: Float sec, speaker: Int)` triples. `FastClusteringConfig.numClusters`: `-1` lets the engine auto-detect the speaker count via threshold-based clustering; a positive value forces exactly that many speakers — this is what backs the Processing screen's Auto/2/3/4/5/6 picker.

Models selected (verified 2026-08-20 by downloading each file and hashing it directly, not trusting HTTP headers):

| Model | Role | File | Size (bytes) | SHA-256 | License |
|---|---|---|---|---|---|
| pyannote/segmentation-3.0 (sherpa-onnx int8 export) | Segmentation | `segmentation.onnx` (extracted from `sherpa-onnx-pyannote-segmentation-3-0.tar.bz2`, 6,958,444 bytes) | 1,540,506 | `d582f4b4c6b48205de7e0643c57df0df5615a3c176189be3fc461e9d18827b5d` | MIT (Copyright 2022 CNRS) |
| 3D-Speaker CAM++ (English, VoxCeleb-trained) | Speaker embedding | `3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx` → `embedding.onnx` | 29,596,978 | `357a834f702b80161e5b981182c038e18553c1f2ca752ed6cec2052365d4129b` | Apache-2.0 (Alibaba DAMO Academy) |

Combined install size: ~31.1MB — far smaller than Parakeet, chosen deliberately (§ "MODEL MANAGEMENT" of the task explicitly wants no surprise hundreds-of-MB downloads; this one is small enough to not need its own storage warning threshold beyond the existing generic check).

**Why English CAM++ over the example app's default Chinese embedding model**: sherpa-onnx's own example (`SpeakerDiarizationObject.kt`) defaults to `3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx` (Chinese-trained) purely as its demo choice — not a recommendation. The `speaker-recongition-models` release also publishes English-trained options (`3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx`, several `wespeaker_en_voxceleb_*` variants, NeMo TitaNet variants); CAM++ was chosen for its small size (~29.6MB vs. 60–100+MB for the WeSpeaker ResNet variants) with strong VoxCeleb benchmark accuracy for its size class.

**Distribution-format wrinkle, handled honestly, not worked around**: the segmentation model ships only as a `.tar.bz2` archive (scripts + LICENSE + both fp32 and int8 `.onnx` variants bundled together) — there is no bare per-file download URL for it. Rather than mirror the extracted file to a different host, `ModelFileSpec` gained two new optional fields (`downloadSizeBytes`, `archiveEntryPath`) and a real `ArchiveExtractor` (`ai/modelmanagement/ArchiveExtractor.kt`, backed by `org.apache.commons:commons-compress` — the standard library for exactly this) was added so `ModelRepository.installModel()` can download the real official tarball, extract only `model.int8.onnx`, verify its SHA-256, and discard the rest. The archive itself is never trusted as "installed" — only the extracted, verified file is.

**Implementation**: `ai/diarization/SherpaSpeakerDiarizer.kt`. Checks `ModelStorage.isInstalled()` before touching any sherpa-onnx class (same testability pattern as Phase 1's VAD/ASR). `SherpaEngineManager.getOrCreateDiarizer()` reuses the loaded segmentation+embedding models across calls; `diarizer.setConfig()` re-applies only the clustering settings (the one thing that legitimately varies per meeting) without reloading the underlying models, per the sherpa-onnx API's own documented behavior ("Only config.clustering is used... All other fields in config are ignored" by `setConfig`).

**Reconciliation** (`reconcileTranscriptWithSpeakers()`, same file, pure and unit-tested independent of any native code): for each real ASR `TranscriptSegment`, finds the raw diarization interval with the greatest millisecond overlap and assigns `speakerId = "spk_{meetingId}_{index}"` / `speakerName = "Speaker {index+1}"`. A segment with zero overlap against any diarization interval is left exactly as it was (`speakerId` stays `null`) — uncertainty is preserved, never guessed at. No turn-alternation, no pause-length heuristic, no random assignment anywhere in this path.

**Speaker identity vs. display name**: `Speaker.id`/`speakerIndex`/`originalLabel` (the raw diarization identity) are never modified by a rename — `TranscriptRepository.renameSpeaker()` only ever updates `customName` (and the denormalized `speakerName` copy on transcript segments), verified by `TranscriptRepositorySpeakerTest`.

### Local Meeting Intelligence: MediaPipe LlmInference + Qwen2.5-1.5B-Instruct

**What existed before this phase**: nothing. `LanguageModel`/`UnavailableLanguageModel` and `MeetingIntelligenceEngine`/`UnavailableMeetingIntelligenceEngine` were interface scaffolding introduced in the P0 pass (§0) with no real backing implementation — `UnavailableMeetingIntelligenceEngine` was the only thing ever wired into `MeetingProcessingPipeline`. There was no existing model to evaluate for suitability; this phase is a from-scratch selection, not a replacement.

**Runtime chosen**: MediaPipe's `com.google.mediapipe:tasks-genai:0.10.35` (Google's official on-device LLM inference API for Android, published on Google's Maven repository — `google()`, already declared in `settings.gradle.kts`). Inspected directly by downloading the AAR and running `javap` against its real classes (`LlmInference`, `LlmInference.LlmInferenceOptions`, `LlmInferenceSession`) rather than assumed. Chosen over llama.cpp because MediaPipe ships a prebuilt AAR with no custom native build required — the same "consume a verified prebuilt artifact, don't compile from source" discipline used for sherpa-onnx in Phase 1 — and it is the current, documented, Google-maintained API for this purpose. Note: `LlmInference` is marked `@Deprecated` in bytecode in this AAR version with no message text embedded and no newer inference class present anywhere else in the same artifact; it remains the only shipped, functional API for this purpose and was used as-is — worth re-checking on a future MediaPipe version bump.

Real API surface used: `LlmInference.createFromOptions(context, LlmInferenceOptions.builder().setModelPath(path).setMaxTokens(contextLength).setPreferredBackend(Backend.CPU).build())`, then the synchronous `engine.generateResponse(prompt): String`. `Backend.CPU` was chosen explicitly over `GPU`/`DEFAULT` for predictable behavior across "ordinary Android phones," where GPU delegate support varies a lot by device/driver.

**Model chosen**: `litert-community/Qwen2.5-1.5B-Instruct` on Hugging Face, the `Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.task` file — q8 quantized, 4096-token KV cache (context length). Verified 2026-08-20 by downloading the full 1.49GB file and hashing it directly:

| Field | Value |
|---|---|
| Size | 1,598,556,720 bytes (~1.49 GiB) |
| SHA-256 | `82968d0a6c3872cf016fdbcfc591571605f4c7fd2b0f64d2533df502cc6596b3` |
| License | Apache-2.0 |
| Context length | 4096 tokens (from the file's own `ekv4096` designation — the real KV-cache size of this specific build, not an invented number) |

**Why this model over Gemma**: several `litert-community/Gemma*` `.task` builds were checked first (the natural first choice, being Google's own flagship small model family for this exact runtime) but every one is a **gated Hugging Face repository** requiring an authenticated, logged-in user to accept Google's Gemma Terms of Use before download — confirmed via a direct HTTP request returning `401 GatedRepo`. An unattended in-app download cannot satisfy a click-through license gate, so Gemma was ruled out for this phase specifically on that basis (not quality). Qwen2.5-1.5B-Instruct's litert-community build is public, ungated, and Apache-2.0 licensed with no separate model-use terms — the same "real, ungated, on-demand download" bar every other model in this app already meets.

**Structured output schema** (`core/model/MeetingModels.kt`):
```
Decision(id, meetingId, text, type: DECISION|SUGGESTION|DISCUSSION|POSSIBILITY, confidence: Float?, sourceSegmentIds: List<String>)
ActionItem(id, meetingId, task, assigneeSpeakerId: String?, assigneeName: String?, deadline: String?, confidence: Float?, isCompleted, sourceSegmentIds)
Question(id, meetingId, text, askedBySpeakerId: String?, resolved, answer: String?, sourceSegmentIds)
FollowUp(id, meetingId, description, ownerSpeakerId: String?, deadline: String?, sourceSegmentIds)
MeetingSummary(title, summary, topics: List<String> [key points], decisions, actionItems, questions, followUps)
```
`confidence` is nullable everywhere and is never populated with an invented number — the extraction prompt/parser has no mechanism to produce one, so it is always `null` today (never a fabricated `0.9f`-style default, unlike the pre-Phase-1 domain model). Every extracted item carries `sourceSegmentIds`, cross-checked against the real segment ids the model was actually shown in that chunk's prompt (`MeetingIntelligenceJsonParser.toValidIds()`) — a citation the model invents that wasn't in its own prompt is silently dropped, never trusted.

**No-hallucination handling**: the extraction prompt (`RealMeetingIntelligenceEngine.buildExtractionPrompt()`) explicitly instructs the model to classify DECISION vs. SUGGESTION vs. DISCUSSION vs. POSSIBILITY, gives the exact "I think we should launch around the 15th" → POSSIBILITY (never DECISION) example from the task spec, and instructs empty arrays over invented content. This is prompt-level guidance, not a hard guarantee — a 1.5B model can still misclassify — but the JSON validation layer catches structural hallucination (invented segment ids, blank required fields, invalid enum values) regardless of the model's own judgment.

**Chunking** (`ai/llm/TranscriptChunker.kt`): splits only at transcript-segment boundaries (never mid-sentence — a single segment is never divided), sized to a real model-derived token budget (`contextLengthTokens` from the model's own catalog entry, 4096 here — never hardcoded independent of that value), using a conservative chars-per-token approximation (3.5, biased toward smaller chunks) since no tokenizer is available outside a loaded engine instance. A single segment exceeding the whole chunk budget isn't specially handled because VAD already caps every spoken interval at 30 seconds (`SileroVadDetector.MAX_SPEECH_DURATION_SEC`), far shorter than any reasonable chunk budget in characters.

**Multi-chunk meetings**: each chunk is independently extracted (decisions/actionItems/questions/followUps + a brief per-chunk summary); one final synthesis call combines the chunk summaries plus the already-extracted decisions/action items into a title + executive summary + key points — never re-feeding the full original transcript a second time, which keeps the synthesis call's own prompt small regardless of total meeting length.

**JSON validation, not free-form parsing**: MediaPipe's `LlmInferenceSession` exposes a `constraintHandle` option suggesting some constrained-decoding support exists in principle, but nothing in the inspected AAR documents how to construct one from the Kotlin API, so this phase uses strict JSON prompting + robust parsing instead (explicitly an accepted fallback per the task spec). `MeetingIntelligenceJsonParser` strips markdown code fences, trims to the outermost `{...}`, parses via `org.json`, and drops (never guesses at) any item with blank required text, an unrecognized decision type (falls back to DISCUSSION), or `sourceSegmentIds` not in the real set shown to the model. Malformed output at the top level yields an empty `ChunkExtraction`, never a crash and never invented content.

**Implementation files**: `ai/modelmanagement/LlmEngineManager.kt` (singleton engine reuse, mirrors `SherpaEngineManager`), `ai/llm/MediaPipeLanguageModel.kt` (`LanguageModel` implementation, checks install state before touching any MediaPipe class), `ai/llm/TranscriptChunker.kt`, `ai/llm/MeetingIntelligenceJsonParser.kt`, `ai/llm/RealMeetingIntelligenceEngine.kt` (`MeetingIntelligenceEngine` implementation — chunked extraction + synthesis, including title generation folded into the synthesis call (see Phase 3C note below), and grounded Ask-Meeting, all built on `LanguageModel`, never depending on MediaPipe types directly).

**Ask Meeting limitation**: `RealMeetingIntelligenceEngine.askMeeting()` is grounded only in whatever fits one `TranscriptChunker` chunk. `AskMeetingUseCase` currently always calls it with an empty `relevantSegments` list, so for a meeting longer than ~4096 tokens' worth of transcript, an answer sourced from later parts of the meeting may be missed. Wiring `AskMeetingUseCase` to the existing `SearchRepository`/`EmbeddingEngine` semantic-search infrastructure to pre-select genuinely relevant segments (rather than "first chunk") is a good next-phase improvement, not implemented here.

### Phase 3C: Lightweight LLM tier + title generation folded into synthesis

**Lightweight model tier added**: `litert-community/Qwen2.5-0.5B-Instruct`, the `Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task` file — same publisher, license, MediaPipe loading path, and Qwen2.5-Instruct prompt format as the 1.5B model above, chosen specifically to avoid validating a second prompt template. Verified 2026-08-20 by downloading the full 546,660,344-byte file and hashing it directly (SHA-256 `e608953f169aeb1bd7b9155fec2559825e08453fc209b84eda3a781ed0452fd2`, matching HuggingFace's own `x-linked-etag`). Context length is 1280 tokens (from the file's own `ekv1280` designation) — under a third of the 1.5B build's 4096, so long transcripts need smaller chunks and more of them. **Not yet validated on-device** for structured-JSON extraction reliability; only the 1.5B model has any device-testing history so far. Candidates considered and rejected: SmolLM2 (360M/1.7B) has no `litert-community` `.task` build published, so using it would mean converting/hosting a model ourselves rather than linking a real existing release; Qwen3-0.6B uses a different prompt/chat template than what `RealMeetingIntelligenceEngine`'s prompts were built against.

Both models are exposed as `ModelTier.RECOMMENDED` / `ModelTier.LIGHTWEIGHT` on `AiModelInfo` (derived live from `ModelCatalog` by id in `AiModelEntity.toDomain()` — deliberately not a persisted Room column, since tier is a static catalog property, not per-install state). `DeviceCapabilityDetector.recommendedLlmModelId(totalRamGb)` suggests the Lightweight tier when total device RAM is under the 1.5B model's `recommendedRamMb` (4608MB) — this is only ever a starting suggestion shown in Model Manager; the user can install/select either model regardless, and nothing is auto-downloaded.

**Title generation folded into the synthesis call**: earlier in Phase 3C, `MeetingProcessingPipeline` ran a separate `generateTitle()` LLM call *before* `processMeeting()`, even though `processMeeting()`'s own synthesis prompt already asks for `{"title": ...}` alongside the summary — a redundant second LLM pass whose only output (the title) was already available from the first call for free. `generateTitle()` was removed from `MeetingIntelligenceEngine` entirely; the pipeline now takes the title from `MeetingSummary.title` (the synthesis JSON), validated by `core/common/MeetingTitleGenerator.sanitizeAndValidate()` (rejects blank output, strips stray wrapping quotes, rejects known-generic placeholders like "Meeting Summary", caps length) and falling back to `MeetingTitleGenerator.deterministicFallbackTitle(recordingType, createdAt)` — e.g. "Voice Memo — Aug 21" — built only from real, already-known data (recording type + real creation date), never fabricated, whenever the model is unavailable or its title candidate doesn't validate.

### Phase 3D: transcript readability + why summaries were coming back empty

**Transcript fragmentation (the "quarter of a sentence per line" problem).** One VAD speech interval becomes exactly one `TranscriptSegment`, and Silero VAD closes an interval at every pause longer than `minSilenceDuration`. That was set to the library's short-voice-command default of 0.25s, so ordinary mid-thought hesitation split a single spoken paragraph into a stack of fragments — sometimes two or three words each. Two changes address this at different layers:

1. `SileroVadDetector.MIN_SILENCE_DURATION_SEC` 0.25s → **0.7s**, above ordinary hesitation but below a real turn-taking pause. This also gives the ASR more acoustic context per decode.
2. `ai/pipeline/TranscriptParagraphBuilder` regroups whatever fragments remain into paragraphs, merging consecutive segments that share a speaker and are separated by under 1.5s, capped at 45s / 700 characters per paragraph so a long monologue still breaks into readable, seekable blocks. It runs **after** diarization, because speaker identity is what decides where a paragraph may legitimately continue. It never merges across a speaker change, never reorders or rewrites words, and never drops a segment.

This also reduces diarization noise indirectly: fewer, longer spans mean fewer chances for one real speaker to be split across several clusters, which was a contributor to the "one person shown as five speakers" behavior.

**Filler words.** `core/common/FillerWordCleaner` removes non-lexical hesitation sounds ("uh", "um", "er"…), comma-bracketed discourse markers ("it's, you know, complicated"), and stuttered function-word repeats. It is a *transform*, never a rewrite of stored data: Room always holds the verbatim ASR text. It is applied (a) at display time when the "Tidy Up Filler Words" setting is on — toggling it off restores the exact original with no reprocessing — and (b) always when building an LLM prompt, where hesitation noise wastes a small context window. Edit mode always shows verbatim text, so a display preference can never become a permanent rewrite of the user's transcript. The word list is deliberately narrow: "like", "kind of", and "sort of" are left alone (they are usually lexical or real hedges), and backchannel tokens like "uh-huh" are explicitly preserved because deleting them would flip a "yes" into nothing.

**Why summaries said "nothing specific was discussed".** This was a design flaw, not a model limitation. `processMeeting()` ran per-chunk extraction, then built the final title/summary from a synthesis prompt containing *only the extracted evidence* — decisions, action items, chunk summaries. A short or casual recording legitimately has no decisions and no action items, so that evidence block came back empty and the model, never having been shown what was actually said, could only report that nothing was discussed. Meanwhile Ask Meeting worked fine on the same recording, because it reads the transcript directly — which is exactly the inconsistency users hit. Three fixes:

1. **The synthesis prompt now includes the real transcript** (`renderTranscriptWithinBudget`), sized to the installed model's actual context window, keeping the head and tail with an explicit elision marker when a long transcript doesn't fit. Short recordings — the common case, and the ones this failed hardest on — fit whole.
2. **Malformed-JSON output is salvaged rather than discarded.** Small on-device models often answer with correct, transcript-grounded prose wrapped in broken JSON. `MeetingIntelligenceJsonParser.salvagePlainSummary()` recovers the prose when the schema parse fails, and the extraction prompt now puts the always-required `briefSummary` first with the four lists explicitly marked as legitimately-empty.
3. **A summary fallback chain**: synthesis summary → concatenated per-chunk summaries. A total model failure is still reported honestly as a failure rather than dressed up as an empty summary.

The UI half of the same problem is fixed too: a finished recording with no summary used to show "Generating notes on your device…" forever, which is a progress claim for work that had already stopped. It now distinguishes still-processing, no-model-installed, failed, and genuinely-no-summary, and empty Decisions/Action Items read as the real results they are rather than as a malfunction.

### Phase 4: recording type is now a first-class input, not an afterthought

**The core problem this phase fixes**: MeetingMind was treating every recording as if it were a meeting. `RecordingType` existed and reached the LLM prompt as a single guidance sentence, but the extraction *schema* was identical for every type (decisions/actionItems/questions/followUps, always), Meeting Detail always showed the same five tabs, processing always described itself as "Extracting decisions & action items," and speaker count was collected once at processing time and immediately thrown away — never persisted, never reused, and diarization ran unconditionally even on a recording the user had explicitly said was solo.

**`RecordingContext` and `IntelligenceProfile`** (`core/model/MeetingModels.kt`) are the two new pieces of shared vocabulary everything else in this phase is built on:
- `RecordingContext(recordingType, speakerCountPreference, customContext, title)` is captured once — at recording start, on import, or via a one-time "before processing" prompt if skipped — and persisted on `Meeting`/`MeetingEntity` (`speakerCountPreference: Int?`, migration 4→5) instead of living as a one-shot WorkManager input that vanished after a single use.
- `IntelligenceProfile` (`extractDecisions`/`extractActionItems`/`extractQuestions`/`extractFollowUps`, plus `sectionTitle`/`topicsLabel`/`analyzingStageLabel`) is derived per `RecordingType` via `RecordingType.intelligenceProfile()` and is the single source of truth three previously-independent, hardcoded places now read from: the LLM extraction schema, which Meeting Detail tabs/Overview rows exist, and what the processing screen's "Analyzing" step says. A Lecture's extraction prompt literally never includes a `"decisions"` key in the JSON schema it asks for — it isn't relying on prose telling the model to leave the array empty.

**Defense in depth on the schema**: even though the prompt never asks a Lecture for decisions, a model that ignores instructions is never trusted to have actually complied — `RealMeetingIntelligenceEngine.processMeeting()` drops any disabled category from the parsed result regardless of what the model returned, the same discipline already applied to hallucinated source-segment-id citations.

**Speaker count is now a real, reusable decision, not a one-time question**:
- `RecordingType.suggestedSpeakerCount()` returns `1` for solo-leaning types (Idea, Voice Memo, Dictation, Journal) and `null` (no guess) for everything else — a soft starting suggestion on the picker, never a forced value.
- The "Who's speaking?" picker (shared component: `core/ui/RecordingContextPicker.kt`, used by both `RecordingScreen` and `ImportScreen`) appears at recording start and on import. If skipped, `ProcessingScreen` asks once, before processing — and only if a preference wasn't already captured; the answer is persisted via `MeetingRepository.updateSpeakerCountPreference()` so it is never asked twice, including across a retry.
- **`speakerCount == 1` skips diarization entirely** in `MeetingProcessingPipeline` — no segmentation/embedding model load, no clustering call. This is a genuine performance win for the common solo-recording case, and it closes the exact failure mode the product spec called out: a real single-speaker recording getting shredded into several fabricated "speakers" by acoustic noise alone. ASR's segments already carry `speakerId = null`, which renders with no speaker label — nothing is invented to fill the gap.
- Home's Quick Record FAB no longer bypasses this picker (the old `quickStart` route parameter is gone). "Quick" only ever meant "a faster button to reach on Home" — it must never mean "skip telling MeetingMind what this recording is."
- Import previously created every meeting as `GENERAL` with no way to say otherwise, and had a "Load Sample" button that generated a **fake, empty (all-zero-byte) audio file** labeled "3-minute multi-speaker sample audio" — a real production gap given this codebase's otherwise strict never-fabricate discipline. Both are fixed: import now shows the same type/speaker picker recording does, and the fake-demo feature is gone.

**Diarization fragmentation quality check** (`ai/diarization/SherpaSpeakerDiarizer.kt`, `analyzeSpeakerFragmentation`/`reconcileFragmentedSpeakers`): a second, broader pass beyond the existing `mergeShortSandwichedFragments`. Flags speaker indices that together account for a small share of total speaking time (< 8% each, < 15% combined, and only when ≥ 2 such indices exist) as clustering noise rather than real participants — the "Speaker 1: 42%, Speaker 2: 3%, Speaker 3: 2%, Speaker 4: 1%, Speaker 5: 52%" pattern the product spec names directly. Flagged segments are reassigned to whichever real speaker is nearest in time, never to an arbitrary majority. Deliberately conservative: a single small-but-real minority speaker, or several genuinely balanced speakers, is never touched — both threshold gates must hold before anything is reassigned.

**Meeting Detail is now dynamic**: the previously-hardcoded `listOf("Overview", "Transcript", "Action Items", "Decisions", "Ask AI")` tab list is built from `IntelligenceProfile` (`DetailTabKind`/`DetailTab` in `MeetingDetailScreen.kt`) — Action Items/Decisions tabs simply don't exist for a Lecture or Idea, rather than existing and staying empty. The Overview tab's grouped-rows section and its "Topics" heading (now `IntelligenceProfile.topicsLabel` — "Key Concepts" for a Lecture, "Key Points" for an Idea, etc.) follow the same profile.

**Known limitation**: type-specific UI/schema differentiation covers the categories `IntelligenceProfile` actually models (decisions/actionItems/questions/followUps/topics). It does not introduce new domain fields for e.g. a Lecture's "definitions" or an Interview's "notable quotes" as separately-tracked, structured data — those are covered qualitatively by the existing `summary`/`topics` fields and the type-specific extraction prompt wording, not as new database tables. Per-exact-model prompt forking (e.g. a materially different prompt for the 0.5B vs. 1.5B vs. Phi-4-mini tiers) was also not implemented — all three currently share the same profile-driven prompt structure; model-appropriate chunk sizing already existed and is unchanged. A full quality-confidence UI (`TranscriptQuality`/`IntelligenceQuality` scoring surfaced to the user) was also not built — the fragmentation-quality check is deterministic and testable but currently silent to the user (a `Log.d` line only), matching the "internal diagnostics, not surfaced in normal UI" pattern this project already follows for performance metrics.

### Resource Management (real, not just documented intent)

Per the task's explicit "ASR → release/reuse → diarization → release/reuse → LLM → release" sequencing: `MeetingProcessingPipeline.processMeeting()` calls `SherpaEngineManager.releaseAll()` immediately after ASR completes (frees the ~650MB Parakeet allocation before diarization's much smaller models load) and again after diarization completes (frees the ~31MB diarization pair before the ~1.5GB LLM loads), then `LlmEngineManager.release()` once the intelligence stage finishes. All of VAD, ASR, diarization, and LLM inference run strictly sequentially within one coroutine — never concurrently — which is inherent to the pipeline's structure (each stage's result feeds the next), not a separately-enforced constraint. Both cancellation and unexpected-failure cleanup paths run their release calls inside `withContext(NonCancellable) { ... }` — without this, a cancelled coroutine's cleanup Room writes could silently no-op instead of running, a real bug caught by `MeetingProcessingPipelineIntegrationTest`'s cancellation test during this phase's own development.

`MediaPipeLanguageModel.generate()` catches `OutOfMemoryError` specifically and reports `AiResult.InsufficientMemory` rather than letting a failed 1.5GB allocation crash the app — this is a best-effort measure (recovering cleanly from OOM is inherently unreliable on the JVM/Android), not a guarantee.

### Processing States (real, typed)

`core/model/Enums.kt` adds `ProcessingStage`: `IDLE, PREPARING_AUDIO, DETECTING_SPEECH, TRANSCRIBING, DIARIZING, ANALYZING, SAVING_RESULTS, COMPLETED, FAILED, CANCELLED`. `MeetingProcessingPipeline.processMeeting()`'s `onProgress` callback now carries `(step: String, percent: Int, stage: ProcessingStage)` instead of just a string+percent pair, and `ProcessingJobEntity` persists `stage` alongside the existing human-readable `currentStep`/`progressPercent` fields (a real Room migration column, not a UI-only concept) so a resumed/observed job has real typed state, not just a display string. `MeetingProcessingPipelineIntegrationTest` asserts the exact real stage sequence using fake (non-native) AI implementations.

### Performance Instrumentation

`MeetingProcessingPipeline` logs (tag `MeetingMindPerf`, `Log.d`) VAD duration, ASR duration + `RTF = asrDurationMs / totalDurationMs`, diarization duration, and combined title+intelligence LLM duration for every processed meeting. This is debug-log-only, not a user-facing diagnostics screen (none existed to extend, and the task said not to expose technical diagnostics beyond what's already appropriate). **No real RTF/timing numbers are reported in this document** — they require running on a physical device, which is the same "REAL DEVICE INFERENCE VERIFIED" gap described below and in the Phase 2 completion report.

### Room / Persistence Changes

`MeetingMindDatabase` bumped to version 2 with an explicit `MIGRATION_1_2` (not a destructive fallback) — real on-device data (`meetings`, `transcript_segments`) is preserved:
- `speakers`: gains `speakerIndex INTEGER NOT NULL DEFAULT 0`, `confidence REAL` (nullable) via `ALTER TABLE ADD COLUMN` — no rows existed yet in practice (diarization was always `UnavailableSpeakerDiarizer` before this phase), but the migration is additive and safe regardless.
- `action_items`, `decisions`, `questions`: rebuilt (old tables guaranteed empty — `UnavailableMeetingIntelligenceEngine` never persisted a row) with nullable `confidence`, `sourceSegmentIdsJson` (JSON array of transcript segment ids, `org.json`-encoded, same pattern as `AiModelEntity.filesJson`), `assigneeSpeakerId`/`assigneeName` replacing the old single `assignee` string, `type` on decisions, `askedBySpeakerId` on questions.
- `follow_ups`: new table, FK to `meetings(id) ON DELETE CASCADE`, same `sourceSegmentIdsJson` pattern.
- `ai_models`: gains `contextLengthTokens INTEGER` (nullable — populated only for LLM-capable catalog entries).
- `processing_jobs`: gains `stage TEXT NOT NULL DEFAULT 'IDLE'`.

Verified by `MeetingMindDatabaseMigrationTest`, which builds a real v1-schema SQLite database via `FrameworkSQLiteOpenHelperFactory` (no `room-testing` schema-export artifact needed, since this project keeps `exportSchema = false`), runs the real `MIGRATION_1_2` object against it, and inspects the resulting schema via `PRAGMA table_info`.

### Known Limitations — Phase 2

- **No Android device or emulator was available in this development environment for Phase 2 either.** Every claim above about the sherpa-onnx diarization API, the MediaPipe LlmInference API, and every model file's size/checksum was verified directly (source inspection via `javap`/reading the real AAR classes, and downloading + hashing every model file) — but real on-device diarization accuracy, real LLM extraction quality, real latency, and real memory/thermal/battery behavior for this phase's new stages **have not been run and are not claimed as verified.**
- **Parakeet TDT real-device inference remains unverified as of the start of this phase** (per the task's explicit status: BUILD VERIFIED = yes, REAL DEVICE APP VERIFIED = yes, REAL DEVICE PARAKEET INFERENCE VERIFIED = not yet). This phase does not change that — see the Phase 2 completion report for current status.
- Robolectric cannot load the sherpa-onnx or MediaPipe native libraries, so `SherpaSpeakerDiarizerTest` and `MediaPipeLanguageModelTest` only cover the "no model installed" honest-failure path — the one path reachable without native code. The pure reconciliation logic (`reconcileTranscriptWithSpeakers`), the JSON parsing/validation logic, and the chunking logic are fully unit-tested with synthetic data since none of them touch native code.
- `MeetingProcessingPipelineIntegrationTest` exercises real pipeline orchestration (stage sequencing, Room persistence, cancellation) using fake VAD/ASR/diarizer/intelligence implementations — this proves the *plumbing* is correct, not that the *real models* produce good output.
- Diarization/LLM RAM estimates in `ModelCatalog` (diarization: 512/1024MB min/recommended; LLM: 3072/4608MB min/recommended) are estimates based on model file size + typical runtime overhead, not measured on-device figures.
- Ask Meeting's grounding is limited to one chunk's worth of transcript for long meetings — see "Ask Meeting limitation" above.

### Critical Non-Goals for This Phase (unchanged from the task scope)

Not implemented, and not started: calendar integration, Zoom/Meet/Teams bots, backend meeting-bot infrastructure, cloud AI of any kind. `AskMeetingUseCase`'s semantic-search grounding improvement (see above) is a known, deliberately deferred follow-up, not a non-goal in the same sense.

## 5. Intelligence Orchestration Layer — Stage A (Audit) + Stage B (Transcript Intelligence)

**Framing**: the product direction for this pass is that MeetingMind itself — not any single AI model — is the intelligence layer; models are workers it calls under an orchestrator's control. That target architecture is staged A (audit) → B (transcript intelligence) → C (orchestration) → D (intelligence/extraction) → E (knowledge/search). **This pass completed Stage A and Stage B only.** Stages C, D, and E are not started — see "What's deliberately not built yet" below.

**Stage A finding (no code change, informs everything else)**: much of the target architecture already existed under different names and just needed to be recognized, not rebuilt. `TranscriptParagraphBuilder` is already a real `TranscriptStructureEngine`. `FillerWordCleaner` was already a real, narrow, rule-based cleanup transform — it just ran live, inline, on every LLM prompt render rather than as an auditable pipeline stage. `SherpaSpeakerDiarizer`'s `analyzeSpeakerFragmentation`/`reconcileFragmentedSpeakers` (Phase 4) is already a real, conservative `SpeakerReconciliationEngine`. `ModelCatalog`/`AiModelInfo` already carries most of what a `ModelCapabilityRegistry` needs (`contextLengthTokens`, `minimumRamMb`, `recommendedRamMb`, `tier`) — it just isn't consulted by any selection policy yet. The one real architectural debt found: `MeetingProcessingPipeline.processMeeting()` is simultaneously cache invalidation (unconditional delete-then-rewrite of every derived table at the top of every run), stage sequencing, model construction, and persistence, with no seams to swap or partially rerun anything — this is Stage C's target, not touched in this pass.

**Stage B: raw/cleaned transcript relationship.** `TranscriptSegment`/`TranscriptSegmentEntity` gain a nullable `cleanedText` column (Room migration 5→6) held strictly separate from `text` (the real ASR/user-edited transcript, unchanged and still the only thing a hand-correction ever touches). `TranscriptCleanupEngine` (`ai/pipeline/TranscriptCleanupEngine.kt`) is a new interface with one implementation today, `RuleBasedTranscriptCleanupEngine`, which wraps the existing `FillerWordCleaner` — explicitly **not** a new model, per the standing constraint against adding model candidates without first building the abstraction; a future local-SLM-backed implementation of this same interface is the legitimate next step, not built here. `applyTranscriptCleanup()` runs the engine over every non-user-edited segment and is the pipeline's one integration point.

**Semantic drift validation.** `TranscriptQualityValidator` (`ai/pipeline/TranscriptQualityValidator.kt`) never trusts a cleanup candidate blindly: it rejects empty/unreadable output, a length ratio outside [0.4, 1.3], any digit sequence present in the raw text but missing from the candidate, any capitalized word (name/place/weekday) present in the raw text but missing from the candidate (after excluding a small stoplist of words — including every real `FillerWordCleaner` hesitation sound — that are capitalized only because they open a sentence, so a legitimate "Uh, John said..." → "John said..." cleanup is never mistaken for a dropped name), and output whose vocabulary overlaps the raw text by less than 50%. A rejected candidate is simply discarded — `cleanedText` stays null and callers fall back to the real raw text — never replaced with a fabrication. Directly unit-tested against the three adversarial cases named in the product spec ($15,000 → $50,000, Monday → Friday, John → Peter), all correctly rejected.

**Pipeline wiring.** `MeetingProcessingPipeline` runs cleanup+validation as an explicit `ProcessingStage.CLEANING_TRANSCRIPT` step ("Cleaning up your transcript...") between diarization/paragraph-building and LLM intelligence — mapped into the same visual stepper bucket as `DIARIZING` in `ProcessingScreen` rather than adding a new stepper step, so the existing visual design is untouched. `RealMeetingIntelligenceEngine.renderSegments()` now prefers each segment's cached `cleanedText` over recomputing `FillerWordCleaner.clean()` live, falling back to the live pass only for a segment with no cached cleanup yet (older data, or a rejected candidate) — fully backward compatible.

**Speaker reconciliation extended.** `analyzeSpeakerFragmentation` gains a second, independent noise signal alongside the existing duration-share one: a speaker index with many turns (≥4) whose average turn is very short (<500ms) is now also a fragmentation candidate — the "rapid clustering flicker" fingerprint, distinct from a speaker that's simply quiet overall. Both signals feed the *same* two safety gates as before (≥2 candidates required, combined share must stay under 15%), so a real participant — even one who talks in many short bursts totalling ~28% of the recording — is still never reassigned; this exact scenario is now a named regression test.

**What's deliberately not built yet (Stages C, D, E)**: `IntelligenceOrchestrator` (stage sequencing/model construction extracted out of `MeetingProcessingPipeline`), `ModelSelector` (device+task-aware model choice — today's `llmModelId` threading is unchanged), `ModelCapabilityRegistry` proper (per-task capability flags on `AiModelInfo`), processing idempotency/dependency-aware cache invalidation (the pipeline still unconditionally deletes and rewrites every derived table on every run), any chunking/extraction/synthesis changes, grounded type-prefixed titles, and any hybrid-search/Ask-Meeting-citation work. None of these were started, per the standing instruction to implement in the smallest safe increments rather than one large change.

**Tests**: 24 new unit tests this pass (`TranscriptCleanupEngineTest`, `TranscriptQualityValidatorTest`, extended `SherpaSpeakerDiarizerTest`, a new `MeetMindDatabaseMigrationTest` case, and an extended `MeetingProcessingPipelineIntegrationTest` case) — 250 total project-wide, 0 failures. `testDebugUnitTest`, `lintDebug`, and `assembleDebug` all pass.

**Known limitation**: this pass did not extend the "clean up filler words" live display toggle in `MeetingDetailScreen` to prefer the cached `cleanedText` — it still runs `FillerWordCleaner.clean()` live at display time, which is correct and unchanged behavior, just not yet reading from the new cache. Low risk, deferred as out of scope for this increment.

## 6. Transcript Structure Fix (P0): a real `TranscriptStructureEngine`, not a fixed-threshold heuristic

**Root cause, traced end to end**: real-device testing after Stage B showed transcripts — including single-speaker recordings — still reading as heavily fragmented. Fragmentation is introduced at three compounding points, only the last of which was under-built:
1. `SileroVadDetector` closes a speech interval after any ≥700ms silence (`MIN_SILENCE_DURATION_SEC`) — the root generator of raw fragment boundaries. Left untouched per the explicit "don't fix this by raising VAD thresholds" instruction.
2. `SherpaParakeetSpeechRecognizer` decodes each VAD interval as a fully independent `OfflineStream` — one interval becomes exactly one `TranscriptSegment`, verbatim, with zero merging (correctly — that isn't ASR's job).
3. The old `TranscriptParagraphBuilder` had exactly one signal (gap duration, fixed at 1500ms) plus two hard caps (45s/700 chars) to decide whether to re-merge those fragments — no recording-type awareness, no sentence-completion awareness, no single-speaker-mode leniency. A single-speaker recording with a natural 2-second thinking pause — extremely common in an Idea/Voice Memo — got split purely because of gap duration, with nothing about the content saying the thought had ended.

**Decision: replace, not extend.** `TranscriptParagraphBuilder` is deleted; `TranscriptStructureEngine` (`ai/pipeline/TranscriptStructureEngine.kt`) replaces it as the pipeline's paragraph-building stage. Extending the old single-function/single-threshold design to cover per-type policy, sentence-boundary detection, and single-speaker mode would have produced exactly the "untestable collection of heuristics" this fix was asked to avoid. The old builder's `joinText` sentence-joining logic (adds the terminal punctuation ASR omitted between two fragments, never rewrites a word) was sound and is reused verbatim as `joinFragmentTexts`. `TranscriptCleanupEngine`/`TranscriptQualityValidator` (Stage B) are unaffected — they still operate downstream on whatever paragraph text this stage produces.

**The merge decision** (`DeterministicTranscriptStructureEngine.canExtend`) reads every signal a person would actually use when reading a transcript, not gap duration alone:
- A user-edited fragment is never merged in either direction (extends Stage B's "user edits always win" discipline to structuring).
- A genuine speaker change always breaks the paragraph — unless `singleSpeakerMode` is true, in which case speakerId carries no signal (diarization was skipped for a confirmed solo recording) and is not consulted at all.
- The pause is compared against one of two thresholds from `RecordingType.transcriptMergePolicy()`: the base `maxGapMs` if the accumulated text already reads as a complete thought, or the more generous `extendedGapMs` if it doesn't — "doesn't" meaning no terminal punctuation, a trailing conjunction/preposition/article ("and", "so", "the", ...), or either neighboring fragment being one or two words long (a lone ASR emission is far more likely to be a sliver of a larger thought than a complete block on its own).
- Two hard ceilings (paragraph duration, character length) apply regardless of signal strength, so nothing merges indefinitely.

**Per-recording-type policy** (`TranscriptMergePolicy`, `core/model/MeetingModels.kt`):

| Types | maxGap / extendedGap | duration / chars | Rationale |
|---|---|---|---|
| Idea, Voice Memo, Journal, Dictation, Research | 3.0s / 7.0s | 90s / 1200 | Solo narration — thinking pauses are normal, merge aggressively |
| Lecture | 3.5s / 8.0s | 120s / 1600 | Explanatory monologue — favor long coherent paragraphs |
| Meeting, Conversation, Brainstorm | 1.5s / 3.0s | 45s / 700 | Speaker turn is the primary structural signal — merge within a turn, keep real turns apart |
| Interview | 1.2s / 2.5s | 45s / 700 | Q&A alternates quickly — slightly tighter base gap keeps exchanges crisp |
| Custom, General | 1.5s / 3.0s | 45s / 700 | Unknown content — same balanced defaults as Meeting |

**Single-speaker mode** (`TranscriptMergePolicy.boostedForSingleSpeaker`) widens `maxGapMs`/`extendedGapMs` to a floor of 4.0s/9.0s on top of whichever policy the recording type already specifies — never the duration/character ceilings, so "do not blindly merge indefinitely" still holds even for a confirmed solo recording. A type that's already more generous than this floor (Idea, Voice Memo, ...) is unaffected.

**Provenance for future word-level timestamps**: `TranscriptSegment`/`TranscriptSegmentEntity` gain `sourceSegmentIds: List<String>` (Room migration 6→7, `sourceSegmentIdsJson`) — every persisted paragraph, merged or not, lists the raw ASR fragment id(s) it was built from, earliest-first. A merge also keeps the earliest `startMs` and latest `endMs` across the group (unchanged from the old builder), which is what keeps `findActiveTranscriptSegment`'s playback-position lookup and the transcript UI's tap-to-seek (`MeetingDetailScreen`, both key off `startMs`) correct after merging — audited directly, no changes were needed there.

**Pipeline wiring**: `recordingType` resolution moved earlier in `MeetingProcessingPipeline.processMeeting()` (it now also drives structuring, not just the later intelligence-extraction step). `TranscriptStructureEngine.structure(speakerLabelledSegments, recordingType, singleSpeakerMode = expectedSpeakerCount == 1)` replaces the old `TranscriptParagraphBuilder.buildParagraphs()` call, in the same position (after diarization, before the cleanup stage).

**Tests**: 21 new tests in `TranscriptStructureEngineTest` covering every signal in isolation plus the required adversarial scenarios — single-speaker natural pauses merging, single-speaker mode still breaking a genuinely long silence, fragmented single-speaker sentences, one-word fragments, long pauses after a trailing conjunction, genuine sentence boundaries still breaking, genuine speaker changes never merging, rapid A/B/A/B alternation, a four-way Meeting/Interview/Lecture/Idea comparison at the same gap, timestamp and `sourceSegmentIds` survival across a merge, user edits never absorbed, and no word ever dropped or reordered. 259 tests total project-wide, 0 failures; `lintDebug`/`assembleDebug` both clean.

**Known limitation**: this fix is deterministic-layer-only, exactly as scoped — the ASR model and VAD threshold were deliberately left untouched, and no LLM-assisted restructuring was added. Real-device transcript readability with this change has not yet been re-verified on hardware (see Known Limitations below); the adversarial test suite proves the decision logic itself is correct against synthetic timing data, not that real Parakeet output on a real device produces the exact gap/punctuation patterns these tests assume.

## 7. AI-Assisted Transcript Cleanup: Stage B's deterministic pass gets a validated LLM upgrade

**Why this exists**: real-device testing of §6's fix showed the deterministic structure/merge layer alone was still insufficient — a purely rule-based/gap-threshold approach cannot reconstruct genuinely disfluent speech (false starts, self-corrections, mid-thought fragments) the way a real language model can. Per the explicit direction for this pass, no VAD/ASR change and no new merge heuristics were attempted; instead, a real on-device LLM was added as a *second, validated pass* on top of the existing deterministic cleanup — never a replacement for it.

### Model research and selection

**Selected: `qwen25_0_5bInstruct`, already in `ModelCatalog`** — Qwen2.5-0.5B-Instruct (litert-community build, `.task` format, runs through the existing `MediaPipeLanguageModel`/`LlmEngineManager` runtime). Nothing new was downloaded or integrated to make this choice — it was already a verified, downloadable, Apache-2.0, ungated catalog entry from Phase 3C (see §0c/Phase 3C above for its original verification). Reused here for transcript cleanup rather than only intelligence extraction because:
- It is genuinely small (546,660,344 bytes / ~546 MB) and already the lightest tier in the catalog, satisfying "prefer the smallest installed model capable of the task" without adding a new download.
- It runs through the exact same MediaPipe LlmInference runtime already integrated — zero new native dependencies, zero new runtime code, directly satisfying "reuse existing model management, do not create another disconnected model registry."
- A user who already installed it for Lightweight-tier intelligence extraction gets transcript cleanup for free, with no additional download at all.
- SHA-256: `e608953f169aeb1bd7b9155fec2559825e08453fc209b84eda3a781ed0452fd2`. Source: `https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task`. License: Apache 2.0. Context: 1280 tokens (this build's real KV-cache size). RAM: 1536 MB minimum / 2560 MB recommended.

**Candidates researched and rejected** (via live web search this pass, not from memory):
- **S1-mini (superwhisper/s1-mini)** — genuinely real and purpose-built exactly for this use case: a 0.6B fine-tune of Qwen3-0.6B whose sole job is ASR-transcript cleanup (fillers, false starts, self-corrections, spoken-number normalization), 462 MiB quantized, Apache 2.0 + naming clause. **Rejected for this pass**: confirmed via the model's own Hugging Face repo that it is distributed only as safetensors and GGUF (llama.cpp/Ollama/LM Studio ecosystem) — no LiteRT/`.task`/MediaPipe build exists. Integrating it would require adding an entirely new native inference runtime (llama.cpp Android bindings) alongside the existing MediaPipe stack, which directly violates the standing "do not create another disconnected model registry/runtime" constraint and is a much larger change than "make the deterministic layer excellent, then add one validated model call." Worth revisiting as a dedicated `TRANSCRIPT_CLEANUP`-tier model **if and when** a LiteRT/MediaPipe-compatible build appears, or if the app ever adds a second on-device runtime deliberately.
- **SmolLM2-360M** — confirmed (again via live search) that no `litert-community` `.task` build exists, consistent with what Phase 3C's own research already found for the 1.7B/360M variants. Still rejected for the same reason.
- **BERT-tiny/RoBERTa-tiny token-classification (KEEP/DELETE tagging) and Gramformer/Mini-T5 GEC models** — genuine, real categories of tiny models that exist, but no specific verified LiteRT/MediaPipe-compatible artifact was identified for either during this pass's research, and both would need a *different* inference pathway (a token-classification head, not next-token generation) than `LanguageModel`/MediaPipe LlmInference already provides. Not pursued now; `TranscriptAiCleanupEngine` is an interface specifically so a future implementation backed by either approach can plug in without touching the pipeline.

### Architecture

**`TranscriptAiCleanupEngine`** (`ai/pipeline/TranscriptAiCleanupEngine.kt`) — a new interface, deliberately separate from `MeetingIntelligenceEngine` (summarization/extraction/synthesis/Ask Meeting): its only job is `raw/structured transcript -> more faithful, readable transcript`. `RealTranscriptAiCleanupEngine` is the only implementation: reuses `TranscriptChunker` (now parameterized with smaller, cleanup-specific overhead reservations — the cleanup prompt is far terser than the extraction/synthesis schema prompts that chunker's defaults were tuned for) to pack already-structured paragraphs into calls sized to the real selected model's context length.

**Model capability system extended, not replaced**: `ModelCapability` gains `TRANSCRIPT_CLEANUP`, `EXTRACTION`, `SYNTHESIS`, `ASK_MEETING` alongside the existing `TRANSCRIPTION`/`SUMMARIZATION`/`DIARIZATION`/`EMBEDDINGS` — `SUMMARIZATION` is kept on every LLM catalog entry unchanged (so existing `LlmModelResolver` call sites/stored preferences keep working), with the new granular flags added alongside it since a general-purpose instruct LLM genuinely can serve all of these tasks. `LlmModelResolver.resolve()` gained an optional `capability` parameter (default `SUMMARIZATION`, so every existing call site is unaffected); a new `LlmModelResolver.resolveSmallestInstalled(modelStorage, capability)` returns the lightest-tier *installed* model with a given capability, or null if none — this is the actual "prefer the smallest model capable of the task" policy, and it is deliberately independent of whatever the user has selected for the (usually larger) intelligence-extraction tier. `ModelCatalog` remains the single source of truth; no second registry was created.

**Prompt contract**: every cleanup call states the fidelity rules explicitly — MUST preserve meaning/chronology/names/numbers/dates/amounts/commitments/uncertainty/contradictions/speaker identity; MAY remove fillers/repeats, repair false starts, join fragments, improve punctuation, resolve an explicitly-present immediate self-correction; MUST NOT invent, summarize, infer, or reorder. Recording-type guidance (`RecordingType.cleanupGuidance()`, same profile-driven-policy pattern as `focusGuidance()`/`transcriptMergePolicy()` — one engine, not a per-type implementation) and a single-speaker-mode note are appended, never weakening the fidelity contract itself. The model is asked for a JSON array (`[{"id":string,"text":string}]`, one entry per input paragraph) — parsed with a salvage-tolerant reader (first `[` to last `]`) that returns null (triggering a whole-chunk fallback) on anything unparseable, never a guessed partial result.

**Every candidate is validated** by the existing (Stage B) `TranscriptQualityValidator` against the segment's real raw text — never against the rule-based-cleaned text used as the model's *input*, so drift introduced at either stage is caught the same way. A rejected, unparseable, or never-attempted (user-edited, or a lone paragraph too large for the model's real budget) paragraph simply keeps whatever cleanup already produced it — never a fabricated replacement, and never destroying the rest of the transcript over one bad chunk.

### Pipeline

```
... -> TranscriptStructureEngine -> rule-based cleanup (Stage B, unchanged)
    -> [NEW] AI cleanup: resolve smallest installed TRANSCRIPT_CLEANUP model
         -> none installed: skip, log "no installed model has TRANSCRIPT_CLEANUP capability"
         -> installed: chunk -> prompt -> parse -> validate per paragraph
              -> accepted candidates overwrite cleanedText; everything else keeps the
                 rule-based result -> release the LLM engine
    -> Meeting Intelligence (unchanged) -> embeddings -> persistence
```

Implemented as a validated *upgrade* over the rule-based pass rather than a strict "AI first, deterministic as pure fallback" ordering — functionally equivalent to the requested `structure -> AI cleanup -> validation -> persist` flow (the final `cleanedText` is always the best available *validated* version), but reuses Stage B's already-tested `applyTranscriptCleanup`/`RuleBasedTranscriptCleanupEngine` machinery as-is instead of restructuring it. `MeetingProcessingPipeline` resolves `recordingType` earlier (already done in §6) and now also resolves the cleanup model id per run via `LlmModelResolver.resolveSmallestInstalled`, builds a fresh `MediaPipeLanguageModel` for it, and releases the LLM engine (`LlmEngineManager.release()`) immediately after — at most one LLM allocation resident at a time, matching every other stage's discipline. Processing-stage copy stays honest: `"Cleaning up your transcript..."` for the rule-based pass, `"Refining transcript with AI..."` only when a capable model was actually found and is really about to run; no stage claims AI cleanup happened when it didn't. Instrumentation (`Log.d(MeetMindPerf, ...)`) records duration, chunks attempted, paragraphs accepted, and paragraphs that fell back.

### Fallback behavior (three tiers, never fabricated)

1. **Best**: AI cleanup produces a validated candidate → used.
2. **No cleanup-capable model installed, or the model reports unavailable on the first attempt**: the whole AI stage is skipped (or short-circuits) → the rule-based cleanup result (already computed) is used.
3. **A specific chunk's candidate is rejected, unparseable, or too large for the model's budget**: that paragraph alone falls back to whatever it already had (rule-based cleaned text, or raw) → every other paragraph in the transcript is unaffected.

### Database

Room migration 6→7 was *not* needed for this stage — no new persisted column. AI cleanup writes into the same `cleanedText`/`sourceSegmentIdsJson` shape Stage B (migration 5→6) and §6 (migration 6→7, `sourceSegmentIdsJson`) already established; a validated AI candidate is persisted exactly like a rule-based one.

### Re-run support

The underlying capability already supports "clean again later without re-transcribing," by construction: `TranscriptAiCleanupEngine.clean()` takes only the already-persisted `List<TranscriptSegment>` plus `RecordingType`/single-speaker flag — no audio file, no ASR dependency. Calling it again on data already retrieved via `TranscriptRepository.getTranscriptDirect()` (e.g. after the user installs a better cleanup model later) works today with no additional plumbing. **Not built this pass**: the UI entry point (a "Clean transcript" / "Re-clean transcript" action on the transcript screen) — deliberately deferred per the explicit "implement the underlying architecture now, polished UI can follow" instruction.

### Tests

15 new tests in `TranscriptAiCleanupEngineTest`, run against a deterministic fake `LanguageModel` (no live model inference in any JVM test) — covers: a validated candidate being accepted; a `ModelUnavailable` first response short-circuiting the whole call; zero eligible (all user-edited) segments never touching the model at all; a validator-rejected candidate falling back without failing the whole call; malformed JSON falling back a whole chunk; a response missing an id falling back only that paragraph; a single oversized paragraph falling back without even calling the model; one failed chunk not blocking a later chunk's success; a user-edited segment never being sent to the model; recording-type-specific and single-speaker-mode prompt content; the fidelity contract always being present regardless of type; chunking splitting a long transcript into multiple calls under a small budget; and re-running cleanup purely from already-cleaned segments. 274 tests total project-wide, 0 failures. `lintDebug`/`assembleDebug` both clean.

### Known limitations

- **Not device/model-inference verified.** Every claim above about chunking, prompt construction, and validation is proven against synthetic fixtures on the JVM — real Qwen2.5-0.5B-Instruct output on real device hardware (its actual JSON-following reliability at this size, actual latency, actual acceptance rate against the fidelity validator) has not been observed. This is the single most important open question before declaring "transcript quality solved" — see the final report's explicit BUILD/UNIT-TEST/DEVICE/MODEL-INFERENCE-VERIFIED breakdown.
- The redundant-reload micro-optimization noted in-code (avoiding an unnecessary engine reload when the cleanup model and the later intelligence-extraction model happen to be identical) was not implemented — correctness (never two models resident at once) was prioritized over this speed optimization, consistent with the stated priority order (correctness > fidelity > speed > RAM > battery).
- The "clean up filler words" live display toggle in `MeetingDetailScreen` (noted as a known limitation in §5 too) still does not read from the AI-upgraded `cleanedText` path any differently than the rule-based one — no change needed there since both are stored in the same field, but worth confirming visually on-device.

## 8. Cleanup Modes, Contextual Windows, and Reprocessing: making AI cleanup tunable and testable

**Why this exists**: real-device testing of §7 showed AI cleanup was visibly working (removing fillers, improving readability) but was too conservative for the product quality bar, and transcripts could still contain ASR errors a wider context window could resolve. Per the explicit direction for this pass, the existing architecture was extended through clean seams rather than rewritten: no new transcript representation, no new model catalog, no new orchestrator was introduced. Stage G (actual AI-assisted diarization reconciliation logic) and the full generalized `IntelligenceOrchestrator`/dependency-invalidation caching system from §5 remain explicitly out of scope for this pass.

### `TranscriptCleanupMode` and `DiarizationStrategy`: real domain concepts, not prompt strings

`TranscriptCleanupMode` (`CONSERVATIVE` / `MODERATE` / `AGGRESSIVE`, `core/model/Enums.kt`) and `DiarizationStrategy` (`DETERMINISTIC` / `AI_ASSISTED` / `AUTO`) are persisted `UserPreferences` fields (`AppPreferencesState.transcriptCleanupMode`/`.diarizationStrategy`), read at recording-processing time and threaded through `MeetingProcessingWorker` → `MeetingProcessingPipeline.processMeeting()` as real parameters — not string interpolation into a prompt. The production default is `DEFAULT_TRANSCRIPT_CLEANUP_MODE = TranscriptCleanupMode.MODERATE`, a single named constant in `UserPreferences.kt` deliberately chosen to be easy to change after testing all three modes against real recordings — Conservative was **not** assumed to be the final default.

`DiarizationStrategy.AI_ASSISTED` is honestly scaffolded, not implemented: `MeetingProcessingPipeline` logs `"AI_ASSISTED diarization strategy selected but AI-assisted reconciliation is not yet implemented — behaving identically to DETERMINISTIC"` and proceeds with the same sherpa-onnx diarizer every strategy already uses. The setting is real and persists, but nothing claims a capability that doesn't exist yet — the actual reconciliation algorithm (distinguishing genuine small-participant fragmentation from diarization error) is future work.

### `TranscriptCleanupProfile`: the single source of truth combining type × mode

`RecordingType.transcriptCleanupProfile(mode)` (`core/model/MeetingModels.kt`) is the one place recording type and cleanup mode combine into a concrete policy — never two independent hardcoded copies that could drift. It returns a `TranscriptCleanupProfile` carrying: `typeGuidance` (identical text to the pre-existing `cleanupGuidance()`, unchanged), `permissivenessGuidance` (new, mode-specific: what Conservative/Moderate/Aggressive additionally allow beyond the universal MUST/MUST-NOT fidelity contract), and mode-scaled `minLengthRatio`/`maxLengthRatio`/`minWordOverlap`/`preferredModelTier`. Both `TranscriptAiCleanupEngine`'s prompt and `TranscriptQualityValidator`'s acceptance thresholds read from this same object, so a mode's prompt permissiveness and its validator leniency can never disagree with each other.

Concretely, per mode (private `CleanupModeTuning.forMode()`):
- **Conservative** — smallest possible changes (fillers, duplicated words, obvious punctuation); `minLengthRatio 0.4` / `maxLengthRatio 1.3` / `minWordOverlap 0.5` (unchanged from §7's original, unscaled thresholds — the no-regression baseline); prefers `ModelTier.LIGHTWEIGHT`.
- **Moderate** — additionally resolves self-corrections, removes abandoned thoughts, restructures awkward spoken grammar, corrects an obvious ASR mistake when nearby context makes the intended word unambiguous; `0.3` / `1.6` / `0.35`; prefers `ModelTier.RECOMMENDED`.
- **Aggressive** — polished professional transcript, substantial restructuring expected, corrects likely ASR mistranscriptions using context, normalizes terminology — still explicitly "never a summary, never an invented fact"; `0.2` / `2.0` / `0.25`; prefers `ModelTier.HIGH_QUALITY`.

### Mode-aware `TranscriptQualityValidator`, with permanent guarantees that never scale

`TranscriptQualityValidator.validate(raw, cleaned, profile)` takes an optional `TranscriptCleanupProfile` (omitted/null behaves exactly as §7's original Conservative-equivalent thresholds — zero behavior change for the deterministic rule-based caller, which has no concept of mode). What scales with mode: the length-ratio and word-overlap acceptance bounds above. What **never** scales with mode, in every mode including Aggressive:
- **Digit numbers and spelled-out cardinal number words** (`extractNumbers()` now matches both `\d+` sequences and words like "three"/"five"/"hundred") must survive exactly — "$15,000" → "$50,000", "3pm" → "5pm", and "three vehicles" → "five vehicles" are all hard-rejected regardless of mode. This is a real gap the mode-aware test suite (below) caught: the validator previously only protected digit numbers, not spelled-out ones.
- **Capitalized names/terms** must survive, case-insensitively, unless the raw text itself contains direct evidence of a self-correction — `hasSelfCorrectionEvidence()` checks whether some *other* capitalized word in the raw text both (a) made it into the cleaned text and (b) shares a ≥4-character prefix with the dropped word. This lets "Myavana ... Myavanna ... Myavana" collapse to "Myavana" (a genuine self-correction, evidenced in the raw text) while "John" → "Peter" or "Qwen" → "Gemini" stay rejected in every mode — there is no variant-spelling evidence for either swap.

### Contextual windows: neighboring paragraphs inform, but are never rewritten

Each AI cleanup call now includes the transcript paragraph immediately before and immediately after the chunk being rewritten, rendered as `CONTEXT BEFORE` / `PRIMARY TEXT` / `CONTEXT AFTER` blocks with explicit "do NOT rewrite this, do NOT include it in your response" instructions on both context blocks — the model may read a neighbor to resolve a self-correction, reference, or terminology repetition that spans a chunk boundary, but can only ever return a candidate for `PRIMARY TEXT`'s own paragraph ids. Context is drawn from every persisted segment (including user-edited ones) sorted by `startMs`, not just AI-eligible ones — a user's own edit is legitimate context for a neighbor even though it's never itself sent to be rewritten. This is deliberately **chunk-level**, not per-paragraph, context (the paragraph before the whole chunk, after the whole chunk) — a compromise between one-call-per-paragraph-with-full-neighbor-context (maximally precise, far more model calls) and the-whole-transcript-in-one-call (a tiny on-device model has no business reading an hour of audio at once).

### Model tier selection: `LlmModelResolver.resolveForModeOrNull`

`resolveForModeOrNull(modelStorage, capability, preferredTier)` picks the exact preferred tier when it's installed, otherwise degrades to the closest installed tier by ordinal distance (`ModelTier.LIGHTWEIGHT < RECOMMENDED < HIGH_QUALITY`) — never assumes the biggest installed model is automatically best for a mode that prefers a smaller/faster one, and never refuses to run just because the "ideal" tier isn't installed. Returns null only when nothing with the requested capability is installed at all, in which case `cleanTranscript()` skips the AI stage entirely and keeps the rule-based result — the same honest three-tier fallback §7 already established, now keyed off mode-aware tier preference instead of "smallest installed."

### Reprocessing without re-transcription: `cleanTranscript()` extraction + `ReprocessTranscriptCleanupUseCase`

The cleanup block inside `MeetingProcessingPipeline.processMeeting()` was extracted into a public `cleanTranscript(structuredSegments, recordingType, cleanupMode, singleSpeakerMode, onStatus)` method — reused both by a fresh processing run and by the new `ReprocessTranscriptCleanupUseCase` (`core/domain/UseCases.kt`), which re-cleans an already-persisted transcript with a different mode via `TranscriptRepository.updateCleanedText()` (whose existing SQL-level `isUserEdited=0` guard already protects user edits with zero new code). This satisfies "the user should not have to record audio again to test different cleanup modes" without a second special-cased pipeline: audio recording, ASR, and diarization are never touched by a re-clean.

**Minimal UI, deliberately** (per "only add UI necessary to test the feature, not the entire future post-processing suite"): Settings gained "Transcript Cleanup" and "Speaker Detection" sections (three-option `RadioButton` rows reusing the existing `ListRow`/`SectionCard` design-system primitives — no new composable needed) that persist `transcriptCleanupMode`/`diarizationStrategy`; Meeting Detail's existing overflow menu gained one "Re-clean Transcript" action that calls the new use case with the currently-selected mode.

### Tests

New/updated coverage, run against the existing Robolectric/JVM test infrastructure (no live model inference in any test):
- `TranscriptAiCleanupEngineTest` — 20 tests (5 new: mode-differentiated prompt permissiveness; the exact "smaller model" worked example rejected under Conservative but accepted under Aggressive; context-before/after blocks present and clearly separated from primary text; a context paragraph never returned as its own cleaned candidate; no context before the first paragraph or after the last), plus every pre-existing test updated to the new `clean(segments, profile, singleSpeakerMode)` signature.
- `TranscriptQualityValidatorTest` — 11 new tests: mode-scaled acceptance (the same restructured candidate rejected under Conservative/Moderate, accepted under Aggressive); `null` profile behaves identically to explicit Conservative; the four adversarial "never scales with mode" cases (dollar amount, day of week, name swap, **vehicle count** — the last one caught the spelled-out-number gap above) all still rejected under Aggressive; the Myavana/Myavanna self-correction acceptance; the Qwen/Gemini unrelated-swap-still-rejected guard against the self-correction exception being too permissive.
- `LlmModelResolverTest` — 4 new tests for `resolveForModeOrNull`: exact-tier match, degrade-to-closest-tier, never-further-than-closest-available, and null when nothing with the capability is installed.
- `RecordingTypeIntelligenceProfileTest` — 5 new tests proving `transcriptCleanupProfile()` actually combines type and mode correctly: same type guidance across every mode, same mode tuning across every type, permissiveness widening monotonically Conservative → Moderate → Aggressive for every recording type, and preferred model tier rising with mode.

All new/updated tests pass; full project-wide `testDebugUnitTest`, `lintDebug`, and `assembleDebug` are clean.

### Known limitations

- **Not device/model-inference verified**, same caveat as §7: mode-differentiated behavior, context-window construction, and validator thresholds are proven against synthetic fixtures on the JVM — which production default (Conservative/Moderate/Aggressive) actually produces the best real transcripts on real device hardware has not been observed, and is exactly what this stage's instrumentation (duration, chunks attempted, paragraphs accepted/fallback, mode, model) exists to help decide.
- **`DiarizationStrategy.AI_ASSISTED` has no reconciliation logic** — the setting exists, persists, and is honestly logged as a no-op fallback to `DETERMINISTIC`. Distinguishing genuine small-participant fragmentation from diarization error is real, separate future work.
- **The full `IntelligenceOrchestrator`/dependency-invalidation caching system from §5 was not built.** `ReprocessTranscriptCleanupUseCase` solves the actual "re-clean without re-transcribing" need directly; a general dependency-aware invalidation graph across every intelligence stage remains future work if a second reprocessing need (e.g. "re-extract action items without re-cleaning") arises.
- **Term/entity correction has no dedicated mechanism** — folded into Moderate/Aggressive's existing prompt permissiveness ("correct an obvious ASR mistake when nearby context makes the intended word unambiguous") rather than a separate extraction/lookup engine, per the explicit "keep the first implementation local/offline, do not invent an external knowledge lookup dependency" instruction.

## 9. Stage G: AI-Assisted Diarization Reconciliation

**Why this exists**: §8 scaffolded `DiarizationStrategy.AI_ASSISTED`/`AUTO` as real, persisted settings with an honest "not yet implemented" fallback. This stage builds the actual reconciliation logic — deliberately as a second-opinion layer on top of `SherpaSpeakerDiarizer`'s own deterministic reconciliation (`analyzeSpeakerFragmentation`/`reconcileFragmentedSpeakers`, built well before this session and already real), never a replacement for it.

### What the deterministic pass already does (confirmed by audit, not re-built)

`SherpaSpeakerDiarizer` already: merges short sandwiched fragments between two identical speakers (likely noise), and flags/reassigns speaker indices that are simultaneously (a) a small share of total speaking time and (b) either two-or-more such indices together, or a single index with many very-short turns (rapid clustering flicker) — gated so a genuine, if brief, lone participant is never touched. This is real, tested (`SherpaSpeakerDiarizerTest`), and unchanged by this stage.

### The gap this stage fills

The deterministic pass's own `MIN_NOISE_SPEAKERS_TO_FLAG = 2` gate deliberately leaves exactly one case alone: a *single* minor speaker. That is intentional — from duration/turn-count numbers alone, "one real brief participant" and "one lingering diarization fragment" are indistinguishable. There is no second numeric signal left to check. `DiarizationReconciliationEngine` (`ai/diarization/DiarizationReconciliationEngine.kt`) adds the one signal pure arithmetic can't provide: whether the minor speaker's own words plainly read as a continuation, self-correction, or restart of something a dominant speaker was already saying.

### Architecture

`computeSpeakerTranscriptFootprints(segments)` — pure, recomputes each speaker id's share of total duration, turn count, and a few sample text excerpts directly from already-diarized `TranscriptSegment`s (no change to `SpeakerDiarizer`'s interface or `SherpaSpeakerDiarizer` itself was needed). `ambiguousSpeakerIds(footprints)` flags any speaker below `AMBIGUOUS_SHARE_THRESHOLD` (0.15, the same order of magnitude as the deterministic pass's own `MAX_NOISE_SHARE_WHEN_FLAGGING`) **only when at least one other speaker sits at or above it** — a recording with no dominant speaker to anchor a merge against (every speaker minor, or a plausible near-even split like 52%/48%) returns no candidates at all, and the model is never even called.

`RealDiarizationReconciliationEngine` prompts a local instruct LLM with every ambiguous speaker's footprint + sample text under "MINOR SPEAKERS TO REVIEW" and every confident speaker's under "MAIN SPEAKERS," asking for a strict JSON array of `{"from","into","reason"}` merge proposals. Every proposal is validated before being applied: `from` must be one of the ambiguous ids actually offered, `into` must be one of the confident ids actually offered — a model that invents an id, or proposes merging two confident speakers into each other, contributes no change at all, never a guessed one. This is the same salvage-tolerant-parse-then-strict-validate shape `RealTranscriptAiCleanupEngine` already established, applied to a different domain.

### Pipeline wiring

Runs inside `MeetingProcessingPipeline`'s diarization step, after the deterministic diarizer call and before `TranscriptStructureEngine` (so structuring/cleanup/intelligence all see the reconciled speaker ids, not the raw ones):

```
diarizer.diarize(...) -> deterministic segments (unchanged, already real)
    -> computeSpeakerTranscriptFootprints -> shouldAttemptAiReconciliation(strategy)
         DETERMINISTIC: never attempts it
         AI_ASSISTED / AUTO: attempts it only when ambiguousSpeakerIds is non-empty
              -> no capable model installed: log honest skip, keep deterministic result
              -> model runs: apply only the guardrail-accepted merges, log what changed and why
```

Model capability: `ModelCapability.DIARIZATION_RECONCILIATION`, added to the same three LLM catalog entries that already carry `TRANSCRIPT_CLEANUP`/`EXTRACTION`/`SYNTHESIS`/`ASK_MEETING` — a general-purpose instruct LLM genuinely can serve this task too, no new model artifact. Resolved via the existing `LlmModelResolver.resolveForModeOrNull`, preferring `ModelTier.LIGHTWEIGHT` (this is a bounded, constrained-output classification task, not open-ended generation — no need for a larger tier by default). The cleanup engine's own `LlmEngineManager.release()` discipline is reused: at most one LLM allocation resident at a time.

`AUTO` behaves identically to `AI_ASSISTED` today (both only act when something is genuinely ambiguous) — kept as a separate `when` branch rather than collapsed into one case so a future AUTO-specific heuristic (e.g. skip on a very long recording to save battery) has a real place to grow into without changing what an explicit `AI_ASSISTED` request means.

### Tests

17 new tests in `DiarizationReconciliationEngineTest`: footprint share/turn-count computed correctly from real durations; segments with no speakerId excluded; **the product spec's own worked examples** — an 82/7/5/4/2% split flags every minor speaker, a 52/48% split flags nothing, a single 12%/88% split is flagged as a genuine candidate (not silently dismissed, not silently merged); `shouldAttemptAiReconciliation` per strategy; a valid merge reassigning segments; a hallucinated id being ignored; a proposal merging two confident speakers never even reaching the model (0 calls); an empty-array response changing nothing; malformed JSON falling back without throwing; `ModelUnavailable` surfacing so the pipeline can fall back honestly; the model never being called when nothing is ambiguous; the prompt never asking about a confident speaker.

### Known limitations

- **Not device-verified.** Everything above is proven against synthetic fixtures on the JVM — real model output on a real ambiguous two-speaker recording (does it actually catch a mid-sentence diarization split? does it over-merge on a genuinely plausible small participant?) has not been observed.
- **`AUTO` has no behavior distinct from `AI_ASSISTED` yet** — both currently mean "attempt only when ambiguous," as documented above.
- **Sample text excerpts are the first 3 turns per speaker, truncated to 160 characters** — a deliberate, simple choice for prompt-budget reasons; a more targeted "excerpts immediately adjacent to the ambiguous speaker's turns" selection (closer to what actually reveals a mid-sentence split) is a reasonable future refinement, not attempted here to keep this stage's scope bounded.

## 10. AI Tools menu — architecture prep only (no new engines built)

The user's product spec for the transcript workspace's forthcoming "✨ AI Tools" menu (Transcript / Analysis / Utilities, 18 actions) is captured as a single source of truth — `TranscriptAiToolType`/`TranscriptAiToolCategory`/`TranscriptAiToolRegistry` (`core/model/TranscriptAiTool.kt`) — so a future session can wire up real UI and engines without re-deciding names, grouping, or scope, and so the eventual UI reads this list instead of hardcoding it.

Each entry carries a `TranscriptAiToolReadiness`: `READY` (Clean Transcript — already backed by `ReprocessTranscriptCleanupUseCase`, reachable today via Meeting Detail's "Re-clean Transcript" action), `DATA_EXISTS_NEEDS_UI` (Find Decisions/Questions/Action Items, Identify Topics, Extract Key Points — the underlying data is already extracted and persisted during normal processing; these need a menu item and a place to show what already exists, not a new AI engine), or `NOT_STARTED` (the remaining 12 — Fix Transcription Errors, Improve Clarity, Remove Repetition, Condense, Expand Context, Fix Terminology, Find Important Moments, Find Names & Organisations, Explain This, Rewrite Professionally, Create Notes, Create Outline, Generate Title-on-demand — genuinely new work).

**Deliberately not built this pass**: no new prompt contracts, no new validators, no new use cases, no UI. Per the standing "only add UI/architecture necessary to test the current feature, don't build the entire future post-processing suite in one task" constraint — this is exactly what the registry itself documents, so that constraint doesn't have to be re-explained at the top of every future session that picks one of these up. Building one out means following `TranscriptAiCleanupEngine`'s established shape (dedicated engine interface, explicit MUST/MAY/MUST-NOT prompt contract, validation before anything is accepted, honest three-tier fallback) — never a second, differently-shaped mechanism.

## 11. Learned Vocabulary (Phase 15 §4)

**Why this exists, and what it explicitly is not**: ASR keeps mistranscribing the same names/jargon the same wrong way. "Self learning" here means a persisted `surfaceForm -> canonicalForm` lookup table the app consults, **not** retraining or fine-tuning any model on-device — nothing in `core/repository/VocabularyRepository.kt` touches Parakeet, the diarizer, or the LLM's weights. See `docs/ARCHITECTURE.md` §4c for the editor-side half of this (Replace/Replace All).

**Schema**: `VocabularyEntity` (`vocabulary` table, migration 9→10) — `surfaceForm`, `canonicalForm`, `type` (`VocabularyTermType`, honestly `OTHER` until something categorizes it — never guessed), `confidence` (1.0 for every entry today, since the only source is an explicit user correction — there's no inference to be less than fully sure about), `source` (`VocabularySource.REPLACE_ALL` today; `SEGMENT_EDIT` exists in the enum for a future source but nothing writes it yet — a single hand-edit isn't a reliable enough signal that it's a *terminology* correction rather than a one-off reword), `frequency`, `lastConfirmedAt`. Global across recordings, not scoped to one meeting — keyed uniquely by `surfaceForm` so a repeated correction strengthens the existing row (`frequency++`, `lastConfirmedAt` refreshed) instead of duplicating it.

**Write path**: wired into exactly one place — `MeetingDetailViewModel.replaceAllInTranscript()` calls `VocabularyRepository.recordCorrection()` after a successful Replace All. This is deliberate: Replace All is an explicit "this word is wrong everywhere" action, unlike a single-segment text edit which could just as easily be an ordinary reword. Nothing else in the codebase writes to this table yet.

**Read path — real lexical similarity, not a prompt dump**: `VocabularyRepository.findRelevantTerms(text, limit)` tokenizes `text` and fuzzy-matches each word against every entry's `surfaceForm` via word-level Levenshtein distance, with tolerance scaled by term length (0 for ≤3 chars — an exact match only, or almost anything would false-positive; 1 for ≤6; 2 above that — the range of near-misses ASR actually produces). Returns only entries that actually matched something in `text`, sorted by `frequency`, capped at `limit` — never the whole table. **Not yet called from anywhere** — no AI tool exists yet to consume it (that's Phase 6+); the retrieval function is built and tested (`VocabularyRepositoryTest`) ahead of having a caller, same shape as `TranscriptAiCleanupEngine`'s prompt-contract precedent.

**Deliberately not built this pass**: a settings UI to browse/edit/delete learned terms; injecting `findRelevantTerms()` results into any prompt (there is no rewrite/cleanup prompt this phase touches — that's Phase 6); a `type` classifier (every entry is honestly `OTHER` until one exists).

## 12. AI Toolkit Infrastructure + User Identity (Phase 15 §5)

**The gap**: the only existing background-AI mechanism, `MeetingProcessingWorker`, is permanently specific to the meeting-processing pipeline. Everything else — `proposeCleanup()`/`applyCleanupProposal()`/`reprocessCleanup()` in `MeetingDetailScreen`'s ViewModel — runs on a bare `viewModelScope.launch`, tied to that ViewModel's lifecycle. Kill the app mid-run and the operation is just gone, with no record it was ever requested.

**What was built — infrastructure only, no new tool engines** (per the Phase 15 plan, individual tools are Phase 6+):
- `AiJobEntity`/`AiJobDao` (`ai_jobs` table, migration 10→11): a persisted row for every background AI Tools run — `toolType`, `status` (`AiJobStatus`: QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELLED), `progressPercent`/`progressStep`, `inputPayloadJson`/`resultPayloadJson`, `errorMessage`, `retryCount`. Written as QUEUED *before* WorkManager is ever touched, so a screen reading `AiJobRepository.getJobsForMeeting()` sees the request immediately, and reopening the app after it was killed mid-run reads the real last-known state instead of losing track of it.
- `AiToolWorker` (`ai/pipeline`): the generic dispatcher every future tool run goes through — reads one job by id, dispatches on `toolType`. Exactly one branch is real today, `CLEAN_TRANSCRIPT` (delegates to the pre-existing `ReprocessTranscriptCleanupUseCase`, since it already exists and needed no new engine). Every other tool type fails the job honestly — `"$label isn't wired up yet — not run."`, persisted to Room as FAILED — never a fabricated result. Cancellation (via `CancellationException`) is recorded as CANCELLED, not lumped in with FAILED.
- `AiJobRepository`: `enqueue()`/`cancel()`/`retry()` — cancel marks CANCELLED in Room immediately rather than waiting for WorkManager's cooperative cancellation to actually stop the worker (which only happens at its next suspend checkpoint); retry only acts on FAILED/CANCELLED jobs and increments `retryCount`.

**Not yet switched over**: `MeetingDetailScreen`'s `proposeCleanup()`/`reprocessCleanup()` call sites still use their original `viewModelScope.launch` path — `AiToolWorker`'s CLEAN_TRANSCRIPT branch proves the infrastructure works end-to-end, but moving the UI to actually go through `AiJobRepository.enqueue()` instead is Phase 6 work (that's where the diff-review UI these jobs will feed also gets built), to avoid destabilizing an already-working review flow in what's meant to be an infrastructure-only pass.

**User Identity** (same phase, unrelated mechanism): `AppPreferencesState.userName` (DataStore) — collected on a new onboarding step, typed by the user, never pre-filled from the device name, Google account, or contacts. Optional: staying blank is a valid choice. Explicitly **not** used for the single-speaker "You" label (see `docs/ARCHITECTURE.md` §4b — that stays a literal, source-derived default regardless of this preference) — it exists for personalization surfaces like Ask AI addressing the user by name, which is Phase 8 work.

## 13. Fix Terminology + Diff UI Audit (Phase 15 §6)

**Audit finding — the "real word-level diff, not markdown strikethrough" item was already done.** `WordDiff.kt` (`core/common`) already wraps `java-diff-utils` for genuine word-level diffing, and `CleanupReviewScreen`/`DiffText.kt` already render its output (green-underlined changed words, strikethrough "Show original"), not markdown. The only real gap was test coverage — `WordDiff` had none despite non-trivial delta-cursor arithmetic. Added `WordDiffTest` (insert/delete/replace/no-op/blank-input cases) rather than rewriting anything that already worked.

**FIX_TERMINOLOGY — wired for real, no new LLM prompt contract**: `FixTerminologyUseCase` (`core/domain`) applies every correction `VocabularyRepository` has learned (from Replace All) to a transcript in one pass. Deliberately not a new AI engine: it calls `VocabularyRepository.findRelevantTerms()` to narrow the vocabulary down to entries worth checking, then `TranscriptRepository.replaceAllInTranscript()` — the exact same deterministic, exact-match replace Replace All itself performs — for each. Wired end-to-end through `AiToolWorker` (Phase 5's job queue) and the "AI tools" bottom sheet; `TranscriptAiToolType.FIX_TERMINOLOGY`'s readiness is now `READY`.

**Bug found and fixed while wiring this**: `VocabularyRepository.findRelevantTerms()`'s relevance filter only ever compared single tokenized words against a vocabulary entry's `surfaceForm` — so a multi-word correction like "Sherpa Onix" → "Sherpa-ONNX" could never match anything, since no single word in a transcript equals a two-word phrase. Fixed: a multi-word `surfaceForm` now gets an exact, case-insensitive substring check (the same match `replaceAllInTranscript` itself performs, so "relevant" can never diverge from "would actually get replaced"); a single-word `surfaceForm` keeps the fuzzy word-level check. Caught by `FixTerminologyUseCaseTest` and `AiToolWorkerTest` both initially failing with zero changes for a realistic multi-word correction; regression-covered directly in `VocabularyRepositoryTest`.

**Not built this pass**: the other 17 tools stay `NOT_STARTED`/`DATA_EXISTS_NEEDS_UI` and correctly say so in the AI tools sheet — each remaining `NOT_STARTED` entry genuinely needs a new LLM prompt contract (`TranscriptAiCleanupEngine`'s established shape: explicit MUST/MAY/MUST-NOT contract, validator, honest fallback), which is out of scope for what a single pass can respectably cover; a diff-review UI specifically for FIX_TERMINOLOGY's bulk multi-segment change also wasn't built — its result currently surfaces only via the transcript itself updating and a toast, not a review screen (CLEAN_TRANSCRIPT's existing `CleanupReviewScreen` stays the one tool with a review step).

## 14. Analysis Tools + Overview Integration (Phase 15 §7)

**Audit finding**: `TranscriptAiToolType`'s `DATA_EXISTS_NEEDS_UI` entries were honestly labeled but under-delivered on their own claim. `FIND_DECISIONS`/`FIND_ACTION_ITEMS` really were already shown (Overview's Decisions/Tasks steps), but `FIND_QUESTIONS` and `IDENTIFY_TOPICS`/`EXTRACT_KEY_POINTS` were not — `MeetingDetailViewModel.questions`/`.topics` were fetched from Room but `topics` was never even collected into Compose state, so `TopicEntity` rows existed with zero UI anywhere in the app. Tapping any of these five tools in the AI Tools sheet just showed one generic "already shown on Overview" toast regardless of tool — which for two of the five was not true.

**Fixed**: `OverviewStepper` gained two new steps — Questions (gated by `IntelligenceProfile.extractQuestions`, same pattern as Decisions/Tasks) and Topics (always present, per `IntelligenceProfile.topicsLabel`'s own doc — "the always-present key points/concepts/topics list"). `EXTRACT_KEY_POINTS` and `IDENTIFY_TOPICS` both point at the same Topics step, since they're the same underlying `Topic` data under a type-varying label, not two separate features.

**Real navigation, not just a toast**: `OverviewStepTarget` is a new public enum `OverviewStepper` accepts as an optional `jumpTo` param — the AI Tools sheet's `onDataAlreadyAvailable` now switches to the Overview tab and passes the right target (DECISIONS/TASKS/QUESTIONS/TOPICS) instead of a single generic message for all five tools. The step-list construction itself (`buildOverviewSteps`) was pulled out as a pure function specifically so it's unit-testable without a Compose test harness.

**Not built this pass**: `FIND_IMPORTANT_MOMENTS` and `FIND_NAMES_ORGANIZATIONS` stay `NOT_STARTED` — genuinely new extraction, not a UI gap on existing data.

## 15. Ask AI Persistence + Personalised Context (Phase 15 §8)

**Audit finding — persistence was already real, no work needed.** `ChatMessage` rows are written to Room (`saveChatMessage`) for both the user's question and the assistant's answer inside `AskMeetingUseCase`, and `MeetingDetailViewModel` already reloads them via a Flow, so Ask AI history already survives process death and app restarts. Nothing was fabricated or half-built here; this pass found a real, working feature and moved on rather than rewriting it.

**Real bug found and fixed**: `AskAiPanel`'s message composer had no `imePadding()`, so on devices where the soft keyboard doesn't already resize the window, the keyboard could cover the input field — the same class of inset bug fixed earlier on the recording confirmation screen. Fixed with `Modifier.imePadding()` on the panel's root `Column`.

**Personalisation — "context selection, not dumping all memory"**: the spec's own phrase is now structurally enforced, not just a convention. `AskPersonalizationContext` (`core/model/Vocabulary.kt`) is a new, narrow data class — a nullable `userName` plus a `relevantVocabulary: List<VocabularyEntry>` — and it is physically the only channel `AskMeetingUseCase` has for passing anything personal into a prompt: it cannot carry the whole learned-vocabulary table or arbitrary user data, because nothing else is on the type.

`AskMeetingUseCase.invoke()` now takes an optional `userName` (passed in per call, not read from DataStore inside the use case, so it stays free of a DataStore dependency), and computes `relevantVocabulary` by calling the existing `VocabularyRepository.findRelevantTerms(question)` — the same relevance filter Fix Terminology uses (Phase 15 §6), scoped to the question actually asked, never the full vocabulary table. `MeetingDetailScreen.askQuestion()` reads the user's name from `UserPreferences` once per question and passes it through. The whole path is threaded through `MeetingIntelligenceEngine.askMeeting()`'s interface (default-valued so every existing caller/fake keeps compiling) into `RealMeetingIntelligenceEngine.buildAskPrompt()`, which only emits a name line and/or a "known terminology" line when the corresponding field is actually non-empty — an empty `AskPersonalizationContext` (the default) produces byte-identical prompts to before personalisation existed.

**Tests**: `AskMeetingUseCasePersonalizationTest` (Robolectric + in-memory Room) pins down that a supplied name reaches the context, a missing name stays `null` rather than being guessed, only vocabulary relevant to the actual question is included (not the whole learned table), and a use case built with no `VocabularyRepository` wired degrades to empty vocabulary instead of crashing. `RealMeetingIntelligenceEngineAskPersonalizationTest` pins down the prompt text itself: a set name reaches the prompt, a relevant vocabulary entry's surface form and canonical form both reach the prompt, and — the regression this whole design is meant to prevent — an empty context adds nothing detectable to the prompt at all.

**Not built this pass**: no UI was added for a user to *edit* the corrected transcript segments' terms from the Ask AI panel itself, and personalisation does not yet extend to other AI tools beyond Ask Meeting (e.g. summary generation) — those tools don't currently accept any per-user context parameter, so extending personalisation to them is a separate, later change, not part of §8's scope (Ask AI specifically).

## 0d. Status Update — Phase 3A: Recording Type Focus Guidance

Phase 3A added `RecordingType` (Meeting, Interview, Lecture, Voice Memo, Idea, Brainstorm,
Dictation, Conversation, Research, Journal, Custom, General) as a per-recording attribute, chosen
before recording starts and persisted on the `Meeting` row (Room migration `MIGRATION_2_3`). This
only ever affects one thing in the AI layer: `RealMeetingIntelligenceEngine.buildExtractionPrompt()`
now appends one extra guidance line — `RecordingType.focusGuidance()` for a built-in type (e.g.
"This is an interview. Focus on the questions asked and the answers given...") or, for `CUSTOM`,
the user's own free-text focus description, followed immediately by an explicit reiteration that
this guidance "never overrides" the grounding rule.

**The grounding requirement is unconditional and unchanged by this feature.** The base instruction
— "Extract ONLY information explicitly supported by the transcript... never invent names, dates,
deadlines, decisions, or commitments that are not actually stated" — is always present in the
prompt regardless of recording type or custom context; focus guidance narrows *what to pay
attention to*, never *what may be reported*. Verified by
`RealMeetingIntelligenceEngineFocusTest`, which asserts both that a type's/custom context's
guidance text reaches the prompt and that the grounding instruction is present alongside it in
every case, including a blank custom-context string (falls back to no extra guidance rather than
injecting an empty/broken instruction).

This phase's other additions — global playback (`PlaybackService`/`PlaybackController`),
WorkManager-backed background processing (`MeetingProcessingWorker`), Sharesheet integration,
and Markdown/CSV/PDF/DOCX export — are productization/UX work, not AI-pipeline changes; they don't
alter any AI interface's contract or grounding behavior. See `docs/ARCHITECTURE.md` §7 and the
Phase 3A completion report for those.

## 1. Historical State (Before This Pass — see `docs/AUDIT.md` for full findings)

| Interface | Then-current implementation | What it did |
|---|---|---|
| `VoiceActivityDetector` | `EnergyAndSpectralVad` | Computes `sin(time)` — never reads the audio file. Fabricated. |
| `SpeechRecognizer` | `RealSpeechRecognizer` | Cloud call to `gemini-2.5-flash` via raw HTTP (real transcription, but not local); falls back to placeholder strings like `"Voice audio segment (12s)"` if no API key/offline. |
| `SpeakerDiarizer` | `AcousticClusterDiarizer` | Alternates "Speaker N" by turn index / pause length. No acoustic analysis. |
| `MeetingIntelligenceEngine` | `RealMeetingIntelligenceEngine` | Cloud call to `gemini-3.5-flash` for summary/decisions/action items/Q&A; falls back to keyword/regex pattern matching (not ML) if offline. |
| `EmbeddingEngine` | `LocalEmbeddingEngine` | Real, local, deterministic 64-dim hash-based bag-of-words vector. Not a trained/semantic embedding model, but it is genuinely on-device. Unchanged by this pass — see §4. |

Model catalog (`ModelRepository`) listed 5 fictitious models with fabricated download URLs pointing at a non-existent domain (`models.meetmind.internal`); "download" was two `delay()` calls and a boolean flip.

**Network dependency (now removed)**: whenever `GEMINI_API_KEY` was configured, the app uploaded full audio recordings and full transcript text to `https://generativelanguage.googleapis.com`. This directly contradicted the product's local-first/privacy requirement and was the top-priority item resolved in this pass.

## 2. Target Architecture

```
                         ┌─────────────────────────┐
                         │   MeetingProcessingPipeline │
                         └─────────────┬────────────┘
                                       │
        ┌──────────────┬──────────────┼──────────────┬───────────────┐
        ▼              ▼              ▼               ▼               ▼
 VoiceActivityDetector SpeechRecognizer SpeakerDiarizer MeetingIntelligenceEngine EmbeddingEngine
        │              │              │               │               │
   (interface)     (interface)    (interface)      (interface)     (interface)
        │              │              │               │               │
   real local VAD  real local ASR  real local      real local LLM   real local
   (e.g. Silero    (e.g. whisper.cpp diarization    (e.g. small       embedding
   VAD via ONNX     or ONNX Runtime  (embeddings +   quantized        model (e.g.
   Runtime Mobile)  Mobile, quantized clustering)     instruction     a small
                     tiny/base)                        model, 1-3B,   sentence-
                                                        GGUF/ONNX)     embedding
                                                                       model)
```

All five interfaces already exist and are already the injection point used by `MeetingProcessingPipeline` — no call-site changes are needed to swap implementations, only new classes.

### Model management — now fully real (updated in Phase 1, see §0b)
- `ModelRepository` (`core/repository/Repositories.kt`) reads/writes through `AiModelEntity`/`AiModelDao`, merged with real on-disk state from `ModelStorage`, so installed state survives process death. **Done.**
- `ModelStorage` → `LocalModelStorage` (`ai/modelmanagement/ModelStorage.kt`) is real: app-private storage at `filesDir/models/{modelId}/`, with an `.installed` marker file only written after every file in the model is verified. **Done.**
- `ModelVerifier` → `Sha256ModelVerifier` (`ai/modelmanagement/ModelVerifier.kt`) is a real, working SHA-256 implementation. **Done.**
- `ModelDownloader` (`ai/modelmanagement/ModelDownloader.kt`) now has a real implementation, `OkHttpModelDownloader` — streaming, resumable, retrying, cancellable. **Done as of Phase 1** — see §0b for details. `UnconfiguredModelDownloader` remains available for any future catalog entry without a real source.
- The `wifiOnlyDownload` preference (`UserPreferencesManager`) still isn't consumed by `OkHttpModelDownloader` — still open, not yet wired in.
- Deletion (`ModelRepository.deleteModel`) removes the on-disk file and flips `isInstalled = false` in Room. **Done.**

### Cloud Gemini path — resolved: removed entirely
Of the three options previously weighed here, **option 1 was chosen**: `GeminiApiClient` and everything that called it were deleted outright, along with the `GEMINI_API_KEY`/secrets-gradle-plugin/`.env` plumbing and the now-unused `retrofit`/`moshi`/`okhttp`/`firebase-ai` dependencies. There is no cloud AI "boost" mode, flag, or dormant code path left in the app — the class does not exist (`PrivacyNoCloudPathTest` asserts this). If a cloud bridge is ever wanted again as an explicit, disclosed, opt-in feature during the transition to real local models, it should be re-added through Firebase AI Logic (server-side attestation via App Check, no client-embedded API key) rather than reintroducing a hand-rolled REST client — but that is a future product decision, not something this pass left half-done.

## 3. Model Selection: Decided vs. Still Guidance

- **ASR — DECIDED (Phase 1)**: NVIDIA Parakeet TDT 0.6B v3, INT8 ONNX export, via sherpa-onnx `OfflineRecognizer`. See §0b for the verified model files/checksums and runtime config.
- **VAD — DECIDED (Phase 1)**: Silero VAD via sherpa-onnx's bundled `Vad` class. See §0b.
- **Diarization — DECIDED (Phase 2)**: pyannote/segmentation-3.0 (int8) + 3D-Speaker CAM++ (English) via sherpa-onnx's `OfflineSpeakerDiarization`, exactly the path this section previously flagged as worth evaluating first. See §0c.
- **LLM — DECIDED (Phase 2; tiers added Phase 3C/3D)**: three real, ungated `.task` models run through MediaPipe's LlmInference API — Qwen2.5-0.5B-Instruct (Lightweight), Qwen2.5-1.5B-Instruct (Recommended), and Phi-4-mini-instruct 3.8B (Highest quality, MIT, ~3.6 GiB). Only the 1.5B has any device-testing history; the other two are verified as real downloadable artifacts but not yet validated on hardware. See §0c for why Gemma (gated) was ruled out. Expect materially lower summary/extraction quality than a cloud model of this scale; set product expectations accordingly (see `docs/AUDIT.md` §J).
- **Embeddings — still guidance, not implemented**: a small sentence-embedding model (e.g., a distilled/quantized MiniLM-class model) via ONNX Runtime Mobile, replacing the current hash-based placeholder.

**Before integrating any specific model, re-verify its license terms** — `THIRD_PARTY_NOTICES.md` currently pre-declares licenses for models that aren't actually integrated; treat it as a template to complete once real choices are made, not a source of truth today.

## 4. RAM / Performance Tiers

`DeviceCapabilityDetector` already estimates total RAM and recommends a model tier (`recommendedAsrModelId`, `recommendedLlmModelId`) based on it. This is good groundwork that nothing currently consumes for actual model selection at runtime (only for a display recommendation during onboarding). The target: `ModelRepository`/pipeline should actually honor the selected/recommended model, and should refuse to load a model whose `minimumRamMb` exceeds the device's available RAM, degrading to a smaller tier automatically with a visible explanation to the user rather than crashing or silently underperforming.
