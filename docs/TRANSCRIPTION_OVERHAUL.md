# Transcription, Diarization & Intelligence Overhaul

Phase 1 deliverable: an audit of the repository as it actually exists, the root causes of the
observed transcript defects, and the implementation plan that follows from them.

Companion documents: `docs/AI_ARCHITECTURE.md` (the existing local-AI contract, still binding),
`docs/ARCHITECTURE.md` (module layout).

---

## 1. What exists today

### 1.1 The local pipeline, as wired

`ai/pipeline/MeetingProcessingPipeline.kt` (655 lines) is the single orchestrator. Its real
control flow is:

```
audio file
  -> SileroVadDetector.detectSpeechIntervals()      -> List<SpeechInterval>
  -> SherpaParakeetSpeechRecognizer.transcribe()    -> List<TranscriptSegment>  (ONE PER INTERVAL)
  -> SherpaSpeakerDiarizer.diarize()                -> the same segments, +speakerId
  -> (optional) RealDiarizationReconciliationEngine -> the same segments, speakers merged
  -> DeterministicTranscriptStructureEngine.structure() -> fewer, merged segments ("paragraphs")
  -> TranscriptCleanupEngine + TranscriptAiCleanupEngine -> same segments, +cleanedText
  -> RealMeetingIntelligenceEngine.processMeeting() -> MeetingSummary
  -> LocalEmbeddingEngine.embed(segment.text)       -> EmbeddingEntity rows
  -> Room
```

Component inventory:

| Area | File | Verdict |
| --- | --- | --- |
| VAD | `ai/vad/SileroVadDetector.kt` | **Retain.** Real Silero VAD via sherpa-onnx. Its tuning constants are compensating for a downstream problem (see §2) and get relaxed. |
| ASR | `ai/asr/SherpaParakeetSpeechRecognizer.kt` | **Refactor.** The Parakeet/sherpa-onnx decode itself is correct and stays; what changes is *what audio it is handed* and *what it emits*. |
| Diarization | `ai/diarization/SherpaSpeakerDiarizer.kt` | **Retain the acoustic half, replace the attribution half.** pyannote + CAM++ + clustering is real and good. `reconcileTranscriptWithSpeakers()` (segment-level majority overlap) is replaced by word-level attribution. |
| Diarization noise filters | `mergeShortSandwichedFragments`, `analyzeSpeakerFragmentation`, `reconcileFragmentedSpeakers` | **Retain.** These operate on raw acoustic turns, which is the correct layer. |
| AI diarization second opinion | `ai/diarization/DiarizationReconciliationEngine.kt` | **Retain**, unchanged. |
| Structuring | `ai/pipeline/TranscriptStructureEngine.kt` | **Retain as the paragraph layer**, re-pointed at utterances instead of VAD fragments. Its signal set (punctuation, trailing conjunctions, short fragments, gap thresholds) is sound and is reused. |
| Cleanup | `ai/pipeline/TranscriptCleanupEngine.kt`, `TranscriptAiCleanupEngine.kt`, `TranscriptQualityValidator.kt` | **Retain**, unchanged. |
| Intelligence | `ai/llm/RealMeetingIntelligenceEngine.kt` | **Retain**, unchanged; gains a second, cloud implementation behind the same interface. |
| Persistence | `core/database/*` (schema v11, 11 migrations, no destructive rewrite of user data) | **Extend** with new tables; never rewrite. |
| Background | `ai/pipeline/MeetingProcessingWorker.kt`, `AiToolWorker.kt` | **Retain.** |
| Model management | `ai/modelmanagement/*` | **Retain.** `ModelCatalog` is the existing single source of truth for local model IDs; the new router extends that idea to remote models rather than replacing it. |

### 1.2 The canonical representation, as it exists

`core/model/MeetingModels.kt`:

```kotlin
data class TranscriptSegment(
    id, meetingId, speakerId?, speakerName?, startMs, endMs, text, confidence?,
    isUserEdited, cleanedText?, sourceSegmentIds: List<String>, words: List<TranscriptWord>
)
data class TranscriptWord(text, startMs, endMs)   // no id, no speaker, no confidence, no source
```

`TranscriptSegment` is the unit of persistence (`transcript_segments`), of UI rendering, of
search, of embeddings, of playback sync, of export, of user editing (split/merge/reassign), and
of intelligence provenance (`sourceSegmentIdsJson` on action items/decisions/...). It is load
bearing in roughly 40 files. **It is not going to be deleted.** Words already exist beneath it
(`wordsJson`, schema v10) but are inert: nothing reads them except playback highlighting.

