import test from "node:test";
import assert from "node:assert/strict";
import { HANDLERS } from "../../src/handlers.js";
import { uploadMedia, downloadMedia, sweepOrphans, sniffMime } from "../../src/media.js";
import { FakeDb, FakeKv, FakeFcm, FakeR2, makeCtx } from "./fakes.js";

const C = (id) => `circles/${id}`;
const NOW = new Date("2026-10-09T14:03:22Z");
const UUID_PATH = /^circles\/[A-Za-z0-9_-]+\/media\/[0-9a-f-]{36}$/;

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
const rawCtx = (W, uid, request, now = NOW) => ({ env: { MEDIA: W.r2 }, db: W.db, kv: W.kv, uid, now, request });

/** Ticket, then the raw PUT. Returns the stored path. */
async function uploadFile(W, uid, circleId, { kind = "image", mime = "image/jpeg", data = jpeg() } = {}) {
  const t = await W.call(uid, "createUploadTicket", { circleId, kind, mime, bytes: data.byteLength });
  const req = new Request(`https://x/v1/media/upload/${t.ticketId}`, { method: "PUT", headers: { "Content-Type": mime }, body: data });
  const { path } = await uploadMedia(rawCtx(W, uid, req), t.ticketId);
  return path;
}

test("sniff: the first bytes must match the declared type", () => {
  assert.equal(sniffMime(jpeg(), "image/jpeg"), true);
  assert.equal(sniffMime(pdf(), "image/png"), false);
  assert.equal(sniffMime(pdf(), "application/pdf"), true);
  assert.equal(sniffMime(new TextEncoder().encode("OggS-----"), "audio/ogg"), true);
  assert.equal(sniffMime(new Uint8Array([0, 0, 0, 0x20, ...new TextEncoder().encode("ftyp")]), "audio/mp4"), true);
  assert.equal(sniffMime(new Uint8Array([0, 0, 0, 0x18, ...new TextEncoder().encode("ftypmp42")]), "video/mp4"), true);
  assert.equal(sniffMime(new Uint8Array([0x1a, 0x45, 0xdf, 0xa3, 0x01, 0x02]), "video/webm"), true);
  assert.equal(sniffMime(new Uint8Array([0xff, 0xf1, 0x50, 0x80]), "audio/aac"), true);
  assert.equal(sniffMime(new Uint8Array([0xff, 0xf3, 0x50, 0x80]), "audio/aac"), false); // layer bits must be 00
});

test("ticket: members get one; strangers, wrong type, oversized, long name and bursts are refused", async () => {
  const W = world();
  const id = await circle(W);
  await joinAs(W, "pat", id, "Pat");
  const t = await W.call("pat", "createUploadTicket", { circleId: id, kind: "image", mime: "image/jpeg", bytes: 1000 });
  assert.match(t.path, UUID_PATH);
  assert.equal(typeof t.ticketId, "string");
  assert.equal((await W.err("stranger", "createUploadTicket", { circleId: id, kind: "image", mime: "image/jpeg", bytes: 1000 })).status, 403);
  assert.equal((await W.err("pat", "createUploadTicket", { circleId: id, kind: "image", mime: "image/gif", bytes: 1000 })).code, "bad_mime");
  assert.equal((await W.err("pat", "createUploadTicket", { circleId: id, kind: "image", mime: "image/jpeg", bytes: 10 * 1024 * 1024 + 1 })).code, "too_large");
  assert.equal((await W.err("pat", "createUploadTicket", { circleId: id, kind: "file", mime: "application/pdf", bytes: 1000, name: "x".repeat(121) })).code, "bad_request");
  await W.call("pat", "createUploadTicket", { circleId: id, kind: "voice", mime: "audio/mp4", bytes: 500, durationMs: 4200, name: "Note" });
  for (let i = 0; i < 58; i++) await W.call("pat", "createUploadTicket", { circleId: id, kind: "image", mime: "image/jpeg", bytes: 1000 });
  assert.equal((await W.err("pat", "createUploadTicket", { circleId: id, kind: "image", mime: "image/jpeg", bytes: 1000 })).code, "rate_limited");
});

