# MeetingMind v1 — Notes, Sermons, and the Faith Notebook

The build plan for MeetingMind v1. It turns the direction in `docs/PRODUCT_DIRECTION.md` into
milestones, a data model and a set of rules the code can be held to.

Status of each milestone is tracked in the table at the end, and updated as work lands.

---

## 1. What v1 is

> **A private workspace for turning what you hear, think and experience into knowledge you keep.**

Recording is one way in. Typing is another. A photo, a video, an imported file, a verse — all
ways in. What they all produce is a **Note**, and notes live in **Notebooks**.

Three decisions shape everything below:

| Decision | Consequence |
| --- | --- |
| **The Note is the primary object.** A recording is a *source* a note has. | New `NoteEntity`; every existing recording gets a note in the migration. |
| **Internet mode covers every AI stage.** Offline remains the default. | One `LanguageModelFactory`; nothing reaches for a local model directly any more. |
| **Scripture comes from the YouVersion Platform.** | Notes store *references*; verse text is fetched live with attribution. |

## 2. Scope

**In v1**

- **Notes** — rich text, headings, lists, checklists, quotes, dividers, photos, videos, audio clips,
  scripture, transcript excerpts, links between notes, tags, notebooks, search, PDF / Word /
  Markdown export.
- **Sermons end to end** — record → transcribe → detect scripture → sermon note → read verses → export.
- **The Faith Notebook** — Sermons, Devotionals, Bible Study, Prayer, Prayer Requests (with an
  answered lifecycle), Testimonies, Gratitude, Reflections, Scripture collections, Media, a Journey
  timeline, On-this-day, and a basic Themes list.
- **Internet mode everywhere** — transcription, cleanup, speakers, intelligence, Ask, AI tools, note
  AI and sermon processing all use Gemini when it is on.
- **Device calendar** — the one connector in v1: "Up next" on Home, one tap to record it pre-filled.

**Not in v1** (deliberately — see §9)

Projects and master-note synthesis · the topic graph · the Learning vertical and flashcards ·
Slack, Notion, CRM, Drive · meeting bots · cloud sync and teams · BYOK beyond the current Gemini key ·
an offline Bible.

## 3. Data model

Added alongside the existing schema — nothing is renamed, nothing existing is rewritten.

```
NotebookEntity ─┐
                ▼
            NoteEntity ──► NoteBlockEntity (ordered content)
             │   │  │  ──► AttachmentEntity (photos, video, audio, files)
             │   │  └────► ScriptureRefEntity (every verse the note cites)
             │   └───────► NoteTagCrossRef ──► TagEntity
             └───────────► NoteLinkEntity (note ↔ note, typed)

MeetingEntity.noteId ──► NoteEntity      (a recording is a source a note has)
```

| Entity | Purpose |
| --- | --- |
| `NoteEntity` | Title, workflow, notebook, event date, pinned, **private**, status (for prayer requests), workflow metadata as JSON. |
| `NoteBlockEntity` | One unit of content. Carries its **source** — `USER`, `AI`, `TRANSCRIPT`, `IMPORTED`, `SCRIPTURE` — and, for anything derived from a recording, the segment ids it came from. |
| `AttachmentEntity` | Media copied into app storage, so a note never depends on a gallery item that might be deleted. |
| `NotebookEntity` | A collection, with a **space**: Work, Learning, Faith or Personal. |
| `NoteLinkEntity` | `RELATED`, `UPDATE_OF`, `ANSWERS`, `TESTIMONY_OF`. The prayer lifecycle is built from these. |
| `ScriptureRefEntity` | Book (USFM), chapter, verse range, optional version, where it came from, and — for detected references — the recording segment and timestamp. |
| `ScriptureCollectionEntity` | A user's named list of verses. |

**Why blocks, not one rich-text blob:** a sermon note mixes AI-extracted key points, the preacher's
verbatim words, live scripture and the person's own reflection. Each needs to say where it came
from, and each renders differently. A block that knows its source is the only representation that
keeps those distinguishable a year later.

## 4. Workflows

A workflow is `RecordingType`, widened. There is no second concept.

`design/capture-pipeline-implementation.md` §2.1: *"This is the workflow architecture the redesign
asks for. Do not introduce a parallel Workflow concept."*

New entries: `SERMON`, `DEVOTIONAL`, `PRAYER`, `PRAYER_REQUEST`, `TESTIMONY`, `GRATITUDE`,
`BIBLE_STUDY`, `REFLECTION`.

Each workflow is configuration through the same hooks every type already has, plus five new ones:

| Hook | Answers |
| --- | --- |
| `space()` | Which part of the app it belongs to. |
| `isRecordable()` | Whether it can start from the recorder. A prayer can; a gratitude note is typed. |
| `noteTemplate()` | The sections a new note opens with, who fills each (AI or the user), and which are private. |
| `processingStageRows()` | What the processing screen shows — never a row for work that won't happen. |
| `vocabularyHints()` | Words recognition should favour. For a sermon: the 66 book names, the speaker, the church. |

