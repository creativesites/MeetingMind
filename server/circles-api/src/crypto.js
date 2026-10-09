// Small WebCrypto helpers shared by token verification and service-account signing.
export const enc = new TextEncoder();

export function b64urlEncode(bytes) {
  if (typeof bytes === "string") bytes = enc.encode(bytes);
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export function b64urlDecode(str) {
  if (typeof str !== "string" || !/^[A-Za-z0-9_-]*$/.test(str)) throw new Error("bad base64url");
  const pad = "=".repeat((4 - (str.length % 4)) % 4);
  const bin = atob(str.replace(/-/g, "+").replace(/_/g, "/") + pad);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

export function pemToDer(pem) {
  const b64 = pem.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

const RS256 = { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" };

export function importPkcs8(pem) {
  return crypto.subtle.importKey("pkcs8", pemToDer(pem), RS256, false, ["sign"]);
}

export function importJwk(jwk) {
  return crypto.subtle.importKey("jwk", jwk, RS256, false, ["verify"]);
}

export async function signRs256(privateKey, input) {
  return b64urlEncode(new Uint8Array(await crypto.subtle.sign(RS256.name, privateKey, enc.encode(input))));
}

export async function verifyRs256(publicKey, input, sigB64) {
  return crypto.subtle.verify(RS256.name, publicKey, b64urlDecode(sigB64), enc.encode(input));
}

export async function hmacHex(secret, message) {
  const key = await crypto.subtle.importKey("raw", enc.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const sig = new Uint8Array(await crypto.subtle.sign("HMAC", key, enc.encode(message)));
  return [...sig].map((b) => b.toString(16).padStart(2, "0")).join("");
}
