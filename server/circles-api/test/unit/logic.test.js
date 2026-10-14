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

import { shapePoll, shapeCelebration } from "../../src/logic.js";
test("shapePoll builds stable option ids; shapeCelebration validates kind and companion", () => {
  const p = shapePoll({ question: " Night? ", options: [" Tue ", "Thu"], multi: undefined });
  assert.deepEqual(p.optionIds, ["o0", "o1"]); assert.equal(p.question, "Night?"); assert.equal(p.multi, false);
  assert.throws(() => shapePoll({ question: "Q", options: "ab" }));
  assert.throws(() => shapePoll({ question: "Q", options: ["a", "b"], multi: "yes" }));
  assert.deepEqual(shapeCelebration({ kind: "streak" }), { kind: "streak", text: "", companion: null });
  assert.throws(() => shapeCelebration({ kind: "x" }));
  assert.throws(() => shapeCelebration({ kind: "streak", text: "x".repeat(141) }));
});
