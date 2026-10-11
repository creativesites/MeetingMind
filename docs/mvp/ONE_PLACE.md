# One place, real experiences: Bible, Faith home, Settings

Status: **founder decisions, 2026-10-11** (two rounds of questions). This supersedes `BIBLE_DEVOTIONAL_V2.md`
§3.1 and §6 (B-1, B-2) where they differ, and the Faith-home parts of `MVP_PLAN.md` U-1F.

## 0. The problem, in the founder's words

People don't create notes called "Bible study". They read the Bible, guided or unguided, and *then* write or add to
notes. Today the app does it the other way round. Big bold buttons open blank templates, which disappoints users who
expected a feature. And the same concept lives in many places under different names.

**Verified in code (2026-10-11):**

- "Bible study" exists in seven places, and none of them is a study:
  - The Faith tile opens a method picker and then a blank note.
  - The reader's "Study this" makes a note.
  - The reader's "Study" pill opens a read-only sheet.
  - The recording type, circle template, assistant chip and the orphaned fellowship kind make up the rest.
- On the home quick row, "Word" opens the devotional, not the Bible.
- "Stories" is both the testimonies feed and the story rings.
- Six of the seven big Faith tiles only create a blank note. The seventh, Bible study, opens the method picker and
  then creates one.
- The Bible reader is strong, but it sits behind a small tile:
  - commentary, cross-references, Hebrew and Greek
  - audio
  - offline copies
  - resume where you left off
- Fellowship's old guide screens can't be reached, yet the card still describes them.

## 1. The rule (also in AGENT_RULES.md)

> **Experience first, note after.**
> - A prominent button must start the experience its label names.
> - Notes are what an experience produces, never the experience itself.
> - A button that only creates a note must say **"Write …"** and be visually quiet.
> - **One concept, one place, one name.**

This is enforced by `EntryPointContractTest`, which lists every prominent button with its label and destination kind
(EXPERIENCE / WRITE / SHEET). A WRITE destination behind a prominent style fails the test.

## 2. Simple and advanced: layered, with no mode switch

- Every screen shows the one to three things most people need.
- Everything else is **one tap away**: a "More ways to …" row, "All settings", or long-press.
- Nothing is removed, and nobody has to call themselves advanced.
- **Earned shortcuts:** a tool used twice (for example Hebrew/Greek, compare translations, or commentary) gets promoted
  into that screen's main bar. This is stored per tool in DataStore, so it's quiet and reversible.

## 3. The Bible: one place

**Route:** `Routes.BIBLE_HOME`. Every "Bible", "Read", "Word", "Study" and "Bible study" entry in the app leads here,
or into the reader *with guidance available*. Nothing else in the app is called "Bible study".

### 3.1 First screen (Continue + Today)

1. **Continue** is the hero, with the exact book, chapter and verse ("Continue · John 3:16"). It opens the reader.
   - For a first-time user it reads "Start reading", which opens John 1, or Psalm 23 offline.
2. **Today's reading** is a short passage picked for today, with two lines on why it matters. It has two buttons:
   - **Guide me** (primary) starts a guided session.
   - **Just read** opens the reader at the passage.
3. Quieter rows below:
   - **How I feel** offers need chips: anxious, grateful, grieving, facing a decision, lonely, tempted, tired, joyful.
     Each gives a fitting passage and the same guided session.
   - **Study a passage**: pick a reference, then Walk me through it.
   - **Plans**: the existing reading plans. A plan day opens its passage with a guided session, not just text.
   - **My study journal**: entries linked to passages.
4. **More ways to study** lists Book overview, Character, Word study, Topical, Inductive and Small group prep.
   - Each one is labelled *coming* until it actually guides. It is never a blank template.

### 3.2 Guided session (one engine)

A session is: the passage pinned at the top (with Listen), then a sequence of steps, then a short close.

- **Today / How I feel / plan day ("Pick it for me" shape):**
  1. Why it matters (two lines).
  2. Read or listen.
  3. Two or three questions, answered in free text and optional.
  4. A short prayer.
  5. A one-line "Live it today".
