# MeetingMind Professional: plan

The Professional vertical, planned for the best experience first and the feature list second. It
also covers a Work Home and the professional additions to the app's personalisation.

**Revised after v34 by Direction v2** (the section after §0): Professional becomes a memory and
execution layer for professional work, built around Work Pulse, the Intelligence Brief and Memory.

Originally written against schema version 14 (v30). It follows the rules in `docs/PLAN_V1.md` §9 and
`docs/PRODUCT_DIRECTION.md` §7, and builds on the identity and Today hub from `docs/PLAN_V2.md`
F0–F1.

---

## 0. Summary

| Question | Answer |
| --- | --- |
| Generalised or focused? | **One engine for every kind of professional, with a focused launch.** The engine works for anyone with people, conversations and commitments. Defaults, templates, onboarding and marketing are aimed first at **people who do client work**: consultants, agencies, freelancers, and founders selling to businesses. |
| What is it? | **A memory and execution layer for professional work** (Direction v2). The loop is **Prepare → Capture → Understand → Act → Communicate → Execute → Remember → Review** (D3). The fundamental object is the ongoing body of work; a meeting is one event in it. |
| The three flagships | **Work Pulse** (the daily habit), **Intelligence Brief** (the wow), **Memory** (retention). See D5. |
| What is it not? | Not a CRM, not a project manager, not an email client. No pipelines, forecasting, Gantt charts, invoicing or HR. |
| The north stars | **Daily:** *the person opens Work Pulse on most workdays because it tells them something true and new* (D5.1). **Per meeting:** *within five minutes of a meeting ending, the person has sent their follow-up and trusts that nothing was dropped, having typed almost nothing.* |
| The most important screens | **Work Pulse** (D5.1), which answers "what needs me today?", and **the Wrap-up** (§4.3), the 60-second review that feeds everything else. |
| Connectors in the first release | Device calendar (exists) and the **Android share sheet**, with WhatsApp and email chosen adaptively (§6.5). No OAuth and **no contacts permission**: MeetingMind keeps its own People list, built from its own history (§5.1). |
| Home | The **Professional home** (built in v34), rebuilt around **Work Pulse** (D5.1). |

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

### Decisions made after this plan was written

These take precedence over anything below that disagrees.

| Question | Decision |
| --- | --- |
| Audience | Client work is the beachhead, **and doctors and lawyers must be able to use it too**. They get their own work profiles (§8.1): clinical and legal wording, templates, and confidential-by-default. |
| Follow-up channel | **Adaptive** (§6.5). WhatsApp or email is chosen per person from what worked before, then from the region and whether an email address is known. |
| Paid and free | Not decided. Gating comes later, so nothing in P1 is built around a paywall. |
| Contacts permission | **Not asked.** MeetingMind builds its own People list from history: speakers, calendar attendees, and names the person types or confirms (§5.1). |
| Centre of gravity (after v34) | **Direction v2.** Context and continuity before more features: first-class decisions, commitments, questions and risks; organisations and projects as context containers; Pulse, Brief and Memory as the flagships. Kanban, Gantt and the whiteboard wait until the model exists. |
| Commitments | **Their own object** (D4.2), distinct from tasks, with a direction (you owe or they owe). This reverses Principle 3's "no separate object". |
| Speaker names | **Dynamic everywhere** (§5.5). Renaming "Speaker 1" in the transcript updates the summary, items, note, AI outputs and exports. |

---

## Direction v2: a memory and execution layer for professional work

Added after v34, and it **takes precedence** over anything later in this document that disagrees.
Sections 1–14 stay as the record of the foundation, and each place this changes is marked
*(superseded by Direction v2)*.

### D1. The diagnosis, accepted

v34 made **Right after** excellent: the Wrap-up, the follow-up, dynamic names. That was the P1
bet in §3. It still feels like *"a very good recorder with professional features"*, because:

- **It's recording-centric.** A professional's day is *Prepare → Meet → Capture → Understand →
  Decide → Assign → Communicate → Execute → Review → Meet again*. We own Capture → Understand,
  have the beginnings of Decide → Assign, and don't yet own Prepare, Execute, Review or
  continuity.
- **Findings are meeting-local.** A decision, a question or a promise lives inside the recording
  it came from. Nothing tracks it after that meeting ends, so nothing can say what changed.
- **There's no pull.** Nothing tells the person something new each day, and nothing makes them
  show the app to someone else.

The change is a change of **centre of gravity**, not 30 more features:

> **Before:** MeetingMind Professional = notes + recordings + AI + tasks.
> **After:** MeetingMind Professional = **a memory and execution layer for professional work.**
> The fundamental object is the ongoing body of work. A meeting is one event inside it.

**The positioning line** (replaces §2):

> **Remember the work. Understand what changed. Know what to do next.**

**The killer test.** Ask *"What is happening with Myavana?"* and get, with every line opening its
evidence:

- active projects and the last meeting
- the decisions in force
- what you owe and what they owe you
- open questions and risks
- the next meeting and what to prepare

Until that works offline from the database, Professional is not done.

### D2. Reconciled with what v34 already has

The direction's gap list was written against an older build. This is the honest state, and what
changes.

