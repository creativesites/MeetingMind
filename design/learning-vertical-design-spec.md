# MeetingMind Learning: vertical redesign, design specification

**Status:** proposed design for founder approval. **No app code has changed.** Implementation (Phase B) starts only after explicit approval.
**Branch:** `release/1.0-paid-launch`.
**Visual source of truth:** `design/Learning Vertical Redesign.dc.html`. Open it in any browser; no build step or server is needed. Where this document and the design file disagree, the design file wins.
**Product source:** `docs/PLAN_LEARNING.md` (Release 1 scope). **Design system:** `docs/mvp/DESIGN_SYSTEM.md`, `ui/theme/*`, `core/ui/mm/*`.
**Scope note:** Learning is outside the MVP/first-Play-release scope (`agents/PRODUCT.md` → Scope). This is a design exploration, not a request to re-scope 1.0. See §2.4 for a documentation conflict about this.

---

## Contents

1. Design direction
2. Discovery: what exists today
3. Personas and primary use cases
4. Information architecture and navigation
5. Screen inventory
6. Main user journeys
7. Design tokens
8. Component system
9. Interaction states and system feedback
10. Copy rules
11. Accessibility
12. Mapping to the existing architecture
13. Issues found in the current Learning code
14. Assumptions and decisions that need input
15. Proposed implementation sequence
16. UX and design review (deliverable 3)

---

## 1. Design direction

**Thesis:** *Turn what you listen to into knowledge you can understand, recall, apply and retain.*
**Loop:** Capture → Understand → Practise → Explain → Recall later → See mastery improve. (*Explain* is Teach-Back, which is in Release 1.1, so it is not designed here.)

**Character: a calm, editorial study companion.** The design uses MeetingMind's own *paper and ink* system (`DESIGN_SYSTEM.md` §1) with three Learning-specific moves:

1. **The Study space accent, Ocean** (`#0E7490` light / `#5CC8DD` dark), as `DESIGN_SYSTEM.md` §2 already assigns to Study. It appears only on the one hero per screen, citations, the active tab and the "learning in progress" states.
2. **A reading face for study content.** Study-guide prose, definitions, answers and question stems use a serif (Literata), so material to *learn* reads differently from interface chrome. This mirrors Faith's serif-for-scripture rule. It is proposed as one new token (§7.2).
3. **Evidence as a visual motif.** Lecturer quotes sit behind a thin accent rule, with a monospace timestamp chip that plays the moment. Citations are the most frequent "decoration" in the vertical, and they are functional.

**What it avoids:** gradients, glass, neon, stat tiles, readiness rings, streak flames, emoji, badges on everything, and stacks of identical cards. Cards appear only where grouping helps. Lists are hairline-separated rows (§8).

**The test every screen passes:** a student can tell *where they are, what they're looking at, what to do next, and what happens when they do it*, without thinking about the app.

---

## 2. Discovery: what exists today

### 2.1 History

| When | What happened | Source |
|---|---|---|
| ≤ v42 | An earlier Learning vertical shipped tables via `MIGRATION_21_22` (`learning_sessions`, `learning_concepts`, `learning_activities`, `activity_attempts`, `review_schedules`) plus Teach-Back files. | `docs/mvp/AUDIT.md` §7 |
| 2026-10 | **S-7:** that Learning UI, its routes and AI were removed for the MVP; tables kept. "Learning is cut from the MVP and out of the first Play release." | `MVP_PLAN.md` §9, `agents/PRODUCT.md` |
| 2026-11-04 → 11-15 | Learning rebuilt on `release/1.0-paid-launch`: `feature/learning/` (~11,000 lines across 7 files), `LearningRepository`, `ai/learning/*`, routes, `HomeStyle.LEARNING`, Exam Prep, Teach-Back, cram sheets, share-to-Circle. | `git log 24479e7..cb1c2e8`, `agents/STATUS.md` |

The brief said "the current main source tree does not contain a dedicated `feature/learning` package". On **this branch it does**. This redesign therefore *replaces the UI* of an existing implementation and keeps most of its data layer.

### 2.2 Reusable foundations (keep)

- **Entities and DAOs** (`core/database/LearningEntities.kt`, `LearningDaos.kt`): sessions, concepts (with `state`, `evidenceJson`, `isUserEdited`, `isDismissed`, `isStale`), activities (`isDiagnostic`, `rubricJson`, `evidenceJson`), attempts (never overwritten), review schedules (`isPaused`, `snoozedUntil`) and goals.
- **Mastery enum** `LearningMasteryState` NEW / LEARNING / DEVELOPING / STRONG / NEEDS_REVIEW (`LearningModels.kt:12-18`). It already matches the plan. **No percentage is stored.**
- **Evidence model** (`LearningModels.kt:143-155`): `noteId`, `blockId`, `meetingId`, `segmentIds`, `startMs`, `endMs`, `quote`. Every citation in this design can be rendered from it.
- **Scheduler** (`LearningModels.kt:496-575`): fixed 1/3/7/14 days, then ease-based.
- **Courses** are LEARNING-space Notebooks plus `CourseProperties` JSON (`LearningModels.kt:84-90`). There is no new table, as the plan's §8 recommends.
- **Note/transcript substrate:** `NoteEntity`, canonical transcript segments, `RecordingType.LECTURE`, and lecture synthesis in `MeetingProcessingPipeline`.
- `DeterministicSpacedScheduler` / `MasteryCalculator` (`AUDIT.md` §7).

### 2.3 Not Learning

`feature/study/StudyWorkspace.kt` is the **Faith/Bible sermon study pane** (tabs Timeline, Scripture, Transcript, Passage; it uses `ScriptureService`/`BibleStore`) and has no Learning imports. Two touch points to keep in mind: the Faith screen's `onOpenStudyStudio` (`MainActivity.kt:~1300`) opens a Learning session, and `Routes.studyRoute` (`AppNavigation.kt:57`) is named like a Learning route. This design does not touch Faith study.

### 2.4 Documentation conflicts to resolve (not design decisions)

