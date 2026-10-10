# Prompt: build the Zuri `.riv` files with a local agent

Paste everything below the line into Claude Code, running on your own machine inside your local clone
of MeetingMind, with the Rive CLI installed. First run
`git fetch origin ccr-5dc13c30-a5eqgv && git checkout ccr-5dc13c30-a5eqgv && git pull`.

---

You are building the four animated companions for MeetingMind's Android app as Rive files:
`zuri.riv`, `nas.riv`, `wren.riv` and `page.riv`. The app's code is already written and waits for
these files. Each form renders in Rive as soon as its file is bundled and passes the contract;
until then the app shows a Compose Canvas fallback.

## Read first (all in this repo)

1. `docs/mvp/ZURI_RIVE_BRIEF.md` is **the contract and the authoring brief**. Follow it exactly:
   - 500 × 500 artboards named `Zuri` / `Nas` / `Wren` / `Page`;
   - the state machine `Companion` with its inputs `state`, `level`, `mode`, `celebrate`, `nod`,
     `calm` (and optionally `blink`, `lookX`, `lookY`);
   - the number mappings in §3.3;
   - the view model `CompanionTheme` with the colour properties in §3.4, bound to every fill
     and stroke, and auto-bound;
   - the per-form structure in §4, motion per state in §5, Create modes in §6;
   - the export settings, size budget and acceptance checklist in §7–9.
2. `app/src/main/java/com/craftflowtechnologies/meetingmind/core/ui/mm/companion/rive/CompanionRiveContract.kt`
   holds the same names in code. If the brief and the code disagree, **the code wins**. Report the
   mismatch.
3. `docs/mvp/zuri-options.html` is the approved visual and motion reference. Its `DRAW.orb`,
   `DRAW.nas`, `DRAW.wren`, `DRAW.folio` and `pose()` functions give exact geometry (100-unit box,
   ×5 for the 500 px artboard) and timings. Open it in a browser to see every state moving.
4. `docs/mvp/ZURI_EXPERIENCE.md` §1–3 covers the character and faith rules. Prayerful means eyes
   closed and hands/paws folded with no bounce, and there is no halo ring above any head.

## Step 1: find out what the Rive CLI can actually do (before building anything)

Run `rive --help` and the help for every subcommand, and read any docs it links to. Then tell me
plainly which of these it can do:

- (a) create or edit a `.riv` file from a source description: shapes, bones, state machines,
  inputs, view models, data binding;
- (b) only export, validate, inspect or upload files authored in the Rive editor;
- (c) something else.

**If it can do (a),** build the files as below. **If it can only do (b),** do NOT fake it. Do
these instead and stop:

- write per-form editor build sheets (`docs/mvp/rive/<form>-build-sheet.md`) with exact
  coordinates, hierarchy, keyframes and binding steps for a human in the Rive editor;
- set up a validation script, described in Step 3, that uses the CLI to inspect a finished
  `.riv` against the contract.

## Step 2: build (only if the CLI can author)

- Build **Zuri first**, as the template: the face rig, the state machine and the view model.
- Then duplicate the file for Nas, Wren and Page: keep the state machine, inputs and view model,
  and redraw the body per §4 of the brief.
- Match the prototype's motion numbers: durations, easing, and loop vs one-shot from §5.
- `level` (0–100) must visibly drive Listening: the Zuri sprout, Nas's ears, Wren's tail and
  Page's lines.
- `calm` must give the Peaceful pose and ignore `level`.
- `mode` (when non-zero) overrides `state`.
- Default view-model values: Indigo light (§3.4 table).
- Keep each file within the §8 size budget.
- Save to `app/src/main/assets/companion/` with the exact lower-case names.

## Step 3: verify (mandatory; never claim a check you didn't run)

For each file:

- **Inspect it with the CLI.** Confirm:
  - the artboard name;
  - the state machine name;
  - every required input, by name and type;
  - the `CompanionTheme` view model with every required colour property;
  - that auto-binding is on;
  - the file size.
- **Go through the §9 acceptance checklist** item by item, marking each pass, fail or "needs a
  human eye".
- **If an Android device or emulator is connected:** run
  `./gradlew :app:installDebug`, open the
  **Companion Lab** (debug builds only), and check that the renderer line reads `RIVE`, not
  `CANVAS (…)`, for each form. Step through every state and mode, the mic slider, all 6 accents
  in light and dark, and reduced motion, which should switch to Canvas.
- **If the line reads `CANVAS (RIVE_FAILED)`:** run `adb logcat -s Companion`, then fix the file
  and recheck.
- **Run the unit and screenshot tests:** `./gradlew :app:testDevDebugUnitTest` (or the repo's
  equivalent). The goldens should not change, because tests always use Canvas.

## Step 4: commit

Commit on `ccr-5dc13c30-a5eqgv` with one commit per form, for example
`feat(companion): zuri.riv (Rive)`, and push. Do not open a PR.

Report back with:

- what the CLI could and couldn't do;
- for each file: its size, checklist results and Companion Lab result;
- anything in the brief you had to change, and why.
