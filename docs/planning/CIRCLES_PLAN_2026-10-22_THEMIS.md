# Circles plan: Faith polish and Work Circles

Owner: Themis (product). Date: 2026-10-22. Status: **§7 decisions approved by the founder (2026-10-22). P0 and P1 may start; P2–P4 wait for P1 design approval.**
Source: the founder's Circles brief (2026-10-22) and the planners' draft audit. The audit was checked against `work-circles-integration` (`b114134`); corrections are marked **Checked**.

## 1. What the founder asked for

| # | Ask | Phase |
|---|---|---|
| F1 | Redesign Circle Home from scratch. Keep the header. | P1 design, P2 build |
| F2 | Premium inputs everywhere: post composer, comment box, chat box, fields | P1, P2 |
| F3 | The verse on a post is grey in dark mode | P2 (quick fix, can go first) |
| F4 | Avatars don't show in the feed or the header member row | P2 (quick fix, can go first) |
| F5 | Comments: high-end and engaging, including the input | P1, P2 |
| F6 | Share devotionals into a Circle (a must) | P3 |
| F7 | Every post type works correctly | P3 matrix |
| F8 | Redesign events | P1, P2 |
| F9 | Redesign and polish chat, especially the input | P1, P2 |
| F10 | Photos, videos and PDFs don't send in chat | P0 diagnosis |
| F11 | Can't send a card into chat after making it | P0 diagnosis |
| F12 | Other improvements to Circles | P5 |
| F13 | Think deeply about Work Circles and plan the build | §4, P4 |

## 2. What the code shows (checked)

| Area | Finding |
|---|---|
| Avatars (F4) | **Checked.** `MemberAvatar` (loads the photo, falls back to initials) exists in `MediaViews.kt:139` but no screen uses it. The feed header, composer, posts, comments, members and chat all call `InitialsAvatar`. Wiring it up is a small fix. Anonymous posts must keep initials or a neutral mark and must never resolve to the author. |
| Verse in dark mode (F3) | **Checked.** `PostVerseCard` (`FeedTab.kt:558`) and the composer preview (`FeedSheets.kt:214`) use the fixed `CirclesPalette.WarmBg`/`WarmBorder`. The fix is theme tokens and a contrast check in both themes. |
| Card into chat (F11) | **Checked.** The flow exists: chat → Create studio with a send target → `Send to <circle>` → `chat.shareCard`. But the button only appears on the studio's **last (share) step** (`CreateStudioContent.kt:434`), alongside WhatsApp and Instagram. Someone who finishes the design and looks for "send" before that step won't find it. Also, `toPayload` silently drops a card with no words and no verse. Likely causes, in order: the button is hard to find, an empty card is dropped, the build is older. Confirm on the founder's build. |
| Photo/video/PDF (F10) | **Checked, not reproduced.** The pickers exist, photos are re-encoded to JPEG, and the Worker allows JPEG/PNG/WebP 10 MB, MP4/WebM 50 MB, PDF 25 MB with content sniffing. When all three kinds fail, the cause is more likely shared: the upload ticket, the R2 bucket (`meetingmind-circles-media`) on the deployed Worker, or auth. Kind-specific risks: phone videos are often `video/quicktime` or `3gpp` (rejected), videos over 50 MB, and `readPicked` reads and re-encodes the whole file **on the main thread** (jank or ANR on large files). First step: capture the error code the chat shows, plus the Worker log for one failed send of each kind. |
| Devotional sharing (F6) | **Checked.** Devotionals can already be shared to WhatsApp and other apps (`DevotionalSharing.kt`), but not into a Circle. No `devotional` post type exists on Android (`CirclesModels.kt`) or in the Worker (`logic.js:6`). The bundled texts are public domain, so excerpts may be posted. |
| Post types (F7) | Faith: prayer, testimony, achievement, study, encouragement, reading, announcement, note. Work: task, decision, brief, standup, blocker, announcement, note. One mixed Android enum; the Worker splits by space. Work actions are flattened into body text. |
| Share to Circle (Work) | **Partly fixed already** on `wt-product-rule-fixes` (in review). It now posts the meeting summary, not the personal note; the brief starts unticked with a preview; it reports per-item results; and retry sends only what failed. **Still open:** (a) no `WorkPrivacy` check, so keep-on-device or confidential meetings can be shared; (b) Faith circles are offered as destinations; (c) the Worker has no idempotency key on `createPost`, so a retry after a timeout can post twice. |
| Events | List, detail, create, edit, cancel and RSVP exist (`EventsSection.kt`). This is redesign, not new build. |
| Home | Cover and member header plus the Feed/Chat/Members shell. The feed stacks composer, events, media and posts. |

