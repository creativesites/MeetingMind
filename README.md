# MeetingMind dev build: 8797a89

Install `MeetingMind-Dev-8797a89.apk` (arm64 phones). The app id is `com.craftflowtechnologies.meetingmind.dev`, so it installs beside the tester app.

Built from `ccr-08b1bb9f-iljmtb` at `8797a89`. It includes everything in the `39d6fde` build plus a fix for the language loop.

## Fix in this build
- **Language flashing and crash loop:** after a language change the screen flashed, crashed and reopened in a loop. On launch the app read the saved language before it had loaded, treated it as "follow system", and reset the locale, which recreated the activity over and over. The app now waits for the saved preference before it applies a language, and it saves the language before applying it. Unit tests cover this; the fix has not been checked on a device.
- **Not confirmed:** the crash itself. A logcat trace from the phone would show whether anything else crashes after the loop stops.
- **Android 7 to 12:** the in-app language may only fully update after a restart.

## Where to find things
- **Language:** Settings → Appearance & home → Language. The welcome step of onboarding also has a language pill, but it only shows before onboarding is finished.
- **Work:** the redesigned Work home, Tasks and Projects, with a sunset header, and Task capture from the Work pages.
- **Assistant:** the note editor and the Faith screens.
- **Circles:** the Fellowship entry points and the Circles card on Today and Work.

## What is new since 0eb1516
- **Work redesign:** Today, Tasks and Projects redesigned, with a sunset header and the latest-meeting card on a sunset background. Back arrow, failed recordings shown on Work, and Task capture.
- **Localization:** the locale no longer flashes on recreation, and there is quicker language access from Settings. Nine languages. Most screens still show English text, so a language switch changes only part of the app.
- **Multilingual speech and AI, transcript translation, model deletion guard, AI assistant reliability, Circles profile photos and video posts (work in progress):** as in earlier builds.

## Not verified
- Unit suite on `8797a89`: 3,210 tests, 1 failure: `WorkHomeLayoutTest.needsRespectsACustomCap`, which also fails on the base commit.
- Not verified on a device: the language fix and the flashing, the Work redesign, the language switch across screens, the recording fixes (the 52% hang and crashes were fixed from code, not reproduced). A microphone revoked mid-recording is not handled yet. Google sign-in will fail on `.dev` (no Android OAuth client in `google-services.json`). Circles profile photos and video posts (work in progress). The assistant changes, and the Bible selection bar.
- Native Android 13+ per-app language sync with the system settings picker.

## Install
1. Enable install from unknown sources for your file manager.
2. Open the APK and install. Uninstall the `.dev` build first if signatures differ.