| Area | The direction says | v34 actually has | Decision |
| --- | --- | --- | --- |
| Organisations | Missing | `people` rows with `kind = ORG`, found from email domains, with a page | **Keep the table and grow it.** Add domain, description, URLs, logo and custom properties. It's a context container, never a CRM. |
| Projects | Missing | Notebooks with `kind = PROJECT`, and a project hub (notes, tasks, people) | **Keep the notebook.** Notes already live in notebooks. Add organisation, members, overview, risks, files and a timeline. |
| Professional Home | Partial | Built: briefing card, Needs you, schedule, tasks, projects, people | **Rebuild its core around Work Pulse** (D5.1). The briefing card becomes the Pulse. |
| Decision log | Missing | A list that reads main's meeting-local `decisions` | **Make decisions first-class** (D4), with status, supersedes, links and evidence. |
| Commitments | Missing | "Waiting on" tasks (`tasks.waitingOn`) | **Commitments become their own object** (D4.2). This reverses Principle 3's "no separate object". |
| Open questions | Meeting-local | A list across meetings, with Answered | **First-class**, with owner, project, answer and where it was answered. |
| People | Data model exists | Built from history, merge, "Same person?", person page | **Add relationship intelligence**: Relationship Pulse (D5.2). |
| Meeting prep | Mostly missing | Up next on the home. The §4.1 prep card was never built. | **Build it as the Prepare skill** (D5.4). |
| Risks, requirements | Missing | Reserved in §5.2, never built | **Build them** as signal kinds (D6). |
| Integrations | Missing | Device calendar and the share sheet; §6.6 provider interfaces planned | **Keep the provider abstraction.** Capability-first integration centre (D8). |
| Automation | Missing | Wrap-up → tasks is the only fixed recipe | **Trigger → Context → Skill → Approved actions** under recipes (D8). |
| Kanban, Gantt, whiteboard | In the old plan | Not built | **Agreed: later.** They are views over the model (D9). |

### D3. The loop, made explicit

The five moments in §3 become the whole loop. Every screen belongs to one step, and every step
hands to the next.

| Step | The person asks | MeetingMind answers with | State in v34 |
| --- | --- | --- | --- |
| **Prepare** | "What do I need for my 10:00?" | Prep: last time, you owe, they owe, decisions, open questions, suggested agenda from open items | Missing |
| **Capture** | "Get it all down" | Record, import, calendar-linked note, marks, quick notes, attachments, URLs | Strong |
| **Understand** | "What happened?" | Transcript, summary, and **signals with evidence** (D6) | Strong, but thin on signals |
| **Act** | "Who does what?" | Wrap-up: tasks, commitments, decisions, questions, risks, linked to people, organisation and project | Built for tasks, thin elsewhere |
| **Communicate** | "What do I send?" | Follow-up, minutes, client update, executive summary, **Brief** (D5.3) | Follow-up and exports built |
| **Execute** | "What's on me today?" | **Work Pulse** (D5.1), tasks, nudges | Partial |
| **Remember** | "What happened with Acme in March?" | **Memory** (D5.5): scoped Ask and entity history | Missing |
| **Review** | "What slipped this week?" | Weekly review (§4.5), then next week's plan | Missing |

### D4. The context model

Five core entities: **Person, Organisation, Project, Meeting, Note.** Everything else attaches to
them through links, and every derived object carries evidence.

```
                 ORGANISATION
               /      |       \
          People   Projects   Notes ── Documents
             \        |        /
              Meetings / Notes
                      |
     Decisions · Commitments · Questions · Risks · Requirements …
                      |
                    Tasks
```

This is a graph, but it's stored as plain tables and link rows. All of it is **core, not
professional**, so `PLAN_V1` §9 rule 1 holds. Faith uses the same objects: a sermon-series project,
a prayer commitment, a study question.

#### D4.1 Items: the durable record

v34 left findings in main's meeting-local tables (`action_items`, `decisions`, `questions`,
`follow_ups`). **Those stay** as the extraction output for one recording, and main's screens keep
reading them. §5.2's single `items` table comes back as the **durable, cross-meeting record**.
Wrap-up *confirm* promotes findings into items, once each, the same way it already promotes
actions into main's `tasks`.

```
items
  id, kind, status, text,
  value?,                        -- structured value: a date for DEADLINE, a number for METRIC
  ownerPersonId?, ownerSpeakerId?,     -- who holds it (null = me)
  counterpartyPersonId?,               -- commitments: who it's owed to
  projectId?, orgId?,                  -- denormalised context for fast queries
  dueAt?, dueText?,
  supersedesId?,                       -- decisions, deadlines, scope: what this replaced
  answerText?, answeredAt?, answerItemId?,   -- questions
  taskId?,                             -- the main task that executes it, if any
  reason?,                             -- decisions: why, when it was said
  confidence?, reviewed, source,       -- USER | AI | MARK
  createdAt, updatedAt, closedAt?
item_evidence   itemId, meetingId?, noteId?, blockId?, segmentIdsJson, startMs?, endMs?, quote
item_links      itemId, targetType (PERSON · ORG · PROJECT · NOTE · MEETING · ITEM), targetId, role
item_events     id, itemId?, entityType, entityId, type, beforeJson?, afterJson?, at, evidenceId?
```

- **Evidence is required** for every AI item. `sourceSegmentIdsJson` already exists on every
  finding, so evidence is copied, not invented. An item without evidence is shown only if the
  person typed it.
- **`item_events` is the change log** that Work Pulse, "What changed" and the project timeline
  read. Every create, status change, reassignment, date change and supersession writes one row, in
  the same transaction.
- **Tasks stay main's `tasks` table.** An item links to a task. Items never duplicate it.

#### D4.2 Commitments are not tasks

A **task** is work: *"Fix the dashboard."* A **commitment** is a promise between people, with a
direction: *"Candace said she'd send the production credentials by Friday."*

| | Mine (I promised) | Theirs (they promised me) |
| --- | --- | --- |
| Shown as | **You owe** | **They owe** |
| Task | Created and linked; ticking the task closes the commitment, and the reverse | None. This replaces v34's `waitingOn` tasks, which are migrated. |
| Actions | Do, reschedule, renegotiate (drafts a message) | Nudge, mark received, release |

- **Status:** Open · Completed · Cancelled · Unclear. *Due soon* and *Overdue* are computed from
  `dueAt`, never stored.
