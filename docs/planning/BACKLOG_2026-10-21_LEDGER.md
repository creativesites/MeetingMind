# MeetingMind Consolidated Backlog (Phase 1 discovery)

**Compiled by:** Ledger 📜 (Triage & Context Keeper) · **Date:** 2026-10-21
**Branch read:** `work-circles-integration` at `b114134` · **Inputs:** `docs/planning/PRODUCT_ASSESSMENT_2026-10-21_THEMIS.md`, `docs/quality/ISSUE_REGISTER.md`, `docs/quality/RELEASE_READINESS.md`, `agents/STATUS.md`, `docs/mvp/WORK_UX_AUDIT.md`
**Status:** Draft. Engineering Planner (Daedalus) critique, QA independent pass (Sentinel), and the emulator walk are **not done**. Nothing below is verified on a device.

## Corrections to the Themis assessment

- **T-02 is wrong on direction.** `origin/claude/meetingmind-mvp-planning-7dgowm` is an ancestor of HEAD: HEAD is 113 commits **ahead**, not 233 behind. `origin/ccr-08b1bb9f-iljmtb` has 8 commits not in HEAD (not 6). `origin/apk-downloads` latest is `1589499` ("dev build 4e7a3bf"), not `5f39cd1`. The core point still stands: no single branch is what testers run. Re-verify which APK testers have before acting on it.
- **T-01 verified.** `TieringModeStore` defaults to `CLOSED_TESTING` (`core/billing/TieringMode.kt`). `REVENUECAT_API_KEY` is empty unless set in the environment (`app/build.gradle.kts:70`), so billing is off in any build without it.

## Backlog (deduplicated, ranked)

| # | Item | Sev | Source | Evidence | Scope | Owner route |
|---|---|---|---|---|---|---|
| 1 | Launch tiering: decide what release builds enforce; keep Closed Testing out of release | R | T-01 | verified | Bounded config + policy decision | Founder decision → Path A once decided |
| 2 | Pick the integration branch testers run; align APK source | R | T-02 | corrected | Process | Synapse / CTO |
| 3 | Low-RAM processing kill (ISS-05): explicit native release between ASR and LLM | R | T-04, ISS-05 | Under investigation | Eng + QA device test on 3–4 GB | Path B (Daedalus + Themis) |
| 4 | Device pass for moments, 00:01 notification, Read to Me, Circles, release Google sign-in | R | T-03 | not verified | QA only | Sentinel |
| 5 | Owner deploys Circles Worker and `firestore.rules` | R | T-05 | owner task | Deployment | Founder |
| 6 | Onboarding "cloud AI ready out of the box" claim vs release config | P1 | T-06 | `OnboardingScreen.kt:592`; release keys env-only | Confirm, then copy or config fix | Path A if copy; Path B if config |
| 7 | Learning appears as an onboarding goal and default space | P1 | T-07 | `OnboardingModel.kt:21,62-63` | Remove from goals and defaults | **Path A (rapid fix → Forge)** |
| 8 | Share to Circle: items unticked by default, full preview, assignee guessing, failure handling | P1 | T-08 | `ShareToCircleSheet.kt:77-79,196,266-297` | Spec + UI + error states | Path B (Themis spec, then Forge) |
| 9 | Work: back arrow (W-01), failed-recording recovery (W-04); approve decisions D1–D7 first | P1 | T-09 | `WORK_UX_AUDIT.md` | Founder approval, then UI | Founder decision, then Path B |
| 10 | Circles member cap: docs say 10, Worker enforces 50 | P2 | T-10 | `logic.js:4`; `PRODUCT.md` | Decision, then doc or config | Founder decision |
| 11 | BYOK key stored unencrypted in DataStore | P2 | T-11 | `PRE_LAUNCH_AUDIT.md` §3 | Encrypt at rest | Path B (security) |
| 12 | Licensed Bible text to Gemini (`fd5501b`) and reduced-motion moments | P2 | T-12 | `STATUS.md` | Founder decisions | Founder decision |
| 13 | Circles HomeTile stuck in Loading on timeout (ISS-06) | P2 | ISS-06 | "Fix ready", unverified | Small | **Path A (rapid fix → Forge)**, then Sentinel test |
| 14 | Deprecated RTL icons (ISS-07) | P3 | ISS-07 | compiler warnings | Mechanical | Path A |
| 15 | Faith Create usability and redesign | P2 | Mission phase 2.5 | no device walk yet | Discovery first | Needs device walk, then design approval |

## Open decisions for the founder

1. Release tiering: when does the public build enforce PRODUCTION, and does the Closed Testing switch ship in release? (#1)
2. Approve or amend Work decisions D1–D7. (#9)
3. Circles member cap: 10 or 50? (#10)
4. Licensed Bible text to Gemini; reduced-motion moments. (#12)

## Proposed order (for planner challenge, not final)

1. Release truth: #2, #1 (after decision), #3 and #4 (device), #5.
2. First-success journey as one flow: #6, #7, paywall behaviour once tiering is PRODUCTION.
3. Work: #9 after approval.
4. Circles for Work: #8, #10.
5. Faith Create: #15 device walk, then design approval.

Path A now (bounded, no approval needed): #7 and #13. Both are small and localized. #14 can follow.

## Not verified

- No Gradle build or test run in this pass. The Android SDK path exists in `local.properties`, but nothing here was executed.
- Emulator walk pending (Themis).
- Notes, Bible study, devotional archive, Read to Me, search, assistant, import, app lock: not covered by any current assessment.
