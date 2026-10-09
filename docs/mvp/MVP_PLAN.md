# MeetingMind: the road to MVP

The master plan. It is built on `docs/mvp/AUDIT.md`, and agents work from it. Improve it **incrementally**: add,
amend or strike tasks. Do not rewrite it.

Status legend: ☐ not started · ◐ in progress · ☑ done · ⊘ cut

---

## 1. Principles (apply to every task)

> **Every surface in MeetingMind must earn its place.**

> **Do not equate discoverability with prominence.**

> **A recording is the source. The Note is the workspace. Everything else is a representation or output of that
> source.**

### The four questions

Every front-facing surface answers, at a glance:

1. **Where am I?**
2. **What's important here?**
3. **What can I accomplish here?**
4. **What should I do next?**

### The "Why is this here?" test

Apply it to every page, section, card, button, icon, label and interaction:

1. What user problem does it solve?
2. Why is it visible here?
3. Why is it this size?
4. Why does it look this prominent?
5. What does the user expect after interacting with it?
6. Does the result meet that expectation?
7. Could it be simpler?
8. Does removing it make the screen better?

### Psychology principles, and where we use them

| Principle | Where it lands in MeetingMind |
|---|---|
| **Smart defaults** | Onboarding pre-selects the most common choice on every step. The Record sheet pre-selects the type from the active space. |
| **Goal-gradient / never start at zero** | Onboarding progress starts at step 1 of 5 already filled ("Account ready ✓"). A "Make MeetingMind yours" checklist starts at 2 of 5. |
| **Reciprocity** | Value before asking: a sample note during onboarding, and the name/goal asked *after* the user has seen what the app does. Sign-in is never required. |
| **Peak-end rule** | Design the peaks: the first finished recording gets a celebration screen with the companion. Remove the lows: the 55% hang, silent AI fallbacks, and a 7-screen form. |
| **Labor illusion** | Processing narrates real work ("Listening for who's speaking… 3 voices so far", "Finding the scripture…"), never a frozen bar. |
| **Von Restorff** | Exactly one visually distinct primary element per screen (one hero card, one accent action). |
| **Choice overload** | One home (not four styles), 8 Settings groups (not 12), one Record button (not six), and secondary features behind a single "More" / "Explore". |
| **Emotionally intelligent design** | A companion character with states for listening, thinking, done, sleepy and worried (§6). |

---

## 2. Target product shape

```
Bottom bar:   Home  ·  Notes  ·  ( ● Record )  ·  Spaces
              Header on every top-level screen: [context switch] ... [search] [avatar → Settings]

HOME (one screen, adapts to the active space: All / Faith / Work / Learning)
  1. Greeting line ("Good morning, Ama") + companion + one context line
  2. NEXT: the one hero card. Next meeting (Work) · Today's Word (Faith) · next session (Learning)
  3. NEEDS YOU: at most 4 items (wrap up, send follow-up, tasks due, prayers)
  4. RECENT NOTES: always present, 5 rows, "All notes →"
  5. TODAY: compact agenda (other calendar views live in Notes/Calendar)

NOTES (the library)
  search field at top → filter chips (Space · Type · Has recording · Has tasks) → Pinned → Recent (date-grouped)
  Notebooks and tags live behind a "Folders" toggle, not above the list
  Each row: title · 2-line preview · icons (recording, transcript status, N tasks, attachments) · space dot · time

RECORD  one global button; the sheet pre-selects the type from the active space; Note and Import live in the sheet

SPACES  Faith · Work · Learning, each with its own hub (its extras: Bible, Prayer, Projects, People, Inbox…)
        Spaces the user didn't choose in onboarding are hidden, not disabled
```

Unchanged from the polish plan: Recording → Processing → Intelligence → Workspace (Note) → Outputs. Source data
(audio, original transcript) is never overwritten.

---

## 3. Decisions

All rows are **pending founder confirmation**. These are the CTO's recommendations; the founder's answer overrides any
one of them. Mark a row ☑ when confirmed.

