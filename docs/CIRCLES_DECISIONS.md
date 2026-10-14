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
