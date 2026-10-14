# Play Store: build and upload

## How the bundle is built
`.github/workflows/release-aab.yml` builds a signed Android App Bundle on GitHub, never on a laptop. It attaches the bundle to a **draft** GitHub release.

Trigger either way:
- Push a tag `aab-<name>` (for example `aab-v44`) on the commit to release. The release is named after the tag.
- Or push a commit whose message contains `[build-aab]` to the branch the workflow watches (`ccr-5e66b00c-btultn`, see the workflow's `on:` section). Our designated branch is not the trigger.

Tags cannot always be pushed from every environment. If the tag push fails, use the branch trigger, or run the workflow by hand from the Actions tab.

## Secrets (repository settings, never in code)
- `KEYSTORE_BASE64` (the upload keystore, base64 with `base64 -w0 upload.jks`)
- `KEYSTORE_PASSWORD`, `KEY_PASSWORD`
- `SYSTEM_GEMINI_API_KEY` (required: the built-in key testers use for Internet mode)
- Optional: `SYSTEM_DEEPSEEK_API_KEY`, `YOUVERSION_APP_KEY`, `DEEPSEEK_PROXY_URL`

## Before you release
- Bump `versionCode` in `app/build.gradle.kts` (currently 43; `versionName` follows it). Play rejects a repeated code.
- Run the full unit suite on the commit being released.
- Check the release build has no debug-only features on: the dev-only flags are in `app/build.gradle.kts` (`COMPANION_LIVE_WHISPER`, `FEATURE_*`).

## Upload
1. Download the bundle from the draft release.
2. Upload to Play Console (internal or closed testing track).
3. **Delete the draft release afterwards.** The bundle contains the built-in API keys. Do not leave it published or downloadable.

## Store listing and data safety
- Data safety declarations: `docs/DATA_SAFETY.md`.
- Learning is out of the MVP. Do not describe it in the store listing.
