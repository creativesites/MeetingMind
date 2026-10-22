import test from "node:test";
import assert from "node:assert/strict";
import { HANDLERS } from "../../src/handlers.js";
import { WORK_CODE_WORDS, WORK_POST_TYPES, WORK_REACTIONS, TEMPLATES } from "../../src/logic.js";
import { FakeDb, FakeKv, FakeFcm, makeCtx } from "./fakes.js";

function world() {
  const db = new FakeDb(), kv = new FakeKv(), fcm = new FakeFcm();
  const call = async (uid, name, body) => { const { ctx, flush } = makeCtx(db, kv, fcm, uid, body); const r = await HANDLERS[name](ctx); await flush(); return r; };
  const err = async (uid, name, body) => call(uid, name, body).then(() => assert.fail("expected error"), (e) => e);
  return { db, kv, fcm, call, err };
}

test("work circle: create with work space and project_team template", async () => {
  const W = world();
  const res = await W.call("lead", "createCircle", {
    name: "Core Engineering",
    template: "project_team",
    space: "work",
    displayName: "Lead Dev",
    requestId: crypto.randomUUID()
  });
  const circle = W.db.dump(`circles/${res.circleId}`);
  assert.equal(circle.name, "Core Engineering");
  assert.equal(circle.space, "work");
  assert.equal(circle.vocab, "Team");
  assert.equal(circle.settings.prayerApproval, false);
  assert.deepEqual(circle.settings.allowedTypes, TEMPLATES.project_team);
  assert.ok(circle.settings.reactions.includes("ack"));
  assert.ok(circle.settings.reactions.includes("approved"));
});

test("work circle: invites generate work-oriented code words", async () => {
  const W = world();
  const { circleId } = await W.call("lead", "createCircle", {
    name: "Client Delivery",
    template: "client_engagement",
    space: "work",
    displayName: "Project Lead",
    requestId: crypto.randomUUID()
  });
  const { code } = await W.call("lead", "createInvite", { circleId });
  const prefix = code.split("-")[0];
  assert.ok(WORK_CODE_WORDS.includes(prefix), `Expected prefix ${prefix} to be in WORK_CODE_WORDS`);
});

test("work circle: createEmailInvite generates an invite linked to an email", async () => {
  const W = world();
  const { circleId } = await W.call("lead", "createCircle", {
    name: "Design Guild",
    template: "project_team",
    space: "work",
    displayName: "Design Director",
    requestId: crypto.randomUUID()
  });

  // Invalid email rejected
  const badErr = await W.err("lead", "createEmailInvite", { circleId, email: "invalid-email" });
  assert.equal(badErr.code, "bad_request");

  // Valid email creates invite
  const inv = await W.call("lead", "createEmailInvite", { circleId, email: "colleague@company.com" });
  assert.equal(inv.email, "colleague@company.com");
  assert.ok(inv.code);

  const inviteRecord = W.db.dump(`invites/${inv.code}`);
  assert.equal(inviteRecord.email, "colleague@company.com");
  assert.equal(inviteRecord.circleId, circleId);
});

test("work circle: accepts work post types and work reactions", async () => {
  const W = world();
  const { circleId } = await W.call("lead", "createCircle", {
    name: "Sprint 42",
    template: "project_team",
    space: "work",
    displayName: "Scrum Master",
    requestId: crypto.randomUUID()
  });

  // Post a task
  const postRes = await W.call("lead", "createPost", {
    circleId,
    type: "task",
    body: "Implement the OAuth 2.0 PKCE flow for mobile clients",
    anonymous: false,
    requestId: crypto.randomUUID()
  });
  assert.ok(postRes.postId);

  const savedPost = W.db.dump(`circles/${circleId}/posts/${postRes.postId}`);
  assert.equal(savedPost.type, "task");
  assert.equal(savedPost.body, "Implement the OAuth 2.0 PKCE flow for mobile clients");

  // React with work reaction "ack" (👍 On it)
  await W.call("lead", "react", { circleId, postId: postRes.postId, kind: "ack" });
  const reactedPost = W.db.dump(`circles/${circleId}/posts/${postRes.postId}`);
  assert.equal(reactedPost.counts.reactions.ack, 1);
});
