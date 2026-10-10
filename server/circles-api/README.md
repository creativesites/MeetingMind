# Circles API

Cloudflare Worker behind Fellowship Circles v2 (docs/mvp/FAITH_V2.md sections 2 and 7). It verifies the caller's
Firebase ID token, then does every trusted operation (join, roles, posts, anonymity, approval, counters, push) with a
service account. Phones only read circles they belong to and write their own comments, reactions and profile
(`/firestore.rules`). Cost: $0 on Firebase Spark plus Workers free.

## Setup (about 20 minutes, once)

1. **Firebase console > Project settings**: note the **Project ID**. Put it in `wrangler.toml` as `FIREBASE_PROJECT_ID`.
2. **Firestore**: Build > Firestore Database > create (production mode). Then deploy the rules:
   `npx firebase-tools deploy --only firestore:rules --project <id>` from the repo root (rules file is `firestore.rules`).
3. **Service account key**: Project settings > Service accounts > *Generate new private key*. This downloads a JSON file.
   Keep it out of git. It needs the default "Firebase Admin SDK" role (covers Firestore and Cloud Messaging).
   Also enable the **Firebase Cloud Messaging API (V1)** (Project settings > Cloud Messaging) if it is off.
4. Install and log in:
   ```sh
   cd server/circles-api
   npm install
   npx wrangler login
   ```
5. **KV namespace** (rate limits): `npx wrangler kv namespace create RATE`, then paste the printed id into `wrangler.toml`.
6. **Secret**: `npx wrangler secret put SERVICE_ACCOUNT_JSON`, then paste the *entire contents* of the JSON file. Delete the file afterwards.
7. `npx wrangler deploy`. It prints `https://meetingmind-circles.<you>.workers.dev`; that is the URL the app will use.
8. Daily prayer digest: the cron in `wrangler.toml` (18:00 UTC) is registered by the deploy. Nothing else to do.

Tests: `npm test` (Worker logic, no network) and `npm run test:rules` (Firestore rules; needs Java and downloads the emulator once).

## API

All endpoints: `POST /v1/<name>`, JSON body, header `Authorization: Bearer <Firebase ID token>` (anonymous tokens fine).
Success: `{"ok":true,...}`. Failure: `{"ok":false,"error":{"code","message"}}` with a 4xx status (5xx only for real faults, with a generic message).

| Endpoint | Who | Body / result |
|---|---|---|
| `createCircle` | anyone (max 10 circles) | `name, template, vocab?, settings?, displayName?` -> `circleId` |
| `updateCircle` | owner/admin | `circleId, name?, vocab?, settings?` |
| `createInvite` | owner/admin (or any member if `whoCanInvite=members`) | `circleId, expiresInDays? (1-30, 7), maxUses? (1-100, 50)` -> `code` like `GRACE-7K2Q` |
| `revokeInvite` | admin or creator | `code` |
| `join` | anyone | `code` (paste the whole share message), `displayName?` -> `circleId, name`. Errors: `not_found, expired, revoked, full, already_member, too_many_circles, rate_limited` |
| `leave`, `removeMember`, `setRole`, `setMute` | see below | `circleId, targetUid?, role?, muted?` |
| `createPost` | member | `circleId, type, body, verseRef?, anonymous` -> `postId, status: pending/published` |
| `approvePost`, `rejectPost` | admin | `circleId, postId` |
| `editPost`, `addUpdate`, `markAnswered` | author (resolved via `postAuthors`) | `circleId, postId, body?` |
| `deletePost` | author or admin (soft delete, body blanked) | `circleId, postId` |
| `prayed` | member | one per user; `alreadyPrayed` true on repeat |
| `react` | member | `kind` in `praying, amen, heart, celebrate`, or `null` to remove |
| `syncCounts` | member | recomputes comment/reaction counters after client-side comment writes (idempotent, 20 s debounce) |
| `report` | member | `circleId, postId, reason?` |
| `myCircles` | any signed-in | `circleIds` the caller is still a member of |
| `createPoll` | member | `circleId, question, options[2-6], multi?` -> `pollId, messageId` (10/h). Votes are written by clients under rules (doc id = uid, open polls only) |
| `closePoll` | poll creator or admin | `circleId, pollId` |
| `startChain` | member | `circleId, title, postId?` -> 24-hour prayer chain (3/day). Members claim hour slots `0..23` under rules |
| `celebrate` | member (self only) | `circleId, kind` in `birthday, answered, streak, milestone`, `text?, companion?, postId` (answered: named, answered requests you wrote only; anonymous ones are refused so nobody is unmasked) (5/day) |
| `registerToken`, `unregisterToken` | any | FCM `token` (max 5 devices per user) |

