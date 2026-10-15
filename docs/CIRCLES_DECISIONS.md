# Circles — decisions log (first increment)

Every call made without the product owner, in the order taken. Source documents: `docs/CIRCLES_AUDIT.md`,
`docs/mvp/FAITH_V2.md` (§2, §5, §6, §7). "Founder decision" means FAITH_V2 §6; "owner instruction" means the
guidance received on the planning branch.

| # | Question | Decision | Basis |
|---|---|---|---|
| 1 | Member cap per circle | **50** | Owner instruction ("limit to 50 initially") and FAITH_V2 §6 decision 4. The 10 in the earlier guidance is not used. |
| 2 | Circles per user | **10** | FAITH_V2 §6 decision 4. Unchanged. |
| 3 | First increment scope | Creation, membership, invitations, text chat. Chat, polls, chains and celebrations already exist in v2 and are kept as they are. | Owner instruction; FAITH_V2 §2.2b. |
| 4 | Chat and fun placeholder | Not built anew. The existing v2 chat, polls, prayer chains, celebrations and fun cards are the placeholder the owner asked for. Nothing new is added to them. | Owner instruction ("keep a placeholder"). |
| 5 | Anonymous sign-in on the console | **Confirmed enabled** (Identity Platform config read, no account created). The D1 fix still makes a disabled provider show a specific message instead of "signed out". | Owner said it is set up; verified by the local agent. |
| 6 | Rate limit for creating circles | Counted **only after a create succeeds** (previously counted on every attempt). Still 5 per hour. | Audit D3. A failed attempt should not use up the allowance. |
| 7 | `userMeta` writes | **Merge**, not replace, in create, join and leave. | Audit D4. Protects fields other code may store there. |
| 8 | `requestId` default in the client repository | Made **required** so a caller cannot silently lose idempotency. | Audit D6. |
| 9 | Auth error visibility | Anonymous sign-in failures carry the Firebase error code into the user message and a log line that has no user data. | Audit D1. |
| 10 | v1 retirement | Remove all v1 code, its five Room tables (`CircleEntity`, `CircleMemberEntity`, `CirclePrayerEntity`, `CircleTestimonyEntity`, `CircleSermonEntity`) and `CircleDao`, with a migration 25 → 26 that drops them. | Founder decision 6 (no data migration). |
| 11 | Invite and push links | Keep the `meetingmind://c/…` behaviour. Move the handler out of the v1 package into `core/circles2` unchanged, so v2 no longer depends on v1 code. | Needed before v1 can be deleted. |
| 12 | End-to-end encryption copy | None found in the v2 UI or repository. Nothing to change. | Founder decision 1. |
| 13 | Scope beyond the first increment | Resource sharing, church tier, media pipeline, Phases 5–7 of the brief are **not** built. | Owner instruction ("resource sharing and the church tier come later"). |
| 14 | Deploying the Worker | **Done.** Version `bef1c74c-ead4-48fc-8c5f-3c1b62c3fb94`, deployed by the owner. `/health` and `/ready` return 200, unauthenticated `createCircle` returns 401, cron `0 18 * * *` is registered, and the `SERVICE_ACCOUNT_JSON` secret is present. | Owner approval; local agent ran the checks. |
| 15 | Firestore rules | **Deployed** to `meetingmind-mobile`. The emulator suite passes 18 of 18. | Owner approval. |
| 16 | Google sign-in config | Mac debug SHA-1 registered on the prod Android app, which then got its Android OAuth client. `google-services.json` was re-downloaded (uncommitted in the local checkout). The `.dev` app has its SHA-1 registered but no Android OAuth client yet. | Owner approval. |

## Results

| Check | Result |
|---|---|
| Worker unit tests (`server/circles-api`, `npm test`) | **33 / 33 pass** (3 new: failed creates do not count against the limit; successful creates do; `userMeta` merge keeps other fields) |
| Android unit tests (`:app:testDebugUnitTest`, full suite) | **2,966 tests, 0 failures** (run on this branch after all changes; includes 4 new sign-in failure tests and 4 new v25→v26 migration tests) |
| Database | Version 26, `app/schemas/…/26.json` exported and committed |
| Not run | Firestore rules emulator tests (`server/circles-api/test/rules`). Need the emulator. |
| Smoke test | **Passed** end to end against production, with cleanup. Create, idempotent retry, list, and leave all behave as specified. The test circle, `userMeta` doc and test account were deleted. |
| Open | Google sign-in not yet tested on a device. The `.dev` build may still fail Google sign-in. Cloudflare returns 1010 to Python-urllib, but OkHttp (the app) and browsers get 200. |

Still open: device test of Google sign-in and the reinstall/link flow. Nothing else blocks the first increment. The chat and fun placeholder (decision 4) is the existing v2 surface.

## Implementation milestone 1 (Circles redesign, branch `circles-redesign-m1`)

| # | Question | Decision | Basis |
|---|---|---|---|
| 17 | Circle creation failure | No defect found in the code path. The app's create payload matches the Worker's accepted values (post types, reactions, templates), the Worker create tests pass (33/33 before this milestone), and the default Worker URL is set in the build. Remaining failure modes are configuration: anonymous sign-in, a Worker or rules deploy that is out of date, and the `.dev` build's missing Android OAuth client. Next step: reproduce on a device and capture the error code. | Code and test reading, 2026-10-14. |
| 18 | `joinApproval` setting | Added to `settings`, default **off**, so circles that exist today join as they do. The design shows it on; turning it on by default is a product decision still open. | Keeps behaviour unchanged for existing circles. |
| 19 | Circle `description` | Added to the circle document on create and update, up to 300 characters, default empty. | Needed by the approved Circle home design. |

