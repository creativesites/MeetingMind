# Companion audit: Notes & Faith assistants (Phase 0/1)

Status: audit + design artifact, awaiting Winston's approval before any production change.
Scope: `feature/assistant/*`, `ai/assistant/Assistant.kt`, `ai/faith/*`, `feature/faith/*`, `feature/bible/*`, the Zuri companion (`core/ui/mm/companion/*`), and their prompts/tests.

## 1. Current architecture

**Notes and Faith already share one assistant** — this is the single most important finding. There is no separate Faith chatbot to merge; the work is reliability and presentation, not consolidation of two engines.

- Entry points: `NoteEditorScreen.kt`'s "Assistant" menu item opens `AssistantSheet` bound to `NoteEditorViewModel.assistant` (one `AssistantSession` per note, held in `viewModelScope`, survives sheet dismiss and rotation). `FaithScreens.kt` opens the same `AssistantSheet` bound to `LibraryAssistantViewModel.faith` (a note-less, library-wide session).
- Shared protocol (`ai/assistant/Assistant.kt`): `AssistantTool` (11 tools), `AssistantProtocol.parse` (lenient JSON-or-prose parsing of the model's `{"say", "calls"}` reply), `AssistantHost` interface, `AssistantEngine` (up to 4 tool-calling rounds), `FaithGuard` (strips "voice of God" phrasing from Faith replies only).
- `AppAssistantHost` (`feature/assistant/AssistantHost.kt`) is the one implementation of every tool, for both spaces — the only branch is `scope.faith`.
- Prompts are versioned Markdown assets (`app/src/main/assets/prompts/*.md`), each with required sections (contract, grounding, citations, forbidden, prompt), parsed by `Prompts`/`PromptAsset`.
- A separate visual **Companion mascot, "Zuri"** (`core/ui/mm/companion/`, spec in `docs/mvp/ZURI_EXPERIENCE.md`) already exists: a Canvas/Rive-drawn character with states (Idle, Listening, Thinking, Celebrating, Worried, Sleepy, Curious, Proud, Reading), six accent colours, and an explicit spec section (§8, "Ask Zuri") that says the existing assistant should be *re-skinned* under this identity, not replaced.
- **The Zuri mascot is wired into Notes screens only** (`NoteEditorScreen`, `NotesHome`, `NotesScreen`) and not into any `feature/faith/*` screen — so today Notes has a companion face and Faith does not, even though they share the underlying assistant.

Beyond the shared chat assistant, three further AI surfaces touch Faith specifically: `AskEverything` (a separate RAG-style cross-library answer engine, also shared with Notes), `AskSermon` (sermon-specific transcript grounding for the in-recording "Ask" feature), and `PrayerCompanion` (a Gemini Live *voice* persona for guided prayer — unrelated code to the Zuri mascot despite the name overlap).

## 2. What already works and should be preserved

- Tool-calling loop with read tools (note, transcript via vector retrieval, other notes via FTS, scripture, cross-refs, commentary, translations, originals) and write tools (insert with instant Undo, confirm-gated replace/delete with before/after preview, create_task, create_note).
- `insert_scripture` always pulls real verse text from the Bible store; `insert_blocks` is explicitly forbidden from containing verse text, and prose quotes ending in a reference are rewritten into a real Scripture block (`AssistantHost.quotedScripture`) — a concrete, working anti-hallucination mechanism.
- `FaithGuard.clean()` programmatically strips "God is telling you…" style sentences even if the model ignores the prompt, while preserving reported speech ("your note says God told him…") — unit-tested.
- Confirm/Undo plumbing (`AssistantAction.State`: PENDING/APPLIED/UNDONE/DISCARDED) is already a complete, correct state machine.
- Context-aware suggested prompts (`AssistantSession.suggestions()`) already branch on sermon / faith-note / plain-note / library scope.
- Send-button double-submit guard (`if (_working.value != null) return`) and stop/cancel wiring already exist.

## 3. Broken, partial, confusing, or duplicated behavior

- **Four separate implementations of the same theological guardrail** ("never claim to speak for God"), already slightly diverged: `FaithGuard.AUTHORITY` in `Assistant.kt`, `AskSermon.AUTHORITY`, the prose list in `faith_contract.md`, and `PrayerCompanion.BOUNDARIES`. `FaithGuard` includes "God told me"; `AskSermon`'s list does not. A fix to one does not propagate to the others.
- **"Companion" names two unrelated things**: the Zuri mascot UI (Notes-only today) and `PrayerCompanion` (a Faith-only voice persona). No shared code or concept links them.
- The Zuri mascot is not present on any Faith screen, despite Faith already using the same underlying assistant engine the mascot is meant to represent.
- `Prompts.ALL` (the prompt manifest) omits `notes_assistant`, even though the file exists and loads correctly at runtime (confirmed by `AssistantTest.promptAssetsLoadWithRequiredSections`) — a stale manifest, not a live bug, but worth fixing in passing.
- `AssistantSession.send()` calls `CloudAi.transport(app)` twice per send — once to build the engine, once to check `refreshConfigured()` — constructing two independent transport instances that both re-resolve the same persisted credentials. Harmless today, wasteful and worth simplifying.
- Open product/licensing question (per `agents/STATUS.md`): Read to Me currently sends the full text of every Bible translation to Gemini, including licensed-only translations that are deliberately never persisted on-device. This is a data-safety decision pending the owner, not a design decision — but it directly affects whether a cloud-synthesized Faith answer (Screen B) can safely ever include licensed verse text.
- `feature/work/ScopedAskSheet.kt` / `ai/assistant/ScopedAsk.kt` is a third, simpler "Ask" surface in the Work space — a sibling, not sharing this engine. Out of scope for this project but worth the team's awareness so a future pass doesn't assume there are only two assistants in the app.

## 4. Root causes of the "must tap Try Again" reliability problem

**Confirmed in code, highest-impact:**

1. **A silent, uncaught-exception path with no error bubble at all.** `AssistantSession.send()`'s coroutine body is `try { … } finally { _working.value = null }` — there is no `catch`. `host.outline()` (called unguarded inside `AssistantEngine.reply`) and `resolveScope()`/`host()`'s lazy initialization (direct Room queries, launched fire-and-forget from `init {}`) are not wrapped in `runCatching`. If either throws — e.g. a cold/migrating database — the `finally` clears the working spinner, but **no `AssistantMessage(error = true)` is ever appended**. The result: the spinner stops, the screen looks idle, there is no error text and therefore no "Try Again" button to tap (that button only renders when the last message has `error == true`) — the person has to retype their message from scratch, not just retry. This is very likely the dominant cause of the reported symptom, and it is a materially different bug than "retry doesn't work" — there frequently *is no retry affordance at all* on this path.
2. **Every transport failure collapses into one untyped string.** `AiResult.Failed(message)` carries no classification — network timeout, HTTP 429 quota, 401 auth, a safety-blocked empty response, and a permanently unconfigured key all produce the same generic `AssistantMessage(error = true)` with the same "Try Again" button. A permanently-unconfigured-key failure and a one-off dropped connection look and behave identically to the user, so "just tap Try Again" becomes the only taught behavior even when it cannot possibly help.
3. **No automatic bounded retry anywhere** — every retry today is the fully manual `AssistantSession.retry()`, triggered only by the person tapping the button. There is no backoff/retry for a plainly transient failure (a single dropped packet, a 503) before surfacing an error at all.
4. **`retry()` discards the whole failed turn, including any actions it already applied.** If a multi-round reply fails on round 2+ after round 1 already executed a write tool (task created, block inserted — a real, applied side effect via `AppAssistantHost.write`), the failure path does carry those actions into the shown error message, but `retry()` unconditionally drops the last message (and the user message before it) and resends the original prompt. The already-applied action card disappears from the visible conversation, and the model — which has no memory of what it already did — can be asked to redo the same work, risking a duplicate task or duplicate note insert.
5. **Up to ~4 minutes of apparently frozen UI in the worst case**: each of the engine's up to 4 rounds uses a hardcoded 60s timeout; `onProgress` only fires *after* a tool call completes, not during the network wait itself (the bulk of the latency), so a slow round shows only a static "Thinking…" with no incremental signal.

**Plausible, not reproduced end-to-end:**
- A safety-blocked or empty-candidate Gemini response (`GeminiHttpTransport.extractText` returning null) surfaces as a generic "Gemini returned no usable content" failure — if the block is content-triggered and deterministic, "Try Again" will reliably fail again identically, with no way for the person to tell the two cases apart.
- Whether `viewModelScope`'s default `SupervisorJob`/`CoroutineExceptionHandler` is what turns finding #1's uncaught exception into a silent "nothing happened" rather than a crash — consistent with the founder's report of a stuck-feeling UI rather than a crash, but not confirmed against a device log.

## 5. Recommended improvements, ranked

1. **(Highest impact, lowest risk)** Add the missing `catch` in `AssistantSession.send()`, and wrap `host.outline()` / `resolveScope()` in `runCatching`, so every failure path — not just transport failures — reliably produces an `AssistantMessage(error = true)` with a visible Retry affordance. This alone plausibly fixes most of the reported symptom.
2. Classify `AiResult.Failed` (and friends) into retryable vs. permanent causes (network/timeout/5xx vs. auth/quota/unconfigured/safety-block), and route permanent causes to a "fix this setting" action instead of a Try Again button that cannot succeed.
3. Add a small bounded automatic retry with backoff and jitter for transient transport failures, before any error reaches the UI.
4. Make `retry()` resume rather than discard: keep already-applied actions visible, and only re-ask the model to do what the turn had not yet finished, with simple idempotency/duplicate-detection around write tools.
5. Surface incremental "still working" progress during the network wait itself (a real elapsed-time or stage line, never a fake percentage), and consider lowering/segmenting the 60s-per-round timeout so a hung round surfaces sooner.
6. Consolidate the four theological-guardrail implementations into one shared module.
7. Decide, with the Zuri spec as the baseline, whether Faith adopts the existing mascot (as §8 of `ZURI_EXPERIENCE.md` already recommends) rather than leaving Faith without a companion face.
8. Fix the stale `Prompts.ALL` manifest and the redundant double `CloudAi.transport(app)` construction per send — small, low-risk cleanups to take opportunistically alongside the above.

## 6. Explicitly out of scope

Per the brief: no live in-meeting whisper, no voice conversation mode changes, no general web browsing or external integrations, no changes to Home, Circles, monetization, or other product areas, and no unification of `ScopedAskSheet`/Work's simpler Ask surface into this engine (noted as a sibling system, not touched here).

## 7. Key files

- `ai/assistant/Assistant.kt` — protocol, tools, `AssistantEngine`, `FaithGuard`
- `feature/assistant/AssistantSession.kt` — conversation state machine (send/retry/stop/confirm/undo)
- `feature/assistant/AssistantHost.kt` — tool execution against the real note/Bible/tasks
- `feature/assistant/AssistantSheet.kt` — the shared Compose UI, including the existing "Try again" button
- `ai/cloud/GeminiTransport.kt`, `GeminiHttpTransport.kt`, `DeepSeek.kt` (`FallbackTransport`), `ai/common/AiTypes.kt` (`AiResult`)
- `ai/faith/AskSermon.kt`, `ai/faith/SermonExtraction.kt`, `core/prayer/PrayerCompanion.kt`, `ai/assistant/AskEverything.kt`
- Prompts: `app/src/main/assets/prompts/{faith_contract,faith_assistant,notes_assistant,sermon_study,ask_sermon,ask_everything}.md`
- Companion mascot: `core/ui/mm/companion/*`, spec in `docs/mvp/ZURI_EXPERIENCE.md`
- Tests: `ai/assistant/AssistantTest.kt`, `ai/assistant/AskEverythingTest.kt`, `ai/faith/AskSermonTest.kt`, `feature/faith/FaithScreenshotTest.kt`

## 8. Design artifact

Interactive HTML prototype (dark-first, built on the app's real `MMColors`/`CompanionPalette` tokens): **https://claude.ai/artifact/9encGERP14Z4vKwoE6cNbZ**

Four scenes: (A) Notes assistant in a meeting note, (B) Faith assistant studying a passage, (C) a multi-step tool-call with a proposed-vs-applied action pair, (D) four reliability states (in progress / recoverable failure / partially completed / permanent failure needing a setting, not a retry).
