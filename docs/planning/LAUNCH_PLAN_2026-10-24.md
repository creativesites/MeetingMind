# Production MVP launch plan

Owner: Themis (product and delivery, working alone with subagents). Date: 2026-10-24.
Base: the shipping branch `ccr-08b1bb9f-iljmtb` (`7806fad`) plus the crash-loop fix on `wt-transcription-crash-guard` (`d90ed5b`, dev APK published).
Evidence: four code audits of `7806fad` (2026-10-24). File references are from that commit.

## 0. The founder's list, and where each item lands

| # | Issue | Cause found in code | Phase |
|---|---|---|---|
| 1 | Tiering, paywall and onboarding not production-ready | Release build forces the billing key empty, so nobody can buy. The "Closed testing" switch is on by default in every build. Only 3 features are gated. 2 of 4 upsell triggers are wired. Onboarding has no plan moment. | A |
| 2 | Share devotional: no share button when browsing | The picker has no reading view: tapping a row picks it and closes. The composer sheet doesn't scroll, so "Share with circle" falls off screen with a long devotional and the keyboard up. | B |
| 3 | No preview of photos/videos before sending (chat, posts, devotional) | The staged attachment shows an icon, name and size only. Posts show nothing until the upload ends. | B |
| 4 | Upload UI not modern | A bare progress bar with no percent. A failure clears the file (no retry). One upload at a time. | B |
| 5 | Images in chat look bad | Drawn inside a padded bubble, cropped to 260 dp, with an empty placeholder while loading. | B |
| 6 | Video/image/PDF in chat should work like WhatsApp | Video has no thumbnail, no inline play, no player controls and no save. Images open full screen but have no zoom or save. PDFs have Save only and no preview. PDFs on posts never open (the FileProvider path is missing). | B |
| 7 | Chat should be fun and high-end | Ticks always show ✓✓. Failed media is silent. Forwarding is text only. | B |
| 8 | Circles should use my app name and photo by default | Circles keeps its own name store, empty on first run. Joining sends "Member". The app's photo is never uploaded. There's no rename after joining. | B |
| 9 | Create: Words and Format tabs need a redesign; make writing work | Writing uses Gemini only and needs a user key or built-in key, but the button is always enabled. The "Source text" box never reaches the card. There are two confusing text boxes. Typing may drop characters (unconfirmed). | C |
| 10 | Upsell when a lower tier hits a higher-tier action, without spam | `PREMIUM_ACTION` trigger exists but nothing calls it. Blocked features either fail silently or show a disabled button. | A |
| 11 | Work home page is missing from Appearance | Removed from the picker in `d1628f7`. `ProfessionalHome.kt` (490 lines) is still in the code, unreferenced. | C |
| 12 | AI Engine: guide users to the right speech and AI models for their phone | No RAM check before download. Speech recommendation hard-coded to Parakeet. VAD is listed as a speech model. Engine jargon. Onboarding also downloads Parakeet whatever you pick. | C |
| 13 | Local LLM as a fallback in notes and elsewhere (founder asked for input) | See §2. | A |
| 14 | Every Gemini call must fall back to DeepSeek (and local where sensible) | Most calls go through `FallbackTransport` (Gemini → DeepSeek). Create and Read to Me bypass it. Only 4 features also fall back to the local model. | A |

## 1. Tiering, paywall and onboarding (A)

### What exists
- **Plans:** Free, BYOK, Pro and Church, with RevenueCat entitlements `meetingmind_byok`/`_pro`/`_church` and packages `monthly`, `yearly`, `lifetime`, `byok_*`, `church_*`.
- **Paywall:** the screen, copy and weekly cooldown exist. The "first result" offer and usage alerts are wired.
- **Prices in STATUS:** BYOK $4.99, Pro $12.99, Church $49.99 a month. Free gets 30 min of cloud transcription and 30 AI actions a month.

### What's missing
1. **No one can buy in a release build.** `app/build.gradle.kts:124` forces `REVENUECAT_API_KEY = ""`. Fix: read a production `goog_` key from env or `local.properties` for release, and fail a Play build without it once billing is on.
2. **The Free allowance isn't enforced.** Since B1 (raw Gemini key in every build), cloud calls don't go through the proxy that meters usage. `UsageMeter` only reads the proxy's headers. Fix: an on-device allowance meter that counts cloud transcription minutes and AI actions per calendar month, per plan. It stops Free at the limit and triggers the "used up" upsell. A determined user can reset it by reinstalling; that's the accepted cost of B1.
3. **The testing switch.** Merge `wt-debug-tier-switcher` (debug-only tier switcher; release always enforces tiers). The debug build also needs a test RevenueCat key so paywalls show while testing.
4. **What each plan unlocks.** `PRO_FEATURES` claims Ask across meetings, Speaker ID, Read to Me and unlimited Circles, but none of these is checked in code. One table in `Plan.kt` (decision D2) and a gate at each entry point.
5. **Upsell, without spam (item 10):** one `Upsell.request(capability, context)` call at each gated action. It shows a short sheet: what this needs, which plan has it, a "See plans" button and "Not now". Rules:
   - Only when the person taps something they can't use. Never on app open.
   - The same feature asks at most once a week. "Not now" means not again that week.
   - At most one uninvited offer per day across the app (the first-result and usage alerts count).
   - The action they tapped still says what it needs, even when the sheet is suppressed, so nothing fails silently.
