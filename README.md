# MeetingMind dev build: d90ed5b

Install `MeetingMind-Dev-d90ed5b.apk` (arm64 phones). The app id is `com.craftflowtechnologies.meetingmind.dev`, so it installs beside the tester app.

Built from `wt-transcription-crash-guard` at `d90ed5b`: everything in dev build `3014407` (`ccr-08b1bb9f-iljmtb`) plus the transcription crash fix below. Built-in Gemini key included.

## What is new: transcription can no longer lock you out of the app
- **No more crash loop.** If offline processing closes the app (it was dying at 52%, speaker detection), the next run skips the step that closed it. Speaker detection and on-device AI are skipped; offline speech recognition stops with a clear message. After two such closes the recording is not restarted on its own; tap Retry to try again. The app always opens.
- **Much less memory.** Audio is converted as it is read instead of loading the whole recording several times over, and the app may use a larger memory allowance. Speaker detection is skipped, with a note, when a recording is too long for the phone's free memory.
- **Better crash reports.** If the app is closed by a native crash, by Android for low memory, or because it froze, the next launch saves that in Settings → crash report, so you can copy it and send it.

## Please test
1. Turn on airplane mode. Record 2 to 5 minutes with two people talking, and let it process offline. Does it pass 52% and finish?
2. A long recording (30+ minutes) offline. Does it finish, perhaps with "Not enough free memory to separate speakers"?
3. If the app closes at any point: reopen it. It should open and show the recording as stopped or skipped, not crash again.
4. After any close, open Settings and copy the crash report into your feedback.

## Not verified on a device
- No emulator or phone was available for this build. The fix is covered by 177 passing unit tests, including new ones for the crash guard and the audio conversion.

## Install
1. Enable install from unknown sources for your file manager.
2. Open the APK and install over the existing app. Don't uninstall first: uninstalling deletes the app's data on the phone. If Android refuses the install, save a copy of your data first, then ask for help.
