# Zuri's signature moments: the First Keep, the Unveil and the Card

**Status:** design spec for engineering, ready to build. It extends `ZURI_EXPERIENCE.md` (§5.2, §5.4, §3.2) and
replaces the "full Celebrating once a day, nod otherwise" rule for note-ready with the Unveil system below.
**Prototype:** `docs/mvp/zuri-moments.html`. Open it, press Play, and scrub. Every timestamp in this file is
in that page.

**Read with:** `ZURI_EXPERIENCE.md` (character bible, states, caps, voice), `ZURI_RIVE_BRIEF.md` (Rive contract),
`FAITH_V2.md` §3 (Create), `DESIGN_SYSTEM.md` §6 (motion tokens), `AGENT_RULES.md`.

| # | Moment | When | Budget | Tier |
|---|---|---|---|---|
| 1 | **The First Keep** | The first note ever is ready | 3.6 s, skippable | T1 Canvas, T2 Rive hero (160 dp) as a stretch |
| 2 | **The Unveil** | The first time any later note is opened | 1.2–1.8 s, or a 0.6 s nod | T1 Canvas (64 dp) |
| 3 | **The Card** | "Include {companion}" on a Create card | 1.5 s reveal; the export is a still | T1 Canvas; the export is always Canvas |

> **The story in one line.** Zuri *listens* (Listening), *keeps* (Thinking), then *gives it back* (the
> Unveil), and steps aside so the note is the hero. The first time, the giving-back is a small ceremony.
> Later it's a quick, varied handover. On a card, Zuri is a guest the user invited.

---

## Contents

