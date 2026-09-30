# Brief: build Professional W7–W15

You are implementing milestones **W7 to W15** of the Professional vertical of MeetingMind, an
Android app (Kotlin, Jetpack Compose, Room). Another agent, the **orchestrator**, reviews your work
at checkpoints and sends the next instructions.

**Read these first, in this order:**

1. `docs/PLAN_PROFESSIONAL.md`, section **Direction v2** (D1–D12). This is the spec. D11 is the
   roadmap, and D12 lists the data changes.
2. `docs/PLAN_PROFESSIONAL.md` §1 (principles), §4.1 (prep), §4.4 (recall), §4.5 (weekly
   review), §5.5 (dynamic names), §6.4 (privacy) and §14 (what v34 built).
3. `docs/ARCHITECTURE.md` and `docs/AI_ARCHITECTURE.md`.
4. This brief. Where it is more specific than the plan, follow this brief. Where they conflict,
   stop and ask (see *Questions and blockers*).

---

## 1. Rules that are never broken

1. **Keep all existing work.**
   - Main's work (v31–v33) and the v34 professional work stay as they are.
   - **Don't delete, rename or change the meaning of any existing table, column, screen, route or
     public function.**
   - Add beside existing code. Any existing line you do change must be necessary and small, and
     you must list it in your handoff (§5).
   - Main's meeting-local tables (`action_items`, `decisions`, `questions`, `follow_ups`), main's
     `tasks`, `people` and `note_people`, and the Faith system all keep working unchanged.
2. **Migrations are additive and tested.**
   - Each schema bump is new tables and `ADD COLUMN` statements with defaults, plus data copies.
     Never `DROP` anything, and never rebuild a table.
   - Room exports its schemas to `app/schemas`. Commit the new JSON file.
   - Every migration gets a Robolectric test like `Migration16To17Test`: build the old schema,
     insert rows, migrate, check that every row survived and that Room accepts the result.
   - Append the new migration to `MeetMindDatabase.getInstance().addMigrations(...)`, **and** to
     the migration chains in the existing migration tests (`Migration14To15Test`,
     `Migration15To16Test`, `Migration16To17Test`, `NoteAiMigrationTest`, `NotesMigrationTest`).
     Otherwise they fail with "migration from N to M not found".
3. **Nothing needs a model to be usable.**
   - Pulse, context pages, the decision log, commitments, Prepare's structure and Brief's
     structure all come from the database.
   - AI only adds prose, and every AI sentence must cite an item or evidence id. Drop a sentence
     that has no citation.
   - Every AI prompt includes `TranscriptToolPrompts.FIDELITY_CONTRACT`.
4. **Privacy.**
   - Anything `WorkPrivacy.mustStayOnDevice(...)` covers (confidential projects, people and
     organisations, keep-on-device, and the Clinical and Legal profiles) is never sent to a cloud
     model or a third-party service. Use the local model, or no AI.
   - This applies to every new AI call, integration and export.
   - Never add the contacts permission.
5. **Nothing silent, nothing outward without approval.**
   - The person confirms supersessions, commitments with no owner, Inbox filing and automation
     actions.
   - Nothing is sent, shared or written to another service without a tap.
6. **Theme tokens only.**
   - Use `Ink`, `SurfaceBase`, `Accent` and the other tokens in `ui/theme`, and `Briefing.*` for
     the dark briefing palette.
   - `ColorLiteralGuardTest` fails the build when a screen gains a hardcoded colour. If you need a
     new content colour, add it to `ui/theme`.
   - Every new screen must work in light and dark mode.
7. **The identity boundary.**
   - People who aren't in a work identity see no change: no Work strings, cards or tab.
   - The bottom bar changes only when `WorkSettings.tabSlot == TabSlot.WORK`.
8. **Git.**
   - Work on branch **`pro/w7-w15`**, created from `origin/ccr-5e66b00c-btultn`. If your
     environment assigns you a different branch, use that one and say so in every handoff.
   - Push after every milestone with `git push -u origin <branch>`. If a push fails, retry up to
     four times, waiting 2, 4, 8 and 16 seconds.
   - Don't merge into `main` or into `ccr-5e66b00c-btultn`, don't open pull requests, and don't
     force-push.
   - Make **one or more commits per milestone**, each with a subject line that starts `W7:`,
     `W8:` and so on.
   - Don't put model names or model ids in commits, code comments or docs.
