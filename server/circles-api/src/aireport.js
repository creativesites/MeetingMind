// Reports of AI-written content from the app (Google Play's AI-Generated Content policy).
// Design: design/ai-content-reporting.md. No account is needed: the app sends only the flagged
// passage, the reason, an optional comment, which feature wrote it, the model and the app version.
import { ApiError } from "./errors.js";
import { FirestoreError, w } from "./firestore.js";
import { hmacHex } from "./crypto.js";
import { parseServiceAccount } from "./google.js";
import { hit } from "./ratelimit.js";

// Must match AiFeature.wire and AiReportReason.wire in the app (core/aireport/AiReport.kt).
export const AI_FEATURES = new Set([
  "note_block", "lecture_notes", "meeting_summary", "ask_recording", "ask_learning", "practice", "assistant",
  "ask_everything", "work_ask", "work_brief", "work_pulse", "deep_synthesis", "devotional", "prayer", "sermon",
  "bible_study", "circle_brief", "home_story", "youtube_import",
]);
export const AI_REASONS = new Set(["offensive", "harmful", "misleading", "other"]);
export const AI_REPORT_KEYS = new Set(["reportId", "feature", "reason", "comment", "text", "model", "appVersion", "createdAt"]);
export const AI_REPORT_LIMITS = { text: 4000, comment: 500, model: 80, appVersion: 40 };
export const AI_REPORT_RATE = { limit: 20, ttl: 3600 };

const bad = (msg) => new ApiError(400, "bad_report", msg);
const ID = /^[A-Za-z0-9-]{8,64}$/;

function boundedString(v, key, max, { optional = false } = {}) {
  if (v === undefined || v === null) {
    if (optional) return undefined;
    throw bad(`${key} is required.`);
  }
  if (typeof v !== "string") throw bad(`${key} must be text.`);
  const s = v.trim();
  if (!s && !optional) throw bad(`${key} is required.`);
  if (s.length > max) throw bad(`${key} is too long.`);
  return s || undefined;
}

/** The stored report, or a 400. Unknown keys are refused, so nothing else about a note can arrive. */
export function validateAiReport(body) {
  if (!body || typeof body !== "object" || Array.isArray(body)) throw bad("Bad report.");
  for (const k of Object.keys(body)) if (!AI_REPORT_KEYS.has(k)) throw bad(`Unexpected field: ${k}.`);
  const reportId = boundedString(body.reportId, "reportId", 64);
  if (!ID.test(reportId)) throw bad("Bad reportId.");
  const feature = boundedString(body.feature, "feature", 40);
  if (!AI_FEATURES.has(feature)) throw bad("Unknown feature.");
  const reason = boundedString(body.reason, "reason", 20);
  if (!AI_REASONS.has(reason)) throw bad("Unknown reason.");
  const createdAt = body.createdAt;
  if (!Number.isFinite(createdAt) || createdAt <= 0) throw bad("Bad createdAt.");
  const doc = {
    feature,
    reason,
    text: boundedString(body.text, "text", AI_REPORT_LIMITS.text),
    model: boundedString(body.model, "model", AI_REPORT_LIMITS.model),
    appVersion: boundedString(body.appVersion, "appVersion", AI_REPORT_LIMITS.appVersion),
    clientCreatedAt: Math.floor(createdAt),
  };
  const comment = boundedString(body.comment, "comment", AI_REPORT_LIMITS.comment, { optional: true });
  if (comment !== undefined) doc.comment = comment;
  return { reportId, doc };
}

/**
 * POST /v1/reportAiContent. Rate-limited per caller by a keyed hash of the IP; the IP itself is
 * never stored. Stored in `aiReports/{reportId}`, which no client rule exposes. A retry is stored once.
 */
export async function reportAiContent(ctx) {
  const { reportId, doc } = validateAiReport(ctx.body);
  const ipKey = (await hmacHex(parseServiceAccount(ctx.env).private_key, `aireport:${ctx.ip}`)).slice(0, 24);
  await hit(ctx.kv, `rl:aireport:${ipKey}`, AI_REPORT_RATE);
  try {
    await ctx.db.commit([w.create(`aiReports/${reportId}`, { ...doc, receivedAt: ctx.now })]);
  } catch (e) {
    if (!(e instanceof FirestoreError && e.code === "ALREADY_EXISTS")) throw e;
  }
  return {};
}
