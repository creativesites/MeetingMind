# MeetingMind dev build: 39d6fde

Install `MeetingMind-Dev-39d6fde.apk` (arm64 phones). The app id is `com.craftflowtechnologies.meetingmind.dev`, so it installs beside the tester app.

Built from `ccr-08b1bb9f-iljmtb` at `39d6fde`. It includes everything in the `0eb1516` build, the localization crash fixes from `origin/claude/meetingmind-localization`, and the Work redesign from `origin/work-ux-redesign`.

## Where to find things
- **Language:** Settings → Appearance & home → Language. The welcome step of onboarding also has a language pill, but it only shows before onboarding is finished.
- **Work:** the redesigned Work home, Tasks and Projects, with a sunset header, and Task capture from the Work pages.
- **Assistant:** the note editor and the Faith screens.
- **Circles:** the Fellowship entry points and the Circles card on Today and Work.

## What is new
- **Localization fixes:** the locale no longer flashes when it is recreated, and there is quicker language access from Settings. The interface is translated in nine languages, but only the parts that use string resources change. Most screens still show English text, so a language switch changes only part of the app.
- **Work redesign:** Today, Tasks and Projects redesigned, with a sunset header and the latest-meeting card on a sunset background. Back arrow, failed recordings shown on Work, and Task capture.
- **Multilingual speech and AI, transcript translation, model deletion guard:** as in `0eb1516`.
- **AI assistant:** the reliability fixes, Zuri in the header, a shared guardrail for faith answers, and prayer tools.
- **Circles:** profile photos and video posts (work in progress), threaded comments groundwork. The Circles Worker side needs deploying before video posts and profile photos work end to end.
- **Recordings:** a recording with no speech completes with "No speech was detected" instead of stopping at 52%. Opening a recording that is still processing no longer crashes. A failed recording ends in a visible FAILED state with a reason. Deleting a recording mid-run stops it cleanly. A damaged audio file fails after 30 seconds.
- **Moments:** every note gets its moment every time it is opened. Reduced motion, battery saver and low-end phones still get a plain open or a nod.

## Not verified
- Unit suite on `39d6fde`: 3,206 tests, 1 failure: `WorkHomeLayoutTest.needsRespectsACustomCap`, which also fails on the base commit.
- The build had a compile error from the Work redesign's two `matchParentSize` imports; `39d6fde` removes them, and the build passes.
- Not verified on a device: the Work redesign, the language switch across screens, the recording fixes (the 52% hang and crashes were fixed from code, not reproduced). A microphone revoked mid-recording is not handled yet. Google sign-in will fail on `.dev` (no Android OAuth client in `google-services.json`). Circles profile photos and video posts (work in progress). The assistant changes, and the Bible selection bar.
- Native Android 13+ per-app language sync with the system settings picker.

## Install
1. Enable install from unknown sources for your file manager.
2. Open the APK and install. Uninstall the `.dev` build first if signatures differ.
