# Work vertical: premium UX redesign, design specification

Status: **Phase A, revision 2. Design for founder approval. No Android code has changed.**
Branch: `release/1.0-paid-launch`. Dates: 2026-11-15 (r1), 2026-11-16 (r2).
Interactive artifact: `design/Work Vertical Redesign.dc.html`. Open it in any browser; it needs no build step. It has
81 live Android frames in 12 turns, plus a walkthrough phone for eight journeys, light and dark themes, and a Pulse time-of-day switch.
Frame ids (`#1b`, `#4a` …) are cited throughout this document. Append one to the URL to jump to it.

Read with: `docs/PLAN_PROFESSIONAL.md` (Direction v2 wins over its older sections), `docs/mvp/WORK_UX_AUDIT.md`
(D1–D7, approved 2026-10-22), `docs/mvp/WORK_UX.md`, `docs/mvp/DESIGN_SYSTEM.md`, `agents/PRODUCT.md`.
Where this spec and the artifact disagree, **the artifact wins** (same rule as `design/README.md`).

**Contents:** 1 Summary · 2 Current audit · 3 Philosophy · 4 IA & navigation · 5 The three flagships ·
6 Core screens · 7 Journeys · 8 Tokens & components · 9 Interaction states · 10 Privacy & trust · 11 Accessibility ·
12 Mapping to the code · 13 Retained / improved / consolidated / later · 14 Implementation plan · 15 UX review ·
16 Founder decisions

**Revision 2 (§R, directly below) records the founder's decisions of 2026-11-16 and supersedes §1–§16 wherever they conflict.**

---

## R. Revision 2: founder decisions applied (2026-11-16)

### R1. Decisions

| # | Founder decision | Effect on the design |
|---|---|---|
| F-1 | Replace Search with Work, and add Learn when one or both are selected; keep the same bar design and update its contents for the current app | Adaptive bar (R2) · artifact `#0a`, `#0f`–`#0h` |
| F-2 | Keep Notes as a fifth Work segment, the same Notes component scoped to Work | Segments: **Pulse · Tasks · Notes · Projects · Decisions** · `#2a` |
| F-3 | Autopilot tasks go to review by default | Tasks land in the Wrap-up as unreviewed; "Create directly" is opt-in · `#10b` |
| F-4 | Full Brief controls | Kind, audience, tone, length, sections, language, footer, Copy (R5) · `#5c`, `#5f`, `#5g` |
| F-5 | Plan and implement full on-device PDF text extraction | Text layer + on-device text recognition, page citations, search, viewer (R4) · `#8c`–`#8c4` |
| F-6 | Keep the Work home; the Today and Work homes remain the two nicest screens | The Work home stays and is elevated in its own visual language (R2, R6) · `#0a`–`#0c` |
| new | Tasks must be first-class: a normal, premium, multi-page task manager | App-wide Tasks (R3) · turn T |
| new | v1 felt underwhelming | Visual language rebuilt on the homes' own hero, Briefing and bento language (R6) |

**Superseded from r1:** retiring the Notes segment (§4.1, §13), the short-card-only Work home and "retire `PulseCard`" (§4.2, §8.2; D7 is re-read as *restyle and share it*, not delete), "Settings stays in the bar", and the PDF "later" labels (§9, §13).

### R2. Bottom bar and the two homes

**The bar keeps the existing pill component** (`core/ui/BottomNavigation.kt:120-242`): floating pill, AccentWash active chip with label, icon-only inactive items, and an Accent **New** button. Only its contents change.

| Spaces selected (`identity_spaces`) | Bar |
|---|---|
| Work **and** Learning | Today · Notes · **New** · Work · Learn |
| Work only | Today · Notes · **New** · Work · Tasks |
| Learning only | Today · Notes · **New** · Learn · Tasks |
| Neither | Today · Notes · **New** · Tasks · Search |

- **Settings moves to the avatar menu** on every home (`#0e`). It is opened a few times a month and was in the bar only because it was placed there at the start of development.
- **Search** stays in every home's dock (it already is, `HomeHeroHeader.kt:291`), in Notes, and as the Work-scoped search in Work.
- **Circles, Inbox, Faith** keep their current doors: the home bento/quick access, the dock bell, and the home mode switch.
- **Implementation:** replace `TabSlot` (`WorkSettings.kt:83`) with a derived `BarConfig(spaces)`. Add `BottomNavDestination.LEARN` and `TASKS`; `LEARN` hosts `LearnHomeHost` (today only reachable from Home, `MainActivity.kt:660`). Keep a manual override in Settings › Appearance for people who want Search back.
- **Open question R-1:** Faith-only users. Recommendation: Faith claims a slot under the same rule (the first two selected spaces, then Tasks).
- **Bug to fix on the way:** `AppIdentity.parseSpaces` defaults an empty set to all four spaces (`AppIdentity.kt:45-46`), but writes default to PERSONAL (`UserPreferences.kt:375`). The bar must use the same default as onboarding.

**Work home (HomeStyle.PROFESSIONAL), kept and elevated** (`#0a`–`#0c`). Order, top to bottom:

1. Header: avatar → menu, date, dock (search, Inbox bell with badge).
2. Mode switch: All · Work (existing `ModeHome.WORK`).
3. Sky hero (`TodayIntelligence` keyframes) with a time label, Outfit 27 sp greeting, and one human sentence.
4. Floating **Up next** tile: in the morning the meeting and Prepare; in the evening "To review · N findings".
5. Quick access: Record · Task · Inbox · Ask · Brief · Review.
6. **Your day**: the Briefing card (`Briefing.Top/Bottom` gradient, Lavender labels), kept from `PulseCard` and restyled. It holds the day ribbon, up to four attention rows each with one verb, Since yesterday as before → after sentences, and Prepare / Ask.
7. **Today's tasks**: the Tasks component, today only, swipeable.
8. Projects carousel (project colour bar, status pill from counts, open counts).
9. People strip with "owes / you owe" badges.

The capture row, glance tiles, the "Customize home" link and the Circles card from today's `ProfessionalHome` either move into quick access or stay under Customize home. None are lost.

