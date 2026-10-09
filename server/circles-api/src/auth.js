// Firebase ID token verification (RS256 against Google's securetoken keys).
import { b64urlDecode, importJwk, verifyRs256 } from "./crypto.js";
import { ApiError } from "./errors.js";

const JWKS_URL = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";
const dec = new TextDecoder();

/** Pure claim checks. Throws ApiError(401) on any problem; returns the uid. */
export function validateClaims(header, payload, { projectId, now = Math.floor(Date.now() / 1000), skew = 60 }) {
  const bad = (why) => new ApiError(401, "unauthorized", `Invalid token (${why})`);
  if (!projectId) throw new ApiError(500, "misconfigured", "Server misconfigured");
  if (!header || header.alg !== "RS256") throw bad("alg");
  if (typeof header.kid !== "string" || !header.kid) throw bad("kid");
  if (!payload || typeof payload !== "object") throw bad("payload");
  if (payload.aud !== projectId) throw bad("aud");
  if (payload.iss !== `https://securetoken.google.com/${projectId}`) throw bad("iss");
  if (typeof payload.exp !== "number" || payload.exp <= now - 0) throw bad("exp");
  if (typeof payload.iat !== "number" || payload.iat > now + skew) throw bad("iat");
  if (typeof payload.auth_time === "number" && payload.auth_time > now + skew) throw bad("auth_time");
  if (typeof payload.sub !== "string" || payload.sub.length === 0 || payload.sub.length > 128) throw bad("sub");
  return payload.sub;
}

function parseJson(bytes) {
  try { return JSON.parse(dec.decode(bytes)); } catch { return null; }
}

/**
 * Verify a Firebase ID token. getKey(kid) -> CryptoKey | null (injected so tests can use a local key).
 * Returns { uid, claims }.
 */
export async function verifyIdToken(token, { projectId, getKey, now }) {
  if (typeof token !== "string" || token.length > 4096) throw new ApiError(401, "unauthorized", "Missing token");
  const parts = token.split(".");
  if (parts.length !== 3) throw new ApiError(401, "unauthorized", "Invalid token");
  let header, payload;
  try {
    header = parseJson(b64urlDecode(parts[0]));
    payload = parseJson(b64urlDecode(parts[1]));
  } catch { throw new ApiError(401, "unauthorized", "Invalid token"); }
  // Header/alg checked before any key lookup: rejects alg=none/HS256 confusion.
  if (!header || header.alg !== "RS256" || typeof header.kid !== "string") throw new ApiError(401, "unauthorized", "Invalid token");
  const key = await getKey(header.kid);
  if (!key) throw new ApiError(401, "unauthorized", "Invalid token");
  let ok = false;
  try { ok = await verifyRs256(key, `${parts[0]}.${parts[1]}`, parts[2]); } catch { ok = false; }
  if (!ok) throw new ApiError(401, "unauthorized", "Invalid token");
  const uid = validateClaims(header, payload, { projectId, now });
  return { uid, claims: payload };
}

// ---- Google key cache (honours Cache-Control max-age) ----
let cache = { keys: new Map(), expires: 0, lastRefetch: 0 };

export function resetKeyCache() { cache = { keys: new Map(), expires: 0, lastRefetch: 0 }; }

export function maxAge(cacheControl) {
  const m = /max-age=(\d+)/.exec(cacheControl || "");
  return m ? parseInt(m[1], 10) : 300;
}

async function refresh(fetchImpl, nowMs) {
  const res = await fetchImpl(JWKS_URL);
  if (!res.ok) throw new Error("jwks fetch failed");
  const { keys } = await res.json();
  const map = new Map();
  for (const jwk of keys || []) {
    if (jwk.kid && jwk.kty === "RSA") map.set(jwk.kid, await importJwk({ ...jwk, alg: "RS256", ext: true }));
  }
  cache = { keys: map, expires: nowMs + maxAge(res.headers.get("Cache-Control")) * 1000, lastRefetch: nowMs };
}

export function googleKeyGetter(fetchImpl = fetch) {
  return async (kid) => {
    const nowMs = Date.now();
    if (nowMs >= cache.expires) await refresh(fetchImpl, nowMs);
    let key = cache.keys.get(kid);
    // Unknown kid: refetch at most once a minute (prevents forced-refetch DoS).
    if (!key && nowMs - cache.lastRefetch > 60_000) {
      await refresh(fetchImpl, nowMs);
      key = cache.keys.get(kid);
    }
    return key || null;
  };
}

export async function authenticate(request, env) {
  const h = request.headers.get("Authorization") || "";
  const m = /^Bearer ([A-Za-z0-9._-]+)$/.exec(h);
  if (!m) throw new ApiError(401, "unauthorized", "Missing token");
  return verifyIdToken(m[1], { projectId: env.FIREBASE_PROJECT_ID, getKey: googleKeyGetter() });
}