| # | Question | Recommendation |
|---|---|---|
| D1 | Navigation | `Home · Notes · Record · Spaces`; Settings under the avatar; Search in the header |
| D2 | Default AI mode in onboarding | **On-device** when the phone qualifies, with Gemini offered as "Faster, uses the internet". Fixes the privacy promise. |
| D3 | Learning | Remove the UI now, keeping the tables (no schema bump). Re-spec from scratch (§5, phase 4). Drop the tables in a later release. |
| D4 | Fellowship + Circles | **Remove from the MVP build** (hidden, with the code deleted once the replacement spec is approved). Rebuild after launch on a proper backend design, because the current security model is broken. |
| D5 | Spark | Remove now. Re-spec as "Share a sermon moment": cards generated *from the user's own sermon notes* under the faith contract. |
| D6 | Companion character | Build it native in Compose (Canvas + vector paths, state-driven, no new dependency) so it ships with the app and themes with it. Rive can come later if a designer joins. |
| D7 | Launch scope | MVP = Faith + Work + **Learning v1 (lecture → study note → flashcards/quiz)**. If Learning v1 slips, launch with Learning shown as "Coming soon" in Spaces. |

---

## 4. The team

| Role | Model | Owns |
|---|---|---|
| CTO / architect | **Opus 5.5** (main session) | This plan; specs; design system; reviewing every diff; merging; the build gate |
| Senior engineer | **Sonnet 5.5** | Multi-file features with judgement: pipeline, navigation, Home, Notes, Work, onboarding, companion |
| Engineer | **Haiku 5.5** | Well-specified mechanical work: removals, flag-offs, literal-to-token sweeps (one file per task), copy fixes, settings dedupe, unit tests, inventories |

**Operating rules for agents:**

- **Every agent prompt starts with `docs/mvp/AGENT_RULES.md`.**
- **One task means one isolated worktree** (`isolation: "worktree"`) and one focused commit.
- **The CTO reviews and merges** into `claude/meetingmind-mvp-planning-7dgowm`.
- **Build gate before every merge:** `./gradlew compileDebugKotlin testDebugUnitTest` must be green.
- **Haiku tasks must be fully specified:** files, the exact change, and the acceptance check. If a Haiku task needs
  judgement, it is a Sonnet task.
- **Parallelise only across disjoint files.** `MainActivity.kt`, `AppNavigation.kt` and `MeetMindDatabase.kt` are
  hot files, so changes to them are serialised through one task at a time.

---

## 5. Phases and tasks

The model column is who executes. ★ = blocks launch.

### Phase 0: Stabilise (trust first)

| ID | Task | Model | Acceptance |
|---|---|---|---|
| S-1 ★ | Diarization budget + fallback + chunked progress + heartbeat + failed-on-stop + orphan reconcile + retry from ASR checkpoint + cleanup budget (AUDIT §1) | Sonnet | JVM test: a never-returning diarizer falls back within budget; percentages are monotonic; a 2 h recording completes on device |
| S-2 ★ | Truthful stage labels: ASR 35–52, speakers 52–60 with "chunk i/n", cleanup 60–66; stalled warning after 2 min with no heartbeat | Sonnet (with S-1) | No two stages share a percent; the UI shows elapsed time and the current stage |
| S-3 ★ | Silent-degradation fixes: sermon note without AI says so with a Retry; Spark/Gemini fallbacks surface a message | Haiku | Each fallback path shows a visible line plus a retry |
| S-4 | `IntegrationRegistry` reads move from `runBlocking` getters to a cached `StateFlow` | Haiku | No `runBlocking` in `core/integrations` |
| S-5 | Flag off for MVP: Recipes, Kanban, Timeline, X-Ray, Saved views, email providers, the 8 stub integrations | Haiku | `BuildConfig` flags default false; no entry point visible |
| S-6 | `WorkViewModel`: projection query for titles, LIMIT on tasks, a grouped count for projects; NavGraph scope instead of Activity; `WrapUpViewModel` via `viewModel()` + `SavedStateHandle` | Sonnet | Unit tests for the DAO queries; the draft survives process death |
| S-7 ★ | Learning removal, steps 1–4 of AUDIT §7 (UI, routes, entry points; tables kept) | Haiku | Build green; no "Learn" anywhere in the UI; app opens on a v42 DB |
| S-8 ★ | Fellowship/Circles/Spark: remove entry points and routes; drop the `mindcircle` intent filter; leave the code compiled but unreachable | Haiku | No entry point; deep link inert |
| S-9 | Onboarding copy and defaults hotfix (D2): default to on-device when the phone qualifies; permissions asked once, in context | Haiku | Welcome copy matches the default; no permission dialog at launch |

### Phase 1: Foundations (design system + navigation)

