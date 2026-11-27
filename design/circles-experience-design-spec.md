# Circles experience redesign: design spec

**Status:** Phase A, for founder approval. Nothing is implemented. No Kotlin, navigation, ViewModel, repository, Worker, Firestore rules, notification or billing code has changed.
**Branch:** `release/1.0-paid-launch`. **Date:** 16 November 2026.
**Artifacts:**
- `design/Circles Experience Redesign.dc.html`: the interactive canvas; open it in any browser
- `design/circles-motion-spec.md`: motion tokens and inventory
- `design/circles-innovation-concepts.md`: nine concepts, ranked

**Sources read:**
- Rules: `AGENTS.md`, `agents/PRODUCT.md`, `agents/STATUS.md`
- Plans and design: `docs/planning/CIRCLES_PLAN_2026-10-22_THEMIS.md`, `docs/mvp/DESIGN_SYSTEM.md`
- Code: `feature/circles2/*`, `core/circles2/*`, `server/circles-api/src/*`

Where this spec and the canvas disagree, the canvas wins, as with the other design canvases.

---

## 1. Product vision

> **Circles is a place, not a feed.** You open it to be with your people: to hold someone's request, to answer an invitation, to see what your team decided. Then you leave, and you're glad you came.

Four commitments follow from that:

1. **One shell, two homes.** The shared shell has an identity header and a thumb dock. Faith Home is a *living room*: what you can do for someone comes before anything chronological. Work Overview is a *status board*: the runway to the next milestone, what needs the team, and who owes what. Every other surface is a shared component with words and data for its space.
2. **Participation, not engagement.** The primary verbs are human and work inline: *I'm praying*, *Going*, *Seen it*, *I can help*, *Mark sent*. Catching up has an end. There are no streaks, rankings, online dots or guilt copy.
3. **Motion that explains.** Each animation has one job. The cover becomes the room. A seat fills when you say Going. An answered prayer warms to gold. Everything uses shared tokens, plays once, and has a still alternative.
4. **Honest by design.** The design says what the server does: not end-to-end encrypted, 50 members, file limits, what an anonymous post hides. It never shows a timeout as an empty Circle, and it explains a refused file before upload.

## 2. Current UX audit

The audit is from the source code. Line references point to `feature/circles2` (F/), `core/circles2` (C/) and `server/circles-api/src` (W/).

### 2.1 What exists today

| Area | Today | Source |
|---|---|---|
| Shell | `HomeTab` is Feed, Chat or Members for every space. Work has no Overview. | F/CircleHomeScreen.kt:62 |
| Header | Back, name, "groupWord · N members", Admin tag, "External / Client space" tag for `client_engagement`, Search, Invite, Settings | F/CircleHomeScreen.kt:420–540 |
| Above the first post | CircleHero, CatchUpCard, TodayPromptCard, draft banner, NextSyncMeetingStrip (Work), WorkPulseStrip (Work), ComposerCard, SharedMediaSection, filter chips, pinned post | F/FeedTab.kt:147–415 |
| Post bodies | Task, Decision, Blocker, Brief and Prayer have dedicated bodies. The other nine types share one generic body with a badge. | F/FeedTab.kt:780+ |
| Comments | `ThreadSheet` is flat, and the UI sends no `parentId`, but the model and rules support one level of threading. | F/CircleHomeScreen.kt:257, C/CirclesModels.kt:274 |
| Composer | Type picked first (PostTypeSheet), then a form. Types are offered three times: quick chips, the sheet, and chips in the composer. Up to 4 photos. No video, PDF or poll. | F/FeedSheets.kt:84, 229 |
| Chat | Swipe to reply, 6 emoji, a tray with Photo/Video/PDF/Poll/Card/Celebrate/Prayer chain, an *indeterminate* upload bar with Cancel, no retry UI for failures | F/ChatTab.kt |
| MOV/3GPP | Rejected only in the video picker, after the file has been read | F/ChatTab.kt:192 |
| Card handoff | Send to Circle appears only on the studio's last step. An empty card is dropped silently. | Circles plan §2 |
| Events | List, a detail sheet, Going/Maybe chips, create/edit/cancel, templates. Shown inside the feed. | F/EventsSection.kt |
| Members | Stats row, leaders and members, contribution counts, "Top contributor" badge, overflow role actions | F/MembersTab.kt |
| List | Featured card plus rows, space chips, a Daily Circle Pulse card with a global streak (its "Check in now" only opens a circle), search at 3+ circles, Join in three places | F/CirclesListScreen.kt |
| Media | Gallery pager with zoom, video speed 1×/1.5×/2×. PDFs open in another app. | F/MediaViews.kt |
| Devotional share | Faith circles only, a 600-character excerpt, a preview, posted as Devotional | F/ShareArtifactToCircleSheet.kt |