9. **Tests are never skipped, disabled or weakened** to get to green.

## 2. The environment

- **Android SDK:** `/root/android-sdk`, set in `local.properties` as
  `sdk.dir=/root/android-sdk`. If the file is missing, create it.
- **Fast compile:** `./gradlew compileDebugKotlin -q`
- **Unit tests:** `./gradlew testDebugUnitTest -q`. There are about 1,130 tests, run under
  Robolectric. All must pass.
- **APK:** `./gradlew assembleDebug`. It produces `arm64-v8a` and `armeabi-v7a` splits in
  `app/build/outputs/apk/debug/`.
- **Rate limits:** Maven Central sometimes returns HTTP 429. Retry the Gradle command with a
  backoff (10 s, 20 s, 30 s). It isn't a code failure.
- **Lint** already fails on main with about 145 `NewApi` errors in Faith and devotional code. Don't
  fix them. Don't add new lint errors in files you touch.
- **Version:** `app/build.gradle.kts` is at `versionCode = 34`. Bump it by one at each checkpoint
  (§4).

## 3. The code you build on (v34)

The package is `com.craftflowtechnologies.meetingmind`. Paths below are under
`app/src/main/java/com/craftflowtechnologies/meetingmind/`.

| What | Where |
| --- | --- |
| Database (schema 17), `MIGRATION_16_17`, `workDao()` | `core/database/MeetMindDatabase.kt`, `WorkDao.kt`, `PeopleTasks.kt`, `Entities.kt`, `NoteEntities.kt` |
| Core work logic | `core/work/`: `Work.kt` (types), `People.kt` (`WorkPeople`), `SpeakerNames.kt`, `FollowUp.kt`, `Send.kt`, `WorkSettings.kt`, `WorkPrivacy.kt`, `WorkRepository.kt` (findings, `confirm`, tasks), `Marks.kt`, `WorkStartup.kt` (one-time backfills), `DueDates` |
| Work UI | `feature/work/`: `WorkSpaceScreen`, `WorkAllScreen`, `ProjectScreen`, `WorkPersonScreen`, `WrapUpScreen`/`WrapUpViewModel`, `FollowUpSheet`, `WorkSettingsScreen`, `ProfessionalHome`, `WorkUi` (shared components), `WorkViewModel` |
| Routes | `feature/navigation/AppNavigation.kt` (`Routes.WORK*`, `WRAP_UP`, `PROJECT`) and the composables in `MainActivity.kt` |
| Main's tasks | `TaskRepository`, `TaskReminders`, `TaskEditorSheet` (reuse it; don't fork it) |
| Prep and week | `core/timeline/TodayIntelligence.kt` (`MeetingPrep`, `WeekReview`, `Greetings`) |
| AI | `ai/routing/AiModelRouter.kt`, `LanguageModelFactory.kt`, `ai/cloud/` (Gemini, with DeepSeek as fallback), `ai/llm/` (on-device), `ai/tools/TranscriptToolPrompts.kt` (`FIDELITY_CONTRACT`), `ai/pipeline/` (processing, `AiToolWorker`) |
| Retrieval and Ask | `ai/retrieval/TranscriptRetriever.kt`, `ai/assistant/AskEverything.kt`, FTS tables |
| Export | `core/export/NoteExporter.kt`, `ExportManager.kt`, `MarkdownExporter.kt` |
| Notifications and widgets | `core/notify/AppNotifications.kt`, `core/widget/Widgets.kt` |
| Bottom bar | `core/ui/BottomNavigation.kt`, and `TabSlot` in `WorkSettings` |
| Evidence | Every finding has `sourceSegmentIdsJson`, and segments carry start and end times |

Before you write anything new, look for an existing helper and reuse it. Match the style around
it: plain-English comments, short functions, and one composable file per screen.

## 4. The milestones

Build them in order. Each one ends with its **Definition of done** met, its tests passing and a
handoff file written (§5).

**Checkpoints:** stop after **W7**, **W8**, **W10**, **W12** and **W15**. Push, write the
handoff, bump the version, build the APK (keep it out of git), and end your run. The orchestrator
reviews the work and tells you to continue. Between checkpoints, carry on without waiting.

### W7: the context model (schema 17 → 18)

Spec: D4, D12.

**1. Entities and migration `MIGRATION_17_18`:**

