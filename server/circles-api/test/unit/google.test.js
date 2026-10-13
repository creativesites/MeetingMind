import test from "node:test";
import assert from "node:assert/strict";
import { Firestore } from "../../src/firestore.js";

test("Firestore and service-account OAuth invoke Worker fetch with its required global receiver", async () => {
  const pair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  const der = new Uint8Array(await crypto.subtle.exportKey("pkcs8", pair.privateKey));
  const pem = `-----BEGIN PRIVATE KEY-----\n${btoa(String.fromCharCode(...der)).match(/.{1,64}/g).join("\n")}\n-----END PRIVATE KEY-----`;
  const env = { FIREBASE_PROJECT_ID: "demo-circles", SERVICE_ACCOUNT_JSON: JSON.stringify({ client_email: "worker@example.invalid", private_key: pem, private_key_id: "test" }) };
  const calls = [];
  const fetchImpl = async function (url, options) {
    assert.equal(this, globalThis, "fetch must be invoked with globalThis as its receiver");
    calls.push(String(url));
    if (String(url).includes("oauth2.googleapis.com/token")) {
      return new Response(JSON.stringify({ access_token: "test-access-token", expires_in: 3600 }), { status: 200 });
    }
    return new Response("", { status: 404 });
  };
  const db = new Firestore({ projectId: "demo-circles", env, fetchImpl });
  assert.equal(await db.get("userMeta/probe"), null);
  assert.equal(calls.length, 2);
  assert.match(calls[0], /oauth2\.googleapis\.com\/token/);
  assert.match(calls[1], /firestore\.googleapis\.com/);
});
