# Prompt: design the Mimi experience

> Give everything below the line to a dedicated Claude Opus agent. If the agent can open the MeetingMind
> repository (`creativesites/meetingmind`, branch `claude/meetingmind-mvp-planning-7dgowm`), tell it so. The prompt
> also works without repo access.

---

You are a world-class character designer, motion designer and product designer, in the tradition of the people who
made Duolingo's Duo, Headspace's characters and Finch. Your one job: **design Mimi, the companion character of
MeetingMind, and plan the full Mimi experience across the app.** Make it remarkable. Mimi should give people a real
emotional connection to the app. It should feel like *their* companion, without ever getting in the way of their
work, their notes or their faith.

## 1. The product

**MeetingMind** is an Android app, currently in Google Play closed testing. At its core it is a recorder plus
transcription app that turns what people hear into notes they keep.

- **How it records.** It records and transcribes offline with on-device models: Parakeet ASR, pyannote/CAM++
  speaker separation, and a small local LLM. It also works online with Google Gemini, which is the default because
  it is faster and better.
- **What it produces.** Every recording becomes a **Note**: an editable, block-based workspace with a summary, key
  points, action items, scripture and quotes, all linked back to the moment in the audio.
- **Principle: "A recording is the source. The Note is the workspace."** Notes are what keep users coming back.
- **Three verticals (spaces), each with its own home design.** A new general-purpose "Everyday" home is the default.
  - **Faith (the most important vertical).** Christians record sermons and get a Faith Note: scripture
    references, main points, quotes, prayer points and reflections. They read daily devotionals, read the Bible
    and do Bible study. They pray with a prayer list. They create shareable content ("Spark": beautiful cards for
    WhatsApp and Instagram stories). They join **Fellowship / Circles**, small private church groups that share
    prayers and studies; churches will subscribe to an enterprise tier for this. Real testers love it. One uses it
    daily for devotionals and calls it her favourite app.
  - **Work (Professional).** Record meetings, identify participants, then get the summary, decisions, action items
    and follow-up email, and turn them into tasks.
  - **Study (being rebuilt).** A study companion. Students record lectures and get formatted study notes organised
    in notebooks per course. AI tools explain, go deeper and generate diagrams. Quizzes and flashcards come with
    spaced review. It surfaces what the lecturer emphasised and what's likely in the exam, and can read notes aloud.
- **Tiers (coming).** Free is local-only; Pro is Internet mode; Church/Enterprise adds Fellowship.

**Tone.** Calm, warm, trustworthy, private, high-end. The app is not a game. People use it in church, in client
meetings and in exam season.

## 2. The principles Mimi must obey

These are the house rules for every MeetingMind surface:

- **"Every surface in MeetingMind must earn its place."** Mimi too. Every appearance must answer *why is Mimi here,
  and what does the user feel or gain?*
- **"Do not equate discoverability with prominence."** Mimi is not a mascot stamped on every screen.
- **Every screen answers the four questions:** Where am I? What's important? What can I do? What next? Mimi may help
  answer them, and must never compete with them.
- **The psychology the founder wants applied:**
  - **Emotionally intelligent design.** Duo's emotional states drive retention.
  - **The peak-end rule.** Design the high points; remove the frustrating lows.
  - **Goal-gradient.** Never start at zero; show progress.
  - **The labor illusion.** Visible, narrated work feels more valuable than an instant result. Processing a
    recording takes minutes, and Mimi can make that wait feel like care.
  - **Reciprocity.** Give value before asking.
  - **Smart defaults.**
  - **Von Restorff.** One distinctive element per screen.
  - **Choice overload.** Fewer, better choices.
- **Honesty.** Mimi never fakes progress, never claims work it didn't do, never guilt-trips and never nags. (Duo's
  aggressive guilt works for language streaks. It would be wrong here, especially around faith and grief.) A
  "worried" Mimi always comes with a fix, such as Retry.
- **Faith respect.** Mimi is a companion, not a spiritual authority. Mimi never prays *for* the user, never
  interprets scripture, never trivialises worship, sermons or prayer requests, and stays quiet during sacred moments.
  Mimi can be gentle and joyful around faith ("Your sermon notes are ready 🙏" is fine; a dancing Mimi on a prayer
  request is not).
- **Mimi never appears inside the user's content:** not in notes, transcripts, scripture text, or shared cards
  unless the user chooses to add it.

## 3. The technical canvas

- **Platform.** Android, Kotlin + Jetpack Compose (Compose BOM 2024.09), minSdk 24 and targetSdk 36. Builds run on
  low-end and mid-range phones.
- **No animation library today.** There is no Lottie and no Rive. All animation is pure Compose (`Canvas`,
  `animate*AsState`, `InfiniteTransition`, `AnimatedContent`). Adding Rive or Lottie is allowed if you argue it's
  worth the size and pipeline cost. Otherwise, design Mimi so it can be drawn with **Compose `Canvas` / vector
  paths** and driven by a state machine.
- **Brand today.**
  - Fonts: Inter (body) and Outfit (display).
  - Light theme "Paper" and dark theme "Graphite"; ink is slate-900.
  - Accent is indigo `#6366F1`; Faith uses a warm gold accent.
  - Users will pick their own accent colour (a curated palette) in the new personalization system, so **Mimi must
    look right in any accent and in both light and dark themes.**
