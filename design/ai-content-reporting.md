# Report AI content ("Report this")

Part E of `design/notes-blocks-and-math-plan.md`. Status: **approved by the founder, 2026-11-16** (design as written, the Circles Worker route, and copy-only when content must stay on the phone).

## Why
Google Play's AI-Generated Content policy: an app that generates content with AI must let people report or flag offensive output to the developer **without leaving the app**, and use the reports to improve its filtering. MeetingMind has no such control today. Circles has a report sheet (`feature/circles2/FeedSheets.kt` `ReportSheet`), but only for people's posts.

## Where AI text reaches the person
Every place gets the same action. "Placement" is where the flag goes.

| # | Surface | Where it renders | Placement | Owner today |
|---|---|---|---|---|
| 1 | AI note blocks ("Written by AI from the recording") | `feature/notes/editor/NoteBlocks.kt` `SourceTag`, `TextBlock` | Flag in the AI source tag row | Notes blocks session |
| 2 | Lecture notes | `feature/learning/LearningSessionScreen.kt` `LectureBlockItem` | Block long-press menu | Learning |
| 3 | Meeting summary | `feature/meetingdetail/MeetingDetailScreen.kt` | Beside the summary's Copy | — |
| 4 | Ask about a recording | `feature/meetingdetail/components/AskAiPanel.kt` `AskAnswerText` | Beside `AiCopyButton` | — |
| 5 | Ask Learning | `feature/learning/space/AskLearningSheet.kt` `AnswerView` | Answer action row | Learning |
| 6 | Practice, flashcards, quizzes | `PracticeModeScreen.kt`, `SessionStudioScreen.kt` | Existing card menus | Learning |
| 7 | Assistant | `feature/assistant/AssistantSheet.kt` `AssistantBubble` | Beside the bubble's Copy | — |
| 8 | Search / Ask everything | `feature/search/SearchScreen.kt` `AnswerCard` | Card action row | — |
| 9 | Work Ask | `feature/work/AskScreen.kt`, `ScopedAskSheet.kt` | Beside `AiCopyButton` | Work |
| 10 | Work Brief | `feature/work/BriefScreen.kt` | Existing export menu | Work |
| 11 | Work wrap-up findings | `WrapUpScreen.kt` | Finding edit sheet, beside Dismiss (the Pulse summary is not AI-written) | Work |
| 12 | Deep synthesis | `feature/synthesis/DeepSynthesisSheet.kt` `InsightCard` | Card action row | — |
| 13 | Devotional | `feature/devotional/DevotionalScreen.kt` | Screen overflow, "Report this reading" | — |
| 14 | Prayer (Pray with me) | `feature/prayer/PrayWithMeScreen.kt` | Screen overflow | — |
| 15 | Sermon notes | Sermon notes are note blocks; the circle pack sheet only previews them | Same as 1 | Notes blocks session |
| 16 | Bible guided study | `feature/bible/GuidedSessionScreen.kt` | Prompt card menu | — |
| 17 | Circle AI brief and digest | `feature/circles2/CircleHomeTab.kt` `LatestBrief`, `CircleHomeDigest.kt` | Existing "More actions" | Circles |
| 18 | Home story cards | `feature/today/home/HomeBento.kt` `StoryCardTile` | Existing long-press | App home screens |
| 19 | YouTube import | Lands as AI note blocks (1); no separate result screen | Covered by 1 | — |

Not included: translation and Read to Me (they speak or translate the person's own text, they don't write new content) and bundled devotional readings (written by people, not AI). Only AI-written devotionals get the flag.

