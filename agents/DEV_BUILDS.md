# Dev APK: build and publish

## Build
```sh
cd /home/user/MeetingMind
./gradlew --no-daemon -q -Dorg.gradle.jvmargs=-Xmx6g --max-workers=4 assembleDebug
```
Output: `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` (about 71 to 74 MB).
- Debug builds use application id `com.craftflowtechnologies.meetingmind.dev`, so they install beside the tester app and keep their own data.
- Run the full unit suite first: `./gradlew --no-daemon -q -Dorg.gradle.jvmargs=-Xmx6g --max-workers=4 compileDebugKotlin testDebugUnitTest` (about 15 to 20 minutes; run in the background).

## Scan before publishing (mandatory)
```sh
unzip -q -o <apk> -x 'lib/*' -d /tmp/apkscan
grep -raoE "AIza[0-9A-Za-z_-]{35}|sk-[A-Za-z0-9]{20,}" /tmp/apkscan | sort -u
```
The only expected hit is the Firebase key from `google-services.json`. Anything else: stop and fix.

## Publish to the `apk-downloads` branch
The branch holds only the latest build. Use a scratch clone, not the working repo.
1. Clone or reset the scratch repo to `origin/apk-downloads`.
2. `git rm` the previous APK. Copy the new one in as `MeetingMind-Dev-<short sha>.apk`.
3. Write a `README.md` with the commit, the build's contents and install steps.
4. One commit, then `git push -f origin HEAD:apk-downloads`. Force-push to this branch only.
5. Verify with `git ls-remote origin apk-downloads` and `git ls-tree --name-only HEAD`.
6. Link to the founder: `https://github.com/creativesites/meetingmind/raw/apk-downloads/<file>.apk`

## Notes
- Say what is new in the build, and what is not verified on a device.
- Circles needs the Worker and rules redeployed to work end to end (`BACKEND.md`).
