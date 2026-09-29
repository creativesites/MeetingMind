# App Lock

Optional. Off by default. When on, MeetingMind asks Android to verify the owner (fingerprint, face,
or the screen lock) before showing anything private.

## Implementation plan (from the audit)

- Single-activity Compose app; `MainActivity` hosts one `NavHost`. Settings persist in DataStore
  (`UserPreferencesManager`) — no SharedPreferences. There was no AndroidX Biometric, so it is added
  (`androidx.biometric:1.1.0`). `minSdk 24`, `targetSdk 36`.
- An older, separate **Faith lock** exists (`feature/faith/FaithLock.kt`, `KeyguardManager`, per-space,
  5-minute window). It is left alone: it protects private Faith notes *inside* an unlocked app, App
  Lock protects the whole app. With both on you may be asked twice.
- Because `BiometricPrompt` needs a `FragmentActivity`, `MainActivity` now extends it (it is still a
  `ComponentActivity`; nothing else changed).

## How it works

| Piece | File | Job |
|---|---|---|
| State machine | `core/applock/AppLockController.kt` | `Disabled / Locked / Unlocking / Unlocked`, grace period. Pure Kotlin, injected clock. |
| Authentication seam | `core/applock/AppLockAuthenticator.kt` | Interface + result types. Fake in tests. |
| Real authenticator | `core/applock/BiometricPromptAuthenticator.kt` | The **only** file that touches `BiometricPrompt`. |
| Turning it on/off | `core/applock/AppLockSettings.kt` | Authenticate, *then* persist. |
| Holder | `core/applock/AppLockViewModel.kt` | Activity-scoped; follows the DataStore preference. |
| Gate + lock screen | `feature/applock/AppLockGate.kt` | The one place the lock takes effect. |
| Switch | `SettingsScreen.kt` → Privacy → **App Lock** | |

**State storage.** The preference `app_lock` (boolean) lives in the existing DataStore. The
locked/unlocked *session* is in memory only (the ViewModel), so a new process always starts locked.
No biometric data, secrets or credentials are stored; the app only learns "success".

**Authentication.** `BiometricPrompt` with `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` (API 28–29 use
`BIOMETRIC_WEAK | DEVICE_CREDENTIAL` because the platform rejects the strong combo there). No
`CryptoObject`: the result is used only to gate the UI.

**Enable / disable.** Both directions authenticate first. Enabling is refused, with an explanation and
a shortcut to Android settings, if the phone can't authenticate. The preference is written only after
success; a cancelled prompt changes nothing. Escape hatch: if the phone has *no* screen lock or
biometric any more (`NoHardware` / `NoneEnrolled`), the lock screen and Settings let the owner turn
App Lock off without a prompt, since there is nothing left to verify against.

**Lifecycle.** `MainActivity.onStop` → `controller.onAppBackgrounded(isChangingConfigurations)`,
`onStart` → `onAppForegrounded()`. Those are the only two hooks; there is no second observer.
Rotation is ignored (`isChangingConfigurations`, and the ViewModel survives). Navigation between
screens, other in-app windows and system dialogs never touch the controller.

**Grace period.** Fixed `AppLockController.DEFAULT_GRACE_MS = 30 s`, measured with
`SystemClock.elapsedRealtime` (not the wall clock, so changing the date can't extend it). Away for
30 s or more → locked on return. It is deliberately short; it exists so a permission dialog, the
share sheet or the photo picker doesn't re-prompt. To add a user setting later (Immediately / 1 min /
5 min), add a preference and pass its value as `graceMs`.

**What "locked" means.** `AppLockGate` does *not* draw an overlay: while locked, `MeetMindApp` is not
composed at all. Dialogs and sheets are separate windows and would show above an overlay. The
`NavController` and ViewModels sit above the gate, so the person comes back to where they were;
purely local, un-saved UI state (scroll position, an open dialog) is lost when the lock engages.

**Deep links / notifications / widgets.** They all go through `DeepLinks.pending`, which is consumed
inside `MeetMindApp`. While locked that composable doesn't exist, so the link waits and is followed
right after unlocking. Nothing needs per-link checks.

**Recording.** The lock is UI-only. `MeetingRecordingService` and background transcription
(`WorkManager`) are untouched and keep running while locked. One related fix: the "unfinished
recording" check now runs once per process instead of on every re-composition, otherwise unlocking
during a *live* recording would have shown a false crash-recovery dialog.

**Screen privacy.** While App Lock is on (and until the preference is known), the recents thumbnail is
hidden: Android 13+ via `setRecentsScreenshotEnabled(false)` (screenshots still allowed); Android
7–12 via `FLAG_SECURE`, which also blocks screenshots and screen recording *only while App Lock is on*.
`FLAG_SECURE` is not applied when App Lock is off. In-app sharing (share cards, exports) renders
its own bitmaps and is unaffected.

## Known limits

- Notifications (transcription progress, devotional, reminders) still show their titles on the phone's
  lock screen according to the phone's own notification privacy settings; they are not changed here.
- Audio already playing (mini-player / media notification) is not paused when the app locks.
- The `app_lock` preference is plain DataStore: it stops a casual reader of an unlocked or borrowed
  phone, not someone with root or the app's data directory.
- Up to 30 s of grace after leaving the app.

## Changing it

- Rules for *when* it locks: `AppLockController` only, and its tests (`AppLockControllerTest`).
- How the owner is verified: `BiometricPromptAuthenticator` only.
- Never add `if (appLockEnabled)` checks to screens; gate at `AppLockGate`.
- Don't cover content with an overlay instead of not composing it (window leaks, see above).
