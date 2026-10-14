# MeetingMind Learning: product and delivery plan

**Status:** proposed plan  
**Depends on:** the Note/Notebook foundation, canonical transcripts, cited AI extraction, and
local-first processing.  
**Relationship to existing direction:** this is the planned Phase 5 Learning vertical from
`docs/PRODUCT_DIRECTION.md`. It extends the existing **Learning** notebook space and **Lecture**
recording type; it does not introduce a parallel note, workflow, or AI architecture.

---

## 0. The decision

> **MeetingMind Learning turns what you listen to into knowledge you can understand, recall,
> apply, and retain.**

The product is not an AI flashcard generator, a generic chatbot, or an LMS. Its advantage is
that MeetingMind starts with material the learner actually encountered: a recorded lecture,
conversation, voice note, source document, or existing note. The product then carries that
material through a persistent learning loop:

```text
Capture → Understand → Practise → Explain → Recall later → See mastery improve
```

The first-class object is a **Learning Session**: the learner's durable study record for one
source or coherent topic. A session may be backed by a lecture recording, a note, imported
material, or a combination. It is not a new competing content model: it is a Learning-scoped
view over a `NoteEntity` plus learning state and evidence-linked activities.

### Product thesis

Most AI study tools produce assets on demand. MeetingMind should instead remember what a person
has learned, surface what is due, and adapt the next activity to demonstrated understanding. The
learner should feel that the app knows the difference between *having read something* and *being
able to explain or apply it*.

### Launch audience

Start with **university and tertiary students who record lectures**, especially students managing
several concept-heavy courses. The product can later serve professional development, books,
podcasts, sermons, and personal curiosity without changing its model, but those are not v1
validation targets.

### North stars

| Moment | Product promise | Evidence of success |
| --- | --- | --- |
| After a lecture | “I know what mattered and can begin studying immediately.” | A learner completes their first generated activity from a captured lecture. |
| Daily | “I know exactly what is worth reviewing today.” | Learners return for Daily Recall on separate days, not only when recording. |
| Before an exam | “I know where I am weak and what to do next.” | Learners complete targeted practice after seeing a gap, rather than merely viewing a score. |

The primary v1 activation is: **record/import a lecture → open its Learning Session → answer a
short diagnostic → complete the next review on another day.**

---

## 1. Experience model

### 1.1 The Learning Session

Every Learning Session answers four questions without making the learner hunt through tools.

| Area | Learner question | Contents |
| --- | --- | --- |
| **Source** | What did this come from? | Recording, transcript, note, attachments, timestamps and citations. |
| **Understand** | What does this mean? | Study guide, key concepts, definitions, relationships, lecturer emphasis, cited tutor. |
| **Practise** | Can I retrieve and use it? | Diagnostic, recall questions, flashcards, concept challenges, teach-back. |
| **Mastery** | What should I do next? | Per-concept confidence, due reviews, gaps, activity history and next action. |

An example session:

> **Biochemistry — Enzyme kinetics**  
> Lecture · 1h 14m · 12 concepts · next review tomorrow  
> *Developing: explain inhibition; practise rate-law application.*

The initial session should be generated after the existing lecture transcript/intelligence pass is
ready. It must always remain editable: concepts can be renamed, merged, hidden, or added; a
learner can flag a generated question as wrong or unhelpful.

### 1.2 The daily home: Learn

Learning has four primary destinations, not a tab for each feature:

| Destination | Job |
| --- | --- |
| **Learn** | Daily Brief: due recall, a short continuation, one high-value weak concept, and momentum. |
| **Library** | Courses, subjects, learning sessions, and original sources. |
| **Practice** | Recall, quizzes, teach-back, and later mock exams. |
| **Progress** | Mastery by topic, gaps, review history, and goals. |

The default home is a bounded recommendation, not an infinite feed:

> You have **14 minutes** of useful learning today: six due reviews, two weak concepts, and one
> session to continue.

The app should never create a punitive backlog. It prioritises the highest-value reviews, gives a
clear “done for today” state, and lets the learner defer an activity.

### 1.3 The signature interaction: Teach-Back

Teach-Back uses MeetingMind's voice-first advantage. The prompt is specific and bounded:

> Explain competitive inhibition in your own words. Aim for 60 seconds.

