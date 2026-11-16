# Circles motion spec

**Status:** Phase A, for founder approval. Builds on `MMMotion` in `docs/mvp/DESIGN_SYSTEM.md` §6 and extends it with Circles-scoped tokens. The canvas's **Motion lab** (section M) plays 16 of these choreographies. Use **Slow ×5** in its top bar to study them and **Off** to see the reduced-motion end states.

## 1. Principles

1. **Every animation has one job:** where something came from, where it went, what changed, or that something worked. If it has no job, it doesn't exist.
2. **Plays once.** No idle or looping animation (DESIGN_SYSTEM §6). The only motion at rest is the system typing indicator, which is driven by a real typing signal and stops when that does.
3. **Never in the way.** Sending, typing, scrolling and navigating are never delayed by decoration. Every animation can be interrupted and resolves to its end state.
4. **Meaning over flourish.** A seat fills because you said Going. A prayer warms because it was answered. Sparks show only on *your own* reaction, never on other people's counts changing.
5. **One vocabulary.** All timing comes from tokens. The motif is the **ripple**: concentric, outward, once.
6. **Respect the system.** Durations multiply by Android's animator duration scale. "Remove animations" (scale 0) gives static end states. A reduced-motion preference turns decorative motion into a 150 ms crossfade.

## 2. Tokens

### 2.1 Durations (existing `MMMotion` first)

| Token | Value | Source | Use |
|---|---|---|---|
| `quick` | 150 ms | MMMotion | Press, toggle, colour change |
| `standard` | 250 ms | MMMotion | Content change, expand and collapse, tab change, sheet content |
| `enter` | 300 ms | MMMotion | Screens and sheets entering |
| `circles.message` | 200 ms | **new** | Chat bubble in, reply context chip |
| `circles.threshold` | 420 ms | **new** | List card → Circle header shared element |
| `circles.reveal` | 480 ms | **new** | Poll bars, new post insertion, progress-driven fills |
| `circles.ripple` | 640 ms | **new** | One-shot halo or sparks (prayer, reaction, creation) |
| `circles.answer` | 900 ms | **new** | Prayer answered warm-up (once, ever, per post) |
| `circles.stagger` | 30 ms per item, cap 5 items (150 ms) | **new** | First entrance of a list section |

### 2.2 Easing

| Token | Curve | Use |
|---|---|---|
| `emphasized` | cubic(0.2, 0, 0, 1) | Default for state changes |
| `emphasizedDecelerate` | cubic(0.05, 0.7, 0.1, 1) | Things arriving (screens, bubbles, shared elements) |
| `emphasizedAccelerate` | cubic(0.3, 0, 0.8, 0.15) | Things leaving (deck card out, card sent) |
| `spring` | dampingRatio 0.8, stiffness medium-low | MMMotion. Dock pill, RSVP indicator. |
| `circles.settle` | dampingRatio 0.62, stiffness low (≈ cubic(0.22, 1.2, 0.36, 1)) | **new**: avatar seat, mark settle. One small overshoot. |
| `circles.pop` | dampingRatio 0.5, stiffness medium (≈ cubic(0.34, 1.36, 0.64, 1)) | **new**: reaction emoji, tick, bookmark |

In Compose, durations come from `MM.motion` × `LocalAnimatorScale` (read once per composition from `Settings.Global.ANIMATOR_DURATION_SCALE`), and springs use `spring(dampingRatio, stiffness)`.

### 2.3 Distance

Translations are 6, 8, 10, 12, 16 or 28 dp. Nothing travels more than 28 dp except shared elements and the dismissed deck card.

## 3. Reduced motion and system settings

| Setting | Behaviour |
|---|---|
| Animator scale 0.5 / 1.5 / 2 / 5 | All durations scale. Springs scale their stiffness. |
| Remove animations (scale 0) | No transitions. End states render immediately. Progress rings still update; they're information, not decoration. |
| Reduced motion (accessibility, or Battery Saver per `MomentEnvironment`) | Shared elements and slides become a 150 ms crossfade. Sparks, halos, ripples, confetti and the deck tilt are removed. Count changes are instant. The seat appears without travel. |
| TalkBack on | The same as reduced motion for decorative effects. Announcements replace visual feedback (§10 of the design spec). |