| ID | Task | Model | Acceptance |
|---|---|---|---|
| F-1 ★ | Write `docs/mvp/DESIGN_SYSTEM.md` and implement tokens in `ui/theme`: `MMType` (8 steps), `MMSpace` (4/8/12/16/24/32), `MMRadius` (8/16/pill), elevation (0 + floating), motion (200/300 ms, one spring), per-space accent | **Opus** | Tokens compile; a sample screen uses only tokens |
| F-2 ★ | `core/ui` components: `ScreenHeader`, `SectionHeader`, `MMCard`, `HeroCard`, `InsetPanel`, `ListRow`, `PrimaryAction`, `FilterChip`, `EmptyState`, `ProcessingPill`, `NoteRow` | Sonnet | Screenshot tests for each in light and dark |
| F-3 ★ | Navigation per D1: the new bar, the space switcher, nested graphs `faith/*` `work/*` `learn/*`; delete `HomeStyle` and the `HomeSection` toggles; Settings moves to the avatar | Sonnet | One home route; every top-level screen has the bar; back stack verified |
| F-4 | Literal sweep, one file per task: `fontSize`/`Color(0x…)`/`RoundedCornerShape` → tokens, starting from the worst offenders in AUDIT §3 | Haiku ×N | Each file's grep for literals is down to 0 (scripture serif excepted) |
| F-5 | Remove legacy palettes (`CleanMac*`, `Dark*`), the duplicate `Slate`s and the three section-title copies | Haiku | Grep clean; build green |

### Phase 2: Surfaces

| ID | Task | Model | Acceptance |
|---|---|---|---|
| U-1 ★ | **One adaptive Home** (§2): greeting + companion, Next, Needs you, Recent notes, Today. Delete the sky hero, QuickAccessRow, focus chip, Work card and StoryRings on Home | Sonnet | Passes the four questions per space; ≤5 sections; no infinite animation at idle |
| U-2 ★ | **Notes library**: search first, multi-select chips, rich `NoteRow`, folders behind a toggle; library search goes through FTS and excludes private text | Sonnet | First note visible without scrolling on a 6" phone; search is ranked |
| U-3 ★ | **Work hub**: one screen (Pulse as the only hero → Needs you → Today → Tasks Mine/Waiting → Decisions); delete `WorkSpaceScreen`, metric tiles, shortcut pills and the template grid; extras go behind "⋯" | Sonnet | One Work hub; an empty section renders nothing |
| U-4 ★ | **Faith hub**: Today's Word, Daily rhythm, Record sermon, Recent sermons, Explore list; delete the stat cards and Backgrounds | Sonnet | ≤6 sections; serif only for scripture |
| U-5 ★ | **Settings IA**: 8 groups (Account · Recording · AI & offline models · Spaces · Appearance · Privacy & security · Storage & backup · About) + Advanced; dedupe the duplicated settings; remove dead prefs and dev copy; one Notifications screen | Haiku (spec from AUDIT §8) | 8 groups; each setting has exactly one place |
| U-6 | **Meeting detail as a workflow**: participants → summary → decisions → actions → follow-up/brief → export, as one thread | Sonnet | Every step reachable in ≤1 tap from the detail |
| U-7 | **Model management + offline readiness**: "● Ready to record offline" on the Record sheet; Process locally / with Gemini; recommended pack | Sonnet | The Record sheet shows the true readiness state |

### Phase 3: Personality (onboarding, companion, peaks)

| ID | Task | Model | Acceptance |
|---|---|---|---|
| P-1 ★ | **Companion spec**: name, shape, palette, the 6 states, where it appears, and where it must *not* appear | **Opus** + founder | Founder sign-off |
| P-2 ★ | **Companion implementation**: a Compose `Companion(state)` with idle breathing, listening (reacts to mic level), thinking, celebrating, sleepy and worried; reduced-motion support | Sonnet | Runs at 60 fps; respects the animator duration scale |
| P-3 ★ | **Onboarding v2**, 5 steps with a progress bar from step 1: Meet the companion → "What do you want to remember?" (choose spaces; the home preview updates live) → name ("What should I call you?") → AI choice with smart default → a 10-second "say hello" test recording that produces a mini note (reciprocity + first peak) | Sonnet | Done in under 90 s; the home reflects the choices; permissions asked in context |
| P-4 | **"Make it yours" checklist** starting at 2/5: first recording, first note edited, Bible chosen (Faith), calendar connected (Work), offline pack | Haiku | Progress persists; it dismisses itself when done |
| P-5 ★ | **Peak moments**: first-recording celebration, "Your note is ready" with the companion, streak/rhythm moments that are calm, not gamified | Sonnet | Shown once per milestone; skippable |
| P-6 | **Labor-illusion processing screen**: the companion is "thinking" plus narrated real stages from S-2 | Haiku | Copy is driven by real stage events |

