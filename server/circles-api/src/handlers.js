// Endpoint handlers. Every handler: (ctx) => result object. ctx = { env, db, fcm, kv, uid, ip, now, body, defer }.
import { ApiError } from "./errors.js";
import { FirestoreError, w } from "./firestore.js";
import { hmacHex } from "./crypto.js";
import { parseServiceAccount } from "./google.js";
import { pushToUsers } from "./fcm.js";
import { hit, check, record } from "./ratelimit.js";
import {
  LIMITS, RATE, REACTIONS, POST_TYPES, TEMPLATES, validId, cleanText, cleanVerseRef, normalizeSettings,
  generateCode, extractCodes, CHAT, shapePoll, shapeCelebration, clampInvite, joinDecision, canInvite, isAdmin, shapePost, publishFromPending, assertNoAuthorLeak,
} from "./logic.js";

const C = (id) => `circles/${id}`;
const forbidden = () => new ApiError(403, "not_a_member", "You're not a member of this circle.");
const notFound = (what = "That") => new ApiError(404, "not_found", `${what} wasn't found.`);

function newId() {
  const A = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
  const b = crypto.getRandomValues(new Uint8Array(40));
  let s = ""; for (const x of b) { if (x < 248 && s.length < 20) s += A[x % 62]; }
  return s.length === 20 ? s : newId();
}
const name = (v) => cleanText(v ?? "Member", "displayName", { max: 40 });
const str = (b, k) => validId(b[k], k);

async function load(ctx, circleId) {
  const [circle, member] = await ctx.db.batchGet([C(circleId), `${C(circleId)}/members/${ctx.uid}`]);
  if (!circle || !member || circle.data.closed) throw forbidden();
  return { circle: circle.data, member: member.data };
}
async function livePost(ctx, circleId, postId) {
  const p = await ctx.db.get(`${C(circleId)}/posts/${postId}`);
  if (!p || p.data.deleted) throw notFound("That post");
  return p.data;
}
async function requireAuthor(ctx, circleId, postId) {
  const a = await ctx.db.get(`${C(circleId)}/postAuthors/${postId}`);
  // Same answer whether the post doesn't exist or isn't yours.
  if (!a || a.data.authorUid !== ctx.uid) throw new ApiError(403, "forbidden", "Only the author can do that.");
  return a.data;
}
const maxPush = (ctx) => Math.max(0, parseInt(ctx.env.NOTIFY_MAX || "30", 10) || 30);

async function notifyMembers(ctx, circleId, excludeUid, message) {
  const ms = await ctx.db.query({ parent: C(circleId), collection: "members", where: [["muted", "==", false]], limit: LIMITS.membersPerCircle });
  const uids = ms.map((m) => m.id).filter((u) => u !== excludeUid);
  return pushToUsers({ db: ctx.db, fcm: ctx.fcm, uids, message, max: maxPush(ctx) });
}
async function notifyAdmins(ctx, circleId, message) {
  const ms = await ctx.db.query({ parent: C(circleId), collection: "members", where: [["role", "in", ["owner", "admin"]]], limit: 50 });
  return pushToUsers({ db: ctx.db, fcm: ctx.fcm, uids: ms.map((m) => m.id), message, max: maxPush(ctx) });
}
const postMsg = (circleName, post, id, circleId) => ({
  title: circleName,
  body: post.anonymous ? "Someone shared a prayer request" : `${post.authorName} shared something new`,
  data: { kind: "post", circleId, postId: id },
});

