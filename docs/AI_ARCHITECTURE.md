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
- **LLM — DECIDED (Phase 2, tier added Phase 3C)**: Qwen2.5-1.5B-Instruct (q8, litert-community `.task` build, Recommended tier) via MediaPipe's LlmInference API, plus Qwen2.5-0.5B-Instruct (Lightweight tier, not yet device-validated) for lower-RAM devices. See §0c for why Gemma (gated) was ruled out. Expect materially lower summary/extraction quality than a cloud model of this scale; set product expectations accordingly (see `docs/AUDIT.md` §J).
- **Embeddings — still guidance, not implemented**: a small sentence-embedding model (e.g., a distilled/quantized MiniLM-class model) via ONNX Runtime Mobile, replacing the current hash-based placeholder.

**Before integrating any specific model, re-verify its license terms** — `THIRD_PARTY_NOTICES.md` currently pre-declares licenses for models that aren't actually integrated; treat it as a template to complete once real choices are made, not a source of truth today.

## 4. RAM / Performance Tiers

`DeviceCapabilityDetector` already estimates total RAM and recommends a model tier (`recommendedAsrModelId`, `recommendedLlmModelId`) based on it. This is good groundwork that nothing currently consumes for actual model selection at runtime (only for a display recommendation during onboarding). The target: `ModelRepository`/pipeline should actually honor the selected/recommended model, and should refuse to load a model whose `minimumRamMb` exceeds the device's available RAM, degrading to a smaller tier automatically with a visible explanation to the user rather than crashing or silently underperforming.
