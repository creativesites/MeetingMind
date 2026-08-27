# MeetingMind — Capture Pipeline Redesign: Implementation Spec (Part 2)

**Audience:** the coding agent implementing this in the Android app.
**Part 1** is `docs/recording-page-implementation.md` (meeting detail / playback / transcript / Ask AI). That spec is already largely built — `feature/meetingdetail/components/` exists. This document does not restate it; §1's design system there applies here unchanged.
**Source of truth for visuals:** `Recording Page Redesign.dc.html`. Where this document and the design file disagree, the design file wins.
**Rule zero:** implement the designs exactly — spacing, weights, colours, copy, information order. Where this document specifies *behaviour* that has no design frame yet, implement the behaviour and match the existing visual language.

Repo state this was written against: `creativesites/MeetingMind`, branch `claude/meetmind-audit-reconciliation-k7aupe`, tree `fd23a40c9b35`, read 2026-08-27.

---

## 0. Screen index in the design file

Open the design file in a browser and append `#<id>`.

| Id | Screen | Replaces / affects |
| --- | --- | --- |
| `#7a` | **New recording** (quick setup) | `RecordingScreen.RecordingTypePickerScreen` |
| `#7b` | **Recording** — calm, marker sheet, quick note, paused | `RecordingScreen` main body + `WaveformCenterButton` |
| `#7c` | **Recording saved** (process now / later) | **new screen** |
| `#7d` | **Processing**, stage list typed by recording type | `feature/processing/ProcessingScreen.kt` |
| `#6b` | **Processing queue** (running / waiting / finished today) | `ProcessingScreen`'s list mode |
| `#6c` | **AI engine** | `feature/models/ModelManagerScreen.kt` |
| `#6d` | **Settings** | `feature/settings/SettingsScreen.kt` |
| `#5a` | **Home** | `feature/home/HomeScreen.kt` |
| `#5b` | **Search** | `feature/search/SearchScreen.kt` |
| `#5c` | **Import audio** | `feature/importing/ImportScreen.kt` |
| `#6a` | Earlier single-screen record draft | **superseded by `#7a`/`#7b` — do not implement** |

The frames are live: tap the ring, Marker, Note, Pause, "Change workflow", "Process later", and the Meeting/Sermon/Lecture chips on `#7d`.

---

## 1. The principle this spec encodes

> **Before recording: almost no friction. During recording: almost no UI. After recording: maximum intelligence and control.**

Three consequences that override any convenience argument:

1. **Capture never depends on AI.** A recording that cannot be transcribed is still a complete, valuable object. Processing is a separate, deferrable, re-runnable job.
2. **The recording is the source object, not the brief.** A `Meeting` owns audio, transcript, speakers, markers, notes, highlights and AI outputs. The AI outputs are *views over* the transcript, and the transcript is the evidence.
3. **Data loss is existential.** A user forgives ugly UI. They do not forgive losing a two-hour meeting.

---

## 2. What already exists (reconciliation)

Read this section before writing code. Roughly half of what follows is already implemented and must be **extended, not rebuilt**.

### 2.1 Already built and correct — reuse as-is

