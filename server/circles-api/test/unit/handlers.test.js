import test from "node:test";
import assert from "node:assert/strict";
import { HANDLERS } from "../../src/handlers.js";
import { runDigest } from "../../src/digest.js";
import { FakeDb, FakeKv, FakeFcm, makeCtx } from "./fakes.js";

function world() {
  const db = new FakeDb(), kv = new FakeKv(), fcm = new FakeFcm();
  const call = async (uid, name, body) => { const { ctx, flush } = makeCtx(db, kv, fcm, uid, body); const r = await HANDLERS[name](ctx); await flush(); return r; };
  const err = async (uid, name, body) => call(uid, name, body).then(() => assert.fail("expected error"), (e) => e);
  return { db, kv, fcm, call, err };
}
async function circle(W, extra = {}) {
  const { circleId } = await W.call("owner", "createCircle", { name: "Cell", template: "small_group", settings: extra, displayName: "Olive" });
  return circleId;
}
async function joinAs(W, uid, circleId, name = uid) {
  const { code } = await W.call("owner", "createInvite", { circleId });
  return W.call(uid, "join", { code: `Join us!\n${code}\nthanks`, displayName: name });
}

test("create circle: owner member, 10 circle cap", async () => {
  const W = world();
  const id = await circle(W);
  assert.equal(W.db.dump(`circles/${id}/members/owner`).role, "owner");
  W.kv.m.clear();
  for (let i = 1; i < 10; i++) { W.kv.m.clear(); await W.call("owner", "createCircle", { name: "c" + i, template: "custom" }); }
  W.kv.m.clear();
  assert.equal((await W.err("owner", "createCircle", { name: "x", template: "custom" })).code, "too_many_circles");
});

test("join: pasted message, replay, already_member, 50 cap, expiry, revoke", async () => {
  const W = world();
  const id = await circle(W);
  const { code } = await W.call("owner", "createInvite", { circleId: id, maxUses: 2 });
  const r = await W.call("u1", "join", { code: `hey ${code.toLowerCase()} :)` });
  assert.equal(r.circleId, id);
  assert.equal(W.db.dump(`circles/${id}`).memberCount, 2);
  assert.equal((await W.err("u1", "join", { code })).code, "already_member");
  await W.call("u2", "join", { code });
  assert.equal((await W.err("u3", "join", { code })).code, "full"); // maxUses spent
  assert.equal((await W.err("u3", "join", { code: "nothing here" })).code, "not_found");
  const c2 = (await W.call("owner", "createInvite", { circleId: id })).code;
  await W.call("owner", "revokeInvite", { code: c2 });
  assert.equal((await W.err("u3", "join", { code: c2 })).code, "revoked");
  const c3 = (await W.call("owner", "createInvite", { circleId: id })).code;
  W.db.dump(`invites/${c3}`).expiresAt = new Date(1);
  assert.equal((await W.err("u3", "join", { code: c3 })).code, "expired");
  W.db.dump(`circles/${id}`).memberCount = 50;
  const c4 = (await W.call("owner", "createInvite", { circleId: id })).code;
  const e = await W.err("u3", "join", { code: c4 });
  assert.equal(e.code, "full");
  assert.equal(W.db.dump(`circles/${id}`).memberCount, 50);
});

test("join: too_many_circles and failed-attempt throttling", async () => {
  const W = world();
  const id = await circle(W);
  W.db.seed("userMeta/u1", { circleCount: 10 });
  const { code } = await W.call("owner", "createInvite", { circleId: id });
  assert.equal((await W.err("u1", "join", { code })).code, "too_many_circles");
  const W2 = world();
  for (let i = 0; i < 10; i++) await W2.err("bot", "join", { code: "GRACE-2222" });
  assert.equal((await W2.err("bot", "join", { code: "GRACE-2222" })).code, "rate_limited");
});

