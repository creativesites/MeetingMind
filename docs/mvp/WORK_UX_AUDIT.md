# Work vertical: UX audit, findings and proposed redesign

Status: **audit and design proposal, awaiting founder approval.** No production UI has changed.
Scope: the Work vertical as it is in code on branch `work-ux-redesign` (from `ccr-26603b0d-1v73bn`, 2026-10-20).
Reported by a user: *"Having a challenge navigating Work."*

Design artifact: https://claude.ai/artifact/H9dnBur8ZzwowA25eWeUzV
(Private until shared from the Share menu. Nobody else can open the link until it is shared.)

Related: `docs/mvp/WORK_UX.md` (the earlier Work page spec, partly implemented), `docs/PLAN_PROFESSIONAL.md`,
`docs/mvp/DESIGN_SYSTEM.md`, `docs/mvp/COMPANION_AUDIT.md` (format reference).

---

## 0. Method and limits

- Read the code paths that the nine journeys in the brief cross. Each finding cites `file:line`.
- Ran no device or emulator tests. This sandbox has no Android SDK, so the build and the UI were not run.
  Anything that needs a device is listed in §5 as a hypothesis, not a finding.
- "Verified" means the code path was read end to end. It does not mean the behaviour was observed on a phone.

---

## 1. Current navigation (as built)

Work is reached through **five separate doors**. The default home for new installs does not show the Work home.

| Door | Where | Goes to |
|---|---|---|
| Quick Access "Work" tile | Today home (default for new installs) and the Work home | `WORK` page |
| Work home (style "Work") | Home picker, `HomeStyle.PROFESSIONAL` | Its own long page; links to `WORK`, `WORK_ALL`, `PROJECT`, `WORK_PERSON`, `WRAP_UP`, `MEETING` |
| Bottom bar 4th slot | Only if the person chose Work over Search (`TabSlot`, default `SEARCH`) | `WORK` |
| Notes tab "Work" filter chip | Notes library | Notes list (a second list of work notes) |
| Settings → Work settings | `WORK_SETTINGS` | Work settings |

**Work page** (`WORK`): header (Search · ＋ · ⋯), four segments: **Today · Notes · Tasks · Projects**.

- **＋** → Record meeting · Write note · Add task · Import
- **⋯** → Inbox · Weekly review · Brief · People & orgs · Decision log · Open questions · Risks · Saved views (flag off) · Work settings
- **Search** (header) → global `SEARCH`, not Work-scoped

**WORK_ALL** (`WorkAllScreen`) lists: You owe · They owe · Decisions · Open questions · Risks · People.

Depth to common answers:

| Question | Taps from Home (default) |
|---|---|
| Open the notes list | 1–2 (Work tile; the Notes segment opens first only when nothing needs the person) |
| Find a decision and open its meeting | 4 (Work tile → ⋯ → Decision log → row) |
| Ask a question about a project | 4 (Work tile → Projects → project → Ask) |
| Add a task | 3 via ＋ → Add task, or 2 via the Work home's "Task" button, which does **not** add (see W-03) |

---

## 2. Findings, ranked by impact

Ranking weighs navigation, first-use success, task completion and retention. Severity: **High** blocks a journey or
misleads on a primary path; **Medium** adds friction or inconsistency; **Low** is a hygiene issue.

| ID | Sev | One line |
|---|---|---|
| W-01 | High | The Work page has no back arrow and no tab bar in the default setup |
| W-02 | High | Two Work homes with the same content and different verbs |
| W-03 | High | The Work home's "Task" button opens a list with no way to add a task |
| W-04 | High | Recordings that failed to process never appear in Work, so there is no recovery path |
| W-05 | High | A first-time user finds Work as one unexplained tile among six |
| W-06 | Medium | The Work header search is global, not Work-scoped |
| W-07 | Medium | "Ask about my work" is not on the Work page |
| W-08 | Medium | Decisions and questions link to the meeting, not to the moment |
| W-09 | Medium | Custom-type recordings get a wrap-up once, then drop out of "Needs you" |
| W-10 | Medium | Some labels send the person somewhere other than what they say |
| W-11 | Medium | Vocabulary differs across surfaces for the same things |
| W-12 | Medium | The Work home is one long column with competing actions and a dark hero |
| W-13 | Medium | No automated test covers Work navigation, so W-01 shipped unnoticed |
| W-14 | Low | `WorkAllScreen` uses its own header and size/colour literals, not the tokens |
| W-15 | Low | The Work page's own state (segment) can hide what is due |

### W-01: No back arrow and no tab bar on the Work page (High, verified)

