import test from "node:test";
import assert from "node:assert/strict";
import { extractCodes, generateCode, joinDecision, shapePost, normalizeSettings, clampInvite, validId, cleanText, assertNoAuthorLeak, publishFromPending, canInvite, CODE_ALPHABET } from "../../src/logic.js";

test("extractCodes pulls the code out of a whole share message, any case, or URL", () => {
  assert.deepEqual(extractCodes("Join my circle! Code: GRACE-7K2Q  see you there"), ["GRACE-7K2Q"]);
  assert.deepEqual(extractCodes("https://example.com/c/grace-7k2q?x=1"), ["GRACE-7K2Q"]);
  assert.deepEqual(extractCodes("my self-care and well-known friend"), []);
  assert.deepEqual(extractCodes("GRACE-7K2QX"), []); // too long
  assert.deepEqual(extractCodes("GRACE-0O1I"), []); // ambiguous chars not in alphabet
  assert.deepEqual(extractCodes(null), []);
  assert.equal(extractCodes("GRACE-7K2Q HOPE-BBBB LAMP-2222 SALT-3333").length, 3);
});

test("generateCode uses the unambiguous alphabet and round-trips through extractCodes", () => {
  for (let i = 0; i < 200; i++) {
    const c = generateCode();
    assert.match(c, /^[A-Z]+-[2-9BCDFGHJKMNPQRSTVWXZ]{4}$/);
    assert.deepEqual(extractCodes(`code ${c}!`), [c]);
  }
  assert.ok(!/[AEIOU01IL]/.test(CODE_ALPHABET));
});

test("joinDecision: friendly codes in priority order", () => {
  const base = { invite: { revoked: false, expiresAt: new Date(2e12), maxUses: 5, uses: 0 }, nowMs: 1e12, memberCount: 3, userCircleCount: 2, alreadyMember: false, circleClosed: false };
  assert.equal(joinDecision(base), null);
  assert.equal(joinDecision({ ...base, invite: null })[0], "not_found");
  assert.equal(joinDecision({ ...base, circleClosed: true })[0], "not_found");
  assert.equal(joinDecision({ ...base, alreadyMember: true })[0], "already_member");
  assert.equal(joinDecision({ ...base, invite: { ...base.invite, revoked: true } })[0], "revoked");
  assert.equal(joinDecision({ ...base, invite: { ...base.invite, expiresAt: new Date(1) } })[0], "expired");
  assert.equal(joinDecision({ ...base, invite: { ...base.invite, uses: 5 } })[0], "full");
  assert.equal(joinDecision({ ...base, memberCount: 50 })[0], "full");
  assert.equal(joinDecision({ ...base, memberCount: 49 }), null);
  assert.equal(joinDecision({ ...base, userCircleCount: 10 })[0], "too_many_circles");
  assert.equal(joinDecision({ ...base, userCircleCount: 9 }), null);
});

const settings = normalizeSettings({}, "small_group");
const now = new Date("2026-10-09T14:03:22Z");

test("anonymous prayer: author never in the readable doc, time coarsened, goes to pending", () => {
  const r = shapePost({ type: "prayer", body: "pray", verseRef: null, anonymous: true, uid: "UID-SECRET-1", authorName: "Ann", settings, now });
  assert.equal(r.pending, true);
  assert.doesNotMatch(JSON.stringify(r.post), /UID-SECRET-1|Ann/);
  assert.equal(r.authorDoc.authorUid, "UID-SECRET-1");
  assert.equal(r.post.createdAt.toISOString(), "2026-10-09T14:00:00.000Z");
  assertNoAuthorLeak(r.post, "UID-SECRET-1");
  const pub = publishFromPending(r.post, r.authorDoc, now);
  assert.doesNotMatch(JSON.stringify(pub), /UID-SECRET-1|Ann/);
});