Worker tests: 37 of 37 pass (4 new: description on create and update, description length cap, description default, join approval default and validation).

## Implementation milestone 1, backend slice

| # | Question | Decision | Basis |
|---|---|---|---|
| 20 | Join approval | With `joinApproval` on, a join creates `joinRequests/{uid}` and returns `pending: true`. The invite is not used up until an admin approves. Admins approve or reject with `decideJoin`. | Approved design (Members page: requests to join). |
| 21 | Free-form post | New post type `note`. Every circle allows it, and it publishes at once with no approval. Anonymous notes are refused. Existing circles get it without changing their settings. | Approved design; decision 2 recommended. |
| 22 | Titles | `setTitle`, admin only, stored on the member as `title` (max 40). Titles never change permissions. | Approved design; the title/role split. |
| 23 | Pinned post | `pinPost` (admin), stored as `pinnedPostId` on the circle. Only live posts can be pinned. | Approved design (pinned feed post). |
| 24 | Events | `createEvent`, `updateEvent`, `cancelEvent` (admin only). Times are UTC milliseconds plus an IANA time zone. Links must be https. Rules: members read events and RSVPs; clients never write them. | Approved design; the events plan. |
| 25 | RSVP | Any member, one answer each (`going`, `maybe`, `no`), stored under the event. Refused once the event is cancelled. | Events plan. |
| 26 | Moderator role | **Not built.** The design shows a Moderator role for "create events" and "remove posts". Adding it touches the Role enum, the Worker, and the rules. It needs its own decision. Until then those actions are admin only. | Open. |

Rules tests: 20 of 20 pass (2 new: events and RSVP reads and writes; join requests are Worker-only). Worker tests: 46 of 46 pass.

Not done in this slice: the Android side (models, parsing, the new API calls, and every redesigned screen). The sandbox has no Android SDK, so the app cannot be compiled or tested here.

## Implementation milestone 1, Android data layer

| # | Question | Decision | Basis |
|---|---|---|---|
| 27 | `join` return type | Now `CirclesResult<JoinOutcome>` (circle id plus `pending`). A pending join is not remembered as a circle, so the app does not open a circle the person is not in yet. | Needed for join approval. |
| 28 | Event and RSVP reads | `CirclesData.observeEvents` and `observeRsvps` read the subcollections the rules allow. Writes all go through the Worker. | Rules from the backend slice. |
| 29 | Compile status | Written without a compiler: the authoring sandbox has no Android SDK. Brace balance is checked; nothing is compiled or run. | Stated plainly. |

Not done: the redesigned screens (Circle home hero, feed composer with free-form, chat, members, events UI, and the join-pending message on the list screen, which needs wiring to `JoinUiState.pending`).

## Client UI pass (after the data layer)

| # | Question | Decision | Basis |
|---|---|---|---|
| 30 | Event times in the form | Quick choices (next Wednesday 7 pm, Sunday 10 am, Saturday 9 am) in the device zone, stored with the event's IANA zone. No full date picker yet. | Keeps the UI compile-safe without a date picker; replace once the UI can be built and tested. |
| 31 | Cancel event | Cancels straight away, with no confirmation dialog. | Gap: add a confirmation before release. |
| 32 | Join pending | The join sheet shows "Request sent" and does not open the circle. | Matches row 27. |
| 33 | Chat redesign | Partly done: day dividers, a "new messages" divider from the last-read time, and grouped runs from the same person within five minutes. Still waiting on the backend (handoff section 5): pins, mentions, forwards, read receipts, typing, media. Its main features (read receipts, typing, mentions, pins, media) need the backend contracts in `docs/CIRCLES_BACKEND_HANDOFF.md` section 5 first. Building those UI parts before then would show features that cannot work. | Honest scope. |
| 34 | Members page | Titles shown beside roles; admins set titles; waiting requests listed to admins only, with Let in and Decline. | Approved design; depends on handoff section 4 (admin read rule). |
| 35 | Settings | Circle description and the "Admins let new people in" toggle are saved through `updateCircle`. | Rows 17-19. |

Not compiled. Nothing in this pass has been built or run. `CirclesScreenshotTest` expects updated lambda arity, which is done here, but the screenshots themselves were not recorded.

## Media storage (owner, 2026-10-15)

| # | Question | Decision | Basis |
|---|---|---|---|
| 36 | Media storage | Cloudflare R2 (`meetingmind-circles-media`), accessed only by the Worker, which proxies bytes. Replaces Firebase Storage. | Firebase Storage needs the Blaze plan; the project is on the free plan. R2 has no egress fees. |
| 37 | Chat attachments | Recommended: written through a `sendMessage` Worker endpoint, so the Worker can confirm the uploader. Open until the owner confirms. | Firestore rules cannot check who uploaded an object. |
| 38 | Ticket single use | Open: KV is eventually consistent, so the agent chooses between a Durable Object and a documented race window. | Found while reviewing the plan. |

