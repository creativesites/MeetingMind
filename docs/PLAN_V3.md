# MeetingMind v3 plan: notes for everything, AI with real power

**Status:** Approved direction. Round 2 answers are recorded in the decisions table (D13–D18).
M0 has its own PRD, `docs/PRD_M0.md`. C4, C5 and C8 are still open (§9).
**Done state:** everything in this document ships. It takes weeks, and that is accepted. There is no
rush to cut the next build.

---

## 1. Context

v2 (F0–F7, APK v30) delivered the Today hub, the devotional engine, voice, stories, the offline
Bible, reading plans, prayer and Live. The user now uses MeetingMind as their **primary notes app**
for everything: work, faith and personal. The professional vertical is next. Professionals need a
notes system that competes with the best (Craft, Bear, Notion, Obsidian, Apple Notes, Granola,
Otter) on function, ease of use and AI.

### Decisions the user made (this round)

| # | Question | Decision |
|---|---|---|
| D1 | Scope | **Everything in this document is v3.** That includes Transcription Engine 2.0, scene detection, the sermon corpus, word-level karaoke playback, a second home layout, the Bible study overhaul, backlinks and graph view, semantic search and collaboration. It also includes every "things you haven't asked for" item. |
| D2 | Markdown paste | **Parse into native blocks**, but **keep the block count low**. A document should read as one flowing page, not a stack of boxes. Also offer a **Markdown block** that holds a whole pasted document as one unit. |
| D3 | AI principle | **The PLAN_V1 "AI never rewrites the person's words" rule is retired for Notes.** It was written for small on-device models. Notes AI gets full generative power: rewrite, expand, restructure, merge, continue writing. **Transcripts keep their integrity:** the original transcript is never changed. A user can copy it into a note and rewrite it completely there. |
| D4 | Gating | **No gating in Notes.** Notes AI and transcription have separate policies (§2). Notes is about productivity and uses cloud AI whenever a key is set, with no per-note consent prompts. |
| D5 | Selecting across blocks | **Select mode.** Long-press a block, drag handles across blocks, then Copy / Cut / AI / Delete. Copy puts both Markdown and HTML on the clipboard. |
| D6 | Data safety first | **Yes.** Remove `fallbackToDestructiveMigration()`, export the Room schema and add migration tests before any schema change. |
| D7 | Notes assistant | **An AI chat sidebar that acts on the note.** It can insert, update, move and remove blocks (with confirmation), create notes, generate images and export. It has **skills** and **tools**. Flagship use case: paste a long ChatGPT answer and a long Claude answer, then have the assistant reconcile them, cut what isn't needed and rewrite them into one plan **without losing information**. |
| D8 | Model | Gemini 3.6 Flash / 3.8 Flash class models. Prompts must be watertight; §4.W3 covers how we prove that. |
| D9 | Notes list performance | Notes are growing fast. Use the best loading approach (paging, projections, FTS). |
| D10 | Theme | Minimalist and high-end. **Dark mode is the default.** Theming and the home screen are highly customisable, and a second home layout is on offer. |
| D11 | Devotionals | Fix the repetition. Past devotionals can be reread on demand. The structure becomes customisable across Christian traditions. |
| D12 | Notes extras | AI image generation as a block, PDF generation, inline YouTube, remote images, copy. |
| D13 | Paste default (C1) | **Formatted native blocks** by default, then a chooser: *Keep as one Markdown block* / *Plain text*. |
| D14 | Assistant approval (C2) | **Deletes and replacements always wait for Apply.** Inserts apply automatically (one-tap undo); a setting makes them wait too. |
| D15 | Gating edges (C3) | No gating in the assistant, including Faith and Private notes. "Private" means left out of share/export by default and locked by app lock. The devotional's `sharePrivateWithCloud` switch stays: it controls data the app sends on its own initiative. |
| D16 | Embeddings (C6) | Gemini cloud embeddings when a key is set; an on-device embedding model as the offline fallback. |
| D17 | Package rename (C7) | In M0, sequenced after backup/restore ships (see `PRD_M0.md` §3). |
| D18 | Tasks (W10) | Confirmed as wanted. |

---

## 2. The two AI policies (replaces PLAN_V1 §7–8 for Notes)

| | **Notes (productivity)** | **Transcripts and recordings (evidence)** |
|---|---|---|
| Purpose | Help the person write, think and produce | Keep a faithful record of what was said |
| AI power | Full: rewrite, expand, shorten, restructure, merge, translate, continue, generate images, tables and diagrams | Clean-up, summaries and tools stay derived views with citations |
| Source of truth | Whatever the person keeps. Version history is the safety net | The verbatim words, timestamps and speakers. Never overwritten |
| Consent | None per action. A key being set is enough | Unchanged (the processing profile decides) |
| Safety net | Every AI change is one undo step plus an automatic version snapshot. Destructive assistant actions show a change set to approve | The existing diff review (`CleanupReviewScreen`), "Show original" and `cleanedText` beside `text` |
| Crossing over | "Send to note" copies transcript text into a note, where it is freely editable and keeps citation links back to the audio | — |
| Faith | The devotional theology contract stays (§4.W8). That is about content quality, not gating | Scripture reference detection only |

`GeminiLanguageModel.DEFAULT_SYSTEM_INSTRUCTION` ("answer only from the transcript… JSON only") is
transcript-shaped. Notes get their own system instructions and their own model route.
`NoteAiEngine`'s "no continue writing" rule and `NoteAiApply`'s "never replace" rule are removed for
Notes.

---

## 3. What the code shows (findings this plan is built on)

