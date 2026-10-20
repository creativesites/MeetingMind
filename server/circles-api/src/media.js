// Circles media: chat photos, voice notes, files, post photos and the circle cover. Cloudflare R2, gated by the
// Worker. Clients never get R2 credentials: the Worker checks membership and writes or streams the bytes.
// Spec: docs/CIRCLES_BACKEND_HANDOFF.md section 6.
import { ApiError } from "./errors.js";
import { w } from "./firestore.js";
import { hit } from "./ratelimit.js";
import { cleanText, isAdmin, validId, LIMITS } from "./logic.js";

const MB = 1024 * 1024;
export const MEDIA = {
  image: { mimes: ["image/jpeg", "image/png", "image/webp"], max: 10 * MB },
  video: { mimes: ["video/mp4", "video/webm"], max: 50 * MB },
  voice: { mimes: ["audio/mp4", "audio/aac", "audio/ogg"], max: 25 * MB },
  file: { mimes: ["application/pdf"], max: 25 * MB },
};
export const MEDIA_RATE = { limit: 60, ttl: 3600 };
/** Kinds a post may carry. Chat takes every kind in MEDIA; a post is a photo or a video. */
const POST_KINDS = ["image", "video"];
/** A profile photo: one per user, shared by all their circles. Small enough to send as one raw PUT. */
export const AVATAR = { mimes: ["image/jpeg", "image/png", "image/webp"], max: 2 * MB };
export const AVATAR_RATE = { limit: 20, ttl: 3600 };
const avatarPath = (uid) => `avatars/${uid}`;
const C = (id) => `circles/${id}`;
export const SEND_RATE = { limit: 120, ttl: 3600 };
const TICKET_TTL_S = 900;
const ORPHAN_AGE_MS = 24 * 3600 * 1000;
const SWEEP_BATCH = 200;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

const bad = (msg) => new ApiError(400, "bad_request", msg);
const notFound = (what) => new ApiError(404, "not_found", `${what} wasn't found.`);
const notMine = (what) => new ApiError(403, "forbidden", `${what} isn't yours.`);
const ticketGone = () => new ApiError(410, "ticket_expired", "That upload has expired. Try sending it again.");

/** The object key for a new upload. The Worker picks the uuid; clients never do. */
const mediaPath = (circleId, uuid) => `circles/${circleId}/media/${uuid}`;

/** Checks a path is exactly one of this circle's media objects, and returns its uuid. */
export function uuidFromPath(circleId, path) {
  const prefix = `circles/${circleId}/media/`;
  if (typeof path !== "string" || !path.startsWith(prefix)) throw bad("That file doesn't belong to this circle.");
  const uuid = path.slice(prefix.length);
  if (!UUID.test(uuid)) throw bad("Bad file path.");
  return uuid;
}

/** Membership for media calls. Same error as the rest of the API. */
async function loadMember(ctx, circleId) {
  const [circle, member] = await ctx.db.batchGet([`circles/${circleId}`, `circles/${circleId}/members/${ctx.uid}`]);
  if (!circle || !member || circle.data.closed) throw new ApiError(403, "not_a_member", "You're not a member of this circle.");
  return { circle: circle.data, member: member.data };
}

/** Kind, mime and size limits. */
export function checkFile({ kind, mime, bytes }) {
  const rule = MEDIA[kind];
  if (!rule) throw bad("kind must be image, video, voice or file.");
  if (!rule.mimes.includes(mime)) throw new ApiError(415, "bad_mime", "That file type isn't allowed here.");
  if (!Number.isInteger(bytes) || bytes <= 0) throw bad("bytes must be a positive whole number.");
  if (bytes > rule.max) throw new ApiError(413, "too_large", "That file is too large.");
}

/**
 * Checks the first bytes against the declared type, so a file can't be sent as an image with the wrong contents.
 * Only the Worker writes object metadata, so after this check the stored mime is trusted.
 */
