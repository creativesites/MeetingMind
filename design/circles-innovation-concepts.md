# Circles innovation concepts

**Status:** Explorations for founder review. **None of these is an approved product capability.** Each one is live in the canvas, section I (frames c1–c9), and several also appear inside the main turns.

## Approved capabilities vs proposals

**Already approved, and in the redesign as requirements:**
- Overview as the Work default
- One shell for both spaces
- Devotional sharing (about 600 characters plus a link)
- Refusing MOV and 3GPP before upload
- The confidential-meeting block
- Extra confirmation before sharing to external members
- The 50-member cap
- Free Circles
- Opt-in, guilt-free routines

**Proposals in this document:** the nine concepts below. Some redesign choices in the main spec also need approval:
- The thumb dock (D1)
- Removing streaks (D2)
- Removing contribution ranking (D3)

## Ranking

Scores run from 1 to 5. A higher score is better for value, originality and feasibility, but means *more* complexity.

| Rank | Concept | User value | Originality | Feasibility | Complexity | Release |
|---|---|---|---|---|---|---|
| 1 | **Catch-up deck** | 5 | 4 | 5 | 2 | **1.0**, no Worker |
| 2 | **Prayer becomes testimony** | 5 | 5 | 4 | 2 | **1.0**, uses existing `markAnswered` and `meta.sourceId` |
| 3 | **The type follows the words** | 4 | 4 | 5 | 2 | **1.0**, on-device rules |
| 4 | **Turn chat into a post** | 4 | 4 | 5 | 2 | **1.0**, no Worker |
| 5 | **Threshold** | 3 | 3 | 4 | 3 | **1.0**, needs Compose shared transitions |
| 6 | **Decision spine** | 4 | 4 | 3 | 3 | **1.1**, needs decision status in the Worker |
| 7 | **The Table** | 4 | 5 | 4 | 3 | **1.0 visual**; bring-list in **1.1** (Worker) |
| 8 | **Thumb arc reactions** | 3 | 2 | 5 | 2 | **1.0** |
| 9 | **Read together** | 4 | 5 | 2 | 4 | **Later**, needs anchored comments |

**The three that go furthest beyond conventional community apps:**
- **Prayer becomes testimony:** content with a life cycle, where the answer is linked to the request and to everyone who held it.
- **Read together:** a shared margin on a reading, not a comment section under it.
- **The Table:** RSVP as taking a seat, with a shared bring-list, instead of a headcount.

The Decision spine and the Catch-up deck are close behind.

---

## 1. Catch-up deck

- **User problem:** Coming back after two days means scrolling a chronological feed and chat to work out what mattered. People either skim and miss Ruth's request, or don't open the Circle at all.
- **Interaction:** "Since Sunday evening" on Home, or "Catch up" on the list card, opens at most five cards ranked by what needs you (design spec §9.3). Each card has one verb: pray, read, RSVP, acknowledge, open. Swipe or tap Next. It ends with "You're caught up".
- **Visual treatment:** Stacked cards (the next two peek behind), large Outfit headline, serif for testimonies and readings, pips.
- **Motion:** I6 (card leaves with an 8° tilt, the next rises) and I4 (ripples on the final card).
- **UX rationale:**
  - It turns an unbounded log into a bounded ritual with an end.
  - The end state is a feeling of being up to date, not a count.
  - It replaces three of today's cards (Pulse, CatchUpCard, TodayPromptCard).
- **The seven questions:**
  1. *Problem:* re-entry overload.
  2. *Better because:* it is bounded, ranked and actionable in place.
  3. *Discoverable:* it's the first line on Home and a link on each list card.
  4. *One hand:* Next and Back sit at the bottom.
  5. *Accessibility:* a list with Next and Back, so swiping is never required.
  6. *Complexity:* one new component. Ranking is a pure function.
  7. *Failure or interruption:* the position is kept for the session. Leaving early loses nothing, because everything is still in Updates.
- **Technical:** derived from `feed_visit_{c}` plus the loaded data. No server change. Unit-test the ranking.
- **Release:** 1.0.