1. `AGENTS.md:38` says "Learning is in production on `release/1.0-paid-launch`". `agents/PRODUCT.md` (Scope) and the brief say Learning is out of the first Play release. They cannot both hold.
2. `AGENTS.md:14` names `claude/meetingmind-mvp-planning-7dgowm` as the designated branch, while `agents/STATUS.md` names `release/1.0-paid-launch` as the shipping branch.
3. `agents/PRODUCT.md` "Learning Vertical (Production)" lists Exam Readiness, cram sheets and Teach-Back as shipped. `PLAN_LEARNING.md` places them in 1.1 and R2.

### 2.5 Existing design system (foundation used)

`MM.colors` (Paper / Midnight Graphite), `MM.type` (display·title·heading·body·bodyStrong·secondary·caption·overline·scripture; Inter + Outfit), `MM.space` 4/8/12/16/24/32, `MM.radius` small 8 · card 16 · pill · sheet, `MM.size.minTouch` 48, `MM.motion`, and the four surfaces Card · Hero · Inset · Sheet. Components live in `core/ui/mm/` (`MMSurfaces`, `MMButtons`, `MMHeaders`, `MMRows`, `MMChoice`, `MMFeedback`, `HomeScaffold`). `LearningFixedColors` in `ui/theme/FixedColors.kt` is a parallel Learning palette that this design **retires** (see §13).

---

## 3. Personas and primary use cases

| Persona | Context | What they need from Learning |
|---|---|---|
| **Thandi, second-year BSc (primary)** | Records 6–10 lectures a week across three concept-heavy courses. Studies between lectures on the bus, late at night, and in bursts before tests. Mid-range Android, patchy campus Wi-Fi. | "What should I do now, and how long will it take?" Quick daily recall, help with the one thing they keep getting wrong, and proof it came from *their* lecture. |
| **Returning-after-a-break student** | Missed four days during a busy week. | To come back without facing a wall of overdue cards or guilt. |
| **Sceptical student** | Has used AI study apps that invented facts. | To see the source for every claim, correct mistakes, and stay in control. |
| **Accessibility needs** | Uses TalkBack, or 130% text, or studies one-handed while commuting. | Every action reachable, legible and unambiguous without colour. |

**Primary use cases (Release 1):** create a session from a lecture → read the study guide → take a short diagnostic → do Daily Recall the next day → strengthen a weak concept → ask a question about the lecture and get quizzed on the answer → see progress by course and concept.

---

## 4. Information architecture and navigation

### 4.1 Decision: three destinations plus a Practice mode

```text
Learning space
├── Learn      (Daily Brief: today's plan, why, continue, momentum)
├── Library    (courses → sessions → sources; search; create)
│     └── Course ─→ Learning Session [Source · Understand · Practise · Mastery] ─→ Concept
├── Progress   (strengths and gaps, courses, history) ─→ Course concepts
│
├── Practice   ← full-screen focus mode, entered from any of the above
│     Daily Recall · Diagnostic · Strengthen a concept · Quiz from Ask · Practice sets
└── Ask Learning ← contextual (session, concept, course), scoped by default
```

`PLAN_LEARNING.md` names four destinations: Learn, Library, Practice, Progress. They all exist here, but **Practice is not a bottom-bar tab**:

- Practice has no content of its own. Its content is *due items* (already on Learn), *a session's sets* (already in the session), and *weak concepts* (already on Learn and Progress). A Practice tab would be a fourth list of the same things, and it would add a decision ("do I review from Learn or from Practice?").
- Practice is a **mode**: full screen, no bar, one task, a clear exit with saved progress. Students enter it from the thing they are looking at, so context is never lost.
- Three tabs fit one-handed thumb reach on small phones better than four.

**Alternative, if the founder prefers the plan literally:** add a fourth tab, "Practice", that lists the same entry points (Daily Recall, by course, by format, weak concepts). The prototype's components support it without change. See decision **D1**.

### 4.2 Where the Learning space sits in the app

Today the global bar (`core/ui/BottomNavigation.kt`) appears on Home, and `HomeStyle.LEARNING` renders `LearnHomeHost` as the app's Home (`MainActivity.kt:543`). A space bar *inside* that home would put two bottom bars on screen. Proposal:

- **Learning is a space** (like Faith and Work today: a pushed destination with its own 3-item bar and a "Learning ▾" space chip top-left to switch or leave).
- **When Learning is the chosen Home style**, Home uses the shared `HomeScaffold` with the Study hero "Today's plan" (`DESIGN_SYSTEM.md` §9: Study hero = "Revise today") plus Continue. "Open Learning" enters the space. Home never hosts the space's bar.

This is decision **D2**.

### 4.3 Inside a Learning Session

- One workspace, four tabs, **ordered like the loop**: Source · Understand · Practise · Mastery. **Understand is the default** because it holds the most useful information on first open. Source is first in the order because *source before synthesis* is the rule, not because students start there.
- A **Next step** strip sits above the tabs and is always one tap away. It changes with evidence: diagnostic → strengthen weakest concept → try a new concept.
- The tab row is sticky. A breadcrumb ("Human Biochemistry ›") keeps the course one tap away without a second back press.
- Any citation, anywhere, opens a sheet with the quote and audio. "Open in transcript" lands on **Source** with the segment highlighted, so the student never loses their place.

### 4.4 Back behaviour and context preservation

- System back always returns to the previous screen. Tabs reset the stack (Android convention).
- Leaving Practice part-way asks once ("Stop here? Your answers are saved") and offers **Resume** on Learn (`3 of 8 done · pick up where you stopped`).
- Opening a concept from a diagnostic summary *replaces* the summary, so back goes to where practice was launched, not into a finished quiz.
- Finishing a session's study guide from the plan ticks the plan item when the student leaves the session.

---

## 5. Screen inventory

Ids match the design file (`…dc.html#3a`). Every frame is live.