```
items(id TEXT PK, kind TEXT, status TEXT, text TEXT, value TEXT?,
      ownerPersonId TEXT?, ownerSpeakerId TEXT?, counterpartyPersonId TEXT?,
      projectId TEXT?, orgId TEXT?, meetingId TEXT?, noteId TEXT?,
      dueAt INTEGER?, dueText TEXT?, supersedesId TEXT?,
      answerText TEXT?, answeredAt INTEGER?, answerItemId TEXT?,
      taskId TEXT?, reason TEXT?, severity TEXT?, direction TEXT?,   -- MINE | THEIRS for commitments
      confidence REAL?, reviewed INTEGER NOT NULL DEFAULT 0, source TEXT NOT NULL DEFAULT 'AI',
      sourceFindingId TEXT?,                                         -- UNIQUE: idempotent promotion
      space TEXT NOT NULL DEFAULT 'WORK',
      createdAt INTEGER, updatedAt INTEGER, closedAt INTEGER?, deletedAt INTEGER?)
item_evidence(id PK, itemId, meetingId?, noteId?, blockId?, segmentIdsJson, startMs?, endMs?, quote)
item_links(itemId, targetType, targetId, role, PRIMARY KEY(itemId, targetType, targetId))
item_events(id PK, itemId?, entityType, entityId, type, beforeJson?, afterJson?, at, evidenceId?)
project_members(notebookId, personId, role, PRIMARY KEY(notebookId, personId))
people: + domainsJson DEFAULT '[]', description, urlsJson DEFAULT '[]', logoPath, propertiesJson DEFAULT '{}'
```

- Add indices on `items(kind, status)`, `items(projectId)`, `items(orgId)`,
  `items(ownerPersonId)`, `items(counterpartyPersonId)`, `items(dueAt)`, `item_events(at)` and
  `item_links(targetType, targetId)`.
- **Kinds:**
  - `DECISION`, `COMMITMENT`, `QUESTION`
  - `RISK`, `REQUIREMENT`, `CONSTRAINT`, `ASSUMPTION`, `DEPENDENCY`, `OBJECTION`
  - `DEADLINE`, `METRIC`, `SCOPE_CHANGE`, `APPROVAL`
- **Statuses:**
  - decisions: `PROPOSED`, `ACTIVE`, `SUPERSEDED`, `REVERSED`
  - commitments: `OPEN`, `COMPLETED`, `CANCELLED`, `UNCLEAR`
  - questions: `OPEN`, `ANSWERED`, `DROPPED`
  - everything else: `OPEN`, `CLOSED`
- *Overdue* and *Due soon* are computed, never stored.

**2. `core/work/Items.kt` (`ItemRepository`)** creates, updates, closes, supersedes, links and
reads items.

- **Every write also writes an `item_events` row, in the same Room transaction.** The event types
  are:
  - `CREATED`, `STATUS`, `DUE_CHANGED`, `OWNER_CHANGED`, `TEXT_CHANGED`
  - `SUPERSEDED`, `ANSWERED`, `LINKED`, `PROJECT_CHANGED`
- Put this in one place, so nothing can change an item without logging it.

**3. Promotion.**

- Extend `WorkRepository.confirm(meetingId)`. After it creates tasks, it promotes the confirmed
  findings to items:
  - decisions become `DECISION`, `ACTIVE`
  - unresolved questions become `QUESTION`, `OPEN`
  - actions and follow-ups owned by someone else become `COMMITMENT`, `THEIRS`
  - actions I own become `COMMITMENT`, `MINE`, linked to the task `confirm` created
- Promotion is idempotent through `sourceFindingId`.
- Each promoted item gets evidence copied from `sourceSegmentIdsJson` (with times and a quote from
  the segment text), links to the meeting's people, organisation and project, and
  `projectId`/`orgId` denormalised from the note's notebook.

**4. Commitment ↔ task sync.**

- Completing or reopening a linked task (through `TaskRepository`, or `WorkRepository.toggle`)
  completes or reopens the commitment, and the reverse. Put the sync where both paths go through
  it.
- Guard against loops with a check for "already in that state".

**5. Data migrations**, run once through `WorkStartup` with a new flag:

- Each `tasks` row with `waitingOn = 1` becomes a `COMMITMENT THEIRS` item with `taskId` set.
  Keep the task row.
- Every **reviewed** meeting (`reviewedAt != null`) has its findings promoted, as if the person
  had confirmed them.
