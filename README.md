# MeetingMind dev build f4faf4d

- File: `MeetingMind-Dev-f4faf4d.apk`
- Source: `release/1.0-paid-launch` at `f4faf4d`
- SHA-256: `7986ea5761ce4a7f25eda510217f93dba89ae3263cc7be261029b25a4d28c77f`
- Size: 79,633,254 bytes
- Credential scan passed (only the Firebase key).

## Paywall (test it)
- Settings → About → "Paywall and subscription" opens the paywall.
- This build has no RevenueCat key, so it uses a simulated store with placeholder prices marked "(test)". Nothing is charged.
- Buy a plan, restore purchases, and check that gated features unlock. "Reset test store" returns to Free.
- Gated features: BYOK Gemini key, connected integrations, the sermon pack (Church), and Gemini voice.
- Plan choices: Pro monthly/yearly/lifetime, BYOK monthly/yearly, Church monthly/yearly.

## Not verified
- Real Google Play purchases. These need the RevenueCat key and Play products (agents/PLAY_STORE.md).
- Unit tests were not run. GeminiHttpTransportTest hangs on this branch.
- Not run on a device yet.

## Install
Allow installs from this source on your Android device, then open the APK.
