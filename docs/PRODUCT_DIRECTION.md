# MeetingMind product direction: reconciliation and plan

What the knowledge-workspace plan asks for, measured against what this repository actually
contains today, and what I would build in what order.

Written against `claude/meetingmind-transcription-overhaul-eknt35` at schema version 12.

---

## 0. Decisions since this was written

This document argued a position. These are the decisions that were then made, and they take
precedence over anything below that disagrees. The build plan that follows from them is
**`docs/PLAN_V1.md`**.

| Question | Decision |
| --- | --- |
| What is the primary object? | **The Note.** A recording is a source a note has. Added alongside the schema, not by renaming (§3 below). |
| How far does Internet mode reach? | **Every AI stage** — transcription, cleanup, speakers, intelligence, Ask, AI tools, note AI, sermon processing. Offline remains the default. |
| Which verticals in v1? | **Professional core + Faith**, with sermons end to end as the top priority. Learning later (§4.3 below). |
| How much of the Faith Notebook in v1? | **All of it**: sermons, devotionals, bible study, prayer, prayer requests, testimonies, gratitude, scripture collections, reflections, media, a journey timeline. The topic *graph* is later. |
| Where does scripture come from? | **The YouVersion Platform** (official Kotlin SDK). Notes store references; text is fetched live, always with its version's attribution. |
| Which connectors in v1? | **Only the device calendar** — no OAuth, works with any Google or Outlook calendar already on the phone. Everything else is post-launch. |
| The schedule? | Milestone-based rather than week-based (§4.1 below). Each milestone ends with a tested APK. |

§4.2 — that nothing about transcription quality has been measured on real audio — still stands.
It is now folded into acceptance: v1 is accepted by a real sermon recorded on a real phone
(`docs/PLAN_V1.md` §11), not by the test suite.

---

## 1. The headline

**The plan's central architectural commitment is already half-built, under a different name, and
the half that is missing is smaller than it looks.**

The plan says: *a Workflow describes the user's intent; it defines what fields exist, what AI
extraction is performed, what UI is shown, what outputs are generated.*

That is `RecordingType`. It has twelve entries and five behaviour hooks:

```kotlin
RecordingType.intelligenceProfile()      // which extraction schema runs, which tabs appear
RecordingType.focusGuidance()            // what the extraction prompt attends to
RecordingType.cleanupGuidance()          // how paragraphs and turns are read
RecordingType.transcriptMergePolicy()    // how aggressively fragments merge
RecordingType.transcriptCleanupProfile() // which model tier, which validation thresholds
```

`design/capture-pipeline-implementation.md` §2.1 already says this in terms:

> Recording type as a first-class pipeline input — **this is the "workflow" architecture the
> redesign asks for. Do not introduce a parallel `Workflow` concept.**

So the plan's Phase 3 is not a greenfield build. It is a rename, a widening, and five new entries.

What is genuinely absent is Phase 2: **the Note**. Every one of the fourteen tables in this
database hangs off `MeetingEntity`. There is no object that can exist without a recording.

---

## 2. What already exists

Checked against the tree, not from memory.

| The plan asks for | Status | Where |
| --- | --- | --- |
| Workflow engine (intent → fields → extraction → output) | **Exists**, as `RecordingType` | `core/model/MeetingModels.kt` |
| Structured extraction with citations | **Exists** | `ActionItem`/`Decision`/`Question`/`FollowUp`/`Topic`, all carrying `sourceSegmentIds` |
| "Tap an item, jump to the moment it was said" | **Exists** | paragraph → utterance ids → word ids → timestamps (`ai/transcript/`) |
| AI abstraction over local + cloud | **Exists** | `LanguageModel`, `AiModelRouter`, `ProcessingProfile` |
| Synthesis engine | **Exists in embryo** | `TranscriptToolEngine`; `CREATE_NOTES`/`CREATE_OUTLINE` already emit documents |
| Semantic search over content | **Exists** | `LocalEmbeddingEngine` + `EmbeddingEntity` + `TranscriptRetriever` (hybrid, speaker- and time-filtered) |
| Export engine | **Exists** | MD, CSV, PDF, DOCX, SRT, VTT |
| Provenance: what came from where | **Partly** | per word (`TranscriptSource`), per segment (`isUserEdited`) — not yet generalised to note fields |
| Processing pipeline typed by intent | **Exists** | `ProcessingStage` + `intelligenceProfile().analyzingStageLabel` |
| Note as the primary object | **Absent** | — |
| Notebook, Project, Tag, Link | **Absent** | — |
| Attachments: images, PDFs, files | **Absent** | audio only |
| Rich-text note editor | **Absent** | transcript segment editing only |
| Scripture as a structured object | **Absent** | — |
| Timeline, topic map, cross-note synthesis | **Absent** | — |

