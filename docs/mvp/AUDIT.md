# MeetingMind MVP audit (v42, schema 23)

Written 2026-10-09 against `claude/meetingmind-mvp-planning-7dgowm` at `e6564d1`, which carries everything on
`claude/meetingmind-polish-professional-8cqp79`: W13–W15, Learning R1, Fellowship, Circles, Spark Studio and the
Home hero. Eight read-only audits fed this document. The most load-bearing claims in it were re-checked in the code by hand.

Paths are relative to `app/src/main/java/com/craftflowtechnologies/meetingmind/`.

---

## 0. One-paragraph summary

The engine is real and mostly sound: local ASR, diarization and LLM work, the Gemini path works, and the note model is
already block-based with provenance, versions and backlinks. **What is not ready is the product around it.** The app
has four interchangeable home layouts and two Work homes. Three verticals are pushed screens with no switcher. About 1
in 20 type, colour and shape decisions goes through the theme. Notes are hard to find. Settings has 42 rows in 12
sections. Onboarding promises privacy while defaulting to the cloud. There is one P0 bug that makes long offline
recordings never finish.

---

## 1. P0 bug: offline processing stuck at 55–60%

**Root cause, confirmed.** `ai/diarization/SherpaSpeakerDiarizer.kt:79` calls `diarizer.process(decoded.samples)`.
That is one blocking JNI call over the whole recording: pyannote segmentation, then a CAM++ embedding per segment, then
clustering, which grows super-linearly with recording length. It has:

- no timeout (no `withTimeout` anywhere in `ai/pipeline` or `ai/diarization`)
- no progress
- no way to cancel it from Kotlin

The pipeline writes "Identifying distinct speakers… 55%" once (`ai/pipeline/MeetingProcessingPipeline.kt:444`). ASR
also ends at exactly 55 (`:290`, `:375`). Cleanup sits at 58–60 with no budget either (`:841`).

**Why it never ends.**

- The worker is a `dataSync` foreground service (`MeetingProcessingWorker.kt:176`). On Android 15+ that is capped at
  about 6 h per 24 h.
- When the system stops it, the job row is never marked failed, so the UI shows 55% forever.
- A multi-hour file is decoded into four full-size arrays (`core/audio/AudioFormatConverter.kt:30-120`).
- `catch (e: Exception)` misses `OutOfMemoryError`.

**Fix (plan task S-1):**

1. Run diarization on a dedicated thread with a budget of about 0.5× realtime, capped at 20 min.
2. On timeout or `Throwable`, fall back to "speakers not separated" and continue.
3. Process long audio in chunks so progress is real.
4. Give every sub-stage distinct percentages, and add a heartbeat on the job row.
5. Mark the job failed on `onStopped` and reconcile orphaned jobs at app start.
6. Add a retry button that resumes from the ASR checkpoint (`ai/asr/AsrCheckpointStore.kt`).
7. Budget the AI cleanup stage the same way.

---

## 2. Navigation and homes

**Bottom bar.** The bar is `Home | Notes | [+] | Search-or-Work | Settings` (`core/ui/BottomNavigation.kt:92`).

- Slot 4 changes meaning with a preference.
- There are about 70 routes in one flat graph.
- Faith, Work and Learn are pushed screens with no bar, reached from cards on Home.

**Home.** Home has four implementations, picked by `Appearance.homeStyle`: Today, Calm, Focus and Professional.
`HomeSection` toggles let users rearrange it further. Work has a second home, `WorkSpaceScreen`, which is about 80%
the same content as `ProfessionalHome`.

**Duplicate entry points.**

| Destination | Places it is reachable from |
|---|---|
| Record | 6 |
| Devotional | 6 |
| Work | 4 |
| Search | 3 |
| Tasks | 3 (three separate lists) |

**Today home, top to bottom** (`feature/today/TodayScreen.kt:248-430`). Each section was run through the 8-point
"Why is this here?" test:

| Section | Verdict |
|---|---|
| Animated sky hero with orb, streak, inbox, search and a scripture/quote banner | Demote to a plain greeting line |
| "Showing X" focus chip | Remove |
| QuickCapture: Record, Note, Import | Merge into one global Record |
| Setup and Getting Started cards | Make a one-time dismissible strip |
| ForYou row: devotional, processing, rhythm, on-this-day, week, Work card | Split. Processing becomes a pill; the Work card is pure navigation, so remove it |
| StoryRings | Move to Faith |
| QuickAccessRow (six Faith deep links) | Remove |
| Calendar plus Agenda, Day, Week, Month and River views | Keep Agenda, and move the other views to the overflow menu |

**Faith home** (`feature/faith/FaithScreens.kt:116-420`) is 17 stacked blocks.