## 4. Motion inventory

The columns give the trigger, start → end, duration and easing, direction, whether layout changes, what happens on interruption, the reduced-motion alternative, and performance notes.

### 4.1 Navigation

| ID | Interaction | Trigger | Start → end | Duration / easing | Direction | Layout? | Interruption | Reduced | Performance |
|---|---|---|---|---|---|---|---|---|---|
| N1 | **Threshold**: list → Circle | Tap a living card or row | Card cover bounds → header cover bounds. Corner 20 → 0 dp. Content below fades and rises 10 dp, starting at 55%, staggered 30 ms. | 420 ms, emphasizedDecelerate | Grows up and out | No (shared bounds) | Back during the transition reverses from the current bounds | 150 ms crossfade | `SharedTransitionLayout` + `sharedBounds(key = "circle-cover-$id")`. The cover is a cached bitmap. |
| N2 | Circle → list (back) | Back or predictive back | Header shrinks back into its card | 300 ms, emphasized | Reverse of N1 | No | Follows the predictive back gesture progress | Crossfade | Same keys |
| N3 | Dock tab change | Tap a dock item | Outgoing content fades. Incoming slides 16 dp from the side of travel. The pill glides to the new item. | 250 ms, emphasizedDecelerate. Pill on `spring`. | Lateral, by tab index | No | A new tap retargets the pill from its current position | Crossfade 150 ms. The pill jumps. | `AnimatedContent` with `togetherWith`. The pill is `animateDpAsState`. |
| N4 | Push (post, event, member, search) | Tap a row or card | 28 dp in from the right plus fade | 300 ms, emphasizedDecelerate | Forward | No | Back mid-way reverses | Crossfade | Nav `enterTransition` |
| N5 | Post card → discussion | Tap a post | Card bounds → top of the detail; the discussion rises 12 dp below | 300 ms, emphasizedDecelerate | Down into the conversation | No | Reverses | Crossfade | `sharedBounds(key = "post-$id")` |
| N6 | Composer sheet | Dock ＋ | Rises 24 dp plus fade. The keyboard opens after the sheet settles. | 300 ms, emphasizedDecelerate | Up | No | Swipe down to close at any time (keeps the draft) | Crossfade | |
| N7 | Event list → event detail | Tap an event | The calendar block is a shared element. The hero band fades in. | 300 ms | Forward | No | Reverses | Crossfade | `sharedElement(key = "cal-$id")` |
| N8 | Member list → member | Tap a face | Avatar shared element, 56 → 72 dp | 300 ms | Forward | No | Reverses | Crossfade | |
| N9 | Chat → media viewer | Tap a photo or video | Tile bounds → full screen. Corner 14 → 0. Backdrop black fades in at 250 ms. | 300 ms, emphasizedDecelerate | Expands | No | Swipe down to dismiss follows the finger and settles back or out | Crossfade | Thumbnail first, full image swapped in after the decode |
| N10 | Catch-up deck open | Tap the since strip | Screen up 24 dp. Cards stack in with a 30 ms stagger. | 300 ms | Up | No | Back | Crossfade | |

### 4.2 Posts and discussion

