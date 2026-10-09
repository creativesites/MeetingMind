# Bible study, devotionals and Spark: v2 direction

Status: **CTO proposal for founder review**, 2026-10-09. It extends `FAITH_V2.md` §4 (guided practices).

Reliability fixes run in parallel:

- **Devotional delivery:** truthful scheduled devotionals, plus copy and share on every block.
- **Live voice:** Pray with me and Talk to me no longer stop mid-session.

## 1. What exists today (verified in code)

**The Bible reader** (`feature/bible`, `core/scripture`) already has:

- highlights
- commentary (HelloAo)
- cross-references
- original-language word taps (Strong's)
- an offline Bible download

**The Study workspace** (`feature/study`) has templates, highlights and cross-references.

**Devotionals** have daily generation, classics as an offline fallback, voice, Ask, and an archive.

**What's missing:**

- **A guided path through all of it.** Today it's a toolbox, not a practice.
- **Read-aloud** of scripture.
- **Memorisation.**
- **Comparing translations.**
- **Personal verse notes** that link back to sermons.
- **Bible ↔ notes integration:** "your pastor preached on this passage".
- **Continuity:** "pick up where you left off".

## 2. Principles

1. **The Bible is the centre; AI is the study partner.** AI asks good questions and explains context. It never replaces
   the text, and every claim cites a verse.
2. **Practices, not features.** Each big button starts a guided flow that ends in something saved (D8).
3. **Denomination-neutral, personalisable.** Methods, translations and tone are user choices.
4. **Everything is selectable, copyable, shareable and readable aloud** (F-7).
5. **Truthful.** Offline or no AI means a clear line and a non-AI path, never a fake result.

## 3. Bible study v2

### 3.1 One entry, three ways in

The **Bible study** button opens one sheet:

- **Continue:** the last passage or study, at the exact verse.
- **Study a passage:** pick a reference.
- **Study a topic:** for example forgiveness, anxiety, or leadership.
- **Follow a plan:** reading plans already exist; studies attach to each day.

### 3.2 Guided methods

Each method is a step-by-step flow, with the passage pinned on top. The user picks once, and the choice is
remembered.

| Method | Steps | AI's role (optional) |
|---|---|---|
| **SOAP** | Scripture → Observation → Application → Prayer | Suggests 2 observation questions; never writes the answer |
| **Inductive** | Observe → Interpret → Apply | Context: author, audience, genre; cross-references with reasons |
| **Verse by verse** | One verse at a time, with notes per verse | Explains hard words through original-language taps |
| **Topic study** | Topic → 5–8 key passages → synthesis | Finds passages with references; the user writes the synthesis |
| **Character study** | A person → key passages → lessons | Builds a timeline of passages |

**Output:** a study note in the Bible-study notebook, linked to the passage. It can be shared to a Circle as a
discussion guide.

### 3.3 Reader upgrades

- **Read aloud** any passage, with follow-along highlighting: on-device TTS, or a Gemini voice online.
- **Compare translations** side by side, two at a time, using the versions the user has.
- **Verse notes.** Long-press a verse → Note, Highlight, Copy, Share as card, Memorise, Ask. Each verse shows a
  small dot when it has notes, sermons or studies attached.
- **"From your notes"** under a passage: sermons and studies where it was quoted, built from the existing
  `scripture_refs`. This is a unique advantage, because no Bible app knows what your pastor preached.
- **Memorise:** add a verse; spaced review, reusing the scheduler kept from Learning; first-letter and fill-the-blank
  drills; an optional streak that is calm, not punishing.

### 3.4 AI study partner (Ask on a passage)

- Answers are grounded in the passage, cross-references and commentary, and show their sources.
- It offers **questions** as well as answers ("What do you notice about…").
- It follows the faith contract, so it doesn't tell the user what their denomination believes.
- Free tier: on-device. Pro: online. This is the same gate as Ask Zuri.

## 4. Devotional v2

On top of the delivery fix:

- **Shape of a devotional:**
  - Scripture (read aloud)
  - Reflection
  - One question
  - Prayer
  - "Live it today" (one action)

  Every block is selectable and has Copy and Share.
- **Journal:** "Write your reflection" saves into the devotional note. The archive becomes a journal people reread.
- **Personal thread:** with permission, the devotional draws on the user's recent sermons and prayer list, using the
  existing memory. For example: "Sunday's sermon on Ruth" or "you're praying for your mother".
- **Talk to me:** a live conversation about today's devotional. The live-voice fix lands first.
- **Sermon → 5-day devotional** (Zuri idea #1): a plan built only from the pastor's words, shareable to a Circle.
  This is a strong selling point for the church tier.

## 5. Spark card redesign (Create v2, task V-0a)

**On the Faith home today,** Spark is a large gateway card. That breaks "don't equate discoverability with
prominence".

**New Faith home entry:** a quiet **Create** row.

- It shows a small live thumbnail of a card suggested for today: today's verse, or a quote from the last sermon.
- The AI picks the mood, one of the six Spark moods (Prayerful, Peaceful, Grateful, Joyful, Reflective,
  Celebratory), and shows it as a chip.
- One tap opens it in the studio. "See more" opens the Create gallery.

**The studio itself** follows FAITH_V2 §3 and ZURI_EXPERIENCE (Z-22):

- **Steps:** source → mood → format (9:16 / 1:1 / 4:5) → design → share.
- **Mood rules:** prayer requests allow only Prayerful or Peaceful. The companion is off on cards by default.
- **Saving:** creations are saved in "My creations".
- **Watermark:** off by default.
- **Scripture text:** always comes from the Bible provider.
- **Design language:**
  - A curated set of type pairs: a serif for scripture, Inter or Outfit for the rest.
  - Photographic and gradient backgrounds tuned per mood.
  - Safe margins for WhatsApp and Instagram status UI overlays.
  - Auto-contrast text over images.

It is built on the F-2 design system, after F-2 merges.

## 6. Build tasks (proposed)

| ID | Task | Model | Depends |
|---|---|---|---|
| B-1 | Bible study entry sheet + "Continue" + Study notebook linking | Sonnet | F-2 |
| B-2 | Guided methods engine (SOAP, Inductive, Verse-by-verse, Topic, Character) as data-driven steps | Sonnet | B-1 |
| B-3 | Read aloud with follow-along (shared `ReadAloud` from F-7) | Sonnet | F-7 |
| B-4 | Verse long-press actions, verse notes, "From your notes" | Sonnet | B-1 |
| B-5 | Compare translations | Haiku | — |
| B-6 | Memorise (scheduler reuse, drills) | Sonnet | B-4 |
| B-7 | Ask on a passage (grounded, question-first) | Sonnet | A-1 |
| D-1 | Devotional journal + personal thread + "Live it today" block | Sonnet | delivery fix |
| D-2 | Sermon → 5-day devotional | Sonnet | D-1 |
| V-0a | Create studio + Faith home Create row (§5) | Sonnet | F-2, Z-22 |

## 7. Questions for the founder

1. **Which study methods matter most to your testers?** SOAP and verse-by-verse are the most common.
2. **The personal thread** (using sermons and prayer list in devotionals): default on, or ask first? I recommend asking
   once during devotional setup.
3. **Read-aloud voice for scripture:** on-device by default (free, offline), with the Gemini voice for Pro?