### 2.2 Main usability problems

1. **Work and Faith look like recolours of each other.** They have the same three tabs, and Work has no answer to "what's the state of the team?" (a founder decision says Overview should be the default).
2. **There's too much before the content.** Up to ten stacked cards before the first post. The pinned post, catch-up card and today prompt compete to be the one hero.
3. **Entry points are duplicated.** Create/Join appear four times. Composer types appear three times. Save sits in both the footer and the overflow. Mark done sits in both the overflow and the body. Invite sits in the header, Members and Settings.
4. **Nine post types render as generic bodies.** A testimony looks like a note.
5. **Discussion is flat** and sits in a separate sheet, although threading is supported.
6. **Uploads are opaque.** Progress is indeterminate, there's no retry, and MOV/3GPP are refused late.
7. **The card handoff hides its primary action**, and empty cards vanish silently.
8. **Some engagement mechanics conflict with the approved plan.** Principle 5 says "no streaks and no guilt language", but the list shows a global streak and a "Check in now" button that doesn't check in. Members shows "Top contributor".
9. **The top tabs are out of thumb reach** on a 6.2" phone, and Chat's unread count is the only signal.
10. **A timeout can look like an empty Circle** (the P2 finding in STATUS, 2026-10-21).

### 2.3 Audit of the four recent polish rounds (STATUS, 2026-11-15)

| Addition | Verdict | In the redesign |
|---|---|---|
| Animated list search and `animateItem` | **Keep** | Turn 1d. Same motion token as list reorder. |
| Space-aware aurora covers and icons | **Improve** | Ripple-field covers generated from the circle id (§7.3). Photo covers still supported. |
| Animated reactions and haptics | **Keep, extend** | Tap for the space default, hold for the thumb arc; sparks plus a count roll (motion R1–R3). |
| Media gallery improvements | **Keep** | Turn 10. Zoom-from-tile added. |
| Daily Circle Pulse card | **Remove from list (proposed)** | Replaced by the catch-up strip on each Home and the deck. |
| Catch-up card | **Make contextual** | Becomes the Catch-up deck (turn 4). |
| Check-ins and streak tracking | **Remove (proposed)** | The optional per-Circle **Daily rhythm** in notifications (turn 19b). The streak keys in `LocalCircleStore` are kept but no longer shown. |
| TodayPromptCard | **Make contextual** | Merged into the deck and the quiet-Circle home. |
| Saved posts | **Keep** | Footer bookmark only, plus a Saved filter. "Only you can see what you save." |
| Interactive checklists | **Keep** | Says "Shared list: anyone can tick it" (true on the server). |
| Tags | **Keep, quieter** | Small chips under the body. Tapping a tag filters. |
| Quiz and trivia polls | **Keep** | Chat only, as on the server. The reveal animation is new. |
| Event templates | **Keep** | Template chips in the event composer. |
| Member contribution indicators | **Remove (proposed)** | "You and Ruth" shared moments on the member page. |
| Categorised search | **Keep** | Turn 14, with highlights and jump-to-message. |
| Celebrate sheet, kudos chips | **Keep, contextual** | Chat tray only. |