---

## 2. Root causes of the observed defects

This is the part that matters. Each defect in the brief traces to a specific line.

**Problem A — one sentence becomes several tiny pieces.**
`SherpaParakeetSpeechRecognizer.transcribe()` loops `for (interval in effectiveIntervals)` and
emits exactly one `TranscriptSegment` per VAD interval. *A VAD boundary is literally a transcript
boundary.* Worse, it is also an **ASR context boundary**: Parakeet decodes each interval as an
isolated stream, so it never sees the words on the other side of a 700 ms breath. That costs
accuracy, not just formatting.

**Problem E — long sentences lose context around pauses.** Same line. The acoustic context
window is whatever Silero happened to cut.

The existing mitigations are both downstream compensations for this one upstream fact:
- `SileroVadDetector.MIN_SILENCE_DURATION_SEC = 0.7f` (library default 0.25) — its own comment
  says it was raised because "one VAD interval becomes exactly one transcript segment".
- `DeterministicTranscriptStructureEngine` — re-glues the fragments *after the fact*, which can
  restore formatting but can never restore the acoustic context the ASR was denied.

**Problem B/C — one speaker split across labels, identities flip.**
`reconcileTranscriptWithSpeakers()` assigns each ASR segment the single speaker with maximum time
overlap. When a VAD interval straddles a real speaker change (common: A finishes as B starts, no
700 ms of silence between them), the whole interval — every word — is given to whichever speaker
held more milliseconds. Half the words are then confidently wrong, with no record that the
decision was close. There is no confidence on the attribution at all, and
`SpeakerEntity.confidence` is hardcoded `null`.

**Problem D — short responses incorrectly merged.** `canExtend()` treats
`isVeryShortFragment(next.text)` (<= 2 words) as *evidence to merge*. A backchannel "Yeah." from
another speaker is exactly a <= 2-word fragment. It is protected only by the speaker-change check
— which is exactly the check Problem B/C corrupts.

**Problem F — unnatural paragraphs.** The structure engine merges *VAD fragments*, whose
boundaries are acoustic, so the paragraph boundaries it can choose from are a subset of acoustic
pause positions. It can never place a boundary where a person would.

