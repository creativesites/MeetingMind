# MeetingMind Professional: plan

The Professional vertical, planned for the best experience first and the feature list second. It
also covers a Work Home and the professional additions to the app's personalisation.

Written against schema version 14 (v30). It follows the rules in `docs/PLAN_V1.md` §9 and
`docs/PRODUCT_DIRECTION.md` §7, and builds on the identity and Today hub from `docs/PLAN_V2.md`
F0–F1.

---

## 0. Summary

| Question | Answer |
| --- | --- |
| Generalised or focused? | **One engine for every kind of professional, with a focused launch.** The engine works for anyone with people, conversations and commitments. Defaults, templates, onboarding and marketing are aimed first at **people who do client work**: consultants, agencies, freelancers, and founders selling to businesses. |
| What is it? | The layer between conversations and work. **Capture → Understand → Structure → Act → Produce → Remember.** |
| What is it not? | Not a CRM, not a project manager, not an email client. No pipelines, forecasting, Gantt charts, invoicing or HR. |
| The north star | *Within five minutes of a meeting ending, the person has sent their follow-up and trusts that nothing was dropped, having typed almost nothing.* |
| The most important screen | **The Wrap-up** (§4.3): a 60-second review after processing that files the meeting, confirms what was extracted and sends the follow-up. |
| Connectors in the first release | Device calendar (exists), **device contacts** (new) and the **Android share sheet** for Gmail, Outlook, Slack and WhatsApp. No OAuth yet. |
| Home | A **Work Home**. It is the same Today hub arranged for work, not a separate app (§7). |

### Why the launch is focused on client work

- They feel the problem most. They have outside people, many projects at once, and promises that
  cost money when they're missed.
- Relationship memory is worth the most to them, because they rarely remember what was said to
  whom three weeks ago.
- They pay for tools, and each follow-up they send shows MeetingMind's output to another
  professional.

Everyone else gets the product through **work profiles** (§8.1). Each profile is configuration:
its own words, templates, Home order and recipes. A profile never changes the code path.

---

## 1. Principles

These decide the arguments. Each one can be checked in review.

1. **Moments, not modules.** Design for the five moments of a professional conversation (§3).
   The pillars are a checklist, not a navigation structure.
2. **No required fields, ever.** Every property is inferred from the calendar, contacts,
   attendees or transcript, and appears as a suggestion the person confirms with one tap. Typing
   is always allowed and never required.
3. **Four nouns.** People see **My tasks, Waiting on, Decisions and Open questions**. Actions,
   commitments, follow-ups and reminders are the same object seen from different angles
   (§5.2). The data layer can be richer than the UI; the UI stays this simple.
4. **Evidence is never overwritten.** Transcript text is evidence. Notes, items and outputs are
   derived from it and cite it. Tapping any AI-written sentence plays the moment it came from.
5. **Show little, keep everything.** Extraction stores every signal it finds. The UI shows the
   top three to five per kind, and the rest can be reached through Ask and filters. Review
   fatigue kills trust faster than a missed item.
6. **Value on the first day.** Every feature that only pays off after ten meetings (relationship
   memory, decision history) is seeded from the calendar and contacts. It is never the first
   thing a new user sees.
7. **Correct once, fixed everywhere.** Renaming a speaker, merging a person, editing a decision or
   reassigning a task updates every place that shows it, plus search and future vocabulary.
8. **Private by default, with rules that do something.** "Confidential" is a behaviour, not a
   label: confidential material never leaves the phone (§6.4).
9. **Phone first.** Anything that needs a big screen (Gantt, visual automation builders, heavy
   document editing) is out of scope or deferred.
10. **The existing rules still hold.**
    - A vertical adds configuration, not tables or type-branching UI.
    - Everything extracted cites its source.
    - Nothing needs a model to be usable.

---

## 2. The positioning in one line

> **MeetingMind remembers the work, understands the context, and helps move it forward.**

Every professional, whether consultant, founder, salesperson, journalist, manager or engineer,
shares the same raw material:

- people
- conversations
- information
- decisions
- commitments
- follow-up
- documents

That shared material is what the product generalises over.

---

## 3. The five moments

| Moment | What the person is thinking | What MeetingMind does | Section |
| --- | --- | --- | --- |
| **Before** (from 15 minutes out) | "What do I need to remember?" | A prep card from the calendar: last conversation, open items both ways, recent decisions, suggested topics. One tap to record. | §4.1 |
| **During** | "I can't take notes and talk" | Recording with **marks** (⭐ key moment · ✓ action · ? question) and quick typed notes, all placed on the timeline. | §4.2 |
| **Right after** (the golden five minutes) | "Did I get everything? What do I send?" | **The Wrap-up**, then the follow-up. | §4.3 |
| **Later** | "What did we agree with Acme in March?" | Ask and search scoped to a person, organisation or project, with every answer cited. | §4.4 |
| **Weekly** | "What's slipping?" | Weekly review: overdue tasks, stale waiting-on items, unanswered questions, quiet clients. | §4.5 |

The first release must make **Right after** excellent. It is the moment competitors handle worst,
and the one that produces the output other people see.

---

## 4. The moments in detail

### 4.1 Before: prep

**Trigger:** a calendar event with attendees or a title that matches a known person, organisation
or project, starting within the prep lead time (15 minutes by default, set in §8.4).

**The prep card**, as a Today card, a notification, and the header of the event's note:

```
10:00  Acme — Project Review                       in 14 min
Sarah Chen · James Obi (Acme)          Project: Acme Integration

LAST TIME · Sep 28, Kickoff
"Need OAuth2 documentation before we commit."          ▶ 14:32

YOU OWE           Send API docs · due Fri
THEY OWE          Sarah → production credentials · 6 days
DECIDED           OAuth2 selected · Launch Oct 15
STILL OPEN        Production deployment date?

SUGGESTED TOPICS  Credentials · API timeline · October launch

[ ● Record ]   [ Open notes ]   [ Share prep ]
```

