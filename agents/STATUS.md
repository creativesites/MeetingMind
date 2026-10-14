# Status

Updated: 2026-10-14. Designated branch head at last update: see `git log -1`.

## Latest dev build
- `MeetingMind-Dev-4ddcdd2.apk` on the `apk-downloads` branch. Built from the designated branch after the no-speech change.
- Devices have not yet checked: the moments, the 00:01 devotional notification, the Circles flow.

## Merged and working (unit tests green)
- Devotionals: bundled readings load on device; on by default; 00:01 write; "Write one with AI" labelled.
- Moments: First Keep on note-ready; Unveil on every note open (Notes list, meeting detail, wrap-up); no-speech notes get their moment.
- Today home: the design update.
- Read to Me: listen from the verse sheet and Study workspace; Smart Read; settings sheet.
- Companion states Curious, Proud, Reading; sample-first onboarding.
- Circles first increment: v1 retired (migration 25→26), creation defects fixed, diagnosable sign-in. Worker deployed (`bef1c74c`); Firestore rules deployed; production smoke test passed with cleanup. Google sign-in config: Mac debug SHA-1 on the prod Android app.

## Other branches (not merged, not built)
- `ccr-e1f66026-rff3e0`: onboarding and billing work (draft PR #1). Head: "Sample-first onboarding with resumable state; stop prompting for permissions at launch". Its code has never been compiled. Do not merge main into it or rebase it. Build it only when the CTO asks. Analytics, billing and the paywall are not on it yet.

## Open, needs a decision
- Circles: `firebase.json` (rules path) is committed on the owner's local branch `circles/firebase-json` (58167c4), not yet on any remote branch. It must reach the team branch before the next rules deploy.
- Circles: the `.dev` app has its SHA-1 registered but no Android OAuth client in `google-services.json`. Re-download after the next prod or dev change.
- Circles: the local `google-services.json` rewrite is uncommitted on the agent machine. Commit it.
- Circles migration of the client to the Worker (V-0b3): not confirmed. Removal of v1 code and tables (V-0b6): done in the merged first-increment PR.
- Play workflow trigger watches branch `ccr-5e66b00c-btultn`, not the designated branch. Team to decide whether to move it. Documented as it is in `PLAY_STORE.md`.
- Decided: no rewrite of old commit trailers from earlier sessions. Leave history as it is; new commits follow the trailer rule.

## Open, needs a device
- Circles: Google sign-in on a real phone, the reinstall and account-link flow, and the `.dev` build (no Android OAuth client yet).
- Circles: Worker answers Python-urllib with Cloudflare 1010. OkHttp and browsers get 200, so the app is unaffected. Check any script you write for it.
- Moments on a real phone: First Keep, Unveil, the no-speech case.
- Devotional notification at the set time with the app closed.
- Read to Me on a real phone (see `docs/mvp/READ_TO_ME_DEVICE_CHECKLIST.md`).

## Not started
- Notes: the Faith home and the "Write" tiles follow-ups.
- Circles: chat redesign, media storage, church workspaces.
- Daily Light and Streams in the Desert devotionals (no clean public-domain text yet).