// ---------- circles ----------
async function createCircle(ctx) {
  const b = ctx.body;
  const template = typeof b.template === "string" ? b.template : "";
  if (!TEMPLATES[template]) throw new ApiError(400, "bad_request", "Unknown template");
  const nm = cleanText(b.name, "name", { max: LIMITS.nameMax });
  const vocab = cleanText(b.vocab, "vocab", { min: 0, max: 40, optional: true }) ?? "Circle";
  const settings = normalizeSettings(b.settings, template);
  const displayName = name(b.displayName);
  await hit(ctx.kv, `rl:createCircle:${ctx.uid}`, RATE.createCircle);
  const id = newId();
  await ctx.db.runTx(async (tx) => {
    const meta = await tx.get(`userMeta/${ctx.uid}`);
    const n = meta?.data.circleCount || 0;
    if (n >= LIMITS.circlesPerUser) throw new ApiError(409, "too_many_circles", "You're in the maximum of 10 circles. Leave one to create another.");
    return [
      w.create(C(id), { name: nm, template, vocab, settings, ownerUid: ctx.uid, memberCount: 1, orgId: null, closed: false, createdAt: ctx.now }),
      w.create(`${C(id)}/members/${ctx.uid}`, { role: "owner", displayName, joinedAt: ctx.now, muted: false }),
      w.set(`userMeta/${ctx.uid}`, { circleCount: n + 1, circleIds: [...(meta?.data.circleIds || []), id] }),
    ];
  });
  return { circleId: id };
}

async function updateCircle(ctx) {
  const id = str(ctx.body, "circleId");
  const { circle, member } = await load(ctx, id);
  if (!isAdmin(member.role)) throw new ApiError(403, "forbidden", "Only admins can change this.");
  const data = {};
  if (ctx.body.name !== undefined) data.name = cleanText(ctx.body.name, "name", { max: LIMITS.nameMax });
  if (ctx.body.vocab !== undefined) data.vocab = cleanText(ctx.body.vocab, "vocab", { max: 40 });
  if (ctx.body.settings !== undefined) data.settings = normalizeSettings({ ...circle.settings, ...ctx.body.settings }, circle.template);
  if (!Object.keys(data).length) throw new ApiError(400, "bad_request", "Nothing to change");
  await ctx.db.commit([w.update(C(id), data)]);
  return {};
}

/** The caller's circle ids (so a fresh install on a linked account can find its circles; rules forbid listing circles). */
async function myCircles(ctx) {
  const meta = await ctx.db.get(`userMeta/${ctx.uid}`);
  const ids = (meta?.data.circleIds || []).slice(0, LIMITS.circlesPerUser);
  const members = ids.length ? await ctx.db.batchGet(ids.map((id) => `${C(id)}/members/${ctx.uid}`)) : [];
  return { circleIds: ids.filter((_, i) => members[i]) };
}

// ---------- invites ----------
async function createInvite(ctx) {
  const id = str(ctx.body, "circleId");
  const { circle, member } = await load(ctx, id);
  if (!canInvite(member.role, circle.settings)) throw new ApiError(403, "forbidden", "Only admins can invite to this circle.");
  const { days, uses } = clampInvite(ctx.body);
  await hit(ctx.kv, `rl:createInvite:${ctx.uid}`, RATE.createInvite);
  const expiresAt = new Date(ctx.now.getTime() + days * 86_400_000);
  for (let i = 0; i < 6; i++) {
    const code = generateCode();
    try {
      await ctx.db.commit([w.create(`invites/${code}`, { circleId: id, createdBy: ctx.uid, expiresAt, maxUses: uses, uses: 0, revoked: false, createdAt: ctx.now })]);
      return { code, expiresAt: expiresAt.toISOString(), maxUses: uses };
    } catch (e) {
      if (!(e instanceof FirestoreError && e.code === "ALREADY_EXISTS")) throw e; // collision: try another
    }
  }
  throw new ApiError(503, "try_again", "Couldn't make a code just now. Try again.");
}

async function revokeInvite(ctx) {
  const codes = extractCodes(String(ctx.body.code ?? ""), 1);
  if (!codes.length) throw notFound("That code");
  const inv = await ctx.db.get(`invites/${codes[0]}`);
  if (!inv) throw notFound("That code");
  const { member } = await load(ctx, inv.data.circleId);
  if (!isAdmin(member.role) && inv.data.createdBy !== ctx.uid) throw new ApiError(403, "forbidden", "Only admins can turn off a code.");
  await ctx.db.commit([w.update(`invites/${codes[0]}`, { revoked: true })]);
  return {};
}

