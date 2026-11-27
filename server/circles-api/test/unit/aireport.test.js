import test from "node:test";
import assert from "node:assert/strict";
import { handle } from "../../src/index.js";
import { reportAiContent, validateAiReport, AI_REPORT_RATE } from "../../src/aireport.js";
import { FakeDb, FakeKv, FakeFcm, makeCtx } from "./fakes.js";

const good = () => ({
  reportId: "0b7c1f7e-1111-4222-8333-944455556666",
  feature: "note_block",
  reason: "offensive",
  comment: "Please fix",
  text: "The AI said something unkind.",
  model: "gemini-3.8-flash",
  appVersion: "1.0-v48",
  createdAt: 1_700_000_000_000,
});

function world() {
  const db = new FakeDb(), kv = new FakeKv(), fcm = new FakeFcm();
  const call = (body, over = {}) => reportAiContent(makeCtx(db, kv, fcm, null, body, over).ctx);
  return { db, kv, call };
}

test("a valid report is stored once, with only the report fields and no caller identity", async () => {
  const W = world();
  await W.call(good());
  await W.call(good());
  const docs = [...W.db.docs].filter(([p]) => p.startsWith("aiReports/"));
  assert.equal(docs.length, 1);
  const [path, doc] = docs[0];
  assert.equal(path, "aiReports/0b7c1f7e-1111-4222-8333-944455556666");
  assert.deepEqual(Object.keys(doc).sort(), ["appVersion", "clientCreatedAt", "comment", "feature", "model", "reason", "receivedAt", "text"]);
  assert.doesNotMatch(JSON.stringify(doc), /1\.2\.3\.4/);
});

test("the comment is optional", async () => {
  const W = world();
  const body = good(); delete body.comment;
  await W.call(body);
  assert.equal(W.db.dump(`aiReports/${body.reportId}`).comment, undefined);
});

test("unknown fields, features and reasons are refused", () => {
  for (const change of [
    { noteId: "n1" }, { title: "My note" }, { transcript: "..." },
    { feature: "everything" }, { reason: "spam" }, { reportId: "../x" }, { createdAt: "soon" },
  ]) {
    assert.throws(() => validateAiReport({ ...good(), ...change }), (e) => e.status === 400, JSON.stringify(change));
  }
  assert.throws(() => validateAiReport(null), (e) => e.status === 400);
  assert.throws(() => validateAiReport([good()]), (e) => e.status === 400);
});

test("limits match the app: text 4000, comment 500, model 80, version 40", () => {
  assert.ok(validateAiReport({ ...good(), text: "a".repeat(4000) }));
  for (const [k, n] of [["text", 4001], ["comment", 501], ["model", 81], ["appVersion", 41]]) {
    assert.throws(() => validateAiReport({ ...good(), [k]: "a".repeat(n) }), (e) => e.status === 400, k);
  }
  for (const k of ["text", "model", "appVersion"]) {
    assert.throws(() => validateAiReport({ ...good(), [k]: "   " }), (e) => e.status === 400, k);
  }
});

test("reports are rate-limited per caller", async () => {
  const W = world();
  for (let i = 0; i < AI_REPORT_RATE.limit; i++) await W.call({ ...good(), reportId: `report-id-${String(i).padStart(4, "0")}` });
  await assert.rejects(W.call({ ...good(), reportId: "report-id-over" }), (e) => e.status === 429);
  await W.call({ ...good(), reportId: "report-id-other-ip" }, { ip: "5.6.7.8" });
  assert.ok([...W.kv.m.keys()].every((k) => !k.includes("1.2.3.4")), "the IP itself is never stored");
});

test("the route needs no sign-in, and other routes still do", async () => {
  const db = new FakeDb(), kv = new FakeKv();
  const env = { FIREBASE_PROJECT_ID: "p", SERVICE_ACCOUNT_JSON: JSON.stringify({ client_email: "x", private_key: "k" }), RATE: kv };
  const deps = { services: { db, fcm: new FakeFcm() }, authenticate: async () => { throw Object.assign(new Error("no"), { status: 401 }); } };
  const post = (path, body) => handle(new Request(`https://circles.test/v1/${path}`, {
    method: "POST", headers: { "Content-Type": "application/json", "CF-Connecting-IP": "9.9.9.9" }, body: JSON.stringify(body),
  }), env, { waitUntil() {} }, deps);

  const ok = await post("reportAiContent", good());
  assert.equal(ok.status, 200);
  assert.ok(db.dump(`aiReports/${good().reportId}`));

  const badReq = await post("reportAiContent", { ...good(), noteId: "n1" });
  assert.equal(badReq.status, 400);

  const other = await post("myCircles", {});
  assert.notEqual(other.status, 200);
});