export function sniffMime(b, mime) {
  const at = (i, s) => [...s].every((ch, k) => b[i + k] === ch.charCodeAt(0));
  switch (mime) {
    case "image/jpeg": return b[0] === 0xff && b[1] === 0xd8 && b[2] === 0xff;
    case "image/png": return [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a].every((v, i) => b[i] === v);
    case "image/webp": return at(0, "RIFF") && at(8, "WEBP");
    case "application/pdf": return at(0, "%PDF-");
    case "audio/ogg": return at(0, "OggS");
    case "audio/mp4":
    case "video/mp4": return at(4, "ftyp");
    case "video/webm": return [0x1a, 0x45, 0xdf, 0xa3].every((v, i) => b[i] === v);
    // ADTS: 12-bit sync word 0xFFF, then layer bits 00.
    case "audio/aac": return b[0] === 0xff && (b[1] & 0xf6) === 0xf0;
    default: return false;
  }
}

const optName = (v) => (v == null ? null : cleanText(v, "name", { max: 120 }));
function optDuration(v) {
  if (v == null) return null;
  if (!Number.isInteger(v) || v < 0 || v > 3_600_000) throw bad("durationMs must be a whole number of milliseconds, up to one hour.");
  return v;
}

/** POST /v1/createUploadTicket: a single-use, 15-minute ticket for one upload. */
export async function createUploadTicket(ctx) {
  const circleId = validId(ctx.body.circleId, "circleId");
  await loadMember(ctx, circleId);
  const { kind, mime, bytes } = ctx.body;
  checkFile({ kind, mime, bytes });
  const name = optName(ctx.body.name);
  const durationMs = kind === "voice" || kind === "video" ? optDuration(ctx.body.durationMs) : null;
  await hit(ctx.kv, `rl:media:${ctx.uid}`, MEDIA_RATE);
  const ticketId = crypto.randomUUID();
  const path = mediaPath(circleId, crypto.randomUUID());
  const expiresAt = ctx.now.getTime() + TICKET_TTL_S * 1000;
  await ctx.kv.put(`ticket:${ticketId}`,
    JSON.stringify({ uid: ctx.uid, circleId, path, kind, mime, bytes, name, durationMs, expiresAt }),
    { expirationTtl: TICKET_TTL_S });
  return { ticketId, path, expiresAt: new Date(expiresAt).toISOString() };
}

/**
 * PUT /v1/media/upload/{ticketId}: raw bytes. The bucket write is conditional on the object not existing, so a ticket
 * can be used once even if two requests race: the second write fails and reports ticket_used.
 */
export async function uploadMedia(ctx, ticketId) {
  if (!UUID.test(ticketId)) throw bad("Bad upload link.");
  const raw = await ctx.kv.get(`ticket:${ticketId}`);
  if (!raw) throw ticketGone();
  const t = JSON.parse(raw);
  if (t.uid !== ctx.uid) throw notMine("That upload");
  if (ctx.now.getTime() > t.expiresAt) throw ticketGone();
  const contentType = (ctx.request.headers.get("Content-Type") || "").split(";")[0].trim();
  if (contentType !== t.mime) throw new ApiError(415, "bad_mime", "The file type doesn't match the upload request.");
  const declared = Number(ctx.request.headers.get("Content-Length") || 0);
  if (declared > MEDIA[t.kind].max) throw new ApiError(413, "too_large", "That file is too large.");
  if (declared && declared !== t.bytes) throw bad("The file size doesn't match the upload request.");
  await loadMember(ctx, t.circleId);
  const data = new Uint8Array(await ctx.request.arrayBuffer());
  if (data.byteLength !== t.bytes) throw bad("The file size doesn't match the upload request.");
  if (!sniffMime(data, t.mime)) throw new ApiError(415, "bad_mime", "The file contents don't match its type.");
  const written = await ctx.env.MEDIA.put(t.path, data, {
    onlyIf: { etagDoesNotMatch: "*" },
    httpMetadata: { contentType: t.mime },
    customMetadata: { uploader: ctx.uid, kind: t.kind, createdAt: ctx.now.toISOString() },
  });
  if (!written) throw new ApiError(409, "ticket_used", "That upload has already been used.");
  return { path: t.path };
}