Post types: prayer, testimony, achievement, study, encouragement, reading, announcement (admins only). Only prayer requests can be
anonymous. Roles: `setRole` is **owner-only** (stricter than "owner/admin", to stop an admin promoting accomplices);
admins can remove plain members; only the owner removes admins; the owner must hand over ownership before leaving.

Pushes (text never contains post bodies; anonymous posts say "Someone shared a prayer request"): new published post (not to the
author or muted members), request waiting for approval (to admins), approved/rejected (to the author), answered (to those who prayed),
daily digest ("N people prayed for you today": counts only).

## How anonymity works

* The post doc and the pending doc never contain the author. Every post's real author lives in `circles/{id}/postAuthors/{postId}`, which no client can read.
* Pending requests carry no author, even non-anonymous ones; the Worker re-attaches a named author on approval.
* Anonymous posts get an hour-rounded `createdAt` and random ids, so they can't be matched to "who was online then".
* Admins' own anonymous requests also wait for approval (publishing instantly would reveal the author is an admin).
* `prayed` gives the author exactly the same response as anyone else; "who prayed" is never client-readable (members see only their own marker).
* Reports store the reporter only as a keyed hash.

## Residual risks (read these)

1. **Operator can unmask.** Whoever holds the Firebase console / service account can read `postAuthors`. Anonymity is "to members and admins", not to the founder.
2. **Anonymous author commenting on their own request** writes `authorUid` on the comment (comments are client-written). The app should either hide the comment box for the author or route those comments through a Worker endpoint (not built yet).
3. **Behavioural unmasking**: a small circle plus a tight request ("my brother Tom's surgery") can identify someone by content; approval helps but can't fix this.
4. **KV rate limits are best effort**: eventually consistent and non-atomic, so parallel requests can slip past; they fail open if KV is down. The Workers **free KV plan allows only 1,000 writes/day** across all users; heavy use will start returning "rate_limited"/slow counters. Upgrade to Workers Paid ($5/month) when real usage arrives. Hard caps (50 members, 10 circles, invite uses) are enforced transactionally in Firestore, not KV.
5. **Anonymous accounts are free to mint**, so per-uid limits are weak; join-failure throttling also runs per IP. Invite codes (about 34M combinations, 7 day default expiry, 50 uses) are guessable only at throttled rates, but a link posted publicly stays valid until revoked or expired.
6. **Free-plan fan-out**: Workers free allows 50 outbound calls per request. `NOTIFY_MAX` (default 30) caps pushes per event; large circles may get only some pushes. Raise it on the paid plan. The digest handles up to 40 tallied posts per day-run and carries leftovers forward.
7. **Chat messages, message reactions, votes and slot claims bypass the Worker** (text/card/reply messages): they are member-only, size- and time-checked by the rules, but have no per-user rate limit. A flood by one member is visible and removable by admins (soft delete); a Worker sweep is a future hardening.
8. **Client comments/reactions bypass the Worker**: they are member-only and size-limited by the rules, but have no rate limit and counters can lag until `syncCounts`.
8. Not built: join-approval setting, block-a-person, `cleanupDeleted` job (soft-deleted posts keep no text), admin UI for reports (admins can read `circles/{id}/reports`), push quiet hours.
9. Token verification trusts Google's JWKS endpoint (cached per Cache-Control). A revoked or deleted user's token stays valid until it expires (up to 1 hour); we do not call Firebase to check revocation.
10. Firestore transactions via REST retry on contention (4 attempts); under extreme contention a join may return a 500 and should simply be retried.
