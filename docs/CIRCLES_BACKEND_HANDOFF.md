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

These need the media pipeline in section 6. The chat contract:

- New message kinds `image`, `voice`, `file`. Each carries `attachment`: a map with `path` (string, must start with `circles/{circleId}/media/`), `mime` (string), `bytes` (integer, at most 25 MB), and where relevant `durationMs` (integer) and `name` (string, at most 120 characters). `text` may be a caption (at most 2000 characters).
- Rules: allow these kinds only when `attachment` passes those checks and `mime` is on the allow-list for the kind (`image/jpeg`, `image/png`, `image/webp`, `audio/mp4`, `audio/aac`, `audio/ogg`, `application/pdf`).
- Message delete stays soft (`deleted`, `text` blanked, `attachment` removed). Storage objects are deleted by the Worker on hard delete; never by the client.

Gate: rules tests for each kind: valid; wrong mime; oversized; path outside the circle.

### 5f. Message reactions and polls

Already on the branch. No change.

## 6. Media storage (decision needed, then build)

Decision needed from the owner (from `docs/CIRCLES_DECISIONS.md`, not yet answered):

- Firebase Storage. One auth and rules model. Check in the console whether this project needs the Blaze plan for Cloud Storage. My understanding is that new projects do, but verify; do not assume.
- Or Cloudflare R2 with signed upload URLs from the Worker. A second system.

Recommendation: Firebase Storage if billing is acceptable.

Once decided:

- Storage rules: read if the caller is a member of the circle in the path; write only from a signed-in member, only under `circles/{circleId}/media/`, only with an allowed content type, with a size cap (25 MB; images 10 MB).
- Worker endpoint `createUploadTicket`: body `circleId, kind, mime, bytes`. Checks membership and limits, returns the object path the client must upload to. Rate limit per user.
- Worker endpoint `deleteMedia`: body `circleId, path`, author or admin, deletes the object. Called when a message or post is hard-deleted.
- Thumbnails and resizing: out of scope for this step. The client compresses before upload.

Gate: Storage rules tests (emulator) for read, write, type, size, path. Worker tests for the ticket and delete endpoints.

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
6. Section 6 once the storage decision is made. Then 5e and 7.
7. Section 3 once the Moderator decision is made.

Each step: tests green, the commit message names the section, and the PR description lists which contracts changed.