/** GET /v1/media/{circleId}/{uuid}: members only. Returns the R2 object; the caller builds the Response. */
export async function downloadMedia(ctx, circleId, uuid) {
  validId(circleId, "circleId");
  if (!UUID.test(uuid)) throw notFound("That file");
  try { await loadMember(ctx, circleId); } catch { throw notFound("That file"); }
  const obj = await ctx.env.MEDIA.get(mediaPath(circleId, uuid));
  if (!obj) throw notFound("That file");
  return obj;
}

/** POST /v1/deleteMedia: the uploader or an admin. Clients never delete objects on hard delete; the Worker does. */
export async function deleteMedia(ctx) {
  const circleId = validId(ctx.body.circleId, "circleId");
  uuidFromPath(circleId, ctx.body.path);
  const { member } = await loadMember(ctx, circleId);
  const head = await ctx.env.MEDIA.head(ctx.body.path);
  if (!head) throw notFound("That file");
  if (head.customMetadata?.uploader !== ctx.uid && !isAdmin(member.role)) throw notMine("That file");
  await ctx.env.MEDIA.delete(ctx.body.path);
  return {};
}

/** POST /v1/sendMessage: the only writer of image, video, voice and file messages. The Worker checks the uploader. */
export async function sendMessage(ctx) {
  const circleId = validId(ctx.body.circleId, "circleId");
  const { kind, uploadPath } = ctx.body;
  if (!MEDIA[kind]) throw bad("kind must be image, video, voice or file.");
  uuidFromPath(circleId, uploadPath);
  const { member } = await loadMember(ctx, circleId);
  const requestedId = ctx.body.requestId == null ? null : validId(ctx.body.requestId, "requestId");
  if (requestedId) {
    const existing = await ctx.db.get(`circles/${circleId}/messages/${requestedId}`);
    if (existing) {
      if (existing.data.authorUid === ctx.uid) return { messageId: requestedId };
      throw new ApiError(409, "conflict", "That message ID is already taken.");
    }
  }
  const head = await ctx.env.MEDIA.head(uploadPath);
  if (!head) throw notFound("That upload");
  const meta = head.customMetadata || {};
  if (meta.uploader !== ctx.uid) throw notMine("That file");
  if (meta.kind !== kind) throw bad(`That file isn't a ${kind}.`);
  const mime = head.httpMetadata?.contentType;
  checkFile({ kind, mime, bytes: head.size });
  const text = ctx.body.text == null ? "" : cleanText(ctx.body.text, "text", { min: 0, max: 2000 });
  const replyTo = ctx.body.replyTo == null ? null : validId(ctx.body.replyTo, "replyTo");
  if (replyTo) {
    const r = await ctx.db.get(`circles/${circleId}/messages/${replyTo}`);
    if (!r || r.data.deleted) throw notFound("That message");
  }
  const durationMs = kind === "voice" || kind === "video" ? optDuration(ctx.body.durationMs) : null;
  const name = optName(ctx.body.name);
  await hit(ctx.kv, `rl:send:${ctx.uid}`, SEND_RATE);
  const attachment = { path: uploadPath, mime, bytes: head.size };
  if (durationMs != null) attachment.durationMs = durationMs;
  if (name) attachment.name = name;
  const messageId = requestedId || crypto.randomUUID().replace(/-/g, "").slice(0, 20);
  const msg = { authorUid: ctx.uid, displayName: member.displayName, kind, text, attachment, createdAt: ctx.now, editedAt: null, deleted: false };
  if (replyTo) msg.replyTo = replyTo;
  await ctx.db.commit([w.create(`circles/${circleId}/messages/${messageId}`, msg)]);
  return { messageId };
}

/**
 * Checks the attachments on a post before it is written: at most 4 photos or videos, under this circle, uploaded by the
 * caller. Returns the stored form, with the mime and size taken from the object rather than the client.
 */
