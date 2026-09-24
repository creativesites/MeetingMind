# MeetingMind v2 plan: "Today" calendar hub, Faith polish, daily devotionals

## Context

v1 (M0–M9, APK v21) is done: notes, sermons end to end, the Faith space, the Bible (YouVersion plus an
offline store and search), the device calendar "Up next", and note AI. The user loves the Faith
vertical and wants the next round to:

1. **Stop autosaving notes that have no content.**
2. **Make a calendar and timeline the heart of the app.** It should be beautiful, interactive and
   intelligent: cards with image backgrounds, several views, and a way into any note, transcript
   or event. Users choose which verticals appear, so a professional's calendar can stay purely
   professional.
3. **Let each person choose the app's personality.** A Christian can use it as a faith app; anyone
   else keeps it general and professional. Professionals remain a core market.
4. **Give a full offline Bible experience.**
5. **Offer daily devotionals.** AI-written by default, with a spoken "preacher" mode, prayers read
   aloud, personal motivation, a shareable Verse of the Day on an image, and a quote or insight of
   the day. They appear in a story viewer and can be shared to WhatsApp (including Status),
   Facebook and Instagram. Classic devotionals and the user's own entries are also options, and
   everything is customisable and adaptive, "like a friend".

### Decisions the user made (this round)

| Question | Decision |
| --- | --- |
| AI boundary | **Labelled AI devotional.** Allowed only in Devotional mode and always marked "AI-written devotional". Every verse is pulled from the Bible text itself, never from the model. It follows the user's tradition setting, makes no prophetic claims ("God is telling you…"), and gives no medical, financial or crisis advice. Notes and sermons keep the strict PLAN_V1 §8 rule. |
| Home | **The calendar becomes Home**, as a "Today" hub. The recordings list becomes a Timeline filter. Record stays in the nav bar. |
| Order | Quick fixes and identity → **calendar hub** → devotional engine → voice → stories and sharing → offline Bible library → reading plans, prayer and widget → live voice. |
| No key or offline | **Graceful fallback.** The on-device model writes the devotional if one is installed; otherwise a bundled public-domain classic is used. The phone's own voice reads it, and a built-in pack supplies backgrounds. |

### Verified research (Sept 2026)

- **Offline Bibles: HelloAO Free Use Bible API** (`bible.helloao.org`). No key, no usage limits,
  free use including commercial.
  - 1,256 translations, 51 in English, including BSB, KJV, WEB, ASV, NET, LSV, Geneva and
    Douay-Rheims. Each translation has a `licenseUrl`.
  - **One file per translation**: `/api/{id}/complete.simple.json`. BSB is 8.5 MB, about 2.5 MB
    over the wire. Chapters include headings.
  - **Free audio Bible** links, with **verse-level timings** (for example, four BSB narrators).
  - 7 public-domain **commentaries**, including Matthew Henry, JFB, Calvin, Gill, Clarke and
    Keil-Delitzsch.
  - An **Open Bible cross-references** dataset (CC BY 4.0).
- **Devotional content:** no good free devotional API exists. The verse-of-the-day APIs are thin
  or have rate limits. The better route is to **bundle public-domain classics**: Spurgeon's
  *Morning & Evening* (732 readings) and *Faith's Checkbook*, *Daily Light on the Daily Path*, and
  *Streams in the Desert* (1925). Source them from CCEL, which is offline and costs nothing.
- **Gemini voice:** `gemini-3.8-flash-tts` (and `-lite`) via `POST /v1beta/interactions`.
  - 30 prebuilt voices plus an extended library.
  - `speech_metadata.style` controls delivery (for example "warm, unhurried pastor").
  - Inline tags such as `<short pause>`.
  - Output is WAV, 24 kHz mono.
- **Gemini images:** `gemini-3.1-flash-lite-image` (fastest and cheapest) supports **9:16**, the
  WhatsApp and Instagram story shape. The Gemini Live API covers real-time voice conversation.

## Principles for this round

