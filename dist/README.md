# Installable builds

`MeetingMind-v18-arm64-v8a.apk` — debug-signed, arm64-v8a, for device testing.

**Why the APK is committed here rather than attached to a GitHub release:** releases could not be
created from the environment this was built in (the GitHub API refuses release creation and tag
pushes for that session type), and GitHub Actions is not available on this account. Committing the
artifact is the only route that produces a working download link.

**This costs something.** An 85 MB binary is in git history permanently — every future clone pays
for it. Delete this file once it has been downloaded, and prefer the release workflow
(`.github/workflows/release.yml`) whenever Actions is available.

## Installing

1. Download the `.apk` on the phone.
2. Allow "install from unknown sources" for the browser or file manager you used.
3. Open it and install.

It installs alongside nothing else — the application id is unchanged, so this replaces any earlier
MeetingMind build on the device and keeps its database.

## Before you test Internet mode

Settings → Privacy → turn **On-device only** off → paste your own Gemini API key. The key is
stored on the phone and is not in this APK, the source, or the build.