export async function checkPostAttachments(ctx, circleId, list) {
  if (list == null) return [];
  if (!Array.isArray(list) || list.length > 4) throw bad("A post can have at most 4 photos or videos.");
  const out = [];
  for (const a of list) {
    if (!a || typeof a !== "object") throw bad("Bad attachment.");
    uuidFromPath(circleId, a.path);
    const dim = (v) => v == null || (Number.isInteger(v) && v > 0 && v <= 10000);
    if (!dim(a.w) || !dim(a.h)) throw bad("Bad photo size.");
    const head = await ctx.env.MEDIA.head(a.path);
    if (!head) throw notFound("That photo");
    const meta = head.customMetadata || {};
    if (meta.uploader !== ctx.uid) throw notMine("That photo");
    if (!POST_KINDS.includes(meta.kind)) throw bad("Posts can only include photos or videos.");
    const item = { path: a.path, kind: meta.kind, mime: head.httpMetadata?.contentType, bytes: head.size, w: a.w ?? null, h: a.h ?? null };
    if (meta.kind === "video") item.durationMs = optDuration(a.durationMs);
    out.push(item);
  }
  return out;
}

/** The cover image of a circle: an admin's choice of an uploaded image in this circle. null clears it. */
export async function checkCover(ctx, circleId, path) {
  if (path === null) return null;
  uuidFromPath(circleId, path);
  const head = await ctx.env.MEDIA.head(path);
  if (!head) throw notFound("That image");
  if (head.customMetadata?.kind !== "image") throw bad("A cover must be an image.");
  return path;
}

/**
 * Deletes media nobody references, older than 24 hours. A reference is a live or deleted message, a post or pending
 * request, or a circle's cover. Runs from the scheduled handler; scans at most SWEEP_BATCH objects per run.
 */
export async function sweepOrphans({ env, db, now = new Date(), maxPages = 5 }) {
  let cursor = (await env.RATE?.get("sweep:cursor")) || undefined;
  let scanned = 0;
  let deleted = 0;
  let truncated = false;
  for (let page = 0; page < maxPages; page++) {
    const listed = await env.MEDIA.list({ prefix: "circles/", limit: SWEEP_BATCH, cursor });
    scanned += listed.objects.length;
    for (const obj of listed.objects) {
      if (now.getTime() - new Date(obj.uploaded).getTime() < ORPHAN_AGE_MS) continue;
      const circleId = obj.key.split("/")[1];
      if (!(await referenced(db, circleId, obj.key))) {
        await env.MEDIA.delete(obj.key);
        deleted++;
      }
    }
    if (listed.truncated && listed.cursor) {
      cursor = listed.cursor;
      truncated = true;
    } else {
      cursor = undefined;
      truncated = false;
      break;
    }
  }
  if (env.RATE) {
    if (truncated && cursor) await env.RATE.put("sweep:cursor", cursor, { expirationTtl: 86_400 });
    else await env.RATE.delete?.("sweep:cursor");
  }
  let typingDeleted = 0;
  const circles = await db.query({ collection: "circles", limit: 100 });
  const typingWrites = [];
  for (const c of circles) {
    const typingDocs = await db.query({ parent: `circles/${c.id}`, collection: "typing", limit: 100 });
    for (const t of typingDocs) {
      const exp = t.data.expiresAt instanceof Date ? t.data.expiresAt.getTime() : 0;
      const upd = t.data.updatedAt instanceof Date ? t.data.updatedAt.getTime() : 0;
      if ((exp && exp < now.getTime()) || (!exp && upd && now.getTime() - upd > 60_000)) {
        typingWrites.push(w.del(`circles/${c.id}/typing/${t.id}`));
        typingDeleted++;
      }
    }
  }
  if (typingWrites.length) await db.commit(typingWrites);
  return { scanned, deleted, truncated, typingDeleted };
}

