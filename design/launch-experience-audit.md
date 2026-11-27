# MeetingMind 1.0 launch experience: audit and redesign proposal

Status: **for founder approval. Nothing here is implemented.** Branch `release/1.0-paid-launch`, audited 2026-11-17.
Screens: `design/Launch Experience Redesign.dc.html` (open it in a browser, and tap through the frames).
Method: four read-only code audits (onboarding, billing, models, navigation), with every load-bearing claim re-checked by hand. Paths are relative to `app/src/main/java/com/craftflowtechnologies/meetingmind/`.
Not done: screenshots of the current app. No device was attached (`adb devices` was empty). Turn on wireless debugging on the S20+ and the current screens can be captured next to the proposals.

---

## 0. The five things that matter most

1. **The privacy promise is broken in one place today, before any redesign.** With "On-device only" on, Ask in Search still sends up to 18 note excerpts and transcript lines to Google for reranking.
   - Where: `feature/search/SearchScreen.kt:255-256`. `NeuralEmbeddingEngine` resolves a Gemini route with no profile check, and release builds always have the managed proxy (`ai/cloud/GeminiRoute.kt:55`).
   - Settings promises the opposite: "Audio and transcripts never leave this phone… search all run on its own processor" (`feature/settings/SettingsScreen.kt:943`).
   - The DeepSeek text fallback is not disclosed anywhere a user reads (`ai/cloud/DeepSeek.kt`).
   - **Recommend fixing both before closed testing, separately from the redesign** (a one-line profile check, plus disclosure copy).
2. **The product has two defaults for the same question.** Onboarding pre-selects and saves Internet mode (`feature/onboarding/OnboardingScreen.kt:304, 395`). The stored default and its comment say OFFLINE, "never starts sending recordings to a cloud service without the user choosing to" (`core/datastore/UserPreferences.kt:66-68`).
   - "Decide later" and "Offline pack" save nothing.
   - "Decide later" also says transcription won't work until set up, which is false for the managed proxy.
3. **Offline setup is a catalogue, not a guide.** There are 11 downloadable models (`ai/modelmanagement/ModelCatalog.kt:452-464`):
   - 5 speech models, 4 language models, plus a helper (Silero VAD) that is wrongly listed as a speech model you can "Use" (`:24`).
   - RAM minimums are never enforced, so a 3.9 GB model can be downloaded on a 4 GB phone.
   - The app silently overrides the chosen speech model by recording language (`ai/.../ProcessingScheduler.kt:68-83`), and nobody is told.
   - Delete has no confirmation (`feature/models/ModelManagerScreen.kt:596-606`).
4. **Monetisation is undersold and mistimed.**
   - The first-result offer fires 2.5 s after opening *any* meeting detail, including failed or still-processing ones (`MainActivity.kt:1611`, `feature/paywall/PaywallScreen.kt:368-398`).
   - Its copy implies free users lack cloud transcription, which they have.
   - BYOK ($4.99) includes every Pro capability except the managed allowance (`core/billing/Plan.kt:57-62`), so Pro's only reason to exist is "more minutes".
   - Only one of five triggers is frequency-capped (`core/billing/PaywallPolicy.kt:47`). Dismissals are stored and never read.
5. **Nothing is measured.** The analytics facade logs only to logcat (`core/analytics/Analytics.kt:15`), and there are no onboarding, model-download or activation events. Conversion cannot be improved, or even claimed, until a sink is chosen. This is a founder decision (§10, D9).

---

## 1. Current user journeys (as built)

**A. Sample route (default), 6 screens (8 with Faith).**
Welcome → Companion pick → Sample (meeting, sermon or lecture demo) → Goals (6 use cases, 6 outcomes, optional work profile, optional name) → "How should the AI run?" (Internet, Offline pack with an inline model picker, or Decide later) → [Faith: Bible download → Reading plan] → Permissions (mic and notifications together) → Home with a 6-step coach-mark tour.
- Step list: `feature/onboarding/OnboardingModel.kt:45-62`.
- UI: `OnboardingScreen.kt`, `OnboardingSteps.kt`, `PlanStep.kt`, `CompanionPickStep.kt`.