| Id | Screen / state | Notes |
|---|---|---|
| 0 | Walkthrough | One live phone, six journeys, condition switches (dark, 1.3× text, offline, done). |
| 1a | Learn · start of day | Hero "About 14 minutes" with a 3-item plan and one primary button. |
| 1b | Learn · partway | Plan ticks, minutes count down, button advances. |
| 1c | Learn · done for today | What changed, tomorrow's size, optional extras. |
| 1d | Learn · first run (empty) | Uses the lecture recorded today; Record is secondary. |
| 1e | Learn · offline | Neutral status line. |
| 1f | Learn · large text 1.3× | Text-size personalisation, largest step. |
| 2a | Library | Search, filter chips, courses, recent sessions, "Not in Learning yet". |
| 2b | Library · search | Concepts, lecture moments and sessions. |
| 2c | Course detail | Next action, understanding counts, gaps, topics, sessions. |
| 2d | New session sheet | Recording · Note · Type · Record now. |
| 2e | Create · course | Suggested course, title, "Not now", privacy line. |
| 2f | Create · building | Staged progress, audio-quality warning, leave and carry on. |
| 3a | Session · Understand | Summary, emphasis, concepts, relationships, outline, "Quiz me". |
| 3b | Session · Source | Player, transcript quality, highlighted cited moment. |
| 3c | Session · Practise | Recommended, diagnostic result, sets, history. |
| 3d | Session · Mastery | States with reasons, due reviews, how states work. |
| 3e | Session · just created | Readable guide, questions still generating (static skeleton). |
| 3f | Concept detail | State and reason, definition, explanation, evidence, related, history. |
| 3g | Concept · edit/report | Rename, edit definition, merge, hide, report. |
| 3h | Concept · uncertain source | Held-back questions and "Check the transcript". |
| 4a | Diagnostic · intro | 6 questions, ~4 min, not graded. |
| 4b–4d | Multiple choice · selected / not quite / correct | Answers hidden until submission. |
| 4e–4f | Short answer · writing / compare | Self-check against key points, with suggested ticks. |
| 4g | Diagnostic · summary | Where you're starting; first reviews tomorrow. |
| 4h | Diagnostic · answers | Read-only review with sources. |
| 5a–5c | Daily Recall · front / revealed / saved confirmation | Deliberate reveal, three ratings with next-review labels. |
| 5d | Recall complete | Counts, what changed, what didn't and why. |
| 5e | Stop mid-way | Saved; resume from Learn. |
| 5f | Strengthen · summary | State moved; what moves it further. |
| 6a–6c | Progress · course concepts · how states work | No percentages anywhere. |
| 7a–7d | Ask · cited / not in lecture / uncertain source / offline | Scoped, cited, honest, Quiz me. |
| 8a–8f | Dark appearance | Learn, Session, Recall, Feedback, Done, Progress. |
| 9 | Design language board | Tokens, states, components. |

---

## 6. Main user journeys

Each journey is runnable from the walkthrough (turn 0).

### J1 · First-time student: lecture → session → diagnostic
- **Intent:** "I recorded a lecture; help me study it."
- **Entry:** Learn first run (1d), Library "Not in Learning yet", or New session sheet (2d).
- **Steps:** Use a recording → pick Lecture 07 → course pre-selected (Human Biochemistry) → **Create session** → building (stages) → **Open session** → Next step "Take the diagnostic" → intro → 6 questions → summary → study guide.
- **Outcome:** Concepts move from New to Learning or Needs review *based on answers*. Learn now says "Your first reviews arrive tomorrow".
- **Potential confusion:** "Is this a test?" The intro says it isn't graded. "Where did my session go?" Learn's Continue and Library show it immediately.
- **Failure and recovery:** generation fails → the transcript and note are untouched; the guide shows "Couldn't build questions. Try again" (`StatusLine` with retry); the study guide stays readable if only question generation failed. Unclear audio → a warning in building (2f) and on Source; questions from that span are held back.
- **Empty and loading:** 1d (empty), 2f (staged progress), 3e (guide readable while questions generate).

### J2 · Returning student, daily (the core habit)
- **Intent:** "What should I do today?"
- **Entry:** Learn (1a), or the Home hero when Learning is the home style.
- **Steps:** **Start Daily Recall** → recall → **Show answer** → rate (Forgot / Hesitated / Knew it, each labelled with its next review) → ×8 → summary (what changed) → **Back to Learn** → plan shows 1 of 3 done → **Strengthen competitive inhibition** → 3 questions → summary → Finish study guide → **Done for today**.
- **Outcome:** about 14 minutes, explicit completion, tomorrow previewed.
- **Potential confusion:** "Do I have to do all of it?" "Only have 5 minutes?" trims to the five most useful cards, and the rest keep their place. Interrupted → resume.
- **Failure and recovery:** offline → everything works (local); a rating is saved locally before advancing; "Skip for today" defers with Undo.

### J3 · Why is this concept weak?
- **Entry:** Learn "Worth your attention" card, Mastery, Progress gap, or search.
- **Steps:** Concept (3f) → reason line ("Two incorrect answers in Tuesday's diagnostic…") → **From the lecture** quote → play 24:12 or **Show in transcript** (Source, highlighted) → back → **Practise this concept**.
- **Outcome:** the student understands *why* it's recommended and acts on it.

### J4 · Ask, then Quiz me
- **Entry:** session app bar, concept app bar, course "Ask".
- **Steps:** answer with numbered citations → tap ① for the quote and moment → follow-up "How is this different from non-competitive?" → follow-up "Lineweaver–Burk?" (answer flags the unclear source) → follow-up "What drugs…?" ("Not covered in Lecture 07", then optional labelled general knowledge) → **Quiz me on this** (2 questions).
- **Outcome:** a question becomes retrieval practice; trust is preserved because nothing unsourced is presented as lecture fact.

### J5 · Organise (Library → Course → Session → Source)
Library → Human Biochemistry → understanding counts and gaps → Lecture 07 → Source → tap a segment to play.

### J6 · Progress
Progress → "Worth practising" → Practise; course row → course concepts (filter by state) → **How states work**.

### Walkthrough findings (found by walking the prototype; all fixed in it)
Both journeys were walked as a first-time and as a returning student, with scripted end-to-end runs and full-size renders of every key frame.