**Problem G — technical terminology corrupted.** `TranscriptionOptions.prompt` exists and is
never read. There is no vocabulary biasing of any kind at the ASR layer, and
`core/model/Vocabulary.kt` + `VocabularyRepository` (which already learn the user's terms) feed
only the LLM cleanup prompt.

**Cross-cutting:** every downstream layer currently compensates for the layer above it. The AI
cleanup engine fixes what structuring couldn't, structuring fixes what VAD broke, the diarization
reconciliation LLM fixes what clustering broke. That is the architecture problem the brief names.

---

## 3. The change

One sentence: **introduce the word/time layer that already half-exists, make every downstream
stage consume it, and let each layer answer only its own question.**

```
audio
 -> SpeechRegion[]            "where is speech?"              (VAD, unchanged engine)
 -> AsrWindow[]               "what should ASR hear at once?" (AsrContextBuilder, NEW)
 -> CanonicalWord[]           "what was said, and when?"      (ASR + AsrWindowReconciler, NEW)
 -> SpeakerTurn[]             "who was talking, and when?"    (diarizer's raw turns, unchanged)
 -> CanonicalWord[+speakerId] "who said each word?"           (WordSpeakerAttributor, NEW)
 -> Utterance[]               "where are the turns?"          (UtteranceBuilder, NEW)
 -> Paragraph[]               "how should this read?"         (structure engine, re-pointed)
 -> CanonicalTranscript       the source of truth             (NEW)
 -> TranscriptSegment[]       the existing persistence/UI view (projection, unchanged shape)
```

The last line is the compatibility strategy: `CanonicalTranscript` **projects to**
`List<TranscriptSegment>`. Every existing screen, export, search, embedding and editing path keeps
working against the type it already knows, and gains better content. Nothing in `feature/` has to
change to get the fix.

### 3.1 New components (package `com.example.ai.transcript`)

| Component | Answers | Determinism |
| --- | --- | --- |
| `AsrContextBuilder` | Which speech regions are decoded together | Pure, configurable (`AsrWindowConfig`), unit-tested |
| `AsrWindowReconciler` | Overlap dedup across windows | Pure, longest-common-subsequence over word text+time |
| `WordSpeakerAttributor` | Word -> speaker, with `HIGH/MEDIUM/LOW` | Pure |
| `SpeakerTurnBuilder` | Contiguous same-speaker word runs | Pure |
| `UtteranceBuilder` | Turn -> sentence-ish units, backchannel aware | Pure |
| `CanonicalTranscriptAssembler` | Assembles + projects to `TranscriptSegment` | Pure |
| `TranscriptQualityEvaluator` | Engineering diagnostics + routing | Pure |

Every one of them is a pure function over data classes with no Android and no native dependency,
so all of it is unit-testable on the JVM — which is the only way any of this can be verified
without a device.

### 3.2 Gemini / Internet mode (package `com.example.ai.cloud`)

Added as a *processing profile*, behind `ProcessingProfile.INTERNET`, never as a fallback from
offline mode. `ai/cloud/GeminiTransport` is the one seam that touches the network; the model IDs
live only in `ai/routing/AiModelRouter`. Two-pass (verbatim -> smart) with
`TranscriptFusionEngine` aligning smart text onto verbatim's timestamped word stream by
deterministic token alignment — the LLM is never asked to produce a timestamp or a speaker id.
`GeminiChunkManager` + `GlobalSpeakerResolver` handle long recordings.

---

## 4. Implementation order and status

| Phase | Scope | Status |
| --- | --- | --- |
| 1 | Audit + plan (this document) | done |
| 2 | Transcript representation rebuilt around words/timestamps (`CanonicalWord`, `CanonicalTranscript`) | done |
| 3 | VAD regions separated from ASR segmentation (`SpeechRegion`, `VoiceActivityDetector`) | done |
| 4 | ASR context windows + overlap reconciliation (`AsrContextBuilder`, `AsrWindowReconciler`) | done |
| 5 | Diarization turns -> word attribution -> speaker turns (`WordSpeakerAttributor`, `SpeakerTurnBuilder`) | done |
| 6 | Natural utterance/paragraph construction (`UtteranceBuilder` + the existing structure engine) | done |
| 7 | `CanonicalTranscript` + projection to `TranscriptSegment`; Room 11->12 | done |
| 8 | Gemini transcription integration (`GeminiTranscriptionEngine`, `GeminiTransport`) | done |
| 9 | Verbatim + smart fusion (`TranscriptFusionEngine`) | done |
| 10 | Chunking + global speaker reconciliation (`GeminiChunkPlanner`, `GlobalSpeakerResolver`) | done |
| 11 | Gemini Flash intelligence with structured output + provenance (`GeminiIntelligenceEngine`) | done |
| 12 | Ask Meeting / RAG grounding (`TranscriptRetriever`) | done |
| 13-14 | Gemini Live, Live extended thinking | **partial** — state machine and async tool lifecycle done and tested; no transport ships, see `## 6` |
| 15 | Benchmark suite | **partial** — metrics done and tested (`TranscriptBenchmark`); no corpus, see `## 6` |
| 16 | Regression against existing meetings and features | done for everything testable without a device — see `## 6` for what is not |

### 4.1 What runs, per profile

```
OFFLINE    SileroVadDetector -> AsrContextBuilder -> SherpaParakeetSpeechRecognizer
           -> AsrWindowReconciler -> SherpaSpeakerDiarizer -> WordSpeakerAttributor
           -> CanonicalTranscriptAssembler -> cleanup -> RealMeetingIntelligenceEngine

INTERNET   GeminiChunkPlanner -> Gemini verbatim -> GlobalSpeakerResolver
           -> AsrWindowReconciler -> Gemini smart -> TranscriptFusionEngine
           -> CanonicalTranscriptAssembler -> cleanup -> GeminiIntelligenceEngine
```

Both profiles converge on the same `CanonicalTranscript`, so everything downstream — UI, search,
embeddings, Ask Meeting, exports, provenance — is identical regardless of which one ran.

The fallback is one-directional. `ProcessingProfile.INTERNET` retains the local routes so a quota
error, timeout or unconfigured build falls through to on-device processing and the user still gets
a transcript; the meeting is then recorded as `OFFLINE`, because that is what actually happened to
it. `ProcessingProfile.OFFLINE` has no cloud route at all and cannot fall the other way.

### 4.2 Regression (Phase 16)

Everything the change could break that can be checked without a device is checked:

- **Existing meetings stay readable.** Migration 11→12 adds defaulted columns only; a seeded
  pre-overhaul meeting survives untouched and reports `processingVersion = 0`. A word persisted
  before word ids existed deserializes with honest "not known" defaults rather than being treated
  as corrupt.
- **Every existing feature still passes.** The 395 tests that existed before this work still pass,
  covering transcript editing (split/merge/speaker reassignment), search, export, playback sync,
  cleanup, AI tools and the processing worker. Two needed updating, both because they asserted on
  a segment id string that is now derived from the canonical transcript's utterances — no caller
  may assume a particular id.
- **Privacy did not regress.** `PrivacyNoCloudPathTest` now also pins that the pipeline's default
  transport is unconfigured, that `OFFLINE` is given no route requiring the network, and that every
  path resolving a processing profile defaults to `OFFLINE`.

### 4.3 Test coverage

544 unit tests, all passing (`./gradlew testDebugUnitTest`). The structural layer is pure Kotlin
with no Android or native dependency, so all of it is covered on the JVM. Two real defects were
found by these tests and fixed: a `Long.MIN_VALUE` sentinel overflowing in the timestamp-anomaly
check, and a duplicate-phrase detector that only probed a fixed three-word period and therefore
missed the four-word repeat an unreconciled overlap actually produces.

## 5. Non-negotiables carried forward from `AI_ARCHITECTURE.md`

- No fabricated output anywhere. `AiResult` stays the only way a stage reports itself unavailable.
- Offline mode makes zero network calls. The cloud engines are unreachable from the offline
  profile by construction, not by a runtime flag.
- A downstream failure never destroys a valid upstream result.
- Never log transcript text.

## 6. Explicitly not done here

Recorded so the next session starts informed, not so it is quietly dropped:

- **Real-recording validation. This is the gating item before shipping.** No audio corpus and no
  device in this environment, and no Gemini credential. Every claim in this document comes from
  unit tests over synthetic word streams and a scripted cloud transport, not from a WER
  measurement or a live API call. Problems A-G are addressed *by construction* — the code path
  that caused each one is gone, and there are tests showing the new path does not reproduce them
  on representative input — but they are not yet *demonstrated* fixed on audio. Nobody should
  claim this overhaul worked until a real recording that currently produces a bad transcript has
  been run through both pipelines and compared.
- **Benchmark corpus (Phase 15), partially done.** `TranscriptBenchmark` implements the §32
  metrics — WER, CER, speaker attribution error, speaker fragmentation, speaker confusion,
  timestamp error, duplicate rate, fragmentation rate — against a hand-checked
  `ReferenceTranscript`, with fourteen tests over cases whose answers are known by construction. It
  shares its alignment with the fusion engine, because a metric computed by a different aligner
  than the pipeline uses measures the difference between two aligners as much as between two
  transcripts.

  What is missing is the **corpus**: the recordings listed in §32 (crosstalk, accented English,
  code switching, 30+ minute meetings, and the rest) and their reference transcripts. Adding one
  means adding a `ReferenceTranscript` and calling `score`; no code here has to change. Paragraph
  quality still has to be scored by a person — there is no metric for "does this read like
  something a human would write", and inventing one would be exactly the fake precision §31 warns
  against.
- **Gemini Live transport (Phases 13-14), partially done.** What ships is the part that could
  actually be got right here and is the part most likely to be got wrong elsewhere:
  `LiveSessionController`, an explicit state machine that never infers idleness from
  `turnComplete` (an extended-thinking model keeps reasoning after it stops speaking, so a client
  that ends the interaction there truncates it mid-thought — and short answers look fine while it
  does), plus non-blocking tool dispatch that keeps consuming server events while several tools
  are in flight. Eleven tests cover those two behaviours.

  What does **not** ship is a `LiveTransport` implementation. A bidirectional streaming session
  against the real endpoint cannot be built or verified with no credential and no device, and a
  transport written blind is untested code that looks finished. There is also no live audio
  capture path and no live UI. Both remain to be built against the real API.
- **Backend proxy for API keys (§35).** `GeminiTransport` is the entire client-side surface that
  has to change, and the app ships `UnconfiguredGeminiTransport` — there is no credential in the
  APK, so Internet mode reports itself unavailable on an unmodified build rather than working via
  a committed key. The authenticated backend itself is not built.

- **A word-level UI.** The canonical transcript carries per-word speakers and attribution
  confidence, and the segment projection preserves them, but no screen renders a LOW-confidence
  attribution differently yet. The data is there for it.
