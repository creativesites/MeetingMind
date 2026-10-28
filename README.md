# MeetingMind dev build 2a9b651

- File: `MeetingMind-Dev-2a9b651.apk`
- Source: `release/1.0-paid-launch` at `2a9b651`
- SHA-256: `36cddf7f215467e201124d3d177d822dafb70384e69208a8c68400083388aae1`
- Size: 79,633,254 bytes
- Credential scan passed (only the Firebase key).

## Changed in this build
- Internet mode with no Gemini key transcribes on the phone (the job no longer fails with "Transcription didn't finish").
- The status no longer says Google AI when no cloud call is made.
- Speaker separation gives up after 20 s if its separate process does not start, and the transcript is saved without speaker labels.
- Settings → Diagnostics: device details, last crash, earlier process exits and recent steps; Copy and Share.
- A finished transcript with no language model notifies the person and links to model downloads.
- Import screen crash fixed; YouTube notes open the video in the YouTube app.

## Not verified
- Unit tests were not run; GeminiHttpTransportTest hangs on this branch.
- Not run on a device yet.
- Work circle creation needs the Circles Worker redeployed ("Unknown template").

## Install
Allow installs from this source on your Android device, then open the APK.