| Found | Fix |
|---|---|
| At 1.3× text, "Start Daily Recall" fell below the fold on Learn. | At large text, the date overline and the hero's explanatory sentence are hidden (progressive disclosure), so the primary action stays on the first screen. |
| The session's Practise tab repeated the Next-step strip directly above it. | Removed the duplicate "Recommended" card; the strip is the single recommendation. |
| The "just created" state said the guide was readable while showing skeleton text. | The guide renders at once; only the Practise tab shows a static skeleton while questions are written. |
| Concept buttons always said "3 questions", and Lineweaver–Burk fell back to a question from another concept. | Counts come from the concept's own grounded questions; held-back questions are stated under the button. |
| The diagnostic summary listed competitive inhibition twice (Q1 and Q6). | One row per concept, listing each question's outcome. |
| The recall summary always claimed "Km stays Developing", even when Km wasn't recalled. | Shown only when it is true. |
| After a diagnostic launched from a session, "See your study guide" left a duplicate session on the back stack, so Back seemed to do nothing. | Finishing practice now replaces the launching screen instead of stacking a copy. |
| Citations and Ask follow-ups opened at the top, away from the cited moment or latest answer. | Source scrolls to the highlighted segment; Ask scrolls to the newest exchange. |
| Course legend on Progress omitted the Learning count. | All five states are listed. |
| A decorative clock icon in the plan hero had no purpose. | Removed. |

---

## 7. Design tokens

All values are dp/sp. The **existing** token is named in `code`. ◆ marks a **proposed** addition or change, documented here rather than applied silently to shared components.

### 7.1 Colour

| Role | Light | Dark | Token |
|---|---|---|---|
| Background | `#FAFAF9` | `#0C111B` | `MM.colors.background` |
| Surface / raised | `#FFFFFF` | `#121A27` / `#192434` | `surface` / `surfaceRaised` |
| Sunk (inset) | `#F5F5F4` | `#080D15` | `surfaceSunk` |
| Track | `#EDEDEB` | `#202C3C` | `track` |
| Ink / secondary | `#18181B` / `#52525B` | `#F0F3F8` / `#B7C0CF` | `ink` / `inkSecondary` |
| Muted text | ◆ `#6B6B73` (5.06:1) | `#8995A8` | `inkMuted` (currently `#8B8B93`, **3.24:1 on Paper: fails AA** for 12–13sp captions) |
| Faint (decorative only) | `#C8C8CD` | `#536176` | `inkFaint` |
| Lines | `#E5E5E3` · `#EDEDEB` · `#F3F3F1` | `#2A3749` · `#202C3A` · `#172231` | `line` / `lineSoft` / `lineFaint` |
| Accent (Study · Ocean) | `#0E7490` (5.13:1 on bg) | `#5CC8DD` (9.68:1) | `accent` via `MMAccent.Ocean` |
| Accent wash | Ocean 9% | Ocean 13% | `accentWash` (DESIGN_SYSTEM says 10%/15%; 9%/13% keeps body ink2 at 6.5:1 on the hero) |
| ◆ Paper (study surfaces) | `#FFFDF8` | `#141D2B` | new `paper`: flashcards, definition panel, "Ready to test yourself" |
| Success (Strong, correct) | ◆ `#15803D` (5.02:1) | `#4ADE80` | `success` is `#16A34A` = **3.30:1: fails AA as text** |
| ◆ Clay (Needs review, not quite) | `#B45309` (5.02:1) | `#F0A35E` | new semantic, or `warning` darkened (`#D97706` = 3.19:1) |

**Rules:** one hero (accent wash) per screen. Clay means "attention", never "error"; Learning never uses `danger` for a wrong answer. Semantic colours never follow the user's accent.

### 7.2 Type

| Token | Spec | Use in Learning |
|---|---|---|
| `display` | Outfit 600 30/36 | Greeting, completion headlines. One per screen. |
| `title` | Inter 600 22/28 | Screen and session titles. |
| `heading` | Inter 600 17/24 | Section headers, card titles. |
| `body` / `bodyStrong` | Inter 15/22 400 / 500 | Default text / row titles. |
| `secondary` | Inter 13/18 | Supporting lines. |
| `caption` | Inter 500 12/16 | Metadata, reasons under states. |
| `overline` | Inter 600 11/14 +0.6 caps | Section labels inside content ("SUMMARY", "FROM THE LECTURE"). |
| ◆ `reading` | Literata 400 17/28 | Study-guide prose, definitions, answers, Ask answers. |
| ◆ `prompt` | Literata 500 22/30 | Question stems and flashcard fronts. |
| mono | system mono 500 11.5 | Timestamps only. |

Literata is the face `DESIGN_SYSTEM.md` §3 already names ("Literata later"). Until it is bundled, `FontFamily.Serif` is an acceptable fallback, as `scripture` already uses it. All sizes multiply by `MMTextScale` and respect the system font scale.

### 7.3 Spacing, shape, elevation, motion

- **Spacing:** `MM.space` only. Learning content uses a **20dp gutter** (between `l` 16 and `xl` 24) for reading comfort. ◆ If the founder prefers strict 16, everything still works; this is decision **D6**.
- **Radii:** `small` 8 (inset, chips inside cards), `card` 16 (cards, hero, paper, sheet tops), `pill` (buttons, filter chips). Answer options and rating buttons use 14, a ◆ exception for touch rows inside a 16 card; or use 16.
- **Elevation:** flat. Only the bottom bar, sheets, snackbars and the practice footer get the shared shadow. Cards use a `line` border in light and the surface step in dark.
- **Motion:** `quick` 150 for presses and selection, `standard` 250 for reveal, feedback and tab content, `enter` 300 for sheets. **No idle or infinite animation**: skeletons are static, and "searching" uses a still dot plus live-region text. Reduced motion → no slide or reveal translation.

---

## 8. Component system

Map to `core/ui/mm/` first; new components go in `feature/learning/components/`.

