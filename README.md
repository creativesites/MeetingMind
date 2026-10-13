# MeetingMind dev build

**MeetingMind-Dev-e0d9935.apk**: "MeetingMind Dev" built from commit `e0d9935` on `claude/meetingmind-mvp-planning-7dgowm`
(arm64-v8a). It installs beside the tester app (`com.craftflowtechnologies.meetingmind.dev`) and keeps its own data.

New in this build:
- **Devotionals:** the bundled reading now loads on device (the packaged file was not the one the code asked for, so the page showed nothing). Daily devotionals are on by default for new installs, with the 00:01 write. Unreadable readings now say so plainly.
- **Moments:** the Unveil plays when a note opens from the Notes list and from meeting detail / wrap-up; First Keep on note-ready. Planner may stay plain for short or no-speech recordings.
- **Today home:** the design update, illustrated tiles, colours on theme tokens.
- **Read to Me, companion states (Curious, Proud, Reading), sample-first onboarding, recording markers, migration 25.**
- **Circles:** creation still uses the old encrypted code; migration to the Worker is pending.

Install: allow installs from unknown sources, then open the APK. Only the latest build is kept on this branch.