- **Where:** `MainActivity.kt:934` passes `onNavigateBottomNav = navigateToPrimary` to `WorkPageScreen` on every path.
  `WorkPageScreen.kt:275` then sets `onBack = if (onNavigateBottomNav == null) onNavigateBack else null`, which is always `null`.
  `WorkPageScreen.kt:261` shows the bottom bar only when `settings.tabSlot == TabSlot.WORK`. The default is `TabSlot.SEARCH` (`WorkSettings.kt:91`).
- **Observed in code:** opening Work from the Today home gives a header with no back control and no tab bar. The only
  way out is the system back gesture. The Home tab is not reachable from the page.
- **Why it blocks:** the brief's question A ("do users know how to return?"). A person who does not know the system back
  gesture is stuck on the page.
- **Recommendation:** show a back arrow whenever the page was pushed from another screen. Show the bar only when Work
  holds the fourth slot. Pass a real back action rather than deriving it from the bar callback.
- **Dependencies and risks:** `TabSlot` routing in `MainActivity.navigateToPrimary`. Low risk.
- **Validation test:** Compose test: open `WORK` with default settings from `HOME`; assert a node labelled "Back" exists and
  pressing it returns to `HOME`. Device check: the back gesture and the arrow both work.

### W-02: Two Work homes, same content, different verbs (High, verified)

- **Where:** `ProfessionalHome.kt` (490 lines, 15 blocks in one column) and the Work page's Today segment
  (`WorkPageToday.kt`). Both show the next-meeting hero, Needs you, today's schedule, tasks, projects and people.
- **Observed:** the Work home's "Needs you" uses "Wrap up" and "Send"; Today uses "Review" and "Send". The Work home's
  projects and people rows open `WORK` and `WORK_ALL`, while the Work page's own Projects segment does the same job with
  a different layout.
- **Why it confuses:** the person learns one Work surface and then meets another with a different structure.
  Changes made in one (a new row type, a fix) are not made in the other.
- **Recommendation:** one Work surface. The Work home becomes a short entry (next meeting, the needs count and "Open Work")
  or is retired into the Today segment (founder decision D1).
- **Dependencies and risks:** `HomeStyle.PROFESSIONAL` is selectable in settings (`Appearance.kt:38`). Anyone who chose it
  needs a migration. The Work home also hosts the Pulse card, the Circles card and the Faith section.
- **Validation test:** a screenshot test for the home and the Today segment, each with one hero and no duplicated section.

### W-03: "Task" on the Work home opens a list with no add control (High, verified)

- **Where:** `WorkHomeParts.kt:279` (`WorkQuickAction(AddTask, "Task", …, onTask)`); `ProfessionalHome.kt:` `onTask = { onOpenAll(WorkTab.MINE) }`.
  `WorkAllScreen.kt` has no add field. Its only add action is "Add someone" under People.
- **Observed:** tapping **Task** shows the "You owe" list. Nothing can be typed.
- **Why it blocks:** task completion. The label promises an action the screen does not perform.
- **Recommendation:** route Task to the Work page's Tasks segment with the quick-add field focused. The quick-add exists
  already (`WorkPageTasks.kt`, `QuickAdd`), and so does "Add task" in ＋ (`WorkPageScreen.kt`).
- **Validation test:** Compose test: tap Task → the Add field is focused → entering text and submitting creates a row in "Mine".

### W-04: Failed recordings never reach Work (High, verified; recovery path not traced)

- **Where:** `WorkDao.kt:69` `observeUnreviewed` filters `status = 'READY'`. `WorkRepository.kt:86–88` `observeToReview`
  additionally requires findings. `observeFollowUps` (`WorkRepository.kt:446–449`) also requires `status == "READY"`.
- **Observed:** a recording whose processing ended in `ERROR` has no row in Needs you, in the Work home's Needs card,
  or in Pulse. In the Notes list it shows a "Transcript failed" glyph (`MMRows.kt:181`) and opens the note.
- **Why it blocks:** the brief's journey 9 (recovering from processing failures). From Work there is no action to take.
  Work also never tells the person a recording is waiting on them.
- **Recommendation:** add a Needs-you row, "Didn't process · Try again / Open". Use the existing failure route. I did not
  trace that route here, so Stage 4 must confirm it exists and where it lives.
- **Dependencies and risks:** the processing pipeline's ERROR state (`agents/STATUS.md`, 2026-10-19). A retry must not
  duplicate jobs.
- **Validation test:** unit test: a meeting with `status = ERROR` yields a Needs-you row. Device test: force a failure, confirm the row, retry.