- **Walk me through it:** the user picks one of two methods.
  - **SOAP:** Scripture → Observation → Application → Prayer.
  - **Verse by verse:** one verse per step. Each step offers the Hebrew/Greek taps, commentary and cross-references
    already in the reader.
- **Zuri asks, never answers.** The AI suggests observation questions and context, and every claim cites a verse. It
  never writes the user's answers.
- **Offline or without a key:** a curated question bank per step type and need, so it still works and the app says so
  in one honest line.
- **Generic by default.** Profile or personal context is used only when Personal touch allows it. This is the same
  rule as devotionals (BIBLE_DEVOTIONAL_V2 §8).
- **Passage choice:** today's reading uses `PassageRotation`, with the 30-day no-repeat rule and the liturgical
  calendar when the user follows one. The need passages come from a curated table, each need with 8 or more passages.

### 3.3 Auto study journal

- Whatever the user writes during a session **saves itself** as a journal entry. It is a note of type `BIBLE_STUDY`
  carrying `passageRef` and `sessionKind` metadata, created on the first keystroke and never before.
- The entry shows in three places:
  - Notes, under Bible
  - on that passage next time, as a dot in the reader and as "Your notes on this passage"
  - in My study journal
- **Add to an existing note** (for example last Sunday's sermon) appends the session's answers as blocks.
- No blank note ever appears first. Closing an untouched session leaves nothing behind.

### 3.4 What happens to today's entries

| Today | Becomes |
|---|---|
| Faith tile "Bible study" | removed; the Bible hero card replaces it |
| Reader "Study this" (note) | **Guide me** on the selection, which starts Walk me through it |
| Reader "Study" pill (sheet) | renamed **Explain**: commentary, cross-references, original language |
| StudyWorkspace for a sermon (Timeline/Scripture/Transcript) | stays, reached from the sermon note as "Study this sermon" |
| Quick row "Word" | renamed **Devotional** |
| Quick row "Stories" (testimonies) | renamed **Testimonies** |
| Recording type "Bible study" | stays: it's a recording of a group study, and its label says "Record a Bible study" |
| Circle template "Bible study" | stays: it's a kind of group |

## 4. Faith home

- **Prominent, in this order:** Bible (Continue + Today), Today's devotional, Pray with me, Circles.
- **Quieter, below:** verse of the day (with Write on it), Praying for, reading plan progress, testimonies, journey,
  Create row, recent notes.
- **The writing tiles stay as small tiles, relabelled:**
  - Write a prayer
  - Write a prayer request
  - Write down a blessing
  - Write a testimony
  - Write a reflection
  - Write a devotional
  These sit under one quiet "Write" heading. Bible study is removed from this set.
- Fellowship card copy now describes Circles.
- The orphaned fellowship guide/draft routes are deleted.
- Record a sermon stays as a quiet row.

## 5. Settings: 8 groups, layered

**Top level:** Account · Recording · AI & offline models · Faith · Appearance & home · Privacy & security ·
Storage & backup · About.

- Each group shows its two to four most-used rows. Everything else in a group sits under **Advanced** at the bottom of
  that group's screen.
- Every setting has exactly one place.
- **Faith:**
  - Bible translation and offline copies
  - original-language packs
  - devotional time and style (the DevotionalSettingsSheet content, also still reachable from the devotional screen)
  - prayer reminders
  - Personal touch
  - Lock Faith
- One Notifications screen, reachable from Account.
- **Search settings** sits at the top of the screen.
- **Removed:** dead prefs and developer copy. A developer-only row stays behind `BuildConfig.DEBUG`.

## 6. Build tasks

| ID | Task | Model | Depends |
|---|---|---|---|
| B-1 | Bible home + guided session engine + auto study journal + need/passage tables + reader "Guide me"/"Explain" | Sonnet | — |
| U-5 | Settings in 8 layered groups (§5) | Sonnet | — |
| U-2 | Notes library: search first, chips, rich rows, ranked FTS search | Sonnet | — |
| U-1F | Faith home (§4) + relabels (§3.4) + EntryPointContractTest + delete orphans | Sonnet | B-1 |
| D-1 | Devotional journal (reflection saves into the journal; archive reads as a journal) + "Live it today" block | Sonnet | B-1 |
