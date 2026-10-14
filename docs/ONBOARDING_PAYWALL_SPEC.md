> Working spec for onboarding and monetization. Decisions confirmed: sample-first entry, first offer after the first useful result, no trial at launch (tested later), RevenueCat over Play Billing, anonymous opt-out analytics. Phases 1-2 are implemented on this branch; phases 3-6 are not started.

# MeetingMind: Onboarding and Paywall Specification

## Context

MeetingMind is a native Android app (Kotlin, Compose, Room, DataStore). Today it has a 7-step feature-and-setup onboarding (`feature/onboarding/OnboardingScreen.kt`). It has no sample content, no accounts, no billing, no analytics and no entitlement layer. It also publicly promises "no tracking, no account".

The goal is an onboarding that gets a new user to the aha moment fast, plus a paywall that converts without damaging trust. The research notes point to selling the outcome, trying before signing up, multi-intent personalization, pre-permission screens, and a contextual multi-page paywall. The product principle is local-first, with paid cloud AI.

**Decisions (confirmed by you):**
1. Entry is **sample first**, with a clear "Record now" option.
2. The first subscription offer comes **after the first useful result**, and never blocks the free path.
3. **No mandatory trial at launch.** A transparent trial is tested later against that baseline.
4. Billing is **RevenueCat** (Play Billing underneath).
5. Analytics are **anonymous funnel events with an opt-out in Settings.**

## Findings that shape the design

| Finding | Where | Consequence |
|---|---|---|
| Onboarding is a 7-step pager (welcome, features, name, spaces, AI setup, Bible, permissions). There is no sample and no aha moment. | `OnboardingScreen.kt` (`STEPS = 7`, :206) | Rewrite the flow and reuse the existing step composables (spaces, setup choice, Bible, permission rows). |
| Mic and notification permissions are requested at app start, before onboarding, over the welcome screen. | `MainActivity.kt:235-251` | Remove the eager request. This is a prerequisite for pre-permission screens. |
| Routing is `onboarding_completed` → HOME or ONBOARDING. | `MainActivity.kt:253-255`, `UserPreferences.kt:33,112` | Add an onboarding version and resumable step state. |
| There is no sample data and no rights-cleared audio. | `docs/FAITH_VERTICAL.md` | Ship an original, scripted sample with precomputed results. It runs with no network and no model download. |
| The only quota today is a free 6M-token monthly DeepSeek allowance, enforced by a Cloudflare Worker per install ID. | `ai/cloud/DeepSeek.kt:129`, `server/deepseek-proxy/worker.js` | Reuse it as the **free cloud summary allowance**. It is also the template for the paid Gemini proxy. |
| Gemini is called directly from the phone. The Play bundle bakes in a tester key (`SYSTEM_GEMINI_API_KEY`). | `GeminiCredentialStore.kt:79`, `release-aab.yml` | **Blocker.** Managed cloud AI cannot be sold until a server-side proxy exists and the built-in key is removed from public builds. |
| Users can paste their own Gemini key (free BYOK) in onboarding step 4 and in Settings. | `OnboardingScreen.kt:397-411` | The "$5 BYOK offering" is undefined (see Open items). Do not break existing BYOK users. |
| There is no analytics SDK. Privacy copy says "no tracking". | `AndroidManifest.xml:10`, `SettingsScreen.kt:669`, `docs/DATA_SAFETY.md` | Analytics work includes updating the privacy copy and the Play Data Safety declaration. |
| The setup default is `INTERNET`, but the code comment says "offline pack by default". | `OnboardingScreen.kt:140` | Resolve this in the new flow (see Open items). |
| Backup exports the user's Gemini key. | `core/backup/BackupManager.kt:59` | Out of scope here. Flag it. |

## A. Onboarding flow

Target: **under 90 seconds to the aha moment**, and about 6 screens with progress shown. Every step after the sample is skippable, and state persists so a killed app resumes where the user left off.

1. **Welcome.** The product is shown in action: a looping mini-demo of audio becoming a transcript, then notes, with no feature list. The tagline stays "Remember every conversation. Privately, on your phone." Primary button: **Try a sample meeting**. Secondary text button: **Record now**. Footer: a one-line analytics disclosure with a link to the Settings toggle.
2. **Sample experience (aha).** A ~90-second original scripted meeting, with a sermon variant if Faith is the chosen intent.
   - The transcript streams in with speaker labels.
   - Summary, decisions and action items then appear.
   - The user taps one suggested question ("What did we decide?") and gets a precomputed answer.
   - Everything is labelled "Sample". It needs no network, no permissions and no model download.
   - The end card has two buttons: **Make it mine** and **Record my own**.
3. **Intent and outcomes (personalization).** Copy is conversational.
   - Multi-select intents: work meetings, sermons or church, classes or learning, personal notes and ideas. These map to the existing Spaces and `WorkProfile`.
   - Multi-select outcomes: action items, searchable archive, summaries, scripture links (shown only when Faith is selected).
   - The name is optional.
