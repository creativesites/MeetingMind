# MeetingMind dev build: 7bcd778

Install `MeetingMind-Dev-7bcd778.apk` (arm64 phones). The app id is `com.craftflowtechnologies.meetingmind.dev`, so it installs beside the tester app.

Built from `ccr-08b1bb9f-iljmtb` at `7bcd778`, which includes the other session's onboarding, billing and Gemini proxy commits (merged).

## What is in this build
- **Moments:** every note gets its moment every time it is opened, including typed notes, failed notes and sacred notes. A moment waits for the note's recordings to load, so notes with recordings show it. Reduced motion, battery saver and low-end phones still get a plain open or a nod.
- **Circles v2, first increment:** v1 retired (migration 25 to 26), idempotent create, clearer sign-in errors.
- **Faith home:** "Your week in faith" at the top, and Bible continues from today's reading.
- **Today home:** the sun or moon sits above the companion. The floating "Up next" card is hidden on Today.
- **Work home:** Circles as the same card as Today. The gap under the hero is closed.
- **Settings:** offers only Today, Work and Calm. A saved Everyday or Focus opens Today.
- **Onboarding, billing and the Gemini proxy:** from the other session's commits.

## Not verified
- The full unit suite was **not** run for this build (skipped on request). Last full run was on an earlier head: 3117 tests, 5 failing (4 order-dependent Circles screenshot tests, 1 base-commit WorkHomeLayout test).
- Not verified on a device. Google sign-in will fail on `.dev` (no Android OAuth client in `google-services.json`).

## Install
1. Enable install from unknown sources for your file manager.
2. Open the APK and install. Uninstall the `.dev` build first if signatures differ.
