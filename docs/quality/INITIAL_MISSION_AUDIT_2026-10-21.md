# MeetingMind — Initial Mission Audit

**Date:** 2026-10-21  
**Audited checkout:** `work-circles-integration` at `b114134`  
**Purpose:** Time-boxed discovery and a ranked first delivery plan; this is not a claim of release readiness.

## Evidence and limits

- `git status --short --branch` showed `work-circles-integration...origin/work-circles-integration`; the only change at audit start was the earlier Mira entry in `agents/README.md`.
- `git rev-list --left-right --count claude/meetingmind-mvp-planning-7dgowm...work-circles-integration` returned `0 233`; the designated branch is an ancestor of the active integration branch. This is a substantial release-source gap, not an unrelated divergent history.
- `origin/apk-downloads` points to `29eeacc` and contains `MeetingMind-Dev-5f39cd1.apk`; `5f39cd1` is not an ancestor of the audited branch. `agents/STATUS.md` instead calls `MeetingMind-Dev-4ddcdd2.apk` the latest build. The artifact, source commit and status record need reconciliation before a release claim.
- A read-only `GET` to the Circles Worker returned `/health` **200** and `/ready` **200**. This proves the Worker responds; it does not verify Firebase rules or authenticated create/join/invite flows.
- `find app/src/test/java -name '*Test.kt'` counted **372** Kotlin test files; `docs/quality/PRE_LAUNCH_AUDIT.md` says 371. This is a file count, not a test-case or pass count.
- No tests, Gradle build, install or UI walkthrough were run in this audit. `adb devices -l` showed an attached emulator; no physical phone evidence was collected. The historical test/build statements in project docs remain historical reports, not results from this pass.
- A separate untracked product-assessment file appeared under `docs/planning/` during the audit. It was left untouched; its claims were not treated as verified unless independently checked below.

## Planner and QA review

Product and Engineering Planners assessed the same commit independently, then reviewed and challenged one another's priorities. QA/Reliability performed a separate source and test-coverage inspection. All three passes were read-only.

**Consensus:** protect saved recordings first; gate a paid release on real production billing/tiering; validate offline processing on low-RAM devices; verify Circles backend rules and authenticated journeys. The Circle Home timeout is a source-confirmed medium-severity defect. No material disagreement remains on the top priorities.

**Priority nuance:** billing is a P1 release blocker for the paid launch implied by commercial readiness. If the founder explicitly chooses a free launch, move it out of the release-blocker column and record that decision. Product's sample-entry friction remains P2 after reliability and release gates. The earlier Work finding W-01 is stale in this checkout: the Work header receives a back action when Work is not the bottom-navigation tab. Device confirmation is still open.

## Deduplicated backlog

