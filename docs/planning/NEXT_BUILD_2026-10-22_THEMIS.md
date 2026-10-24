# Next build plan (founder feedback, 2026-10-22)

Owner: Themis (product). Status: **for team planning. B1 decided 2026-10-22: raw key in every build.**
Builds on `CIRCLES_PLAN_2026-10-22_THEMIS.md`. Some items move earlier than that plan had them: the founder wants them in the next build.

## 1. What the founder saw, and why (checked in code)

| # | Report | Cause |
|---|---|---|
| N1 | "Share devotional" doesn't appear under New post | Not built yet. There's no `devotional` post type on Android or the Worker; it was planned for P3. Moves into this build. |
| N2 | "Share from your study" opens an empty field | The Study type is only a label: the composer is the same text box for every type. Nothing lets you pick from your study. |
| N3 | The New post composer is the same basic UI | The founder means this most of all by "redesign the inputs". |
| N4 | Photos, videos and PDFs don't send | Not reproduced yet (Sentinel). In chat, picking a file **sends it immediately**, so you can't add text first or see what you're sending. Captions technically go along only if you typed before picking. |
| N5 | Need media with text ("a video and what I think of it") | Chat needs a staged attachment: pick, see a preview, write, then send. Posts already accept photo attachments in the API, but the composer needs the same tray, plus video and PDF. |
| N6 | Work Today looks basic | Redesign the body; the header stays for now. |
| N7 | Work Circles aren't in the build | Planned for P4. Moves into this build. |
| N8 | Onboarding only offers three model choices | The catalog has about 13 models (5 speech, 4+ AI text, speaker ID, voice detection). They include Omnilingual (1,600+ languages, including Shona, Ndebele, Bemba, Nyanja and Zulu), Parakeet (English and 24 European languages) and Whisper. Onboarding hides all of this behind three packs. |
| N9 | Recording in onboarding fails: no cloud AI, and no models downloaded yet | The built-in cloud key is added **only when the build environment provides it** (`SYSTEM_GEMINI_API_KEY` or `GEMINI_PROXY_URL` env vars, `app/build.gradle.kts:36,72`). A local or sideloaded build without them has no cloud AI. CI (`release-aab.yml`) supplies one or the other, but nothing stops an unsupplied build. |

## 2. Built-in AI in every build (N9): top priority

**Goal:** every install can record and get notes on the first try, before any model is downloaded and without a personal key.

**B1, decided by the founder (2026-10-22):** every build, Play included, carries the raw Gemini key. The founder accepts that the key can be extracted. Consequences:
- **CI:** `release-aab.yml` currently unsets `SYSTEM_GEMINI_API_KEY` when `GEMINI_PROXY_URL` is set. Change it so the key is always included.
- **Routing:** an install with no own key must reach Gemini through the built-in key. The managed proxy may still serve paid plans if it's deployed, but a missing or failing proxy must never leave someone unable to record.
- **Tiers:** the server can no longer enforce allowances for key traffic, so the app enforces them (`UsageMeter`) and treats it as best effort.
- **Mitigations (do before the Play release):**
  - Restrict the key to the Android app: package name plus signing-certificate SHA-1, for both the Play app-signing key and the upload key. Confirm the Gemini API honours the app restriction, and send the `X-Android-Package` and `X-Android-Cert` headers if needed.
  - Set a budget alert and a daily quota cap in Google Cloud.
  - Use a dedicated Cloud project, so the key can be rotated without breaking anything else.
  - Plan for rotation: a rotated key needs a new build, so keep the proxy path available as the escape route.

**Work**
1. **Build guard:** every build (debug, tester, Play) fails without a built-in key. Read the key from `local.properties` as well as env, so a local build gets it too.
2. **Proxy (optional, for paid plans):** if deployed, it needs `PLAN_ALLOWANCES` set. Recording must not depend on it.
3. **Onboarding record step:** if cloud AI is ready, record and show notes. If it isn't (offline or over quota), the step says so plainly and offers the smallest offline pack. It never fails silently.
4. **Test:** a fresh install of each build kind records a 30-second sample with no download and no key.

## 3. Offline models: choose what you need (N8)

