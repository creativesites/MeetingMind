# MeetingMind — Recording Page Redesign: Implementation Spec

**Audience:** the coding agent implementing this in the Android app.
**Source of truth for visuals:** `Recording Page Redesign.dc.html` (the design file this spec accompanies). Where this document and the design file disagree, **the design file wins**.
**Rule zero:** implement the designs exactly. Do not "improve" spacing, radii, colours, weights, copy, or information order. Every value below is deliberate.

Target file in the app: `app/src/main/java/com/example/feature/meetingdetail/MeetingDetailScreen.kt` and new files under `feature/meetingdetail/`.

**Part 2 of this spec is `docs/capture-pipeline-implementation.md`** — the capture → process → review pipeline (new recording, recording, markers, notes, saved, processing, AI engine, settings, home, search, import). It reconciles against the current repo and defines the new subsystems. Read it before touching anything outside `feature/meetingdetail/`.

---

## 0. How to open each screen in the design file

Open `Recording Page Redesign.dc.html` in a browser. It is a canvas of Android frames grouped into turns, newest at the top. Every option has a visible id badge; append `#<id>` to the URL to jump to it.

| Id | Screen | State shown |
| --- | --- | --- |
| `#3a` | Transcript — **Reading** | Default, no chrome, playing paragraph marked |
| `#3b` | Transcript — **AI tools sheet** | Bottom sheet, Transcript group expanded |
| `#3c` | Transcript — **Editing** | Text selected, floating tool bar + "more" panel |
| `#3d` | Transcript — **Result review (diff)** | After "Improve clarity", not yet applied |
| `#2a` | Overview — **step 2, Decisions** | Timeline with first decision expanded |
| `#2b` | Transcript — **Review queue** | Low-confidence word open with alternatives |
| `#2c` | **Ask AI** | Answer with inline citations, one open |
| `#1b` | Overview — **stepper** | Step 1, and the shell for all four steps |
| `#1a`, `#1c` | Rejected directions | Reference only — **do not implement** |

The frames are live: tap group headers, flagged words, citations, decisions, the ⋯ button, the stepper arrows, "Show original", and the cleanup modes to see each sub-state.

**Chosen direction is `1b`** (progressive disclosure) with `2a`'s decision timeline. `1a` and `1c` are dead.

---

## 1. Design system

All values are dp/sp at 1:1 with the design file's px. Frame width in the design is 412dp.

### 1.1 Colour

Add to `ui/theme/Color.kt`. These **replace** the current gradient-heavy palette on this screen. Purple is now confined to the player, speaker identity, timestamps and citations. Primary actions are ink, not purple.

```kotlin
// Ink / text
val Ink          = Color(0xFF0F172A)  // primary text, primary buttons, active tab
val InkSecondary = Color(0xFF475569)  // secondary text, inactive controls
val InkMuted     = Color(0xFF94A3B8)  // metadata, captions, inactive tabs
val InkFaint     = Color(0xFFCBD5E1)  // chevrons, gutter timestamps, disabled

// Lines / surfaces
val Line         = Color(0xFFE2E8F0)  // borders on interactive surfaces
val LineSoft     = Color(0xFFEEF2F6)  // section dividers, timeline spine
val LineFaint    = Color(0xFFF5F7FA)  // list-row dividers
val SurfaceSunk  = Color(0xFFFAFBFD)  // inline panels (word editor, notes, cleanup)
val SurfaceCanvas= Color(0xFFF4F6FB)  // only where a non-white canvas is used
val SurfaceTrack = Color(0xFFEBF1F9)  // segmented-control track, meter track
val White        = Color(0xFFFFFFFF)

// Accent — player, identity, citations
val Accent       = Color(0xFF6366F1)  // indigo: progress ring, speaker 1, timestamps, links
val AccentWash   = Color(0x1A6366F1)  // 10% — citation chips, selection highlight
val Speaker2     = Color(0xFFA855F7)
val Speaker3     = Color(0xFF10B981)  // also: "kept/changed" diff underline
val Speaker4     = Color(0xFFF59E0B)  // also: low-confidence flag
```

Speaker colours cycle in this order: Accent → Speaker2 → Speaker3 → Speaker4, assigned by first appearance in the recording and then **persisted per speaker id** so they never shuffle.

### 1.2 Type

One family (the platform sans — `Helvetica Neue`/Roboto equivalent; do not introduce a display face) plus one monospace for all time values.

