# MeetMind — AI Architecture

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
- **Diarization — still guidance, not implemented**: realistic target is a lightweight speaker-embedding model (d-vector/x-vector class) + simple clustering (e.g., agglomerative) over VAD segments — full state-of-the-art diarization is out of scope for a phone. sherpa-onnx also ships speaker-diarization support (`OfflineSpeakerDiarization`), which should be evaluated first before sourcing a separate runtime, since it would reuse the AAR/native libs already integrated in Phase 1.
- **LLM — still guidance, not implemented**: a small (1–3B parameter) instruction-tuned, quantized (Q4) model runnable via `llama.cpp` (GGUF) or the MediaPipe LLM Inference API. Expect materially lower summary/extraction quality than Gemini; set product expectations accordingly (see `docs/AUDIT.md` §J).
- **Embeddings — still guidance, not implemented**: a small sentence-embedding model (e.g., a distilled/quantized MiniLM-class model) via ONNX Runtime Mobile, replacing the current hash-based placeholder.

**Before integrating any specific model, re-verify its license terms** — `THIRD_PARTY_NOTICES.md` currently pre-declares licenses for models that aren't actually integrated; treat it as a template to complete once real choices are made, not a source of truth today.

## 4. RAM / Performance Tiers

`DeviceCapabilityDetector` already estimates total RAM and recommends a model tier (`recommendedAsrModelId`, `recommendedLlmModelId`) based on it. This is good groundwork that nothing currently consumes for actual model selection at runtime (only for a display recommendation during onboarding). The target: `ModelRepository`/pipeline should actually honor the selected/recommended model, and should refuse to load a model whose `minimumRamMb` exceeds the device's available RAM, degrading to a smaller tier automatically with a visible explanation to the user rather than crashing or silently underperforming.
