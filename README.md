# MeetingMind dev build: 7863d16

Install `MeetingMind-Dev-7863d16.apk` (arm64 phones). The app id is `com.craftflowtechnologies.meetingmind.dev`, so it installs beside the tester app.

## What is in this build
- **Notes page:** fixed the crash on open (empty bubble dropped a layout slot).
- **Read to Me in Bible study:** labelled controls in the reader ("Read aloud"), the guided session reading step, and the study completion screen ("Listen to my study"). A "Read from here" action on a selected verse starts reading without narration audio.
- **Bible study and Faith home:** the study tile reads "Study a passage". Section tags are checked at 360 dp.
- **Circles on Home:** the Circles tile shows loading, empty (Start / Join), the first three circles, or a failure with Try again.
- **Sun / moon:** moved beside the greeting so it no longer overlaps the Today headline.
- **Work home:** no fix. The gap was not reproduced, so it is still open.
- **Build:** a compile fix in the Bible session (duplicate test tag, ambiguous import).

## Not verified on a device
- Everything above was checked with unit and screenshot tests only, not on a phone.
- Read to Me audio playback and the verse highlight.
- Sun / moon placement on a real device.
- Work-home gap: open.
- Circles needs the Cloudflare Worker and Firestore rules redeployed, and Google sign-in on `.dev` needs the Android OAuth client in `google-services.json`.

## Known test failures on the merged branch
- `WorkHomeLayoutTest.needsRespectsACustomCap` also fails on the base commit.
- `HomeCirclesScreenshotTest` fails only in the full suite and passes alone (order-dependent).
- Canvas player styling for the Bible sheet is not finished (per the Bible session).

## Install
1. Enable install from unknown sources for your file manager.
2. Open the APK and install. Uninstall the `.dev` build first if signatures differ.