test("non-members cannot act; members can't escalate", async () => {
  const W = world();
  const id = await circle(W);
  await joinAs(W, "m1", id);
  assert.equal((await W.err("stranger", "createPost", { circleId: id, type: "testimony", body: "hi", anonymous: false })).code, "not_a_member");
  assert.equal((await W.err("m1", "setRole", { circleId: id, targetUid: "m1", role: "admin" })).status, 400);
  assert.equal((await W.err("m1", "removeMember", { circleId: id, targetUid: "owner" })).code, "forbidden");
  assert.equal((await W.err("m1", "createInvite", { circleId: id })).code, "forbidden"); // admins-only default
  assert.equal((await W.err("m1", "approvePost", { circleId: id, postId: "x" })).code, "forbidden");
});

test("anonymous prayer: pending has no author; approve publishes anonymously; author can edit; others can't", async () => {
  const W = world();
  const id = await circle(W);
  await joinAs(W, "ann", id, "Ann");
  await joinAs(W, "bob", id, "Bob");
  const { postId, status } = await W.call("ann", "createPost", { circleId: id, type: "prayer", body: "Please pray", anonymous: true });
  assert.equal(status, "pending");
  const pend = W.db.dump(`circles/${id}/pending/${postId}`);
  assert.doesNotMatch(JSON.stringify(pend), /ann|Ann/);
  assert.equal(W.db.dump(`circles/${id}/postAuthors/${postId}`).authorUid, "ann");
  assert.equal(W.db.dump(`circles/${id}/posts/${postId}`), undefined);
  await W.call("owner", "approvePost", { circleId: id, postId });
  const post = W.db.dump(`circles/${id}/posts/${postId}`);
  assert.doesNotMatch(JSON.stringify(post), /ann|Ann/);
  assert.equal(W.db.dump(`circles/${id}/pending/${postId}`), undefined);
  assert.equal((await W.err("bob", "editPost", { circleId: id, postId, body: "hijack" })).code, "forbidden");
  assert.equal((await W.err("owner", "editPost", { circleId: id, postId, body: "admin edit" })).code, "forbidden");
  await W.call("ann", "editPost", { circleId: id, postId, body: "edited" });
  await W.call("ann", "addUpdate", { circleId: id, postId, body: "update" });
  assert.equal(W.db.dump(`circles/${id}/posts/${postId}`).counts.updates, 1);
  await W.call("ann", "markAnswered", { circleId: id, postId });
  assert.equal(W.db.dump(`circles/${id}/posts/${postId}`).status, "answered");
  // admin can delete without learning the author; content is blanked
  await W.call("owner", "deletePost", { circleId: id, postId });
  const del = W.db.dump(`circles/${id}/posts/${postId}`);
  assert.equal(del.deleted, true); assert.equal(del.body, "");
});

test("prayed: once per user, author gets no different answer; counter; digest counts only", async () => {
  const W = world();
  const id = await circle(W, { prayerApproval: false });
  await joinAs(W, "ann", id); await joinAs(W, "bob", id);
  W.db.seed("users/ann", { fcmTokens: ["tok-ann-aaaaaaaaaaaaaaaa"] });
  const { postId, status } = await W.call("ann", "createPost", { circleId: id, type: "prayer", body: "p", anonymous: true });
  assert.equal(status, "published");
  assert.equal((await W.call("bob", "prayed", { circleId: id, postId })).alreadyPrayed, false);
  assert.equal((await W.call("bob", "prayed", { circleId: id, postId })).alreadyPrayed, true);
  assert.equal((await W.call("owner", "prayed", { circleId: id, postId })).alreadyPrayed, false);
  assert.equal(W.db.dump(`circles/${id}/posts/${postId}`).counts.prayed, 2);
  const { ctx } = makeCtx(W.db, W.kv, W.fcm, "x", {});
  W.fcm.sent.length = 0;
  const r = await runDigest({ db: W.db, fcm: W.fcm, env: ctx.env });
  assert.equal(r.authors, 1);
  assert.equal(W.fcm.sent.length, 1);
  assert.equal(W.fcm.sent[0].msg.body, "2 people prayed for you today");
  assert.equal(W.db.dump(`prayerTally/${id}_${postId}`).today, 0);
});