## 5. Sermons, end to end

The vertical that matters most, so it is specified precisely.

1. **Capture.** Sermon is in the recorder's type list and Home's quick types. Title, speaker, church,
   date and translation are all optional — one tap still starts recording.
2. **Transcribe.** With Internet on, Gemini's verbatim and smart passes, biased toward the book
   names and the speaker's name.
3. **Find the scripture.** A deterministic parser reads the transcript's words. It understands how
   preachers actually say references — *"John three sixteen"*, *"First Corinthians chapter thirteen,
   verses four through seven"*, *"Psalm twenty-three"* — and rejects anything that doesn't exist
   (there is no John 30). Every match keeps the segment and timestamp it came from.
4. **Build the sermon note.** Gemini extracts the title, main scripture, key message, key points,
   application, memorable quotes, prayer points and reflection questions — each citing the
   transcript segments it came from. Then everything is checked:
   - a citation that doesn't resolve to a real segment is dropped
   - a "quote" that isn't word-for-word in the transcript is dropped
   - a reference the model proposes must also pass the parser, or it is dropped
5. **Read and act.** Tapping a verse opens it with live text, a translation switch and the required
   attribution; or jumps to the moment the preacher read it.
6. **Export.** PDF and Word, with verse text and attribution. Private sections left out by default.

The sermon note's shape:

```
WALKING BY FAITH                       ← AI, editable
26 September 2026 · Pastor John · Grace Chapel

MAIN SCRIPTURE      Hebrews 11:1       ← SCRIPTURE, tap for text
KEY MESSAGE                            ← AI, cites 14:02
KEY POINTS          1. …  2. …  3. …   ← AI, each cites its moment
SCRIPTURES          Hebrews 11:1 · Romans 8:28 · 2 Corinthians 5:7
QUOTES              "…"                ← TRANSCRIPT, verbatim, 22:41
MY NOTES            [ … ]              ← USER
MY REFLECTION       [ … ]              ← USER, private
APPLICATION         [ … ]              ← USER
PRAYER              [ … ]              ← USER, private
```

## 6. The Faith Notebook

Every item below is a note on the same engine, with a different template. None of them adds a table.

| Type | Sections |
| --- | --- |
| **Devotional** | Scripture · What stood out · What it means to me · Prayer · Today I will |
| **Prayer** | Free text or a spoken prayer, transcribed |
| **Prayer request** | What I'm asking for · Updates (linked notes) · **Mark answered** |
| **Answered prayer** | Created from a request, linked to it |
| **Testimony** | Situation · What I prayed for · What happened · What I learned · Scripture · Media |
| **Gratitude** | What I'm thankful for |
| **Reflection** | Free-form |
| **Bible study** | Passage · Observations · What I think it means · Application · Questions |

**The prayer lifecycle** is the part that makes this more than a journal:

```
Prayer request "A new job"   ── OPEN
      │  UPDATE_OF
      ├── "Two interviews this week"
      │  ANSWERS
      └── Answered prayer "Offer from Acme"  ── request marked ANSWERED
            │  TESTIMONY_OF
            └── Testimony "God opened a door"
```

**Faith home:** Verse of the Day (from YouVersion) with *Start devotional* · quick create · open
prayer requests · recent · journey counts.

**Journey:** entries grouped by month, counts per type, and *On this day* — "a year ago you wrote
this prayer".

**Themes:** the topics that recur across sermons, devotionals and prayers, with counts. Tap one to
see every note on it. (The graph view is later.)

**Scripture collections:** named verse lists, and an *All scriptures* view grouped by book.

**Privacy:** Faith notes are private by default. Private notes are left out of sharing and export
unless the user chooses otherwise. An optional app lock uses the phone's own biometric prompt.

**Look:** the same design system, with a serif face (Source Serif 4, open licence) for Faith note
body and verse text only. The design file has no Faith frames, so this is recorded here as a
design decision rather than inherited from one.

## 7. Scripture and YouVersion

**Provider.** `ScriptureProvider` is an interface; `YouVersionScriptureProvider` implements it
against the YouVersion Platform REST API (`api.youversion.com/v1/bibles/{version}/passages/{USFM}`,
header `X-YVP-App-Key`) over the app's existing OkHttp client, with its own small cache. Nothing
outside the provider knows YouVersion exists.

*Why not the Kotlin SDK (decided in M2):* `platform-*:2.1.3` and the Compose rich-text library both
pull Compose 1.12 and the Kotlin 2.4 standard library into a project built with Kotlin 2.2.10.
Taking them means upgrading the whole toolchain mid-milestone, for UI we would restyle anyway.
The REST API is the same data under the same licence terms. Revisit when the project moves to
Kotlin 2.4.

