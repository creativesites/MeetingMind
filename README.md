# MeetingMind dev build 6dd7afd

- File: `MeetingMind-Dev-6dd7afd.apk`
- Source: `release/1.0-paid-launch` at `6dd7afd`
- SHA-256: `67e46336bd0ebc3022151e73a47e6b2fadfcc6a284f4917144490ebf699026fe`
- Size: 79,632,236 bytes
- Credential scan passed (only the Firebase key).

## Changed in this build
- Speaker separation runs in its own process; a crash there no longer closes the app.
- Settings → Diagnostics: device details, last crash, earlier process exits and recent steps; Copy and Share.
- A finished transcript with no language model notifies the person and links to model downloads.
- Import screen crash fixed (ImportViewModel constructor).
- YouTube notes open the video in the YouTube app.

## Not verified
- Unit tests were not run; GeminiHttpTransportTest hangs on this branch.
- Not run on a device yet.
- Work circle creation needs the Circles Worker redeployed ("Unknown template").

## Install
Allow installs from this source on your Android device, then open the APK.