async function join(ctx) {
  const codes = extractCodes(String(ctx.body.code ?? ""));
  const displayName = name(ctx.body.displayName);
  await check(ctx.kv, `rl:joinfail:u:${ctx.uid}`, RATE.joinFailUser);
  await check(ctx.kv, `rl:joinfail:ip:${ctx.ip}`, RATE.joinFailIp);
  const failed = async (code, msg, status = 409) => {
    await Promise.all([record(ctx.kv, `rl:joinfail:u:${ctx.uid}`, RATE.joinFailUser), record(ctx.kv, `rl:joinfail:ip:${ctx.ip}`, RATE.joinFailIp)]);
    return new ApiError(status, code, msg);
  };
  let found = null;
  for (const c of codes) { const d = await ctx.db.get(`invites/${c}`); if (d) { found = d; break; } }
  if (!found) throw await failed("not_found", "That code wasn't found. Check it and try again.", 404);
  const code = found.id;
  let result;
  try {
    await ctx.db.runTx(async (tx) => {
      const invite = (await tx.get(`invites/${code}`))?.data;
      const circleDoc = invite && (await tx.get(C(invite.circleId)));
      const memberDoc = invite && (await tx.get(`${C(invite.circleId)}/members/${ctx.uid}`));
      const meta = await tx.get(`userMeta/${ctx.uid}`);
      const circle = circleDoc?.data;
      const err = joinDecision({
        invite, nowMs: ctx.now.getTime(), memberCount: circle?.memberCount ?? 0, userCircleCount: meta?.data.circleCount || 0,
        alreadyMember: !!memberDoc, circleClosed: !circle || circle.closed,
      });
      if (err) throw new ApiError(err[0] === "not_found" ? 404 : err[0] === "expired" || err[0] === "revoked" ? 410 : 409, err[0], err[1]);
      result = { circleId: invite.circleId, name: circle.name };
      return [
        w.create(`${C(invite.circleId)}/members/${ctx.uid}`, { role: "member", displayName, joinedAt: ctx.now, muted: false }),
        w.update(C(invite.circleId), { memberCount: circle.memberCount + 1 }),
        w.update(`invites/${code}`, {}, { inc: { uses: 1 } }),
        w.set(`userMeta/${ctx.uid}`, { circleCount: (meta?.data.circleCount || 0) + 1, circleIds: [...(meta?.data.circleIds || []), invite.circleId] }),
      ];
    });
  } catch (e) {
    if (e instanceof ApiError && e.code !== "already_member") throw await failed(e.code, e.message, e.status);
    if (e instanceof FirestoreError && e.code === "ALREADY_EXISTS") throw new ApiError(409, "already_member", "You're already in this circle.");
    throw e;
  }
  return result;
}

// ---------- membership ----------
async function removeFromCircle(ctx, circleId, targetUid, authorize) {
  await ctx.db.runTx(async (tx) => {
    const circle = await tx.get(C(circleId));
    const actor = await tx.get(`${C(circleId)}/members/${ctx.uid}`);
    const target = await tx.get(`${C(circleId)}/members/${targetUid}`);
    if (!circle || !actor) throw forbidden();
    if (!target) throw notFound("That member");
    authorize(actor.data.role, target.data.role, circle.data);
    const meta = await tx.get(`userMeta/${targetUid}`);
    const left = circle.data.memberCount - 1;
    return [
      w.del(`${C(circleId)}/members/${targetUid}`),
      w.update(C(circleId), left <= 0 ? { memberCount: 0, closed: true } : { memberCount: left }),
      w.set(`userMeta/${targetUid}`, { circleCount: Math.max(0, (meta?.data.circleCount || 1) - 1), circleIds: (meta?.data.circleIds || []).filter((x) => x !== circleId) }),
    ];
  });
  return {};
}
const leave = (ctx) => {
  const id = str(ctx.body, "circleId");
  return removeFromCircle(ctx, id, ctx.uid, (_a, role, circle) => {
    if (role === "owner" && circle.memberCount > 1) throw new ApiError(409, "owner_must_transfer", "Make someone else the owner before you leave.");
  });
};
const removeMember = (ctx) => {
  const id = str(ctx.body, "circleId"); const target = str(ctx.body, "targetUid");
  if (target === ctx.uid) throw new ApiError(400, "bad_request", "Use leave to remove yourself.");
  return removeFromCircle(ctx, id, target, (actorRole, targetRole) => {
    if (!isAdmin(actorRole)) throw new ApiError(403, "forbidden", "Only admins can remove members.");
    if (targetRole === "owner" || (targetRole === "admin" && actorRole !== "owner")) throw new ApiError(403, "forbidden", "You can't remove that person.");
  });
};

