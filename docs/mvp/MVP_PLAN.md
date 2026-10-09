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
              Header on every top-level screen: [space switch] ... [search] [avatar → Settings]

HOME: one home system, four home designs, all built from the same design system and components
  ├─ Everyday (NEW default)  general-purpose: greeting + Mimi, Next, Needs you, Recent notes, Today
  ├─ Faith                   the current "Today" home, rebuilt: daily word/devotional, scripture or quote hero,
  │                          stories, prayer and reading rhythm, sermons, recent notes. Same sections, coherent and beautiful.
  ├─ Work                    the current ProfessionalHome, rebuilt: Pulse (next meeting), Needs you, Today, Tasks, Decisions
  └─ Study                   new: next class, revise today (due flashcards/quiz), courses (notebooks), recent lecture notes
  The user picks their home in onboarding (smart default from the spaces they chose) and can switch any time.
  Recent notes are on every home.

PERSONALIZATION SYSTEM (one coherent system, replacing the scattered homeStyle / HomeSection / look / card-style /
  hero-mode settings)
  Basic:    home (Everyday/Faith/Work/Study) · accent colour (curated palette) · light/dark/system · name
  Advanced: hero content (scripture/quote/progress) · home sections on/off and order · text size · density
            · per-space accents
  Every option renders through design-system tokens, so no combination can look broken.

NOTES (the library)
  search field at top → filter chips (Space · Type · Has recording · Has tasks) → Pinned → Recent (date-grouped)
  Notebooks (= courses / projects / sermon series) and tags live behind a "Folders" toggle, not above the list
  Each row: title · 2-line preview · icons (recording, transcript status, N tasks, attachments) · space dot · time

RECORD  one global button; the sheet pre-selects the type from the active home or space; Note and Import live in the sheet

SPACES  Faith · Work · Study, each with its own hub of extras (Bible, Prayer, Fellowship, Projects, People, Courses…)

CROSS-CUTTING, everywhere the user sees their own content
  • Select & copy:  any text (transcript, summary, note, AI answer, devotional, verse, quiz explanation) is
                    selectable and copyable, down to a single sentence.
  • Read aloud:     notes, devotionals, scripture, summaries and study material can be read aloud
                    (on-device TTS, Gemini voice when online).
  • Share:          anything worth sharing can become a beautiful card (the Spark engine) or a document.
  • No empty promises: a prominent action (e.g. "Devotional", "Prayer") must lead to real functionality,
                    never just a blank template.