- **Onboarding:** keep a one-tap recommended choice based on the phone (RAM and storage) and the language they pick, and add **"Choose models"**, which shows every model in the catalog grouped by job:
  - **Speech to text:** each model shows the languages it covers in plain words, its download size, the phone it needs, and its speed and accuracy, e.g. "Best for Shona, Ndebele, Bemba, Nyanja and Zulu. 1 GB. Needs 6 GB RAM."
  - **AI notes and summaries**
  - **Speaker names**
- **Language first:** ask "Which languages do you record in?" and pre-select the best model for those languages.
- **Settings → AI Engine:** the same catalog, browsable later. Download, pause or remove each model.
- **Rules:** never auto-download over mobile data unless the person allows it. Show the total size before downloading. A model the phone can't run is shown but disabled, with the reason.
- **Owner:** Android, with Sentinel testing on a low-RAM phone. The data is already in `ModelCatalog.kt`; it needs plain-language labels and language tags per model.

## 4. Circles in this build

### 4.1 New post, redesigned (N1, N2, N3, N5)
A new composer, not a reskin. Design first; the founder approves the screen before it's built.
- **Step 1: "What are you sharing?"** Large, clear choices for the circle's space.
  - **Faith:** Prayer request, Testimony, Encouragement, **Devotional**, **From my study**, Reading plan, Photo or video, Announcement (admins).
  - **Work:** Update, Decision, Action, Blocker, Brief, Announcement.
- **Step 2: the content, depending on the type:**
  - **Devotional:** a picker of all devotionals (today first, then search by date or title). Preview the excerpt (about 600 characters, already decided), add your own words, post. The post opens the full reading.
  - **From my study:** a picker of the person's study journal entries, notes and highlights. Pick one, preview it, add words, post. Nothing private is attached without being picked. If the study journal is empty, say so and offer to write instead; never show a bare empty box.
  - **Prayer, Testimony and the others:** a premium writing field with an optional verse (theme-aware) and media.
- **Media tray in every composer:** attach photos, a video or a PDF with a thumbnail preview, remove before sending, then write and post. Upload progress per item, retry on failure, and nothing posts until uploads finish.
- **Worker:** add the `devotional` and `study` payloads `{sourceId, title, date or reference, excerpt}`. Old clients show the excerpt as text.

### 4.2 Chat attachments (N4, N5)
- Picking a file adds it to a tray above the input: preview, caption, send. No more instant sending.
- Fix the root cause Sentinel finds. Move file reading off the main thread. Refuse `.mov` and `3gpp` with a clear message before upload (already decided).
- Progress, cancel and retry on the message bubble; a failed message stays with a retry.

### 4.3 Work Circles (N7)
A first usable version in this build, drawn from the plan's §4:
- Work circle with the **Overview** default tab: needs the team, actions due, latest brief.
- Updates feed with the Work post types above.
- Chat (shared with Faith).
- Meeting → Circle sharing with the privacy gate (decided: confidential meetings never), Work-only destinations, and the extra confirmation for client circles.

**Later:** typed decision history (confirm or replace), Work events, the weekly recap.

## 5. Work Today redesign (N6)
Keep the header. Redesign the body using the Work UX audit decisions D1–D7 (approved): clear "Needs you", today's meetings, actions due, and the back-arrow and failed-recording fixes (W-01, W-04). Design first, then build.

## 6. Order of work

| Order | Item | Lead | Needs design approval first |
|---|---|---|---|
| 1 | Built-in AI in every build (§2) | Daedalus | No (B1 decision only) |
| 1 | Attachment root cause, and card send on device | Sentinel → Daedalus | No |
| 2 | Designs: New post (both spaces), media tray, chat input, Work Overview, Work Today, model chooser | Design | Founder approves |
| 3 | Worker: devotional and study payloads, post idempotency, Work types | Daedalus | No |
| 4 | Build the approved screens: composer, pickers, tray, Work Circles, Work Today, model chooser | Android | Yes |
| 5 | Device walkthrough of the whole build, light and dark | Sentinel | — |

Items 1–3 start now and in parallel.

## 7. Done means
- Fresh install, no download, no key: record → notes works in onboarding.
- New post shows Devotional and From my study. Both open a picker with real content, and posting shows the excerpt and opens the source.
- A photo, a video and a PDF each send with text, in chat and in a post. A failure shows a retry, and nothing is lost or posted twice.
- A Work circle opens on Overview. A safe meeting can be shared; a confidential one can't.
- Onboarding lets someone pick their languages and models, and shows sizes and why to pick each one.
