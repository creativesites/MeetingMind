# Backend

## What runs where
- **Firebase project:** `meetingmind-mobile`. Anonymous Auth, Firestore, Cloud Messaging. Spark (free) plan.
- **Circles Worker:** Cloudflare Worker in `server/circles-api/`. URL: `https://meetingmind-circles.meetingmind.workers.dev`. It verifies the caller's Firebase ID token, then does every trusted operation with a service account. Phones only read circles they belong to, and write their own comments, reactions and profile (`firestore.rules`).
- **Rate limits:** a Workers KV namespace. The id is in `server/circles-api/wrangler.toml`.
- **Daily prayer digest:** a cron in `wrangler.toml` (18:00 UTC), registered by the deploy.

## Endpoints
All: `POST /v1/<name>`, JSON body, `Authorization: Bearer <Firebase ID token>`. Full table in `server/circles-api/README.md`. Main ones: `createCircle` (max 10 circles per person), `updateCircle`, `createInvite`, `revokeInvite`, plus join, roles, posts, anonymity, approval, counters and push.

## Secrets: never in the repo
- The service-account JSON is set with `npx wrangler secret put SERVICE_ACCOUNT_JSON`. Delete the downloaded file afterwards.
- Client-side Firebase config (`google-services.json`) is in the app and is expected there. Only the Firebase key in it may appear in a built APK.
- Built-in API keys for testers come from CI secrets (`agents/PLAY_STORE.md`).

## Deploy (owner, when the Worker or rules change)
```sh
cd server/circles-api && npm install && npx wrangler deploy
cd /home/user/MeetingMind && npx firebase-tools deploy --only firestore:rules --project meetingmind-mobile
```
- `GET /health` checks the Worker responds. `GET /ready` checks the service-account secret is valid. Neither exposes configuration.
- Check Anonymous sign-in is enabled in the Firebase console (Authentication > Sign-in method). If it is off, circle creation fails for everyone.
- Google sign-in linking needs the provider on and the dev SHA-1 and SHA-256 fingerprints added. Then a new `google-services.json` is needed.

## Tests
- `cd server/circles-api && npm test` (Worker logic, no network).
- `npm run test:rules` (Firestore rules; needs Java, downloads the emulator once).

## Known state (2026-10-14)
- The Android app's Circles creation path is being migrated to the Worker. Until then, creation may still hit the old rules and fail.
- Old Circles v1 data existed only on dev builds and is retired without migration (founder decision).