0. [Principles for every moment](#0-principles-for-every-moment)
1. [What the app does today (grounding)](#1-what-the-app-does-today-grounding)
2. [Moment 1: the First Keep](#2-moment-1-the-first-keep)
3. [Moment 2: the Unveil system](#3-moment-2-the-unveil-system)
4. [Special cases](#4-special-cases)
5. [Moment 3: the Create card with Zuri](#5-moment-3-the-create-card-with-zuri)
6. [The "alive" contract](#6-the-alive-contract)
7. [Implementation plan](#7-implementation-plan)
8. [Frequency caps and ethics](#8-frequency-caps-and-ethics)
9. [Copy](#9-copy)
10. [Build tasks](#10-build-tasks)
11. [Ideas (not commitments)](#11-ideas-not-commitments)
12. [Decisions (founder, 2026-10-12)](#12-decisions-founder-2026-10-12)

---

## 0. Principles for every moment

1. **The note is the hero; Zuri hands it over.** Zuri appears, gives the note, and **steps back**: it ends
   off-stage or small. It may share the note screen during a designed moment (founder decision 2026-10-12: user
   experience comes first), but it never covers, edits or replaces the user's own words, and it never stays on
   the note once the moment ends (§5.10).
2. **Never blocking.** The note renders underneath from frame 0. Any tap skips to the end state, and the tap
   still reaches what it landed on. Back skips too. The Share and Open actions work from the first frame.
3. **Text carries the meaning.** Each moment makes one polite announcement and has a static end state. Motion is
   decoration on top of information that already exists.
4. **Restraint is a ladder.** First Keep (once ever) > Unveil (rationed) > Nod > Plain. Anything uncertain
   steps *down* the ladder, never up (§3.3).
5. **One signature gesture per form.** Every moment has a beat called **Signature**. Each form fills it with its
   own gesture, so a form change feels like a different personality, not a recolour:

| Form | Signature gesture | What it says |
|---|---|---|
| **Zuri** (orb) | The sprout acts as a hand: its three bars stretch (10/16/10), lean, tick or glow | "I'm the thing that listened" |
| **Nas** | Ears lift, then a short wag that decays (7 Hz → 0 over 1.2 s), and a head tilt toward the note | "I kept it for you" (loyal, proud) |
| **Wren** | Wing flick or sweep, tail cocked; it can *perch* on things | "Here, light and quick" |
| **Page** | The fold corner flips, and its own lines *become* the note's lines | "Your note, come to life", in reverse |

6. **Same beats, any renderer.** Beats are data (`MomentSpec`). Canvas and Rive both follow the app's moment
   clock, so haptics and the note's own motion line up whichever renderer draws Zuri.
7. **Space sets the energy.** Faith is gentle (×1.15 time, ×0.7 amplitude, no hops), Work is crisp (×0.85),
   and Study is playful. Everyday is the reference.

---

## 1. What the app does today (grounding)

These facts come from the code on this branch. The plan below is built around them.

| Fact | Where | Consequence for the design |
|---|---|---|
| The pipeline reports real stages: `PREPARING_AUDIO → DETECTING_SPEECH → TRANSCRIBING → DIARIZING → CLEANING_TRANSCRIPT → ANALYZING → SAVING_RESULTS → COMPLETED / FAILED / CANCELLED` | `core/model/Enums.kt`, `MeetingProcessingWorker` progress keys | Thinking stays as specified. The moment starts on the WorkManager `SUCCEEDED` (not on `SAVING_RESULTS`), so we never celebrate early. |
| On `SUCCEEDED`, `ProcessingViewModel` calls `onComplete`. `MainActivity` then **navigates immediately** to `MeetingDetail`, or to `WrapUp` for Work recordings with findings | `feature/processing/ProcessingScreen.kt`, `MainActivity.kt:1089` | The 1.6 s hop in `ProcessingCompanionBlock` is cut off by navigation today. **Moments therefore play on the destination screen**, as its entrance, not on the processing screen. This avoids touching `MainActivity` (a hot file). |
| The user can **Minimise** processing. The worker posts `processingFinished` with "Transcript ready" and a system icon | `ProcessingScreen.kt` `onMinimise`, `core/notify/AppNotifications.kt:342` | Background completion is common. A note is unveiled **the first time it is opened**, whatever path led there (§3.2). |
| `CompanionEvent.NoteReady(noteId, isFirstEver, sensitive)` exists and the reducer handles caps, but **no feature code emits it** | `core/companion/CompanionEvent.kt`, `CompanionMachine.kt` | Wiring it is part of Z-16. |
| `MomentLedger` caps: `FirstRecordingPeak` once ever, `FullCelebration` once a day, `TapHi` 10 s, `CompanionNotification` once a day outside quiet hours | `core/companion/MomentLedger.kt` | They are reused. One new cap kind (`DailyBudget`) is added for the Unveil (§8). |
| Create: `CreateFormat` STORY 1080×1920, SQUARE 1080×1080, PORTRAIT 1080×1350. Safe fractions: story top 14% / bottom 18% / side 9%; portrait 8/8/9; square 9/9/9. The companion is drawn at **20% of the width, below the text, above the foot**, centred or start-aligned with the text | `core/create/CreateModels.kt`, `CreateCardRenderer.kt` | Placement (§5.2) keeps this as the default anchor and adds one corner anchor. |
| The card pose is `CreateVibePolicy.companionMode(source, vibe)`, so the pose follows the vibe and the user has **no separate pose override**. The bitmap comes from `renderCompanionBitmap(form, mode, palette, 360)` (Canvas, static) | `feature/create/CreateStudio.kt` | Z-22 adds the override chips. The export stays a static Canvas bitmap. |
| Motion data exists: `MotionSpec`, `Track`, `Keyframe`, `PoseChannel {DY, SX, SY, TILT, HOP, EYE_SCALE}`, `ZuriMotion.spaceScale` (Faith 1.3, Work 0.8) | `core/ui/mm/companion/CompanionMotion.kt` | `MomentSpec` reuses `Keyframe`/easing and adds stage targets. Moments use their own space scale (×1.15 / ×0.85) so every Unveil stays within 1.2–1.8 s. |
| The Rive contract has `state`, `level`, `mode`, `celebrate`, `nod`, `calm`, plus optional `blink`, `lookX`, `lookY` | `rive/CompanionRiveContract.kt`, `ZURI_RIVE_BRIEF.md` §3 | The new inputs are **optional**. A file without them still works, and its moments play on Canvas (§7.6). |

---

## 2. Moment 1: the First Keep

### 2.1 Purpose and feeling

**The feeling:** *"Oh, it actually kept everything. And it's glad to give it to me."* A small, warm gasp, not
fireworks.

**Psychology:**

- **Peak-end rule.** The first recording is the moment the product's promise ("Remember what matters") is
  proven. A designed peak here sets how people remember the whole onboarding.
- **Labor illusion → reward.** The user has just watched real stages ("Writing down every word… 12 of 48 min").
  The First Keep is the visible payoff of that work: the same character that listened and thought now hands over
  the result.
- **Reciprocity.** Value arrives before anything is asked. There's no rating prompt, no upsell and no sign-up
  wall here, ever (§8).
- **Von Restorff.** It's the only time in the app that Zuri appears at 160 dp with a full-screen bloom, which is
  why it gets remembered and talked about. It never repeats, so it stays special.

**Why Zuri earns its place:** the character *is* the source ("A recording is the source"). Without Zuri this
screen would just be a success toast. With it, the moment has a giver.

### 2.2 Where and when

- **Trigger:** `NoteReady(isFirstEver = true)`, and `MomentLedger.allows(FirstRecordingPeak)`. **Both** the
  onboarding say-hello note (P-3 step 5, a sample note) **and** the first real recording get a First Keep
  (founder decision 2026-10-12). They are two separate once-ever moments with their own ledger keys
  (`peak.first.hello` and `peak.first`). The hello version plays on the sample content with the line "Here's what
  a note looks like", and is otherwise identical, so the user learns the gesture before it matters. The real one
  is the full celebration.
- **Where:** a full-screen overlay (`FirstKeepOverlay`) composed **on top of the note screen** (`MeetingDetail` or
  `WrapUp`), which is already rendered underneath. "Open note" just dismisses the overlay, so the note is already
  there.
- **If the note finished in the background:** the overlay plays the first time the note is opened (ticket rule,
  §3.2). If a recording is in progress at that moment, it waits until the recording stops.

### 2.3 Choreography (base: Zuri form, Everyday space, 3.6 s)

Units: dp on screen; character units are the 100-unit box (§2.1 of the experience spec). Easing names map to
`MM.motion`: **std** = emphasized (0.2, 0, 0, 1); **out** = (0, 0, 0.2, 1); **in** = (0.4, 0, 1, 1);
**back** = overshoot 1.04; **spring** = `MM.motion.spring` (damping 0.8, stiffness medium-low).

| # | Beat | Time (ms) | Zuri | Note card | Stage | Haptic / sound |
|---|---|---|---|---|---|---|
| 0 | **Complete** | 0–300 | Thinking → happy eyes at 200. Grows from the processing slot (96 dp) to 160 dp (spring, 0–500), centred at 47% of screen height | Hidden behind Zuri: scale 0.45, alpha 0 | The processing ring closes to 100% (0–200, out). The caption fades (0–200). An accent bloom (radial `accentWash`, 0 → 100%) grows from Zuri's centre (100–600, out) | — |
| 1 | **Anticipate** | 300–450 | Squash: sy 0.92, sx 1.04 (in) | — | — | — |
| 2 | **Hop & glow** (Signature) | 450–950 | Hop: dy 0 → −12 (450–700, out) → 0 (700–950, in); sy 1.06 at the apex; sprout 8/12/8 → 10/16/10 | — | Halo ring: scale 0.8 → 1.8, alpha 0.6 → 0 (700–1200, out). **6 sparkles** burst radially to 110 dp, alternating accent and `gold`, rotating 90° (700–1700) | **Tick** at 700 (`CLOCK_TICK`) |
| 3 | **Present** | 900–1600 | Lands at 950 (sx 1.08, sy 0.92, then spring). Moves down 70 dp and scales to 0.72 (1000–1600, std) to make room | **Rises from behind Zuri:** y +180 → 0 dp, scale 0.45 → 1.03 → 1 (back), alpha 0 → 1 (900–1100), rotation −4° → 0. Z-order stays behind Zuri | — | **Confirm** at 1500, when the card lands (`CONFIRM`) |
| 4 | **Title writes** | 1500–2300 | Looks up at the card (lookY −1.5) | Title words fade up 6 dp, 80 ms per word (capped at 8 words, then the rest together). Key-point skeleton bars resolve into the note's **verbatim** key points (1900–2300, crossfade) | Sparkles are gone by 1700 | Optional chime at 1500 (§2.5) |
| 5 | **Words** | 2100–2900 | — | — | Headline fades up 8 dp (2100–2500, std), the sub line (2300–2700), then the actions (2500–2900) | — |
| 6 | **Settle** | 2700–3600 | One blink at 3000, then the rest pose, still. **No idle loop.** | — | Bloom recedes to 30% (2400–3200) | — |

**End state (also the reduced-motion static):** the card at the top third, Zuri at about 115 dp below it in the
rest pose, the headline, the sub line, **Open note** (primary) and **Share a moment** (secondary text button).

**Space variants:**

- **Faith** (or a sermon as the first note): **the quiet First Keep**. There is no hop and no sparkle burst.
  Beat 2 becomes a *glow*: the bloom is warm (lerp(accent, gold, 0.5)), Zuri closes its eyes, then opens them
  (Peaceful → happy), and one gold glint appears at the card's corner. Time ×1.15, amplitudes ×0.7, no haptics.
- **Work** (×0.85): fewer sparkles (4). The sub line shows the **real** action-item count when there is one.
- **Study:** the reference timing. Sparkles land as 3 small sprout-bar shapes.

### 2.4 Per-form takes (not recolours)

| Form | The take | Differences from the base |
|---|---|---|
| **Zuri** | **"The record button gives back what it kept."** The note rises out from behind the orb as the sprout stretches like two arms lifting. | Base timeline. Sprout 10/16/10 in beat 2, leaning 6° toward the card in beat 4. |
| **Nas** | **"A proud fetch."** The card slides in from behind-right as if Nas nudged it up with his nose. He gives a small hop and a wag that decays, then sits with his head tilted at the card. | Hop ×0.5 (dy −6). Wag 7 Hz with amplitude 1 → 0 over 450–1650. Ears 24° → 12°. The card enters from x +40 dp, rotation +6° → 0. Head tilt −8° in beat 4. |
| **Wren** | **"Flutter-lift, then perch."** Two wing flaps carry the card up. At the end Wren flies to **perch on the card's top end corner** at 45% scale. | Wing −30° ↔ −55°, two flaps (450–1300). A perch move 2400–3000 (std): x +95 dp, y −215 dp, scale 0.45. This is allowed: it is the moment screen, not the note itself (open question 5). |
| **Page** | **"I become your note."** Page grows and dissolves into the card. Its three lines *are* the card's three key-point lines. At the end a mini Page pops at the card's bottom end corner and winks. | Page: scale 1 → 1.4, alpha 1 → 0 (900–1200). The card starts at Page's own size (scale 0.4). Mini Page: scale 0 → 0.4 (2500–2800, back), with its fold flicking (F 12 ↔ 15) and one wink at 3100. |

### 2.5 Sparkles, haptics, sound

- **Sparkles:** 6 (Work 4, Faith none, plus one glint). Accent and `gold` alternate, at sizes 5/4/3.5 units.
  Each one scales 0 → 1 → 0 and rotates 90°. They are drawn in the stage layer, not in the character, so Rive
  and Canvas share them.
- **Haptics:** at most **2** (tick at the apex, confirm at the landing), and only when system haptic feedback is
  on. None in Faith. Use `View.performHapticFeedback` with the constants above. Never use vibration patterns.
- **Sound (decided 2026-10-12: ship it):** one soft two-note chime (≤ 300 ms, ≤ 20 KB OGG) at 1500. It plays only
  when the ringer mode is normal, Do Not Disturb is off and the space isn't Faith, and the user can turn it off in
  Settings → Companion → Advanced → "Sounds" (on by default). There's no sound in any other moment, ever.

### 2.6 Skip and never blocking

- A tap anywhere jumps to the end state with a 150 ms crossfade. The tap is observed in the `Initial` pointer pass
  and **not consumed**, so a tap on "Open note" during the animation opens the note.
- Back closes the overlay and shows the note.
- The actions are focusable and clickable from frame 0 (alpha < 1 doesn't disable them).
- If the user leaves the app mid-moment, the end state is shown on return. It never replays.

---

## 3. Moment 2: the Unveil system

### 3.1 Purpose and feeling

**The feeling:** *"Here it is."* A small, specific pleasure each time, like a barista sliding your cup across:
quick, warm and a little different every day, and never a performance you have to sit through.

**Psychology and anti-staleness:**

- **Variable presentation, fixed reward.** The reward (your note) is always the same and always immediate. Only
  the *presentation* varies, from a small set of handovers that share one character. This keeps it fresh without
  the slot-machine mechanics of a variable *reward*.
- **Rhythm.** Five variants, rotated deterministically, never the same twice in a row, and weighted by space so
  each space has its own "accent".
- **Restraint.** A daily budget (2), a back-to-back rule and familiarity compression: the 50th unveil is faster
  than the 5th (§3.6). Anything that might be annoying degrades to the 0.6 s nod.
- **Peak-end, small.** Each note-ready is a micro-peak, and the note's first impression is its end.

**Why Zuri earns its place:** the character that listened is the one handing over the note, which closes the
loop the processing screen opened (the labor illusion pays off). Without Zuri, there's just a fade.

### 3.2 Where it plays: the ticket rule

> **A note is unveiled once: the first time it is opened, within 24 hours of being ready.**

- On `SUCCEEDED`, the processing view model writes an **`UnveilTicket(noteId, readyAtMs, space, sermon,
  partial)`** to `MomentLedgerStore` (DataStore). It expires after 24 h.
- The note screen (`MeetingDetail`, `WrapUp`) consumes the ticket on first composition. **No ticket means no
  moment**, so reopening a note is always plain.
- Paths:

| Path | What the user sees |
|---|---|
| Watching processing → auto-navigate | The note screen enters with the planned moment |
| Elsewhere in the app | A snackbar: "{Note title} is ready." with **Open**, and a 40 dp nod in the leading slot. Open → the note → the Unveil |
| App in the background | A notification with the **neutral glyph** (`ic_zuri_glyph`): "{Note title} is ready." Tap → the note → the Unveil |
| From the Notes list later the same day | The Unveil still plays (the ticket is valid) |
| Opened after 24 h | Plain open (the ticket has expired) |

### 3.3 The intensity ladder and the degrade rule

The ladder: **FIRST_KEEP** (3.6 s) > **UNVEIL** (variant, 1.2–1.8 s) > **NOD** (0.6 s) > **PLAIN** (no motion;
the note is simply there).

> **Founder decision 2026-10-12 (supersedes the Unveil rotation for now):** the First Keep plays on **every**
> successful note, because the founder loves it; variations come later. It sits behind
> `CompanionFlags.firstKeepEveryNote` (default **true**). While it is true, the planner uses the table below
> with these changes: row 3 becomes "any successful note → **FIRST_KEEP** (the quiet version in Faith or for a
> sermon)", the daily budget and the back-to-back guard no longer apply, and rows 8 and 9 (the Unveil rotation)
> are skipped. Everything else stays: failures and sacred notes stay plain, a recording in progress defers it, and
> reduced motion, battery saver, a low-end phone and a very short recording still step down, because those are
> system or honesty guards, not taste. The Unveil variants, `UnveilRotation` and `DailyBudget` stay in the code
> behind the flag, dormant, so "later we make variations" is a flag flip, not a rewrite. The **chime** plays only
> on the two once-ever First Keeps (hello and first real recording), never on every note. It would become
> grating at the 50th note.

`MomentPlanner.plan(facts)` is a pure function. **The first matching row wins:**

| # | Condition | Plan | Why |
|---|---|---|---|
| 1 | Processing failed, or partial (summary missing), or `MODEL_REQUIRED` | **PLAIN** + `StatusLine` with the fix; Zuri Worried / Sleepy in its leading slot | Never celebrate a failure |
| 2 | `sensitive` (prayer request, grief/funeral/memorial tag) or Good Friday | **PLAIN** (no companion motion) | §1.3, §5.10 |
| 3 | First note ever, and the ledger allows `FirstRecordingPeak`; **or `firstKeepEveryNote` is on and the note succeeded** | **FIRST_KEEP** (quiet if Faith or a sermon) | The signature moment (every note while the flag is on) |
| 4 | A recording is in progress | Defer: the ticket waits. After the stop, one snackbar for all queued notes ("2 notes are ready."); each later opens with a **NOD** | Never interrupt a recording |
| 5 | Reduced motion (animator duration scale 0) | **PLAIN** + the polite announcement | Static end state |
| 6 | Sermon recording (quiet) | **UNVEIL Dawn, quiet** (no glint, eyes closed → soft nod) if the budget allows, else **NOD** quiet | Calm: a warm glow and a soft nod |
| 7 | Battery saver on, **or** a low-end device, **or** a very short recording (< 20 s of audio or < 30 transcript words), **or** back-to-back (the previous note-ready was < 15 min ago) | **NOD** | Restraint |
| 8 | The ledger allows `Unveil` (2 per local day, ≥ 3 h apart) | **UNVEIL**, variant = `UnveilRotation.next(space)` | The rationed reward |
| 9 | Otherwise (the 3rd+ note today, or < 3 h since the last Unveil) | **NOD** | Restraint |

**Low-end device:** `ActivityManager.isLowRamDevice()`, or total RAM < 3 GB, or `Build.VERSION.SDK_INT < 28`.
Computed once and cached.

**Presence filter:** with presence OFF, hidden, or no companion, the plan still runs, but the moment renders
**without the character**. FIRST_KEEP becomes the card, words and actions (bloom only). UNVEIL becomes the note-side
beats at NOD level (header fade-up 250 ms). With "Big moments", it plays, because `NOTE_READY` is in BigMoments.

### 3.4 The five variants

All five play on the note screen's **header block** (title, meta row, chips), with Zuri at 48–64 dp in an overlay
confined to the header and top-bar zone. The **body** (summary, key points, actions) always enters the same
quiet way: alpha 0 → 1 and y +8 → 0 dp, staggered 70 ms per section, starting where each variant says. Times are
base (Everyday); the space scale is applied on top (§3.6).

#### V1 · Lift (playful, 1500 ms) *"Zuri lifts the note out from behind itself."*

| Beat | ms | Zuri (64 dp, header centre) | Header block | FX / haptic |
|---|---|---|---|---|
| Arrive | 0–150 | alpha 0 → 1, scale 0.6 → 1 (out) | Behind Zuri: scale 0.85, y +16, alpha 0 | — |
| Anticipate | 150–300 | sy 0.93 (in) | — | — |
| **Lift** (Signature) | 300–700 | Hop dy −10, apex at 500 | Rises from behind: alpha 0 → 1 (300–500), y +16 → −4, scale 0.85 → 1.02 (back) | — |
| Land | 700–900 | Squash sx 1.08, sy 0.92 → spring | Settles: y 0, scale 1 | **Tick** at 700 |
| Step back | 900–1300 | Slides 40 dp to the end side and 30 dp up into the top-bar zone; scale → 0.5, alpha → 0 (in) | — | — |
| Body | 600–1300 | — | Sections fade up | — |

#### V2 · Inkline (crisp, 1400 ms) *"The title writes itself."*

| Beat | ms | Zuri (48 dp, at the title's start edge) | Header | FX / haptic |
|---|---|---|---|---|
| Arrive | 0–150 | alpha in | Meta row visible; title empty | A 2 dp accent caret at the title start |
| **Write** (Signature) | 150–950 | Gaze follows the caret: lookX −1.2 → +1.2 | Title words appear left to right, alpha 0 → 1 and y 4 → 0, 70 ms per word (max 8 words, then the rest together) | The caret leads the words |
| Underline | 950–1150 | Nod: one 3-unit dip | — | A 2 dp accent underline draws from the start (scaleX 0 → 1). **Tick** at 1000 |
| Clear | 1150–1400 | alpha → 0 | — | Underline alpha → 0; the caret is gone |
| Body | 700–1300 | — | Sections fade up | — |

#### V3 · Curtain (crisp, 1500 ms) *"A soft curtain of the accent parts."*

| Beat | ms | Zuri (56 dp, at the seam) | Header | FX / haptic |
|---|---|---|---|---|
| Cover | 0–200 | Pops in at the seam: scale 0.6 → 1 | Under two `accentWash` halves (12 dp feathered seam) | — |
| **Grip** (Signature) | 200–400 | Squash, looks down | — | — |
| Part | 400–1000 | Rides the end half: x +56 dp, alpha → 0 by 1150 | Revealed: scale 0.98 → 1, alpha 0 → 1 | The halves slide out 60% of the width each, alpha 1 → 0 (std). **Tick** at 700 |
| Settle | 1000–1500 | — | — | — |
| Body | 800–1400 | — | Sections fade up | — |

#### V4 · Dawn (gentle, 1500 ms, no hop) *"A warm light rises behind the title."*

| Beat | ms | Zuri (48 dp, at the title's end edge) | Header | FX / haptic |
|---|---|---|---|---|
| Glow | 0–300 | alpha in, eyes closed | — | A radial glow, lerp(accent, gold, 0.5) at 35% alpha, rises from 24 dp below the header to its centre |
| **Rise** (Signature) | 300–900 | Eyes open at 700 ("wakes") | Title fades up through the glow: alpha 0 → 1, y 6 → 0. On API 31+, a 4 → 0 dp blur (`RenderEffect`); below 31, alpha only | — |
| Nod | 900–1200 | One 3-unit dip | — | One gold glint at the title's end (900–1300). **Omitted in sermon-quiet** |
| Fade | 1200–1500 | alpha → 0 | — | Glow → 0. **No haptic** |
| Body | 700–1300 | — | Sections fade up | — |

#### V5 · Plant (playful, 1600 ms) *"The sprout plants the note on the page."*

| Beat | ms | Zuri (56 dp) | Header | FX / haptic |
|---|---|---|---|---|
| Drop | 0–400 | Drops from the top bar to the title baseline: dy −30 → 0 (in), sy 1.06 | — | — |
| **Plant** (Signature) | 400 | Impact squash: sx 1.1, sy 0.9 | — | A ripple ring from the impact point: scale 0 → 3.2, alpha 0.45 → 0 (400–1000). **Tick** at 400 |
| Grow | 450–900 | — | The title **grows from its baseline**: scaleY 0.6 → 1.04 → 1 (pivot at the bottom, back), alpha 0 → 1 | Three tiny rounded sprout bars pop beside the ripple (450–800) and shrink (1000–1200) |
| Pat | 600–750 | A second, smaller squash | — | — |
| Hop away | 1050–1450 | A small hop toward the end side, alpha → 0 | — | — |
| Body | 800–1400 | — | Sections fade up | — |

#### Per-form takes on each variant (the Signature beat)

| Variant | Zuri | Nas | Wren | Page |
|---|---|---|---|---|
| **Lift** | The sprout stretches 10/16/10, like arms lifting | Nudges the note up with his nose: head tilts up 10°, then 2 wags | The wing sweeps up to −45° and *presents* it | Its top line peels off and becomes the title |
| **Inkline** | Sprout bars tick per word, like a metronome | Ears twitch per word, and his head follows the caret | Head bobs per word, as if pecking along the line | **Writes it**: its lines grow in sync with the words |
| **Curtain** | The sprout leans and tugs the seam | A head-tilt tug, then ears flop back | The wing opens like a fan | The fold corner flips like a page-turn |
| **Dawn** | The sprout bars brighten (deep → light) | Ears lift slowly | Head lifts to the light, tail rises | The paper brightens and the fold settles flat |
| **Plant** | The sprout touches down first and grows | A paw-pat with one wag | Two quick hops and a wing flick | Stamps its corner on the line like a seal |

### 3.5 The nod (the everyday default)

600 ms, no sparkles, no haptic. The header fades up (alpha 0 → 1, y 8 → 0, 250 ms `standard`). Zuri at 40 dp in
the top-bar end zone fades in (0–120), dips 3 units (150–450, the existing `ZuriMotion.Nod`) and fades out
(450–600). The body fades up from 100 to 500. On the snackbar path, the nod already played in the snackbar, so the
note opens with the header fade only.

### 3.6 Selection rule: deterministic, non-repeating, weighted by space

**Weights** (0 = never offered in that space):

| Space | Lift | Inkline | Curtain | Dawn | Plant | Character |
|---|---|---|---|---|---|---|
| **Everyday** | 3 | 2 | 2 | 2 | 2 | Balanced, slightly playful |
| **Faith** | 0 | 2 | 1 | 4 | 0 | Gentle; no hops (Lift and Plant are excluded) |
| **Work** | 1 | 4 | 3 | 1 | 0 | Crisp |
| **Study** | 3 | 2 | 1 | 1 | 4 | Playful |

**Algorithm: smooth weighted round-robin (SWRR) with a no-repeat guard.** It is deterministic (no randomness, so
it is testable), proportional over every cycle, and spreads repeats evenly.

```
next(space):
  W = weights[space]; total = Σ W
  for v in VARIANT_ORDER where W[v] > 0:  cw[space][v] += W[v]
  candidates = variants with W[v] > 0, sorted by cw desc, ties by VARIANT_ORDER
  pick = first candidate != lastVariant          // the guard is global, across spaces
         (if only one candidate exists, it may repeat)
  cw[space][pick] -= total
  lastVariant = pick; seen[pick] += 1
  return pick
```

`VARIANT_ORDER = [LIFT, INKLINE, CURTAIN, DAWN, PLANT]` (the same as the `riveId`, append-only).

**Worked example (Everyday, from zero):** Lift, Inkline, Curtain, Dawn, Plant, Lift, Inkline, Curtain, Lift, Dawn,
Plant… With the guard, no variant ever plays twice in a row. In Faith, Dawn can't play back to back, so its
effective share is at most 50% (≈ Dawn, Inkline, Dawn, Curtain, Dawn, Inkline…), which is intended. The prototype
prints the next 12 picks for each space.

**Persistence:** `cw` (small ints per space × variant), `lastVariant` and `seen[variant]` live in
`MomentLedgerStore` next to the ledger (DataStore, about 40 bytes). A cleared store restarts the rotation, which is
harmless.

**Timing scale** (multiplied together, then clamped):

- Space: Everyday ×1.0, Study ×1.0, **Faith ×1.15** (amplitudes ×0.7), **Work ×0.85**.
- **Familiarity compression:** after a variant has played 8 times, ×0.9; after 25 times, ×0.8.
- The result is **clamped to [1200, 1800] ms**. The 50th Lift in Work runs about 1.2 s.
- The system animator duration scale multiplies last. Scale 0 means PLAIN.

### 3.7 Variant data format

```kotlin
/** Append-only: the id is also the Rive `momentVariant` value. */
enum class UnveilVariant(val id: Int, val energy: Energy, val hops: Boolean, val baseMs: Int) {
    LIFT(1, Energy.PLAYFUL, hops = true, 1500),
    INKLINE(2, Energy.CRISP, hops = false, 1400),
    CURTAIN(3, Energy.CRISP, hops = false, 1500),
    DAWN(4, Energy.GENTLE, hops = false, 1500),
    PLANT(5, Energy.PLAYFUL, hops = true, 1600),
}
enum class Energy { GENTLE, CRISP, PLAYFUL }

/** Data, not code: tuned in Companion Lab → Moments without touching the renderer. */
data class MomentSpec(
    val kind: MomentKind,                       // FIRST_KEEP, UNVEIL, NOD, CARD_REVEAL
    val variant: UnveilVariant? = null,
    val baseMs: Int,
    val beats: List<Beat>,                      // named spans, for the Lab, the tests and the docs
    val pose: List<PoseTrack>,                  // character channels (DY, SX, SY, TILT, HOP, LOOK_X, LOOK_Y, EYES, SIGNATURE…)
    val stage: List<StageTrack>,                // Compose layers: header, title words, body, bloom, halo, curtain…
    val formTakes: Map<CompanionForm, FormTake>,// per-form overrides of pose and stage tracks
    val haptics: List<HapticBeat>,
    val sparkles: Int = 0,
    val sound: Boolean = false,
)
data class Beat(val name: String, val startMs: Int, val endMs: Int)
data class MsKey(val ms: Int, val value: Float, val easing: Easing = MomentEasing.Std)
data class StageTrack(val target: StageTarget, val prop: StageProp, val keys: List<MsKey>)
data class PoseTrack(val channel: MomentChannel, val keys: List<MsKey>)
data class FormTake(val pose: List<PoseTrack> = emptyList(), val stage: List<StageTrack> = emptyList())
data class HapticBeat(val ms: Int, val kind: HapticKind)          // TICK | CONFIRM
enum class StageTarget { COMPANION, CARD, HEADER, TITLE_WORDS, BODY, BLOOM, HALO, SPARKLES, CURTAIN, GLOW,
                         RIPPLE, UNDERLINE, CARET, SPROUTS, HEADLINE, SUBLINE, ACTIONS, MINI_COMPANION }
enum class StageProp { ALPHA, X_DP, Y_DP, SCALE, SCALE_Y, ROTATION, PROGRESS }
```

`MomentSpecs` is one Kotlin object holding the table. The keys are absolute milliseconds on the base timeline, and
the scale is applied at evaluation time. A test checks that every track's last key is ≤ `baseMs`, that every
beat's span lies within `baseMs`, and that each `formTake` only references channels its form draws.

---

## 4. Special cases

| Case | What happens | Zuri | Copy |
|---|---|---|---|
| **Failed note** | No moment. The processing screen (or the note, if reached) shows the `StatusLine` with **Retry**. On success after Retry: **NOD** only (the existing `retrying` rule) | Worried, with the fix | "That one didn't finish. Your recording is safe." [Retry] |
| **Partial note** (transcript OK, summary failed) | **PLAIN**: the note opens with the StatusLine above the header. Never celebrated | Worried in the StatusLine's leading slot | "The transcript is ready. The summary didn't finish." [Try again] |
| **On-device fallback** (online AI failed, the note was made on the phone) | **PLAIN** + the honest line. The note is complete, but the honest line comes first and nothing celebrates on top of it | Worried + **Redo online** (§5.6) | "Couldn't reach Gemini, so this note was made on your phone." [Redo online] |
| **Models missing** (`MODEL_REQUIRED`) | **PLAIN**: "Recording saved" + Download | Sleepy | "{companion} needs its on-device models to work offline." [Download] |
| **Very short recording** (< 20 s or < 30 words) | **NOD**. It's kept and acknowledged, not fussed over. If no speech was found: PLAIN, with no companion | Nod | No speech: "We didn't hear anyone speaking. Your recording is safe." [Play it] |
| **Sermon** | **Dawn, quiet**: no hop, a warm glow, eyes closed → a soft nod, no glint, no haptic. If it's the first note ever, the **quiet First Keep** | Peaceful → nod | Snackbar / notification: "Your sermon notes are ready 🙏" |
| **Prayer request / grief tag / Good Friday** | **PLAIN** | None (or Peaceful, static) | "{Note title} is ready." |
| **Meeting in progress** (another recording running) | Nothing on the recording screen. Tickets wait. After the stop: one snackbar for all queued notes. Each later opens with a **NOD** | Nothing during the recording | "2 notes are ready." [Show] |
| **Offline-model notes** (on-device by choice) | The normal ladder. The header's provenance chip "Made on your phone" is part of the revealed header (Inkline writes past it) | Normal | Chip: "Made on your phone" |
| **Long processing finished in the background** | Notification with `ic_zuri_glyph` → tap → the note opens and the planned moment plays (FIRST_KEEP or UNVEIL per the ladder). Counts against `CompanionNotification` (once a day, none in quiet hours). Beyond the cap, the plain system notification ("{Note title} is ready.") still posts, because status is never withheld | Glyph only in the notification | "{Note title} is ready." / sermon: "Your sermon notes are ready 🙏" |
| **Several notes ready while away** | The first one opened gets the planned moment; the rest get NOD (budget) | — | — |
| **Work Wrap-up destination** | Same ladder. Zuri stays in the header and top-bar zone at ≤ 48 dp and never crosses decisions or tasks (§2.6 Work) | Crisp | — |
| **Onboarding say-hello note** (P-3 step 5) | **FIRST KEEP (hello version)** on the sample note, at the onboarding preview size. It has its own once-ever key (`peak.first.hello`) and doesn't consume the real First Keep (decision 1) | — | "Here's your first note. Everything you said, kept." |
| **Retry succeeded** | **NOD** | — | — |

---

## 5. Moment 3: the Create card with Zuri

### 5.1 Purpose and feeling

**The feeling:** *"My little friend is on my card."* Pride and ownership (the IKEA effect: they chose the form,
named it, and now pose it). When people share the card, it's word of mouth with a face: the moment others see is
the one they talk about.

**Psychology:** ownership and self-expression; **social proof** that travels (the companion is recognisable on a
WhatsApp status); **Von Restorff** (one small character on a calm card stands out); **control** (always the user's
choice, off by default, so it never feels like branding was slipped in).

**Why Zuri earns its place:** it's the only decoration the user *owns*. Everything else on the card is the user's
own words or a background.

### 5.2 Placement rules

**Anchors** (`CompanionAnchor`):

| Anchor | Where | Default for |
|---|---|---|
| **BELOW** (today's behaviour) | Under the text block, above the foot. Centred if the text is centred, start-aligned otherwise | All formats |
| **CORNER_END** | In the foot row, at the end corner; the attribution and watermark move to the start side | Offered on Story and Portrait; on Square only when the side margin is ≥ 13% (grid crop) |

The top corners are **never** used: on Story, the WhatsApp/Instagram progress bars and profile row sit there.

**Size:** Story 20% of the width (216 px at 1080), Portrait 19%, Square 18%. **Minimum 160 px** (below that it reads
as a sticker). It shrinks toward the minimum before the text shrinks below its floor. If the text still doesn't fit,
the existing `fits()` asks for a shorter passage and offers "Hide {companion}".

**Safe zones** (fractions of the card; the renderer's values, plus the platform reason):

| Format | Top | Bottom | Side | Why |
|---|---|---|---|---|
| **Story 9:16** (1080×1920) | **14%** (269 px) | **18%** (346 px) | 9% | WhatsApp status / Instagram story: progress bars, avatar, name and time at the top; the reply field and send/react row at the bottom |
| **Portrait 4:5** (1080×1350) | 8% | 8% | 9% | Feed post. The Instagram profile grid shows a centred **3:4** crop, which takes about 3% off each side, so the side margin covers it |
| **Square 1:1** (1080×1080) | 9% | 9% | 9% (**13%** for a corner anchor) | Feed post / chat preview. The 3:4 grid crop takes **12.5%** off each side, so CORNER_END needs a 13% side margin |

*(The platform chrome sizes were current at the time of writing. The editor's overlay is the source of truth, and
the fractions are one table in `CreateCardRenderer.safe`.)*

**Algorithm** (`CompanionPlacement.place(format, align, anchor, textRect, footRect) → RectF`, pure, unit-tested):

1. Start from the safe rect.
2. Reserve the foot (attribution, watermark).
3. BELOW: the companion's top is `textRect.bottom + 24u` and its bottom is `footRect.top − 12u`; x follows the
   alignment.
4. CORNER_END: it sits in the foot row at the end; the foot text width is reduced by the companion size + 16u.
5. **Invariants:** it never intersects `textRect` (≥ 24u gap), never leaves the safe rect, and never covers the
   attribution or the watermark.

### 5.3 Mode: the AI suggests, the user chooses

- The pose follows the vibe by default (`CreateVibePolicy.companionMode`). **New:** a **pose override**
  (`CreateDesign.companionPose: CreateMode?`, null = follow the vibe), shown as chips under "Include {companion}".
- The chips show only the **allowed** modes (`CreateModePolicy.allowed(source, vibe, grief, goodFriday)`).
  Disallowed chips are **hidden, not greyed**, with one line saying why when the set is restricted.
- The suggestion chip is labelled "Suggested: Peaceful". After an override: "Suggested: Peaceful · you chose
  Reflective". Re-checking against the policy happens on every source or vibe change. An override that becomes
  disallowed falls back to the suggestion with a quiet line ("Grateful isn't offered on prayer requests").
- The suggestion **only picks a pose**. It never writes or changes text (§3.2 rule 2). No model output reaches
  the pose without passing `CreateModePolicy`.

### 5.4 The reveal in the editor (1.5 s)

It plays when "Include {companion}" is switched on (and when the format changes while it's on), on the live
preview. It draws as an overlay at the placement rect; at the end it **settles into exactly the export pose**, so
what you see is what you share.

| Beat | ms | Companion | Card |
|---|---|---|---|
| Mark | 0–150 | — | A dashed ring at the placement rect fades in to 50% (shows *where* it will sit) |
| **Step on** | 150–700 | Rises: y +12 units → 0, scale 0.88 → 1 (back; **out** for Faith), alpha 0 → 1 | The ring fades out |
| **Signature** | 700–1300 | The mode's own motion, once: Joyful bob · Grateful heart float · Reflective look-up + blink · Peaceful breath · Prayerful breath · Celebratory hop (+2 sparkles) | — |
| **Settle to export** | 1300–1500 | Lerps to the static export pose | A caption fades in: "This is how it'll look when shared." (once per card session) |

- **Mode chip change:** a 250 ms pose crossfade + the new mode's Signature (≤ 600 ms). No ring.
- **Prayer request:** opacity only (no rise, no scale), a 6 s breath for one cycle, then still.
- **Tap or scrub:** any interaction with the editor jumps to the settled pose.
- **Reduced motion:** the export pose appears with a 150 ms fade.

### 5.5 Export

- **Shipped:** a **static image**, the Canvas `renderCompanionBitmap(form, mode, palette, sizePx)` drawn into the
  `CreateCardRenderer` bitmap at the placement rect. Render at 2× the placed size and downsample (e.g. 432 px for a
  216 px slot) for clean edges. Each mode has an export pose: the `StaticPoses` entry (the reduced-motion pose),
  which is what the editor settles to.
- **IDEA, optional, not committed: a 2-second animated loop.**

| Option | How | Cost | Verdict |
|---|---|---|---|
| **MP4 (H.264), 2 s, 30 fps, 1080×1920** | Draw the static card once. Each of the 60 frames redraws only the companion's rect (Canvas, deterministic `t`) onto a copy, then `MediaCodec` + `MediaMuxer` | About 1.5–3 s to encode on a mid-range phone; about 0.4–0.9 MB per file; no new dependency; WhatsApp status and Instagram stories accept it | **The one to try** |
| Animated WebP | Needs a native libwebp encoder (Android has no animated WebP encoder) | +300–500 KB per ABI, plus JNI | Not worth it |
| GIF | Built-in-ish | 256 colours band the gradients; large files | No |

  The loops must be seamless at 2 s: Joyful bob (2 cycles), Grateful heart float (1), Reflective blink (1),
  Celebratory hop + settle, Peaceful/Prayerful a 2 s breath at reduced amplitude. **Prayer requests: static only**.
  The animated export is never offered for them. It would sit behind a flag `create.animatedExport`, as a share
  sheet option: "Share as a short video".

### 5.6 Prayer requests (hard rules)

1. **Off by default, always**, even when "Suggest {companion} on cards" is on (it applies to achievements and study
   wins only: `CreateModePolicy.includeByDefault`).
2. Only **Prayerful** or **Peaceful** is offered (the other chips are hidden).
3. **No motion beyond a slow breath**: the reveal is opacity-only, and nothing is animated in the export.
4. **The user's words are never altered.** The companion's placement may shrink the companion, never the text below
   its floor, and it never rewrites, trims or reflows the request beyond normal wrapping.
5. No sparkles, no hearts, no gold glints.
6. Copy under the chips: "On prayer requests, only Prayerful or Peaceful."

---

## 6. The "alive" contract

What an engineer **must** ship for each moment to count as built, and what is a stretch.

| Moment | Minimum (must) | Stretch |
|---|---|---|
| **First Keep** | The full-screen overlay over the already-rendered note; the Zuri and Nas takes on **Canvas T1** at 160 dp; beats 0–6 from `MomentSpec`; bloom, halo, 6 sparkles; the card with the verbatim title and key points; the headline, sub line and actions; skip (non-consuming tap, Back); 2 haptics; the quiet Faith variant; reduced-motion static; one polite announcement; never twice (ledger) | The Wren and Page takes; the Rive T2 hero; the optional chime |
| **Unveil** | `MomentPlanner` + ladder (all rows); the ticket rule; `UnveilRotation` (SWRR + guard, persisted); the **5 variants** on Canvas for Zuri and Nas; the nod; the space scale and familiarity compression; skip; one tick; reduced motion → PLAIN; one announcement | Wren and Page takes; Rive variants; blur on API 31+ for Dawn |
| **Card** | Pose override chips filtered by policy; placement anchors (BELOW + CORNER_END) with the invariants; a safe-zone overlay toggle in the Design step; the reveal (1.5 s) settling to the export pose; static export; the prayer request rules | The animated MP4 export (§5.5, an idea) |

---

## 7. Implementation plan

### 7.1 Where the code goes, by lane

Two sessions own two lanes. **Each task touches one lane only.**

```
ENGINE LANE (companion session)
core/companion/moments/                     pure Kotlin, no Compose
    MomentPlanner.kt        MomentFacts → MomentPlan (FirstKeep | Unveil(variant, ms, quiet) | Nod | Plain)
    UnveilRotation.kt       SWRR + no-repeat guard + familiarity (pure; state in, state out)
    UnveilTicket.kt         ticket model + expiry
core/companion/MomentLedger.kt              + Cap.DailyBudget(max, minGapMs), Moment.Unveil
core/companion/MomentLedgerStore.kt         + rotation state + tickets (same DataStore file)
core/ui/mm/companion/moments/               Compose
    MomentSpecs.kt          the data table (§3.7)
    MomentClock.kt          frame clock for one-shots; LocalMomentTime override for tests
    MomentStage.kt          the stage host: evaluates tracks, skip, haptics, a11y, reduced motion
    MomentLayers.kt         Modifier.momentLayer(target) + bloom/halo/sparkles/curtain/glow/ripple/underline/sprouts
    FirstKeepOverlay.kt     slots: card, headline, sub, actions
    NoteUnveil.kt           wraps a header block + body
    CardReveal.kt           the Create reveal overlay
core/ui/mm/companion/rive/CompanionRiveContract.kt   + optional moment inputs (§7.6)
tools/rive/companion/**                     timelines for the moment inputs (stretch)

FEATURE LANE (screens session)
feature/processing/          write the UnveilTicket + emit NoteReady on SUCCEEDED (isFirstEver from READY count)
feature/meetingdetail/       consume the ticket → FirstKeepOverlay or NoteUnveil around the header
feature/work/WrapUpScreen    the same, crisp, ≤ 48 dp
feature/notes (snackbar)     "{title} is ready" + Open, nod in the leading slot; the queued-while-recording case
core/notify/AppNotifications the glyph small icon, copy, deep link to the note (the ticket travels via noteId)
core/create/                 CompanionAnchor + CompanionPlacement (pure) + renderer changes
feature/create/              pose override chips, anchor chip, safe-zone overlay, CardReveal call site
res/values/strings_moments.xml  every line in §9
```

No `MainActivity` change is needed: navigation stays as it is, and the moment rides on the destination.

### 7.2 Planner inputs

```kotlin
data class MomentFacts(
    val noteId: String, val isFirstEver: Boolean, val sensitive: Boolean, val goodFriday: Boolean,
    val outcome: NoteOutcome,                 // COMPLETE, PARTIAL, AI_FALLBACK, FAILED, MODEL_REQUIRED, NO_SPEECH
    val space: NotebookSpace, val sermonQuiet: Boolean,
    val audioMs: Long, val transcriptWords: Int,
    val recordingActive: Boolean, val batterySaver: Boolean, val lowEnd: Boolean,
    val durationScale: Float, val companionShown: Boolean,
    val lastNoteReadyMs: Long?, val clock: LocalClock, val ledger: MomentLedger, val rotation: RotationState,
)
sealed interface MomentPlan {
    data class FirstKeep(val quiet: Boolean) : MomentPlan
    data class Unveil(val variant: UnveilVariant, val durationMs: Int, val quiet: Boolean) : MomentPlan
    data object Nod : MomentPlan
    data class Plain(val status: FixAction?) : MomentPlan
}
fun plan(f: MomentFacts): Pair<MomentPlan, MomentEffects>   // effects = ledger records + new rotation state
```

The planner runs **when the ticket is consumed** (on open), not when the note finishes, so battery saver, the
recording state and the budget reflect the moment of viewing.

### 7.3 Which tier draws it

| Moment | Canvas T1 (reference, must) | Rive (stretch) |
|---|---|---|
| First Keep (160 dp) | The character via `Companion()` with moment pose overrides; everything else is Compose layers | **T2 hero**: the character part only, when the form's file has the moment inputs and the slot passes `CompanionRendererSelector`. The bloom, card, sparkles and words stay in Compose either way |
| Unveil (48–64 dp) | Canvas | Rive allowed if the file has `momentVariant`; otherwise Canvas |
| Nod | The existing `nod` | The existing `nod` trigger |
| Card reveal | Canvas (and the export is **always** Canvas) | Not used, so the preview pose is identical to the exported bitmap |

**The same beats under both renderers:** the **app's `MomentClock` is the master**. The stage layers, haptics and
the announcement are driven by `MomentSpec` in Compose. Rive is only told *what* to play (`moment`,
`momentVariant`) and *when* (`momentPlay`). Its timelines are authored to the same base durations. Companion Lab
checks each file's timeline length against `MomentSpec.baseMs` (±1 frame) and refuses Rive for a moment that
fails (that moment uses Canvas). For Faith and Work, scaled moments use Canvas unless the Rive runtime can bind
playback speed (open engineering question, Z-31).

### 7.4 Compose hooks

```kotlin
/** Hosts one moment. Owns the clock (withFrameNanos only while playing), skip, haptics and the announcement. */
@Composable
fun MomentStage(
    plan: MomentPlan, form: CompanionForm?, space: NotebookSpace,
    announcement: String,                         // the single polite line
    onEnd: () -> Unit, modifier: Modifier = Modifier,
    content: @Composable MomentScope.() -> Unit,
)
interface MomentScope {
    /** graphicsLayer driven by the spec for [target]; read in the draw phase, no recomposition per frame. */
    fun Modifier.momentLayer(target: StageTarget): Modifier
    val progress: () -> Float                     // 0..1, for custom drawing (curtain, ripple…)
    @Composable fun Companion(size: Dp)           // the character, with pose overrides for this moment
}

// Call sites (feature lane)
NoteUnveil(ticket, form, space) { HeaderBlock(Modifier.momentLayer(StageTarget.HEADER)); Body(Modifier.momentLayer(StageTarget.BODY)) }
FirstKeepOverlay(note = FirstKeepNote(title, meta, keyPoints = note.keyPoints.take(3), actionCount), onOpen, onShare)
CardReveal(form, mode, placement, playKey = includeCompanion to format)
```

- **Skip:** `pointerInput(Unit) { awaitPointerEventScope { … PointerEventPass.Initial, never consume } }` plus
  `BackHandler(enabled = playing)`. Skip sets `t = baseMs` and runs a 150 ms crossfade.
- **Lifecycle:** the clock pauses below STARTED. On resume the moment shows its end state (never replays).
- **Haptics:** `LocalView.current.performHapticFeedback(TICK → CLOCK_TICK, CONFIRM → CONFIRM (API 30+) else
  VIRTUAL_KEY)`, only while playing, never after a skip.

### 7.5 Budgets

| Item | Budget |
|---|---|
| Duration | First Keep 3.6 s; Unveil 1.2–1.8 s; Nod 0.6 s; Card reveal 1.5 s. The clock stops at the end (0 fps after) |
| Frame time (Pixel 4a class) | ≤ 4 ms of UI-thread work per frame for the whole stage; the character ≤ 0.5 ms (existing budget) |
| Animating characters | 1 (First Keep: 2 only for the Page mini). Within `LocalCompanionRiveBudget` |
| Allocations | 0 per frame in the layers (paths and brushes pre-allocated, like `CompanionDrawKit`) |
| Sensors | **None.** No gyro parallax in moments, even at T2 (not worth registering for 3.6 s) |
| Battery | One moment per opened note at most; the daily budget caps Unveils at 2. In practice < 5 s of animation a day for a heavy user |
| Jank gate | Macrobenchmark `FrameTimingMetric` on the First Keep: P90 < 16 ms, 0 frozen frames, over 10 runs |

### 7.6 Rive contract additions (all optional; append-only)

| Input | Type | Values | Meaning |
|---|---|---|---|
| `moment` | Number | 0 none · 1 First Keep · 2 Unveil · 3 Nod · 4 Card reveal | Selects the moment timeline. 0 = the normal state machine |
| `momentVariant` | Number | 0 none · 1 Lift · 2 Inkline · 3 Curtain · 4 Dawn · 5 Plant | Which Unveil (= `UnveilVariant.id`) |
| `momentPlay` | Trigger | — | Starts the selected moment from 0. The app fires it at the `MomentClock`'s t0 |
| `momentSkip` | Trigger | — | Jumps to the moment's end pose and holds it |
| `gentle` | Boolean | false | Faith: no hops, amplitudes ×0.7 (for the quiet First Keep and Dawn) |
| *Events* (optional) | Rive events | `peak`, `land`, `end` | Used **only** by Companion Lab to verify sync (±1 frame); the app never waits on them |

Timelines per artboard: `moment_firstkeep`, `unveil_lift`, `unveil_inkline`, `unveil_curtain`, `unveil_dawn`,
`unveil_plant`. Their lengths equal the `MomentSpec` base ms. A missing `moment` input means the moments for that
form use Canvas; the state machine keeps working for everything else (unchanged behaviour).

### 7.7 Reduced motion (static versions)

| Moment | Static version |
|---|---|
| First Keep | The end layout, with Zuri in the static Celebrating pose (mid-hop with placed sparkles, §3.1) beside the card. No haptics |
| Unveil / Nod | PLAIN: the note as it is, with no companion |
| Card reveal | The export pose, with a 150 ms fade |

### 7.8 TalkBack

**Exactly one polite announcement per moment.** It's a live region on real text, not an announce call
(`announceForAccessibility` is deprecated on new APIs). The companion stays decorative (`clearAndSetSemantics`).

| Moment | Announcement | Focus order |
|---|---|---|
| First Keep | "Your first note is ready. {title}." | Headline → sub line → card title → Open note → Share a moment |
| Unveil / Nod / Plain | "{title} is ready." (once; the live region is then cleared) | Unchanged: the note's normal order |
| Failed / partial | The StatusLine text (already a live region) | StatusLine → fix action |
| Card reveal | "{companion} added. {Mode} pose." / on a chip change, "{Mode} pose." | Unchanged |

### 7.9 Tests

**Unit** (engine lane):

- `MomentPlanner`: one test per row of §3.3, plus precedence (failed + first-ever means PLAIN, not FIRST_KEEP).
- `UnveilRotation`: deterministic for a given state. **No repeats** in 1,000 picks for each space, and across space
  switches. Proportions within ±1 of the weights over every complete cycle. Faith never yields Lift or Plant.
  Familiarity thresholds. Clamp to [1200, 1800].
- `Cap.DailyBudget`: 2 a day, a 3 h gap, local-day rollover.
- The ticket: 24 h expiry; consumed once.
- `MomentSpecs`: tracks within `baseMs`; beats ordered; form takes valid.

**Unit** (feature lane):

- `CompanionPlacement`: for 3 formats × 2 alignments × 2 anchors × short/long text, no intersection with the text,
  inside the safe rect, and the CORNER_END square margin is ≥ 13%.
- Pose override: disallowed after a source change → falls back.

**Goldens (Roborazzi)**, frozen with `LocalMomentTime provides ms`:

| Set | Frames | Count |
|---|---|---|
| First Keep (engine) | t = 0, 700, 1500, 3600 × Paper/Graphite × Zuri/Nas (+ the quiet variant at 1500 and 3600) | 16 + 4 |
| Unveil variants (engine) | 5 variants × {Signature peak, end} × Paper/Graphite, Zuri | 20 |
| Nas takes (engine) | 5 variants × peak, Paper | 5 |
| Nod + Dawn-quiet (engine) | peak × Paper/Graphite | 4 |
| Reduced motion (engine) | First Keep, Unveil end, Card reveal × Paper/Graphite | 6 |
| Feature screens | the note header after the Unveil (MeetingDetail, WrapUp) × 2 themes; the First Keep end over the note × 2; the Create Design step for a quote, a prayer request and a study win × 2; the export bitmaps for 3 formats × 2 anchors | ≈ 22 |

---

## 8. Frequency caps and ethics

**Caps** (the existing ones are reused; ★ = new):

| Moment | Cap | Ledger key |
|---|---|---|
| First Keep | Once ever | `peak.first` (existing `FirstRecordingPeak`) |
| ★ Unveil | **2 per local day, ≥ 3 h apart** (decided 2026-10-12) (`Cap.DailyBudget(2, 3h)`) | `unveil` (replaces `FullCelebration` for note-ready; `celebrate.full` stays for other full Celebrating uses) |
| ★ Back-to-back guard | A note-ready < 15 min after the previous one → NOD | `noteready.last` |
| Nod | Uncapped (it's the quiet default), but at most one per note (the ticket) | — |
| ★ Ticket | One moment per note, valid for 24 h | `ticket.{noteId}` |
| Companion notification | Once a day, none in quiet hours or during a Faith recording | `notify` (existing) |
| ★ Card reveal | The full 1.5 s reveal once per card session; afterwards, chip changes get the 250 ms crossfade + Signature | In memory (per card) |
| ★ First Keep chime | Twice ever (with each First Keep), when "Sounds" is on | — |

The ledger stores only timestamps (`DailyBudget` keeps the last *max* timestamps per key), so nothing personal is
stored.

**What these moments deliberately don't do:**

- No count of unveils, notes or "keeps" shown anywhere. The 10th note is a Proud milestone, once (existing).
- No streaks, no "you haven't recorded today", no guilt, no sad companion after an absence.
- No rating or review prompt, no upsell and no paywall anywhere near a peak. **Never piggyback on a moment.**
- No sharing nudge after the Unveil. "Share a moment" exists only on the First Keep, as a secondary text button.
- No auto-posting. Cards are shared only by an explicit tap.
- No sound except the First Keep chime (which has a "Sounds" switch).
- No randomness in what the user gets. The reward is always the note, immediately underneath.
- No animation the user has to wait for: skippable, never blocking, and 0 fps after.
- No moment on failures, prayer requests, grief or Good Friday. No hops in Faith.
- No new attention-grabbing idle loops (the end state is still).

---

## 9. Copy

**Name rules:** {name} appears **only** on the First Keep headline, and only if the user gave one. It's never used in
Unveils, the nod, errors, notifications or cards. {companion} is the user's chosen companion name. The app speaks
status. {companion} speaks in the first person only on the First Keep (bonding).

| Where | Line | Faith-sensitive variant |
|---|---|---|
| First Keep headline | "Your first note is ready." · with a name: "{name}, your first note is ready." | Sermon: "Your first sermon notes are ready." |
| First Keep sub line | "Everything you said, kept." | "Every word, kept." |
| First Keep sub line (Work, ≥ 1 action item) | "{n} action items, kept." (**the real count**, else the default line) | — |
| First Keep sub line (Study) | "Ready when you are to revise." | — |
| First Keep, companion line (optional, under the card, 12 sp) | "I kept every word." | None in Faith (quiet) |
| First Keep actions | **Open note** · Share a moment | Same |
| Unveil / nod announcement | "{title} is ready." | Same |
| Snackbar | "{title} is ready." [Open] | "Your sermon notes are ready 🙏" [Open] |
| Snackbar, queued after a recording | "{n} notes are ready." [Show] | Same |
| Notification | Title: "{title} is ready." · Text: "Tap to open your note." | "Your sermon notes are ready 🙏" |
| Notification, on-device | Text: "Made on your phone." | Same |
| Partial | "The transcript is ready. The summary didn't finish." [Try again] | Same |
| No speech | "We didn't hear anyone speaking. Your recording is safe." [Play it] | Same |
| Card toggle | "Include {companion}" | Same |
| Card suggestion | "Suggested: {mode}" · "Suggested: {mode} · you chose {mode2}" | — |
| Card restriction | "On prayer requests, only Prayerful or Peaceful." · "On scripture, {companion} stays calm." | — |
| Card reveal caption | "This is how it'll look when shared." | Same |
| Card words promise (prayer request) | "Your words stay exactly as you wrote them." | Same |
| Card anchor chip | "Below the words" · "Corner" | Same |
| Override fallback | "{mode} isn't offered on this kind of card." | — |

The emoji 🙏 appears only in the sermon snackbar and notification (§5.4 of the experience spec). It never appears
on the First Keep screen.

---

## 10. Build tasks

Conventions follow `ZURI_EXPERIENCE.md` §11: one engineer, one sitting, one commit. **S** is under about 2 hours;
**M** is one sitting. **Lane E** = the companion-engine lane (`core/companion`, `core/ui/mm/companion`,
`tools/rive/companion`). **Lane F** = feature screens (`feature/*`, `core/create`, `core/notify`, `res`).

| ID | Title | Model | Size | Lane | Maps to | Depends | Acceptance criteria |
|---|---|---|---|---|---|---|---|
| **Z-35** | Moment strings: every §9 line in `strings_moments.xml` with placeholders | Haiku | S | F | Z-18 | — | No hard-coded moment copy; `{name}` only in the First Keep headline (lint or test) |
| **Z-26** | `MomentPlanner` + `MomentFacts`/`MomentPlan`, `Cap.DailyBudget` + `Moment.Unveil`, `UnveilRotation` (SWRR + guard + familiarity), `UnveilTicket` + store (DataStore) | Sonnet | M | E | Z-2 | — | Every §3.3 row is a test; rotation: no repeats in 1,000 picks per space, proportions ±1 per cycle, Faith never yields Lift or Plant; ticket expires at 24 h and is consumed once; the reducer's `NoteReady` uses the planner's caps (no double counting) |
| **Z-27** | `MomentSpecs` data table (First Keep, 5 Unveils, Nod, Card reveal), `MsKey`/`StageTrack`/`PoseTrack` evaluator, `MomentClock` + `LocalMomentTime`, new pose channels (LOOK_X, LOOK_Y, EYES, SIGNATURE) with the **Zuri and Nas** takes | Sonnet | M | E | **Z-16** (engine half) | Z-26 | Values match this spec (table-driven test); tracks within `baseMs`; scale and clamp applied in one place; the evaluator allocates nothing per frame |
| **Z-28** | `MomentStage` + `Modifier.momentLayer` + layers (bloom, halo, sparkles, curtain, glow, ripple, underline, caret, sprouts) + skip (non-consuming) + Back + haptics + the live-region announcement + reduced-motion statics | Sonnet | M | E | **Z-16** | Z-27 | A tap during play reaches the underlying button (test); Back skips; no haptic after a skip; the clock stops at the end; announced once (semantics test) |
| **Z-29** | `FirstKeepOverlay` (card, headline, sub line, actions slots; the quiet Faith version), `NoteUnveil`, `CardReveal` | Sonnet | M | E | **Z-16 / Z-22** | Z-28 | Layout per §2.3 end state at 360–600 dp widths and 200% font; the card shows only verbatim title and key points (slot API, no text generation); `CardReveal` settles to exactly the `StaticPoses` export pose (pixel test against `renderCompanionBitmap`) |
| **Z-30** | `MomentGoldenTest` (§7.9 engine sets) + Companion Lab → **Moments** tab (moment, variant, form, space, scrubber, speed) | Haiku | S | E | Z-6 | Z-29 | The 55 engine goldens are committed; the Lab is debug-only (existing test pattern) |
| **Z-16** *(revised)* | **Wire the First Keep**: emit `NoteReady` + write `UnveilTicket` on `SUCCEEDED` (`isFirstEver` from the READY note count; `sensitive` from the type and tags); `MeetingDetail` and `WrapUp` consume the ticket → planner → `FirstKeepOverlay`; Open dismisses; Share opens Create with the note's title as the source | Sonnet | M | F | **P-5** | Z-29, Z-35 | Once ever (ledger); plays over an already-rendered note; reopening is plain; no `MainActivity` change; never on failed, partial, sensitive or Good Friday notes; screen goldens §7.9 |
| **Z-33** | **Wire the Unveil**: `NoteUnveil` around the MeetingDetail and WrapUp headers; the snackbar path ("{title} is ready" + Open, nod in the leading slot); queue while recording → one combined snackbar; special cases §4 (PLAIN + StatusLine for partial, fallback, model-required, no-speech) | Sonnet | M | F | **P-5 / P-6** | Z-16 | Every §4 row is demonstrated in a UI test or golden; Work uses ≤ 48 dp and stays in the header zone |
| **Z-34** | Note-ready notification: `ic_zuri_glyph` small icon, §9 copy (sermon 🙏, on-device line), deep link to the note, the `CompanionNotification` cap with the plain fallback beyond it | Haiku | S | F | Z-13 | Z-35 | Glyph in both themes; a status notification always posts (only its *voice* is capped); the deep link opens the note and the ticket plays the moment |
| **Z-22** *(revised)* | **Create**: `CreateDesign.companionPose` override chips (policy-filtered, hidden not greyed, the suggestion label), `CompanionAnchor` + `CompanionPlacement` (pure) in `core/create`, the renderer using the placement rect and 2× supersampling, a safe-zone overlay toggle in the Design step, the `CardReveal` call site, the prayer request rules §5.6 | Sonnet | M | F | **V-0a / P-5** | Z-29 | The placement invariants are unit-tested for all formats; a prayer request offers only Prayerful/Peaceful and never animates beyond a breath; text unchanged (a test compares the input text and the rendered layout text); the export matches the editor's settled pose |
| **Z-31** *(stretch)* | Rive moment inputs (`moment`, `momentVariant`, `momentPlay`, `momentSkip`, `gentle`) in the contract as **optional**; generator timelines; a Lab duration check vs `MomentSpec`; renderer selection per moment | Sonnet | M | E | Z-19 | Z-27 | A file without the inputs keeps working (Canvas moments); timeline length within ±1 frame of the spec or that moment falls back; the speed-binding question answered in the report |
| **Z-32** *(when in roster)* | Wren and Page takes (First Keep perch and become-the-note; 5 Unveil Signatures) | Sonnet | M | E | Z-23 / Z-24 | Z-27, Z-28 | Matches §2.4 and §3.4; goldens for both forms (First Keep 4 frames, 5 Unveil peaks) |

**Order:**

1. Z-35, Z-26 (in parallel)
2. Z-27 → Z-28 → Z-29 → Z-30 (lane E)
3. Then Z-16 → Z-33, Z-34, and Z-22 in parallel (lane F)
4. Stretch: Z-31, then Z-32 when Wren and Page enter the roster

**Mapping:** Z-16 (peak moments) is now the First Keep wiring plus the engine tasks Z-27–Z-29. The Unveil
replaces "daily full celebrate vs nod". Z-22 (Create integration) gains the override, the placement and the reveal.
Z-26 to Z-35 are new.

---

## 11. Ideas (not commitments)

Each idea comes with a one-line sanity check.

1. **IDEA: the animated card export** (2 s MP4, §5.5). *Sanity:* about 2 s of encoding and ~0.7 MB per share, with
   no new dependency; worth an experiment behind a flag. Never for prayer requests.
2. **IDEA: Dawn knows the time.** After 18:00, Dawn becomes **Dusk** (a cool tint, settling downward). After 23:00
   the nod is a sleepy-soft one. *Sanity:* time-of-day only, no data; a few lines in `MomentSpecs`.
3. **IDEA: haptic signatures per form** on the First Keep only: Nas = 3 decaying ticks (the wag), Wren = 2 quick
   (the flutter), Page = one paper "tick". *Sanity:* respects the system haptics setting; once ever, so no fatigue.
4. **IDEA: the First Keep returns once, on the 10th note** (the existing Proud milestone): Zuri briefly holds a tiny
   copy of the first card. "Ten notes kept." *Sanity:* once ever, skippable, no counter beyond it; the risk of
   nostalgia bait is low because it never repeats.
5. **IDEA: seasonal unveils** (Phase C, with the Seasonal toggle): Advent Dawn with a single soft star glint;
   Spring Plant grows one leaf. *Sanity:* never on Good Friday; no costumes; an off switch already exists.
6. **IDEA: a Live Update notification for long processing** (Android 16 progress-centric notifications) using the
   glyph and the real stage caption. *Sanity:* API 36+ only, falling back to today's progress notification;
   status only, never voiced.
7. **IDEA: a shared sound motif.** If the First Keep chime ships, record start and stop could use the same two
   notes, so the product has one sound. *Sanity:* off unless the founder wants sound at all; respects the ringer
   mode.
8. **IDEA: "Share a moment" pre-fills from the note's best line** (verbatim key point, user-picked). *Sanity:* only
   verbatim text; never on Faith sources without the faith contract.

---

## 12. Decisions (founder, 2026-10-12)

| # | Question | Decision |
|---|---|---|
| 1 | Which note gets the First Keep? | **Both**: the onboarding say-hello note and the first real recording (§2.2). Two once-ever keys. |
| 2 | The Unveil budget | **2 a day**, ≥ 3 h apart (§8). |
| 3 | Sound | **Soft chime ships**, with a "Sounds" switch in Advanced (§2.5). |
| 4 | Companion off | The note header **still fades up** (header fade only, no character). |
| 5 | Wren perching on the First Keep card | **Yes**, and the broader rule changes: user experience comes first. "Never inside notes" is replaced by "never cover, edit or replace the user's words" (§0, ZURI_EXPERIENCE §5.10). The sacred-moment rows there (prayer list, prayer requests, guided Prayer, Bible study steps) are unchanged unless the founder says otherwise. |
| 6 | Curtain in Faith | **Keep** at weight 1. |
| 7 | Animated card export | **Out for now.** It stays in §11 as an idea. Nothing is built for it, and no task is scheduled. |
| 8 | "Share a moment" on the First Keep | **Keep** (§5.2). |

| 9 | Should the First Keep play on every note? | **Yes, for now** (later 2026-10-12). `CompanionFlags.firstKeepEveryNote = true`; the Unveil rotation stays dormant behind it (§3.3). The chime stays on the two once-ever plays. Variations come later. |

The original questions and recommendations are kept in git history.