- Merge the devotional and the verse of the day into one "Today's Word".
- Merge the reading plan and "praying for" into one "Daily rhythm".
- Make Record sermon the single primary action.
- Remove the stat cards and the Backgrounds block.
- Group Spark, Fellowship and Testimonies under "Explore".

**Work home.** There are four competing hero treatments: the sky card, the Pulse card, the accent gateway cards and
a five-circle capture strip. Pulse is the one that earns its place.

- The metric tiles duplicate counts shown elsewhere. "To send" opens the wrong place.
- "Open the Work space" is a gateway banner at the bottom of the Work home.
- The Faith strip and Stories sit inside Work.

## 3. Design system

**Tokens that exist.** `MMColors` role tokens (Paper/Graphite) and a 10-style Material `Typography`.

**Tokens that are missing.** Spacing, radius, elevation and motion.

**How far the screens bypass the theme:**

| Pattern | Count |
|---|---|
| Inline `fontSize = N.sp` | 1,580, against 69 `MaterialTheme.typography` uses |
| Distinct font sizes | about 20 |
| `Color(0xFF…)` literals | 173 |
| `RoundedCornerShape` uses | over 400 |
| Distinct corner radii | about 15 |

Other inconsistencies:

- Screen gutters are 16 or 20 depending on the screen.
- Faith titles are serif; Work and Today use sans.
- Filled and outlined icons are mixed, including within the bottom bar.
- The section header has been re-implemented three times.
- The legacy `CleanMac*` and `Dark*` palettes are still present.

**Worst offenders:** `FaithScreens.kt`, `DevotionalScreen.kt`, `LearningSessionScreen.kt`, `PlansAndPrayer.kt`,
`PraySession.kt`, `WorkSpaceScreen.kt`, `BibleScreen.kt`, `SettingsScreen.kt` and `TodayScreen.kt`.

## 4. Notes

**The model is strong.** It already has:

- notebooks of kind NOTEBOOK or PROJECT
- typed blocks with rich spans, source provenance and `sourceSegmentIds`
- attachments, tags, typed links and backlinks
- gzipped versions, private notes, a 30-day trash and FTS

**The editor is good.** It has autosave, undo and redo, a formatting toolbar, an insert sheet, AI tools including
per-block "Edit with AI", version history, and export to PDF, Word and Markdown.

**The surface is the problem:**

1. **No stable Recent notes on the default home.** A note shows only on its own day in Agenda, and the Focus style has
   no notes at all.
2. **The Notes tab puts about five rows of chrome above the first note:** space pills, vertical cards, notebooks and
   tags.
3. **List rows say too little.** They show no indicator for recording, transcript status, tasks, attachments or tags.
4. **Filters are single-select.** There is no filter by type, has-recording or has-tasks.
5. **Library search bypasses FTS.** It is an in-memory `contains`, and it matches private-note text, which global
   Search excludes.
6. **Some editor features can't be reached.** TABLE, CODE and EMBED blocks exist but cannot be inserted. FILE
   attachments are a no-op.

## 5. Work (Professional)

The pipeline is sound. **Recording → wrap-up → follow-up → tasks** works end to end.

**Hide for MVP, behind flags:**

- Kanban: cards are inert (`ProjectScreen.kt:207`).
- Timeline, X-Ray and Saved views.
- Recipes: `RecipeEngine` is never invoked by the pipeline, so automation never fires.
- Gmail and Outlook: the fetchers are stubs returning `emptyList()`, and there is no sign-in flow.
- The 8 "Notify me" stub integrations.

**Service fixes before launch:**

- **`runBlocking` on DataStore in property getters.** In `core/integrations/IntegrationRegistry.kt:23-50` these are
  read from Compose, which is an ANR risk.
- **`WorkViewModel` queries are unbounded:**
  - `titles` observes `SELECT * FROM meetings`.
  - `tasks` has no limit.
  - `projects` does an N+1 count on every task toggle.
- **`WorkViewModel` is scoped to the Activity,** so its flows stay hot after the user leaves Work.
- **`WrapUpViewModel` is built with `remember`,** so its draft is lost on process death.
- **Two infinite transitions run in the idle home header.**

## 6. Faith

**Solid:**

- Sermon pipeline: transcript, scripture detection, chunked extraction, verbatim quotes and the Faith Note
- Devotionals
- Bible reader, including offline and original languages
- Study workspace
- Reading plans and the prayer list
- Testimonies, Journey and the Faith lock

**Rough:**

- **Silent degradation.** With no model or a failed extraction, the note holds only scripture and the user is not told.
- **No retry for truncated chunks.** Each chunk has a 1,500-token output cap.
- **Two copies of the faith contract,** one in Kotlin and one in assets.
- **No sermon-to-devotional action.**
- **Ask Sermon has never been run against a live model.**
- **Verse of the day is empty offline.**