async function setRole(ctx) {
  const id = str(ctx.body, "circleId"); const target = str(ctx.body, "targetUid"); const role = ctx.body.role;
  if (!["admin", "member", "owner"].includes(role)) throw new ApiError(400, "bad_request", "Invalid role");
  if (target === ctx.uid) throw new ApiError(400, "bad_request", "You can't change your own role.");
  await ctx.db.runTx(async (tx) => {
    const actor = await tx.get(`${C(id)}/members/${ctx.uid}`);
    const tm = await tx.get(`${C(id)}/members/${target}`);
    if (!actor) throw forbidden();
    if (actor.data.role !== "owner") throw new ApiError(403, "forbidden", "Only the owner can change roles.");
    if (!tm) throw notFound("That member");
    const ws = [w.update(`${C(id)}/members/${target}`, { role })];
    if (role === "owner") ws.push(w.update(`${C(id)}/members/${ctx.uid}`, { role: "admin" }), w.update(C(id), { ownerUid: target }));
    return ws;
  });
  return {};
}

async function setMute(ctx) {
  const id = str(ctx.body, "circleId");
  if (typeof ctx.body.muted !== "boolean") throw new ApiError(400, "bad_request", "muted must be true or false");
  await load(ctx, id);
  await ctx.db.commit([w.update(`${C(id)}/members/${ctx.uid}`, { muted: ctx.body.muted })]);
  return {};
}

// ---------- posts ----------
async function createPost(ctx) {
  const b = ctx.body;
  const circleId = str(b, "circleId");
  const { circle, member } = await load(ctx, circleId);
  const body = cleanText(b.body, "body", { max: LIMITS.bodyMax });
  const verseRef = cleanVerseRef(b.verseRef);
  if (b.type === "announcement" && !isAdmin(member.role)) throw new ApiError(403, "forbidden", "Only admins can post announcements.");
  const { pending, post, authorDoc } = shapePost({ type: b.type, body, verseRef, anonymous: b.anonymous === true ? true : b.anonymous === false ? false : undefined, uid: ctx.uid, authorName: member.displayName, settings: circle.settings, now: ctx.now });
  await hit(ctx.kv, `rl:createPost:${ctx.uid}`, RATE.createPost);
  const id = newId();
  let doc = post;
  if (pending) { doc = { ...post }; delete doc.authorUid; delete doc.authorName; }
  if (post.anonymous) assertNoAuthorLeak(doc, ctx.uid);
  await ctx.db.commit([
    w.create(`${C(circleId)}/postAuthors/${id}`, authorDoc),
    w.create(`${C(circleId)}/${pending ? "pending" : "posts"}/${id}`, doc),
  ]);
  ctx.defer(pending
    ? notifyAdmins(ctx, circleId, { title: circle.name, body: "A prayer request is waiting for approval", data: { kind: "pending", circleId } })
    : notifyMembers(ctx, circleId, ctx.uid, postMsg(circle.name, post, id, circleId)));
  return { postId: id, status: pending ? "pending" : "published" };
}

