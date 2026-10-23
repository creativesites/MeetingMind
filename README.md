# MeetingMind dev build: 3014407

Install `MeetingMind-Dev-3014407.apk` (arm64 phones). The app id is `com.craftflowtechnologies.meetingmind.dev`, so it installs beside the tester app.

Built from `ccr-08b1bb9f-iljmtb` at `3014407`. It includes the complete Circles & Work implementation, built-in Gemini API key, modern staged media attachments with text, Devotional and Study note pickers, redesigned composer, and multi-model speech recognition selection (`3014407`, `01877e2`, `d54baec`).

## What is new
- **Built-in System API Key**:
  - Embedded `BuildConfig.SYSTEM_GEMINI_API_KEY` into dev and test builds. Testers and onboarding users can record and transcribe immediately without entering an API key or waiting for offline downloads.
- **Modern Staged Media & Caption Uploads**:
  - Staged attachment preview banner with thumbnail, video badge, or PDF icon and remove action.
  - Send media (photos, videos, PDFs) alongside typed text in a single message.
  - Full tap-to-open view for PDFs via `FileProvider` with download progress indicators.
  - Video MIME and QuickTime/3GPP video support on both client and Cloudflare Worker.
- **First-Class Devotional & Study Sharing on Feed**:
  - "Share a devotional" and "Share from your study" are always offered in faith circles.
  - **Devotional Picker Sheet**: Choose Today's reading or browse saved/classic devotionals; pre-fills reflections and scripture reference.
  - **Study Notes Picker Sheet**: Choose from Bible study, sermon, or personal reflection notes.
- **Redesigned Post Composer UI**:
  - Modern curved container with rounded card styling (`RoundedCornerShape(20.dp)`).
  - Quick action banners for Devotionals and Study Notes.
  - Integrated tool pills for Photos, Videos, and Scripture Verses.
  - Theme-aware illuminated dark mode tokens (`Color(0xFF231F1A)`, gold accents, warm off-white scripture) avoiding dull grey surfaces.
- **Reliable Card Creation & Chat Handoff**:
  - `CreateChatCard.toPayload` falls back to prompt or source text when draft text is blank, guaranteeing cards created in Create Studio can be shared directly into Circle chat.
  - Prominent "Send to chat" CTA in the studio header and Words/Share steps.
- **Circles in Work Vertical**:
  - Added "Circles" tab (`WorkSegment.CIRCLES`) to Work Page navigation with direct links to Team Circles.
- **Multi-Model Offline Selection in Onboarding**:
  - Onboarding `SetupStep` allows choosing speech recognition models tailored to the user's needs:
    - **Parakeet TDT 0.6B**: High accuracy for English and European languages.
    - **Omnilingual 300M**: Lightweight model optimized for African and Southern African languages (Shona, Ndebele, Bemba, Nyanja, Zulu, Swahili, etc.).
    - **Omnilingual 1B**: High-capacity multilingual model for deep African language coverage.
    - **Whisper Turbo**: Global multilingual transcription.

## Not verified on a device
- Physical camera hardware handoff and video playback on a real physical Android phone.
- Google sign-in on `.dev` (no Android OAuth client configured in `google-services.json`).

## Install
1. Enable install from unknown sources for your file manager.
2. Open the APK and install over the existing app. Don't uninstall first: uninstalling deletes the app's data on the phone. If Android refuses the install, save a copy of your data first, then ask for help.
