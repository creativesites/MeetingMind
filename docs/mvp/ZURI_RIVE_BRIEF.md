# Zuri in Rive: the authoring brief (Z-19)

**For:** the founder or a motion designer building the companion files in the Rive editor.
**Status:** the app side is built and merged. The app renders all four forms on Canvas today, and
switches to Rive on its own as soon as a `.riv` file is bundled. Nothing in the code has to change
when the files arrive.

**Read with:**

- `docs/mvp/ZURI_EXPERIENCE.md`: §2 (look), §3 (states and motion), §10 (implementation)
- `docs/mvp/zuri-options.html`: the approved reference. Its `DRAW.orb`, `DRAW.nas`, `DRAW.wren`,
  `DRAW.folio` and `pose()` functions are the geometry and motion to match.
- The Canvas goldens in `app/src/test/screenshots/companion/`: 240 images of every form, state and
  Create mode in light and dark, at 24 dp and 96 dp. The Rive files should match these still frames.

---

## 1. How the app uses Rive

There are two renderers behind one composable, `Companion(form, state, size, …)`:

| Renderer | Used when |
|---|---|
| **Rive** | **All** of these hold: the slot is ≥ 48 dp; system animations are on (animator duration scale > 0); the form's `.riv` is bundled; the screen has a free live-Rive slot (budget 2, `LocalCompanionRiveBudget`); `CompanionFlags.rive` is on; and the file passed the contract check below. |
| **Canvas** | Everything else: the T0 glyph under 48 dp; reduced motion (static poses); screenshot tests; Create card export to a bitmap; any form without a `.riv`; any file that fails the contract. |

So Rive draws only the living character at medium and large sizes. The small glyph, the
reduced-motion stills and card exports stay on Canvas on purpose, and the Rive file does **not**
need a 24 dp variant.

**Fallback is automatic and silent to the user.** If a file is missing, the state machine is
missing, a required input is missing, or the view model or a required colour is missing, the slot
draws on Canvas and logs one line under the logcat tag `Companion`. Companion Lab shows the reason.

**Lifecycle.** The app pauses the Rive view when the slot is off screen or the screen is not
resumed, so the file never ticks in the background. Do not rely on wall-clock time inside the file.

---

## 2. Files

| Form | File | Artboard |
|---|---|---|
| Zuri (the Orb) | `zuri.riv` | `Zuri` |
| Nas (the puppy) | `nas.riv` | `Nas` |
| Wren (the songbird) | `wren.riv` | `Wren` |
| Page (the folio) | `page.riv` | `Page` |

**Location:** `app/src/main/assets/companion/`. The names are exact and lower case.

**One file per form (recommended) rather than one file with four artboards.**

- **The app loads only what it shows.** A user has one form. With one file, every launch would load
  and parse all four characters to show one.
- **Each form ships when it is ready.** Zuri can land and go live while Wren is still in progress.
  The app switches per form: whichever files are bundled render in Rive, and the rest stay on Canvas.
- **Separate budgets and reviews.** A file over budget or failing the checklist blocks only its form.
- **Less merge pain.** Two people can work on two forms at once.
- **The cost is small.** The shared parts (the face rig, sparkles, the state machine layout) are
  duplicated across files. Build Zuri first, then use it as the template: duplicate the file,
  rename the artboard, redraw the body, and keep the state machine, inputs and view model as they are.

---

## 3. The contract (exact names)

Source of truth in code: `core/ui/mm/companion/rive/CompanionRiveContract.kt`.

### 3.1 Artboard

- **Size: 500 × 500.** The prototype draws in a 100 × 100 unit box. In Rive, **1 unit = 5 px**,
  origin top-left, so prototype point (50, 58) is (250, 290).
- The app shows the artboard with `Fit.CONTAIN`, centred, in a square slot. Keep everything inside
  the artboard: the halo, sparkles, dots and z's included. The Zuri halo (prototype r 44 around
  (50, 60)) is the one element that reaches the bottom edge; shrink it to r 40 if it clips.