| ID | Interaction | Trigger | Start → end | Duration / easing | Layout? | Interruption | Reduced | Performance |
|---|---|---|---|---|---|---|---|---|
| P1 | Post expansion (read more) | Tap the clamped body | maxLines N → ∞. The fade mask dissolves. | 250 ms, emphasized | Yes, animated height (`animateContentSize`) | A second tap collapses from the current height | Instant | One item at a time. No sibling relayout during the scroll fling. |
| P2 | New post insertion | Publish | The composer drops away (N6 reverse). The new card slides in 14 dp from the top with one soft glow (`circles.glow`, 0 → 6 dp → 0). | 480 ms, emphasizedDecelerate | Yes: the list inserts at index 0 via `animateItem` | Scrolling cancels the glow | Card appears. No glow. | Glow drawn in `drawBehind`, no extra layer |
| P3 | Comment thread expand | Tap "View 2 replies" | Replies fade and drop 6 dp in place. The reply line draws. | 250 ms, emphasizedDecelerate | Yes, below only | A tap during expansion collapses | Instant | |
| P4 | Comment sent | Send | The bubble appears at the end and rises 10 dp. The list scrolls smoothly to it. The field clears immediately. | 250 ms | Yes, appended | N/A | Instant, with a jump scroll | |
| P5 | Reply context chip | Tap Reply | The chip rises 8 dp above the field. The keyboard opens. | 200 ms | Input bar grows | ✕ removes | Instant | |
| P6 | Bookmark | Tap save | The icon fills and pops 0.6 → 1.35 → 1. Snackbar. | 360 ms, `circles.pop` | No | Tap again reverses | Fill only | |
| P7 | Checklist tick | Tap an item | Box scales 0.7 → 1. The tick path draws. The strike-through fades in. The meter glides. | 250 ms, `circles.pop` and emphasized | No | Re-tap untoggles from the current state | Instant | Path draw by `PathMeasure` segment |
| P8 | Prayer answered | The requester marks it answered | The ring glows to gold and settles. The background warms. The journey line appears. | 900 ms, emphasized, **once per post ever** | Journey row inserts | N/A | Warm state appears. No glow. | |

### 4.3 Reactions

| ID | Interaction | Trigger | Start → end | Duration / easing | Interruption | Reduced | Notes |
|---|---|---|---|---|---|---|---|
| R1 | Default reaction (tap) | Tap the reaction button | Emoji 0.6 → 1.35 → 1. Ten sparks in the accent travel 22–36 dp out and fade. The count rolls up 8 dp. Light haptic (`CLOCK_TICK`). | 360 ms emoji, 640 ms sparks, `circles.pop` | A second tap removes the reaction; no sparks on removal | Colour and count change only, plus the haptic | Sparks only for your own action |
| R2 | Thumb arc | Press and hold 380 ms | The arc springs up from the thumb (scale 0.6 → 1, 10 dp rise). Options lift 6 dp and scale 1.12 under the finger. Slide to pick, release to apply (R1). | 250 ms, `circles.pop` | Lifting outside the arc cancels. Scrolling cancels. | The arc appears without the spring | Haptic on open and on each option change |
| R3 | Message reaction | Pick an emoji in the hold menu | The menu dissolves. The reaction pill pops on the bubble. | 200 ms + 360 ms | Tap the pill to remove | Instant | |
| R4 | I'm praying | Tap | The button turns to the wash colour. The halo ripples 0 → 18 dp out once. You join the ring (seat, §4.5). | 640 ms | Tap again to un-pray (the halo does not replay) | No halo | Uses the `prayed` endpoint |
| R5 | Seen it (decision) | Tap | The button becomes a "Seen" tag with a pop. Your avatar joins the ring. | 360 ms | Undo from the snackbar | Instant | |

### 4.4 Chat

