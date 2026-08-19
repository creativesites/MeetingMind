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
4. (Optional) Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example). **Note:** as of this reconciliation pass, configuring this key causes recorded audio and transcripts to be uploaded to Google's Gemini API — see `docs/AUDIT.md` and `docs/AI_ARCHITECTURE.md` before enabling it, since this currently conflicts with the app's local-first privacy goal.
5. Run the app on an emulator or physical device. The debug build type uses Android's standard auto-generated debug signing — no manual keystore setup is required.
6. If you have already published your app in AI Studio, please [request upload key reset](https://support.google.com/googleplay/android-developer/answer/9842756#zippy=%2Crequest-an-upload-key-reset) in Google Play Console.

## Project Status

This repository is under active reconciliation against a local-first MVP specification. See `docs/AUDIT.md` for a full audit of what's real vs. placeholder, `docs/ROADMAP.md` for prioritized work remaining, and `docs/ARCHITECTURE.md` / `docs/AI_ARCHITECTURE.md` for the current and target architecture.
