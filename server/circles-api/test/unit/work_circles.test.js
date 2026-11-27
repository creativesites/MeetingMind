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
  cleanPostMeta,
  shapePoll,
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
  assert.ok(circle.settings.allowedTypes.includes("blocker"));
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

  const circle = W.db.dump(`circles/${circleId}`);
  assert.ok(circle.settings.allowedTypes.includes("blocker"));

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

  // Post a blocker
  const blockerRes = await W.call("lead", "createPost", {
    circleId,
    type: "blocker",
    body: "Staging deployment pipeline failing",
    anonymous: false,
    requestId: crypto.randomUUID()
  });
  assert.ok(blockerRes.postId);

  const savedBlocker = W.db.dump(`circles/${circleId}/posts/${blockerRes.postId}`);
  assert.equal(savedBlocker.type, "blocker");
  assert.equal(savedBlocker.body, "Staging deployment pipeline failing");

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

test("cleanPostMeta: sanitizes tags and checklist", () => {
  // Tags: trim, strip leading #, max 24 chars, max 4 items, dedupe case-insensitively, filter empty
  const metaWithTags = cleanPostMeta({
    tags: [
      "  #backend  ",
      "##Frontend",
      "BACKEND", // duplicate case-insensitive
      "   ",
      "#",
      "a".repeat(30), // truncated to 24 chars
      "mobile",
      "extra-tag-5", // exceeds max 4
    ],
  });
  assert.deepEqual(metaWithTags.tags, ["backend", "Frontend", "a".repeat(24), "mobile"]);

  // Empty tags array or all blank tags leaves tags undefined
  assert.equal(cleanPostMeta({ tags: ["#", "   "] }), null);

  // Checklist: max 8 items, trimmed text <= 120, id string trimmed <= 32 (default c{idx}), done boolean
  const metaWithChecklist = cleanPostMeta({
    checklist: [
      { id: "step-1", text: "  Design schema  ", done: true },
      { text: "Implement endpoint", done: false }, // defaults id to c1
      { id: "  custom-id  ", text: "b".repeat(150), done: "not-bool" }, // text trimmed to 120, done false
      { id: "bad", text: "   " }, // skipped because empty text
      { text: "Item 4" },
      { text: "Item 5" },
      { text: "Item 6" },
      { text: "Item 7" },
      { text: "Item 8" },
      { text: "Item 9" }, // exceeds max 8 items
    ],
  });
  assert.equal(metaWithChecklist.checklist.length, 8);
  assert.deepEqual(metaWithChecklist.checklist[0], { id: "step-1", text: "Design schema", done: true });
  assert.deepEqual(metaWithChecklist.checklist[1], { id: "c1", text: "Implement endpoint", done: false });
  assert.deepEqual(metaWithChecklist.checklist[2], { id: "custom-id", text: "b".repeat(120), done: false });
  assert.equal(metaWithChecklist.checklist[3].id, "c3");
  assert.equal(metaWithChecklist.checklist[7].id, "c7");
});