**B. Record-now route, 4 screens.** Welcome → Companion → AI setup → Permissions → recorder (`MainActivity.kt:490-498`).

**C. First recording.**
- Recorder: `feature/recording/RecordingScreen.kt`. It needs a type choice and the mic.
- The processing stage list then leads to meeting detail.
- 2.5 s later comes "Get more from every recording" (any meeting detail, once, 7-day cooldown).

**D. Offline setup after onboarding.** There are two entry points into the same download path:
- Setup, "Three small jobs" (`feature/setup/SetupUi.kt`): Hearing (Silero + Parakeet), Who spoke, Thinking (by RAM).
- AI engine, the full catalogue (`feature/models/ModelManagerScreen.kt`).
- They use different names for the same roles.

**E. Upgrade.**
- Settings → "Plan and usage" (inside "AI & offline models").
- The app menu → Plans.
- The Autopilot unlock, the Deep-synthesis upgrade card, the integrations gate, the BYOK key row.
- 80% and 100% usage dialogs.
- All of these open one 3-page paywall: Outcome → Included table → Plans (`feature/paywall/PaywallScreen.kt:90-193`).

**F. Settings.** It is reached only from the avatar or ⋯ app menu (`core/navigation/AppMenuModel.kt:100`) or deep links:
- 8 groups and 54 rows (`feature/settings/SettingsRegistry.kt`).
- Language, AI engine, backup, integrations and plans are each reachable from 2–4 places.

---

## 2. Major UX and usability problems

### Onboarding
- **It asks before it gives.** Before the user records anything, they pick a companion, 6 goals, 6 outcomes, a work profile, a name, an AI mode, a Bible and a reading plan.
  - Outcomes are read only by paywall copy (`feature/paywall/PaywallViewModel.kt:61`).
  - The name is used only for greetings.
- **The AI-mode step mixes a concept with a catalogue.** Choosing "Offline pack" opens 5 speech and 2 language model pickers, with RAM figures, inside onboarding (`OnboardingScreen.kt:~897-968`).
- **Permissions are bundled at the end**, with no tie to the moment of use. Notifications are asked before the user has anything to be notified about.
- **About 25 hardcoded English strings**, and the whole sample transcript is English-only (`SampleContent.kt`). A Bemba user's first impression is English.
- **Dead code:** the Gemini key field (`OnboardingScreen.kt:308-310`) and `markSampleViewed` (no reader).

### Work and navigation
- **"To review" overlap: root cause found.**
  - `feature/work/ProfessionalHome.kt:246` floats the Up next / To review tile up by `offset(y = -38.dp)` from a 54 dp slot.
  - That room is only reserved when the hero is drawn with `showTile = true` (338 dp, `HomeHeroHeader.kt:382`).
  - The Work home passes `showTile = false` (`feature/today/home/ModeHomeHeader.kt:108`), so the tile covers the mode-switch caption, or the bottom of the hero.
  - The fix is structural: put the tile in the list flow. Padding is not the fix.
- **The Work tab bar is too wide for 360 dp.**
  - Home + 5 segments, with the selected one drawn as a 44 dp chip and an unbounded label (`core/ui/work/VerticalBar.kt:107-131`).
  - Estimated width is about 390 dp with "Decisions" selected, against a 360 dp floor (estimate; measure on device).
  - The label has no `maxLines`, so at large text it wraps inside a fixed 44 dp height, which is the "folded" look.
- **Cramped tiles.**
  - Quick access fits 7 `weight(1f)` tiles in about 336 dp, roughly 48 dp each, with 11 sp ellipsised labels (`feature/work/home/WorkQuickAccess.kt:57-91`).
  - The glance row is 4 tiles at 11 sp (`WorkHomeParts.kt:159-172`).
  - The Up next button is about 36 dp tall (`WorkUpNextTile.kt:89-94`), below the 48 dp rule.
- **Text scale is multiplied twice.** System font scale × in-app text size, up to about 1.6× (`ui/theme/Theme.kt:85`). Meanwhile fixed heights stay fixed: the 44 dp chip, the 54 dp slot, the 46 dp mode switch and the 150 dp glance tile.
- **Two "pulse" surfaces.** The Work home "Your day" and the Pulse segment (design spec W-02).
- **Dead parts:** `WorkHeroCard`, `ScheduleAgendaRow`, `WorkCaptureRow`, `NeedLine`.