- Findings from unreviewed meetings are promoted with `reviewed = 0`.

**6. Reads.**

- Replace the v34 decision-log, open-question and Waiting-on lists to read items. Keep the old
  DAO queries in place, since they're unused but harmless.
- `WorkAllScreen` tabs: **You owe · They owe · Decisions · Open questions · People**. The "You
  owe" tab shows `MINE` commitments plus tasks with no commitment.

**Tests:**

- `Migration17To18Test`
- `ItemRepositoryTest`: every write logs an event; supersede sets both status and link
- `PromotionTest`: idempotent; evidence is copied; project and org are inherited
- `CommitmentSyncTest`
- `WaitingOnMigrationTest`

**Definition of done:**

- On a database seeded with main's fixtures plus v34 data, every reviewed meeting's decisions,
  questions and promises exist as items with evidence.
- Every existing row in every table is still there.
- All tests pass.

**→ Checkpoint.**

### W8: Work Pulse, context pages and Prepare v1

Spec: D5.1, D5.2, D5.4.

1. **`core/work/Pulse.kt`** is pure queries, with no AI.
   - `attention(now, limit = 4)` ranks rows in this order:
     1. my commitments and tasks overdue or due today
     2. `PROPOSED` decisions on projects with a meeting today, then any `PROPOSED` decision
     3. `THEIRS` commitments overdue, or open for 5 days or more
     4. quiet relationships: people or organisations with open items and no meeting for
        `quietDays`
   - `changesSince(t)` groups `item_events` after `t` by project, then organisation, then person,
     and renders **specific sentences**: "Launch moved Oct 14 → Oct 21", "Sarah committed to API
     docs by Fri". Never render counts alone.
   - `today()` returns calendar events with open counts from their last meeting.
   - Keep `pulseSeenAt` in `UserPreferences`, and update it when the Pulse has been on screen
     for 2 seconds or more.
2. **The Professional home.** Replace the briefing card's body with the Pulse. Keep the gradient
   look, and keep every other section.
   - Each attention row carries its action: ▶ evidence, Nudge (existing `NudgeSheet`), Prepare,
     or open the item.
   - **"Ask about my work"** opens Ask.
   - **Cold start:** with no items, show today's calendar lines ("First meeting with NetOne") and
     a recording or template prompt. Never show a bare empty state.
3. **The context page:** one screen, `feature/work/ContextScreen.kt`, parameterised by
   `PERSON`/`ORG`/`PROJECT`.
   - It opens with the pulse header: You owe, They owe, Open, Decided, Next, and "Prepare for
     conversation".
   - Then meetings, notes, projects or members, decision history (the supersede chain), risks,
     and a **timeline** (`item_events` plus meetings, by date) with a "This month" filter.
   - Route `Routes.context(type, id)`. Make the existing `WORK_PERSON` and `PROJECT` routes open
     it. Keep their route strings working.
   - Organisation pages get editable fields: domain, description, URLs and properties.
   - Project pages get members, status, dates and properties.
4. **Prepare v1** (database only): `core/work/Prepare.kt` builds a `PrepPack` for an event,
   person, organisation or project.
   - Last meeting with a cited quote (the last decision or commitment evidence).
   - Still open, you owe, they owe, decisions, questions to resolve.
   - A suggested agenda: open items and proposed decisions only, ranked.
   - A `PrepareSheet` UI, reachable from Pulse, calendar rows, context pages and the event's note
     header.
5. **Saved-filter chips** (§4.4 and D5.5), answered from the database:
   - What have I promised?
   - Who's waiting on me?
   - Active decisions (3 months)
   - What changed this month?

   Put them in the Work space and on context pages.

**Tests:**

- `PulseRankingTest`: order and the limit of 4
- `ChangesSinceTest`: the exact sentences for date change, new commitment and supersession
- `PrepareTest`
- `ContextHeaderTest`
- A Compose test that the Pulse renders with no AI configured

**Definition of done:** with airplane mode and no model, Pulse shows the right four rows and
"Since yesterday", and "What have I promised?" answers correctly.

**→ Checkpoint.**

### W9: meeting intelligence 2.0 (schema 18 → 19)

Spec: D6.

1. Add `segment_signals(segmentId, meetingId, kind, entityId?, value?, confidence)` to the
   schema, with a migration and a test.