| Component | Built from | Notes |
|---|---|---|
| `LearningSpaceBar` | `BottomNavigation` pattern | Learn · Library · Progress; the selected item is filled with an accent-wash pill. |
| `SpaceChip` | `MMChip` | "Learning ▾"; opens the space switcher. |
| `TodayPlanHero` | `HeroCard` | Overline, minutes headline (display face 26), reason line, 1–3 `PlanRow`s, one `PrimaryButton`, "Only have 5 minutes?" `TextAction`. |
| `PlanRow` | `ListRow` | Number or check, title, sub, minutes. A done row gets strike + muted. |
| `MasteryGlyph` / `MasteryLabel` | new, 16dp `Canvas` | Shape + fill level + label: ○ New, ◔ Learning, ◑ Developing, ● ✓ Strong, ◇ ! Needs review. `contentDescription = "Learning state: Needs review"`. |
| `MasteryBar` + `MasteryLegend` | new | Segment widths = **counts**; the legend always shows numbers. |
| `ConceptRow` | `ListRow` | Glyph, name (+ "emphasised"), definition (2 lines) or reason (caption). |
| `CitationChip` | new | Mono timestamp + play glyph, accent wash, ≥48dp touch area via padding. Opens `CitationSheet`. |
| `CitationNumber` | new | ①-style inline marker in Ask answers; selected = filled. |
| `EvidenceQuote` | new | 3dp accent rule, Literata italic 15/24, then `CitationChip` and "Show in transcript". |
| `NextStepStrip` | `HeroCard` (compact) | Session header; whole strip is one button. |
| `SessionTabs` | `SegmentedControl` (underline variant) | Sticky; due dot on Practise with a text alternative. |
| `TranscriptSegment` | existing transcript row | Timestamp gutter, highlight on cite, wavy underline + reason for unclear spans. |
| `PracticeScaffold` | new | Close, title/counter (live region), overflow; segmented progress; content; sticky footer action. |
| `AnswerOption` | `MMChoice` | States: idle, selected (ring + filled letter), correct (check + "Correct answer"), chosen-wrong (× + "Your answer"), dimmed. |
| `FeedbackPanel` | `StatusLine` variant | "That's right" / "Not quite" / "Compare with the lecture" + explanation + evidence. |
| `SelfCheckList` | `MMChoice` checkbox | Key points with suggested ticks; learner decides. |
| `RecallCard` | `paper` surface | Source line, prompt face, "Show answer" button, answer reveal. |
| `RecallRating` | new | Three 72dp buttons: icon + label + next-review text. |
| `CompletionSummary` | `HomeScaffold` sections | Headline, counts, what changed (glyph + reason), what didn't, next. |
| `StatusLine` | existing | Offline, transcript quality, held-back questions, generation stages. |
| `EmptyState` | existing | First run with illustration and three-step "How it works". |
| `ProgressStages` | new | Done / now / waiting rows with live region. |

**Rules carried from the system:** section headers carry a count and one "All" action; empty sections return nothing; every tappable thing ≥48dp; one hero per screen.

---

## 9. Interaction states and system feedback

| Action | Immediate feedback | Success | Failure / recovery |
|---|---|---|---|
| Create session | Navigates to staged progress | "Study guide ready" snackbar; session opens on Understand | Retry in `StatusLine`; source untouched; guide still readable if only questions failed |
| Select an answer | Ring + filled letter (150 ms), `aria-checked` | "Check answer" enables | — |
| Submit an answer | Options lock; feedback panel reveals (250 ms) and is announced | "That's right" + why + source | "Not quite": why the chosen answer doesn't fit, the reasoning, the lecture quote. Never "Wrong". |
| Short answer | "Check my answer" enables when text exists | Model answer + key points with suggested ticks | "Not sure — show me" is a respectable path; it counts as "not yet" |
| Reveal card | Answer slides in; focus moves to it | Ratings appear with next-review labels | — |
| Rate card | Saved locally first, then a quiet "Saved — next review in 3 days" line on the next card | Advances | Stored offline; syncs nothing (local-first) |
| Skip / defer | Card removed | Snackbar "Moved to tomorrow. No penalty." + Undo | Undo restores position |
| Stop practice | Confirm sheet (only if ≥1 answer) | "Saved. Pick up from card 4 on Learn." | — |
| Rename / edit concept | Sheet closes | Snackbar + Undo; "renamed by you" provenance; kept on regeneration | — |
| Hide / report concept | Snackbar + Undo | Hidden from practice / questions paused | Undo |
| Ask a question | Still "Searching Lecture 07…" line (live region) | Cited answer | Not in source → says so, cites closest, offers labelled general knowledge on request; unclear source → warning above the answer; offline → composer disabled with reason |
| Network loss | `StatusLine` on Learn, Library, Ask | Practice, reviews and guides keep working | Generation and Ask queue or use on-device model |
| Pause course or session | Snackbar | Reviews stop appearing on Learn | Resume from the same menu |

---

## 10. Copy rules

- Speak to the student in second person, warmly and briefly. No exclamation marks in feedback.
- Time-aware greeting ("Good morning" at 9:30); never assume morning.
- **Never:** "Wrong!", "You failed", "Overdue", "Streak lost", "You'll pass", "% ready", "Score".
- **Always:** give the reason for a recommendation, say "not graded" before a diagnostic, and say "no penalty" when deferring.
- Name the lecturer and lecture in evidence ("Dr Mensah stressed this at 24:12") so the source feels personal and checkable.
- Prefer verbs on buttons that name the experience: "Start Daily Recall", "Strengthen competitive inhibition", "Quiz me".

---

## 11. Accessibility