**Work home vs Work › Pulse.** The home is *your day*: time, calendar, tasks and people, at a glance. **Pulse** is *follow-through*: all of Needs you, Since yesterday, waiting, and decisions to confirm, with full lists. Both render the same `BriefingCard` composable with different inputs, so there is one implementation and no divergence (this resolves W-02's actual risk).

**Today home** (`#0d`) keeps its sky and bento, and gains three tiles: **Work · your day** (Briefing tile with the ribbon), **Tasks** (progress ring, next task), **Learn** (today's plan).

### R3. Tasks: a first-class task manager

**Scope:** app-wide, covering work, personal and learning tasks. One `TasksComponent` is used three ways: the Tasks destination (bar slot or Home tile), Work › Tasks (scoped to work lists), and Today's tasks on the homes. The current `feature/tasks/TasksScreen.kt` is replaced; its People tab moves to Work › Projects › People.

**Pages:**

| Page | Content | Frame |
|---|---|---|
| Tasks home | Plan your day (ring) · smart views Today, Upcoming, Inbox, Waiting on, Priority 1, Completed · lists grouped Work / Personal / Learning · labels · New list | `#Ta` |
| Today | Progress ring and time estimate · past-due banner (Do today / Reschedule) · **Scheduled** interleaved with calendar meetings · Anytime today | `#Tb` `#Tm` |
| Upcoming | Week strip with dots · per-day tasks and meetings · Later | `#Tc` |
| List | Colour, space, progress · sections · filters · Select · **list ↔ board** | `#Td` `#Te` |
| Task page | Complete · title · list/section · **promise + source** · date · reminder · repeat · priority · labels · "Someone else is doing this" (→ Waiting on) · subtasks · notes · attachments (meeting, Inbox file) · history | `#Tf` |
| Quick add | Natural language: `tomorrow 3pm` `#Acme` `!1` `@email` become chips live | `#Tg` |
| Date sheet | Today / Tomorrow / Weekend / Next week / No date · month grid · time · reminder · repeat | `#Th` |
| Plan my day | One card at a time from past-due, due-soon and promises · Today / Not today / Pick a time | `#Ti` |
| Bulk edit | Select → Date · Priority · Move · Assign · Complete | `#Tj` |
| Inbox, Completed, labels | Unsorted quick adds (including Inbox → Task filings) · a log the weekly review reads · label views | `#Tk` `#Tn` |

**Interactions:** swipe right to complete and left to reschedule (with Undo) · drag to reorder within a section · long-press to select · a completion pop animation (150 ms) · repeating tasks roll forward on completion with a toast naming the next date · completing a promised task closes its commitment (existing write-through, `core/work/Items.kt:131`).

**What exists today** (`TaskEntity`, `PeopleTasks.kt:68-100`): title, notes, kind, dueAt, remindAt, repeat (NONE/DAILY/WEEKLY/MONTHLY), doneAt, personId, noteId, blockId, meetingId, startMs, deletedAt, waitingOn, ownerSpeakerId, sourceItemId, space. Reminders use AlarmManager with a cap of 40 (`TaskReminders.kt`). `DueDates.parse` handles dates but not times.

**Data changes (one additive migration, 30 → 31, following the `MIGRATION_N_M_SQL` pattern):**

| Change | Kind |
|---|---|
| `tasks`: `priority INT DEFAULT 4`, `parentId TEXT` (subtasks), `listId TEXT` (notebook), `sectionId TEXT`, `position REAL`, `hasTime INT DEFAULT 0`, `repeatRule TEXT` (RRULE; the enum stays for compatibility), `completedFromRepeat INT` | columns |
| `task_sections(id, listId, name, position)` | table |
| `task_labels(id, name, color)` and `task_label_links(taskId, labelId)` | tables |
| `tasks_fts` (FTS4 on title and notes), added to `SearchDao` | table |
| Lists are notebooks: existing projects (`kind = PROJECT`) plus a new `kind = TASK_LIST` with `space` PERSONAL, LEARNING or WORK | data |
| Backfill: `listId` from the note's notebook for tasks with `noteId`, else Inbox; `space` for tasks created in the Tasks screen (today null) | data |

**Fix first (found in this audit):** `Task.toEntity()` (`core/tasks/Tasks.kt:177`) rebuilds the entity positionally and drops `waitingOn`, `ownerSpeakerId`, `sourceItemId` and `space`. It is used by `TaskRepository.save` and `toggleDone`, so editing or ticking a Work task in the Tasks screen likely erases its Waiting-on flag and source link. This is a paid-launch data-integrity bug, independent of the redesign. **Recommend fixing it now, with a unit test**, even before Phase B.

**Natural-language parser:** extend `DueDates.parse` with times (`3pm`, `15:00`, `at noon`), `#list` (fuzzy match against list names), `!1`–`!4`, `@label`, and `every …` → `repeatRule`. Ambiguous tokens stay plain text, never guessed.

**Sync:** `TaskSyncProvider.pushTask` has no call site. Wire "Send to Todoist / Linear / GitHub" in the task menu. It uses the existing `CONNECTED_INTEGRATIONS` gate; no new gate.

### R4. On-device document reading (PDF and scans): implementation plan

**Goal:** every PDF, scan or image that enters the Work Inbox is read on the phone, summarised, filed with a page-cited suggestion, searchable, and opens at the cited passage.

**Today:** only `play-services-mlkit-document-scanner` is present; the scanner produces a PDF with no text (`InboxScreen.kt:189-208`). `InboxItemEntity.text` exists but is never filled for files. `InboxProposer` runs only on non-blank text, truncated to 6,000 chars (`Inbox.kt:214-249`).

**Libraries:**

| Need | Choice | Why |
|---|---|---|
| PDF text layer | **PdfBox-Android** (`com.tom-roush:pdfbox-android`, Apache-2.0) | Pure Java, offline, gives text with positions (for highlights). Shrinks well under R8; add keep rules for its font resources |
| Scanned pages and images | **ML Kit Text Recognition v2**, unbundled via Google Play services (`play-services-mlkit-text-recognition`) | On-device; the model is delivered by Play services, so no APK growth. Latin script first; add other scripts later if needed |
| Rasterising pages for recognition and the viewer | **`android.graphics.pdf.PdfRenderer`** (platform) | No dependency; render at most 1600 px on the long edge |

**Pipeline:**

1. `ShareIn` / scanner / Files import → `InboxItemEntity` (kind PDF or IMAGE) with `readStatus = PENDING`.
2. `DocumentReadWorker` (a `CoroutineWorker`, unique work per item, modelled on `NoteAiWorker`). Each page tries the PdfBox text layer first; if it yields fewer than about 40 characters, the page is rendered and recognised instead. It writes a `document_pages` row per page, updates `readPages` for the progress UI, and runs as a foreground notification for documents over 20 pages.
3. On completion: fill `InboxItemEntity.text` (head, for compatibility), set `readStatus = DONE`, then run `InboxProposer` with **page-aware chunk selection** instead of plain truncation. The selection is the first two pages, plus pages that mention known people, organisations or projects, plus pages with requirement and decision cues, all within the existing 6,000-char budget.
4. Grounding: proposals keep the ≥60% grounding rule; evidence carries the **page** and, when available, the text bounding box.

**Schema (in the same 30 → 31 migration):**
- `inbox_items`: `readStatus TEXT` (NONE/PENDING/READING/DONE/NO_TEXT/LOCKED/FAILED), `pageCount INT`, `readPages INT`.
- `document_pages(itemId, page, text, source TEXT_LAYER|OCR, confidence REAL, boxesJson TEXT)`, plus FTS4 `document_pages_fts`, added to `SearchDao`.
- `item_evidence`: `page INT`, `boxJson TEXT` (nullable), so items found in documents cite "p. 4".

**States in the UI:**
- Reading, with page progress: `#8c`.
- Read, with summary and page-cited suggestions: `#8c2`.
- Viewer opened at the passage: `#8c3`.
- Recognised scan: `#8c4`.
- Password-protected: "Can't read a locked PDF. Open it in another app, or file it as is."
- No text found: "No readable text. Filed by name."
- Too large: over 300 pages or 50 MB. The first 300 pages are read, and the screen says so.

**Privacy:** reading text is **always on-device**. Summarising and proposing follow `WorkPrivacy` and `ProcessingProfile` exactly as for transcripts. Confidential destinations use the local model, and the UI shows where it ran.

**Tests:**
- Unit tests for chunk selection and the NO_TEXT and LOCKED states.
- Fixture PDFs: text, scanned, mixed, rotated, encrypted, 300+ pages.
- Worker state transitions, including cancellation and re-entry after process death.
- Device checks: memory on a 2 GB phone, and the time for a 12-page text PDF (target under 5 s) and a 3-page scan (target under 6 s).

**Not in this plan:** web-page reading for shared links. They are still filed by name and say so.

### R5. Intelligence Brief: full controls

- **Controls:**
  - Kind: the existing `BriefKind` (MEETING, CLIENT, PROJECT, RELATIONSHIP, WEEKLY).
  - Audience: Client sponsor, Internal team, Executive, Just me.
  - Tone: Plain, Formal, Warm, Direct.
  - Length: Headline, One page, Detailed.
  - Sections: nine toggles.
  - Language.
  - Footer on or off.
  - Confidential material is never included.
- **What the controls change:** audience, tone and language change **only the two AI paragraphs** (the prompt gets a style block; the `FIDELITY_CONTRACT` and the citation rule are unchanged). Length and section toggles change **which deterministic sections print**, never their contents.
- **Caching:** results are cached per entity, kind and options hash, so switching back is instant.
- **Actions:** Copy (formatted and plain), Export (PDF, Word, Markdown, through `NoteExporter`) and Share. There is no send button.
- **Typography:** a document style, with Literata for the body and headings, an evidence appendix, and "Confidential" on the PDF where it applies.

### R6. Visual language (why r1 felt underwhelming, and what changed)

r1 used the design-system minimum: white cards on Paper. The two screens the founder rates highest use a richer language, so r2 adopts it.

- **Sky hero:** the `TodayIntelligence` keyframes, with sun, moon and stars, radius 28–36 dp, a floating tile, and Outfit greetings.
- **Briefing palette** (`Briefing.kt`) for "Your day" and Pulse heroes: Lavender overlines, white primary and glass secondary buttons. Retained, not retired.
- **Day ribbon:** 08–18 h, with meetings as accent blocks, tasks as priority-coloured dots and a red now-line. It is the signature element of Work.
- **Project colour identity:** a colour bar on cards, rows and boards, from the org mark or notebook colour.
- **Progress rings** for Today and Plan my day; **before → after** change sentences; Outfit for numbers and titles.
- **Document typography** (Literata) for the Brief and Autopilot results; the PDF viewer has a highlight.
- **Motion:** completion pop, sheet spring, swipe reveal, bento reveal and press scale (existing `Reveal`). There is still no idle animation.
- **Dark (Graphite)** is designed first-class, because it is the default theme.

### R7. Build order (replaces §14)

| Inc | Content |
|---|---|
| B-0 | Navigation and Tasks tests. **Fix `Task.toEntity()` data loss** and the `space`/`identity_spaces` defaults |
| B-1 | Shared components: SourceChip, SourceSheet, TrustMark, BriefingCard, DayRibbon, ProgressRing, PulseHeader, project colours |
| B-2 | Adaptive bar (`BarConfig`), avatar menu, Learn and Tasks destinations |
| B-3 | Work home and Today home tiles (Briefing card, ribbon, tasks, projects, people) |
| B-4 | Work hub: five segments, Pulse, Decisions, Work search, ⋯ reduced |
| B-5 | Tasks data migration 30 → 31 and the extended `DueDates` parser |
| B-6 | Tasks app pages, swipe, bulk, Plan my day, board, task page, sync wiring |
| B-7 | Wrap-up (type change, Done check, completion) and follow-up composer |
| B-8 | Context pages merged, and the Prepare screen |
| B-9 | Document reading (worker, libraries, schema, viewer) and the Inbox redesign |
| B-10 | Brief full controls; the Ask screen with suggestions |
| B-11 | Weekly review, Autopilot review default and copy fix, settings |

Each increment: screenshot tests in Paper and Graphite, empty and populated, at text scale 1.3; the F-4 guards green; tests on remote agents per `agents/TESTING.md`.

### R8. Other things found in this pass

- The `BottomNavigation.kt` doc comment says "four primary destinations plus New"; there are five (lines 58-63).
- The working tree currently holds another session's uncommitted edits. One of them (`core/work/Inbox.kt:127-129`) places a `val` inside a constructor call, which won't compile. That work is not part of this design and was left untouched.

---

## 1. Summary

The Work vertical already holds most of what a professional memory needs: first-class items (decisions, commitments,
questions, risks) with evidence, a Pulse model, briefs, scoped Ask, an Inbox with filing proposals, a Wrap-up with
change detection, a deterministic follow-up, weekly review, Autopilot and privacy enforcement. The problem is not
missing capability. It is that the capability is **spread across too many doors, two homes, two project pages and
three copies of the same lists**, with inconsistent words and very few visible sources.

The redesign:

1. Gives Work **one home, Pulse**. It is time-aware, capped at four "needs you" rows, and every row has one verb and a source.
2. Reduces the hub to **four segments with one job each**: Pulse · Tasks · Decisions · Projects.
3. Makes **"open the source"** one consistent component (the ▶ timestamp chip and the source sheet) on every surface.
4. Uses **one context-page layout** (pulse header → actions → history) for project, person and organisation.
5. Turns the Brief into a **document workspace**, and the follow-up into a **channel-aware composer**.
6. States what is **confirmed, suggested, edited and uncertain** with four trust marks used everywhere.
7. Is **honest about maturity**: PDF and web text reading, Gmail and Outlook are labelled *later*. Nothing that doesn't exist is shown as live.

No capability is removed. Every current destination stays reachable within two taps (§13).

---

## 2. Current UX audit (as built on this branch)

Method: the Work code paths were read end to end, and each claim below cites the code. No device or emulator was run
(AGENTS.md forbids local Gradle). Anything that needs a phone is in §15.5.

### 2.1 What works well, and is preserved

- **The data model is right.** `ItemEntity` (`core/database/Items.kt:32`) holds DECISION, COMMITMENT (MINE|THEIRS),
  QUESTION, RISK, REQUIREMENT and more. Statuses are honest (Decision ACTIVE|PROPOSED|SUPERSEDED|REVERSED, with a
  `supersedesId` chain). Evidence carries `startMs` (`Items.kt:77-88`). Commitments write through to tasks (`core/work/Items.kt:131`).
- **Pulse is computed from the database**, not a model: `AttentionKind` YOU_OWE, DECISION_NEEDED, THEY_OWE, QUIET (`core/work/Pulse.kt:19`).
- **Grounding rules are enforced**: brief prose and Ask drop uncited sentences (`core/work/Brief.kt`, `ai/assistant/ScopedAsk.kt:71,111`).
  The follow-up is a template, not a model (`core/work/FollowUp.kt:40`).
- **The Wrap-up already does the hard parts**: a Changes card with Confirm / Undo / Not the same (`WrapUpScreen.kt:440-452`),
  Unclear triage (`:459-468`), "+N more found" (`:262-272`), swipe-remove with Undo, and an add field.
- **Inbox filing is confirm-only** (`InboxFiler.file`, `core/work/Inbox.kt:278`). Proposals must be at least 60% grounded in the source (`:264-268`).
- **People without contacts**: no READ_CONTACTS and no ContactsContract. People come from speakers, calendar attendees and typed names (`core/work/People.kt`).
- **Privacy is enforced in one place**: `WorkPrivacy.mustStayOnDevice` (`core/work/WorkPrivacy.kt:43-59`).
  CLINICAL and LEGAL are sensitive by default (`WorkSettings.kt:54,125,128`).
- **Notes segment engineering**: keyset paging, skeletons, retry rows and "That's everything" (keep these in the Notes library).
- **Fixed since the October audit**: W-01 (the back arrow, `WorkPageScreen.kt:344`) and W-09 (custom types reach review, `WorkRepository.kt:88,108`).
  Failed recordings now reach the Work page's Needs you (`WorkPageScreen.kt:198`, `WorkDao.kt:73`).

### 2.2 Status of the 15 earlier findings

| ID | Finding | Now | Evidence |
|---|---|---|---|
| W-01 | No back arrow | **Fixed** | `WorkPageScreen.kt:344` |
| W-02 | Two Work homes | **Open** | `ProfessionalHome.kt` (≈15 blocks) and `WorkPageToday.kt` (9 blocks) both live |
| W-03 | "Task" doesn't add | **Partial** | routes to Tasks (`:356`), but the field is not focused (no FocusRequester) |
| W-04 | Failed recordings hidden | **Partial** | on the Work page, not on the Work home (`ProfessionalHome.kt:364-378`); MODEL_REQUIRED and QUEUED never surface |
| W-05 | Purpose unexplained | **Open** | default home is still TODAY (`Appearance.kt:42`); empty-state copy unchanged |
| W-06 | Header search global | **Open** | `WorkPageUi.kt:106` → `MainActivity.kt:434` |
| W-07 | Ask not on Work | **Partial** | on Today (`WorkPageToday.kt:280`), not in ⋯ |
| W-08 | Decisions link to the meeting, not the moment | **Open** | `WorkAllScreen.kt:156,162` pass `null`; the item evidence has `startMs` |
| W-09 | Custom types drop out | **Fixed** | `WorkRepository.kt:88,108` |
| W-10 | Mislabelled actions | **Open** | "Send" and "Use a template" → `onOpenWork` (`ProfessionalHome.kt:306,336`); "Work space" title (`:435`) |
| W-11 | Vocabulary | **Partial** | Mine / Waiting on on the page; "My tasks" on the home; "You owe / They owe" in Wrap-up and Brief |
| W-12 | Dark Pulse card, competing actions | **Open** | `PulseCard.kt:75`, 11 callbacks |
| W-13 | No navigation tests | **Open** | only `ExampleInstrumentedTest` |
| W-14 | WorkAll literals | **Open** | `WorkAllScreen.kt:101` hard-codes 26 sp |
| W-15 | Remembered segment hides what's due | **Open** | the segment is persisted; no banner. The redesign removes the cause (§4.4) |

### 2.3 New findings (this audit)

Numbered N-01…, continuing the W- series. Sev: H blocks or misleads on a primary path · M friction or inconsistency · L hygiene.

| ID | Sev | Category | Finding | Evidence |
|---|---|---|---|---|
| N-01 | H | Duplication | **The Inbox is reachable from 4–5 places on one page**: header icon, ⋯, the Today spotlight, a quick action, and the rhythm row | `WorkPageUi.kt:107`, `WorkPageScreen.kt:313`, `WorkPageToday.kt:162,271` |
| N-02 | H | Duplication | **There are two project pages**: `ProjectScreen` (Overview / Kanban, You owe, Waiting on…) and `ContextScreen` for a project (Details, Members, Decisions, Risks, Timeline, Memory). Each has data the other lacks | `ProjectScreen.kt:60-193`, `ContextScreen.kt:184-378` |
| N-03 | H | Duplication | **`WorkAllScreen` repeats** the Tasks segment (Mine, Waiting on) and adds Decisions, Questions, Risks and People as tabs reached only from ⋯ | `WorkAllScreen.kt:58,104` |
| N-04 | H | Hierarchy | **Today stacks nine blocks with two heroes** (Next up and the Needs-you hero), plus a glance bento of counts and a rhythm card | `WorkPageToday.kt:152-253` |
| N-05 | H | Trust | **Autopilot auto-run creates tasks without review** when it is on (auto-create defaults to true), bypassing the Wrap-up's confirm rule (Direction v2 D10: "no silent intelligence") | `PostSessionAutopilot.kt:151,156,595` |
| N-06 | M | Brief | **The Brief exposes no kind picker** (the engine has five `BriefKind`s), no tone, no length and no Copy | `core/work/Brief.kt:24`, `BriefScreen.kt:124-150` |
| N-07 | M | Composer | **The follow-up sheet has no Copy** and no preview of the channel's formatting | `FollowUpSheet.kt:96-130` |
| N-08 | M | Destructive | **Destructive actions with no undo or confirm**: Inbox Dismiss, and Remove person | `InboxScreen.kt:392,1080`, `WorkPersonScreen.kt:111` |
| N-09 | M | Wrap-up | **Done doesn't check unconfirmed changes**: a pending supersession is silently left unresolved | `WrapUpScreen.kt:170-173` |
| N-10 | M | Labels | **"Send…" on every task** sends only the bare title through the generic share sheet. It reads like "send the task to someone" | `WorkPageTasks.kt:187`, `WorkPageScreen.kt:297-300` |
| N-11 | M | Icons | **The Ask and Task quick actions reuse the Search icon** | `WorkPageToday.kt:280` |
| N-12 | M | Ask | **No suggested questions**, though the structured queries (what I've promised, who's waiting) exist | `ScopedAskSheet.kt` |
| N-13 | M | Inbox honesty | **PDFs, scans and links are never read** (no PDF text or OCR), yet the card layout implies the same understanding as audio | `core/work/Inbox.kt`; ML Kit scanner only yields a PDF (`InboxScreen.kt:189-196`) |
| N-14 | M | Failure | **MODEL_REQUIRED and stuck QUEUED recordings never surface**; only ERROR does | `WorkDao.kt:73` |
| N-15 | M | Prepare | **Prepare is a sheet** that has to hold seven sections; its sources are not consistently playable | `PrepareSheet.kt:80-123` |
| N-16 | L | Billing copy | **The Autopilot paywall names only Pro and Church, but BYOK also unlocks it** (copy mismatch, not a gate change) | `PostSessionAutopilot.kt` runRecipe; `Plan.kt:56-63` |
| N-17 | L | Settings | **Work settings has no entry for the home style or for integrations**, and Gmail/Outlook providers exist behind flags | `WorkSettingsScreen.kt`; `app/build.gradle.kts:72` |

### 2.4 Conflicts between documents and code, resolved here

| Conflict | Resolution |
|---|---|
| WORK_UX (9 Oct): notes are the founder's priority on the Work page · Direction v2 D9: "Notes stays as it is", Work holds the records | Recommend retiring the Notes segment; **founder decision F-2** |
| D6: Search stays the default 4th slot · D9: Work takes the slot for work identities | Both: Search by default, Work by default when a work profile is picked; **F-1** |
| D1: Work home becomes a short entry · v34: Professional home with a Pulse core | Pulse lives once, in Work; homes get a short Work card; **F-6** |
| Audit vocabulary "Mine / Waiting on" · Direction v2 "You owe / They owe" | Lists use **Mine / Waiting on**; relationship sentences on context pages use **You owe / owes you** (§4.5) |
| PLAN §6.5 "Composer: tone, length, Send via, Mark sent" · code: tone only, no length | Length is added as a deterministic section filter (Short = decisions + next steps; Detailed = + questions, risks) |

### 2.5 Architectural constraints that shaped the design

- **A vertical may not add a table or a screen type** (`PRODUCT_DIRECTION.md` §7). Everything here reuses existing entities. The new surfaces are layouts of existing data.
- **Nothing requires a model.** Pulse, Prepare, the structured Ask chips, the follow-up and the Brief's sections all work offline. AI adds prose only.
- **Items carry evidence `startMs`**, so W-08 (decision → moment) needs no schema change.
- **Projects are notebooks** (`NotebookEntity`, kind PROJECT). Organisations are `PersonEntity` with kind ORG. Confidentiality lives in `propertiesJson` and on people.
- **`InboxProposer` targets** are PROJECT, ORG, PERSON and EVENT; its actions are NOTE, TASKS, DECISION and CONTACT. The Inbox design uses exactly these.
- **Billing**: AUTOMATION_RECIPES, CONNECTED_INTEGRATIONS and DEEP_SYNTHESIS belong to BYOK, Pro and Church (`Plan.kt:56-63`). Ask, Brief, Inbox and Pulse are ungated. **No new gates are introduced.**
- **The default theme is Midnight Graphite** (dark; `Theme.kt:55`). The design is therefore checked in dark first-class, not as an afterthought.

---

## 3. Design philosophy

**Professional intelligence, refined productivity, dependable assistance.** Four rules:

1. **Answer, then explain.** Every screen opens with the conclusion (what needs you, where it stands), then shows the evidence one tap down. Never the reverse.
2. **One hero, one verb.** Each screen has a single primary element and each row a single primary action. Everything else is quiet.
3. **Evidence over eloquence.** Anything AI derived carries its source chip and a trust mark. Without a source, there is no claim.
4. **Calm by default.** No shame colours for lateness (amber, not red), no fabricated work on quiet days, no counts for their own sake, no idle animation.

Hierarchy comes from type, space and weight, not from colour. Colour is reserved for the accent (one per screen), amber
for "past its date", and the recording red.

---

## 4. Information architecture and navigation

### 4.1 Model

```
Bottom bar    Today · Notes · ＋ New · [Work | Search] · Settings          (unchanged component)
Today home    … + one short Work card (D1)                                 #1a
Work          header: Work · context line          🔍 Work search · 📥 Inbox (badge) · ＋ · ⋯
  Pulse       what needs me now (default, always)                          #1b–1h
  Tasks       Mine · Waiting on                                            #2a #2b #2g
  Decisions   Decisions · Open questions · Risks · Requirements            #2c
  Projects    Projects · People · Organisations                            #2d #2e
Pushed        Prepare #3a · Recording #3c · Wrap-up #4a · Follow-up #5a · Brief #5c · Ask #6a
              Project #7a · Person #7c · Organisation #7d · Inbox #8a · Inbox item #8b
              Weekly review #9a · Autopilot #10a · Work settings #10d · Work search #2f
⋯ menu        Ask about my work · Weekly review · Autopilot · Team circles · Work settings · What Work does   #2h
＋ menu       Record a meeting · Write a note · Add task (field focused) · Import · Add to Inbox
```

### 4.2 Why it is easier

| Before | After | Effect |
|---|---|---|
| Two homes (Professional home ≈15 blocks + Today segment 9 blocks) | One Pulse; the homes get one card | One place to learn; no divergent verbs (W-02) |
| Inbox in 4–5 places | Header icon with badge; one Pulse row only when needed | Discoverable without noise (N-01) |
| Today · Notes · Tasks · Projects, plus WorkAll's six tabs, plus ⋯ ×11 | Four segments, ⋯ ×6 | Each list exists once (N-03) |
| ProjectScreen and ContextScreen | One context layout | One mental model for project, person, org (N-02) |
| Global header search | Work-scoped, with "Search everything" | Search does what its place implies (D2) |
| Ask on Today only | Pulse, ⋯, every context page, search results | Memory is where the question arises (D3) |

### 4.3 Back and state

- A pushed screen always shows a back arrow. The bottom bar shows only when Work holds the bar slot (W-01 rule, kept).
- System back closes an open sheet first. Wrap-up edits survive back (they are the draft).
- **The segment is not remembered across launches** (it is within a session). Work always opens on Pulse. This
  removes W-15's cause instead of adding a banner. Tasks, Decisions and Projects are one tap away.

### 4.4 Vocabulary (single source of truth; goes into `strings.xml`)

| Concept | Word | Where it differs |
|---|---|---|
| Tasks I do | **Mine** (list) · "You promised" (Pulse row) | Context pages: "You owe Sarah" |
| Promises to me | **Waiting on** (list) | Context pages: "Sarah owes you" / "She owes" |
| Things to check after a meeting | **To review** · the screen is **Wrap-up** | "Needs you" is the Pulse section that contains it |
| Recorded item states | **Confirmed · Suggested · Edited by you · Uncertain match** | — |
| Projects / clients | Profile words (`WorkSettings.terms`): Client · Project; Clinical: Patient · Case; Legal: Client · Matter | — |
| Past its date | **Past due** (amber) · never "Overdue" in red | — |

---

## 5. The three flagship experiences

### 5.1 Work Pulse: "Open MeetingMind. Know what matters." (#1b–#1h)

**Layout (top to bottom, empty sections render nothing):**

1. **Greeting**: Outfit display, plus one human sentence that names the single most important thing ("Sarah's credentials are the one thing slipping").
2. **Hero, by moment:**

| Moment | Rule | Hero |
|---|---|---|
| Morning / before a meeting | next meeting within the prep window (default 15 min; shown from the first meeting of the day) | **Next**: title, time, project, avatars, three lines (You owe / They owe / Still open), **Prepare** + **Record** |
| After meetings | an unreviewed READY meeting exists | **To review**: "9 findings from Project Review", **Review findings** |
| Friday after the review time (`WorkSettings` rhythm) | weekly review due and not snoozed | **Weekly review**, Start / Monday |
| Quiet | nothing due today, no meeting today | No hero. A headline: "Nothing needs you today" |
| Day one | no items yet | Calendar-led hero ("First time meeting Acme… Record at 10:00") + three ways to start |

3. **Needs you · ≤4 rows**, ranked by Pulse.kt plus the redesign's additions: failed recordings (ERROR, MODEL_REQUIRED, QUEUED > 30 min) → YOU_OWE due/past → THEY_OWE past → DECISION_NEEDED with a meeting today → Inbox (if NEW items) → QUIET.
   Each row: kind overline · title · one sub-line with its source chip · **one verb** (Done / Nudge / Review / Try again / Open / Prepare). "See all" → Tasks.
4. **Today**: a timeline with a red now-line; past meetings dim; tomorrow's first item when today is over.
5. **Since yesterday**: 1–3 plain sentences from `item_events` since `pulseSeenAt`, each opening its source. Never counts alone.
6. **Ask**: a field and up to two suggested questions.

**Removed from today's Today segment**: the quick-action row (now ＋ and the header), the Inbox spotlight (now a row),
the glance bento (counts belong in section headers), the "Work intelligence & rhythm" card (Weekly review becomes the
Friday hero; Brief lives on context pages), the Team Circles block (⋯), and "Continue where you left off" (Notes / Recent).

### 5.2 Intelligence Brief: "Turn everything into something I can send." (#5c #5d #9b)

- **Entry**: context pages (Brief chip), Weekly review, ⋯ on a meeting. It opens scoped to that entity.
- **Controls row 1, kind**: Project status (PROJECT) · Client brief (CLIENT) · Meeting brief (MEETING) · Relationship (RELATIONSHIP) · Weekly (WEEKLY). These are the existing `BriefKind`s.
- **Controls row 2**: For (Client sponsor / Internal) · Tone (Plain / Formal / Warm) · Length (One page / Short). **They change only the two AI paragraphs and the section selection**, never the facts. (F-4: row 2 is the second increment.)
- **Document**: header (org, kind, date), **status pill from counts** (Attention / On track, with its reason line), then Executive picture (AI, cited), What changed, Decisions, Commitments, Risks, Open questions, Recommended next conversation (AI, cited), Evidence.
- **Citations**: numbered `cite` chips → the source sheet. Every AI sentence has at least one, or it is dropped (as today).
- **Edit**: the pencil outlines the AI paragraphs; sections are edited by fixing the record (tap → source → item).
- **Bottom**: Copy · Export (PDF / Word / Markdown) · Share. **No send button.** Share opens the system sheet.
- **States**: generating (skeleton prose, sections already visible) · no model / offline (sections plus a note with Try again) · confidential (on-phone model, Confidential header, footer off).
- **Deep synthesis** (`EXECUTIVE_PACK`, `PRE_SESSION_BRIEF`; BYOK / Pro) stays where it is, listed under the Brief's ⋯ as "Executive pack". It is not merged into the Brief.

### 5.3 Memory: "What do I know about this?" (#6a–#6e, plus context pages)

- **Ask is a pushed screen**, not a sheet, so long answers and sources have room. It opens **pre-scoped** (`AskScope`) to the page it came from. The scope chips can widen or narrow it: All of Work · project · organisation · person · time range.
- **Suggested questions**, first. The ones marked *instant* run the existing structured queries (what I'm waiting for, what I promised, decisions this month, what changed). They need no model and work offline (#6c).
- **Prose answers**: cited `[n]` chips, a Sources list (meeting · date · quote · ▶), and a provenance line (AI · where it ran). Unreviewed sources are labelled (#6b).
- **No source, no answer** (#6d): the existing `ScopedReason.UNCITED` / no-results state becomes a useful sentence that points to where the answer would come from.
- **Context pages carry Memory**: the pulse header, timeline and monthly story (`ContextScreen.kt:337-378`) (#7a #7b #7d).

### 5.4 How they reinforce each other

Pulse row ("Sarah · credentials · 4 days past") → **▶ source** (her own words, 10 Nov) → **Person page** (history, what
you owe her) → **Ask** ("why are the credentials late?") → **Brief** (client update with the dates) → the next meeting's
**Wrap-up** confirms the new date (Changes card) → tomorrow's Pulse says "Credentials moved to Thu".

---

## 6. Core screens

Each entry: purpose · structure · primary action · states. Frame ids point to the artifact.

**Today home Work card (#1a).** One line about the most relevant thing (prep before meetings, review after), one verb, the count, and one amber line if something is slipping. Tapping it opens Work › Pulse. It never lists more.

**Tasks (#2a #2b #2g).** Mine | Waiting on toggle. Quick add at the top. Opening via ＋ › Add task focuses it (W-03). Mine is grouped Past due · Today · This week · Later · No date. A row shows: checkbox · title · due · project (lock if confidential) · "Promised to James" pill · source chip. Past due rows show Friday / Next week / Drop inline. Waiting on rows show: avatar · what · who · due · how late · source · Nudge / Received. "Send…" (N-10) is renamed **Send to Todoist / Linear / GitHub** and appears only when task sync is connected (task sheet).

**Decisions (#2c).** Chips: Decisions · Open questions · Risks · Requirements (Risks and Requirements only for profiles with those `itemKinds`). Decisions has "To confirm" (PROPOSED, AI mark, Confirm / Not decided) and "In force" (ACTIVE) with History (the supersede chain). Rows open the **moment** (W-08 fix: pass the evidence `startMs`).

**Projects · People · Organisations (#2d #2e).** Project cards: org mark, name, lock, a status pill from counts with its reason sentence, and the next meeting. People: avatar, role, org, last seen; an uncertain match shows Confirm / Not a person. Organisations: mark, domain, counts.

**Work search (#2f).** Scoped to Work, with type chips. Results are grouped by kind (Promises, In meetings, People…). At the end: "Ask: <query>" and "Search everything".

**Prepare (#3a #3b).** A full screen (it replaces `PrepareSheet`; the sheet stays as a peek from the recorder). Sections: time · title · project and people chips → Last time (quote, ▶) → You owe → They owe (with linked Inbox evidence) → Decided → Still open → Suggested agenda (from open items only; AI orders, never adds) → Material. Sticky **Record**. First meeting: attendees from the invite, filed material, the profile template. No empty state.

**Recording (#3c #3d #3e).** The existing recorder (Recording Page Redesign `#7b`) plus one **context strip**: project · title · people · "3 open items". Tapping it shows the prep's open items, each one tap to mark as a key moment (an existing marker type). Stop → processing with named stages.

**Wrap-up (#4a–#4d).** Meta card (Project with a "guessed" mark until confirmed · With · Speakers) → one-line AI summary → groups Decisions · Your tasks · Waiting on · Open questions · Risks → "N more, lower confidence" → add field. Row: checkbox (pre-checked) · text · owner and due chips · ▶ · trust mark · Changes inset (Confirm) · ✕ (Undo toast). Tapping a row opens the edit sheet: text, **type** (task / waiting on / decision / question / risk), who, due, source playback, Dismiss / Save. Bottom: Draft follow-up · **Done · save N**. Done with unconfirmed changes opens a check sheet ("Check them" / "Confirm all"). Complete screen: what went where, plus the next step.

**Failed recording (#4e).** Reassurance first ("The 38-minute recording is safe"), the reason in plain words, then Try again · Process when charging · Listen · Write what you remember · Use a smaller model.

**Follow-up composer (#5a #5b).** To · Send via (adaptive, with the reason) · Tone · Length · Email / WhatsApp preview · body (editable) · trust line "Written from N confirmed items · no AI" · Sources. Bottom: Copy · Share · **Open in <channel>**. Returning asks "Did it go?" (existing).

**Nudge (#5e).** A sheet: a short editable draft, tone chips, Copy, Open in <their channel>, and the channel's reason.

**Context pages (#7a–#7f).** One layout:
`identity` → `status line (counts)` → `action chips (Ask · Brief · Prepare · Record / Message)` → **pulse header**
(You owe · They owe · Open · Decided · Next) → for projects, tabs Overview · Timeline · Notes & files; for people,
Conversations · Projects · "How MeetingMind knows Sarah"; for orgs, People · Projects · History (story). Confidential
pages lead with the privacy banner and offer only the on-phone Brief.

**Inbox (#8a–#8h).** Capture tiles (Voice note · Scan · Paste note · File) → filters (All · Needs you · Voice & media · Docs & links · Filed) → cards: kind icon · title · source · arrival time · 2-line summary (when it exists) · state · suggestion pill · **File to X** · Change… · Dismiss (with Undo). Item screen: player (audio) → provenance (From, recognised person, Arrived, File, size) → What it says (AI summary + where it ran) → *honest note for PDFs and links* → **MeetingMind suggests** (destination, the reason, grounded extra items each with a checkbox) → File it / Change → transcript with the grounded span highlighted. Filed state: where it went, Open project, Undo.

**Weekly review (#9a).** The facts in one sentence → What changed → Finished (folded) → Carry into next week (Carry / Move to Mon / Drop per item) → Still with others (Nudge) → Unanswered → Gone quiet. Bottom: Weekly brief · Set next week.

**Autopilot (#10a–#10c).** Only the Work recipes inside Work (Follow-up message = EXECUTIVE_DEBRIEF; Client follow-up draft = CLIENT_CRM_LOG); the others stay in their spaces. Auto-run switch (off by default). Tasks: **Add to Wrap-up** (recommended default) or Create directly (F-3). Result screen: cited draft, then Save as note · Review N tasks · Copy · Share.

**Work settings (#10d).** What kind of work · Your words · Work rhythm · Follow-ups · Bottom bar · Autopilot · Privacy (Keep work on this phone, Recording reminder) · Connected (Phone calendar *on*; Task sync, BYOK & Pro; Gmail & Outlook *later*).

---

## 7. User journeys (validated in the artifact's walkthrough)

| # | Journey | Taps (happy path) | Main failure point → recovery |
|---|---|---|---|
| 1 | Start the workday: Today → Pulse → Prepare → source → Record | 3 to prep, 4 to record | Calendar event with no attendees → project shown as "guessed" until confirmed |
| 2 | Complete a client meeting: Stop → Wrap-up → edit → confirm changes → Done → Follow-up → "Did it go?" | about 6 + one glance per finding | 25+ findings → low confidence stays folded; Done saves the rest as unreviewed, never blocks |
| 3 | New information: Pulse row → Inbox → item → source → File → project | 2 to file | PDF/link not readable → filed by name, said plainly; Undo on File and Dismiss |
| 4 | Understand a project: Projects → project → Timeline → Ask → source → Brief | 2 to status, 4 to a cited answer | Stale AI paragraph → cited and dated; status from counts |
| 5 | Relationship history: People → Sarah → source → Nudge → Prepare | 2 to Sarah, 3 to a nudge | Wrong merge → "Same person as…", uncertain-match state |
| 6 | Recover from failure: evening Pulse → failed recording → Try again; failed import | 1 to retry | Duplicate jobs → existing job-row guard |
| 7 | Weekly review: Friday Pulse → review → carry/move/drop → brief | 1 + one pass | Skipped weeks → snooze to Monday; never stacks |

Each journey in the artifact lists clarity, cognitive load, failure points, recovery and whether the outcome is obvious.

---

## 8. Design tokens and components

### 8.1 Tokens (unchanged, from `ui/theme`)

- **Colour**: Paper (light) and Midnight Graphite (dark) roles exactly as `Tokens.kt:62-127`. Accent: the user's `MMAccent`, Work default Indigo `#5B5BD6` / `#9B9CF6`, wash 10% / 15%.
  The artifact uses these hex values verbatim. **Amber** (`warning`) is used for past-due and uncertain states, **not** `danger`.
  For text in light mode use a darker amber (`#B45309`) to meet 4.5:1 on Paper. This needs a token, `warningInk`, to be added (F-4 style guard: add to `DESIGN_SYSTEM.md` §2 first).
- **Type** (`MMType`): display 30/36 Outfit (greeting only) · title 22/28 · heading 17/24 · body 15/22 · secondary 13/18 · caption 12/16 · overline 11/14. The artifact renders about 1 sp smaller to fit the 412 dp frame at web scale; Compose uses the tokens as they are.
- **Space** `MMSpace` 4/8/12/16/24/32; gutter 16; section gap 24. **Radius** 8 / 16 / pill; sheets 16 top (the artifact shows 20, so use `MM.radius.sheet`).
- **Elevation**: flat; light cards have a 1 dp `line` border; dark cards have none (the surface step). Only sheets, the bar and toasts get the floating shadow.
- **Motion**: 150 / 250 / 300 ms; sheet enter 300 ms; no idle animation (the processing spinner is a state, not idle).

### 8.2 Components (new ones are compositions of `core/ui/mm`)

| Component | Built from | Used on |
|---|---|---|
| `SourceChip(ts, onPlay)`: ▶ + timestamp, accent wash | `MMChip` | every AI-derived row |
| `CiteChip(n)`: a numbered citation | text span | Brief, Ask, story paragraphs |
| `SourceSheet(evidence)`: speaker, time, quote, play, open in transcript | `Sheet` | everywhere (#6e) |
| `TrustMark(kind)`: Confirmed · Suggested · Edited · Uncertain · Typed | caption + dot | rows, paragraphs |
| `WhereItRan(local)`: On this phone / MeetingMind cloud | pill | AI outputs |
| `AttentionRow(kind, title, sub, verb)` | `ListRow` | Pulse Needs you |
| `AgendaTimeline(items, now)` with now-line | new composable, tokens only | Pulse, project timeline |
| `PulseHeader(rows)`: You owe / They owe / Open / Decided / Next | `MMCard` + key-value rows | context pages, Prepare |
| `ContextActions(chips)` | `FilterChipRow` | context pages |
| `FindingRow` + `FindingEditSheet` | `ListRow`, `Sheet` | Wrap-up |
| `DocumentPreview(sections)` | `MMCard` | Brief, Autopilot result |
| `ChannelPreview(email \| whatsapp)` | `InsetPanel` | Follow-up |
| `InboxCard` / `SuggestionCard` | `MMCard` / `HeroCard` | Inbox |
| `OrgMark`, `AvatarStack` | existing avatar | everywhere |

**Retire**: `PulseCard` (dark gradient), the glance bento, the `WorkAllScreen` header, the Inbox spotlight, and the rhythm card.

---

## 9. Interaction states (all drawn in the artifact)

| State | Treatment | Frame |
|---|---|---|
| First-time user | Day-one Pulse from the calendar, three ways to start, no-contacts line | #1f |
| No meetings today | Quiet Pulse, no hero, two true lines | #1e |
| Empty Inbox | "Inbox clear" with what belongs here | #8g |
| No outstanding tasks | Section disappears; Pulse "Nothing needs you" | #1e |
| No projects | Projects: one-sentence empty state + New project (existing) | — |
| Missing project context | Wrap-up "guessed" mark; Inbox "Your choice" | #4a #8d |
| Processing recordings | Named stages; Inbox progress on this phone | #3e #8e |
| Failed recordings / imports | Reassurance, reason, recovery options | #4e #8f |
| Unreviewed findings | Suggested mark; "To confirm" group; faint dot in lists | #2c #4a |
| Offline | Pulse banner; Brief without prose; Ask instant answers | #1g #5d #6c |
| Failed / partial AI generation | Brief sections render, prose replaced by a note + Try again | #5d |
| Incomplete preparation / first meeting | First-meeting prep with what exists | #3b |
| No historical context | Ask "No source, no answer" pointing forward | #6d |
| Loading | Skeleton rows, never a spinner on a blank page | #2g |
| Destructive | Undo toast on Dismiss / File; Remove person asks first | #8a, #7f |
| Autopilot pending | Draft "not saved yet"; tasks to review | #10c |

---

## 10. Privacy and trust

- **No rule is weakened.** Confidential projects and people, `keepOnDevice`, and Clinical/Legal are routed exactly as `WorkPrivacy` does today. The design only makes it visible:
  the **lock** on rows, the **banner** on confidential pages, **WhereItRan** on every AI output, "Brief (on this phone)", and Confidential on exports.
- **Confidential items never leave the phone** and can't be shared to a Circle (existing rule). Weekly briefs list them as "1 confidential engagement".
- **Sensitive profiles**: Pulse notifications and lock-screen content show counts only (PLAN D5.1). The prototype shows names only inside the app.
- **No contacts permission.** Person pages show "How MeetingMind knows Sarah" (provenance), and day one says so explicitly.
- **AI is never presented as fact.** Every derived item has a trust mark until confirmed. Supersessions, unclear owners and Inbox filing all need confirmation (D10). Autopilot tasks go to review (F-3).
- **Nothing outward is automatic.** Follow-ups, nudges and briefs end at "Open in <app>" or Share; MeetingMind asks "Did it go?" afterwards.
- **Consent**: the recording reminder ("Let people know you're recording") stays; Clinical and Legal can't silently skip it.

---

## 11. Accessibility

- **Contrast**: all text pairs come from tokens verified to AA. Amber text needs `warningInk` (light `#B45309`, dark `#FBBF24`). Trust-mark dots always come with a text label, so meaning never depends on colour alone.
- **Touch targets**: every chip, ✕, checkbox and source chip has a 48 dp hit area (visuals are smaller; use `Modifier.minimumInteractiveComponentSize`).
- **Screen readers**: a source chip reads "Play source, Sarah Chen, 22 minutes 10 seconds". A citation reads "Source 3". An attention row reads "You promised: API documentation to James, due today. Button: Done". Pulse sections are headings.
- **Text scale 1.3 and 200%**: rows wrap instead of truncating titles; the pulse header's key column wraps above its value below 360 dp; the context line in the Work header truncates first.
- **Reduced motion**: no idle animation; the skeleton shimmer and spinner become static under reduced motion (as in the artifact's CSS).
- **One-handed use**: primary actions sit in sticky footers (Prepare › Record, Wrap-up › Done, Composer › Open in…).

---

## 12. Mapping to the existing implementation

| Design | Existing code | Change |
|---|---|---|
| Pulse (#1b–1h) | `WorkPageToday.kt`, `core/work/Pulse.kt`, `WorkViewModel.kt:112-114` | Re-compose Today as Pulse; add the hero-by-moment rule; add failed / MODEL_REQUIRED / QUEUED rows (`WorkDao.kt:73` predicate); remove the bento, spotlight, rhythm and quick-action rows |
| Today home card (#1a) | `ProfessionalHome.kt`, `HomeQuickAccessRow.kt` | New `WorkEntryCard`; `ProfessionalHome` becomes Today home + card + Work slot (F-6); retire `PulseCard` |
| Segments | `WorkPageLogic.kt:21`, `WorkPageScreen.kt` | Today→Pulse, Notes→(retired, F-2), +Decisions; Projects absorbs People/Orgs; stop persisting the segment across launches |
| Tasks | `WorkPageTasks.kt` | Focus quick-add from ＋ (W-03); "Promised to" pill (commitment link via `sourceItemId`); rename "Send…" → task-sync action (N-10) |
| Decisions | `WorkAllScreen.kt` tabs | Move to the segment; pass evidence `startMs` (W-08); PROPOSED → "To confirm" |
| Work search | `MainActivity.kt:434` | Route to a Work-scoped search (D2) |
| Prepare | `PrepareSheet.kt` | Promote to a screen route; keep the sheet as the recorder's peek |
| Recording strip | recorder (Recording Page Redesign `#7b`) | Add a context strip, read only, from the meeting's calendar match |
| Wrap-up | `WrapUpScreen.kt` | Type-change in the edit sheet; Done checks pending Changes (N-09); completion screen |
| Composer | `FollowUpSheet.kt`, `core/work/FollowUp.kt` | Full screen; Copy; channel preview; Length = section filter |
| Brief | `BriefScreen.kt`, `core/work/Brief.kt:24` | Kind chips (existing `BriefKind`); Copy; increment 2: audience, tone, length |
| Ask | `ScopedAskSheet.kt`, `ai/assistant/ScopedAsk.kt` | Promote to a screen; suggested questions mapped to the structured queries; source list rows |
| Context pages | `ContextScreen.kt`, `ProjectScreen.kt`, `WorkPersonScreen.kt` | Merge into one layout on `ContextScreen`; keep Kanban behind its flag as a project tab; Remove person asks first |
| Inbox | `InboxScreen.kt`, `core/work/Inbox.kt` | Card hierarchy; Undo on Dismiss (N-08); "Why" line from proposal reason; honest no-text note (N-13) |
| Weekly review | `WeeklyReviewScreen.kt` | Restyle; the plan control is per row (Carry / Move / Drop) |
| Autopilot | `PostSessionAutopilot.kt`, `feature/automation/AutopilotSheet.kt` | Work-only recipe list in Work; task mode "Add to Wrap-up" (F-3); fix the plan copy (N-16) |
| Settings | `WorkSettingsScreen.kt` | Add Autopilot and Connected rows; Gmail/Outlook shown as Later |

**Data needed, none new:** items and evidence (`Items.kt`), Pulse kinds, `observeFailed`, proposals, `BriefKind`, `AskScope`, privacy flags.
Two **queries** change: `observeFailed` (add MODEL_REQUIRED and QUEUED older than 30 min) and the decision row route (evidence `startMs`). **No schema change.**

---

## 13. Retained · improved · consolidated · later

- **Retained as is**: data model, Pulse ranking logic, grounding rules, deterministic follow-up, "Did it go?", confirm-only filing, keyset paging, privacy routing, billing gates, marks and the recorder, Circles sharing from Wrap-up.
- **Improved**: Pulse layout, Tasks, Wrap-up (type change, Done check, completion), Inbox cards and item, Prepare (full screen), Brief (kind, copy), Ask (screen, suggestions), Weekly review, Autopilot defaults, settings.
- **Consolidated**: two homes → one Pulse + a card · ProjectScreen + ContextScreen → one page · WorkAll tabs → Tasks + Decisions · five Inbox doors → one · ⋯ 11 → 6 · Notes segment → Notes library (F-2).
- **Reachability check (≤2 taps from Work)**: Inbox (1) · Weekly review (⋯, 2) · Brief (context page, 2) · People & orgs (Projects, 2) · Decision log (1) · Open questions (2) · Risks (2) · Autopilot (⋯, 2) · Work settings (⋯, 2) · Team circles (⋯, 2) · Ask (1) · Notes (bar, 1) · Saved views (flagged, unchanged).
- **Later (shown as unavailable, never as live)**: PDF/scan/web text reading (F-5) · Gmail and Outlook context · Drive/OneDrive/Dropbox · Recipes engine with approval cards (`FEATURE_RECIPES`) · lock-screen marks · next-meeting widget · Professional X-Ray.

**Intentionally not added**: pipelines, deals, Gantt, invoicing, HR, resource planning, a Kanban as a default view, KPI dashboards, an automation builder, auto-send of any kind, and a contacts permission.

---

## 14. Implementation recommendations (Phase B, after approval)

Small increments, each with its own tests, in this order. Navigation tests first (W-13).

| Inc | Content | Findings | Risk |
|---|---|---|---|
| B-0 | Compose navigation tests for Work routes, back, bar slot, and ＋ › Add task focus | W-13 | Low |
| B-1 | Shared components: SourceChip, SourceSheet, TrustMark, WhereItRan, AttentionRow, PulseHeader, AgendaTimeline; `warningInk` token | — | Low |
| B-2 | Pulse: hero-by-moment, ≤4 Needs you, failed-state query, Since yesterday, Ask; remove the retired blocks | W-02 W-04 W-12 N-04 N-14 | Medium |
| B-3 | Segments and vocabulary: Decisions segment, Projects/People/Orgs, WorkAll retired, segment not persisted, Work search, ⋯ reduced | N-01 N-03 W-06 W-07 W-08 W-11 W-15 | Medium |
| B-4 | Home: Work card; `HomeStyle.PROFESSIONAL` migration; retire `PulseCard` | D1 D7 W-10 | High (migration) |
| B-5 | Wrap-up: type change, Done check, completion; Composer: Copy, preview, length | N-07 N-09 | Medium |
| B-6 | Context pages merged; Prepare screen; Remove-person confirm | N-02 N-08 N-15 | Medium |
| B-7 | Inbox: cards, Undo, Why line, no-text note | N-08 N-13 | Low |
| B-8 | Brief kind + Copy (then B-8b audience/tone/length); Ask screen + suggestions | N-06 N-12 | Medium |
| B-9 | Weekly review restyle; Autopilot task mode + copy fix; settings rows | N-05 N-16 N-17 | Low |

For every increment: screenshot tests in Paper and Graphite, empty and populated, at text scale 1.3; the F-4 guards
stay green; no colour or size literals; tests dispatched to remote agents per `agents/TESTING.md`.

---

## 15. UX review

### 15.1 The ten most important improvements

1. **One Work home (Pulse)** that changes with the time of day and never fills a quiet day.
2. **"Open the source" is one gesture everywhere**: the same chip and the same sheet, landing on the moment (fixes W-08).
3. **Four segments, one job each**: Tasks, Decisions and Projects each exist once (WorkAll, the second project page and the Notes duplicate go).
4. **Wrap-up you can trust**: type change, unconfirmed-change check on Done, a completion screen that says where everything went.
5. **One context-page layout** with a pulse header for project, person and organisation.
6. **The Brief is a document workspace**: kinds, citations, export and Copy, with graceful degradation.
7. **Ask is memory, not chat**: pre-scoped, instant structured answers, sources, and "no source, no answer".
8. **The Inbox as a calm front door**: provenance, a reason for each suggestion, Undo, and honesty about what it can't read.
9. **Recovery is designed**: failed recordings and imports lead with reassurance and offer real options; MODEL_REQUIRED and QUEUED recordings surface.
10. **Trust is visible**: four trust marks and "where it ran" on every AI output; Autopilot tasks go to review.

### 15.2 Reduction in interactions

| Task | Today | Redesign |
|---|---|---|
| Prepare for the next meeting from Home | Home → Work → Today → Prepare (sheet) → scroll | Home card → **Prepare** (1 tap) |
| Open a decision's moment | Work → ⋯ → Decision log → row → meeting top → scroll | Work → Decisions → ▶ (2 taps, lands on the moment) |
| File an Inbox item | Work → Inbox → card → Options/File to → sheet → Confirm | Work → Inbox → **File to Acme** (2 taps) |
| Add a task from ＋ | ＋ → Add task → tap the field | ＋ → Add task (field focused) |
| See what someone owes you | Work → ⋯ → People & orgs → person → scroll | Work → Projects › People → person (header) |
| Copy a follow-up | not possible | Composer → Copy |

### 15.3 Usability risks

- **Losing the Notes segment** may upset people who used it daily (F-2). Mitigation: the Notes tab remembers the Work filter, project pages list notes, and Work search covers them. Measure taps before removing it.
- **Pulse correctness is the product.** A wrong "Needs you" row erodes the habit faster than a missing one. The W0 extraction gates (actions ≥ 80%, commitments ≥ 75%) must hold before wide release.
- **Four-row cap** can hide a fifth urgent item. "See all" goes to Tasks, and the ranking puts failures and past-due first.
- **Hero switching by moment** could feel unpredictable. Each hero is labelled with its reason ("Next · in 1 h", "To review").
- **Uncertain matches** (people, projects) need low-friction confirmation, or they accumulate. They are inline, never a separate queue.

### 15.4 Small screens

- Designed at 412 dp; checked against 360 dp: segment labels fit (≈ 300 dp); header actions take 176 dp, so the context line truncates before the title.
- Pulse's first viewport on a 6" phone shows the greeting, the full hero and the first Needs-you row.
- Long titles wrap to two lines in rows; chips scroll horizontally; sheets cap at 86% height and scroll.
- Sticky footers keep the primary action reachable with one thumb.

### 15.5 Needs device or user testing (hypotheses, not findings)

H-1 people find Notes without the segment · H-2 the morning/evening hero switch is understood · H-3 the Wrap-up for a 45-minute meeting takes under 60 s · H-4 the source chip is discovered without a hint · H-5 the Inbox "Why" line increases one-tap filing.

---

## 16. Founder decisions

| # | Decision | Recommendation |
|---|---|---|
| F-1 | Work in the fourth bar slot when a work profile is chosen? | Yes; Search stays the default for everyone else (keeps D6) |
| F-2 | Retire the Work Notes segment? | Yes, with the Notes tab's remembered Work filter; alternative: keep it as a fifth segment using the same component |
| F-3 | Autopilot tasks: Wrap-up review by default? | Yes; "Create directly" becomes an opt-in |
| F-4 | Brief controls order | Kind + Copy first; audience, tone and length second |
| F-5 | Add on-device PDF / scan text reading to the Inbox roadmap? | Your call; outside this redesign |
| F-6 | `HomeStyle.PROFESSIONAL` | Keep it as a choice: Today home + pinned Work card + Work slot |

Approval of this document and the artifact starts Phase B at increment B-0.