2. **Extraction.** Extend the intelligence output (the JSON parser in
   `ai/llm/MeetingIntelligenceJsonParser.kt`, and the Gemini and local engines) with a `signals`
   array. Each signal has a kind, text, segment ids, a value (dates for `DEADLINE`) and a
   confidence.
   - Keep the old fields and parsing, so old outputs still parse (add a test).
   - Proposed decisions and commitments with a speaker, counterparty and due date come through
     the same array.
   - Update the focus guidance per `RecordingType`, and keep CONSULTATION's rule: no diagnosis,
     dose or legal opinion that wasn't said.
   - The kinds a profile shows by default are `COMMITMENT`, `DECISION`, `QUESTION`, plus:
     - `RISK` and `REQUIREMENT` for Client work, Product and Legal
     - `DEADLINE` for everyone
3. **Change detection.** `core/work/Changes.kt` matches new `DECISION`, `DEADLINE` and
   `SCOPE_CHANGE` signals against the project's open or active items.
   - A cheap lexical and date match runs first.
   - A model check runs only on the candidates, and never for `mustStayOnDevice` material unless
     the model is local.
   - The output is a **proposal** only.
4. **The Wrap-up.**
   - Add the **Changes** card ("This replaces: Launch Oct 14?" with Confirm / Not the same).
     Confirming supersedes and writes the event.
   - Split the tasks section into **You owe** and **They owe**.
   - Add an **Unclear** chip for commitments with no owner or date, which the person must settle
     or dismiss.
   - Keep the rule that the Wrap-up takes under 60 seconds: extra kinds sit under "N more found".
5. Add filter chips to the transcript (Decisions · Commitments · Questions · Risks), driven by
   `segment_signals`.

**Tests:**

- The parser: new and old formats
- `ChangesMatcherTest`: the date-move and decision-reversal fixtures
- Wrap-up confirm with supersession
- Privacy: a confidential meeting makes no cloud call during change detection. Use a fake
  transport.

**Definition of done:** on a two-meeting fixture where the launch date moves, the Changes card
proposes the supersession with the right evidence. Confirming it makes Pulse say "Launch moved
Oct 14 → Oct 21".

### W10: Brief, Prepare and Memory (schema 19 → 20)

Spec: D5.3, D5.4, D5.5.

1. Add `briefs(id, entityType, entityId, kind, contentJson, citedIdsJson, createdAt)` and
   `memory_stories(entityType, entityId, month, text, citedIdsJson, itemCount, createdAt)`, with
   a migration and a test.
2. **`core/work/ContextPack.kt`** assembles one scoped, bounded bundle of items, evidence quotes,
   meeting summaries and recent events for any entity or date range. Everything else in W10 reads
   through it.
3. **`core/work/Brief.kt`.**
   - The structure is deterministic: executive picture, what changed, decisions, commitments,
     risks, open questions, next 7 days, decisions required, recommended next conversation,
     evidence.
   - The model writes **only** the executive picture and the recommended next conversation. Its
     output is JSON sentences, each with `cites: [ids]`.
   - Drop every sentence whose citations don't resolve to ids in the pack.
   - The status (On track / Attention) and progress come from counts.
   - Variants: meeting, client (organisation), project status, relationship (person), weekly.
   - Without a model, the brief renders with no prose.
4. **`BriefScreen`:**
   - Every item expands to its evidence (playing from the timestamp).
   - Share, and export to **PDF, DOCX and Markdown** through `NoteExporter`/`ExportManager`,
     using a new **Brief** PDF theme: clean type, status chip, sections, and an evidence
     appendix of cited quotes with dates and times.
   - Confidential material prints a confidentiality line.
   - Entry points: **✨ Create brief** on meeting detail, context pages, the Work space and the
     weekly review.
5. **Prepare** (the skill): `PrepareSheet` gains an optional cited "Last time" line and agenda
   phrasing from the model, with the same grounding rules.
6. **Memory.**
   - **The history view** on context pages: the counts header, **The story** (one cited
     paragraph per month, from `memory_stories`, generated when missing or when that month's
     item count changes), **Most important** and **Still open**.
   - **Scoped Ask.** Add a scope to `AskEverything` (entity type and id, or a date range).
     Retrieval uses `item_links` and `note_people` to limit which notes and transcripts it
     searches, and puts the context pack before transcript chunks. An answer without a citation
     is not shown.
   - Ask opened from a context page is pre-scoped, with a scope chip the person can clear.