| ID | Interaction | Trigger | Start → end | Duration / easing | Interruption | Reduced | Performance |
|---|---|---|---|---|---|---|---|
| C1 | Send own message | Send | The bubble appears **immediately** in its final place, scaling 0.96 → 1 with a 12 dp rise from the composer. Status "Sending…" → "Sent" with no animation. | 200 ms, emphasizedDecelerate | Typing continues during it. The field never waits. | Appears | The list's `reverseLayout` keeps the anchor. No relayout of the visible items. |
| C2 | Message arrival | New message while at the bottom | Fades in, 10 dp lift | 200 ms | N/A | Appears | If not at the bottom: no movement; the Jump pill (C6) updates instead |
| C3 | Attachment tray | Tap ＋ | ＋ rotates 45° to ✕. The tray rises 14 dp and fades. It replaces the keyboard area. | 250 ms | Tapping the field swaps the tray for the keyboard without animating twice | Instant | |
| C4 | Staging | Pick files | Thumbnails settle into the strip (`circles.settle`), 40 ms apart | 250 ms | Remove ✕ fades out | Instant | |
| C5 | Upload progress | Send with an attachment | A ring tracks real bytes (linear, updates every ≤120 ms). On completion the ring fades, the image sharpens and the tick pops. | Linear, tied to progress. Completion 360 ms. | Cancel ✕ in the ring stops at once and removes the bubble with an Undo-free toast ("Nothing was sent") | Ring updates without easing | Counting `RequestBody`. Throttle UI updates to 8/s. |
| C6 | Failure and retry | Network error | The ring freezes at its last value and its stroke turns danger. A line under the bubble: "Couldn't send. Connection dropped at 62%." **Retry** / **Remove**. | 150 ms colour change | Retry resumes the ring from 0 with the same `requestId` | Same | |
| C7 | Jump to latest | Scrolled up more than 220 dp and new messages arrive | The pill springs in from 16 dp below with the faces of the senders | 250 ms, `circles.pop` | Tap → smooth scroll, distance-capped at 1500 dp (beyond that, jump then animate the last screen) | Instant, with a jump scroll | |
| C8 | Unread separator | Opening chat with unread messages | Static line. The list opens at the separator. | None | N/A | N/A | |
| C9 | Hold menu | Press and hold a message | The scrim blurs in 150 ms. The bubble lifts to the focus position. The emoji row and menu spring up 24 dp. | 250 ms, `circles.pop` | Tap the scrim to dismiss (reverse at 150 ms) | Crossfade | Blur only on Android 12+ (`RenderEffect`); otherwise a plain scrim |
| C10 | Card sent from the studio | Tap Send to circle | The card scales to 0.4 and travels up 120 dp while fading. The studio closes. The new card bubble lands (C1). | 520 ms, emphasizedAccelerate | Send is single-shot. The button disables. | The studio closes, then the bubble appears | |
| C11 | Typing indicator | A typing signal from the server (≤10 s) | Three dots pulse in opacity | 1.2 s cycle **only while the signal is live** | Stops when the signal expires | Static "Daniel is typing" | The only repeating animation in Circles, and it is information |

### 4.5 Events and participation

| ID | Interaction | Trigger | Start → end | Duration / easing | Interruption | Reduced |
|---|---|---|---|---|---|---|
| E1 | RSVP | Tap Going / Maybe / Can't | The indicator slides to the option with a slight overshoot. The label turns to onAccent. The check fades in. | 250 ms, `circles.settle` | Retargets from the current position | Instant |
| E2 | Taking a seat | Going | Your avatar travels 60 dp up into the next empty seat at The Table and settles. The count rolls up. | 520 ms, `circles.settle` | Changing to Maybe fades the seat out (250 ms) | The seat appears |
| E3 | Reminder confirmation | Going | A hint line rises under the control: "Reminder set for 1 hour before". | 250 ms | N/A | Instant |
| E4 | Date strip | Tap a day | The pill fills and the number pops. The list below filters with `animateItem`. | 250 ms + 300 ms | Retargets | Instant |
| E5 | Event created | Create | The screen returns to the list. The new card inserts with the P2 glow. | 480 ms | N/A | Appears |
| E6 | Cancel | Confirm cancel | The title strike-through draws left to right. The card dims to 66%. | 250 ms | N/A | Instant |
| E7 | Event expansion (agenda) | Tap "The evening" | `animateContentSize` | 250 ms | Collapses from current | Instant |

### 4.6 Identity, presence, loading and success