**Licensing — this shapes the design:**

- Every displayed passage **must show its version's attribution** (the copyright text from the
  version's metadata). The verse sheet and every export do.
- Copyrighted translations (NIV, ESV, NLT…) are licensed for on-demand display, not storage.
  **So notes store the reference, not the text.** Text is fetched when shown and again at export time.
- Open translations — public domain or an open licence (BSB, WEB, ASV, LSV…) — may be copied, so
  the app keeps them on the phone: each chapter read is kept, and a whole translation can be
  downloaded for offline reading and search. `BibleLicensing` decides, and the store refuses
  anything else as a second check.
- Offline, a reference still shows — *"Hebrews 11:1 · text unavailable offline"* — and still links
  to the moment it was said.
- The app key comes from `local.properties` or an environment variable into `BuildConfig`. It is
  **never committed** to this public repository. The SDK is designed for the key to ship in the app,
  so it does.
- Setup is a one-time step for the developer: register the app at platform.youversion.com and
  accept the licence for each translation to offer (NIV, KJV, and so on).

### 7.1 The whole Bible (added in M7)

A verse lookup isn't enough for Faith: people read chapters, search for a word they half remember,
and take passages into their notes. So the Faith features talk to one interface and never to a
vendor:

```
BibleProvider  (bibles · chapter · passage · search · verse of the day)
   └── BibleLibrary          phone first, network second
         ├── BibleStore       SQLite + FTS4 on the phone: open translations only
         └── YouVersionScriptureProvider   REST, session cache
```

- **Reader** (`feature/bible`): translation menu (the list the API says this app is licensed for),
  books and chapters, paragraphs and poetry as the translation sets them, tap verses to select a
  run, then insert into a note, start a devotional, save to a collection, copy or share. Opens as a
  route from Faith and as a full-screen picker from the editor and the verse sheet.
- **Search**: YouVersion has no search endpoint, so search runs over a translation downloaded to the
  phone (`BibleDownloadWorker`: 1,189 chapter requests, four at a time, about 2–3 minutes, resumable,
  with a notification). Typing a reference ("Rom 8:28") jumps straight there without a download.
- **Other providers**: API.Bible (has server-side search and audio) and Bible Brain (audio) fit
  behind `BibleProvider` without touching the Faith UI. Deciding between them is a licensing
  decision — which translations, and on commercial terms — so it waits until MeetingMind's pricing
  is known. Licensed translations appear automatically once accepted at platform.youversion.com.

## 8. The AI boundary

MeetingMind helps a person organise, remember, search, connect and reflect on **their own
material**. It does not speak as a spiritual authority.

| Allowed | Not allowed |
| --- | --- |
| "These three sermons all reference Matthew 6." | "God is telling you to forgive your brother." |
| "You've written about forgiveness in 12 notes." | Interpreting a passage in its own voice. |
| "The pastor said this about grace — how does it apply to your week?" (grounded in a cited moment) | A reflection question with no source. |

This is enforced in code, the same way the transcript tools' fidelity contract already is: one
shared clause every Faith prompt carries verbatim, and a test that fails if any prompt drops it.

## 9. Rules that stop v1 becoming bloated

These can be checked in code review.

1. **A vertical may not add a table.** If Faith needs storage the Note model doesn't have, the Note
   model is wrong. Scripture references are the one exception — a verse is a genuinely new kind of
   object, not a Faith-flavoured note.
2. **A vertical may not branch the UI on its type.** It adds a template and hook configuration.
3. **Everything AI-derived cites its source.** Anything that can't point at a transcript segment, an
   attachment or the user's own writing is not saved as a finding.
4. **Nothing requires a model.** Every note type is fully usable with no AI installed and no
   network. Internet mode makes things better; it never makes them possible.
5. **A new feature must strengthen one loop** — capture, understand, organise, transform. "It would
   be cool" is not a reason.

## 10. Milestones

| # | Milestone | Status |
| --- | --- | --- |
| M0 | This document; `PRODUCT_DIRECTION.md` updated | done |
| M1 | Internet mode everywhere — one `LanguageModelFactory` | done |
| M2 | Notes data layer, migration 12→13, export document model | done |
| M3 | Notes UI — library, notebooks, editor, export, navigation | done (v19) |
| M4 | Workflows widened — templates, processing rows | done (v20) |
| M5 | Scripture — parser, YouVersion provider, verse sheet | done (v20) |
| M6 | **Sermons end to end** | done (v20) |
| M7 | Faith Notebook, plus the whole-Bible reader and search | done (v20) |
| M8 | Device calendar | done (v21) |
| M9 | Note AI | done (v21) |

Each milestone from M3 on ends with a tested arm64 APK in `dist/`.

**Decisions made while building**

