// D5 Worker package: external member flag, decision status, invite preview, event bring list (The Table),
// and the poll-deadline and MP4 hardening. Mirrors work_circles.test.js.
import test from "node:test";
import assert from "node:assert/strict";
import { HANDLERS } from "../../src/handlers.js";
import { handle } from "../../src/index.js";
import { ApiError } from "../../src/errors.js";
import { sniffMime } from "../../src/media.js";
import { cleanBringList, isWorkCircle, cleanPostMeta, BRING, WORK_TEMPLATES } from "../../src/logic.js";
import { FakeDb, FakeKv, FakeFcm, makeCtx } from "./fakes.js";

function world() {
  const db = new FakeDb(), kv = new FakeKv(), fcm = new FakeFcm();
  const call = async (uid, name, body) => { const { ctx, flush } = makeCtx(db, kv, fcm, uid, body); const r = await HANDLERS[name](ctx); await flush(); return r; };
  const err = async (uid, name, body) => call(uid, name, body).then(() => assert.fail("expected error"), (e) => e);
  return { db, kv, fcm, call, err };
}

/** A work circle: lead is owner, "ann" is a member, "bo" is an admin. */
async function workCircle(W) {
  const { circleId } = await W.call("lead", "createCircle", {
    name: "Client Delivery", template: "client_engagement", space: "work", displayName: "Lead", requestId: crypto.randomUUID(),
  });
  await joinAs(W, "ann", circleId, "Ann");
  await joinAs(W, "bo", circleId, "Bo");
  await W.call("lead", "setRole", { circleId, targetUid: "bo", role: "admin" });
  return circleId;
}
async function joinAs(W, uid, circleId, displayName = uid) {
  const { code } = await W.call("lead", "createInvite", { circleId });
  return W.call(uid, "join", { code, displayName });
}
async function faithCircle(W) {
  const { circleId } = await W.call("owner", "createCircle", { name: "Cell", template: "small_group", displayName: "Olive", requestId: crypto.randomUUID() });
  return circleId;
}
const decisionPost = (W, circleId, uid, extra = {}) => W.call(uid, "createPost", {
  circleId, type: "decision", body: "Ship on Friday", anonymous: false, requestId: crypto.randomUUID(), ...extra,
});

// ---------- external member flag ----------

test("setExternal: owner and admins mark a work member external; the member doc carries it", async () => {
  const W = world();
  const circleId = await workCircle(W);
  assert.equal(W.db.dump(`circles/${circleId}/members/ann`).external, undefined);
  await W.call("lead", "setExternal", { circleId, uid: "ann", external: true });
  assert.equal(W.db.dump(`circles/${circleId}/members/ann`).external, true);
  await W.call("bo", "setExternal", { circleId, uid: "ann", external: false });
  assert.equal(W.db.dump(`circles/${circleId}/members/ann`).external, false);
});

test("setExternal: members cannot set it, even on themselves; non-members are refused", async () => {
  const W = world();
  const circleId = await workCircle(W);
  assert.equal((await W.err("ann", "setExternal", { circleId, uid: "ann", external: true })).code, "forbidden");
  assert.equal((await W.err("ann", "setExternal", { circleId, uid: "bo", external: true })).code, "forbidden");
  assert.equal((await W.err("stranger", "setExternal", { circleId, uid: "ann", external: true })).code, "not_a_member");
  assert.equal(W.db.dump(`circles/${circleId}/members/ann`).external, undefined);
});

test("setExternal: work circles only, boolean only, and the target must be a member", async () => {
  const W = world();
  const circleId = await workCircle(W);
  assert.equal((await W.err("lead", "setExternal", { circleId, uid: "ann", external: "yes" })).code, "bad_request");
  assert.equal((await W.err("lead", "setExternal", { circleId, uid: "nobody", external: true })).code, "not_found");
  const faith = await faithCircle(W);
  const e = await W.err("owner", "setExternal", { circleId: faith, uid: "owner", external: true });
  assert.equal(e.code, "not_work_circle");
  assert.equal(e.status, 409);
});

