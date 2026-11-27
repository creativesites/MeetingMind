# Crash audit before the next Play bundle (2026-11-17)

The founder asked the team to find and fix anything that can crash the app before the next closed-testing bundle. The app is still in **closed testing**.

Coordinator and only build runner: the Learning session, **"MeetingMind Learning premium UX redesign"**. Send it your test classes and findings.

## How we work
1. **Audit your own area first, read-only.** Then fix only real crash risks: small diffs, with no restyling or refactoring. Leave a finding in someone else's area to its owner and send it to them, with the file, the line and why it crashes.
2. **Never run Gradle.** Send the Learning session the test classes you added or touched. It batches builds, runs them one at a time and reports back. Add a unit test for a fix wherever one fits.
3. **Shared checkout.** Stage by explicit path: never `git add -A`, `git add .` or `git commit -a`. Commit your own files only, and do not push. Never revert, reformat or stash someone else's uncommitted changes.
4. **The phone** (S20+, wireless) is used only by the Learning session. Ask it for a device check.
5. **When you finish,** send the Learning session a short report:
   - each finding, with its file and line, severity and a one-line cause;
   - which findings are fixed, by which commit;
   - which findings are left, and why.
6. **Message a peer** with SendMessage, using the name the peer list shows (ListAgents), or `local_…` ids for desktop sessions. Messages to another session are plain text: `@file` attaches nothing.

## What counts as a crash risk
- `!!`, `lateinit` read before it is set, `.first()`/`.last()`/`[0]` on a list that can be empty, and `error()`/`require()`/`check()` reachable from UI or saved data.
- Number parsing (`toInt()` and similar) on user, file or AI input. JSON from AI or the network, parsed without a fallback.
- Exceptions thrown inside `viewModelScope.launch`, `LaunchedEffect`, collectors and WorkManager workers with no catch. These kill the app.
- Duplicate keys in `LazyColumn`/`LazyRow` `items(key = …)`, which crash Compose. Look especially at ids built from titles, timestamps or indices after a filter.
- Navigation: missing or unparsable route arguments, and navigating with a stale back stack.
- Android 12–14 rules:
  - starting a foreground service from the background (`ForegroundServiceStartNotAllowedException`);
  - a missing `foregroundServiceType`;
  - `SecurityException` from permissions the user denied;
  - `PendingIntent` mutability flags;
  - the notification permission on Android 13.
- Media and audio: using a player or recorder after it is released, missing files after the user deletes them, and an `AudioRecord` that fails to start.
- Memory: loading large audio, PDFs, bitmaps or models fully into memory, and on-device models on low-RAM phones.
- Release builds only:
  - R8 stripping classes that are used by reflection (serialisation, Gson, RevenueCat, Firebase, Room, WorkManager);
  - code that only works when `BuildConfig.DEBUG` is true.
- Saved data from older versions: Room migrations from every version that testers already have, DataStore or JSON that a newer schema no longer reads.

A fix must degrade gracefully. Show a calm message, keep the user's data and never lose a recording.

## Who audits what
Paths are relative to `app/src/main/java/com/craftflowtechnologies/meetingmind/`.

