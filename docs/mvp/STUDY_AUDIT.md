# Study (Learning) audit and redesign proposal

Status: proposal, 2026-11-27. Input for V-1 (`docs/mvp/STUDY_V1.md`, not yet written). Learning is still out of the MVP
(founder, 2026-10-11). Nothing here changes that. It describes what to build when Study is scheduled.

---

## 1. Where Learning stands today

| Area | State | Evidence |
|---|---|---|
| UI | **None.** Learn home, the session page, practice, Study With Me and Ask Learning were removed in S-7. | `b35595b` (2026-10-09) |
| Entry points | The Learning space is hidden in the identity picker and the recording picker. `LECTURE` survives only if already chosen. | `core/identity/IdentityPicker.kt:56`, `core/ui/RecordingContextPicker.kt:48` |
| Data | The five tables (`learning_sessions`, `learning_concepts`, `learning_activities`, `activity_attempts`, `review_schedules`) are on every install. | `core/database/LearningEntities.kt`, `LearningDaos.kt` |
| Logic kept | `DeterministicSpacedScheduler` and `MasteryCalculator`, both tested. | `core/model/LearningModels.kt:266`, `:374` |
| Companion hooks | Ready but unused: `CompanionEvent.QuizQuestion` (lean-in), `CURIOUS` and `PROUD` states, `MomentSpace.STUDY` Unveil with sparkle bars, `CreateSource.STUDY_WIN` (Celebratory card). | `core/companion/*`, `core/ui/mm/companion/moment/ResolvedMoment.kt:109` |
| Spec | `STUDY_V1.md` (V-1) was never written, so U-1S (Study home) is blocked. | `docs/mvp/MVP_PLAN.md:173`, `:202` |
| Naming clash | The note tool called **Study guide** makes a church small-group discussion guide for WhatsApp, not a lecture study guide. | `ai/notes/StudyGuideFormatter.kt`, `feature/notes/ai/NoteAiSheets.kt:287` |

So the job is a rebuild, not a polish. The parts worth keeping are the data model, the scheduler, the plan's
principles (`docs/PLAN_LEARNING.md` §4) and the companion hooks.

## 2. What the removed UI got wrong (do not repeat)

Read from `b35595b^:app/src/main/java/.../feature/learning/`.

1. **The lesson was not the page.** `LearningSessionScreen` (1,152 lines) opened on four Material tabs: Understand,
   Practise, Source, Mastery. The lecture itself sat behind "Open Source Note / Transcript". A student had to pick a
   tool before seeing what was taught.
2. **System words on screen.** "STALE CITATION", "EVIDENCE & CITATION INTEGRITY", "TRANSPARENT MASTERY MODEL",
   `Status: ${session.status}` (a raw enum), "Interval: 3d", and an "Emphasis (HIGH, MEDIUM)" free-text field.
3. **Off the design system.** A private `LearningTeal`, colour literals for green, red, orange and blue, raw `sp` sizes,
   all-caps labels. The audit listed it as a worst offender (`docs/mvp/AUDIT.md` §3).
4. **Correct and wrong shown by colour alone** (`CorrectGreen` / `IncorrectRed`). This breaks the plan's own
   accessibility rule.
5. **CRUD in dialogs.** Edit concept, Add concept and Regenerate were each an `AlertDialog` with text fields.
6. **Practice had extra taps and little teaching.** Multiple choice was choose → "Submit Answer" → "Next Question".
   Recall used four ease ratings. A wrong answer showed the right one, without the lecturer's words.
7. **No companion, no Read to Me, no moments.** It felt like a different app.

## 3. Proposed shape

Four surfaces, not a tab per feature:

| Surface | Job | Replaces |
|---|---|---|
| **Study home** (U-1S) | Today's round, continue a lesson, courses | Learn home |
| **Lesson** | The lecture, made readable and explorable | Session tabs Understand + Source |
| **Practice round** | 5 to 10 items, about 5 minutes, one per screen | Practise tab, `ActivityPracticeScreen` |
| **Course** | A notebook per course: lessons, weak spots, exam date later | Library |

Mastery stops being a tab. It becomes one plain line per concept ("Solid", "Getting there", "New", "Worth a look"),
shown where the concept appears.

### 3.1 Lesson page: the lesson is the page

