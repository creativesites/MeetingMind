# Circles backend handoff

For the local backend agent. Branch: `circles-redesign-m1` (PR #7). Everything here is server side: the Worker in `server/circles-api/` and `firestore.rules`. The Android client is written against the contracts below. Where the client and this document disagree, this document wins and the client should be changed.

Work in the order given. Each item ends with a test gate. Do not deploy the rules before the Worker that depends on them.

## 0. What is already on the branch (do not redo)

- Worker: `description`, `joinApproval`, `decideJoin`, `setTitle`, `pinPost`, `createEvent`, `updateEvent`, `cancelEvent`, `rsvp`, free-form `note` post type. See `server/circles-api/README.md`.
- Rules: events and RSVPs readable by members, join requests Worker-only.
- Tests: `npm test` in `server/circles-api` (46 pass); `npm run test:rules` in `server/circles-api/test/rules` (20 pass; needs Java, downloads the emulator once).

## 1. Deploy what is on the branch

Owner runs (or the agent, if it has the credentials):

```sh
cd server/circles-api && npm ci && npx wrangler deploy
cd /home/user/MeetingMind && npx firebase-tools deploy --only firestore:rules --project meetingmind-mobile
```

Checks: `GET /health` and `GET /ready` return 200; an unauthenticated `POST /v1/createCircle` returns 401; Anonymous sign-in is enabled in the Firebase console.

Gate: both test suites green on the deployed commit. Report the Worker version id and the rules release.

## 2. Circle creation failure: reproduce first

The app code and tests show no defect (see `docs/CIRCLES_DECISIONS.md` row 17). Do not change code until a failure is reproduced.

- Reproduce on a debug build against production: create a circle. If it fails, record the exact message shown and the `error.code` from the Worker response.
- Tail the Worker: `npx wrangler tail` while creating a circle. Record the status and error code.
- Check the Anonymous sign-in state in the console and the `userMeta/{uid}` document for the test account.

Gate: a written result: works / fails, with the code, the status and the layer where it fails (sign-in, Worker, rules, or the app). Only then propose a fix.

## 3. Member roles: Moderator (needs a decision first)

The design shows a Moderator role that can create events and remove others' posts. Today there are three roles: owner, admin, member (`Role` in `CirclesModels.kt`, `isAdmin` in `logic.js`).

Decision needed from the owner: approve adding `moderator`, or keep admin-only. Until approved, do not build this.

If approved:

- Role wire value `moderator`, between admin and member.
- Worker: a `canModerate(role)` helper for `createEvent`, `updateEvent`, `cancelEvent`, and removing other people's posts (`deletePost` for admins only today). Owner and admin can still do everything a moderator can.
- Rules: an `isModerator(circleId)` helper. Use it wherever the Worker writes role-gated data on the client's behalf (none today; the Worker is the only writer of these paths).
- `setRole` accepts `moderator`. Titles stay separate: a moderator can have the title "Deacon".

Gate: new Worker tests for each permission; the existing 46 still pass.

## 4. Members page: admins read join requests

The Members screen lists waiting requests. Clients cannot read `circles/{id}/joinRequests` today.

Rules change (in `firestore.rules`, under the `circles/{circleId}` block, replacing the current `joinRequests` rule):

```
match /joinRequests/{uid} {
  allow get, list: if isAdmin(circleId);
  allow write: if false;
}
```

Gate: rules tests: admin can list; member and outsider cannot; no client write. Add them to `firestore.rules.test.js`.

## 5. Chat: the backend for the redesigned chat

The client sends messages straight to Firestore under these rules. Each item below adds a field or kind the client will write. Each needs a rule and a test.

### 5a. Pinned messages (Worker)

- New Worker endpoint `pinMessage`: body `circleId, messageId` (or `null` to unpin). Admin only. The message must exist and not be deleted. Stores `pinnedMessageId` on the circle document.
- Client reads it from the circle document. No rules change.

### 5b. Mentions, forwards

- Rules, `messages` create: allow an optional `mentions` list (`list`, at most 20 entries, each a string of at most 128 characters) on `text` and `reply` kinds. Add `mentions` to `hasOnly`.
- Rules, `messages` create: allow an optional `forwardedFrom` string of at most 64 characters (the original message id). Add it to `hasOnly`. The text rule still applies.
- Client uses these to highlight `@name` and to show a forwarded label.

### 5c. Read receipts (member documents)

- Rules, `circles/{circleId}/members/{uid}`: a member may update only their own document, and only the `lastReadAt` field, set to `request.time`.
- Client writes `lastReadAt` when a chat screen is opened and when the newest message is shown. Seen-by counts come from comparing `lastReadAt` with each message's `createdAt`.

Gate: rules tests: own `lastReadAt` allowed; other member's refused; other fields refused; a non-member refused.

### 5d. Typing indicator

- New subcollection `circles/{circleId}/typing/{uid}`. Fields: `displayName` (string, at most 40), `updatedAt` (must equal `request.time`).
- Members write only their own doc; read by members. Delete on stop.
- Set a Firestore TTL policy on `typing` documents using an `expiresAt` field: the client writes `expiresAt = now + 10 s` (add it to the allowed keys). Rules check `expiresAt` is a timestamp no more than 60 seconds after `request.time`.

Gate: rules tests: own write allowed; other uid refused; oversized name refused; expiry too far in the future refused.

### 5e. Images, voice notes and files

These need the media pipeline in section 6. Decided by the owner: attachment messages are written by the Worker through `sendMessage`. Clients never write them directly.

- New Worker endpoint `sendMessage`: body `circleId, kind` (`image`, `voice` or `file`), `uploadPath` (the `path` from a finished upload ticket), `text` (optional caption, at most 2000 characters), `replyTo?`. The Worker checks membership, that the caller is the uploader of `uploadPath` (ticket record or object metadata), that the object exists, that its mime and bytes match the kind's limits, and that the kind's mime allow-list holds. It then writes the message with a server `createdAt`, `authorUid` from the token, and `attachment {path, mime, bytes, durationMs?, name?}`. `durationMs` and `name` are optional; `name` is at most 120 characters.
- Text messages stay as they are: clients write `text` and `reply` directly under the existing rules.
- Rules: refuse client creation of `image`, `voice` and `file` messages entirely. The Worker is the only writer, so the rules do not need to check the attachment shape.
- Message delete stays soft (`deleted`, `text` blanked, `attachment` removed). Storage objects are deleted by the Worker on hard delete; never by the client.

Gate: Worker tests for `sendMessage`: a non-uploader is refused; a valid message is written with server time; a wrong mime or an oversized object is refused; a missing object is refused. Rules tests: a client cannot create an `image`, `voice` or `file` message; text messages still work.

### 5f. Message reactions and polls

Already on the branch. No change.

## 6. Media storage: Cloudflare R2, gated by the Worker (approved direction, 2026-10-15)

Replaces the Firebase Storage option. Firebase Storage needs the Blaze plan, and the project is on the free plan. Do not create a Storage bucket or storage rules.

Decisions:

- Storage: Cloudflare R2, bucket `meetingmind-circles-media`, same Cloudflare account as `meetingmind-circles`.
- Access: the Worker is the only thing that reads or writes R2. Clients never get R2 credentials and never touch the bucket. Membership is checked in the Worker.
- Transfer: the Worker proxies the bytes, for upload and download. No presigned R2 URLs and no S3 keys. Acceptable because R2 has no egress fees and the cap is 25 MB.
- Object path: `circles/{circleId}/media/{uuid}`. The Worker generates the uuid.
- Object metadata: uploader uid, mime, bytes, kind, createdAt.
- Firebase Storage rules and their emulator tests: none. Worker tests replace them.

Limits:

| Kind | Allowed mime | Max bytes |
|---|---|---|
| image | image/jpeg, image/png, image/webp | 10 MB |
| voice | audio/mp4, audio/aac, audio/ogg | 25 MB |
| file | application/pdf | 25 MB |

- The Worker sniffs the file header and checks it matches the declared mime. It does not trust the Content-Type header alone.
- Refuse an upload over its cap from `Content-Length` before reading the body in full.

Endpoints:

- `POST /v1/createUploadTicket`: body `circleId, kind, mime, bytes, durationMs?, name?`. Checks membership, kind, mime, bytes, `name` at most 120 characters, and a rate limit per user (suggest 60 per hour). Returns `{ticketId, path, expiresAt}`. The ticket is stored in KV with uid, circleId, path, mime, bytes, a 15-minute expiry, and single use.
- `PUT /v1/media/upload/{ticketId}`: raw bytes. `Content-Type` must equal the ticket mime. Checks the ticket exists, is unexpired and unused, belongs to the caller, the length equals the ticket bytes, and the header sniff matches the mime. Writes to R2 at the ticket path, marks the ticket used, returns `{ok, path}`.
- `GET /v1/media/{circleId}/{uuid}`: member only. Non-members get 404, so existence is not revealed. Streams the object with `Cache-Control: private, max-age=3600`.
- `POST /v1/deleteMedia`: body `circleId, path`. The uploader or an admin may delete. The Worker calls it on hard delete. Clients never delete objects.
- Orphan cleanup, in the existing daily cron: delete objects older than 24 hours that no message or post references.

Client contracts added with the client UI (the backend must match):

- `sendMessage` body also takes `durationMs?` (voice) and `name?` (file or voice name, at most 120 characters). The response is `{ok:true}`.
- `updateCircle` takes `coverPath?`: the path of an uploaded image (`circles/{circleId}/media/{uuid}`) that the circle's hero shows. Admins only. The circle document stores it as `coverPath`.
- `pinMessage` takes `circleId, messageId` (null to unpin). The circle document stores it as `pinnedMessageId`.
- `createUploadTicket` returns `{ok:true, ticketId, path, expiresAt}`. `uploadMedia` (PUT) returns `{ok:true, path}`. `downloadMedia` (GET) returns the raw bytes, with the Worker's error body on failure.
- `createPost` takes `attachments`: an array of `{path, mime, bytes}`, at most four.
- Clients write `members/{uid}.lastReadAt = serverTimestamp` for read receipts, and `typing/{uid}` with `displayName`, `updatedAt` and `expiresAt` for the typing line. Both are covered by section 5c and 5d.
- Clients write `forwardedFrom` (the original message id) and `mentions` (uids, at most 20) on text messages (section 5b).

Messages and posts: the field names in section 5e and section 7 stay exactly as written: `attachment {path, mime, bytes, durationMs?, name?}`, and the caption in `text` (at most 2000 characters).

Decided by the owner (2026-10-15): chat messages with attachments are written through the `sendMessage` Worker endpoint (section 5e). Firestore rules cannot check that the user uploaded the object, so the Worker does.

Plain text messages keep their current rules.

Client errors: `{ok:false, error:{code, message}}` with a plain message. Codes: `not_member, too_large, bad_mime, ticket_expired, ticket_used, rate_limited, bad_request`.

Setup, before any code:

1. `npx wrangler r2 bucket create meetingmind-circles-media`
2. Add to `server/circles-api/wrangler.toml`:
   ```toml
   [[r2_buckets]]
   binding = "MEDIA"
   bucket_name = "meetingmind-circles-media"
   ```
3. Add a KV namespace for upload tickets, or reuse the rate-limit KV with a `ticket:` key prefix.
4. The Worker's request body limit covers 25 MB.

Tests, the gate for each step:

- Ticket: member and non-member; each kind's valid case; wrong mime; oversized; name too long; rate limit.
- Upload: expired ticket; reused ticket; another user's ticket; length mismatch; header does not match the mime (for example a PDF sent as `image/png`).
- Read: a member gets the bytes; a non-member gets 404; a missing object gets 404.
- Delete: uploader allowed; another member refused; admin allowed.
- Orphan cleanup: an unreferenced object older than 24 hours is removed; a referenced object is kept; a recent object is kept.
- Firestore rules (existing emulator suite): clients cannot create `image`, `voice` or `file` messages; text messages still work.

Out of scope for this step: thumbnails and resizing (the client compresses before upload), video, and virus scanning.

Notes for the agent:

- KV is eventually consistent, so "single use" for tickets needs an atomic check. Use a Durable Object for the ticket, or document the small race window and accept it. Decide before writing the code.
- Sniff the audio mime with its container or header: `audio/mp4` is an `ftyp` box, `audio/ogg` starts with `OggS`, and `audio/aac` must be ADTS framing.

## 7. Feed attachments (photos)

- Worker `createPost` accepts `attachments`: a list of at most 4 items, each `{path, mime, bytes, w, h}`. `path` must be under the circle's media prefix and must match an object the uploader created (store the uploader uid in the ticket and check it on publish).
- Post documents store `attachments` as given (after validation). Rules are unaffected because the Worker writes posts.

Gate: Worker tests: too many; wrong prefix; a path not issued to this user; valid.

## 8. What the client needs back

For each endpoint or rule above, the client expects:

- The exact field names in this document. Do not rename.
- Errors as `{ok:false, error:{code, message}}` with a plain message; the client shows the message.

If any contract has to change, update this document and the Android side in the same commit, and say so in the PR.

## 9. Order and sign-off

1. Section 1 (deploy and verify).
2. Section 2 (creation repro). Report before continuing.
3. Section 4 (join request read rule): small, unblocks the Members page.
4. Section 5a and 5b (pins, mentions, forwards).
5. Section 5c and 5d (read receipts, typing).
6. Section 6 (R2). Then 5e and 7 once the sendMessage decision is confirmed.
7. Section 3 once the Moderator decision is made.

Each step: tests green, the commit message names the section, and the PR description lists which contracts changed.