## 2. Prayer becomes testimony

- **User problem:** Prayer requests disappear down the feed. When one is answered, the people who prayed rarely find out, and the testimony that follows isn't connected to the request.
- **Interaction:**
  1. *I'm praying* adds you to a visible ring of people holding the request.
  2. The requester taps *Mark as answered*. The card warms to gold, and everyone who prayed is told (the server already sends the `answered` push).
  3. The requester is offered *Write a testimony*. That opens the composer as Testimony, linked to the prayer.
  4. The testimony shows a journey line: "Prayer · 12 Aug → Answered · 10 Nov".
- **Visual treatment:** Warm paper. The ring of avatars. The gold state persists. The journey line has a gradient from faint to gold.
- **Motion:** R4 (praying halo) and P8 (answered warm-up, once ever per post).
- **UX rationale:**
  - It gives prayer the shape of a story with an ending, which is the most meaningful moment a Faith community shares.
  - "Holding" replaces "liking".
- **The seven questions:**
  1. *Problem:* answers go unseen, and testimonies lose their context.
  2. *Better because:* the link is explicit, and the people who prayed are told.
  3. *Discoverable:* Mark as answered sits on the requester's own post. The testimony offer appears at the moment of answering.
  4. *One hand:* single taps.
  5. *Accessibility:* "7 people holding this. Double-tap to pray." Answered is announced.
  6. *Complexity:* low.
  7. *Failure:* if `markAnswered` fails, the card reverts and a snackbar offers Retry. The testimony draft is kept.
- **Anonymous requests:** the requester sees Mark as answered through their own local `anon_{c}` record. Others never learn who it was. A linked testimony from an anonymous prayer is offered as anonymous too.
- **Technical:** `markAnswered` exists. The testimony uses `meta.sourceKind = "prayer"` and `meta.sourceId`, which `cleanPostMeta` already allows.
- **Release:** 1.0.

## 3. The type follows the words

- **User problem:** Today you choose a post type before writing, from up to 14 options shown three times. People pick Note for everything, so prayer requests and decisions lose their structure.
- **Interaction:**
  - Start writing in a blank composer.
  - On-device keyword rules notice "pray for…", "we decided…", "blocked on…" or "will send by Friday" and offer: "This sounds like a prayer request. Make it a prayer."
  - Accepting reveals that type's fields.
  - The suggestion never switches by itself. It only appears while the type is Note, and it can be ignored.
- **Visual treatment:** A wash-coloured banner under the text with a sparkle mark. The matching chip outlines and pulses once.
- **Motion:** I7 and I8.
- **UX rationale:** Writing is the intent, and the structure follows. It reduces wrong-type posts without a form-first wizard.
- **The seven questions:**
  1. *Problem:* form-first friction and wrong types.
  2. *Better because:* zero cost when it's wrong, and helpful when it's right.
  3. *Discoverable:* it appears as you type.
  4. *One hand:* the banner action is near the text and keyboard.
  5. *Accessibility:* the banner is a polite live region.
  6. *Complexity:* a small rule table per space, localised later.
  7. *Failure:* no suggestion means no harm.
- **Technical:** a pure Kotlin matcher. No AI and no network, which keeps it private and offline. A later version could use the on-device model.
- **Release:** 1.0.

## 4. Turn chat into a post

- **User problem:** Important things are said in chat ("Could we keep Friday 14:00 for the review?", "please pray for…") and scroll away. They never become decisions, actions or prayer requests.
- **Interaction:** Press and hold a message, then *Turn into a post*. The composer opens with the text and a type pre-chosen (action in Work; prayer when the text asks for prayer). The new post keeps a link back to the message.
- **Visual treatment:** One row in the hold menu with a "new" pill. The post shows "From chat · 17:05".
- **Motion:** C9 then N6.
- **UX rationale:** It respects that conversation happens in chat while giving the important moments a durable home.
- **The seven questions:**
  1. *Problem:* chat loses commitments.
  2. *Better because:* it's one gesture where the moment happened.
  3. *Discoverable:* in the standard hold menu, and a tray hint in Work.
  4. *One hand:* yes.
  5. *Accessibility:* a "Turn into a post" custom action.
  6. *Complexity:* low.
  7. *Failure:* the composer keeps a draft. Nothing is sent until Post.