### Settings
- **It isn't discoverable.** It sits behind the avatar menu.
- **Subscription is filed under "AI & offline models".**
- **Developer and test rows sit in About:** "Onboarding experience", "Paywall and subscription", "Gemini log", "Audio benchmark".
- **Search runs over English-only titles** (`SettingsRegistry.kt:183`).

### Models
- **The terminology doesn't match** across screens: Hearing / Who spoke / Thinking (Setup) vs Speech recognition / Speaker detection / Meeting intelligence & Ask (AI engine) vs "Offline pack" (onboarding).
- **A local model is named after a cloud service.** "DeepSeek-R1 Distill" is a local model, and "DeepSeek" in Settings is a cloud backup. Users will think they are the same.
- **The setup pack installs Parakeet, which covers English and European languages only.** A Bemba recording routes to Omnilingual 300M, which the pack never installs (`ProcessingScheduler.kt:69-74`). It probably stops with "model required" (unverified, needs a device run).
- **Wi-Fi-only is off by default**, for downloads of 1.2–2.3 GB.

### States
- "Decide later" copy is wrong for Internet users.
- Setup says Internet mode "Needs… your own Gemini API key" (`feature/setup/SetupUi.kt:238`). That's false: the managed proxy serves free users.
- The free allowance "30 mins + 30 AI actions" is hardcoded in two places (`OnboardingScreen.kt:820, 1113`). The allowance table has four different versions:
  - `server/gemini-proxy/logic.js:15-21`;
  - `docs/RELEASE_BILLING_SETUP.md:130-136`;
  - the proxy README;
  - the proxy tests.

---

## 3. Conversion and monetisation weaknesses

| # | Weakness | Evidence |
|---|---|---|
| M1 | The first-result offer fires on any meeting detail, including failures | `MainActivity.kt:1611` passes only `currentRoute == MEETING_DETAIL` |
| M2 | The offer is a modal dialog over the notes the user just got | `PaywallScreen.kt:391` |
| M3 | Copy undersells: "A plan adds cloud transcription", but free already has it | `PaywallScreen.kt:393` |
| M4 | BYOK $4.99 = Pro minus the allowance, so Pro is just "more minutes" | `Plan.kt:57-62` |
| M5 | No numbers on the paywall. The free allowance and Pro's 20 h are never shown | `PaywallCopy.kt:31-41` |
| M6 | The 3-page paywall puts price on page 3 | `PaywallScreen.kt:90-193` |
| M7 | Only FIRST_RESULT is capped. A locked Autopilot tap also mutes the first-result offer for 7 days | `PaywallPolicy.kt:47`, `PaywallViewModel.kt:150` |
| M8 | Dismissal count is write-only. The CHURCH trigger is dead, and the Church plan has no page of its own | `UserPreferences.kt:362`, `PaywallPolicy.kt:12` |
| M9 | Usage is visible only in Settings. At 100% there's an OK dialog, with no "process on this phone" route | `UsageWarning.kt:62-99` |
| M10 | Restore is on the paywall only. There's no usage reset date and no renewal date | `PaywallScreen.kt:183`, `PaywallCopy.kt:60-76` |
| M11 | Live Play trials (Pro monthly 1 month free, BYOK 3 months free) contradict the spec's "no trial at launch" | `agents/PLAY_STORE.md:26-28`, `docs/ONBOARDING_PAYWALL_SPEC.md:14` |
| M12 | `trial_checkout_started` fires on every purchase. There's no restore-result event | `PaywallViewModel.kt:82, 95` |

**Your positioning vs what is live in Play Console** (`agents/PLAY_STORE.md:22-28`):

| | Positioning | Live |
|---|---|---|
| Pro | $10 | $12.99/mo · $99.99/yr · $199.99 lifetime |
| Pro+ | $30 | **No store product.** It exists only in the proxy's plan table (`server/gemini-proxy/logic.js:4-20`) |
| BYOK | $5 | $4.99/mo · $44.99/yr |
| Church | $80 | $49.99/mo · $499.99/yr |

The designs show the **live store prices**, which the app always reads from Play. Changing them is a Play Console action, not code.