4. **Your setup (the reveal plus processing education).** Opens with "Here's what we set up for you". It lists the chosen Spaces and note templates.
   - It then explains the two processing paths in plain language: **on device** (private, free, one-time model download) and **cloud boost** (faster, uses your allowance).
   - Reuses the existing `SetupChoice` UI. The Gemini key field moves to Settings > Advanced.
5. **Permissions with context.** A custom screen precedes each system dialog, and the dialogs run in the user's own flow.
   - **Microphone:** asked at the first Record tap, or here if the user chose "Record now". The pre-screen explains why.
   - **Notifications:** asked after the first useful result. The pre-screen previews the actual notification ("Your meeting notes are ready").
6. **Land on a personalized Home.** The sample stays as a dismissible "Your sample meeting" card.
   - The existing `HomeStart` Getting-started card becomes a persistent **checklist**: record your first meeting, view the summary, ask a question, turn on reminders.
   - It survives dismissal of the tour and is reachable from Settings.
   - An optional short founder note appears with the first checklist completion, as a human touch.

**First-record path.** "Record now" skips steps 2-4 and goes to the mic pre-screen and then recording. The intent and outcome questions are asked after the first result, as a lightweight card, so no value is blocked behind questions.

## B. Free versus paid (proposal, to be reconciled before building)

Principles: users never pay to access, export, or leave with their own notes. Local processing stays free. Paid means managed cloud AI, higher limits, and extra options.

| Capability | Free | Paid |
|---|---|---|
| Record, browse, search, export notes | Full | Same |
| On-device transcription and notes | Full | Same |
| Cloud summaries and Ask | Existing monthly DeepSeek allowance (6M tokens) | Larger published allowance |
| Cloud transcription (Gemini) | Small allowance or none (decided by cost model) | Published monthly allowance |
| BYOK | Unchanged for existing users | The "$5 offering" (undefined, see Open items) |
| Church or group use | Individual use | Group offer when the use case emerges |

**Allowance arithmetic comes first.** Before any number appears in the UI, build a cost model (Gemini cost per audio minute, per summary, per Ask) and set allowances in "AI minutes" and "AI actions" with margin. **Prices are not invented here.** They are read from RevenueCat offerings, which come from the Play Console.

## C. Paywall system

**Rules**
- The paywall is a flow, not a screen, and it never blocks the free path.
- Show at most one full-screen offer per 7 days.
- After a dismissal, show it again only on a trigger event (below).
- Never use fake scarcity, resetting timers, or confusing trial toggles.
- Every price, renewal date and cancel path is stated plainly.

**Placements**
1. **Post-first-result.** After the user's first real recording result, a dismissible sheet offers "See plans". The sample ends with only a quiet "See what Pro adds" link.
2. **Premium action.** The user selects a capability that needs a plan. Explain what it does, why it needs a plan, and which plans include it.
3. **Usage nearing a limit.** A gentle indicator starts at 80% (Home plus Settings). **A running job always finishes. On exhaustion, fall back to on-device processing rather than failing.**
4. **Church or group.** Shown when the user uses the Sermon type or Faith Space, or asks about group usage. It is never part of general onboarding.
5. **Settings > Plans**, always reachable.

**Primary paywall flow (3 pages, Compose)**
- **Page 1, outcome:** personalized to the chosen intents, showing the user's own result or the sample ("Every action item, found for you").
- **Page 2, what you get:** a Free versus Paid comparison table with real allowances.
- **Page 3, plans and terms:**
  - Two plans only, with yearly as the default. A "View all plans" sheet holds the rest.
  - A step timeline: today, renewal date and price, how to cancel.
  - The CTA names the action ("Start Pro"), with a "No commitment, cancel anytime" subtitle.
  - The founder benefit and the normal price are shown together.

**Exit sheet.** One-time, on closing page 3: "Not ready for a year? Try monthly." **No fake discount.**

**Founder offer.** The first 100 users get **+50% AI allowance for 3 months**.
- It is stored as a server-side bonus with an explicit end date.
- The exact end date is shown at purchase.
- A reminder notification goes out 7 days before it ends.
- Any "spots left" counter is shown only if it is real.

**Trials.** None at launch. Prepare a RevenueCat offering so a transparent trial can be A/B tested later. The terms must state whether payment details are required, the duration, the renewal price, and the first charge date.

## D. Analytics

Anonymous events behind a small `Analytics` interface, so the vendor can be swapped. Recommended default vendor: PostHog (autocapture and session replay off).

**Privacy rules**
- A random install ID only.
- No audio, transcript, or note content in any event.
- Opt-out toggle in Settings > Privacy.
- Disclosure on the welcome screen.
- **Default off for EEA, UK and Swiss locales** (consent law), and on elsewhere.

**Events**