| Role | Size / line height | Weight | Colour |
| --- | --- | --- | --- |
| Step heading (`Three decisions`) | 30sp / 34.5sp, tracking −0.8 | 600 | Ink |
| Sheet title (`AI tools`) | 20sp, tracking −0.4 | 600 | Ink |
| Ask AI answer | 17.5sp / 30sp | 400 | Ink |
| Ask AI question (asked) | 20sp / 27sp, tracking −0.4 | 600 | InkMuted |
| Transcript body | 16.5sp / 28sp (29sp when editing) | 400 | Ink (active) / InkSecondary (inactive) |
| Step body copy | 17sp / 29sp | 400 | InkSecondary |
| Decision / task line | 17sp / 26sp | 400 | Ink |
| List row (tools, follow-ups) | 15.5–16sp | 400/600 | Ink |
| Card title | 15sp | 600 | Ink |
| Body small (panels) | 13.5sp / 22sp | 400 | InkSecondary |
| Caption | 12–12.5sp / 19–20sp | 400 | InkMuted |
| Section label (`SUMMARY`) | 11sp, tracking +0.6, uppercase | 600 | InkMuted |
| Mono timestamp | 10.5–11sp, tracking +0.5 | 400/600 | InkMuted, Accent when active |
| Mono clock in dial | 27sp, tracking −0.5 | 600 | Ink |
| Step counter (`2 OF 4`) | 11sp mono, tracking +1.2 | 400 | InkMuted |

`text-wrap: pretty` in the design = enable Compose's `LineBreak.Paragraph` / balanced breaking on headings and body paragraphs.

### 1.3 Shape, spacing, elevation

- Radii: sheet 28dp (top corners only), cards/panels 20dp, buttons 16dp, chips/segments 10–12dp, avatars fully round.
- Screen horizontal padding: 18dp for chrome and lists, 22dp for transcript body, 24dp for the sheet and Ask AI, 30dp for stepper content.
- Vertical rhythm: 26dp between transcript segments, 22–28dp between panel sections.
- **No drop shadows** except the tools sheet (`0 −18 50 rgba(15,23,42,.13)`) and the floating selection bar (`0 12 30 rgba(15,23,42,.28)`). Cards separate with `LineSoft` dividers, not elevation.
- Touch targets: every tappable row ≥ 48dp tall even where the visible text is smaller.

### 1.4 Components to extract into `feature/meetingdetail/components/`

`MiniDialPlayer`, `StepperFooter`, `BottomTabBar`, `SegmentedControl`, `SunkenPanel`, `ToolRow`, `SpeakerAvatar`, `MonoTimestamp`, `TimelineSpine`, `FloatingSelectionBar`, `DiffText`.

---

## 2. Screens

### 2.1 Shell (all tabs)

Top bar, 34dp back chevron + title block + 52dp mini dial, padding `10dp 18dp 0`.
Title 14.5sp/600 single-line ellipsised; subtitle 11.5sp InkMuted, and it is **contextual**: `Today · 42:18` (Overview), `Transcript · 214 segments` (Transcript), `Answers from this recording only` (Ask AI).

Mini dial (`MiniDialPlayer`): 52dp circle whose background is a conic sweep — `Accent` from 0 to progress%, `#E9EDF5` for the remainder, starting at 180° (bottom) — with a 4dp white inset disc holding the play/pause glyph in Accent. Tapping toggles playback; long-press opens the full player. It is present on every tab and never scrolls away.

Bottom tab bar: three equal columns, 15dp vertical padding, 13sp. Active = Ink 600 with a 2dp Ink underline (`box-shadow: inset 0 -2px 0`), inactive = InkMuted 400. Top border `LineSoft`. Tabs are **Overview · Transcript · Ask AI** only — the current dynamic tab list (Action Items, Decisions, etc.) is removed; that content moves into the Overview stepper.

### 2.2 Overview — stepper (`#1b`, `#2a`)

Four steps, horizontally pageable, `HorizontalPager` with the footer arrows also driving it:

1. **What happened** — 30sp heading, 17sp/29sp summary paragraph, then a second paragraph in InkMuted for open questions.
2. **Three decisions** — see below.
3. **Three things to do** — 22dp circular unchecked outlines (1.5dp `InkFaint`), 16.5sp/25sp task text, `Owner · Due` in 12.5sp InkMuted underneath. Checking one persists and syncs to the task store.
4. **Who talked** — 34dp speaker avatar, name 16sp, a 5dp rounded meter in the speaker's colour on a `SurfaceTrack` track sized by talk share, and mono duration at the right.