- *M1.* Ask, AI cleanup, speaker reconciliation and every AI tool now get their model from
  `LanguageModelFactory`. Under Internet mode, cloud intelligence falls back to the on-device engine
  on failure rather than leaving a recording with no summary. Fixed along the way: v18's Internet
  mode ran locally even with a key entered, because the transport cached "not configured" at
  startup and never re-read it.
- *M2, rich text.* Notes use MeetingMind's own `RichText` (text plus style ranges), edited in
  per-block text fields, instead of an HTML editor library. The dependency cost is above; the gain
  is that every editing rule (toggle, continue-style-while-typing, split on Enter, merge on
  Backspace) is plain Kotlin with unit tests.
- *M2, export.* One `ExportDocument` feeds three renderers. The Word renderer writes real styles
  (headings show in the navigation pane), real restartable lists, working links, embedded images and
  page numbers. The PDF renderer paginates line by line. Meeting exports move onto it in M3.
- *M2, search.* Notes join search by title and text now. Note *embeddings* move to M9, where
  "related notes" is the feature that needs them; searching by meaning over notes before then would
  be an embedding pass with nothing using it but search.
- *Background work (between M3 and M4).* Opening a recording's processing screen used to enqueue
  it again, so a transcript that finished overnight appeared to start over. The screen now checks
  the recording's state first; `ProcessingScheduler` never enqueues twice; decoding checkpoints each
  finished window so an interrupted transcription resumes rather than restarting; interrupted work
  is re-queued at launch. Model downloads moved to background work with HTTP Range resume.
- *M4.* The eight Faith workflows are `RecordingType`s with templates, like every other type. The
  processing screen's rows come from the workflow (`Workflows.processingStageRows`).
- *M5.* The reference parser is deterministic and needs a cue before accepting ambiguous book names
  ("John", "Acts", "Job") so a person called John isn't a Bible reference. The version list comes
  from the API rather than a fixed list: this app's key currently serves 11 English translations,
  all open; NIV, KJV and others appear once their licences are accepted.
- *M6.* Sermon notes are built from the transcript by one extraction pass whose quotes must be
  verbatim and whose citations must resolve. Regenerating never overwrites a section the person
  edited.
- *M7, lock.* The Faith lock uses the phone's own screen lock (KeyguardManager) rather than
  `androidx.biometric`: one less dependency, and it accepts fingerprint, face, PIN or pattern.
- *M7, type.* Faith notes and verse text use the system serif rather than a bundled Source Serif —
  about 1 MB saved, and it reads well on Samsung and Pixel alike.
- *M8.* The calendar is read through Android's own calendar provider, so every calendar already
  synced to the phone works with no sign-in. It is off until the person turns it on (a one-time
  invitation on Home, or Settings → Calendar), which is when READ_CALENDAR is asked for. Each event
  occurrence gets one note (found again by its event id and start time) holding the guests, place
  and time; Record files into that note with the title, a type guessed from the title, and a
  speaker count from the guest list — all changeable before recording starts. Also fixed: the type
  picked in Home's Record row now reaches the recorder.
- *M9, engine.* One `NoteAiEngine` over `SourcePassage`s (note blocks and transcript paragraphs)
  with its own contract, plus the Faith clause for Faith material. Passages go to the model as
  `p1`, `p2`…; results are validated — citations must resolve, numbers must appear in what they
  cite, and organised text must be the person's own words. Anything that fails is dropped, not
  shown.
- *M9, jobs.* Runs are rows in `note_ai_jobs` (migration 13→14) worked by `NoteAiWorker`, so a
  result waits in the note if the app is closed. Results are offered, never written in without
  the person choosing to; applying one is undoable, and organising keeps every sentence it didn't
  use under "Other notes".
- *M9, privacy.* In Internet mode a notebook run leaves private notes (prayers, journals) on the
  phone and says so; a private note's text is only sent when the person runs a tool on that note.
- *M9, related notes.* No embeddings: the existing on-device "embedding" is a 64-dimension hash of
  words, not a semantic model. Related notes instead scores shared chapters, tags, links and TF-IDF
  wording — instant, offline, and every match shows why. A real embedding model can replace the
  wording part later without changing the feature.
- *M7, storage.* Translations whose licence allows copies are kept on the phone (chapters as they
  are read, or the whole translation on request); copyrighted ones never are.

## 11. How v1 is accepted

Not by passing tests. By this, on a real phone:

1. Record a real 20–40 minute sermon with Internet on.
2. The verses the preacher cites appear, each jumping to the right moment.
3. Verse text loads, with attribution.
4. The sermon note reads well, and its PDF and Word exports open cleanly in Word and Google Docs.
5. A devotional from the Verse of the Day; a prayer request, marked answered, turned into a
   testimony — and the Journey shows all of it.
6. Internet off: everything still works, verses marked unavailable offline.
