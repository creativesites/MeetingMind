// Pure logic: validation, codes, caps, post shaping. No I/O, fully unit-tested.
import { ApiError } from "./errors.js";

export const LIMITS = { membersPerCircle: 50, circlesPerUser: 10, bodyMax: 4000, commentMax: 2000, nameMax: 60, descriptionMax: 300, maxTokensPerUser: 5, maxUpdates: 50 };
// Post types for Faith, Work, Learning, and General verticals. "note" is the universal free-form post.
export const FAITH_POST_TYPES = ["prayer", "testimony", "achievement", "study", "encouragement", "reading", "announcement", "note", "devotional"];
export const WORK_POST_TYPES = ["task", "decision", "brief", "standup", "blocker", "announcement", "note"];
export const LEARNING_POST_TYPES = ["study", "announcement", "encouragement", "achievement", "note"];
export const GENERAL_POST_TYPES = ["achievement", "encouragement", "reading", "announcement", "note"];
export const POST_TYPES = [...new Set([...FAITH_POST_TYPES, ...WORK_POST_TYPES, ...LEARNING_POST_TYPES, ...GENERAL_POST_TYPES])];

export const FAITH_REACTIONS = ["praying", "amen", "heart", "celebrate"];
export const WORK_REACTIONS = ["ack", "approved", "priority", "reviewing", "win"];
export const LEARNING_REACTIONS = ["ack", "heart", "celebrate", "priority"];
export const GENERAL_REACTIONS = ["ack", "heart", "celebrate", "priority"];
export const REACTIONS = [...new Set([...FAITH_REACTIONS, ...WORK_REACTIONS, ...LEARNING_REACTIONS, ...GENERAL_REACTIONS])];

