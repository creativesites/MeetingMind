# MeetingMind dev build: fdf5670

Install `MeetingMind-Dev-fdf5670.apk` (arm64 phones). The app id is `com.craftflowtechnologies.meetingmind.dev`, so it installs beside the tester app.

Built from `ccr-08b1bb9f-iljmtb` at `fdf5670`. It includes everything in the `aac5b0b` build, the Circles work from `ccr-c4d22493-kjxz3d` (`374a23f`), and a Bible test change.

## What is new
- **Circles:** profile photos, video posts, and threaded comment groundwork (work in progress in the app). The debug diagnostics button is removed from the Circles list. The Circles Worker side (video posts and profile photo endpoints) needs deploying before these work end to end.
- **Bible:** the reader's selection bar is now testable on its own, with a screenshot test. No behaviour change yet. The blank space seen on a phone under the selection bar is still open; the bar itself renders at its natural height in isolation, so the cause is in the reader's layout around it.
- **Settings test entry points:** Settings → About → Onboarding experience restarts onboarding from Welcome. Settings → About → Paywall and subscription opens the paywall. Settings → AI & offline models → Plan and usage has the closed testing toggle (on by default for testers).
- **Onboarding and paywall:** sample-first onboarding with a "Skip to notes" shortcut; no raw BYOK prompt; "Your Personal Space is Ready" summary. Paywall with the 14-day trial timeline, weekly price anchoring, and founder bonus badges.
- **Circles feed:** composer, comments, scripture cards, member profile, card and story sharing. Milestone 1 Circles: photo and file attachments, mentions, pinned messages, read receipts, typing, moderator role.
- **Offline models:** wider offline ASR and intelligence catalog, kept under 3 GB.
- **Recordings:** a recording with no speech completes with "No speech was detected" instead of stopping at 52%. Opening a recording that is still processing no longer crashes. A failed recording ends in a visible FAILED state with a reason. Deleting a recording mid-run stops it cleanly. A damaged audio file fails after 30 seconds.
- **Moments:** every note gets its moment every time it is opened. Reduced motion, battery saver and low-end phones still get a plain open or a nod.

## Not verified
- Unit suite (run on `374a23f`, the same app code as this build apart from the Bible test): 3,177 tests, 1 failure, `WorkHomeLayoutTest.needsRespectsACustomCap`, which also fails on the base commit.
- Not verified on a device: the recording fixes (the 52% hang and crashes were fixed from code, not reproduced). A microphone revoked mid-recording is not handled yet. Google sign-in will fail on `.dev` (no Android OAuth client in `google-services.json`). Circles profile photos and video posts (work in progress). The Bible selection bar's blank space.
- Paywall and onboarding entry points: unit tests only, not walked through on a device.

## Install
1. Enable install from unknown sources for your file manager.
2. Open the APK and install. Uninstall the `.dev` build first if signatures differ.