- **Privacy:** the original author is shown as the source. Turning someone else's message into a post shows "From Sarah's message", and the post's author is you. Anonymous content can't originate in chat, so there is nothing to leak.
- **Technical:** `meta.sourceKind = "message"`, `meta.sourceId = messageId`. No server change.
- **Release:** 1.0.

## 5. Threshold

- **User problem:** Moving from a list to a Circle feels like changing apps. Circles lack a sense of place.
- **Interaction:**
  - The cover you tap grows into the Circle's header.
  - The faces of people who've been here since you last visited assemble once beside the name.
  - Covers are generated "ripple fields" from the circle's id: concentric rings (the Circles motif) on a space palette, so every Circle looks like itself without an upload.
- **Visual treatment:** Ripple covers, a rounded-square mark with initials, and the presence ring.
- **Motion:** N1, I1 and I2.
- **UX rationale:** Continuity of place: you know where you are and who's around, without "online" surveillance.
- **The seven questions:**
  1. *Problem:* no sense of entering.
  2. *Better because:* the transition preserves context.
  3. *Discoverable:* it happens on every entry.
  4. *One hand:* N/A.
  5. *Accessibility:* reduced motion uses a crossfade. The ring has a label ("Grace, Ruth and 3 others since Sunday").
  6. *Complexity:* shared-element APIs.
  7. *Failure:* without the shared-element API, it falls back to a crossfade.
- **Technical:** `SharedTransitionLayout` (Compose 1.7+). Check the BOM. The presence ring is derived from loaded posts, messages and `lastReadAt`.
- **Release:** 1.0, or fall back to a crossfade if the BOM is older.

## 6. Decision spine

- **User problem:** Teams lose track of what was decided, especially when a decision changes. Feeds bury the history, and chats rewrite it.
- **Interaction:**
  - A vertical timeline: the milestone ahead, then this week's and earlier decisions.
  - Each node shows its status (proposed or confirmed), owner, source moment, and who has seen it.
  - A replaced decision stays, struck through, linked to its replacement.
  - Tapping a node opens the decision and its discussion.
  - Reached from Overview ("Decisions ledger"), the Circle menu and search.
- **Visual treatment:** A 2 dp rail. Node dots coloured by status. A milestone diamond. Cards with source chips.
- **Motion:** stagger on entry; nothing else.
- **UX rationale:** Decisions are the most valuable artefact a Work Circle produces. A ledger with history beats scrolling.
- **The seven questions:**
  1. *Problem:* lost or contradictory decisions.
  2. *Better because:* the history is visible and acknowledgement is explicit.
  3. *Discoverable:* a button on Overview, plus the menu.
  4. *One hand:* scrolling and taps.
  5. *Accessibility:* a list with headings per week. Status is in words.
  6. *Complexity:* medium.
  7. *Failure:* without the Worker status, every decision shows as Confirmed and "replaces" is hidden.
- **Technical:** needs `meta.decisionStatus` and `meta.replacesPostId` (design spec §12). Acknowledgement uses `react(ack)` today.
- **Release:** 1.1, or 1.0 without replace history.

## 7. The Table

- **User problem:** RSVP lists are headcounts. They don't feel like being expected, and fellowship meals need coordination ("who's bringing drinks?").
- **Interaction:**
  - Gatherings show a table with seats.
  - Saying Going places your avatar at it. Maybe and Can't leave your seat empty.
  - Below is a shared *Bring something* list, where one tap claims an item.