test("upload: the right bytes are stored once; wrong type, contents, length, owner or expiry are refused", async () => {
  const W = world();
  const id = await circle(W);
  const t = await W.call("owner", "createUploadTicket", { circleId: id, kind: "image", mime: "image/jpeg", bytes: 64 });
  const ok = new Request(`https://x/v1/media/upload/${t.ticketId}`, { method: "PUT", headers: { "Content-Type": "image/jpeg" }, body: jpeg() });
  assert.deepEqual(await uploadMedia(rawCtx(W, "owner", ok), t.ticketId), { path: t.path });
  const stored = W.r2.objs.get(t.path);
  assert.equal(stored.customMetadata.uploader, "owner");
  assert.equal(stored.httpMetadata.contentType, "image/jpeg");
  // The same ticket again: the object exists, so the bucket refuses the second write.
  const again = new Request(`https://x/v1/media/upload/${t.ticketId}`, { method: "PUT", headers: { "Content-Type": "image/jpeg" }, body: jpeg() });
  await assert.rejects(uploadMedia(rawCtx(W, "owner", again), t.ticketId), (e) => e.code === "ticket_used");

  const t2 = (await W.call("owner", "createUploadTicket", { circleId: id, kind: "image", mime: "image/jpeg", bytes: 64 })).ticketId;
  const wrongContents = new Request(`https://x/v1/media/upload/${t2}`, { method: "PUT", headers: { "Content-Type": "image/jpeg" }, body: pdf() });
  await assert.rejects(uploadMedia(rawCtx(W, "owner", wrongContents), t2), (e) => e.code === "bad_mime");
  const wrongHeader = new Request(`https://x/v1/media/upload/${t2}`, { method: "PUT", headers: { "Content-Type": "image/png" }, body: jpeg() });
  await assert.rejects(uploadMedia(rawCtx(W, "owner", wrongHeader), t2), (e) => e.code === "bad_mime");
  const short = new Request(`https://x/v1/media/upload/${t2}`, { method: "PUT", headers: { "Content-Type": "image/jpeg" }, body: jpeg(32) });
  await assert.rejects(uploadMedia(rawCtx(W, "owner", short), t2), (e) => e.code === "bad_request");
  const fine = new Request(`https://x/v1/media/upload/${t2}`, { method: "PUT", headers: { "Content-Type": "image/jpeg" }, body: jpeg() });
  await assert.rejects(uploadMedia(rawCtx(W, "eve", fine), t2), (e) => e.status === 403);
  const late = new Request(`https://x/v1/media/upload/${t2}`, { method: "PUT", headers: { "Content-Type": "image/jpeg" }, body: jpeg() });
  await assert.rejects(uploadMedia(rawCtx(W, "owner", late, new Date(NOW.getTime() + 901_000)), t2), (e) => e.code === "ticket_expired");
  await assert.rejects(uploadMedia(rawCtx(W, "owner", fine), "00000000-0000-4000-8000-000000000000"), (e) => e.code === "ticket_expired");
});

test("download: members get the bytes; strangers and missing files get 404", async () => {
  const W = world();
  const id = await circle(W);
  const path = await uploadFile(W, "owner", id);
  const uuid = path.split("/").pop();
  const obj = await downloadMedia(rawCtx(W, "owner", null), id, uuid);
  assert.equal(obj.size, 64);
  await assert.rejects(downloadMedia(rawCtx(W, "stranger", null), id, uuid), (e) => e.status === 404);
  await assert.rejects(downloadMedia(rawCtx(W, "owner", null), id, "00000000-0000-4000-8000-000000000000"), (e) => e.status === 404);
  await assert.rejects(downloadMedia(rawCtx(W, "owner", null), id, "not-a-uuid"), (e) => e.status === 404);
});

test("delete: the uploader and admins can delete; other members cannot", async () => {
  const W = world();
  const id = await circle(W);
  await joinAs(W, "pat", id, "Pat");
  await joinAs(W, "eve", id, "Eve");
  const path = await uploadFile(W, "pat", id);
  assert.equal((await W.err("eve", "deleteMedia", { circleId: id, path })).status, 403);
  await W.call("pat", "deleteMedia", { circleId: id, path });
  assert.equal(W.r2.objs.has(path), false);
  const second = await uploadFile(W, "pat", id);
  await W.call("owner", "deleteMedia", { circleId: id, path: second });
  assert.equal(W.r2.objs.has(second), false);
});