### W-05: Work is one unexplained tile for new users (High, verified)

- **Where:** `DEFAULT_FOR_NEW_INSTALLS = TODAY` (`Appearance.kt:41`). The Today home's Quick Access row includes "Work"
  (`HomeQuickAccessRow.kt:59`) among six icons. The row's comment says Work is "represented by its richer Work & Projects card"
  (`HomeQuickAccessRow.kt:53–54`). That card is not on the default home.
- **Observed:** the default home for a new person has no Work card, only a small "Work" tile. The Work page's empty state
  says "Your meeting notes live here" and offers Record and Write note. The only text that says what Work does
  ("Start with one conversation … decided and promised will show up here") sits in the Pulse card on the Work home,
  which a new person on the default home never sees.
- **Why it blocks:** question A. The purpose of Work is never stated where the person meets it.
- **Recommendation:** on the Work page's first visit, a single contextual line in the Today empty state: what Work does, and
  one primary action. No tooltip tour. Dismissible; revisitable from ⋯ → "What Work does".
- **Validation test:** screenshot test for the first-use empty state; user test with two first-time people (see §5).

### W-06: Header search is global (Medium, verified)

- **Where:** `WorkPageScreen.kt` header `onSearch`, wired to `openSearch` (`MainActivity.kt:378–380`, passed at `:928`),
  which navigates to `Routes.SEARCH`.
- **Observed:** Work's search icon leaves Work for the global search. The Notes segment has its own, scoped "Search notes" field.
  `WORK_UX.md` §2 says the header search is "scoped to Work".
- **Why it confuses:** two search fields with different scopes on one page, and nothing says which is which.
- **Recommendation:** make the header search open Work-scoped results, with a "Search everything" option. Keep the Notes field as the filter.

### W-07: "Ask about my work" is absent from the Work page (Medium, verified)

- **Where:** `ScopedAskSheet` is opened from the Work home's Pulse card (`ProfessionalHome.kt`, `onAsk`) and from
  `ContextScreen.kt:116,158` (project, person, organisation). `WorkPageScreen.kt` has no Ask entry.
- **Why it blocks:** journey 8 (searching and asking questions). The question-asking feature is on pages the person
  must already be on.
- **Recommendation:** one Ask entry on the Work page (in the Today segment, and in the header menu) that opens the
  scoped sheet with the scope set to "All of Work".

### W-08: Decisions and questions link to the meeting, not the moment (Medium, verified in code)

- **Where:** `WorkAllScreen.kt` (`FindingLine` for decisions: `onClick = { onOpenMeeting(d.meetingId, null) }`; questions
  likewise). Tasks do pass the moment (`onOpenSource` → `onOpenMeeting(it, r.task.startMs)` in `WorkPageScreen.kt`).
- **Why it matters:** the brief requires that decisions keep their links to source material. The person lands at the top of a
  long recording, not at the decision.
- **Open point:** whether a decision row stores a timestamp is not verified. If it does, pass it. If not, the schema needs
  a column, and that needs a migration decision.

### W-09: Custom-type recordings drop out of Needs you (Medium, verified)

- **Where:** `WorkRepository.kt:101–103` `wantsWrapUp` includes `CUSTOM`. `WorkRepository.kt:88` `observeToReview` includes only
  Work types and `GENERAL`.
- **Observed:** a custom recording gets a wrap-up straight after it is recorded. Once that is dismissed, it never returns to
  Needs you, so its follow-ups are easy to lose.
- **Recommendation:** use one predicate for both. Decide whether custom recordings belong in the queue (default: yes).

### W-10: Labels that promise something else (Medium, verified)

- "Use a template" in the Pulse card → `onTemplates = onOpenWork` (`ProfessionalHome.kt`). Templates exist only in the
  record-type sheet (`WorkSheets.kt:119`), not on the Work page.
- "Send" on the glance tile → `onSend = { onOpenWork() }`. It opens the Work root, not the follow-ups list.
- "Work space" section title on the Work home → `WORK`. It is the old name, and the page it opens is the Work page.
- "Open Work" card at the foot of the Work home → `WORK`. The same destination as the Quick Access tile.

### W-11: Vocabulary differs for the same things (Medium, verified)

| Concept | Names in use |
|---|---|
| Tasks I own | "You owe" (`WorkAllScreen` `WorkTab.MINE`) · "My tasks" (Work home) · "Mine" (Tasks segment) |
| Tasks owed to me | "They owe" · "Waiting on" (Work home, Tasks segment) |
| Recordings to review | "Needs you" · "Wrap up" (UI) · "To review" (code comments in `WorkRepository`) |
| Projects | "Projects" (segment) · "Work space" (Work home header) · `terms.project` (customisable) |