| Area | Finding | Where |
|---|---|---|
| Paste | Pasted text is split at every `\n` into plain blocks of the same type. There is no Markdown or HTML parser for incoming text, and shortcuts skip pasted text on purpose. The block model already has headings, lists, checks, quotes and inline styles, and `RichText.toMarkdown()` already exists. **Only the parser is missing.** | `feature/notes/editor/BlockEditing.kt:33` |
| Missing block types | No code block, table, callout, toggle/collapsible, embed (YouTube or bookmark), remote image, Markdown block or AI image | `core/model/Notes.kt:53` |
| Copy | One `BasicTextField` per block, so a selection can't extend past one block. The editor has no clipboard code | `NoteBlocks.kt:196` |
| AI layer | `LanguageModel` is `generate(prompt) -> String`: single turn, no streaming, no tools/function calling, no chat history, and a transcript-only system instruction | `ai/llm/MeetingIntelligenceEngine.kt:19`, `ai/cloud/GeminiLanguageModel.kt` |
| Data safety | `fallbackToDestructiveMigration()` with `exportSchema = false`. **One missed migration wipes every note.** Android Auto Backup also caps at 25 MB and excludes recordings | `core/database/MeetMindDatabase.kt:448` |
| Bible highlights | Stored in `BibleStore`'s separate SQLite, **outside Room**. Not linked to notes, not in any export | `core/scripture/BibleStore.kt:54` |
| Notes list | Every note row, **including its full `plainText`**, is loaded into memory. Filtering is an in-memory `contains` and the whole list is re-sorted on every emission. The draft flag is `metadataJson NOT LIKE '%"draft":"1"%'`, which scans every row with no index. Each autosave bumps `updatedAt`, so the whole list re-emits | `feature/notes/NotesViewModel.kt:59`, `core/database/NoteDaos.kt:48` |
| Search | `LIKE` queries. No FTS | `NoteDaos.searchText`, `Repositories.searchHybrid` |
| "Semantic" embeddings | `LocalEmbeddingEngine` is a **64-dim hash of words**, not a semantic model | `ai/embeddings/EmbeddingEngine.kt` |
| Theme | "CleanMyMac" palette with indigo, purple, magenta and cyan gradients on cool slate. `darkTheme = false` is hardcoded and dynamic colour is off. `Ink*` tokens are light-only constants. **27 files** hardcode `Color(0x…)`, with about 400 colour references outside `ui/theme` | `ui/theme/*.kt` |
| Devotional repetition | (a) The same JSON sections every day. (b) **No memory:** `DevotionalRepository.recent()` exists but nothing calls it, so the prompt never sees past devotionals. (c) Passages come from small topic lists keyed by date. (d) The same tone every day | `ai/devotional/DevotionalContract.kt`, `DevotionalEngine.passageFor` |
| Devotional archive | `DayList` only switches between devotionals from the same day. No history view | `feature/devotional/DevotionalScreen.kt:262` |
| Things we can reuse | Gemini image generation (`ImageBackgrounds`), `PdfExporter`/`PdfDocumentRenderer`, DOCX/Markdown export, `note_links` and the `NOTE_LINK` block, `scripture_refs` (note ↔ verse), Bible cross-references and commentary tables, per-segment `wordsJson` (karaoke is possible), `cleanedText`, `seekPlayback`, Coil, device calendar "Up next", `chat_messages` (meeting chat), Gemini Live, fonts (Inter, Lora, Outfit, Playfair) | various |
| Android backup | `meetmind_database` is excluded from **both** cloud backup and device transfer (`res/xml/*backup*`), so a new phone loses every note | `res/xml/data_extraction_rules.xml` |
| Delete | `deleteNote` deletes permanently: blocks and attachment files, no undo | `NoteRepository.kt:224` |
| Devotional timing | **Fixed (3e2b7e6).** 24-hour periodic WorkManager jobs re-synced with `UPDATE` kept no time of day, so the devotional and its notification drifted. Now wall-clock alarms (`core/notify/DailyAlarms.kt`); prayer, reading and evening reminders had the same bug and are fixed too | `core/devotional/DevotionalWork.kt` |
| Sync | Firestore syncs **meeting metadata only**. No note sync, and there's no backend for collaboration | `core/firebase/FirebaseManagers.kt` |
| Share target | The manifest has no `ACTION_SEND` intent filter, so other apps can't share into MeetingMind | `AndroidManifest.xml` |
| Open roadmap items | Package still `com.example`. No CI | `docs/ROADMAP.md` P2 #18, #19 |

---

## 4. Workstreams

Each workstream lists **Why**, **What** and **Done when**. IDs are stable so PRDs can reference them.

### W0. Foundations: data safety and the platform

**Why:** MeetingMind is now someone's primary notes app. Losing notes is the one unforgivable bug.

- Remove `fallbackToDestructiveMigration()`. Turn on `exportSchema = true`, commit the schema JSONs,
  and add a `MigrationTestHelper` test for every version from 1 → current, run against seeded
  real-looking data. If a migration is missing, the app refuses to open the database and offers
  an export, instead of wiping it.
- **Full backup and restore:** a `.mmbackup` zip containing a JSON dump of every table, attachments,
  the Bible highlights DB and, optionally, recordings. It can be scheduled to a folder chosen via
  SAF (Drive, local), and restore works on a fresh install. Human-readable **Markdown export of
  everything** comes alongside it (a folder per notebook, images next to the notes; Obsidian can
  open it).
- **Trash:** deleted notes and notebooks stay recoverable for 30 days.
- **Version history per note:** a snapshot before every AI change, paste, bulk edit or restore, and
  on a timer while typing (coalesced). A list shows who/what changed it (you / AI skill name /
  import), with a diff view and restore. Stored compactly (block-level deltas or zstd snapshots)
  and pruned to a budget.
- Move note metadata flags out of `metadataJson` into real indexed columns (`isDraft`,
  `devotionalKey`, `kind`), with a migration.