The learner records an explanation. MeetingMind compares it with a cited concept rubric, then
reports *what was covered, what was missing, and one next question*. It must not present a
spurious percentage as fact. v1 uses clear qualitative states — **covered**, **partly covered**,
**not yet demonstrated** — with the original lecture moments available as evidence.

Teach-Back is an activity type, not a separate social feature or an always-listening mode.

---

## 2. Scope and sequencing

### Release 1: the learning loop

Release 1 is the smallest version that can prove retention rather than content generation.

1. **Create a Learning Session** from an existing Lecture note/recording, and create a typed
   session in the Learning space.
2. **Extract a cited study guide**: concepts, definitions, emphasis, and a concise outline.
3. **Run a short diagnostic** (five to eight questions) and let the learner correct generated
   content.
4. **Provide source-grounded practice**: recall questions and flashcards, each linked to its
   concept and supporting note/transcript segments.
5. **Schedule reviews locally** and show a Daily Recall queue.
6. **Track per-concept learning evidence** and present a transparent “what next” recommendation.
7. **Offer source-grounded Ask Learning**, with an explicit “Quiz me” handoff from answers.

The gate is not a finished dashboard. It is this real-device scenario:

> A student records a real lecture, corrects any transcript issue, completes a diagnostic, returns
> the next day for a due review, and can see why one concept is being recommended.

### Release 1.1: deepen active learning

- Teach-Back recording, assessment rubric, and retry flow.
- Question modes that distinguish definition, explanation, and application.
- “Explain my mistake,” grounded in the learner's answer, the expected reasoning, and source
  evidence.
- Study With Me: a short, structured session of understand → recall → application → teach-back.
- A deliberately simple gap summary: for example, *strong on definitions; practise application*.

### Release 2: exam readiness

- Exam date and topic weighting entered by the learner.
- A time-bounded daily plan that combines due recall and exam priorities.
- Timed, source-grounded mock exams.
- Performance breakdown by recall, conceptual understanding, and application — only when enough
  activity evidence exists to justify that distinction.

### Release 3: synthesis and adaptation

- Knowledge Map across sessions and courses, with nodes reflecting evidence-backed mastery.
- Cross-source Learning Sessions (slides, PDFs, notes, recordings, web imports only where rights
  and product policy allow).
- Audio study briefs using the learner's weak concepts.
- A learner profile based on observed preferences and outcomes, always inspectable, editable and
  optional.

---

## 3. What we deliberately will not build first

The following are useful only after the learning loop has retention evidence:

- Social study feeds, leaderboards, public flashcard marketplaces, and collaborative classrooms.
- A teacher LMS, grading, roster management, or course sales.
- A generic web-answer chatbot disconnected from the learner's sources.
- Automatic high-stakes claims such as “you will pass” or a definitive knowledge score.
- A decorative knowledge graph without meaningful links to sources, activities, and next actions.
- Massive card generation or hundreds of learning preferences.

Flashcards are one useful recall format, not the product's centre of gravity.

---

## 4. Product rules

1. **Source before synthesis.** Every AI-generated concept, answer, question, feedback item, and
   mastery recommendation is traceable to a note/source block or transcript segment. If there is
   insufficient evidence, MeetingMind says so.
2. **Retrieval before rereading.** The default action after understanding is a small attempt to
   recall or apply, not another summary.
3. **Activities teach; scores inform.** A score is never an end state. Each outcome leads to a
   targeted next action, explanation, or review.
4. **Mastery is earned, not inferred from exposure.** Listening to a lecture or opening a card
   cannot increase mastery on its own. Correct recall, explanation, and application supply the
   evidence.
5. **The learner stays in control.** Generated material is editable, deletable, and reportable.
   Learners may pause a course, snooze reviews, and reset their learning history.
6. **Local-first and private by default.** Follow existing AI routing and storage rules. Voice
   Teach-Back recordings remain device-local unless a future, explicit opt-in changes that.
7. **Honest uncertainty.** Transcript quality and model confidence limit what Learning can claim.
   Do not conceal an uncertain source behind a polished quiz.

---

## 5. Data and architecture plan

### 5.1 Reuse before adding

The current architecture already supplies much of the substrate:

- `NoteEntity`, `NotebookEntity`, blocks, attachments, tags, and the `Learning` space.
- `MeetingEntity` as a recording source, including canonical transcript segments and timestamps.
- `RecordingType.LECTURE` and its lecture-specific intelligence profile.
- Existing cited extraction, local storage, model routing, search, and audio playback.

