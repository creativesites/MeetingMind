# MeetingMind dev build: 4cbe26b

Install `MeetingMind-Dev-4cbe26b.apk` (arm64 phones). The app id is `com.craftflowtechnologies.meetingmind.dev`, so it installs beside the tester app.

Built from `ccr-08b1bb9f-iljmtb` at `4cbe26b`.

## What is in this build
- **Circles v2, first increment:** v1 retired (migration 25 to 26 drops its tables), idempotent create, failed attempts no longer use up the 5-per-hour limit, and clearer anonymous sign-in errors. Worker and Firestore rules are live.
- **Faith home:** "Your week in faith" at the top, and Bible continues from today's reading.
- **Today home:** the sun or moon sits above the companion, which is 84 dp down. The floating "Up next" card is hidden on Today.
- **Work home:** Circles shows as the same card as Today, in place of the text link. The gap under the hero is closed.
- **Settings:** offers only Today, Work and Calm. A saved Everyday or Focus opens Today. New installs open Today.
- **Moments:** every note gets its moment every time it is opened, including typed notes (no recording), failed notes and sacred notes. Reduced motion and battery saver still give a plain open or a nod.

## Not verified on a device
- Everything above was checked with unit and screenshot tests only, not on a phone.
- Circles on a phone: create, join, feed, chat, polls. Push notifications, the first-circle permission prompt, and behaviour after a reinstall.
- Moments on a phone: the Unveil on each open, and typed notes.
- Google sign-in will fail on `.dev`: `google-services.json` has no Android OAuth client for this package yet.

## Known test failures (full suite: 3117 tests, 5 failing)
- `HomeCirclesScreenshotTest` (4): fails in the full suite, passes alone (order-dependent).
- `WorkHomeLayoutTest.needsRespectsACustomCap`: also fails on the base commit.
- `CirclesApiTest` could not download its Robolectric artifact in the build environment, so its tests did not run.

## Install
1. Enable install from unknown sources for your file manager.
2. Open the APK and install. Uninstall the `.dev` build first if signatures differ.