Nothing is removed silently. The three removals above are founder decision D2 and D3 (§16).

## 3. Design philosophy

- **The room before the record.** Lead with what someone can do now, not with what happened most recently.
- **One verb per row.** Rows on Home and Overview carry at most one inline action.
- **Shape follows purpose.** Each post type changes the middle of one shared card, never the header or footer.
- **Calm by default.** One hero per screen (DESIGN_SYSTEM §7). No idle animation. Separators instead of shadows. Colour carries space identity and status, never decoration alone.
- **Words are the interface.** Plain verbs, the circle's own word (Fellowship, Team), and no jargon.

## 4. Faith and Work distinctions

| | Faith | Work |
|---|---|---|
| Accent | Gold (`#996515` light, `#E0B25A` dark) | Indigo (`#5B5BD6` / `#9B9CF6`) |
| Home | Living room: devotional hero, hold in prayer, coming up, testimony, reading together, faces | Status board: runway hero, needs the team, due this week, latest brief, next up, since Friday, source meetings |
| Hero | Today's devotional on warm paper with serif scripture | Runway to the next milestone |
| Surfaces | Warm paper (`--warm` `#FBF6EC`, dark `#1A1A16`) for prayer, devotional and testimony | Plain surface, a left status rule for decision and blocker |
| Type voice | Literata for scripture, devotional and testimony body | Inter only, tabular numbers |
| Default reaction | Amen 🙌 (testimony, devotional, reading), Love ❤️, Celebrate 🎉 (achievement) | Seen 👍 (`ack`) |
| Primary verbs | I'm praying, Going, I've read today, Reflect | Seen it, Mark sent, I can help, Mark resolved |
| Events | Gatherings, "The Table", bring list | Reviews, milestones and deadlines, with owner, agenda and source meeting |
| People | Faces, leaders first, "you and Ruth" | Rows, role and **External** tag |
| Words | Fellowship, Circle, Gathering | Team, Review, Commitment |

Both share every component: the header, dock, post surface, reaction control, discussion, input family, chat, events engine, people, invite, search and deck.

## 5. Navigation architecture

```
Circles list ── living card ──(threshold)──▶ Circle shell
     │ ＋ Start ▸ create/kind ▸ identity ▸ people ▸ created ▸ shell (first run)
     │ ＋ Join  ▸ join sheet ▸ invite preview ▸ shell (welcome)
Circle shell
  header: ‹ · cover/mark/name · presence ring · 🔍 search · 👤＋ invite · ⋯ menu
  dock:   Home|Overview · Updates · ＋ composer · Chat · People
  ⋯ menu: notifications (+ daily rhythm) · photos and files · events · decisions (Work) · guidelines · settings · leave
Home      ▸ catch-up deck · post · reader · event · member · decisions ledger
Updates   ▸ post + discussion · composer
Chat      ▸ hold menu · tray ▸ picker / card studio · media viewer
People    ▸ member page · invite sheet
Outside   Devotional ▸ Share to a Circle ▸ pick ▸ preview ▸ Updates
          Work wrap-up ▸ Share to Circle ▸ pick ▸ (external confirm) ▸ results
```

**Thumb dock (D1).** The tabs move from the top to a 60 dp dock with a centre ＋. On Chat the ＋ is hidden, and the composer sits above the dock and rides the keyboard. The active item shows its label in a pill that glides between items. Settings stays a full page, as today, reached from ⋯.

**Deep links and push** keep their existing routes (`PushRouting`). `tab=chat` opens the Chat dock item. Post links open post detail. Unknown targets open the list.

## 6. Screen inventory

Each row gives the screen (with its canvas frame), its states, and its status.

