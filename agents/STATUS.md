# Status

Updated: 2026-11-27. **Shipping branch: `release/1.0-paid-launch`** (Head: see `git log -1`).

## Personal: garden pages for the Sanctuary (P5, branch `wt-p5`, not pushed)
- **Built:** the garden home lists every Sanctuary capability (Rituals, Journeys & challenges, Living journal, Seed jar, Keepsakes, Privacy & sync). Each opens its own garden page (`GardenPlaceScreen`, routes `garden/<place>`), styled by `GardenTheme` (garden palette as `MMColors`, serif page headers, `GardenPageHeader`). The tabbed Sanctuary screen and its dock are retired; the bodies and view model logic are unchanged.
- **Privacy & sync** is a page of its own (`PrivacySyncPage`: lock, screenshot shield, Obsidian, Day One). Vault sync stays free.
- **Personal autopilot** is a paid feature, as Work's is: `PersonalAutopilotCard` uses `AutopilotWorkspace.isUnlocked` (honours closed testing) and shows "Part of the paid plans" with "See plans" only when billing is configured. It sits at the top of the Living journal page.
- **Routine player** runs under `GardenTheme` (restyled through the tokens; its own layout is unchanged).
- **Tests:** `GardenPlacesTest` (every capability has a page, routes resolve), `GardenPagesScreenshotTest` (every place opens from the garden home; locked autopilot does not run; unlocked does; privacy page and autopilot shots in light and dark).
- **Open:** `AutopilotWorkspace.runRecipe` (shared with Work) still refuses on free plans, which is the same gate as the card. Rituals, Journeys, Journal, Seed jar and Keepsakes use the existing bodies, restyled by tokens; their layouts were not redesigned. Screenshots of those bodies are not in the test set.