7. Every model call goes through `WorkPrivacy` (local or none for sensitive material).

**Tests:**

- `BriefGroundingTest`: uncited sentences are dropped; the structure matches the database
- `BriefNoModelTest`
- `ContextPackTest`: scoping
- `MemoryStoryCacheTest`: regenerated only when the month changes
- PDF export smoke test: the file exists, has pages, and contains the evidence appendix
- A scoped Ask test with a fake model

**Definition of done:** a project brief is generated and exported to PDF, and every sentence in it
opens its evidence. With no model, the same brief renders without prose.

**→ Checkpoint.**

### W11: Work Inbox (schema 20 → 21)

Spec: D7.

1. Add `inbox_items(id, kind, uri?, text?, title?, status, proposedJson?, createdAt,
   processedAt?, resultRefJson?)`, with a migration and a test.
2. **The share target.** Add an `ACTION_SEND` / `ACTION_SEND_MULTIPLE` intent filter for
   `text/*`, `application/pdf`, `audio/*`, `image/*` and URLs.
   - Copy the file into app storage, then create an Inbox item.
   - Open the Inbox, or show a toast.
   - Check the manifest for an existing share entry first, and don't break any existing
     share-in.
3. **The Inbox screen** in the Work space. Show the count on Pulse and on the Work card.
4. **Process.**
   - Extract the text: PDF text, reuse the web-to-readable-text code if it exists, and use the
     existing audio import for audio.
   - Propose one filing as a Wrap-up-style card: note in project X, tasks, decision, contact
     details for a person, document for an organisation, or meeting material for an event.
   - The person confirms or edits it, and filing goes through existing repositories and
     `ItemRepository`.
   - Without AI, show a manual "File to…" picker.
5. **Document scan:** use the ML Kit document scanner if the dependency is acceptable (it's
   on-device). Otherwise write a note in the handoff and skip it.

**Tests:** the migration; `InboxProcessTest` (proposal → filing, and nothing filed before
confirmation); the share intent parsing.

**Definition of done:** a shared PDF is filed to a project in two taps.

### W12: rhythm and review

Spec: D5.1 (notification), D5.6, §4.1, §4.5, §8.4.

1. **A Work notification channel** in `AppNotifications`, respecting working days, working hours
   and quiet hours from `WorkSettings`:
   - **Morning Pulse:** one line, counts only for sensitive profiles. Scheduled with WorkManager.
   - **Prep:** `prepLeadMinutes` before events with known people, opening `PrepareSheet`.
   - **"Starting now — record?":** opens recording with the workflow and title prefilled.
     Opt-in.
   - **Weekly review:** on the review day.
2. **The Weekly Review screen:**
   - the facts
   - the D5.6 questions
   - "Create next week's plan": carry forward, reschedule or drop each open item, in one pass
   - an optional weekly brief

   Extend `WeekReview`; don't fork it.
3. **The Work tab.** When `tabSlot == WORK`, the fourth bottom-bar slot opens the Work space,
   with Inbox · Projects · People · Organisations · Decisions · Commitments. Otherwise nothing
   changes. Test both states.
4. **Marks** from the recording notification's actions (Key, Action and Question) and from the
   lock screen.
5. **A next-meeting widget** in `core/widget`: the next event, a countdown, Record, and the prep
   line.
6. **An accent colour choice** in Look and home (§8.7). It's a token, so it works in both themes.

**Tests:** the scheduling-window logic (working hours, quiet hours, sensitive redaction); the
weekly plan carry-forward; the tab slot in both states; the notification mark action writing a
mark.

**Definition of done:** notifications fire only inside the window; the review creates next week's
plan; the tab slot switches.

**→ Checkpoint.**

### W13: integrations

Spec: D8.

1. **Providers with capabilities.** Build `core/integrations/`, with:
   - `CalendarProvider`, `EmailProvider`, `StorageProvider`, `OutputChannel` and
     `MeetingProvider`
   - a `Capability` enum
   - a registry
   - the device calendar and the share sheet moved behind the interfaces, with their behaviour
     unchanged
2. **The integration centre** (Settings → Integrations):
   - Each row lists what it **enables**, its state, and per-account scope.
   - "Coming later" rows get a Notify me tap, stored locally.