## The action
- **Entry:** a small flag icon labelled "Report" (content description "Report this AI content"), placed as in the table. Where there is already a menu, it is a menu item "Report this". One shared composable, `ReportAiButton` / `ReportAiMenuItem`, so every surface looks the same.
- **Sheet** (`ReportAiSheet`, `MM.*` tokens, modelled on Circles' `ReportSheet`):
  - Title "Report this". Reasons, one choice: **Offensive**, **Harmful or unsafe**, **False or misleading**, **Something else**.
  - Optional comment, 500 characters.
  - "Hide this from now on" switch, off by default (see Hiding).
  - The privacy line, always shown: "We'll send only the passage you're reporting, your reason and comment, which feature wrote it, the AI model, and the app version. Nothing else from your notes or recordings."
  - **Send report**. The sheet closes at once with "Thanks, we'll look into it." Sending happens in the background.

## What is sent
Exactly these fields, and a test pins the key set:

| Field | Value |
|---|---|
| `reportId` | random UUID, so a retry is stored once |
| `feature` | fixed id, e.g. `note_block`, `ask_learning`, `work_brief`, `devotional` |
| `reason` | `offensive`, `harmful`, `misleading`, `other` |
| `comment` | the person's words, at most 500 characters, or absent |
| `text` | the flagged passage only, at most 4,000 characters (longer is cut and marked) |
| `model` | the model id when the surface knows it, else the route at report time (`gemini-3.8-flash`, the on-device model id, or `unknown`) |
| `appVersion` | `BuildConfig.VERSION_NAME` |

Never sent: note or meeting ids, titles, other blocks, the question asked, the transcript, audio, the person's name, email, Circles or RevenueCat ids. No account is needed and none is created.

## Privacy: copy instead of upload
A report is **copy only** (never uploaded) when any of these is true:
- Processing is **Private and on-device** (`ProcessingProfile.OFFLINE`).
- The note is **private** (`Note.isPrivate`).
- The Work content is **confidential** or the Work settings say **on-device only** / **keep on device** (`core/work/WorkPrivacy`).

Then the sheet says "This stays on your phone because <reason>. Copy the report and send it to us when you choose," with a **Copy report** button (the same fields as plain text, plus the support address). The rule lives in one pure function, `AiReportPolicy.delivery(context)`, and is tested.

## Delivery
- **What exists:** Diagnostics (`core/diagnostics/`) is copy and share only, no upload. The Gemini proxy promises in `docs/DATA_SAFETY.md` to store no text. The Circles Worker (`server/circles-api`) already stores reports in Firestore with rate limits.
- **Proposal (needs your OK, it is a server piece):** a new Circles Worker route `POST /v1/reportAiContent`.
  - No sign-in required, so people who never use Circles don't get a Firebase account.
  - Validates every field against the lists and limits above; rejects anything else.
  - Rate limit 20 an hour per caller, keyed on a hash of the IP in KV; the IP itself is never stored.
  - Writes Firestore `aiReports/{reportId}` with the service account. No client rule exists for that path, so phones can't read or write it.
  - You read reports in the Firebase console (Firestore > `aiReports`). Keep 180 days.
  - Worker tests with `npm test` (no Gradle). You deploy (`agents/BACKEND.md`).
- **On the phone:** reports wait in a small file queue (`filesDir/ai_reports/`, no Room migration). A WorkManager job with a network constraint and back-off sends them. A 404 or 5xx keeps them queued, so the app can ship before the Worker is deployed. The queue holds at most 50 reports or 30 days. The person is never blocked or shown an error.

## Hiding
- Answers (Ask, Assistant, Search, Work Ask): hidden for the rest of that screen.
- Note blocks: the block gets `hiddenAfterReport` in its payload and shows as one quiet line "Hidden after you reported it · Show".
  - The "Written by AI" caption and its flag show once per section, on its first block. That flag reports the whole section: its blocks' text joined by new lines. Hide hides the whole section behind a single "Hidden after you reported it · Show" line.
  - Every AI block also has "Report this" in its block menu, which reports and hides only that block. Callouts, equations and AI Markdown blocks keep their own flag.
- Devotional, prayer, brief: no hiding; "Write a new one" is already there.

## Using reports to improve filtering
- A monthly look at `aiReports` by feature, model and reason; fixes go into prompts and guards (`ai/faith/FaithAuthorityGuard.kt` is one).
- **Found while looking:** no Gemini request sets `safetySettings`, so Google's defaults apply. Suggest setting explicit thresholds as a separate task.
- `docs/DATA_SAFETY.md` gets a row: "Reports of AI content: the passage, reason and comment you choose to send."

## Tests (JVM)
- `AiReportPayloadTest`: the exact key set; cut at 4,000 and 500 characters; no ids or other text get in even when the source object has them; the copy text has the same fields and nothing more.
- `AiReportPolicyTest`: every privacy flag alone and combined gives copy only; Internet mode with nothing private gives upload.
- `AiReportQueueTest`: queue round trip, de-duplication by `reportId`, cap and expiry.
- Worker: field validation, rate limit, idempotent write.

## Order of work
1. Core: model, policy, payload, queue, worker, sheet, button, tests.
2. Surfaces I can edit directly (3, 4, 7, 8, 12, 13, 14, 16).
3. Surfaces another session is editing (1, 2, 5, 6, 9–11, 15, 17, 18): agreed with that session; it either adds the one-line call or lets me in.
4. Worker route, after your OK, and the Data Safety row.

## Where it stands (2026-11-16)
- **Wired:** 3, 4, 7, 9, 10, 11, 12, 13, 14, 16; 1, 15 and 19 by the Notes blocks session; 17 by the Circles session (AI meeting briefs only: the `LatestBrief` flag and the post card's "More actions"); 8 and 18 by the App home screens session (Search answers; long-press on AI-written devotional story cards); 2, 5 and 6 by the Learning session (Ask Learning model answers, practice cards, studio concepts; `LearningSessionScreen.kt` no longer exists). Core, tests and the Worker route (`server/circles-api/src/aireport.js`, `npm test` 112/112) are written.
- **With other sessions:** the Learning session summary (`SessionStudioScreen.kt` session menu) still needs its flag.
- **Open:** a support email for copied reports (`AiReports.SUPPORT_EMAIL`); deploying the Worker; turning on explicit Gemini `safetySettings` (separate task).