| # | Screen (canvas frame) | States | Status |
|---|---|---|---|
| 1 | Circles list (1a–1e) | living, quiet, first-time, search, join sheet | improve |
| 2 | Create Circle (2a–2f) | kind, identity, people, created, first run, Work path | improve |
| 3 | Faith Home (3a, 3b, 3e) | normal, dark, welcome, quiet circle | new |
| 4 | Work Overview (3c, 3d, 17b) | normal, dark, after action done | new |
| 5 | Catch-up deck (4a–4c) | Faith, Work, caught up | new |
| 6 | Updates (5a–5e) | Faith, Work, saved empty, photos, draft | improve |
| 7 | Post types (6a–6c) | 10 Faith samples, 8 Work samples | new |
| 8 | Post and discussion (7a–7g) | testimony, prayer, answered, anonymous, decision, action, blocker | improve |
| 9 | Composer (8a–8f) | blank with suggestion, prayer, study, action, decision, preview | improve |
| 10 | Chat (9a–9i) | Faith, Work, tray, picker refusals, staged, hold menu, reply + keyboard, jump, empty | improve |
| 11 | Media (10a–10f) | gallery, photo, video and speed, PDF, card studio, empty card | improve / Worker-free |
| 12 | Events (11a–11f) | list and date strip, The Table, Work review, create ×2, cancel | improve |
| 13 | People (12a–12e) | Faith faces, member, Work rows, manage, invite | improve |
| 14 | Joining (13a–13f) | preview, typo, expired, full, already, pending | improve |
| 15 | Search (14a–14c) | Faith, Work tasks, Work all | improve |
| 16 | Devotional share (15a–15d) | devotional, pick, preview, reflection warning | improve |
| 17 | Meeting share (16a–16e) | wrap-up, pick, external confirm, results with retry, blocked | improve |
| 18 | Decisions ledger (17a) | spine | new |
| 19 | States (18a–18d) | loading, error, offline, empty | improve |
| 20 | Notifications and rhythm (19a, 19b) | menu, sheet | new |
| 21 | Devotional reader (c5) | reader, Read together | new, concept |

## 7. Component system

All components live in `feature/circles2/ui/` (new) and build on `core/ui/mm`. **No global component changes.** Proposed additions to the design system are listed in §8.

| Component | Purpose | Notes |
|---|---|---|
| `CircleIdentityHeader` | Cover, mark, name, subtitle, presence ring, actions | Collapses to a 56 dp compact bar on tabs other than Home. Shared-element key `circle-cover-{id}`. |
| `RippleCover` / `CircleMark` | Generative cover and mark from `circle.id` + palette | Canvas drawing, cached as `ImageBitmap` per id and size. A photo cover replaces it. |
| `PresenceRing` | Avatars of members active since your last visit | From posts, messages and `lastReadAt` already loaded. Never "online". |
| `ThumbDock` | Bottom tabs plus ＋ | 60 dp. The pill indicator uses `MMMotion.standard`. |
| `PostSurface` | Header, type body slot, footer | One composable. `PostBody` is chosen by type. |
| `PrayerHold` | Holders' ring, count, I'm praying | `prayed` endpoint. The halo plays once. |
| `TestimonyBody`, `DevotionalBody`, `AnnouncementBand`, `ReadingTogether`, `EncouragementBody`, `StudyBody`, `AchievementRibbon`, `NoteBody` | Faith type bodies | §4 visual rules |
| `DecisionBody`, `ActionRow`, `BlockerBody`, `StandupGrid`, `BriefCard` | Work type bodies | From typed meta (Circles plan P4). Falls back to body text. |
| `SourceChip` | ▶ meeting · timestamp | Same affordance as Work. Opens the source sheet. |
| `ReactionControl` + `ReactionArc` | Tap = default, hold = arc | Accessibility actions list every reaction. |
| `SharedChecklist` | Toggle items, meter, "anyone can tick" | `toggleChecklistItem` |
| `PollCard` | Vote, reveal, quiz feedback | Chat only |
| `DiscussionThread` | One-level replies, fold "View N replies", reply chip | Requester naming for anonymous |
| `MMInputBar` family | Comment box, chat composer, composer toolbar | One anatomy: context chip, field, leading ＋, trailing send |
| `AttachmentTray`, `AttachmentPicker`, `StagedStrip`, `UploadRing` | Pick, validate, stage, progress, fail, retry | Validation before reading bytes (§11.4) |
| `ChatBubble` + `MessageHoldMenu` | Grouping, reply quote, reaction pill, seen-by | |
| `CardStudio` | Template, text, Send to circle | Persistent primary action |
| `EventCard`, `RsvpControl`, `GatheringTable`, `BringList`, `DateStrip`, `TimeGrid` | Events | `GatheringTable` is decorative. RSVP is also exposed as a radio group. |
| `FaceGrid`, `MemberRow`, `CapacityMeter`, `InviteSheet` | People | |
| `CatchUpDeck` | ≤5 ranked cards, Next/Back, swipe | §9.3 |
| `RunwayHero`, `NeedsTeamList`, `CommitmentRow`, `DecisionSpine` | Work | |
| `StatusLine`, `SkeletonPost`, `OfflineBanner`, `EmptyState` | States | Reuses `core/ui/mm` |