3. **Gmail and Outlook**, behind a build-config flag that is **off by default**.
   - Implement the OAuth flow and the scoped read: messages involving known people and domains
     only.
   - Send stays a draft handoff.
   - Indexed mail becomes evidence in Memory, cited.
   - **The sign-in registrations already exist.** Read `docs/INTEGRATION_CREDENTIALS.md` and use
     the client ids there, chosen per build type (dev client for debug builds, Play client for
     release). They are public identifiers and are committed. **Never commit or paste a client
     secret or token.** You can't create or change registrations. If something there looks wrong,
     say so in the handoff.
   - Disabled in Clinical and Legal unless the person explicitly enables it, with a warning.
   - Never sent to cloud AI when `mustStayOnDevice`.
4. **Storage:** the SAF picker as a provider now. Drive, OneDrive and Dropbox get stubs with
   capability rows only.
5. Write a fake provider for tests.

**Tests:** the registry and capabilities; scope filtering (mail from unknown senders is never
stored); privacy gates; the flag-off state hides the rows.

### W14: views

Spec: D9.

1. **Saved views over items and tasks:** Kanban by status, per project; a lightweight timeline by
   date; the decision log; commitments; risks; people. Drag on Kanban changes status through
   `ItemRepository` or `TaskRepository`, so the event is logged.
2. **Professional X-Ray:** a project's entities as a laid-out graph (people, decisions, risks,
   meetings, tasks), with a short cited explanation from the context pack. It's read-only.
3. No dependency-editing Gantt and no whiteboard.

**Tests:** view queries; a Kanban move logs an event; X-Ray renders with no model.

### W15: automation

Spec: D8, §6.7.

1. **`core/work/Recipes.kt`: Trigger → Context → Skill → proposed actions → approval → output.**
   - **Triggers:** meeting processed (by workflow, project or organisation), schedule, Inbox
     item.
   - **Skills:** minutes, follow-up draft, create tasks, update project, brief, reminder.
2. **Built-in recipes come from the work profile.** Each one is edited as toggles on the
   workflow's settings page and on the project page.
   - The Wrap-up is recipe #1.
   - Recipe #2: *"When a client meeting ends, draft minutes and the follow-up, create the tasks,
     update the project, and remind me Friday."*
3. **Every outward or state-changing action goes into one approval card.** It's the Wrap-up for
   the Wrap-up flow, and a notification plus a card otherwise. Nothing runs past approval.

**Tests:** trigger matching; nothing executes without approval; the privacy gates on skills.

**Definition of done:** the client-meeting recipe runs end to end on a fixture, stopping at
approval.

**→ Final checkpoint.**

## 5. The handoff (at every milestone)

Write `docs/handoff/W<n>.md` and commit it with the milestone. Use these headings:

```
# W<n> handoff
Branch / head commit:
## Built                  (bullets, mapped to the brief's numbered items)
## Not built or changed   (and why)
## Existing code touched  (file:line, why it was necessary)
## Schema                 (version, migration, new tables and columns)
## Tests                  (new test classes; full-suite result: N passed, 0 failed)
## How to see it          (tap path on the phone)
## Risks / questions for the orchestrator
```

At each checkpoint:

1. Bump the version.
2. Build the APK.
3. Run the whole unit suite. Paste the real counts, never "should pass".
4. Push.
5. End your run with a short message that points to the handoff.

## 6. Questions and blockers

- **Decide it yourself** when the plan's intent is clear from D1–D12 and the principles. Record the
  decision under *Risks / questions* in the handoff.
- **Stop and ask the orchestrator** before any of these:
  - changing existing behaviour that main users would notice
  - a destructive migration
  - a new runtime permission
  - a new third-party SDK other than ML Kit
  - anything that sends data somewhere new
  - a design choice that contradicts D10
- **If you're blocked** (credentials, a device-only check, network policy), build everything
  around the blocker, mark it clearly, and continue.

## 7. What the orchestrator checks

Expect every one of these to be checked, so check them yourself first:

- The diff against the previous checkpoint: no deletions or renames of existing code, and every
  touched existing file justified.
- Migrations: a new schema JSON, a migration test, the chain added to the old tests, and a real
  upgrade from a v34 database keeping every row.
- The full unit suite is green, and the APK builds.
- The principles: works without a model, every AI sentence cites something, privacy gates are on
  every new AI and network path, the four-row Pulse budget, nothing outward without approval,
  theme tokens and dark mode, and the identity boundary.
- The Definition of done, demonstrated by a test or fixture wherever possible.
- The handoff matches the code.
