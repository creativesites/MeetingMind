# MeetingMind dev build: 5f39cd1

Install `MeetingMind-Dev-5f39cd1.apk` (arm64 phones). The app id is `com.craftflowtechnologies.meetingmind.dev`, so it installs beside the tester app.

Built from `ccr-08b1bb9f-iljmtb` at `5f39cd1`. It includes everything in the `8797a89` build, the Circles work from `ccr-c4d22493-kjxz3d` (`d2c1b6a`), and the Bible selection sheet redesign from `claude/meetingmind-localization` (`518e962`).

## What is new
- **Circles:** profile photo controls, sending a card to a circle chat, Pro-gated Google sign-in with threaded comments and a comment composer (`8cd8858`, `d2c1b6a`). Google sign-in is part of Pro; free accounts see a note and their circles stay on the phone.
- **Bible:** the selection sheet is redesigned, with horizontal scrolling and rounded corners.
- **Compile fixes:** two compile errors in the merged Circles and share code are fixed in `5f39cd1`.

## Not in this build
- The devotional share rules (`6e7816d`, Circles devotional messages). It is committed only in a local checkout on the Circles side and has not been pushed, so this build does not include it.

## Not verified
- Unit suite on `5f39cd1`: 3,210 tests. `WorkHomeLayoutTest.needsRespectsACustomCap` fails and also fails on the base commit. The four `AccountLinkingTest` failures from the merged Circles code are fixed; that test passes (5 of 5) when run alone.
- The Circles work came from another session and was not compiled there (the Maven rate limit blocked their build). The two compile errors were fixed here.
- Not verified on a device: the Circles changes, the Bible selection sheet, the Work redesign, the language switch, and the language loop fix. A microphone revoked mid-recording is not handled yet. Google sign-in will fail on `.dev` (no Android OAuth client in `google-services.json`).

## Install
1. Enable install from unknown sources for your file manager.
2. Open the APK and install over the existing app. Don't uninstall first: uninstalling deletes the app's data on the phone. If Android refuses the install, save a copy of your data first, then ask for help.
