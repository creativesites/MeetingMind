# Circles — Phase 0 audit (Circles v2 on the planning branch)

Audited at: `claude/meetingmind-mvp-planning-7dgowm` (HEAD `70ed175`). This document supersedes an earlier version that
was written against the wrong branch and concluded Circles was absent. It is not absent here: Circles v2 exists, and so
does v1.

Decisions this audit builds on (`docs/mvp/FAITH_V2.md` §6 and the product owner's guidance):

- Backend: Firebase (anonymous Auth, Firestore, FCM) plus the Cloudflare Worker `server/circles-api` for trusted
  operations. Decided in §7; no new backend choice needed.
- Circle content lives on the server. End-to-end encryption is dropped (§6 decision 1). Personal notes stay local.
- First increment: creation, membership, invitations, text chat. Resource sharing and the church tier come later.
- v1 is retired with no migration (§6 decision 6): it only ever shipped on dev builds.

## 1. Inventory

| Area | Path (planning branch) | State |
|---|---|---|
| Client data layer (v2) | `app/.../core/circles2/` (`CirclesRepository`, `CirclesApi`, `CirclesAuth`, `CirclesData`, `FirestoreCirclesData`, `CirclesParsing`, `CirclesModels`, `InviteCodes`, `LocalCircleStore`, push files) | Present, unit-tested |
| UI (v2) | `app/.../feature/circles2/` (list, create, join sheet, circle home, feed, chat, members, settings, fun cards, account linking) | Present |
| Worker (v2) | `server/circles-api/src/` (`index.js`, `handlers.js`, `logic.js`, `auth.js`, `firestore.js`, `fcm.js`, `digest.js`, `ratelimit.js`) | Present, deployed |
| Worker tests | `server/circles-api/test/unit/*.test.js` (30 tests) and `test/rules/` (Firestore emulator suite) | Unit tests run here: **30/30 pass** |
| Security rules | `firestore.rules` (circles section from line 47) | Present |
| Worker config | `server/circles-api/wrangler.toml` | **Filled in**: project `meetingmind-mobile`, KV binding `RATE` with id, cron `0 18 * * *`. No placeholders remain. |
| v1 (to retire) | `app/.../core/circles/` (`CircleSync`, `FirestoreCircleTransport`, `InMemoryCircleTransport`, `CircleEvent`, `CircleDeepLinks`), `core/repository/CircleRepository*`, tests under `core/circles/` and `core/repository/CircleRepositoryTest.kt` | Present; uses the E2EE design FAITH_V2 §2.4 drops |

Deployment check (read-only, run from this session):

- `GET https://meetingmind-circles.meetingmind.workers.dev/health` → 200 `ok`.
- `GET …/ready` → 200 `ready`. This confirms the service-account secret is present and parses.
- `POST …/v1/createCircle` without a token → 401 `unauthorized` "Missing token". The auth gate is live.

The app's default Worker URL (`app/build.gradle.kts:43`) matches the deployed host.

## 2. The creation path, traced

1. **UI.** `CreateCircleViewModel.create()` (`feature/circles2/CirclesListViewModel.kt`) validates name, display name and
   post types, then creates one `requestId` (UUID) and stores it in state. Retries reuse it, and it is cleared only when
   the person edits a field. Correct for idempotency.
2. **Repository.** `CirclesRepository.createCircle` (`core/circles2/CirclesRepository.kt:79`) checks `isConfigured`,
   calls `uid()` (anonymous sign-in if needed), then `api.createCircle`. On success it stores the id locally.
3. **Auth.** `FirebaseCirclesAuth.ensureSignedIn` (`CirclesAuth.kt:60–73`) calls `signInAnonymously()`. Any exception is
   replaced by `CirclesFailure.signedOut`. See defect D1.
4. **Client HTTP.** `HttpCirclesApi.call` (`CirclesApi.kt`) gets a fresh ID token (`getIdToken(false)`) and POSTs JSON to
   `/v1/createCircle`. Non-2xx and `ok:false` bodies become `CirclesFailure` with the Worker's message. Network failure
   becomes `offline`; a bad body becomes `server`. Nothing throws to the UI.
5. **Worker entry.** `index.js` checks the route, verifies the Bearer token (`auth.js`: RS256 against Google's
   securetoken keys, `aud`/`iss`/`exp`/`iat`/`sub` checked, `alg` checked before key lookup), enforces a 32 KB body limit,
   and builds services.
6. **Handler.** `handlers.js:60–94`:
   - Validates template, name, vocab, settings and display name (`logic.js` `cleanText`, `normalizeSettings`).
   - Validates `requestId` as a v4 UUID.
   - Reads `userMeta/{uid}/circleCreateRequests/{requestId}`. If a circle already exists for that key, it returns it.
     This is the idempotency guard.
   - Applies the per-user create rate limit (5 per hour, KV).
   - In one Firestore transaction (`firestore.js` `runTx`, which retries on ABORTED): re-checks the idempotency key,
     checks `circleCount < 10`, then writes the circle, the owner member doc, `userMeta` and the idempotency record.
7. **Firestore REST.** `runTx` uses `beginTransaction` → `commit` with `currentDocument` preconditions. A duplicate create
   yields ALREADY_EXISTS, which is handled.
8. **Read-back.** The client observes `circles/{id}` through a snapshot listener (`FirestoreCirclesData.observeCircle`).
   Rules allow `get` for a member (`firestore.rules:49`), and the member doc is written in the same transaction, so the
   creator can read it immediately.

**Consistency checks, all verified by reading and by the 30 Worker tests:**

| Requirement (brief §3C) | Result |
|---|---|
| Tokens accepted and validated | Yes: `auth.js`, algorithm confusion blocked, claims checked. |
| Config bindings present | Yes: `wrangler.toml` filled; `/ready` returns 200. |
| Wire values match validators | Yes: `PostType`, `ReactionKind`, `WhoCanInvite`, and all seven template ids match `logic.js` exactly. |
| Create is transactional | Yes: circle, owner, `userMeta` and idempotency record commit together. |
| Retrying does not duplicate | Yes for retries that reuse `requestId`. The UI does reuse it. |
| Creator can see it immediately | Yes: rules and member doc are consistent after commit. |
| Partial failure leaves no orphans | Yes: one commit. A failed commit writes nothing. |
| Useful network errors | Partly: see D1 and D3. |

**Conclusion on the reported creation failure:** I could not reproduce a creation defect in the planning branch's code
or in the deployed Worker's gate. The two things that would produce "creation failed, repeatedly" are both outside the
code I could run here, and both are checkable by the product owner:

1. **Anonymous sign-in disabled in the Firebase console.** If it is, `signInAnonymously` fails and the UI shows "signed
   out" with no cause (D1). This is the most likely cause, and it cannot be confirmed from this session.
2. **Build pointing elsewhere.** `CIRCLES_API_URL` defaults to the deployed Worker, so this is less likely.

## 3. Defects

Only items I could verify are listed as defects. Severity is for the first increment.

| ID | Severity | Where | Finding | Evidence |
|---|---|---|---|---|
| D1 | High (diagnosability) | `CirclesAuth.kt:66–73` | Anonymous sign-in failures are logged at WARN with only `e.message` and then turned into `CirclesFailure.signedOut`. The FirebaseAuth `errorCode` (for example `admin-restricted-operation` when the provider is off) never reaches the UI or a structured log. | Code read. Not reproducible without the console. |
| D2 | High (policy conflict) | `server/circles-api/src/logic.js:4`, `docs/mvp/FAITH_V2.md` §6 decision 4 | Member cap is 50 in code and in the founder's decision. The product owner's guidance says 10 for the first increment. The code value is the only one that can be changed in a single line. | Documents disagree. |
| D3 | Medium | `handlers.js:77` | The create rate limit (5/hour) is counted before the transaction, so server-side failures count against the user. Five failed attempts block creation for an hour with "You're doing that too fast", which is misleading. | Code read; the message is from `ratelimit.js`. |
| D4 | Low (fragility) | `handlers.js:89`, `handlers.js:179`, `handlers.js:204` | `userMeta/{uid}` is replaced with `w.set`. Nothing else writes that document today, so there is no data loss, but any future field (for example push tokens) would be wiped by the next create or join. | Grep: only the three writers above. |
| D5 | Low | `handlers.js:74` | Idempotency records under `userMeta/{uid}/circleCreateRequests` are never deleted. This is storage growth, not a correctness problem. | Code read. |
| D6 | Low | `CirclesRepository.kt:79` | `createCircle` defaults `requestId` to a fresh UUID, so a caller that forgets to pass one loses idempotency. The only caller passes one. | Code read. |

Not defects: the client wire values, the Worker token verification, the transaction, and the rules' read path.

Known limits of the rate limiter (`ratelimit.js`): KV is eventually consistent and not atomic, and it fails open on
outage. The file says so, and it is an accepted residual risk.

## 4. Security, privacy and scale notes

- **Authorization is server-side.** Clients can only read circles they belong to and write their own comments, reactions,
  chat messages and votes (`firestore.rules` header). Privileged operations go through the Worker, whose service account
  bypasses the rules.
- **Anonymous authorship** is enforced by storing the author in `postAuthors`, which clients can't read (FAITH_V2 §7).
- **Honest copy.** The app must not say "end-to-end encrypted". FAITH_V2 §2.4 asks for "Only members of this circle can
  see what's shared here." Check this string when the UI is rewritten.
- **v1 must not be reachable.** The `mindcircle` deep link and the E2EE code path must go in the retirement task (V-0b6).
- Rules tests (`test/rules/`) need the Firestore emulator. I did not run them.

## 5. What I could not verify

- **Anonymous sign-in on the Firebase console.** I have no console access and `gcloud` has no credentials in this
  session. I did not create a test anonymous account in the production project to probe it, because that is a write.
  If you approve, a single `accounts:signUp` call with the public web key from `google-services.json` would tell us
  whether the provider is on.
- **Android unit tests** (`app/src/test/.../circles2/`). The Android SDK is not installed here, so they did not run.
- **Firestore rules tests** (`server/circles-api/test/rules/`). Need the emulator.

## 6. Proposed sequence

1. **Confirm anonymous sign-in** in the console (Authentication → Sign-in method). Otherwise creation fails regardless of code.
2. **D1 fix.** Carry the FirebaseAuth error code through `CirclesFailure` and into a log line with no user data. Add a unit test
   that a `FirebaseAuthException` with a given code produces a specific, non-"signed out" message. Small, and it makes
   the next failure diagnosable.
3. **Decide D2** (10 or 50), then change `LIMITS.membersPerCircle` and the matching client copy.
4. **D3 fix.** Count a create attempt against the limit only after the transaction succeeds, or move the limit check after
   validation. Add a Worker test that failed attempts don't consume the quota.
5. **Retire v1** (V-0b6). Remove `core/circles/`, `core/repository/CircleRepository*`, the `mindcircle` link and the
   Room tables, with a Room migration that drops them. Migration test required (AGENT_RULES, database rules).
6. **First increment** (creation, membership, invitations, text chat), per FAITH_V2 §5 V-0b2 and V-0b3.
7. **Chat and fun placeholder.** Reserve the `chat` surface and the fun cards for the existing v2 types (polls, chains,
   celebrations, verse of the day) behind the first increment, without wiring the write paths. FAITH_V2 §2.2b describes them.

## 7. Decisions still needed

1. **Member cap: 10 or 50?** Code and the founder's decision say 50; your guidance says 10 for the first increment. Which
   applies to the first increment, and does it change later?
2. **Anonymous sign-in:** confirm it's enabled, or approve the one-call probe above.
3. **Whether to keep the 10-circles-per-user limit** (`circlesPerUser`) on the same basis as the member cap.

The branch is not changed beyond this document. No application code was modified in this audit.
