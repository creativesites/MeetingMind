// Best-effort per-key counters in Workers KV. KV is eventually consistent and not atomic, so this
// stops floods, not a determined attacker with many parallel requests (see README "Residual risks").
import { ApiError } from "./errors.js";

const tooMany = () => new ApiError(429, "rate_limited", "You're doing that too fast. Please wait a bit and try again.");

async function read(kv, key) {
  try { return parseInt((await kv.get(key)) || "0", 10) || 0; } catch { return 0; } // fail open on KV outage
}
async function write(kv, key, n, ttl) {
  try { await kv.put(key, String(n), { expirationTtl: Math.max(60, ttl) }); } catch { /* fail open */ }
}

/** Count one action; throw 429 once over the limit. */
export async function hit(kv, key, rule) {
  const n = await read(kv, key);
  if (n >= rule.limit) throw tooMany();
  await write(kv, key, n + 1, rule.ttl);
}
/** Throw if already over the limit without counting (used before an attempt that may fail). */
export async function check(kv, key, rule) {
  if ((await read(kv, key)) >= rule.limit) throw tooMany();
}
/** Count a failure. */
export async function record(kv, key, rule) {
  await write(kv, key, (await read(kv, key)) + 1, rule.ttl);
}