test("sendMessage: the uploader sends a photo, video, voice note or file; idempotent with requestId; the Worker writes the server time", async () => {
  const W = world();
  const id = await circle(W);
  await joinAs(W, "pat", id, "Pat");
  await joinAs(W, "eve", id, "Eve");
  const path = await uploadFile(W, "pat", id);
  const { messageId } = await W.call("pat", "sendMessage", { circleId: id, kind: "image", uploadPath: path, text: "Our sunrise", requestId: "req_msg_1" });
  assert.equal(messageId, "req_msg_1");
  // Retrying with the same requestId is idempotent for the same author
  const retry = await W.call("pat", "sendMessage", { circleId: id, kind: "image", uploadPath: path, text: "Our sunrise", requestId: "req_msg_1" });
  assert.equal(retry.messageId, "req_msg_1");
  // Another member cannot reuse that requestId
  assert.equal((await W.err("eve", "sendMessage", { circleId: id, kind: "image", uploadPath: path, requestId: "req_msg_1" })).status, 409);

  const msg = W.db.dump(C(id) + `/messages/${messageId}`);
  assert.equal(msg.authorUid, "pat");
  assert.equal(msg.displayName, "Pat");
  assert.equal(msg.kind, "image");
  assert.equal(msg.text, "Our sunrise");
  assert.deepEqual(msg.attachment, { path, mime: "image/jpeg", bytes: 64 });
  assert.equal(msg.createdAt.getTime(), NOW.getTime());
  assert.equal(msg.deleted, false);

  const voice = await uploadFile(W, "pat", id, { kind: "voice", mime: "audio/mp4", data: new Uint8Array([0, 0, 0, 0x20, ...new TextEncoder().encode("ftyp"), ...Array(56).fill(1)]) });
  const v = await W.call("pat", "sendMessage", { circleId: id, kind: "voice", uploadPath: voice, durationMs: 4200, name: "Prayer" });
  assert.equal(W.db.dump(C(id) + `/messages/${v.messageId}`).attachment.durationMs, 4200);

  const video = await uploadFile(W, "pat", id, { kind: "video", mime: "video/mp4", data: new Uint8Array([0, 0, 0, 0x18, ...new TextEncoder().encode("ftypmp42"), ...Array(52).fill(2)]) });
  const vidMsg = await W.call("pat", "sendMessage", { circleId: id, kind: "video", uploadPath: video, durationMs: 12000, name: "Clip.mp4" });
  assert.equal(W.db.dump(C(id) + `/messages/${vidMsg.messageId}`).kind, "video");
  assert.equal(W.db.dump(C(id) + `/messages/${vidMsg.messageId}`).attachment.durationMs, 12000);

  assert.equal((await W.err("eve", "sendMessage", { circleId: id, kind: "image", uploadPath: path })).status, 403);
  assert.equal((await W.err("pat", "sendMessage", { circleId: id, kind: "file", uploadPath: path })).code, "bad_request");
  assert.equal((await W.err("pat", "sendMessage", { circleId: id, kind: "image", uploadPath: `circles/${id}/media/00000000-0000-4000-8000-000000000000` })).status, 404);
  assert.equal((await W.err("pat", "sendMessage", { circleId: id, kind: "image", uploadPath: path, text: "x".repeat(2001) })).code, "bad_request");
  assert.equal((await W.err("pat", "sendMessage", { circleId: id, kind: "image", uploadPath: path, replyTo: "nope123" })).status, 404);
  assert.equal((await W.err("pat", "sendMessage", { circleId: id, kind: "image", uploadPath: `circles/other/media/${path.split("/").pop()}` })).code, "bad_request");
});

test("post photos: at most four, from this circle, uploaded by the poster, images only, photo-only allowed, and deletePost cleans up media", async () => {
  const W = world();
  const id = await circle(W);
  const a = await uploadFile(W, "owner", id);
  const b = await uploadFile(W, "owner", id, { kind: "file", mime: "application/pdf", data: pdf() });
  const att = (path, extra = {}) => ({ path, mime: "image/jpeg", bytes: 64, ...extra });
  // Photo-only post (empty body) is allowed when attachments are present
  const { postId } = await W.call("owner", "createPost", { circleId: id, type: "testimony", body: "", anonymous: false, attachments: [att(a, { w: 800, h: 600 })] });
  const post = W.db.dump(C(id) + `/posts/${postId}`);
  assert.equal(post.body, "");
  assert.equal(post.attachments[0].path, a);
  assert.deepEqual(post.attachmentPaths, [a]);
  assert.equal(post.attachments[0].w, 800);

  assert.equal((await W.err("owner", "createPost", { circleId: id, type: "testimony", body: "x", anonymous: false, attachments: [1, 2, 3, 4, 5].map(() => att(a)) })).code, "bad_request");
  assert.equal((await W.err("owner", "createPost", { circleId: id, type: "testimony", body: "x", anonymous: false, attachments: [att(`circles/other/media/${a.split("/").pop()}`)] })).code, "bad_request");
  assert.equal((await W.err("owner", "createPost", { circleId: id, type: "testimony", body: "x", anonymous: false, attachments: [att(b)] })).code, "bad_request");
  await joinAs(W, "pat", id, "Pat");
  assert.equal((await W.err("pat", "createPost", { circleId: id, type: "testimony", body: "x", anonymous: false, attachments: [att(a)] })).status, 403);
  assert.equal((await W.err("owner", "createPost", { circleId: id, type: "testimony", body: "x", anonymous: false, attachments: [att(`circles/${id}/media/00000000-0000-4000-8000-000000000000`)] })).status, 404);

  // Deleting the post clears attachments/attachmentPaths and removes the R2 object
  await W.call("owner", "deletePost", { circleId: id, postId });
  const deletedPost = W.db.dump(C(id) + `/posts/${postId}`);
  assert.equal(deletedPost.deleted, true);
  assert.deepEqual(deletedPost.attachments, []);
  assert.deepEqual(deletedPost.attachmentPaths, []);
  assert.equal(W.r2.objs.has(a), false);
});

