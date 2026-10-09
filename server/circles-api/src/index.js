// MeetingMind Circles API (Cloudflare Worker). See README.md.
import { authenticate } from "./auth.js";
import { ApiError } from "./errors.js";
import { Firestore } from "./firestore.js";
import { Fcm } from "./fcm.js";
import { HANDLERS } from "./handlers.js";
import { runDigest } from "./digest.js";

const MAX_BODY = 32 * 1024;
const json = (obj, status = 200) => new Response(JSON.stringify(obj), { status, headers: { "Content-Type": "application/json", "Cache-Control": "no-store" } });
const err = (status, code, message) => json({ ok: false, error: { code, message } }, status);

function services(env) {
  const projectId = env.FIREBASE_PROJECT_ID;
  return { db: new Firestore({ projectId, env }), fcm: new Fcm({ projectId, env }) };
}

export async function handle(request, env, ctxExec, deps = {}) {
  const url = new URL(request.url);
  const m = /^\/v1\/([A-Za-z]+)$/.exec(url.pathname);
  const handler = m && Object.hasOwn(HANDLERS, m[1]) ? HANDLERS[m[1]] : null;
  if (request.method !== "POST" || !handler) return err(404, "not_found", "Not found");
  try {
    const { uid } = await (deps.authenticate || authenticate)(request, env);
    const len = parseInt(request.headers.get("Content-Length") || "0", 10);
    if (len > MAX_BODY) return err(413, "too_large", "Request too large");
    const text = await request.text();
    if (text.length > MAX_BODY) return err(413, "too_large", "Request too large");
    let body;
    try { body = text ? JSON.parse(text) : {}; } catch { return err(400, "bad_request", "Bad JSON"); }
    if (!body || typeof body !== "object" || Array.isArray(body)) return err(400, "bad_request", "Bad JSON");
    const { db, fcm } = deps.services || services(env);
    const ctx = {
      env, db, fcm, kv: env.RATE, uid, body, now: new Date(),
      ip: request.headers.get("CF-Connecting-IP") || "unknown",
      defer: (p) => ctxExec.waitUntil(Promise.resolve(p).catch((e) => console.error("deferred", e?.message))),
    };
    const result = await handler(ctx);
    return json({ ok: true, ...result });
  } catch (e) {
    if (e instanceof ApiError) return err(e.status, e.code, e.message);
    console.error("internal", e?.message); // never echo internals to the client
    return err(500, "internal", "Something went wrong. Please try again.");
  }
}

export default {
  fetch: (request, env, ctx) => handle(request, env, ctx),
  async scheduled(_event, env, ctx) {
    ctx.waitUntil(runDigest({ ...services(env), env }).catch((e) => console.error("digest", e?.message)));
  },
};
