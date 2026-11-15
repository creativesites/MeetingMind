import test from "node:test";
import assert from "node:assert/strict";
import { HANDLERS } from "../../src/handlers.js";
import {
  WORK_CODE_WORDS,
  LEARNING_CODE_WORDS,
  GENERAL_CODE_WORDS,
  LEARNING_REACTIONS,
  GENERAL_REACTIONS,
  TEMPLATES,
} from "../../src/logic.js";
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

test("learning circle: create preserves learning space, vocab, reactions, and invites", async () => {
  const W = world();
  for (const template of ["study_group", "course_cohort", "exam_prep_room", "custom_learning"]) {
    const res = await W.call("student", "createCircle", {
      name: `Cohort ${template}`,
      template,
      space: "learning",
      displayName: "Maya",
      requestId: crypto.randomUUID(),
    });
    const circle = W.db.dump(`circles/${res.circleId}`);
    assert.equal(circle.space, "learning");
    assert.equal(circle.vocab, "Study group");
    assert.equal(circle.settings.space, "learning");
    assert.equal(circle.settings.prayerApproval, false);
    assert.deepEqual(circle.settings.allowedTypes, TEMPLATES[template]);
    assert.deepEqual(circle.settings.reactions, LEARNING_REACTIONS);
    const { code } = await W.call("student", "createInvite", { circleId: res.circleId });
    const prefix = code.split("-")[0];
    assert.ok(LEARNING_CODE_WORDS.includes(prefix), `Expected ${prefix} to be in LEARNING_CODE_WORDS`);
  }
});

test("general & community circle: infers or normalizes general space cleanly", async () => {
  const W = world();
  const res = await W.call("host", "createCircle", {
    name: "Weekend Book Club",
    template: "book_club",
    space: "community",
    displayName: "Sam",
    requestId: crypto.randomUUID(),
  });
  const circle = W.db.dump(`circles/${res.circleId}`);
  assert.equal(circle.space, "general");
  assert.equal(circle.vocab, "Group");
  assert.equal(circle.settings.prayerApproval, false);
  assert.deepEqual(circle.settings.reactions, GENERAL_REACTIONS);
  const { code } = await W.call("host", "createInvite", { circleId: res.circleId });
  assert.ok(GENERAL_CODE_WORDS.includes(code.split("-")[0]));
});

async function joinUser(W, inviterUid, circleId, uid, displayName = uid) {
  const { code } = await W.call(inviterUid, "createInvite", { circleId });
  return W.call(uid, "join", { code, displayName });
}

