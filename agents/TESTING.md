# Testing

> [!NOTE]
> **Local Gradle runs are allowed** (founder decision, 2026-11-16). Run the tests that match your change before committing.
>
> **One build runner (founder decision, 2026-11-16).** This Mac has 8 GB of RAM, so only one session runs Gradle: the Learning session ("MeetingMind Learning premium UX redesign"). Other sessions never start Gradle. Message that session with the exact tasks or test classes, and it queues them, runs them one at a time, and reports back. Use these memory-safe flags: `./gradlew --no-daemon -Dorg.gradle.jvmargs="-Xmx3g -XX:MaxMetaspaceSize=1g" -Pkotlin.compiler.execution.strategy=in-process --max-workers=2 <task>`.

## Commands
- One area: `./gradlew --no-daemon -q -Dorg.gradle.jvmargs=-Xmx2g --max-workers=2 testDebugUnitTest --tests '*<Pattern>*'`
- Full: `./gradlew --no-daemon -q -Dorg.gradle.jvmargs=-Xmx6g --max-workers=4 compileDebugKotlin testDebugUnitTest`
- Screenshots (Roborazzi): add `-Proborazzi.test.record=true` to record, then look at the PNG yourself.
- Results: `app/build/test-results/testDebugUnitTest/*.xml`. Count failures there; the console summary can hide them.

## Known gotchas (each cost real time)
1. **Paused looper.** A wait that depends on `Dispatchers.IO` posting back to the main looper will time out. Inside the wait loop call `ShadowLooper.idleMainLooper(50, TimeUnit.MILLISECONDS)`.
2. **Companion never idle.** The companion's frame clock keeps requesting frames, so Compose never goes idle. In any test that shows a companion, wrap content in `CompositionLocalProvider(LocalCompanionReducedMotion provides true, LocalCompanionForceCanvas provides true)`.
3. **Shared DataStore.** `GeminiCredentialStore` uses one process-wide DataStore. A test that saves a Gemini key must clear it in teardown, or later tests see AI as set up.
4. **Built-in key.** A build made with `SYSTEM_GEMINI_API_KEY` set makes AI "available". Tests for "no AI set up" must set `GeminiCredentialStore.systemKey = null` in setup and restore it after.
5. **Packaged assets vs `useForTest`.** `ClassicDevotionals.useForTest` bypasses the real asset load. Keep at least one test that loads the real packaged reading (`ClassicsAssetLoadTest`).
6. **Dates.** Goldens and tests that depend on today's date or verse drift. Pin the clock in the test.
7. **Colour guard.** `ColorLiteralGuardTest` fails on any `Color(0x...)` outside `ui/theme`. Use tokens (`MM.colors`) or the fixed palettes in `ui/theme/FixedColors.kt`.
8. **Looks-passing-alone.** A test that passes alone and fails in the full suite is usually shared state (gotchas 3 and 4). Run it with its neighbours before calling it flaky.
9. **Compose semantics.** `matchParentSize` is a `BoxScope` member, not a top-level import.

## The test phone
- Samsung Galaxy S20+ (SM-G985F, Android 13), connected over wireless debugging when the founder turns it on. Debug builds install as `com.craftflowtechnologies.meetingmind.dev`, beside the founder's own app, with their own data. Find it with `adb mdns services` or `adb devices -l`; if it is not listed, ask for the IP and port shown under Developer options > Wireless debugging, then `adb connect <ip>:<port>`.
- Install: `./gradlew --no-daemon -q installDebug` (or `adb install -r app/build/outputs/apk/debug/*.apk`). Never `adb uninstall` or clear app data without asking: it holds the founder's notes.
- Screen: `adb exec-out screencap -p > shot.png`; record with `adb shell screenrecord --time-limit 60 /sdcard/mm.mp4` then `adb pull`. Logs: `adb logcat -d | grep -i meetingmind`.

## Device checks (unit tests cannot cover these)
- Moments: First Keep on a note that just finished; Unveil on opening a note from the Notes list.
- Devotional notification at the set time with the app closed.
- Read to Me: `docs/mvp/READ_TO_ME_DEVICE_CHECKLIST.md`.
- Circles: create, join, invite, post, after the Worker and rules are deployed.