- Every line opens its source, and the quote plays from its timestamp.
- **Suggested topics** come only from open items and unanswered questions. The model is not asked
  to invent an agenda.
- **Without any AI** the card still works. Open items, decisions and the last meeting are all
  database queries. AI only adds the one-line "last time" summary.
- **On a first meeting** with someone there's no history. The card shows the attendees' contact
  cards and their organisation, plus "First meeting with Acme", and the empty state stays useful.
- This builds on the existing `MeetingPrep.find`. Matching moves from comparing attendee name
  strings to **resolved people** (§5.1).

### 4.2 During: recording with marks

The recording screen gains a **mark bar** with three large tap targets:

| Mark | Meaning | Effect on extraction |
| --- | --- | --- |
| ⭐ Key moment | "This matters" | The surrounding segment is weighted up for summary and highlights. |
| ✓ Action | "Someone just agreed to do something" | Action extraction is anchored to the ±30 s window, and a missed action there counts as a quality failure. |
| ? Question | "Unanswered, or needs following up" | An open question is created from the window, even if the model would have skipped it. |

- **Quick note:** a text field that stamps its note at the current time. The note appears in the
  transcript as a `USER` block at that moment.
- Marks work from the **notification** and the **lock-screen controls**, because people rarely
  unlock their phone mid-meeting.
- The phone **vibrates lightly** to confirm a mark and makes no sound.
- **Consent reminder:** when recording starts in a work space, a single dismissable line says
  "Let people know you're recording." There's an optional one-tap message to share (§6.4).

This is cheap to build, because marks are timestamps and the timeline already exists. It is also
rare among mobile competitors, and it gives people a feeling of control that no summary can.

### 4.3 Right after: the Wrap-up

When processing finishes, the note opens as a **review card stack**, not as a finished document.
The same card appears in the Today "To review" section until it's done.

```
┌──────────────────────────────────────────────┐
│ Acme — Project Review · 42 min               │
│ Project  [Acme Integration ▾]   ← guessed    │
│ With     Sarah Chen · James Obi  [+]         │
│ Speakers 3 named · 1 unknown  [Name them]    │
├──────────────────────────────────────────────┤
│ DECISIONS                                 2  │
│ ✓ OAuth2 selected                    ▶ 14:32 │
│ ✓ Target launch Oct 15               ▶ 31:05 │
├──────────────────────────────────────────────┤
│ YOUR TASKS                                3  │
│ ✓ Send API docs · Fri                ▶ 18:40 │
│ ...                                          │
├──────────────────────────────────────────────┤
│ WAITING ON                                1  │
│ ✓ Sarah → production credentials     ▶ 22:10 │
├──────────────────────────────────────────────┤
│ OPEN QUESTIONS                            1  │
│ ✓ Production deployment date?        ▶ 36:48 │
├──────────────────────────────────────────────┤
│ + 6 more found  [Show]                       │
├──────────────────────────────────────────────┤
│ [ Draft follow-up ]          [ Done ]        │
└──────────────────────────────────────────────┘
```

**Interaction rules**