| Rank / severity | Finding and evidence | User impact | Scope, dependency and owner |
|---|---|---|---|
| 1 · **P1 data integrity** | `MeetingRecordingService.stopRecording()` catches a failed `createInitialMeeting()` at `app/src/main/java/com/craftflowtechnologies/meetingmind/core/audio/MeetingRecordingService.kt:246-267`, then clears the recovery journal and calls the success callback. The audio file remains on disk but is no longer indexed or recoverable through the journal. `MeetingRecordingServiceTest` has no persistence-failure case. | A finished recording can become inaccessible after a Room write failure. | Preserve/retry recovery until the row is durable; surface a truthful failure state; add a failure-path test. Android Engineer, with independent QA. This is the first rapid-fix contract. |
| 2 · **P1 paid-release blocker** | Release unconditionally sets `REVENUECAT_API_KEY` to empty in `app/build.gradle.kts:112-114`; `MeetingMindApp.kt:29-32` selects `DisabledBillingGateway` when empty. Fresh installs also default to `CLOSED_TESTING` in `core/billing/TieringMode.kt:27,31-35`, and `PaywallPolicy.kt:42-44` suppresses non-Settings offers in that mode. `RELEASE_READINESS.md` currently rates Billing GREEN. | A paid Play build offers no plans; with billing enabled but the default mode unchanged, fresh installs receive the closed-testing bypass. | Require a production RevenueCat key and `PRODUCTION` fresh-install mode in the release variant. Verify offerings, Free entitlements, purchase and restore on the Play test track. Build/Release Engineer + Billing owner; requires production project/store configuration. |
| 3 · **P1 release-source blocker** | The designated branch is 233 commits behind the active integration branch; the APK on `origin/apk-downloads` names source `5f39cd1`, which is outside the audited branch. Status also names a different APK hash. | The team cannot confidently say that the tested source is what users will install. | Choose and document the shipping source/artifact, then align the build and QA evidence. CTO + Build/Release Engineer. Do not merge branches as part of this discovery pass. |
| 4 · **P1 core-journey acceptance gate** | Low-RAM offline inference, real recording interruptions, model download and long-session behavior remain unverified on device; `docs/quality/ISSUE_REGISTER.md` ISS-05's claim that engine release calls are absent is stale—current pipeline code calls `SherpaEngineManager.releaseAll()` and `LlmEngineManager.release()`. | Users may lose the first useful result on lower-memory Android phones even though source-level tests pass. | Measure completion, memory and thermal behavior on target 3–4 GB phones; verify mic revocation and incoming-call recovery. AI/Android Engineer + independent QA. Treat this as a validation gate, not proof of a present crash or a reason to add `System.gc()` without evidence. |
| 5 · **P1 Circles-launch gate** | Worker `/health` and `/ready` returned 200 in this audit. `agents/STATUS.md` and `RELEASE_READINESS.md` still require Firestore rules deployment; rules and authenticated end-to-end flows were not verified. | Circles and Share to Circle may fail or have incorrect access control for real members. | Confirm deployed rules; QA a real create/join/invite/post flow and the Work share path. Backend owner + QA; requires authorized Firebase test accounts. |
| 6 · **P2 recoverable UX defect** | `HomeCirclesViewModel.load()` wraps refresh in `withTimeoutOrNull(3500L)` and sets `refreshed=true` in `finally` (`feature/today/home/HomeCircles.kt:77-86`). `HomeCircles.from()` can then show `Empty` when the timeout returned no result and no error was recorded (`:45-53`). | A slow network can make a member think their account has no circles. | Represent timeout as retryable failure; add a delayed-refresh test. Android Engineer; depends on Circles flow verification. |
| 7 · **P2 MVP-scope defect** | Onboarding exposes `CaptureGoal.LEARNING` (`feature/onboarding/OnboardingModel.kt:18-25`) and `spacesFor(emptySet())` enables every `NotebookSpace`, including Learning (`:61-63`), although `core/model/Notes.kt:43-47` and `agents/PRODUCT.md` exclude Learning from MVP. | New users can be offered an unsupported space, especially when they skip goal selection. | Remove Learning from onboarding choices and default-space selection without deleting stored legacy data; add a focused model test. Android Engineer; bounded rapid fix after the first data-integrity fix. |
| 8 · **P2 partial-share defect** | `ShareToCircleSheet.kt:272-292` ignores each `repo.createPost()` result, increments the success count anyway, and dismisses the sheet. Repository calls return `CirclesResult`. | Partial or failed publication can be reported as successful, with no retry or clear recovery. | Handle per-item results, prevent duplicate retries, show exactly what posted and what failed. Android Engineer + QA; after backend/access verification. |
| 9 · **P2 onboarding friction** | “Explore a sample” routes through the required companion picker before the sample (`OnboardingScreen.kt:206-207`; `OnboardingModel.kt:45-50`). The example is scripted and precomputed. | The first demonstration takes an extra choice before showing value. | Test a skippable/default companion choice against the agreed first-success goal; do not redesign until evidence shows meaningful friction. Product + Android Engineer. |
| 10 · **P2 Work / Faith UX approval gates** | `WORK_UX_AUDIT.md` is partly stale (W-01 is contradicted by current `WorkPageScreen.kt:273-292`); Work has both `ProfessionalHome` and `WorkPageScreen` paths. Create generation uses `GeminiCreateModel` and explicitly falls back to writing/starters without Internet mode and a Gemini key (`core/create/CreateEngineApi.kt:16-28`, `CreateGenerator.kt:13-26`). | Users may meet inconsistent Work navigation or expect offline AI writing in Faith Create. No current source evidence establishes a Notes or Create crash. | Re-walk Work, Notes and Faith/Create on the current build; produce a mobile design artifact and get founder approval before substantial UI changes. Product + Design, then Android Engineer. |

## Initial delivery roadmap

1. **Release-source baseline:** resolve the designated/integration/APK mismatch and identify the exact build to validate. This is a prerequisite for any release claim.
2. **Recording integrity rapid fix:** preserve an actionable recovery route when the meeting row cannot be written; add and independently verify the failure-path regression test.
3. **Paid-release configuration:** make production billing and tiering explicit in release builds; verify the complete paywall/store journey. If launch is intentionally free, record that instead.
4. **Stability acceptance:** execute offline processing, low-RAM, mic interruption, Notes, Moments and devotional checks against the selected build; keep device results separate from unit-test claims.
5. **First-success journey:** connect onboarding, model setup, sample/first recording, first note and paywall; remove Learning from onboarding; fix evidenced gaps before adding more screens.
6. **Work:** refresh the audit, present the mobile design for approval, then implement only the approved scope.
7. **Circles:** confirm rules, fix retry/partial-share states, then validate its Work adaptation end to end.
8. **Faith Create:** walk the current flow, present a redesign artifact for approval, then implement.

## Release and product decisions still needed

- Confirm whether the target Play launch is paid. For paid launch, release billing/tiering is blocked by source configuration until corrected and verified.
- Confirm the shipping branch/build artifact after the designated/integration comparison.
- Confirm that Firestore rules are deployed; the unauthenticated Worker health check does not answer this.
- Keep the existing approval gates: Work and Faith/Create UI redesigns need an approved mobile design before substantial implementation. No new UI work is authorized by this audit.