## 3. Principles

1. **One shell, two homes.** Keep the single Circle shell and header. Faith and Work each get their own first tab and actions inside it, not a second app.
2. **Shared parts, separate content.** Inputs, comments, avatars, verse surfaces, upload states, event cards and card handoff are shared parts. Post types, words, templates, reactions, privacy rules and primary actions stay separate per space.
3. **Privacy before reach.** Nothing from a keep-on-device or confidential meeting can reach a Circle. Circle content lives on the server and is not end-to-end encrypted, and the share screen says so in plain words.
4. **Fix what exists before rebuilding.** Uploads, cards and events already exist. Diagnose before rewriting.
5. **Invite, don't nag.** Routines are opt-in and dismissible, with no streaks and no guilt language.

## 4. Work Circles

**What they're for:** a shared place where a team stays on top of what came out of its meetings: what was decided, who owes what by when, what's blocked, and what changed since last time. **What they're not:** a CRM, a project-management tool or a second task list. The task list stays in Work. A Circle shows the shared commitments and links back to them.

**Who they're for:** the existing templates: project team, client engagement, exec board, department, custom. Up to 50 members (founder decision).

**Tabs inside the shell**
- **Overview** (the Work home): a "Needs the team" strip (open decisions and blockers), commitments due this week (owner, due date, status), the latest meeting brief, the next review or milestone, and links to source meetings.
- **Updates** (the feed): typed posts: brief, decision, action, standup, blocker, announcement, note. A decision can be confirmed or replaced by a later one, and the history stays visible. An action has an owner, a due date and a status, and the owner can mark it done.
- **Chat:** the same chat as Faith, with Work reactions. Files and cards stay; Faith-only cards are hidden.
- **People:** members, roles and who is external (a client).

**Meeting → Circle (the main way content gets in)**
1. Privacy check first: if the meeting must stay on the device, the Share button explains why and nothing is offered.
2. Only Work circles are offered.
3. Pick items one by one. Each shows exactly what will be posted.
4. Every post keeps a link to its source meeting.
5. Results per item, and retry without duplicates (needs a Worker idempotency key).

**Data:** typed payloads per post type, replacing body text (for example action: `{title, ownerUid?, ownerName, dueAt?, status, sourceMeetingId}`). The Worker validates these per space. Old clients show a safe fallback, rendering the body text and never crashing.

**Events for Work:** reviews, milestones and deadlines, with an owner, an optional agenda and a link to the meeting it came from. Same engine as Faith, with different words and fields.

## 5. Phases

### P0: Fix what's broken and unsafe (start now; no design needed)
- **Share to Circle safety:** finish the gaps above. Add a `WorkPrivacy.mustStayOnDevice` gate at every entry point (checked when the sheet opens and again at send), offer only Work destinations, and add a Worker `requestId` idempotency key on `createPost` and use it from the sheet. Builds on `wt-product-rule-fixes`.
- **Attachments (F10):** reproduce on a device with one photo, one phone video and one PDF. Record the error code and the Worker log, check the R2 binding on the deployed Worker, and fix the cause. Also: move `readPicked` off the main thread, and either accept or convert `video/quicktime` and `3gpp`, or explain the limit before upload.
- **Card into chat (F11):** confirm on the founder's build. If the button is hard to find, show `Send to <circle>` as a persistent primary action when the studio is opened from chat, and explain an empty card instead of silently dropping it.
- **Quick visual fixes (F3, F4):** theme-aware verse card and preview. Use `MemberAvatar` everywhere a member is shown, and keep anonymous posts unidentifiable.
- **Done when:** each fix has a unit test, plus a device check for uploads and the card flow.

