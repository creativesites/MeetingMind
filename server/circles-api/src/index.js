// MeetingMind Circles API (Cloudflare Worker). See README.md.
import { authenticate } from "./auth.js";
import { ApiError } from "./errors.js";
import { Firestore } from "./firestore.js";
import { Fcm } from "./fcm.js";
import { HANDLERS } from "./handlers.js";
import { runDigest } from "./digest.js";
import { parseServiceAccount } from "./google.js";

const MAX_BODY = 32 * 1024;
const json = (obj, status = 200, requestId) => new Response(JSON.stringify({ ...obj, requestId }), { status, headers: { "Content-Type": "application/json", "Cache-Control": "no-store", "X-Request-Id": requestId } });
const err = (status, code, message, requestId) => json({ ok: false, error: { code, message } }, status, requestId);
function safeErrorDetail(value) {
  return String(value || "unknown")
    .replace(/Bearer\s+\S+/gi, "Bearer [redacted]")
    .replace(/-----BEGIN [^-]+-----[\s\S]*?-----END [^-]+-----/g, "[redacted PEM]")
    .replace(/\b[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}\b/g, "[redacted token]")
    .replace(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/gi, "[redacted email]")
    .slice(0, 180);
}

function services(env) {
  const projectId = env.FIREBASE_PROJECT_ID;
  return { db: new Firestore({ projectId, env }), fcm: new Fcm({ projectId, env }) };
}

export async function handle(request, env, ctxExec, deps = {}) {
  const requestId = request.headers.get("CF-Ray") || crypto.randomUUID();
  const url = new URL(request.url);
  if (request.method === "GET" && url.pathname === "/health") return json({ ok: true, status: "ok" }, 200, requestId);
  if (request.method === "GET" && url.pathname === "/ready") {
    try { parseServiceAccount(env); return json({ ok: true, status: "ready" }, 200, requestId); }
    catch (e) {
      console.error(JSON.stringify({ event: "circles_readiness_failed", requestId, layer: "configuration", reason: /lacks client_email/.test(e?.message || "") ? "missing_service_account_fields" : "invalid_service_account_json" }));
      return err(503, "service_misconfigured", "Circles service is temporarily unavailable.", requestId);
    }
  }
  const m = /^\/v1\/([A-Za-z]+)$/.exec(url.pathname);
  const handler = m && Object.hasOwn(HANDLERS, m[1]) ? HANDLERS[m[1]] : null;
  if (request.method !== "POST" || !handler) return err(404, "not_found", "Not found", requestId);
  try {
    const { uid } = await (deps.authenticate || authenticate)(request, env);
    const len = parseInt(request.headers.get("Content-Length") || "0", 10);
    if (len > MAX_BODY) return err(413, "too_large", "Request too large", requestId);
    const text = await request.text();
    if (text.length > MAX_BODY) return err(413, "too_large", "Request too large", requestId);
    let body;
    try { body = text ? JSON.parse(text) : {}; } catch { return err(400, "bad_request", "Bad JSON", requestId); }
    if (!body || typeof body !== "object" || Array.isArray(body)) return err(400, "bad_request", "Bad JSON", requestId);
    const { db, fcm } = deps.services || services(env);
    const ctx = {
      env, db, fcm, kv: env.RATE, uid, body, now: new Date(), requestId,
      ip: request.headers.get("CF-Connecting-IP") || "unknown",
      defer: (p) => ctxExec.waitUntil(Promise.resolve(p).catch((e) => console.error("deferred", e?.message))),
    };
    const result = await handler(ctx);
    return json({ ok: true, ...result }, 200, requestId);
  } catch (e) {
    if (e instanceof ApiError) return err(e.status, e.code, e.message, requestId);
    const configFailure = /SERVICE_ACCOUNT_JSON|service account JSON/i.test(e?.message || "");
    const configReason = /lacks client_email/.test(e?.message || "") ? "missing_service_account_fields" : "invalid_service_account_json";
    console.error(JSON.stringify({ event: "circles_request_failed", requestId, operation: m?.[1] || "unknown", layer: configFailure ? "configuration" : "unhandled", reason: configFailure ? configReason : (e?.name || "Error"), detail: safeErrorDetail(e?.message) }));
    return configFailure
      ? err(503, "service_misconfigured", "Circles service is temporarily unavailable.", requestId)
      : err(500, "internal", "Something went wrong. Please try again.", requestId);
  }
}

export default {
  fetch: (request, env, ctx) => handle(request, env, ctx),
  async scheduled(_event, env, ctx) {
    ctx.waitUntil(runDigest({ ...services(env), env }).catch((e) => console.error("digest", e?.message)));
  },
};
