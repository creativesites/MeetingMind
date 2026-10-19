# MeetingMind dev build: ed241be

Install `MeetingMind-Dev-ed241be.apk` (arm64 phones). The app id is `com.craftflowtechnologies.meetingmind.dev`, so it installs beside the tester app.

Built from `ccr-08b1bb9f-iljmtb` at `ed241be`. It combines the companion moments, redesigned onboarding and paywall, 4-tier billing, the Circles Milestone 1 redesign (R2 media, chat attachments, pinned messages, read receipts, typing, moderator role), and the southern-African ASR research branch.

## What is new
- **Onboarding and paywall:** sample-first onboarding; paywall with four billing tiers. Release builds run with billing off.
- **Circles Milestone 1:** photo and file attachments, mentions, pinned messages, read receipts, typing, moderator role. The app ignores empty live posts.
- **Moments:** every note gets its moment every time it is opened. Reduced motion, battery saver and low-end phones still get a plain open or a nod.

## Not verified
- The full unit suite was run on the merged head before the final fixes: 3160 tests, 1 base-commit failure (`WorkHomeLayoutTest`) and 2 fixed since (paywall colour guard, Circles parsing). The final fixes were re-run on their own: colour guard, Circles parsing and Circles redesign tests all pass.
- Not verified on a device. Google sign-in will fail on `.dev` (no Android OAuth client in `google-services.json`).
- Moments on a device: if "Remove animations" or battery saver is on, moments are suppressed by design.

## Install
1. Enable install from unknown sources for your file manager.
2. Open the APK and install. Uninstall the `.dev` build first if signatures differ.
