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

1. **Resolve the privacy violation** (`docs/AI_ARCHITECTURE.md` §2). This is the single most urgent item: the app currently ships a false "never leaves your device" claim while uploading audio/transcripts to a cloud API by default. Decide and implement one of the three options in `AI_ARCHITECTURE.md` before any further AI work — everything else builds on top of whichever path is chosen.
2. **Fix `debugConfig` signing** so a fresh clone builds without a manual step (use AGP's default debug signing instead of a missing custom keystore file). Low effort, removes a real onboarding-friction bug for every future contributor.
3. **Real local VAD** — replace `EnergyAndSpectralVad`'s sine-wave fabrication with actual PCM decode + energy/spectral analysis (or integrate Silero VAD). Every downstream stage (ASR segmentation, diarization) currently depends on VAD output that means nothing today.
4. **Real local ASR** — bundle a quantized model (Whisper tiny/base recommended) and an inference runtime (whisper.cpp/NDK or ONNX Runtime Mobile). This is the single highest-value, highest-effort item — nothing about "meeting notetaker" works without real transcription.
5. **Real model management** — wire `ModelRepository` to `AiModelEntity`/`AiModelDao`, implement real download/progress/checksum/delete. Required before item 4 is usable by an actual user (they need to be able to install the model item 4 needs).
6. **Real local LLM for summary/action items/decisions/questions** — replace the regex/keyword fallback with a real small quantized instruction model. Can land after ASR (3–5) since it consumes the transcript ASR produces.
7. **WorkManager-backed processing** — once real local ASR/LLM inference replaces the current near-instant fake/cloud paths, processing will take real wall-clock time (potentially minutes for a long meeting); it must survive the app being backgrounded. Wire to the already-existing `ProcessingJobEntity`.

## P1 — Important, Follows the Core MVP

8. **Real speaker diarization** — replace the turn-alternation heuristic with embeddings + clustering. Depends on VAD (3) and benefits from ASR (4) landing first.
9. **Real local embeddings** — replace the hash-based bag-of-words vector with a small local sentence-embedding model; this directly improves both semantic search and Ask Meeting quality.
10. **Real RAG for Ask Meeting** — retrieval (top-k relevant chunks via the improved embeddings from item 9) → local LLM (item 6) → grounded answer with timestamp references, replacing both the cloud full-transcript-stuffing path and the offline keyword-quote fallback.
11. **Real video audio extraction** — use `MediaExtractor`/`MediaMuxer` (already imported, unused) to actually demux the audio track instead of copying the whole video file.
12. **Real Google Sign-In** — wire the already-present Credential Manager/`googleid` dependencies to `FirebaseAuth.signInWithCredential`, replacing the hardcoded mock-profile button in Settings.
13. **Resolve the dead use-case layer** — wire `StartRecordingUseCase`, `StopRecordingUseCase`, `ImportRecordingUseCase`, `TranscribeMeetingUseCase`, `DeleteMeetingUseCase`, `DownloadModelUseCase` into the ViewModels that currently duplicate their logic (or delete them if intentionally abandoning the pattern — wiring them in is preferred, since they're already correctly written).
14. **Real automated tests** — unit tests for the AI interfaces (with fakes), repositories, and the processing pipeline; replace/supplement the default AI-Studio boilerplate tests, which currently test nothing about this app.

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
