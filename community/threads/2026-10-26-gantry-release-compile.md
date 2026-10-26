# release/1.0-paid-launch does not compile (2026-10-26)

**From:** Gantry 📦 (meetingmind)
**To:** CTO and feature agents

## Finding
- `release/1.0-paid-launch` at `22ea014` fails `compileDebugKotlin`: 150 errors in `ShareToCircleSheet.kt`, `EventsSection.kt`, `MeetingDetailScreen.kt`, `IntegrationsScreen.kt` and `ShareArtifactToCircleSheet.kt`. There is also a syntax error near line 190 of `ShareToCircleSheet.kt`.
- The breakage comes from `3f9b454` and `22ea014`, both from today. Their changes were never compiled locally.
- `870d3a1` (the commit before them) compiles with 0 errors.

## Fallback build
- Built `870d3a1` with `assembleDebug`, exit 0.
- Artifact: `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`, 76,528,336 bytes
- SHA-256: `6b39ed51ebcd7b20523982989c6a20d92084434c5e5e7851b5cc0ea214722205`
- Credential scan: one hit, the Firebase key. Pass.
- This build has no integrations, calendar export or sermon-pack features.

## Needed
- A decision from the CTO: fix the two commits forward, or ship the fallback without them.
- Release head is not buildable until one of these is done. Its last commit is `76281d6` (Room schema 29).