| Concern | Where | Note |
| --- | --- | --- |
| Recording type as a first-class pipeline input | `core/model/MeetingModels.kt` → `RecordingType` (12 entries), `RecordingContext`, persisted on `Meeting` | **This is the "workflow" architecture the redesign asks for.** Do not introduce a parallel `Workflow` concept. |
| Per-type AI behaviour | `RecordingType.intelligenceProfile()` / `focusGuidance()` / `cleanupGuidance()` / `transcriptMergePolicy()` / `transcriptCleanupProfile()` | Already drives extraction schema, which detail sections appear, and the processing screen's Analyzing label. Extend these `when` blocks; never branch on type in the UI. |
| Foreground recording service, wake lock, lock-screen Pause/Resume/Stop actions, `FOREGROUND_SERVICE_TYPE_MICROPHONE` | `core/audio/MeetingRecordingService.kt` | Recording already survives screen-off and leaving the app. |
| Typed processing stages, no fabricated progress | `core/model/Enums.kt` → `ProcessingStage` (11 states), `ai/pipeline/MeetingProcessingPipeline.kt`, `MeetingProcessingWorker.kt` (WorkManager) | Keep the "no fake percentages" discipline. |
| Model-missing is not an error | `MeetingStatus.MODEL_REQUIRED` | Audio is never discarded in this state. This is the precedent for `SAVED` in §3.1. |
| Title suggestion after transcription | `core/common/MeetingTitleGenerator.kt` | Already the "suggest a title later, never block on it" behaviour. |
| Shared type/speaker-count pickers across record + import | `core/ui/RecordingContextPicker.kt` | Both entry points must keep using these. |
| Per-word timings, cleanup modes, citations, export | `TranscriptWord`, `TranscriptCleanupMode`, `ChatMessage.readSegmentCount`, `core/export/*` | Part 1 territory. Untouched here. |

### 2.2 Exists but must change

| Thing | Current behaviour | Required change |
| --- | --- | --- |
| `feature/recording/RecordingScreen.kt` | The type picker is a mandatory full-page form (type grid + custom context + speaker count + Start + Quick Record). The recording state is a 260dp radiating-waveform canvas with a red/indigo gradient centre button, Discard and Finish (green) at the bottom. | Rebuild as `#7a` + `#7b`. Type picker collapses to **remembered type + optional title + Start**, with the full list one tap behind "Change workflow". The recording body loses the gradient, the red, and the giant visualizer; gains Marker, Note, and a marks list. |
| `AudioRecorder` lifetime | Owned by `RecordingViewModel`. The service only shows a notification and holds the wake lock; it does not own the recorder. | Invert it: the **service owns the recorder** (§3.2). The UI observes. |
| Stop | `finishRecording()` writes the meeting and immediately hands off to processing. | Stop lands on `#7c`. Processing is a user choice. |
| `MeetingStatus` | `RECORDING, PROCESSING, READY, ERROR, MODEL_REQUIRED` | Add `SAVED` and `QUEUED` (§3.1). |
| `feature/processing/ProcessingScreen.kt` | Maps 11 `ProcessingStage` values onto 6 fixed display rows. | Stage rows become **type-derived** (§5). Add the queue view (`#6b`) and the failure state. |
| `ProcessingJob` | `currentStep` string + percent. | Add `stage`, `queuedReason`, `elapsedMs`, `batteryPercentUsed` (§5.3). |
| `RecordingType` | No sermon. | Add `SERMON` with its own profile, guidance, merge policy, and stage tail (§5.2). |
| `WaveformCenterButton` | Red→indigo gradient bars, 260dp. | Replace with a flat bar meter: 3dp bars, 3dp gaps, `InkFaint`/`InkMuted`/`Accent` by level, ≤52dp tall. No gradients anywhere in capture. |

### 2.3 Does not exist at all — build from scratch

Markers · quick notes · highlights · segments (continue recording) · crash recovery · the saved screen · the process-later queue · notebooks · projects · tags · storage and battery pre-flight warnings · the explicit recording state machine.

---

## 3. Capture architecture

### 3.1 The recording state machine

Model it explicitly. This is the single biggest source of future bugs if left implicit.

```kotlin
enum class RecordingState { IDLE, PREPARING, RECORDING, PAUSED, STOPPING, SAVING, SAVED, FAILED }
```

`RecordingState` is the **live capture** lifecycle, owned by the service and exposed as a `StateFlow`. It is deliberately separate from `MeetingStatus`, which is the **persisted** lifecycle of the recording object:

```kotlin
enum class MeetingStatus {
    RECORDING,        // capture in flight
    SAVED,            // NEW — audio safe, user has not chosen to process yet
    QUEUED,           // NEW — process-later; waiting on a trigger (charger, Wi-Fi, manual)
    PROCESSING,
    READY,
    MODEL_REQUIRED,   // existing — audio safe, a model needs installing
    ERROR
}
```