**Roughly 60% of the plan's Phases 2–4 is done.** The missing 40% is concentrated in one place:
there is no object other than a recording.

---

## 3. The one decision that matters

> *"MeetingMind must stop thinking of a recording as the primary object."*

Agreed. But taken literally — rename `MeetingEntity` to `NoteEntity` — that is a migration
touching thirteen foreign keys and around forty files, for no user-visible gain on day one.

**Do it additively instead.**

```
NoteEntity  (new, primary)
   ├── sources:     0..n   ──►  MeetingEntity (existing, now "a recording source")
   ├── attachments: 0..n   ──►  AttachmentEntity (new)
   ├── content              ──►  blocks, rich text
   ├── workflow             ──►  RecordingType, widened and renamed
   └── notebookId           ──►  NotebookEntity (new)
```

A recording becomes *a source a note has*, rather than *the thing a note is*. Concretely:

- **Migration 12→13** creates one `NoteEntity` per existing `MeetingEntity` and links them. Every
  existing recording opens as a note with one source. Nothing breaks, nothing is rewritten.
- A typed note, an imported PDF, a photographed page: a `NoteEntity` with zero recording sources.
- `TranscriptSegment`, speakers, action items and the whole transcript stack keep keying off the
  recording. They are facts *about a recording*, and they should stay there.

This gets the plan's architecture without a big-bang rewrite, and it is the difference between
"Phase 2 is three weeks" and "Phase 2 is a month of regressions."

### Rename `RecordingType` → `Workflow`

Once a note need not have a recording, "recording type" is the wrong name for a thing that also
describes a written prayer. Same enum, same five hooks, widened:

```kotlin
enum class Workflow {
    // existing twelve, unchanged in behaviour
    MEETING, INTERVIEW, LECTURE, VOICE_MEMO, IDEA, BRAINSTORM,
    DICTATION, CONVERSATION, RESEARCH, JOURNAL, CUSTOM, GENERAL,
    // new
    SERMON, DEVOTIONAL, PRAYER, TESTIMONY, STUDY
}
```

Each new entry is an `intelligenceProfile()` branch and a `focusGuidance()` string. That is the
whole cost of a vertical's *extraction* behaviour. The design spec's instruction stands: extend
the `when` blocks, never branch on type in the UI.

---

## 4. Where I disagree with the plan

### 4.1 The schedule is wrong by a factor of two to three

Phase B is: note model, rich-text editor, multimedia attachments, notebooks, projects, tags,
search, linking, exports — listed as **week 3**. A rich-text editor with attachments is, by
itself, more than a week. Three verticals in **week 4** is not a week either.

I am not saying the scope is wrong. I am saying calling it six weeks makes the first slip feel
like failure, and the response to that feeling is usually to ship something unvalidated. The same
scope over **ten to twelve weeks** is a schedule that can absorb one bad week.

### 4.2 The real risk is not bloat — it is that the core is unvalidated

The plan's Phase A is "finish core, now → end of this week." But as of today **no transcription
quality claim in this repository has been measured on a real recording.** The canonical-transcript
work is covered by unit tests over synthetic word streams and a scripted cloud transport. That is
honest engineering, and it is not evidence.

The plan itself makes the argument better than I can:

> *If a sermon transcript doesn't sync accurately with the audio: the Faith experience sucks.*

Three verticals resting on an unmeasured core is the actual failure mode here — not feature
creep. **Validation is a phase, not a checkbox at the end of one.**

### 4.3 Launch with two verticals, not three

Professional and Faith. Learning as the first follow-up.

Not to reduce scope for its own sake, but because Learning is the one that *proves least*. A
lecture is structurally a meeting with one speaker: same extraction, same outputs, different
labels. It adds an acquisition channel and very little evidence that the platform thesis holds.

Faith is the opposite — new note types, relationships between them (request → update → answered →
testimony), a scripture object, a timeline. If the engine can carry Faith, it can carry anything.
And because Learning overlaps Professional so heavily, it becomes nearly pure configuration once
the workflow engine exists: a fortnight, not a quarter.

### 4.4 Scripture detection is an ASR problem before it is an AI problem

The plan treats scripture references as something the intelligence layer extracts. Most of the
difficulty is upstream. A preacher says *"Hebrews eleven, verse one"*; Parakeet emits
`hebrews eleven verse one`, or `he brews 11 1`, or `hebrew's 11:1`. Regex over the transcript
finds close to none of it.

What this needs:

1. **A spoken-form reference parser** — book-name variants (including "First Corinthians", "1
   Cor", "Corinthians chapter 3"), spoken numerals, ranges, "verses one through four". This is
   deterministic, testable, and has nothing to do with a model.
2. **Vocabulary biasing at recognition time** — the mechanism already exists
   (`TranscriptionOptions.vocabularyHints`, built from meeting title, focus text and learned
   corrections). Seeding it with the 66 book names for a `SERMON` workflow is a one-line change
   and will measurably improve recognition of exactly the words that matter most here.
3. **Only then** an LLM pass, for references made without naming a book ("as Paul writes to the
   Romans", "the psalmist says").

Budget this as real work. It is the single most-visible thing in the Faith vertical, and getting
it wrong is worse than not shipping it: a sermon note that cites the wrong verse is not a small
error to the person reading it.

### 4.5 One thing I would add that the plan omits

**A note has to be able to say where each of its fields came from.** The plan mentions this for
trust; I would make it structural, because it is what lets a sermon note mix AI-extracted key
points with a person's own reflection without the two becoming indistinguishable a year later.

The mechanism already exists at word level (`TranscriptSource`). Generalise it to a field:

```
NoteField.source ∈ { USER, AI, TRANSCRIPT, IMPORTED, SCRIPTURE }
```

Cheap now. Impossible to retrofit once there are 1,120 notes.

---

## 5. On the AI boundary

The plan draws a line: MeetingMind organises and reflects back a person's own material; it does
not speak as a spiritual authority.

I agree, and I would make it a code artifact rather than a principle in a document. It already is
one: `TranscriptToolPrompts.FIDELITY_CONTRACT` is a single shared clause that every model-backed
tool carries verbatim, with tests asserting no tool weakens it. It currently says, in part:

> *You MUST NOT add any fact... that is not in the transcript. You MUST NOT answer from general
> knowledge; only this transcript counts.*

For Faith workflows that clause extends by one sentence — the model may report what a speaker
said about a passage, and may not interpret the passage itself. Enforced the same way: one
contract, shared, tested.

This also happens to be the correct engineering position regardless of the subject matter. It is
the same rule that stops a meeting summary inventing a decision.

---

## 6. The order I would build in

Each phase ends with something demonstrably working, not with a layer nobody can see.

**Phase 0 — Validate the core.** *Before anything else.*
Record real audio on the S20: a two-person conversation with interruptions, a 45-minute
monologue, something with names and numbers. Compare against the previous pipeline. Fix what the
recordings expose. `TranscriptBenchmark` computes the metrics already; it needs recordings and
reference transcripts, not code.
*Gate: transcripts measurably better than v17, and playback sync accurate to the word.*

**Phase 1 — The Note, additively.**
`NoteEntity`, `AttachmentEntity`, `NotebookEntity`. Migration 12→13 creating a note per existing
recording. Note editor. Attachments. Recording becomes a source.
*Gate: MeetingMind is a useful note app with the transcription turned off.*

**Phase 2 — `RecordingType` → `Workflow`, widened.**
Rename, add the five new entries, extend the five `when` blocks. Note templates driven by
workflow rather than hardcoded per screen.
*Gate: a sermon note and a meeting note are the same code with different configuration.*

**Phase 3 — Professional vertical, completed.**
Projects, the master-note synthesis across sources, the professional exports. Mostly assembly:
the extraction, provenance and export pieces exist.

**Phase 4 — Faith vertical.**
Spoken-form scripture parser (§4.4), `ScriptureProvider` abstraction, the note types and the
relationships between them, basic timeline. No topic graph yet.

**Phase 5 — Learning vertical.** Largely configuration over Phases 1–3.

**Phase 6 — Synthesis and discovery.** Topic map, journey, cross-note study generation. This is
where the local embedding index earns its keep — and it is where the plan's most impressive
screens live, which is exactly why it should come after the foundation can hold them.

**Deferred, explicitly:** calendar, Slack, CRM, meeting bots, cloud sync, teams, church groups,
Bible reading. All of them are inputs to a knowledge engine that has to be good first.

---

## 7. The rule, restated in terms this codebase can enforce

The plan's rule is: *no feature gets added because it would be cool.* Here is the version with
teeth, because it can be checked in review:

1. **A vertical may not add a table.** If Faith needs storage Professional does not have, the
   model is wrong. (Scripture is the one permitted exception — it is a genuine new object, not a
   Faith-flavoured note.)
2. **A vertical may not add a screen.** It adds a workflow configuration and, at most, a
   template. `design/capture-pipeline-implementation.md` §2.1 already forbids branching on type
   in the UI.
3. **Every extracted item cites its source.** Anything that cannot point back at a word id, an
   attachment or a person's own typing does not get persisted as a finding.
4. **Nothing is required to have a model.** Every vertical must be fully usable with no LLM
   installed and no network. This is already true of the app today and is the easiest thing in
   the world to lose.

Rules 1 and 2 are what keep three verticals from becoming three apps. Rules 3 and 4 are what
keep it trustworthy.