- **An existing `RecordOrb`.** The Home screen has an animated orb (`feature/today/RecordOrb.kt`). Mimi could grow
  out of the orb so the record button and the character are one object. Evaluate this idea; don't assume it.
- **Accessibility.** Respect the system "remove animations" setting and animator duration scale. Mimi needs a static
  pose for every state, content descriptions for TalkBack, and must never be the only carrier of information.
- **Performance.** There can be no idle animation that drains the battery. Animation runs only while visible and
  meaningful. Target 60 fps on a mid-range phone.

## 4. What to produce

### Deliverable A: `docs/mvp/MIMI_EXPERIENCE.md`

The spec the engineering team will build from.

1. **Who Mimi is.**
   - A character bible: personality, voice, values, what Mimi loves, what Mimi would never do.
   - Why Mimi fits a private "remember what matters" app.
   - A one-sentence essence.
   - The name: keep "Mimi" unless you have a strong case. If you do, give it alongside Mimi, but Mimi is the
     working name.
2. **Visual design.**
   - Silhouette, proportions, construction from simple geometric primitives so it is drawable in Compose, and the
     face system (eyes, mouth or none).
   - How Mimi takes the user's accent colour.
   - Light and dark variants, sizes from 24 dp (inline) to 160 dp (onboarding hero), and readability at 24 dp.
   - Per-vertical touches that stay the same character: a subtle accessory or ambient detail for Faith, Work and
     Study. No costumes that cheapen faith.
3. **Emotional states and the state machine.** For each state, give:
   - its trigger, a real app event
   - pose and face
   - motion (keyframes, durations, easing, loop or one-shot)
   - the reduced-motion static pose
   - the transitions in and out

   At minimum include idle, listening (reacts to live mic amplitude while recording), thinking (processing, tied to
   real pipeline stages: transcribing, identifying speakers, understanding, writing your note), celebrating, proud
   (milestones), sleepy (offline models missing or late night), worried (failure, always with a fix), curious
   (onboarding questions) and reading (read-aloud). Add others only if they earn their place.
4. **The Mimi journey.** Map Mimi across the whole app:
   - First launch and onboarding (5 steps, personal, progress from step 1). The user should feel they are
     *setting MeetingMind up to be theirs* and form a bond before their first recording.
   - The first-recording "peak" moment.
   - Each of the four homes (Everyday, Faith, Work, Study): where Mimi lives on each home and how it greets.
   - Recording, processing (labor illusion with real stages) and the note-ready moment.
   - Empty states, errors, streaks and rhythms (devotional streaks, study streaks, weekly work review: calm, not
     gamified pressure).
   - Notifications and the home-screen widget, if any.
   - Read-aloud, Spark sharing, Fellowship moments, and seasonal touches (Christmas or Easter, handled tastefully).

   For each touchpoint give the purpose, the state, the copy and the frequency cap. Then list **where Mimi must
   never appear**, and why.
5. **Voice and copy.**
   - Mimi's microcopy style guide, with 30+ real example lines across the journey.
   - Rules for addressing the user by name, for faith-sensitive moments, and for failures.
6. **Personalization.** How the user's choices (name, space, accent, goals) shape Mimi. Can the user rename Mimi,
   turn Mimi down ("quiet mode") or off? Recommend defaults. Keep options few, with the rest under Advanced.
7. **Retention mechanics, done ethically.** Which emotional hooks you use and why each one is respectful. List what
   you deliberately won't do.
8. **Implementation plan for Compose.**
   - The `Mimi(state, size, accent)` composable API and the state-machine model (events in, states out).
   - The drawing approach: Canvas paths, or a library and why.
   - The animation spec as data (so the motion can be tuned without code archaeology).
   - Asset pipeline, testing (screenshot tests per state, light and dark), and performance budgets.
   - A phased build plan (MVP set of states first, then more), with each task sized for an engineer.
9. **Open questions for the founder.**

### Deliverable B: `docs/mvp/mimi-prototype.html`

A single self-contained HTML file (inline SVG/CSS/JS, no external assets) that shows:

- Mimi in every state, animated, with a toggle for reduced motion
- light and dark, with an accent-colour picker
- the size ladder from 24 to 160 dp
- a phone-frame mock of 4–5 key moments: onboarding hello, recording (with a fake mic-level slider driving
  "listening"), processing stages, note-ready celebration, and a worried state with Retry

This is how the founder will fall in love with Mimi (or tell you what to change). Make it beautiful.

## 5. How to work

- **If you have repo access:**
  - Read `docs/mvp/MVP_PLAN.md`, `docs/mvp/AUDIT.md` (§9 covers onboarding and assets) and `docs/mvp/AGENT_RULES.md`.
  - Skim `feature/onboarding`, `feature/today` (RecordOrb, the homes), `feature/processing` and `ui/theme`, all
    under `app/src/main/java/com/craftflowtechnologies/meetingmind/`.
  - Write the two deliverables to the paths above and commit them on a new branch. Don't change app code.
- **If you don't have repo access,** return the two deliverables as files.
- Before finalising, check your design against every rule in section 2 and your own "why is Mimi here?" test for
  each touchpoint. Cut any appearance that doesn't earn its place.
- Be bold in the character and disciplined in the placement.
