import test from "node:test";
import assert from "node:assert/strict";
import { HANDLERS } from "../../src/handlers.js";
import { uploadAvatar, downloadAvatar, uploadMedia, sniffMime } from "../../src/media.js";
import { FakeDb, FakeKv, FakeFcm, FakeR2, makeCtx } from "./fakes.js";

const C = (id) => `circles/${id}`;
const NOW = new Date("2026-10-09T14:03:22Z");

function world() {
  const db = new FakeDb(), kv = new FakeKv(), fcm = new FakeFcm(), r2 = new FakeR2();
  const run = async (uid, name, body, now = NOW) => {
    const { ctx, flush } = makeCtx(db, kv, fcm, uid, body, { env: { NOTIFY_MAX: "30", MEDIA: r2 }, now });
    const r = await HANDLERS[name](ctx); await flush(); return r;
  };
  const err = (uid, name, body) => run(uid, name, body).then(() => assert.fail("expected error"), (e) => e);
  return { db, kv, r2, call: run, err };
}
async function circle(W) {
  const { circleId } = await W.call("owner", "createCircle", { name: "Cell", template: "small_group", displayName: "Olive", requestId: crypto.randomUUID() });
  return circleId;
}
async function joinAs(W, uid, circleId, name = uid) {
  const { code } = await W.call("owner", "createInvite", { circleId });
  return W.call(uid, "join", { code: `Join us!\n${code}\nthanks`, displayName: name });
}
const jpeg = (n = 64) => Uint8Array.from([0xff, 0xd8, 0xff, 0xe0, ...Array(n - 4).fill(7)]);
const pdf = (n = 64) => new Uint8Array([...new TextEncoder().encode("%PDF-1.7"), ...Array(n - 8).fill(7)]);
const put = (W, uid, body, mime = "image/jpeg") =>
  uploadAvatar({ env: { MEDIA: W.r2 }, db: W.db, kv: W.kv, uid, now: NOW, request: new Request("https://x/v1/avatar", { method: "PUT", headers: { "Content-Type": mime }, body }) });
const get = (W, uid, circleId, target) => downloadAvatar({ env: { MEDIA: W.r2 }, db: W.db, kv: W.kv, uid, now: NOW }, circleId, target);

/** Ticket, then the raw PUT. Returns the stored path. */
async function uploadVideo(W, uid, circleId, data = new Uint8Array([0, 0, 0, 0x18, ...new TextEncoder().encode("ftypmp42"), 1, 2, 3, 4])) {
  const t = await W.call(uid, "createUploadTicket", { circleId, kind: "video", mime: "video/mp4", bytes: data.byteLength, durationMs: 4200 });
  const req = new Request(`https://x/v1/media/upload/${t.ticketId}`, { method: "PUT", headers: { "Content-Type": "video/mp4" }, body: data });
  const { path } = await uploadMedia({ env: { MEDIA: W.r2 }, db: W.db, kv: W.kv, uid, now: NOW, request: req }, t.ticketId);
  return path;
}

test("posts: a video can be attached alongside photos, and files still can't", async () => {
  const W = world();
  const id = await circle(W);
  const v = await uploadVideo(W, "owner", id);
  const { postId } = await W.call("owner", "createPost", { circleId: id, type: "testimony", body: "Watch this", anonymous: false,
    attachments: [{ path: v, mime: "video/mp4", bytes: 16, durationMs: 4200 }] });
  const post = W.db.dump(`${C(id)}/posts/${postId}`);
  assert.equal(post.attachments[0].kind, "video");
  assert.equal(post.attachments[0].durationMs, 4200);
  assert.deepEqual(post.attachmentPaths, [v]);

  const pdfT = await W.call("owner", "createUploadTicket", { circleId: id, kind: "file", mime: "application/pdf", bytes: 64 });
  const pdfPath = (await uploadMedia({ env: { MEDIA: W.r2 }, db: W.db, kv: W.kv, uid: "owner", now: NOW,
    request: new Request(`https://x/v1/media/upload/${pdfT.ticketId}`, { method: "PUT", headers: { "Content-Type": "application/pdf" }, body: pdf() }) }, pdfT.ticketId)).path;
  assert.equal((await W.err("owner", "createPost", { circleId: id, type: "testimony", body: "x", anonymous: false, attachments: [{ path: pdfPath, mime: "application/pdf", bytes: 64 }] })).code, "bad_request");
});