Learning must use those objects. It should not fork the transcript, make a separate AI pipeline,
or duplicate source text into flashcard rows.

### 5.2 New durable objects

The implementation names may vary, but the relationships should remain this simple:

```text
Note / Lecture source
        │
LearningSession ── LearningConcept ──► evidence (note blocks / transcript segments)
        │                    │
        │                    └── LearningActivity ── ActivityAttempt
        │
        └── ReviewSchedule (computed next due activity)
```

| Object | Minimum responsibility |
| --- | --- |
| `LearningSession` | Links a Learning note to its course/subject, state, source scope, and last studied time. A session can exist without a recording. |
| `LearningConcept` | Canonical concept text, optional definition, session/course ownership, learner edits, and evidence links. Concepts are reusable across sessions only after an explicit merge/link decision. |
| `LearningActivity` | An immutable prompt/activity version: recall card, multiple-choice question, free response, application question, or teach-back. Stores expected answer/rubric, difficulty, concept links, and evidence. |
| `ActivityAttempt` | What the learner answered or recorded, correctness/review state, self-correction, feedback, and timestamp. Original attempts are never overwritten. |
| `ReviewSchedule` | Current due time, interval, ease/priority inputs, suspension state and reason. Derived scheduling variables are kept separate from content. |
| `LearningGoal` (Release 2) | Exam/event date, selected courses/topics, weighting, and learner-entered availability. |

**Evidence model.** Reuse the repository's cited source representation rather than a new text-copy
field: an activity and concept reference `noteId`/block ids and/or meeting segment ids with time
bounds and a short quotation. Any activity whose source evidence disappears or changes becomes
stale and is withheld until regenerated or confirmed.

### 5.3 Mastery is a transparent view, not a magic field

Do not store one opaque “mastery percent” as truth. Compute a concept state from attempts and
time since successful retrieval, then show the factors in plain language.

Initial v1 state:

| State | Meaning |
| --- | --- |
| **New** | Captured or extracted but not yet tested. |
| **Learning** | Some correct evidence, but an early review is due or an outcome was mixed. |
| **Developing** | Recalled successfully across more than one session. |
| **Strong** | Sustained successful recall/application over time. |
| **Needs review** | An overdue or unsuccessful attempt makes this the next best action. |

v1 scheduling should be a documented, deterministic spaced-retrieval algorithm with conservative
intervals (for example: same day → 1 day → 3 days → 7 days → 14 days). Do not claim personal
adaptation until outcomes show it. Capture enough event data to tune the schedule later without
migrating learner history.

### 5.4 AI contracts

Each generation is a typed, validated output with citations:

| Contract | Required output |
| --- | --- |
| `LearningStudyGuide` | Concepts, definitions, emphasis markers, relationships, and evidence. |
| `LearningActivitySet` | Small set of questions/cards, expected responses/rubrics, concept ids, difficulty, evidence. |
| `TeachBackFeedback` | Covered claims, missing/misunderstood claims, one next prompt, evidence; no unsupported numerical grade. |
| `MistakeExplanation` | Learner answer, correct reasoning, misconception, cited source, and next question. |

Validate source ids, ensure question answers do not leak in user-visible prompts, reject
duplicate/near-duplicate activities, and keep an explicit model/version field for regeneration.
Ask Learning uses retrieval restricted to the session/course scope by default; it may widen only
after the learner asks it to.

---

## 6. Delivery plan

Milestones are capability gates, not calendar promises. Every milestone ends with unit coverage
for the new rules and a hands-on run using a real lecture.

### L0 — validate the learner and the source (discovery)

- Recruit 5–8 students in the launch segment.
- Collect consented real lectures across at least three subjects and lengths.
- Observe their present workflow: capture, notes, test preparation, and review.
- Test the prototype question: after receiving a cited study guide, would they choose a short
  diagnostic now and return tomorrow?
- Define transcript quality and source-grounding acceptance thresholds before measuring learning
  outcomes.

**Exit:** a clear, evidence-backed first persona and a validated single-lecture workflow. If
students only want summaries, revisit the activation before building scheduling.

### L1 — foundation and session shell

