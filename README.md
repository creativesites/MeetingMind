# MeetingMind dev build 65b36f6

- File: `MeetingMind-Dev-65b36f6.apk`
- Source: `release/1.0-paid-launch` at `65b36f6`
- SHA-256: `0977e680f5c0614af8e5802e4bd286772b9266dcccbb02c4295b8eae4a6e0695`
- Size: 78,525,059 bytes
- Credential scan passed (only the Firebase key).

## What is new
- Redesigned `LearnHomeScreen` to match Today and Work homes: `ScreenHeader` + context subtitle, pill `SegmentedControl` tabs (`Today`, `Courses`, `Practice`, `Exam`), single focused `HeroCard`, 4-tile `QuickActions` strip (`Record Lecture`, `Audio Cram`, `Write Note`, `Circles`), and `SectionHeader` + grouped `MMCard` lists with `MM.colors.lineSoft` dividers and design tokens.
- Includes all Learning vertical crash fixes, AI JSON hardening, Practice Arena / Exam Prep / Session / Circle Share polish, and `circles2/` compile fixes in the working tree.
- `LearnHomeUiTest`: 2 / 2 passing.

## Not verified
- Not run on a physical Android device yet.
- Full non-learning unit suite was not run in this build.

## Install
Allow installs from this source on your Android device, then open the APK.
