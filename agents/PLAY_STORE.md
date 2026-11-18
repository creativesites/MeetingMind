# Play Store: build and upload

## How the bundle is built
`.github/workflows/release-aab.yml` builds a signed Android App Bundle on GitHub, never on a laptop. It attaches the bundle to a **draft** GitHub release.

Trigger either way:
- Push a tag `aab-<name>` (for example `aab-v44`) on the commit to release. The release is named after the tag.
- Or push a commit whose message contains `[build-aab]` to the branch the workflow watches (`ccr-5e66b00c-btultn`, see the workflow's `on:` section). Our designated branch is not the trigger.

Tags cannot always be pushed from every environment. If the tag push fails, use the branch trigger, or run the workflow by hand from the Actions tab.

## Building on the founder's Mac instead (2026-11-16)
The founder may build the bundle locally with the upload key kept outside the repository:
- `local.properties` (gitignored) holds `revenuecat.apiKey` (the `goog_` key), `gemini.proxyUrl`,
  `legal.privacyPolicyUrl` and `legal.termsOfUseUrl`. `circles.apiUrl` has a default.
- Signing comes from the environment: `KEYSTORE_PATH` (the `.jks` outside the repo), `KEYSTORE_PASSWORD`,
  `KEY_PASSWORD`. Never copy the keystore or its passwords into the repository.
- Live values (2026-11-16): proxy `https://meetingmind-gemini.meetingmind.workers.dev`; legal pages
  `https://meetingmind-legal.meetingmind.workers.dev/privacy` and `/terms` (source: `server/legal`).

## Store products (created 2026-11-16)
Subscriptions (each with one auto-renewing base plan, ids `monthly` / `yearly`):
`pro_monthly` $12.99, `pro_yearly` $99.99, `byok_monthly` $4.99, `byok_yearly` $44.99,
`church_monthly` $49.99, `church_yearly` $499.99 (South Korea capped at ₩599,000).
One-time: `pro_lifetime` $199.99 (purchase option `buy`).
Launch offers (new customers only, live now): `pro_monthly:monthly:launch-1m-free-2m-499` (1 month free,
then 2 months at 62% off), `pro_yearly:yearly:launch-first-year-30off` (first year $69.99),
`byok_monthly:monthly:launch-3m-free` (3 months free). Play offers have no end date: switch them off by hand.

## Secrets (repository settings, never in code)
- `KEYSTORE_BASE64` (the upload keystore, base64 with `base64 -w0 upload.jks`)
- `KEYSTORE_PASSWORD`, `KEY_PASSWORD`
- `SYSTEM_GEMINI_API_KEY` (required: the built-in key testers use for Internet mode)
- Optional: `SYSTEM_DEEPSEEK_API_KEY`, `YOUVERSION_APP_KEY`, `DEEPSEEK_PROXY_URL`

## Closed testing: tiers off (founder decision 2026-11-17)
- Release builds made with `meetingmind.closedTestingAllAccess=true` (in `gradle.properties`) give every tester everything, as if on Pro lifetime. No paywall or plan purchase appears, and Settings says "Closed testing: everything is included". The code is in `core/billing/ClosedTesting.kt`.
- The Gemini proxy's `PLAN_ALLOWANCES` gives `free` Pro's allowance for now: 72000 audio seconds and 1200 actions a month.
- **Before the production launch, undo both:**
  - set the property to `false`;
  - put `free` back to `{"audioSeconds":1800,"actions":30}` with `wrangler secret put PLAN_ALLOWANCES`;
  - check that the paywall shows on a fresh install.

- Bump `versionCode` in `app/build.gradle.kts` (currently 49; `versionName` follows it). Play rejects a repeated code.
- Run the full unit suite on the commit being released.
- Check the release build has no debug-only features on: the dev-only flags are in `app/build.gradle.kts` (`COMPANION_LIVE_WHISPER`, `FEATURE_*`).

## Upload
1. Download the bundle from the draft release.
2. Upload to Play Console (internal or closed testing track).
3. **Delete the draft release afterwards.** The bundle contains the built-in API keys. Do not leave it published or downloadable.

## Store listing and data safety
- Data safety declarations: `docs/DATA_SAFETY.md`.
- Learning is in the MVP (founder decision, 2026-11-16). The store listing may describe it.

## Paywall go-live (what the code expects)
The code is complete. These steps need the store accounts, so they are done outside the repo.

**CI secrets** (the release bundle refuses to build without them, see `app/build.gradle.kts`):
- `REVENUECAT_API_KEY`: the RevenueCat public Android SDK key for the release app.
- `GEMINI_PROXY_URL`: the managed AI proxy (`server/gemini-proxy`).
- `PRIVACY_POLICY_URL` and `TERMS_OF_USE_URL`: public pages. The paywall shows these links only when set, and Play needs them for subscriptions.

**Google Play Console**: one product per offer, with these ids (they must match `OfferKind` in `core/billing/Plan.kt`):
- `monthly`, `yearly`, `lifetime`: the Pro plan.
- `byok_monthly`, `byok_yearly`: the BYOK plan.
- `church_monthly`, `church_yearly`: the Church plan.
- `lifetime` is a one-time (non-consumable) product. The rest are subscriptions.

**RevenueCat**: one app for `com.craftflowtechnologies.meetingmind`, with these entitlements:
- `meetingmind_byok`, `meetingmind_pro`, `meetingmind_church`. Each product grants the entitlement of its plan.
- One offering that is the current offering, with packages whose identifiers are exactly the seven product ids above.

**Prices and trials** are set in Play Console, not in the app. The prices in `SimulatedBillingGateway` are test placeholders and are never used by a release build.

**Testing before launch**: a debug build with no RevenueCat key uses the simulated store, so the paywall can be run on a device. A debug build with a RevenueCat Test Store key (`revenuecat.testApiKey` in `local.properties`) uses real RevenueCat flows.

## Before the next store build: Play's code-optimisation warning (raised 2026-11-18, fix by Feb 2027)
**What Play said** (Play Console, "Needs attention", on release 46, `1.0-v46`; labelled Memory usage, Bad behaviour):
"DEX code optimisation is below our threshold: Obfuscation 0%." Any category under 25% may affect the
app's visibility and its ability to publish. Deadline: **February 2027**. Guide:
https://developer.android.com/topic/performance/vitals/code-optimization

**What it means.** R8 shrinks, optimises and *obfuscates* (renames classes and methods to short names) the
release code. Play measured how much of our code was renamed and found none. Shrinking and optimising are
not flagged. Smaller, renamed code makes the download smaller, and the app starts faster and uses less memory.
That is why Play files the warning under memory.

**What we know (2026-11-18).** `app/build.gradle.kts` release already has `isMinifyEnabled = true` and
`isShrinkResources = true` with `proguard-android-optimize.txt`. `app/proguard-rules.pro` has **no**
`-dontobfuscate`. v47 to v49 use the same rules, so they will score the same. Exactly 0% means renaming is
switched off for the whole app, not just for the kept libraries. The likely cause is a library whose own
packaged rules contain `-dontobfuscate`, which applies to the whole app. This is not confirmed yet: the
release build output and the Gradle cache were cleared, so the merged rules could not be read.

**What to do (by the build runner, on a release build):**
1. Find the cause. After `bundleRelease`, open `app/build/outputs/mapping/release/configuration.txt`
   (R8's merged rules; or add `-printconfiguration build/r8-merged.txt` to `proguard-rules.pro` for one
   build). Search it for `-dontobfuscate`. The comment above it names the library it came from. Also check
   `mapping.txt`: if `com.craftflowtechnologies…` classes map to themselves, nothing was renamed.
2. Remove it. Update that library if a newer version drops the rule. Otherwise, AGP 9.1 can be told to ignore one
   library's packaged rules. Then copy into `proguard-rules.pro` only the keep rules that library really needs.
3. Narrow our broad keeps. These keep every name in a whole library and lower the score:
   `com.k2fsa.sherpa.onnx.**`, `app.rive.**`, `com.google.mediapipe.**`, `com.revenuecat.purchases.**`,
   `com.tom_roush.**`, `com.google.mlkit.vision.text.**`. Keep the JNI and reflection entry points.
   Use `-keep,allowobfuscation` or the library's own packaged rules where only reachability matters.
   RevenueCat, ML Kit and MediaPipe ship their own rules.
4. Test the release build on a phone before upload, because renaming breaks code that looks things up by
   name. Check:
   - Record and transcribe (sherpa-onnx, JNI).
   - On-device AI (MediaPipe).
   - Rive animations.
   - Buy and restore (RevenueCat).
   - Circles (Firestore models).
   - PDF reading and scans (PdfBox, ML Kit).
   - Opening an existing database after an update (Room).
   - Anything parsed from JSON by field name.
5. Crash reports: the bundle carries R8's `mapping.txt`, so Play Console deobfuscates stack traces itself.
   Our own CrashLog stack traces will show short names. Keep each build's `mapping.txt` (CI artifact), so
   they can be read with `retrace`.
6. Check the result in Play Console › Android vitals after the next upload. The target is 25% or more in
   every category.