test("named post carries author; approval off publishes prayers; anonymous only for prayer", () => {
  const t = shapePost({ type: "testimony", body: "x", anonymous: false, uid: "u1", authorName: "Ann", settings, now });
  assert.equal(t.pending, false); assert.equal(t.post.authorUid, "u1");
  const off = normalizeSettings({ prayerApproval: false }, "small_group");
  assert.equal(shapePost({ type: "prayer", body: "x", anonymous: false, uid: "u1", authorName: "A", settings: off, now }).pending, false);
  assert.throws(() => shapePost({ type: "testimony", body: "x", anonymous: true, uid: "u", authorName: "A", settings, now }));
  assert.throws(() => shapePost({ type: "achievement", body: "x", anonymous: false, uid: "u", authorName: "A", settings, now }), /doesn't use/);
  assert.equal(normalizeSettings({}, "family").prayerApproval, true); // default on
});

test("validation helpers reject path injection and junk", () => {
  assert.throws(() => validId("a/b")); assert.throws(() => validId("../x")); assert.throws(() => validId(5));
  assert.equal(validId("abcDEF123_-"), "abcDEF123_-");
  assert.throws(() => cleanText("", "x", { max: 5 })); assert.throws(() => cleanText("toolong", "x", { max: 3 }));
  assert.throws(() => normalizeSettings({ allowedTypes: ["evil"] }, "custom"));
  assert.throws(() => normalizeSettings({ whoCanInvite: "everyone" }, "custom"));
  assert.throws(() => clampInvite({ maxUses: 1000 })); assert.deepEqual(clampInvite({}), { days: 7, uses: 50 });
  assert.equal(canInvite("member", settings), false); assert.equal(canInvite("member", { whoCanInvite: "members" }), true);
});

import { shapePoll, shapeCelebration, cleanPostMeta, GENERAL_POST_TYPES, GENERAL_REACTIONS, GENERAL_CODE_WORDS, ALL_CODE_WORDS, TEMPLATES } from "../../src/logic.js";
test("shapePoll builds stable option ids; shapeCelebration validates kind and companion", () => {
  const p = shapePoll({ question: " Night? ", options: [" Tue ", "Thu"], multi: undefined });
  assert.deepEqual(p.optionIds, ["o0", "o1"]); assert.equal(p.question, "Night?"); assert.equal(p.multi, false);
  assert.throws(() => shapePoll({ question: "Q", options: "ab" }));
  assert.throws(() => shapePoll({ question: "Q", options: ["a", "b"], multi: "yes" }));
  assert.deepEqual(shapeCelebration({ kind: "streak" }), { kind: "streak", text: "", companion: null });
  assert.throws(() => shapeCelebration({ kind: "x" }));
  assert.throws(() => shapeCelebration({ kind: "streak", text: "x".repeat(141) }));
});

test("join approval defaults off and must be a boolean", () => {
  assert.equal(normalizeSettings({}, "family").joinApproval, false);
  assert.equal(normalizeSettings({ joinApproval: true }, "family").joinApproval, true);
  assert.throws(() => normalizeSettings({ joinApproval: 1 }, "family"));
});

test("general space: templates, settings normalization, and invite code generation", () => {
  // General templates
  const generalTemplates = ["friends_group", "community_club", "book_club", "custom_general"];
  for (const tpl of generalTemplates) {
    assert.ok(TEMPLATES[tpl], `Template ${tpl} should exist`);
    assert.deepEqual(TEMPLATES[tpl], GENERAL_POST_TYPES);
  }

  // Normalization with space="general"
  const s1 = normalizeSettings({}, "friends_group", "general");
  assert.equal(s1.space, "general");
  assert.equal(s1.prayerApproval, false);
  assert.deepEqual(s1.reactions, GENERAL_REACTIONS);
  assert.deepEqual(s1.allowedTypes, GENERAL_POST_TYPES);

  // Normalization via s.space="general"
  const s2 = normalizeSettings({ space: "general" }, "custom_general");
  assert.equal(s2.space, "general");
  assert.equal(s2.prayerApproval, false);
  assert.deepEqual(s2.reactions, GENERAL_REACTIONS);

  // Custom overrides in general space
  const s3 = normalizeSettings({ prayerApproval: true, reactions: ["heart"] }, "book_club", "general");
  assert.equal(s3.prayerApproval, true);
  assert.deepEqual(s3.reactions, ["heart"]);

  // All GENERAL_CODE_WORDS are part of ALL_CODE_WORDS
  for (const word of GENERAL_CODE_WORDS) {
    assert.ok(ALL_CODE_WORDS.includes(word), `ALL_CODE_WORDS should include ${word}`);
  }

  // Code generation for general space uses GENERAL_CODE_WORDS and round-trips
  for (let i = 0; i < 50; i++) {
    const code = generateCode(undefined, "general");
    const [prefix, suffix] = code.split("-");
    assert.ok(GENERAL_CODE_WORDS.includes(prefix), `Prefix ${prefix} should be in GENERAL_CODE_WORDS`);
    assert.match(suffix, /^[2-9BCDFGHJKMNPQRSTVWXZ]{4}$/);
    assert.deepEqual(extractCodes(`Join our club: ${code}`), [code]);
  }
});

test("cleanPostMeta cleans and preserves work metadata and existing fields", () => {
  assert.equal(cleanPostMeta(null), null);
  assert.equal(cleanPostMeta(undefined), null);
  assert.equal(cleanPostMeta("bad"), null);
  assert.equal(cleanPostMeta([]), null);
  assert.equal(cleanPostMeta({}), null);

  // All valid fields
  const full = {
    sourceKind: "Devotional",
    sourceId: "src-12345",
    assigneeName: " Alice Engineer ",
    dueText: " End of sprint ",
    assigneeUid: "  uid-alice-42  ",
    dueAt: 1_800_000_000_000,
    priority: "  URGENT  ",
    blockedOn: "  API deployment to staging  ",
    decisionOwner: "  Chief Architect  ",
    meetingDate: "  2026-11-20  ",
  };
  const cleaned = cleanPostMeta(full);
  assert.deepEqual(cleaned, {
    sourceKind: "Devotional",
    sourceId: "src-12345",
    assigneeName: " Alice Engineer ",
    dueText: " End of sprint ",
    assigneeUid: "uid-alice-42",
    dueAt: 1_800_000_000_000,
    priority: "urgent",
    blockedOn: "API deployment to staging",
    decisionOwner: "Chief Architect",
    meetingDate: "2026-11-20",
  });

  // Priority accepted values and case insensitivity
  for (const prio of ["low", "normal", "high", "urgent", " LOW ", "Normal", "HIGH"]) {
    assert.equal(cleanPostMeta({ priority: prio }).priority, prio.trim().toLowerCase());
  }
  assert.equal(cleanPostMeta({ priority: "critical" }), null);
  assert.equal(cleanPostMeta({ priority: "" }), null);

  // Truncation & empty string stripping
  const truncated = cleanPostMeta({
    assigneeUid: "u".repeat(100),
    blockedOn: "b".repeat(120),
    decisionOwner: "d".repeat(100),
    meetingDate: "m".repeat(80),
  });
  assert.equal(truncated.assigneeUid.length, 64);
  assert.equal(truncated.blockedOn.length, 80);
  assert.equal(truncated.decisionOwner.length, 60);
  assert.equal(truncated.meetingDate.length, 40);

  // Whitespace-only strings ignored (returns null if no other valid keys)
  assert.equal(cleanPostMeta({ assigneeUid: "   ", blockedOn: "  ", decisionOwner: " ", meetingDate: " " }), null);

  // dueAt: only safe positive integers
  assert.equal(cleanPostMeta({ dueAt: 100 }).dueAt, 100);
  assert.equal(cleanPostMeta({ dueAt: 0 }), null);
  assert.equal(cleanPostMeta({ dueAt: -100 }), null);
  assert.equal(cleanPostMeta({ dueAt: 123.45 }), null);
  assert.equal(cleanPostMeta({ dueAt: "100" }), null);
  assert.equal(cleanPostMeta({ dueAt: Infinity }), null);
});

test("shapePoll supports optional closesAt deadline", () => {
  // Omitted or null closesAt preserves existing return object shape without closesAt
  const p1 = shapePoll({ question: "Release today?", options: ["Yes", "No"] });
  assert.equal("closesAt" in p1, false);
  assert.deepEqual(p1.optionIds, ["o0", "o1"]);

  const p2 = shapePoll({ question: "Release today?", options: ["Yes", "No"], closesAt: null });
  assert.equal("closesAt" in p2, false);

  // Valid positive safe integer closesAt
  const p3 = shapePoll({ question: "Release today?", options: ["Yes", "No"], closesAt: 1_800_000_000_000 });
  assert.equal(p3.closesAt, 1_800_000_000_000);

  // Invalid closesAt throws bad_request
  assert.throws(() => shapePoll({ question: "Q?", options: ["A", "B"], closesAt: 0 }), /Poll deadline must be a valid time/);
  assert.throws(() => shapePoll({ question: "Q?", options: ["A", "B"], closesAt: -100 }), /Poll deadline must be a valid time/);
  assert.throws(() => shapePoll({ question: "Q?", options: ["A", "B"], closesAt: 123.45 }), /Poll deadline must be a valid time/);
  assert.throws(() => shapePoll({ question: "Q?", options: ["A", "B"], closesAt: "tomorrow" }), /Poll deadline must be a valid time/);
});