test("toggleChecklistItem: membership, toggle boolean, explicit done, and 404 handling", async () => {
  const W = world();
  const { circleId } = await W.call("lead", "createCircle", {
    name: "Engineering Team",
    template: "project_team",
    space: "work",
    displayName: "Tech Lead",
    requestId: crypto.randomUUID(),
  });

  // Create post with checklist
  const post = await W.call("lead", "createPost", {
    circleId,
    type: "task",
    body: "Deployment steps",
    anonymous: false,
    requestId: crypto.randomUUID(),
    meta: {
      checklist: [
        { id: "item-1", text: "Run migrations", done: false },
        { id: "item-2", text: "Deploy workers", done: true },
      ],
    },
  });
  const postId = post.postId;

  // Toggle item-1 from false to true (omitting done)
  const res1 = await W.call("lead", "toggleChecklistItem", {
    circleId,
    postId,
    itemId: "item-1",
  });
  assert.equal(res1.ok, true);
  assert.equal(res1.checklist[0].done, true);
  assert.equal(res1.checklist[1].done, true);
  let saved = W.db.dump(`circles/${circleId}/posts/${postId}`);
  assert.equal(saved.meta.checklist[0].done, true);

  // Toggle item-1 from true to false (omitting done)
  const res2 = await W.call("lead", "toggleChecklistItem", {
    circleId,
    postId,
    itemId: "item-1",
  });
  assert.equal(res2.checklist[0].done, false);

  // Explicit done: true
  const res3 = await W.call("lead", "toggleChecklistItem", {
    circleId,
    postId,
    itemId: "item-1",
    done: true,
  });
  assert.equal(res3.checklist[0].done, true);

  // Explicit done: false
  const res4 = await W.call("lead", "toggleChecklistItem", {
    circleId,
    postId,
    itemId: "item-1",
    done: false,
  });
  assert.equal(res4.checklist[0].done, false);

  // 404 for missing checklist item
  const missingItem = await W.err("lead", "toggleChecklistItem", {
    circleId,
    postId,
    itemId: "non-existent-item",
  });
  assert.equal(missingItem.code, "not_found");

  // 404 for missing post
  const missingPost = await W.err("lead", "toggleChecklistItem", {
    circleId,
    postId: "missing-post-id",
    itemId: "item-1",
  });
  assert.equal(missingPost.code, "not_found");

  // 403 for non-member
  const nonMember = await W.err("outsider", "toggleChecklistItem", {
    circleId,
    postId,
    itemId: "item-1",
  });
  assert.equal(nonMember.code, "not_a_member");
});

test("quiz polls: shapePoll and createPoll support correctOptionId, correctIndex, and explanation", async () => {
  // shapePoll with valid correctIndex
  const p1 = shapePoll({
    question: "What is 2+2?",
    options: ["3", "4", "5"],
    correctIndex: 1,
    explanation: "Basic arithmetic: 2 + 2 = 4.",
  });
  assert.equal(p1.correctOptionId, "o1");
  assert.equal(p1.explanation, "Basic arithmetic: 2 + 2 = 4.");

  // shapePoll with valid correctOptionId
  const p2 = shapePoll({
    question: "Select capital of France",
    options: ["Berlin", "London", "Paris"],
    correctOptionId: "o2",
  });
  assert.equal(p2.correctOptionId, "o2");
  assert.equal("explanation" in p2, false);

  // Rejects quiz poll when multi is true
  assert.throws(() => shapePoll({
    question: "Quiz question",
    options: ["A", "B"],
    multi: true,
    correctIndex: 0,
  }), /Quiz polls cannot be multiple choice/);

  // Rejects invalid correctIndex
  assert.throws(() => shapePoll({
    question: "Quiz question",
    options: ["A", "B"],
    correctIndex: 5,
  }), /Invalid correctIndex/);

  assert.throws(() => shapePoll({
    question: "Quiz question",
    options: ["A", "B"],
    correctIndex: -1,
  }), /Invalid correctIndex/);

  // Rejects invalid correctOptionId
  assert.throws(() => shapePoll({
    question: "Quiz question",
    options: ["A", "B"],
    correctOptionId: "o99",
  }), /Invalid correctOptionId/);

  // Explanation max 240 chars
  assert.throws(() => shapePoll({
    question: "Quiz question",
    options: ["A", "B"],
    correctIndex: 0,
    explanation: "x".repeat(241),
  }), /explanation is too long/);

  // createPoll end to end in world
  const W = world();
  const { circleId } = await W.call("teacher", "createCircle", {
    name: "Biology 101",
    template: "project_team",
    space: "work",
    displayName: "Prof Smith",
    requestId: crypto.randomUUID(),
  });

  const pollRes = await W.call("teacher", "createPoll", {
    circleId,
    question: "What organelle produces ATP?",
    options: ["Nucleus", "Mitochondria", "Ribosome"],
    correctIndex: 1,
    explanation: "Mitochondria are the powerhouses of the cell.",
  });

  assert.ok(pollRes.pollId);
  const pollDoc = W.db.dump(`circles/${circleId}/polls/${pollRes.pollId}`);
  assert.equal(pollDoc.correctOptionId, "o1");
  assert.equal(pollDoc.explanation, "Mitochondria are the powerhouses of the cell.");
  assert.equal(pollDoc.multi, false);
});