| ID | Interaction | Trigger | Start → end | Duration / easing | Reduced |
|---|---|---|---|---|---|
| I1 | Presence ring assembles | First render of the Circle header per visit | Avatars rise 8 dp and scale 0.6 → 1, 40 ms apart, after N1 settles | 250 ms each, `circles.settle` | Static |
| I2 | Cover reveal (no shared element, e.g. a deep link) | Open the Circle directly | The cover wipes down from the top (`clip` inset 100% → 0) | 420 ms, emphasizedDecelerate | Static |
| I3 | Circle created | Create | Three ripple rings expand from the mark, 140 ms apart, and fade to 25%. The mark settles 0.6 → 1. The headline and the invite card stagger in. | 1400 ms rings (once), 520 ms mark | Static |
| I4 | Caught up | The last deck card | The same three ripple rings around a tick. "You're caught up." | 1400 ms once | Static tick |
| I5 | Skeleton loading | Data pending more than 200 ms | Shimmer 1.4 s, **only while loading** and only after 200 ms (no flash for fast loads) | Linear | Static grey blocks |
| I6 | Catch-up deck next | Next or swipe | The top card leaves left with an 8° turn. The next rises from behind (scale 0.95 → 1, 12 dp). Pips stretch. | 300 ms accelerate + 250 ms decelerate | Crossfade |
| I7 | Composer type suggestion | Typing matches a rule | The suggestion banner rises 8 dp. The matching chip pulses its outline once (scale 1.06). | 250 ms + 700 ms (once per suggestion) | Banner appears, no pulse |
| I8 | Type-specific fields reveal | Choose a type | New fields stagger in 30 ms apart, rising 10 dp | 250 ms | Instant |
| I9 | Achievement celebration | First view of someone's achievement post, or Celebrate in chat | Confetti falls once from the top of the card area (34 pieces) | 1300 ms, **once per post per person** | None |

## 5. Interruption rules (global)

1. **The user always wins.** A gesture or tap during an animation retargets it from its current value (Compose `Animatable.animateTo`), never queueing.
2. **Navigation beats decoration.** Leaving a screen cancels its decorative effects at once; no completion is required.
3. **No double play.** One-shot effects are keyed (`postId + effect`) and recorded in memory per session. `circles.answer` and I9 are recorded in `LocalCircleStore` so they play once ever per person.
4. **State before motion.** The data change commits first and the animation is presentation only. If the app is killed mid-animation, nothing is lost or half-applied.

## 6. Battery and performance

- **No idle animation.** Nothing animates while the user isn't interacting, apart from C11 (the typing signal) and I5 (loading). Both are bound to real state.
- **Layer-only animation:** `graphicsLayer { translationY; scaleX; alpha }` with no layout passes. The exceptions are `animateContentSize` for P1, P3 and E7, and `animateItem` for list changes. Neither runs while flinging.
- **Shared elements** use `sharedBounds` with bitmaps (covers, avatars) rather than recomposing complex content in the overlay.
- **Generative covers** are drawn once and cached per id and size. They never animate.
- **Particles** (R1 sparks, I9 confetti) are at most 10 and 34 `drawCircle` and `drawRect` calls in one `Canvas` over a fixed duration, with no per-particle composables.
- **Haptics:** `CLOCK_TICK` on reactions and arc option changes, `CONFIRM` on RSVP Going and Mark answered. None on scroll.
- **Recomposition:** animated values are read in the draw phase (`drawBehind`, `graphicsLayer` lambdas), so frames don't recompose the post.
- **Budget:** at most 2 concurrent decorative animations per screen. The rest snap.

## 7. Motion lab mapping (canvas section M)

| Lab | Spec IDs |
|---|---|
| m1 Circle entrance | N1, I1 |
| m2 Tab change | N3 |
| m3 Post → discussion | N4, N5 |
| m4 Reaction burst | R1 |
| m5 Thread expansion | P3 |
| m6 Send and arrival | C1, C2 |
| m7 Upload, failure, retry | C4–C6 |
| m8 Poll reveal | §4.2 poll: bars 0 → value over 480 ms, decelerate; percentages fade after 180 ms |
| m9 Checklist | P7 |
| m10 RSVP seat | E1, E2 |
| m11 Gallery open | N9 |
| m12 Prayer answered | P8 |
| m13 Catch-up deck | I6 |
| m14 Composer → published | N6, P2 |
| m15 Card into chat | C10 |
| m16 Thumb arc | R2 |

The prototype uses CSS keyframes and the Web Animations API with the same values. Its **Slow ×5** switch multiplies every duration by 5, as Android's animator duration scale does. **Off** sets durations to effectively 0 and shows end states.