test("isWorkCircle: an explicit space decides; with no space a work template does", () => {
  assert.equal(isWorkCircle({ space: "work", template: "custom" }), true);
  assert.equal(isWorkCircle({ space: "faith", template: "project_team" }), false);
  assert.equal(isWorkCircle({ settings: { space: "work" }, template: "small_group" }), true);
  assert.equal(isWorkCircle({ template: "department" }), true);
  assert.equal(isWorkCircle({ template: "small_group" }), false);
  assert.ok(WORK_TEMPLATES.includes("project_team"));
});

// ---------- decision status and replaces ----------

test("decision: createPost stores decisionStatus and replacesPostId on a decision", async () => {
  const W = world();
  const circleId = await workCircle(W);
  const { postId } = await decisionPost(W, circleId, "ann", { meta: { decisionStatus: "proposed", decisionOwner: "Ann" } });
  const saved = W.db.dump(`circles/${circleId}/posts/${postId}`);
  assert.equal(saved.meta.decisionStatus, "proposed");
  assert.equal(saved.meta.decisionOwner, "Ann");
  assert.equal("replacedByPostId" in saved.meta, false);
});

test("decision: a bad status, a status on another type, and a non-decision replacement are refused", async () => {
  const W = world();
  const circleId = await workCircle(W);
  assert.equal((await decisionPost(W, circleId, "ann", { meta: { decisionStatus: "maybe" } }).catch((e) => e)).code, "bad_request");
  assert.equal((await decisionPost(W, circleId, "ann", { meta: { decisionStatus: "replaced" } }).catch((e) => e)).code, "bad_request");
  const task = await W.call("ann", "createPost", { circleId, type: "task", body: "Do it", anonymous: false, requestId: crypto.randomUUID() });
  assert.equal((await W.err("ann", "createPost", { circleId, type: "task", body: "x", anonymous: false, meta: { decisionStatus: "confirmed" } })).code, "bad_request");
  assert.equal((await W.err("ann", "createPost", { circleId, type: "task", body: "x", anonymous: false, meta: { replacesPostId: "abc" } })).code, "bad_request");
  assert.equal((await W.err("ann", "createPost", { circleId, type: "decision", body: "x", anonymous: false, meta: { replacesPostId: task.postId } })).code, "bad_request");
});

test("decision: a new decision that replaces a live one marks the old one replaced, in the same write", async () => {
  const W = world();
  const circleId = await workCircle(W);
  const first = await decisionPost(W, circleId, "ann", { meta: { decisionStatus: "confirmed", decisionOwner: "Ann" } });
  const second = await decisionPost(W, circleId, "bo", { body: "Ship on Monday", meta: { replacesPostId: first.postId, decisionStatus: "proposed" } });
  const old = W.db.dump(`circles/${circleId}/posts/${first.postId}`);
  assert.equal(old.meta.decisionStatus, "replaced");
  assert.equal(old.meta.replacedByPostId, second.postId);
  assert.equal(old.meta.decisionOwner, "Ann", "the old meta is merged, not replaced");
  assert.equal(W.db.dump(`circles/${circleId}/posts/${second.postId}`).meta.replacesPostId, first.postId);
});

test("decision: replacing a missing, deleted, or already replaced decision is refused", async () => {
  const W = world();
  const circleId = await workCircle(W);
  assert.equal((await W.err("ann", "createPost", { circleId, type: "decision", body: "x", anonymous: false, meta: { replacesPostId: "nopost" } })).code, "not_found");
  const gone = await decisionPost(W, circleId, "ann");
  await W.call("ann", "deletePost", { circleId, postId: gone.postId });
  assert.equal((await W.err("ann", "createPost", { circleId, type: "decision", body: "x", anonymous: false, meta: { replacesPostId: gone.postId } })).code, "not_found");
  const first = await decisionPost(W, circleId, "ann");
  await decisionPost(W, circleId, "bo", { meta: { replacesPostId: first.postId } });
  const again = await W.err("ann", "createPost", { circleId, type: "decision", body: "third", anonymous: false, meta: { replacesPostId: first.postId } });
  assert.equal(again.code, "already_replaced");
  assert.equal(again.status, 409);
});