---

## 4. Technical constraints (what the design must respect)

- **Prices, trials and periods come from Play through RevenueCat.**
  - The app matches *package ids* (`monthly`, `yearly`, `lifetime`, `byok_*`, `church_*`; `Plan.kt:22-32`).
  - The redesign must not hardcode a price or a trial. It renders `PlanOffer.priceText` and the store's trial phase.
- **Allowances are enforced only by the proxy**, read through `X-Usage-*` headers (`ai/cloud/UsageMeter.kt:50-63`). The app can show live numbers once `/usage` has answered. Before that it must say "monthly allowance", not a guessed figure.
  - Resets on the UTC calendar month.
- **Device capability.**
  - Total RAM, ABI and free storage are readable (`core/common/DeviceCapabilityDetector.kt`).
  - Chipset, GPU and NPU are not. The wizard can honestly say "suited / not suited to this phone's memory". It cannot promise speed.
- **Downloads are good already.** WorkManager, foreground service, HTTP Range resume, SHA-256 per file, 8 retries, and survival across restarts (`ModelDownloadWorker.kt`, `ModelDownloader.kt`). The wizard is mostly UI over this.
- **Deleting a model never touches recordings or transcripts.** Re-processing on the phone needs the model again. That's the honest consequence to show.
- **No language identification.** `autoDetectLanguage` is stored but has no reader. The wizard and recorder must ask for the language, not detect it.
- **Diarization is one on-device model** (pyannote + CAM++, English-trained embedding) or Gemini's pass in Internet mode. "Who spoke" is not a user choice of model.
- **No on-device translation, embeddings or language-ID model.** Translation has no UI. The wizard must not mention translation.
- **Several sessions share this checkout, and one session runs Gradle.** Implementation must stage by path, announce `MainActivity.kt` edits, and send builds to the runner.
- **`ColorLiteralGuardTest` and `DesignSystemGuardTest` already fail** on onboarding and `AiCraft.kt` (STATUS.md). New screens must use `MM.*` tokens.

---

## 5. Recommended information architecture

```
Bottom bar (5):  Today · Notes · [＋ New] · {lead space} · Settings
                                                 └ Work | Learn | Faith (from Home style / first use case)
Avatar menu:     switch spaces · Circles · Search · Import · Archive   (Settings, Plans, AI engine leave it)
Settings tab:
  ┌ Account card: name · plan badge · this month's usage meter → Plan & billing
  ├ AI & privacy
  │   How MeetingMind processes   Internet ◉ / On this phone ○   (one place, plain explanation)
  │   On-device AI                models, storage, setup wizard
  │   Your own Google AI key      (BYOK, Pro, Church)
  ├ Recording        language of recordings · speakers · cleanup · note style
  ├ Notifications
  ├ Spaces           Work · Learning · Faith settings (each opens its own page)
  ├ Appearance & language   app language · look · text size · companion
  ├ Plan & billing   current plan · usage + reset date · change plan · restore · manage in Google Play
  ├ Privacy & data   app lock · backup & restore · delete data · what leaves your phone
  └ Help & about     show me around · share · version   (developer rows: debug builds only)
```

Why Settings takes the second vertical slot: five items is the comfortable maximum at 360 dp with labels. The second space was the least used slot, and it stays one tap away in the avatar menu. (Decision D3.)