Built on the existing note editor and transcript, not a parallel screen.

```
┌──────────────────────────────────────────┐
│ ←  BIO 201 · Lecture 7          ⋯        │
│                                          │
│ Enzyme kinetics                    (Zuri)│  ← serif-free title; Zuri 40 dp, Curious
│ Tue 12 Nov · 1 h 14 min · 12 ideas       │
│                                          │
│ ┌ In 30 seconds ───────────────────────┐ │
│ │ Three sentences. Tap ▶ to hear it.   │ │
│ └──────────────────────────────────────┘ │
│                                          │
│ What your lecturer stressed              │
│  ▶ 14:02 "This will be on the exam…"     │  ← plays the original audio from there
│  ▶ 41:30 Competitive vs non-competitive  │
│                                          │
│ 1  Michaelis–Menten                      │
│    Formatted note text with key terms    │
│    underlined. Tap a term → concept sheet│
│    ┌ Quick check ──────────────────────┐ │
│    │ What does Km tell you?  [Reveal]  │ │  ← one folded recall per section
│    └───────────────────────────────────┘ │
│ 2  Inhibition …                          │
│                                          │
├──────────────────────────────────────────┤
│  ▶ Listen      [ Practice · 8 ]    Ask   │  ← sticky; Practice is the one strong button
└──────────────────────────────────────────┘
```

- **Concept sheet** (bottom sheet, not a dialog): the definition, the lecturer's own words with a play button, then
  *Explain it another way*, *Give me an example*, *Quiz me on this*. Editing is inline in the sheet; "Not right?"
  is a quiet text action.
- **Selection tools** (V-1 list): Go deeper, Simpler, Example, Diagram. Results land as note blocks, clearly marked as
  AI.
- **Listen** uses Read to Me, or "Play the lecture" synced to the text (P-1 and P-2 already cover the sync).
- **Honesty line** when the transcript is weak: "Parts of this recording were hard to hear. Questions skip them."
  This replaces "STALE CITATION".
- Product rules hold: experience first (Practice is prominent, "Write…" stays quiet); every AI claim opens its
  source.

### 3.2 Practice round

```
┌──────────────────────────────────────────┐
│ ✕   ●●●○○○○○                   (Zuri)    │  ← progress dots; Zuri leans in each question
│                                          │
│ Which inhibitor raises the apparent Km   │
│ but leaves Vmax the same?                │
│                                          │
│  ○ Competitive                           │  ← tap = answer. No Submit button.
│  ○ Non-competitive                       │
│  ○ Uncompetitive                         │
│  ○ Mixed                                 │
│                                          │
│ ── after a wrong tap ──────────────────  │
│  ✓ Competitive — correct answer          │  ← icon + word, not colour alone
│  ✗ Non-competitive — your answer         │
│  Why: it competes for the active site…   │
│  ▶ Your lecturer, 41:30 (8 s)            │
│               [ Continue ]               │
└──────────────────────────────────────────┘
```

- **Item types, in order of build:** multiple choice; recall card (think → reveal → *Missed it* / *Got it* /
  *Easy*, mapped onto `RecallRating`); fill the blank; *Explain it aloud* (Teach-Back, release 1.1).
- **A wrong item comes back once later in the same round**, then follows the scheduler. Note: the plan says a failed
  item is due the same day; `DeterministicSpacedScheduler` currently sets 1 day. Align them in V-1.
- **End of round:** "6 of 8 first try. 3 come back tomorrow." Then one next step: *Revisit inhibition* (opens that
  lesson section) or *Done for today*. Never an infinite queue; never a punishing backlog.
- **No streak counters that reset.** Use rhythm: "You studied 4 days this week" (Z-21).

### 3.3 Study home

Top to bottom: header with Zuri and one line; **Today's round** card ("8 cards · about 5 min", one button); **Continue**
(the last lesson, at the section you left); **Courses** (notebook tiles with "2 worth a look"); **Recent lectures**.
When nothing is due: "All caught up. Nice work." and the round card turns into *Practise anyway*.

### 3.4 Look and feel

- Use only `MM.colors`, `MM.type` and `core/ui` components. Add anything new to `DESIGN_SYSTEM.md` first. No
  `LearningTeal`.