- Everything is **pre-checked**. Swipe left to dismiss, tap to edit, long-press to change kind
  (for example, a task that's really a waiting-on).
- **Owner and due date** are chips that say "Me", "Sarah", "Fri" and so on. Tapping one gives
  People suggestions, or date presets: Today, Tomorrow, Fri, Next week, Pick.
- The **project and people guesses** show a small "guessed" hint until confirmed. Confirming or
  changing one teaches the matcher (§5.1).
- **"Name them"** opens the existing speaker naming, with People suggestions taken from the
  calendar attendees.
- **"+ N more"** shows the lower-confidence findings (Principle 5).
- **Done** saves, dismisses the review and schedules due-date reminders.
- **Nothing is lost if the person ignores the Wrap-up.** Items are saved as *unreviewed*. They
  appear in lists with a faint dot, and a daily digest reminds the person once.
- **Target:** under 60 seconds for a typical 30–60 minute meeting.

**Draft follow-up** opens the follow-up composer (§6.5):

- It is prefilled from the confirmed items.
- Its tone comes from the output defaults (§8.5).
- It's addressed to the attendees' emails from their contacts.
- **Send via** uses the share sheet: Gmail, Outlook, WhatsApp, Slack, or Copy.
- **Marking it sent** closes the follow-up task automatically. MeetingMind asks "Did you send
  it?" when the person returns from the share sheet.

**Without any AI**, the Wrap-up shows only what the person marked (§4.2) and anything they typed.
It becomes a quick manual checklist, and still works.

### 4.4 Later: recall

- **Ask**, scoped. Ask exists; it gains a scope chip: `Everything · Acme · Sarah Chen · This
  project · Last 30 days`. Opening Ask from a person, organisation or project page pre-scopes
  it. Answers always cite sources, and a claim with no citation is not shown.
- **Structured questions** answered from the database, not by the model:
  - "Everything Sarah owes me"
  - "Decisions on Acme since March"
  - "Unanswered questions this month"
  - "What did I promise this week?"

  These are **saved filters** offered as chips, not free-text parsing. They work offline and
  without a model.
- **Search** gains filters for person, organisation, project and item kind.

### 4.5 Weekly: the review

On the person's chosen day and time (Friday 16:00 by default, see §8.4), Today shows a **weekly
review card**:

- **Meetings:** how many this week, and time spent in them.
- **Done:** tasks closed.
- **Slipping:** overdue tasks and waiting-on items older than 7 days, each with one-tap actions
  (Nudge, Reschedule, Drop).
- **Still open:** unanswered questions.
- **Quiet:** clients or projects with no contact in 21 or more days (the threshold can be set).
- **[Generate weekly report]**: the Weekly review skill writes a shareable status report with
  citations.

This extends the existing `WeekReview` rather than replacing it.

---

## 5. The model: what the person sees and what we store

### 5.1 People, organisations and projects are core

None of these are professional tables. Faith needs them too: preachers, church members, a small
group, a sermon series. That keeps `PLAN_V1` §9 rule 1 intact.

| User-facing | Stored as | Notes |
| --- | --- | --- |
| **Person** | `entities` row, `kind = PERSON` | Name, aliases, emails, phones, contact URI, organisation, role, avatar, private notes. |
| **Organisation** | `entities` row, `kind = ORG` | Name, aliases, email domains, website. Membership is `entities.orgId`. One table, not two systems. |
| **Project** | `notebooks` row with `kind = PROJECT` plus properties | A notebook already groups notes. A project adds status, organisation, people, dates, and optionally a colour and cover. |

**Resolving who's who is make-or-break.** If "Sarah", "Sarah C", "SPEAKER_2" and
`sarah@acme.com` don't become one person, relationship memory quietly fails. These are the rules:

1. **Seed from device contacts.** This needs `READ_CONTACTS`, asked for at the moment it helps (the
   first Wrap-up, or the People page), never during onboarding.
   - People are created on demand, when an attendee or speaker matches a contact, so the People
     list isn't flooded with the whole address book.
   - Emails and phone numbers are matched exactly. Names are matched loosely.
2. **Calendar attendees resolve by email first**, then by name.
3. **Speakers link to people, not to text labels.** Speaker naming offers the attendees first.
4. **The email domain gives the organisation.** `@acme.com` suggests Acme. Common free-mail
   domains are ignored.
5. **Ask when unsure.** A "Same person?" card appears in the Wrap-up or on the People page when two
   records share a name or email. Merge moves every link. Undo is available for 10 seconds, and a
   merge can be split from the person's page.
6. **Aliases are learned from corrections.** Every rename adds an alias, and aliases feed
   vocabulary hints for ASR. This builds on the existing `VocabularyEntity`, whose
   `PERSON_NAME` and `ORGANIZATION` types already exist.
7. **Later (P3), voice matching.** Diarization speaker embeddings are stored per person. A new
   recording can then suggest "Sounds like Sarah Chen?" It only ever suggests, is on the phone
   only, and can be switched off.

### 5.2 The four nouns and a single item table

Today there are four tables tied to meetings: `action_items`, `decisions`, `questions` and
`follow_ups`. Actions and follow-ups are the same thing to a user. Action deadlines are free text,
so tasks can't sit on the calendar.

**Proposal:** one `items` table, attached to a **note** (not a meeting), with a real due date and
a source.

```
items
  id, noteId, kind, text, status,
  ownerEntityId?,          -- null = me
  dueAt?, dueText?,        -- parsed date plus the original words ("by Friday")
  projectId?,              -- denormalised from the note for fast project queries
  source,                  -- USER | AI | MARK
  sourceSegmentIdsJson, sourceStartMs?,
  confidence?, reviewed,   -- unreviewed items show a faint dot (§4.3)
  supersededById?,         -- decisions: later decision that replaced this one
  answerText?, answeredAt?, answerSegmentIdsJson,   -- questions
  createdAt, updatedAt, completedAt?
kind ∈ TASK · DECISION · QUESTION · (reserved: RISK, REQUIREMENT)
status ∈ OPEN · DONE · DROPPED  (questions: OPEN · ANSWERED · DROPPED)
```

How the four nouns map onto the table:

| Noun | Query |
| --- | --- |
| **My tasks** | `kind = TASK AND ownerEntityId IS NULL` |
| **Waiting on** | `kind = TASK AND ownerEntityId IS NOT NULL`. This is the "commitment" from the earlier plan, with no separate object. |
| **Decisions** | `kind = DECISION`. `supersededById` gives decision history: "OAuth2 → replaced by SSO on Nov 3". |
| **Open questions** | `kind = QUESTION AND status = OPEN`. |

- **A follow-up** is a task with an output attached: `metadata.output = EMAIL_DRAFT`.
- **Risks and requirements** are reserved kinds. The UI ignores them until a work profile turns
  them on (Consulting and Engineering in P2, §8.1).
- **Migration 14 → 15** copies the four old tables into `items`, using the note already created
  for each recording.
  - Free-text deadlines are parsed with a deterministic parser (the same approach as the spoken
    scripture references). Unparsed text stays in `dueText`.
  - The old tables are kept read-only for one version, then dropped.

The timeline gets a **TASKS layer**. `PLAN_V2` F1 listed it, but it couldn't be built because
deadlines were free text. With real dates it can.

### 5.3 Professional notes

A professional note is a normal note:

- The **workflow** gives the template and extraction focus.
- **Properties** are optional and inferred, never required.
- **Blocks** carry `BlockSource` provenance, which already exists.

**Properties** live in `notes.metadata`, so no schema change is needed:

| Property | Where it comes from |
| --- | --- |
| Project | Calendar title or attendees matched to a project, or picked in the Wrap-up |
| People, organisation | Attendees, speakers, contacts |
| Meeting date | The calendar event or recording time |
| Follow-up date | The earliest due date among open tasks |
| Confidentiality | Inherited from the project or organisation (§6.4) |
| Status | `Draft · Reviewed · Sent` for meetings. None for other notes. |
| Tags | The user, plus AI suggestions shown as chips |

Owner, priority and due date belong to **items**, not notes. That keeps notes as knowledge and
items as work.

**New workflows** are pure configuration: `RecordingType` entries, a template, and focus guidance.

| Workflow | Template sections | Default outputs |
| --- | --- | --- |
| `ONE_ON_ONE` (new) | Check-in · Their updates · Blockers · Feedback (private) · Agreed actions | 1:1 summary |
| `CLIENT_CALL` (new) | Context · What they need · Decisions · Actions · Risks · Next meeting | Follow-up email, client update |
| `STANDUP` (new) | Per person: yesterday · today · blockers | Team update |
| `MEETING` (exists) | Summary · Decisions · Actions · Open questions | Minutes, follow-up |
| `INTERVIEW` (exists) | Questions and answers · Notable quotes · Assessment (private) | Interview summary, scorecard |
| `BRAINSTORM`, `RESEARCH` (exist) | Unchanged | Synthesis |
| `PROJECT_BRIEF` (new, not recorded) | Goal · Scope · People · Decisions so far · Open questions · Timeline | Project brief |

Calendar keyword rules pick the workflow automatically (§8.4). For example, "1:1" in the title
gives `ONE_ON_ONE`, and an external attendee domain gives `CLIENT_CALL`.

### 5.4 The semantic transcript

Each transcript segment can carry optional semantic fields. These are written by the extraction
pass and stored beside the segment rather than in it, so evidence is never touched.

```
segment_signals
  segmentId, kind, entityId?, value?, confidence
kind ∈ DECISION · ACTION · QUESTION · COMMITMENT · RISK · DEADLINE · METRIC ·
       REQUIREMENT · OBJECTION · ASSUMPTION
```

- **Transcript scenes** (Discussion, Presentation, Q&A and so on) are shown as section dividers in
  the transcript. They reuse the existing scene and chapter detection.
- **Signals** power the saved filters in §4.4 and the transcript filter chips (Decisions, Actions,
  Questions, Risks). They're also used as extraction evidence, so items and signals agree.
- The UI **never lists raw signals**. They are an index, not a feed.
- This table is core, not professional: Faith uses it for scripture and prayer points. P2.

**Transcript views** stay as **Verbatim / Clean**, which exist today. "Structured" is not a third
transcript mode. It is the **Note** tab, where every line has a citation chip. The Transcript and
Note toggle is the whole model.

---

## 6. Capabilities by pillar, with release

**P1** makes the loop work for one meeting. **P2** adds memory. **P3** adds reach. Anything not
listed is out of scope.

### 6.1 Capture

| Feature | Release | Notes |
| --- | --- | --- |
| Record from a calendar event or prep card | P1 | Exists. The prep card adds one-tap record. |
| "Starting now — record?" notification at event start | P1 | Opt-in (§8.4). Deep-links to recording with the workflow, title and speaker count prefilled. |
| Marks and quick notes | P1 | §4.2 |
| Import audio or video | exists | |
| Share target: audio, PDF, text or URL into MeetingMind | P2 | Lands in **To review** (§7). Anything shared in becomes a note with an attachment. |
| Voice memo, including from the widget | exists | |
| Document scan (camera to PDF to note) | P2 | ML Kit document scanner, on the phone. |
| Web capture (URL to readable text) | P2 | Through the share target. |
| Meeting bots (Zoom, Meet, Teams) | P3 | Needs a backend. See `FUTURE_BACKEND.md`. |

### 6.2 Transcription

Most of this exists: local and cloud ASR, diarization, speaker naming, vocabulary,
checkpoints, word timestamps and citations.

Professional additions:

- **Vocabulary is seeded from people, organisations and projects**, so "Acme" and "Obi" are
  recognised. P1.
- **Speaker naming suggests calendar attendees.** P1.
- **Mixed-language meetings.** The Clean view gets a choice: *keep original language* (the
  default) or *English alongside*. Always store the original. P2.
- **SRT/VTT export.** P2.

### 6.3 Knowledge: the context pages

A **person page**, an **organisation page** and a **project page** all share one layout. That
makes them one screen parameterised by an entity, not three screens.

```
Sarah Chen                          [Ask about Sarah]
Acme Corporation · Head of Engineering

NEXT        Tue 10:00 — Technical review   [Prep]
LAST        Sep 28 — Kickoff               "Need OAuth2 docs…"

YOU OWE        Send API docs · Fri
THEY OWE       Production credentials · 6 days
DECISIONS      OAuth2 selected · Launch Oct 15
OPEN           Production deployment date?

CONVERSATIONS  Kickoff · Tech review · Pricing        (timeline)
PROJECTS       Acme Integration
NOTES          3 notes mention Sarah
```

- An **organisation page** adds a People row. A **project page** adds Status, Timeline and
  Documents.
- Every section hides when it's empty, and the whole page starts from contact data on day one.
- **Pin** any page to the Work Home (§7).
- Release: person and project pages in P1; organisation pages and decision history in P2.

### 6.4 Privacy, consent and retention

Privacy isn't a pillar in the earlier plan, but professionals will judge the product on it.

- **Confidential** can be set on a project, organisation or note.
  - Confidential material **never goes to cloud AI**, even in Internet mode. It uses the local
    model, or no AI at all.
  - It's left out of share cards and stories by default.
  - A lock icon shows wherever it appears.
  - A confidential note's text is hidden in list previews. This is the same behaviour as private
    Faith notes today (`isPrivateByDefault`).
  - P1.
- **Consent:** the reminder on first record in a work space, plus an optional message to share:
  "I'm using MeetingMind to take notes. The recording stays on my phone." P1.
- **Retention:** "Delete audio after 7 / 30 / 90 days / never." The transcript and note are kept.
  It can be set per project, and confidential projects default to 30 days. P2.
- **Where it goes:** each note shows a small line saying where it was processed, for example
  "Processed on this phone" or "Transcribed with Gemini". P1.

### 6.5 Outputs and professional skills

**Composer:** one screen for every output. It shows a draft, a tone chip, a length chip, **Send
via**, and **Mark sent**. Every output is a note block with `source = AI`, keeps its citations,
and can be edited before sending.

| Category | Skills | Release |
| --- | --- | --- |
| Meeting | Minutes · Executive summary · Follow-up email · Actions list | P1 |
| Communication | Client update · Slack/WhatsApp update · Escalation · Nudge ("Just checking on…") | P1 (Nudge, from waiting-on) / P2 |
| Project | Project brief · Status report · Weekly review · Risk and open-questions list | P2 |
| Management | 1:1 summary · Team update · Feedback notes (private) | P2 |
| Client work | Discovery summary · Requirements extraction · Proposal outline · Account summary | P2 |
| Research | Interview synthesis · Compare sources · Research brief | P2 |
| Writing | Professionalise · Shorten · Executive tone · Proofread · Translate | exists (note AI), extend in P1 |
| Your own | User-defined skills: a prompt, a scope and an output kind | P3 |

- Skills are shown **by work profile** (§8.1). A salesperson sees Discovery summary first; a
  manager sees 1:1 summary first. All skills are always reachable through "More".
- Every skill carries `TranscriptToolPrompts.FIDELITY_CONTRACT` unchanged.
- **Export formats:** PDF, DOCX and Markdown exist (`NoteExporter`). Outputs reuse them. Read-only
  share links come in P3, because they need a backend.
- **Growth:** shared minutes and follow-ups end with an optional, tasteful line: "Notes by
  MeetingMind". It's on by default and can be switched off in §8.5.

### 6.6 Integrations

The question isn't "which apps", but **where work enters and leaves**.

```kotlin
interface CalendarProvider  // events, attendees          — DeviceCalendar (exists) · Google · Microsoft
interface ContactsProvider  // people, orgs, emails       — DeviceContacts (P1) · Google · Microsoft
interface OutputChannel     // send a composed output     — ShareSheet (P1) · Gmail · Slack · WhatsApp direct
interface StorageProvider   // import/export documents    — SAF picker (P2) · Drive · OneDrive · Dropbox
interface MeetingProvider   // recordings from platforms  — Zoom · Meet · Teams (P3)
interface TaskProvider      // mirror tasks out           — Todoist · Google Tasks · Microsoft To Do (P3)
```

**The integration centre** is Settings → Integrations. Each row lists what it enables, not just a
"Connected" badge:

```
PHONE CALENDAR                                    ✓ On
  ✓ Meeting prep   ✓ One-tap record   ✓ Attendees   ✓ Workflow rules
PHONE CONTACTS                                    Turn on
  People and companies from your contacts · Follow-up recipients
SHARE TO APPS                                     ✓ Always on
  Gmail · Outlook · WhatsApp · Slack · anything on your phone
─────────────────────────────────────────
COMING LATER   Google · Microsoft 365 · Slack · Zoom · Drive · Notion · CRMs
```

- The "coming later" rows collect interest with a "Notify me" tap. That's founder data: which
  integration to build first.
- **CRM** means one-way export of notes and items to HubSpot or Pipedrive. It is never a pipeline
  inside MeetingMind. P3.

### 6.7 Recipes instead of an automation builder

A **Recipe** is an after-meeting default attached to a workflow or a project. It is not a visual
builder.

```
After a Client call
  ✓ Open the Wrap-up
  ✓ Draft the follow-up email (Friendly, short)
  ✓ Add tasks to "Acme Integration"
  ☐ Draft a client update
```

- Recipes are shown and edited on the workflow's settings page and on each project page. Each
  step is a toggle.
- Built-in recipes come from the work profile.
- **Scheduled recipes:** "Every Friday 16:00 — weekly review" (§4.5). P2.
- These cover about 90% of the event → trigger → skill → action value with no builder. A full
  builder is only worth reconsidering once there's a web or desktop client.

---

## 7. The Work Home

### 7.1 The idea

The Today hub (`feature/today`) is already the Home. The Work Home is **the same screen with a work
layout**. It adds no new route and no branching on vertical:

- Home becomes a list of **sections**.
- Identity and personalisation (§8) choose which sections appear, and in what order.
- Faith-first people keep today's order. Work-first people get the order below. People with both
  switch with the existing space toggle (`TodayFocus`), and each focus remembers its own layout.

```kotlin
enum class HomeSection {
    HERO, SETUP, UP_NEXT, TO_REVIEW, FOLLOW_UPS, MY_TASKS, WAITING_ON, OPEN_QUESTIONS,
    PINNED, PROJECTS, WEEKLY_REVIEW, WEEK_STRIP, TIMELINE, STORIES, DEVOTIONAL, FOR_YOU, MEMORIES
}

data class HomeLayout(val sections: List<HomeSection>, val density: Density)

object HomeLayouts {
    fun defaultFor(identity: AppIdentity, focus: TodayFocus, profile: WorkProfile): HomeLayout
}
```

Existing Today pieces become sections: hero, setup card, For you carousel, story rings, week
strip and timeline. New sections are added beside them.

### 7.2 The default Work Home

The layout reads from top to bottom and answers **"What needs me now?"**

```
┌─────────────────────────────────────────────┐
│ (A) Hero — "Good morning, Ana"             │
│     3 meetings · 2 to review · 1 overdue    │
│     [Search] [Inbox •2] [Switch space]      │
│     ┌ Up next ─────────────────────────┐    │
│     │ 10:00 Acme — Project Review 14m  │    │
│     └──────────────────────────────────┘    │
├─────────────────────────────────────────────┤
│ (B) UP NEXT — the prep card (§4.1)          │
│     [● Record] [Notes] [Share prep]         │
├─────────────────────────────────────────────┤
│ (C) TO REVIEW                          2    │
│     Design sync · 38 min · Wrap-up ready    │
│     acme-brief.pdf · shared in · Process    │
├─────────────────────────────────────────────┤
│ (D) FOLLOW-UPS TO SEND                 1    │
│     Acme — review recap · draft ready [Send]│
├─────────────────────────────────────────────┤
│ (E) MY TASKS           Today · Overdue (1)  │
│     ☐ Send API docs · Acme · Fri            │
│     ☐ Update proposal · Zeta · overdue      │
├─────────────────────────────────────────────┤
│ (F) WAITING ON                              │
│     Sarah → credentials · 6d     [Nudge]    │
├─────────────────────────────────────────────┤
│ (G) PROJECTS (horizontal)                   │
│     [Acme ● 3 open] [Zeta ● quiet 23d] [+]  │
├─────────────────────────────────────────────┤
│ (H) TODAY — agenda / week strip (exists)    │
│     meetings and recordings at their times  │
├─────────────────────────────────────────────┤
│ (I) WEEKLY REVIEW  (Fridays; §4.5)          │
└─────────────────────────────────────────────┘
```

**Section rules**

- **Each section hides when it has nothing.** An empty Home shows only the hero, Up next and the
  agenda, plus the Getting started card.
- **Counts are real and every row opens its source**, following the PLAN_V2 principle that
  everything opens something.
- **Inline actions:**
  - Swipe right on a task marks it done.
  - Swipe left snoozes it to tomorrow.
  - Nudge opens the composer with the Nudge skill.
  - Send opens the composer.
- **Density:** no more than 3 rows per list section, with "See all" leading to the items screen
  filtered to that view.
- **The time of day changes the order, by rule:**
  - Before the first meeting, Up next moves above To review.
  - After the last meeting, To review and Follow-ups rise.
  - On Friday afternoon, Weekly review rises.

  People can pin the order to switch this off (§8.2).

### 7.3 The hero, in the professional look

It keeps the existing `HomeHeroHeader`: avatar, dock, tilt card, `TimeOfDaySky` and the floating
Up next tile. The professional look makes these changes:

- **Greeting tone** follows the setting: Playful (the current pool), Plain ("Good morning, Ana")
  or Off (§8.2).
- **The status line** shows work facts only: "3 meetings · 2 to review · 1 overdue".
- **Stat chips:** *meetings this week* and *follow-ups sent this week*. Sending follow-ups is the
  behaviour we want to reward. Recording streaks don't mean much at work.
- **The Up next tile** shows a countdown during working hours, "Free until 14:00" in gaps, and
  "Done for today · 2 to review" after the last meeting.
- **The sky** uses cooler tones. The orb can be switched to "Calm" (a still gradient) for people
  who find animation unprofessional in a client's view.

### 7.4 The Work tab and the items screen

`BottomNavDestination` is `HOME · NOTES · NEW · SEARCH · SETTINGS`. Search is already in the hero
dock, so for work identities the **fourth slot can be Work**. This is configurable (§8.2), and
Search stays the default for others.

**Work** is a single core screen with four segments:

```
[ Tasks ]  [ People ]  [ Projects ]  [ Decisions ]
```

- **Tasks** has the filters *Mine · Waiting on · Open questions*, plus grouping by project, person
  or due date. There's no Kanban in P1. A status board per project can come in P2 if people ask for
  it.
- **People** includes organisations: a search box and recently met people first.
- **Projects** shows active projects first, then quiet ones, then archived.
- **Decisions** is a searchable decision log across everything, including superseded decisions.

Faith people with a lot of people and groups can also switch this tab on. It's core, not
professional.

### 7.5 Widgets and notifications

- **Next meeting widget**, 2×2 and 4×2: the next event, a countdown, a **Record** button, and the
  prep line. P1. It reuses the F6 widget code.
- **Tasks widget**, 4×2: today's tasks and overdue ones, tick to complete. P2.
- **Notifications** are grouped in a "Work" channel:
  - prep (lead time)
  - "Starting now — record?"
  - Wrap-up ready
  - due-today digest (morning)
  - weekly review

  Each can be switched off, and all of them respect quiet hours and working hours (§8.4).

---

## 8. Professional personalisation

Today, identity is:

- **spaces** (Work, Learning, Faith, Personal)
- **look** (Professional, Sanctuary, Minimal)
- **display name** and **avatar**
- **card style** (Classic, Vivid)
- **timeline layers** and **timeline view**

Professional adds the groups below. All of them are stored in `UserPreferences`, and the complex
groups are stored as JSON, following the same pattern as `DevotionalProfile`.

Settings → **Personalize** becomes a single page with these rows:

```
You               Name · Photo
Spaces            Work · Learning · Faith · Personal
Look & feel       Professional · Sanctuary · Minimal  + accent · dark mode
Home              Layout · Greeting · Motion · Density          (§8.2)
Work profile      Consulting & client work ▾                     (§8.1)
Words             Client · Project                               (§8.3)
Work rhythm       Mon–Fri 08:30–17:30 · Prep 15 min · Friday review (§8.4)
Outputs           Friendly · Short · Signature · Footer          (§8.5)
Privacy           Confidential defaults · Cloud AI · Audio retention (§8.6)
Tab bar           Home · Notes · + · Work · Settings             (§7.4)
```

### 8.1 Work profile

This asks "What kind of work do you do?". It appears in onboarding when Work is chosen, is one
tap, and can be skipped. It is configuration, not code.

```kotlin
enum class WorkProfile {
    CLIENT_WORK, FOUNDER, SALES, MANAGEMENT, RESEARCH_JOURNALISM, RECRUITING, PRODUCT_ENGINEERING, GENERAL
}

data class WorkProfileConfig(
    val words: Terms,                       // §8.3 defaults
    val recordTypes: List<RecordingType>,   // Record sheet order
    val skills: List<SkillId>,              // skill order in composer and note AI
    val recipes: List<Recipe>,              // §6.7 defaults
    val homeOrder: List<HomeSection>,       // §7.2 default order
    val itemKinds: Set<ItemKind>            // e.g. RISK and REQUIREMENT for engineering
)
```

| Profile | Words | First record types | First skills | Home emphasis |
| --- | --- | --- | --- | --- |
| Client work (default) | Client · Project | Client call, Meeting, 1:1 | Follow-up, Client update, Minutes | Up next, Follow-ups, Waiting on |
| Founder | Company · Project | Meeting, 1:1, Pitch/Interview | Follow-up, Investor update, Weekly review | To review, Projects |
| Sales | Account · Deal | Client call (Discovery) | Discovery summary, Follow-up, Objections | Follow-ups, Waiting on |
| Management | Team · Project | 1:1, Standup, Meeting | 1:1 summary, Team update | My tasks, Waiting on |
| Research / journalism | Source · Story | Interview, Research | Interview synthesis, Quotes | To review, Projects |
| Recruiting | Candidate · Role | Interview | Scorecard, Candidate summary | To review, Up next |
| Product / engineering | Team · Project | Standup, Meeting, Brainstorm | Minutes, Requirements, Risks | My tasks, Decisions |
| General | Organisation · Project | Meeting, Conversation | Minutes, Follow-up | Default |

Changing profile changes defaults only. Anything the person has set explicitly is never
overwritten.

### 8.2 Home personalisation

- **Layout:** an edit mode reached by long-pressing any Home section header, or from Settings.
  - Drag to reorder, and toggle sections on or off.
  - "Reset to profile default".
  - Stored per focus: `homeLayouts: Map<TodayFocus, HomeLayout>`.
- **Adaptive order:** On (by time of day, §7.2) or Fixed.
- **Greeting:** Playful · Plain · Off.
- **Motion:** Full (tilt, sky, parallax) · Calm (still sky, no tilt) · Follow system.
- **Density:** Comfortable · Compact. Compact shows 5 rows per section and smaller cards.
- **Pinned:** people and projects pinned from their pages show as a chip row.
- **Tab bar:** choose the fourth slot: Search (default) · Work · Notes-by-project.

### 8.3 Words

Professionals don't all say "client" and "project". A lawyer says "matter", a recruiter says
"candidate", sales says "account" and "deal". Words are **display labels only**; the data model
doesn't change.

| Concept | Options |
| --- | --- |
| Organisation | Client · Customer · Account · Company · Partner · Organisation |
| Project | Project · Matter · Deal · Engagement · Case · Story · Role |
| Person on the other side | Contact (default) · Candidate · Source · Stakeholder |

Words are used in:

- Home section titles, e.g. "Quiet clients" or "Quiet accounts"
- the Work tab segments
- the Wrap-up
- property chips
- composer prompts: "the client" is passed as context so drafts use the same word

Custom words can be typed. Plurals follow simple English rules, with an override field.

### 8.4 Work rhythm

- **Working days and hours**, e.g. Mon–Fri 08:30–17:30. They decide:
  - when work notifications fire
  - what the Up next tile shows
  - when the due-today digest arrives
  - whether "Starting now — record?" asks outside hours (off by default)
- **Prep lead time:** Off · 5 · 10 · **15** · 30 minutes.
- **Record prompt at event start:** Off (default) · Events with attendees · All events.
- **Workflow rules**, e.g. `title contains "1:1" → 1:1`, `external attendee → Client call`,
  `title contains "standup" → Standup`. Profile presets are included and rules are editable.
- **Project rules**, e.g. `attendee domain acme.com → Acme Integration`. These are learned from
  Wrap-up corrections. After two matching confirmations, MeetingMind offers "Always file Acme
  meetings here?"
- **Weekly review:** day and time, or Off.
- **Quiet threshold:** a client or project with no contact for N days (default 21) shows as quiet.
- **Calendars:** which device calendars count as work. This lets a person hide a family calendar
  from the Work Home while it still shows under All.

### 8.5 Output defaults

- **Tone:** Formal · **Friendly** · Brief. **Length:** Short · Standard · Detailed.
- **Language:** follow the note's language (default) or always a chosen one.
- **Greeting and sign-off:** "Hi {first name}," · "Thanks, Ana" with an editable pattern.
- **Signature** block.
- **Default channel** for Send via: Ask each time (default) · Email · WhatsApp · Slack.
- **Minutes format:** Decisions first · Chronological · Action-only.
- **Footer:** "Notes by MeetingMind", On (default) or Off.
- **Include timestamps** in shared minutes: Off by default, because they mean nothing to
  recipients without the audio.

### 8.6 Privacy defaults

- **Cloud AI for Work:** follow Internet mode (default) · On-device only.
- **New clients and projects are confidential:** Off (default) · On. The legal and health profiles
  suggest On.
- **Audio retention:** Keep · 7 · 30 · 90 days, per space. It can be overridden per project.
- **Consent reminder:** On (default) · Off. The message to share can be edited.
- **App lock for Work:** reuses the existing `FaithLock` mechanism, generalised to a per-space
  lock (biometric). P2.

### 8.7 Look & feel additions

- **Accent colours** within the Professional look: Indigo (current) · Teal · Slate · Emerald ·
  Crimson. This is one new field, `AppIdentity.accent`, read by `AppLook.of`.
- **Dark mode.** `PLAN_V2` deferred it because screens hard-code white surfaces. Professionals
  expect dark mode, and many work late. Moving the **Work Home, Work tab, Wrap-up, context pages
  and composer** to `LocalAppLook` tokens from the start means every new professional screen
  works in dark mode on day one. Older screens migrate as they're touched. P1 for new screens,
  P2 for everything.

### 8.8 Onboarding changes

- The identity screen already asks for spaces. When **Work** is picked, one more screen appears:
  **"What kind of work?"**, showing the profile tiles from §8.1, with "Skip" leading to General.
- The **calendar** permission keeps its current explanation, rewritten around prep: "See who
  you're meeting and what's still open."
- **Contacts** is **not** asked in onboarding. It's asked at the first Wrap-up that has an
  unresolved attendee.
- The **Getting started** card gains work steps:
  1. Record or import a meeting
  2. Finish your first Wrap-up
  3. Send your first follow-up
  4. Connect your calendar

---

## 9. Milestones

Each milestone ends with a tested APK and a gate that can be demonstrated, following the practice
from `PLAN_V1`.

| # | Milestone | Contents | Gate |
| --- | --- | --- | --- |
| **W0** | Validate on real meetings | Record 5 real work meetings (2-person, 4-person with crosstalk, a client call on speaker, a 1:1, a standup). Measure action and decision recall against hand-labelled truth. Fix what fails. | Action recall ≥ 80%, decision recall ≥ 70%, no invented owners. |
| **W1** | Items and migration | `items` table and migration 14 → 15, deadline parser, TASKS timeline layer, the four nouns in the note's items view. | Every existing recording's actions, decisions and questions appear as items; tasks with dates sit on the calendar. |
| **W2** | People and organisations | `entities`, contacts provider, attendee and speaker resolution, merge flow, person page, vocabulary seeding. | Two recordings with the same attendee resolve to one person without a prompt. |
| **W3** | Marks and the Wrap-up | Mark bar, notification and lock-screen marks, quick notes, the Wrap-up stack, unreviewed state, consent line. | A 45-minute meeting is reviewed in under 60 s, by stopwatch, on the S20. |
| **W4** | Composer and follow-up | Composer, Follow-up / Minutes / Nudge skills, Send via share sheet, Mark sent, output defaults (§8.5). | Meeting end → follow-up in Gmail in under 5 minutes, typing nothing but corrections. |
| **W5** | Work Home | `HomeSection` / `HomeLayout`, new sections, adaptive order, professional hero, prep card upgrade, next-meeting widget, Work notification channel. | A work identity's Home shows only work; Faith disappears; the layout editor round-trips. |
| **W6** | Personalisation and projects | Work profiles, words, work rhythm, workflow and project rules, privacy defaults (confidential means on-device only), accent, Work tab, project page, new workflows (1:1, Client call, Standup), onboarding step. | Switch profile to Sales and see "Account/Deal" everywhere; a confidential project refuses cloud AI with a clear message. |
| — | **P1 release** | | A real consultant uses it for a week and sends ≥ 5 follow-ups from it. |
| **W7** | Memory | Organisation pages, decision history (superseded decisions), saved filters, scoped Ask, weekly review and report, recipes, quiet clients. | "What did we agree with Acme in March?" answered with citations, offline. |
| **W8** | Inbox and outputs | Share target, document scan, web capture, audio retention, P2 skills, semantic signals table, tasks widget, full dark mode. | A shared PDF becomes a note filed to a project from To review in two taps. |
| **W9+** | Reach (P3) | OAuth providers (Google, Microsoft), Slack direct, task mirrors, meeting bots, voice matching, user skills, share links. | Driven by the integration centre's "Notify me" counts. |

---

## 10. Data changes, all at once

| Change | Kind | Milestone |
| --- | --- | --- |
| `items` table; migrate `action_items`, `decisions`, `questions`, `follow_ups` into it | new core table | W1 |
| `entities` table (person/org, aliases JSON, emails JSON, phones JSON, contactUri, orgId, notes, confidential) | new core table | W2 |
| `note_entities` cross-ref (noteId, entityId, role: ATTENDEE, SPEAKER, MENTIONED) | new core table | W2 |
| `speakers.entityId` | column | W2 |
| `notebooks.kind` (NOTEBOOK · PROJECT) plus `propertiesJson` (status, orgId, confidential, retention) | columns | W6 |
| `segment_signals` | new core table | W8 |
| `entities.voiceEmbedding` | column | P3 |
| Preferences: work profile, home layouts, words, work rhythm, output defaults, privacy defaults, accent, tab slot | DataStore | W5–W6 |

Every table here is **core**. Faith uses entities (preachers, members, groups), items (prayer
follow-ups, study questions), project notebooks (sermon series) and signals (scripture, prayer
points). No table exists only for Professional.

---

## 11. Non-goals

- CRM pipelines, deal stages or forecasting. **Export** to a CRM is fine.
- Full project management: no Gantt, resource planning, sprints or time tracking.
- An email or Slack client. MeetingMind drafts and hands off.
- Team workspaces and shared projects. These need a backend and accounts; see
  `FUTURE_BACKEND.md`.
- A visual automation builder on the phone.
- Accounting, HR, or a document management system.

---

## 12. How P1 is accepted

The same approach as `PLAN_V1` §11: by real use, not the test suite.

1. **Real meetings.** Five recorded on a real phone (the W0 set) go through the whole loop:
   Wrap-up, follow-up sent, and prep shown for the next meeting with the same people.
2. **Timings.** Wrap-up under 60 s, and meeting end to follow-up sent under 5 minutes, both
   measured.
3. **Offline.** With airplane mode on and no model installed, the person can still:
   - record
   - mark
   - see the transcript
   - review marks
   - write tasks by hand
   - see prep from the database
   - send a hand-written follow-up
4. **Confidential.** A confidential project with Internet mode on makes no network call. This is
   verified with the network inspector.
5. **Identity.** Onboard as Work-only and confirm no Faith string, layer or card appears anywhere.
   Then onboard as Faith-first and confirm the Work Home sections are absent unless Work is
   enabled.
6. **A week with a real professional** from the beachhead, using it for their own client work,
   and a written list of what they stopped using and why.

---

## 13. Open questions for the product owner

1. **Beachhead:** is client work (consultants, agencies, founders) the right first audience, or is
   there a specific network (for example a Zimbabwe/Zambia business community) to launch into?
   That changes the language defaults and WhatsApp-first sending.
2. **WhatsApp as the main follow-up channel.** In many markets a follow-up goes on WhatsApp, not
   email. Should the default for Send via be WhatsApp in those locales?
3. **Pricing boundary.** Which of these is paid: cloud processing, P2 memory features, or
   profiles? This affects what the free Work Home shows.
4. **Contacts permission.** Is asking for it an acceptable trust cost for the first release, or
   should People start purely from calendar attendees and speakers?
