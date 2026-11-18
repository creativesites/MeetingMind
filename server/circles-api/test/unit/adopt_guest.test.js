// A circle made before sign-in must follow the person when the phone switches to their existing Google account.
// The founder made a circle as a guest, signed in, got their old circles back, and lost the new one.
import test from "node:test";
import assert from "node:assert/strict";
import { HANDLERS } from "../../src/handlers.js";
import { FakeDb, FakeKv, FakeFcm, makeCtx } from "./fakes.js";

/** Tokens are "guest:<uid>" or "google:<uid>" here; the real Worker verifies a Firebase ID token. */
const verifyToken = async (t) => {
  const [provider, uid] = String(t).split(":");
  if (!uid) throw new Error("bad token");
  return { uid, claims: { firebase: { sign_in_provider: provider === "guest" ? "anonymous" : "google.com" } } };
};

function world() {
  const db = new FakeDb(), kv = new FakeKv(), fcm = new FakeFcm();
  const call = async (uid, name, body) => { const { ctx, flush } = makeCtx(db, kv, fcm, uid, body, { verifyToken }); const r = await HANDLERS[name](ctx); await flush(); return r; };
  const err = async (uid, name, body) => call(uid, name, body).then(() => assert.fail("expected error"), (e) => e);
  return { db, call, err };
}
const make = (W, uid, name) => W.call(uid, "createCircle", { name, template: "small_group", displayName: "Ann", requestId: crypto.randomUUID() }).then((r) => r.circleId);

test("create as a guest, sign in to an existing account: the circle moves over, owned by the account", async () => {
  const W = world();
  const old = await make(W, "google-ann", "Old fellowship");
  const fresh = await make(W, "guest-1", "Made before sign-in");

  const r = await W.call("google-ann", "adoptGuestCircles", { guestToken: "guest:guest-1" });
  assert.deepEqual(r.moved, [fresh]);

  assert.deepEqual((await W.call("google-ann", "myCircles", {})).circleIds, [old, fresh]);
  assert.deepEqual((await W.call("guest-1", "myCircles", {})).circleIds, []);
  const circle = W.db.dump(`circles/${fresh}`);
  assert.equal(circle.ownerUid, "google-ann");
  assert.equal(circle.memberCount, 1);
  assert.equal(W.db.dump(`circles/${fresh}/members/google-ann`).role, "owner");
  assert.equal(W.db.dump(`circles/${fresh}/members/guest-1`), undefined);
  // Nothing about the circle itself is lost.
  assert.equal(circle.name, "Made before sign-in");
});

test("a circle both accounts are in keeps one member, with the stronger role", async () => {
  const W = world();
  const id = await make(W, "guest-1", "Shared");
  const { code } = await W.call("guest-1", "createInvite", { circleId: id });
  await W.call("google-ann", "join", { code, displayName: "Ann" });
  assert.equal(W.db.dump(`circles/${id}`).memberCount, 2);

  await W.call("google-ann", "adoptGuestCircles", { guestToken: "guest:guest-1" });
  assert.equal(W.db.dump(`circles/${id}`).memberCount, 1);
  assert.equal(W.db.dump(`circles/${id}/members/google-ann`).role, "owner");
  assert.equal(W.db.dump(`circles/${id}`).ownerUid, "google-ann");
});

test("only a guest account can be moved, only with its own token, and running it twice changes nothing", async () => {
  const W = world();
  const id = await make(W, "guest-1", "Mine");
  await make(W, "google-bo", "Bo's");
  assert.equal((await W.err("google-ann", "adoptGuestCircles", { guestToken: "google:google-bo" })).status, 403);
  assert.equal((await W.err("google-ann", "adoptGuestCircles", { guestToken: "nonsense" })).status, 403);
  assert.equal((await W.err("google-ann", "adoptGuestCircles", {})).status, 400);
  assert.deepEqual((await W.call("google-bo", "myCircles", {})).circleIds.length, 1);

  await W.call("google-ann", "adoptGuestCircles", { guestToken: "guest:guest-1" });
  const again = await W.call("google-ann", "adoptGuestCircles", { guestToken: "guest:guest-1" });
  assert.deepEqual(again.moved, []);
  assert.deepEqual((await W.call("google-ann", "myCircles", {})).circleIds, [id]);
});