const ALL = POST_TYPES;
export const TEMPLATES = {
  // Faith templates
  small_group: ["prayer", "testimony", "study", "encouragement", "devotional"],
  prayer_partners: ["prayer", "encouragement", "devotional"],
  bible_study: ["study", "reading", "prayer", "devotional"],
  family: ["prayer", "testimony", "encouragement", "achievement", "devotional"],
  youth: FAITH_POST_TYPES,
  ministry_team: ["prayer", "announcement", "study", "devotional"],
  custom: ["prayer", "testimony", "encouragement", "devotional"],
  // Work templates
  project_team: ["task", "decision", "brief", "standup", "blocker", "announcement", "note"],
  client_engagement: ["decision", "brief", "task", "blocker", "announcement", "note"],
  exec_board: ["decision", "brief", "announcement", "note"],
  department: ["announcement", "standup", "task", "decision", "blocker", "note"],
  custom_work: ["task", "decision", "brief", "standup", "blocker", "announcement", "note"],
  // Learning templates
  study_group: ["study", "announcement", "encouragement", "achievement", "note"],
  course_cohort: ["study", "announcement", "note"],
  exam_prep_room: ["study", "announcement", "achievement", "note"],
  custom_learning: ["study", "announcement", "encouragement", "achievement", "note"],
  // General templates
  friends_group: GENERAL_POST_TYPES,
  community_club: GENERAL_POST_TYPES,
  book_club: GENERAL_POST_TYPES,
  custom_general: GENERAL_POST_TYPES,
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
export function normalizeSettings(input, template, space = "faith") {
  const s = input && typeof input === "object" && !Array.isArray(input) ? input : {};
  const tpl = TEMPLATES[template];
  if (!tpl) throw bad("bad_request", "Unknown template");
  const isGeneral = space === "general" || s.space === "general";
  const isWork = space === "work" || s.space === "work";
  const isLearning = space === "learning" || s.space === "learning";
  const defaultReactions = isLearning ? [...LEARNING_REACTIONS] : isGeneral ? [...GENERAL_REACTIONS] : isWork ? [...WORK_REACTIONS] : [...FAITH_REACTIONS];
  // joinApproval defaults off so circles that exist today keep joining as they do.
  const out = {
    allowedTypes: [...tpl],
    whoCanInvite: "admins",
    prayerApproval: (isWork || isLearning || isGeneral) ? false : true,
    joinApproval: false,
    reactions: defaultReactions,
    guidelines: "",
    space: isLearning ? "learning" : isWork ? "work" : isGeneral ? "general" : "faith"
  };
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
  if (s.joinApproval !== undefined) {
    if (typeof s.joinApproval !== "boolean") throw bad("bad_request", "Invalid join approval setting");
    out.joinApproval = s.joinApproval;
  }
  if (s.reactions !== undefined) {
    if (!Array.isArray(s.reactions) || s.reactions.length === 0 || s.reactions.some((r) => !REACTIONS.includes(r))) throw bad("bad_request", "Invalid reactions");
    out.reactions = [...new Set(s.reactions)];
  }
  if (s.guidelines !== undefined) out.guidelines = cleanText(s.guidelines, "guidelines", { min: 0, max: 1000 });
  return out;
}

// ---- invite codes ----
export const FAITH_CODE_WORDS = ["GRACE", "FAITH", "HOPE", "LIGHT", "PEACE", "JOY", "AMEN", "MERCY", "TRUST", "PRAISE", "SHEPHERD", "COVENANT", "ANCHOR", "BEACON", "CEDAR", "DOVE", "EMBER", "FIELD", "GARDEN", "HARVEST", "OLIVE", "RIVER", "ROCK", "SPRING", "STAR", "VINE", "WILLOW", "WINGS", "LAMP", "PATH", "SALT", "SEED"];
export const WORK_CODE_WORDS = ["SPRINT", "FOCUS", "LAUNCH", "ALIGN", "IMPACT", "STRIDE", "TALENT", "VISION", "TARGET", "MATRIX", "NEXUS", "VECTOR", "DELTA", "APEX", "SUMMIT", "PULSE", "BRIDGE", "PIVOT", "THRIVE", "FORGE", "CLIMB", "SCALE", "DRIVE", "RESOLVE", "QUEST", "SYNC", "PRISM", "SIGNAL", "CHORD", "SYNERGY"];
export const LEARNING_CODE_WORDS = ["STUDY", "EXAM", "CLASS", "NOTES", "LEARN", "LOGIC", "ACADEMY", "THEORY", "CAMPUS", "SCHOLAR", "DEGREE", "HONOR", "TUTOR", "COURSE", "LECTURE", "THESIS", "REVISION", "RECALL", "FORMULA", "SYNTAX"];
export const GENERAL_CODE_WORDS = ["CIRCLE", "CLUB", "SQUAD", "TRIBE", "CREW", "GUILD", "TABLE", "FORUM", "COMMON", "SPARK", "HARBOR", "HAVEN"];
export const CODE_WORDS = FAITH_CODE_WORDS;
// Templates that make a work circle when a circle has no explicit space (see isWorkCircle).
export const WORK_TEMPLATES = ["project_team", "client_engagement", "exec_board", "department", "custom_work"];
/** A decision's status. Anyone may post a new one as proposed or confirmed; "replaced" is set when a newer decision replaces it. */
export const DECISION_STATUSES = ["proposed", "confirmed", "replaced"];
export const CREATE_DECISION_STATUSES = ["proposed", "confirmed"];
/** The Table: a bring list on an event. */
export const BRING = { max: 20, textMax: 80 };
export const ALL_CODE_WORDS = [...new Set([...FAITH_CODE_WORDS, ...WORK_CODE_WORDS, ...LEARNING_CODE_WORDS, ...GENERAL_CODE_WORDS])];
// No vowels (no accidental words), no 0/1/I/L/O lookalikes.
export const CODE_ALPHABET = "23456789BCDFGHJKMNPQRSTVWXZ";

export function generateCode(randomBytes = (n) => crypto.getRandomValues(new Uint8Array(n)), space = "faith") {
  const pick = (max) => { // rejection sampling: no modulo bias
    const lim = 256 - (256 % max);
    for (;;) { const b = randomBytes(1)[0]; if (b < lim) return b % max; }
  };
  const words = space === "learning" ? LEARNING_CODE_WORDS : space === "work" ? WORK_CODE_WORDS : space === "general" ? GENERAL_CODE_WORDS : FAITH_CODE_WORDS;
  let suffix = "";
  for (let i = 0; i < 4; i++) suffix += CODE_ALPHABET[pick(CODE_ALPHABET.length)];
  return `${words[pick(words.length)]}-${suffix}`;
}

/** Pull candidate codes out of whatever was pasted (whole share message, URL, lowercase...). */
export function extractCodes(text, max = 3) {
  if (typeof text !== "string") return [];
  const re = new RegExp(`(?<![A-Z0-9])([A-Z]{3,10})-([${CODE_ALPHABET}]{4})(?![A-Z0-9])`, "gi");
  const found = [];
  for (const m of text.slice(0, 2000).matchAll(re)) {
    const code = `${m[1]}-${m[2]}`.toUpperCase();
    if (!ALL_CODE_WORDS.includes(m[1].toUpperCase())) continue;
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
/**
 * Moderator sits between admin and member: creates, edits and cancels events, and removes other people's posts.
 * Owners and admins can do everything a moderator can. Titles are a label and never grant anything.
 */
export const canModerate = (role) => isAdmin(role) || role === "moderator";

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

export function cleanPostMeta(meta) {
  if (!meta || typeof meta !== "object" || Array.isArray(meta)) return null;
  const out = {};
  if (typeof meta.sourceKind === "string") out.sourceKind = meta.sourceKind.slice(0, 32);
  if (typeof meta.sourceId === "string") out.sourceId = meta.sourceId.slice(0, 64);
  if (typeof meta.assigneeName === "string") out.assigneeName = meta.assigneeName.slice(0, 60);
  if (typeof meta.dueText === "string") out.dueText = meta.dueText.slice(0, 40);
  if (typeof meta.assigneeUid === "string") {
    const s = meta.assigneeUid.trim();
    if (s.length > 0) out.assigneeUid = s.slice(0, 64);
  }
  if (Number.isSafeInteger(meta.dueAt) && meta.dueAt > 0) {
    out.dueAt = meta.dueAt;
  }
  if (typeof meta.priority === "string") {
    const p = meta.priority.trim().toLowerCase();
    if (["low", "normal", "high", "urgent"].includes(p)) {
      out.priority = p;
    }
  }
  if (typeof meta.blockedOn === "string") {
    const s = meta.blockedOn.trim();
    if (s.length > 0) out.blockedOn = s.slice(0, 80);
  }
  if (typeof meta.decisionOwner === "string") {
    const s = meta.decisionOwner.trim();
    if (s.length > 0) out.decisionOwner = s.slice(0, 60);
  }
  if (typeof meta.meetingDate === "string") {
    const s = meta.meetingDate.trim();
    if (s.length > 0) out.meetingDate = s.slice(0, 40);
  }
  if (meta.decisionStatus !== undefined && meta.decisionStatus !== null) {
    if (!CREATE_DECISION_STATUSES.includes(meta.decisionStatus)) throw bad("bad_request", "Invalid decision status");
    out.decisionStatus = meta.decisionStatus;
  }
  if (typeof meta.replacesPostId === "string" && meta.replacesPostId.trim()) {
    out.replacesPostId = validId(meta.replacesPostId.trim(), "replacesPostId");
  }
  if (Array.isArray(meta.tags)) {
    const seen = new Set();
    const tags = [];
    for (const item of meta.tags) {
      if (typeof item !== "string") continue;
      let s = item.trim();
      while (s.startsWith("#")) s = s.slice(1).trim();
      s = s.slice(0, 24);
      if (!s) continue;
      const lower = s.toLowerCase();
      if (seen.has(lower)) continue;
      seen.add(lower);
      tags.push(s);
      if (tags.length === 4) break;
    }
    if (tags.length > 0) out.tags = tags;
  }
  if (Array.isArray(meta.checklist)) {
    const checklist = [];
    let idx = 0;
    for (const item of meta.checklist) {
      if (!item || typeof item !== "object") continue;
      const text = typeof item.text === "string" ? item.text.trim().slice(0, 120) : "";
      if (!text) continue;
      let id = typeof item.id === "string" ? item.id.trim().slice(0, 32) : "";
      if (!id) id = `c${idx}`;
      const done = item.done === true;
      checklist.push({ id, text, done });
      idx++;
      if (checklist.length === 8) break;
    }
    if (checklist.length > 0) out.checklist = checklist;
  }
  return Object.keys(out).length > 0 ? out : null;
}

/**
 * Decide where a new post goes and build every doc. The returned `post`/`pending` docs NEVER contain
 * the author's uid or name when anonymous; the uid lives only in `authorDoc` (postAuthors/{id}).
 */
export function shapePost({ type, body, verseRef, anonymous, uid, authorName, settings, now, meta }) {
  if (!POST_TYPES.includes(type)) throw bad("bad_request", "Unknown post type");
  if (type !== "note" && !settings.allowedTypes.includes(type)) throw new ApiError(403, "type_not_allowed", "This circle doesn't use that kind of post.");
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
  const cleanMeta = cleanPostMeta(meta);
  if (cleanMeta) {
    if (type !== "decision" && (cleanMeta.decisionStatus !== undefined || cleanMeta.replacesPostId !== undefined)) {
      throw bad("bad_request", "Only decisions can have a status or replace another decision.");
    }
    post.meta = cleanMeta;
  }
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

/** A work circle: its space is "work"; with no space on the circle, a work template makes it one. */
export function isWorkCircle(circle) {
  const space = circle?.space || circle?.settings?.space;
  if (space) return space === "work";
  return WORK_TEMPLATES.includes(circle?.template);
}

const defaultItemId = () => crypto.randomUUID().replace(/-/g, "").slice(0, 12);

/**
 * An event's bring list (The Table), at most BRING.max items of at most BRING.textMax characters.
 * Items arrive as plain strings (what the app sends) or as {id, text}. A string keeps the id and claim of an existing
 * item with the same text; an object keeps them when its id matches an existing item and its text is unchanged.
 * Claims survive only on kept items. Every other item gets a fresh id. Blank items are dropped.
 */
export function cleanBringList(input, existing = [], newId = defaultItemId) {
  if (input === undefined || input === null) return [];
  if (!Array.isArray(input)) throw bad("bad_request", "The bring list must be a list.");
  if (input.length > BRING.max) throw bad("bad_request", `A bring list can have at most ${BRING.max} items.`);
  const prior = Array.isArray(existing) ? existing : [];
  const usedIds = new Set();
  const out = [];
  for (const item of input) {
    const isObj = item !== null && typeof item === "object" && !Array.isArray(item);
    const text = cleanText(isObj ? item.text : item, "bring item", { min: 0, max: BRING.textMax });
    if (!text) continue;
    const keep = isObj
      ? (typeof item.id === "string" ? prior.find((p) => p.id === item.id) : undefined)
      : prior.find((p) => p.text === text && !usedIds.has(p.id));
    let id = keep && !usedIds.has(keep.id) ? keep.id : null;
    while (!id || usedIds.has(id)) id = newId();
    usedIds.add(id);
    const row = { id, text };
    if (keep && keep.id === id && keep.text === text && keep.claimedByUid) {
      row.claimedByUid = keep.claimedByUid;
      row.claimedByName = keep.claimedByName ?? "";
    }
    out.push(row);
  }
  return out;
}

// ---- chat & fun (FAITH_V2 section 2.2b) ----
export const EVENT = { titleMax: 80, descriptionMax: 1000, locationMax: 200, urlMax: 300, timeZoneMax: 64 };
export const RSVP_STATES = ["going", "maybe", "no"];
export const TITLE_MAX = 40;

/** A display title ("Pastor", "Deacon"). Null clears it. It never changes what the person may do. */
export function cleanTitle(v) {
  if (v === null || v === undefined || v === "") return null;
  return cleanText(v, "title", { max: TITLE_MAX });
}

function cleanUrl(v) {
  if (v === null || v === undefined || v === "") return null;
  if (typeof v !== "string" || v.trim().length > EVENT.urlMax) throw bad("bad_request", "The online link is too long");
  let u;
  try { u = new URL(v.trim()); } catch { throw bad("bad_request", "The online link isn't a web address"); }
  if (u.protocol !== "https:") throw bad("bad_request", "The online link must start with https://");
  return u.toString();
}

function checkTimeZone(zone) {
  try { new Intl.DateTimeFormat("en-US", { timeZone: zone }); } catch { throw bad("bad_request", "Unknown time zone"); }
  return zone;
}

/** Event fields from a request. Times are epoch milliseconds in UTC; [timeZone] is the IANA zone to show them in. */
export function cleanEvent(b) {
  const title = cleanText(b.title, "title", { max: EVENT.titleMax });
  const description = cleanText(b.description, "description", { min: 0, max: EVENT.descriptionMax, optional: true }) ?? "";
  const location = cleanText(b.location, "location", { min: 0, max: EVENT.locationMax, optional: true }) ?? "";
  const onlineUrl = cleanUrl(b.onlineUrl);
  if (!Number.isSafeInteger(b.startsAt) || !Number.isSafeInteger(b.endsAt)) throw bad("bad_request", "Start and end must be times");
  if (b.endsAt <= b.startsAt) throw bad("bad_request", "The event must end after it starts");
  const timeZone = checkTimeZone(cleanText(b.timeZone, "timeZone", { max: EVENT.timeZoneMax }));
  return { title, description, location, onlineUrl, startsAt: b.startsAt, endsAt: b.endsAt, timeZone };
}

export const CHAT = { pollQuestionMax: 200, pollOptionMax: 80, pollOptionsMin: 2, pollOptionsMax: 6, chainHours: 24, chainTitleMax: 120, celebrationTextMax: 140 };
export const COMPANIONS = ["zuri", "nas", "wren", "page"];
export const CELEBRATIONS = ["birthday", "answered", "streak", "milestone"];

/** Validate and shape a poll. Returns { question, options:[{id,text}], optionIds, multi, closesAt?, correctOptionId?, explanation? }. */
export function shapePoll({ question, options, multi, closesAt, correctOptionId, correctIndex, explanation }) {
  const q = cleanText(question, "question", { max: CHAT.pollQuestionMax });
  if (!Array.isArray(options) || options.length < CHAT.pollOptionsMin || options.length > CHAT.pollOptionsMax) {
    throw bad("bad_request", `A poll needs ${CHAT.pollOptionsMin} to ${CHAT.pollOptionsMax} options`);
  }
  const texts = options.map((o) => cleanText(o, "option", { max: CHAT.pollOptionMax }));
  if (new Set(texts.map((t) => t.toLowerCase())).size !== texts.length) throw bad("bad_request", "Options must be different");
  if (multi !== undefined && typeof multi !== "boolean") throw bad("bad_request", "multi must be true or false");
  if (closesAt !== undefined && closesAt !== null) {
    if (!Number.isSafeInteger(closesAt) || closesAt <= 0) {
      throw bad("bad_request", "Poll deadline must be a valid time.");
    }
  }
  const opts = texts.map((text, i) => ({ id: `o${i}`, text }));
  const res = { question: q, options: opts, optionIds: opts.map((o) => o.id), multi: multi === true };
  if (closesAt !== undefined && closesAt !== null) {
    res.closesAt = closesAt;
  }

  let resolvedCorrectId = null;
  const hasIndex = correctIndex !== undefined && correctIndex !== null;
  const hasId = correctOptionId !== undefined && correctOptionId !== null;

  if (hasIndex) {
    if (Number.isInteger(correctIndex) && correctIndex >= 0 && correctIndex < opts.length) {
      resolvedCorrectId = opts[correctIndex].id;
    } else {
      throw bad("bad_request", "Invalid correctIndex");
    }
  } else if (hasId) {
    if (typeof correctOptionId === "string" && opts.some((o) => o.id === correctOptionId.trim())) {
      resolvedCorrectId = correctOptionId.trim();
    } else {
      throw bad("bad_request", "Invalid correctOptionId");
    }
  }

  if (resolvedCorrectId) {
    if (res.multi) throw bad("bad_request", "Quiz polls cannot be multiple choice");
    res.correctOptionId = resolvedCorrectId;
  }

  if (explanation !== undefined && explanation !== null) {
    if (typeof explanation === "string") {
      const expTrimmed = explanation.trim();
      if (expTrimmed.length > 0) {
        res.explanation = cleanText(explanation, "explanation", { min: 1, max: 240 });
      }
    } else {
      throw bad("bad_request", "Invalid explanation");
    }
  }

  return res;
}

/** Validate a celebration. `kind` is one of CELEBRATIONS; companion falls back to null (client picks its own). */
export function shapeCelebration({ kind, text, companion }) {
  if (!CELEBRATIONS.includes(kind)) throw bad("bad_request", "Unknown celebration");
  const t = cleanText(text, "text", { min: 0, max: CHAT.celebrationTextMax, optional: true }) ?? "";
  if (companion !== undefined && companion !== null && !COMPANIONS.includes(companion)) throw bad("bad_request", "Unknown companion");
  return { kind, text: t, companion: companion ?? null };
}
