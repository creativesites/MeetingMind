# Product assessment: Discover, Prioritize, Stabilize (Phase 1)

**Author:** Themis ⚖️ (Product Planner) · **Date:** 2026-10-21 · **Branch read:** `work-circles-integration` at `b114134`
**Status:** Independent product assessment. Written before reading the Engineering Planner's or Mira's conclusions. Not a final plan: it needs the Engineering Planner's critique under the two-planner process.

## Method and limits

- Read: `AGENTS.md`, `agents/STATUS.md`, `agents/PRODUCT.md`, `docs/quality/*` (issue register, pre-launch audit, release readiness), `docs/mvp/WORK_UX_AUDIT.md`, and the code behind onboarding, billing/tiering, Share to Circle, Circles limits and navigation.
- Git: compared every remote branch against HEAD.
- Device: see "Emulator walk" below. No physical device was used. Nothing here is verified on a real phone.
- Time-boxed to one session. Areas I did not open are listed at the end.

## Findings, ranked

Severity: **R** = release blocker, **P1** = breaks a core journey or a product rule, **P2** = significant gap, **P3** = polish.

| ID | Sev | Finding | Evidence | User impact | Suggested owner |
|---|---|---|---|---|---|
| T-01 | R | Every build, release included, defaults to **Closed Testing**: all Pro features unlocked, paywall suppressed. Any user can flip it in Settings → Plan and usage. Release builds also hard-code an empty RevenueCat key, so billing is off. `RELEASE_READINESS.md` still rates Billing GREEN. | `core/billing/TieringMode.kt:27,34`; `feature/settings/SettingsScreen.kt:1466-1479`; `app/build.gradle.kts:114` | No revenue at launch. If the managed proxy trusts the client's tier, our AI costs are open to anyone. | Founder decision, then Eng |
| T-02 | R | No single branch is what testers run. The designated branch is 233 commits behind this one. The latest dev APK (`5f39cd1`) was built from `ccr-08b1bb9f-iljmtb`, which has 6 commits not here, including "compile fixes for merged Circles and share work". | `git rev-list` counts; `origin/apk-downloads` head | We can't say what ships, or reproduce what a tester saw. | CTO / Synapse |
| T-03 | R | The signature experiences have **never been verified on a device**: moments (First Keep, Unveil), the 00:01 devotional notification, Read to Me, Circles create/join/invite, Google sign-in on release. The readiness doc's verdict ("Conditionally Ready") rests on unit tests. | `agents/STATUS.md` "Open, needs a device"; `docs/quality/RELEASE_READINESS.md` | The founder already reports features "only partly there" on device (Bible/Faith home, Read to Me, Home Circles). | QA (independent) |
| T-04 | R | Low-RAM processing kill (ISS-05) is still "Under investigation". Our Southern African audience skews to 3–4 GB phones, so this is a core-journey failure for them, not an edge case. | `docs/quality/ISSUE_REGISTER.md` ISS-05 | Recording finishes, the note never arrives. | Eng + QA |
| T-05 | R | Circles needs the owner to deploy the Worker and `firestore.rules`. Until then, Circles and Share to Circle can't work for real users. | `STATUS.md`, `RELEASE_READINESS.md` §3 | Whole feature unavailable. | Founder |
| T-06 | P1 | Onboarding's Setup step promises "Built-in cloud AI is ready out of the box". That is only true when the build has a managed proxy URL or system key; release builds take both from env vars. | `OnboardingScreen.kt:592`; `ai/cloud/GeminiRoute.kt`; `build.gradle.kts:38,72` | A broken promise at the moment that decides first success. | Eng to confirm release config |
| T-07 | P1 | **Learning leaks into onboarding**, against "Learning is out of the MVP". The Goals step offers "Learning and research". Picking no goals turns on every space, Learning included. The rest of the app filters Learning out (`OfferedNotebookSpaces`). | `OnboardingModel.kt:21,62-63`; `OnboardingSteps.kt:149`; `core/model/Notes.kt:47` | New users get a space we don't support. | Rapid fix |
| T-08 | P1 | **Share to Circle sends more than the person may mean to.** All items start ticked. The brief preview is cut at 120 characters, so the full AI summary goes to the server unread. AI-guessed assignees are posted as `@names`. No error handling: a failure leaves "Publishing…" stuck, or publishes some items and not others. | `feature/work/ShareToCircleSheet.kt:77-79,196,266-297` | Content leaves the device without the person seeing it; wrong people named in front of the team. | Spec (Themis) + Eng |
| T-09 | P1 | The Work page still lacks a back arrow (W-01), and failed recordings never reach Work, so there's no recovery (W-04). The audit's decisions D1–D7 are **waiting on founder approval**. That approval, not another audit, is the blocker. | `docs/mvp/WORK_UX_AUDIT.md`; `STATUS.md` | People get stuck and lose failed meetings. | Founder decision |
| T-10 | P2 | Docs disagree with code on the Circles member cap: `PRODUCT.md` says 10, the Worker enforces 50. Work templates ("Department", "Workspace") suggest groups larger than 10. | `server/circles-api/src/logic.js:4`; `agents/PRODUCT.md` | Wrong expectations for work teams. | Founder decision |
| T-11 | P2 | BYOK keys sit unencrypted in DataStore. The readiness doc rates Security GREEN anyway. | `PRE_LAUNCH_AUDIT.md` §3 | Exposure if the device is compromised. | Eng |
| T-12 | P2 | Two open founder decisions affect launch: licensed Bible text sent to Gemini (`fd5501b`, Data Safety), and reduced-motion / battery-saver behaviour for moments (accessibility). | `STATUS.md`; `PRODUCT.md` Moments | Legal/commercial risk; accessibility. | Founder |

## Proposed priority (product view, for the Engineering Planner to challenge)

1. **Release truth (blocking everything else).** One integration branch that matches what testers run (T-02); the launch configuration for tiering and billing (T-01); a device smoke pass by QA (T-03); low-RAM processing (T-04); Circles backend deployment (T-05).
2. **First-success journey, specified as one flow:** install → onboarding → first recording or sample → note with its moment → first-result offer. Includes T-06 and T-07, plus paywall behaviour once tiering is PRODUCTION. I'll write this spec once the Engineering Planner agrees the order.
3. **Work:** get founder approval of D1–D7, then fix W-01 and W-04 first. A mobile design artifact already exists.
4. **Circles for Work:** Share to Circle preview and failure handling (T-08); the member-cap decision (T-10).
5. **Faith Create:** device walk, then a design for approval.

Small, bounded items that can go through rapid-fix now: T-07.

## Decisions needed from the founder

1. Launch configuration: when does the public build enforce PRODUCTION tiering, and should the Closed Testing switch exist in release builds at all? (T-01)
2. Approve or amend Work decisions D1–D7. (T-09)
3. Circles member cap: 10 or 50? (T-10)
4. Licensed Bible text to Gemini, and reduced-motion moments. (T-12)

## Emulator walk

_Pending: debug build on emulator `MyaTest`._

## Not covered in this pass

Notes list and editor in depth, Bible study, devotional archive, Read to Me, search, settings beyond Plan and usage, assistant, import, app lock.