6. **Onboarding:**
   - Collect name and photo (the photo is used as the Circles default, item 8).
   - Ask "Which languages do you record in?" and recommend models from that and the phone's RAM (item 12).
   - Keep the first offer after the first real result, per `docs/ONBOARDING_PAYWALL_SPEC.md`.
   - Add a plans card to the final "You're set" step: Free is unlimited offline recording plus a monthly cloud taste; Pro adds X. Shown once, skippable, and not a hard wall.
   - Rename the Faith "Plan" step to "Reading plan".
   - Fix the BYOK copy that points to a locked Settings row.
7. **The paywall when billing is off** says "Check your connection". Change it to "Plans aren't on sale in this build".

### Founder actions outside the code
- RevenueCat: a production project, the Google Play app, entitlements and the 7 packages above, and an offering marked current.
- Play Console: the subscription products and prices, plus license testers.
- Give me the production `goog_` key (as a CI secret and in local config).

## 2. AI text: one service with fallbacks (A, items 13 and 14)

**My recommendation on the local LLM:** yes, as the **last** fallback, under four conditions:
- Only if a model is already downloaded and the phone meets its RAM minimum. Never download in the background to make it work.
- Only for tasks a 0.5–1.5B model does acceptably on one note or one meeting: summary, rewrite, title, cleanup, short Q&A on one item, Work brief for one meeting. Not "Ask everything" across all meetings, and not long devotional writing (quality too low; those say "needs Internet").
- Results made on the phone are labelled ("Made on this phone").
- A time budget, plus the crash guard pattern from the transcription fix, so a slow or crashing model can't hang or close the app.

**It also gives Free a strong story:** unlimited AI on your phone, plus a monthly taste of cloud AI. Pro means fast, high-quality cloud AI with no limits on your phone's power.

