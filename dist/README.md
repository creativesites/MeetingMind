# Installable builds

`MeetingMind-v23-arm64-v8a.apk` — debug-signed, arm64-v8a, for device testing.

## What's new in v23

- **Cards that tell things apart.** Home and Timeline cards are clean white cards by default,
  showing the type, the time, the title and a two-line summary (a recording's summary, or the
  opening lines of a note). A card with a picture uses it as its background, with a shade so the
  text stays readable. Prefer the colourful gradient cards? Settings → Look → Card style → Vivid.
- **Cover images.** A note's ⋮ menu → Cover image: pick a photo to use as its cover on Home and
  the Timeline, and at the top of exported documents.
- **A better Day view.** Items are cards pinned to their time, spaced by the real gaps in your day
  ("4 h free"), with a line for now. Items at about the same time stack into a deck; tap +N to
  spread them out.
- **Faith → Begin** is redesigned: a Record a sermon hero and a tile for each kind of entry.
- **Read everything in the note.** Long passages, transcript excerpts and a recording's summary
  open in place; "Read the transcript here" shows the transcript inside the note (tap a line to
  play from it). Videos play inline, with a full-screen button.
- **Add many verses at once**: "John 3:16-21; Ps 23, Rom 8:28, 31-39", one per line or
  separated by ; or , — or type or paste a verse's text yourself.
- **Better PDF and Word exports.** Pictures fill the page width, screenshots of the phone lose
  their status and navigation bars, the cover leads the document, and full passages and
  summaries are printed.
- Empty or junk themes (like "[]") are no longer shown.

## What was new in v22

- **A new Home: Today.** A hero whose sky follows the real time of day — the sun rises, arcs and
  sets; at night a moon and stars — with a playful greeting, your streak, and what's up next
  floating over it. Press and hold the card to tilt it.
- **Your calendar, everywhere.** Agenda, Day, Week, Month and a picture-card Timeline of
  everything you've recorded, written and planned. Choose what shows (Layers button); spaces you
  don't use never appear. Long-press any card for quick actions.
- **For you**: recordings in progress, your rhythms ("You usually record a sermon on Sundays
  around 10:30"), On this day, and your week in review.
- **Make it yours** (onboarding, or Settings → Profile): what you use MeetingMind for (Work,
  Learning, Faith, Personal), a look (Professional, Sanctuary, Minimal), and your photo — tap the
  avatar on Home to change it.
- **Empty notes aren't kept.** A note you open and leave blank — even one with a template's
  headings — is discarded and never shows in a list.

## What was new in v21

- **Up next from your calendar** (Home, or Settings → Calendar): today's meetings and services
  from every calendar on the phone. Tap Record and the recording is titled, typed and set to the
  right number of speakers from the invite, and filed into a note that lists who was there. Read
  only; nothing leaves the phone.
- **AI for notes** (a note's ⋮ menu → AI tools, or ✨ in a notebook): Summarize, Find action items,
  Ask, Organize into sections, and Related notes. Every point shows where it came from — tap to
  jump there. Results wait for you if you leave; applying one can be undone.
- Fixed: choosing a type in Home's Record row now opens the recorder on that type.

## What was new in v20

- **Background transcription that survives.** Minimise the processing screen (↓) and keep using the
  app or the phone; a pill shows progress on the main tabs and a notification tracks it. A finished
  transcript is never started again. If the app is closed mid-way, work resumes from the last
  decoded window when it can run again. Model downloads run the same way and resume where they stopped.
- **Faith space** (Notes → Faith): Verse of the Day → devotional, quick start for Sermon, Devotional,
  Prayer, Prayer request, Gratitude, Testimony, Reflection and Bible study, your prayer requests,
  "On this day", your journey by month, themes, scripture and collections, and media.
- **Prayer requests** track their life: praying since, dated updates, mark answered, then a
  testimony pre-filled from the request and linked to it.
- **The Bible**: read any chapter in any translation licensed to the app, select verses and insert
  them into a note, start a devotional, save to a collection, copy or share. Download an open
  translation (about 2–3 minutes) to read offline and search every verse.
- **Sermons end to end**: Faith → Record sermon; scripture references detected in the transcript;
  a sermon note with key points, quotes, application and prayer; verses with live text.
- **Faith lock** (Settings → Bible & Faith): private Faith notes open with your fingerprint, face
  or screen lock.

**Why the APK is committed here rather than attached to a GitHub release:** releases could not be
created from the environment this was built in (the GitHub API refuses release creation and tag
pushes for that session type), and GitHub Actions is not available on this account. Committing the
artifact is the only route that produces a working download link.

**This costs something.** A ~90 MB binary is in git history permanently — every future clone pays
for it. Delete this file once it has been downloaded, and prefer the release workflow
(`.github/workflows/release.yml`) whenever Actions is available.

## Installing

1. Download the `.apk` on the phone.
2. Allow "install from unknown sources" for the browser or file manager you used.
3. Open it and install.

It installs alongside nothing else — the application id is unchanged, so this replaces any earlier
MeetingMind build on the device and keeps its database.

## Before you test Internet mode

Settings → Privacy → turn **On-device only** off → paste your own Gemini API key. The key is
stored on the phone and is not in this APK, the source, or the build.
