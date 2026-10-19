# MeetingMind dev build: 978ab07

Install `MeetingMind-Dev-978ab07.apk` (arm64 phones). The app id is `com.craftflowtechnologies.meetingmind.dev`, so it installs beside the tester app.

Built from `ccr-08b1bb9f-iljmtb` at `978ab07`, which merges `origin/claude/southern-african-asr-research` (`5c70b72`) into the designated branch.

## What is new
- **Settings test entry points:** Settings → About → Onboarding experience restarts onboarding from Welcome. Settings → About → Paywall and subscription opens the paywall. Settings → AI & offline models → Plan and usage has the closed testing toggle (on by default for testers).
- **Onboarding and paywall:** sample-first onboarding with a "Skip to notes" shortcut; no raw BYOK prompt; "Your Personal Space is Ready" summary. Paywall with the 14-day trial timeline, weekly price anchoring, and founder bonus badges.
- **Circles:** feed composer, comments, scripture cards, member profile, and card and story sharing. Milestone 1 Circles: photo and file attachments, mentions, pinned messages, read receipts, typing, moderator role.
- **Offline models:** wider offline ASR and intelligence catalog, kept under 3 GB.
- **Recordings:** a recording with no speech completes with "No speech was detected" instead of stopping at 52%. Opening a recording that is still processing no longer crashes. A failed recording ends in a visible FAILED state with a reason. Deleting a recording mid-run stops it cleanly. A damaged audio file fails after 30 seconds.
- **Moments:** every note gets its moment every time it is opened. Reduced motion, battery saver and low-end phones still get a plain open or a nod.

## Not verified
- Unit suite: 3,177 tests, 1 failure: `WorkHomeLayoutTest.needsRespectsACustomCap`, which also fails on the base commit.
- Not verified on a device: the recording fixes (the 52% hang and crashes were fixed from code, not reproduced). A microphone revoked mid-recording is not handled yet. Google sign-in will fail on `.dev` (no Android OAuth client in `google-services.json`).
- Paywall and onboarding entry points: unit tests only, not walked through on a device.

## Install
1. Enable install from unknown sources for your file manager.
2. Open the APK and install. Uninstall the `.dev` build first if signatures differ.