- Move Bible highlights into Room, or back them up and link them (see W9).
- CI: GitHub Actions running `assembleDebug`, unit tests, lint and Roborazzi on every push.
- Baseline profile and startup trace. R8 enabled for release.
- **The package/applicationId rename** from `com.example` / `com.aistudio.meetmind.qxynvp` must
  happen before any Play release. **Changing the applicationId makes it a new app with no data.**
  It ships together with backup/restore (question C7).

**Done when:** migration tests go green from every version; a backup → uninstall → restore round
trip loses nothing; a deleted note can be recovered; every AI edit can be restored from history.

### W1. The editor, rebuilt around a flowing document

**Why:** the editor is where professionals spend their day. Today it feels like a stack of boxes.

**Paste (D2):**
- A `MarkdownParser` (CommonMark + GFM: headings, emphasis, strike, `==highlight==`, `<u>`,
  inline code, links, autolinks, lists nested to `MAX_INDENT`, task lists, block quotes, fenced
  code with language, tables, thematic breaks, images, bare image URLs, bare YouTube URLs,
  hard/soft line breaks) that produces `List<NoteBlock>`. It is the exact inverse of the existing
  serializer, and round trips are tested.
- An `HtmlParser` for rich clipboard content (Chrome, Docs, Gmail, Word) that maps into the same
  blocks. HTML is preferred when the clipboard has it.
- **Lower block count:** a Markdown paragraph stays one block, and soft line breaks inside it
  become `\n` within the block, not new blocks. Consecutive quote lines become one quote block.
  Visually, block chrome (handles, gaps) is hidden until you interact with a block, so the note
  reads as a single page.
- **Paste chooser.** A snackbar after a formatted paste offers: *Formatted* (default) · *Keep as
  one Markdown block* · *Plain text*. A large paste is one undo step and one history snapshot.
- **Markdown block:** one block holding raw Markdown. It renders beautifully (including tables,
  code and images). Tapping it opens its source for editing. One tap converts it to native blocks,
  and the assistant can read and rewrite it.

