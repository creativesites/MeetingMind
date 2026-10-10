// Pure logic: validation, codes, caps, post shaping. No I/O, fully unit-tested.
import { ApiError } from "./errors.js";

export const LIMITS = { membersPerCircle: 50, circlesPerUser: 10, bodyMax: 4000, commentMax: 2000, nameMax: 60, maxTokensPerUser: 5, maxUpdates: 50 };
export const POST_TYPES = ["prayer", "testimony", "achievement", "study", "encouragement", "reading", "announcement"];
export const REACTIONS = ["praying", "amen", "heart", "celebrate"];
const ALL = POST_TYPES;
export const TEMPLATES = {
  small_group: ["prayer", "testimony", "study", "encouragement"],
  prayer_partners: ["prayer", "encouragement"],
  bible_study: ["study", "reading", "prayer"],
  family: ["prayer", "testimony", "encouragement", "achievement"],
  youth: ALL,
  ministry_team: ["prayer", "announcement", "study"],
  custom: ["prayer", "testimony", "encouragement"],
};

const bad = (code, msg) => new ApiError(400, code, msg);

// ---- ids & strings ----
export function validId(v, field = "id") {
  if (typeof v !== "string" || !/^[A-Za-z0-9_-]{1,40}$/.test(v)) throw bad("bad_request", `Invalid ${field}`);
  return v;
}
export function cleanText(v, field, { min = 1, max, optional = false } = {}) {
  if ((v === undefined || v === null || v === "") && optional) return null;
  if (typeof v !== "string") throw bad("bad_request", `${field} must be text`);
  // strip control chars except newline/tab
  const s = v.replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g, "").trim();
  if (s.length < min) throw bad("bad_request", `${field} is too short`);
  if (s.length > max) throw bad("bad_request", `${field} is too long`);
  return s;
}
export function cleanVerseRef(v) {
  if (v === undefined || v === null || v === "") return null;
  if (typeof v !== "string" || !/^[\p{L}\p{N} .:,;\-–]{1,64}$/u.test(v.trim())) throw bad("bad_request", "Invalid verse reference");
  return v.trim();
}

// ---- settings ----
export function normalizeSettings(input, template) {
  const s = input && typeof input === "object" && !Array.isArray(input) ? input : {};
  const tpl = TEMPLATES[template];
  if (!tpl) throw bad("bad_request", "Unknown template");
  const out = { allowedTypes: [...tpl], whoCanInvite: "admins", prayerApproval: true, reactions: [...REACTIONS], guidelines: "" };
  if (s.allowedTypes !== undefined) {
    if (!Array.isArray(s.allowedTypes) || s.allowedTypes.length === 0 || s.allowedTypes.some((t) => !POST_TYPES.includes(t))) throw bad("bad_request", "Invalid post types");
    out.allowedTypes = [...new Set(s.allowedTypes)];
  }
  if (s.whoCanInvite !== undefined) {
    if (!["admins", "members"].includes(s.whoCanInvite)) throw bad("bad_request", "Invalid invite setting");
    out.whoCanInvite = s.whoCanInvite;
  }
  if (s.prayerApproval !== undefined) {
    if (typeof s.prayerApproval !== "boolean") throw bad("bad_request", "Invalid approval setting");
    out.prayerApproval = s.prayerApproval;
  }
  if (s.reactions !== undefined) {
    if (!Array.isArray(s.reactions) || s.reactions.length === 0 || s.reactions.some((r) => !REACTIONS.includes(r))) throw bad("bad_request", "Invalid reactions");
    out.reactions = [...new Set(s.reactions)];
  }
  if (s.guidelines !== undefined) out.guidelines = cleanText(s.guidelines, "guidelines", { min: 0, max: 1000 });
  return out;
}

// ---- invite codes ----
export const CODE_WORDS = ["GRACE", "FAITH", "HOPE", "LIGHT", "PEACE", "JOY", "AMEN", "MERCY", "TRUST", "PRAISE", "SHEPHERD", "COVENANT", "ANCHOR", "BEACON", "CEDAR", "DOVE", "EMBER", "FIELD", "GARDEN", "HARVEST", "OLIVE", "RIVER", "ROCK", "SPRING", "STAR", "VINE", "WILLOW", "WINGS", "LAMP", "PATH", "SALT", "SEED"];
// No vowels (no accidental words), no 0/1/I/L/O lookalikes.
export const CODE_ALPHABET = "23456789BCDFGHJKMNPQRSTVWXZ";