A new person learns four names for the same two lists. Pick one set and use it everywhere (§4).

### W-12: The Work home is a long column with competing actions (Medium, verified)

- **Where:** `ProfessionalHome.kt`. From the top: header, Circles card, "Customize home", Quick Access row, hero, five-action
  capture row, five-figure glance tile, Pulse card, Processing bar, Needs you, schedule, My tasks, Waiting on, Projects, People,
  Faith and stories, "Open Work".
- **Observed:** the Pulse card (`PulseCard.kt:73–122`) is a full dark surface in white-on-dark Briefing colours. Its
  `PulseActions` exposes eleven callbacks, among them Ask, Record, Use a template and Inbox, plus per-row Play, Nudge and Prepare. That breaks `WORK_UX.md` §3: "one hero only",
  "no dark full-bleed surfaces anywhere on the page".
- **Why it hurts:** no single first action is obvious, and the screen works against the "What needs me?" question.
- **Recommendation:** one hero, one primary action per context, the Pulse folded into Today's Needs you.

### W-13: No automated navigation tests for Work (Medium, verified)

- The `androidTest` source set has one file and no Work test. `WorkPageScreenshotTest` (20 cases) renders composables only.
  Nothing asserts route targets, back availability or bar visibility. W-01 is the proof: a missing back control passed every test.
- **Recommendation:** add Compose navigation tests (§4, Stage 5) before any Work change, so each fix is guarded.

### W-14: Legacy header and literals in `WorkAllScreen` (Low, verified)

- `WorkAllScreen.kt` draws its own header (26 sp title, back arrow, chip row) and uses `Ink`, `SurfaceBase`, `InkMuted`, `Chip` and
  `fontSize = …sp` literals. `DESIGN_SYSTEM.md` §3 and `WORK_UX.md` §4 require tokens. `ColorLiteralGuardTest` covers colour
  literals only, so it does not catch the size literals.

### W-15: The remembered segment can hide what is due (Low, verified in code, impact is hypothesis)

- The segment is remembered across launches (`PrefsSegmentStore`). A person whose last segment was Tasks lands on Tasks, even when
  a meeting is starting and two recordings need review. Today becomes the default only on first visit.
- **Recommendation:** when the remembered segment is not Today and Needs-you is non-empty, show a one-line banner that links to Today. No automatic switch.

---

## 3. Journeys traced (brief §C)

| # | Journey | Result |
|---|---|---|
| 1 | First entry into Work | Works, but unexplained (W-05). Quick Access tile is the only door on the default home. |
| 2 | First recording → processing → summary → decisions → actions → saved notes | Recording and processing work. Wrap-up appears once (W-09 for custom types). Output reaches Notes and Tasks. |
| 3 | Import audio or write a note | Both in ＋. Works. |
| 4 | Return to an existing meeting | Notes segment and Today's "Recent notes" open the note. Works. |
| 5 | Find a task and update its status | Tasks segment: the checkbox toggles. Works. The Work home's "Task" button does not add (W-03). |
| 6 | Follow a decision or task to its source | Tasks → "From <meeting>" jumps to the moment (works). Decisions go to the meeting only (W-08). |
| 7 | Open a project and see its status | Projects segment → project screen. Works. Status line is in `ContextScreen`, not reviewed in depth. |
| 8 | Search and ask about existing work | Notes search works. Header search is global (W-06). Ask is off the Work page (W-07). |
| 9 | Recover from a processing failure | Not possible from Work (W-04). |

---

## 4. Proposed information architecture

### 4.1 Current vs proposed

| | Current | Proposed |
|---|---|---|
| Destinations | Five doors, two homes, two notes lists | One Work destination; one entry from Home; Notes stays the notes library |
| Work segments | Today · Notes · Tasks · Projects | Same four names. Each has one job. |
| Secondary (⋯) | Nine flat items | Three groups: *Review* (Inbox, Weekly review, Brief) · *Records* (Decisions, Open questions, Risks) · *People & settings* (People & orgs, Work settings) |
| Ask | Work home and project/person pages | Work Today segment and ⋯ → Ask about my work |
| Header search | Global | Work-scoped, with "Search everything" |
| Capture (＋) | Record · Note · Add task · Import | Unchanged. Task opens Tasks with the field focused. |
| Vocabulary | Four names for two lists | **Mine · Waiting on** for tasks; **Needs you** for things to review; **Projects**; **Decisions**, **Open questions**, **Risks** |

### 4.2 Navigation model

