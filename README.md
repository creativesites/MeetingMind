# MeetingMind dev build: a00c5db

Install `MeetingMind-Dev-a00c5db.apk` (arm64 phones). The app id is `com.craftflowtechnologies.meetingmind.dev`, so it installs beside the tester app.

Built from `ccr-08b1bb9f-iljmtb` at `a00c5db`.

## What is new
- **Recordings (new):** a recording with no speech completes with "No speech was detected" instead of stopping at 52%. Opening a recording that is still processing no longer crashes. A failed recording ends in a visible FAILED state with a reason. Deleting a recording mid-run stops it cleanly. A damaged audio file fails after 30 seconds.
- **Moments:** every note gets its moment every time it is opened. Reduced motion, battery saver and low-end phones still get a plain open or a nod.
- **Circles Milestone 1:** photo and file attachments, mentions, pinned messages, read receipts, typing, moderator role.
- **Onboarding and paywall:** sample-first onboarding; paywall with four billing tiers.

## Not verified
- Unit suite: 3,169 tests, 1 failure: `WorkHomeLayoutTest.needsRespectsACustomCap`, which also fails on the base commit.
- Not verified on a device: the recording fixes (the 52% hang and crashes were fixed from code, not reproduced). A microphone revoked mid-recording is not handled yet. Google sign-in will fail on `.dev` (no Android OAuth client in `google-services.json`).

## Install
1. Enable install from unknown sources for your file manager.
2. Open the APK and install. Uninstall the `.dev` build first if signatures differ.