- **Unclear** is for when the model heard a promise but not who made it or when it's due. It's
  shown in the Wrap-up as a question to settle, never silently guessed.
- The two questions this makes answerable offline: **"What have I promised people?"** and **"Who
  is waiting on me?"**

#### D4.3 Decisions, questions and risks are first-class

- **Decision:** what, why, project, organisation, people, date, evidence. The status is *Proposed*,
  *Active*, *Superseded* or *Reversed*. A *Proposed* decision (options discussed, nothing decided)
  feeds Pulse's **Decision needed**. The supersede chain gives the decision log its history:
  *"WordPress is the source of truth (Sep 29), replacing Headless CMS (Sep 12)."*
- **Open question:** question, owner, project, status (*Open*, *Answered*, *Dropped*), the answer
  and the meeting that answered it. A later meeting that answers it proposes "Answered?" in its
  Wrap-up.
- **Risk:** statement, severity, project, what it blocks, evidence. It's closed when the blocker
  goes.

#### D4.4 Organisations and projects grow up

- **Organisation** (`people`, `kind = ORG`): name, domain(s), description, logo, URLs, custom
  properties, members, projects. Its page is the context page (D5.2) with People and Projects
  rows. There are no deals, stages or forecasts (§11).
- **Project** (notebook, `kind = PROJECT`): organisation, members, status, overview (a
  generated, cited paragraph), dates, files, custom properties.
  - The project page adds **Decisions, Commitments, Questions, Risks and Timeline**.
  - The timeline is `item_events` and meetings in date order.
  - **"What changed this month?"** is a filter on that timeline, not a model call.
- **Linking is inferred**, never required (Principle 2):
  - A meeting inherits its project from the calendar title and attendees, or from the Wrap-up
    guess.
  - Its items inherit the project and organisation from the meeting.

### D5. The three flagship experiences

Three experiences define the vertical. Everything else serves them.

| Experience | The promise | Frequency | Role |
| --- | --- | --- | --- |
| **Work Pulse** | "Open MeetingMind. Know what matters." | Daily | The habit |
| **Intelligence Brief** | "Turn everything it knows into something I can send." | As needed, shared | The wow |
| **Memory** | "What do I know about this?" | Constantly, and more valuable over time | Retention |

They reinforce each other:

1. Pulse shows *what changed*.
2. The evidence shows *why*.
3. Memory shows *the whole history*.
4. A Brief turns it into *something to send*.
5. The next meeting adds information, and tomorrow's Pulse picks it up.

#### D5.1 Work Pulse (the Professional home's core)

It replaces the briefing card at the top of the Professional home. It answers *"What do I need to
know and do today?"*, not *"What notes have I made?"*

```
WORK PULSE · Good morning, Winston          4 things need you
─────────────────────────────────────────────────────────────
● YOU PROMISED    Send revised proposal to Acme · due today      ▶
● WAITING ON      John — production credentials · 5 days         [Nudge]
● DECISION NEEDED Myavana launch date · 2 options, no decision   ▶
● FOLLOW UP       NetOne · 8 days quiet · 3 unresolved           [Prepare]
─────────────────────────────────────────────────────────────
TODAY   10:00 Myavana — Sync · 3 open from last time   [Prepare]
        14:00 NetOne · last met Sep 24 · 2 questions   [Prepare]
─────────────────────────────────────────────────────────────
SINCE YESTERDAY
  Myavana   +2 commitments · +1 decision · launch Oct 14 → Oct 21  ▶
  Sarah committed to API docs by Fri                                 ▶
─────────────────────────────────────────────────────────────
[ Ask about my work ]
```

- **Built from the database, not a model.** The attention rows are queries over items and tasks.
  "Since yesterday" reads `item_events` since the last time the Pulse was seen
  (`pulseSeenAt`). It works offline and with no AI.
- **Every row has evidence.** ▶ plays the moment, or opens the item.
- **The attention budget:** at most **four** rows, ranked:
  1. overdue and due today, mine first
  2. decision needed on a project that has a meeting today
  3. commitments owed to me and overdue
  4. quiet relationships with open items

  The rest is behind "See all". A Pulse that lists 20 things is a to-do list, and people stop
  opening it.
- **Changes are specific sentences**, never counts alone: "Launch moved Oct 14 → Oct 21", made from
  a `DEADLINE` item superseding another, with the new evidence.
- **Cold start (Principle 6):**
  - On day one, Pulse reads the calendar ("3 meetings today, first time meeting NetOne") and any
    past recordings backfilled into items.
  - It never shows an empty state that says "record something first".