test("cover: an admin sets an uploaded image; members and non-images are refused; null clears it", async () => {
  const W = world();
  const id = await circle(W);
  await joinAs(W, "pat", id, "Pat");
  const img = await uploadFile(W, "pat", id);
  const pdfPath = await uploadFile(W, "pat", id, { kind: "file", mime: "application/pdf", data: pdf() });
  assert.equal((await W.err("pat", "updateCircle", { circleId: id, coverPath: img })).status, 403);
  assert.equal((await W.err("owner", "updateCircle", { circleId: id, coverPath: pdfPath })).code, "bad_request");
  await W.call("owner", "updateCircle", { circleId: id, coverPath: img });
  assert.equal(W.db.dump(C(id)).coverPath, img);
  await W.call("owner", "updateCircle", { circleId: id, coverPath: null });
  assert.equal(W.db.dump(C(id)).coverPath, null);
});

test("sweep: old unreferenced media goes; media a message, post, pending request or cover uses stays; recent media stays", async () => {
  const W = world();
  const id = await circle(W);
  const day = (ms) => new Date(Date.now() - ms);
  const put = async (name, uploaded) => {
    const key = `circles/${id}/media/${name}`;
    await W.r2.put(key, jpeg(), { httpMetadata: { contentType: "image/jpeg" }, customMetadata: { uploader: "owner", kind: "image" } });
    W.r2.objs.get(key).uploaded = uploaded;
    return key;
  };
  const orphan = await put("00000000-0000-4000-8000-000000000001", day(2 * 86_400_000));
  const inMessage = await put("00000000-0000-4000-8000-000000000002", day(2 * 86_400_000));
  const inPost = await put("00000000-0000-4000-8000-000000000003", day(2 * 86_400_000));
  const inPending = await put("00000000-0000-4000-8000-000000000004", day(2 * 86_400_000));
  const inCover = await put("00000000-0000-4000-8000-000000000005", day(2 * 86_400_000));
  const recent = await put("00000000-0000-4000-8000-000000000006", day(60_000));
  W.db.seed(C(id) + "/messages/m1", { authorUid: "owner", kind: "image", text: "", attachment: { path: inMessage, mime: "image/jpeg", bytes: 64 }, deleted: false });
  W.db.seed(C(id) + "/posts/p1", { attachmentPaths: [inPost], deleted: false });
  W.db.seed(C(id) + "/pending/p2", { attachmentPaths: [inPending], deleted: false });
  W.db.seed(C(id), { ...W.db.dump(C(id)), coverPath: inCover });
  W.db.seed(C(id) + "/typing/staleUser", { displayName: "Stale", updatedAt: day(120_000), expiresAt: day(60_000) });
  W.db.seed(C(id) + "/typing/activeUser", { displayName: "Active", updatedAt: new Date(), expiresAt: new Date(Date.now() + 60_000) });
  const r = await sweepOrphans({ env: { MEDIA: W.r2 }, db: W.db });
  assert.equal(r.deleted, 1);
  assert.equal(r.typingDeleted, 1);
  assert.equal(W.db.dump(C(id) + "/typing/staleUser"), undefined);
  assert.equal(W.db.dump(C(id) + "/typing/activeUser").displayName, "Active");
  assert.equal(W.r2.objs.has(orphan), false);
  for (const k of [inMessage, inPost, inPending, inCover, recent]) assert.equal(W.r2.objs.has(k), true, k);
});