async function decide(ctx, approve) {
  const circleId = str(ctx.body, "circleId"); const postId = str(ctx.body, "postId");
  const { circle, member } = await load(ctx, circleId);
  if (!isAdmin(member.role)) throw new ApiError(403, "forbidden", "Only admins can do that.");
  let published, authorUid;
  await ctx.db.runTx(async (tx) => {
    const pend = await tx.get(`${C(circleId)}/pending/${postId}`);
    const auth = await tx.get(`${C(circleId)}/postAuthors/${postId}`);
    if (!pend || !auth) throw notFound("That request");
    authorUid = auth.data.authorUid;
    if (!approve) return [w.del(`${C(circleId)}/pending/${postId}`), w.del(`${C(circleId)}/postAuthors/${postId}`)];
    published = publishFromPending(pend.data, auth.data, ctx.now);
    return [w.create(`${C(circleId)}/posts/${postId}`, published), w.del(`${C(circleId)}/pending/${postId}`)];
  });
  ctx.defer((async () => {
    await pushToUsers({ db: ctx.db, fcm: ctx.fcm, uids: [authorUid], max: 5, message: approve
      ? { title: circle.name, body: "Your prayer request was shared with the circle", data: { kind: "approved", circleId, postId } }
      : { title: circle.name, body: "Your prayer request wasn't shared this time", data: { kind: "rejected", circleId } } });
    if (approve) await notifyMembers(ctx, circleId, authorUid, postMsg(circle.name, published, postId, circleId));
  })());
  return {};
}
const approvePost = (ctx) => decide(ctx, true);
const rejectPost = (ctx) => decide(ctx, false);

async function editPost(ctx) {
  const circleId = str(ctx.body, "circleId"); const postId = str(ctx.body, "postId");
  await load(ctx, circleId);
  await requireAuthor(ctx, circleId, postId);
  await livePost(ctx, circleId, postId);
  const data = { body: cleanText(ctx.body.body, "body", { max: LIMITS.bodyMax }), editedAt: ctx.now };
  if (ctx.body.verseRef !== undefined) data.verseRef = cleanVerseRef(ctx.body.verseRef);
  await ctx.db.commit([w.update(`${C(circleId)}/posts/${postId}`, data)]);
  return {};
}

async function addUpdate(ctx) {
  const circleId = str(ctx.body, "circleId"); const postId = str(ctx.body, "postId");
  await load(ctx, circleId);
  await requireAuthor(ctx, circleId, postId);
  const post = await livePost(ctx, circleId, postId);
  if (post.type !== "prayer") throw new ApiError(400, "bad_request", "Only prayer requests have updates.");
  if ((post.counts?.updates || 0) >= LIMITS.maxUpdates) throw new ApiError(409, "too_many_updates", "That request has too many updates.");
  const id = newId();
  await ctx.db.commit([
    w.create(`${C(circleId)}/posts/${postId}/updates/${id}`, { body: cleanText(ctx.body.body, "body", { max: LIMITS.bodyMax }), createdAt: ctx.now }),
    w.update(`${C(circleId)}/posts/${postId}`, {}, { inc: { "counts.updates": 1 } }),
  ]);
  return { updateId: id };
}

async function markAnswered(ctx) {
  const circleId = str(ctx.body, "circleId"); const postId = str(ctx.body, "postId");
  const { circle } = await load(ctx, circleId);
  await requireAuthor(ctx, circleId, postId);
  const post = await livePost(ctx, circleId, postId);
  if (post.type !== "prayer") throw new ApiError(400, "bad_request", "Only prayer requests can be answered.");
  if (post.status === "answered") return {};
  await ctx.db.commit([w.update(`${C(circleId)}/posts/${postId}`, { status: "answered", answeredAt: ctx.now })]);
  ctx.defer((async () => {
    const prayers = await ctx.db.query({ parent: `${C(circleId)}/posts/${postId}`, collection: "prayers", limit: LIMITS.membersPerCircle });
    await pushToUsers({ db: ctx.db, fcm: ctx.fcm, uids: prayers.map((p) => p.id).filter((u) => u !== ctx.uid), max: maxPush(ctx),
      message: { title: circle.name, body: "A prayer you prayed for was answered", data: { kind: "answered", circleId, postId } } });
  })());
  return {};
}

