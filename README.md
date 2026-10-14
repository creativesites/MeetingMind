# MeetingMind dev build

**MeetingMind-Dev-b3ab90c.apk**: "MeetingMind Dev" built from commit `b3ab90c` on `claude/meetingmind-mvp-planning-7dgowm`
(arm64-v8a). It installs beside the tester app (`com.craftflowtechnologies.meetingmind.dev`) and keeps its own data.

New in this build:
- **Home screens:** the Today, Work and Calm homes redesigned, with a shared Today header and animated mode switch; the companion clears the sun; notes home with the illustrated sky hero and date sections.
- **Bible:** restyled home, method chooser and session complete; share a verse as a card; read-to-me in the study flow.
- **Read to Me:** background reading, saved-reading summary, listen to saved parts; Gemini audio removed once a note stays on the phone; licensed Bible text to Gemini (owner decision).
- **Moments:** every note gets its moment, including no-speech notes.
- **Devotionals:** bundled reading loads on device; on by default; 00:01 write.
- **Circles:** first increment (v1 retired, creation defects fixed). Needs the Worker and rules redeployed.

Known failing tests in this build (not blocking the APK):
- `WorkHomeLayoutTest.needsRespectsACustomCap` (expected (2,1), got (3,0)).
- `NotesLibraryScreenshotTest` populated, chipsFilter, searching: index out of bounds (list has 3 elements).

Install: allow installs from unknown sources, then open the APK. Only the latest build is kept on this branch.