Headings are generated with the real count (`Three decisions`, `Three things to do`) — spell numbers one through nine, use digits from 10.

Step counter `N OF 4` sits above the heading at 34dp top padding. Content is top-aligned; a spacer pushes the footer down.

**Footer:** 52dp circular prev (1dp `Line` outline, InkSecondary chevron) and next (Ink fill, white chevron), with dot indicators between — 7dp dots `Line`, the active one a 22dp Ink pill. Animate the pill width on step change (200ms).

**Step 2, the decision timeline (`#2a`):** a 9dp Ink dot on a 1.5dp `LineSoft` vertical spine, 18dp gutter. Each entry: mono time, then the decision at 17sp/26sp. Tapping expands **in place** — a 15dp-indented block with a 2dp `LineSoft` left rule containing the supporting quote (14.5sp/23sp InkSecondary), the speaker avatar + name, and a `Play from MM:SS` action. Only one expands at a time; the others collapse. Expansion animates height, no dialogs, no navigation.

### 2.3 Transcript — Reading (`#3a`)

The default state. A 38dp left gutter holds mono timestamps (`InkFaint`, `Accent` for the playing segment). Right of it, speaker name at 12sp/600 in the speaker's colour, then the text at 16.5sp/28sp — Ink for the currently playing segment, InkSecondary for all others.

The playing segment additionally carries a 2dp Accent left rule that overhangs into the gutter (`margin-left:-16dp; padding-left:14dp`). Auto-scroll keeps it in the upper third while playing; any manual scroll suspends auto-scroll until the user taps the dial or a "Jump to playing" affordance.

Tapping a segment seeks audio to its start. There are no cards, no borders, no elevation in this state.

**The toolbar is three words** above the tab bar, in a 3-column row separated from the content by a `LineSoft` rule: `Search` · `Edit` · `Tools`. The active/primary one is Ink 600, the others InkSecondary.

### 2.4 Transcript — AI tools sheet (`#3b`)

A modal bottom sheet over a dimmed (35% opacity) transcript. 28dp top radius, 38×4dp grabber.

Header row: `AI tools` (20sp/600) left, **scope control** right — a text button in Accent reading `Whole transcript ▾`. Scope options: `Whole transcript`, `This selection`, `From here on`, `One speaker…`. The scope line is the only setting on the sheet.

Three collapsible groups, dividers `LineSoft`, header row 17dp × 24dp padding, title 16sp/600. **Collapsed groups show only a count** (`7`, `7`, `5`) in 12.5sp InkMuted at the right; the expanded group shows no count. Only one group is open at a time (accordion). Items are 15.5sp Ink rows, 11dp vertical padding, separated by `LineFaint` — no icons, no descriptions, no chevrons.

Group contents, **verbatim and in this order**:

- **Transcript** — Clean transcript · Fix transcription errors · Improve clarity · Remove repetition · Condense · Expand context · Fix terminology
- **Analysis** — Extract key points · Find decisions · Find questions · Find action items · Find important moments · Identify topics · Find names & organisations
- **Utilities** — Explain this · Rewrite professionally · Create notes · Create outline · Generate title

Footer above the sheet edge, 12sp/19sp InkMuted, separated by `LineSoft`: the honesty line about on-device chunked execution. Keep this copy.

### 2.5 Transcript — Editing (`#3c`)

Entered via `Edit`, or automatically when the user selects text or taps into a paragraph.

Top bar changes to `Cancel` (15sp InkSecondary) · `Editing · MM:SS` (12.5sp InkMuted) · `Save` (15sp/600 Ink). Non-focused segments drop to 40% opacity. The focused segment's text becomes editable at 16.5sp/**29sp**, and its speaker label gains a ▾ for reassignment.

Selected text is highlighted with `AccentWash`, 3dp radius.

**Floating selection bar:** Ink pill, 16dp radius, 6dp padding, 12dp above the selection, shadow as specified. Four items: `Fix errors` · `Clarity` · `Condense` · `⋯`, each 13sp white with 9×12dp padding and an 11dp radius pressed state.

The `⋯` opens a `SurfaceSunk` panel inline below (not a sheet): label `ON THIS SELECTION`, then wrapped 13sp chips with white fill and 1dp `Line` — Remove repetition, Expand context, Fix terminology, Explain this, Rewrite professionally, Create notes — and a divider with `All tools · 19` leading to the full sheet (2.4) pre-scoped to the selection.

Bottom row becomes four columns: `Undo` · `Redo` · `Speaker` · `Split`. Undo/Redo render in `InkFaint` when unavailable.