- **Primary:** Home, Notes, Work (or Search, per the existing setting), Settings.
- **Secondary, inside Work:** the four segments; the ⋯ groups; the project and person screens.
- **Back:** always a visible arrow when a screen was pushed. The system back returns to the previous screen and first
  closes any open sheet. Work's selection mode clears on back.
- **State:** segment, notes query, filters and per-segment scroll survive navigation, rotation and relaunch (the segment is
  remembered as today). A pending Needs-you item is never hidden by the remembered segment (W-15 banner).

### 4.3 Work home

- Keep the Work home only as a short entry, or retire it (D1). Either way, one hero, one primary action, and no duplicated lists.

---

## 5. Hypotheses that need device or user testing

These are not findings. Each needs a real phone or a user.

- **H-01:** first-time people do not know what Work is for, even after W-05 is fixed. (Test: two people who have never opened it, 10 minutes, no help.)
- **H-02:** people use the Notes tab's "Work" chip and the Work page's Notes segment for the same task and are confused by the two lists.
- **H-03:** the remembered segment causes missed follow-ups in practice (W-15).
- **H-04:** the Pulse card's seven actions are rarely used. Measure taps once the new surface ships; do not add analytics for this (out of scope).
- **H-05:** failed-recording recovery works on a device with a real processing error. The unit test covers the query, not the UI.

---

## 6. Working well: keep

- Keyset paging (30 rows), skeleton rows on first load, retry rows, and "That's everything" at the end of lists.
- The Notes segment's search, chips and sort, with a Clear-filters empty state.
- Tasks: "From <meeting>" opens the moment; Undo on delete; the Mine / Waiting on toggle.
- Empty states with a primary action (Notes, Tasks, Projects).
- The Today logic: default to Today when something needs the person, otherwise Notes.
- ⋯ and ＋ menus reach every destination. Nothing has been removed.

---

## 7. Decisions for the founder

| # | Decision | Recommendation |
|---|---|---|
| D1 | Work home: keep as a short entry, or retire into the Work page's Today segment? | Keep as a short entry: next meeting, needs count, Open Work. Migrate anyone on the old layout. |
| D2 | Header search: Work-scoped with a "Search everything" option, or unchanged? | Work-scoped |
| D3 | Ask: where? | Work Today segment and ⋯ → Ask about my work |
| D4 | Failed recordings in Needs you, with Retry, or only in the Notes list? | Needs you, with Retry and Open |
| D5 | Custom-type recordings: in the review queue? | Yes |
| D6 | Keep "Search" as the default fourth bar slot? | Keep. Work stays one tap from Home. |
| D7 | Retire the Pulse card? | Fold its useful lines into Needs you and Today. Remove its dark surface. |

---

## 8. Expected implementation scope (after approval, Stage 4)

Small, separate increments. Each has its own test.

| Increment | Findings | Files (indicative) | Risk |
|---|---|---|---|
| I-1 Navigation | W-01, W-03, W-10 | `MainActivity.kt` (Work route), `WorkPageScreen.kt`, `ProfessionalHome.kt` | Low |
| I-2 Recovery and queue | W-04, W-09 | `WorkDao.kt`, `WorkRepository.kt`, `WorkPageToday.kt` | Medium: query change, needs a unit test |
| I-3 Search and Ask | W-06, W-07 | `WorkPageScreen.kt`, `ScopedAskSheet.kt` | Low |
| I-4 Work home | W-02, W-12, D1, D7 | `ProfessionalHome.kt`, `PulseCard.kt`, `home/*` | High: largest change; migration for `HomeStyle.PROFESSIONAL` users |
| I-5 Vocabulary and tokens | W-11, W-14, W-05 | `WorkAllScreen.kt`, `WorkPageUi.kt`, string resources | Low |
| I-6 Tests | W-13 and the validation test for each increment | `androidTest/…/work/`, `test/…/work/` | Low |

**Not in scope:** new features, analytics, projects-management, integrations, schema changes beyond what W-08 needs
(to be decided), and changes to Faith, Circles or Personal.

Stage 4 starts with I-1 and I-6 together, so the back and navigation regressions are guarded from the first commit.

---

## 9. Remaining risks

- The design is not validated on a device or with users. §5 lists what must be tested.
- The build and unit tests were not run in this sandbox (no Android SDK). Stage 4 must run them with `agents/TESTING.md`.
- Dark theme for the proposed screens is shown in the artifact only in light mode. The tokens already have dark values (`Tokens.kt`).
- `WorkAllScreen`, `WorkRepository` and `ProfessionalHome` are large. Changes there need focused reviews.
