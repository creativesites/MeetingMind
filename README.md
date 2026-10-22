# MeetingMind dev build: d54baec

Install `MeetingMind-Dev-d54baec.apk` (arm64 phones). The app id is `com.craftflowtechnologies.meetingmind.dev`, so it installs beside the tester app.

Built from `ccr-08b1bb9f-iljmtb` at `d54baec`. It includes the complete Circles Redesign, Work Execution Space, Meeting Privacy Safety Gate, Audio Hardware Resilience, and Read to Me Bible license policies (`d54baec`, `8d38132`, `d1628f7`, `1cb6654`).

## What is new
- **Work Circles Execution & Continuity Space**: Purpose-built execution hub (`WorkOverviewTab`) answering "what changed, what needs a decision, who owns the next commitment, and the latest meeting brief".
  - **Active Blockers**: Red alert cards with "Help unblock" threads and "Mark resolved" action.
  - **Latest Meeting Brief**: Displays the latest shared meeting brief with direct links to discussion and source notes.
  - **Commitments & Tasks**: Action items with assignee avatars and interactive check toggles.
  - **Key Decisions**: Recorded decision log showing consensus reactions (Approved ✅, On it 👍) and comment links.
  - **What Changed / Standups**: Timeline cards summarizing status updates from team members.
  - **Work Quick Operations Row**: One-tap quick chips: `+ Action item`, `+ Decision`, `+ Meeting brief`, `+ Status update`, `+ Blocker`.
- **Dynamic Navigation**: `CircleHomeScreen` presents `Overview` · `Updates` · `Chat` · `Team` for Work circles, while Faith circles maintain `Feed` · `Chat` · `Members`.
- **Work Meeting Sharing Privacy Safety Gate**: Strictly blocks confidential/on-device meeting notes from leaking to server-side circles (`WorkPrivacy.mustStayOnDevice`), restricts destinations strictly to Work spaces, and tracks item-level IDs for safe retries without duplicate posts.
- **Faith Devotional Sharing (End-to-End)**: Added `devotional` post type on Android and Cloudflare Worker with gold styling badge, composer placeholder, and quick chip.
- **Verse Dark Mode Styling**: Fixed grayish/washed-out cards across feed, composer, and comment threads using theme semantic tokens (`surfaceSunk`, `gold`, `goldWash`, `line`).
- **Member Avatars**: Hooked `avatarVersion` to `MemberAvatar` and disk/memory caching across feed cards, hero headers, composer, and chat.
- **Chat Media & PDF Uploads**: Magic-byte sniffing (JPEG, PNG, PDF, WEBP, MP4) and PDF MIME normalization (`application/x-pdf` & `application/pdf`) on client and server.
- **Card-to-Chat Sharing Flow**: Added prominent "Send to chat" button directly in the header on all card creator steps.
- **Chat & Events Polish**: Modern rounded pill chat input with circular attachment launcher, and dark-mode calendar day tiles for events.
- **Audio Capture Hardware Error Listeners (STAB-02)**: Added `MediaRecorder.setOnErrorListener` and `setOnInfoListener` to handle microphone revocation or media server death mid-stream.
- **Work QuickAdd Auto-Focus (W-03)**: Soft keyboard and focus request immediately upon selecting "Add task" or "Task".

## Not verified on a device
- End-to-end multi-device email invite acceptance flow against live production backend.
- End-to-end real camera/photo picker hardware handoff on a physical phone.
- Google sign-in on `.dev` (no Android OAuth client configured in `google-services.json`).

## Install
1. Enable install from unknown sources for your file manager.
2. Open the APK and install over the existing app. Don't uninstall first: uninstalling deletes the app's data on the phone. If Android refuses the install, save a copy of your data first, then ask for help.