Rules:
- Only `RECORDING → SAVED` is reachable from capture. Nothing in the capture path may set `PROCESSING`.
- `SAVED`, `QUEUED`, `MODEL_REQUIRED` and `ERROR` all guarantee the audio file exists on disk. Never delete audio on any of these transitions.
- Every transition is persisted immediately, not on screen destruction.
- Room migration required for the two new enum values (they are stored by name).

### 3.2 The service owns the recorder

Required topology:

```
UI (RecordingViewModel)  →  binds/observes
        ↓
MeetingRecordingService  →  owns AudioRecorder, the output file, RecordingState,
                            amplitude + duration telemetry, segment list
```

Concretely:
- Move `AudioRecorder` construction and all `start/pause/resume/stop` handling into `MeetingRecordingService`. `RecordingViewModel` sends intents (or binds) and collects `StateFlow`s from the service. It must not hold the recorder.
- The service writes a **crash-recovery journal** (§3.7) on every state change.
- Keep the existing wake lock, notification channel, `FOREGROUND_SERVICE_TYPE_MICROPHONE`, and the Pause/Resume/Stop notification actions. Update the notification's content text to the current state and keep the chronometer.
- Audio focus: register an `AudioManager.OnAudioFocusChangeListener`. On `AUDIOFOCUS_LOSS_TRANSIENT` (incoming call) auto-pause, mark the boundary as a segment break, and post a notification line saying so; on regain, do **not** auto-resume — a silent auto-resume the user did not ask for is worse than a visible gap. Show a resume prompt in-app and in the notification.

### 3.3 Segments — pause/resume and "continue recording"

One `Meeting` may hold multiple audio spans. Build the model now even if the concatenating playback lands later.

```kotlin
@Entity(tableName = "recording_segments")
data class RecordingSegmentEntity(
    @PrimaryKey val id: String,
    val meetingId: String,
    val index: Int,           // 0-based, source order
    val filePath: String,
    val startOffsetMs: Long,  // position of this segment on the meeting's logical timeline
    val durationMs: Long,
    val createdAt: Long
)
```

- Pause/resume within one session: either one file with a recorded gap, or one file per span — but the **logical timeline** the rest of the app sees (transcript timestamps, markers, citations) must always be gapless and monotonic. All timestamps everywhere are logical-timeline milliseconds. Only the segment table knows about physical files.
- **Continue recording** (`#7c` row): starting a new span on an existing `SAVED`/`READY` meeting appends a segment with `startOffsetMs = previous end`. The meeting stays one object. If it was already processed, mark it `QUEUED` for a re-run over the appended range only.
- Playback and export concatenate segments in `index` order. Media3 `ConcatenatingMediaSource` handles this; do not stitch files on disk.

### 3.4 Markers

The most valuable interaction during a long recording. **One tap, no typing, sheet closes immediately.**

```kotlin
enum class MarkerType { DECISION, ACTION, IDEA, QUESTION, IMPORTANT, BOOKMARK, NOTE }

@Entity(tableName = "markers")
data class MarkerEntity(
    @PrimaryKey val id: String,
    val meetingId: String,
    val timestampMs: Long,     // logical timeline
    val type: String,          // MarkerType.name
    val text: String? = null,  // added later, never required
    val createdAt: Long,
    val createdDuringRecording: Boolean
)
```

- Sheet order is exactly `#7b`'s: Decision, Action, Idea, Question, Important, Bookmark. `NOTE` is created by the Note affordance, not this sheet.
- Tapping a type writes the marker at the current recording position and dismisses. No confirmation, no text field, no toast that blocks. A one-line inline confirmation in the marks list is enough.
- Markers are editable afterwards from the recording workspace (add/change text, retype, delete, nudge the timestamp).
- After processing, markers are **evidence the extractor must see**: pass marker type + timestamp + the transcript window around it into the extraction prompt as a hint that the user already flagged this moment. A user-marked DECISION that the model missed should still appear in the brief, attributed to the user, not the model.
- Markers render on the playback scrubber and in the transcript gutter, in the marker type's colour (reuse `Speaker4`/amber for IMPORTANT, `Accent` for the rest — no new palette).

