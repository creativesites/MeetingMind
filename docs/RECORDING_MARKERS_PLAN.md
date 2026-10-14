# Recording → Transcript → Notes: audit and staged plan

Status: audit complete; implementation not started. This plan is for review before schema or code changes.
Source spec: the "Recording → Transcript → Notes" proposal (sections 1–13 of the spec as supplied).

## 1. What the code does today (audit)

| Area | Current behaviour | Where | Gap against spec |
|---|---|---|---|
| In-flight marks | Singleton `RecordingMarks` holds an in-memory `StateFlow<List<Mark>>`. Marks have no recording identity. | `core/work/RecordingMarks.kt` | Not durable until Stop. A process death loses every mark. Spec §3.2/§6.1 requires a Room insert at tap time. |
| Persistence | `Marks.save` appends JSON to `NoteEntity.metadataJson["marks"]`, called only on stop. | `core/work/Marks.kt:31-44`; called from `MeetingRecordingService.kt:151` and `RecordingScreen.kt:182` | Note-level JSON with no source. Spec §6.1 says do not keep new markers as unscoped JSON. |
| Ownership | `Mark(kind, atMs, text)` has no `meetingId`, `id` or `sequence`. | `Marks.kt:19` | Cannot tell Part 1 from Part 2. |
| Reconciliation | `Marks.reconcile(database, meetingId, segments)` reads marks from the **shared note** (`noteDao.getById(noteId).metadataJson`) with no meeting filter, then matches them against **this meeting's** segments. | `Marks.kt:61-69` | This is the known bug (spec Stage 0). Once a note has two recordings, Part 2's processing reconciles Part 1's marks against its own segments. |
| Key moments | `keyMoments()` returns `List<Long>` with no source. | `Marks.kt:50` | Same ownership problem. |
| Section notes | `MarkerNote.kt:104` reads note-level marks to build note sections. | `core/work/MarkerNote.kt` | Needs source-aware reads and the spec's routing rules. |
| Marker kinds | `MarkKind`: KEY, ACTION, QUESTION, DECISION, SCRIPTURE, NOTE, PRAYER. | `Marks.kt:9-14` | Close to the spec's kinds. IDEA is missing. Labels are fixed, not workflow-driven. |
| Recording to note link | `MeetingEntity.noteId` exists. `MeetingEntity` has no `recordingOrder`. | `database/Entities.kt:12-44` | Spec §7.1 needs an explicit order. |
| Workflows | `Workflows.template(type)` exists in `model/Workflows.kt`. | `core/model/Workflows.kt` | Needs capture-context fields, marker actions and routing policies declared on it. |
| Context | `MeetingEntity.customContext` and `speakerCountPreference` exist. No typed snapshot. | `Entities.kt:25-28` | Spec §5.1 needs an immutable `CaptureContextSnapshot`. |
| Schema | Room DB, `MeetMindDatabase.VERSION`, latest migration 23→24 in `ALL_MIGRATIONS`. | `database/MeetMindDatabase.kt` | Stage 1 adds an additive migration after the current version. Confirm `VERSION` before writing it. |

Confirmed bug: `Marks.reconcile` (`Marks.kt:61-69`) is the cross-recording mis-association the spec describes. It needs a regression test in Stage 0 before any fix.

## 2. Decisions needed from you before Stage 1

1. **Marker kinds.** Keep the current seven kinds and add IDEA, or use the spec's list verbatim? Proposal: keep the seven, add IDEA, and map SCRIPTURE and PRAYER to the spec's Scripture and Prayer point kinds.
2. **Unknown or unmapped markers.** Proposal: route to an explicit "Other moments" section in the workflow's template. Confirm the section name.
3. **Legacy ambiguous marks.** For a multi-recording note, a legacy mark with no source is kept as an unassigned note-level marker that the user assigns. Confirm this policy. It is the spec's default.
4. **Section order.** Already decided in the spec: group by template section, chronological within a section, global timeline separate. No change needed.
5. **Scope of this pass.** The spec has six stages. Realistic first pass: Stage 0 (regression test that fails on the reconcile bug) plus Stage 1 (table, DAO, migration, legacy migration tests). Stages 2–6 need device testing and are a separate review.

## 3. Staged plan

**Stage 0: audit and contract tests** (no behaviour change)
- Failing regression test: a Part 1 marker must not reconcile against Part 2 segments. Use `Marks.reconcile` with a fake database.
- Golden tests for each workflow's note template (`Workflows.template`).
- Exit: the reconcile bug is demonstrated by a red test.

**Stage 1: persistence and migration**
- `RecordingMarkerEntity` as in spec §6.1, FK to `MeetingEntity` (cascade on recording deletion only), indexes on `(meetingId, timestampMs)` and `(meetingId, sequence)`.
- Additive migration from the current `VERSION` using `CREATE TABLE IF NOT EXISTS`. Add `recordingOrder` to `MeetingEntity` with backfill by `createdAt`.
- Legacy `metadataJson["marks"]` migration: single-source notes migrate with timestamps kept. Multi-source notes keep unassigned rows. Malformed JSON and unknown kinds never crash migration; the raw value is preserved until counts are validated.
- `RecordingMarkerDao`: insert, update, delete, ordered observation, source isolation.
- Tests: `RecordingMarkerDaoTest`, `LegacyMarkerMigrationTest`, `RecordingClockTest`.
- Exit: taps survive process death, old data migrates, markers are queryable by source.

**Stage 2: capture UX and lifecycle**
- Tap writes Room directly. UI reads from a Flow. `RecordingMarks` becomes a view, not the source of truth.
- Quick-note drafts autosave. Undo is limited to the latest unedited mark of the active recording.
- Notification and lock-screen marks route through the same repository.
- Saved-screen actions: Process now, Process later, Add another recording.

**Stage 3: context and prompt pack**
- Workflow-declared capture fields and an immutable `CaptureContextSnapshot` (additive `captureContextJson`).
- One `ContextPackBuilder` with source IDs. Tests prove context never overrides transcript text and marker-only claims are not attributed to the speaker.

**Stage 4: marker-to-transcript and note integration**
- `sourceMarkerIds` added to note block provenance.
- Transcript-selection highlights as a separate entity with quoted snapshot.

**Stage 5: multi-source ordering, playback and synthesis**
- Two-step resolver: `toNoteTime(meetingId, localMs)` and `toSource(noteId, noteMs)`.
- `SynthesizeNoteUseCase` with idempotent merges and partial-coverage reporting.

**Stage 6: dogfood and quality gates**
- Device tests: Bluetooth, screen lock, pause and resume, interruption, process kill, low storage.
- Full unit tests, migration tests, lint and APK build. Results attached to the PR, not claimed without a run.

## 4. Verification status

- Build and test runs: **not run**. This session did not run Gradle, so no suite result is claimed.
- Stage 0 red test: not yet written.

## 5. Next step

Reply with answers to section 2, and I will start Stage 0 and Stage 1 in order. Each stage ends with the tests above run and their results reported.