## 8. Design tokens

**Unchanged:** `MMColors` (Paper, Midnight Graphite), `MMType`, `MMSpace`, `MMRadius`, `MMMotion`. Gold and Indigo are the space defaults per DESIGN_SYSTEM §2.

**Proposed additions** (Circles-scoped, in `ui/theme/CirclesTokens.kt`, not global):

| Token | Light | Dark | Use |
|---|---|---|---|
| `circles.warm` | `#FBF6EC` | `#1A1A16` | Faith paper surfaces (prayer, devotional, testimony, the table) |
| `circles.warmLine` | `#EFE3CB` | `#2E2A20` | Their hairline |
| `circles.glow` | gold 35% / indigo 28% | gold 28% / indigo 25% | One-shot glows only, never at rest |
| `circles.cover.*` | Faith: ember, clay, sage, dusk. Work: indigo, slate, ocean, graphite | same | Two stops per palette, white ripples at 4.5–12% |
| `MMRadius.dock` | 22 dp | | Dock pill |

Measured contrast on `circles.warm`:

| Pair | Light | Dark |
|---|---|---|
| `ink` | 16.5:1 | 15.7:1 |
| `inkSecondary` | 7.2:1 | |
| `inkMuted` (the AA value `#6B6B73`) | 4.9:1 | 5.8:1 |
| Gold accent text | 4.6:1 | 8.9:1 |
| Text on a gold button | 5.0:1 | 9.3:1 |

Add these as unit tests in the `ColorLiteralGuardTest` style.

**Type:** Literata (or the system serif until it is bundled) for `scripture` and for the devotional and testimony body at 17/28. Outfit 600 only for Circle names in the header and on cards (a display use, one per card).

## 9. Interaction architecture

### 9.1 Primary verbs (inline, one tap, reversible)

| Verb | Where | Server |
|---|---|---|
| I'm praying | prayer cards, deck, Home | `prayed` (one per user) |
| Going / Maybe / Can't | event cards, deck, detail | `rsvp` |
| Seen it | decision cards, Overview, deck | `react(ack)` |
| Mark sent / complete | own action rows | `setWorkStatus(answered)` |
| I've read today | reading posts | none: local only today. Proposed as a reaction. |
| Mark as answered | the requester only | `markAnswered` (pushes to the people who prayed) |
| Mark resolved | blocker author or moderator | `setWorkStatus` |

### 9.2 Gestures, always paired with a visible control

| Gesture | Visible equivalent |
|---|---|
| Press and hold a reaction → arc | Accessibility actions "React with …" |
| Press and hold a message → menu | The reaction pill and swipe-to-reply still work. Accessibility actions: Reply, React, More. |
| Swipe a deck card | Next and Back buttons |
| Swipe a message right → reply (kept from today) | Hold → Reply |

### 9.3 Catch-up deck ranking

Built on the phone from `LocalCircleStore.feed_visit_{c}` and the loaded posts, events and messages. No server work.