- **Not colour alone:** states use shape + fill + label. Answers use icon + text label ("Correct answer", "Your answer"). Due dots carry a text alternative. Unclear transcript spans use a wavy underline plus a reason.
- **Contrast:** every text pair ≥4.5:1 with the ◆ colour proposals (§7.1). The current `inkMuted`, `success` and `warning` fail as small text. This is a **proposed global fix**, not applied to shared code.
- **TalkBack:** practice title and counter are a polite live region ("Question 3 of 6"). Feedback panels are announced. The reveal moves focus to the answer. Options are radio semantics; self-check rows are checkboxes; tabs are `Role.Tab` with selected state. Citation chips read "Source: Lecture 07 at 24:12. Opens the lecture moment". Mastery glyphs read their state.
- **Touch:** every target ≥48dp (rating buttons 72dp; answer options ≥56dp; primary practice actions 48–56dp, in the thumb zone at the bottom).
- **Large text:** frame 1f and the walkthrough switch show 1.3×. Titles wrap; nothing uses fixed heights for text; plan rows grow. Test also at 200% system font scale (DESIGN_SYSTEM §10).
- **Reduced motion:** no translation; instant state changes. No idle animation anywhere.
- **One-handed and small screens:** the primary action of every practice screen is a full-width bottom button. Tabs and the bar are reachable. Nothing important is in the top corners except Close/Back.
- **Interrupted sessions:** every answer is persisted on submit; resume from Learn.

---

## 12. Mapping to the existing architecture

### 12.1 Routes (adapt, don't multiply)

| Proposed | Existing | Change |
|---|---|---|
| `learn` (space host with Learn/Library/Progress) | `learn` → `LearnHomeScreen` with `LearnTab {TODAY, COURSES, PRACTICE, EXAM}` | Replace the segmented `LearnTab` with the 3-item space bar. EXAM is removed from R1 UI. |
| `learn/course/{notebookId}` | Courses are an inline drawer in `LearnHomeScreen` | New screen; data from `observeCourses` / `CourseProperties`. |
| `learning_session/{noteId}?tab=` | `learning_session/{noteId}`, `SessionTab {UNDERSTAND, CONCEPTS, PRACTICE, DIAGNOSTICS}` | Tabs become SOURCE, UNDERSTAND, PRACTISE, MASTERY. CONCEPTS folds into Understand; DIAGNOSTICS ("Signals") becomes Mastery. |
| `learn/concept/{conceptId}` | none (`ConceptCard` inline) | New screen. |
| `learn_practice?mode=&sessionId=&conceptId=&activityId=` | `learn_practice?sessionId&activityId` | Add `mode` (recall, diagnostic, concept, quiz, set). Remove the `PracticeArenaFilter` mode bar from R1. |
| Ask Learning | `AssistantSheet` via `LibraryAssistantViewModel.forLearningNote` | Keep the engine; render answers with `CitationNumber` + evidence; add scope chip and Quiz me. **Verify** the assistant returns segment ids (not verified). |
| `learn/create/*` | `CreateCourseDialog`, `createCourseFromNotes` | Becomes a 3-step flow (source → course → build). |
| `learn_exam` | Exam Prep | **Hidden in R1** (R2). Keep the code behind a flag or remove it from navigation. |

### 12.2 Data

