# Product decisions (founder)

## Team
- Preferred model for the project: the Haiku-tier model, for the CTO and every sub-agent. Use another tier only when the founder asks for it for a specific task.

## Scope
- MVP: transcription (offline and online), workflows, the Work vertical, the Faith vertical.
- Learning is cut from the MVP and out of the first Play release.
- Circles: Firebase plus the Worker. Circle content lives on the server. Personal notes stay local. End-to-end encryption dropped for Circles. Cap of 50 members per circle (founder decision, 2026-10-21; matches Cloudflare Worker enforcement). First increment: creation, membership, invitations, text chat. Resource sharing later.
- Tiering & Billing (founder decision, 2026-10-22; replaces the 2026-10-21 closed-testing toggle): the release build always enforces tiers and the paywall, with no in-app switch, because Play promotes the same build from closed testing to production. Testers get Pro through RevenueCat promotional entitlements (plus Play license testers for test purchases); ending that is an account change, not an app change. The debug build gets a tier switcher (Free / BYOK / Pro / Church / unlocked). Before launch: a production RevenueCat key in release, and server-side entitlement checks on the managed AI proxy.
- **Paid launch (founder decision, 2026-10-25).** The Play launch is paid. The managed Gemini proxy (`server/gemini-proxy`) is approved for release builds and is the only AI route a Play build has; it holds the Google key and meters each plan's monthly allowance. A Play bundle carries **no raw Google key**; the CI build fails if one is found. The debug build keeps the raw key and the RevenueCat Test Store key so a fresh development install works with no setup. Release is built only with a production RevenueCat key and a proxy URL — the bundle task fails without either. What the paid plan sells in 1.0: the managed cloud AI allowance and the right to use a personal Gemini key. Ask across meetings, Speaker ID, Read-to-Me and Circles are included on **every** plan in 1.0 and must not be advertised as paid-only.
- ~~Built-in AI (founder decision B1, 2026-10-22): every build, Play included, carries the raw Gemini key, so a fresh install can record with no personal key and no downloaded model…~~ **Superseded on 2026-10-25 by the paid-launch decision above.** The raw key is debug-only; a Play build reaches AI only through the metered proxy, and a bundle containing a Google key fails its own build.
- Work UX decisions D1–D7 approved as written in `docs/mvp/WORK_UX_AUDIT.md` §7 (founder decision, 2026-10-22).
- Built-in AI (founder decision B1, 2026-10-22): every build, Play included, carries the raw Gemini key, so a fresh install can record with no personal key and no downloaded model. The founder accepts that the key can be extracted from the app. Mitigations: an Android app restriction on the key (package + signing certificate), a Google Cloud budget alert and quota cap, and a dedicated project so the key can be rotated without touching anything else. Tier allowances are enforced in the app.
- Circles (founder decisions, 2026-10-22; plan in `docs/planning/CIRCLES_PLAN_2026-10-22_THEMIS.md`):
  - A meeting that is confidential or must stay on the device can never be shared to a Circle (MVP: no override). Only Work circles are offered for meeting shares.
  - Sharing a meeting brief to a Work circle with external (client or guest) members needs an extra confirmation.
  - Overview is the default tab of a Work circle.
  - Devotionals can be shared to Faith circles as an excerpt of about 600 characters plus a link to the reading.
  - Chat refuses `.mov` and `3gpp` videos with a clear message before upload. Converting them comes later.

## Devotionals
- Always a reading for today, with no network and no key (Morning and Evening, bundled).
- On by default for new installs; the write runs at 00:01 local time; the notification fires at the chosen time.
- "Write one with AI" makes a separate devotional labelled "Written with AI". The plan reading stays in the archive.
- Generic by default. Personal context (profile, sermons, prayer list) only when the person turns on Personal touch.

## Moments (Zuri)
- First Keep on every note-ready arrival. The chime plays only on the two once-ever moments (the hello sample note and the first real recording).
- Unveil on every open of a note: Notes list, meeting detail, wrap-up, note editor. Every note gets its moment, every time it is opened (founder decision, 2026-10-14, overrides the earlier guards below).
- Every note gets its moment, including notes with no speech, typed notes, failed notes and sacred notes (founder decision, 2026-10-14). The earlier rule that failed and sacred notes stay plain is withdrawn.
- Still open: reduced motion and battery saver. The founder has not yet decided whether these keep their plain or nod behaviour (accessibility); until decided, the code keeps them.
- Sermons get the quiet version; a recording in progress defers the moment.
- Zuri may share the note screen during a designed moment, but never covers, edits or replaces the user's own words.

## Experience rules
- Experience first, notes after. A prominent button starts the experience its label names. Buttons that only create a note say "Write…" and are quiet.
- Simple screens with power one tap away. Never remove a capability to simplify. No Simple/Advanced switch.
- Home: Everyday is the default for new installs; Today is kept for existing users; users can pick.
- Settings: eight groups, search at the top, advanced options folded.

## Companions and names
- Zuri is the companion. Nas, Wren and Page are further forms.
- Companion voice and tone: warm, not chatty; never assumes it is morning (time-aware).

## Bible study
- One Bible place: Continue, Today's reading, How I feel, Study a passage, plans, study journal.
- Guided sessions: SOAP and verse by verse first. Zuri asks, never writes the answer.
- The study journal saves what the person types as they type; no blank note first.
