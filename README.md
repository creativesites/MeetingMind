# MeetingMind dev build: 1cb6654

Install `MeetingMind-Dev-1cb6654.apk` (arm64 phones). The app id is `com.craftflowtechnologies.meetingmind.dev`, so it installs beside the tester app.

Built from `ccr-08b1bb9f-iljmtb` at `1cb6654`. It includes everything in the `4e7a3bf` build, plus the Work consolidation, navigation fix, and Read to Me Bible license policies (`d1628f7`, `c57c4bc`, `1cb6654`).

## What is new
- **Unified Work Surface (Decision D1)**: Retired `ProfessionalHome.kt` and removed `PROFESSIONAL` style from settings. `WorkPageScreen` is now the single, canonical surface for all Work workflows, eliminating UI drift and duplicate code paths.
- **Work Back Navigation Trap Fixed (W-01)**: Connected `onNavigateBack` directly to `onBack` in `WorkPageScreen.kt`. Navigating into Work from Home or Today now provides a functional back arrow.
- **Read to Me Bible AI Restriction (Decision D2)**: Restricted cloud AI reflection strictly to public-domain translations (KJV and WEB). Licensed translations (NIV, ESV, NLT, etc.) remain 100% on-device on the system TTS voice.
- **Work Circles Collaboration**: Space-aware Circles (`work` vs `faith`), work-tailored templates (Project Team, Client Engagement, Exec Board, Department), professional post types (`task`, `decision`, `brief`, `standup`, `blocker`), professional reactions, and memorable invite codes.
- **Post-Meeting "Share to Circle"**: Wrap-up screen one-tap publishing of meeting brief, decisions, and action items to a chosen Work Circle.
- **In-Circle Search & Feed Experience**: Real-time post search within circles and task completion toggle on feed action cards.
- **Audio & Reliability**: Safe handling for very short recordings and credential exclusion from cloud backup.
- **Build Toolchain Fix**: Configuration cache serialization resolved for AGP 8.x XML tasks.

## Not verified on a device
- The newly wired `WorkPageScreen` back navigation and layout on various screen densities.
- The Read to Me public-domain filter behavior with diverse user network conditions.
- End-to-end multi-device email invite acceptance flow against live production backend.
- Google sign-in on `.dev` (no Android OAuth client configured in `google-services.json`).

## Install
1. Enable install from unknown sources for your file manager.
2. Open the APK and install over the existing app. Don't uninstall first: uninstalling deletes the app's data on the phone. If Android refuses the install, save a copy of your data first, then ask for help.
