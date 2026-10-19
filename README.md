# MeetingMind dev build: 82124eb

Install `MeetingMind-Dev-82124eb.apk` (arm64 phones). The app id is `com.craftflowtechnologies.meetingmind.dev`, so it installs beside the tester app.

Built from `ccr-08b1bb9f-iljmtb` at `82124eb`. It includes everything in the `fdf5670` build and merges `origin/winston/companion-audit-design` (`fc1a667`), which had not been in earlier builds.

## What is new
- **AI assistant (new in this build):** the assistant reliability fixes. Errors are always shown with Try again where it can help. Permanent failures (rejected key, exhausted quota) no longer offer a retry. A retry keeps the parts of a reply that already applied. Zuri appears in the assistant header, a shared guardrail covers faith answers, and prayer tools are added.
- **Circles:** profile photos and video posts (work in progress). Threaded comment groundwork. The debug diagnostics button is removed from the Circles list. The Circles Worker side (video posts and profile photo endpoints) needs deploying before these work end to end.
- **Where to find them:** the assistant opens from the note editor and the Faith screens. Circles opens from the Fellowship entry points and the Circles card on Today and Work.
- **Bible:** the reader's selection bar is testable on its own, with a screenshot test. No behaviour change. The blank space seen on a phone under the selection bar is still open.
- **Settings test entry points:** Settings → About → Onboarding experience restarts onboarding from Welcome. Settings → About → Paywall and subscription opens the paywall. Settings → AI & offline models → Plan and usage has the closed testing toggle (on by default for testers).
- **Onboarding and paywall:** sample-first onboarding with a "Skip to notes" shortcut; no raw BYOK prompt; "Your Personal Space is Ready" summary. Paywall with the 14-day trial timeline, weekly price anchoring, and founder bonus badges.
- **Circles feed:** composer, comments, scripture cards, member profile, card and story sharing. Milestone 1 Circles: photo and file attachments, mentions, pinned messages, read receipts, typing, moderator role.
- **Offline models:** wider offline ASR and intelligence catalog, kept under 3 GB.
- **Recordings:** a recording with no speech completes with "No speech was detected" instead of stopping at 52%. Opening a recording that is still processing no longer crashes. A failed recording ends in a visible FAILED state with a reason. Deleting a recording mid-run stops it cleanly. A damaged audio file fails after 30 seconds.
- **Moments:** every note gets its moment every time it is opened. Reduced motion, battery saver and low-end phones still get a plain open or a nod.

## Not verified
- Unit suite on `82124eb`: 3,187 tests, 2 failures. `WorkHomeLayoutTest.needsRespectsACustomCap` also fails on the base commit. `CirclesScreenshotTest.feedEmpty` fails only in the full run with an uncaught coroutine exception leaked from an earlier test; it passes when its class runs alone (17 of 17).
- Not verified on a device: the recording fixes (the 52% hang and crashes were fixed from code, not reproduced). A microphone revoked mid-recording is not handled yet. Google sign-in will fail on `.dev` (no Android OAuth client in `google-services.json`). Circles profile photos and video posts (work in progress). The assistant changes and the Bible selection bar.
- Paywall and onboarding entry points: unit tests only, not walked through on a device.

## Install
1. Enable install from unknown sources for your file manager.
2. Open the APK and install. Uninstall the `.dev` build first if signatures differ.