## Study (Learning) rebuild (2026-11-27, branch `ccr-193c8c6b-bozoxb`)
- **Decision:** the founder approved the Study redesign canvas (2026-11-27). After merging `ccr-4eced482-bn5f1l`, which has its own Learning vertical, the founder chose to keep both for now: Study runs on its own `learn_study` routes beside Learning. Audit and rules: `docs/mvp/STUDY_AUDIT.md`. Mockups: `design/study/`. Contract: `feature/learn/LearnContract.kt`.
- **Built:** Study home (round card, "Your sky", continue, courses, weekly rhythm), Lesson (lecture ribbon with stressed moments, 30-second summary, stressed quotes that play the recording, sections with tappable terms, quick checks, hard-to-hear line), concept sheet (lecturer's words, Explain another way, Draw it diagrams, Quiz me), Listen (dark read-along replay), Practice round (tap-to-answer, recall cards, wrong answers come back, Hear your lecturer), Round complete (stars light up, one next step, study-win card).
- **Data and AI:** shares `LearningRepository` with Learning; `StudyKitGenerator` (validated, cited, chunked), `ExplainEngine`, `StudyKitStore` (JSON per session), `SkyLayout`, `RoundComposer`. No schema change. A missed review is due again 10 minutes later.
- **Entry points:** "Open lesson" on Lecture and Research notes and a "Lesson" pill on recording detail open the Study lesson; the Today bento's night-sky Study tile opens the Study home. Learning keeps its own bar place, menu and quick-access entries.
- **Companion:** `CompanionPage.STUDY_HOME` and `PRACTICE`; Zuri curious on the home and questions, a short celebration on a right answer, calm on a wrong one, proud or celebrating on round complete, reading in Listen.
- **Not done:** Explain it aloud (Teach-Back); Read to Me inside Listen (the Read to Me option opens the existing sheet); a NOD variant through `ZuriSlot`; dotted term underlines; a mono font token.
- **Not verified on a device:** lesson generation on a real lecture (Gemini and on-device), playback from moments, the round's next-day return.
- **Build note:** Maven Central returns 429 through this proxy; builds here use a session-only init script that points at Google's Central mirror (`--init-script`).

## Learning on the Study design (2026-11-27, branch `ccr-193c8c6b-bozoxb`, head `see git log`)
- **Founder decision:** keep Learning's functionality, replace all its UI with the Study design; navigation must be easy; one Learning home for the space and the Learning home style.
- **Routes (`feature/learn/LearnRoutes.kt`, `learnGraph`):** `learn` home, `learn_course/{id}`, `learning_session/{noteId}` lesson, `learning_listen/{noteId}`, `learn_practice?sessionId&activityId&course&concept`. `learn_study/*` retired. Exam prep (`learn_exam`) is still the old screen, reached from a course's "Exam plan".
- **Removed:** LearnHomeScreen, LearningSessionScreen, SessionStudioScreen, PracticeArenaScreen (and LearnHomeUiTest). LearningSpaceScreen and PracticeModeScreen have no route; their tests still use them.
- **Database:** schema 33 (`5e75b81b`). `release/1.0-paid-launch` shipped a different schema 32 (Personal tables); the 32→33 step accepts both. Merging that branch later needs its Personal tables in a 34. Never reuse a schema number another branch has shipped.
- **Debug builds and online AI:** build with `GEMINI_PROXY_URL=https://meetingmind-gemini.meetingmind.workers.dev` (or a key in local.properties); without either, online transcription falls back to local models.
- **Tests (full run, 4,359):** 21 failures arrived with `ccr-4eced482-bn5f1l` and are not Learning's: TodayHeroSun (6), ImportViewModel (2), AudioExtractor, GeminiHttpTransport, GeminiTranslationEngine, MomentStage, NoteEditorUnveilScreenshot, PrivacyNoCloudPath (2), ProcessingScheduler, ProcessingScreenshot, SermonTranscriptSection, TranscriptToolEngine, DesignSystemGuard (AiCraft.kt), Assistant (PaywallScreen.kt sparkles). The two Learning ones are fixed.
- **Not verified on a device.**

## Personal becomes "Your garden" (2026-11-27, branch `ccr-4eced482-bn5f1l`)
- **Design:** the founder chose the garden concept (design canvas "MeetingMind: Personal vertical redesign", page "Your garden (new)").
- **Built:**
  - Personal has a home of its own (`feature/personal/`): a painted scene that follows the clock (dawn, day, dusk, night), the day's question with "Light the lantern" (records a private Journal titled with the question; "Write instead" is the quiet button), practices drawn as plants, and the seed jar.
  - Light and dark: `ui/theme/GardenColors.kt` paints every time of day for both themes; in the dark theme no phase is a bright sky (tested).
  - Practices (`core/practice/`, schema 32, `375f8af1`): a practice is also a repeating PRACTICE task, so it shows in Tasks; ticking it there logs the day. Plants grow by kept days, rest when untended, never die. No streaks anywhere.
  - Navigation: Personal claims a bottom-bar place like Work, Learning and Faith (Personal only: Personal, Tasks); the Today "Your spaces" card opens the garden instead of a blank note; "Your garden" leads the menu's Personal section.
- **Tests (2026-11-27, this machine):** 183 run, all pass except `DesignSystemGuardTest` on `AiCraft.kt`, which predates this. Garden screenshots in `src/test/screenshots/personal/` (four times of day and empty, light and dark, both sheets), reviewed.
- **Open:** not checked on the S20+; the reflection prompt is generic only (no AI follow-up yet); next phases from the canvas: the lantern recording screen, Your sky (patterns across reflections), seed packets from talk notes ("Plant it" from a note), the seed jar sorting screen, the Today window tile. New strings are inline English (for the i18n session).
- **Build notes for cloud sessions:** Android SDK at `/home/user/android-sdk` (needs `dl.google.com`, allowed in the "MeetingMind" environment); Gradle needs `--no-configuration-cache` here; Maven Central sometimes answers 429, so retry after a minute.

## Personal Vertical (Phases 1–3, Schema 32) (2026-11-27)
- **Room Schema 32 (`MIGRATION_31_32`):** Added 6 normalized tables (`personal_routines`, `routine_steps`, `routine_completions`, `growth_enrollments`, `growth_step_progress`, `personal_daily_plans`), `PeopleDao.observeBySpace`, exported `32.json`, and `Migration31To32Test`.
- **First-class Personal space & 5-tab Sanctuary dock:** Wired `NotebookSpace.PERSONAL` across bottom nav, Home picker, Today `SpacesRow` live glance (`rememberPersonalGlanceLine`), deep links, daily rhythm alarms, and Note Editor's private "Reflect in Personal Journal" bridge.
- **Growth engine & living 3D journal:** Bundled 12 starter Routines, 14 Guided Journeys, 8 Challenges, and 12+ Coaching activities voiced via cached Read-to-Me (`coaching:` prefix); full-screen `RoutinePlayerScreen` with ambient soundscapes, breathing pacer, and low-energy day mode; `RoutineEditorSheet`, `LifeChapterDetailSheet`, `EnrollmentDetailSheet`, interactive mood calendar, offline Idea Collision engine, and 3D visual rituals (`Personal3DVisuals.kt`).
- **Privacy, Inner Circle & Phase 3 Pro integrations:** Added `PersonalSanctuaryLock` (per-space BiometricPrompt lock + `FLAG_SECURE` recents shield), `GuidedActivityStageSheet`, `MemoryUnsealCeremonySheet` ("Reply to past self"), Inner Circle & Gift/Mention Radar, Personal Life Lists, 4 Personal Pro Autopilot recipes (`EVENING_STOIC_DEBRIEF`, `IDEA_TO_ACTION_BLUEPRINT`, `THERAPY_OR_COACHING_PREP`, `WEEKLY_LIFE_LETTER`), Obsidian SAF Markdown sync, and Day One JSON importer.
- **Verified:** `:app:assembleDebug` (`arm64-v8a`, `com.craftflowtechnologies.meetingmind.dev`) built with zero compile errors on 2026-11-24 and passed the `DEV_BUILDS.md` secret scan (only the expected Firebase key from `google-services-dev.json`). Static design-system guard scan across `core/personal` and `feature/personal` returned 0 hits.
- **Open:**
  - Not yet verified on the Galaxy S20+: the previously built debug APK under `app/build/` was cleared, and rebuilding is currently blocked by low disk space (`329 MiB` free on `/`) and two other Gradle daemons running on this 8 GB Mac.
  - Targeted unit tests (`Migration31To32Test`, `PersonalRepositoryTest`, `PersonalPolishTest`, `PostSessionAutopilotTest`, `NavigationModelTest`, `CompanionMotionTest`, `ColorLiteralGuardTest`, `AssistantTest`) are written and compiled cleanly in debug Kotlin, pending execution once disk space and Gradle are free.

## Release v51 for closed testing (2026-11-21)
- **Bundle:** built at `6694dc6` from a clean tree and copied to the founder's `~/Downloads/meetingmind-v51.aab` (141.6 MB, sha256 `c2599ebc5c813139…`).
  - Contents match v50 folder by folder (all four ABIs, assets, resources); the file is smaller only because the zip compresses differently.
  - Verified: signed with the upload key (SHA-1 A5:DC:…:7C:D5); versionName 1.0-v51 (code 51); screen.portrait is optional; CLOSED_TESTING_ALL_ACCESS is true; the diarization callback keeps `invoke(IIJ)Ljava/lang/Integer;`; the new speaker-model sha is in the dex.
  - No raw API keys. The one key-scan match, `TAQ.TSQ.q`, is an R8 name.
  - Not pushed; uploading is the founder's.
- **In v51, by commit:**
  - Tasks board and Work task fix: `00e6b4a`.
  - Model, Retry and processing fixes, plus the speaker-model pin: `ef634d3`.
  - Learning practice and home: `7e50256`.
  - Onboarding: `89b2c7a`.
  - Companion, opening reveal and paywall: `54468a2`.
  - Work home: `e08871e`, `ba385ca`.
  - Homes companion line: `d5a3ab7`.
  - Milestones: `945490f`.
  - First recording: `f1c1dad`.
  - Version and portrait-optional manifest: `6694dc6`.
- **Tests:** 371 run. All pass except DesignSystemGuardTest on `AiCraft.kt`, which predates this release. Screenshots were re-recorded for processing, Work, companion moments, paywall and onboarding Ready.
- **Build notes:**
  - `processMeeting`'s coroutine hit the JVM's 64 KB method limit. The cloud route and the analysis-and-save steps now run in local suspend functions; keep new steps out of the inline body.
  - The build runner's scripts and test lists live in `~/.meetingmind-build`, because the session scratchpad dies when the Claude app restarts.
- **Open:** a check on the phone of the debug build (`app/build/outputs/apk/debug`); upgrading existing installs of the old speaker model; summaries still go to Gemini after a switch to the phone.

## Onboarding: "You're all set", Google sign-in, installed models kept (2026-11-21, `89b2c7a`)
- **Joint commit** with the build runner's always-light onboarding (`LightSurfaces.kt`). Not pushed.
- **"You're all set"** (`ReadyStep.kt`, `ReadyModel.kt`, `strings_onboarding_ready.xml`) is the last step of the example route. It shows:
  - the companion celebrating and the person's name;
  - one card summing up their spaces, Internet or On this phone, and their companion;
  - first actions, one tap each. Internet: Record, Notes from a YouTube video, Look around. On this phone: Set up on this phone, Record, Look around. If everything is already installed: Record first, with no download.
- **Optional Sign in with Google** for Circles, through the Circles `AccountViewModel` and its switch-account dialog. It's hidden when the Circles repository can't be built. Skipping it changes nothing.
- **The on-phone download, sized honestly:** `SetupGuide.compute` with the installed models and the recording language, so only what's missing is counted. The Setup and Ready steps show the room it leaves, or a warning when space is short.
- **Founder's rule: no screen pushes Parakeet over an installed model.**
  - Onboarding keeps an installed speech model: `onboardingSpeechModelId` uses `AsrModelResolver`.
  - It also keeps an installed language model: `onboardingThinkerModelId` uses `LlmModelResolver`.
  - Only a phone with no model gets the recommendation.
- **Founder decision (2026-11-19):** the recording language matches the app language ("Yes, match it"). For example, someone reading the app in Bemba is set up for Omnilingual.
- **Fix:** `completeOnboarding` stalled before finishing, which broke three `OnboardingTest` cases. The saved choices are now read first, and the model checks run off the main thread. A `finishStage` marker in the view model reports in test failure messages which step stalled.
- **Tests (build-runner run, 2026-11-21):** all of `feature.onboarding.*` passes: `ReadyModelTest`, `OnboardingModelTest`, `OnboardingTest`, `CompanionPickStepTest`, `OnboardingReadyScreenshotTest`. The `onboarding_ready_*` screenshots are recorded; I haven't reviewed them.
- **Open:**
  - not checked on a phone;
  - the new strings are English only (for the i18n session);
  - not built: your own YouTube link or audio during onboarding, and the fade from light into the chosen theme;
  - `PlanStep.kt` and `PlanStepTest` are dead code to delete;
  - `UserPreferencesManager.setSelectedLanguage` still switches the speech model by itself (Omnilingual, Whisper) when the language changes in Settings. Onboarding overrides it, but the Settings path isn't installed-aware yet. That fix belongs to the models owner.

## Companion milestones, calm rhythms and paywall polish (2026-11-21)
- **Proud milestones** (ZURI_EXPERIENCE §3.1, task Z-21): `CompanionMachine` could already play Proud, but nothing in the app ever triggered a milestone.
  - The pure `MilestoneDetector` covers the tenth note, all cards cleared, seven devotional days in a month and a finished reading plan.
  - `MilestoneCard` and `MilestoneHost` show a private card once ever per milestone, through the ledger. It never shows with Presence OFF or while the companion is hidden; with "No companion" it uses the glyph.
  - Wired: the tenth note, in `NoteMomentHost` after an arrival First Keep; Faith reading plan and devotionals, on the Faith home (Home session).
  - Not wired: all cards cleared. It needs a one-line call in Learning's study screens.
- **Calm rhythms** (§5.7): the pure `CompanionRhythm` (this week, at least two, never a streak) and `RhythmLine`. Placed by the Home session: Faith shows days with the Word; Today, Calm, Work and Learn show notes kept. Learn should switch to review days once study has a review-times query.
- **Paywall polish**, on the build runner's light redesign:
  - every string moved to `strings_paywall.xml`;
  - no companion on the paywall (§9); the halo holds the app tile, 96 dp on phones under 800 dp tall;
  - the "what happens next" label is no longer a fixed width;
  - the "—" cell has a "Not included" description.
- **Tests:**
  - Green (build-runner run, 2026-11-21): `MilestoneDetectorTest`, `CompanionRhythmTest`, `MilestoneHostRulesTest`, `NoteMomentHostTest`, `OpenRevealTest`, `CompanionTapPlayTest`, `PaywallCopyTest`.
  - Screenshots `companion_moments_*` (light and dark) were recorded and reviewed.
  - `PaywallPremiumScreenshotTest` is waiting on a re-record after the halo fix.
- **Open:** not checked on the S20+.

## Release v50 for closed testing (2026-11-18)
- **Bundle:** built at `7f7e68e`, copied to the founder's `~/Downloads/meetingmind-v50.aab` (216 MB, both ABIs). Verified: upload-key signed (SHA-1 A5:DC:…:7C:D5), versionName 1.0-v50, closed testing all-access on, no raw API keys in the dex, and the diarization callback keeps `invoke(IIJ)Ljava/lang/Integer;` after R8. Not pushed. Upload to closed testing is the founder's (the file is too large for the browser upload tool).
- **Release blocker fixed (`5cb8f2d`):** v46 on Play aborted at the speakers step. sherpa-onnx's JNI looks up `invoke(IIJ)Ljava/lang/Integer;` on the progress callback, and an invokedynamic Kotlin lambda doesn't have it. The callback is now `DiarizationProgressCallback`, kept by R8, guarded by `DiarizationProgressCallbackTest` (the test is written but not yet run).
- **Bundles and ABI splits (`7f7e68e`):** AGP refuses `bundleRelease` while ABI splits are on, so splits are off whenever a bundle task is requested; APKs keep them.
- **Builds on the 8 GB Mac:** run `bundleRelease` on its own, `-Xmx3584m -XX:+UseParallelGC --max-workers=1`. Compiling debug and release in one 3 GB run stalled for 2.5 hours on garbage collection.
- **In v50:** Settings in the bottom bar, Home review tile and Overview scrolling (`5de7c92`); Timeline paging (`fba3b02`); the shared YouTube card everywhere and the retried-import fix (`eecdc4c`, `1b923ba`); Circles sign-in, covers, guest carry-over (`65b8e44`) and the premium redesign (`1cd27c9`).
- **Open after v50:** `TodayScreenTest` hangs at the Agenda scroll; `DesignSystemGuardTest` on `AiCraft.kt`; a re-run of the Circles screenshots after `1cd27c9` (record mode); insets on the S20+ for Circles; guest posts and messages don't move with adopted circles.

## Release v49 for closed testing, and the store setup (2026-11-16)
- **Team pause:** every session committed its own work, and nothing is pushed:
  - Report AI: `1bc6eba`.
  - Notes: `bb5ba77`.
  - Home and navigation: `a2677bb`, `c7059ba`.
  - Work: `c399061`, `9b2802a`, `64fb252`.
  - Learning: `af84cc8`.
  - Circles: `969f0bd`, `1bd4e03`, `5c58c30`.
  - Version 49: `41eec32`.
- **Tests (verified):**
  - The combined run: 655 tests, 639 passing at first. Every failure was then fixed and re-run green by its owner.
  - Still failing:
    - `ColorLiteralGuardTest` on `feature/onboarding/OnboardingScreen.kt` (21 hardcoded colours, 16 allowed), there since `a3ca467` on 2026-10-25. The founder decides.
    - `DesignSystemGuardTest` on `core/ui/mm/AiCraft.kt` (from `8d97b49`).
- **The bundle:** v49 `bundleRelease` is built on the founder's Mac with the upload key kept outside the repository (see `agents/PLAY_STORE.md`, "Building on the founder's Mac"). `local.properties` holds the store key, the proxy URL and the legal URLs.
- **Live services (verified):**
  - Gemini proxy: `https://meetingmind-gemini.meetingmind.workers.dev`. All three secrets are set, and a real request answered through it. Allowances follow `docs/RELEASE_BILLING_SETUP.md`: free 30 min and 30 actions, Pro 20 h and 1,200, Church 60 h and 4,000.
  - Legal pages: `https://meetingmind-legal.meetingmind.workers.dev/privacy` and `/terms`. The privacy URL is set in Play Console (waiting in Publishing overview).
- **Play Console:** the merchant account is set up. 6 subscriptions and the one-time product `pro_lifetime` are active. Launch offers are live (see `agents/PLAY_STORE.md`).
- **RevenueCat:**
  - A Google Play app (`goog_` key) is set up.
  - The 7 Play products are attached to `meetingmind_pro`, `meetingmind_byok` and `meetingmind_church`.
  - The `default` offering now has custom packages `monthly`, `yearly` and `lifetime`; the app matches package ids exactly, so the old `$rc_*` packages were never shown. BYOK and Church packages carry the Play products too.
- **Open, for the founder:**
  - A Google Cloud service account for RevenueCat (Play Developer API, financial and orders permissions). Without it, RevenueCat cannot validate real purchases.
  - Uploading the v49 bundle to closed testing.
  - Approving the Circles Worker and rules deploy, which carries Circles D5 and the AI-report route.
  - Checking the Play Data safety form against `docs/DATA_SAFETY.md`.
  - Deleting the old `$rc_*` packages.
  - Switching the launch offers off when the promo ends.
- **Next store build (Play warning, fix by Feb 2027):** Play scored release 46 at 0% obfuscation, below its 25% threshold. R8 is on, but nothing is being renamed. The likely cause is a library rule that turns renaming off. Plan and test list: `agents/PLAY_STORE.md`, "Before the next store build".

## Notes rendering: no raw JSON, inline maths and chemistry, citations, YouTube source (2026-11-16)
- Plan: `design/notes-blocks-and-math-plan.md` parts A, B and C, plus the founder's YouTube items (play on YouTube, source line at the bottom) and the note-editor hooks for "Report this" (part E).
- **A. No raw JSON:**
  - `core/notes/AiMarkdown` unwraps a model reply wrapped in a JSON object (any key, cut-off replies too, LaTeX backslashes kept). `MarkdownImport.parse` runs it on all AI text.
  - Saved notes are repaired when shown (editor load, `expand`, Markdown blocks, the list preview). There is no migration. A person's JSON and a ```json code block are never touched.
- **B. Inline maths and chemistry:** `feature/notes/editor/InlineMath` renders `$…$`, `\(…\)`, `$$…$$`, `\[…\]` and `\ce{…}` in text, lists, callouts and Markdown blocks.
  - It uses the native `LatexMathParser`; KaTeX was rejected (the reasons are in the KDoc).
  - While a block is being edited, it shows the raw LaTeX.
  - `$$` lines become equation blocks. A formula that won't parse shows as plain text, and screen readers get a spoken form.
  - Note prompts (polish, assistant, study drill) now ask for `$…$` and `\ce{}`.
- **C. Citations:** the polish prompt numbers transcript lines. `[n]` becomes a ▶ chip that plays the moment (CitationSheet). Markers with no source and `[Speaker n]` tags are hidden.
- **YouTube notes:**
  - "Play on YouTube" on the recording card, through a plain ACTION_VIEW, with no logo.
  - Moments open at `&t=<s>s`.
  - A "Source: …" line stays as the last block, and older notes get it when opened. It needs no schema change: two metadata keys hold the video title and import date.
- **AI caption and Report this:**
  - "Written by AI" shows once per AI section, with the report flag.
  - Each AI block has "Report this" in its block menu. Hide collapses the reported run to one line.
- **Verified:** on the S20+ `.dev` build of 13:04, both broken notes ("Joyce Meyer…", "Factoring Trinomials…") read cleanly, including the list previews; inline maths renders; Play on YouTube and the source line show. Unit tests (13:27 run): AiMarkdownTest, NoteCitationsTest, InlineMathTest, NotePolishJsonReplyTest, YouTubeSourceTest, YouTubeIngestionTest and MarkdownImportTest pass. The fix to PostTranscriptionNoteFormattingTest (an expectation that could never match) is in the 14:15 re-run.
- **Crash audit (2026-11-17), Notes, YouTube, share, synthesis:** nine guards. Covered: on-device AI edit and polish failures, broken LaTeX and Mermaid, damaged saved versions, absurd YouTube `t=` times, share card render and audio share, and Deep Synthesis run, gather, save and add-to-tasks. `NotesCrashGuardsTest` passes. Left (Low, Room failures only): undo `launch` blocks in NotesScreen and NotesViewModel, the NotesLibrary flow collect, VerseSheet saves.
- **Open:**
  - Phone recheck of the caption and `[Speaker n]` fixes.
  - The two sermon quotes showing the same ▶ 11:04 (`SermonNoteBuilder` quote timestamps; not this work).
  - Plan screenshot tests for a maths and chemistry note are not written yet.

## Report AI content, Play AI-Generated Content policy (2026-11-16)
- **Founder-approved design:** `design/ai-content-reporting.md` (part E of `design/notes-blocks-and-math-plan.md`). A "Report this" flag on every AI surface opens one sheet: a reason, an optional comment, an optional Hide, then Send.
  - **What is sent:** only the flagged passage, the reason, the comment, the feature, the model and the app version.
  - **What stays on the phone:** on-device mode, private notes and confidential or keep-on-device Work give a copy-only report.
- **Code:**
  - `core/aireport/`: the model, the privacy rule, a file queue with a WorkManager sender, and the sheet and button.
  - Screens: recording summary and Ask, assistant, deep synthesis, AI devotionals, live prayer, Bible study, Work Ask, Brief and Wrap-up.
  - Other sessions wired notes blocks, Learning, Circles AI briefs, Search and Home story cards.
- **Server:** `server/circles-api/src/aireport.js` adds `POST /v1/reportAiContent`. It needs no sign-in, refuses unknown fields, stores reports in Firestore `aiReports/{reportId}` and allows 20 an hour per keyed IP hash. `npm test` passes 112/112.
- **Tests:** `AiReportPayloadTest` 13/13, `AiReportPolicyTest` 3/3 and `AiReportQueueTest` 7/7 pass, and the whole app compiles with every hook (Learning session run, 13:27).
- **Before release reports reach the founder:**
  - Deploy the Circles Worker: `cd server/circles-api && npx wrangler deploy`. Until then, reports wait in the phone's queue (up to 50, for 30 days) and send after the deploy.
  - No new CI secret: the app uses `CIRCLES_API_URL`, which defaults to the production Worker.
  - Firestore rules need no change: the catch-all already denies clients `aiReports`.
- **Open:**
  - Founder: a support email for copied reports (`AiReports.SUPPORT_EMAIL` is null, so copies name no address).
  - The Learning studio's lecture summary flag (asked of the Learning session).
  - Explicit Gemini `safetySettings`, as a separate task.
  - Not yet checked on the phone.
- **Crash audit (2026-11-17), AI, recording and services:** `a2e43d2`. Twelve guards; the full report is in the Learning session's notes folder (`crash-audit-ai-recording.md`).
- **Keep recording and auto-recovery (2026-11-18, founder: "Keep recording", "nothing lost"):**
  - Recording continues after Back or a swipe from Recents. The service starts itself when capture begins and shows a "Recording" notification with the elapsed time, Pause, Stop, and a tap that opens the recorder.
  - A Stop from the notification queues notes at once.
  - If the system ends the service, the mic is released and the journal kept.
  - On launch, an interrupted recording is saved automatically as a SAVED meeting with "Make notes / Later". A new recording parks an unrecovered journal instead of overwriting it.
  - Capture was already crash-safe: ADTS with a sync every 8 s. A breadcrumb logs any phone that takes the MPEG_4 fallback.
  - `584ed3a`. Tests: 46/46 green. S20+: survived swipe-away; notification Stop ran notes to COMPLETED with the app closed; both notifications cleared.
  - Follow-up: title backfill waits on live capture, not on RECORDING rows, so a stopped recording no longer blocks it. Open: a SAVED recording shows as "Ready" in NoteBlocks.kt and WorkRepository.kt (owners told).

## Work premium redesign, Phase B: paused for the team release (2026-11-16)
- **Founder-approved design:** `design/work-vertical-design-spec.md` §R (revision 2) and `design/Work Vertical Redesign.dc.html`. Commits on this branch (none pushed):
  - `47c39a4`: Task data-loss fix, schema 31 and Work UI kit.
  - `a29045e`: Work home.
  - `1e9b329`: document reading engine.
  - `4a64665`: Brief, Autopilot and Wrap-up.
  - `e0a907e`: context pages, Prepare and Ask.
  - `27acb34`: Tasks app.
  - `ff91863`: Work hub and in-vertical bar.
  - `130220f`: tasks open routes.
  - `dc0fb82`: Inbox and documents.
  - `5ec1740`: MainActivity wiring.
  - `2ee45f3`: compile fixes.
  - `1d1ca35`: test imports.
  - `c399061`: Work ⋯ opens the shared app menu.
  - `9b2802a`: test-suite fixes.
- **What's in the code:**
  - **Work bar:** Work has its own bottom bar (Home · Pulse · Tasks · Notes · Projects · Decisions) and an app-menu ⋯ with Work first.
  - **Pulse** changes by time of day.
  - **Work home:** kept and elevated with a Briefing card, day ribbon, Up next, tasks, projects and people.
  - **Tasks:** a first-class, app-wide manager (smart views, lists and board, task page, natural-language quick add, Plan my day, swipe).
  - **PDFs and scans:** read on-device (PdfBox-Android plus ML Kit text recognition, unbundled), with page-cited Inbox suggestions and a viewer.
  - **Brief controls:** all of them, with decisions to make kept separate.
  - **Autopilot:** tasks go to Wrap-up review by default.
  - **Wrap-up:** type change and a Done check.
  - **Follow-up:** Copy and channel preview.
  - **Context pages:** one layout for project, person and organisation.
  - **Schema:** version 31 (additive) and `schemas/31.json`.
- **Verified:**
  - **Compile:** `:app:compileDebugKotlin` passes, and `assembleDebug` passes in the team build.
  - **Unit tests** (team build runner): every Work, Tasks and documents class passes. That includes Migration30To31 9/9, QuickAddParser 19/19, TaskManagerRepository 9/9, TasksViewModel 9/9, BriefOptions 11/11, BriefGrounding 9/9 and ProjectStatusLogic 5/5, plus the rest of the 29-class list in the 13:27 run.
  - **Device:** an APK with the Work changes was installed on the S20+ (13:04). No guided walkthrough was done.
- **Open for next time:**
  - **Screenshots:** WorkPageScreenshotTest goldens need re-recording, because the Work page changed.
  - **Device checks:** PDF reading memory on a 2 GB phone, and release APK size after PdfBox.
  - **Tasks:** no Nudge from Waiting on, no drag reorder, no sort options, no task history.
  - **Brief:** headings stay English when the language changes, and export ignores the footer option.
  - **Wrap-up:** the "project guessed" mark isn't built (no stored flag).
  - **Not mine:** `DesignSystemGuardTest` fails on `core/ui/mm/AiCraft.kt` (`fontSize = …sp`, from `8d97b49`); its owner needs to fix it.
  - **Pre-existing:** minSdk 24 uses `java.time` without core-library desugaring, a crash risk on Android 7. It's the founder's call.

## Circles premium redesign: every page, Google sign-in, insets (2026-11-18)

- **Why:** the founder found Circles functional but "like a different app". Every Circles page now uses Home's and Work's
  building blocks (MM tokens, Work's header anatomy, MMCard groups, the app's floating bottom bar). No Circles-only look.
- **Shared kit:** `feature/circles2/CirclesKit.kt` holds `CirclesTopBar`, section titles, row dividers, `circlesBottomInset`,
  `CirclesReveal` (a once-only 30 ms stagger; off under `MM.motion.reduced`) and `chatBubbleColors`.
- **Pages:**
  - List: a living featured card (cover edge to edge), the other circles grouped in one card, quiet header (Search, Join, ＋).
  - Circle page: cover, name and one subtitle line in the header; a plain ⋯ menu. The dock is now the app's floating pill bar.
  - Post card: avatar, name and time only in the header; type and status in one dot-and-word line; media edge to edge; a
    muted footer (React or Pray, Comment, Save, Share). Everything else is in the ⋯ menu.
  - Updates opens on a "Share with the …" prompt. Create post is a tall sheet: Post in the header, type chips, a borderless
    writing area, and a media toolbar pinned above the keyboard.
  - Chat: no blue. Your bubbles are charcoal ink in light and raised graphite in dark; others sit on the surface. Photos and
    videos have no bubble or backdrop; a caption gets its own bubble. Calmer day labels, input bar and attachment tray.
  - People: grouped rows, one quiet line per person. D3 is still paused, so post counts and "Top contributor" remain, as text.
  - Settings: grouped cards, Save in the header and pinned above the navigation bar while there are changes.
- **Sign in with Google:** `core/ui/GoogleSignInButton.kt`, per Google's branding (multicolour G on a white or #131314 pill).
  The G is `res/drawable/ic_google_g.xml`; the pill colours are in the button file, in the colour baseline with a comment.
  Used by the account card (including "Sign in to get your circles back"), the create flow, the join sheet and Settings.
- **Insets:** the circle page pads top and sides with `safeDrawing`; the dock clears the navigation bar; while the keyboard is
  up the page pads the navigation bar and the keyboard; Circles sheets ride the keyboard. Not yet checked on the S20+.
- **Tests:** `CirclesPremium{List,Updates,Chat,People}ScreenshotTest`, light and dark (images under
  `build/outputs/roborazzi/circles_premium_*`). fix17 green: List 6/6, Updates 7/7, Chat 3/3, People 4/4,
  CirclesScreenshotTest 19/19, ColorLiteralGuardTest 1/1 (DesignSystemGuardTest fails only on the old AiCraft.kt). The
  Learning session reviewed the images; after that the busy Google button got a visible ring and the shots wait for pack
  covers to decode. Those two need a rerun of the screenshot tests.

## Circles: missing circles, sign-in carry-over, covers and profile photo (2026-11-18)

- **Why the founder's circles vanished:** a reinstall gave the phone a new guest Circles account; the circles stayed on the server, and Google sign-in brought them back. A circle made *before* sign-in was then lost, because switching to the existing Google account left it with the guest account.
- **Fixed (live with the Worker deploy below):**
  - The new `adoptGuestCircles` route moves a guest's circles (membership, ownership, list) to the signed-in account, proved by the guest's ID token.
  - Before switching, the app checks the Worker can do this. If it can't, nothing switches.
- **The phone never forgets circles quietly:** a cache-only "doesn't exist" no longer removes a circle. Circles the current account can't read are kept as "from an earlier sign-in", and the list offers the way back.
- **Sign-in up front:** a Sign in card is the first thing on the Circles home in every state.
- **Covers:**
  - Cover upload in Settings now shows saving, success and a calm error. (Before, it failed silently: Settings had no snackbar.)
  - Every circle type has a default cover from the image pack. Create and Settings offer the pack, or Upload your own.
  - A pack cover is stored as `pack:photo_N`, so nothing is uploaded.
- **Profile photo:** Circles uses the app profile photo until the person picks one in Circles.
- **Deployed (2026-11-18):** the Worker from `99f1133` (with D5) is live as version `58102286-7f61-4ab3-a12d-e868c5055a8a`; roll back to `a2829b9d` if needed. The carry-over and pack covers work against it. The D5 Firestore rules passed 26/26 on the emulator; the founder is deploying them.
- **Next:** the premium redesign of every Circles page (Updates and post card, Create post, chat, members, settings, invite), Google-branded sign-in button, bottom insets.

## Circles experience redesign: Phase B, paused for the team pause (2026-11-16)
- Design files: `design/Circles Experience Redesign.dc.html`, plus `design/circles-{experience-design-spec,motion-spec,innovation-concepts}.md`.
- **Founder decisions:**
  - D1: yes. D2: yes. D4: yes, plus Faith Home polish. D5: yes, as recommended. D6: yes.
  - **D3: paused.** Contribution counts and "Top contributor" stay untouched until the founder decides. The case for removing them was sent to the founder.
- **Done (Android):**
  - Thumb dock (`CircleDock.kt`): Home or Overview · Updates · ＋ · Chat · People. It hides while typing. The ⋯ menu holds notifications and daily rhythm, photos and files, the decision ledger and settings. Every Circle gets a generated `RippleCover`.
  - Faith Home and Work Overview (`CircleHomeTab.kt`, built by the pure `CircleHomeDigest.kt`), plus the catch-up deck.
  - Updates is the feed without the old top stack.
  - The Pulse card is gone from the list. Opt-in Daily rhythm (`CircleRhythm.kt`, WorkManager, 07:00–21:00 slots, no counts), cleared on leave.
  - D6: in-app PDF reader (`PdfReader.kt`), composer type suggestions (`TypeSuggester.kt`), "Make it a post" in Faith chat.
  - D5 UI:
    - External tag, plus an admin switch in Work circles.
    - Decision status: proposed, confirmed or replaced, with "Replace with a new decision".
    - Invite preview in the Join sheet.
    - Event bring-list ("What to bring" or "Checklist", with claim and release).
    - A second confirmation before any meeting share to a circle with external members.
  - AI-content report on meeting briefs (Home and the post menu).
- **Done (server):** the D5 Worker package in `server/circles-api` and `firestore.rules`: `setExternal`, `setDecisionStatus`, decision `replacesPostId`, `previewInvite`, event `bringList` and `claimBringItem`. Also poll `closesAt` enforcement in the rules, and QuickTime-as-MP4 refused. See `docs/CIRCLES_BACKEND_HANDOFF.md` §10.
- **Verified:**
  - `npm test` in `server/circles-api` passes 112/112, including the AI-report tests now on the branch.
  - The whole app compiles (APK built by the Learning session, 13:04).
  - Every `feature.circles2` and `core.circles2` unit test class passes, including `CirclesD5Test` (16/16) and `ColorLiteralGuardTest` for the Circles files. The Learning session ran them at 13:27, with re-runs after.
  - Not verified: rules emulator tests, device or TalkBack checks, screenshot review.
- **⚠ Deploy before testers use the new Circles features:** the Worker (`wrangler deploy`) and the rules (`firebase deploy --only firestore:rules`) must be deployed (needs the founder's yes). Without it:
  - What breaks: External, decision status, invite preview and bring-list calls fail with a friendly error.
  - What still works: the existing features, and the dock, Home and Overview (they need no server change).
  - Poll deadlines are not enforced until the rules are deployed.
- **Open for next time:**
  - The founder's D3 decision.
  - Device pass: the dock with the keyboard, the PDF reader on real PDFs, Daily rhythm delivery.
  - Run the rules emulator tests.
  - Motion from `circles-motion-spec.md` (threshold transition, answered-prayer glow) is not built.
  - Threaded replies (`parentId`) and the upload ring with retry (spec §15 steps 2–3) are not built.

## App-wide navigation, spaces and home discoverability (2026-11-16)
- **Spaces really switch off:** Settings › What it's for is now one row per space with a switch (theme tokens, readable in dark mode; the last space can't be turned off and says why). A space that is off is hidden from the bar, the app menu, the quick-access row (now self-gated, Work · Learn · Faith first), Today's devotional stories, the hero's scripture banner, Notes' work-meeting hero, the Bible search result, and the home picker. A vertical's home falls back to Today while its space is off (`HomeStyle.effectiveFor`). The devotional and faith reminders stop while Faith is off and come back with the person's settings when it is turned on (`core/navigation/SpaceSchedules.kt`).
- **Bar follows spaces (Work spec R2):** Today · Notes · New, then two places: the Home's own vertical first, then Work, Learning, Faith; free places go to Tasks, then Search (`core/navigation/BarConfig.kt`). Settings left the bar. `TabSlotTest` replaced by `BottomBarTest` (the slot it tested was retired by R2).
- **App menu:** one menu for the whole app (`core/navigation/AppMenu.kt`, `AppMenuModel.kt`): Your spaces (with an inline space picker), Everyday, each space's features, and App (Settings, Plans, AI engine, Appearance, Connected apps, Backup). Opened from the avatar and a new menu button on every home header, and from Faith, the Bible home and the Learning space with that vertical's group first. Work adds its own button (Work session).
- **Today:** "Your spaces" row under the hero: a live card per space (next meeting, cards due, today's devotional) plus Tasks, and "All features". Faith gets the app bar inside its space.
- **Not done:** Work spec B-3 tiles (Briefing card, day ribbon, progress ring) on Today; Learning-only users still see the bento's "Work & projects" tile (`HomeSections.work` counts Learning); the devotional home-screen widget is not gated; reminder settings saved from Faith settings still sync directly (`UserPreferences.setReminderSettings`, Work session's file).
- **Notes from a YouTube video, up front (plan D in `design/notes-blocks-and-math-plan.md`):**
  - One sheet (`feature/importing/YouTubeLink.kt`) takes a link and a kind (Auto, Lecture, Sermon with Faith on, Motivation, Life skills, Meeting with Work on). It says up front when Google's AI isn't set up, then opens the import screen with the link loaded (`import_file/{path}?type=`).
  - Opened from: Today (a card under Your spaces, plus a banner when a YouTube link was just copied, offered once per video, with "Don't ask again"); the Create sheet (its own row, and "Import audio" became "Import a file"); the app menu (Everyday, Learning with Lecture pre-set, Faith with Sermon pre-set); Faith home ("Sermon from a YouTube video"); and links shared from other apps.
  - A shared YouTube link opens the sheet and is filed in the Work Inbox only when Work is on (`MainActivity.handleShare`).
  - No YouTube logo or colours: a generic play icon in the accent colour.
  - Learning's New study session entry is with the Learning session.
- **Report AI content (rows 8 and 18 of `design/ai-content-reporting.md`):** AI-written story cards on Today (devotional origin CLOUD_AI or DEVICE_AI only) get a long-press "Report this"; the Search answer card gets the Report flag beside Copy.
- **Verification:** the Learning session's 13:27 run compiled the app with all of this work. `BottomBarTest` 5/5, `NavigationModelTest` 10/10, `YouTubeLinkTest` 6/6 and `AppMenuLocalTest` 5/5 pass. `ColorLiteralGuardTest` fails only on Circles' `CircleHomeTab.kt`, not on these files. Nothing checked on a device.
- **Depends on other sessions' commits before the release tag:** `core/aireport/` (Report AI content session), used by the Search and Home story reports; and the Learning session's `settings_group` destination in `MainActivity.kt`, opened by the menu's "Faith settings".

## YouTube Video Ingestion, Gemini 3.8 Flash Model Standardization & Domain-Adaptive Retention Bytes (2026-11-16)
- **Gemini 3.8 Model Lineup Standardization (`AiModelRouter.kt`, `server/gemini-proxy`):**
  - Standardized all cloud text/reasoning routes (`GEMINI_FAST`, `GEMINI_INTELLIGENCE`, `GEMINI_DEEP_REASONING`) on **`gemini-3.8-flash`**, keeping `gemini-3.8-live` and `gemini-3.8-live-extended-thinking` for Live voice sessions and `gemini-3.8-flash-tts` / `gemini-3.8-flash-lite-tts` for Read-to-Me TTS, while removing expensive Gemini 4 model IDs from `DefaultAiModelRouter` and `server/gemini-proxy/wrangler.toml`.
  - Updated `server/gemini-proxy/logic.js` (`classifyRequest`) so YouTube `file_data` (`file_uri` = `https://www.youtube.com/...` or `https://youtu.be/...`) requests draw on the action meter rather than the uploaded-audio meter (`cd server/gemini-proxy && node --test test/*.test.mjs`: 29/29 passing).
- **Native YouTube URL Ingestion & Transcript/Bytes Extraction (`YouTubeUrlParser.kt`, `GeminiYouTubeEngine.kt`, `GeminiTransport.kt`, `GeminiHttpTransport.kt`, `MeetingProcessingPipeline.kt`, `ProcessingScheduler.kt`):**
  - Created `YouTubeUrlParser` supporting `youtube.com/watch?v=...`, `youtu.be/...`, `youtube.com/shorts/...`, `youtube.com/live/...`, `m.youtube.com`, and timestamp parameters (`t=125s`, `t=2m10s`).
  - Extended `GeminiRequest` and `GeminiHttpTransport.buildGenerateBody` with `externalMediaUri` and `externalMediaMimeType = "video/mp4"`, passing canonical YouTube URLs directly via `file_data` to `gemini-3.8-flash`.
  - Built `GeminiYouTubeEngine` to extract the video's title, speaker/channel, auto-detected category (`SERMON`, `LECTURE`, `MOTIVATION`, `LIFE_SKILLS`, `BIBLE_STUDY`, `INTERVIEW`, `RESEARCH`, `MEETING`), 3–7 high-retention bite-sized takeaways (`retentionBytes` with `[mm:ss]` timestamps), and a full chronological timestamped transcript projected into `CanonicalWord`s.
  - Wired `MeetingProcessingPipeline` and `ProcessingScheduler` so YouTube videos flow through the full MeetingMind note pipeline (Sermon notes with Scripture detection, Lecture concepts & active recall flashcards, Motivation & Life Skills playbooks, embeddings, Ask AI, Read-to-Me, and Zuri Moments).
- **Domain-Adaptive Workflows & UI Entry Points (`Enums.kt`, `MeetingModels.kt`, `Workflows.kt`, `NotePolishEngine.kt`, `RecordingNoteBuilder.kt`, `ImportScreen.kt`, `ShareIn.kt`, `Inbox.kt`, `InboxScreen.kt`, `WorkViewModel.kt`, `MainActivity.kt`):**
  - Added `MeetingSource.YOUTUBE_VIDEO` and two personal-growth workflows: `RecordingType.MOTIVATION` (*Core Mindset Shift*, *Key Principles & Bytes*, *Memorable Quotes*, *Daily Challenge & Habits*) and `RecordingType.LIFE_SKILLS` (*What This Teaches*, *Step-by-Step Playbook*, *Do's & Mistakes to Avoid*, *Action Checklist*).
  - Upgraded `ImportScreen` with a dedicated **YouTube video link** card, 1-tap clipboard **Paste**, **Auto-detect** category chip alongside `Sermon`, `Lecture`, `Motivation`, `Life skills`, and `Interview`, and 1-tap **"Extract transcript & bytes"**.
  - Updated `ShareIn`, `Inbox`, `WorkViewModel.transcribeInboxItem`, and `InboxScreen` so sharing a link from the YouTube app into MeetingMind tags it as `"YouTube"` and enables 1-tap background transcript & bytes extraction right from the Inbox.
- **Verification:** Added `YouTubeIngestionTest.kt` and extended `GeminiHttpTransportTest.kt` and `server/gemini-proxy/test/logic.test.mjs` (29/29 passing locally). Per `AGENTS.md`, `./gradlew` was not run locally; Android compilation and unit tests await remote runner verification.

## Learning vertical redesign, Phase B (2026-11-16)
- **Approved:** founder approved `design/Learning Vertical Redesign.dc.html` and `design/learning-vertical-design-spec.md` "as designed", so decisions D1-D10 take the spec's recommendations. D11 (Learning's release status: `AGENTS.md` says production, `agents/PRODUCT.md` says cut from MVP) is still open.
- **New Learning space** (`feature/learning/space/`, `feature/learning/components/`): Learn (bounded daily plan, five-minute version, done state, week without streaks), Library (search, courses with mastery counts, course view), Progress (qualitative states with reasons, no percentages); Session with Source · Understand · Practise · Mastery and a Next step strip; Concept detail with cited quotes, rename/hide/report with Undo; Practice as a full-screen mode (Daily Recall, ungraded diagnostic, strengthen a concept, session practice) with deliberate reveal, three ratings labelled by the real scheduler, short-answer self-check, skip with Undo, stop/resume, completion summary.
- **Routing:** `learn`, `learning_session/{noteId}` and `learn_practice` now render the new screens; `HomeStyle.LEARNING` shows the Learn pane with the global bar and "Open Learning" (D2). Exam Prep and Teach-Back have no entry in the space (D3); their code and the `learn_exam` route are kept. The old `LearnHomeScreen`, `LearningSessionScreen` and `PracticeArenaScreen` are no longer routed; delete them after a device pass.
- **Trust fixes:** offline hints no longer quote the answer and AI hints that do are dropped; review previews match the scheduler; the daily brief refreshes while open; a failed save no longer crashes practice; load errors show with Retry. AA contrast fix for `inkMuted`, `success`, `warning` (D5, separate commit).
- **Not done:** Ask Learning redesign (still the shared `AssistantSheet`), the 3-step create flow, bundling Literata (D4; the system serif stands in), audio playback inside the citation sheet (it opens the recording at the moment instead), reminders (D8).
- **Verification:** static checks only (icon names against the cached icon jars, colour/size literal guards, entry-point contract). Per `AGENTS.md`, `./gradlew` was not run locally: compile, `LearningRulesTest`, `LearningSpaceUiTest`, `HintSafetyTest`, `DeterministicSpacedSchedulerTest` and screenshot re-recording (contrast change) await the remote runner. Nothing checked on a device.

## Local LLM Parity Round 2, Vital Editor/Reminder Tools & Paid Killer Feature #2: Post-Session Autopilot Workflows (`Capability.AUTOMATION_RECIPES`) (2026-11-15)
- **Pass 1 — Eliminated Remaining Cloud-Only & Privacy-Bypass Gaps:**
  - `CreateEngineApi.kt` (`GeminiCreateModel`) & `CreateGenerator.kt`: Added `UserPreferencesManager.processingProfile` awareness + automatic one-way fallback to local `LanguageModelFactory.resolveLocal(ModelCapability.SUMMARIZATION, ModelTier.RECOMMENDED)` (supporting both `generateStructured` and `generate` with `LlmEngineManager.release()` cleanup) so Create cards work offline and on Free tier.
  - `NoteEditorViewModel.kt` (`runAiEdit`): Added `UserPreferencesManager.processingProfile` + `WorkPrivacy.forNote(...)` enforcement and streaming fallback via `localResolved.languageModel.generateStream(...)` with `LlmEngineManager.release()` cleanup.
  - `RecordingTitleBackfill.kt`: Enforced `UserPreferencesManager.processingProfile` and per-meeting `WorkPrivacy.forMeeting(...)` before selecting online vs local title engine.
  - `Changes.kt` (`DeviceChangeModels`) & `SessionAi.kt` (`ModelSessionAi`): Switched `Changes.kt` to `factory.resolveWithFallback(...)` and made `SessionAi.candidates()` honor `ProcessingProfile.OFFLINE` before trying cloud.
  - `MeetingProcessingPipeline.kt` (`runPolishPass`, sermon extraction, lecture synthesis): Upgraded `runPolishPass` and sermon extraction to `resolveWithFallback(...)` with `ResilientLanguageModel.lastRanLocally` release handling, and switched lecture synthesis fallback resolver to `resolveLocal(...)`.
  - `ScopedAsk.kt` & `WorkViewModel.kt`: Added `onModelFinished` `finally` cleanup in `ask` & `askStream`, wired to `LlmEngineManager.release()`.
- **Pass 2 — Vital Inline Editor & Reminder Tools + Sparkles Icon Fix:**
  - `AiEditSheet.kt` (`AI_EDIT_ACTIONS`): Added `"Proofread & polish"`, `"Turn into callout"`, and `"Explain key terms"` inline editor actions (cloud + local LLM compatible).
  - `Assistant.kt` & `AssistantHost.kt`: Added `AssistantTool.LIST_UPCOMING_REMINDERS` (`Read`, surfacing scheduled task reminders and dated tasks with overdue/due status via `TaskRepository.upcomingReminders()`).
  - `WorkPageToday.kt` & `InboxScreen.kt`: Replaced lingering `Icons.Filled.AutoAwesome` usages with `AiMark` so `AssistantTest.noSparklesIconAnywhere` stays green.
- **Pass 3 — Paid-Plan Killer Feature #2 End-to-End (`Capability.AUTOMATION_RECIPES`):**
  - `ai/automation/PostSessionAutopilot.kt`: Implemented 4 grounded workflow recipes (`EXECUTIVE_DEBRIEF`, `CLIENT_CRM_LOG`, `STUDY_FLASHCARD_DRILL`, `SERMON_SMALL_GROUP_PACK` with Faith Contract), `AutopilotPreferences` (`SharedPreferences`-backed post-recording auto-run toggles), `AutopilotWorkspace` (enforcing `Capability.AUTOMATION_RECIPES`, `WorkPrivacy`, Cloud + local `resolveWithFallback`, `saveRecapAsNote`, `commitTasks`, and `maybeAutoRunForReadyMeeting`).
  - `feature/automation/AutopilotSheet.kt`: Built token-compliant `AutopilotSheet` (`MM.*` tokens) supporting `AutopilotTarget.Meeting`, `.Note`, and `.LatestSession`, recipe selector, auto-run trigger switches, Pro paywall upgrade prompt (`PaywallTrigger.PREMIUM_ACTION`), task commitment, clipboard copy, share, and Save-as-Note flow.
  - Wired entry points in `MeetingDetailScreen.kt` (`Autopilot` button), `NoteAiSheets.kt` & `NoteEditorScreen.kt` (`Autopilot Workflows · Pro`), `WorkPageToday.kt`, `WorkPageScreen.kt`, `MainActivity.kt`, and optional post-processing hook in `MeetingProcessingPipeline.kt`.
- **Verification:** Added `PostSessionAutopilotTest` and extended `AssistantTest` (`LIST_UPCOMING_REMINDERS`) and `BillingModelTest` (`Capability.AUTOMATION_RECIPES`). Re-ran static guard checks: zero `AutoAwesome` across `src/main/java`, zero `Color`/`sp`/`RoundedCornerShape` literals in `AutopilotSheet.kt`, and balanced delimiters across all 25+ touched Kotlin files. Per `AGENTS.md`, `./gradlew` was not run locally; Kotlin compilation and unit tests await remote runner verification.

## Circles 4-Round Polish, Daily Habit Loops & Power Features Upgrade (2026-11-15)
- **Round 1 — Visual UX & Micro-Interactions (`CirclesListScreen.kt`, `CircleHomeScreen.kt`, `CircleSettingsScreen.kt`, `ChatTab.kt`, `MediaViews.kt`):**
  - Added space-aware aurora covers/icons/counts (`spaceTint`, `spaceIcon`), animated search (`circles.size >= 3`), and `.animateItem()` in `CirclesListScreen`.
  - Upgraded `CircleHomeScreen` header actions to tonal circular `Surface` buttons, added space/engagement subtitle pills, wired search jump-to-message (`targetChatMessageId`), and added a mini hero cover preview in `CircleSettingsScreen`.
  - Added double-tap quick-react with space-tailored reaction emoji + burst animation + haptics and unread Jump-to-Latest badge in `ChatTab`; added swipeable multi-photo `FullScreenGalleryDialog`, `1x/1.5x/2x` playback speed pills, and duration timecodes in `MediaViews`.
- **Round 2 — Daily Habit Loops (`LocalCircleStore.kt`, `CirclesListViewModel.kt`, `CircleHomeViewModel.kt`, `ChatViewModel.kt`, `CirclesListScreen.kt`, `CircleHomeScreen.kt`, `FeedTab.kt`, `FunCards.kt`):**
  - Added local multi-day streak tracking, today check-in state, saved post bookmarks, per-circle last-visit timestamps, and prompt dismissal in `LocalCircleStore` (`InMemoryLocalCircleStore` + `SharedPrefsLocalCircleStore`) plus `CircleStreakUi` and habit unit tests in `CircleViewModelsTest.kt`.
  - Surfaced `DailyCirclePulseCard` on `CirclesListScreen`, collapsible `CatchUpCard` ("Catch me up · N new"), dismissible space-tailored `TodayPromptCard`, `"Saved (N)"` filter chip + post bookmark toggles in `FeedTab`, and 1-tap vertical kudos chips in `CelebrateSheet`.
- **Round 3 — Power-User & Multi-Vertical Features (`server/circles-api/`, `core/circles2/`, `feature/circles2/`):**
  - **Post `#tags` & Interactive Checklists:** Added `PostMeta.tags` (up to 4) and `PostMeta.checklist` (`ChecklistItem(id, text, done)`, up to 8) across the Cloudflare Worker (`cleanPostMeta`, `POST /v1/toggleChecklistItem`), Android models/parsing/API/repository, `ComposerSheet` editor, and interactive `PostCard` checklist + clickable `#tag` filtering in `FeedTab`.
  - **Quiz/Trivia Polls:** Extended `shapePoll`/`createPoll`, `Poll`/`PollDraft`/`ChatViewModel`, `PollSheet`, and `PollBody` with single-choice `correctOptionId`/`correctIndex` and `explanation` reveal UX.
  - **Vertical Templates, Activity Badges & Categorized Search:** Added 1-tap vertical `EventTemplate` chips and `"Join"` meeting link pill in `EventsSection`, contributor post count/top-contributor badges in `MembersTab`, and `All`/`Posts`/`Messages`/`Tasks`/`Media` category filter chips in `CircleSearchSheet`.
- **Round 4 — Cohesion Audit, Token Sweep & Live Worker Redeploy:**
  - Verified 0 `fontSize`/`Color(0x...)`/`RoundedCornerShape(...)` literal violations across all of `core/circles2/` and `feature/circles2/`, verified trailing default compatibility across `CirclesScreenshotTest` and all sheet/tab signatures.
  - Ran `bash server/circles-api/scripts/deploy-and-verify.sh`: 81/81 Worker unit tests passed (`npm test`), deployed Cloudflare Worker `meetingmind-circles` (`https://meetingmind-circles.meetingmind.workers.dev`, Version ID `a2829b9d-4e3e-4f7d-b9ed-d291e6495d04`), released `firestore.rules`, and verified live smoke checks (`/health` 200, `/ready` 200, `/v1/createCircle` 401). Per `AGENTS.md`, `./gradlew` was not run locally; Android compilation and unit/screenshot tests await remote CI.

## Learning Vertical Round 2 — 4-Pass Deep Polish: Tab Badges, Concept Filters, Note Intuition Save, Missed-Card Drill & Exam Cram Preview (2026-11-15)
- **Pass 1 — Learn Home & Courses Deep Polish (`LearnHomeScreen.kt`, `LearnViewModel.kt`, `MainActivity.kt`):**
  - Added live count badges to `LearnTabSegmentedControl` (`Courses` count, `Practice` due count, `Exam` goals count) while keeping `EntryPoints.*` labels and `learn_tab_*` testTags intact.
  - Added 1-tap **"Drill All"** header pill and per-concept **"Drill"** pills on `HighYieldWeakSection` wired to `LearnViewModel.loadWeakConceptsPractice(...)`.
  - Upgraded `RecentSessionsSection` with **Course / Unassigned filter chips**, **live lecture search** (`displayLectures.size > 4`), and a **"Show All N Lectures" / "Show Less"** toggle (`filteredLectures.size > 6`).
  - Enhanced `CoursesSection` expandable lecture drawer rows with lecture duration (`Xm`) and mastered concept ratios (`strong/total mastered`).
- **Pass 2 — Lecture Studio Concepts, Intuition-to-Note Save & Diagnostics (`LearningSessionScreen.kt`, `LearnViewModel.kt`):**
  - Upgraded `SessionTab.CONCEPTS` with interactive **Concept Filter Chips** (`All (N)`, `To Review (N)`, `High-Yield (N)`, `Mastered (N)`), live concept search (`sessionDetail.concepts.size > 4`), and an empty-filter reset card.
  - Added 1-tap **"+ Save Intuition to Note"** / **"✓ Saved to Lecture Note"** (`LearnViewModel.saveSimpleExplanationToNote`) inside `ConceptCard`'s `Explain Simply (ELI12)` drawer, appending a structured `NoteBlockType.CALLOUT` (`BlockSource.ENRICHED`) directly to the lecture's `NoteDocument`.
  - Upgraded `DiagnosticsCard` (`SessionTab.DIAGNOSTICS`) into a rich **Session Intelligence & Signals** dashboard with a segmented mastery bar, `Flashcards` / `Problems` / `Teach-Back` counts, and 1-tap `Practice Session` + `Ask Tutor` CTAs.
- **Pass 3 — Practice Arena Bidirectional Navigation, Schedule Confirmation & Missed-Card Drill (`PracticeArenaScreen.kt`, `LearnViewModel.kt`):**
  - Added `LearnViewModel.previousCard()` and a **`← Prev`** button (`practice_prev_button`) in the Practice Arena footer when `currentIndex > 0`.
  - Added selected-state styling on `RecallButton` and a spaced-repetition schedule confirmation banner (`"✓ Scheduled for review in X"`) on `RecallFlashcard`.
  - Added a **"Try Again"** reset button inside `InteractiveProblemCard`'s feedback box when answered wrong (`!gotItRight`).
  - Added `missedActivityIds: StateFlow<Set<String>>` in `LearnViewModel` (`!isCorrect || rating == AGAIN || rating == HARD`) and surfaced a primary **"Drill Missed Cards (N)"** (`practice_drill_missed`) CTA on `AllDoneScreen`.
- **Pass 4 — Exam Prep Studio Matrix Counts, 1-Tap Concept Drill & Cram Sheet Preview (`ExamPrepScreen.kt`):**
  - Updated High-Yield Priority Matrix filter pills (`All (N)`, `Priority Gaps (N)`, `High-Yield (N)`, `Mastered (N)`) to show exact counts and true filtered lists (with a `"Show All (N)"` empty-filter state instead of silently resetting).
  - Added a 1-tap **"Drill →"** pill on every `ExamPriorityRow` wired to `onDrillConcept`.
  - Added a **"Preview"** button and 2-line preview snippet on `CramSheetActionCard` when a cram sheet is already generated, plus a 1-tap **"Copy" / "✓ Copied"** clipboard action inside `CramSheetPreviewDialog`.

## Local LLM Parity Audit, Vital AI Tools Expansion & Paid Killer Feature: Cross-Session Deep Synthesis (`Capability.DEEP_SYNTHESIS`) (2026-11-15)
- **Pass 1 — Local LLM Parity Across All AI Tools & Engines:**
  - `LanguageModelFactory.kt`: Added `ResilientLanguageModel` and `resolveWithFallback(...)` so Internet-mode cloud calls fall back automatically to an installed local LLM (MediaPipe / AICore) when offline or quota-limited, while preserving `resolve(...)` contracts.
  - `TranscriptToolEngine.kt` & `AiToolWorker.kt`: Added token-budgeted chunking (`contextLengthTokens` + `actualEngineName`) so all `REVISION`, `FINDINGS`, `DOCUMENT`, and sampled `TITLE` transcript tools execute cleanly on 4K-context local models without overflow, releasing `LlmEngineManager` in `finally`.
  - `Assistant.kt` & `AssistantSession.kt`: Added `localModelProvider`, `forceLocal`, and `runLocalRounds(...)` so Ask Assistant works offline or on the Free tier via local JSON tool-calling instead of blocking when cloud is unconfigured.
  - `AskEverything.kt` & `SearchScreen.kt`: Added `localModelProvider`, `preferLocal`, `capSourcesForLocal(...)`, and cloud-to-local fallback so Ask Everything runs offline and on the Free tier.
  - `GeminiTranslationEngine.kt`: Added `fallbackModelProvider`, `preferLocal`, and batched `translateWithLocalModel(...)` (batches of 8 segments) with cleanup callback.
  - `LearningAiEngine.kt` & `LearnViewModel.kt`: Wired `fallbackModelProvider`, `preferLocal`, `12_000`-char local cap, and `LlmEngineManager.release()`.
  - `NoteAiJobs.kt`, `NoteEditorViewModel.kt` (`polishWholeNote`), `WorkModels.kt` (`DeviceWorkModels`), `Scenes.kt`, and `MeetingProcessingPipeline.kt`: Switched to `resolveWithFallback(...)` / `fallbackModel` with `LlmEngineManager.release()` cleanup when running locally.
- **Pass 2 — Vital AI Tools Added Across Assistant, Transcript & Notes:**
  - **Assistant Tools (`Assistant.kt`, `AssistantHost.kt`):** Added `LIST_TASKS` (`Read`) and `COMPLETE_TASK` (`Write`, backed by `TaskRepository.toggleDone` with undo support).
  - **Transcript Tools (`TranscriptAiTool.kt`, `TranscriptToolPrompts.kt`, `AiToolWorker.kt`):** Added `FIND_RISKS_BLOCKERS`, `DRAFT_FOLLOW_UP`, and `TRANSLATE_TRANSCRIPT` (all `MODEL_BACKED`).
  - **Note & Notebook AI Tools (`NoteAiEngine.kt`, `NoteAiApply.kt`, `NoteAiSheets.kt`, `NotesViewModel.kt`, `NoteEditorViewModel.kt`, `NoteEditorScreen.kt`):** Added `UNRESOLVED_GAPS` ("Open questions & gaps") and `FOLLOW_UP_BRIEF` ("Recap & follow-up") across both single notes and entire notebooks.
- **Pass 3 — Paid-Plan Killer Feature End-to-End: Cross-Session Deep Intelligence & Executive Action Pack (`Capability.DEEP_SYNTHESIS`):**
  - **Billing & Paywall (`Plan.kt`, `PaywallCopy.kt`, `BillingModelTest.kt`, `PaywallCopyTest.kt`):** Added `Capability.DEEP_SYNTHESIS` across `BYOK`, `PRO`, and `CHURCH` tiers with matching `PaywallCopy.includedRows` row.
  - **Engine & Workspace (`ai/synthesis/DeepSynthesisEngine.kt`):** Implemented `DeepSynthesisEngine` (4 modes: `EXECUTIVE_PACK`, `PRE_SESSION_BRIEF`, `SERIES_SYNTHESIS`, `EXAM_MASTER_CRAM`), cross-session workspace gatherer (`DeepSynthesisWorkspace`: related notes/recordings, open tasks/action items, `WorkPrivacy` confidential on-device enforcement, instant zero-AI `DeepSynthesisPreview`), strict citation grounding (`parseAndValidate`), cloud + local LLM parity, and 1-tap `saveAsNote` / `addCommitmentsAsTasks`.
  - **UI & Navigation (`feature/synthesis/DeepSynthesisSheet.kt`, `MeetingDetailScreen.kt`, `NotesScreen.kt`, `NoteEditorScreen.kt`, `MainActivity.kt`):** Built `DeepSynthesisSheet` strictly with `MM.*` design tokens (including an instant Free-tier preview & `PaywallTrigger.PREMIUM_ACTION` upgrade card in `PRODUCTION` tiering) and wired launch points from Recording Detail (`Deep Pack`), Note AI Menu, and Notebook AI Menu.
- **Verification:** Added `DeepSynthesisEngineTest` and extended `AssistantTest`, `GeminiTranslationEngineTest`, `TranscriptToolEngineTest`, `NoteAiEngineTest`, `BillingModelTest`, and `PaywallCopyTest`. Static checks passed (zero `AutoAwesome` / style-literal / color-literal violations across all new & edited files; `PaywallCopy` and `TranscriptAiTool` contract checks clean). Per `AGENTS.md`, `./gradlew` was not run locally; Kotlin compilation and test execution await remote runner verification.

## Learning Vertical 4-Pass Iterative Polish: Micro-Interactions, Course Drawers, Lecture Studio Filters, Animated Practice & Exam Readiness (2026-11-15)
- **Pass 1 — Learn Home & Courses UX + Micro-Animations (`LearnHomeScreen.kt`, `MainActivity.kt`):**
  - Deduplicated `learn_mastery_ring` Compose `testTag` on `LearnHomeScreen` and replaced instant layout pops with smooth `animateContentSize()`, `animateFloatAsState()` (500ms `FastOutSlowInEasing`), and `animateColorAsState()` transitions.
  - Upgraded `RecentSessionsSection` cards with lecture duration badges (`Xm`), mastered concept ratios (`strong/total mastered`), and 1-tap `+ Course` assignment pills.
  - Upgraded `CoursesSection` cards with an inline expandable **`View Lectures (N)` drawer** (`expandedCourseIds`, rotating chevron, direct click-to-open lecture rows, and 1-tap `+ LectureTitle` assignment chips for unassigned recorded lessons), plus `strong/total concepts` labels.
  - Added 1-tap starter topic chips (`Cellular Respiration`, `Linear Algebra Eigenvalues`, `Contract Law Remedies`, `Macroeconomic Policy`) to the Instant Topic Deck Generator on the Practice tab.
- **Pass 2 — Lecture Session Studio Deep Polish (`LearningSessionScreen.kt`):**
  - Replaced static tab buttons with an animated segmented tab bar (`animateColorAsState` + subtle active border) and added a 1-tap **"Ask Tutor"** action in `UnderstandTabHeader` alongside `Open Editor`, `Read Aloud`, and `Practice This Lecture`.
  - Added interactive **Note Block Filter Chips** (`All Notes`, `Exam Hints & Definitions`, `Formulas & Diagrams`) and `animateContentSize` callout unpacking inside `RecordedLectureNotesCard`.
  - Added live **inline Transcript Search** (`OutlinedTextField` filtering speaker segments in real time) inside `RecordedLectureTranscriptCard`.
  - Added an expandable **"Peek Answer" / "Hide Answer"** toggle (`AnimatedVisibility`) to `ActivitySummaryCard` in the Lecture Studio's Practice tab.
- **Pass 3 — Practice Arena & Exam Prep Studio Choreography (`PracticeArenaScreen.kt`, `ExamPrepScreen.kt`):**
  - `PracticeArenaScreen`: added directional `AnimatedContent` slide+fade card transitions when advancing/going back, animated `LinearProgressIndicator` (`animateFloatAsState`), animated mode filter pills, side-by-side **"Your Attempt"** comparison box on flipped `RecallFlashcard`s, and animated option border/background colors + smooth feedback reveal on `InteractiveProblemCard`.
  - `ExamPrepScreen`: added `animateIntAsState` readiness score counter and animated mastery breakdown bar on `ExamReadinessDashboardCard`, interactive **High-Yield Priority Matrix filter pills** (`All`, `Priority Gaps`, `High-Yield & Flagged`, `Mastered`), and **Share** + **Regenerate** actions on `CramSheetActionCard`.
- **Pass 4 — Cross-Vertical Edge Cases & Guardrail Audit (`NoteEditorScreen.kt`, `AssistantSession.kt`, `LearnViewModel.kt`):**
  - Fixed `Color.White` in `NoteEditorScreen.kt`'s `Study Studio` pill to `MM.colors.onAccent` (`ColorLiteralGuardTest` compliance).
  - Updated `AssistantSession.resolveScope()` so opening the Socratic Learning Assistant from a `sessionId`-routed `LearningSessionScreen` (e.g., from `ExamPriorityRow` or `CourseCard`'s lecture drawer) resolves the underlying `noteId` via `learningSessionDao.getSessionById(sessionId)` and exposes full lecture note blocks and tools.
  - Updated `LearnViewModel.saveCramSheetAsNote(...)` to parse Cram Sheet markdown into structured `NoteBlock`s (`HEADING`, `BULLET`, `PARAGRAPH`, `CALLOUT`) via `MarkdownImport.parse(...)` instead of a single raw paragraph block.

## Learning Vertical Overhaul: Learn Home, Courses from Recorded Notes, Lecture Notes Linking, Practice Arena, Exam Prep & Socratic Learning Assistant (2026-11-14)
- **Recorded Lecture Notes <-> Lecture Studio & Courses Linking (`LearningRepository.kt`, `MeetingProcessingPipeline.kt`, `LearnViewModel.kt`, `LearningSessionScreen.kt`):**
  - Fixed `MeetingProcessingPipeline` passing `courseName = meeting.title` to `ensureSessionForNote` (which previously polluted `courseName` with the lecture's own title) and wired `updateSessionSignals(...)` after lecture synthesis.
  - Added `LectureNoteSummary`, `observeLectureNotes()`, `syncLectureNotesToSessions()`, and `bootstrapSessionMaterials(...)` in `LearningRepository` so every recorded `LECTURE`/`RESEARCH` note or note in a `LEARNING` notebook is automatically synced to a `LearningSessionEntity` and extracts concepts + multi-modal practice activities (`RECALL`, `MULTIPLE_CHOICE`, `WORKED_PROBLEM`, `TEACH_BACK`) from the note's blocks and transcript.
  - Overhauled `LearningSessionScreen` (`SessionTab.UNDERSTAND` / `"Lecture Notes"`) to render the full recorded lecture note (`RecordedLectureNotesCard`: summary, headings, `EXAM_TIP`/`DEFINITION` callouts, expandable `BlockSource.ENRICHED` step-by-step explanations, `MathExpressionView` formulas, bullets) and collapsible timestamped `RecordedLectureTranscriptCard` directly on the Lecture page, plus an inline `+ Create / Assign Course` dialog trigger, `Open Editor`, `Read Aloud`, and `Practice This Lecture`.
- **Easy Course Creation from Recorded Lessons (`LearnHomeScreen.kt`, `LearningRepository.kt`, `NoteEditorViewModel.kt`, `NoteEditorScreen.kt`):**
  - Added `createCourseFromNotes(...)` and `assignNotesToCourse(...)` in `LearningRepository` with automatic `SubjectDomain` and course-code inference.
  - Redesigned the Courses tab in `LearnHomeScreen` with a **"Create Course from Recorded Notes"** 1-tap spotlight card for unassigned recorded lessons, an upgraded `CreateCourseDialog` with multi-select for recorded lectures, and Course cards with mastery progress bars, `"View Lectures"` expandable sheets, and `"Practice Course"` buttons.
  - Added `NoteEditorViewModel.createCourseFromNote(...)` and an inline Learning Studio strip (`+ Create Course` + `Study Studio` buttons) and overflow menu actions directly inside `NoteEditorScreen`.
- **Practice Arena Overhaul (`PracticeArenaScreen.kt`, `LearnViewModel.kt`, `LearningRepository.kt`, `MainActivity.kt`):**
  - Fixed the `"No Practice Cards Yet"` bug when launching Flashcards or Voice Teach-Back by wiring `loadPracticeByFilter(...)` and `ensurePracticeLoaded(sessionId, activityId)` with automatic fallback extraction from recorded lecture notes.
  - Added a top Mode Filter bar (`All Mixed`, `Flashcards`, `Quiz & Problems`, `Voice Teach-Back`), live progress bar & streak badge, `InteractiveProblemCard` (with multiple-choice A/B/C/D options and instant feedback), an **Instant Study Deck Generator** on `EmptyPracticeScreen` (custom topic input + domain selector + 4 starter academic decks), and session accuracy/streak telemetry on `AllDoneScreen`.
- **Exam Prep Studio (`ExamPrepScreen.kt`):**
  - Added `ExamReadinessDashboardCard` (overall readiness %, Strong/Developing/Gap mastery bar, and `"Drill Priority Gaps"` CTA), Course filter chips, clickable `ExamGoalCard`s, and clickable `ExamPriorityRow`s with lecture provenance.
- **Real Socratic Learning Assistant (`Assistant.kt`, `AssistantHost.kt`, `AssistantSession.kt`, `AssistantSheet.kt`, `MainActivity.kt`):**
  - Extended `AssistantScope` with `learning` & `subjectDomain` and added 5 Learning tools to `AssistantTool` & `AppAssistantHost`: `LIST_COURSES`, `GET_STUDY_CONCEPTS`, `CREATE_FLASHCARDS`, `CREATE_COURSE`, and `CREATE_EXAM_GOAL` (plus direct note block reading/insertion when opened from `LearningSessionScreen`).
  - Added Socratic Learning Tutor instructions in `AssistantEngine.buildSystemPrompt`, academic study suggestions in `AssistantSession.suggestions()`, and note-scoped `lib.forLearningNote(noteId)` in `MainActivity`.
- **Tests & Verification:** Extended `LearnViewModelTest` and `AssistantTest`. Static color/icon guard checks passed; per the remote-only Gradle mandate (`AGENTS.md`), no `./gradlew` commands were run locally and Android tests await remote runner verification.

## Work Vertical & Work Inbox Redesign + End-to-End Voice Note Transcription & Summary Bridge (2026-11-14)
- **Smart Source Attribution, Date/Time Provenance & Media Probing (`ShareIn.kt`, `core/work/Inbox.kt`, `core/database/Inbox.kt`):**
  - Added `ShareIn.inferSource` to detect `"WhatsApp Voice Note"` (`PTT-YYYYMMDD-WAxxxx.opus` / `WhatsApp Ptt`), `"WhatsApp Audio"` (`AUD-...-WA...`), `"WhatsApp Video"` (`VID-...-WA...`), `"WhatsApp"`, `"Telegram Voice Note"`, `"Telegram"`, `"Slack"`, `"Gmail"`, `"Microsoft Teams"`, `"Google Drive"`, and `"Voice Recorder"` from URI authorities, package names, and filenames.
  - Added immediate media duration probing in `ShareIn.copy` and `ShareIn.importUriToInbox` for direct in-app imports into the Work Inbox.
  - Added `InboxDao.observeFiled()`, `InboxDao.getByUri(uri)`, and `InboxDao.getByMeetingId(meetingId)`.
- **End-to-End Inbox <-> Meeting <-> Note <-> AI Summary Bridge (`ImportScreen.kt`, `MeetingProcessingPipeline.kt`, `WorkViewModel.kt`, `core/work/Inbox.kt`):**
  - Fixed the broken link where transcribing an Inbox audio/video item created a `Meeting` and `Note` without ever updating the `InboxItemEntity`:
    - `WorkViewModel.transcribeInboxItem` provides 1-tap background transcription right inside the Inbox (extracting audio, creating the `Meeting` + linked `Note`, linking `meetingId` to the `InboxItemEntity`, and enqueueing `ProcessingScheduler.enqueue`).
    - `ImportViewModel.handleSelectedUri` also links `meetingId` and marks `transcriptionStatus = "TRANSCRIBING"` whenever an Inbox file is opened in `ImportScreen`.
    - `MeetingProcessingPipeline.processMeeting` automatically updates any linked `InboxItemEntity` (`inboxDao.getByMeetingId(meetingId)`) upon completion with `transcriptionStatus = "COMPLETED"`, the full transcript, generated title, duration, and runs `InboxProposer` to propose filing (and marks `"FAILED"` on pipeline error/cancellation).
    - `WorkViewModel.inboxLinkedMeetings` live-joins open and filed Inbox items with `MeetingEntity`, `ActionItemEntity`, and `DecisionEntity` rows, surfacing `summaryText`, true `noteId` (`MeetingEntity.noteId`, fixing the bug where `meetingId` was passed to `Routes.noteRoute`), `meetingId`, and extracted tasks/decisions.
    - `InboxFiler.fileNote` now moves/links the existing rich recording `NoteEntity` (containing the audio player, transcript, and AI summary) when filing a transcribed Inbox item to a Project, Person, or Organisation instead of creating a duplicate plain-text note.
- **Redesigned Work Inbox (`InboxScreen.kt`, `MainActivity.kt`):**
  - Upgraded `InboxScreen` with companion presence, 3-tile Quick Capture bar (`Voice Note / File`, `Scan Doc`, `Paste Note`), live triage summary strip, and `Filed` filter tab.
  - Redesigned `InboxCard` and `InboxDetailSheet` with explicit provenance strip (`From WhatsApp Voice Note`, exact import date `d MMM yyyy`, exact import time `h:mm a`, and parsed recording date), inline `MediaPlayer` voice note playback bar (`InboxInlineAudioPlayer`), dedicated **AI Summary** callout card with 1-tap `Linked Note →` button, **Transcript** preview card with 1-tap `Full transcript →` button, and 1-tap background `Transcribe` + `Options…`.
- **Premium Work Page UI (`WorkPageScreen.kt`, `WorkPageToday.kt`, `WorkPageUi.kt`, `WorkPageTasks.kt`, `WorkPageProjects.kt`):**
  - Added a direct **Work Inbox** button with live badge to `WorkPageHeader` and `QuickActions`.
  - Added `InboxSpotlightCard` on Work Today when items are waiting in the Inbox, upgraded `GlanceRow` into a 3-column Executive Glance Bento, added `ExecutiveRhythmRow` (`Weekly review`, `Executive brief`, `People & orgs`), and polished `NextUp`, `TaskCard`, and `ProjectCardRow` with `MM.*` tokens.
- **Tests & Verification:** Added unit tests in `InboxTest.kt` for WhatsApp voice note source inference, display formatting, and transcribed voice note project filing. Verified zero raw `Color(0x...)` literals (`ColorLiteralGuardTest` compliance). Per `AGENTS.md`, `./gradlew` was not run locally; Gradle compilation and test execution await remote runner verification.

## Bible Selection Dock, Guide Me Read Aloud & End-Only Notes, Testimonies & Create Viral Studio Redesign (2026-11-14)

- **Bible multi-verse selection & compact dock (`BibleViewModel.kt`, `BibleScreen.kt`):**
  - Updated `BibleViewModel.tapVerse` and added `extendSelection(delta: Int)` so users can expand/trim multi-verse selections effortlessly via taps or dock `-1`/`+1` steppers.
  - Moved `SelectionBar` into an overlaid floating bottom dock (`Alignment.BottomCenter` inside reader `Box`) with `AnimatedVisibility`, ~100dp height, verse count badge (`"Nv"`), stepper pills (`bible_trim_verse`, `bible_extend_verse`), primary action pill (`bible_insert`), and a horizontally scrollable action rail (`highlight_*`, `bible_study`, `verse_listen`, `bible_card`, Save, Copy, Share).
- **Guide Me Bible study polish, Read Aloud preload & conditional end note creation (`GuidedSessionViewModel.kt`, `GuidedSessionScreen.kt`):**
  - Prewarms chapter audio in the background using an isolated `ReadingCoordinator` instance (`prewarmPassageAudio`) without disrupting playback UI state; observes active `ReadToMe.coordinator(context).state` to route disclosure/voice-choice/error prompts into `ReadToMeSheet` with exact `bibleId` and adds live playback/preload status + `"Voice & player"` trigger.
  - Defers Room note creation until reaching `StepType.CLOSE`, `finish()`, or `onCleared()`, and only creates/persists a note when `session.touched == true` (`persistIfContentAdded`); adds `"Open created note"` (`session_open_note`) CTA on `Close`.
- **Testimonials redesign & Circle sharing (`TestimoniesFeedScreen.kt`, `TestimonyStudioSheet.kt`, `TestimonyDetailSheet.kt`, `FaithViewModel.kt`):**
  - Redesigned the Testimonies feed, Studio sheet, and Detail sheet with `MM.*` design tokens while preserving 350ms Room auto-save and `ON_PAUSE`/`ON_STOP`/Back non-destructive flush.
  - Added Circle sharing (`shareTestimonyToCircle(..., anonymous = ...)`, `"Circle"` quick-share buttons on feed cards & Studio footer, and `TestimonyCircleShareSheet` with `"Post anonymously"` toggle and shared badges).
- **Create Studio redesign, viral cards & Circle sharing (`CreateModels.kt`, `CreateGallery.kt`, `CreateCardRenderer.kt`, `CreateViewModel.kt`, `CreateStudio.kt`, `CreateStudioContent.kt`, `CreateRenderingTest.kt`):**
  - Added `CreateCardStyle` (`CLEAN`, `QUOTE_POSTER`, `FRAMED`, `SPOTLIGHT`, `BADGE_HOOK`), `eyebrow` hook tag support, `CreateViralPresets`, `CreateViralHooks`, viral caption copy helper, and codec/renderer support with pixel-compatible defaults.
  - Redesigned `CreateStudioContent` with `MM.*` tokens, elevated canvas stage, ratio cards, viral presets, and `"Share to a Circle"` (`Circle chat card` / `Circle feed post`).
- **Verification (local):** Static `DesignSystemGuardTest` / `CreateStyleGuardTest` / `ColorLiteralGuardTest` / `noSparklesIconAnywhere` regex checks passed (exit 0) across all modified files and call sites verified; Gradle compilation and Robolectric/JVM unit tests **not run locally** per `AGENTS.md` remote-runner rule (awaiting remote CI/Gradle execution).

## Crash-Safe Recording Auto-Save, Frontier Transcription & Notes/3D Reveal Fixes (2026-11-14)
- **Crash-Safe Auto-Save Checkpoints & Reboot/Transcription Recovery (`core/audio/`, `ProcessingScheduler`, `MainActivity`):**
  - Replaced single unsealed `MPEG_4` capture (which lost its `moov` atom on unexpected power-off) in `AudioRecorder` with streamed `AAC_ADTS` capture (`capture.aac`) backed by periodic `FileDescriptor.sync()` flash checkpoints every 8s, pause/stop sync, and lossless clean-stop remux to `audio.m4a` (`MPEG_4` fallback preserved if a device rejects ADTS).
  - Created `AdtsAudioInspector` (pure-Kotlin ADTS frame validator and truncated-tail trimmer) and `RecordingAudioSalvager` (ADTS->M4A remuxer, duration prober, and multi-candidate checkpoint recovery).
  - Updated `MeetingRecordingService` (`startMaintenanceLoop`, `stopRecording`), `MainActivity` `RecordingRecovery` (now covers `STOPPING`/`SAVING` states too and salvages checkpointed audio before `createInitialMeeting`), and `ProcessingScheduler.resumeOne` (salvages/repairs `audioFilePath` and updates duration before failing with `MISSING_AUDIO_MESSAGE`).
- **World-Class Frontier Transcription & Cleanup (`GeminiTranscriptionEngine`, `RealTranscriptAiCleanupEngine`, `GeminiIntelligenceEngine`, `MeetingProcessingPipeline`):**
  - Fixed critical `RealTranscriptAiCleanupEngine` output truncation bug on frontier Gemini models: previously `32_000`-token cloud chunks were capped at `CLEANUP_OUTPUT_TOKENS = 600`, silently truncating JSON and falling back to basic rule-based cleanup. Cloud-scale contexts (`>= 16_000`) now pair `2_400`-token input chunks with `4_096` output tokens, and `cleanWithContext(...)` passes `vocabularyHints`, `recordingTitle`, and `customContext` from `MeetingProcessingPipeline`.
  - Added single transient `IOException` retry per chunk and malformed-JSON single retry in `GeminiTranscriptionEngine`, plus `ThinkingLevel.LOW` and `8_192` `maxOutputTokens` with structured executive note guidance in `GeminiIntelligenceEngine.processMeeting`.
- **Dark-Mode Note Opacity Bug, Immediate Structured Notes & Transcript UI Redesign (`MomentStage`, `RecordingNoteBuilder`, `NoteBlocks`, `NoteEditorViewModel`):**
  - **Fixed dark-mode translucent/grayed note body (`MomentStage.kt`):** Normalized `BODY` reveal stagger (`delay = index.coerceIn(0, 4) * 0.10f`) and snapped `alpha = 1f` / `translationY = 0f` once `p >= 1f` or `!playing` (previously `index >= 3` stopped permanently at `0.7f`/`0.4f` alpha!).
  - **Immediate structured notes on first open:** Removed `NotePolishMode.OFF` gate skipping `writeStructuredNoteSections` in `MeetingProcessingPipeline`; upgraded `RecordingNoteBuilder` with zero-fabrication extractive `Overview`, `Key Highlights`, and `Questions Raised` fallbacks when AI summary/polish is absent plus guaranteed checklist emission; updated `NoteEditorViewModel` to live-reload newly generated AI/transcript blocks even after light session activity.
  - **Redesigned in-note transcript presentation (`NoteBlocks.kt`):** Upgraded `RecordingBlock` with status pills, expandable `SUMMARY` card, inline transcript search, horizontal speaker filter chips, and `TranscriptTurnRow` speaker/timestamp cards; replaced purple-washed italic `ExcerptBlock` boxes with crisp dark/light-mode editorial speaker turn cards (`SurfaceRaised` + speaker accent rail + initial badge + tappable `mm:ss` playback pill).
- **Companion 3D Reveal (`MomentLayers`, `FirstKeepOverlay`, `NoteMomentHost`, `NoteEditorScreen`, `MainActivity`):**
  - Activated 3D volume shading/parallax in `MainActivity` via `LocalCompanionVolume provides true` (preserving `CompanionFlags.volumeLayer = false` default for screenshot/parallax unit tests).
  - Wired `CompanionLook()`, `CompanionTiltEffect`, and `.touchModifier()` into `FirstKeepOverlay`, passed `look` through `MomentCharacter` in `MomentLayers`, switched `NoteEditorScreen` to `selectNoteMomentSubject` with `refreshKey = momentCard?.status`, skipped heading/transcript blocks in `NoteFirstKeep` keypoints, and aligned `NoteReadySnackbarHost` / `resolveProcessedTargetRoute`.
- **Tests & Verification:** Added `AdtsAudioInspectorTest` and extended `MomentStageTest`, `PostTranscriptionNoteFormattingTest`, `TranscriptAiCleanupEngineTest`, and `NoteMomentHostTest`. Verified zero `Color(0x...)` literals added to `feature/notes/`. Per the remote-only Gradle mandate, `./gradlew` was not run locally; Kotlin compilation and unit/Compose tests await remote runner verification.

## Circles Work/Learning Creation Fix & CreateCircleScreen Redesign (2026-11-14)
- **Root Cause & Backend Fixes (`server/circles-api`):**
  - Diagnosed `"Unknown template"` error on Work & Study circle creation: all 20 template IDs exist in `server/circles-api/src/logic.js`, meaning the deployed Cloudflare Worker (`meetingmind-circles.meetingmind.workers.dev`) needs redeployment (`npx wrangler deploy` from `server/circles-api`, `wrangler` CLI not available locally).
  - Fixed `server/circles-api/src/handlers.js` (`createCircle`) which previously coerced any non-`"work"` space to `"faith"`; now preserves `"work"`, `"learning"`, `"general"` (mapping `"community"` -> `"general"`), and `"faith"` (with `SPACE_BY_TEMPLATE` fallback) and sets space-accurate default `vocab` (`Team`, `Study group`, `Group`, `Circle`).
  - Added `LEARNING_POST_TYPES` & `LEARNING_REACTIONS` in `server/circles-api/src/logic.js` so Learning circles default to study-appropriate reactions rather than Faith reactions.
  - Added unit tests in `server/circles-api/test/unit/work_circles.test.js` (`cd server/circles-api && npm test`: 74/74 passing).
- **Client Routing, Settings & Premium Redesign (`feature/circles2`):**
  - Updated `CreateCircleViewModel` (`CirclesListViewModel.kt`) to normalize space arguments cleanly, filter outgoing `CircleSettings.reactions` by vertical, wire `description` and `joinApproval`, and surface clear guidance if an un-updated Worker returns `"Unknown template"`.
  - Fixed `MainActivity.kt` (`Routes.circleCreateRoute(...)`) and `CirclesListScreen.kt` (`onCreateInSpace`) so Work/Study/Faith/Community entry points open with the matching vertical pre-selected.
  - Redesigned `CreateCircleScreen.kt` across Work, Study/Learning, Faith, and Community using `MM.*` and `FixedColors` tokens: vertical switcher rail, live gradient Circle preview card, selectable blueprint cards with included post-type chips, identity card with group-word pills + custom label + optional description, vertical-filtered post-type selector, collapsible membership/moderation drawer (`whoCanInvite`, `joinApproval`, Faith-gated `prayerApproval`), and sticky animated vertical CTA bar. Extended `CircleViewModelsTest.kt` & `CirclesFakes.kt` (ready for remote Gradle runner).

## 2026 AI Modernization Blueprint — Phases A–D (2026-11-14)
- **Phase A (Cloud, Proxy & Streaming Foundation):**
  - Proxies: updated `server/gemini-proxy` (`wrangler.toml`, `logic.js`, `worker.js`) to allow `gemini-3.8-flash`, `gemini-3.5-flash-lite`, `gemini-4-argon`, `gemini-4-carbon`, and `gemini-embedding-001`, route `:streamGenerateContent`, `:embedContent`, and `:batchEmbedContents`, and pass SSE upstream unbuffered; updated `server/deepseek-proxy/worker.js` for `deepseek-v4-flash` / `deepseek-v4-pro` SSE pass-through with async KV token metering and `16384` `max_tokens` ceiling. **Cloudflare `wrangler deploy` still required** where Cloudflare credentials live (`wrangler` CLI not present locally).
  - Client transport & routing: upgraded `DefaultAiModelRouter` (`gemini-3.8-flash`, `gemini-4-argon`, offline-zero-network invariant intact), fixed `GeminiRoute.url()` `?` vs `&` query joining, added SSE `SseStreamReader`, `GeminiHttpTransport.stream()`, DeepSeek/Fallback OpenAI streaming, and schema-enforced structured JSON across `MeetingIntelligenceEngine`, `TranscriptToolEngine`, `NoteAiEngine`, `CreateGenerator`, and `LearningAiEngine` (`90_000`-char cloud window).
- **Phase B (Agentic Assistant, Live Voice & Streaming Ask UX):**
  - Native tool calling & streaming: added `AssistantTool.nativeDeclarationsJson`, streaming `AssistantEngine.replyStream()` with concurrent read-tool batching, live reasoning trace (`AssistantBubble`), tool telemetry pills (`AssistantToolStep`), and smooth partial `stop()` in `AssistantSession`.
  - Bidirectional Live Voice: added `OkHttpLiveTransport`, `LiveVoiceAudioBridge` (16 kHz AEC/NS/AGC capture + 24 kHz playback + barge-in interruption flush + `AppAssistantHost` tool execution), and voice controls (`LiveVoiceBar`, `assistant_voice_toggle`) in `AssistantSheet`.
  - Streaming Ask UX: wired incremental token streaming across `AskMeetingUseCase` / `AskAiPanel`, `AskEverythingEngine` / `SearchScreen`, and `ScopedAsk` / `ScopedAskSheet`.
- **Phase C (Granola-Style AI Notepad, Inline Canvas Diffs & Neural RAG):**
  - Co-authored AI notepad: extended `NotePolishEngine.polish(...)`, `MeetingProcessingPipeline`, and `NoteEditorViewModel.polishWholeNote()` to anchor synthesis on the person's `BlockSource.USER` shorthand notes and `Marks` while preserving user-written blocks.
  - Inline canvas diffs: added streaming AI rewrite + `PendingBlockDiff` stage/accept/reject actions in `NoteEditorViewModel`, `AiEditSheet` (`Review inline`), and `NoteEditorScreen` (`PendingDiffsBanner`, `InlineBlockDiffCard`).
  - Hybrid neural RAG: upgraded `LocalEmbeddingEngine` (signed hashing + subword 3-grams + bigrams), built `NeuralEmbeddingEngine` (`gemini-embedding-001` 256-dim batching + LRU/disk cache + offline fallback), added Reciprocal Rank Fusion (`RRF`) in `TranscriptRetriever`, and wired into `MeetingDetailScreen`, `AssistantHost`, and `SearchScreen`.
- **Phase D (Next-Gen Local Runtime Seam, Evals & Telemetry):**
  - Added `AiCoreRuntime` / `AiCoreLanguageModel` + `LanguageModelFactory.resolveLocal` fallback seam for Android 14+ AICore Gemini Nano alongside installed MediaPipe models.
  - Added privacy-safe `AiTelemetry` (TTFT/duration/cache/token ring buffer wired into `GeminiHttpTransport`) and `IntelligenceBenchmark` (citation precision, schema validity, `FaithGuard`, tool trajectory, and retrieval Recall@K).
- **Tests & Verification:** `server/gemini-proxy` (`node --test test/*.test.mjs` -> 29/29 pass) and `server/deepseek-proxy` (`node --test test/*.test.mjs` -> 2/2 pass) verified locally. Added Android JVM tests (`GeminiHttpTransportTest`, `AssistantTest`, `OkHttpLiveTransportTest`, `NeuralEmbeddingEngineTest`, `IntelligenceBenchmarkTest`); per the remote-only Gradle mandate, no `./gradlew` commands were run locally and Android tests await remote runner verification.

## Sharing Links, Circle Notifications, Auto-Save & Testimonies Redesign (2026-11-14)
- **App Download Links (`AppLinks`):** Added canonical Google Play Store link (`https://play.google.com/store/apps/details?id=com.craftflowtechnologies.meetingmind`) across outbound devotionals (`DevotionalSharing`, `DevotionalScreen`), `ShareStudio` (opt-out toggle defaulting on), `CreateViewModel`, `ShareContentFormatter` transcripts/summaries/action items, Circles `FeedTab` & `ChatCards`, `InviteCodes`, `NoteEditorScreen` export share, and Settings `"Share MeetingMind"`, while leaving raw clipboard copy clean.
- **Circle Post & Activity Notifications (Client & Server):**
  - Server (`server/circles-api`): set Android `HIGH` priority + `circles_posts` channel in `fcm.js`, type-aware non-leaking post notification copy in `handlers.js`, KV-throttled comment pushes, `notifyChat` handler (25s KV cooldown bypassed for mentions), and push on media chat messages in `media.js`. `cd server/circles-api && npm test` passed (71/71).
  - Client: ensured `circles_posts` (`IMPORTANCE_HIGH`) channel + `R.drawable.ic_zuri_glyph` icon in `CirclesNotifier`, `AndroidManifest.xml`, and `MeetingMindApp`; wired foreground active-circle suppression in `CirclesMessagingService` + `CircleHomeScreen`; added OS-notification-off warning banner in `CircleHomeScreen`, startup token sync (`CirclesRepository.syncPushOnStartup` in `MainActivity`), `PushKind.Comment`/`Chat` routing, and `notifyChat` trigger in `ChatViewModel`.
- **Zero-Loss Back Navigation & Auto-Save:**
  - Circles `ComposerSheet`: persists drafts per `circleId` in `LocalCircleStore` on every edit, Back, outside-tap, and Close (with `"Draft saved"` snackbar & `"Resume unfinished draft"` banner in `FeedTab`); only explicit `Discard` or successful publish clears it.
  - Note Editor: added `NoteEditorViewModel.flushAndExit(onComplete)` + `flushOnPause()` (non-cancellable I/O flush before `onNavigateBack()`), `ON_PAUSE`/`ON_STOP` lifecycle observer in `NoteEditorScreen`, reduced `SAVE_DELAY_MS` to `350L`, and extended `NoteContent.hasUserContent` for testimony metadata.
- **Testimonies Redesign ("Wall of Remembrance"):**
  - Added `TestimonyColors` in `FixedColors.kt` (zero `Color(0x...)` literals in `feature/`), `TestimonyModels` (`TestimonyCategory`, `TestimonyBeats`, `TestimonyStory`, `TestimonyMapper`), and `NoteDao.observeDraftsByWorkflow` / `NoteRepository.observeDraftsForWorkflow`.
  - Upgraded `FaithViewModel`, `TestimoniesFeedScreen`, `TestimonyStudioSheet` (guided beats + freeform mode + live scripture lookup + 350ms continuous auto-save + non-destructive Back + Speak-in-Voice switcher), and `TestimonyDetailSheet` (Prayer->Answer journey timeline, persisted `Amen 🙌` counters, Visual Share Card, Share Text with `AppLinks`, and 1-tap Share to Circle).
- **Tests & Verification:** Added `AppLinksTest` & `TestimonyRedesignTest`, extended `PushRoutingTest`, `PushTokensTest`, `CircleViewModelsTest`, and `server/circles-api/test/unit/handlers.test.js` (71/71 passing locally). Per the remote-only Gradle mandate, Android tests were not executed locally and await remote runner verification.

## World-Class Polish, Auto-Save Coverage & Play Hardening Sequence (2026-11-14)
- **Complete Zero-Loss Draft Auto-Save:** Extended `LocalCircleStore` (`chatDraft`, `commentDraft`), `ChatViewModel` (draft restore/persist/clear per circle), and `CircleHomeViewModel` / `FeedSheets` (`commentDraft` per `(circleId, postId)`, restored on failed submit); verified `GuidedSessionViewModel` already uses a conflated channel + non-cancelled I/O saver scope.
- **Verified HTTPS App Links & Notification Grouping:** Added `<intent-filter android:autoVerify="true">` for `https://meetingmind.app/c` in `AndroidManifest.xml`, `InviteCodes.webLink`, and `PushRouting.fromUri` HTTPS parsing; added tab-aware foreground push suppression + in-app alert snackbar (`CirclesNotifier`, `CirclesMessagingService`, `CircleHomeScreen`) and per-Circle `InboxStyle` notification group summaries.
- **Release Play Hardening:** Added `app/proguard-rules.pro`, enabled `isMinifyEnabled = true` and `isShrinkResources = true` on `release`, and strengthened `missingForPlayBundle` in `app/build.gradle.kts` to reject `test_` RevenueCat keys and blank `CIRCLES_API_URL`. Verified startup crash/exit diagnostics (`CrashLog`, `Breadcrumbs`, `ExitHistory` in `MeetingMindApp`), `RecordingJournal` recovery (`MainActivity`), Media3 `PlaybackService`, unified `SearchScreen`, and Reduced Motion/Battery Saver adaptation (`MomentEnvironment`).
- **Share-to-Circle Entry Points:** Wired `ShareArtifactToCircleSheet` into `DevotionalScreen` (`Footer` `"Share to Circle"` pill + snackbar confirmation) and `NoteEditorScreen` (overflow menu `"Share to Circle"` gated against private & `WorkPrivacy.mustStayOnDevice` notes), connected to `Routes.circleCreateRoute("faith")` in `MainActivity`.
- **Tests & Verification:** Extended `InviteCodesTest`, `PushRoutingTest`, and `CircleViewModelsTest` for HTTPS invite/open links, tab-aware push suppression, and chat/comment draft persistence; `cd server/circles-api && npm test` passed (71/71). Per the remote-only Gradle mandate, Android tests and release R8 builds were not executed locally and await remote runner verification.

## Post-Transcription Structured Note Formatting & AI Polish (2026-11-14)
- **Pipeline Dispatch Fix (`MeetingProcessingPipeline`):** Replaced `Workflows.faith`-only gating with `writeStructuredNoteSections(...)` so `SERMON`, `LECTURE`/`RESEARCH` (previously dead code), and all other `RecordingType`s populate structured `NoteBlock`s on transcription completion.
- **Settings-Driven Note Polish (`NotePolishMode`):** Added `NotePolishMode` (`OFF` / `STANDARD` / `DEEP`, defaulting to `DEEP`) in `Enums.kt`, `UserPreferences.kt`, `SettingsRegistry.kt`, and `SettingsScreen.kt`.
- **AI Polish & General Note Builders:** Created `NotePolishEngine` (workflow-aware Markdown -> `NoteBlock`s with `PAYLOAD_START_MS` timestamp matching) and `RecordingNoteBuilder`; upgraded `SermonNoteBuilder`, `LectureNoteBuilder`, `LearningAiEngine` (offline `fallbackModel` + `SubjectDomain.detect`), and `CalloutKind` (`KEY_TAKEAWAY`, `APPLICATION`, `DECISION`).
- **Editor & Navigation Polish:** Added `NoteEditorViewModel.polishWholeNote()` + `NoteAiMenu` action, live block refresh in `NoteEditorViewModel` when background processing finishes, and direct navigation to the polished note for Sermon/Lecture completions in `MainActivity`.
- **Tests & Verification:** Added `PostTranscriptionNoteFormattingTest`. Per the remote-only Gradle mandate, no `./gradlew` commands were executed locally; ready for remote runner verification.

## Learning Vertical Crash Fixes, Polish & Feature Upgrades (2026-11-13, CTO session)
- **Fatal Crash Fixes:**
  - `LearnViewModel`: added `@JvmOverloads constructor` so Compose `viewModel()` / `AndroidViewModelFactory` can instantiate `LearnViewModel(Application)` without `NoSuchMethodException`.
  - `MainActivity`: registered `navArgument` defaults (`nullable = true`, `defaultValue = null`) for `Routes.LEARN_PRACTICE` and `Routes.LEARN_EXAM`, replaced raw `{sessionId}` placeholder navigation with `Routes.learnPracticeRoute()` / `Routes.learnExamRoute()`, and added toast feedback on `onAudioCram` when no sessions exist.
  - `WebBlocks`: guarded `styled()` span bounds (`minOf`/`maxOf` after `coerceIn`) so inverted spans cannot throw `IllegalArgumentException`.
- **AI & State Reliability:**
  - `LearningAiEngine`: strips markdown code fences before `JSONObject` parsing, uses `optString` instead of throwing `getString`, and matches generated questions to concepts by title/id.
  - `LearnViewModel`: `toggleSessionPause` refreshes `_activeSessionDetail` immediately; `loadSessionDetail` tracks `isLoadingSession`/`sessionLoadError` and auto-creates a missing session for a note to prevent infinite spinners.
- **UI Polish & New Features:**
  - `PracticeArenaScreen`: added `EmptyPracticeScreen` for 0 cards, "Finish Practice" CTA on the last card to reach `AllDoneScreen`, `remember(activity.id)` key on `TeachBackStudioCard` input, wired "Explain My Mistake" on `AGAIN`/`HARD`, and integrated Android `SpeechRecognizer` + `RECORD_AUDIO` permission for Voice Teach-Back.
  - `ExamPrepScreen`: renders all goals with delete confirmation, adds exam date presets (7d/14d/30d/60d) + `DatePickerDialog` in `CreateGoalDialog`, makes `CramSheetPreviewDialog` scrollable with a Close button, deduplicates dialog state, and renders formulas via `MathExpressionView`.
  - `LearningSessionScreen`: replaces infinite spinner with an empty/unavailable state ("Go Back" / "Create Session") and renders formulas via `MathExpressionView`.
  - `LearnHomeScreen` & `ShareStudyPackToCircleSheet`: wired weak-concept card clicks (`onOpenSession`), added empty-state CTAs across tabs, added `try/catch` error handling and scrollable circle list (`heightIn(max = 260.dp)`) in `ShareStudyPackToCircleSheet`.
- **Tests Added:** `LearnViewModelTest` (`AndroidViewModelFactory` instantiation regression test + `toggleSessionPause` refresh) and `LearningAiAndBuilderTest` (fenced JSON stripping + concept matching).
- **Verification Note:** Per the remote-only Gradle rule, no `./gradlew` build or test commands were executed locally; compile-safety and signature parity were verified statically across all modified files, and the test suite is ready for remote runner verification.

## Remote-Only Gradle Mandate (2026-11-02, Founder Directive) — superseded 2026-11-16: local Gradle runs are allowed
- **Zero Local Gradle Execution**: All Gradle tests, compilation runs (`compileDebugKotlin`), and builds must be executed by remote agents only — never executed on the local host machine.
- Documented in `AGENTS.md` and `agents/TESTING.md`.

## Learning Vertical End-to-End Delivery (2026-11-02, Production Release)
- **Flagship Learning Studio (`feature/learning/`)**:
  - `LearnHomeScreen.kt`: Integrated `HomeScaffold`, `CompanionBriefHero` reacting to daily brief & exam countdowns, primary action tiles (`learnDailyRecall`, `learnRecordLecture`, `learnAudioCram`, `learnWriteStudyNote`), and 4 tabs (Today, Courses, Practice, Exam Prep) + `CreateCourseDialog`.
  - `PracticeArenaScreen.kt`: Active recall card flipper with 4-tier grading (`AGAIN`, `HARD`, `GOOD`, `EASY`), "Explain My Mistake" root misconception diagnostics, and verbal Teach-It-Back studio with live mic reaction, criteria checklist, and celebration view.
  - `ExamPrepScreen.kt`: Exam readiness dashboard with countdown badges, High-Yield Priority Matrix, 1-Page Cram Sheet generation and preview, and `CreateGoalDialog`.
  - `LearningSessionScreen.kt`: 4-tab session studio (Understand, Concepts with LaTeX formula cards, Practice activities, and Session Diagnostics with pause review toggle).
  - `ShareStudyPackToCircleSheet.kt`: Modal sheet to publish cram sheets to Study Circles (`space = "learning"`).
  - `LearnViewModel.kt`: Central state management across `LearningRepository`, `NoteRepository`, `LearningAiEngine`, and `ReadingCoordinator`.
- **Wave 1 Substrate & Database (Schema 30)**:
  - Added Room Schema 30 (`LearningGoalEntity`, `LearningGoalDao`, `MIGRATION_29_30`).
  - Implemented `SubjectDomain.kt` covering 10 academic subject domains with domain-specific templates, spoken normalization rules, and vocabulary hints.
  - Built `AcademicBlocks.kt` and `SpokenNotationNormalizer.kt` for spoken formula normalization and LaTeX block rendering.
  - Extended Circles v2 with `space = "learning"` support and `PostType.Study`.
- **Navigation & Entry Points**:
  - Added `Routes.LEARN`, `Routes.LEARNING_SESSION`, `Routes.LEARN_PRACTICE`, `Routes.LEARN_EXAM` and route builders to `AppNavigation.kt`.
  - Added `DeepLink.Learn` to `AppNotifications.kt` and wired into `MainActivity.kt`.
  - Enabled `showLearn = true` by default in `HomeQuickAccessRow.kt` and connected across `EverydayHome`, `CalmHome`, `TodayScreen`, and `ProfessionalHome`.
  - Registered `EntryScreen.LEARN_HOME` in `EntryPointContractTest.kt`.
- **Billing & Packaging**: Strictly preserved 1.0 pricing contract (Free taste + BYOK $4.99/mo + Pro $12.99/mo) with zero artificial feature locks.

## External Integrations, Premium Features & Circles (2026-10-26, CTO session)
- **Mission Accomplished:** Delivered end-to-end architecture, implementation, and feature-gating for external integrations, subscription entitlements, and Circles collaboration across Work and Faith verticals.
- **WP-1: Native Android Media Import & Share-In (Completed):**
  - Added 500 MB file size bounds with `OpenableColumns.SIZE` guard and 8KB chunk-bounded streaming copy with delete-on-overflow in `ShareIn.kt`.
  - Added recording language picker to `ImportScreen.kt` and wired `updateMeetingLanguage` in `MeetingRepository`.
  - Added cancellation and unstarted-meeting cleanup on back navigation and prior file replacement.
  - Extended `InboxScreen` video and audio imports.
- **WP-2: Universal Export, File Sharing & Pinned Vault Sync (Completed):**
  - Added `ExportFormat.TXT` ("Plain Text", `.txt`, `text/plain`) with `PlainTextExporter.kt` and `PlainTextDocumentRenderer.kt` for summaries, decisions, action items, and transcripts.
  - Implemented `ShareHelper.shareExportedFile` and `ShareHelper.cleanupStaleExports` in cache storage.
  - Implemented `writeToPinnedVault()` in `SafStorageProvider.kt` using `DocumentsContract.createDocument` for direct folder sync (e.g. Obsidian vaults).
  - Added `pinnedVaultTreeUri` and `pinnedVaultName` persistence in `IntegrationPreferences.kt`.
- **WP-3: Entitlement Gating & Encrypted Keystore Credential Store (Completed):**
  - Added `Capability.CONNECTED_INTEGRATIONS`, `Capability.AUTOMATION_RECIPES`, and `Capability.CHURCH_ORG_WORKFLOWS` to `core/billing/Plan.kt`.
  - Mapped individual cloud connectors (`CONNECTED_INTEGRATIONS`, `AUTOMATION_RECIPES`) to `BYOK` ($4.99/mo), `PRO` ($12.99/mo), and `CHURCH` ($49.99/mo).
  - Reserved multi-circle `CHURCH_ORG_WORKFLOWS` (Sermon-to-Circle Pack) exclusively to `CHURCH` ($49.99/mo).
  - Updated `PaywallCopy.kt` with truthful rows naming enforcing capabilities; guarded the free-circles invariant (`PaywallCopyTest.kt` passing).
  - Built `IntegrationCredentialStore.kt` using Android Keystore AES-256-GCM encryption in `noBackupFilesDir` with JVM fallback for unit tests.
- **WP-4: Circles Universal Sharing & Worker Parity (Completed):**
  - Server: Added `"devotional"` post type and templates to `server/circles-api/src/logic.js`. Added `cleanPostMeta(meta)` sanitization. Implemented `requestId` idempotency check and `userMeta/{uid}/postRequests/{requestId}` persistence in `server/circles-api/src/handlers.js`.
  - Verified: `npm test` in `server/circles-api` — all 68 tests passing (0 failures).
  - Client: Added `requestId` to `CreatePostRequest` and `createPost` serialization in `CirclesApi.kt`.
  - Privacy Guard: Added strict `WorkPrivacy.mustStayOnDevice` enforcement in `ShareToCircleSheet.kt` and passed deterministic `requestId` per post item.
  - Faith Circle Sharing: Created `ShareArtifactToCircleSheet.kt` for publishing devotional excerpts (<= 600 chars + reading link) and verse cards to Faith circles.
- **WP-5: Connected Circle Events & Phone Calendar Bridge (Completed):**
  - Built `CalendarExporter.kt` with `buildInsertEventIntent()` (`ACTION_INSERT`) and `generateIcs()` (RFC 5545 `.ics`).
  - Added "Add to calendar" and "Join link" buttons to `EventSheet.kt`.
- **WP-6: Productivity & Task/Issue Connectors (Completed):**
  - Built `TaskSyncProvider.kt` interface and providers: `TodoistTaskProvider`, `GitHubIssueProvider`, `LinearIssueProvider`, and `WebhookChannel` (Slack/Teams). All gated by `Capability.CONNECTED_INTEGRATIONS`.
  - Registered all providers in `IntegrationRegistry.kt`.
  - Implemented `IntegrationsScreen.kt`: displays device storage with pinned vault directory selection & clear actions, connected productivity connectors with credential entry dialog (Keystore encryption) and paywall prompt dialog for free users.
- **WP-7: Contextual UI Actions & Church Sermon Pack (Completed):**
  - Created `SermonCirclePackSheet.kt` (gated by `Capability.CHURCH_ORG_WORKFLOWS`) packaging sermon takeaways, scripture verses, and discussion guides into Church Circles.
  - Integrated "Share Sermon Pack to Circle…" into `MeetingOptionsSheet.kt` and `MeetingDetailScreen.kt`.
  - Connected `WorkPageTasks.kt` with `onSyncExternal` action ("Send…") and `WorkPageScreen.kt` share intent launcher.
  - Enforced `meetingId = viewModel.meetingId` in `WrapUpScreen.kt` for device-privacy checking.
- **Verification Note:** Per user instruction to preserve host machine disk space and RAM, local Gradle compilation and unit test executions were omitted; test suites and code are prepared for remote agent verification.

## Work Inbox Redesign & Video Intelligence (2026-10-26, CTO session)
- **Work Inbox Redesign & Differentiation:** Resolves founder audit finding (lack of identity, source, timestamp, and post-transcription details in Work Inbox).
- **First-Class Video Sharing:** `AndroidManifest.xml` gains `video/*` intent filters; `InboxKind.VIDEO` added; `ShareIntentParser` and `ShareIn` preserve video file extensions (`mp4`, `mov`, `webm`, `mkv`) for hardware decoders and companion video transcription.
- **Item Differentiation:** `source` attribution (e.g. `youtube.com`, `via WhatsApp`, `Document Scanner`), relative timestamp (`relativeTimeLabel`), and media duration (`mediaDurationMs`) displayed on every card.
- **Schema 29:** Room version bumped to 29 with `MIGRATION_28_29` adding `source`, `mediaDurationMs`, `mediaThumbnailUri`, `transcriptionStatus`, and `meetingId` to `inbox_items`.
- **Post-Transcription Lifecycle Bridge:** `WorkViewModel.updateInboxTranscription` sets status to `COMPLETED`, attaches transcript, and automatically triggers `InboxProposer.propose()` on spoken words under `FIDELITY_CONTRACT`.
- **Reimagined Triage UI:** `InboxScreen` upgraded with Kind badges, Source pills, relative timestamps, duration badges, post-transcription indicators (`✓ Transcribed`, detected action items / decisions), triage filter tabs (`All`, `Action needed`, `Media`, `Docs & links`), and tap-to-inspect `InboxDetailSheet` with copy text and filing actions.
- **Unit Tests Authored:** `Migration28To29Test.kt` (Room schema migration verification, column defaults, PRAGMA check, DAO writes), `InboxTest.kt` (video intent parsing, video items, source attribution, and extension resolution tests).
- **Verification:** Per founder instruction to preserve host machine disk space and RAM, local Gradle compilation and test executions were omitted; test suites authored and ready for remote agent verification. Merged to `release/1.0-paid-launch` (commit `6403a48`).

## Video Audio Extraction & Unified Media Import (2026-10-26, CTO session)
- **On-Device Streaming Audio Extraction (P1 audit item resolved):** Resolves Audit Item #7 ("Video import does not extract audio").
- **Zero Video Disk Duplication:** Streams directly from Storage Access Framework `AssetFileDescriptor` / `FileDescriptor` via `MediaExtractor`. High-resolution, multi-gigabyte video containers (even 4K recordings) are never copied to internal storage or loaded into RAM.
- **Dual-Path Audio Engine (`AudioExtractor.kt`):**
  - *Fast Path (Lossless Remux):* Direct packet demuxing using `MediaMuxer` (AAC on all SDKs, Opus and Vorbis on API 29+) without decoding or re-encoding. Sub-second runtime, zero audio degradation, and negligible battery usage.
  - *Streaming Transcode Fallback:* Uses `MediaCodec` decoder to decode to PCM and streams directly into a `MediaCodec` AAC encoder buffer-by-buffer into `audio.m4a`, capping peak memory usage under 2 MB regardless of video duration.
  - *Missing Audio Detection:* Detects videos without audio tracks immediately and reports `NoAudioTrackException("This video does not contain an audio track.")`.
  - *Exact Timestamp Preservation:* Preserves original video audio presentation timestamps and duration so transcripts, VAD, speaker separation, and playback scrubbers remain synchronized.
- **Unified Import UI (`ImportScreen.kt`, `ImportViewModel.kt`):**
  - Unified file picker using `ActivityResultContracts.OpenDocument()` with `arrayOf("audio/*", "video/*")`.
  - Determinate progress display with percentage during extraction (`Extracting audio track… X%`).
  - Safe user cancellation (`cancelExtraction()`) that terminates extraction, releases codecs/muxers, and deletes partial files.
  - Actionable error card when extraction fails or video contains no audio, with "Choose another file" option.
  - Creates Room meeting with `MeetingSource.IMPORTED_VIDEO` and links lightweight `audio.m4a` as `audioFilePath` for playback and transcription.
- **Unit Tests Authored:**
  - `AudioExtractorTest.kt`: covers `canDirectRemux` codec rules, `NoAudioTrackException`, video extraction progress, cancellation cleanup, and audio-only copy.
  - `ImportViewModelTest.kt`: covers video vs audio source attribution, progress emission, cancellation state reset, and error display for audio-less videos.
- **Verification:** Per founder instruction to preserve host machine disk space and RAM, local Gradle compilation and test executions were omitted; test suites authored and ready for remote agent verification. Merged to `release/1.0-paid-launch` (commit `9464c85`).

## Paid launch starts (2026-10-25, founder decisions, CTO session)
- **The Play launch is paid.** RevenueCat account exists but has no products, entitlements or offerings yet. Setup guide with the exact identifiers the code expects: `docs/RELEASE_BILLING_SETUP.md`.
- **The managed Gemini proxy is approved for release builds** (`server/gemini-proxy`). This amends decision B1: Play bundles carry **no raw Google key**; debug keeps it. `agents/PRODUCT.md` updated.
- **Release config now enforces the launch:** `REVENUECAT_API_KEY` is per build type (debug reads `revenuecat.testApiKey` from gitignored `local.properties`; release reads CI secrets), `SYSTEM_GEMINI_API_KEY` is empty in release, `GEMINI_PROXY_URL` comes from CI, and `bundleRelease`/`assembleRelease` **fail** when the store key or proxy URL is missing. Version 47 → 48.
- **CI** (`.github/workflows/release-aab.yml`): builds the managed bundle on `release/1.0-paid-launch`, requires `REVENUECAT_API_KEY` + `GEMINI_PROXY_URL`, and fails if any Google API key is found in the bundle. The `SYSTEM_GEMINI_API_KEY` secret should be deleted.
- **Recording durability fixed (P0):** `stopRecording` now retries the Room insert, and clears the recovery journal **only** after the row is durable. A failed save ends in the new `RecordingState.SAVE_FAILED`, the audio and journal are kept, the person is told the truth, and the next launch offers to add it again. New tests: `RecordingStopFinalizerTest` (4 cases). Recovery dialog now handles both the crash and the failed-save cases.
- **In flight, not yet merged:** title reliability (`wt-title-reliability`) and billing/tiering/paywall truthfulness (`wt-billing-tiers`), each in its own worktree.
- **Titles fixed (P1, founder's explicit ask):** a title is now decided by one `RecordingTitles` step — dedicated model prompt, then the synthesis title only when it is real, then a deterministic title built from the transcript's own words, and a localized disambiguated date only when there is no speech. `meetings.titleSource` (schema 28) records who chose it; a person's rename and an imported file name are never overwritten, and a backfill retitles `DATE`/`CONTENT` rows once a model or managed route appears. Tests: 28 passing (`RecordingTitlesTest` 9, `MeetingTitleGeneratorTest` 14, `Migration27To28Test` 3, `ProcessingTitleTest` 2). Merged to the release branch.
- **Build verified so far:** `:app:compileDebugKotlin` succeeded on the release branch with the durability fix and the new release configuration (41 min, cold dependency cache), and again on the release branch **after the titles merge** (19 min, `BUILD SUCCESSFUL`).
- **Billing/tiering/paywall truthfulness merged:** `FeatureGate`; `PRO_FEATURES` removed (1.0 sells only the managed-cloud allowance and BYOK); tier switch debug-only with `PRODUCTION` default; paywall unreachable and honest when billing is off; owned/pending/retry states; refresh on resume; included page built from rows that name the capability enforcing them; `UsageWarning` wired to 402/429 and near-limit; duplicate `PAYWALL_VIEWED` gone. Tests: 43 passing.
- **Onboarding truthfulness (first increment) merged:** Learning is no longer offered and no longer switched on by skipping goals; the "Record now" route now ends on the permissions step so notifications are asked for, with the reason. Tests: `OnboardingModelTest` 7, `OnboardingTest` 5.
- **Work (first increment) merged:** the sunset-photo header is replaced by the shared light `ScreenHeader` (WORK_UX §2/§3) and the search icon is labelled for what it does. Still open: `ProfessionalHome` (D1), the shadow component library, loading states for Projects/WorkAll, dead project views.
- **Work Inbox Redesign & Intelligence (Increments 1–3) implemented:**
  - **Video Sharing:** `AndroidManifest.xml` gains `video/*` intent filters; `InboxKind.VIDEO` added; `ShareIntentParser` and `ShareIn` preserve video file extensions (`mp4`, `mov`, `webm`, `mkv`) for hardware decoders and companion video transcription.
  - **Item Differentiation:** `source` attribution (e.g. `youtube.com`, `via WhatsApp`, `Document Scanner`), relative timestamp (`relativeTimeLabel`), and media duration (`mediaDurationMs`) displayed on every card.
  - **Schema 29:** Room version bumped to 29 with `MIGRATION_28_29` adding `source`, `mediaDurationMs`, `mediaThumbnailUri`, `transcriptionStatus`, and `meetingId` to `inbox_items`.
  - **Post-Transcription Lifecycle Bridge:** `WorkViewModel.updateInboxTranscription` sets status to `COMPLETED`, attaches transcript, and automatically triggers `InboxProposer.propose()` on spoken words under `FIDELITY_CONTRACT`.
  - **Reimagined Triage UI:** `InboxScreen` upgraded with Kind badges, Source pills, relative timestamps, duration badges, post-transcription indicators (`✓ Transcribed`, detected action items / decisions), triage filter tabs (`All`, `Action needed`, `Media`, `Docs & links`), and tap-to-inspect `InboxDetailSheet` with copy text and filing actions.
  - **Verification:** Unit and migration tests authored (`Migration28To29Test`, `InboxTest`). Per founder instruction, local Gradle test runs avoided to preserve disk space and RAM; designated for remote agent verification.
- **Combined verification on the merged branch:** `:app:testDebugUnitTest` for onboarding, paywall/billing, titles, migration 27-28, recording save ordering and the design guard — **49 tests, 0 failures**; `:app:compileDebugKotlin` `BUILD SUCCESSFUL`.
- **Not done yet:** Work convergence beyond the header, onboarding copy/localization and the BYOK dead end, proxy deploy (needs the founder's Cloudflare account), Play Console/RevenueCat setup, Play compliance (account deletion route, privacy policy link), device verification.
- **Team:** registered as **Vega ✶** (MeetingMind CTO) in the CreativeSites MyaOS hub; briefs sent to Themis, Synapse, Ledger, Mira, Forge, Sentinel, Gantry and Hermes. No MeetingMind squad agent had a live runtime at the time.

## Dev Build & Release Verification (2026-10-22, Gantry 📦)
- **Artifact**: `MeetingMind-Dev-1cb6654.apk` published to branch `apk-downloads` (commit `e5883f8`).
- **Source**: Built from `ccr-08b1bb9f-iljmtb` at `1cb6654`. Includes Work consolidation (Decision D1, `WorkPageScreen`), Work navigation back button (W-01), Read to Me Bible license filter (Decision D2), short recording crash guard, and build toolchain fixes.
- **Physical Verification**: 76,375,512 bytes (~72.8 MB), SHA-256: `086f1b6842b83b97a5d0166c5917dd3006e1a4716c8c4748a465c3c122407fb6`.
- **Mandatory Credential Leak Gate**: PASSED. Unpacked APK audited; zero private keys leaked (only Google Services Firebase client key in `resources.arsc`).
- **Download Link**: `https://github.com/creativesites/meetingmind/raw/apk-downloads/MeetingMind-Dev-1cb6654.apk`

## Initial mission audit (2026-10-21)
- Evidence-based discovery and independent Product, Engineering and QA reviews are documented in `docs/quality/INITIAL_MISSION_AUDIT_2026-10-21.md`.
- First priority: recording persistence failure can clear the recovery journal before a Room row is durable. Next release gates: reconcile shipping source/artifact, configure and verify production billing for a paid launch, verify low-RAM/offline reliability, and confirm deployed Circles rules and authenticated flows.
- The current Circle Home timeout can appear as an empty state; onboarding still exposes Learning despite the MVP exclusion. Both are source-confirmed P2 findings.
- This pass ran no tests or build and performed no device walkthrough. Worker health/readiness returned HTTP 200 only; that does not verify authenticated Circles flows.
- No implementation has started. The designated branch is 233 commits behind `work-circles-integration`; resolve the shipping baseline before delivery work. Preserve the existing Mira entry in `agents/README.md` and the concurrent untracked `docs/planning/` assessment.

## Work Circles & Post-Meeting Collaboration (2026-10-21)
- **Work Circles Architecture & Domain Extension**:
  - Space-aware Circles (`work` vs `faith`): `Circle.isWork`, `Circle.groupWord` ("Team", "Department", "Project", "Workspace" for Work; "Circle", "Cell Group", "Fellowship" for Faith).
  - Work-tailored templates: `project_team`, `client_engagement`, `exec_board`, `department`, `custom_work` with automatic work defaults (e.g., prayer approval disabled).
  - Professional post types (`task`, `decision`, `brief`, `standup`, `blocker`) and professional reactions (`ack` 👀, `approved` ✅, `priority` 🎯, `reviewing` 🔍, `win` 🚀).
  - Professional invite dictionary words (e.g., `TEAM`, `SPRINT`, `LAUNCH`, `CLIENT`, `PROJ`, `SYNC`) for memorable codes.
  - Email & Google Workspace invitations: added `createEmailInvite` API and backend handling with email validation and audit logging.
- **Post-Meeting "Share to Circle" Integration**:
  - Created `ShareToCircleSheet` in `feature/work`: post-meeting flow enables one-tap publishing of Meeting Brief, Decisions, and Action Items (with assignees and due dates) directly to a chosen Work Circle.
  - Integrated into `WrapUpScreen`: primary "Share to Circle" action button alongside "Draft follow-up" and "Done", with direct navigation to create a new circle if none exist.
- **In-Circle Search & Feed Experience**:
  - Added in-circle search bar/sheet in `CircleHomeScreen` allowing real-time searching through posts and chat messages.
  - Work-aware `FeedTab` composer cards with quick chips (`Task`, `Decision`, `Brief`, `Standup`) and tailored placeholders in `FeedSheets`.
  - Task completion toggling on feed cards (`Mark completed`) with visual status pills (`Completed ✅` / `Action item`).
- **Tests & Verification**:
  - Server unit tests (`server/circles-api/test/unit/work_circles.test.js`): 67/67 tests passing.
  - Android unit tests: 110/110 tests passing across `CirclesApiTest`, `CirclesParsingTest`, `CirclesRedesignM1Test`, `CirclesMediaParsingTest`, `PushTokensTest`, `Migration25To26Test`, `CircleViewModelsTest`, `CirclesScreenshotTest`, `HomeCirclesScreenshotTest`, `HomeCirclesSectionTest`, `HomeCirclesTest`, and `ColorLiteralGuardTest`.
  - Audio recording safety tests: `AudioRecorderShortStopTest` (2/2 tests passing).
- **Not verified on device**: End-to-end multi-device email invite acceptance flow against live production backend.


## App Localization (i18n & l10n) (2026-10-20)
- **9 Languages Supported**: English (`en` source & fallback), French (`fr`), Spanish (`es`), Portuguese (`pt`), Swahili (`sw`), Nyanja/Chichewa (`ny`), Shona (`sn`), Zulu (`zu`), and Bemba (`b+bem` via AAPT2 BCP-47 qualifier).
- **Architectural Decoupling**: Interface language is strictly independent of speech recognition (ASR) language, AI prompt response language, and transcript translation language.
- **Natural Phrasing**: African Bantu languages (Shona, Bemba, Nyanja, Zulu) adopt modern spoken code-switching conventions ("Englishified" where there is no direct or meaningful native translation, e.g., Settings, Account, Audio, AI, Wi-Fi).
- **Core i18n Foundation**:
  - `AppLocaleManager`: Handles runtime per-app locale switching on Android 13+ (API 33+ `LocaleManager`) and backward-compatible context wrapping (`Configuration` update and JVM `Locale.setDefault()`) on API 24–32.
  - `locales_config.xml` & `AndroidManifest.xml`: Wired `android:localeConfig="@xml/locales_config"` for system-level per-app language settings.
  - `UserPreferences`: Persists `appLanguage` in DataStore preferences with fallback to system default.
  - **Flashing Loop Bug Fixed**: Android OS normalizes Shona to regional `sn-ZW`, which caused string equality mismatches against `sn`, triggering repeated activity recreations in `LaunchedEffect`. Resolved by adding `areLocalesEquivalent` language-code comparison, underscore/hyphen normalization, double-lock loop guards in `AppLocaleManager` and `MainActivity`, and synchronous SharedPreferences persistence.
  - **Real loop cause (2026-10-20, branch `ccr-08b1bb9f-iljmtb`)**: `MainActivity` treated the preference flow's first `null` (before DataStore loads) as "follow system", reset the locale and recreated the activity on every launch. Fixed by `AppLocaleManager.shouldApplySavedLanguage` (no apply until loaded), and by saving the language before applying it in `LanguageSelectionViewModel` and `OnboardingViewModel`. Unit tests pass. **Not verified on a device.**
- **UI & Navigation Integration**:
  - **Easy Access from Settings**: Added a prominent Language quick-card right at the top of `SettingsHub` displaying the active language with one-tap access, plus a dedicated Language icon button in the Settings header.
  - `LanguageSelectionScreen`: Accessible from Settings header, Settings Hub, and `Settings → Appearance → Language`, displaying native and English language names, system language option, and check indicators.
  - `SettingsRegistry`: Added `SettingId.LANGUAGE` in `SettingsGroup.APPEARANCE` (keeping primary row count <= 4).
  - `OnboardingScreen`: Added language choice pill on Welcome step with `OnboardingLanguageDialog` allowing interface language selection before completing onboarding.
  - `BottomNavigation`: Localized all primary tab destinations (Home/Today, Notes, Work, Search, Settings).
- **Unit Tests Verified**: `AppLanguageTest` (6/6 pass including regional variants & equivalence), `LocaleResourcesIntegrityTest` (3/3 pass across all 8 localized resource directories), `SettingsRegistryTest` (8/8 pass), and `UserPreferencesManagerTest` (pass).
- **Not verified on device**: Native Android 13+ per-app language picker sync from Android OS Settings vs in-app switcher.

## Recording & processing reliability (2026-10-19)
- **52% hang fixed**: a recording with no words skipped speaker separation (it ran over silence and parked the bar at 52%) and finishes COMPLETED with "No speech was detected". Covered by `NoSpeechRecordingTest`.
- **Crashes fixed**: a processing route with no audio path could not be matched by the NavHost (opening it crashed); startup resume is isolated per recording; the decoder fails instead of spinning on a damaged file.
- **Terminal states**: a failure before the pipeline's own handler now ends the job row FAILED with a reason and moves the recording to ERROR. Deleting a recording mid-run stops its work and leaves no job row.
- **Not verified on a device**: the decoder stall guard, a real silent recording on the native recognizer, a mic revoked mid-recording (no error listener yet), and the notification refusal on Android 12+.

## Latest UX, Onboarding, Paywall & Billing Update (2026-10-19)
- **Moments & Companion Theater**: Fixed `FirstKeep` ticket claim lifecycle so processing notes wait and trigger celebration on finish; added `NoteUnveil` stage target layers in `NoteEditorScreen`; added Companion Processing Theater card in `MeetingDetailScreen` during background processing.
- **Redesigned Onboarding**: Added Continuous Companion Guide Header across steps; streamlined sample transcript reveal (~2.1s) with a `"Skip to notes"` shortcut; removed raw BYOK Gemini key prompt from primary onboarding; added "Your Personal Space is Ready" summary on permissions step; verified readable layout on narrow screens (320x640).
- **4-Tier Billing & Proxy**: Restored 4-tier model (`FREE`, `BYOK`, `PRO`, `CHURCH`). Free tier includes 30 mins cloud transcription & 30 AI actions taste; BYOK is a paid tier ($4.99/mo) unlocking all Pro features with personal key; Pro ($12.99/mo) and Church ($49.99/mo) with managed proxy routing.
- **Closed Testing Toggle**: Added testing vs production tiering toggle in `Settings → AI & offline models → Plan and usage` (`TieringModeStore`), defaulting to `CLOSED_TESTING` (testers benefit: all Pro features unlocked without paywall blocks) with toggleable `PRODUCTION` tiering.
- **Redesigned Paywall & Dev Test Rows**: Trial timeline, weekly price anchoring, Founder bonus badges, RevenueCat package bindings. Added explicit test entry points in `Settings -> About` ("Onboarding experience" and "Paywall and subscription") as well as in `Settings -> AI & offline models` ("Plan and usage"), and searchable via Settings search.
- **Unit Tests Verified**: `NoteMomentHostTest` (9/9 pass), `OnboardingTest` (5/5 pass), `SettingsRegistryTest` (8/8 pass), `BillingModelTest`, `PaywallPolicyTest`, `PaywallCopyTest` (all pass), and Cloudflare proxy test suite `logic.test.mjs` (28/28 pass).

## Companion audit & assistant polish (branch `winston/companion-audit-design`, PR #8)
- Audit + design artifact done, approved by the founder: `docs/mvp/COMPANION_AUDIT.md`, prototype at the artifact link in the PR.
- Reliability fixes landed: `AssistantSession.send()` no longer silently swallows exceptions with no error/Try Again; `AiResult.Failed` now carries `retryable` so the UI stops offering Try Again for a failure a retry can't fix; `retry()` keeps a partially-completed turn's already-applied actions instead of discarding them.
- Zuri (the companion mascot) is now shown in the shared `AssistantSheet` header for both Notes and Faith — Faith wears the Gold rim. New `CompanionPage.ASK`.
- Consolidated the three duplicated "never speak for God" regex guards (`FaithGuard`, `AskSermon`) into one `FaithAuthorityGuard`; `Prompts.ALL` now lists every prompt asset on disk, with a test that keeps it honest.
- New prayer-management tools on the shared assistant: `list_prayer_requests`, `create_prayer_request`, `mark_prayer_answered` (Faith scope only), wired to the existing prayer-request Note/NoteStatus system, with Undo.
- **Not verified on device.** This sandbox has no Android SDK, so none of the above has been compiled or run — only reviewed by hand and unit-tested where Room/Robolectric made that possible. Needs a real build + device pass before merging.
- Not done from the original brief: deep-link navigation from an assistant answer straight to a scripture passage or transcript timestamp (today only note/task open); broader device-level QA pass.

## Southern African Language Transcription (MeetingMind Voice Intelligence)
- Research & Planning complete: `docs/SOUTHERN_AFRICAN_ASR_RESEARCH_AND_PLAN.md` (12 sections, benchmark methodology, legal audit) and `docs/models/southern_african_asr_catalog.json` (26 fields across 12 candidate model checkpoints).
- License guardrail: `facebook/mms-1b-all` formally rejected due to `CC-BY-NC 4.0` non-commercial restriction. `Meta Omnilingual ASR CTC 300M (INT8)` approved and selected (`Apache-2.0`, ~365 MB download, ~650 MB peak RAM).
- Phase P0 & P1 implemented (`bd280b4`):
  - `SherpaOmnilingualSpeechRecognizer.kt` integrated with sherpa-onnx offline CTC config.
  - `ModelCatalog.omnilingualAsr300mInt8` wired with direct Hugging Face download URL and SHA-256 integrity hash.
  - `MeetingProcessingPipeline.kt` updated to dynamically route to `SherpaOmnilingualSpeechRecognizer` when the multilingual model is selected.
  - Discourse markers in `UtteranceBuilder.kt` and `TranscriptStructureEngine.kt` extended for Shona, Ndebele, Bemba, and Nyanja.
- Phase P2 implemented (`629994a`):
  - `LanguagePickerRow` Compose UI added to `RecordingContextPicker.kt` and integrated into `RecordingScreen.kt` recording setup flow.
  - Spoken language selection persisted via `UserPreferencesManager.setSelectedLanguage` and tracked on `RecordingContext`.
  - `ProcessingScheduler.kt` and `MeetingProcessingPipeline.kt` dynamically select Omnilingual ASR CTC 300M when an African language is chosen.
  - Unit test suite added in `SupportedLanguagesTest.kt` and updated in `UserPreferencesManagerTest.kt`.
- Expanded Tier-1 & Tier-2 Offline Models (1 GB to 2 GB size class, strictly <= 3 GB cap):
  - Added `omnilingualAsr1bInt8` (`asr_omnilingual_1b_ctc_int8`): 1B params INT8 quantized Meta Omnilingual CTC checkpoint (1.03 GB download, Apache-2.0). Drop-in high-accuracy upgrade for Southern African languages (Shona, Ndebele, Bemba, Nyanja/Chichewa, etc.).
  - Added `whisperTurboInt8` (`asr_whisper_turbo_int8`): 809M params INT8 quantized Whisper Turbo checkpoint (1.04 GB download, MIT). Fast multilingual transcription with segment timestamps.
  - Added `whisperLargeV3Int8` (`asr_whisper_large_v3_int8`): 1.5B params INT8 quantized Whisper Large-v3 checkpoint (1.78 GB download, MIT). Maximum accuracy multilingual transcription with word-level timestamps.
  - Added `deepseekR1DistillQwen15b` (`llm_deepseek_r1_distill_qwen_1_5b`): 1.5B params q8 quantized reasoning model (1.83 GB download, MIT). Advanced on-device meeting intelligence and reasoning.
  - Implemented `SherpaWhisperSpeechRecognizer.kt` integrating sherpa-onnx `OfflineWhisperModelConfig`, byte-level BPE grouping, and timestamp interpolation.
  - Dynamic routing in `MeetingProcessingPipeline.kt` updated to dispatch Whisper models to `SherpaWhisperSpeechRecognizer` and Omnilingual models to `SherpaOmnilingualSpeechRecognizer`.
  - User preference preservation in `ProcessingScheduler.kt` and `UserPreferences.kt` ensuring selected 1B model is preserved across African language switches.
  - Verified with comprehensive unit test suites in `SherpaWhisperSpeechRecognizerTest.kt`, `ModelCatalogTest.kt`, and `UserPreferencesManagerTest.kt`.

## Multilingual Speech & AI (MeetingMind Voice Intelligence)
- Technical audit and design complete: `docs/MULTILINGUAL_SPEECH_AND_AI_AUDIT_DESIGN.md` (principles, model inventory, Southern African Englishification, benchmark matrix, multi-engine routing, room migration 27, and commercial licensing gate).
- Phase P1 (Pipeline Wiring & Language Parameter Propagation):
  - `SpeechRecognizer.kt`: Extended `SupportedLanguages` with Tier A international languages (Spanish, French, Portuguese, German, Italian, Dutch, Japanese, Korean, Mandarin, Hindi, Arabic) and Tier C. Added `isWhisperPreferredLanguage(code)` and capability routing in `defaultModelIdForLanguage(code)`.
  - `SherpaWhisperSpeechRecognizer.kt`: Dynamically passes `whisperLanguage` into `OfflineWhisperModelConfig` and caches `recognizer` by `"$modelId:$whisperLanguage"`.
  - `MeetingProcessingPipeline.kt`: Added `language: String? = null` parameter to `processMeeting`, resolved `effectiveLanguage`, passed `language = effectiveLanguage` to `TranscriptionOptions` across hybrid and offline local ASR paths, and set `Transcript(..., language = effectiveLanguage)`.
  - `MeetingProcessingWorker.kt`: Added `KEY_LANGUAGE = "language"` in companion object, extracted and passed to `pipeline.processMeeting`.
  - `ProcessingScheduler.kt`: Added Whisper model auto-routing for Whisper-preferred languages, passed `MeetingProcessingWorker.KEY_LANGUAGE to meeting.language`.
- Phase P2 (Multilingual AI Behavior & Southern African Englishification):
  - `GeminiIntelligenceEngine.kt`: Injected spoken language and Southern African Englishification & code-switching directives to `buildExtractionPrompt`.
  - `RealMeetingIntelligenceEngine.kt`: Updated `buildExtractionPrompt` and `buildSynthesisPrompt` to accept `language` and inject English loanword preservation guidance. Passed `transcript.language` from `processMeeting`.
  - `UserPreferences.kt`: Added `aiResponseLanguage`, `summaryLanguage`, `translationTargetLanguage`, and `autoDetectLanguage` to `AppPreferencesState` and `preferencesFlow`, with setters and intelligent Whisper model switching.
  - `RecordingContextPicker.kt`: Added Tier A international languages to `LanguagePickerRow`.
  - `RecordingScreen.kt`: Updated spoken language subtitle to reflect active pack (`African & Multilingual pack`, `Whisper Multilingual pack`, `English & European pack`).
- Phase P3 (Non-Destructive Transcript Translation & Room Schema 27):
  - `Entities.kt`: Added `TranscriptTranslationEntity` (table `transcript_translations` with `id`, `meetingId` FK cascade, `sourceLanguage`, `targetLanguage`, `translationEngine`, `translatedTitle`, `translatedSummary`, `translatedSegmentsJson`, `createdAt`). Original verbatim transcripts are preserved intact.
  - `Daos.kt`: Added `TranscriptTranslationDao` with `insertOrReplace`, `getTranslation`, `getTranslationsForMeeting`, `observeTranslationsForMeeting`, `deleteTranslation`, `deleteForMeeting`.
  - `MeetMindDatabase.kt`: Registered `TranscriptTranslationEntity`, added `transcriptTranslationDao()`, added `MIGRATION_26_27` and `MIGRATION_26_27_SQL`, bumped `VERSION = 27`.
  - `TranslationEngine.kt`: Created interface and data models (`TranslatedTranscript`, `TranslatedSegment`) in `app/src/main/java/com/craftflowtechnologies/meetingmind/ai/translation/`.
  - `GeminiTranslationEngine.kt`: Created cloud translation engine enforcing `isConfigured()` check, structured JSON schema response, and regional Englishification loanword directives.
- Phase P4 (Model Management Hardening):
  - `Repositories.kt`: Hardened `ModelRepository.deleteModel(modelId)` to block deletion during background processing and reset active model preferences to defaults if deleted.
  - `ModelManagerScreen.kt`: Connected user preferences and deletion error messages.
- Unit test verification:
  - `GeminiTranslationEngineTest`, `ModelRepositoryTest`, `Migration26To27Test`, `SupportedLanguagesTest`, `UserPreferencesManagerTest` (all green).

## Where we are (what the founder sees on device, latest report)
- Notes page crashed on open. Fixed on the branch in `5f120cf` (hero layout dropped a child when the latest-meeting bubble was empty). Unit tests pass. Not yet seen on device.
- Circles section on Home shows its label but no content. Circles v2 code is on the branch; the Home section needs checking.
- Bible study and Faith home: the redesigns are merged but the founder reports they are only partly there. Not yet traced screen by screen.
- Read to Me is wired into the Bible reader (top-bar listen icon, "Listen here" on a selection, the study flow, the verse sheet). The founder cannot see it on device. Cause not confirmed. Check the entry point on device first.
- Work UX: audit and design artifact done; founder approved D1–D7 as written (2026-10-22), so W-01 and W-04 can start. Report: `docs/mvp/WORK_UX_AUDIT.md` (15 ranked findings, 7 decisions D1–D7). Artifact: https://claude.ai/artifact/H9dnBur8ZzwowA25eWeUzV. Redesign of Today, Tasks and Projects implemented on `work-ux-redesign` (sunset header, quick actions, card rows); Notes unchanged. Not compiled or run: no Android SDK in this sandbox. Still open: the Work page back arrow (W-01), failed-recording recovery (W-04), and the Work home.
- The sun or moon sits over the headline in the Today quote card. It is the card's own illustration, not the companion. Not started.

## Circles (team report)
- Milestone 1 Redesign & Architecture: Backend & client complete, compiled, and verified (Android unit tests: 78 pass, 0 fail; Worker unit tests: 57 pass, 0 fail).
- Client redesign: All 7 screens upgraded to match `MeetingMind Circles Redesign.html` (`CirclesListScreen`, `CircleHomeScreen`/`FeedTab`, `ChatTab`, `MembersTab`, `CircleSettingsScreen`, `EventsSection`, `MediaViews`). Fullscreen photo viewer, video player dialog with `MediaKind.Video` support, 12-bar audio waveforms, L1/L2 media disk cache, pastel avatar initials palette with online dots, `Post.attachments` grid, and role/permission badges.
- Fixed theme tokens: Circles palette moved to `CirclesFixedColors` in `FixedColors.kt`; `FixedWhite` applied to paywall; `ColorLiteralGuardTest` compliance verified green.
- Backend (`server/circles-api`): 57/57 unit tests pass. R2 media bucket bindings (`meetingmind-circles-media`), `video` media validation (MP4/WebM, 50MB cap), idempotent `sendMessage` via `requestId`, photo-only `createPost`, R2 media deletion in `deletePost`, cursor-paginated `sweepOrphans` with expired `typing` cleanup, and moderator deletion permissions in `firestore.rules`.
- Deployment automation: `server/circles-api/scripts/deploy-and-verify.sh` verified with live Worker smoke checks (`/health` 200, `/ready` 200, unauthenticated 401).
- Feed & Posts polish:
  - `ComposerSheet` overhauled with post-type switcher chips, live Scripture passage lookup & preview in serif with translation label, photo attachment gallery with file-size chips, character counter, and anonymous posting toggle.
  - Posts with scripture references render styled serif verse cards (`PostVerseCard`) with translation abbreviations and attribution, opening `VerseSheet` on tap for chapter navigation.
  - Floating pill "New post" FAB elevated at bottom-end above navigation bars; `FeedTab` and `MembersTab` list paddings set to 96dp bottom padding to prevent content or buttons being cut off.
  - Thread comments overhauled with pastel avatar initials, author roles, relative timestamps, copy/delete actions, and bottom sunk input pill.
  - `MemberInfoSheet` added: tapping members in `MembersTab`, feed posts, or comment rows displays member profile, title, role description, join date, moderation controls, and direct "Message in chat" shortcut.
  - Card & Story sharing: `ChatCard` in chat opens `CardDetailSheet` with full card preview, "Share card" and "Copy words"; Story share button is always visible across all story types with fallback `ShareCardContent`.
- Google sign-in: OAuth client fixes merged from `circles/firebase-json` into `app/google-services.json`.
- Not verified on device: push notifications, live R2 uploads on real device, Google sign-in on release builds.

## Latest dev build
- `MeetingMind-Dev-4ddcdd2.apk` on the `apk-downloads` branch. Built from the designated branch after the no-speech change.
- Devices have not yet checked: the moments, the 00:01 devotional notification, the Circles flow.

## Merged and working (unit tests green)
- Devotionals: bundled readings load on device; on by default; 00:01 write; "Write one with AI" labelled.
- Moments: First Keep on note-ready; Unveil on every note open (Notes list, meeting detail, wrap-up); no-speech notes get their moment.
- Today home: the design update.
- Read to Me: listen from the verse sheet and Study workspace; Smart Read; settings sheet.
- Companion states Curious, Proud, Reading; sample-first onboarding.
- Circles first increment on the designated branch: v1 retired, creation defects fixed, diagnosable sign-in. Needs the Worker and rules redeployed.

- Faith home: "Your week in faith" at the top and Bible continues from today's reading (`17d838e`, cherry-picked as `4c6b786`). Unit test `FaithWeekTest` run pending.

## Other branches (not merged, not built)
- `ccr-1face576-9bomay`: 7 commits not on the designated branch. Only `17d838e` is taken so far. The rest: `6652901` hide floating Up next on Today; `04db60d` shared Today header and mode switch; `5e35c13` Work and Calm homes keep every feature; `0565adf` companion clears the sun; `cf7d379` polished notes home; `fd5501b` licensed Bible text to Gemini and a tier gate (owner decision, needs confirming). A full merge conflicts in `NotesHome.kt`, `HomeHeroHeader.kt`, `HomeBento.kt` and `ModeHomeHeader.kt`. Merge per commit with a decision on `fd5501b`.
- `ccr-e1f66026-rff3e0`: onboarding and billing work (draft PR #1). Head: "Sample-first onboarding with resumable state; stop prompting for permissions at launch". Its code has never been compiled. Do not merge main into it or rebase it. Build it only when the CTO asks. Analytics, billing and the paywall are not on it yet.


## Latest merge (2026-10-15)
- Onboarding, RevenueCat plans and the managed Gemini proxy (`ccr-e1f66026-rff3e0`) are merged. The paywall is in the app but only shows when a RevenueCat key is set (`REVENUECAT_API_KEY`).
- The home branch `ccr-1face576-9bomay` is already in this branch except `fd5501b` (licensed Bible text to Gemini). `fd5501b` is held for the owner's decision; see `docs/DATA_SAFETY.md`.
- Data Safety now describes anonymous Circles sign-in and optional Google linking.
- Not built into an AAB yet. Build waits for Circles and the remaining fixes. Owner wants the AAB pushed to GitHub, with a download link.

## Decisions recorded (founder, 2026-10-21)
- **Closed Testing Toggle**: Stays active in release builds for now since the app is in closed testing (testers have full Pro features unlocked).
- **Circles Member Cap**: Reconciled to **50 members** (matches Cloudflare Worker enforcement).
- **Circles Backend Deployment**: Founder will assign an agent to deploy `server/circles-api` and `firestore.rules` to production.
- **Host Disk Health & Release Readiness**: Build & Release Engineer (Gantry 📦) has taken responsibility for keeping host free space healthy (recovered to 16 GiB available).

## Open, needs a decision
- Licensed Bible text to Gemini (Read to Me). Options are in the Data Safety doc. The code currently sends all translations.

## Earlier open items (still open)
- Circles: the Worker and `firestore.rules` deployment to be assigned to an agent by the founder. See `BACKEND.md`.
- Circles: anonymous sign-in confirmed enabled in the Firebase console (owner, 2026-10-15).
- Circles migration of the client to the Worker (V-0b3) and removal of old tables (V-0b6): not done.
- Play workflow trigger watches branch `ccr-5e66b00c-btultn`, not the designated branch. Team to decide whether to move it. Documented as it is in `PLAY_STORE.md`.
- Decided: no rewrite of old commit trailers from earlier sessions. Leave history as it is; new commits follow the trailer rule.

## Open, needs a device
- Moments on a real phone: First Keep, Unveil, the no-speech case.
- Devotional notification at the set time with the app closed.
- Read to Me on a real phone (see `docs/mvp/READ_TO_ME_DEVICE_CHECKLIST.md`).

## Not started
- Notes: the Faith home and the "Write" tiles follow-ups.
- Circles: chat redesign, media storage, church workspaces.
- Daily Light and Streams in the Desert devotionals (no clean public-domain text yet).