- Transparent background.

### 3.2 State machine: `Companion`

| Input | Type | Range / values | Required | Meaning |
|---|---|---|---|---|
| `state` | Number | 0–8, see §3.3 | Yes | The app state. |
| `level` | Number | 0–100 | Yes | Mic (or TTS) level, already smoothed by the app (60 ms attack, 250 ms release), sent at most once per frame and only when it changes by ≥ 0.5. |
| `mode` | Number | 0 = none, 1–6, see §3.3 | Yes | A Create-card pose. **When non-zero it overrides `state`.** |
| `celebrate` | Trigger | — | Yes | Fired when Celebrating starts (and for the Celebratory mode). Plays the hop once. |
| `nod` | Trigger | — | Yes | The 600 ms nod (a later note on the same day, a tap, a new form pick). |
| `calm` | Boolean | false | Yes | Sermon quiet: show the Peaceful pose, ignore `level`, no rings, no one-shots. |
| `blink` | Trigger | — | No | One blink. If absent, the file blinks on its own timer (§4). |
| `lookX`, `lookY` | Number | −100..100 | No | Parallax gaze. The app does not send these yet (T1 parallax is Z-14); default 0. |

The app reads input names on load. A missing **required** input means Canvas for that form.

### 3.3 Number mappings (append-only; never reorder)

`state`:

| Value | State | MVP |
|---|---|---|
| 0 | Idle | ✓ |
| 1 | Listening | ✓ |
| 2 | Thinking | ✓ |
| 3 | Celebrating | ✓ |
| 4 | Worried | ✓ |
| 5 | Sleepy | ✓ |
| 6 | Curious | Phase B |
| 7 | Proud | Phase B |
| 8 | Reading | Phase B |

`mode`: 0 none · 1 Prayerful · 2 Peaceful · 3 Grateful · 4 Joyful · 5 Reflective · 6 Celebratory.

Phase B states (6–8) may be missing in the first files: route them to Idle until they are built.

### 3.4 Colours: data binding (view model `CompanionTheme`)

The companion wears the user's accent (6 accents × light/dark = 12 palettes), so **no colour is
baked in**. Every fill and stroke binds to a colour property of a view model:

1. Create a view model named **`CompanionTheme`**.
2. Add these **colour** properties (exact names):

   | Property | Use | Required |
   |---|---|---|
   | `accent` | Listening rings, Page ribbon, the writing caret | Yes |
   | `bodyTop` | Body gradient start (top-left stop) | Yes |
   | `bodyBottom` | Body gradient end | Yes |
   | `deep` | Sprout, ears, tail, wing, beak, paw outlines | Yes |
   | `light` | Belly, snout, paws, the page fold | Yes |
   | `eye` | Eyes, brows, mouth, nose | Yes |
   | `blush` | Cheeks (already 34% alpha) | Yes |
   | `sparkle` | Thinking dots, every other sparkle | Yes |
   | `gold` | The other sparkles, the Proud glint | Yes |
   | `shadow` | Ground shadow (alpha included) | Yes |
   | `rim` | T1 rim light (if you add one) | No |
   | `paper`, `paperLine`, `lines` | Page only: paper, its outline, its text lines | Page: yes |
   | `mute` | Sleepy z's | No (falls back to the authored colour) |
   | `halo` | Zuri halo (alpha included) | No |

3. Set the default instance's values to **Indigo, light** (below) so the file looks right in the
   editor.
4. Make `CompanionTheme` the artboard's view model, so that **auto-binding** attaches the default
   instance. The app loads the file with `autoBind = true` and then writes the 16 colours from
   `companionPalette(accent, dark)`.
5. Colours that need their own opacity (the halo, the shine, the 0.85 belly) should use the
   **shape's opacity**, not the colour's alpha, so the binding stays a plain colour.

Indigo light defaults:

| Property | Hex | Property | Hex |
|---|---|---|---|
| `accent` | `#5B5BD6` | `eye` | `#151827` |
| `bodyTop` | `#ADADEA` | `blush` | `#F4728A` at 34% |
| `bodyBottom` | `#6F6FDB` | `sparkle` | `#5B5BD6` |
| `deep` | `#4A4AA7` | `gold` | `#B7791F` |
| `light` | `#DEDEF7` | `shadow` | `#18181B` at 9% |
| `paper` | `#F7F7FD` | `lines` | `#8484E0` |

**Why data binding.** The pinned runtime (rive-android 11.12.1) supports view-model data binding:
`ViewModelInstance.getColorProperty(name).value = argb` with auto-binding. That gives the designer
named, previewable colour slots in the editor and lets the app restyle all 12 palettes without
touching shapes at runtime. The older alternative, finding named shapes and overwriting solid
fills, breaks on gradients and on any rename. It was rejected.

---

## 4. Structure and motion per form

Pivots and coordinates are prototype units; multiply by 5 for the artboard. "Body transform" is
`translate(0, dy) → rotate(tilt) about the foot → scale(sx, sy) about the foot`, as in the prototype's
`bodyT()`. Build it as one group (or one root bone) named **`body`** with its origin at the foot.

Bones are optional: each form is a few rigid parts, so groups with set origins are enough. Use bones
only where you want a soft bend (Nas's ears, Wren's tail).

### 4.1 Shared face (all forms)

| Part | Shape | Variants (states) |
|---|---|---|
| `eyeL`, `eyeR` (Wren: `eye`) | Ellipse, `eye` fill, with a white catch-light circle (r = 0.36·rx at −0.3rx, −0.42ry) | **open** · **soft** (60% height, +0.28ry lower) · **happy** (∩ arc, stroke) · **closed** (∪ arc, stroke). Blink: 160 ms, the height to 12%, the catch-light hidden past 40%. |
| `cheeks` | Ellipses, `blush` | Hidden in Worried |
| `mouth` | Smile arc (stroke 0.5w) | open (filled, with tongue `#F47A8A`) · o (small ellipse) · wobble · none |
| `brows` | Two strokes, inner ends raised | Worried only |
| `hands` | Two ellipses rotated ±18°, `bodyTop` fill, `deep` 0.9 stroke | Prayerful only |
| `fx` | Four sparkles (4-point stars), three dots, three z's, a heart | Celebrating, Joyful (2 sparkles), Thinking, Sleepy, Grateful, Proud (one gold glint) |

The z's are **shapes**, not text: don't embed a font.

### 4.2 Zuri (artboard `Zuri`)

| Group | Geometry (units) | Animated |
|---|---|---|
| `halo` | Radial gradient circle c(50, 60) r 44: `accent` at 20% (light) / 32% (dark), solid to 55%, then fading | Moves with half of `dy` |
| `rings` | Two stroked circles c(50, 58) r 31, 1.4 stroke, `accent` | Listening only |
| `shadow` | Ellipse c(50, 93) 36 × 5.2 | Width shrinks during a hop (×(1 − 0.35·hop)) |
| `body` (origin at foot (50, 88)) | Circle c(50, 58) r 30, radial gradient `bodyTop` → `bodyBottom` centred (41.6, 46), r 51 | dy, sx, sy, tilt |
| `body/sprout` (origin (50, 30), behind the body) | Three round rects x 43.5/50/56.5, w 4.4, r 2.2, bottom at y 34, height h + 4 | Bar heights (3–16), lean |
| `body/shine` | Ellipse c(39, 44) 17 × 9.2, rotated −35°, white 42% (light) / 28% (dark) | — |
| `body/face` | Eyes (41, 56)/(59, 56) rx 3.3 ry 4.6, stroke 2.4 · cheeks (33.5, 65)/(66.5, 65) · mouth (50, 65), w 3 · hands at (50, 76) | per §4.1 |
| `fx` | Sparkles (16, 30, 5), (84, 24, 4), (88, 62, 3.5), (12, 64, 3.5) · dots from (67, 20), 6.5 apart, r 2.1 · z's from (70, 32) | |

