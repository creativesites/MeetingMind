import test from "node:test";
import assert from "node:assert/strict";
import { handle } from "../../src/index.js";

const execCtx = { waitUntil() {} };

test("health and readiness report Worker health separately from service configuration", async () => {
  const healthy = await handle(new Request("https://circles.test/health"), {}, execCtx);
  assert.equal(healthy.status, 200);
  assert.equal((await healthy.json()).status, "ok");

  const ready = await handle(new Request("https://circles.test/ready"), { SERVICE_ACCOUNT_JSON: "{}" }, execCtx);
  assert.equal(ready.status, 503);
  assert.equal((await ready.json()).error.code, "service_misconfigured");
});

test("malformed service account is a correlated safe configuration failure", async () => {
  const logs = [];
  const original = console.error;
  console.error = (...args) => logs.push(args.join(" "));
  try {
    const response = await handle(new Request("https://circles.test/v1/myCircles", {
      method: "POST", headers: { "Content-Type": "application/json", "CF-Ray": "test-ray-123" }, body: "{}",
    }), { FIREBASE_PROJECT_ID: "test-project", SERVICE_ACCOUNT_JSON: "{}" }, execCtx, {
      authenticate: async () => ({ uid: "user-1" }),
    });
    assert.equal(response.status, 503);
    assert.equal(response.headers.get("X-Request-Id"), "test-ray-123");
    const body = await response.json();
    assert.equal(body.requestId, "test-ray-123");
    assert.equal(body.error.code, "service_misconfigured");
    assert.doesNotMatch(JSON.stringify(body), /private_key|client_email/);
    assert.match(logs.join(" "), /test-ray-123/);
  } finally { console.error = original; }
});