1. Items that name you: a blocker that needs you, an action due today, an @mention.
2. Prayer requests and answers, and decisions to acknowledge.
3. New testimonies, briefs and devotionals.
4. Gatherings or reviews within 7 days that you haven't answered.
5. Chat summary: count, plus the one most-reacted message.

At most five cards. Each has one verb. Ties go to the newest. A final "You're caught up" card.

### 9.4 Composer: the type follows the words

You start writing. On-device keyword rules (no AI, no network) suggest a type: prayer, testimony, decision, blocker or action. A suggestion only appears while the type is still Note, and it never switches on its own. Choosing a type reveals only that type's fields (§9.5). Preview renders the exact `PostSurface`. Close saves the draft (existing `draft_{c}`).

### 9.5 Fields per type (only what the server stores)

| Type | Fields | Server |
|---|---|---|
| Prayer | body, anonymous | `anonymous` (prayer only), `prayerApproval` |
| Testimony | body, linked prayer (optional) | `meta.sourceKind/sourceId` |
| Encouragement / Reading / Study | body, scripture ref, checklist, tags | `verseRef` (64), `meta.checklist` (8), `meta.tags` (4) |
| Achievement | body, milestone | body |
| Announcement | body, pin | admin only, `pinPost` |
| Devotional | from the devotional screen only | 600-character excerpt plus link |
| Decision | title/body, owner, source | `meta.decisionOwner`, `sourceKind/Id`. Status needs the Worker. |
| Action | title, who, due, priority | `meta.assigneeUid/Name`, `dueAt`, `priority` |
| Blocker | what, blocked on, who can unblock | `meta.blockedOn` (80), assignee |
| Standup | done, next, blocked | body with three labelled lines (parsed for display) |
| Note | body, tags, checklist | |

Attachments: up to 4 images, videos or PDFs per post. Polls stay in chat (`createPoll` is chat-only).

## 10. Accessibility

- **Targets:** every interactive element is at least 48 dp (dock items 44 dp tall inside a 60 dp bar, with full-width hit areas).
- **Screen readers:**
  - Each post is one focus group with custom actions (React, Comment, Save, More).
  - Prayer: "Ruth Banda asked for prayer, 7 people holding this. Double-tap to pray."
  - The Table is `invisibleToUser` and is described in words. RSVP is a radio group.
  - The deck is a list with Next and Back.
  - The upload ring announces 25%, 50% and 75%, then Sent or Failed.
  - The runway has a text equivalent: "On track, if credentials land by Thursday. Go-live in 21 days."
- **Colour:** status is always dot plus word. Gold and indigo text pass AA on their surfaces in both themes.
- **Font scale:** layouts use wrap rows. Covers grow to fit names at 200%. The reader has its own Aa control.
- **Motion:** §3 of the motion spec. Every animation has an end-state alternative when the animator duration scale is 0 or "Remove animations" is on.
- **One hand:** the dock, ＋, composer, send, deck Next and RSVP all sit in the bottom third.
- **Selectable text:** post bodies, comments and the reader use `SelectableBody` (DESIGN_SYSTEM F-7).

## 11. Privacy and trust

1. **Server content.** The list's first-time state, the list footer, create step 3, the join preview, the share flows and the media hint all say: stored on MeetingMind's servers, not end-to-end encrypted. Personal notes stay on the phone.
2. **Anonymous.**
   - No avatar resolution, no member link, no metadata. The server already rounds `createdAt` to the hour and keeps `postAuthors` unreadable.
   - Anonymous posts never appear on member pages.
   - The requester replies as "Requester" (`commentAsAuthor`).
   - The composer says "not even leaders".
3. **Meeting sharing.**
   - The `WorkPrivacy.mustStayOnDevice` and confidential gate applies at open and at send, with no override (16e).
   - Only Work circles are offered.
   - The brief starts unticked.
   - Ticking the brief with external members present leads to a confirmation that shows the exact content (16c).
   - Every item links back to its source.
   - Results are reported per item, and a retry reuses the `requestId`.