Sprout heights per state: Idle 5/8/5 · Listening 3.5 + L·(7 + 5 sin(9t + i·1.9)), middle +2 ·
Thinking pulses 4 → 6.5 in sequence · Celebrating 8/12/8 · Joyful 6 ± 2 · Worried and Sleepy 3/4/3
leaning 14° · Prayerful 3/5/3 leaning 10° · Peaceful 4/6/4 leaning 6°.

### 4.3 Nas (artboard `Nas`)

| Group | Geometry | Animated |
|---|---|---|
| `shadow` | Ellipse c(50, 93) | hop |
| `body` (origin (50, 91)) | Oval c(50, 77) 35 × 27, gradient · belly c(50, 80) 18 × 16 `light` 85% · paws c(42.5, 89)/(57.5, 89) 11.2 × 6.8 `light` | dy, sx, sy, tilt |
| `body/tail` (origin (64, 76)) | Round rect at (63, 72) 16 × 6.5, r 3.25, base rotation −35° | Wag angle (rotation −35° − tail) |
| `body/head` (origin (50, 60)) | Circle c(50, 42) r 21, gradient · patch c(59, 40) 14 × 13 `deep` 28% · snout c(50, 51) 19 × 14 `light` · nose c(50, 47.6) 7.2 × 5.2 `eye`, with a small white shine · mouth under the nose | Head tilt, head drop |
| `body/head/earL` (origin (33, 31)) | Oval c(30.5, 44) 15.2 × 29 `deep` | Ear angle (+) |
| `body/head/earR` (origin (67, 31)) | Oval c(69.5, 44) | Ear angle (−) |
| face | Eyes (42, 40)/(58, 40) rx 3.1 ry 3.8 · cheeks (37.5, 49)/(62.5, 49) | |
| `paws-up` | Two ovals c(47.2, 64)/(52.8, 64) 8 × 12.4, ±14° | Prayerful only (the standing paws hide) |
| `fx` | Sparkles (12, 26, 4.5), (88, 20, 4), (92, 64, 3.5), (9, 66, 3) · dots (68, 10) · z's (76, 22) | |

Ears: Idle 12° ± 1.5 with the breath · Listening 12 + 16·L (+ a 4° flutter) · Thinking 16 ·
Celebrating 24 ± 6 · Joyful 18 · Worried 2 · Sleepy 4 · Prayerful 5 · Peaceful 8 · Grateful 10.
Head tilt: Listening −10 ± 2 · Thinking 8 · Reflective −6 · Sleepy 8 (drop 4) · Peaceful 4 ·
Grateful 6. Tail: Idle sways ±5 (slow, 1.1 rad/s) · Celebrating: a 7 Hz wag of 28° decaying to 0
over 1.2 s · Worried 35 · Sleepy 20. **Celebrating hop is half of Zuri's.** Nas never wags on
Prayerful.

### 4.4 Wren (artboard `Wren`)

| Group | Geometry | Animated |
|---|---|---|
| `arcs` | Three quadratic strokes from (x, 26) via (x − 6, 36) to (x, 46), x = 95 − 9k, 1.6 stroke, `accent` | Listening only |
| `shadow` | Ellipse c(50, 92) | hop |
| `body` (origin (50, 88)) | Circle c(49, 60) r 25, gradient · legs (45, 80)→(43.5, 89) and (55, 80)→(56, 89), 2.4 stroke `deep` · belly c(57, 69) 26 × 21 `light` 90% | dy, sx, sy, tilt |
| `body/tail` (origin (34, 54)) | Path M32 58 Q16 44 15 24 Q24 27 40 48 Z, `deep` | Tail angle |
| `body/head` (origin (58, 50)) | Circle c(62, 40) r 15.5 `bodyTop` · beak M76 38 L87 41.8 L76 45.5 Z (open: two triangles) · eye (68, 37) rx 2.8 ry 3.4, gaze ×0.8 · brow (63.5, 30.5)→(71.5, 33) · cheek (70, 45.5) | Head tilt, head drop |
| `body/wing` (origin (40, 57)) | Path M28 60 Q42 45 63 59 Q47 73 28 60 Z, `deep` | Wing angle |
| `fx` | Sparkles (14, 30, 4.5), (88, 22, 4), (90, 66, 3.5), (12, 72, 3) · dots (70, 14) · z's (74, 26) | |