test("work circle: task metadata, setWorkStatus, and markAnswered workflow", async () => {
  const W = world();
  const { circleId } = await W.call("lead", "createCircle", {
    name: "Engineering Core",
    template: "project_team",
    space: "work",
    displayName: "Tech Lead",
    settings: { allowedTypes: ["task", "blocker", "note"] },
    requestId: crypto.randomUUID(),
  });

  await joinUser(W, "lead", circleId, "dev", "Dev");
  await joinUser(W, "lead", circleId, "assignee", "Assignee");
  await joinUser(W, "lead", circleId, "mod", "Mod");
  await joinUser(W, "lead", circleId, "other", "Other");
  await W.call("lead", "setRole", { circleId, targetUid: "mod", role: "moderator" });

  // Create a task with rich work metadata
  const taskRes = await W.call("dev", "createPost", {
    circleId,
    type: "task",
    body: "Build the auth module",
    anonymous: false,
    requestId: crypto.randomUUID(),
    meta: {
      assigneeUid: "assignee",
      assigneeName: "Assignee Dev",
      dueAt: 1_800_000_000_000,
      dueText: "Tomorrow",
      priority: "high",
      blockedOn: "Design specs",
      decisionOwner: "Lead Architect",
      meetingDate: "2026-11-20",
    },
  });
  const taskId = taskRes.postId;
  const taskDoc = W.db.dump(`circles/${circleId}/posts/${taskId}`);
  assert.equal(taskDoc.meta.assigneeUid, "assignee");
  assert.equal(taskDoc.meta.dueAt, 1_800_000_000_000);
  assert.equal(taskDoc.meta.priority, "high");
  assert.equal(taskDoc.meta.blockedOn, "Design specs");
  assert.equal(taskDoc.meta.decisionOwner, "Lead Architect");
  assert.equal(taskDoc.meta.meetingDate, "2026-11-20");

  // setWorkStatus validation
  const badStatus = await W.err("dev", "setWorkStatus", { circleId, postId: taskId, status: "completed" });
  assert.equal(badStatus.code, "bad_request");
  assert.equal(badStatus.message, "Unknown status.");

  // setWorkStatus on non-task/blocker post (note)
  const noteRes = await W.call("dev", "createPost", {
    circleId,
    type: "note",
    body: "Architecture notes",
    anonymous: false,
    requestId: crypto.randomUUID(),
  });
  const badType = await W.err("dev", "setWorkStatus", { circleId, postId: noteRes.postId, status: "in_progress" });
  assert.equal(badType.code, "bad_request");
  assert.equal(badType.message, "Only tasks or blockers have a workflow status.");

  // setWorkStatus permissions: other member cannot update
  const forbidStatus = await W.err("other", "setWorkStatus", { circleId, postId: taskId, status: "in_progress" });
  assert.equal(forbidStatus.code, "forbidden");
  assert.equal(forbidStatus.message, "Only the creator, assignee, or a moderator can update status.");

  // Assignee updates status to in_progress
  await W.call("assignee", "setWorkStatus", { circleId, postId: taskId, status: "in_progress" });
  const inProgDoc = W.db.dump(`circles/${circleId}/posts/${taskId}`);
  assert.equal(inProgDoc.status, "in_progress");
  assert.equal(inProgDoc.answeredAt, null);

  // Moderator updates status to answered
  await W.call("mod", "setWorkStatus", { circleId, postId: taskId, status: "answered" });
  const ansDoc = W.db.dump(`circles/${circleId}/posts/${taskId}`);
  assert.equal(ansDoc.status, "answered");
  assert.ok(ansDoc.answeredAt);

  // Creator updates status back to open
  await W.call("dev", "setWorkStatus", { circleId, postId: taskId, status: "open" });
  const openDoc = W.db.dump(`circles/${circleId}/posts/${taskId}`);
  assert.equal(openDoc.status, "open");
  assert.equal(openDoc.answeredAt, null);

  // Blocker post & markAnswered permissions
  const blockerRes = await W.call("dev", "createPost", {
    circleId,
    type: "blocker",
    body: "Database migration failing",
    anonymous: false,
    requestId: crypto.randomUUID(),
    meta: { assigneeUid: "assignee" },
  });
  const blockerId = blockerRes.postId;

  // other member cannot markAnswered on blocker
  const forbidAnswer = await W.err("other", "markAnswered", { circleId, postId: blockerId });
  assert.equal(forbidAnswer.code, "forbidden");

  // Assignee can markAnswered on blocker
  await W.call("assignee", "markAnswered", { circleId, postId: blockerId });
  const blockerDoc = W.db.dump(`circles/${circleId}/posts/${blockerId}`);
  assert.equal(blockerDoc.status, "answered");
  assert.ok(blockerDoc.answeredAt);

  // markAnswered when already answered is idempotent
  const repeat = await W.call("assignee", "markAnswered", { circleId, postId: blockerId });
  assert.deepEqual(repeat, {});

  // Creator can markAnswered on task
  await W.call("dev", "markAnswered", { circleId, postId: taskId });
  assert.equal(W.db.dump(`circles/${circleId}/posts/${taskId}`).status, "answered");

  // markAnswered on note rejected
  const noteAnswer = await W.err("dev", "markAnswered", { circleId, postId: noteRes.postId });
  assert.equal(noteAnswer.code, "bad_request");
  assert.equal(noteAnswer.message, "Only prayer requests, tasks, or blockers can be resolved.");
});

test("work circle: createPoll supports closesAt deadline", async () => {
  const W = world();
  const { circleId } = await W.call("lead", "createCircle", {
    name: "Product Sprint",
    template: "project_team",
    space: "work",
    displayName: "Product Manager",
    requestId: crypto.randomUUID(),
  });

  // Valid closesAt stores deadline on poll document
  const poll1 = await W.call("lead", "createPoll", {
    circleId,
    question: "Ship 1.0 this Friday?",
    options: ["Ship it", "Wait a week"],
    closesAt: 1_850_000_000_000,
  });
  assert.ok(poll1.pollId);
  const pDoc1 = W.db.dump(`circles/${circleId}/polls/${poll1.pollId}`);
  assert.equal(pDoc1.closesAt, 1_850_000_000_000);
  assert.equal(pDoc1.closed, false);

  // Omitted closesAt does not set closesAt property on poll document
  const poll2 = await W.call("lead", "createPoll", {
    circleId,
    question: "Preferred demo time?",
    options: ["Morning", "Afternoon"],
  });
  const pDoc2 = W.db.dump(`circles/${circleId}/polls/${poll2.pollId}`);
  assert.equal("closesAt" in pDoc2, false);

  // Invalid closesAt rejected
  const badPoll = await W.err("lead", "createPoll", {
    circleId,
    question: "Standup time?",
    options: ["9am", "10am"],
    closesAt: "not-a-timestamp",
  });
  assert.equal(badPoll.code, "bad_request");
  assert.equal(badPoll.message, "Poll deadline must be a valid time.");
});