4. **Devotionals.** Faith circles only. A 600-character excerpt plus a link. Private reflection and journal are off by default, and turning one on shows a warning naming what will be posted (15d).
5. **Media.** Format and size are checked against the Worker's lists before any bytes are read: JPEG/PNG/WebP up to 10 MB, MP4/WebM up to 50 MB, PDF up to 25 MB. MOV, 3GPP and HEIC are refused with a reason and a way forward (9d). Converting them later is out of scope.
6. **Membership.** The 50-member cap is shown as a meter. The full and expired states are explained. Join failures are rate-limited server-side, and the copy never reveals whether a circle exists beyond "We couldn't find that code".
7. **Notifications.** Gentle by default; Everything and Off are available. The Daily rhythm is opt-in, has no counting, and respects quiet hours.
8. **Billing.** Circles are free. Nothing in this design is gated.

## 12. Backend dependencies

Each item names a capability, its status, and what is needed.

| Capability | Status | Needed |
|---|---|---|
| Shell, dock, Home, Overview, deck, search UI, The Table visual, thumb arc, composer suggestions, turn chat into post, card studio, upload ring with retry, picker validation | **UI only** | Android |
| Threaded replies (one level) | **Android only** | Send `parentId` (the rules already allow it) |
| Seen it on decisions | **Android only** | `react(ack)` (Work default reaction) |
| Action complete, blocker resolve | **Android only** | `setWorkStatus` |
| Prayer answered → testimony link | **Android only** | `markAnswered`, plus a new post with `meta.sourceKind:'prayer'` |
| Typed Work payloads (owner, due) | **Partly there** | `meta` keys exist. Circles plan P4 adds per-type validation. |
| **External member flag** | **Worker** | `members/{uid}.external: bool`, set by the owner. It drives the EXT tag and the brief confirmation. |
| **Decision status and replaces** | **Worker** | `meta.decisionStatus` (proposed, confirmed, replaced) and `meta.replacesPostId`, plus a status endpoint |
| **Invite preview** | **Worker** | `previewInvite(code)` → name, space, member count, cover, inviter display name. No membership side effects. |
| **Event bring-list** | **Worker** (or a linked post) | `event.meta.checklist`, or create a linked Note post |
| **Event reminders and push** | **Phone first** | Calendar-bridge reminder on RSVP (exists). A Worker event push comes later. |
| In-app PDF reader | **Android only** | `PdfRenderer` |
| Server search over history | **Later** | Search is local and says so |
| Read together (anchored reflections) | **Later, Worker** | `comment.anchor {start, end}` |
| Rule hardening noticed in passing | **Worker** | Validate post-reaction kinds against settings, hide the quiz answer until vote, enforce poll `closesAt`, deeper MOV sniff |

## 13. Technical feasibility

- **Compose:**
  - Every screen fits `LazyColumn` and `Scaffold`.
  - The dock is a `NavigationBar` alternative with an `animateDpAsState` pill.
  - The shared-element threshold uses `SharedTransitionLayout` and `sharedBounds` (Compose 1.7+). Check the project's BOM before Phase B.
- **Generative covers:** one `Canvas` draw (about 13 circles) cached per id and size. This costs less than an image decode.
- **Presence ring and catch-up:** derived in `CircleHomeViewModel` from loaded data and `feed_visit`. No new listeners.
- **Uploads:** `sendAttachment` already streams through a ticket. Progress needs a counting `RequestBody` (OkHttp). Retry re-sends with the same `requestId`. Several files go in a queue of single-attachment messages.
- **Validation before read:** check `ContentResolver.getType` and `OpenableColumns.SIZE` before `readPicked`. This also removes the main-thread read flagged in the plan.
- **Performance:**
  - No idle animation. Ripple glows are one-shot.
  - `graphicsLayer` is used for scale, alpha and translation, so there's no relayout during animation.
  - Covers are bitmaps.
  - The Table is a `Layout` with fixed seat positions, so it's measured once.

