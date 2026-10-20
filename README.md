# MeetingMind dev build: 0eb1516

Install `MeetingMind-Dev-0eb1516.apk` (arm64 phones). The app id is `com.craftflowtechnologies.meetingmind.dev`, so it installs beside the tester app.

Built from `ccr-08b1bb9f-iljmtb` at `0eb1516`. The app code is the same as `39926dd`; the last commit changes only a database test.

## What is new
- **App localization (new):** the interface can be shown in English, French, Spanish, Portuguese, Swahili, Nyanja, Shona, isiZulu and Bemba. Choose it in Settings → Appearance → Language, or on the Welcome step of onboarding. "Follow system language" is the default. The spoken language, AI answers and transcript translations are set separately from the interface language.
- **Multilingual speech and AI (new):** recordings can be transcribed in more languages, with Whisper used for the languages it handles best. AI notes keep natural English loanwords, for example in Shona, Ndebele, Bemba and Nyanja.
- **Transcript translation (new):** you can translate a transcript without changing the original. Translations are stored separately (database schema 27).
- **Models:** deleting a speech model is refused while a processing job uses it. If the active model is deleted, the app falls back to a default.
- **AI assistant:** the reliability fixes. Errors are always shown, with Try again where it can help. Permanent failures (rejected key, exhausted quota) no longer offer a retry. A retry keeps the parts of a reply that already applied. Zuri appears in the assistant header, a shared guardrail covers faith answers, and prayer tools are added.
- **Circles:** profile photos and video posts (work in progress). Threaded comment groundwork. The debug diagnostics button is removed from the Circles list. The Circles Worker side (video posts and profile photo endpoints) needs deploying before these work end to end.
- **Where to find them:** the assistant opens from the note editor and the Faith screens. Circles opens from the Fellowship entry points and the Circles card on Today and Work.
- **Bible:** the reader's selection bar is testable on its own, with a screenshot test. No behaviour change. The blank space seen on a phone under the selection bar is still open.
- **Settings test entry points, onboarding and paywall:** Settings → About → Onboarding experience restarts onboarding from Welcome. Settings → About → Paywall and subscription opens the paywall. Settings → AI & offline models → Plan and usage has the closed testing toggle. Onboarding is sample-first with a "Skip to notes" shortcut.
- **Circles feed:** composer, comments, scripture cards, member profile, card and story sharing. Milestone 1 Circles: photo and file attachments, mentions, pinned messages, read receipts, typing, moderator role.
- **Offline models:** wider offline ASR and intelligence catalog, kept under 3 GB.
- **Recordings:** a recording with no speech completes with "No speech was detected" instead of stopping at 52%. Opening a recording that is still processing no longer crashes. A failed recording ends in a visible FAILED state with a reason. Deleting a recording mid-run stops it cleanly. A damaged audio file fails after 30 seconds.
- **Moments:** every note gets its moment every time it is opened. Reduced motion, battery saver and low-end phones still get a plain open or a nod.

## Not verified
- Unit suite on `39926dd` (app code of this build): 3,204 tests, 6 failures. `WorkHomeLayoutTest.needsRespectsACustomCap` also fails on the base commit. `OnboardingTest` (2), `HomeStartTest` (1) and `BibleScreenshotTest.home_first_time` fail only in the full run, from an uncaught coroutine exception leaked by an earlier test, and pass when their classes run alone. `Migration25To26Test` was a real failure (it pinned schema 26); it is fixed in `0eb1516` and passes.
- Native Android 13+ per-app language sync with the system settings picker. Narrow-screen layout of the longer Bantu labels.
- Not verified on a device: the recording fixes (the 52% hang and crashes were fixed from code, not reproduced). A microphone revoked mid-recording is not handled yet. Google sign-in will fail on `.dev` (no Android OAuth client in `google-services.json`). Circles profile photos and video posts (work in progress). The assistant changes, the localization, the multilingual speech, and the Bible selection bar.
- Paywall and onboarding entry points: unit tests only, not walked through on a device.

## Install
1. Enable install from unknown sources for your file manager.
2. Open the APK and install. Uninstall the `.dev` build first if signatures differ.