### 3.5 Quick notes

Same sheet mechanics, one text field, recording continues while typing.

```kotlin
@Entity(tableName = "recording_notes")
data class RecordingNoteEntity(
    @PrimaryKey val id: String,
    val meetingId: String,
    val timestampMs: Long,
    val content: String,
    val createdAt: Long,
    val createdDuringRecording: Boolean
)
```

A note always inherits `meetingId`, `timestampMs`, and (through the meeting) recording type and date. Never ask the user for any of that. Notes appear in the Notes tab of the workspace and as gutter anchors in the transcript.

### 3.6 Highlights (post-processing, first-class)

```kotlin
@Entity(tableName = "highlights")
data class HighlightEntity(
    @PrimaryKey val id: String,
    val meetingId: String,
    val segmentId: String,
    val startCharOffset: Int,
    val endCharOffset: Int,
    val timestampMs: Long,
    val quotedText: String,   // snapshot, so a later transcript edit can't orphan the highlight
    val colorKey: String,     // "amber" | "green" | "purple" — from the existing palette only
    val note: String? = null,
    val createdAt: Long
)
```

Created from the transcript selection toolbar (Part 1 `#3c`): `Copy · Highlight · Add note · Create marker · Share · Ask AI`. Highlights aggregate across recordings into a Highlights view — that view is the seed of the Notebook feature and must be queryable by notebook, project, tag and date.

### 3.7 Crash recovery

Invisible when it works. Mandatory.

- The service maintains a journal file (`recovery.json`, atomic write) holding: meeting id, title, recording type, output paths, segment list, last known duration, marker/note counts, last heartbeat timestamp. Update on every state change and at least every 10 seconds while recording.
- On app start, if a journal exists whose state is `RECORDING`/`PAUSED`, finalise what is on disk (audio files are already flushed) and present the recovery prompt: **Keep recording · Save recording · Delete**. Copy: `We found an unfinished recording.` Never auto-delete, never silently discard a partial file, and never overwrite it on the next session.
- `AudioRecorder` must flush to disk periodically rather than buffering an entire session in memory.

### 3.8 Pre-flight and in-flight warnings

- **Before start** (`#7a` footer line): available storage translated into hours (`11 GB free, enough for about 40 hours`), mic route, and level. Refuse to start below ~200 MB free with a plain explanation.
- **During**: at 15% battery, `Battery low — recording may stop soon.`; at 500 MB free, `Storage is running low. Recording may stop.` Both as a single quiet line in the recording screen and a notification update. Never a modal dialog over a live recording.

---

## 4. Capture screens

### 4.1 New recording (`#7a`)

Two states in one screen.

**Remembered (default).** `WORKFLOW` label 11sp mono `InkMuted`; the remembered type as a 32sp/600 heading, letter-spacing −1; a 14sp/22sp `InkSecondary` line naming what that type extracts plus the honest reason it is remembered (`You have used this for your last 9 recordings.`); then `Change workflow` at 13.5sp/500 in `Accent`.

The remembered type is `UserPreferences.lastRecordingType`, defaulting to `GENERAL` on first run — on first run show the full list instead, since there is nothing to remember. Do **not** implement the "you recorded 4 sermons recently, start Sermon mode?" suggestion; it is a later intelligence feature.

**Full list (behind "Change workflow").** All types as rows: name 16sp, `shortDescription` 12.5sp `InkMuted`, `LineFaint` dividers, a 14sp `Accent` ✓ on the selected row. Rows are ≥48dp. `CUSTOM` reveals the existing custom-context field. `GENERAL` is presented last as `Free recording — Transcript only, classify it later`.