async function deletePost(ctx) {
  const circleId = str(ctx.body, "circleId"); const postId = str(ctx.body, "postId");
  const { member } = await load(ctx, circleId);
  const auth = await ctx.db.get(`${C(circleId)}/postAuthors/${postId}`);
  if (!auth) throw notFound("That post");
  const isAuthor = auth.data.authorUid === ctx.uid;
  if (!isAuthor && !isAdmin(member.role)) throw new ApiError(403, "forbidden", "Only the author or an admin can delete this.");
  const pend = await ctx.db.get(`${C(circleId)}/pending/${postId}`);
  if (pend) { // withdrawing / removing a request that was never published
    await ctx.db.commit([w.del(`${C(circleId)}/pending/${postId}`), w.del(`${C(circleId)}/postAuthors/${postId}`)]);
    return {};
  }
  await livePost(ctx, circleId, postId);
  await ctx.db.commit([w.update(`${C(circleId)}/posts/${postId}`, { deleted: true, body: "", verseRef: null })]);
  return {};
}

async function prayed(ctx) {
  const circleId = str(ctx.body, "circleId"); const postId = str(ctx.body, "postId");
  await load(ctx, circleId);
  await livePost(ctx, circleId, postId);
  // No special case for the author: a different answer would unmask anonymous requesters.
  try {
    await ctx.db.commit([
      w.create(`${C(circleId)}/posts/${postId}/prayers/${ctx.uid}`, { createdAt: ctx.now }),
      w.update(`${C(circleId)}/posts/${postId}`, {}, { inc: { "counts.prayed": 1 } }),
      w.update(`prayerTally/${circleId}_${postId}`, { circleId, postId }, { inc: { today: 1 }, mustExist: false }),
    ]);
  } catch (e) {
    if (e instanceof FirestoreError && e.code === "ALREADY_EXISTS") return { alreadyPrayed: true };
    throw e;
  }
  return { alreadyPrayed: false };
}

async function react(ctx) {
  const circleId = str(ctx.body, "circleId"); const postId = str(ctx.body, "postId");
  const kind = ctx.body.kind;
  const { circle } = await load(ctx, circleId);
  if (kind !== null && !(REACTIONS.includes(kind) && circle.settings.reactions.includes(kind))) throw new ApiError(400, "bad_request", "Unknown reaction");
  await livePost(ctx, circleId, postId);
  const rp = `${C(circleId)}/posts/${postId}/reactions/${ctx.uid}`;
  await ctx.db.runTx(async (tx) => {
    const old = (await tx.get(rp))?.data.kind ?? null;
    if (old === kind) return [];
    const inc = {};
    if (old) inc[`counts.reactions.${old}`] = -1;
    if (kind) inc[`counts.reactions.${kind}`] = 1;
    return [kind ? w.set(rp, { authorUid: ctx.uid, kind, createdAt: ctx.now }) : w.del(rp), w.update(`${C(circleId)}/posts/${postId}`, {}, { inc })];
  });
  return {};
}

/** Clients write comments directly (rules); this recomputes the counters from the truth. Idempotent. */
async function syncCounts(ctx) {
  const circleId = str(ctx.body, "circleId"); const postId = str(ctx.body, "postId");
  await load(ctx, circleId);
  const post = await livePost(ctx, circleId, postId);
  const last = post.countsSyncedAt instanceof Date ? post.countsSyncedAt.getTime() : 0;
  if (ctx.now.getTime() - last < 20_000) return { counts: post.counts };
  const parent = `${C(circleId)}/posts/${postId}`;
  const comments = await ctx.db.count({ parent, collection: "comments" });
  const reactions = {};
  for (const k of REACTIONS) reactions[k] = await ctx.db.count({ parent, collection: "reactions", where: [["kind", "==", k]] });
  await ctx.db.commit([w.update(parent, { "counts.comments": comments, "counts.reactions": reactions, countsSyncedAt: ctx.now })]);
  return { counts: { ...post.counts, comments, reactions } };
}

