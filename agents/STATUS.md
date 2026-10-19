# Status

Updated: 2026-10-19. Branch: `claude/southern-african-asr-research`. Head: see `git log -1`.

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


## Where we are (what the founder sees on device, latest report)
- Notes page crashed on open. Fixed on the branch in `5f120cf` (hero layout dropped a child when the latest-meeting bubble was empty). Unit tests pass. Not yet seen on device.
- Circles section on Home shows its label but no content. Circles v2 code is on the branch; the Home section needs checking.
- Bible study and Faith home: the redesigns are merged but the founder reports they are only partly there. Not yet traced screen by screen.
- Read to Me is wired into the Bible reader (top-bar listen icon, "Listen here" on a selection, the study flow, the verse sheet). The founder cannot see it on device. Cause not confirmed. Check the entry point on device first.
- Work home has a large gap. Not started.
- The sun or moon sits over the headline in the Today quote card. It is the card's own illustration, not the companion. Not started.

## Circles (team report)
- Milestone 1 Redesign & Architecture: Backend & client complete and COMPILED (`:app:compileDebugKotlin` clean pass).
- Client redesign: All 7 screens upgraded to match `MeetingMind Circles Redesign.html` (`CirclesListScreen`, `CircleHomeScreen`/`FeedTab`, `ChatTab`, `MembersTab`, `CircleSettingsScreen`, `EventsSection`, `MediaViews`). Fullscreen photo viewer, video player dialog with `MediaKind.Video` support, 12-bar audio waveforms, L1/L2 media disk cache, pastel avatar initials palette with online dots, `Post.attachments` grid, and role/permission badges.
- Fixed theme tokens: Circles palette moved to `CirclesFixedColors` in `FixedColors.kt`; `ColorLiteralGuardTest` compliance verified.
- Backend (`server/circles-api`): 57/57 unit tests pass. R2 media bucket bindings (`meetingmind-circles-media`), `video` media validation (MP4/WebM, 50MB cap), idempotent `sendMessage` via `requestId`, photo-only `createPost`, R2 media deletion in `deletePost`, cursor-paginated `sweepOrphans` with expired `typing` cleanup, and moderator deletion permissions in `firestore.rules`.
- Deployment automation: `server/circles-api/scripts/deploy-and-verify.sh` verified with live Worker smoke checks (`/health` 200, `/ready` 200, unauthenticated 401).
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

## Open, needs a decision
- Licensed Bible text to Gemini (Read to Me). Options are in the Data Safety doc. The code currently sends all translations.

## Earlier open items (still open)
- Circles: the Worker and `firestore.rules` must be redeployed by the owner. See `BACKEND.md`.
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