test("notifications: published post skips author and muted members", async () => {
  const W = world();
  const id = await circle(W, { prayerApproval: false });
  await joinAs(W, "ann", id); await joinAs(W, "bob", id);
  W.db.seed("users/ann", { fcmTokens: ["tok-ann-aaaaaaaaaaaaaaaa"] });
  W.db.seed("users/bob", { fcmTokens: ["tok-bob-bbbbbbbbbbbbbbbb"] });
  W.db.seed("users/owner", { fcmTokens: ["tok-own-cccccccccccccccc"] });
  await W.call("owner", "setMute", { circleId: id, muted: true });
  W.fcm.sent.length = 0;
  await W.call("ann", "createPost", { circleId: id, type: "prayer", body: "p", anonymous: true });
  assert.deepEqual(W.fcm.sent.map((s) => s.token), ["tok-bob-bbbbbbbbbbbbbbbb"]);
  assert.doesNotMatch(JSON.stringify(W.fcm.sent), /ann|Ann/);
});

test("leave frees the slot; owner must transfer; removeMember rules; report is idempotent and unattributed", async () => {
  const W = world();
  const id = await circle(W, { prayerApproval: false });
  await joinAs(W, "m1", id); await joinAs(W, "m2", id);
  assert.equal((await W.err("owner", "leave", { circleId: id })).code, "owner_must_transfer");
  await W.call("m1", "leave", { circleId: id });
  assert.equal(W.db.dump(`circles/${id}`).memberCount, 2);
  assert.equal(W.db.dump("userMeta/m1").circleCount, 0);
  await W.call("owner", "setRole", { circleId: id, targetUid: "m2", role: "admin" });
  await W.call("owner", "removeMember", { circleId: id, targetUid: "m2" });
  await joinAs(W, "m3", id);
  const { postId } = await W.call("m3", "createPost", { circleId: id, type: "testimony", body: "t", anonymous: false });
  await W.call("owner", "report", { circleId: id, postId, reason: "x" });
  await W.call("owner", "report", { circleId: id, postId, reason: "x" });
  assert.equal(W.db.dump(`circles/${id}/posts/${postId}`).counts.reports, 1);
  const rep = [...W.db.docs].filter(([p]) => p.includes("/reports/"));
  assert.equal(rep.length, 1); assert.doesNotMatch(JSON.stringify(rep), /owner/);
});

test("path injection and rate limit on posts", async () => {
  const W = world();
  const id = await circle(W, { prayerApproval: false });
  assert.equal((await W.err("owner", "createPost", { circleId: "a/b", type: "prayer", body: "x", anonymous: false })).code, "bad_request");
  for (let i = 0; i < 10; i++) await W.call("owner", "createPost", { circleId: id, type: "prayer", body: "x", anonymous: false });
  assert.equal((await W.err("owner", "createPost", { circleId: id, type: "prayer", body: "x", anonymous: false })).code, "rate_limited");
});

test("polls: shape validated, message created, only creator/admin closes, rate limited", async () => {
  const W = world();
  const id = await circle(W);
  await joinAs(W, "ann", id, "Ann"); await joinAs(W, "bob", id, "Bob");
  const bad = (o) => W.err("ann", "createPoll", { circleId: id, ...o });
  assert.equal((await bad({ question: "Q?", options: ["only one"] })).code, "bad_request");
  assert.equal((await bad({ question: "Q?", options: ["a", "A"] })).code, "bad_request");
  assert.equal((await bad({ question: "Q?", options: ["1", "2", "3", "4", "5", "6", "7"] })).code, "bad_request");
  assert.equal((await bad({ question: "", options: ["a", "b"] })).code, "bad_request");
  assert.equal((await W.err("stranger", "createPoll", { circleId: id, question: "Q?", options: ["a", "b"] })).code, "not_a_member");
  const { pollId, messageId } = await W.call("ann", "createPoll", { circleId: id, question: "Which night?", options: ["Tue", "Thu"], multi: true });
  const poll = W.db.dump(`circles/${id}/polls/${pollId}`);
  assert.deepEqual(poll.optionIds, ["o0", "o1"]); assert.equal(poll.multi, true); assert.equal(poll.closed, false);
  const msg = W.db.dump(`circles/${id}/messages/${messageId}`);
  assert.equal(msg.kind, "poll"); assert.equal(msg.pollId, pollId); assert.equal(msg.authorUid, "ann"); assert.equal(msg.deleted, false);
  assert.equal((await W.err("bob", "closePoll", { circleId: id, pollId })).code, "forbidden");
  await W.call("ann", "closePoll", { circleId: id, pollId });
  assert.equal(W.db.dump(`circles/${id}/polls/${pollId}`).closed, true);
  await W.call("owner", "closePoll", { circleId: id, pollId }); // idempotent, admin allowed
  assert.equal((await W.err("ann", "closePoll", { circleId: id, pollId: "nope" })).code, "not_found");
  for (let i = 0; i < 9; i++) await W.call("ann", "createPoll", { circleId: id, question: "Q" + i, options: ["a", "b"] });
  assert.equal((await W.err("ann", "createPoll", { circleId: id, question: "x", options: ["a", "b"] })).code, "rate_limited");
});