async function report(ctx) {
  const circleId = str(ctx.body, "circleId"); const postId = str(ctx.body, "postId");
  await load(ctx, circleId);
  await livePost(ctx, circleId, postId);
  const reason = cleanText(ctx.body.reason, "reason", { min: 0, max: 500, optional: true }) ?? "";
  await hit(ctx.kv, `rl:report:${ctx.uid}`, RATE.report);
  // Reporter identity is stored only as a keyed hash: admins can count reports but not unmask reporters.
  const h = (await hmacHex(parseServiceAccount(ctx.env).private_key, `${ctx.uid}:${postId}`)).slice(0, 24);
  try {
    await ctx.db.commit([
      w.create(`${C(circleId)}/reports/${postId}_${h}`, { postId, reason, createdAt: ctx.now }),
      w.update(`${C(circleId)}/posts/${postId}`, {}, { inc: { "counts.reports": 1 } }),
    ]);
  } catch (e) {
    if (!(e instanceof FirestoreError && e.code === "ALREADY_EXISTS")) throw e;
  }
  return {};
}

// ---------- chat & fun ----------
// Clients write text/card/reply messages themselves (rules). Polls, prayer chains and celebrations
// are created here so their shape, rate limits and notifications can't be forged.
function systemMessage(ctx, member, fields) {
  return { authorUid: ctx.uid, displayName: member.displayName, deleted: false, createdAt: ctx.now, editedAt: null, ...fields };
}

async function createPoll(ctx) {
  const circleId = str(ctx.body, "circleId");
  const { circle, member } = await load(ctx, circleId);
  const shaped = shapePoll(ctx.body);
  await hit(ctx.kv, `rl:createPoll:${ctx.uid}`, RATE.createPoll);
  const pollId = newId(); const messageId = newId();
  await ctx.db.commit([
    w.create(`${C(circleId)}/polls/${pollId}`, { ...shaped, closed: false, createdBy: ctx.uid, createdAt: ctx.now, messageId }),
    w.create(`${C(circleId)}/messages/${messageId}`, systemMessage(ctx, member, { kind: "poll", text: shaped.question, pollId })),
  ]);
  ctx.defer(notifyMembers(ctx, circleId, ctx.uid, { title: circle.name, body: `${member.displayName} started a poll`, data: { kind: "poll", circleId, messageId } }));
  return { pollId, messageId };
}

async function closePoll(ctx) {
  const circleId = str(ctx.body, "circleId"); const pollId = str(ctx.body, "pollId");
  const { member } = await load(ctx, circleId);
  const poll = await ctx.db.get(`${C(circleId)}/polls/${pollId}`);
  if (!poll) throw notFound("That poll");
  if (poll.data.createdBy !== ctx.uid && !isAdmin(member.role)) throw new ApiError(403, "forbidden", "Only the person who made the poll, or an admin, can close it.");
  if (!poll.data.closed) await ctx.db.commit([w.update(`${C(circleId)}/polls/${pollId}`, { closed: true, closedAt: ctx.now })]);
  return {};
}

async function startChain(ctx) {
  const circleId = str(ctx.body, "circleId");
  const { circle, member } = await load(ctx, circleId);
  if (!circle.settings.allowedTypes.includes("prayer")) throw new ApiError(403, "type_not_allowed", "This circle doesn't use prayer requests.");
  const title = cleanText(ctx.body.title, "title", { max: CHAT.chainTitleMax });
  let postId = null;
  if (ctx.body.postId !== undefined && ctx.body.postId !== null) {
    postId = str(ctx.body, "postId");
    const post = await livePost(ctx, circleId, postId);
    if (post.type !== "prayer") throw new ApiError(400, "bad_request", "A prayer chain is for a prayer request.");
  }
  await hit(ctx.kv, `rl:startChain:${ctx.uid}`, RATE.startChain);
  const chainId = newId(); const messageId = newId();
  const endsAt = new Date(ctx.now.getTime() + CHAT.chainHours * 3_600_000);
  await ctx.db.commit([
    w.create(`${C(circleId)}/chains/${chainId}`, { title, postId, hours: CHAT.chainHours, startsAt: ctx.now, endsAt, createdBy: ctx.uid, createdByName: member.displayName, messageId }),
    w.create(`${C(circleId)}/messages/${messageId}`, systemMessage(ctx, member, { kind: "chain", text: title, chainId })),
  ]);
  ctx.defer(notifyMembers(ctx, circleId, ctx.uid, { title: circle.name, body: `${member.displayName} started a 24-hour prayer chain`, data: { kind: "chain", circleId, chainId } }));
  return { chainId, messageId, endsAt: endsAt.toISOString() };
}