**Title.** Optional, one line, 19sp, `InkFaint` placeholder over a 1dp `Line` rule, plus the honest 12.5sp explanation that empty means date-named and that a title will be suggested later. It must be impossible to leave this screen unable to record.

**Start.** Full-width `Accent` fill, 20dp radius, 16sp/600, the purple shadow from the design. Above it the storage/mic line from §3.8.

**What moved out.** Speaker count, participants and purpose are **not** on this screen. Speaker count keeps its existing job as an input to diarisation, but it is now asked either on the saved screen (`#7c` → *Rename, participants, purpose*) or in the before-processing prompt that already exists. `RecordingContext.speakerCountPreference` stays exactly as it is — only the moment of capture moves.

### 4.2 Recording (`#7b`)

Four states, one screen. Everything is quiet: no red, no gradients, no dashboard.

- **Header**: 34dp back chevron · a 7dp `Accent` dot + `RECORDING` in 11sp mono (or `PAUSED` in `InkMuted`) · a `⋯` for the rarely-needed menu.
- **Body**: title in 13sp/1.4px-tracked uppercase `InkMuted`; the clock at 62sp mono weight 300, letter-spacing −3, `Ink`; one 12.5sp `InkMuted` line: type, voices heard, and `keeps running when locked`.
- **Meter**: 3dp bars, 3dp gaps, max 44dp, centred, colours stepping `LineSoft → InkFaint → InkMuted → Accent` by level. Reassurance, not spectacle. Drive it from the existing single-scalar amplitude; never fabricate motion when paused.
- **Marks list**: `MARKS` label with a `7 · 3 notes` counter, then the two most recent entries — mono `Accent` timestamp + label. This is the proof that a tap landed.
- **Controls**: `＋ Marker` as the primary, full-width `Ink` fill 22dp radius, with a 62dp square outlined `Note` button beside it; `Pause` and `Stop` are equal-width outlined buttons on the row below. Marker is visually the biggest control on the screen. All ≥48dp.
- **Paused**: the dot becomes `PAUSED`, the meter freezes at rest, and the body line says the audio so far is already saved and that resuming continues the same recording as a second segment. `Resume` (Accent) + `Stop`.
- **Marker sheet / Quick note**: bottom sheets, 28dp top radius, 38×4dp grabber, `#7b`'s exact rows and copy. Both show the captured timestamp in mono `Accent` in the header. Recording never pauses for either.
- **`⋯` menu**: Rename · Add participant · Change type · View all markers · Keep screen awake · Recording settings. Most users never open it; give it no visual weight.

### 4.3 Recording saved (`#7c`)

Stop lands here. Never straight into processing.

- `✓ SAVED ON THIS PHONE` — 22dp `Speaker3` green disc + 11sp mono green label. This is the whole point of the screen.
- Title 30sp/600, then type and datestamp.
- A 2×2 stats grid on `LineFaint` rules: `LENGTH`, `AUDIO`, `MARKERS`, `NOTES`, each an 11sp mono label over a 17sp value.
- **Processing cost block**, left-ruled 2dp `Accent`: `PROCESSING WILL TAKE` + a plain-language estimate. **Only show a number once you can measure it** — derive from real device throughput (audio seconds per second for the installed ASR model on this device class, recorded from previous runs in `UserPreferences`). Until there is a measurement, drop the number and say what will happen, not how long.
- **Process later** switches the block to an amber-ruled `QUEUED` panel: audio, markers and notes are kept; processing starts on charge or on demand. Sets `MeetingStatus.QUEUED`.
- Rows: `Continue this recording` (§3.3) · `Add to notebook or project` (§6) · `Rename, participants, purpose`.
- Footer: `Process now` (Accent fill) · `Process later` + `Play it back` (outlined) · `Discard recording` as plain text, always confirmed by a dialog that names the length being destroyed.

---

## 5. Processing