- **Offline-first.** Every feature has a no-network path, and AI only makes things better.
- **Two faces, one engine.** Identity changes wording, colour, type, default views and layers;
  it never forks code. This follows PLAN_V1 §9 rule 2: no branching on vertical.
- **Private by default.** Prayers, journals and prayer requests are used to personalise
  devotionals only on the phone. They go to Gemini only if the user turns on
  "Use my prayers to personalise (sent to Gemini)".
- **Everything opens something.** Every card, dot and bar leads to a note, recording, transcript
  moment, verse or event.

---

## F0. Quick fixes and app identity

**Empty notes are never kept or shown.**
- Current problem: `NoteEditorViewModel.discardIfEmpty` (feature/notes/editor) treats a note as
  empty only when *all* its blocks are empty paragraphs. Notes created from a template, such as
  the Faith types with their headings, are therefore never discarded, and they appear in lists
  while they're still blank.
- Fix:
  - Add a single `NoteContent.hasUserContent(doc)` rule (core/repository). A note has content if
    any of these is true:
    - it has a title the user typed
    - a non-heading text block has text
    - a heading differs from its template section title
    - it has media, scripture, a recording, an attachment or a tag
  - New notes are created with `metadata["draft"]="1"`.
  - `persist()` skips writing while there's no user content.
  - The first real content clears the draft flag.
  - `onCleared` deletes a draft that is still empty.
  - List queries (`observeActive`, `observeInNotebook`, `observeByWorkflows`, Faith flows) exclude
    drafts with `metadataJson NOT LIKE '%"draft":"1"%'`.
- Test: a template note left blank disappears; one typed word keeps it.

**App identity ("What's MeetingMind for you?").**
- New preferences in `UserPreferences`:
  - `spaces: Set<NotebookSpace>`, meaning the visible verticals: Work, Learning, Faith, Personal.
  - `lookAndFeel`: **Professional** (Ink/indigo, sans), **Sanctuary** (warm gold accents, serif
    headings, softer surfaces) or **Minimal**.
  - `displayName` for greetings.
- Onboarding adds one screen with picture tiles. Settings gets a "Personalize" page to change it.
- Identity drives:
  - Home greeting (e.g. "Grace and peace, Ana" for Faith-first)
  - default calendar layers
  - which Today cards appear
  - whether Faith appears in Notes and Create
  - Record's default types
  - the tone of notifications
- Theme work: extend `ui/theme` with a `LocalAppLook` CompositionLocal (accent, heading font
  family, card style). Existing screens keep using `Ink`, `Accent` and similar tokens through it.
- **Dark mode**, previously missing, comes with this. The story viewer is dark-first anyway.

## F1. The Today hub: calendar, timeline and layers (the new Home)

**Data: `core/timeline/TimelineRepository`**
- Aggregates sources into
  `TimelineItem(id, layer, kind, start, end?, title, subtitle, cover: CoverSource, accent, people, badge, target: DeepTarget)`.
- Queries run by date range, so each view pages cheaply.
- Sources:

  | Source | Layer | Built on |
  | --- | --- | --- |
  | Device calendar | Events | `core/calendar/CalendarEvents.between` |
  | Recordings | Recordings | MeetingDao date-range query, status and duration shown |
  | Notes | Notes | `eventDate` (non-draft) |
  | Faith items | Faith | Workflow DEVOTIONAL, SERMON, PRAYER, TESTIMONY and others |
  | Prayer requests | Faith | "Praying since" and "Answered on X" milestones |
  | Action items | Tasks | Due dates, from `ActionItemDao` and AI action checklists |
  | Reading-plan steps | Faith | F6 |
  | Daily devotional | Faith | F2 |
  | "On this day" memories | Memories | Past items from the same date |

- **Covers** come from, in order:
  1. the note's first image attachment
  2. the devotional's generated image
  3. a thumbnail of a video frame
  4. the event calendar colour as a gradient
  5. the built-in cover pack, chosen by type
- **Layer settings:** per-layer on/off, colour and "show on Today". Defaults come from identity.
  Professional identity hides every Faith layer.