export function generateCode(randomBytes = (n) => crypto.getRandomValues(new Uint8Array(n))) {
  const pick = (max) => { // rejection sampling: no modulo bias
    const lim = 256 - (256 % max);
    for (;;) { const b = randomBytes(1)[0]; if (b < lim) return b % max; }
  };
  let suffix = "";
  for (let i = 0; i < 4; i++) suffix += CODE_ALPHABET[pick(CODE_ALPHABET.length)];
  return `${CODE_WORDS[pick(CODE_WORDS.length)]}-${suffix}`;
}

/** Pull candidate codes out of whatever was pasted (whole share message, URL, lowercase...). */
export function extractCodes(text, max = 3) {
  if (typeof text !== "string") return [];
  const re = new RegExp(`(?<![A-Z0-9])([A-Z]{3,10})-([${CODE_ALPHABET}]{4})(?![A-Z0-9])`, "gi");
  const found = [];
  for (const m of text.slice(0, 2000).matchAll(re)) {
    const code = `${m[1]}-${m[2]}`.toUpperCase();
    if (!CODE_WORDS.includes(m[1].toUpperCase())) continue;
    if (!found.includes(code)) found.push(code);
    if (found.length >= max) break;
  }
  return found;
}

export function clampInvite({ expiresInDays, maxUses }) {
  let days = expiresInDays === undefined ? 7 : expiresInDays;
  let uses = maxUses === undefined ? 50 : maxUses;
  if (!Number.isInteger(days) || days < 1 || days > 30) throw bad("bad_request", "expiresInDays must be 1-30");
  if (!Number.isInteger(uses) || uses < 1 || uses > 100) throw bad("bad_request", "maxUses must be 1-100");
  return { days, uses };
}

// ---- caps ----
/** Returns an error code or null. */
export function joinDecision({ invite, nowMs, memberCount, userCircleCount, alreadyMember, circleClosed }) {
  if (!invite || circleClosed) return ["not_found", "That code wasn't found. Check it and try again."];
  if (alreadyMember) return ["already_member", "You're already in this circle."];
  if (invite.revoked) return ["revoked", "That code has been turned off. Ask for a new one."];
  const exp = invite.expiresAt instanceof Date ? invite.expiresAt.getTime() : Number(invite.expiresAt);
  if (!(exp > nowMs)) return ["expired", "That code has expired. Ask for a new one."];
  if (invite.maxUses != null && invite.uses >= invite.maxUses) return ["full", "That code has reached its limit. Ask for a new one."];
  if (memberCount >= LIMITS.membersPerCircle) return ["full", "This circle is full."];
  if (userCircleCount >= LIMITS.circlesPerUser) return ["too_many_circles", "You're in the maximum of 10 circles. Leave one to join another."];
  return null;
}

export function canInvite(role, settings) {
  return role === "owner" || role === "admin" || (role === "member" && settings?.whoCanInvite === "members");
}
export const isAdmin = (role) => role === "owner" || role === "admin";

// ---- rate limits ----
export const RATE = {
  createPost: { limit: 10, ttl: 3600 },
  createCircle: { limit: 5, ttl: 3600 },
  createInvite: { limit: 10, ttl: 3600 },
  report: { limit: 20, ttl: 3600 },
  createPoll: { limit: 10, ttl: 3600 },
  startChain: { limit: 3, ttl: 86_400 },
  celebrate: { limit: 5, ttl: 86_400 },
  comment: { limit: 60, ttl: 3600 },
  joinFailUser: { limit: 10, ttl: 3600 },
  joinFailIp: { limit: 40, ttl: 3600 },
};
export const rateExceeded = (count, limit) => count >= limit;

// ---- post shaping ----
export const hourFloor = (d) => new Date(Math.floor(d.getTime() / 3_600_000) * 3_600_000);