Tail: Idle ±2 · Listening 6 + 10·L flick · Celebrating 12 · Worried −26 · Sleepy −18 · Prayerful −8.
Wing: Celebrating −30 − |sin(11t)|·25 (a double flap) · Joyful −12 · Worried 6 · Prayerful −16
(folded in front). Head: Prayerful bowed 14° and dropped 3. **Wren is not a dove:** keep the tail
cocked and the colour warm.

### 4.5 Page (artboard `Page`)

| Group | Geometry | Animated |
|---|---|---|
| `shadow` | Ellipse c(50, 95) | hop |
| `body` (origin (50, 84), plus a `lean` rotation about the same point) | | dy, sx, sy, tilt, lean |
| `body/ribbon` | M36 78 H44 V94 L40 90 L36 94 Z, `accent` | — |
| `body/page` | Rounded page 28..72 × 20..82 with the top-right corner cut by the fold F: `paper` fill, `paperLine` 1.4 stroke | Fold size F (12 → 17) |
| `body/fold` | The folded corner triangle, `light` | F |
| face | Eyes (42, 40)/(56, 40) rx 2.9 ry 3.9 · cheeks (36.5, 47)/(61.5, 47) · mouth (49, 47.5), w 2.6 | |
| `body/lines` | Three strokes from x 35, y 58/65/72, lengths 28/22/15, 2.2 stroke, `lines` | Listening: they become a waveform (amplitude 3.4·L, enveloped by sin over the line) · Thinking: they write themselves at 16 units/s, with a blinking `accent` caret · Sleepy 35% opacity · Worried 55% and dashed (3, 3.5) · Prayerful: replaced by folded hands at (49, 66) |
| `fx` | Sparkles (16, 26, 4.5), (86, 20, 4), (88, 60, 3.5), (14, 66, 3) · dots (68, 10) · z's (74, 22) | |

The paper stays **light** in both themes (the binding handles it). Fold: Celebrating 12 ± 3 flutter ·
Worried 17. Lean: Worried −4 · Sleepy 4 · Prayerful and Grateful 3.

---

## 5. States: motion notes (from spec §3.1)

All durations follow the system animator scale on the app side. **Loops** repeat while the state is
active; **one-shots** play once on entry (or on their trigger) and hold their last frame. State
changes blend over **250 ms** (`MM.motion.standard`); use 250 ms blend transitions between all states.