**Home hero header (`feature/today/HomeHeroHeader.kt`, following the user's reference design)**

The header scrolls with the page rather than sticking, and it's built on the user's own component,
adapted to this app:

- **Top row:**
  - The **avatar**, with a light-catching sweep-gradient ring. The user can change it: tap opens
    the Photo Picker, the image is centre-cropped to a square and stored in app files, and the
    path is kept in preferences. Without a photo it shows initials on an identity-coloured
    gradient. The same avatar appears in Settings, where the display name can also be edited.
  - Greeting and today's date.
  - A floating **glass dock** with Search, Inbox (devotional ready, processing done, prep cards,
    with an unread badge) and **Switch space**. Switch space is a quick toggle between Faith and
    Work views of Today, and it's hidden when only one space is enabled.
- **Hero stage:** a lit card with the reference's **3D press tilt** (springs back), and it
  **recedes on scroll**: it tilts back, scales down and fades. It's driven by the LazyColumn's
  first-item scroll offset rather than a `ScrollState`. The orb and the tile sit on separate
  parallax planes.
- **The orb shows the real time of day**, as a `TimeOfDaySky` model computed from the hour and
  minute:

  | Time | Orb | Sky |
  | --- | --- | --- |
  | Dawn | A low rose-gold sun rising from the left | Peach-to-lilac |
  | Day | A bright sun high on its arc | Sky blue |
  | Golden hour | An amber sun descending | Warm gradient |
  | Dusk | A setting sun | Violet |
  | Night | A **moon** with soft craters and a halo | Navy, with twinkling stars |

  - The orb's position follows a sun arc across the card through the day.
  - Colours interpolate smoothly between phases.
  - The reference's bobbing, cast shadow and ring are kept.
  - With reduced motion on, it stays static.
  - Sanctuary identity warms the palette; Professional keeps it cooler.
- **Playful greetings, the way Claude does them:** a pool per time slot and identity, picked
  deterministically per day so the greeting doesn't flicker. For example:

  | Slot | General | Faith-first |
  | --- | --- | --- |
  | Morning | "Rise and shine, Ana ☀️" | "This is the day the Lord has made, Ana" |
  | Afternoon | "Coffee, then conquer?" | |
  | Night | "Burning the midnight oil?" | "Rest well — He never sleeps" |
  | Sunday morning | | "Ready for church?" |

  The subtitle line is contextual: "3 meetings today · 1 recording processing".
- **Glass stat chips** show the day streak (devotional, or recording for Professional) and this
  week's recordings or notes.
- A **floating "Up next" tile** on its own depth plane shows the next event, today's devotional
  when there's no event, or "Record something" when there's neither. Tapping it opens the event,
  prep card or devotional.
- Fonts: bundle the OFL **Inter** and **Outfit** fonts that the design uses (about 0.5 MB).

**UI: `feature/today`**
- **Today (Home):**
  - The hero header (above).
  - A **swipeable week strip** with activity dots per day, coloured by layer.
  - The **Now / Up next** event card: big, cover-image or calendar-colour gradient, a live
    countdown, and Record / Notes / Prep buttons.
  - Today's cards, in a horizontal carousel then a list: devotional (F2), events, recent
    recordings, notes, prayer and reading items.
  - A "Your stories" ring row that opens the story viewer (F4).
- **Views** (toggle, or pinch to zoom between them):
  - **Day:** hour grid, with events as blocks and recordings as waveform bars at their real time.
  - **Week:** 7 columns.
  - **Month:** heatmap tiles with mini covers.
  - **Agenda:** grouped list.
  - **Timeline "river":** a vertical, infinitely scrolling stream of image-backed cards with
    sticky month and year headers, a fast year scrubber and a "Jump to date" control. This is the
    fun browsing place.
- **Interactions:**
  - Tap opens the target: a note, a recording (`meetingDetailRoute` with `startAtMs` for a
    transcript moment), a verse, or a devotional.
  - Long-press gives quick actions: record, add note, share, mark prayed, complete task.
  - Swipe a day in the strip to move by day.
  - The FAB "+" is context-aware: on a future empty slot it offers "Plan something"; today it
    offers "Record" or "Note".
  - Search box: "Find anything on any day" searches the timeline index, using the existing
    SearchRepository plus date parsing such as "last Sunday" or "March".
- **Intelligence (deterministic first, with AI only where it adds value):**
  - **Meeting prep card** about 15 minutes before an event: last time with these attendees, their
    open action items, and related notes (reuses `RelatedNotes` and the attendee matching in
    note metadata `participants`).
  - **After-event nudge:** "Add notes or record a recap?"
  - **Learned rhythms:** for example "You usually record a sermon on Sundays at 10:30". This
    becomes a pre-armed Record card at that time, computed from past recordings' weekday and hour
    histogram.
  - **Streaks and gentle consistency:** devotional days, prayer days and recording counts. They
    appear as quiet badges, not guilt.
  - **Week in review** (Sunday evening card): what you recorded, decided and prayed, answered
    prayers, and verses you met. It is built deterministically, with an optional AI summary
    through the note AI engine.
- The existing Home recordings list becomes the Timeline with the Recordings layer and the
  current filters (Microphone, Imported, With summaries). Nothing is lost.

## F2. The daily devotional engine

**Model:**
- A devotional **is a Note**: workflow DEVOTIONAL, `metadata.source = AI|CLASSIC|USER`,
  `metadata.day = yyyy-mm-dd`. It therefore shows up in the journey, calendar, search and export
  for free.
- Generated assets are **Attachments** on that note: the voice audio (m4a) and the image.
- The note gets new template sections:

  | Section | Key |
  | --- | --- |
  | Scripture | `scripture` |
  | Reflection | `reflection` |
  | Today I will | `application` |
  | Prayer | `prayer` |
  | A word for today (motivation) | `motivation` |
  | Quote or insight | `insight` |
  | Question to sit with | `question` |
  | My response | `my_response` (user, private) |

**`DevotionalProfile`** (preferences, all optional, sensible defaults):
- Source: AI (default), Classic (choose a book), My own, or Mix (for example a classic on Sunday).
- Tradition: Non-denominational, Evangelical, Catholic, Orthodox, Anglican, Pentecostal or
  Reformed. It shapes wording and calendar awareness, such as Lent and Advent from a computed
  liturgical calendar.
- Translation, length (3, 7 or 12 minutes), language and reading level.
- Tone: pastor, friend, teacher, poet or scholar.
- Focus: topics and a life season (grief, anxiety, new job, marriage, parenting, exams, and so on).
- Series: for example "Psalms of comfort, 14 days" or "Fruit of the Spirit, 9 days". The engine
  carries on from where the series is.
- Include: prayer, motivation, quote, question.
- Delivery time, and quiet hours.

**Adaptivity ("like a friend"):**
- Local signals:
  - recent sermon themes and passages
  - open and answered prayer requests (titles only, local unless the opt-in allows sending)
  - reflection and journal tone
  - which devotionals were finished, listened to or skipped
  - thumbs up or down, and "More like this" / "Less like this"
  - calendar load, for example "a big day ahead" or "a hard week"
- An editable **"About me"** card (a free-text sentence or two) that the user controls.
- Signals are summarised on the phone into a short brief that goes into the prompt, so raw prayers
  never leave the phone unless the user opts in.

**Generation (`ai/devotional/DevotionalEngine` + `DevotionalWorker`):**
- A daily `PeriodicWorkRequest` runs about 2 hours before delivery (default 04:30), prefers
  unmetered networks, and is retried. It generates text, then the image (F4), then the audio (F3),
  then posts a notification at the delivery time.
- Fallback chain: Gemini (Internet mode with a key) → on-device model → bundled classic reading
  for today.
- **Validation (the labelled-AI boundary):**
  - The model returns references only. The parser validates them and `BibleLibrary` fills in the
    verse text.
  - Any quoted verse text that doesn't match is replaced by the real text.
  - A phrase filter rejects prophetic or authority claims.
  - A tradition-consistency instruction is part of the prompt.
  - Quotes must come from a bundled public-domain quote set, with attribution, or be clearly
    labelled as the AI's own reflection.
  - **Safety:** crisis language in the user's own recent entries (self-harm, abuse) triggers a
    gentle card with helpline resources instead of an AI devotional that day.
  - A tested contract clause is added, `DEVOTIONAL_CONTRACT` (alongside the existing
    `FaithPrompts`).
- **Classic library:** bundled `assets/devotionals/*.json.gz`, one entry per date, about 1–2 MB in
  total. Morning & Evening has two readings a day, which map to the morning and evening
  notifications.
- **"My own":** a guided template, plus reminders.

## F3. Voice: the preacher mode

- `ai/voice/SpeechEngine` with two implementations:
  - `GeminiSpeech`: `gemini-3.8-flash-tts` via the interactions endpoint, added to
    `ai/cloud/GeminiHttpTransport`.
  - `DeviceSpeech`: Android `TextToSpeech`, used offline.
- **Preacher styles** are presets that map to a voice plus a `speech_metadata.style`:
  - "Warm pastor"
  - "Gentle friend"
  - "Bold preacher"
  - "Calm teacher"
  - "Storyteller"

  Each has a male or female option and a speed setting, and there's a preview button.
- The text is structured into segments (scripture, reflection, prayer, motivation) with
  `<short pause>` tags between them.
  - The **prayer is spoken** when enabled, with an "Amen" pause.
  - The **motivation** closes the recording.
  - Scripture is read by the same voice, or by an optional second "reader" voice using Gemini's
    two-speaker mode.
- The WAV is converted to AAC/m4a (MediaCodec) and stored as an attachment. Playback reuses the
  existing `PlaybackController` with a lock-screen media session and background play.
- A **"Listen" mini-player** follows you around the app, like the processing pill.

## F4. Today stories and the share studio

**Story viewer (`feature/stories`):**
- Full-screen, with Instagram-style progress bars. Tap for next or previous, hold to pause, swipe
  down to close, swipe up to open the full item or journal.
- Cards, each only if enabled:
  1. Verse of the Day (image background)
  2. Devotional (with Listen)
  3. Prayer (read aloud)
  4. Quote or insight
  5. Today's reading-plan step
  6. Your day (events)
  7. Praying for (people and requests)
  8. On this day (memory)
  9. Week in review (Sundays)
- Reachable from the Today rings, the notification and the widget.
- Also useful to professionals: the "Your day" and "Meeting recap" stories use the same viewer
  for work.

**Share studio (`core/share/ShareCardRenderer` + `feature/share/ShareStudio`):**
- Renders any card to a bitmap by drawing its Compose layout offscreen (`GraphicsLayer`).
- Formats:
  - **Story/Status 1080×1920**
  - **Square 1080×1080**
  - **Wide 1920×1080**
- Backgrounds:
  - **AI-generated** by `gemini-3.1-flash-lite-image` at 9:16, from a verse-aware prompt, with a
    "new background" button and 4 styles (landscape, abstract light, watercolour, minimal).
  - The **built-in pack**: about 24 CC0 photos and gradients as WebP, around 3 MB, which works
    offline.
  - The user's own photos.
  - A solid or gradient colour.
- Controls: bundled OFL fonts (Lora, Playfair Display, Inter; about 0.8 MB), text size and
  alignment, a scrim slider, and translation attribution, which is **always added
  automatically** (licence rule).
- Share targets, via `FileProvider`:
  - The system share sheet, which covers WhatsApp chats and "My status", Facebook, Telegram,
    X and Messages.
  - Direct **WhatsApp** (`setPackage("com.whatsapp")`).
  - **Instagram Stories** (`com.instagram.share.ADD_TO_STORY` with a background image).
  - Facebook Stories needs a Facebook App ID. The share sheet handles Facebook for now, and a
    direct integration is noted as a follow-up.
  - Save to Gallery (MediaStore).
  - Devotional audio is shared as m4a, for example as a WhatsApp voice note.
- **Professional share cards** reuse the same studio: a meeting summary card, a key decision, an
  action list or a quote from a talk.

## F5. Offline Bible library (upgrading what exists)

- Add `HelloAoProvider` behind the existing `BibleProvider` interface
  (`core/scripture/Bible.kt`). `BibleLibrary` then prefers: phone → HelloAO → YouVersion (for
  licensed translations such as NIV once accepted).
- A **translation catalogue** filtered by language (1,256 translations, 51 English). Each shows its
  size and licence link, and downloads with **one request**: `complete.simple.json` through the
  existing resumable `BibleDownloadWorker`, rewritten to import the whole file in one transaction.
- `BibleStore` schema v2 is a **real migration**, because the current `onUpgrade` drops
  everything. It adds:
  - `headings` (section titles, shown in the reader)
  - `audio(bible, book, chapter, narrator, url, timingsJson)` for the audio Bible: stream, or
    download a chapter or book; the reader highlights and follows each verse as it's read
  - `commentary(source, book, chapter, verse, html)`, with optional download per commentary,
    shown in the VerseSheet as "What commentators say", clearly attributed
  - `crossref(from, to, votes)`, shown as "See also" in the verse sheet and reader
  - `highlights(bible, book, chapter, verse, color, noteId?)` and bookmarks, which appear as a
    Highlights layer in the Timeline
- Verse of the Day works offline from a bundled 366-entry list when YouVersion can't be reached.

## F6. Reading plans, the prayer list and the widget

- **Reading plans**, bundled as JSON:
  - Bible in a Year
  - New Testament in 90 days
  - Psalms and Proverbs monthly
  - Gospels in 30 days
  - Chronological
  - Seasonal plans for Advent and Lent

  Progress is stored in table `reading_progress` (migration 14→15). Plans appear on Today, in
  stories and on the calendar, and there's catch-up mode.
- **Praying for (the prayer list):**
  - People and topics with a rotation: pray for 3 each day.
  - Optional **Daily Office reminders** at morning, midday and evening.
  - "Mark prayed" builds a quiet history.
  - Answered items flow into testimonies (existing lifecycle).
- **Home-screen widgets (Glance):** Verse of the Day on its image, and Today at a glance (next
  event and the devotional's Listen button).
- **Notifications:** per-type toggles (devotional ready, evening reflection, prayer reminders,
  reading nudge, meeting prep) and quiet hours. The devotional notification is rich, with the
  image, a Listen action and an Open action.

## F7. "Talk it through" (Gemini Live), last

- An optional voice conversation with the devotional companion about today's reading: reflect,
  ask about the passage, or pray together.
- Uses the Live API (WebSocket audio), under the same labelled-AI boundary with safety rules.
  Transcripts are saved to the devotional note only if the user chooses.
- Gated behind Internet mode, a key, and an explicit enable.

## Additions (not in the request, but these users will expect them)

- **Faith-first vs professional polish:**
  - Liturgical-calendar awareness: Advent, Lent, Easter, Pentecost colours and prompts.
  - Sermon series grouping: consecutive sermons from the same church are grouped automatically on
    the Timeline.
  - A Sunday "Record sermon" smart card.
- **Memory verses**: a light spaced-repetition card in stories, from highlighted verses.
- **Accessibility:** large-text-safe share cards, TalkBack labels on all timeline cards, and
  reduced motion.
- **Backup and restore** (local export zip) before this data becomes precious: notes, devotionals
  and highlights.

## Data and migrations

- Room 14→15:
  - `reading_progress`
  - `prayer_people` (id, name, topic, cadence, lastPrayedAt)
  - `timeline_prefs` is in DataStore rather than Room
  - devotionals reuse `notes` and `attachments`
  - new indexes on `notes(eventDate)` and `meetings(createdAt)` for range queries
- `BibleStore` v1→v2: additive `ALTER` and `CREATE` only, so existing downloads survive.
- Each migration gets a test in the same style as `NotesMigrationTest` and `NoteAiMigrationTest`.

## Reuse (don't rebuild)

- `CalendarEvents` / `UpNext`: the Events layer and meeting prep.
- `NoteRepository` (notes as the single object), `Workflows` templates, `AppNotifications`,
  `DeepLinks`.
- WorkManager patterns: `ProcessingScheduler`, `BibleDownloadWorker`, `NoteAiWorker`.
- `LanguageModelFactory` (fallback chain), `GeminiHttpTransport` (add interactions calls for TTS
  and images), `FaithPrompts`, the `NoteAiEngine` validation style.
- `BibleLibrary` / `BibleStore` / `ScriptureReferenceParser` (verified verse text),
  `RelatedNotes`, `FaithViewModel.journeyOf`, `PlaybackController`.
- `feature/scripture/VerseSheet`, `BibleScreen`, and the Faith screens' card language.

## Delivery

Each milestone ends with tests passing, an arm64 APK in `dist/` (versions v22, v23…), a PLAN doc
update (a new `docs/PLAN_V2.md` recording these decisions), commit and push.

## Verification

- **Unit tests (JVM and Robolectric):**
  - `hasUserContent` and the draft lifecycle
  - `TimelineRepository` merging, date paging and layer filtering (fakes for each source, the
    same fake calendar provider as `CalendarEventsTest`)
  - rhythm learning
  - meeting prep matching
  - devotional prompt contract, reference-to-verse substitution, the prophetic-phrase filter,
    crisis gating, and the fallback chain order
  - classic lookup by date
  - TTS request building (fake transport)
  - share renderer output size and attribution present
  - HelloAO import from a small fixture, and the BibleStore v1→v2 migration keeping existing rows
  - reading-plan progress
- **Screenshots (Roborazzi):** Today, Timeline river, Month, story viewer, share studio, and
  Sanctuary vs Professional looks.
- **Live checks here:** HelloAO download and import of BSB; one Gemini TTS and image call if a key
  is available in the environment (otherwise on the device).
- **On the Galaxy S20:**
  1. Onboard as Faith-first, then again as Professional, and confirm Faith disappears entirely.
  2. The next morning, a devotional notification arrives with its image. Listen with the screen
     locked. Share the verse card to WhatsApp Status.
  3. Turn on airplane mode: the devotional falls back to the on-device model or a classic, the
     phone voice reads it, and the offline Bible and audio work.
  4. Timeline: scroll back months, tap a recording card and land on the transcript moment.
  5. The prep card appears before a real meeting.

## Status

| # | Milestone | Status |
| --- | --- | --- |
| F0 | Empty-note drafts, app identity (spaces, look, avatar) | done (v22) |
| F1 | Today hub: hero, timeline, views, layers, intelligence | done (v22) |
| — | Polish: classic cards, covers, Day view, Begin, inline reading, multi-verse, exports | done (v23) |
| F2 | Daily devotional engine | done (v24) |
| F3 | Voice: the preacher | done (v25) |
| F4 | Stories and share studio | done (v25) |
| F5 | Offline Bible library (HelloAO) | done (v27) |
| F6 | Reading plans, prayer list, widgets | done (v27) |
| F7 | Talk it through (Gemini Live) | done (v26, with Pray with me; fixed in v28) |
| — | Launch readiness: new icon, onboarding, setup guide, getting started, tour | done (v28) |

**Decisions made while building**

- *F0, dark mode deferred.* Screens hard-code white surfaces and ink colours throughout, so dark
  mode means moving them all onto theme tokens. The look (`LocalAppLook`) now exists for that
  move, and new screens use it; the full dark theme is its own piece of work.
- *F0, drafts.* A new note is a draft (a metadata flag) until it has something of the person's in
  it. Lists filter drafts in SQL. Template headings and a calendar event's own title don't count
  as content.
- *F1, one card per moment.* A recording and its note are one timeline card, opening the note; an
  event with a note made for it shows once, as the event. Action items have only free-text
  deadlines, so open tasks appear as a badge on their recording rather than as dated items.
- *F1, the sky.* `TimeOfDaySky` gives the hero a sun that rises, arcs and sets across the right of
  the card (clear of the greeting) and a moon with stars at night, interpolating colours through
  the day. It honours the system's reduced-motion setting.
- *F1, fonts.* Inter and Outfit (OFL) are bundled as static latin subsets, about 270 KB in total.