- **Notification:** an optional morning Pulse, one line ("4 things need you · first meeting
  10:00"). It respects working days and quiet hours (§8.4).
- **Sensitive profiles** (Clinical, Legal) show counts on the lock screen and in notifications,
  never names.

#### D5.2 Context pages and Relationship Pulse

§6.3's one-layout context page is built for person, organisation and project. Each page opens with
a **pulse header**:

```
CANDACE · Myavana · last spoke yesterday
YOU OWE     Production build · Architecture update
SHE OWES    Credentials · Final copy
OPEN        Launch timing
DECIDED     WordPress stays the source of truth
NEXT        Fri — Hair Journey sync                   [Prepare for conversation]
```

Below the header: meetings, notes, projects, decisions, the timeline, and **Ask about Candace**.
For organisations and projects, the same header is aggregated across their members.

#### D5.3 Intelligence Brief (the wow)

**✨ Create brief** is available on a meeting, person, organisation, project and the week. It
produces a polished, shareable document:

- Executive picture
- What changed
- Decisions
- Commitments
- Risks
- Open questions
- Next 7 days
- Decisions required
- Recommended next conversation
- Evidence

Variants:

- meeting brief
- client brief
- project status for *"something I can send my boss"*
- relationship brief
- weekly brief

**Grounding rules.** These are what make the reaction *"it actually understands"* and not *"pretty
summary"*:

1. **The structure is deterministic.** Decisions, commitments, risks, questions and changes are
   database rows rendered by a template. The model never lists them.
2. **The model writes only the prose:** the executive picture and the recommended next
   conversation. It writes from a **context pack** of those rows plus the relevant summaries. It
   works under `FIDELITY_CONTRACT`, and every sentence must cite an item or evidence id. A sentence
   without a citation is dropped before display.
3. **Every claim expands to its evidence.** In the app that's a timestamp that plays; in the PDF
   it's an appendix of cited moments.
4. **Progress bars and status colours come from counts**, never from the model's opinion. The
   status is Attention when there are overdue commitments, open risks or pending decisions, and
   On track otherwise.
5. **Without a model** the brief still renders, with no prose paragraphs.

**Export** reuses `NoteExporter`: PDF, DOCX and Markdown, with a new **Brief** PDF theme designed
to be screenshotted.

- Confidential material prints a confidentiality line.
- For sensitive profiles the prose is written by the on-device model only.

This is the first thing to demo, and the feature people show to someone else.

#### D5.4 Prepare (the flagship skill)

*"Prepare me for my meeting with Acme"*, from Pulse, a calendar row, a context page or Ask:

- **Last time:** a cited quote
- **Still open**, **They owe**, **You owe**
- **Decisions made**, **Questions to resolve**
- **Suggested agenda**, ordered from open items and pending decisions only (§4.1: the model never
  invents an agenda)

It's a Brief variant, so it follows the same grounding rules. It is also the §4.1 prep card, built
at last.

#### D5.5 Memory

- **Every meeting contributes to the memory** of its people, organisation and project. Memory is
  the items, events and summaries, reachable through two surfaces:
  - **The history view** on any context page. *"Acme — 4 months: 12 meetings · 37 decisions · 3
    scope changes"*, then **The story**: one short cited paragraph per month, then **Most
    important** and **Still open**.
    - Story paragraphs are generated once per month per entity and cached.
    - They're regenerated only when that month gains items.
  - **Scoped Ask** (§4.4). *"What did Candace say about the October launch?"* Retrieval is scoped
    through `item_links` and `note_people` before transcript chunks are searched. It builds on
    `AskEverything` and `TranscriptRetriever`. An answer with no citation is not shown.
- **Structured questions** stay database queries offered as chips (§4.4), and now include:
  - "What have I promised?"
  - "Who's waiting on me?"
  - "Decisions still active from the last 3 months"
  - "What changed on this project this month?"

#### D5.6 Weekly Review (the second habit)

§4.5, promoted:

1. **The facts:** meetings, actions, decisions, commitments and unresolved questions this week.
2. **The questions:**
   - What changed?
   - What did I finish?
   - What slipped?
   - Who am I waiting on, and who is waiting on me?
   - Which projects need attention?
3. **Create next week's plan:** carry forward, reschedule or drop, in one pass.
4. **Weekly brief** (D5.3), optional, to send.

### D6. Meeting intelligence 2.0: signals with evidence

Extraction returns **signals**, each with evidence (segment ids and time range) and a confidence
score. The signal kinds:

- Decision, Proposed decision
- Commitment, Action, Question
- Risk, Requirement, Constraint, Assumption, Dependency
- Objection or Concern
- Deadline, Metric
- Scope change
- Approval, Rejection
- Agreement, Disagreement

§5.4's `segment_signals` is the index. Items (D4.1) are what gets reviewed and tracked.

> *"We can't ship this until legal approves the wording."*
> → **Constraint:** legal approval required · **Dependency:** legal → copy approval ·
> **Risk:** release blocked · ▶ 32:18

- **The Wrap-up stays under 60 seconds** (Principle 5). It shows Decisions, You owe, They owe and
  Questions, plus a new **Changes** card: *"This replaces: Launch Oct 14?"* Confirming it sets
  `supersedesId`.
- **Risks and requirements** are shown by profile, for Client work, Product and Legal. The other
  kinds are kept, but reached only through filters, Ask and briefs. They never show as a feed
  (§5.4).
- **Change detection** matches new decisions, deadlines and scope against the open items of the
  same project. The model proposes a match; the person confirms it in the Changes card. A
  supersession is never written silently, because a wrong one corrupts the memory.
- **W0 comes first.** Pulse, Brief and Memory all amplify extraction quality, good or bad. The W0
  recall gates (actions ≥ 80%, decisions ≥ 70%, no invented owners) must be measured before
  Pulse ships to anyone outside the team, and commitments gain their own gate: ≥ 75% recall and
  no invented promises.
- **Profiles set the words.** Clinical says *patient*, *plan* and *follow-up*; Legal says
  *matter*, *instruction* and *undertaking*. The objects stay the same.
- **CONSULTATION** keeps its rule: no diagnosis, dose or legal opinion that wasn't said.

### D7. Work Inbox: the front door

Everything that isn't a live meeting enters here:

- voice memos and recordings
- PDFs and shared files
- web pages
- quick notes
- document scans
- forwarded email (once email is connected)

- **The share target** (§6.1, now pulled forward) puts things in the Inbox, not straight into a
  note.
- **Process with MeetingMind** proposes what it is and where it goes, as one Wrap-up-style card:
  - *Note in project X*
  - *Tasks*
  - *Decision*
  - *Contact details for Candace*
  - *Document for Acme*
  - *Meeting material for Friday*

  The person confirms or changes it with one tap.
- **Without AI**, the Inbox is a manual "file to…" list, and it still works.
- The Inbox count shows on Pulse and in the Work space. **Nothing processed from the Inbox skips
  confirmation.**

### D8. Integrations and automation

- **Keep §6.6's provider interfaces**, and add capabilities to each one. The integration centre
  lists what each connection **enables**:
  - **Calendar:** detection, attendees, prep, auto-notes, reminders
  - **Email:** relevant correspondence in Memory, reply drafts, attach minutes
  - **Storage:** project documents, attachments, retrieval
- **Order:**
  1. Android Calendar (have)
  2. Share sheet (have)
  3. Gmail and Outlook (read opt-in and scoped to known people and domains; send stays a draft
     handoff)
  4. Drive, OneDrive and Dropbox
  5. Slack
  6. Meet, Zoom and Teams recordings
  7. HubSpot export

  The "Notify me" counts (§6.6) can reorder anything after step 3.
- **Email read access is a trust decision**, like contacts was (§13.4).
  - It's opt-in per account and limited to people MeetingMind already knows.
  - It's off and unavailable in Clinical and Legal unless the person turns it on, with a clear
    warning.
  - Confidential projects never send email content to cloud AI.
- **Automation is Trigger → Context → Skill → Approved actions → Output**, exposed as **recipes**
  (§6.7), not a builder. The Wrap-up is recipe #1.
  - Next: *"When a client meeting ends, draft minutes and the follow-up, create the tasks,
    update the project, and remind me Friday."*
  - **Nothing outward happens without approval:** nothing is sent, shared or written to another
    service.

### D9. Views and navigation

- **Views come after the model.** Kanban is tasks by status, and a timeline is tasks by date.
  Decision log, commitments, risks and people are saved views over items.
  - A **lightweight timeline** is in scope.
  - **Dependency-editing Gantt and the whiteboard stay out** on the phone (Principle 9, §11).
  - **Professional X-Ray** (the explained project graph) comes after Brief and Memory prove the
    data is right. It's a view, not a foundation.
- **Navigation.** The direction's *Home · Inbox · Work · Meetings · Notes · Memory* is the right
  mental model, but the bottom bar is shared with Faith and Personal people. So:
  - **Home** is the Professional home, with Pulse at its core.
  - **Work** takes the fourth tab slot for work identities (`TabSlot.WORK`, already in settings).
    It holds Inbox · Projects · People · Organisations · Decisions · Commitments.
  - **Notes** stays as it is. Meetings are notes.
  - **Memory is Ask**, available from every screen and pre-scoped by where you are. It isn't a tab.
  - People who aren't in a work identity see no change.

### D10. Where this pushes back on the direction

- **Commitments and tasks don't both show for the same promise.** "You owe" and "My tasks" read
  the same linked row, and ticking either closes both. Two lists of the same thing is exactly the
  review fatigue Principle 5 warns about.
- **Pulse stays short.** Four rows, ranked. The habit comes from being right, not from volume.
- **No silent intelligence.** Supersessions, commitments with no owner, and Inbox filing are all
  confirmed. The memory is only valuable if it's trusted.
- **No CRM, still.** Organisations are context containers, never pipelines (§11).
- **Evidence over eloquence.** A brief that can't cite a line doesn't print it.

### D11. Roadmap from here

W0–W6 are done (§14). The direction's P0–P8 map onto these milestones. Pulse moves earlier than
the direction's P5, because the habit is what makes the rest worth building, and a database-only
Pulse needs nothing past W7.

| # | Milestone | Contents | Gate |
| --- | --- | --- | --- |
| **W0** | Validate extraction | Unchanged, plus commitment recall. **Blocks W9 shipping.** | Actions ≥ 80%, decisions ≥ 70%, commitments ≥ 75%, no invented owners or promises |
| **W7** | Context model (P0) | Schema 18: `items`, `item_evidence`, `item_links`, `item_events`; org and project fields; project members. Wrap-up confirm promotes findings to items. Migrate `waitingOn` tasks to commitments, and backfill items from past recordings. | Every past recording's decisions, questions and promises exist as items with evidence; no main table loses a row |
| **W8** | Pulse and context pages (P1 + P5a) | Work Pulse on the Professional home; context pages for person, organisation and project with pulse headers, timelines and item lists; decision log and commitments views; Prepare v1 from the database | Airplane mode, no model: Pulse shows 4 correct rows and "since yesterday"; "What have I promised?" answers |
| **W9** | Intelligence 2.0 (P2) | Signals with evidence; Changes card and supersession; risks and requirements by profile; `segment_signals` | On the W0 set, "launch moved" is detected and confirmed, with the right evidence |
| **W10** | Brief, Prepare and Memory (P4) | Intelligence Brief (all variants) with the PDF theme; Prepare skill; history view and scoped Ask | A project brief is generated and exported, and every sentence opens its evidence |
| **W11** | Inbox (P5b) | Share target into Inbox, Process card, document scan, web capture | A shared PDF is filed to a project in two taps |
| **W12** | Rhythm and review (P3) | Morning Pulse, prep and "Starting now" notifications, Weekly Review with next-week plan, widgets, the Work tab slot | A week of real use: Pulse opened on 4 of 5 workdays |
| — | **P2 release** | | A real consultant says they'd miss Pulse, and sends a Brief to someone else unprompted |
| **W13** | Integrations (P6) | Provider capability model, Gmail and Outlook (scoped), then storage | Email context appears in Memory for a known person, cited |
| **W14** | Views (P7) | Kanban, timeline, saved views, X-Ray | |
| **W15** | Automation (P8) | Recipes on Trigger → Context → Skill → Approved actions | A client-meeting recipe runs end to end, with approval |

### D12. Data changes for Direction v2

These are additive, following the v34 rule: main's tables are kept.

| Change | Kind | Milestone |
| --- | --- | --- |
| `items`, `item_evidence`, `item_links`, `item_events` | new core tables | W7 |
| `people` (ORG): `domainsJson`, `description`, `urlsJson`, `logoPath`, `propertiesJson` | columns | W7 |
| `project_members` (notebookId, personId, role) | new core table | W7 |
| Migrate `tasks.waitingOn = 1` to commitment items (the task row is kept and marked migrated) | data | W7 |
| `segment_signals` | new core table (§5.4) | W9 |
| `briefs` cache (entityType, entityId, kind, contentJson, citedIdsJson, createdAt) and the monthly memory story cache | new core tables | W10 |
| `inbox_items` (kind, uri, text, status, proposedJson) | new core table | W11 |
| Preferences: `pulseSeenAt`, Pulse notification, attention ranking | DataStore | W8 |

---

## 1. Principles

These decide the arguments. Each one can be checked in review.

1. **Moments, not modules.** Design for the five moments of a professional conversation (§3).
   The pillars are a checklist, not a navigation structure.
2. **No required fields, ever.** Every property is inferred from the calendar, People history,
   attendees or transcript, and appears as a suggestion the person confirms with one tap. Typing
   is always allowed and never required.
3. **Few nouns.** *(Revised by Direction v2: commitments are now a separate object, D4.2.)* People see **You owe, They owe, Decisions and Open questions**, plus My tasks for work that isn't a promise. Actions,
   commitments, follow-ups and reminders are the same object seen from different angles
   (§5.2). The data layer can be richer than the UI; the UI stays this simple.
4. **Evidence is never overwritten.** Transcript text is evidence. Notes, items and outputs are
   derived from it and cite it. Tapping any AI-written sentence plays the moment it came from.
5. **Show little, keep everything.** Extraction stores every signal it finds. The UI shows the
   top three to five per kind, and the rest can be reached through Ask and filters. Review
   fatigue kills trust faster than a missed item.
6. **Value on the first day.** Every feature that only pays off after ten meetings (relationship
   memory, decision history) is seeded from the calendar and past recordings. It is never the first
   thing a new user sees.
7. **Correct once, fixed everywhere.** Renaming a speaker, merging a person, editing a decision or
   reassigning a task updates every place that shows it, plus search and future vocabulary.
8. **Private by default, with rules that do something.** "Confidential" is a behaviour, not a
   label: confidential material never leaves the phone (§6.4).
9. **Phone first.** Anything that needs a big screen (Gantt, visual automation builders, heavy
   document editing) is out of scope or deferred.
10. **Change is the product** (Direction v2). What changed since last time is worth more than
    what exists. Every state change is logged (`item_events`), and every change shown cites its
    evidence.
11. **An attention budget.** Pulse shows at most four things. Being right beats being complete.
12. **The existing rules still hold.**
    - A vertical adds configuration, not tables or type-branching UI.
    - Everything extracted cites its source.
    - Nothing needs a model to be usable.

---

## 2. The positioning in one line

> **Remember the work. Understand what changed. Know what to do next.** *(Direction v2; it
> replaces "MeetingMind remembers the work, understands the context, and helps move it
> forward.")*

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
- **On a first meeting** with someone there's no history. The card shows the attendees' names and
  email domains, and the organisation when it's known, plus "First meeting with Acme", and the empty state stays useful.
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
- It's addressed to the attendees, using the email addresses and phone numbers MeetingMind has
  learned for them (calendar invites, or typed once and remembered).
- **Send via** is adaptive (§6.5): WhatsApp or email is offered first, with the share sheet and
  Copy always available.
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
| **Person** | `people` row, `kind = PERSON` | Name, aliases, emails, phones, organisation, role, preferred channel, private notes. `isSelf` marks the user. |
| **Organisation** | `people` row, `kind = ORG` | Name, aliases, email domains, website. Membership is `people.orgId`. One table, not two systems. |
| **Project** | `notebooks` row with `kind = PROJECT` plus properties | A notebook already groups notes. A project adds status, organisation, people, dates, and optionally a colour and cover. |

**Resolving who's who is make-or-break.** If "Sarah", "Sarah C", "SPEAKER_2" and
`sarah@acme.com` don't become one person, relationship memory quietly fails. These are the rules:

1. **People come from MeetingMind's own history, never the address book.** There's no contacts
   permission. A person is created when:
   - a speaker is named
   - a calendar attendee appears (with their email)
   - the person types a name as an owner or attendee

   Existing recordings are scanned once on upgrade, so People isn't empty on day one. Emails and
   phone numbers are matched exactly; names are matched loosely.
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

*(Superseded by Direction v2, D4.1.* v34 kept main's meeting-local tables instead of this one.
The `items` table returns as the durable cross-meeting record, beside main's tables rather than
replacing them. *Waiting on* becomes a commitment object.)*

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
| People, organisation | Attendees, speakers, People history |
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

### 5.5 Dynamic names

When "Speaker 1" becomes "Sarah Chen" in the transcript, **every place that shows that person
updates at once**: the summary, the Wrap-up, items, the meeting note, AI tool results, chat
answers, exports and share text. It's the most visible form of Principle 7.

There are two mechanisms, because there are two kinds of data:

- **Structured references resolve by id at display time.**
  - An item's owner is a speaker id or a person id, never only a string.
  - The name shown is looked up when it's drawn. Renaming the person or speaker changes it
    everywhere with no rewriting.
  - `ownerName` is kept only as a fallback, for when the id no longer resolves.
- **Free text is rewritten when a name changes.** A summary sentence like "Speaker 1 will send
  the docs" is prose, so the old name is replaced with the new one in everything derived from
  that recording:
  - the summary
  - item text
  - AI-written note blocks
  - AI tool outputs
  - chat answers

  The rules for the rewrite:
  - **Real names** are matched as whole words, case-sensitively, so "Mark" doesn't change
    "mark the date".
  - **Generic labels** ("Speaker 1", "SPEAKER_1", "speaker one") are matched case-insensitively,
    and never inside a longer label ("Speaker 1" doesn't touch "Speaker 10").
  - **The person's own writing is never rewritten**, because it's theirs.
  - **Transcript text is evidence and is never rewritten.** Only its speaker label changes.
- **Linking a speaker to a person** does the same rename, then keeps them linked. Renaming the
  person later (from the person page) updates every speaker linked to them, in every recording.
- **Merging two speakers** is a rename of the removed speaker to the kept one's name.

`SpeakerNames` (core) is the single place that does this, so no screen formats a speaker name on
its own.

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
- Every section hides when it's empty. A new person's page starts from the recordings and events
  they appear in.
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
- **Adaptive channel.** Send via offers one primary button, chosen in this order:
  1. The channel last used successfully with this person, or their preferred channel if set.
  2. If the person has only a phone number, WhatsApp. If they have only an email, email.
  3. Otherwise the region default: WhatsApp first where it's the normal business channel (for
     example most of Africa, Latin America, South and Southeast Asia and the Middle East),
     email first elsewhere.
  4. Group meetings with external attendees and known emails default to email.

  The other channels stay one tap away. WhatsApp text uses WhatsApp formatting (`*bold*`, short
  lines), and email gets a subject line and paragraphs. The **same content** is rendered for
  each channel, so nothing is written twice.
- **Export formats:** PDF, DOCX and Markdown exist (`NoteExporter`). Outputs reuse them. Read-only
  share links come in P3, because they need a backend.
- **Growth:** shared minutes and follow-ups end with an optional, tasteful line: "Notes by
  MeetingMind". It's on by default and can be switched off in §8.5.

### 6.6 Integrations

The question isn't "which apps", but **where work enters and leaves**.

```kotlin
interface CalendarProvider  // events, attendees          — DeviceCalendar (exists) · Google · Microsoft
interface ContactsProvider  // people, orgs, emails       — none in P1 (People is built from history) · Google · Microsoft (P3, opt-in)
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

*(Updated by Direction v2, D9: Work gains Inbox, Organisations and Commitments; Memory is Ask,
available from every screen.)*

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
    CLIENT_WORK, FOUNDER, SALES, MANAGEMENT, CLINICAL, LEGAL, RESEARCH_JOURNALISM, RECRUITING,
    PRODUCT_ENGINEERING, GENERAL
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
| Clinical (doctors, therapists, other health professionals) | Patient · Case | Consultation, Case discussion, Meeting | Clinical note (history, examination, plan, as said), Referral letter, Patient summary | To review, Up next |
| Legal (lawyers, paralegals) | Client · Matter | Client consultation, Meeting, Interview | Attendance note (with duration), Advice summary, Client letter | To review, Follow-ups, Waiting on |
| Research / journalism | Source · Story | Interview, Research | Interview synthesis, Quotes | To review, Projects |
| Recruiting | Candidate · Role | Interview | Scorecard, Candidate summary | To review, Up next |
| Product / engineering | Team · Project | Standup, Meeting, Brainstorm | Minutes, Requirements, Risks | My tasks, Decisions |
| General | Organisation · Project | Meeting, Conversation | Minutes, Follow-up | Default |

**Clinical and Legal differ from the rest by default:**

- **Everything is confidential.** Processing is on the phone only unless the person turns cloud
  processing on per case or matter. The consent reminder is on and can't be skipped silently.
- **Audio is deleted after 30 days** by default. The note and transcript are kept.
- **The AI boundary is stricter.**
  - A clinical note records what was said. It never adds a diagnosis, dose or recommendation that
    wasn't spoken.
  - A legal note never offers legal advice of its own.

  Both are enforced by one extra clause beside `FIDELITY_CONTRACT`, with tests, the same way the
  Faith rule is enforced.
- **The footer is off** for shared output, and **exports are marked "Confidential"**.
- **Legal attendance notes** carry the start, end and duration from the recording, because
  lawyers bill from them. MeetingMind records time; it doesn't do billing.
- Both also add **`CONSULTATION`**, a workflow with its own template:
  - Reason for visit or instruction
  - What was discussed
  - Plan or advice given (as said)
  - Next steps
  - Private notes

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
- **Contacts** are never asked for. People builds itself from recordings and the calendar.
- The **Getting started** card gains work steps:
  1. Record or import a meeting
  2. Finish your first Wrap-up
  3. Send your first follow-up
  4. Connect your calendar

---

## 9. Milestones

*(W7 onward is superseded by the Direction v2 roadmap, D11. W0–W6 are kept as built.)*

Each milestone ends with a tested APK and a gate that can be demonstrated, following the practice
from `PLAN_V1`.

| # | Milestone | Contents | Gate |
| --- | --- | --- | --- |
| **W0** | Validate on real meetings | Record 5 real work meetings (2-person, 4-person with crosstalk, a client call on speaker, a 1:1, a standup). Measure action and decision recall against hand-labelled truth. Fix what fails. | Action recall ≥ 80%, decision recall ≥ 70%, no invented owners. |
| **W1** | Items and migration | `items` table and migration 14 → 15, deadline parser, TASKS timeline layer, the four nouns in the note's items view. | Every existing recording's actions, decisions and questions appear as items; tasks with dates sit on the calendar. |
| **W2** | People and organisations | `entities` built from history (no contacts), attendee and speaker resolution, dynamic names (§5.5), merge flow, person page, vocabulary seeding. | Two recordings with the same attendee resolve to one person without a prompt. |
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

*(As planned before v34. What was actually built is in §14, and what comes next is in D12.)*

| Change | Kind | Milestone |
| --- | --- | --- |
| `items` table; migrate `action_items`, `decisions`, `questions`, `follow_ups` into it | new core table | W1 |
| `people` table (person/org, aliases JSON, emails JSON, phones JSON, orgId, role, preferred channel, isSelf, notes, confidential) | new core table | W2 |
| `note_people` cross-ref (noteId, personId, role: ATTENDEE, SPEAKER, MENTIONED) | new core table | W2 |
| `speakers.personId` | column | W2 |
| `notebooks.kind` (NOTEBOOK · PROJECT) plus `propertiesJson` (status, orgId, confidential, retention) | columns | W6 |
| `segment_signals` | new core table | W8 |
| `people.voiceEmbedding` | column | P3 |
| Preferences: work profile, home layouts, words, work rhythm, output defaults, privacy defaults, accent, tab slot | DataStore | W5–W6 |

Every table here is **core**. Faith uses people (preachers, members, groups), items (prayer
follow-ups, study questions), project notebooks (sermon series) and signals (scripture, prayer
points). No table exists only for Professional.

---

## 11. Non-goals

- CRM pipelines, deal stages or forecasting. **Export** to a CRM is fine.
- Full project management: no dependency-editing Gantt, resource planning, sprints or time
  tracking. Kanban and a lightweight timeline are **views** over tasks (D9), not a project tool.
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

## 13. Questions answered

1. **Audience.** Client work is right, and doctors and lawyers must be served too (the Clinical
   and Legal profiles in §8.1).
2. **WhatsApp or email.** Adaptive, per person (§6.5).
3. **Paid and free.** Decided later. P1 builds no paywall.
4. **Contacts permission.** Not worth it. People comes from MeetingMind's own history (§5.1).

---

## 14. Status

**v34 is the professional vertical rebuilt on top of main (v33).** Main's work is kept exactly as it
is: its tables (`people`, `tasks`, `note_people`), its task editor, its three homes and its Faith
system are unchanged. The vertical adds to them rather than running alongside them.

- **Schema 17 adds columns only** (`MIGRATION_16_17`):
  - people: `kind`, `orgId`, contacts, `preferredChannel`, `isSelf`, `confidential`, `lastSeenAt`, `space`
  - tasks: `waitingOn`, `ownerSpeakerId`, `sourceItemId`, `space`
  - speakers: `personId`
  - meetings: `reviewedAt`
  - notebooks: `kind`, `propertiesJson`
- **Findings stay where main keeps them** (action items, decisions, questions and follow-ups).
  Confirming the Wrap-up turns actions and follow-ups into main's tasks, once each.
- **The Work space** (Notes → Work, like Faith) is useful before the first recording:
  - templates for client calls, 1:1s, standups, consultations, briefs, decision records and
    weekly reviews
  - projects, people, tasks, Waiting on with Nudge, the decision log and open questions
- **The Professional home** is the fourth home in Look and home: a briefing card, then Needs you,
  Schedule, tasks, Waiting on, projects and people. Every other feature is still reachable from it.
- **Settings → Work** sets the profile, words, tone and privacy. Onboarding asks "What kind of
  work?" when Work is picked.

| # | Milestone | Status |
| --- | --- | --- |
| W0 | Validate on real meetings | **Needs you.** Five real work meetings recorded on the phone, then compare what was caught with what was said. Nothing in this repository can stand in for it. |
| W1 | Findings and migration | Done, on main's tables (schema 17). `DueDates` reads deadlines into days. |
| W2 | People and organisations | Done, **without contacts**, on main's `people` table. People are built from named speakers, calendar attendees and typed owners; a one-time backfill runs after upgrade. Organisations come from work email domains. Merge, "Same person?", aliases. |
| — | Dynamic names | Done (§5.5). `SpeakerNames.propagate` renames everywhere; owners are read through speaker ids. |
| W3 | Marks and the Wrap-up | Done. Marks and the consent line are on the recording screen; notification and lock-screen marks are still to do. |
| W4 | Composer and follow-up | Done. Written from confirmed findings with no model; the channel is adaptive; "Did it go?" marks it sent. |
| W5 | Work Home | Done as the **Professional home** and the **Work space**. The Work tab slot, widgets and the Work notification channel are still to do. |
| W6 | Personalisation and projects | Done: profiles (including Clinical and Legal), words, tone, keep-on-phone, consent reminder, new workflows, the onboarding step and confidential enforcement. |

**Next:** the Direction v2 roadmap (D11). Start with W0 measurement and W7, the context model.

**Small P1 leftovers**, folded into D11:

- Work rhythm notifications (W12)
- marks from the notification and lock screen (W12)
- the next-meeting widget (W12)
- the Work tab slot (W12)
- the accent colour choice (W12)

**Decisions made while building**

- **The table is `people`, not `entities`.** It reads better in queries, and one table holds both
  people and organisations (`kind`).
- **`projectId` is the note's notebook.** Any notebook can be a work context. A notebook marked as a
  project (`kind = PROJECT`) is what the Wrap-up offers and creates.
- **An unassigned task counts as the person's own.** It shows under My tasks until someone else is
  given it, because nothing that came out of a meeting should fall between the lists.
- **Findings below 50% confidence wait behind "N more found"** in the Wrap-up (Principle 5). A
  marked item is raised to 95%, because the person flagged it.
- **The follow-up is a template, not a model.** It can only say what was confirmed. An AI "polish"
  can come later as an option, with the fidelity contract.
- **Clinical and Legal profiles** add the Consultation workflow, whose extraction focus forbids
  adding any diagnosis, dose or legal opinion that wasn't said. They keep work on the phone, turn
  the footer off, and default to a formal tone.