| State | Pose | Motion | Timing |
|---|---|---|---|
| **Idle** (0) | Eyes open with catch-lights, small smile | Breath: scaleY 1 ± 0.022, scaleX 1 ∓ 0.012, sine, pivot at the foot. Blink 160 ms every 4.6 s (± 0.8 s jitter). | Loop, 3.4 s |
| **Listening** (1) | Soft eyes (60%), tilt −5°, looking slightly right (+0.6) | Driven by `level` L = level/100: scaleY 1 + 0.035·L, scaleX 1 + 0.014·L; tilt −5° ± 2° (4.8 s). Form channels in §4. Ripple rings: two, period 1.5 s, alpha 0.18 + 0.55·L, growing to r·(1 + 0.42·(0.45 + L)). Use a 1D blend state on `level`. | Loop, driven |
| **Thinking** (2) | Looking up-right (+1.1, −1.5), mouth "o" | Sway ±3° (4.2 s). Three dots above, bouncing 3.2 units, staggered 170 ms, 1.5 s loop. Blink every 3.1 s. Page writes. | Loop, 1.5 s; author at 30 fps feel |
| **Celebrating** (3) | Happy eyes (∩), open smile, 4 sparkles (accent and gold alternating) | 1.6 s: anticipation squash 0–120 ms (sy 0.94); hop 120–620 ms (y −9, sy 1.05, ease-out); land squash 620–800 ms (sx 1.08, sy 0.92); settle with a soft spring (damping 0.8) to 1.6 s. Sparkles twinkle 0–1.4 s, then fade. Nas: hop ×0.5 + wag. Wren: double wing flap. | **One-shot** on `celebrate` |
| **Nod** (`nod` trigger) | Happy eyes, smile | 600 ms: one 3-unit dip, no sparkles | One-shot |
| **Worried** (4) | Droop: sx 1.03, sy 0.95, y +1.5; brows raised on the inner ends; wobbly mouth; eyes 0.88×, looking down (+0.7); no cheeks | Enter 250 ms. Slow sway ±1.5° (7 s). Blink every 3.8 s. | Loop, 7 s |
| **Sleepy** (5) | Closed eyes (∪), no mouth, tilt 6°, y +2 | Breath 5.5 s ± 2.5%. z's rise 16 units and fade over 2.6 s, staggered by a third. **3 cycles, then still** (two z's placed). | Loop ×3, then hold |
| Curious (6) | Head tilt 8°, lean in, eyes 1.1× | One-shot 600 ms on entry, then hold | Phase B |
| Proud (7) | Chest up (sy 1.04), happy eyes, one gold glint | One-shot 1.2 s, then Idle | Phase B |
| Reading (8) | Eyes glance left to right (2.4 s per sweep); the sprout or ears follow `level` | Loop while active | Phase B |
| **calm** = true | The Peaceful pose (below), 6 s breath | Ignore `level`; no rings, no one-shots | Overrides `state` |

**Never** add idle motion that grabs attention: no bouncing at rest, no random glances, no wagging
in Idle beyond the slow tail sway.

## 6. Create modes (`mode` 1–6, cards and the Create preview only)

| Mode | Pose | Motion (preview only) |
|---|---|---|
| 1 **Prayerful** | Eyes closed, no mouth, hands or paws folded at the chest (Nas: under the chin; Wren: wing folded in front, head bowed), sprout bowed 10° | Breath 6 s ± 0.8%, nothing else |
| 2 **Peaceful** | Eyes closed, soft smile | Breath 6 s ± 1.5% |
| 3 **Grateful** | Happy eyes, smile, 4° bow, a small heart | Heart floats ±1.5 (1.6 rad/s) |
| 4 **Joyful** | Happy eyes, open smile, 2 sparkles | Bob 3 units at 2.6 rad/s |
| 5 **Reflective** | Looking up-left (−1, −1.5), smile, tilt −3° | Blink every 4.2 s |
| 6 **Celebratory** | The Celebrating pose | The full hop |

Card **export** is always drawn by the Canvas renderer from the static pose, so a mode never needs
an "export frame" in the file.

## 7. Export settings