## 14. Existing functionality mapping

| Today | Tomorrow |
|---|---|
| `HomeTab.Feed/Chat/Members` | `CircleTab.Home/Updates/Chat/People`. Work's Home renders Overview. |
| `CircleHero` + `CatchUpCard` + `TodayPromptCard` | Faith Home sections, the since strip, `CatchUpDeck` |
| `WorkPulseStrip`, `NextSyncMeetingStrip` | `RunwayHero`, `NeedsTeamList`, Next up |
| `ComposerCard` quick chips, `PostTypeSheet` | Dock ＋ → composer with type chips inside |
| `ThreadSheet` | `DiscussionThread` under the post on the post detail route |
| `EventsSection` in the feed | Home sections plus the `events/{id}` screen |
| `DailyCirclePulseCard`, streak | Removed (D2). Optional Daily rhythm. |
| Contribution counts, Top contributor | Removed (D3). "You and Ruth". |
| `JoinSheet` | Join sheet plus invite preview route |
| Indeterminate `LinearProgressIndicator` | `UploadRing` with real progress, cancel and retry |
| Card studio last-step send | Persistent `Send to <circle>` bar |
| `PdfTile` → external viewer | In-app reader, with "open in…" kept |
| `CircleSearchSheet` | Search route with highlights |

## 15. Implementation recommendations (Phase B, after approval)

Build in this order, each step shippable:

1. **Shared foundations:** tokens, `RippleCover`, `CircleIdentityHeader`, `ThumbDock`, `MMInputBar` family, `PostSurface` with all type bodies (old data renders through fallbacks).
2. **Chat reliability:** validation before read, `UploadRing`, retry, multi-file queue, hold menu, persistent card send. This closes F10 and F11 for good.
3. **Discussion:** threaded replies with `parentId`, fold and expand, reply chip.
4. **Faith Home, catch-up deck, Updates filters.**
5. **Work Overview** on existing meta, with Seen it (`ack`), complete and resolve.
6. **Events** redesign and The Table. **People** without rankings. Invitations.
7. **Worker package**, approved separately: the external flag, decision status, invite preview and bring-list, with Worker tests mirroring `work_circles.test.js`.

Tests: Compose screenshot tests per type in both themes and at font scale 1.3, accessibility action tests for posts and messages, the privacy gate tests already planned in P0 and P4, upload state tests with a fake transport, and a deck ranking unit test.

## 16. Founder decisions requested

| # | Decision | Recommendation |
|---|---|---|
| D1 | Bottom thumb dock instead of top tabs | Yes |
| D2 | Remove the Daily Circle Pulse and streak. Add the opt-in Daily rhythm. | Yes |
| D3 | Remove contribution counts and Top contributor | Yes |
| D4 | Faith Home order: devotional → prayer → gatherings → testimony → reading → faces | Yes, or lead with prayer |
| D5 | Approve the Worker package: external flag, decision status, invite preview, bring-list | Yes, as a separate change |
| D6 | New behaviours: turn chat into a post, type suggestions while writing, in-app PDF reader | Yes for 1.0 |
| D7 | Concepts for later: Read together, Decision spine as its own tab | Later; the spine stays reachable from Overview now |

## 17. UX validation summary

The canvas walkthrough (section W) steps through five journeys with evaluations:
1. First-time Faith member: 6 taps from link to first reply.
2. Returning member: 1 tap to pray, 3 to RSVP.
3. Work member: 2 taps to the blocker, 1 to acknowledge, 1 to complete.
4. Chat: 2 taps to stage a photo; uploads are visible and recoverable.
5. Admin: about 5 taps to create, 2 to invite.

**Remaining risks:**
- The invite preview depends on the Worker; without it, joining shows the welcome afterwards.
- The external confirmation depends on the external flag; until then, the `client_engagement` template triggers it for every member except the owner.
- The deck ranking needs tuning with real data.
