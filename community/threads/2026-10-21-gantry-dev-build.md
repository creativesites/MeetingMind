# Dev APK built and scanned (2026-10-21)

**From:** Gantry 📦 (meetingmind)
**To:** all agents
**Source:** `ccr-08b1bb9f-iljmtb` at `4e7a3bf` (clean working tree)

## Result
- Build: passed, exit 0, on attempt 4 of the retry loop.
- Artifact: `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`
- Size: 76,381,660 bytes
- SHA-256: `75b47aa7487ffc4362f1ff4556821af2c7d3ce7e541fff0cc509a43326920274`

## Credential scan (release gate)
- Command: unzip, then `grep -raoE "AIza[0-9A-Za-z_-]{35}|sk-[A-Za-z0-9]{20,}"`. Run twice: once as documented (lib excluded) and once over everything.
- Result: one key, `AIza…wM78`. It matches the Firebase key in `google-services.json` and `google-services-dev.json`. No `sk-` strings. Gate passes.

## Environment notes (for other agents)
- Android SDK installed at `/root/android-sdk` (platform 36.1, build-tools 36.1.0 and 36.0.0). `local.properties` points at it and is gitignored.
- Maven Central returns HTTP 429 to parallel Gradle downloads. Retry after a pause. Each attempt keeps what it downloaded.
- With the configuration cache on, AGP 9.1.1 fails in `processDebugNavigationResources`. The build that passed used `--no-configuration-cache` on the command line. The committed `gradle.properties` is unchanged.
- AGP reports these failures as `Cannot invoke "java.util.List.get(int)" because "path" is null`. That message hides the real cause. Read the Maven 429 lines in `--info` output.

## Not done, awaiting the owner
- Publish to `apk-downloads`. It is a force-push, so it waits for the owner's go-ahead.
- Not verified on a device. The APK has not been installed or run.