Work vertical bar, proposed: **Home · Tasks · Notes · Projects · Decisions.**
- Pulse becomes the top section of Work Home (removes W-02's duplicate).
- Five equal-width items, each with its label under the icon. No chip that grows with its label.

---

## 6. Proposed onboarding journey

Goal: **first real note in under 3 minutes, with zero downloads.** Four short screens, every one skippable except Welcome.

| # | Screen | What it does | Removed or moved |
|---|---|---|---|
| 1 | **Welcome** | An outcome-led loop: a waveform becomes a transcript with speakers, then a summary. "Record it. Understand it. Keep it." · Language pill · **Get started** / *Record something now* | Static icon screen |
| 2 | **What will you record?** | One question, multi-select, with icons: Meetings · Church & sermons · Lectures & study · Interviews · Conversations · Voice notes. **Skip** keeps every space. | Outcomes (now derived from use case), work profile (asked when Work first opens), name (asked by the companion later) |
| 3 | **See it work** | The existing sample, pre-set from answer 2 (sermon for church, lecture for study). Speakers appear as coloured names, and the user taps "What did we decide?" | Same, now localised |
| 4 | **How should MeetingMind think?** | Two cards with practical trade-offs. **Internet** is pre-selected for everyone (D1): "works right away, no downloads, audio is sent to Google's AI through MeetingMind". **On this phone**: "private, no signal needed, one download of about 1.3–2.3 GB, set up after this". The choice is always saved. | The inline model catalogue, "Decide later" |
| → | **Home**, first-use card | "Make your first note": Record · Import audio · Use the sample again. A 3-item getting-started checklist, dismissible. | The 6-step coach-mark tour (2 contextual hints instead) |

- **Permissions move to the moment of use.**
  - The mic primer appears on the first record tap.
  - The notification primer appears on the processing screen: "Want a nudge when your notes are ready?"
- **Moved out of onboarding:**
  - Companion pick → a "Meet your companion" card after the first note (D4).
  - Bible + reading plan → a Faith first-open card.
  - Work profile → Work first-open.
- **Record-now route:** Welcome → How should MeetingMind think? → mic primer → recorder (2 screens).
- **If the user chose "On this phone":** Home shows a non-blocking "Finish offline setup" card that opens the wizard. Recording works immediately. Processing waits for the models, or the user can process this one recording over the internet, with consent per recording.

---

## 7. Proposed offline AI setup (wizard and management)

**One rule drives the design: one model per job, unless a second language needs a second speech model.** Every extra download must state the job it does.

Wizard (Settings → On-device AI → Set up, or the Home card), 5 steps:
1. **Why on this phone?**
   - What works offline: recording, transcription, speakers, summaries, Ask about a recording, search.
   - What still needs the internet: YouTube import, live voice, AI images.
   - Costs: storage, slower processing, battery while processing.
2. **What languages do you record in?** Chips pre-filled from the app language and use case. This one answer picks the speech model:
   - English / European → Parakeet (670 MB).
   - African languages (Bemba, Nyanja, Shona, Zulu…) → Omnilingual 300M (365 MB). It covers 1,600+ languages, English included.
   - Japanese, Korean, Chinese, Hindi, Arabic… → Whisper Turbo (1.0 GB).
   - English + Bemba → Omnilingual alone (one download), with an optional "+ Parakeet for the best English" (+670 MB), stated as an extra.
3. **Your setup.** Three job cards: **Speech to text**, **Who spoke**, **Summaries & Ask**.
   - Each card shows the chosen model in plain words: tag, size and a one-line reason.
   - "Change" opens a comparison sheet for that job. Tags: *Recommended · Lightweight · Higher accuracy · Already installed · Not suited to this phone* (below `minimumRamMb`, disabled with the reason).
   - Total download, free storage after install, and Wi-Fi only (**on by default** for this wizard, D7).
   - Silero VAD is folded silently into Speech to text. It is a helper, not a choice.
4. **Downloading.**
   - Per-job progress. "Keeps going if you close the app."
   - Paused / Waiting for Wi-Fi / Failed, with Retry. "Not enough space: free 400 MB or pick Lightweight."
5. **Ready.** "Checked and installed ✓" (SHA-256 verified). Then the switch: "Process new recordings on this phone? [Yes] [Keep using Internet]". "Change any time in Settings → AI & privacy."

Management (Settings → On-device AI):
- A storage bar and the three jobs, each showing the active model.
- "Also on this phone" lists unused models with **Remove to free X**.
- **Replace** flow: a new model for a job that already has one asks "Replace Parakeet (frees 670 MB) / Keep both". Keep both is allowed only if it serves a different language.
- **Delete confirmation** states the real consequence: "Your recordings, transcripts and notes stay. To re-transcribe on this phone you'd need a speech model again."
- Paused and failed downloads show inline Resume / Retry / Discard.

Code changes this needs (no schema change):
- `ModelCapability.VAD` for Silero.
- A `ModelRoles` mapping (job → candidates → tags) computed from the catalogue and `DeviceCapabilityDetector`.
- A RAM gate in the wizard and the manager.
- A language → speech-model rule shared with `ProcessingScheduler`, so what the wizard promises is what the pipeline runs.
- A local model rename: "DeepSeek-R1 Distill" → "Step-by-step (R1 Distill)".
- A confirm dialog.
- Wi-Fi-only default on the wizard path.

---

## 8. Proposed paywall strategy

**Principle: never interrupt the first value. Offer when a limit or a locked feature is in the user's hand.**

| Moment | What appears | Rule |
|---|---|---|
| Onboarding | Nothing. Screen 4 says "Free includes a monthly allowance" in fact-only copy | — |
| **First successful result** | Not a dialog. An inline card *below* the notes, at the end of the user's first read: "Your 42-minute meeting is now notes. Pro gives you 20 hours a month like this, and Autopilot drafts the follow-ups." | Only when the meeting's notes completed from a real recording. Once. Never on failed or sample recordings |
| **Locked feature** (Autopilot, Deep synthesis, connected apps, own key, sermon packs) | A feature sheet: what it does, a preview, the plans that include it, and **Not now** back to the free path | User-initiated: always allowed. Does **not** start the cooldown |
| **80% of the allowance** | A quiet banner on the processing screen and in Settings: "24 of 30 minutes used · resets 1 Dec" | Once per period |
| **Allowance used** | At the moment a recording would go to the cloud, a sheet: **Process on this phone** (if models are installed) · **Set up offline** · **Upgrade** · **Wait until 1 Dec**. The recording is always saved | Once per recording |
| Settings → Plan & billing | The full plan page | Always |

- **Frequency:** at most one *app-initiated* prompt per 7 days. After 3 dismissals of the same kind, that kind is silent for 30 days. User-initiated opens never count. This needs dismissal counts per kind, a reuse of the existing `paywallDismissals` key.
- **The paywall itself is one scrolling page, not three.**
  - Personalised headline from the use case, honest ("For your meetings: …").
  - "What you have free" and "What Pro adds", with real numbers from the live allowance once known.
  - Yearly / Monthly / Lifetime selector, with "Best value" on yearly only when it is.
  - The trial line appears only when the store returns one.
  - Renewal sentence with the date. "Other plans": BYOK and Church, each with a one-line who-it's-for.
  - Restore · Terms · Privacy · Manage in Google Play.
- **Church** gets a real page reached from Faith: share sermon packs to a circle, larger allowance, team use (only what `CHURCH_ORG_WORKFLOWS` and the allowance actually do).
- **Plan & billing** in Settings shows:
  - the current plan and status (trial, grace or expiring);
  - usage with the reset date;
  - Restore and Manage in Google Play;
  - "What happens if I cancel": notes stay, the allowance returns to free.

---

## 9. Screen-by-screen redesign plan

Every screen named below is drawn in the canvas (ids in brackets).

| Area | Change | Size |
|---|---|---|
| Design foundations | Tokens only (`MM.*`). A `MMBottomBar` with equal-width labelled items and `maxLines = 1`. `minTouch` on all buttons. One text-scale path | M |
| Work home [6b] | Up next / To review in the flow (no offset). Quick actions 4 + More. Glance 2×2. Pulse summary at top | M |
| Work bar [6c] | 5 equal items, labels under icons, Pulse merged | S |
| Bottom bar + Settings tab [6a, 6d] | Settings tab. IA §5. Developer rows debug-only. Localised registry titles | L |
| Onboarding [1a–1f] | 4 screens. Localised sample. Permissions in context | L |
| First use [4a–4f] | Home first-use card, recorder hint, processing with notification primer, speaker hint, result | M |
| Offline wizard [2a–2h] | New, over the existing download path | L |
| Model management [3a–3d] | Replaces the AI engine list. Setup screen retired (one path) | M |
| Paywall [5a–5g] | Single page, feature sheets, usage sheets, Plan & billing | L |
| States [7a–7f] | Shared empty, loading, error and offline patterns | S |
| Analytics | Event taxonomy on the existing facade, plus an opt-out toggle | S (plus the vendor decision) |

Suggested order:
1. The privacy fix and the Silero fix (now, small).
2. Foundations and Work layout.
3. Bottom bar and Settings.
4. Onboarding.
5. Wizard and management.
6. Paywall and triggers.
7. First-use guidance.
8. Analytics.
9. Accessibility, localisation and reliability.

---

## 10. Decisions needed from you

| # | Decision | My recommendation |
|---|---|---|
| D1 | Default processing for new users | Internet, pre-selected and saved, with the plain disclosure on screen 4. Change the `UserPreferences` default comment to match |
| D2 | Prices: keep the live Play prices or move to your positioning ($10 / $30 / $5 / $80)? | Keep live prices for closed testing. Decide before production. The designs render whatever Play returns |
| D3 | Settings takes the second space slot in the bottom bar | Yes |
| D4 | Companion pick, Bible and reading plan move out of onboarding | Yes: first-note card and Faith first-open card |
| D5 | Pro+ ($30): create it, or remove it from the proxy? | Remove from the proxy for 1.0. One paid personal plan is easier to sell |
| D6 | BYOK includes Autopilot, deep synthesis and integrations | Keep BYOK = own key + integrations. Move Autopilot and deep synthesis to Pro and Church (existing BYOK subscribers: none yet in production) |
| D7 | Wi-Fi only on by default for offline setup | Yes |
| D8 | Launch trials (Pro monthly 1 month free, BYOK 3 months free) vs the spec's "no trial" | Keep the Pro yearly first-year discount. Drop the BYOK 3-month trial. Decide the Pro monthly trial |
| D9 | Analytics vendor (needs a dependency; none today) | Firebase Analytics (Firebase is already in the app), with an in-app opt-out and no content, per the existing `Analytics` facade |
| D10 | Keep the DeepSeek text fallback in Internet mode? | Keep it, and disclose it in screen 4 and Privacy. Or remove it to keep "Google only" |
| D11 | Work bar: merge Pulse into Work Home | Yes |
| D12 | One allowance table (four disagree today) | Use the live one in `docs/RELEASE_BILLING_SETUP.md`: free 30 min / 30 actions, Pro 20 h / 1,200, Church 60 h / 4,000 |

## 11. Fix now, independent of the redesign (asks approval separately)

1. **Privacy:** skip `NeuralEmbeddingEngine` in Search when the profile is OFFLINE or the note is confidential (`feature/search/SearchScreen.kt:255`). Audit the other embedding callers the same way.
2. **Silero listed as a speech model:** give it its own capability, and hide it from the "Use this" list (`ModelCatalog.kt:24`).
3. **The first-result offer:** require completed notes on a real recording (`MainActivity.kt:1611`). Fix the "adds cloud transcription" copy.
4. **Model delete:** add a confirmation (`ModelManagerScreen.kt:596-606`).
5. **Setup copy:** fix "needs your own Gemini API key" (`SetupUi.kt:238`) and the "Decide later" copy.
6. **Verify on device:** does a Bemba recording with only the setup pack installed fail with "model required"?

## 12. Event taxonomy (Phase 8, proposed)

No content, no free text, no ids beyond the existing install id. Enum values only.

- **Onboarding:** `onboarding_started`, `onboarding_step_viewed{step}`, `onboarding_use_case{use_cases}`, `onboarding_mode_chosen{internet|device}`, `onboarding_completed{route, seconds_bucket}`, `onboarding_abandoned{last_step}` (sent at the next launch).
- **Offline:** `offline_setup_opened{source}`, `offline_setup_skipped{step}`, `model_recommendation_shown{job, tag}`, `model_download_started{job, model_id}`, `model_download_completed`, `model_download_failed{reason}`, `model_removed{job}`.
- **Activation:** `first_recording_started`, `first_recording_completed{duration_bucket}`, `first_transcription_completed{mode}`, `first_transcript_reviewed`, `first_ai_interaction{kind}`.
- **Monetisation:** `paywall_displayed{placement}`, `paywall_dismissed{placement}`, `upgrade_initiated{plan, period}`, `purchase_completed`, `purchase_failed{reason_code}`, `subscription_restored{result}`, `allowance_warning{level}`, `process_on_device_chosen`.
- Existing events are renamed to match: `paywall_viewed` → `paywall_displayed`, `trial_checkout_started` → `upgrade_initiated`, `purchase_succeeded` → `purchase_completed`.