```

Unchanged from the polish plan: Recording → Processing → Intelligence → Workspace (Note) → Outputs. Source data
(audio, original transcript) is never overwritten.

---

## 3. Decisions (founder, 2026-10-09)

| # | Decision |
|---|---|
| D1 ☑ | **Navigation:** `Home · Notes · Record · Spaces`, with Settings under the avatar and Search in the header. **Four home designs** (Everyday = new default, Faith = rebuilt Today, Work, Study) on one design system, plus a proper **personalization system** with basic and advanced options. |
| D2 ☑ | **Default AI mode is Internet (Gemini).** It is faster and gives better results, and most users are online. Local-only will become the free tier and Internet mode Pro; tier definition comes later. **Onboarding copy must be honest** about what happens online versus on-device (the Welcome screen can't say "on your phone" while the default sends audio to Google). |
| D3 ☑ | **Learning:** remove the current implementation (UI now, tables kept) and rebuild as a **study companion** (V-1). |
| D4 ☑ | **Fellowship and Circles are MVP-critical.** They will be fully re-planned and rebuilt, including a backend design that can support a **church enterprise tier**. The current build crashes when a Circle code is pasted, and its security model is broken. Faith is the most important vertical: existing testers come from it, and it's underserved. |
| D5 ☑ | **Spark is MVP-critical:** users generate beautiful shareable content at any moment (devotional, verse, sermon point, prayer, testimony, study insight) that keeps them in the app. Re-plan and rebuild it. |
| D6 ☑ | **Companion = "Zuri and friends"** (replaces Mimi): Zuri, the orb and the default, plus Wren, Page and Nas the puppy. Prototype: https://claude.ai/artifact/CwsiqKq3ff7wCt4hjC2bB3 (branch `ccr-5dc13c30-a5eqgv`). **Choosing a companion:** in onboarding and Settings. **Long-press quick sheet:** change companion, presence level, rename, hide until tomorrow. **Record button:** plain, with Zuri on it only as an Advanced toggle. **Sermons:** Zuri is quiet by default, with a per-recording chip to change it. **Spark:** six moods (Prayerful, Peaceful, Grateful, Joyful, Reflective, Celebratory); AI suggests one and the user can change it; prayer requests allow only Prayerful or Peaceful; the companion is off on cards by default. **Rendering is tiered:** a flat drawing at 24–40 dp, Compose pseudo-3D at 56–120 dp, Rive for hero moments at 160 dp+ only if it earns its place (about 2–4 MB, still to verify); real-time 3D is ruled out. **Each screen has a mood:** Home greets, Faith is slower and softer, Work is crisper, Study is curious. The full spec is coming as `docs/mvp/ZURI_EXPERIENCE.md` from the Zuri design session. **Candidates from its 12 ideas:** Ask Zuri, the assistant (grounded answers with sources, pre-meeting briefs, follow-ups, "what did I commit to", a Friday review); sermon → 5-day devotional; drive-home debrief; a sleeping widget; an NFC plush. |
| D7 ☑ | **No rush. Quality over speed.** Launch scope: Faith (excellent) + Work + Study v1. Faith gets the deepest investment. |
| D8 ☑ | **Faith template buttons must be real.** Highly visible actions such as Devotional and Prayer on the Faith and Work pages must lead to real guided experiences, not blank note templates. **Proper Bible study** in-app is a goal. |

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
| S-8 ⊘ | ~~Hide Fellowship/Circles/Spark~~. Cut: they are MVP-critical (D4/D5) and get rebuilt in Phase 4 instead. Until then they stay as they are in tester builds. | — | — |
| S-9 | Onboarding honesty hotfix (D2): keep Internet as the default, but rewrite the Welcome and AI-step copy so it is truthful ("Fast, accurate results with Gemini · Switch to on-device any time"); permissions asked once, in context | Haiku | No privacy claim the default contradicts; no permission dialog at launch |

### Phase 1: Foundations (design system + navigation)

| ID | Task | Model | Acceptance |
|---|---|---|---|
| F-1 ★ | Write `docs/mvp/DESIGN_SYSTEM.md` and implement tokens in `ui/theme`: `MMType` (8 steps), `MMSpace` (4/8/12/16/24/32), `MMRadius` (8/16/pill), elevation (0 + floating), motion (200/300 ms, one spring), per-space accent | **Opus** | Tokens compile; a sample screen uses only tokens |
| F-2 ★ | `core/ui` components: `ScreenHeader`, `SectionHeader`, `MMCard`, `HeroCard`, `InsetPanel`, `ListRow`, `PrimaryAction`, `FilterChip`, `EmptyState`, `ProcessingPill`, `NoteRow` | Sonnet | Screenshot tests for each in light and dark |
| F-3 ★ | Navigation per D1: the new bar, the space switcher, nested graphs `faith/*` `work/*` `learn/*`; delete `HomeStyle` and the `HomeSection` toggles; Settings moves to the avatar | Sonnet | One home route; every top-level screen has the bar; back stack verified |
| F-4 | Literal sweep, one file per task: `fontSize`/`Color(0x…)`/`RoundedCornerShape` → tokens, starting from the worst offenders in AUDIT §3 | Haiku ×N | Each file's grep for literals is down to 0 (scripture serif excepted) |
| F-6 ★ | **Personalization system spec + engine**: one `Personalization` model (home, accent, theme, name; advanced: hero content, section order, text size, density, per-space accents) replacing `homeStyle`, `HomeSection`, look, card style and hero mode, with a migration of existing prefs | Opus spec → Sonnet | Old prefs map 1:1; every combination passes a screenshot matrix |
| F-7 ★ | **Select & copy everywhere** (shared `SelectableText` patterns) and **Read aloud** service (one `ReadAloud` controller used by notes, devotional, scripture, summaries, study) | Sonnet | Every user-content surface in the audit list supports both |
| F-5 | Remove legacy palettes (`CleanMac*`, `Dark*`), the duplicate `Slate`s and the three section-title copies | Haiku | Grep clean; build green |

### Phase 2: Surfaces

| ID | Task | Model | Acceptance |
|---|---|---|---|
| U-1 ★ | **Home system**: a shared home scaffold + section components; then **Everyday** (new default: greeting + Mimi, Next, Needs you, Recent notes, Today) | Sonnet | Passes the four questions; Recent notes visible without scrolling |
| U-1F ★ | **Faith home** = today's Today home rebuilt on the system: keep its sections (scripture/quote hero, devotional, stories, quick access, rhythm, timeline) but with one palette, one type scale and one card language; remove duplicates (AUDIT §2) | Sonnet | Founder review: "beautiful and coherent" |
| U-1W ★ | **Work home** = ProfessionalHome rebuilt (merges `WorkSpaceScreen`; Pulse is the only hero; AUDIT §5 cuts) | Sonnet | One Work home; an empty section renders nothing |
| U-1S | **Study home** (after V-1): next class, revise today, courses, recent lecture notes | Sonnet | Golden path on device |
| U-2 ★ | **Notes library**: search first, multi-select chips, rich `NoteRow`, folders behind a toggle; library search goes through FTS and excludes private text | Sonnet | First note visible without scrolling on a 6" phone; search is ranked |
| U-3 ★ | **Work hub** (the Work *space*; shares components with U-1W): one screen (Pulse as the only hero → Needs you → Today → Tasks Mine/Waiting → Decisions); delete `WorkSpaceScreen`, metric tiles, shortcut pills and the template grid; extras go behind "⋯" | Sonnet | One Work hub; an empty section renders nothing |
| U-4 ★ | **Faith hub** (the Faith *space*): Today's Word, Daily rhythm, Record sermon, Bible study, Fellowship, Create (Spark), Recent sermons; delete the stat cards and Backgrounds | Sonnet | ≤7 sections; serif only for scripture |
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
| A-1 ★ | **Ask Zuri**, the assistant: grounded answers with source chips that jump to the audio; an honest "couldn't find it in your notes"; on-device and online modes; a tier-gate flag (ZURI_EXPERIENCE §8, task Z-17) | Sonnet | Every factual answer has a source or the not-found line |
| A-2 | **Live whisper experiment** (ZURI_EXPERIENCE Z-25): flagged, dev builds only, consent safeguards; the founder decides after testing | Sonnet | Founder decision after a device test |
| P-6 | **Labor-illusion processing screen**: the companion is "thinking" plus narrated real stages from S-2 | Haiku | Copy is driven by real stage events |

### Phase 4: Vertical rebuilds

| ID | Task | Model | Acceptance |
|---|---|---|---|
| V-0 ★ | **Faith re-plan** (`docs/mvp/FAITH_V2.md`): Fellowship + Circles (church/enterprise-ready backend, roles enforced server-side, invites that can't crash, moderation, member caps, honest privacy), Spark (shareable content from anywhere: devotional, verse, sermon point, prayer, testimony, with a real design studio), real Devotional / Prayer / Bible-study experiences behind every prominent button (D8), and sermon → devotional | **Opus** + founder | Founder sign-off |
| V-0a ★ | Faith v2 build: Spark studio | Sonnet | Shareable cards from 5+ sources; faith contract enforced |
| V-0b ★ | Faith v2 build: Fellowship + Circles (backend + app) | Sonnet (+ Opus review of security) | Security review passes; invite paste/scan never crashes |
| V-0c ★ | Faith v2 build: guided Devotional / Prayer / Bible study replacing blank templates | Sonnet | Every Faith button leads to a guided flow |
| V-1 ★ | **Study v1 spec** (`docs/mvp/STUDY_V1.md`): a study companion built on note + transcript, not a parallel pipeline. Record lecture → formatted study note → notebooks per course → AI tools (go deeper, expand, explain, generated diagrams/images and a few built-in components) → multiple-choice tests and flashcards (reusing the spaced scheduler) → personalised explanations that adapt over time → surfaces what the lecturer emphasised and what's likely in the exam → read aloud throughout | **Opus** + founder | Founder sign-off |
| V-2 ★ | Learning v1 build | Sonnet (+ Haiku for tests) | Golden path on device |
| V-3 | Old Learning table drop (unless the V-1 spec reuses them): `MIGRATION_23_24` (+ remap `LEARNING` notebooks); delete `filesDir/teach_back` | Haiku | Migration test from 21, 22 and 23 |
| V-5 | **Sermon → devotional** action; one faith-contract source of truth (assets only); retry on truncated extraction chunks | Sonnet | Unit tests for the chunk retry |

### Phase 5: Quality and release

| ID | Task | Model | Acceptance |
|---|---|---|---|
| Q-1 ★ | Golden paths on device: Faith sermon, Work meeting, Learning lecture, offline-only, Gemini-only, app killed mid-processing | Founder + Opus checklist | All six pass |
| Q-2 | Accessibility: 48 dp targets, content descriptions, font scale 200%, TalkBack on the 5 top screens | Haiku audit → Sonnet fixes | Checklist green |
| Q-3 | Performance: long transcripts, home recomposition, cold start | Sonnet | Cold start <1.5 s on a mid-range phone |
| Q-4 | Release: v43 notes, Data Safety update (D2), Play listing screenshots from the new UI | Haiku | Bundle uploaded to closed testing |

---

## 6. Companion: Zuri and friends

**`docs/mvp/ZURI_EXPERIENCE.md` is the spec** (P-1). It covers character, states, the journey, voice, personalization,
Ask Zuri, Compose implementation and the build plan. The options page is `docs/mvp/zuri-options.html`, and the live
prototype is https://claude.ai/artifact/CwsiqKq3ff7wCt4hjC2bB3.

**Build tasks:**

- **Phase A (MVP):** Z-1 to Z-18 in spec §11. These replace P-2, P-5 and P-6, and fold into P-3 (onboarding step 1)
  and U-1 (homes).
- **Phase B:** Z-19 to Z-24. This includes the Rive spike and the Wren and Page drawers.
- **Order:**
  1. Z-1 → Z-3 and Z-4 → Z-5
  2. Z-6, Z-7, Z-8, Z-9, Z-11 and Z-18 in parallel
  3. Z-10, Z-12, Z-13 and Z-14
  4. Z-15, Z-16 and Z-17

**Dependencies on this plan:**

- Z-3 adds the companion tokens to the F-2 design system.
- Z-9 needs the F-2 `HomeHeader` and `EmptyState` slots.
- Z-11 needs the F-6 personalization model.

**Decisions (founder, spec §12):**

- Nas stays.
- Zuri + Nas launch first.
- Ask Zuri: on-device on Free, online on Pro.
- Live whisper gets built as a flagged experiment (Z-25 / A-2), and the founder decides after testing.

## 7. Execution order (what runs in parallel)

| Wave | Tasks |
|---|---|
| 1 (running) | S-1/S-2 (Sonnet) ∥ S-4 + S-5 (Haiku) ∥ S-7 (Haiku) ∥ F-1 design system (Opus) ∥ Mimi spec (founder's Opus agent) |
| 2 | F-2 components (Sonnet) ∥ S-6 (Sonnet) ∥ S-3, S-9 (Haiku) ∥ specs: F-6 personalization, V-0 Faith v2, V-1 Study v1 (Opus + founder) |
| 3 | F-3 navigation (Sonnet, alone on the hot files) → then U-1 Everyday ∥ U-1F Faith home ∥ U-1W Work home ∥ U-2 Notes (Sonnet) ∥ F-4 sweeps (Haiku, on files not in flight) |
| 4 | F-6 engine, F-7 select/copy + read aloud ∥ U-3, U-4, U-5, U-6, U-7 ∥ P-2 Mimi → P-3, P-5 ∥ V-0a/b/c Faith v2 |
| 5 | V-2 Study v1 → U-1S ∥ V-3, V-5 ∥ Q-2, Q-3 → Q-1 → Q-4 |