test("setDecisionStatus: the author, an admin or a moderator may change it; others may not", async () => {
  const W = world();
  const circleId = await workCircle(W);
  const { postId } = await decisionPost(W, circleId, "ann", { meta: { decisionOwner: "Ann" } });
  await W.call("ann", "setDecisionStatus", { circleId, postId, status: "confirmed" });
  const saved = W.db.dump(`circles/${circleId}/posts/${postId}`);
  assert.equal(saved.meta.decisionStatus, "confirmed");
  assert.equal(saved.meta.decisionOwner, "Ann", "other meta fields are kept");
  await W.call("bo", "setDecisionStatus", { circleId, postId, status: "proposed" });
  assert.equal(W.db.dump(`circles/${circleId}/posts/${postId}`).meta.decisionStatus, "proposed");
  await W.call("lead", "setDecisionStatus", { circleId, postId, status: "confirmed" }); // the owner can moderate
});

test("setDecisionStatus: members who did not write it are refused, a moderator is allowed", async () => {
  const W = world();
  const circleId = await workCircle(W);
  await joinAs(W, "cy", circleId, "Cy");
  const { postId } = await decisionPost(W, circleId, "ann");
  assert.equal((await W.err("cy", "setDecisionStatus", { circleId, postId, status: "confirmed" })).code, "forbidden");
  await W.call("lead", "setRole", { circleId, targetUid: "cy", role: "moderator" });
  await W.call("cy", "setDecisionStatus", { circleId, postId, status: "confirmed" });
  assert.equal(W.db.dump(`circles/${circleId}/posts/${postId}`).meta.decisionStatus, "confirmed");
});

test("setDecisionStatus: junk status, non-decisions, missing posts and strangers are refused", async () => {
  const W = world();
  const circleId = await workCircle(W);
  const { postId } = await decisionPost(W, circleId, "ann");
  assert.equal((await W.err("ann", "setDecisionStatus", { circleId, postId, status: "final" })).code, "bad_request");
  const task = await W.call("ann", "createPost", { circleId, type: "task", body: "Do it", anonymous: false, requestId: crypto.randomUUID() });
  assert.equal((await W.err("ann", "setDecisionStatus", { circleId, postId: task.postId, status: "confirmed" })).code, "bad_request");
  assert.equal((await W.err("ann", "setDecisionStatus", { circleId, postId: "nopost", status: "confirmed" })).code, "not_found");
  assert.equal((await W.err("stranger", "setDecisionStatus", { circleId, postId, status: "confirmed" })).code, "not_a_member");
});

test("cleanPostMeta: decisionStatus is validated and replacedByPostId is never taken from a client", () => {
  assert.equal(cleanPostMeta({ decisionStatus: "confirmed" }).decisionStatus, "confirmed");
  assert.throws(() => cleanPostMeta({ decisionStatus: "replaced" }), /Invalid decision status/);
  const m = cleanPostMeta({ replacesPostId: " abc123 ", replacedByPostId: "forged" });
  assert.equal(m.replacesPostId, "abc123");
  assert.equal("replacedByPostId" in m, false);
  assert.throws(() => cleanPostMeta({ replacesPostId: "bad/id" }), /Invalid replacesPostId/);
});

// ---------- invite preview ----------

test("previewInvite: returns the circle summary and changes nothing", async () => {
  const W = world();
  const circleId = await workCircle(W);
  const { code } = await W.call("lead", "createInvite", { circleId });
  const before = JSON.stringify([...W.db.docs.entries()]);
  const preview = await W.call("newcomer", "previewInvite", { code: `Come along ${code.toLowerCase()}!` });
  assert.deepEqual(preview, {
    circleId, name: "Client Delivery", space: "work", template: "client_engagement", memberCount: 3, coverPath: null, inviterName: "Lead",
  });
  assert.equal(JSON.stringify([...W.db.docs.entries()]), before, "no membership, counter or invite write");
  assert.equal(W.db.dump(`invites/${code}`).uses, 0);
  assert.equal(W.db.dump(`circles/${circleId}/members/newcomer`), undefined);
});