| Owner (session) | Area |
|---|---|
| Home and navigation (`local_3727a92a-70cc-454b-9337-977ec8507b2c`) | `MainActivity.kt` routes and arguments, `feature/navigation`, `core/navigation`, `feature/today`, `feature/settings`, notifications and `PendingIntent`s, share and deep-link intents |
| Notes (`local_23f32fc6-93b2-46bf-b0be-c934095a733d`) | `feature/notes` (editor, blocks, `AcademicBlocks`, Markdown import), `core/notes`, YouTube ingestion, `feature/scripture`, `feature/share`, `feature/synthesis`, `feature/moments` |
| Work (`local_380b4bb9-0119-4e97-b1b0-4ef6d0725775`) | `feature/work` (document viewer, imports, pages), `feature/tasks` |
| Circles (`local_ed70a508-0c3c-47e4-8ad2-04c122913adc`) | `feature/circles`, the Circles API client, Firebase auth, `feature/faith` (Faith Home, plans and prayer, devotionals) |
| Report AI (`local_fb86ad5c-59d8-48ed-b51e-147eb3282997`) | `ai/` (processing and download workers, live transport, diarisation, on-device models, cloud clients), recording and playback services, foreground services |
| Launch experience (`meetingmind-9d`) | the files it is already changing: `feature/onboarding`, `feature/paywall` and `core/billing` (RevenueCat start-up, empty offerings, purchase errors), `feature/models`, `feature/processing`, `feature/search`, `feature/setup` |
| Learning (coordinator) | `feature/learning`, `ai/learning`, `core/database` (migrations, `DatabaseGuard`), application start-up, R8/ProGuard rules for the release build, Play Console crash reports |
| Internationalisation | No crash audit. See "Internationalisation and this audit" below. |

## Internationalisation and this audit
Internationalisation work touches many UI files and `res/values/strings.xml`, which are the same files the crash fixes land in. To keep both moving:
- Crash fixes go first in any file both need.
- Before the i18n session edits a feature folder, it checks with that folder's owner above. It commits in small batches, one feature folder at a time.
- `strings.xml`:
  - add new entries in the block for your feature;
  - never re-sort or reformat the file;
  - a crash fix that needs a string adds it beside its feature's strings.
- A format string must keep its argument count and types in every translation. A `%s`/`%d` mismatch crashes at runtime, so the i18n session checks every placeholder.

## Results (2026-11-18)
Tests: 795 run in one combined run. Every failure was then fixed and re-run green, except `DesignSystemGuardTest` on `core/ui/mm/AiCraft.kt`, which has failed since `8d97b49` and still has no owner.

| Area | Commit | Main fixes |
|---|---|---|
| Circles and Faith | `be56a04` | PDF page render and out-of-memory errors; Firestore documents in a shape the app can't read; two sign-ins at once; a chapter-level highlight; the D5 endpoint 404 copy |
| Work | `dd55453` | Large-PDF memory and out-of-memory errors in the document worker; page-marker overflow; duplicate lazy keys (3 screens); an empty decision chain; unguarded inbox, import and share-in |
| Home and navigation | `75eb305`, `0ded45e` | Today flows with no `.catch`; a double-decoded import path; an unguarded notification deep link; share-in scope; Recipes and Today writes; duplicate calendar keys |
| Notes | `1c8d349`, `23ec10f` | On-device AI stream and polish errors; bad LaTeX and Mermaid parsed during composition; damaged versions; YouTube `t=` overflow; Share Studio and Deep Synthesis failures; a late autosave into a deleted note |
| AI, recording and services | `a2e43d2` | The journal write on a full disk; the service scope; a double recording start; Live voice audio busy; a model larger than RAM; the engine closed during generation; DeepSeek and WAV parsing; foreground starts in download workers |
| Launch experience | (pending) | Unguarded DataStore in billing, onboarding, paywall, search and model manager; the First Keep ticket consumed during cancellation |
| Learning | (this commit) | A cram-sheet save with a non-notebook id (FOREIGN KEY); every Learning task and data flow guarded; overlapping mistake explanations |

Open:
- **Recording keeps running after Back or a swipe** (founder: keep recording, with auto-save). It has a foreground service with a notification, automatic recovery at launch, and processing starts after a stop from the notification. This is in progress (Report AI) and needs an S20+ check.
- **Left, low risk:** DataStore writes with no catch (Home); undo and flow collectors in Notes that fail only if Room fails; `searchHybrid` loads every embedding (`core/repository/Repositories.kt:760`); the remux in `stopRecording` runs on Main (ANR risk on long recordings).
- **Not crashes:** `HtmlToMarkdown` loses text across a table-cell link; `createInitialMeeting` writes RECORDING at stop, which blocks `RecordingTitleBackfill`.