/**
 * Decide where a new post goes and build every doc. The returned `post`/`pending` docs NEVER contain
 * the author's uid or name when anonymous; the uid lives only in `authorDoc` (postAuthors/{id}).
 */
export function shapePost({ type, body, verseRef, anonymous, uid, authorName, settings, now }) {
  if (!POST_TYPES.includes(type)) throw bad("bad_request", "Unknown post type");
  if (!settings.allowedTypes.includes(type)) throw new ApiError(403, "type_not_allowed", "This circle doesn't use that kind of post.");
  if (typeof anonymous !== "boolean") throw bad("bad_request", "anonymous must be true or false");
  if (anonymous && type !== "prayer") throw bad("bad_request", "Only prayer requests can be anonymous");
  const pending = type === "prayer" && settings.prayerApproval !== false;
  const base = {
    type, body, verseRef: verseRef ?? null, anonymous,
    status: "open", deleted: false,
    counts: { prayed: 0, comments: 0, updates: 0, reports: 0, reactions: {} },
    // Anonymous: coarse time, so a post can't be matched to "who was online at 14:03".
    createdAt: anonymous ? hourFloor(now) : now, editedAt: null,
  };
  const post = { ...base };
  if (!anonymous) { post.authorUid = uid; post.authorName = authorName; }
  const authorDoc = { authorUid: uid, anonymous, authorName: anonymous ? null : authorName, createdAt: now };
  return { pending, post, authorDoc };
}

/** Defence in depth: throws if a doc that clients can read carries any identity field. */
export function assertNoAuthorLeak(doc, uid) {
  if ("authorUid" in doc || "authorName" in doc || JSON.stringify(doc).includes(uid)) throw new Error("anonymity leak");
}

/** Publish a pending doc (anonymous stays anonymous; named gets its author from postAuthors). */
export function publishFromPending(pendingDoc, authorDoc, now) {
  const post = { ...pendingDoc, createdAt: pendingDoc.anonymous ? hourFloor(now) : now };
  if (!pendingDoc.anonymous) { post.authorUid = authorDoc.authorUid; post.authorName = authorDoc.authorName; }
  return post;
}

// ---- chat & fun (FAITH_V2 section 2.2b) ----
export const CHAT = { pollQuestionMax: 200, pollOptionMax: 80, pollOptionsMin: 2, pollOptionsMax: 6, chainHours: 24, chainTitleMax: 120, celebrationTextMax: 140 };
export const COMPANIONS = ["zuri", "nas", "wren", "page"];
export const CELEBRATIONS = ["birthday", "answered", "streak", "milestone"];

/** Validate and shape a poll. Returns { question, options:[{id,text}], optionIds, multi }. */
export function shapePoll({ question, options, multi }) {
  const q = cleanText(question, "question", { max: CHAT.pollQuestionMax });
  if (!Array.isArray(options) || options.length < CHAT.pollOptionsMin || options.length > CHAT.pollOptionsMax) {
    throw bad("bad_request", `A poll needs ${CHAT.pollOptionsMin} to ${CHAT.pollOptionsMax} options`);
  }
  const texts = options.map((o) => cleanText(o, "option", { max: CHAT.pollOptionMax }));
  if (new Set(texts.map((t) => t.toLowerCase())).size !== texts.length) throw bad("bad_request", "Options must be different");
  if (multi !== undefined && typeof multi !== "boolean") throw bad("bad_request", "multi must be true or false");
  const opts = texts.map((text, i) => ({ id: `o${i}`, text }));
  return { question: q, options: opts, optionIds: opts.map((o) => o.id), multi: multi === true };
}

/** Validate a celebration. `kind` is one of CELEBRATIONS; companion falls back to null (client picks its own). */
export function shapeCelebration({ kind, text, companion }) {
  if (!CELEBRATIONS.includes(kind)) throw bad("bad_request", "Unknown celebration");
  const t = cleanText(text, "text", { min: 0, max: CHAT.celebrationTextMax, optional: true }) ?? "";
  if (companion !== undefined && companion !== null && !COMPANIONS.includes(companion)) throw bad("bad_request", "Unknown companion");
  return { kind, text: t, companion: companion ?? null };
}