test("previewInvite: the same codes as join; a preview never makes the person a member", async () => {
  const W = world();
  const circleId = await workCircle(W);
  const { code } = await W.call("lead", "createInvite", { circleId, maxUses: 1 });
  assert.equal((await W.err("x", "previewInvite", { code: "nothing here" })).code, "not_found");
  assert.equal((await W.err("x", "previewInvite", { code: "WRONG-ZZZZ" })).code, "not_found");
  await W.call("x", "join", { code, displayName: "X" });
  assert.equal((await W.err("y", "previewInvite", { code })).code, "full");
  const r = await W.err("y", "previewInvite", { code }).then((e) => e);
  assert.equal(r.status, 409);

  const revoked = (await W.call("lead", "createInvite", { circleId })).code;
  await W.call("lead", "revokeInvite", { code: revoked });
  const e1 = await W.err("z", "previewInvite", { code: revoked });
  assert.equal(e1.code, "revoked");
  assert.equal(e1.status, 410);

  const old = (await W.call("lead", "createInvite", { circleId })).code;
  W.db.dump(`invites/${old}`).expiresAt = new Date(1);
  const e2 = await W.err("z", "previewInvite", { code: old });
  assert.equal(e2.code, "expired");
  assert.equal(e2.status, 410);

  const closed = await faithCircle(W);
  const closedCode = (await W.call("owner", "createInvite", { circleId: closed })).code;
  W.db.dump(`circles/${closed}`).closed = true;
  assert.equal((await W.err("z", "previewInvite", { code: closedCode })).code, "not_found");

  assert.equal(W.db.dump(`circles/${circleId}/members/z`), undefined);
  assert.equal(W.db.dump(`circles/${circleId}/members/y`), undefined);
});

test("previewInvite: failed codes share the join failure budget and are throttled", async () => {
  const W = world();
  for (let i = 0; i < 10; i++) assert.equal((await W.err("spammer", "previewInvite", { code: "nope" })).code, "not_found");
  assert.equal((await W.err("spammer", "previewInvite", { code: "nope" })).code, "rate_limited");
  const { circleId } = await W.call("lead", "createCircle", { name: "Ok", template: "custom", requestId: crypto.randomUUID() });
  const { code } = await W.call("lead", "createInvite", { circleId });
  // A successful preview is not a failure and is not counted against the caller.
  assert.equal((await W.call("fresh", "previewInvite", { code })).circleId, circleId);
});

test("previewInvite: needs a signed-in caller; the handler is never reached without one", async () => {
  const db = new FakeDb();
  let reached = false;
  const response = await handle(new Request("https://circles.test/v1/previewInvite", {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ code: "GRACE-7K2Q" }),
  }), { FIREBASE_PROJECT_ID: "p" }, { waitUntil() {} }, {
    authenticate: async () => { throw new ApiError(401, "unauthenticated", "Sign in first."); },
    services: { db: new Proxy(db, { get(t, k) { reached = true; return t[k]; } }), fcm: new FakeFcm() },
  });
  assert.equal(response.status, 401);
  assert.equal(reached, false);
});

// ---------- bring list (The Table) ----------

test("bringList: createEvent stores up to 20 items with ids and no claims", async () => {
  const W = world();
  const circleId = await workCircle(W);
  const { eventId } = await W.call("lead", "createEvent", {
    circleId, title: "Kickoff", startsAt: 1_800_000_000_000, endsAt: 1_800_003_600_000, timeZone: "UTC", bringList: ["Chairs", "  Cake  ", ""],
  });
  const list = W.db.dump(`circles/${circleId}/events/${eventId}`).bringList;
  assert.equal(list.length, 2);
  assert.deepEqual(list.map((x) => x.text), ["Chairs", "Cake"]);
  assert.ok(list.every((x) => typeof x.id === "string" && x.id.length > 0 && !("claimedByUid" in x)));
  assert.notEqual(list[0].id, list[1].id);
});

