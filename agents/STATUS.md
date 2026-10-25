# Status

Updated: 2026-10-25. **Shipping branch: `release/1.0-paid-launch`** (cut from `work-circles-integration` + one checkpoint commit of the in-flight tree, `a3ca467`). The earlier designated branch is a 233-commit ancestor and is no longer the working branch. Head: see `git log -1`.

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