### 2.6 Transcript — Review queue (`#2b`)

Reachable from the header card whenever the ASR pass flagged anything. This is the state that makes a weak local model acceptable, so build it fully.

**Header card** (1dp `Line`, 20dp radius, 16×18dp, margin `22dp 18dp 0`): title `Words to review` (→ `Everything reviewed` at 100%), count `6 of 14` in mono at the right, and a 4dp progress bar (Ink on `LineSoft`). Tapping expands to a 13.5sp/21sp explanation plus two actions: `Review flagged words` (Ink pill) and `Fix speakers (2)` (outlined).

**In the text**, flagged spans get a 2dp dotted `Speaker4` underline and a 10% amber wash, 3dp radius. Tapping one opens a `SurfaceSunk` panel below the paragraph:

- `HEARD AS` label, the word at 19sp/600, and `61% confident` in mono `Speaker4`.
- Action chips: `Keep` (Ink fill) then up to three ranked alternatives from the ASR n-best list, then `Type it`.
- A checkbox row: `Apply to the other N times this word appears` — checked by default, Ink 18dp rounded square.
- Caption: `Playing MM:SS – MM:SS on loop while you decide.` — and it must actually loop that ±4s window while the panel is open.

Resolving a word advances the counter, dismisses the panel, and auto-scrolls to the next flag.

**Cleanup pass** (the `Cleanup pass` button above `Done`): expands a `SurfaceSunk` panel with a three-way segmented control on a `SurfaceTrack` — `Verbatim` · `Readable` · `Polished` — the selected segment white with Ink text. Each mode shows its own one-line description (copy as in the design). Below it, a `Hide filler words` switch (Ink when on), then the guarantee in 12sp InkMuted: cleanup runs from the stored verbatim text and **never overwrites a line the user has edited**. That guarantee is a hard requirement, not copy — see §3.4.

### 2.7 Transcript — Result review (`#3d`)

Every AI tool that rewrites text lands here. **Nothing is applied until the user keeps it.**

Top bar title = the tool name (`Improve clarity`), subtitle = `N paragraphs · not applied yet`.

Body: per-segment `MM:SS · SPEAKER` mono label, then the proposed text at 16.5sp/29sp with changed runs carrying a 2dp `Speaker3` underline. A text button `Show original` (13.5sp/500 Accent) toggles to a stacked before/after: the original struck through in InkMuted with `Line`-coloured strike, the proposal below it.

Then a `SurfaceSunk` panel with a plain-English account of what the model did (counts of what changed, and what it explicitly did not do), and a mono footprint line: `GEMMA 3N · ON DEVICE · 48s`.

Footer: `Discard` (outlined) and `Keep changes` (Ink fill), equal width.

### 2.8 Ask AI (`#2c`)

No chat bubbles. The exchange reads as a document.

- The asked question sits at 20sp/600 in **InkMuted** — it recedes once answered.
- The answer is 17.5sp/30sp Ink prose. **Citations are inline**: mono 11sp/600 Accent chips on `AccentWash`, 5dp radius, 2×5dp padding, 4dp side margins, containing the timestamp. Tapping one expands a `SurfaceSunk` panel with the speaker avatar, `Name · MM:SS`, the verbatim quote at 14.5sp/24sp, and two actions: `Play from MM:SS` (Ink pill) and `Open in transcript` (outlined).
- Below the answer, the footprint line in 12.5sp/20sp InkMuted: how many of how many segments were read, and that nothing outside the transcript was used.
- `ASK NEXT`: three follow-ups as plain rows with chevrons, `LineFaint` dividers, 15sp Ink. These are generated from the transcript, not hard-coded.
- **Running state** (`SurfaceSunk` panel): the question, a 6dp Accent dot, `Reading the transcript — chunk N of M`, a 3dp progress bar, and `Gemma 3n, on this phone. Roughly 20 seconds.` Must be cancellable.
- Composer: 26dp-radius field, 1dp `Line`, 42dp Ink circular send button. It is pinned above the tab bar and rises with the keyboard.

Answers stream token-by-token into the prose; citations resolve as they arrive.

---

## 3. Features to build

Everything in this section is new. Grouped by subsystem, with the behaviour that the designs assume.

### 3.1 Playback