test("bringList: any member claims an open item; a second claim is refused; the holder's repeat is harmless", async () => {
  const W = world();
  const circleId = await workCircle(W);
  const { eventId } = await W.call("lead", "createEvent", { circleId, title: "Dinner", startsAt: 1, endsAt: 2, timeZone: "UTC", bringList: ["Bread"] });
  const itemId = W.db.dump(`circles/${circleId}/events/${eventId}`).bringList[0].id;
  await W.call("ann", "claimBringItem", { circleId, eventId, itemId, claim: true });
  const item = W.db.dump(`circles/${circleId}/events/${eventId}`).bringList[0];
  assert.equal(item.claimedByUid, "ann");
  assert.equal(item.claimedByName, "Ann");
  assert.equal((await W.err("bo", "claimBringItem", { circleId, eventId, itemId, claim: true })).code, "already_claimed");
  await W.call("ann", "claimBringItem", { circleId, eventId, itemId, claim: true });
  assert.equal(W.db.dump(`circles/${circleId}/events/${eventId}`).bringList[0].claimedByUid, "ann");
});

test("bringList: release is for the holder or an admin only; releasing an open item is harmless", async () => {
  const W = world();
  const circleId = await workCircle(W);
  const { eventId } = await W.call("lead", "createEvent", { circleId, title: "Dinner", startsAt: 1, endsAt: 2, timeZone: "UTC", bringList: ["Bread", "Salt"] });
  const [bread, salt] = W.db.dump(`circles/${circleId}/events/${eventId}`).bringList.map((x) => x.id);
  await joinAs(W, "cy", circleId, "Cy");
  await W.call("ann", "claimBringItem", { circleId, eventId, itemId: bread, claim: true });
  assert.equal((await W.err("cy", "claimBringItem", { circleId, eventId, itemId: bread, claim: false })).code, "forbidden");
  await W.call("ann", "claimBringItem", { circleId, eventId, itemId: bread, claim: false });
  assert.equal("claimedByUid" in W.db.dump(`circles/${circleId}/events/${eventId}`).bringList[0], false);
  await W.call("bo", "claimBringItem", { circleId, eventId, itemId: salt, claim: false });
  await W.call("ann", "claimBringItem", { circleId, eventId, itemId: salt, claim: true });
  await W.call("lead", "claimBringItem", { circleId, eventId, itemId: salt, claim: false });
  assert.equal("claimedByUid" in W.db.dump(`circles/${circleId}/events/${eventId}`).bringList[1], false);
});

test("bringList: unknown event or item, cancelled event, strangers and a junk flag are refused", async () => {
  const W = world();
  const circleId = await workCircle(W);
  const { eventId } = await W.call("lead", "createEvent", { circleId, title: "Dinner", startsAt: 1, endsAt: 2, timeZone: "UTC", bringList: ["Bread"] });
  const itemId = W.db.dump(`circles/${circleId}/events/${eventId}`).bringList[0].id;
  assert.equal((await W.err("ann", "claimBringItem", { circleId, eventId: "noevent", itemId, claim: true })).code, "not_found");
  assert.equal((await W.err("ann", "claimBringItem", { circleId, eventId, itemId: "noitem", claim: true })).code, "not_found");
  assert.equal((await W.err("stranger", "claimBringItem", { circleId, eventId, itemId, claim: true })).code, "not_a_member");
  assert.equal((await W.err("ann", "claimBringItem", { circleId, eventId, itemId, claim: "yes" })).code, "bad_request");
  await W.call("lead", "cancelEvent", { circleId, eventId });
  assert.equal((await W.err("ann", "claimBringItem", { circleId, eventId, itemId, claim: true })).code, "event_cancelled");
});

