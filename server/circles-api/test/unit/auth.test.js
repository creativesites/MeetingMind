import test from "node:test";
import assert from "node:assert/strict";
import { verifyIdToken, validateClaims, maxAge } from "../../src/auth.js";
import { b64urlEncode } from "../../src/crypto.js";

const P = "demo-proj";
const kp = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
const other = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
const now = 1_800_000_000;

async function mint(over = {}, header = {}, key = kp.privateKey) {
  const h = b64urlEncode(JSON.stringify({ alg: "RS256", kid: "k1", typ: "JWT", ...header }));
  const p = b64urlEncode(JSON.stringify({ aud: P, iss: `https://securetoken.google.com/${P}`, sub: "user-1", iat: now - 10, exp: now + 3000, auth_time: now - 10, firebase: { sign_in_provider: "anonymous" }, ...over }));
  const sig = b64urlEncode(new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(`${h}.${p}`))));
  return `${h}.${p}.${sig}`;
}
const getKey = async (kid) => (kid === "k1" ? kp.publicKey : null);
const verify = (t) => verifyIdToken(t, { projectId: P, getKey, now });
const rejects = (t) => assert.rejects(verify(t), (e) => e.status === 401);

test("valid anonymous token accepted", async () => {
  const r = await verify(await mint());
  assert.equal(r.uid, "user-1");
});
test("rejects wrong aud, iss, expired, future iat, empty sub, missing sub", async () => {
  await rejects(await mint({ aud: "other" }));
  await rejects(await mint({ iss: "https://securetoken.google.com/other" }));
  await rejects(await mint({ exp: now - 1 }));
  await rejects(await mint({ iat: now + 3600 }));
  await rejects(await mint({ sub: "" }));
  await rejects(await mint({ sub: undefined }));
});
test("rejects bad signature, unknown kid, alg none / HS256, malformed", async () => {
  await rejects(await mint({}, {}, other.privateKey));
  await rejects(await mint({}, { kid: "nope" }));
  await rejects(await mint({}, { alg: "none" }));
  await rejects(await mint({}, { alg: "HS256" }));
  await rejects("a.b");
  await rejects("");
  const t = await mint(); const [h, p, s] = t.split(".");
  await rejects(`${h}.${b64urlEncode(JSON.stringify({ aud: P, iss: `https://securetoken.google.com/${P}`, sub: "admin", iat: now, exp: now + 99 }))}.${s}`);
});
test("validateClaims / maxAge", () => {
  assert.equal(validateClaims({ alg: "RS256", kid: "k" }, { aud: P, iss: `https://securetoken.google.com/${P}`, sub: "u", iat: now, exp: now + 5 }, { projectId: P, now }), "u");
  assert.equal(maxAge("public, max-age=19000, must-revalidate"), 19000);
  assert.equal(maxAge(null), 300);
});
