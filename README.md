<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/ad6f1fbe-42ac-4d50-a935-b7666b3b23cc

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Run the app on an emulator or physical device. The debug build type uses Android's standard auto-generated debug signing — no manual keystore setup is required.
5. (Optional) To enable Google Sign-In, add your own `google-services.json` to `app/`. The app works fully offline without it — local recording, storage, and (once integrated) on-device AI processing never require any cloud configuration or API key.
6. If you have already published your app in AI Studio, please [request upload key reset](https://support.google.com/googleplay/android-developer/answer/9842756#zippy=%2Crequest-an-upload-key-reset) in Google Play Console.

## Project Status

This repository is under active reconciliation against a local-first MVP specification. As of this pass, the app no longer calls any cloud AI API — see `docs/AUDIT.md` for the full audit history, `docs/ROADMAP.md` for prioritized work remaining, and `docs/ARCHITECTURE.md` / `docs/AI_ARCHITECTURE.md` for the current and target architecture. Local AI (VAD/ASR/diarization/LLM) is not yet installed on-device — see `docs/AI_ARCHITECTURE.md` for current status; the app honestly reports "model not installed" rather than fabricating results.