test("bringList: updateEvent keeps claims on unchanged items; a changed bring list is cleaned again", async () => {
  const W = world();
  const circleId = await workCircle(W);
  const { eventId } = await W.call("lead", "createEvent", { circleId, title: "Dinner", startsAt: 1, endsAt: 2, timeZone: "UTC", bringList: ["Chairs", "Cake"] });
  const cake = W.db.dump(`circles/${circleId}/events/${eventId}`).bringList[1].id;
  await W.call("ann", "claimBringItem", { circleId, eventId, itemId: cake, claim: true });
  const base = { circleId, eventId, title: "Dinner", startsAt: 1, endsAt: 2, timeZone: "UTC" };
  // No bringList in the request: everything stays.
  await W.call("lead", "updateEvent", { ...base });
  assert.equal(W.db.dump(`circles/${circleId}/events/${eventId}`).bringList.length, 2);
  // The app sends texts: Cake is unchanged so it keeps its id and claim; Chairs goes; Plates is new.
  await W.call("lead", "updateEvent", { ...base, bringList: ["Cake", "Plates"] });
  const list = W.db.dump(`circles/${circleId}/events/${eventId}`).bringList;
  assert.deepEqual(list.map((x) => x.text), ["Cake", "Plates"]);
  assert.equal(list[0].id, cake);
  assert.equal(list[0].claimedByUid, "ann");
  assert.equal("claimedByUid" in list[1], false);
  // Objects with an id keep the claim only while the text is unchanged.
  await W.call("lead", "updateEvent", { ...base, bringList: [{ id: cake, text: "Chocolate cake" }] });
  const renamed = W.db.dump(`circles/${circleId}/events/${eventId}`).bringList[0];
  assert.equal(renamed.id, cake);
  assert.equal("claimedByUid" in renamed, false);
  assert.equal((await W.err("ann", "updateEvent", { ...base, bringList: ["x"] })).code, "forbidden");
});

test("bringList: more than 20 items or an 81-character item is refused", async () => {
  const W = world();
  const circleId = await workCircle(W);
  const base = { circleId, title: "Big", startsAt: 1, endsAt: 2, timeZone: "UTC" };
  assert.equal((await W.err("lead", "createEvent", { ...base, bringList: Array.from({ length: BRING.max + 1 }, (_, i) => `Item ${i}`) })).code, "bad_request");
  assert.equal((await W.err("lead", "createEvent", { ...base, bringList: ["x".repeat(BRING.textMax + 1)] })).code, "bad_request");
  const ok = await W.call("lead", "createEvent", { ...base, bringList: Array.from({ length: BRING.max }, (_, i) => `Item ${i}`) });
  assert.equal(W.db.dump(`circles/${circleId}/events/${ok.eventId}`).bringList.length, BRING.max);
});

test("cleanBringList: strings and objects, ids are fresh and unique, claims kept only on exact matches", () => {
  let n = 0;
  const newId = () => `new${n++}`;
  const existing = [{ id: "a", text: "Chairs", claimedByUid: "ann", claimedByName: "Ann" }, { id: "b", text: "Cake" }];
  const out = cleanBringList(["Chairs", { id: "b", text: "Cake" }, { id: "zzz", text: "Plates" }, "Cake"], existing, newId);
  assert.deepEqual(out[0], { id: "a", text: "Chairs", claimedByUid: "ann", claimedByName: "Ann" });
  assert.deepEqual(out[1], { id: "b", text: "Cake" });
  assert.equal(out[2].id, "new0", "an id the server does not know gets a fresh one");
  assert.equal(out[3].id, "new1", "a repeated string does not reuse an id");
  assert.deepEqual(cleanBringList(undefined), []);
  assert.throws(() => cleanBringList("Chairs"), /must be a list/);
});

// ---------- hardening ----------

test("sniff: a QuickTime (qt brand) file declared as MP4 is refused; a real MP4 is accepted", () => {
  const box = (brand) => new Uint8Array([0, 0, 0, 0x14, ...[...("ftyp")].map((c) => c.charCodeAt(0)), ...[...brand].map((c) => c.charCodeAt(0)), 0, 0, 0, 0]);
  assert.equal(sniffMime(box("qt  "), "video/mp4"), false);
  assert.equal(sniffMime(box("isom"), "video/mp4"), true);
  assert.equal(sniffMime(box("mp42"), "video/mp4"), true);
  assert.equal(sniffMime(box("qt  "), "audio/mp4"), true, "audio sniff is unchanged");
});

test("poll closesAt: the Worker's createPoll keeps the deadline that the rules enforce on votes", async () => {
  const W = world();
  const circleId = await workCircle(W);
  const { pollId } = await W.call("ann", "createPoll", { circleId, question: "Lunch?", options: ["Thai", "Pizza"], closesAt: 1_850_000_000_000 });
  assert.equal(W.db.dump(`circles/${circleId}/polls/${pollId}`).closesAt, 1_850_000_000_000);
});
