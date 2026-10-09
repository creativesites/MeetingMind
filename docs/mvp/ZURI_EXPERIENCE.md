# Zuri: the companion experience (P-1 spec)

**Status:** Phase 2 spec, written for engineering. The visual direction was signed off from `zuri-options.html` (v2).
The founder still has to answer four questions; §12 gives a recommendation for each, and each answer is a small
switch in code, so nothing here blocks on them.

**Replaces:** MVP_PLAN §6 (the Mimi starting concept). Every "Mimi" or "companion" in MVP_PLAN, DESIGN_SYSTEM and
AUDIT now means **Zuri**.

**Read with:**

- `docs/mvp/AGENT_RULES.md`
- `docs/mvp/DESIGN_SYSTEM.md` (tokens `MM.*`, motion, the 6 accents)
- `docs/mvp/FAITH_V2.md` (Create studio, Circles, guided practices)
- `docs/mvp/MVP_PLAN.md` (P-1, P-2, P-3, P-5, P-6)
- `docs/mvp/zuri-options.html`, the live visual reference

**How to read this file:**

- §1–§2 cover who Zuri is and what it looks like.
- §3–§4 cover how it behaves.
- §5–§9 cover where it shows up, what it says and what it won't do.
- §10–§11 are the build.
- §12 lists the open decisions.

---

## Contents