- Add migrations and DAOs for sessions, concepts, activities, attempts, and reviews.
- Create Learning Session from a Lecture and create a typed Learning note.
- Build the Source/Understand view with cited concept extraction and corrections.
- Implement a small activity renderer; no broad quiz builder yet.

**Exit:** a recorded lecture can become an editable session with concepts that jump to evidence.

### L2 — diagnostic, recall, and Daily Brief

- Generate and validate five-to-eight-question diagnostics.
- Persist attempts and compute transparent concept states.
- Implement deterministic review scheduling, Daily Recall, snooze, and course/session pause.
- Add telemetry for activation and return behaviour, respecting the existing privacy policy.

**Exit:** the end-to-end v1 scenario in §2 works offline after the initial generation, including
the next-day review.

### L3 — tutor and correction loop

- Add scoped Ask Learning with citations and “Quiz me” conversion.
- Add wrong-answer explanation and learner feedback controls.
- Test adversarial material: ambiguous lectures, transcript errors, unsupported claims, and
  questions with multiple defensible answers.

**Exit:** no answer or correction flow presents unsupported material as lecture fact.

### L4 — Teach-Back and Study With Me (1.1)

- Record, transcribe, retain, and delete voice explanations locally.
- Evaluate against a cited rubric; show qualitative feedback and a retry.
- Compose short study sessions from due reviews and one appropriate challenge.

**Exit:** users can explain a concept aloud and receive useful, evidence-linked feedback without
feeling graded by a black box.

### L5 — exam readiness and synthesis (Release 2+)

- Add goals, priority-aware review planning, mock exams, then the Knowledge Map.
- Measure whether gap recommendations change study choices and improve later attempts.

**Exit:** exam plans and maps only ship when their recommendations are supported by sufficient
attempt evidence.

---

## 7. Measurement and quality bar

### Product metrics

| Layer | Measure |
| --- | --- |
| Activation | % of eligible lecture sessions where a learner completes a diagnostic within 24 hours. |
| Retention | % of activated learners completing a due review on a second and seventh distinct day. |
| Learning loop | Correctness improvement for a concept between first attempt and a later spaced attempt. Report cohort-level patterns, not promises to individuals. |
| Recommendation value | % of weak-concept recommendations that lead to a practice action. |
| Trust | Edit, dismiss, and report rates for generated concepts/questions; sampled source-grounding accuracy. |
| Friction | Time from processed lecture to first useful activity; session completion and abandonment. |

### Quality acceptance

- Every generated activity passes schema validation and has valid evidence links.
- A learner can open the cited source from every answer explanation and concept.
- Generated questions are reviewed against a representative real-lecture test set for answer
correctness, ambiguity, and source support before release.
- The user can edit/delete learning content and pause scheduling without data loss.
- Network loss after an already-created session does not prevent recall, attempt recording, or
review scheduling.
- Accessibility: answers and feedback work with TalkBack, large text, keyboard navigation, and
without colour as the only correctness cue.

---

## 8. Risks and decisions to settle before L1

| Risk / open decision | Why it matters | Proposed handling |
| --- | --- | --- |
| Transcript mistakes become false learning content | A polished wrong question destroys trust. | Surface source evidence, allow correction, and do not generate from low-confidence/edited-unresolved sections. |
| AI question quality varies by subject | Application questions in quantitative subjects need more than generic prompts. | Launch with a constrained supported-subject test set; label unsupported question types; collect feedback. |
| “Mastery” overpromises | Users may treat a number as an exam prediction. | Use transparent qualitative states in v1 and explain their evidence. |
| Notifications become pressure | A learning app can create guilt rather than a habit. | Start with in-app Daily Brief; make reminders opt-in, quiet, and easily paused. |
| Local model latency/cost | Multiple generations after every lecture could be slow or expensive. | Generate the study guide first; generate a small activity set on demand/background queue; cache typed outputs. |
| Course structure | A single session is useful, but courses need organisation. | Reuse notebooks/tags for v1; add a thin Course object only when cross-session review proves necessary. |
| Privacy of spoken answers | Teach-Back can contain sensitive information. | Store on device; clear retention controls; no cloud upload by default. |

---

## 9. One-line positioning and launch message

**Positioning:** *Don’t just capture what you learn. Make it stick.*

**Launch promise:** *Record a lecture, understand what mattered, practise it now, and get the
right review when it is time to remember it.*