**Build:** one `AiText.generate(task, input)` service used by every text call:
1. **Order:** Gemini (own key, then built-in key) → DeepSeek → local, if the task allows it and a model is installed → a clear message ("No connection and no on-device AI downloaded. Download one in Settings → AI engine").
2. **Privacy:** confidential or keep-on-device work skips the cloud entirely (today's `WorkPrivacy` rules). The Offline setting is honoured: today Assistant, Ask everything and Bible study ignore it.
3. **Counting:** counts one AI action against the Free allowance (§1.2) only when the cloud answered.
4. **Move these onto it:** Create (no fallback today), Read to Me text prep, note editor rewrite and paste tools, notes AI jobs, AiToolWorker, Assistant, Ask everything, Work brief, memory, prepare, inbox and changes, scene classifier, cleanup, reconciliation and faith notes. Then delete the "needs a Gemini API key" error strings.
5. **Fix:** `GeminiError.fromHttp` misreads the proxy's string `error`, so a 429 "allowance used up" is retried as a rate limit.
6. **Fix:** `GeminiCredentialStore.getApiKey()` ignores the managed path, which switches off Create, the devotional writer and Pray With Me on managed builds. The B1 built-in key covers it today; fix it so a future proxy build doesn't lose them.

## 3. Circles (B)

### Devotional and study sharing (item 2)
- The picker opens a **reading view** (title, date, passage, full text) with a sticky **"Share to circle"** button.
- The composer becomes a full-height, scrollable sheet with a pinned bottom bar (attachments plus Share), so Share is always visible above the keyboard.
- Send the devotional as its own post type with source and link, as planned. Today only body text and verse go out.

### Media: picking, preview, upload (items 3 and 4)
- **Preview before sending:** after picking, a tray shows real thumbnails (photos; a video frame with its duration; the PDF's first page), each with ✕. Add a caption, then send. The same tray is used in chat, posts and devotional share.
- **Several files at once,** each with its own progress ring and percent, cancel, and **Retry** on failure. A failed item stays in the tray and is never silently dropped.
- **Upload runs in the background** (WorkManager), so leaving the screen doesn't kill it. In chat, the message appears at once with a progress overlay, as in WhatsApp.

### Media in chat (items 5–7)
- **Images:** shown edge to edge with rounded corners and no padded bubble. Caption underneath; time and ticks over the image. A blurred or sized placeholder while loading. Two to four images sent together appear as a grid.
- **Image viewer:** pinch to zoom, swipe between the chat's images, Save to phone, Share, Forward.
- **Video:** thumbnail with duration and a play button. Tap to play inline, or full screen with a real player (Media3 ExoPlayer: play, pause, seek, mute). Download, cached so it plays offline after that. Save to phone.
- **PDF:** first-page preview (Android `PdfRenderer`), with name and page count. Tap opens an in-app viewer (pages via `PdfRenderer`), with "Open in…" and Save.
- **Fix:** add `circles_media` to `res/xml/file_paths.xml`, so PDFs on posts open.
- **Fun and high-end:**
  - Real delivery ticks.
  - Long-press reactions with quick emojis.
  - Swipe to reply.
  - Typing indicator.
  - Voice-note waveform.
  - Date separators and grouped bubbles.
  - Forwarding media.
  - A media gallery per circle ("Photos, videos and files").
  - A "jump to latest" button.

### My name and photo (item 8)
- On first create or join, Circles uses the app name (`UserPreferences.user_name`). It never sends "Member" when a name exists.
- If the app has a profile photo, upload it automatically as the Circles avatar (once, then whenever the app photo changes, unless they set a Circles-only photo).
- Add a "Your name in this circle" setting and a Worker endpoint to rename. It currently doesn't exist.

### Checks
- Device walkthrough of every media type sending and receiving, between two phones.
- Existing tests plus new ones for the tray state, retry and name defaults.

## 4. Create, Work home, AI engine (C)

### Create studio (item 9)
- **Words tab redesign:** one writing area, not two. The card text is the main field. "Help me write" is beside it: tap it, pick a mood or prompt, and it fills the field. Remix chips (Shorter, Warmer, Funnier, Add a verse) sit below. Starters show when the field is empty.
- **Writing goes through `AiText`** (Gemini → DeepSeek → local), so it works on every build. The button shows what it needs only when nothing is available.
- **Fix the typing path:** the field binds to local state, synced to the session after typing, so characters can't drop. Typing no longer disables Write or Send.
- **Format tab redesign:** visual size cards (Story, Square, Portrait) with a live thumbnail of the card in each, a safe-zone overlay toggle, and a correct "too long" warning that names what's too long.

### Work home (item 11)
- Restore `PROFESSIONAL` in `HomeStyle.SELECTABLE` and the `ProfessionalHome` branch in `MainActivity` (from `d1628f7`).
- Check its parameters against current routes.
- Update it with D1–D7: Overview, Needs you, next meeting.
- People who chose it before get it back automatically.

### AI engine and model choice (item 12)
- Group models by job, in plain words, with "what it's good at", languages, download size and the phone it needs:
  - Speech to text
  - AI notes and answers
  - Speaker names (with voice detection folded in, not shown as a speech model)
- **Recommendations** from the person's languages plus total RAM and free storage. A "Best for your phone" badge. Models below the phone's minimum RAM are shown greyed out with the reason. Warn before downloading a model at the recommended limit.
- **Onboarding:** download only what was chosen (today Parakeet always downloads too), show true sizes and the total, and use the same recommendations.
- Remove engine jargon from descriptions ("sherpa-onnx", "NeMo", "MediaPipe").

## 5. Order and how it gets done

Work runs on branches cut from `wt-transcription-crash-guard` (shipping branch plus the crash fix). Independent parts run as parallel subagents in their own worktrees; I review and merge each one.

| Step | Work | Parallel? |
|---|---|---|
| 1 | Merge the crash fix and the debug tier switcher into the shipping branch | — |
| 2a | `AiText` service and fallbacks (§2) | yes |
| 2b | Billing: release key, on-device allowance meter, gates table, `Upsell` (§1.1–1.5, 1.7) | yes |
| 2c | Circles media: tray, upload worker, chat rendering, viewers, PDF (§3) | yes |
| 2d | Circles: devotional reader, scrollable composer, name and photo defaults with Worker rename (§3) | yes |
| 3a | Create redesign on `AiText` (§4) | after 2a |
| 3b | Onboarding: name and photo, languages → models, plans card (§1.6, §4) | after 2b |
| 3c | Work home restore; AI engine guidance (§4) | yes |
| 4 | Full test run, dev APK, and your screen-recorded walkthrough | — |
| 5 | Release bundle with the production billing key; closed-testing round on Play | after your RevenueCat/Play setup |

Every step ends with its unit tests passing, and a dev APK on `apk-downloads` for you to test.

## 6. Decisions for the founder

- **D1. Local LLM as the last AI fallback** under the conditions in §2? Recommended: yes.
- **D2. What Free gets vs. what's paid.** Recommended:
  - **Free:** unlimited offline recording and transcription, on-device AI unlimited, 30 min cloud transcription and 30 cloud AI actions a month, Circles (join and create up to 3).
  - **BYOK:** everything with your own Gemini key, no allowance.
  - **Pro:** 30 h cloud transcription and 400 AI actions, Ask across all meetings, cloud Read to Me voices, unlimited Circles, Google sign-in for Circles.
  - **Church:** Pro plus 100 h and church/ministry features.
- **D3. Prices** as in STATUS (BYOK $4.99, Pro $12.99, Church $49.99 a month, yearly discount), and **no trial at launch** (per the spec)? Recommended: yes, and revisit a 7-day Pro trial after the first month's numbers.
- **D4. A plans card at the end of onboarding** (skippable, not a wall), as well as the offer after the first result? Recommended: yes.