test("avatars: a JPEG is stored once for the user and versioned onto every circle they are in", async () => {
  const W = world();
  const id = await circle(W);
  await joinAs(W, "pat", id, "Pat");
  const { avatarVersion } = await put(W, "pat", jpeg());
  assert.equal(avatarVersion, NOW.getTime());
  assert.equal(W.db.dump(`${C(id)}/members/pat`).avatarVersion, NOW.getTime());
  assert.equal(W.db.dump("userMeta/pat").avatarVersion, NOW.getTime());
  assert.equal(W.db.dump(`${C(id)}/members/owner`).avatarVersion, undefined); // owner has no photo yet
  assert.ok(W.r2.objs.has("avatars/pat"));
});

test("avatars: only members of the same circle can read a photo, and only if it has one", async () => {
  const W = world();
  const id = await circle(W);
  const other = await circle(W); // owner's second circle
  await joinAs(W, "pat", id, "Pat");
  await joinAs(W, "sam", other, "Sam"); // sam is in a circle the owner is not in
  await put(W, "pat", jpeg());
  const obj = await get(W, "owner", id, "pat");
  assert.equal(obj.size, 64);
  assert.equal(obj.httpMetadata.contentType, "image/jpeg");
  await assert.rejects(get(W, "sam", id, "pat"), (e) => e.status === 404);     // sam isn't in this circle
  await assert.rejects(get(W, "owner", id, "owner"), (e) => e.status === 404); // owner has no photo
  await assert.rejects(get(W, "owner", other, "pat"), (e) => e.status === 404); // pat isn't in that circle
});

test("avatars: bad type, wrong contents and oversize are refused; a photo is never stored", async () => {
  const W = world();
  await circle(W);
  await assert.rejects(put(W, "owner", jpeg(), "image/gif"), (e) => e.status === 415);
  await assert.rejects(put(W, "owner", pdf(), "image/jpeg"), (e) => e.status === 415);
  await assert.rejects(put(W, "owner", new Uint8Array(0), "image/jpeg"), (e) => e.status === 413);
  assert.equal(W.r2.objs.has("avatars/owner"), false);
  assert.equal(sniffMime(jpeg(), "image/jpeg"), true);
});

test("avatars: removing a photo clears it everywhere; a new join after that gets no version", async () => {
  const W = world();
  const id = await circle(W);
  await joinAs(W, "pat", id, "Pat");
  await put(W, "pat", jpeg());
  await HANDLERS.removeAvatar({ env: { MEDIA: W.r2 }, db: W.db, kv: W.kv, uid: "pat", now: NOW, body: {} });
  assert.equal(W.r2.objs.has("avatars/pat"), false);
  assert.equal(W.db.dump(`${C(id)}/members/pat`).avatarVersion, null);
  await assert.rejects(get(W, "owner", id, "pat"), (e) => e.status === 404);
});

test("avatars: a new member inherits the user's current photo version", async () => {
  const W = world();
  const first = await circle(W);
  await joinAs(W, "pat", first, "Pat");
  await put(W, "pat", jpeg());
  const second = await circle(W);
  await W.call("pat", "join", { code: `Join us!\n${(await W.call("owner", "createInvite", { circleId: second })).code}\nthanks`, displayName: "Pat" });
  assert.equal(W.db.dump(`${C(second)}/members/pat`).avatarVersion, NOW.getTime());
});