1. [Who Zuri is](#1-who-zuri-is)
2. [Visual design](#2-visual-design)
3. [States and the state machine](#3-states-and-the-state-machine)
4. [Per-page modes and the 3D tiers](#4-per-page-modes-and-the-3d-tiers)
5. [The Zuri journey](#5-the-zuri-journey)
6. [Voice and copy](#6-voice-and-copy)
7. [Personalization](#7-personalization)
8. [Ask Zuri: the assistant role](#8-ask-zuri-the-assistant-role)
9. [Retention, done ethically](#9-retention-done-ethically)
10. [Compose implementation](#10-compose-implementation)
11. [Build plan](#11-build-plan)
12. [Open decisions (founder)](#12-open-decisions-founder)
13. [Smaller open questions](#13-smaller-open-questions)

---

## 1. Who Zuri is

### 1.1 Essence

> **Zuri is the small, warm presence that listens with you, and quietly keeps what matters.**

"Zuri" is Swahili for *beautiful, good*. It is a short word in every language, has no faith-specific meaning and
is easy to say. It is the **family name and the brand name**, and the default form is called Zuri. Users can rename
their companion.

### 1.2 The family: "Zuri and friends"

A user picks one **form**. Every form runs on the same rig, states, rules and voice; only the drawing differs.

| Form | One line | Default? | Character note |
|---|---|---|---|
| **Zuri** (the Orb) | The record button, awake. | **Yes, pre-selected** | A soft sphere with a three-bar "sound sprout" on its head that moves with your voice. It is the brand mark. |
| **Nas** (the puppy) | A calm little puppy, glad you're here. | — | Floppy ears that lift with sound, a head tilt when listening, a soft patch over one eye, and a slow wag only at real peaks. |
| **Wren** (the songbird) | A small songbird who keeps what matters. | — | Round body and cocked tail. Head tilt to listen, wing flick to celebrate. Deliberately not a dove. |
| **Page** (the folio) | Your note, come to life. | — | A folded page with a ribbon. Its lines become your waveform, and it writes while thinking. |
| *No companion* | Just the app, quietly. | — | Every status still shows as plain text, and the record button stays a button. |

**Why Zuri (the Orb) is the default:**

- It shares the brand name.
- It belongs to the record button's family (the existing `RecordOrb`).
- It is the most legible at 24 dp.
- It is the most neutral across faiths and cultures.

### 1.3 Character bible

**Personality:**

- Attentive, unhurried and warm.
- Quietly glad, never excited for the sake of it.
- A little shy: it lets you speak first.
- It enjoys small things done well: a tidy note, a remembered promise, a finished week.

**Values:**

- **Your words are yours.** Zuri keeps them, never twists them, and never adds to them.
- **Honesty over cheerfulness.** When something fails, Zuri says so and offers the fix.
- **Calm over attention.** Zuri would rather be missed than be in the way.
- **Respect for what's sacred.** In worship and prayer, Zuri goes quiet.

**What Zuri loves:**

- The moment a note is ready.
- A recording with clear voices.
- Mornings with a devotional.
- A promise kept.
- Being called by a name you chose.

**What Zuri would never do:**

- Pray *for* you, interpret scripture or act as a spiritual authority.
- Appear inside your notes, transcripts, scripture text or other people's posts.
- Guilt-trip, sulk, beg or say it "misses you".
- Pretend work happened, invent a quote or fake progress.
- Interrupt a recording, a sermon or a prayer.
- Sell you anything. Zuri never appears on paywalls.
- Celebrate on a prayer request, a grief moment or Good Friday.

**Relationship to the user:** a companion, not a coach or a pet that needs feeding. Zuri needs nothing from you.

### 1.4 Why Zuri fits a private "remember what matters" app

MeetingMind's principle is *"A recording is the source. The Note is the workspace."* Zuri is the face of the
source: it is literally the thing that listened.

That gives three wins:

1. **Labor illusion.** Processing takes minutes. The same character that listened is visibly working, with stages
   narrated from real pipeline events, so the wait reads as care.
2. **Peak-end.** "Your note is ready" becomes a small, designed peak.
3. **Privacy as warmth.** A companion who keeps your words is the emotional version of "private by design".
   Copy must be honest about online vs on-device processing (D2).

---

## 2. Visual design

### 2.1 Construction (all forms)

Every form is drawn in a **100 × 100 unit box** with Compose `Canvas` primitives: `drawCircle`, `drawOval`,
`drawRoundRect`, and short `Path`s with quadratic curves. There are no bitmaps and no vector assets per state.

The geometry below is the reference; the live versions are in `zuri-options.html` (`DRAW.orb`, `DRAW.nas`,
`DRAW.wren`, `DRAW.folio`).

| Form | Primitives (100-unit box) | Pivots (animated) |
|---|---|---|
| **Zuri** | Body: circle c(50,58) r30. Sprout: 3 round rects at x 43.5/50/56.5, w 4.4, height 3–16, base y 30, behind the body. Eyes: ellipses at (41,56) and (59,56). Cheeks at (33.5,65) and (66.5,65). Mouth at (50,65). | Sprout bar heights; sprout lean around (50,30); body squash around the foot (50,88) |
| **Nas** | Head: circle c(50,42) r21. Body: oval c(50,77) 35×27. Ears: ovals c(30.5,44) and (69.5,44), 15×29. Snout: oval c(50,51) 19×14. Nose: oval at (50,47.6). Eye patch: oval at (59,40), 28% alpha. Paws: ovals at (42.5,89) and (57.5,89). Tail: round rect 16×6.5. | Ears around (33,31)/(67,31); head tilt around (50,60); tail around (64,76) |
| **Wren** | Body: circle c(49,60) r25. Head: circle c(62,40) r15.5. Tail, wing and beak: 3 quadratic paths. Eye at (68,37). Belly: oval at (57,69). Legs: two strokes. | Tail around (34,54); wing around (40,57); head around (58,50) |
| **Page** | Page: one path, a rounded rect 28..72 × 20..82 with a folded top-right corner (fold size F = 12–17). Ribbon path. Eyes at (42,40) and (56,40). Three text lines at y 58/65/72. | Fold size; lean around (50,84); lines become a polyline waveform |

**Shared face system:** every form uses the same face primitives, which is what makes them read as one family.

- **Eyes:** ovals (rx ≈ 3–3.3, ry ≈ 3.8–4.6) with a white catch-light. Variants: open, soft (60% height), happy
  (∩ arc), closed (∪ arc).
- **Cheeks:** a blush oval (`companionBlush`), hidden in Worried.
- **Mouth:** a small smile arc. Variants: open (with tongue), "o", wobble, none.
- **Brows:** two short strokes, used only in Worried.
- **Hands/paws:** two small folded ovals, used only in **Prayerful**.

### 2.2 Colour: Zuri wears the user's accent

Zuri derives every colour from the theme's `MM.colors.accent`, which is already resolved for light and dark.
**Semantic colours never apply** to Zuri, so Zuri never turns red when something fails.

The prototype used 7 accents; this spec reconciles them to the design system's **6**: Indigo, Ocean, Forest, Gold,
Rose and Graphite. `zuri-options.html` has been updated to match.

| Role | Light theme | Dark theme |
|---|---|---|
| `bodyTop` (gradient start) | lerp(accent, white, 0.50) | lerp(accent, white, 0.22) |
| `bodyBottom` | lerp(accent, white, 0.12) | lerp(accent, background, 0.10) |
| `deep` (sprout, ears, wing, tail) | lerp(accent, ink, 0.25) | lerp(accent, background, 0.42) |
| `light` (belly, snout, fold) | lerp(accent, white, 0.80) | lerp(accent, white, 0.62) |
| `rim` (Tier 1 rim light) | lerp(accent, white, 0.55) | lerp(accent, white, 0.60) |
| `paper` (Page only) | lerp(accent, white, 0.95) | lerp(accent, #EEF0F5, 0.90) (stays light) |
| `eye` | `companionInk` (fixed slate-900 class, the same in both themes) | same |
| `blush` | `companionBlush` (rose, 34% alpha) | same |
| `sparkle` | accent / `gold` (alternating) | lerp(accent, white, 0.25) / `gold` |
| `shadow` | ink at 9% | black at 40% |

**New tokens.** These are missing from DESIGN_SYSTEM and are added by task Z-3, in `ui/theme`, so the colour-literal
guard stays green:

- `companionInk`
- `companionBlush`
- a pure function `companionPalette(colors: MMColors): CompanionPalette`

**Checks:**

- Every accent × theme pair is covered by a golden screenshot.
- The **Graphite** accent gives a grey Zuri. That is intended: the blush and catch-lights keep it warm.
- Eye contrast against `bodyTop` must be ≥ 4.5:1 for every accent and theme (unit test).

### 2.3 Light and dark

- **Light (Paper):** a soft ground shadow (ink at 9%) and an accent halo at 20% behind the body, at 64 dp and up.
- **Dark (Graphite):** a halo at 32% and a darker shadow. Bodies use the dark accent value, which is already pale.
  The Page stays light paper in both themes.

### 2.4 Sizes, level of detail, readability

| Size | Use | Level of detail (LOD) | Tier (§4.2) |
|---|---|---|---|
| **24 dp** | Inline rows, chips, the badge on the record button | **LOD-0:** silhouette + eyes (+ Nas ears and nose). No cheeks, mouth, catch-lights, lines, belly or patch. Eye size ×1.3. | T0 |
| **40 dp** | Home header (Work), Ask header, quick-sheet picks | Full face | T0 |
| **56–64 dp** | Home header (Everyday, Faith, Study), Create cards, empty states | Full | T1 |
| **96–120 dp** | Recording, processing, note ready | Full + volume layer | T1 |
| **160 dp** | Onboarding hero, the first-recording peak | Full + volume + gyro parallax | T2-eligible |

The LOD cut-off is **< 32 dp**. The readability test is that at 24 dp, on both themes and all 6 accents, an
internal tester names the form and state (open/closed eyes) at arm's length.

### 2.5 The neutral glyph

Outside the app (notifications, the launcher shortcut, a future widget) Zuri appears only as the **sprout glyph**:
three rounded bars, the middle one tallest, as a monochrome `VectorDrawable` (`ic_zuri_glyph`). This holds whatever
form the user chose, so the brand stays consistent where people see it most.

### 2.6 Per-vertical touches (the same character, no costumes)

| Space | Touch | Rule |
|---|---|---|
| Faith | Warm gold rim (when the Gold accent or the Faith space default is active), all timings ×1.3, no hops | Never a halo ring above the head, a cross, or praying hands outside Prayerful |
| Work | Smaller (40 dp, T0), no cheeks, timings ×0.8 | Never on decisions or tasks |
| Study | "Curious" lean-in during quizzes | Never sad at a wrong answer |
| Everyday | The user's accent, standard timings | — |

**Seasonal touches** (Advanced → Zuri → "Seasonal touches", default on, Phase C):

- Advent/Christmas: a tiny knitted scarf in `deep`.
- Spring/Easter Sunday: one small leaf on the sprout (Wren: in the beak; Nas: on the collar line).
- **Good Friday:** no celebrations at all. Every celebrating moment becomes Peaceful.
- No bunny ears, no Santa hats, nothing that cheapens worship.

---

## 3. States and the state machine

### 3.1 App states

**MVP (P-2):** Idle, Listening, Thinking, Celebrating, Worried, Sleepy.
**Phase B:** Curious, Proud, Reading.

For each state below:

- **Motion** values are in the 100-unit box.
- **Durations** follow the system animator duration scale.
- **"Static"** is the reduced-motion pose, also used for golden tests.

#### Idle

| | |
|---|---|
| **Trigger** | The default base state on Home, quick sheet and Settings preview |
| **Pose / face** | Eyes open with catch-lights, small smile, sprout heights 5/8/5, Nas ears at 12° |
| **Motion** | Breathing: scaleY 1 ± 0.022, scaleX 1 ∓ 0.012, sine, period 3.4 s, pivot at the foot. Blink: 160 ms every 4.6 s ± 0.8 s jitter. **Home budget:** one breath + one blink on arrival, then still. It resumes for one cycle on return to Home or on scroll-to-top. |
| **Static** | Breath at rest, eyes open |
| **In / out** | Crossfade 150 ms (`MM.motion.quick`) + pose lerp 250 ms (`standard`) |

#### Listening

| | |
|---|---|
| **Trigger** | `RecordingStarted` (any non-sermon recording; see Quiet below) |
| **Pose / face** | Soft eyes (60%), tilt −5°, looking slightly toward the sound |
| **Motion** | **Driven by mic level L ∈ [0,1].** Source: `AudioRecorder.amplitude`, smoothed with 60 ms attack and 250 ms release, read in the draw phase only. scaleY 1 + 0.035 L. Tilt −5° ± 2° (sine, 4.8 s). Zuri: sprout bar heights 3.5 + L·(7 + 5 sin(9t + i·1.9)). Nas: ears +16° · L. Wren: tail flick 10° · L. Page: lines become a waveform with amplitude 3.4 L. Ripple rings: two rings, period 1.5 s, alpha (0.18 + 0.55 L). |
| **Static** | L = 0.55 pose, one ring, tilt −5° |
| **In / out** | In: 250 ms tilt-in. Out: on `RecordingStopped`, to Thinking with a 250 ms pose lerp. |
| **Quiet variant** | Sermons, and presence Big moments while in church: see §3.4. Pose is Peaceful, L ignored, no rings, 56 dp. |

#### Thinking

| | |
|---|---|
| **Trigger** | `ProcessingStage` ∈ {PREPARING_AUDIO … SAVING_RESULTS} (S-2's real stages) |
| **Pose / face** | Looking up and right (+1.1, −1.5), mouth "o" |
| **Motion** | Sway ±3° (sine, 4.2 s). Three dots above, bouncing 3.2 units, staggered 170 ms, 1.5 s loop. Blink every 3.1 s. Zuri's sprout pulses sequentially. Page **writes**: its lines grow at 16 units/s with a blinking caret. Runs at 30 fps (§10.8). |
| **Static** | Middle dot raised; Page shows 1.6 lines written |
| **In / out** | Stage changes do **not** restart the animation; only the caption changes (§5.4). Out to Celebrating (`NoteReady`) or Worried (`ProcessingFailed`). |

#### Celebrating (one-shot)

| | |
|---|---|
| **Trigger** | `NoteReady` (the full version once a day; otherwise the "nod" variant), first-recording peak, Retry succeeded |
| **Pose / face** | Happy eyes (∩), open smile, 4 sparkles (accent and gold alternating) |
| **Motion** | 1.6 s total. Anticipation squash 0–120 ms (sy 0.94). Hop 120–620 ms (y −9, sy 1.05, `EaseOut`). Land squash 620–800 ms (sx 1.08, sy 0.92). Settle with `MM.motion.spring` until 1.6 s. Sparkles twinkle 0–1.4 s, then fade. **Nas:** hop ×0.5 plus a wag at 7 Hz, decaying over 1.2 s. **Wren:** wing flap ×2. **Nod variant:** 600 ms, a single 3-unit dip, no sparkles. |
| **Static** | Mid-hop pose (y −5, sy 1.04) with sparkles placed |
| **In / out** | Returns to the previous base state (usually Idle) |
| **Never** | On prayer requests, in grief-tagged notes, during Faith quiet, or on Good Friday (it becomes Peaceful) |

#### Worried

| | |
|---|---|
| **Trigger** | `ProcessingFailed`, `ModelDownloadFailed`, `AiFallback` (the honest fallback line), `PermissionDenied` while recording |
| **Pose / face** | Droop: sx 1.03, sy 0.95, y +1.5. Brows raised on the inner ends, wobbly mouth, eyes 0.88×, looking down. Zuri's sprout leans 14°, Nas's ears 2°, Wren's tail −26°, Page's fold grows to 17. No cheeks. |
| **Motion** | Enter 250 ms. Slow sway ±1.5° (7 s). Blink every 3.8 s. |
| **Static** | Droop pose |
| **In / out** | **Always rendered next to a `StatusLine` with a fix** (Retry, Open settings, Download). The composable refuses to show Worried without an `action` (§10.3). On `RetryTapped`: Thinking, then Celebrating (nod) or back to Worried. |

#### Sleepy

| | |
|---|---|
| **Trigger** | `ModelsMissing` (offline mode chosen but models not downloaded), or Home opened between 23:00 and 05:00 local time |
| **Pose / face** | Closed eyes (∪), no mouth, tilt 6°, y +2. Zuri's sprout lean 14°, Nas's ears 4° and head 8°. |
| **Motion** | Breathing 5.5 s ± 2.5%. "z"s rise and fade over 2.6 s, **3 cycles maximum, then static.** |
| **Static** | Two z's placed |
| **In / out** | Out to Idle on any interaction, 250 ms |
| **Copy** | Never shaming ("you're up late"). See §6. |

#### Curious (Phase B, P-3)

| | |
|---|---|
| **Trigger** | Each onboarding question; a Study quiz question |
| **Pose / face** | Head tilt 8°, lean in 4 units, eyes 1.1× |
| **Motion** | One-shot 600 ms on each new question, then hold |
| **Static** | Tilted pose |

#### Proud (Phase B, P-5)

| | |
|---|---|
| **Trigger** | `Milestone`: first note, 10th note, finished a reading plan, 7 devotionals in a calendar month, all due cards cleared. Never a loss-based streak. |
| **Pose / face** | Chest up (sy 1.04), happy eyes, one gold glint sparkle |
| **Motion** | One-shot 1.2 s, then Idle |
| **Static** | Chest-up pose with glint |
| **Cap** | Once per milestone, ever |

#### Reading (Phase B, F-7 read-aloud)

| | |
|---|---|
| **Trigger** | `ReadAloudStarted` |
| **Pose / face** | Eyes glance left → right per sentence (2.4 s or the TTS sentence duration). The mouth (Zuri: the sprout; Nas: the ears, subtly) follows TTS amplitude. |
| **Motion** | 30 fps while playing; stops on pause |
| **Static** | Eyes centred, mouth closed |

### 3.2 Create modes (poses for shared cards only)

These six poses exist only on **Create** cards (FAITH_V2 §3) and in the Create preview. They are never app states.

| Mode | Pose | Motion (preview only; the export is static) | Allowed on |
|---|---|---|---|
| **Prayerful** | Eyes closed, no mouth, hands/paws folded (two small ovals at the chest; Nas: under the chin; Wren: wing folded in front and head bowed), sprout bowed 10° | Breath 6 s ± 0.8%, nothing else | Everything |
| **Peaceful** | Eyes closed, soft smile | Breath 6 s ± 1.5% | Everything |
| **Grateful** | Happy eyes, smile, 4° bow, a small heart | Heart float ±1.5 | Not on prayer requests |
| **Joyful** | Happy eyes, open smile, 2 sparkles | Bob 3 units at 2.6 rad/s | Not on prayer requests, scripture or sermon quotes |
| **Reflective** | Looking up-left, smile | Blink | Not on prayer requests |
| **Celebratory** | = Celebrating static pose | The full hop (preview only) | Not on prayer requests, scripture or sermon quotes |

**Create rules (settled in v2):**

1. **Off by default on every card.** "Include {companion}" is a switch in the Create Design step. The Advanced
   setting "Suggest {companion} on cards" (default off) can pre-enable the switch for Achievement and Study sources only.
2. **The AI suggests a mode; the user chooses.** The suggestion picks a pose and never writes or alters any text.
   The mode chips show the suggestion as "Suggested: Peaceful".
3. **Restrictions are enforced in code**, by `CreateModePolicy.allowed(source, vibe)`, not by the model.

**Suggestion table:** the source wins over the vibe.

| Source / vibe (FAITH_V2 §3) | Suggested mode | Allowed modes |
|---|---|---|
| **Prayer request** | Prayerful | **Prayerful, Peaceful only** |
| Scripture verse, sermon quote, devotional | Peaceful (vibe Reflective → Reflective) | Prayerful, Peaceful, Grateful, Reflective |
| Answered prayer, testimony | Grateful | All except Joyful-on-grief (grief tag → Peaceful only) |
| Achievement, study win | Celebratory | All |
| Vibe Encouraging / Love | Grateful | All (subject to source) |
| Vibe Motivational / Funny | Joyful | All (subject to source) |
| Vibe Wisdom / Reflective | Reflective | All (subject to source) |
| Vibe Celebration | Celebratory | All (subject to source) |
| Custom / unknown | Peaceful | All (subject to source) |

**Export.** The companion is drawn into the card bitmap at export density in one of 4 corners, with a 16 dp safe
margin, at 64–96 dp scaled to the format. It is never over the text block.

### 3.3 The state machine: events in, state out

One `CompanionMachine` per app process holds a **base state** plus at most **one one-shot overlay**. Screens send
events; screens don't set states.

**Events** (all are real app events):

| Event | Source |
|---|---|
| `ScreenShown(page: CompanionPage)` | Navigation |
| `RecordingStarted(kind: RecordingKind, space: Space)` | Recording service |
| `RecordingStopped` | Recording service |
| `ProcessingStageChanged(stage: ProcessingStage, detail: StageDetail?)` | `MeetingProcessingPipeline` (S-2) |
| `ProcessingFailed(reason, fix: FixAction)` | Pipeline |
| `NoteReady(noteId, isFirstEver: Boolean)` | Pipeline |
| `RetryTapped` | `StatusLine` |
| `ModelsMissing` / `ModelsReady` | Model manager |
| `ReadAloudStarted` / `ReadAloudStopped` | F-7 |
| `Milestone(kind)` | `MomentLedger` (§10.6) |
| `OnboardingQuestion(step)` | P-3 |
| `QuizQuestion` | Study |
| `ClockTick(localTime)` | Once per Home entry (late-night check) |
| `PresenceChanged(presence)`, `HiddenUntil(time)`, `FormChanged(form)` | Settings and quick sheet |

**Resolution order** (the highest active item wins):

1. Worried (an unresolved failure with a fix)
2. The one-shot overlay (Celebrating, Proud, Curious, nod)
3. Listening (while recording; or Quiet listening)
4. Thinking (while processing)
5. Reading
6. Sleepy
7. Idle

**Presence filter** (applied after resolution, per page):

| Page / moment | Around (default) | Big moments | Off / hidden / No companion |
|---|---|---|---|
| Home greeting | ✓ | — | — |
| Recording (non-sermon) | ✓ | ✓ | — |
| Recording (sermon) | Quiet | Quiet | — |
| Processing | ✓ | ✓ | — |
| Note ready / first recording | ✓ | ✓ | — |
| Worried + fix | ✓ | ✓ | — (the `StatusLine` still shows) |
| Empty states | ✓ | — | — (EmptyState with no illustration) |
| Milestones (Proud) | ✓ | ✓ | — |
| Read-aloud | ✓ | — | — |
| Onboarding, quick sheet, Companion settings | always (the user is choosing) | always | always |

When the filter removes Zuri, **every message still renders as text**. Zuri is never the only carrier of
information.

### 3.4 Quiet during sermons (settled)

- **Default on** for recordings where `kind == SERMON`, or where the recording starts in the Faith space with the
  Sermon type (see §12 open item for wider scope).
- Quiet pose: Peaceful, 56 dp, no level reaction, no rings, no one-shots until the recording stops.
- **Local override:** a chip on the recording screen, "Quiet during sermon · tap to change", switches *this
  recording* to Listening. The next sermon starts quiet again.
- **Global setting:** Settings → Advanced → {companion} → "Quiet during sermons".

---

## 4. Per-page modes and the 3D tiers

### 4.1 Per-page modes

| Page | Mode | What Zuri does | Motion budget |
|---|---|---|---|
| Home | Greeting | One breath + blink on arrival, then still. Sleepy at night. | T1 about 3 s, then 0 fps |
| Recording | Listening | Follows the mic. Head tilt when speech resumes after more than 3 s of silence. | T1, 60 fps while visible (the one continuous animation) |
| Processing | Thinking | Narrated real stages (§5.4) | T1, 30 fps |
| Note ready | Celebrating | One hop or nod, then settles beside the note title | T1 (T2 candidate), 1.6 s |
| Faith (all pages) | Gentle | Timings ×1.3, amplitudes ×0.7, no hops, quiet in sermons | T1 |
| Work | Crisp | 40 dp T0 on Home; appears for the brief and review (Phase C) | T0–T1 |
| Study | Curious | Leans in on quiz questions; a nod on a correct answer, calm on a wrong one | T1 one-shots |
| Read-aloud | Reading | Eyes follow the line; mouth/sprout follow TTS | T1, 30 fps |

### 4.2 The 3D tiers (settled; tier chosen automatically by size)

| Tier | Size | Technique | Status |
|---|---|---|---|
| **T0** | < 48 dp | Flat vector. No idle animation; state changes crossfade only (150 ms). | MVP |
| **T1** | 48–143 dp | Flat + a **volume layer**, clipped to the silhouette: a radial shading field (white 26% → clear → black 28% at the far edge), a soft specular oval, a rim stroke (`rim`, bottom-right), and contact AO (a second, tighter shadow). Parallax from touch and scroll offset (±1.2 units). | MVP (the volume layer is behind `companion.volume` in Phase A and on by default after the perf check, Z-14) |
| **T2** | ≥ 144 dp, hero moments only | T1 + gyro parallax (`TYPE_GAME_ROTATION_VECTOR`, about 30 Hz, registered only while the hero is visible). **Rive is an evaluation spike only** (Z-19): it is adopted only if it beats T1 on feel *and* fits the size budget. | T1 fallback is the default |

**Not adopted:** pre-rendered 3D in the app (store art and marketing only), and real-time 3D (Filament/SceneView) at
any tier. The reasons are in `zuri-options.html` §3D: APK size, shader warm-up and battery.

---

## 5. The Zuri journey

Every touchpoint must pass the "Why is Zuri here?" test. The **cap** is the hard frequency limit, enforced by
`MomentLedger`.

### 5.1 First launch and onboarding (P-3: 5 steps, progress from step 1)

| Step | Purpose | Zuri state | Copy | Cap |
|---|---|---|---|---|
| 1 · **Pick who keeps you company** | Bond and ownership before anything is asked; a smart default | Preview 112 dp: Idle; Celebrating (nod) on each pick. Picks at 44 dp, from the roster (§10.7), Zuri pre-selected, plus "No companion". | "Pick who keeps you company" / "They listen when you record, keep you posted while your note is written, and cheer when it's ready. Never inside your notes." / "You can change this any time in Settings, or just long-press your companion." | Once |
| 2 · What do you want to remember? (spaces) | Personal setup; the home preview updates | Curious (Phase B; Idle in Phase A) at 64 dp | "What do you want to remember?" | Once |
| 3 · Your name | Personal | Curious | "What should {companion} call you?" | Once |
| 4 · AI choice (smart default, honest) | Trust | Idle | "Fastest results use Google's Gemini online. Prefer everything on your phone? Choose On-device." | Once |
| 5 · Say hello (a 10 s test recording → mini note) | Reciprocity + **first peak** | Listening (live mic) → Thinking → **Celebrating (full)** | "Say hello. Tell {companion} one thing you want to remember this week." → "Here's your first note." | Once |

**Progress:** step 1 shows 1 of 5, already filled (goal-gradient).
**Permissions** are asked in context at step 5 ("{companion} needs the microphone to listen").

### 5.2 The first-recording peak (P-5)

- **When:** the first real (non-onboarding) note is ready.
- **What:** a full-screen moment with Zuri at 160 dp (T2-eligible) Celebrating, the note title, and one line:
  "Your first note is ready. Everything you said, kept." Two actions: **Open note** (primary) and Share a moment
  (secondary, opens Create).
- **Cap:** once ever. It is skippable, and it never returns.

### 5.3 The four homes (`HomeHeader` leading slot)

| Home | Size / tier | Greets with | Never |
|---|---|---|---|
| Everyday | 56 dp T1 | "Good morning, {name}." State: Idle; Sleepy late at night; Thinking while a note processes; a nod once when one finishes. | Over the hero |
| Faith | 56 dp T1, gentle | "Good morning, {name}. Today's Word is ready." | On the scripture hero, the prayer list or Circles sections |
| Work | 40 dp T0 | "Good morning, {name}." In Phase C, a pre-meeting brief entry: "Brief me for 3:00". | On tasks, decisions or Pulse |
| Study | 56 dp T1, curious | "{n} cards are due. A quick round?" | Sad faces on missed reviews |

**Long-press** on the header companion opens the quick sheet (§7.3).
**Tap:** a small "hi" (a nod), at most once per 10 s. It never navigates.

### 5.4 Recording → processing → note ready

**Recording screen:** Listening at 96 dp (Quiet: 56 dp), above the timer and waveform. Copy: none, because the
timer is the hero. TalkBack: "{companion} is listening".

**Processing:** labor illusion narrated from real stages. The caption changes **only** on a real
`ProcessingStageChanged`, never on a timer.

| `ProcessingStage` | Caption (Everyday) | Faith variant | Work variant | Study variant |
|---|---|---|---|---|
| PREPARING_AUDIO | "Getting the audio ready…" | — | — | — |
| DETECTING_SPEECH | "Finding where people speak…" | "Finding the sermon in the recording…" | — | "Finding the lecture…" |
| TRANSCRIBING | "Writing down every word… {done} of {total} min" | — | — | — |
| DIARIZING | "Listening for who's speaking… {k} voices so far" | — | "Working out who said what…" | — |
| CLEANING_TRANSCRIPT | "Tidying the transcript…" | — | — | — |
| ANALYZING | "Finding what matters…" | "Finding the scripture and main points…" | "Pulling out decisions and action items…" | "Picking out what the lecturer stressed…" |
| SAVING_RESULTS | "Putting your note together…" | — | — | — |
| COMPLETED | → Note ready | | | |
| FAILED | → Worried + fix (§5.6) | | | |
| CANCELLED | Idle: "Stopped. Your recording is safe." | | | |

If a detail value is unknown, the clause is dropped. **Never fabricate a number.**

**Note ready:**

- In-app snackbar: "{Note title} is ready." with an Open action. Zuri nods at 40 dp in the snackbar leading slot.
- The full Celebrating version plays once per day; later notes get the nod.
- Faith copy: "Your sermon notes are ready 🙏". The emoji is allowed only here and in the notification.

### 5.5 Empty states (`EmptyState` illustration slot)

| Screen | State | Copy |
|---|---|---|
| Notes (no notes yet) | Idle, 96 dp | "Nothing here yet. Record something, and {companion} will keep it." Action: **Record** |
| Search, no results | *No Zuri* (a text-only EmptyState) | "No matches for "{q}"." |
| Tasks (all done) | Proud nod (Phase B) / Idle | "Nothing waiting on you." |
| Study, no cards due | Idle | "All caught up. Nice work." |
| Faith prayer list | **No Zuri** (sacred) | Plain text |

### 5.6 Errors and honest fallbacks

Zuri is Worried only when there is a fix. The `StatusLine` carries the message; Zuri sits in its leading slot.

| Failure | Copy | Fix |
|---|---|---|
| Processing failed | "That one didn't finish. Your recording is safe." | **Retry** |
| AI online failed, fell back to on-device | "Couldn't reach Gemini, so this note was made on your phone." | **Redo online** |
| Models missing (offline) | Sleepy: "{companion} needs its on-device models to work offline." | **Download (420 MB)** |
| Mic permission denied | "{companion} can't hear without the microphone." | **Open settings** |
| No storage | "Your phone is out of space, so recording stopped. What's recorded is saved." | **Manage storage** |

### 5.7 Rhythms (calm, never loss-based)

- **Devotional rhythm (Faith):** "3 mornings with the Word this week." Shown on Faith Home as a quiet line, with Zuri
  as Proud at month milestones. **No streak counter that resets, ever.**
- **Study:** "You reviewed 4 days this week." Proud when all due cards are cleared.
- **Weekly review (Work, Phase C, opt-in):** Friday 16:00. Zuri at 40 dp on the review card.

### 5.8 Notifications and widget

- Notifications use **`ic_zuri_glyph` only**, never a character drawing.
- Text uses the user's companion name.
- **Cap:** at most one companion-voiced notification per day, none in quiet hours (22:00–07:00 by default), and none
  during a Faith recording.

| Notification | Copy |
|---|---|
| Note ready (if the app is in the background) | "{Note title} is ready." |
| Sermon notes ready | "Your sermon notes are ready 🙏" |
| Model download done | "{companion} can now work offline." |
| *Never* | "{companion} misses you", "Don't break your streak", any guilt |

**Widget:** Phase C idea ("a widget that sleeps"), not MVP.

### 5.9 Read-aloud, Create, Circles, seasonal

| Touchpoint | Purpose | State | Copy | Cap |
|---|---|---|---|---|
| Read-aloud mini-player | A sense of being read to | Reading, 40 dp | — (the player has its controls) | While playing |
| Create (Design step) | User-chosen decoration | A Create mode (§3.2) | "Include {companion}" · "Suggested: Peaceful" | Per card; off by default |
| Circles | — | **Never in feeds or posts.** Appears only inside a Create card that a member chose to share. | — | — |
| Achievement suggestion (FAITH_V2 §2.2) | Celebrate privately first | Proud on your own screen | "You finished the reading plan. Share it with your circle?" | Once per achievement; never auto-posts |
| Seasonal | Warmth | Touches (§2.6) | — | Season-long, Advanced toggle |

### 5.10 Where Zuri must never appear, and why

| Never in | Why |
|---|---|
| Inside notes, transcripts, the block editor, scripture text, the Bible reader | "The Note is the workspace." It is the user's content. |
| Prayer list, prayer requests, guided Prayer, Bible study steps | Sacred moments; Zuri goes quiet. |
| Circle feeds, other people's posts and comments | Other people's content. |
| Paywalls, upgrade prompts, pricing | Zuri never sells. |
| Settings (except Settings → Companion) | No value there. |
| Search results, lists, chips | No value; choice overload. |
| App lock screen, permission system dialogs, legal/privacy screens | Trust surfaces must be neutral. |
| Over FABs, the hero, or any content | Von Restorff: one distinct element. |
| Destructive confirmations ("Delete note?") | No emotional pressure on a decision. |
| Notes or recordings tagged grief/funeral/memorial | Celebration would be cruel. Nothing celebrates; it stays Peaceful. |
| Notification art and the launcher icon | The glyph only (§2.5). |
| Shared content (Create cards) unless the user switched it on | §3.2 rule 1. |

---

## 6. Voice and copy

### 6.1 Style guide

- **Who speaks.** The **app** speaks status ("Putting your note together…"). **{companion}** speaks in the first
  person only at bonding moments: the onboarding hello, peaks, and Ask answers. The default name is "Zuri".
- **Short.** One line, 60 characters or fewer where possible. Sentence case. Plain verbs.
- **Warm, not cute.** No baby talk, no "yay!!", at most one exclamation mark per screen, and emoji only where listed.
- **Honest.** Say what happened, what's safe, and what to do. Never claim work that didn't happen.
- **The user's name** ({name}) appears at most once per screen and never in error copy. It is used in greetings and
  first peaks only.
- **The companion's name** appears in copy as the user's chosen name. Store, system and notification-channel names
  say "Zuri".
- **Faith.** Joyful but reverent. Zuri never says "God told you", "I'll pray for you", "Amen" or "Bless you", and
  never paraphrases scripture. Scripture text comes only from the Bible provider (faith contract).
- **Failures.** Lead with what's safe ("Your recording is safe"), then the fix. Never blame the user. Never "Oops".

### 6.2 Example lines (36)

**Onboarding:**

1. "Pick who keeps you company."
2. "Hi, I'm {companion}. I'll listen when you record and keep what matters."
3. "What do you want to remember?"
4. "What should I call you?"
5. "Nice to meet you, {name}."
6. "Say hello. Tell me one thing you want to remember this week."
7. "Here's your first note. Everything you said, kept."

**Home:**

8. "Good morning, {name}."
9. "Good evening, {name}. Your notes from today are ready."
10. "Today's Word is ready." (Faith)
11. "3 cards are due. A quick round?" (Study)
12. "It's late, so take it easy. Your notes will keep." (Sleepy)

**Recording and processing:**

13. "{companion} is listening." (TalkBack only)
14. "Quiet during sermon · tap to change"
15. "Getting the audio ready…"
16. "Listening for who's speaking… 3 voices so far"
17. "Writing down every word… 12 of 48 min"
18. "Finding the scripture and main points…"
19. "Pulling out decisions and action items…"
20. "Picking out what the lecturer stressed…"
21. "Putting your note together…"

**Peaks:**

22. "Your note is ready."
23. "Your sermon notes are ready 🙏"
24. "Ten notes kept. That's a lot of remembering."
25. "You finished the reading plan. Share it with your circle?"
26. "All caught up. Nice work."

**Failures:**

27. "That one didn't finish. Your recording is safe." [Retry]
28. "Couldn't reach Gemini, so this note was made on your phone." [Redo online]
29. "I need my on-device models to work offline." [Download]
30. "I can't hear without the microphone." [Open settings]
31. "Stopped. Your recording is safe."

**Create:**

32. "Include {companion}"
33. "Suggested: Prayerful. On prayer requests, only Prayerful or Peaceful."

**Ask {companion}:**

34. "From your Acme sync on 12 Sep: you promised a revised quote by Friday. It's still open."
35. "I couldn't find that in your notes. Want me to search the transcripts too?"
36. "Answered on your phone. Nothing left it."

**Quick sheet:**

37. "Hidden until tomorrow morning. Long-press the greeting to bring {companion} back."

---

## 7. Personalization

### 7.1 Settings (part of the F-6 `Personalization` model)

| Setting | Values | Default | Where |
|---|---|---|---|
| `companion.form` | ZURI · NAS · WREN · PAGE · NONE (only forms in the roster are offered) | ZURI | Onboarding step 1 · Settings → Companion · quick sheet |
| `companion.name` | Text, 1–14 chars, per form | The form's display name (§12.1) | Quick sheet · Settings → Companion |
| `companion.presence` | AROUND · MOMENTS ("Big moments") · OFF | AROUND (Faith-first users: AROUND, with the sermon quiet default) | Quick sheet · Settings → Companion |
| `companion.hiddenUntil` | Timestamp? | null | Quick sheet "Hide for now" (until the next 07:00) |
| `companion.onRecordButton` | Boolean | **false** | Settings → Advanced → Companion |
| `companion.quietSermons` | Boolean | **true** | Settings → Advanced → Companion · local chip |
| `companion.createSuggest` | Boolean | false | Settings → Advanced → Companion |
| `companion.seasonal` | Boolean | true | Settings → Advanced → Companion (Phase C) |
| `companion.volume` (T1 shading) | Boolean | true after Z-14 | Internal flag, not user-facing |

**Basic vs Advanced.** Basic has three choices: form, name, presence. Everything else is under Advanced
(choice overload).

### 7.2 Personal touches derived from existing choices

These need no extra settings:

- Accent → Zuri's colour.
- The space's home → per-vertical touches (§2.6).
- {name} → greetings.
- Goals (P-3 step 2) → which empty states and rhythms appear.

### 7.3 The long-press quick sheet

**Opened by:** a long-press (with haptic) on the companion anywhere it appears, or the TalkBack custom action
"{companion} options".

**Contents** (`MM` Sheet surface):

1. **Form row:** the roster's forms at 36 dp, as radio buttons. Selecting one gives the new form a nod.
2. **Presence:** a segmented control, Around / Moments / Off.
3. **Name:** an inline text field. It saves on done or close.
4. **Hide for now:** hides the companion until the next 07:00. Shows "Hidden until tomorrow morning". After that,
   the greeting shows a small text button "Show {companion}".
5. Footer: "More in Settings → Companion".

### 7.4 Record button

- **The record button stays a plain `RecordOrb` by default** (four questions: it must read as Record).
- Advanced "**{companion} on the record button**":
  - With ZURI, the orb shows Zuri's face and sprout in place of the mic glyph. Still labelled "Record" for TalkBack.
  - With NAS, WREN or PAGE, the orb keeps the mic glyph and gains a **24 dp LOD-0 badge** of the form at its top
    right.
  - With NONE, the toggle is disabled.

---

## 8. Ask Zuri: the assistant role

### 8.1 Companion vs assistant

**Zuri is pulled, not pushed.** The character stays small. The assistant appears only when asked (Ask {companion},
voice) or at moments the user scheduled (Phase C briefs and the weekly review).

In an answer, Zuri is a 40 dp avatar in the header, Thinking while working. **The answer is the hero.** Zuri never
writes into a note unless the user taps "Add to note" (the existing confirm/undo `AssistantAction` flow).

### 8.2 Built on what exists

`feature/assistant` (`AssistantSession`, `LibraryAssistantViewModel`, `AssistantSheet`) already runs library-wide
and in-note conversations, with confirmable and undoable actions. **Ask Zuri is that assistant, re-skinned and
held to these rules.** It is not a new pipeline.

### 8.3 Rules

- **Sources or silence.** Every factual answer carries ≥ 1 source chip (note + timestamp) that opens the moment in
  the audio. With no source, the answer is "I couldn't find that in your notes."
- **Provenance on every answer.**
  - "Answered on your phone. Nothing left it."
  - "Answered online (Gemini) from {n} notes. Only those snippets were sent."
  - Spaces marked on-device only (prayer list, Circles) are never sent online.
- **Faith contract.** Ask retrieves the user's notes and the pastor's quoted words, with attribution. It never
  interprets scripture, never gives spiritual direction and never prays for the user. Scripture text comes from the
  Bible provider. A question like "What does God want me to do?" gets a gentle redirect to the user's own notes and
  their community.
- **Tier gate:** a single flag (§12.3).

### 8.4 Scope

| Capability | Scope | Data | Where it runs |
|---|---|---|---|
| **Ask {companion} across all notes** with citations | **MVP** (Z-17) | Notes, transcripts | On-device retrieval + local model; Online for synthesis |
| "What did I commit to this week?" (a canned prompt over action items + transcripts) | **MVP** (Z-17 suggestion) | Action items, transcripts | On-device |
| Quiz me (Study) | MVP via V-1 (the Study spec owns it); Zuri is the avatar | Study notes | Online / on-device |
| Draft the follow-up email | Exists in Work; adopt the avatar only | Note | Online |
| Pre-meeting brief | **Phase C** | Calendar + notes with the same participants | Online |
| Chase my action items | Phase C | Action items | On-device |
| Weekly review | Phase C (opt-in) | The week's notes | Online |
| Devotional companion ("what you noted last time on this passage") | Phase C (V-0c hook) | Faith notes | On-device |
| Prayer list follow-up ("Mark answered?") | Phase C | The user's prayer list | On-device; user's own words only |
| Voice conversation | Later | On-device ASR | On-device |
| People memory | Later (privacy review) | Participants | On-device only |
| **Live in-meeting whisper** | **Excluded from MVP**, behind `liveWhisper = false`, no code (§12.4) | — | — |

---

## 9. Retention, done ethically

| Hook | How | Why it's respectful |
|---|---|---|
| **Ownership** (IKEA effect) | Choose a form and name it in onboarding step 1 | The user's choice; changeable any time; "No companion" is a first-class option |
| **Reciprocity** | The say-hello test produces a real mini note before we ask for anything more | Value first |
| **Goal-gradient** | Onboarding starts at 1 of 5, filled | Truthful progress |
| **Peak-end** | The first-recording peak; note-ready nods | Celebrates *their* work, once, skippable |
| **Labor illusion** | Narrated **real** stages | Never a fake timer or percentage |
| **Emotional intelligence** | Worried only with a fix; Sleepy is gentle | Honest emotion, not manipulation |
| **Calm rhythms** | "3 mornings this week" | Counts what happened; nothing is lost |
| **Proud milestones** | Once per milestone | Private; sharing is a suggestion only |

**We deliberately won't:**

- Use streak counters that reset, or "don't break your streak" messages.
- Show a sad or crying companion after an absence, or send "{companion} misses you" notifications.
- Show guilt copy of any kind ("You haven't prayed today").
- Put Zuri on paywalls, or lock forms behind Pro. All forms are free.
- Hide a needed status behind the companion (it is never the only carrier).
- Use spiritual metrics, leaderboards, or prayer counts as scores.
- Run idle animations designed to grab attention.
- Fake progress, "AI magic" pauses, or invented quotes.
- Auto-post anything to Circles or share anywhere without an explicit tap.
- Infer emotion from the user's voice (see "Mood weather" in the options page: probably never).

---

## 10. Compose implementation

### 10.1 Where it lives

```
ui/theme/CompanionColors.kt             companionInk, companionBlush, companionPalette(MMColors)  (Z-3)
core/companion/                          pure Kotlin, unit-tested, no Compose
    CompanionModel.kt                    CompanionForm, CompanionState, CreateMode, Presence, CompanionPage
    CompanionEvent.kt                    the events of §3.3
    CompanionMachine.kt                  reducer + presence filter + MomentLedger hooks
    CompanionRoster.kt                   which forms ship (flag), display names
    CompanionFlags.kt                    askGate, liveWhisper, volumeLayer, rive
    MomentLedger.kt                      frequency caps (DataStore)
    CreateModePolicy.kt                  suggestion + allowed modes
core/ui/mm/companion/                    Compose
    Companion.kt                         the composable (§10.2)
    Pose.kt, PoseMath.kt                 Pose data + lerp
    MotionSpec.kt, ZuriMotion.kt         motion as data
    forms/OrbDrawer.kt, NasDrawer.kt, WrenDrawer.kt, PageDrawer.kt
    VolumeLayer.kt                       T1 shading
    CompanionQuickSheet.kt
    ZuriSlot.kt                          presence-aware wrapper for app screens
feature/settings/companion/              Settings → Companion (+ Advanced section)
```

This plan writes against the **F-2 contract** (branch `mvp/f2-design-system`, not yet pushed):

- the `MM` token access object (`MM.colors`, `MM.type`, `MM.space`, `MM.radius`, `MM.motion`)
- `HomeHeader(greeting, subtitle, leading = { … }, actions)`
- `EmptyState(illustration = { … }, title, body, action)`

If a signature lands differently, adapt the call sites; **don't** add new tokens outside `ui/theme`.

### 10.2 Public API

```kotlin
/** Draws one companion form in one state. Stateless; animation-only state lives inside. */
@Composable
fun Companion(
    form: CompanionForm,
    state: CompanionVisual,               // CompanionState or CreateMode (sealed)
    size: Dp,
    modifier: Modifier = Modifier,
    level: () -> Float = { 0f },           // mic or TTS amplitude, read in the draw phase only
    variant: CompanionVariant = CompanionVariant.Normal, // Normal | Quiet | Nod
    tier: CompanionTier = CompanionTier.forSize(size),
    contentDescription: String? = null,    // null = decorative (clearAndSetSemantics)
)

/** App screens use this: it reads settings + machine, applies presence, and handles long-press. */
@Composable
fun ZuriSlot(
    page: CompanionPage,
    size: Dp,
    modifier: Modifier = Modifier,
    level: () -> Float = { 0f },
)   // renders nothing when presence/hidden/NONE filters it out

sealed interface CompanionVisual
enum class CompanionState : CompanionVisual { IDLE, LISTENING, THINKING, CELEBRATING, WORRIED, SLEEPY, CURIOUS, PROUD, READING }
enum class CreateMode : CompanionVisual { PRAYERFUL, PEACEFUL, GRATEFUL, JOYFUL, REFLECTIVE, CELEBRATORY }
enum class CompanionForm { ZURI, NAS, WREN, PAGE }      // NONE is a setting, not a form
enum class CompanionTier { T0, T1, T2; companion object { fun forSize(s: Dp) = when { s < 48.dp -> T0; s < 144.dp -> T1; else -> T2 } } }
```

**`ZuriSlot` call sites:**

- `HomeHeader(leading = { ZuriSlot(CompanionPage.HOME, 56.dp) })`
- `EmptyState(illustration = { ZuriSlot(CompanionPage.EMPTY, 96.dp) }, …)`
- The recording screen: `ZuriSlot(RECORDING, 96.dp, level = { amp.value })`

`ZuriSlot` returns nothing when filtered, and the host layouts must collapse that space cleanly.

### 10.3 State machine model

```kotlin
data class CompanionUiState(
    val base: CompanionState,                 // IDLE / LISTENING / THINKING / READING / SLEEPY / WORRIED
    val oneShot: OneShot?,                    // CELEBRATING / PROUD / CURIOUS / NOD, with startedAt
    val variant: CompanionVariant,            // Quiet during sermons
    val caption: CaptionKey?,                 // processing caption (§5.4), rendered by the host as text
    val fix: FixAction?,                      // required when base == WORRIED
)

class CompanionMachine(
    private val settings: Flow<CompanionSettings>,
    private val ledger: MomentLedger,
    private val clock: Clock,
) {
    val state: StateFlow<CompanionUiState>
    fun onEvent(e: CompanionEvent)
    fun visibleOn(page: CompanionPage, s: CompanionUiState, cfg: CompanionSettings): Boolean   // presence table §3.3
}
```

- `reduce(state, event, cfg, now)` is a **pure function** and is unit-tested exhaustively. Every row of §3.3, the
  presence table and the caps gets a test.
- **Invariant:** `base == WORRIED ⇒ fix != null`. `Companion()` in WORRIED with no host `StatusLine` is a debug
  assertion.
- Singleton scope: the app-level DI container. Events come from the recording service, the pipeline (S-2) and
  navigation.

### 10.4 Poses as data

```kotlin
@Immutable data class Pose(
    val dy: Float = 0f, val sx: Float = 1f, val sy: Float = 1f, val tiltDeg: Float = 0f,
    val eyes: Eyes = Eyes.OPEN, val eyeScale: Float = 1f, val lookX: Float = 0f, val lookY: Float = 0f,
    val blink: Float = 0f, val mouth: Mouth = Mouth.SMILE, val brows: Boolean = false, val hands: Boolean = false,
    val hop: Float = 0f,
    // form-specific channels (ignored by forms that don't have them)
    val sprout: Triple<Float, Float, Float> = Triple(5f, 8f, 5f), val sproutLean: Float = 0f,
    val ear: Float = 12f, val headTilt: Float = 0f, val headDy: Float = 0f, val tail: Float = 0f,
    val wing: Float = 0f, val fold: Float = 12f, val lines: LinesMode = LinesMode.STATIC,
    val fx: Fx = Fx.NONE,                     // SPARKLES, DOTS, ZZZ, HEART, RINGS
)
fun lerp(a: Pose, b: Pose, t: Float): Pose  // enums switch at t = 0.5
```

`StaticPoses` holds the reduced-motion pose for each `CompanionVisual` × form. These are exactly the `rm` branches
in the HTML reference.

### 10.5 Motion spec as data

```kotlin
sealed interface Timing { data class Loop(val periodMs: Int, val maxCycles: Int? = null) : Timing
                          data class OneShot(val durationMs: Int) : Timing
                          data object Driven : Timing }           // listening/reading: level-driven
data class Keyframe(val at: Float, val value: Float, val easing: Easing = FastOutSlowInEasing)
data class Track(val channel: PoseChannel, val keys: List<Keyframe>)
data class MotionSpec(val visual: CompanionVisual, val timing: Timing, val tracks: List<Track>,
                      val enterMs: Int = 250, val exitMs: Int = 250, val fps: Int = 60)
object ZuriMotion { val specs: Map<CompanionVisual, MotionSpec>; val formOverrides: Map<Pair<CompanionForm, CompanionVisual>, MotionSpec>;
                   val spaceScale: Map<Space, Float> /* FAITH 1.3, WORK 0.8 */ }
```

The numbers in §3.1 are the initial values. They are tuned in **Companion Lab**, a debug-only screen (Z-6) with
sliders over `ZuriMotion`, so motion can change without code archaeology.

### 10.6 Drawing

- **One `Canvas` per instance.** Drawers implement `fun DrawScope.draw(form: Pose, p: CompanionPalette, lod: Lod)`.
- **Paths are pre-allocated** in a `remember`ed holder and reset each frame. There are **zero allocations per
  frame** (a test asserts this via an allocation counter in Companion Lab, and it is reviewed).
- **Animation clock:** `withFrameNanos` in a `LaunchedEffect`. It runs only when the active `MotionSpec` needs time
  (Loop, OneShot or Driven) **and** the slot is visible. Visibility means the lifecycle is ≥ STARTED and the slot is
  on screen (`onGloballyPositioned` + window bounds).
- **Duration scale:** `Settings.Global.ANIMATOR_DURATION_SCALE` is read once per composition (the existing pattern
  in `HomeHeroHeader.kt`).
  - Scale 0, or "Remove animations", → `StaticPoses`, with no clock at all.
  - Other scales multiply every duration.
- **T1 volume layer:**
  - `clipPath(silhouette)`, then a radial gradient field, a specular oval and a rim stroke with a linear gradient.
  - Parallax input comes from `LocalCompanionParallax`: scroll and touch in T1; gyro in T2 heroes only, registered
    while visible, at about 30 Hz.
- **Mic level:** `AudioRecorder.amplitude` (a `StateFlow<Float>`), smoothed by `LevelSmoother` (attack 60 ms,
  release 250 ms). It is passed as a lambda and read inside `drawBehind`, so recomposition never happens per frame.

### 10.7 Roster and flags (the founder's switches)

```kotlin
object CompanionRoster {
    /** Forms offered in onboarding, the quick sheet and Settings. Order = display order. */
    val enabled: List<CompanionForm> = BuildConfig.COMPANION_FORMS.split(',').map { CompanionForm.valueOf(it) }
    fun displayName(f: CompanionForm): Int = when (f) { ZURI -> R.string.form_zuri; NAS -> R.string.form_nas; WREN -> R.string.form_wren; PAGE -> R.string.form_page }
}
object CompanionFlags {
    val askGate: AskZuriGate = AskZuriGate.FREE_LOCAL_PRO_ONLINE   // §12.3
    const val liveWhisper = false                                   // §12.4: no code ships behind it in MVP
    val volumeLayer = true; val riveHero = false
}
```

- `COMPANION_FORMS` defaults to `ZURI,NAS` (§12.2). It is set in `app/build.gradle.kts`.
- If a user's stored form is not in the roster (for example after a roster change), it falls back to ZURI with no
  prompt.

### 10.8 Performance budgets

| Item | Budget |
|---|---|
| Draw time per instance, T1, mid-range (Pixel 4a class) | ≤ 0.5 ms |
| Allocations per frame | 0 |
| Simultaneous animating instances | ≤ 2 (e.g. the onboarding preview + one pick nod) |
| Listening | 60 fps; Thinking and Reading at 30 fps (frame-skip clock) |
| Home idle | Clock stops after about 3 s |
| Off-screen | No clock, no sensor listeners |
| APK size | +0 KB (no libraries) unless the Rive spike is approved |

### 10.9 Accessibility

- **Decorative by default.** The adjacent text (caption, `StatusLine`, title) carries the meaning, and the companion
  uses `clearAndSetSemantics {}`.
- **Interactive slots** (the Home header, the quick-sheet target) have role Button with the label "{companion}",
  `onLongClickLabel = "{companion} options"`, and a custom accessibility action.
- **Picker items** (onboarding, quick sheet) are radio buttons with the label "{form name}, {one line}".
- The 48 dp touch target applies even when the drawing is 24–40 dp.
- Reduced motion → static poses (§10.6). Never rely on motion alone: Celebrating always comes with a text line.

### 10.10 Testing

**Golden screenshots (Roborazzi, already in the build).** One parameterised test, `CompanionGoldenTest`, over:

> roster forms × (6 MVP states + 6 Create modes) × {Paper, Graphite} × {24 dp, 96 dp}

using static poses and the Indigo accent. That is **48 images per form**, plus 6 accents × 2 themes on the Idle pose
at 64 dp (12 per form). Phase B adds Curious, Proud and Reading (+12 per form).

**Unit tests:**

- Reducer (all §3.3 rows)
- Presence filter
- `MomentLedger` caps
- `CreateModePolicy` (prayer request → only Prayerful and Peaceful)
- Palette contrast (eye vs body ≥ 4.5:1, all accents and themes)
- `LevelSmoother`
- `CompanionTier.forSize`

**Screen goldens:** the Home header with a companion (each home), EmptyState with and without a companion, the
quick sheet, onboarding step 1, the recording screen (normal and Quiet), and processing.

---

## 11. Build plan

**Conventions:**

- Every task follows AGENT_RULES.md and is one engineer, one sitting, one commit.
- **Sonnet:** multi-file work with judgement. **Haiku:** fully specified, mechanical work.
- **Size:** **S** is under about 2 hours of agent work; **M** is one sitting.
- "Goldens" means Roborazzi images for each listed state in Paper and Graphite, committed with the task.

### Phase A: MVP (Zuri + Nas, 6 states, placements)

| ID | Title | Model | Size | Maps to | Depends | Acceptance criteria | Screenshot tests |
|---|---|---|---|---|---|---|---|
| **Z-0** | This spec + founder sign-off | Opus + founder | — | **P-1** | — | Founder signs off; MVP_PLAN §6 points here | — |
| **Z-1** | `core/companion` model, events, pure reducer, presence filter, roster + flags | Sonnet | M | **P-2** | — | All §3.3 rules, the presence table and the WORRIED ⇒ fix invariant are unit-tested; no Android deps in the reducer | — (unit tests) |
| **Z-2** | `MomentLedger` (DataStore caps) + `CreateModePolicy` | Haiku | S | **P-2** / P-5 | Z-1 | Caps from §5 are enforced with tests; the policy matrix of §3.2 is tested row by row (prayer request → {PRAYERFUL, PEACEFUL}) | — |
| **Z-3** | Companion tokens + palette: `companionInk`, `companionBlush`, `companionPalette(MMColors)` in `ui/theme` | Haiku | S | **P-2** | F-2 contract | Formulas from §2.2; contrast unit test for 6 accents × 2 themes passes; `ColorLiteralGuardTest` green | — |
| **Z-4** | `Pose`, `lerp`, `StaticPoses`, `MotionSpec`/`ZuriMotion` data (6 MVP states), the frame clock, duration-scale handling, `LevelSmoother` | Sonnet | M | **P-2** | Z-1 | Lerp and keyframe tests; scale 0 → no clock; smoother attack/release tests | — |
| **Z-5** | `Companion()` shell + `OrbDrawer` (Zuri) + LOD-0 + `CompanionGoldenTest` harness | Sonnet | M | **P-2** | Z-3, Z-4 | API exactly as §10.2; 0 allocations per frame (debug counter); clock paused off-screen (test with an `onGloballyPositioned` stub); semantics per §10.9 | **Zuri:** 6 states + 6 Create modes × Paper/Graphite × 24/96 dp (48); Idle × 6 accents × 2 themes @64 dp (12) |
| **Z-6** | Companion Lab debug screen (sliders over `ZuriMotion`, mic-level slider, form/state/tier pickers) | Haiku | S | **P-2** | Z-5 | Debug builds only; not reachable in release (test) | — |
| **Z-7** | `NasDrawer` (ears, head tilt, tail wag, patch, folded paws) | Sonnet | M | **P-2** | Z-5 | Matches the §2.1 geometry; Nas celebrating = hop ×0.5 + wag | **Nas:** the same 60 images as Z-5 |
| **Z-8** | Listening wiring: `AudioRecorder.amplitude` → `LevelSmoother` → recording screen `ZuriSlot`; Quiet during sermons + local chip | Sonnet | M | **P-2** | Z-5, Z-1 | 60 fps while recording (frame-time check on a mid-range device or emulator profile); sermon recordings start Quiet; the chip toggles only this recording | Recording screen: Listening and Quiet × Paper/Graphite |
| **Z-9** | `ZuriSlot` + placements: `HomeHeader` leading slot (4 homes, sizes per §5.3), `EmptyState` illustration slot (Notes, Tasks, Study), note-ready snackbar nod | Sonnet | M | **P-2** / U-1 | Z-5, F-2 | Presence filter honoured; empty space collapses when filtered; Home clock stops after ~3 s | Each home header × Paper/Graphite; EmptyState with/without companion |
| **Z-10** | Processing: Thinking + stage captions from `ProcessingStage` (per space, §5.4); Worried + `StatusLine` fixes (§5.6) | Haiku | S | **P-6** | Z-9, S-2 | Captions change only on stage events (test with fake pipeline events); no number shown when detail is null | Processing (Thinking) and Failed (Worried + Retry) × Paper/Graphite |
| **Z-11** | Settings → Companion (Basic: form, name, presence) + Advanced (record button, sermon quiet, Create suggest) in the F-6 model | Sonnet | M | **P-2** / F-6 | Z-1, F-6 | Defaults per §7.1; roster-filtered forms; stored form not in the roster → ZURI | Settings screen × Paper/Graphite |
| **Z-12** | `CompanionQuickSheet` (long-press anywhere) + "Hide for now" + TalkBack custom action | Sonnet | M | **P-2** | Z-9, Z-11 | Long-press works on every `ZuriSlot`; hide until 07:00; rename persists | Sheet × Paper/Graphite, Zuri and Nas selected |
| **Z-13** | Record button option: faceless default; Zuri face or form badge when enabled; `ic_zuri_glyph` vector + notification small icon | Haiku | S | **P-2** | Z-5, Z-11 | Default unchanged ("Record" content description); toggle per §7.4; notifications use the glyph | Bottom bar: off / Zuri / Nas badge × Paper/Graphite |
| **Z-14** | T1 volume layer + touch/scroll parallax + perf check (budget §10.8) | Sonnet | M | **P-2** | Z-5, Z-7 | ≤ 0.5 ms per instance (benchmark or trace); flag `volumeLayer` is the kill switch | Idle, Listening, Celebrating with volume × Paper/Graphite (both forms) |
| **Z-15** | Onboarding step 1, "Pick who keeps you company" (roster picks + No companion + preview + nod) and step 5 say-hello states | Sonnet | M | **P-3** | Z-11, Z-8 | Zuri pre-selected; No companion works end to end; the choice is persisted; under 90 s total for P-3 | Step 1 with Zuri, Nas, None × Paper/Graphite |
| **Z-16** | Peak moments: the first-recording peak screen (160 dp, once ever) + daily full celebrate vs nod | Sonnet | M | **P-5** | Z-2, Z-9 | Caps via `MomentLedger`; skippable; never on grief-tagged notes; Good Friday → Peaceful | Peak screen × Paper/Graphite × Zuri/Nas |
| **Z-17** | Ask {companion}: re-skin `AssistantSheet` (avatar Thinking/Idle, "Ask {name}"), provenance line per answer, sources-or-"couldn't find", tier gate flag | Sonnet | M | **New (A-1)** | Z-5, Z-11 | Every factual answer has a source chip or the not-found line (tests with a fake host); gate flag respected; faith contract prompts unchanged | Ask sheet: on-device answer, online answer, not-found × Paper/Graphite |
| **Z-18** | Companion strings: every line in §5–§6 into `strings.xml` with `{companion}`/`{name}` placeholders, no hard-coded copy | Haiku | S | **P-2** | — | Lint: no hard-coded companion strings; placeholders tested | — |

### Phase B: after the MVP companion ships

| ID | Title | Model | Size | Maps to | Depends | Acceptance criteria | Screenshot tests |
|---|---|---|---|---|---|---|---|
| **Z-19** | **Rive evaluation spike** (Tier 2 hero only): rebuild Zuri's Celebrating in Rive with inputs (state, level), measure APK delta per ABI, cold-start, frame time, battery over 2 min | Sonnet | S (time-boxed) | P-2 | Z-14 | **Report only**, no merge to main: a recommendation with numbers | — |
| **Z-20** | States Curious, Proud and Reading + motion data + drawer support (Zuri, Nas) | Sonnet | M | **P-3 / P-5** | Z-7 | Triggers per §3.1; caps for Proud | 3 states × 2 forms × Paper/Graphite × 24/96 (24) |
| **Z-21** | Calm rhythms: devotional/study weekly counts on Faith and Study homes + Proud milestones | Sonnet | S | **P-5** | Z-20 | No resetting counters anywhere (test); Proud once per milestone | Faith home rhythm line, Study home × Paper/Graphite |
| **Z-22** | Create integration: "Include {companion}" (off by default), suggestion chips, `CreateModePolicy` enforcement, export draw into the card bitmap | Sonnet | M | **V-0a / P-5** | Z-2, V-0a | Prayer request offers only Prayerful/Peaceful; text never altered; export at 9:16, 1:1 and 4:5 | Create Design step: quote, prayer request, achievement × Paper/Graphite |
| **Z-23** | `WrenDrawer` (if in the roster) | Sonnet | M | P-2 | Z-5 | §2.1 geometry | Wren: 60 images |
| **Z-24** | `PageDrawer` (if in the roster): waveform lines and writing lines | Sonnet | M | P-2 | Z-5 | §2.1 geometry | Page: 60 images |

### Phase C: ideas with a spec to come

These have no task IDs until specced:

- Pre-meeting brief
- Chase action items
- Weekly review
- Devotional companion
- Prayer list follow-up
- Seasonal touches
- The sleeping widget

Live whisper is **not in any phase** until §12.4 is approved.

**Order:**

1. Z-1 → Z-3/Z-4 (in parallel) → Z-5
2. Then Z-6, Z-7, Z-8, Z-9, Z-11, Z-18 in parallel
3. Then Z-10, Z-12, Z-13, Z-14
4. Then Z-15, Z-16, Z-17

---

## 12. Open decisions (founder)

**Not yet answered. Nothing blocks on them.** Each has a recommendation and a one-line switch.

### 12.1 The name "Nas"

**Context:**

- "Nas" is a well-known rapper's name.
- In Arabic, *an-Nās* ("mankind") is the title of the Qur'an's final surah.
- Separately, some Muslim households consider dogs unclean.

**Recommendation:**

- **Keep "Nas"** for the launch build: it is short, warm and mostly benign.
- Ask 5 testers in the next closed-testing round, including any in Muslim-majority regions.
- Keep a Swahili-family fallback ready to match Zuri: **"Rafiki"** ("friend"). Note the *Lion King* association.
- Users can rename their companion anyway.

**Switch:** `R.string.form_nas` (one string). Stored data uses the enum `NAS`, not the name, so nothing migrates.

### 12.2 Launch with all 4 forms, or Zuri + Nas first?

**Recommendation:** **Zuri + Nas at launch.** Wren and Page follow as Phase B fast follows (Z-23/Z-24).

- That halves the drawing, tuning and golden work in the launch window.
- It gives a real choice: a brand mark and a lovable creature.
- It keeps onboarding step 1 to two forms plus None, which helps with choice overload.

**Switch:** `COMPANION_FORMS` in `app/build.gradle.kts`.

- Launch: `"ZURI,NAS"`
- Later: `"ZURI,NAS,WREN,PAGE"`

### 12.3 Ask Zuri: Free or Pro?

**Recommendation:** **`FREE_LOCAL_PRO_ONLINE`.**

- Free gets Ask with on-device retrieval and the local model (citations, shorter answers).
- Pro adds online synthesis: drafts, briefs, longer answers.
- This matches D2 (local = Free, Internet = Pro) and gives value before asking (reciprocity).
- It makes Ask the natural upgrade moment without gating the basic promise "find what I said".

**Switch:** `CompanionFlags.askGate`. One enum, three values:

- `FREE_LOCAL_PRO_ONLINE` (recommended)
- `PRO_ONLY`
- `ALL_ONLINE`

The UI shows the provenance line either way.

### 12.4 Live in-meeting whisper

**Recommendation:** **Excluded from MVP. No code ships.**

- Recording-consent laws vary.
- There is social trust with the other people in the room.
- Low-end phones would pay in latency and battery.

Revisit after launch with a legal review and an explicit design: headphones only, on-device only, opt-in per meeting.

**Switch:** `CompanionFlags.liveWhisper = false`. The capability does not exist in MVP scope, so it can't be turned
on by accident.

---

## 13. Smaller open questions

1. **Sermon scope.** Does "Quiet during sermons" cover only recordings of type Sermon (the spec default), or every
   Faith-space recording?
2. **Nas pronoun.** Copy currently uses "he" for Nas and "it" for Zuri, Wren and Page. Is that OK, or should all
   forms use "it"?
3. **Achievement suggestions.** When a Circle exists, do achievement suggestions offer "Share with your circle"
   (FAITH_V2 §2.2) from the Proud moment itself, or only from the achievement card?
4. **Create default suggestion.** Should "Suggest {companion} on cards" stay off (the spec default), or default on
   for Study wins?
5. **Rive budget.** If Z-19 shows Rive is clearly better, what is the APK budget ceiling (MB per ABI)?