**Fellowship and Circles**

They are about 4,400 lines including data and tests. Defects verified in the code:

- Roles and authorship come from the encrypted payload, so any member can forge ADMIN.
- MemberLeft never reaches Room.
- The event log has no edit, delete or cap.
- A failed create leaves a half-made circle and throws an unhandled exception.
- The invite key is stored in plaintext in Room, so it ends up in backups.
- The privacy copy overclaims.
- An internal label, "Tier 2", is visible to users.

Verdict: **replace, don't fix.**

**Spark**

It is about 1,430 lines and has four entry points under three names.

- There is no faith contract in the prompt.
- Remix can pair a verse's text with the wrong reference.
- Offline remix is string substitution.
- Failures are silent.
- There is a race between remix and generate.

Verdict: **replace, don't fix.**

## 7. Learning

- About 5,500 main lines plus 1,330 test lines.
- Tables come from `MIGRATION_21_22`: `learning_sessions`, `learning_concepts`, `learning_activities`,
  `activity_attempts` and `review_schedules`.
- Every v42 install has them.
- Teach-Back writes files to `filesDir/teach_back`.

**Keep from it:**

- `RecordingType.LECTURE` and `RESEARCH`, which are core types
- `feature/study/StudyWorkspace.kt`, which is the Faith study workspace
- The pure `DeterministicSpacedScheduler` and `MasteryCalculator`, which are tested
- The old plan's principles: source before synthesis, mastery earned rather than assumed, and reuse of the
  note/transcript model instead of building a parallel pipeline

**Removal path:**

1. Remove the UI and routes, keeping the tables, with no schema bump.
2. In a later release, `MIGRATION_23_24` drops the tables child-first and remaps `NotebookSpace.LEARNING` to
   `PERSONAL`.

## 8. Settings

There are 12 sections and 42 rows, 4 of them conditional.

**Set in two places:**

- `theme_mode`, written by Settings and by the Appearance screen
- `wifi_only_download`, written by Settings and by Setup
- Calendar, with two switches holding separate state
- Name
- Look & feel, which overlaps Look and home
- "Include recordings", which appears twice in backup

**Five preferences that no screen writes:** `battery_saver`, `audio_sample_rate`, `auto_stop_minutes`,
`cloud_sync_enabled` and `last_recording_type`.

**Notification controls are split across three places:** Work settings, Faith reminders and the Devotional sheet.

**User-visible developer text:** "Built-in tester key", "send it to the developer", and "verse text isn't set up in
this build".

**"Clear all local data" deletes meetings only.** Notes and tasks are kept, and the label doesn't say so.

## 9. Onboarding and first run

**The flow is 7 screens:** Welcome, What it does, Name, Spaces, AI setup, Bible, Permissions. Each is text-only
input before any value is delivered.

**Problems:**

- **The privacy promise doesn't match the default.** Welcome says "Privately, on your phone"
  (`OnboardingScreen.kt:293`), but the default is `SetupChoice.INTERNET` (`OnboardingViewModel.kt:140`).
- **Permissions are requested twice:** at launch (`MainActivity.kt:243`) and on step 6. There is no Settings link
  after a permanent deny.
- **Every space is on by default,** so personalization doesn't change the home.
- **The name is never used on the home.**
- **Goals are not captured.**
- **Three first-run layers stack on Home:** the tour, Getting Started and the setup card.
- **There is no first-recording success moment.**
- **There are no animation libraries and no illustrations.** No Lottie, no Rive, no mascot. Animation is pure Compose.
  The fonts are Inter and Outfit.

## 10. Baseline build and tests (2026-10-09, `e6564d1`)

- **`compileDebugKotlin`:** green, with 0 warnings.
- **`testDebugUnitTest`:** 1,463 tests, **3 failing before any MVP change**:
  1. `ExampleRobolectricTest`: environmental. It could not download the Robolectric `android-all` jar in the sandbox.
  2. `TodayScreenTest` "today lists the day and opens a card": looks for the `view_river` tag, which the latest Home
     commit no longer renders. Fixed by U-1, which rewrites Home.
  3. `ColorLiteralGuardTest`: the latest Fellowship/Spark commits added colour literals (`SparkStudioSheet.kt` has 8,
     and `TestimoniesFeedScreen.kt` has some too). Fixed by S-8 and F-4.
- **Build gate for merges:** no *new* failures beyond these three until they are fixed.
- **Sandbox note:** Maven Central rate-limits this sandbox (HTTP 429). A local, uncommitted init script at
  `~/.gradle/init.d/central-mirror.gradle` routes it through Google's mirror.
