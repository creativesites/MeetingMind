# MeetMind — Roadmap

Prioritized work remaining, based on the findings in `docs/AUDIT.md`. Priorities: **P0** (required for core MVP), **P1** (important, follows P0), **P2** (polish/secondary), **FUTURE** (explicitly out of scope — see `docs/FUTURE_BACKEND.md`).

The primary P0 flow that must work end-to-end before secondary features get attention:
```
Record → Persist audio → Process → ASR → Transcript → Local intelligence → Summary → Action items → Meeting detail
```
Recording, persistence, and the meeting-detail UI already work today. Everything from "Process" through "Summary/Action items" currently runs on cloud calls or fabricated output — that's the real gap.

---

## Already Done (This Session)

- ✅ Generated and committed a Gradle wrapper pinned to `gradle-9.3.1-bin.zip`, matching the AGP 9.1.1 requirement. `./gradlew` now self-bootstraps correctly on any machine.
- ✅ Verified `./gradlew assembleDebug`, `testDebugUnitTest`, and `lintDebug` all pass (0 compile errors, 0 lint errors).
- ✅ Full repository audit (`docs/AUDIT.md`), architecture documentation (`docs/ARCHITECTURE.md`, `docs/AI_ARCHITECTURE.md`), and this roadmap.

## P0 — Required for the Core MVP

1. ✅ **DONE (P0 pass)** — **Resolve the privacy violation** (`docs/AI_ARCHITECTURE.md` §0). Cloud Gemini path removed entirely; every AI interface honestly reports `AiResult.ModelUnavailable` instead of fabricating output.
2. ✅ **DONE** — **Fix `debugConfig` signing**.
3. ✅ **DONE (Phase 1)** — **Real local VAD** — Silero VAD via sherpa-onnx's `Vad`. See `docs/AI_ARCHITECTURE.md` §0b.
4. ✅ **DONE (Phase 1)** — **Real local ASR** — NVIDIA Parakeet TDT 0.6B v3 (INT8) via sherpa-onnx's `OfflineRecognizer`, not Whisper (a materially stronger model was available at comparable size — see §0b for why). Real-device inference verification status is tracked in the Phase 1/Phase 2 completion reports, not assumed from a successful build.
5. ✅ **DONE (Phase 1)** — **Real model management** — `OkHttpModelDownloader`, real SHA-256 verification, real on-disk + Room install state.
6. ✅ **DONE (Phase 2)** — **Real local LLM for summary/action items/decisions/questions** — Qwen2.5-1.5B-Instruct via MediaPipe's `LlmInference`, with structured JSON extraction (never free-form regex) and explicit decision/suggestion/discussion/possibility classification. See `docs/AI_ARCHITECTURE.md` §0c.
7. **WorkManager-backed processing** — still open. Real local ASR/diarization/LLM inference now takes real wall-clock time (potentially minutes for a long meeting); processing still runs in a `viewModelScope` coroutine tied to the Compose screen's lifecycle, not `WorkManager`, so it does not yet survive the app being fully backgrounded/killed. Wire to the already-existing `ProcessingJobEntity` (which now also carries a real `ProcessingStage`, see §0c).

## P1 — Important, Follows the Core MVP

8. ✅ **DONE (Phase 2)** — **Real speaker diarization** — pyannote/segmentation-3.0 + 3D-Speaker CAM++ via sherpa-onnx's `OfflineSpeakerDiarization`, reconciled against ASR segments by real timestamp overlap. See `docs/AI_ARCHITECTURE.md` §0c.
9. **Real local embeddings** — still open. The hash-based bag-of-words vector (`LocalEmbeddingEngine`) is unchanged; this directly improves both semantic search and Ask Meeting quality.
10. **Real RAG for Ask Meeting** — partially done. `RealMeetingIntelligenceEngine.askMeeting()` (Phase 2) is real and grounded, but `AskMeetingUseCase` still passes an empty `relevantSegments` list rather than doing top-k retrieval via item 9's embeddings first, so grounding is limited to one transcript chunk for long meetings — see `docs/AI_ARCHITECTURE.md` §0c "Ask Meeting limitation".
11. **Real video audio extraction** — still open.
12. ✅ **DONE (P0 pass)** — **Real Google Sign-In** — Credential Manager → Google ID token → `FirebaseAuth.signInWithCredential`.
13. **Resolve the dead use-case layer** — still open (`StartRecordingUseCase`, `StopRecordingUseCase`, `ImportRecordingUseCase`, `DeleteMeetingUseCase`, `DownloadModelUseCase` remain unwired; `TranscribeMeetingUseCase`'s signature was kept current with the pipeline in Phase 2 but it is still not called from any ViewModel).
14. ✅ **DONE, ongoing** — **Real automated tests** — 85 unit tests as of Phase 2 (up from 46 after Phase 1), covering AI-unavailable paths, diarization reconciliation, LLM JSON validation/hallucination-rejection, pipeline stage sequencing (via fakes), Room migrations, and speaker-rename persistence. Native inference itself remains untestable on the JVM — see `docs/AI_ARCHITECTURE.md` "Known Limitations".

## P2 — Polish / Secondary

15. **Firestore metadata sync** — wire the already-correctly-scoped (metadata-only) `FirestoreSyncManager` into the meeting-save flow, gated behind the user being signed in and (ideally) an explicit sync-enabled preference (`cloudSyncEnabled` already exists in `UserPreferencesManager` and is unused).
16. **Room migrations + `exportSchema = true`** — replace `fallbackToDestructiveMigration()` before any real release; check schema JSON into version control.
17. **Theme consistency** — audit screens using hardcoded `Dark*` color constants instead of `MaterialTheme.colorScheme`, so light/dynamic theming behaves correctly everywhere.
18. **Rename package/applicationId** from the AI-Studio template defaults (`com.example` / `com.aistudio.meetmind.qxynvp`) to a real owned identity before any release build.
19. **CI/CD** — add a basic GitHub Actions workflow running `assembleDebug`, `test`, and `lint` on every push/PR, now that the build is reproducible.
20. **Accessibility pass** — content descriptions, TalkBack behavior; requires a real device/emulator to verify (not verifiable in this audit's environment).
21. **`THIRD_PARTY_NOTICES.md` correction** — update to reflect whatever models are actually integrated (item 4, 6, 8, 9), rather than the current pre-declared, not-yet-true list.

## FUTURE — Not Part of This MVP

Calendar integration (Google/Outlook), meeting bots (Zoom/Meet/Teams), Slack/Notion/Asana/Salesforce integrations, cloud meeting-agent infrastructure, team collaboration, full cloud transcript storage. See `docs/FUTURE_BACKEND.md`. The `MeetingSource.REMOTE_BOT` enum value and `core/future/FutureMeetingInterfaces.kt` scaffolding already exist and should remain untouched (inert) until this phase actually begins.

---

## Suggested Execution Order

Given what's already real vs. fake, the order above roughly matches dependency order, but the practical grouping is:

1. **Trust & build hygiene** (items 1–2) — must happen first, cheap, unblocks everything else honestly.
2. **The local AI core** (items 3–7) — the expensive, high-value engineering work; do in the listed order since each stage consumes the previous stage's output.
3. **Quality & retrieval** (items 8–11) — improves what's already functionally working end-to-end after step 2.
4. **Auth & architecture cleanup** (items 12–14) — can be parallelized with step 3 by a second workstream since they don't depend on the AI work.
5. **Polish** (items 15–21) — after the above, before a real release.