async function referenced(db, circleId, key) {
  const parent = `circles/${circleId}`;
  const hits = await Promise.all([
    db.query({ parent, collection: "messages", where: [["attachment.path", "==", key]], limit: 1 }),
    db.query({ parent, collection: "posts", where: [["attachmentPaths", "array-contains", key]], limit: 1 }),
    db.query({ parent, collection: "pending", where: [["attachmentPaths", "array-contains", key]], limit: 1 }),
    db.query({ collection: "circles", where: [["coverPath", "==", key]], limit: 1 }),
  ]);
  return hits.some((h) => h.length > 0);
}

/** The avatar fields a new member doc carries, from the user's meta. Empty when the user has no photo. */
export const avatarField = (meta) => (meta?.data.avatarVersion ? { avatarVersion: meta.data.avatarVersion } : {});

/**
 * Writes the avatar version onto the caller's member doc in every circle they are in, and on their meta, so the other
 * members' apps know to fetch a new photo (or fall back to initials when the version is null).
 */
async function setAvatarVersion(ctx, version) {
  const meta = await ctx.db.get(`userMeta/${ctx.uid}`);
  const ids = (meta?.data.circleIds || []).slice(0, LIMITS.circlesPerUser);
  const members = ids.length ? await ctx.db.batchGet(ids.map((id) => `${C(id)}/members/${ctx.uid}`)) : [];
  const writes = ids.filter((_, i) => members[i]).map((id) => w.update(`${C(id)}/members/${ctx.uid}`, { avatarVersion: version }));
  writes.push(w.update(`userMeta/${ctx.uid}`, { avatarVersion: version }, { mustExist: false }));
  await ctx.db.commit(writes);
}

/** PUT /v1/avatar: the caller's profile photo, as raw bytes. Replaces the last one. Returns the new version. */
export async function uploadAvatar(ctx) {
  const mime = (ctx.request.headers.get("Content-Type") || "").split(";")[0].trim();
  if (!AVATAR.mimes.includes(mime)) throw new ApiError(415, "bad_mime", "Profile photos must be JPEG, PNG or WebP.");
  const declared = Number(ctx.request.headers.get("Content-Length") || 0);
  if (declared > AVATAR.max) throw new ApiError(413, "too_large", "That photo is too large.");
  await hit(ctx.kv, `rl:avatar:${ctx.uid}`, AVATAR_RATE);
  const data = new Uint8Array(await ctx.request.arrayBuffer());
  if (data.byteLength === 0 || data.byteLength > AVATAR.max) throw new ApiError(413, "too_large", "That photo is too large.");
  if (!sniffMime(data, mime)) throw new ApiError(415, "bad_mime", "The file contents don't match its type.");
  await ctx.env.MEDIA.put(avatarPath(ctx.uid), data, {
    httpMetadata: { contentType: mime },
    customMetadata: { uid: ctx.uid, updatedAt: ctx.now.toISOString() },
  });
  const avatarVersion = ctx.now.getTime();
  await setAvatarVersion(ctx, avatarVersion);
  return { avatarVersion };
}

/**
 * GET /v1/avatar/{circleId}/{uid}: a member's photo, for members of the same circle only. The target must have a photo
 * (a null avatarVersion in that circle means none). Returns the R2 object; the caller builds the Response.
 */
export async function downloadAvatar(ctx, circleId, uid) {
  validId(circleId, "circleId");
  validId(uid, "uid");
  try { await loadMember(ctx, circleId); } catch { throw notFound("That photo"); }
  const target = await ctx.db.get(`${C(circleId)}/members/${uid}`);
  if (!target?.data.avatarVersion) throw notFound("That photo");
  const obj = await ctx.env.MEDIA.get(avatarPath(uid));
  if (!obj) throw notFound("That photo");
  return obj;
}

/** POST /v1/removeAvatar: deletes the caller's profile photo and clears it in every circle. */
export async function removeAvatar(ctx) {
  await ctx.env.MEDIA.delete(avatarPath(ctx.uid));
  await setAvatarVersion(ctx, null);
  return {};
}