- Study "gets focus" (`DESIGN_SYSTEM.md` §1): sans titles, generous line height for note text, one accent. Suggest
  **Ocean** as the Study default accent, the way Gold is Faith's, still overridable by the user's accent.
- One memorable element per screen: on the lesson it is *What your lecturer stressed*; in practice it is the question.
- Correctness by icon and word as well as colour; TalkBack reads "Correct" / "Not quite" first.
- Sheets, not dialogs. Sentence case, no all-caps labels. Plain words: "Worth a look", not "Needs review state".

## 4. Companion experiences

All of these use the existing API (`ZuriSlot`, `Companion`, `MomentStage`, `CompanionEvent`). They follow
`ZURI_EXPERIENCE.md`: Study's mood is curious, Zuri is never sad at a wrong answer, never covers the user's words,
and presence Off means off.

| Moment | Companion | Where it hooks in |
|---|---|---|
| Study home greeting | 56 dp T1, `CURIOUS`. "3 cards are due. A quick round?" / `IDLE` "All caught up." | `ZuriSlot` in the home header (Z-9) |
| Recording a lecture | `LISTENING`, small, beside the timer | Recording screen, as today |
| Processing | Study captions in the Processing Theater: "Finding what your lecturer stressed", "Writing your quick checks" | `ProcessingCompanion.kt`, Study variant (ZURI_EXPERIENCE §ProcessingStage) |
| Lesson ready | Unveil in `MomentSpace.STUDY` (sparkle bars already exist) | `NoteMomentHost` / `MomentStage` |
| Reading aloud | `READING` while Listen plays | Lesson bottom bar |
| Each question | `QuizQuestion` event → lean-in (`CURIOUS` one-shot) | Practice round |
| Correct | `NOD` one-shot (600 ms), light haptic | Practice round |
| Wrong | Stays calm (`IDLE`). No `WORRIED`, no sad face | Practice round |
| Round complete | `PROUD` (1,200 ms). `CELEBRATING` only for milestones, e.g. all due cards cleared or a first full study week | End-of-round screen |
| Study win card | Offer a Create card from `STUDY_WIN` (Celebratory). Companion on the card follows the user's setting | End-of-round screen |
| Explain it aloud | `LISTENING` while the student talks, `THINKING` while it is assessed, then "covered / partly / not yet" with lecture quotes | Teach-Back (1.1) |
| Ask Zuri about this lecture | Answers only from this lecture, with timestamps, and offers "Quiz me on this" | ZURI_EXPERIENCE "Quiz me (Study)" |

**Code gaps to close:** `CompanionPage` has no Study or practice entry, so the presence filter cannot tell them apart.
Add `STUDY_HOME` and `PRACTICE` (append only) and decide their behaviour under *Moments only*. Confirm `CURIOUS`,
`PROUD` and `READING` render: they are reserved "Phase B" states and nobody has seen them on a device.

## 5. Cleanup and decisions for V-1

1. **Rename the note tool "Study guide" to "Discussion guide"** (its real job), so "study guide" can mean the lecture
   one.
2. **Reuse the five Learning tables** instead of dropping them in V-3. They already match the plan's model (session,
   concept, activity, attempt, schedule). If V-1 changes them, migrate forward rather than drop and recreate.
3. **Align the failed-item interval** with the plan (same day), with a scheduler test.
4. **Unhide the Learning space and `LECTURE`/`RESEARCH`** only when the lesson page ships.

## 6. Suggested build order (when Study is scheduled)

| Step | What | Why this order |
|---|---|---|
| 1 | Write `STUDY_V1.md` from this audit (V-1, founder sign-off) | Unblocks U-1S and settles §5 |
| 2 | Lesson page on the note editor: summary, stressed moments with audio, concept sheet, Listen | Useful on its own, with no scheduling |
| 3 | Practice round: multiple choice and recall, scheduler, end of round | The core loop |
| 4 | Study home and courses | Needs 2 and 3 to have content |
| 5 | Companion placements from §4 and the Study win card | Needs the surfaces to exist |
| 6 | Explain it aloud (Teach-Back), Ask Zuri scoped to a lecture | Release 1.1 |
| 7 | Exam date, mock exams | Release 2 |

Gate for steps 2 and 3, from the plan: a real lecture recorded on a device, its lesson page reviewed, a round done,
and a due card come back the next day.
