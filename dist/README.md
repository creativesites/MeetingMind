# Installable builds

APKs are no longer committed to source branches; they bloated the repository (over 600 MB).

The current dev build is published on the **`apk-downloads`** branch, which holds only the latest APK:
https://github.com/creativesites/meetingmind/tree/apk-downloads

The dev build installs beside the tester/Play app as "MeetingMind Dev" (`com.craftflowtechnologies.meetingmind.dev`),
signed with the fixed `app/dev-debug.keystore`, so updates install over each other and keep their data.
