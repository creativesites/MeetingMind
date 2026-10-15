# MeetingMind dev build: 4655d77

Install `MeetingMind-Dev-4655d77.apk` (arm64 phones). The app id is `com.craftflowtechnologies.meetingmind.dev`, so it installs beside the tester app.

Built from `ccr-08b1bb9f-iljmtb` at `4655d77`. It includes the Circles redesign branch (`circles-redesign-m1`, PR #7): R2 media, chat attachments and mentions, pinned messages, read receipts, typing, and the moderator role. It also includes the onboarding, billing and Gemini proxy commits from the other session.

## What is new in this build
- **Circles redesign (first compile):** the Android code from the redesign branch compiles. Four compile fixes were made on top of it: `fold` is an inline extension on `CirclesResult`, the message item opts in to the experimental combined-click API, `mentionText` is composable, and the view models import `fold`.
- **Moments:** every note gets its moment every time it is opened. A plan waits for a note's recordings to load. Reduced motion, battery saver and low-end phones still get a plain open or a nod.
- **Circles v2, first increment, and the Faith, Today and Work home changes** from earlier builds.

## Not verified
- The unit test suite was **not** run for this build. The Circles redesign has never had its tests run.
- Not verified on a device. Google sign-in will fail on `.dev` (no Android OAuth client in `google-services.json`).
- Moments on a device: if "Remove animations" or battery saver is on, moments are suppressed by design (reduced-motion and battery guards).

## Install
1. Enable install from unknown sources for your file manager.
2. Open the APK and install. Uninstall the `.dev` build first if signatures differ.