- **Concept state shown = `LearningConceptEntity.state`**, recomputed from attempts by `MasteryCalculator`. Show `MasteryEvaluation.reason` (exists, `LearningModels.kt:604+`, currently unused) as the reason line.
- **Citations = `evidenceJson`** → `startMs` → `mm:ss` chip; `quote` → `EvidenceQuote`; `segmentIds` → Source highlight.
- **Ratings:** three buttons → `selfRating` AGAIN / HARD / GOOD. The R1 design drops EASY to keep the decision simple; the scheduler maps GOOD to the next fixed step. Interval labels come from the real scheduler (fix `previewInterval`, §13 #9).
- **Hide from practice** = `isDismissed`. **Report** sets `isStale` (activities withheld) plus a report record, ◆ to confirm whether a reason field is needed. **Pause course** = `CourseProperties.paused` (JSON, **no schema change**). **Pause session reviews** = `ReviewScheduleEntity.isPaused`. **Skip for today** = `snoozedUntil`.
- **Diagnostic** = activities with `isDiagnostic = true` (created today but never surfaced).
- **Unclear audio** = transcript confidence and review flags from the existing review queue (`recording-page-implementation.md` review queue). Activities whose evidence falls in an unresolved low-confidence span are withheld.
- **No schema migration is required for the R1 design.** If a report-reason field is wanted, that is the only candidate.

---

## 13. Issues found in the current Learning code

Read-only audit of `release/1.0-paid-launch` at `cb1c2e8`. Items marked ✔ were re-verified by reading the code. The others come from the audit sweep and need a second look before fixing. Paths are under `app/src/main/java/com/craftflowtechnologies/meetingmind/`.

### High: conflicts with product rules or trust
1. ✔ **Percentages and readiness scores shown as mastery.** `ExamPrepScreen.kt:~515-542` "X% Ready" (weights 100/60/20); `LearningSessionScreen.kt:~2078-2097` "N% concept readiness" (100/55/15); also `LearnHomeScreen.kt:2216` "Score: N", and teach-back and accuracy % in `PracticeArenaScreen.kt:1107, 1589`. Violates `PLAN_LEARNING.md` §5.3.
2. ✔ **Progress shown without evidence.** `LearnHomeScreen.kt:598-601`: the hero ring is 100% when any session or course exists and no reviews are due. `LearnHomeScreen.kt:1577-1583`: course progress has a floor of 12% or 15% with no attempts. Violates "mastery is earned, not inferred from exposure".
3. ✔ **No citations in the UI.** Evidence is stored (`evidenceJson`), but nothing in `feature/learning/` renders it. R1 items 2 and 4 are unmet.
4. ✔ **Answer visible before an attempt.** "Peek Answer" (`showAnswerPeek`) in `LearningSessionScreen.kt:~1988` reveals `expectedAnswer` on the session screen.
5. ✔ **The offline hint fallback leaks the answer.** `ai/learning/LearningAiEngine.kt:~658-666` puts `expectedAnswer.take(40)` into hints 2 and 3, silently.
6. **Release 1.1 and Release 2 features in the R1 UI:** Teach-Back studio (`LearnHomeScreen.kt:~1988`), `evaluateTeachBack` (`LearnViewModel.kt:~872`), auto-created TEACH_BACK activities (`LearningRepository.kt:~1365`), `LearnTab.EXAM`, cram sheets.
7. **The diagnostic is never surfaced.** `isDiagnostic` activities are created but no UI lists a 5–8 question diagnostic.
8. ✔ **Errors are swallowed.** `LearnViewModel.sessionLoadError` (`:113-114`) is never read by any screen; `onRetry` in `LearningSessionScreen` is never invoked; several `catch` blocks only log.

### Medium
9. `previewInterval` (`LearningModels.kt:~567-587`) disagrees with the scheduler ("<1d" vs 1 day) and has no callers.
10. `observeDailyBrief` captures `now` once (`LearningRepository.kt:~656`), so the due queue goes stale while the screen stays open.
11. `recordAttempt` uses the just-answered activity's schedule for the concept's overdue check (`LearningRepository.kt:~600-630`).
12. Practice stores "Correct self-assessment" / "Needs review" as `userResponse` (`PracticeArenaScreen.kt:~170`), so mastery is driven only by self-report.
13. `MasteryEvaluation.reason` exists but is never shown.
14. Accessibility: clickable `Text` used as buttons without a role or 48dp target (`LearningSessionScreen.kt:~2040-2057`); about 50 `contentDescription = null` icons to check (mostly decorative).
15. Hard-coded colours outside the theme: `LearningVisuals.kt:~1092-1340` (`android.graphics.Color.rgb`), not caught by `DesignSystemGuardTest`. `LearningFixedColors` is a parallel palette.
16. Glyphs in text instead of icons: "✓", "☑", "☐" (`ExamPrepScreen.kt:~1106`, `PracticeArenaScreen.kt:~780`, `LearningSessionScreen.kt:~1382, ~1953`).
17. ✔ Duplicate `testTag("quick_sprint_pill_due")` at `LearnHomeScreen.kt:900` and `:961`.

### Low
18. Dead code: `previewInterval`, `TeachBackState`, unused activity types `JOURNAL_ENTRY` and `CODE_TRACE`, the unused `onRetry` parameter.
19. The UI reaches into `viewModel.learningRepo` directly (layering).
20. No `stringResource` in `feature/learning`; all copy is inline.
21. Copy: "Drill Missed Cards"; streak reset logic (`LearnViewModel.kt:~789-799`) conflicts with "no punitive pressure".
22. A stale comment in `core/identity/IdentityPicker.kt:56` says LEARNING is hidden, but `OfferedNotebookSpaces = NotebookSpace.entries` shows it.

### Design-system issues (global; proposed, not applied)
23. `inkMuted #8B8B93` is 3.24:1 on Paper and 3.38:1 on white, below AA for the 12–13sp captions it is used for app-wide.
24. `success #16A34A` (3.30:1) and `warning #D97706` (3.19:1) fail AA when used as text.

---

## 14. Assumptions and decisions that need input

| # | Decision | Recommendation |
|---|---|---|
| D1 | Practice as a **mode** (3 tabs) or as a **4th tab** | Mode (§4.1). |
| D2 | How the Learning space coexists with the global bar and `HomeStyle.LEARNING` | Space with its own bar; Home shows a Study hero that enters it (§4.2). |
| D3 | What happens to the shipped Exam Prep, Teach-Back, cram sheets and share-to-Circle | Hide from R1 navigation behind a flag; keep code for 1.1 and R2. |
| D4 | Reading face: bundle **Literata** or use the system serif | Bundle Literata (one family, two weights). |
| D5 | Global contrast fixes for `inkMuted`, `success`, `warning` | Apply globally (separate, small change with its own screenshot run). |
| D6 | 20dp content gutter in Learning vs. 16dp system gutter | 20dp for reading screens only. |
| D7 | Rating scale: 3 buttons (design) vs. 4 (SM-2 AGAIN/HARD/GOOD/EASY) | 3 for R1. |
| D8 | Reminders | Off by default; opt-in quiet daily nudge; no streaks. |
| D9 | Short-answer marking: learner self-check with suggested ticks (design) vs. AI grade | Self-check in R1; AI rubric arrives with Teach-Back (1.1). |
| D10 | "General knowledge" answers in Ask | Allowed only on explicit request, always labelled, never cited as lecture. |
| D11 | Resolve the documentation conflicts in §2.4 | Founder to confirm Learning's release status. |

**Assumptions:** lecture transcripts carry segment timings (true for recorded lectures; typed sessions cite paragraphs instead). Courses remain Notebooks. On-device models can generate guides offline on capable devices; otherwise generation waits. Time estimates come from a per-format average (e.g. ~50 s per card), shown rounded.

---

## 15. Proposed implementation sequence (Phase B, after approval)

Each step ends with unit tests for its rules, Roborazzi screenshots (light, dark, 1.3×) and a remote build per `AGENTS.md` (no local Gradle).

1. **Foundations.** `MasteryGlyph`/`MasteryBar`/`CitationChip`/`EvidenceQuote`/`StatusLine` uses; `reading` and `prompt` type tokens; retire `LearningFixedColors` from new code. *(Optional, separate PR: D5 contrast fixes.)*
2. **Trust fixes in the current code** (no new UI): remove percentages and floors (#1, #2), Peek Answer (#4), the hint leak (#5); wire `sessionLoadError` and retry (#8); duplicate testTag (#17).
3. **Practice scaffold:** `PracticeScaffold`, `AnswerOption`, `FeedbackPanel` with evidence, `RecallCard` + `RecallRating`, `SelfCheckList`, completion summaries, stop/resume, skip. Route `mode=`.
4. **Learning Session:** the four tabs, `NextStepStrip`, Source with highlight and transcript quality, Mastery with `MasteryEvaluation.reason`, Concept detail and edit/report sheet.
5. **Diagnostic:** surface `isDiagnostic` activities (intro → 6 questions → summary).
6. **Learn Daily Brief:** bounded plan from `observeDailyBrief` (fix the stale `now`, #10), 5-minute version, done state, momentum without streaks.
7. **Library, course, create flow, search.**
8. **Progress.**
9. **Ask Learning:** cited rendering, scope chip, not-in-source and uncertain states, Quiz me.
10. **Navigation:** the space bar and Home integration (D2); hide Exam and Teach-Back (D3).
11. **Device pass:** the `PLAN_LEARNING.md` §2 gate scenario on a real device, offline, TalkBack, 200% font.

---

## 16. UX and design review (deliverable 3)

### The five most important UX decisions
1. **A bounded daily plan with one button and a real finish line.** It turns "what should I do?" into one tap and ends guilt. The 5-minute version and Skip-for-today mean nothing ever becomes a backlog.
2. **Practice is a mode, not a place.** Students practise from the thing in front of them, so context is preserved and there is one fewer navigation decision.
3. **The session is one workspace with four tabs ordered like the loop**, plus a Next-step strip. Students never hunt for "what now" inside a lecture.
4. **Evidence is first-class and visible.** Every concept, answer and recommendation carries a timestamped, playable source. Uncertain audio is flagged and its questions held back. Ask states when something isn't in the lecture.
5. **Qualitative, explained states.** Five states with shape + label + reason replace percentages and readiness scores. A state changes only through demonstrated recall, never through reading.

### Navigation and cognitive load
- Three bar items. One hero per screen. One primary button per screen.
- Progressive disclosure: one evidence quote, then "1 more moment"; five concepts, then "Show all 9"; edit and report behind a pencil; course admin (pause, reset) in the overflow.
- Breadcrumbs inside sessions and concepts avoid back-stack hunting. Citations deep-link to Source and highlight the segment.
- Practice screens show only the task: no bar, a progress segment row and a full-width action at the bottom.

### Accessibility and small screens
Covered in §11. Specifically checked in the prototype: 1.3× text on Learn (1f and the walkthrough switch), dark mode on every key screen (turn 8), offline on Learn, Library and Ask, and no colour-only signals.

### Usability risks
| Risk | Mitigation in the design | What to test |
|---|---|---|
| Students want summaries, not quizzes (plan L0 risk) | The guide is readable first; the quiz is offered, not forced | L0 interviews: do they take the diagnostic? |
| Self-assessment is optimistic | Ratings are honest-sounding and labelled; diagnostics and question sets supply objective evidence | Compare self-ratings with question accuracy |
| Three-tab Practice-as-mode is less discoverable | Practice entry points on every screen; the Learn hero is always a practice action | Can a student "do flashcards for Cell Biology" unaided? |
| Long concept names or formulas overflow | Wrapping titles; formula block in its own inset | 200% font scale with real course data |
| Generated content is wrong | Evidence on every item, edit/report everywhere, held-back unclear spans | Seeded-error test: do students spot and fix it? |
| Notifications become pressure | Off by default; no streaks | Diary study over 2 weeks |

### Deliberately excluded from Release 1
Teach-Back recording and rubric, Study With Me, question-mode taxonomy, "Explain my mistake" as a separate tool (1.1). Exam dates, daily exam plans, mock exams, readiness or performance breakdowns, cram sheets (R2). Knowledge Map, cross-source sessions, audio study briefs, learner profile (R3). Social study, leaderboards, sharing to Circles, streaks, marketplace, teacher/LMS features (not planned).

### Walkthrough performed
Both scenarios (first-time and returning) were walked end to end, by hand in the walkthrough phone and with scripted runs of journeys J1–J4: create → diagnostic → back stack; recall → strengthen → study guide → done; concept → transcript → back; Ask → citation → follow-ups → Quiz me → close. Also checked: rename with Undo, hide, the 5-minute plan, resume after stopping, and offline Ask. All 47 canvas frames render without script errors, in light, dark and 1.3× text. Ten issues were found and fixed; see the end of §6. **Not verified:** rendering in the user's own browser with Google Fonts loaded (reviewed with system-font fallbacks); real-device behaviour, which is Phase B.

---

## 17. Phase B status (approved 2026-11-16)

The founder approved the design as specified, so D1–D10 follow the recommendations in §14. D11 (Learning's release status) is still open.

| Step (§15) | State | Where |
|---|---|---|
| 1 Foundations | Done. Literata is not bundled yet; the system serif stands in (D4). | `ui/theme/LearningTheme.kt`, `feature/learning/components/` |
| 2 Trust fixes | Done for every surface still routed: no percentages or floors, no answer before an attempt, hints never quote the answer, errors shown with Retry. The old screens that held #1, #2, #4 and #17 are no longer routed. | `LearningAiEngine.kt`, `LearnViewModel.kt` |
| 3 Practice scaffold | Done: recall with three ratings, choice with labelled results, short answer with self-check, skip with Undo, stop and resume, summary. | `space/PracticeModeScreen.kt` |
| 4 Learning Session | Done: four tabs, Next step, Source with highlight and unclear-audio marking, Mastery with reasons, Concept detail with rename, hide and report. | `space/SessionStudioScreen.kt` |
| 5 Diagnostic | Done: intro, 5–8 questions, summary that splits "worth strengthening" from "looks solid". | `LearningRules.diagnosticSet` |
| 6 Daily Brief | Done: bounded plan, five-minute version, done state, week without streaks; brief refreshes while open (#10). | `space/LearningSpaceScreen.kt` |
| 7 Library, course, search | Done, except the 3-step create flow (Record and Write still open the existing flows). | `space/LearningSpaceScreen.kt` |
| 8 Progress | Done. | `space/LearningSpaceScreen.kt` |
| 9 Ask Learning | Not started: the shared `AssistantSheet` is still used, scoped to the session. | — |
| 10 Navigation | Done: space bar, Home integration (D2), Exam and Teach-Back hidden from the space (D3). | `MainActivity.kt` |
| 11 Device pass | Not done. | — |

Known gaps: the citation sheet opens the recording at the moment rather than playing in place; reminders (D8) are not built; the old `LearnHomeScreen`, `LearningSessionScreen` and `PracticeArenaScreen` files remain until a device pass confirms the new screens.
