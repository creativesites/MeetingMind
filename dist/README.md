# Installable builds

`MeetingMind-v21-arm64-v8a.apk` — debug-signed, arm64-v8a, for device testing.

## What's new in v21

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