/** A member celebrates THEMSELVES (never someone else, so birthdays stay opt-in). */
async function celebrate(ctx) {
  const circleId = str(ctx.body, "circleId");
  const { circle, member } = await load(ctx, circleId);
  const c = shapeCelebration(ctx.body);
  if (c.kind === "answered") {
    // An answered prayer may be celebrated only by its named author. Anonymous requests can never be
    // celebrated by name, or this would unmask the requester.
    const postId = str(ctx.body, "postId");
    const post = await livePost(ctx, circleId, postId);
    if (post.anonymous) throw new ApiError(400, "bad_request", "Anonymous requests can't be celebrated by name.");
    await requireAuthor(ctx, circleId, postId);
    if (post.status !== "answered") throw new ApiError(409, "not_answered", "Mark the request answered first.");
  }
  await hit(ctx.kv, `rl:celebrate:${ctx.uid}`, RATE.celebrate);
  const messageId = newId();
  await ctx.db.commit([w.create(`${C(circleId)}/messages/${messageId}`, systemMessage(ctx, member, { kind: "celebration", text: c.text, celebration: c.kind, companion: c.companion }))]);
  ctx.defer(notifyMembers(ctx, circleId, ctx.uid, { title: circle.name, body: `${member.displayName} has something to celebrate`, data: { kind: "celebration", circleId, messageId } }));
  return { messageId };
}

/** Report a chat message. Like post reports, the reporter is stored only as a keyed hash. */
async function reportMessage(ctx) {
  const circleId = str(ctx.body, "circleId"); const messageId = str(ctx.body, "messageId");
  await load(ctx, circleId);
  const m = await ctx.db.get(`${C(circleId)}/messages/${messageId}`);
  if (!m || m.data.deleted) throw notFound("That message");
  const reason = cleanText(ctx.body.reason, "reason", { min: 0, max: 500, optional: true }) ?? "";
  await hit(ctx.kv, `rl:report:${ctx.uid}`, RATE.report);
  const h = (await hmacHex(parseServiceAccount(ctx.env).private_key, `${ctx.uid}:${messageId}`)).slice(0, 24);
  try {
    await ctx.db.commit([w.create(`${C(circleId)}/reports/m_${messageId}_${h}`, { messageId, reason, createdAt: ctx.now })]);
  } catch (e) {
    if (!(e instanceof FirestoreError && e.code === "ALREADY_EXISTS")) throw e;
  }
  return {};
}

// ---------- devices ----------
function token(b) {
  const t = b.token;
  if (typeof t !== "string" || !/^[A-Za-z0-9_:.\-]{20,4096}$/.test(t)) throw new ApiError(400, "bad_request", "Invalid token");
  return t;
}
async function registerToken(ctx) {
  const t = token(ctx.body);
  await ctx.db.runTx(async (tx) => {
    const cur = (await tx.get(`users/${ctx.uid}`))?.data.fcmTokens || [];
    return [w.update(`users/${ctx.uid}`, { fcmTokens: [t, ...cur.filter((x) => x !== t)].slice(0, LIMITS.maxTokensPerUser) }, { mustExist: false })];
  });
  return {};
}
async function unregisterToken(ctx) {
  const t = token(ctx.body);
  await ctx.db.commit([w.update(`users/${ctx.uid}`, {}, { removeFromArray: { fcmTokens: [t] }, mustExist: false })]);
  return {};
}

export const HANDLERS = {
  createCircle, updateCircle, createInvite, revokeInvite, join, leave, removeMember, setRole, setMute,
  createPost, approvePost, rejectPost, editPost, addUpdate, markAnswered, deletePost,
  prayed, react, syncCounts, report, registerToken, unregisterToken,
  createPoll, closePoll, startChain, celebrate, myCircles, reportMessage,
};
export { POST_TYPES };