| Funnel stage | Events |
|---|---|
| Entry | `welcome_viewed`, `sample_started`, `record_now_tapped` |
| Aha | `sample_completed`, `sample_question_asked` |
| Personalization | `intent_selected` (the chosen values), `setup_choice_selected`, `onboarding_step_skipped`, `onboarding_abandoned` (step) |
| First result | `first_recording_saved`, `first_result_viewed`, `time_to_first_result_ms` |
| Monetization | `paywall_viewed` (placement), `plan_selected`, `purchase_completed`, `paywall_dismissed`, `exit_sheet_shown` |
| Usage | `quota_warning_shown`, `quota_exhausted`, `fallback_to_on_device` |
| Retention | Day 1, 7 and 30 retention derived server-side. RevenueCat supplies subscription and refund events. |

## E. Implementation phases and PR sequence

Each phase ships as its own PR so the value-first work lands before the monetization work.

1. **Phase 1, foundation (PR 1).**
   - Remove the eager permission prompt at `MainActivity.kt:235-251`.
   - Add onboarding version and step-state prefs in `UserPreferences.kt`.
   - Fix the routing at `MainActivity.kt:253-255` and :382.
   - Add routes to `AppNavigation.kt`.
   - Write the sample asset schema.
2. **Phase 2, sample-first onboarding (PR 2).**
   - New `feature/sample/` with the scripted sample in `assets/sample/`.
   - Rewrite `OnboardingScreen.kt`.
   - Implement the pre-permission screens.
   - Add the personalized Home and the persistent checklist (extends `feature/today/HomeStart.kt`).
3. **Phase 3, analytics (PR 3).**
   - New `core/analytics/` and the funnel events.
   - Settings toggle.
   - Update `AndroidManifest.xml:10`, `SettingsScreen.kt:669`, `docs/DATA_SAFETY.md` and `README.md`.
4. **Phase 4, billing and paywall (PR 4).**
   - New `core/billing/` (RevenueCat wrapper, `Entitlements`, cached entitlement state for offline use).
   - New `feature/paywall/` (3-page flow, plans sheet, exit sheet, upgrade-context sheet, usage indicator).
   - The paywall must **not** promise managed Gemini until Phase 5 ships.
5. **Phase 5, managed cloud AI and quota (PR 5).**
   - New Gemini proxy modeled on `server/deepseek-proxy/`. It verifies the RevenueCat entitlement and meters per user per billing period.
   - `GeminiCredentialStore` route order: the user's own key, then the proxy if entitled, then on-device.
   - Remove the built-in tester key from public builds (`release-aab.yml`).
   - Add the usage meter.
6. **Phase 6, measure and experiment.**
   - Dashboards.
   - Purchase-restore and quota tests.
   - Paywall variants (video, long-form, table) and the trial test.

## F. Critical files

- Modify: `feature/onboarding/OnboardingScreen.kt`, `MainActivity.kt`, `feature/navigation/AppNavigation.kt`, `core/datastore/UserPreferences.kt`, `feature/today/HomeStart.kt`, `feature/settings/SettingsScreen.kt`, `ai/cloud/GeminiCredentialStore.kt`, `.github/workflows/release-aab.yml`, `AndroidManifest.xml`.
- Create: `feature/sample/`, `core/analytics/`, `core/billing/`, `feature/paywall/`, `assets/sample/`, and a Gemini proxy under `server/`.
- Reuse: the existing step composables in `OnboardingScreen.kt`, `core/ui/CoachMarks.kt`, `core/ui/DesignSystem.kt`, `DeepSeek.kt` and `server/deepseek-proxy/` for quota, and the `AiModelRouter` / `ProcessingProfile` fallback in `Enums.kt`.
- On approval, copy this spec to `docs/ONBOARDING_PAYWALL_SPEC.md`.

## G. Verification

- **Unit and screenshot tests.** Run `./gradlew testDebugUnitTest`. Update `OnboardingTest.kt` and `HomeStartTest.kt`, and add Roborazzi screenshot tests for each onboarding step and each paywall page. `ColorLiteralGuardTest` must stay green.
- **Fresh-install check.** On an emulator, run `./gradlew assembleDebug` and walk Welcome → sample → intents → setup → Home with no network. Confirm there is no permission dialog before the pre-screens. Kill the app mid-flow and confirm it resumes.
- **First-record path.** Walk Record now → first result → post-result sheet → dismiss. Confirm the offer does not return until a trigger event.
- **Analytics.** Use a debug sink to confirm each event fires once, that no event carries content, and that opting out stops them.
- **Billing.** Use a Play internal test track with license testers and the RevenueCat sandbox to test purchase, restore, cancel and renewal.
- **Quota.** Use `wrangler dev` to test the proxy through exhaustion. Confirm a running job finishes and later jobs fall back to on-device.

## H. Open items needing your input (none block Phases 1-3)

1. **The "$5 BYOK offering."** Today BYOK is free and uses the user's own Gemini key. What does $5 buy: higher limits, a supported key flow, other features? Existing testers should be grandfathered.
2. **Plans and prices.** Decide the plan names, prices, and allowances after the cost model is built.
3. **Default processing path.** Decide whether on-device or cloud is the default for new users (the current code and comment disagree).
4. **Sample content.** Who writes and records the original sample meeting and the sermon variant? Transcript-only streaming is the fallback if no audio is available.
5. **Tester key.** Confirm the built-in Gemini key leaves public Play builds before paid launch.
