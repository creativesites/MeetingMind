// Service-account OAuth2 (JWT bearer) with an in-isolate cached access token.
import { b64urlEncode, importPkcs8, signRs256 } from "./crypto.js";

const SCOPES = "https://www.googleapis.com/auth/datastore https://www.googleapis.com/auth/firebase.messaging";
let cached = { token: null, exp: 0, inflight: null };

export function parseServiceAccount(env) {
  let sa;
  try { sa = JSON.parse(env.SERVICE_ACCOUNT_JSON || ""); } catch { throw new Error("SERVICE_ACCOUNT_JSON is missing or not valid JSON"); }
  if (!sa.client_email || !sa.private_key) throw new Error("service account JSON lacks client_email/private_key");
  return sa;
}

export async function getAccessToken(env, fetchImpl = fetch) {
  const nowMs = Date.now();
  if (cached.token && nowMs < cached.exp - 60_000) return cached.token;
  if (cached.inflight) return cached.inflight;
  cached.inflight = (async () => {
    try {
      const sa = parseServiceAccount(env);
      const iat = Math.floor(Date.now() / 1000);
      const head = b64urlEncode(JSON.stringify({ alg: "RS256", typ: "JWT", kid: sa.private_key_id }));
      const claims = b64urlEncode(JSON.stringify({ iss: sa.client_email, scope: SCOPES, aud: "https://oauth2.googleapis.com/token", iat, exp: iat + 3600 }));
      const sig = await signRs256(await importPkcs8(sa.private_key), `${head}.${claims}`);
      const res = await fetchImpl("https://oauth2.googleapis.com/token", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: `grant_type=${encodeURIComponent("urn:ietf:params:oauth:grant-type:jwt-bearer")}&assertion=${head}.${claims}.${sig}`,
      });
      if (!res.ok) throw new Error(`token exchange failed: ${res.status}`);
      const j = await res.json();
      cached.token = j.access_token;
      cached.exp = Date.now() + (j.expires_in || 3600) * 1000;
      return cached.token;
    } finally { cached.inflight = null; }
  })();
  return cached.inflight;
}