### Phase 4: Vertical rebuilds

| ID | Task | Model | Acceptance |
|---|---|---|---|
| V-1 ★ | **Learning v1 spec** (`docs/mvp/LEARNING_V1.md`), built on note + transcript, not a parallel pipeline: record lecture → study note (overview, key concepts, definitions, examples) → flashcards + quiz (reusing the spaced scheduler) → "Teach me this" on a selected concept | **Opus** + founder | Founder sign-off |
| V-2 ★ | Learning v1 build | Sonnet (+ Haiku for tests) | Golden path on device |
| V-3 | Learning table drop: `MIGRATION_23_24` (+ remap `LEARNING` notebooks); delete `filesDir/teach_back` | Haiku | Migration test from 21, 22 and 23 |
| V-4 | **Sermon → share moments** (Spark replacement), spec then build: cards from the note's own quotes and scripture, under the faith contract | Opus spec → Sonnet | No AI text without a source in the note |
| V-5 | **Sermon → devotional** action; one faith-contract source of truth (assets only); retry on truncated extraction chunks | Sonnet | Unit tests for the chunk retry |
| V-6 | **Fellowship v2 spec** (post-MVP): server-assigned roles, member caps, key in the Keystore, honest copy | Opus | Spec only for MVP |

### Phase 5: Quality and release

| ID | Task | Model | Acceptance |
|---|---|---|---|
| Q-1 ★ | Golden paths on device: Faith sermon, Work meeting, Learning lecture, offline-only, Gemini-only, app killed mid-processing | Founder + Opus checklist | All six pass |
| Q-2 | Accessibility: 48 dp targets, content descriptions, font scale 200%, TalkBack on the 5 top screens | Haiku audit → Sonnet fixes | Checklist green |
| Q-3 | Performance: long transcripts, home recomposition, cold start | Sonnet | Cold start <1.5 s on a mid-range phone |
| Q-4 | Release: v43 notes, Data Safety update (D2), Play listing screenshots from the new UI | Haiku | Bundle uploaded to closed testing |

---

## 6. Companion: starting concept for P-1

A small, soft **listening creature** grown out of the existing `RecordOrb`, so the brand mark and the character are
the same object.

**Working names:** *Echo*, *Hush*, *Pip* or *Mimi*. The founder picks one.

**Shape.** A rounded drop, with two dot eyes and a small "sound-wave" crest that moves with the voice.

**States:**

| State | When | Motion |
|---|---|---|
| Idle | Home | Slow breathing |
| Listening | Recording | The crest pulses with mic amplitude; eyes half-closed and attentive |
| Thinking | Processing | Eyes look up; dots orbit |
| Celebrating | Note ready / first recording | Hop, then sparkles |
| Sleepy | Offline models missing / idle late at night | Closed eyes, "z" |
| Worried | A job failed | Small droop; paired with a Retry action, never alone |

**Where it appears:** the greeting, onboarding, recording, processing, the success moments and empty states.

**Where it never appears:** inside notes, Settings, or on top of content.

---

## 7. Execution order (what runs in parallel)

| Wave | Tasks |
|---|---|
| 1 | S-1/S-2 (Sonnet) ∥ S-4, S-5, S-7, S-8, S-9 (Haiku, disjoint files) ∥ F-1 (Opus) |
| 2 | F-2 (Sonnet) ∥ S-6 (Sonnet) ∥ S-3 (Haiku) ∥ P-1, V-1 specs (Opus + founder) |
| 3 | F-3 navigation (Sonnet, alone on the hot files) → then U-1 ∥ U-2 ∥ U-3 ∥ U-4 (Sonnet) ∥ F-4 sweeps (Haiku, on files not in flight) |
| 4 | U-5, U-6, U-7 ∥ P-2 → P-3, P-5 ∥ V-2 |
| 5 | V-3, V-4, V-5 ∥ Q-2, Q-3 → Q-1 → Q-4 |