- **Visual treatment:** A warm oval table with the count in the middle, 12 seats (with "+N" beyond that), and your seat ringed in gold.
- **Motion:** E1 and E2 (your avatar travels to your seat and settles).
- **UX rationale:** It makes being expected visible and warm, and it coordinates the meal in one place without a chat thread.
- **The seven questions:**
  1. *Problem:* cold RSVP, and meal chaos.
  2. *Better because:* belonging plus coordination.
  3. *Discoverable:* on the event, directly under RSVP.
  4. *One hand:* yes.
  5. *Accessibility:* the table is decorative and described ("10 going, including you"). RSVP is a radio group, and the bring-list is a checklist.
  6. *Complexity:* a fixed-position layout.
  7. *Failure:* if RSVP fails, the seat returns and a snackbar offers Retry.
- **Work version:** the same engine shows reviews with an agenda, owner and "who's coming". No table.
- **Technical:** the table is UI only. The bring-list needs `event.meta.checklist` (Worker), or a linked Note post with a checklist today.
- **Release:** the table in 1.0. The bring-list in 1.1, or via a linked post in 1.0.

## 8. Thumb arc reactions

- **User problem:** The reaction picker today is a row of chips plus a dropdown. Choosing anything except the default takes two reaches.
- **Interaction:** A tap gives the space's default (Amen, Love, Celebrate, or Seen in Work). Press and hold opens an arc of that Circle's reactions right under the thumb. Slide and release to pick.
- **Visual treatment:** A raised pill with labelled emoji. The hovered option lifts.
- **Motion:** R1 and R2.
- **UX rationale:** The common case is one tap, and the rarer case is one gesture. The labels make meaning explicit ("Praying" rather than an unexplained 🙏).
- **The seven questions:**
  1. *Problem:* reach and ambiguity.
  2. *Better because:* the default is fast and the full set is still close.
  3. *Discoverable:* the hold hint is in the accessibility label and the first-run tip. The pattern is familiar from other apps.
  4. *One hand:* designed for it.
  5. *Accessibility:* custom actions list every reaction.
  6. *Complexity:* low.
  7. *Failure:* lifting outside the arc cancels. A failed reaction reverts.
- **Technical:** uses `circle.settings.reactions`. The Worker should also validate client-written reaction kinds (an audit finding).
- **Release:** 1.0.

## 9. Read together

- **User problem:** A devotional shared to a Circle gets a comment thread underneath, but people respond to *lines*, not to the whole reading.
- **Interaction:** In the devotional reader, sentences others held on to are softly underlined, with a count. Tap one to read short reflections from Ruth or Grace. Underline a sentence yourself, and optionally add a reflection.
- **Visual treatment:** A warm reader with a serif face. The underline is a soft highlighter in the space accent, and the most-held line glows slightly warmer. Superscript counts.
- **Motion:** none beyond the sheet. The underlines are static.
- **UX rationale:** A shared margin is how small groups actually read together. It brings quiet members in with a single gesture.
- **The seven questions:**
  1. *Problem:* generic comments under rich readings.
  2. *Better because:* responses are anchored and the threshold is low.
  3. *Discoverable:* underlines are visible, with a one-time tip.
  4. *One hand:* a tap opens the sheet.
  5. *Accessibility:* highlights are links with the count in their label. A toggle hides them.
  6. *Complexity:* high, because comments need anchors and edits to the text must be handled.
  7. *Failure:* without anchors, reflections fall back to normal comments.
- **Privacy:** reflections are posts in the Circle, so they are server content and not private journal entries. The reader says so before the first reflection. Nothing comes from the private journal.
- **Technical:** needs `comment.anchor {start, end, textHash}` in the Worker and rules.
- **Release:** later (1.2).

---

## Concepts considered and rejected

| Idea | Why not |
|---|---|
| Live "online now" presence | Not supported by the server. It also creates surveillance and pressure. The "since you were here" ring gives the warmth without it. |
| Streaks and badges for participation | Conflicts with the approved principle "no streaks and no guilt language". |
| An AI-written catch-up summary | Circle content would need to go to a model. The deck achieves the goal from structured data, privately and offline. Could be revisited as opt-in. |
| A Stories-style auto-advancing catch-up | Timers create pressure and fail accessibility. The deck is user-paced. |
| Polls inside feed posts | `createPoll` is chat-only. Adding it would duplicate a working feature. |