- Export from the Rive editor as a **runtime `.riv`** (File → Export → For runtime).
- **Vector only.** No raster images, no embedded fonts (the z's are shapes), no audio, no text runs.
- One artboard per file, named as in §2. Delete unused artboards and timelines before export.
- Keep the editor's runtime compatibility at or below what rive-android **11.12.1** reads
  (data binding and auto-binding are supported). If the editor warns about a newer feature,
  don't use it.
- No events are needed. The app does not listen to Rive events.

## 8. Size budget

| | Per file | Why |
|---|---|---|
| **Target** | ≤ 60 KB | A vector character with one state machine is typically 20–60 KB. |
| **Hard cap** | 120 KB | Above this, simplify the paths and timelines. |
| All four files | ≤ 300 KB | |

The runtime itself costs far more than the files: see §11.

## 9. Acceptance checklist (per file)

1. [ ] File name, artboard name and state machine name exactly as in §2 and §3.
2. [ ] All required inputs exist with the right types (§3.2). The Lab's renderer line reads
   `RIVE (RIVE_READY)`, not `CANVAS (RIVE_FAILED)`.
3. [ ] View model `CompanionTheme` is the artboard's view model; all required colours (§3.4) exist and
   every fill/stroke is bound. Switch all 6 accents × light/dark in the Lab: **no** shape keeps
   a baked colour.
4. [ ] Each state (0–5) and each mode (1–6) matches its Canvas golden still frame in pose and
   proportion (`app/src/test/screenshots/companion/<form>/`). The motion matches §5.
5. [ ] `celebrate` plays once and settles; `nod` plays once; neither loops.
6. [ ] `calm` gives the Peaceful pose, ignores `level`, and hides the rings.
7. [ ] `level` 0 → 100 visibly drives Listening (the Lab's slider and "Simulate speech").
8. [ ] Sleepy stops after 3 z cycles; Idle has no attention-grabbing motion.
9. [ ] Nothing is clipped at the artboard edge (halo, sparkles, dots, z's).
10. [ ] At 48 dp the character still reads (the Lab's size ladder: 64, 96, 160 use Rive; 24 and 40
    stay Canvas by design).
11. [ ] File size within budget (§8).
12. [ ] Faith rules: no halo ring above the head, no cross, no praying hands outside Prayerful.
    Wren is not a dove.
13. [ ] Frame time: on a mid-range phone, the Lab hero at 160 dp holds 60 fps in Listening.
    The Rive view pauses when the app goes to the background (no CPU use in a trace).

## 10. Dropping the files in and checking them

1. Copy the file to `app/src/main/assets/companion/` (for example `zuri.riv`).
2. Build and install the dev app: `./gradlew installDebug`.
3. Open **Companion Lab**. It has its own launcher icon in debug builds ("Companion Lab"), or run
   `adb shell am start -n com.craftflowtechnologies.meetingmind.dev/com.craftflowtechnologies.meetingmind.feature.companionlab.CompanionLabActivity`.
4. "Bundled .riv files" lists the file. Pick the form; the hero's renderer line reads
   `Renderer: RIVE (RIVE_READY)`.
5. Walk the checklist: every state, every Create mode, Quiet and Nod variants, the level slider,
   accents, light/dark, and the size ladder.
6. Turn on **Reduced motion**: the hero switches to Canvas (`REDUCED_MOTION`) and shows the static
   pose. That is expected.
7. If the line reads `CANVAS (RIVE_FAILED)`, run `adb logcat -s Companion`: the log names what is
   missing (state machine, an input or a colour property).
8. Commit the file. No code change is needed. The screenshot goldens don't change: tests always use
   Canvas.

## 11. Runtime cost (measured)

rive-android **11.12.1** (published 2026-09-16). The library ships native code for four ABIs; this
app builds arm64-v8a and armeabi-v7a splits.

| | arm64-v8a | armeabi-v7a |
|---|---|---|
| `librive-android.so` (stored uncompressed in the APK) | 5.34 MB | 5.09 MB |
| `libc++_shared.so` (no other library in the app ships it) | 1.29 MB | 0.87 MB |
| **Split APK delta, measured** (debug build, this branch vs the commit before Rive; includes the companion code) | **+8.19 MB** | **+7.54 MB** |

Only the View-based `RiveAnimationView` is used. Rive's own Compose and lifecycle-compose dependencies are
excluded in `app/build.gradle.kts`, so adding Rive does not move the app off its Compose 1.7 / lifecycle 2.8
versions. The only transitive additions are volley, relinker, `customview` 1.1.0 and `startup-runtime` 1.2.0.

The library is loaded lazily: `Rive.init` runs only the first time a Rive companion is drawn, so
builds and sessions without `.riv` files never load the native code.