### P1: Design direction (needs founder approval before building)
High-fidelity mobile designs, light and dark: Faith Home, Work Overview, the post composer, the comment thread and comment input, the chat composer with upload states, event list/detail/create, the card handoff, the Work meeting-share preview, and decision/action detail. Each with empty, loading, error, offline and accessibility states. Keep the current header. Uses `docs/mvp/DESIGN_SYSTEM.md`.

### P2: Shared Circle quality (after P1 approval)
Build the approved shared parts: one premium input family (composer, comment, chat; with focus, sending, failed and retry states), the comment thread, the new Home layout, the events redesign, and chat polish with upload progress, cancel and retry.

### P3: Faith content
- **Devotional post type** end to end: Worker allowlist and validation, Android model and parsing, composer entry from the devotional screen ("Share to a Circle"), and a post card showing title, date, excerpt, source and a link that opens the reading. The user previews the excerpt; nothing from a journal or reflection is attached unless they add it.
- **Post-type matrix:** for every Faith type: composer fields, who can post, approval, rendering, edit, reactions, comments, attachments, moderation and dark mode. Test each one.

### P4: Work Circles experience
Typed Work payloads and Worker validation, the Overview tab, decision and action detail (confirm or replace; owner marks done), source links, Work events, and the safe meeting-share bundle from P0 extended to typed items.

### P5: Coming back (opt-in)
- **Faith:** reading-plan check-ins, event reminders, a welcome prompt for a quiet circle.
- **Work:** a weekly recap (decisions, commitments, blockers, next review), "what changed since the last meeting", and acknowledging a decision.
- All dismissible and respecting quiet hours. Measure with counts only, never content.

## 6. Order of work and owners

| Track | Lead | Can start |
|---|---|---|
| P0 Share to Circle safety and Worker idempotency | Daedalus (Android + Worker) | Now, on top of `wt-product-rule-fixes` |
| P0 attachments and card diagnosis on device | Sentinel (QA), then Daedalus | Now. Needs disk space and an emulator or the founder's phone. |
| P0 verse colours and avatars | Android | Now |
| P1 designs | Design | Now, in parallel with P0 |
| P2–P4 | Android + Worker | After P1 approval |
| Backlog and tracking | Ledger | Now |

P0 and P1 can run at the same time. P3 devotional sharing can start after P1 only if it reuses the current post card styling; otherwise it waits for P2.

## 7. Founder decisions (approved 2026-10-22 as recommended)

1. **External members in Work Circles:** can client or guest members see meeting briefs, or only posts addressed to them? **Decided:** briefs are shared only after an extra confirmation when the circle has external members.
2. **Override for confidential meetings:** never allowed, or allowed by the meeting's owner with a warning? **Decided:** never, for the MVP.
3. **Work Home:** Overview as the default tab for Work circles? **Decided:** yes.
4. **Devotional excerpt length:** the full day's reading, or an excerpt plus "Read in MeetingMind"? **Decided:** an excerpt of about 600 characters plus a link.
5. **Phone videos:** convert `.mov` or `3gpp` on the phone, or refuse with a clear message? **Decided:** refuse with a clear message before upload for now; convert later.

## 8. How we'll know it works
- Unit and Compose tests for the post-type matrix, both themes, avatar privacy, composer states and the share privacy gate.
- Worker tests for type validation per space, idempotent posts, attachment limits and old-client fallback.
- Device walkthroughs: photo, video and PDF send, cancel and retry; card make-and-send; event RSVP; dark mode.
- A two-person Work walkthrough: share a safe meeting; try to share a confidential one (blocked); confirm a decision; complete an action; open each item's source.
- A Faith walkthrough: share a devotional, every post type, open the passage, comment, react, mark a prayer answered.