### 5.1 Stage lists are derived from recording type

`ProcessingStage` (the engine's real states) stays as it is. Add a **presentation** layer on top:

```kotlin
data class ProcessingStageRow(
    val label: String,
    val detail: String?,          // "Whisper small · chunk 9 of 14"
    val stages: Set<ProcessingStage>,   // engine stages this row represents
    val estimateSeconds: Int?     // null until measurable
)

fun RecordingType.processingStageRows(): List<ProcessingStageRow>
```

Put it next to `intelligenceProfile()` in `MeetingModels.kt`, driven by the same profile flags — the tail rows must be generated from `extractDecisions`/`extractActionItems`/`extractQuestions`/`extractFollowUps` plus the type's own extras, so a row can never appear for something the pipeline will not produce.

Shared head for every type: `Preparing audio` · `Transcribing` · `Identifying speakers` · `Building the transcript`. Type-specific tail, e.g.:

| Type | Tail |
| --- | --- |
| `MEETING`, `CUSTOM`, `GENERAL` | Topics and decisions · Actions and questions · Meeting brief |
| `SERMON` | Themes and passages · Scripture references · Sermon notes |
| `LECTURE` | Concepts covered · Definitions · Study notes |
| `INTERVIEW` | Questions and answers · Notable quotes · Interview notes |
| `IDEA`, `VOICE_MEMO`, `JOURNAL` | Key points · Cleaned-up note |

`Identifying speakers` is suppressed when `speakerCountPreference == 1` — the pipeline already skips diarisation there, so the row must not appear either.

### 5.2 `SERMON`

Add to `RecordingType` with: `displayName = "Sermon"`, `shortDescription = "Scripture, structure, and sermon notes"`, focus guidance about themes/passages/application, an `IntelligenceProfile` with decisions and actions off and questions on, `analyzingStageLabel = "Extracting themes & scripture references..."`, lecture-like `cleanupGuidance()` and `transcriptMergePolicy()` (long coherent monologue paragraphs).

Scripture detection: a deterministic reference matcher (book name/abbreviation + chapter + optional verse range) over the transcript, **not** a model task — matches carry a `segmentId` and timestamp so every reference is tappable evidence like every other citation. The LLM only ever names references it can point at.

### 5.3 Processing screen (`#7d`) and queue (`#6b`)

Single job (`#7d`): title 26sp, `42:18 of audio · started 2 minutes ago`, a 4dp `Accent` progress rule, the derived stage rows, then two honest lines — that the transcript is already readable and everything after it is an editable draft, and an estimate plus battery used so far. Footer: `Read transcript` (Ink fill, enabled as soon as the transcript exists — do not gate reading on the analysis finishing) and `Background`.

Stage row states: done = ✓ + `InkMuted` text + elapsed; running = 7dp `Accent` dot + `Ink` 500 label + detail line + elapsed in `Accent`; pending = empty 16dp gutter + `Ink` label + `~estimate` in `InkFaint`.

Queue (`#6b`): `Running` (left-ruled Accent), `Waiting` with the **reason** stated (`Waiting for the charger`, `Queued behind two jobs`) and a bar in amber/`LineSoft`, then `Finished today` with elapsed times and a flag count where relevant. `Pause all` in the header. Footer: processing keeps running if you leave the app, plus battery used today.

**Failure** (`ProcessingStage.FAILED`): the row that failed stays visible with its real error, the transcript up to that point remains readable, and the actions are `Try again` · `Run on charge` · `Use a different model`. Copy must never blame the user, and must never imply the audio is at risk. `MODEL_REQUIRED` reuses this layout with `Install model` as the primary action.

### 5.4 Deferred processing

- `QUEUED` meetings are picked up by a WorkManager `PeriodicWorkRequest` with `requiresCharging` (+ a `requiresBatteryNotLow` constraint), plus an explicit `Process now` path from home and the workspace.
- Enqueue only after the audio file is confirmed written and the meeting row is persisted.
- Home's job card (`#5a`) is the single surface that shows in-flight work; it reads the same `ProcessingJob` flow as the queue screen.

---

## 6. Notebooks, projects, tags

Foundational, and cheap to model now.

```kotlin
@Entity(tableName = "notebooks")   data class NotebookEntity(id, name, createdAt)
@Entity(tableName = "projects")    data class ProjectEntity(id, name, createdAt, archivedAt)
@Entity(tableName = "tags")        data class TagEntity(id, label)
@Entity(tableName = "meeting_collections",
        primaryKeys = ["meetingId", "kind", "targetId"])
data class MeetingCollectionEntity(val meetingId: String, val kind: String, val targetId: String)
```

- **Notebook** = a collection of knowledge (`My Sermon Notes`, `Meeting Notes`). **Project** = a goal-oriented collection (`Website Redesign`, `Client ABC`). A recording may belong to one notebook, one project, and any number of tags — enforce that shape in the DAO, not in the UI.
- v1 UI is only what `#7c` shows: an `Add to notebook or project` row opening a picker with an inline `Create new…`. No management screens yet.
- Highlights, markers and notes inherit their recording's collections for querying. Do not denormalise them onto each row.

---

## 7. Workspace, brief, evidence

Part 1 already specifies the workspace visuals. Three behaviours belong here because they are what makes the capture pipeline worth building:

1. **Every AI claim is traceable.** Decisions, actions, questions, follow-ups, scripture references and definitions all carry `sourceSegmentIds` (already in the models). The UI must render each one's timestamp as a tappable citation that seeks the player and scrolls the transcript to that segment. A claim with no resolvable source is a bug — drop it rather than show it uncited.
2. **The brief is a draft, not a report.** Summary text, decisions, action items and questions are all editable, deletable, addable and reorderable, with the user's edit marked as user-owned (the `isUserEdited` pattern already used on segments). A re-run must never silently overwrite a user-edited item.
3. **Action items are structured objects, not strings.** `ActionItem` already carries assignee, deadline, source and `isCompleted`. Render the checkbox, the assignee, the deadline and the source timestamp; make the checkbox work. Do not build a task manager beyond that.

---

## 8. Acceptance checks

The core milestone, phrased as one test:

> Walk into a two-hour meeting. Tap Record → Start (two taps from home). Put the phone down, lock it. Tap Decision on the notification-free lock screen path when something is settled. Come back two hours later, Stop.

- The recording is saved and visible as `SAVED` before any AI runs, with markers and notes attached.
- Killing the app mid-recording and reopening offers recovery, with the audio intact.
- An incoming call pauses cleanly and does not corrupt the file or the timeline.
- `Process later` genuinely defers; `Process now` runs the type-derived stage list and the transcript becomes readable before the analysis finishes.
- Every decision, action and question in the brief jumps the player and the transcript to the moment it came from.
- Transcript edits survive a re-run. Deleting nothing loses nothing.
- With no models installed, capture still works end to end and the recording sits in `MODEL_REQUIRED` with its audio intact.
- No screen in the capture flow shows red, a gradient, or a fabricated percentage.

---

## 9. Build order

1. **§3.1 + §3.2** — state machine, service owns the recorder, journal writing. Nothing else is safe until this is done.
2. **§3.7** recovery, **§3.8** warnings.
3. **§4.1 + §4.2** — new recording and recording screens, including the marker and note sheets, on top of **§3.4 + §3.5**.
4. **§4.3** saved screen, `MeetingStatus.SAVED`/`QUEUED` + migration, **§5.4** deferred processing.
5. **§5.1–5.3** type-derived stage rows, queue, failure states; `SERMON`.
6. **§3.3** segments and continue-recording.
7. **§3.6** highlights, **§6** notebooks/projects/tags (model + picker only).
8. **§7** brief editability and citation jumps.

Ship 1–4 before anything in 5–8. A calm recorder that never loses audio beats a clever one that sometimes does.