test("prayer chain: 24h window, needs prayer type, linked post must be a live prayer, rate limited", async () => {
  const W = world();
  const id = await circle(W, { prayerApproval: false });
  await joinAs(W, "ann", id, "Ann");
  const { postId } = await W.call("ann", "createPost", { circleId: id, type: "prayer", body: "p", anonymous: true });
  const r = await W.call("ann", "startChain", { circleId: id, title: "For Sam's surgery", postId });
  const chain = W.db.dump(`circles/${id}/chains/${r.chainId}`);
  assert.equal(chain.endsAt.getTime() - chain.startsAt.getTime(), 24 * 3_600_000);
  assert.equal(W.db.dump(`circles/${id}/messages/${r.messageId}`).kind, "chain");
  // anonymity: a chain on an anonymous request must not carry the starter's link to the author in the post
  assert.equal(W.db.dump(`circles/${id}/posts/${postId}`).authorUid, undefined);
  assert.equal((await W.err("ann", "startChain", { circleId: id, title: "x", postId: "missing" })).code, "not_found");
  assert.equal((await W.err("stranger", "startChain", { circleId: id, title: "x" })).code, "not_a_member");
  await W.call("ann", "startChain", { circleId: id, title: "two" });
  await W.call("ann", "startChain", { circleId: id, title: "three" });
  assert.equal((await W.err("ann", "startChain", { circleId: id, title: "four" })).code, "rate_limited");
  const W2 = world();
  const id2 = await circle(W2, { allowedTypes: ["study"] });
  assert.equal((await W2.err("owner", "startChain", { circleId: id2, title: "x" })).code, "type_not_allowed");
});

test("celebrate: self only; answered needs a named, answered request you wrote; never unmasks anonymous", async () => {
  const W = world();
  const id = await circle(W, { prayerApproval: false });
  await joinAs(W, "ann", id, "Ann"); await joinAs(W, "bob", id, "Bob");
  const b = await W.call("bob", "celebrate", { circleId: id, kind: "birthday", text: "Turning 30!", companion: "zuri" });
  const m = W.db.dump(`circles/${id}/messages/${b.messageId}`);
  assert.equal(m.kind, "celebration"); assert.equal(m.authorUid, "bob"); assert.equal(m.companion, "zuri"); assert.equal(m.celebration, "birthday");
  assert.equal((await W.err("bob", "celebrate", { circleId: id, kind: "party" })).code, "bad_request");
  assert.equal((await W.err("bob", "celebrate", { circleId: id, kind: "streak", companion: "dragon" })).code, "bad_request");
  const anon = (await W.call("ann", "createPost", { circleId: id, type: "prayer", body: "p", anonymous: true })).postId;
  await W.call("ann", "markAnswered", { circleId: id, postId: anon });
  assert.equal((await W.err("ann", "celebrate", { circleId: id, kind: "answered", postId: anon })).code, "bad_request");
  const named = (await W.call("ann", "createPost", { circleId: id, type: "prayer", body: "p2", anonymous: false })).postId;
  assert.equal((await W.err("ann", "celebrate", { circleId: id, kind: "answered", postId: named })).code, "not_answered");
  assert.equal((await W.err("bob", "celebrate", { circleId: id, kind: "answered", postId: named })).code, "forbidden");
  await W.call("ann", "markAnswered", { circleId: id, postId: named });
  await W.call("ann", "celebrate", { circleId: id, kind: "answered", postId: named });
  assert.equal((await W.err("stranger", "celebrate", { circleId: id, kind: "birthday" })).code, "not_a_member");
});