**New block types:** `CODE` (language, syntax colours, copy button, wrap toggle), `TABLE` (render,
tap a cell to edit, add/remove rows and columns; pasting TSV from Sheets makes a table), `CALLOUT`
(info/tip/warning/decision), `TOGGLE` (collapsible, holding child blocks), `EMBED` (YouTube,
bookmark card with OpenGraph title/thumbnail), `REMOTE_IMAGE` (URL, alt, caption, optional "save
offline"), `MARKDOWN`, `AI_IMAGE` (prompt, style and model stored; regenerate, variations, save),
`MERMAID` (diagram rendered from AI output; optional, O4), `FILE`/`PDF` (inline preview, AI can
read it), and `VOICE_NOTE` (a short recording inside the note, with its transcript).

**Editing:**
- **Select mode (D5):** multi-block selection, then Copy (Markdown + HTML + plain), Cut, Delete,
  Duplicate, Move, Turn into…, and Ask AI.
- **Copy out:** "Copy note as…" Markdown / rich text / WhatsApp formatting (`*bold*`, `_it_`) /
  plain text.
- A floating toolbar on selected text: B I U S, highlight (with colours), code, link, AI ✨,
  comment (later).
- A **slash menu** (`/`) for every block type, plus AI and templates.
- **Collapsible headings** (the fold state is remembered per note), an **outline / table of
  contents** sheet, and jump to heading.
- **Find and replace** within a note.
- **Reading mode:** a note opens read-only when it's long or old, which prevents accidental edits
  on a phone. Tap Edit to change it.
- Hardware keyboard shortcuts (Ctrl+B/I/U/K/Z/Shift+Z, Ctrl+Shift+7/8/9 for lists, Tab and
  Shift+Tab to indent) for tablets, ChromeOS and DeX.
- Drag handles to reorder, and reorder several blocks at once from Select mode.
- Links to a specific block or heading.
- Word count and reading time in the note info sheet.
- Very large notes: blocks render lazily, so a 20k-word note opens quickly.

**Done when:** the golden paste corpus (real ChatGPT, Claude and Gemini outputs; Docs/Gmail HTML;
Sheets TSV) renders with no manual fixes; Markdown round trips are stable; a 5k-word paste is one
undo step and scrolls at 60 fps.

### W2. Notes AI engine: a real chat model layer

**Why:** the existing `LanguageModel` is single-turn text in, text out. An assistant needs
conversations, tools and streaming.

- A new `ChatModel` interface alongside `LanguageModel`: messages (system/user/model/tool),
  function declarations, streaming (`streamGenerateContent`), structured output (response
  schema), thinking level, stop/cancel, token usage in the result, and context caching for long
  notes.
- `GeminiChatModel` implements it. A new route, `AiRoute.NOTES_ASSISTANT`, lives in `AiModelRouter`
  (the one place model ids live), with **Fast** and **Best** tiers (question C4).
- The on-device Qwen stays as an offline fallback for small inline actions only (question I16).
- Errors are clear about what happened and what to do: quota (429), no key, offline, safety block.
  Never a silent failure.
- A **usage meter**: tokens and estimated cost by feature, because users bring their own key.

### W3. The notes assistant (D7)

**Surfaces**, all sharing one engine:
1. **Inline AI** on a selection or block: rewrite, shorten, expand, make professional, fix grammar,
   simplify, change tone, translate, turn into bullets/table/checklist, explain, continue writing,
   and a custom prompt. It shows a preview → Replace / Insert below / Try again.
2. **Assistant sidebar** (a side panel on tablets, a full-height sheet on phones), scoped to *this
   note / selection / notebook / everything*. Conversations are saved per note, reusing and
   generalising `chat_messages`.
3. **Ghost-text continue** (optional, O1).
4. Voice: talk to the assistant through the existing Gemini Live stack.

**Tools the assistant can call** (function calling, over block ids it is given):
`read_outline`, `read_blocks`, `search_notes`, `open_note`, `insert_blocks(after, markdown)`,
`replace_blocks(ids, markdown)`, `delete_blocks(ids)`, `move_blocks`, `set_block_type`,
`set_checked`, `create_note`, `add_tags`, `link_notes`, `generate_image`, `make_table`,
`export_pdf`, `read_transcript(meetingId, range)`, `get_scripture(ref)`, `create_task`,
`set_reminder`, `read_url` (fetches a pasted link's content), `web_search` (Gemini grounding;
question I9).

**Change sets:** every edit tool builds a *change set*, shown **inline in the note** as a
diff (additions tinted, deletions struck through), with *Apply all* / accept or reject each
change / *Discard*. Deletes and replaces always need approval. Inserts can auto-apply behind a
setting (question C2). Applying is one undo step plus a version snapshot.

**Skills** are named, versioned recipes (system prompt + tool policy + output checks):
- **Reconcile sources** (the flagship). Input: two or more pasted sources, for example a ChatGPT
  plan and a Claude plan. Pipeline:
  1. *Extract.* Each source becomes atomic items (claims, steps, requirements, risks, numbers,
     names), each with a source id.
  2. *Align.* Duplicates merge, complementary items combine, and **conflicts are flagged**, never
     silently resolved.
  3. *Compose.* One cohesive document in the requested shape (plan / spec / brief).
  4. *Verify coverage.* A second pass checks that every extracted item is present, deliberately
     merged, or listed under **"Dropped as redundant"**. Numbers, names and dates must match the
     sources exactly.
  5. The output ends with a **coverage report**: "A: 42 points, B: 37 points → 61 kept, 14 merged
     duplicates, 3 conflicts (see Decisions needed), 0 lost". The original sources move into a
     collapsed "Sources" toggle (or only into version history, if the user chooses).
- Others: **Turn into plan**, **Meeting minutes**, **Executive brief**, **Email / WhatsApp
  update**, **Tidy formatting** (fixes structure only, never wording), **Extract actions** (→
  Tasks, W11), **Decision log**, **Study guide**, **SOAP study**, **Sermon outline**, **Interview
  highlights**, **Compare options (table)**, **Proofread**.
- **User-defined skills:** save any prompt as a skill with a name and icon, pin it to the toolbar,
  and share it as a file (question I10).

**Watertight prompts (D8):**
- Prompts live as versioned assets (`assets/prompts/*.md`) with a contract section. Tests check no
  clause can disappear, following the pattern already used for `DevotionalContract.CONTRACT`.
- **An eval harness:** a golden set per skill (inputs + checks: coverage, preserved
  numbers/names, format validity, no invented facts). JVM tests use recorded responses; a
  `./gradlew promptEval` task hits the live API with a key so a prompt change comes with before
  and after scores.
- Output is validated in code (schemas, id resolution, number/name preservation) and retried with
  the validator's complaint when it fails.

**Done when:** the ChatGPT + Claude reconcile case produces a plan whose coverage report
shows 0 lost items on the golden set; every edit tool goes through change sets; skills pass their
evals.

### W4. The notes library at scale (D9)

- **Paging 3** with Room `PagingSource` (the `room-paging` artifact).
- A **list projection** (`NoteSummary`: id, title, first ~200 characters, updatedAt, pinned,
  notebookId, workflow, tags, thumbnail attachment id, word count), never the full `plainText`.
- Indexes on `(archivedAt, isDraft, pinned, updatedAt)`, `notebookId` and `workflow`. Filters run
  in SQL, not in memory.
- **FTS5** (or Room `@Fts4`) over title + text + tags + transcript text, with ranked results,
  snippets and highlighted matches. Debounced as you type.
- Autosave no longer causes list churn: the list observes the projection, and `updatedAt` changes
  move one row, not the whole list.
- LazyColumn with stable keys and `contentType`, date section headers (Pinned, Today, Yesterday,
  This week, month), skeleton placeholders, restored scroll position, a fast scroller with a date
  index, and thumbnails sized with Coil.
- **Views:** list / compact list / cards (with images). Sort, and **saved views** ("Client X
  open", "Sermons 2026").
- Bulk actions: move, tag, archive, delete, export, merge.
- **Done when:** a seeded database of 10,000 notes and 1,000 recordings opens the library in under
  300 ms and searches in under 100 ms (measured in a JVM/Robolectric benchmark), and scrolling
  holds 60 fps on a mid-range phone.

### W5. Theme system, dark by default (D10)

- **Semantic tokens** (a `MMColors` CompositionLocal, mapped into `MaterialTheme`): background,
  surface, raised surface, sunken surface, text primary/secondary/tertiary/disabled, hairline,
  divider, accent, accent-muted, on-accent, success, warning, danger, selection, highlight
  colours (5, tuned per theme), speaker palette (6, tuned per theme), faith warm set, recording
  red. Remove the "CleanMyMac" names, gradients and light-only `Ink*` constants.
- **Migrate every screen.** Remove all ~400 hardcoded references. A **unit test fails the build**
  if `Color(0x` appears outside `ui/theme`.
- **Why the current look feels off:** four saturated accents (indigo, purple, magenta, cyan) plus
  gradients compete; the greys lean blue; and white cards on a blue-grey canvas read as "SaaS
  dashboard". High-end minimalism means **one accent**, neutral greys, hierarchy carried by type
  and spacing, hairlines instead of shadows, fewer containers, and generous margins.
- **Theme presets:** *Graphite* (default: neutral dark with a slight warm tint, not navy), *Ink*
  (pure black, for OLED), *Paper* (warm light), *Mist* (cool light), *Sanctuary* (warm sepia for
  reading and Faith), *Classic* (today's indigo, for those who like it). Light / Dark / System and
  optional Material You dynamic colour.
- **Accent:** 8 muted choices (graphite, blue, teal, green, amber, clay, rose, violet).
- **Type:** UI font (Inter / Outfit), reading font for notes and Scripture (Lora / Playfair /
  Inter), text size, line height, density (comfortable/compact).
- Every Roborazzi screenshot runs in Graphite, Paper and Sanctuary.

### W6. Home: two layouts, customisable

- **Today** (the current hub): same layout, re-skinned onto the tokens with the gradients removed.
- **Focus** (new, minimal and text-first): a search/command bar at the top ("Search or ask…",
  which also runs commands and skills), a quick capture row (note, voice, record, photo, paste),
  *Up next* in one line, then **Pinned**, **Continue** (recently edited), **Tasks due**, and
  **Today's devotional** in one line. No cards, just type and hairlines.
- **Customise:** show, hide and reorder sections, per layout. Available sections: Up next, Quick
  capture, Pinned, Recent notes, Tasks, Recordings, Devotional, Verse, Prayer list, Stories,
  Reading plan, On this day, Weekly review. The layout can be switched anytime (Settings → Home,
  or long-press the Home tab).
- Per-space homes (Work/Faith spaces exist) are optional (O3).

### W7. Devotionals 2.0: never repetitive (D11)

**Archive:** "Past devotionals", a calendar plus list with search (passage, title, words), filters
(format, series, favourites) and ★ favourites. Reread and listen. Opening an old devotional never
triggers generation. "A year ago today" appears when one exists.

**The variety engine:**
- **Formats library.** Each format has its own schema, prompt and validator, and the reader UI
  renders whichever sections the format has:
  Reflection (today's) · **Lectio Divina** (read, reflect, respond, rest, with a silence timer) ·
  **SOAP** · **P.R.A.Y.** · **Daily Examen** (evening) · **Daily Office** (morning/evening:
  opening sentence, psalm, reading, collect, prayers) · **Catechism** (Heidelberg / Westminster
  Q&A, both public domain) · **Creed** meditation · **Hymn story** (public-domain hymns) · **Bible
  character** · **Word study** · **Psalm to pray** · **Breath prayer** · **Church history**
  (a figure from the past, tradition-aware) · **Quiet** (verse + silence only) · **Family** (for
  children, with discussion questions) · **Couples**.
- **Rotation:** the user enables formats and chooses daily / by weekday ("Sunday = hymn") /
  random, with a rule that the same format never runs twice in a row.
- **Series:** multi-day arcs ("7 days in Philippians", "Fruit of the Spirit", Advent, Lent). Each
  day's prompt gets a running summary of the series so far, so it continues rather than repeats.
- **Memory:** the prompt includes the last 14 devotionals' passages, titles, opening lines and
  main points, and must choose a different angle. After generation, a **novelty check** (passage
  repeat, title similarity, opening similarity, reused phrases) regenerates once if it fails.
- **Voice variety:** tone and length rotate within the user's chosen set. Openings vary (a story,
  a question, historical context, a word from the original language, a line of a hymn).
- **Richer feedback:** "too long / too short / generic / repeated / loved this format", per
  section. Feeds `moreOf`/`lessOf` and format weights.
- **Morning ↔ evening continuity:** the evening Examen looks back at the morning's passage and your
  response.

**Custom structures:** a **format builder**. Pick ordered sections from a palette (Scripture,
reflection, application, prayer, question, journal prompt, hymn, psalm, creed, confession,
silence timer, memory verse, intercession list from the Prayer list, blessing) and set their
length. Save it as "My format", which joins the rotation.

**Tradition presets** (the starting sets; each is editable):
- Catholic: readings reference, reflection, saint, prayer.
- Anglican: Daily Office.
- Orthodox: saint and feast, prayer.
- Reformed: Scripture, catechism, prayer.
- Evangelical: SOAP quiet time.
- Pentecostal/Charismatic: Scripture, reflection, prayer, faith declaration drawn from Scripture,
  still under the "no speaking for God" rule.
- Contemplative: Lectio, Examen, breath prayer.
- Family.

**Interactive:** journal prompts that save to a linked note, a silence timer with ambient sound,
**memory verse practice** (spaced repetition), a "carry this" widget and lockscreen line, and a
**weekly review** ("what you've been reflecting on", built from your responses).

**Kept:** the devotional contract (verses come from the Bible text in the app, not the model; no
"thus says the Lord"; no medical/financial/crisis advice; the care check). **Pre-generating** the
next 2–3 days while online keeps it working offline.

**Done when:** a 30-day simulated run (JVM, recorded responses) has no repeated passage within 14
days, never repeats a format twice in a row, passes the contract checks for every format, and
opens the archive from any date.

### W8. Bible study 2.0, tied to notes

- A **study workspace:** the reader and a note side by side (tablet/foldable) or the reader with a
  pull-up note (phone). Tapping a verse inserts a scripture block; long-pressing a verse turns a
  highlight into a note.
- **"Notes on this passage":** every verse shows a marker when notes cite it (from
  `scripture_refs`). The reader's side panel lists those notes, sermons (with timestamps) and
  devotionals.
- Highlights move to or link with Room so they are exported, backed up and searchable, and can
  carry an attached note.
- **Study methods as templates:** SOAP, Inductive (Observe / Interpret / Apply), Book overview,
  Character, Word study, Topical, **Sermon prep** (which overlaps with professional use for
  pastors), Small group guide.
- **Tools:** parallel translations, cross-references (the table exists), commentary (exists), and
  an **original-language layer** (Strong's / morphology from open data such as OpenScriptures
  Hebrew Bible and SBLGNT; licensing is question I11). Maps and timeline come later.
- **Study assistant:** the W3 assistant with Bible tools (`get_verses`, `crossrefs`,
  `commentary`, `compare_translations`, `search_my_notes`), under the faith contract (verse text
  always comes from the library).
- Reading plans: each plan day links to a note, and progress carries no guilt.
- Verse collections (these exist) become shareable study sets. Memory verses are shared with W7.
- Sermon recordings: detected references become links into the reader, and "study this sermon"
  opens the workspace.

### W9. Knowledge: links, backlinks, graph, semantic search, Ask everything

- **`[[wikilinks]]`** typed inline with autocomplete, creating the note if it doesn't exist.
  Reuses `note_links` and the `NOTE_LINK` block.
- A **backlinks panel** on each note, plus **unlinked mentions** (the title appears but isn't
  linked; one tap links it).
- **Graph view:** a local graph (this note ± 2 hops) and a global graph with filters (notebook,
  tag, space), drawn on Canvas with a force layout. Has to stay smooth with 5k+ nodes (clustering,
  level of detail).
- **Semantic search:** real embeddings per block/paragraph chunk (notes, transcripts,
  devotionals), stored with the chunk, **hybrid-ranked** with FTS5. The embedding source is
  question C6.
- **Ask everything:** RAG across notes, recordings and Scripture, with citations to blocks and
  timestamps. Replaces the hash embeddings and closes ROADMAP P1 #9/#10.
- **Related notes** suggested at the bottom of each note, and AI-suggested links.

### W10. Tasks, people and properties (the professional core)

- **Tasks:** every checklist item in every note is a task. A **Tasks view** (Today, Upcoming,
  by project/person, done), due dates from natural language ("tomorrow 3pm", "Fri"), reminders
  via the existing notify stack, and "Extract actions" feeding it. Tasks due also appear on Home.
- **People:** `@mentions` create or link a person. A **person page** shows every meeting, note and
  task involving them, plus an AI brief ("what's open with Sinovia"). Meeting participants from
  the calendar link to people.
- **Properties:** typed note fields (status, project, client, due, owner, URL), shown in the
  header and filterable in saved views (W4). This generalises `metadata`.
- **Templates:** a gallery (Meeting, 1:1, Interview, Project brief, Decision record/ADR,
  Engineering design, Client call, Lecture, Research, Weekly review, Sermon, SOAP) plus
  **user-made templates** with variables (`{{date}}`, `{{event.title}}`, `{{attendees}}`).
- **Daily note** (optional): one note per day, linked from Today, collecting quick captures.

### W11. Capture everywhere

- An **Android share target** (text, URLs, images, PDFs, audio). Shared content lands in a new
  note or is appended to a chosen note. Shared Markdown is parsed (W1).
- Home screen **quick capture widget**, a **Quick Settings tile**, **app shortcuts** (long-press the
  icon: New note, Record, Voice note, Paste as note), and a persistent "new note" notification
  (optional).
- **Scan document:** camera → Gemini vision → Markdown blocks plus the original image.
- **Web bookmark:** paste a URL, get a card with title, description and image. The assistant can
  `read_url` it to summarise.

### W12. Transcription Engine 2.0 and transcript editing

From the user's own plan, adopted in full with its order kept:

1. **Performance:** `PreparedAudio` (decode once to 16 kHz mono, cached); chunks sliced from it;
   **parallel Gemini chunks** with bounded, adaptive concurrency; per-chunk persistence; stream
   finished chunks into the canonical transcript; **move Smart off the critical path** (it becomes
   background polish); verbatim Gemini as the default cloud transcript; measure
   time-to-first-transcript.
2. **Interactive engine switching:** `TranscriptionSession`, `TranscriptionChunk` and checkpoints;
   Local ↔ Gemini mid-run with overlap reconciliation; mixed-source transcripts recorded via
   `TranscriptSource`.
3. **Scene intelligence:** `AcousticActivity` (speech / music / silence / crowd / mixed) plus
   `SemanticActivity` (sermon / prayer / song / scripture / announcement / discussion / response /
   Q&A / presentation). A cheap local boundary detector, then Gemini classifies only the
   candidate windows. Song policy: collapse lyrics; identify the title, never invent it, and never
   reproduce full lyrics. `TranscriptScene` lives on `CanonicalTranscript`, not on words.
4. **Sermon intelligence:** a timeline of scriptures, songs, prayers and points, grounded in
   timestamps.
5. **Scene-aware transcript UI:** Sermon / Scripture / Prayer / Worship sections, each tappable to
   seek.
6. **A real corpus and benchmark:** `testdata/sermons/` (the 10 categories from the user's plan)
   with reference transcripts, scenes and scriptures, plus a professional meeting set. The
   **MeetingMind Quality Scorecard** (recognition, timing, speakers, structure, audio
   understanding, product latency, battery, RAM, network, cost). Parakeet vs Gemini vs Gemini +
   Smart.

Also: **vertical-aware custom vocabulary** (the faith lexicon, your company and people names,
learned from notes and people pages).

**Transcript editing:**
- **Word-level karaoke** (`wordsJson` exists): the active word highlights during playback, and
  tapping any word seeks to it.
- Low-confidence words get a dotted underline. Tapping one loops that clip and allows a one-tap
  fix.
- Rename a speaker everywhere; merge and split turns (split exists); speaker identities remembered
  across recordings (optional voice profiles; privacy question I13).
- A **Verbatim / Clean** toggle (`cleanedText` exists), with per-sentence diff on tap.
- **Citation chips** on every summary item and note block drawn from audio (`[14:22]`): tapping
  opens the quote and plays it.
- **Send to note:** transcript text (a range, speaker turns or everything) becomes note blocks
  you can rewrite freely, with links back to the audio.
- Find and replace; export SRT/VTT; non-destructive "hide from export" ranges.

### W13. Professional workflows, PDF and images

- **The meeting loop:** a calendar event → a note from the template with attendees linked to
  people → record → transcript → the **Minutes** skill → actions go to Tasks with owners → a
  **follow-up draft** (share to Gmail/WhatsApp) → next time, the note pulls in the open actions
  from last time. An **AI meeting prep** brief appears in Up next ("last time with these people…").
- **Interview workflow:** a question bank, and quotes collected with timestamps.
- **Project hubs:** a notebook becomes a project with properties, people, tasks, a decisions log
  and a timeline.
- **PDF:** (a) **typeset export** of any note: themes, cover page, table of contents, headers and
  footers, page numbers, code/table/image support, remote images embedded at export time, and
  optional branding (logo, accent). (b) **AI-generated documents:** "Make a report / proposal /
  brief from this", which writes a new note first (reviewable) and then exports it. Also
  Android print.
- **AI image block:** reuses `ImageBackgrounds` (Gemini image model via the router). Prompt,
  style and aspect ratio; regenerate and variations; saved locally; labelled "AI image"; can be
  inserted from the assistant.
- **Import:** Markdown files and folders (Obsidian vault), Google Keep Takeout, Notion export,
  Evernote ENEX, plain text, and PDF text (question I12 sets the order).

### W14. Sync and collaboration

Staged, because each stage stands alone:
1. **My devices:** encrypted sync of all notes, attachments and metadata for one account, which
   is also a cloud backup.
2. **Share:** a read-only link or PDF; send a note to someone as a copy.
3. **Shared notebooks:** invite by email; per-notebook roles (viewer/commenter/editor);
   **comments** on blocks; activity feed.
4. **Real-time co-editing:** presence, cursors, conflict-free merging (a block-level CRDT, with a
   text CRDT inside each block).

The backend, encryption model and who pays for it (question C5) must be settled before stage 1.
`FirestoreSyncManager` today only handles meeting metadata.

### W15. Quality, accessibility and the whole-app polish

- An accessibility pass (TalkBack labels, touch targets, font scaling to 200 %, contrast in every
  theme).
- Motion: consistent, quick transitions; respects "remove animations".
- Empty states, loading states and error states with a next step on every screen.
- Settings reorganised (Appearance, Home, Notes, AI & keys, Transcription, Faith, Data & backup,
  About), with search.
- Adaptive layouts for tablets and foldables (list + detail, and the assistant as a side panel).
- App lock (biometric), with per-note locking for private notes (question I13).
- Crash and ANR reporting (question I15).

---

## 5. Ideas not in the request, ranked by impact

1. **Version history + trash + full backup** (W0). Everything else depends on trust.
2. **The reconcile coverage report** (W3). Proof that nothing was lost is the differentiator, not
   the merge itself.
3. **Change sets shown inline in the note** (W3). This makes a powerful assistant safe to use every
   day.
4. **The share target and quick capture** (W11). Most Markdown arrives from other apps.
5. **Tasks across notes + people pages** (W10). This is what makes a notes app a professional's
   operating system.
6. **FTS search with snippets + paging** (W4). Speed is felt on every visit.
7. **Reading mode** (W1). Stops accidental edits on a phone.
8. **Copy as rich text / WhatsApp formatting** (W1). How notes leave the app.
9. **Devotional memory + novelty check + series** (W7). The actual cure for repetition.
10. **"Notes on this passage"** (W8). The bridge between the Bible and notes.
11. **AI meeting prep in Up next** (W13). Uses calendar data the app already has.
12. **Prompt eval harness + usage meter** (W3, W2). Prompt quality you can measure, and costs
    you can see.
13. **Weekly review** (W7 faith, W10 work). A digest of the week's notes, open tasks and reflections.
14. **Hardware keyboard shortcuts + tablet layouts** (W1, W15). Professionals use tablets and
    ChromeOS.
15. **Scan document → Markdown** (W11).
16. **Mermaid diagrams rendered in notes** (W1, optional). AI answers often contain them.
17. **Memory verses with spaced repetition** (W7/W8).
18. **"A year ago today" for notes and devotionals** (the `On this day` query exists).

---

## 6. Order of work (milestones)

Each milestone ships as its own build with a changelog in `RELEASE_NOTES.md`.

| M | Contents | Why here |
|---|---|---|
| **M0** | W0 (safety, backup, trash, history, indexed flags, CI), W5 tokens + dark default | Everything after this changes the schema and every screen |
| **M1** | W1 editor + paste + new blocks + Select/copy + reading mode; W4 library at scale | The daily-use core |
| **M2** | W2 chat layer; W3 inline AI, assistant, change sets, skills (incl. reconcile), eval harness | Needs the M1 block types and version history |
| **M3** | W5 presets/customisation; W6 Focus home + section customisation | Visible polish on the new tokens |
| **M4** | W7 devotionals 2.0 | Independent of M1–M3 apart from tokens |
| **M5** | W10 tasks, people, properties, templates; W11 capture; W13 professional workflows, PDF, images, import | The professional vertical |
| **M6** | W9 links, graph, semantic search, Ask everything; W8 Bible study 2.0 | Builds on the M5 entities and the M2 assistant |
| **M7** | W12 Transcription Engine 2.0, phases 1–6, and transcript editing | Mostly independent; phase 1 could be pulled earlier (question C8) |
| **M8** | W14 sync → share → shared notebooks → co-editing; W15 final pass | Needs the backend decision |

---

## 7. Risks

- **Schema churn during M0–M6** on a primary data store. Mitigated by W0 first, migration tests
  on every PR, and automatic backup before migrating.
- **Dark mode half-done** looks worse than none. Migrate screen by screen, and the build fails on
  stray colours.
- **An assistant that destroys content.** Change sets, approval for deletes, one-step undo and
  version history. Coverage verification for reconcile.
- **Prompt drift and model updates:** Gemini versions change behaviour. The eval harness plus
  model ids pinned in the router, and upgrades only with eval scores.
- **Cost and quota** on the user's own key: the usage meter, Fast/Best tiers, context caching,
  and not re-sending whole notes on every turn (outline + fetch).
- **Performance of large notes** with many rich blocks (tables, embeds, WebViews): lazy rendering
  and only one live WebView at a time.
- **YouTube and remote images** reveal the user's IP address and viewing to third parties. Use
  `youtube-nocookie.com`; this is accepted under D4.
- **Licensing:** lectionaries (RCL), modern prayer books (BCP 2019) and Catholic readings are
  copyrighted, so ship references only and take text from the app's own Bibles. Hymn lyrics must
  be public domain only. Check Strong's/morphology data licences.
- **Collaboration** is a backend product (hosting, auth, cost, abuse, privacy law). It is scoped
  last for that reason.
- **Package rename** loses data unless it ships together with backup/restore.
- **Graph view at scale:** needs level of detail and clustering.
- **Scene detection** needs the corpus. Without real audio every quality claim is unverified, as
  `TRANSCRIPTION_OVERHAUL.md` already admits.

## 8. Verification

- **Golden corpora:** paste (Markdown/HTML/TSV from real AI and office apps), skills (reconcile
  and the others), devotional simulations (30/90 days), the sermon and meeting audio corpus.
- **Tests:** migration (every version), backup round trip, parser round trips, change-set apply
  and undo, paging and FTS performance on a 10k-note seed, novelty and contract checks, Roborazzi
  screenshots of every main screen in three themes, and a no-hardcoded-colour check.
- **Prompt evals:** `promptEval` scores recorded per prompt version.
- **Device checks** (the user's phone): the full workday acceptance script from the user's own
  plan (paste a large doc → AI rewrite → undo/redo → transcript seek → YouTube inline → checklist
  document), plus the ChatGPT + Claude reconcile case.

## 9. Open questions

Numbering continues from the first GRILL round. ★ marks the recommendation.

**Critical**
- **C1 (paste default).** Is *Formatted native blocks* the default, with the "Keep as one Markdown
  block" chooser after pasting? ★ Yes. Or should Markdown pastes default to a Markdown block?
- **C2 (assistant approval).** ★ Deletes and replacements always wait for Apply. Inserts
  auto-apply (undo is one tap), and a setting can make inserts wait too. Or must everything wait
  for Apply?
- **C3 (no gating, confirm the edges).** Does "no gating" also cover **Faith prayer/journal notes
  and notes marked Private**? ★ Yes for the assistant. "Private" then only means *excluded from
  share/export by default* and *locked when app lock is on*. Is the devotional
  `sharePrivateWithCloud` personalisation switch also removed? ★ Keep it: it controls what the app
  sends on its own initiative, not what the user asks for.
- **C4 (model routes).** The router today has `gemini-3.6-flash` for intelligence. ★ Fast = 3.6
  Flash, Best = 3.8 Flash with thinking, chosen per skill and overridable. Please confirm the exact
  model ids you have access to.
- **C5 (sync and collaboration backend).** Firestore (already integrated) or Supabase? End-to-end
  encrypted or server-readable (E2E blocks server-side AI and search)? Who pays for hosting (a
  subscription tier?)? This decides W14 and part of the business model.
- **C6 (embeddings for semantic search).** ★ Gemini embeddings in the cloud when a key is set
  (best quality, consistent with D4), with an on-device embedding model as the offline fallback.
  Alternatively, on-device only.
- **C7 (package / applicationId rename).** Rename before the next build, alongside W0's
  backup/restore, so the switch costs nothing? ★ Yes, in M0.
- **C8 (order).** Is the §6 order right? In particular, should **Transcription phase 1 (speed)**
  move up to M1 because it's self-contained and you feel it daily?

**Important**
- **I1** Approve the **Focus** home concept and its sections.
- **I2** Theme presets and the default accent. Keep *Classic* indigo as an option? ★ Yes.
- **I3** Which traditions and formats are required at launch? ★ All presets in W7. Which ones
  matter most to you?
- **I4** The devotional theology contract stays as it is? ★ Yes.
- **I5** Tasks: are assignees (people) and reminders needed at launch, or only due dates?
- **I6** A People entity with person pages: yes?
- **I7** PDF branding (logo, colours, company name) as a setting?
- **I8** Remote images hotlinked by default, with "save offline" per image? ★ Yes.
- **I9** Should the assistant have **web search** (Gemini grounding) and **read_url**? ★ Yes, both,
  with sources cited.
- **I10** User-defined skills: private, or shareable as files?
- **I11** Original-language data (Strong's/morphology): OK to add about 30–60 MB of open data as
  an optional download?
- **I12** Import priority: Obsidian / Keep / Notion / Evernote?
- **I13** App lock and per-note lock. Encrypt the database at rest (SQLCipher adds size and some
  speed cost)? Speaker voice profiles (biometric-like data)?
- **I14** How important are tablets/foldables for you?
- **I15** Crash reporting (Firebase Crashlytics) acceptable?
- **I16** Keep the on-device Qwen as an offline fallback for inline AI, or remove it from Notes?

**Optional**
- **O1** Ghost-text "continue writing" suggestions as you type?
- **O2** Mermaid diagrams and LaTeX math blocks?
- **O3** A different home per space (Work vs Faith)?
- **O4** Handwriting/sketch block?
- **O5** Nested notebooks (folders within folders)?
- **O6** Wear OS quick capture?

## 10. Non-goals

- No web or desktop client in v3 (though sync in W14 would make one possible later).
- No meeting bots (Zoom/Meet/Teams) and no third-party task-manager integrations beyond
  share/export.
- No devotional content marketplace or social feed.
- No reproducing copyrighted lyrics, lectionary text or modern prayer-book text.
- Transcripts are never rewritten in place: users rewrite a copy in a note.
