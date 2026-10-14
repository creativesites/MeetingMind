# MeetingMind dev build: 5f6c728

Install `MeetingMind-Dev-5f6c728.apk` (arm64 phones). The app id is `com.craftflowtechnologies.meetingmind.dev`, so it installs beside the tester app.

Built from `ccr-08b1bb9f-iljmtb` at `5f6c728`. Contains the designated branch, Circles v2 and these changes:

## What is in this build
- **Circles v2, first increment:** v1 retired (migration 25 to 26 drops its tables), creation is idempotent, a failed attempt no longer uses up the 5-per-hour limit, and anonymous sign-in failures show a specific message. Worker and Firestore rules are live.
- **Faith home:** "Your week in faith" at the top, and Bible continues from today's reading.
- **Today home:** the sun or moon rides the top of the right column, above the companion. The companion sits lower, 84 dp down. The floating "Up next" card is hidden on Today.
- **Work home:** Circles shows as the same card as Today, in place of the text link. The gap under the hero is closed.
- **Settings:** offers only Today, Work and Calm. A saved Everyday or Focus opens Today. New installs open Today.

## Not verified on a device
- Everything above was checked with unit and screenshot tests only, not on a phone.
- Circles on a phone: create, join, feed, chat, polls.
- Push notifications, and the first-circle permission prompt.
- Behaviour after a reinstall.
- Google sign-in will fail on `.dev`: `google-services.json` has no Android OAuth client for this package yet.

## Known test failures (full suite: 3117 tests)
- `HomeCirclesScreenshotTest` (4): fails in the full suite, passes alone (order-dependent).
- `WorkHomeLayoutTest.needsRespectsACustomCap`: also fails on the base commit.
- `NotesLibraryScreenshotTest.empty`: an uncaught exception before the test starts. Not investigated.
- `CirclesApiTest` could not download its Robolectric artifact in the build environment, so its tests did not run.

## Install
1. Enable install from unknown sources for your file manager.
2. Open the APK and install. Uninstall the `.dev` build first if signatures differ.
