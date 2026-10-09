# Work page redesign (U-1W + U-3)

Status: **CTO spec**, 2026-10-09, from founder feedback:

- "I don't like the header design nor the dark background."
- "Notes must be easily accessible on the page, with proper loading and pagination."
- "Make the page feel more useful; design it from the user's point of view."

It builds on AUDIT §5, `DESIGN_SYSTEM.md` and the F-2 components. It replaces both `WorkSpaceScreen` and
`ProfessionalHome`'s Work content with **one Work page**.

---

## 1. Who opens this page, and why

A professional opens Work for one of four jobs. The page is ordered by how often each happens:

1. **"What's next, and am I ready?"** The next meeting, prep, and what was promised last time.
2. **"What do I owe?"** Unsent follow-ups, unreviewed recordings, tasks due.
3. **"Find that note."** Recent meeting notes and work notes. This is the founder's priority: **notes are the
   reason people come back.**
4. **"Where does X stand?"** A project or a person. This is occasional, so it lives behind a tab, not on the main
   scroll.

Everything else (decision log, open questions, weekly review, brief, inbox, people, organisations, templates) is
reachable in **one tap**, but never shown on the main scroll unless it needs the user's attention now.

## 2. Layout

```
┌──────────────────────────────────────────────┐
│ Work                         🔍   ＋   ⋯      │  ScreenHeader: plain surface, no gradient,
│ Thursday 9 Oct · 3 meetings · 2 need you     │  one context line (MMType.title + secondary)
├──────────────────────────────────────────────┤
│ [ Today ]  [ Notes ]  [ Tasks ]  [ Projects ] │  SegmentedControl, sticky under the header
└──────────────────────────────────────────────┘
```

**Header (replaces the dark gradient masthead and the intro card):**

- A light `ScreenHeader` on `background`, like every other screen.
- **Title:** "Work".
- **Context line:** date, today's meeting count, and how many items need the user.
- **Actions:** Search (scoped to Work), **＋** (New: Record meeting · Write note · Add task · Import), and **⋯**
  (Inbox · Weekly review · Brief · People & orgs · Decision log · Open questions · Work settings).
- **Inbox badge:** a count on the ⋯ icon only when it is above 0.
- **Removed:**
  - the sky/gradient/"Executive Briefing" masthead
  - the "From conversation to done" intro card. Its one useful line moves to the empty state.
  - the stat tiles, the shortcut pills, the saved-filter row and the template grid. Templates go into the Record
    sheet.

**Segments.** There are four segments and the page remembers the last one. **Today is the default** on a day with
meetings or with items that need the user; otherwise it is **Notes**.

### 2.1 Today

1. **Next up:** `HeroCard`, the only hero on the page.
   - The next meeting: time, title, and who's attending.
   - "Last time: you promised a revised quote · 2 open items", from the existing Pulse/ContextPack data.
   - Actions: **Prepare** (the existing prepare sheet) and **Record**.
   - With no meeting today, the hero becomes the most urgent "needs you" item. When there's nothing at all, the
     section is omitted.
2. **Needs you** (≤ 4 rows, with "All N →"): wrap up a recording, send a follow-up, tasks due today or overdue.
   Each row has one verb button (Review / Send / Done).
3. **Today's schedule:** compact agenda rows. Past meetings collapse into "Earlier today (2)".
4. **Recent notes:** 5 `NoteRow`s, then "All notes →", which switches to the Notes segment.

### 2.2 Notes (founder priority)

- **Search field** pinned at the top of the segment. It searches Work notes through FTS and returns ranked results.
- **Filter chips** (multi-select): Meetings · My notes · Has tasks · Has recording · This week, plus a Project
  chip that opens a picker.
- **Sort:** Recent (default) · Meeting date · Title.
- **List:** `NoteRow` items with:
  - title
  - 2-line preview
  - project dot
  - icons for recording, transcript status and open tasks
  - date
- **Date headers:** Today / Yesterday / This week / Earlier, with month headers after that.
- **Loading and pagination:**
  - **Pagination.** Use keyset pagination on `(sortKey, id)` with a page size of 30, loading the next page when 8
    rows from the end. No OFFSET.
  - **First load.** Show 6 skeleton rows; never a spinner on a blank page.
  - **Appending.** Show a slim footer row, "Loading more…", and "That's everything" at the end.
  - **Errors.** Show an inline retry row, never a dead end.
  - **Lifecycle.** Pull to refresh. Scroll position and filters survive navigation and rotation (`SavedStateHandle`).
  - **No full-list flows.** Each page is a bounded `LIMIT` query.
  - **Data layer.** Use a small `KeysetPager` in `core/notes` (reusable by the main Notes library, U-2) rather than
    adding Paging 3.
- **Row actions:** pin, move to project, share, delete (with undo). Long-press opens multi-select.
- **Empty state:** "Your meeting notes live here. Record a meeting or write a note — we'll keep decisions and tasks
  linked to what was said." Actions: Record, Write note.

### 2.3 Tasks

- **Mine | Waiting on** toggle.
- Grouped by Overdue / Today / This week / Later / No date.
- Each task shows its source meeting (tap to jump to the moment).
- Quick add at the top.
- Paginated with the same pager.

### 2.4 Projects

- A grid or list of projects. Each shows its counts (open tasks, notes, last activity).
- Tap opens the project hub. Only the Overview tab exists while `FEATURE_PROJECT_VIEWS` is off.
- "New project".
- People & orgs are reachable from ⋯, not here.

## 3. Visual rules

- **One hero only:** Next up. No dark full-bleed surfaces anywhere on the page.
- **Colour:** the Work accent from the user's accent setting, used on the hero tint, the primary buttons and the
  selected segment.
- **Tokens only:** DESIGN_SYSTEM §2–§8.
- **Animation:** no idle animation. The sky header's two infinite transitions are deleted.
- **Empty sections** render nothing. Section counts sit in headers; there are no stat tiles.
- **Performance:**
  - Data comes from the S-6 projection, limit and grouped queries.
  - `LazyColumn` keys are stable.
  - No `SELECT *` flows feed this page.

## 4. Acceptance

1. The page answers the four questions within the first viewport on a 6" phone, in the Today and Notes segments.
2. **The Notes segment shows the first note without scrolling.** 1,000 seeded notes load page by page with no jank:
   a recomposition trace, and a unit test for `KeysetPager` covering page boundaries, ties and inserts during
   scroll.
3. **Every destination of the old page** stays reachable in ≤ 2 taps (checklist in the PR).
4. **Screenshot tests:** each segment × Paper/Graphite × empty/populated.
5. **No dark gradient, no colour or size literals.** The F-4 guard tests stay green for the new files.
6. **Removed screens:**
   - `WorkSpaceScreen` is deleted.
   - `ProfessionalHome` keeps only what the Work *home design* (U-1W) needs, built from the same section components.
   - `FEATURE_*` flags are respected.

## 5. Build tasks

| ID | Task | Model | Depends |
|---|---|---|---|
| W-1 | `KeysetPager` + DAO keyset queries for work notes and tasks + tests | Sonnet | S-6 |
| W-2 | New Work page: header, segments, Today, Notes, Tasks, Projects; delete `WorkSpaceScreen`; route changes | Sonnet | F-2, W-1 |
| W-3 | Screenshot tests + the reachability checklist | Haiku | W-2 |