1. **Mini dial player** — conic progress ring, play/pause, present on all three tabs, survives tab switches.
2. **Seek from anywhere** — `Play from MM:SS` in decisions, citations, review panels, and tapping any transcript segment.
3. **Loop window** — play an arbitrary `[start, end]` range on repeat while a review panel is open, then restore the previous position and play state.
4. **Follow playback** — transcript auto-scroll bound to the current segment, suspended on manual scroll.

Use **Media3 / ExoPlayer** with a foreground `MediaSessionService`. Do not hand-roll `MediaPlayer` state.

### 3.2 Transcript model and editing

5. **Segment store** — `Room` entities: `Segment(id, recordingId, startMs, endMs, speakerId, textVerbatim, textCurrent, isUserEdited, cleanupMode)` and `Token(id, segmentId, start, end, text, confidence, alternativesJson, resolvedBy)`. `textVerbatim` is immutable after ASR; everything else is derived or user-owned.
6. **Rich editing** — inline `BasicTextField`-based per-segment editing with selection, backed by a document-level undo/redo stack (not the field's own). Undo must reverse an AI apply as a single step.
7. **Split segment** — break one segment at the caret, interpolating the timestamp from character offset across the segment's duration.
8. **Merge segment** — deleting at the start of a segment merges it into the previous one (same speaker only).
9. **Speaker reassignment** — the ▾ on a speaker label opens a picker: existing speakers, `New speaker…`, and `Rename globally`. Offer "apply to the rest of this run" when the following segments share the speaker.
10. **Speaker registry** — persisted names and colours per recording; renaming updates every reference including Overview step 4 and Ask AI citations.
11. **Search mode** — the third toolbar word. Query field, match count, next/prev, matches highlighted with `AccentWash`; searches `textCurrent` and jumps by seeking.

### 3.3 ASR confidence and review

12. **Confidence capture** — persist per-token confidence and n-best alternatives from the ASR pass. If the current engine does not expose them, switch to one that does (whisper.cpp with token probabilities, or Vosk's word-level confidences) — the review UI is not implementable without them.
13. **Flag threshold** — mark tokens below 0.75 confidence; cap flags at 2% of tokens per recording so long recordings stay reviewable, keeping the lowest-confidence ones.
14. **Review queue** — ordered list of unresolved flags with progress, next/previous, and a resolved state that persists.
15. **Apply to all occurrences** — resolving a word optionally rewrites every other occurrence of the same surface form that is itself flagged. Must be a single undo step.
16. **Speaker-boundary flags** — the `Fix speakers (2)` path: places where diarisation confidence is low, presented as a two-option choice ("one speaker" vs "split here") with the audio looping across the boundary.

### 3.4 On-device AI toolkit

17. **19 tools**, exactly the list in §2.4, each a prompt template plus an output contract:
    - *Rewrite tools* (Clean transcript, Fix transcription errors, Improve clarity, Remove repetition, Condense, Expand context, Fix terminology) → return revised text per segment → **always land in the diff review screen (§2.7)**.
    - *Analysis tools* (Extract key points, Find decisions, Find questions, Find action items, Find important moments, Identify topics, Find names & organisations) → return structured JSON with `{text, startMs, speakerId}` per item → merge into the Overview steps and the timeline; never mutate the transcript.
    - *Utilities* (Explain this, Rewrite professionally, Create notes, Create outline, Generate title) → produce a new artefact (a note, an outline, a title) attached to the recording, not an edit of the transcript. `Generate title` offers to rename the recording.
18. **Scope** — every tool runs against whole transcript / selection / from-here-on / single speaker. Scope is resolved to a segment range before chunking.
19. **Chunked execution** — split the scope into model-context-sized chunks on segment boundaries with a one-segment overlap, run sequentially, report `chunk N of M`, and support cancellation that keeps completed chunks discardable as a unit.
20. **Queue** — one job at a time per recording, persisted through process death via `WorkManager`, with a notification for long passes.
21. **Never auto-apply** — a rewrite job's output is a proposal record; applying it writes `textCurrent` and sets `isUserEdited=false` on those segments.
22. **Edit preservation** — re-running a cleanup or rewrite skips any segment with `isUserEdited=true`. Show the count skipped in the result summary. This is the guarantee printed in the cleanup panel.
23. **Cleanup modes** — Verbatim / Readable / Polished are three prompt strengths applied from `textVerbatim`, not stacked on prior output. Switching modes re-derives from verbatim.
24. **Filler removal** — a deterministic pre-pass (word list + regex), not a model call, toggled independently of mode.
25. **Diff computation** — word-level diff between original and proposal for the green underlines. Use **java-diff-utils**; do not write a diff.
26. **Change summary** — count the diff operations and render the plain-English sentence; never let the model self-report what it changed.
27. **Model footprint** — record model name, on-device flag, and elapsed time per job and show it in results and Ask AI.

Run the models through **MediaPipe LLM Inference API** (Gemma 3n / Gemma 2 2B `.task` bundles) or **llama.cpp via JNI** if you need GGUF flexibility. Do not build an inference loop from scratch.

### 3.5 Ask AI

28. **Retrieval** — embed segments once after ASR, store vectors in Room (`ObjectBox` or a flat float array + cosine scan is fine at this scale), retrieve top-k segments for each question. The footprint line reports the real `k` and total.
29. **Grounded answers** — the prompt supplies retrieved segments with their timestamps and instructs the model to cite `[MM:SS]` inline; parse those markers into citation chips. Drop any citation that does not resolve to a real segment.
30. **Streaming** — token streaming into the prose view with progressive citation resolution.
31. **Suggested follow-ups** — generated from the transcript after each answer, three at a time.
32. **Conversation history** — persisted per recording; the previous question recedes to InkMuted when a new answer starts.
33. **Refusal path** — when retrieval finds nothing relevant, say so plainly rather than answering from the model's own knowledge. Copy: `Nothing in this recording covers that.`

### 3.6 Overview

34. **Stepper** — four pageable steps with the footer control, swipe, and persisted last-viewed step per recording.
35. **Decision timeline** — decisions with timestamps, expandable supporting quote, and play-from. Populated by the `Find decisions` tool.
36. **Tasks** — checkable, owner and due date parsed by `Find action items`, persisted, and surfaced to whatever task list the app already has.
37. **Talk share** — per-speaker speaking time and percentage computed from segment durations.
38. **Empty and pending states** — each step needs a state for "this pass has not run yet" with a single action to run it. Do not show an empty step.

### 3.7 Cross-cutting

39. **Export** — the professional output the toolkit is for: Markdown, plain text, SRT/VTT, and PDF of the cleaned transcript, with options for timestamps and speaker labels. Use **iText**/`PdfDocument` for PDF; write SRT/VTT directly.
40. **Share** — the standard share sheet for the same formats plus a summary-only variant.
41. **Autosave** — edits persist per keystroke debounce; no explicit save required, though `Save` in editing mode commits and exits.
42. **Offline-first** — the entire screen must work with no network. Nothing here calls out.

---

## 4. Libraries

Prefer these over hand-rolling:

| Need | Use |
| --- | --- |
| Audio playback, sessions, seeking | `androidx.media3` (ExoPlayer + MediaSession) |
| On-device LLM | MediaPipe `tasks-genai` LLM Inference, or llama.cpp JNI |
| ASR with token confidence | whisper.cpp (token logprobs) or Vosk |
| Embeddings / retrieval | MediaPipe Text Embedder, vectors in Room |
| Persistence | Room + DataStore (preferences) |
| Background jobs | WorkManager |
| DI | Hilt |
| Text diff | java-diff-utils |
| JSON from model output | kotlinx.serialization, lenient parsing |
| PDF export | `android.graphics.pdf.PdfDocument` or iText |
| Paging/stepper | Compose `HorizontalPager` (Foundation) |
| Bottom sheet | Material3 `ModalBottomSheet`, restyled to the tokens in §1 |

No new UI framework, no chat library, no rich-text editor dependency.

---

## 5. Suggested order

1. Tokens and shared components (§1).
2. Shell: top bar, mini dial, three tabs (§2.1).
3. Transcript reading (§2.3) on the existing data.
4. Segment store + editing + undo/redo (§3.2).
5. Confidence capture and the review queue (§3.3, §2.6) — this unblocks the honesty story.
6. Tool sheet, job runner, chunking, diff review (§2.4, §2.7, §3.4).
7. Ask AI with retrieval and citations (§2.8, §3.5).
8. Overview stepper fed by the analysis tools (§2.2, §3.6).
9. Export and share (§3.7).

---

## 6. Acceptance checks

- Every screen matches its design-file frame at 412dp width — spacing, weights and colours compared side by side, not from memory.
- Purple appears only on: the dial ring, speaker 1 identity, active timestamps, citation chips, selection wash, and the `Show original` / scope text buttons. Every primary button is Ink.
- No AI output is ever written to the transcript without passing through the diff review screen.
- A segment the user has edited is never overwritten by a re-run.
- Every timestamp shown anywhere is tappable and seeks.
- Airplane mode changes nothing.
