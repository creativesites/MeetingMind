import test, { before, after, beforeEach } from "node:test";
import { readFileSync } from "node:fs";
import { initializeTestEnvironment, assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc, updateDoc, deleteDoc, getDocs, collection, serverTimestamp } from "firebase/firestore";

const rules = readFileSync(new URL("./firestore.rules", import.meta.url), "utf8");
const [host, port] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8089").split(":");
let env;
before(async () => { env = await initializeTestEnvironment({ projectId: "demo-circles", firestore: { rules, host, port: Number(port) } }); });
after(async () => { await env?.cleanup(); });

const C = "c1", P = "p1";
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, `circles/${C}`), { name: "Cell", ownerUid: "owner", memberCount: 3 });
    await setDoc(doc(db, `circles/${C}/members/owner`), { role: "owner", displayName: "O" });
    await setDoc(doc(db, `circles/${C}/members/admin`), { role: "admin", displayName: "A" });
    await setDoc(doc(db, `circles/${C}/members/member`), { role: "member", displayName: "M" });
    await setDoc(doc(db, `circles/${C}/posts/${P}`), { type: "prayer", body: "pray", anonymous: true, deleted: false, counts: { prayed: 0 } });
    await setDoc(doc(db, `circles/${C}/posts/gone`), { type: "prayer", body: "", deleted: true });
    await setDoc(doc(db, `circles/${C}/posts/${P}/comments/cm1`), { authorUid: "member", body: "x", createdAt: new Date() });
    await setDoc(doc(db, `circles/${C}/postAuthors/${P}`), { authorUid: "member" });
    await setDoc(doc(db, `circles/${C}/pending/pp`), { type: "prayer", body: "wait", anonymous: true });
    await setDoc(doc(db, `circles/${C}/posts/${P}/prayers/member`), { createdAt: new Date() });
    await setDoc(doc(db, `circles/${C}/posts/${P}/prayers/other`), { createdAt: new Date() });
    await setDoc(doc(db, `circles/${C}/messages/m1`), { authorUid: "member", displayName: "M", kind: "text", text: "hello", createdAt: new Date(), deleted: false });
    await setDoc(doc(db, `circles/${C}/messages/mp`), { authorUid: "admin", kind: "poll", text: "Night?", pollId: "pl1", createdAt: new Date(), deleted: false });
    await setDoc(doc(db, `circles/${C}/polls/pl1`), { question: "Night?", options: [{ id: "o0", text: "Tue" }, { id: "o1", text: "Thu" }, { id: "o2", text: "Sat" }], optionIds: ["o0", "o1", "o2"], multi: false, closed: false });
    await setDoc(doc(db, `circles/${C}/polls/plm`), { question: "Pick", options: [], optionIds: ["o0", "o1", "o2"], multi: true, closed: false });
    await setDoc(doc(db, `circles/${C}/polls/plc`), { question: "Done", optionIds: ["o0", "o1"], multi: false, closed: true });
    await setDoc(doc(db, `circles/${C}/chains/ch1`), { title: "Sam", hours: 24, endsAt: new Date(Date.now() + 3_600_000) });
    await setDoc(doc(db, `circles/${C}/chains/old`), { title: "Old", hours: 24, endsAt: new Date(Date.now() - 3_600_000) });
    await setDoc(doc(db, `circles/${C}/chains/ch1/slots/5`), { authorUid: "admin", displayName: "A", createdAt: new Date() });
    await setDoc(doc(db, `invites/GRACE-7K2Q`), { circleId: C });
    await setDoc(doc(db, `userMeta/member`), { circleCount: 1 });
    await setDoc(doc(db, `users/member`), { displayName: "M", fcmTokens: ["t"] });
  });
});
const as = (uid) => env.authenticatedContext(uid).firestore();
const anon = () => env.unauthenticatedContext().firestore();

test("non-member and signed-out cannot read circle, posts, comments, members", async () => {
  for (const db of [as("stranger"), anon()]) {
    await assertFails(getDoc(doc(db, `circles/${C}`)));
    await assertFails(getDoc(doc(db, `circles/${C}/posts/${P}`)));
    await assertFails(getDocs(collection(db, `circles/${C}/posts`)));
    await assertFails(getDocs(collection(db, `circles/${C}/posts/${P}/comments`)));
    await assertFails(getDocs(collection(db, `circles/${C}/members`)));
  }
});
test("member can read circle, posts, comments, members; cannot list circles", async () => {
  const db = as("member");
  await assertSucceeds(getDoc(doc(db, `circles/${C}`)));
  await assertSucceeds(getDocs(collection(db, `circles/${C}/posts`)));
  await assertSucceeds(getDocs(collection(db, `circles/${C}/posts/${P}/comments`)));
  await assertSucceeds(getDocs(collection(db, `circles/${C}/members`)));
  await assertFails(getDocs(collection(db, "circles")));
});
test("nobody can read or write postAuthors (member, admin, owner)", async () => {
  for (const u of ["member", "admin", "owner"]) {
    await assertFails(getDoc(doc(as(u), `circles/${C}/postAuthors/${P}`)));
    await assertFails(getDocs(collection(as(u), `circles/${C}/postAuthors`)));
    await assertFails(setDoc(doc(as(u), `circles/${C}/postAuthors/new`), { authorUid: u }));
  }
});
test("pending readable by admins only, and carries no author", async () => {
  await assertSucceeds(getDoc(doc(as("admin"), `circles/${C}/pending/pp`)));
  await assertSucceeds(getDoc(doc(as("owner"), `circles/${C}/pending/pp`)));
  await assertFails(getDoc(doc(as("member"), `circles/${C}/pending/pp`)));
  await assertFails(getDoc(doc(as("stranger"), `circles/${C}/pending/pp`)));
  await assertFails(setDoc(doc(as("member"), `circles/${C}/pending/mine`), { body: "x", authorUid: "member" }));
  await assertFails(setDoc(doc(as("admin"), `circles/${C}/pending/mine`), { body: "x" }));
  // Admins can read it, and the Worker never writes author fields there (unit-tested); sanity-check the shape admins see.
  const snap = await getDoc(doc(as("admin"), `circles/${C}/pending/pp`));
  if ("authorUid" in snap.data() || "authorName" in snap.data()) throw new Error("pending doc exposes an author");
});
test("clients cannot write circles, members, roles, posts, counters directly", async () => {
  const db = as("member");
  await assertFails(setDoc(doc(db, "circles/new"), { name: "x", ownerUid: "member" }));
  await assertFails(updateDoc(doc(db, `circles/${C}`), { memberCount: 1 }));
  await assertFails(updateDoc(doc(db, `circles/${C}/members/member`), { role: "owner" }));
  await assertFails(setDoc(doc(db, `circles/${C}/members/stranger`), { role: "member" }));
  await assertFails(setDoc(doc(db, `circles/${C}/posts/new`), { type: "prayer", body: "x", authorUid: "member" }));
  await assertFails(updateDoc(doc(db, `circles/${C}/posts/${P}`), { "counts.prayed": 99 }));
  await assertFails(deleteDoc(doc(db, `circles/${C}/posts/${P}`)));
  await assertFails(updateDoc(doc(as("admin"), `circles/${C}/posts/${P}`), { deleted: true }));
  await assertFails(deleteDoc(doc(as("owner"), `circles/${C}/members/member`)));
});
test("invites, userMeta, prayerTally unreadable and unwritable", async () => {
  for (const p of ["invites/GRACE-7K2Q", "userMeta/member", "prayerTally/x"]) {
    await assertFails(getDoc(doc(as("member"), p)));
    await assertFails(setDoc(doc(as("member"), p), { circleCount: 0 }));
  }
  await assertFails(setDoc(doc(as("stranger"), "invites/HOPE-2222"), { circleId: C }));
});
test("comments: own authorUid ok; spoofing / extra fields / non-members / bad time rejected", async () => {
  const path = (id) => `circles/${C}/posts/${P}/comments/${id}`;
  await assertSucceeds(setDoc(doc(as("member"), path("a")), { authorUid: "member", body: "amen", createdAt: serverTimestamp() }));
  await assertFails(setDoc(doc(as("member"), path("b")), { authorUid: "admin", body: "spoof", createdAt: serverTimestamp() }));
  await assertFails(setDoc(doc(as("member"), path("c")), { authorUid: "member", body: "x", createdAt: serverTimestamp(), role: "admin" }));
  await assertFails(setDoc(doc(as("member"), path("d")), { authorUid: "member", body: "x", createdAt: new Date("2020-01-01") }));
  await assertFails(setDoc(doc(as("stranger"), path("e")), { authorUid: "stranger", body: "x", createdAt: serverTimestamp() }));
  await assertFails(setDoc(doc(as("member"), path("f")), { authorUid: "member", body: "", createdAt: serverTimestamp() }));
  await assertFails(setDoc(doc(as("member"), path("g")), { authorUid: "member", body: "x".repeat(2001), createdAt: serverTimestamp() }));
  await assertFails(setDoc(doc(as("member"), `circles/${C}/posts/gone/comments/h`), { authorUid: "member", body: "x", createdAt: serverTimestamp() }));
  // edit/delete: only own text; authorUid immutable
  await assertSucceeds(updateDoc(doc(as("member"), path("cm1")), { body: "edited" }));
  await assertFails(updateDoc(doc(as("member"), path("cm1")), { authorUid: "admin" }));
  await assertFails(updateDoc(doc(as("admin"), path("cm1")), { body: "hax" }));
  await assertSucceeds(deleteDoc(doc(as("admin"), path("cm1"))));
});
test("reactions: own only, valid kind, spoofing rejected", async () => {
  const r = (uid) => `circles/${C}/posts/${P}/reactions/${uid}`;
  await assertSucceeds(setDoc(doc(as("member"), r("member")), { authorUid: "member", kind: "amen", createdAt: serverTimestamp() }));
  await assertFails(setDoc(doc(as("member"), r("admin")), { authorUid: "admin", kind: "amen", createdAt: serverTimestamp() }));
  await assertFails(setDoc(doc(as("admin"), r("admin")), { authorUid: "member", kind: "amen", createdAt: serverTimestamp() }));
  await assertFails(setDoc(doc(as("admin"), r("admin")), { authorUid: "admin", kind: "evil", createdAt: serverTimestamp() }));
  await assertFails(getDocs(collection(as("member"), `circles/${C}/posts/${P}/reactions`)));
});
test("prayers: a member sees only their own marker, never who else prayed", async () => {
  await assertSucceeds(getDoc(doc(as("member"), `circles/${C}/posts/${P}/prayers/member`)));
  await assertFails(getDoc(doc(as("member"), `circles/${C}/posts/${P}/prayers/other`)));
  await assertFails(getDocs(collection(as("owner"), `circles/${C}/posts/${P}/prayers`)));
  await assertFails(setDoc(doc(as("admin"), `circles/${C}/posts/${P}/prayers/admin`), { createdAt: new Date() }));
});
test("profile: own displayName/photoUrl only; fcmTokens, others' docs, extra keys denied", async () => {
  await assertSucceeds(getDoc(doc(as("member"), "users/member")));
  await assertFails(getDoc(doc(as("admin"), "users/member")));
  await assertSucceeds(updateDoc(doc(as("member"), "users/member"), { displayName: "New" }));
  await assertFails(updateDoc(doc(as("member"), "users/member"), { fcmTokens: ["x"] }));
  await assertFails(updateDoc(doc(as("admin"), "users/member"), { displayName: "hax" }));
  await assertSucceeds(setDoc(doc(as("fresh"), "users/fresh"), { displayName: "F" }));
  await assertFails(setDoc(doc(as("fresh2"), "users/fresh2"), { displayName: "F", fcmTokens: ["x"] }));
  await assertFails(setDoc(doc(as("fresh3"), "users/fresh3"), { displayName: "x".repeat(41) }));
});
test("reports readable by admins only; old encrypted events path is denied; default deny", async () => {
  await env.withSecurityRulesDisabled((c) => setDoc(doc(c.firestore(), `circles/${C}/reports/r1`), { postId: P }));
  await assertSucceeds(getDoc(doc(as("admin"), `circles/${C}/reports/r1`)));
  await assertFails(getDoc(doc(as("member"), `circles/${C}/reports/r1`)));
  await assertFails(setDoc(doc(as("member"), `circles/${C}/events/e1`), { uid: "member", ts: 1, iv: "a", ciphertext: "b" }));
  await assertFails(getDoc(doc(as("member"), "anything/else")));
});

const M = (id) => `circles/${C}/messages/${id}`;
const msg = (over = {}) => ({ authorUid: "member", displayName: "M", kind: "text", text: "hi", createdAt: serverTimestamp(), deleted: false, ...over });

test("chat: members only read; only members write; author == uid; time and size checked", async () => {
  await assertSucceeds(getDocs(collection(as("member"), `circles/${C}/messages`)));
  await assertFails(getDocs(collection(as("stranger"), `circles/${C}/messages`)));
  await assertFails(getDoc(doc(anon(), M("m1"))));
  await assertSucceeds(setDoc(doc(as("member"), M("n1")), msg()));
  await assertFails(setDoc(doc(as("stranger"), M("n2")), msg({ authorUid: "stranger" })));
  await assertFails(setDoc(doc(as("member"), M("n3")), msg({ authorUid: "admin" })));
  await assertFails(setDoc(doc(as("member"), M("n4")), msg({ createdAt: new Date("2020-01-01") })));
  await assertFails(setDoc(doc(as("member"), M("n5")), msg({ text: "" })));
  await assertFails(setDoc(doc(as("member"), M("n6")), msg({ text: "x".repeat(2001) })));
  await assertSucceeds(setDoc(doc(as("member"), M("n7")), msg({ text: "x".repeat(2000) })));
  await assertFails(setDoc(doc(as("member"), M("n8")), msg({ role: "admin" })));
  await assertFails(setDoc(doc(as("member"), M("n9")), msg({ deleted: true })));
  await assertFails(setDoc(doc(as("member"), M("n10")), msg({ displayName: "x".repeat(41) })));
});

test("chat: Worker-only kinds (poll, celebration, chain) cannot be forged by clients", async () => {
  for (const kind of ["poll", "celebration", "chain", "system"]) {
    await assertFails(setDoc(doc(as("member"), M("f" + kind)), msg({ kind, pollId: "pl1" })));
  }
  await assertFails(setDoc(doc(as("owner"), M("fowner")), msg({ authorUid: "owner", kind: "poll" })));
});

test("chat: replies must point at an existing message; cards must be well-formed", async () => {
  await assertSucceeds(setDoc(doc(as("member"), M("r1")), msg({ kind: "reply", replyTo: "m1" })));
  await assertFails(setDoc(doc(as("member"), M("r2")), msg({ kind: "reply", replyTo: "ghost" })));
  await assertFails(setDoc(doc(as("member"), M("r3")), msg({ kind: "reply" })));
  await assertFails(setDoc(doc(as("member"), M("r4")), msg({ replyTo: "m1" }))); // replyTo only on kind reply
  const card = { templateId: "dawn", text: "Be still", mood: "calm" };
  await assertSucceeds(setDoc(doc(as("member"), M("c1")), msg({ kind: "card", text: "", card })));
  await assertFails(setDoc(doc(as("member"), M("c2")), msg({ kind: "card", text: "" })));
  await assertFails(setDoc(doc(as("member"), M("c3")), msg({ kind: "card", text: "", card: { ...card, extra: 1 } })));
  await assertFails(setDoc(doc(as("member"), M("c4")), msg({ kind: "card", text: "", card: { templateId: "dawn", text: "x".repeat(601) } })));
  await assertFails(setDoc(doc(as("member"), M("c5")), msg({ card }))); // card on a text message
});

test("chat: edit own text only; soft-delete own; admins soft-delete any; no hard delete", async () => {
  await assertSucceeds(updateDoc(doc(as("member"), M("m1")), { text: "edited", editedAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(as("member"), M("m1")), { text: "edited" })); // editedAt required
  await assertFails(updateDoc(doc(as("member"), M("m1")), { text: "", editedAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(as("member"), M("m1")), { authorUid: "admin", text: "x", editedAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(as("admin"), M("m1")), { text: "admin edit", editedAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(as("stranger"), M("m1")), { deleted: true, text: "" }));
  await assertFails(updateDoc(doc(as("member"), M("mp")), { deleted: true, text: "" })); // not theirs
  await assertFails(updateDoc(doc(as("member"), M("m1")), { deleted: true, text: "still here" }));
  await assertSucceeds(updateDoc(doc(as("admin"), M("m1")), { deleted: true, text: "" }));
  await assertFails(updateDoc(doc(as("member"), M("m1")), { text: "zombie", editedAt: serverTimestamp() })); // deleted: no edits
  await assertSucceeds(updateDoc(doc(as("owner"), M("mp")), { deleted: true, text: "" }));
  await assertFails(deleteDoc(doc(as("owner"), M("mp"))));
  await assertFails(deleteDoc(doc(as("member"), M("m1"))));
});

test("chat reactions: one per member (doc id == uid), allowed emoji, readable by members", async () => {
  const r = (id, uid) => doc(as(id), `${M("m1")}/reactions/${uid}`);
  await assertSucceeds(setDoc(r("member", "member"), { authorUid: "member", emoji: "🙏", createdAt: serverTimestamp() }));
  await assertSucceeds(updateDoc(r("member", "member"), { emoji: "❤️" }));
  await assertFails(setDoc(r("member", "admin"), { authorUid: "admin", emoji: "🙏", createdAt: serverTimestamp() }));
  await assertFails(setDoc(r("admin", "admin"), { authorUid: "member", emoji: "🙏", createdAt: serverTimestamp() }));
  await assertFails(setDoc(r("admin", "admin"), { authorUid: "admin", emoji: "💩", createdAt: serverTimestamp() }));
  await assertFails(setDoc(r("stranger", "stranger"), { authorUid: "stranger", emoji: "🙏", createdAt: serverTimestamp() }));
  await assertSucceeds(getDocs(collection(as("owner"), `${M("m1")}/reactions`)));
  await assertFails(getDocs(collection(as("stranger"), `${M("m1")}/reactions`)));
  await assertFails(deleteDoc(r("admin", "member")));
  await assertSucceeds(deleteDoc(r("member", "member")));
  await env.withSecurityRulesDisabled((c) => updateDoc(doc(c.firestore(), M("m1")), { deleted: true, text: "" }));
  await assertFails(setDoc(r("admin", "admin"), { authorUid: "admin", emoji: "🙏", createdAt: serverTimestamp() })); // deleted message
});

test("poll votes: doc id == uid, open polls only, valid options, single vs multi, no forged polls", async () => {
  const v = (poll, uid, as_ = uid) => doc(as(as_), `circles/${C}/polls/${poll}/votes/${uid}`);
  const vote = (choices, uid = "member") => ({ authorUid: uid, choices, updatedAt: serverTimestamp() });
  await assertSucceeds(setDoc(v("pl1", "member"), vote(["o1"])));
  await assertSucceeds(setDoc(v("pl1", "member"), vote(["o2"]))); // change vote
  await assertFails(setDoc(v("pl1", "member"), vote(["o0", "o1"]))); // single choice
  await assertFails(setDoc(v("pl1", "admin", "member"), vote(["o0"], "admin"))); // someone else's doc
  await assertFails(setDoc(v("pl1", "admin"), vote(["o0"], "member"))); // spoofed authorUid
  await assertFails(setDoc(v("pl1", "admin"), vote(["o9"], "admin"))); // not an option
  await assertFails(setDoc(v("pl1", "admin"), vote([], "admin")));
  await assertFails(setDoc(v("pl1", "stranger"), vote(["o0"], "stranger")));
  await assertFails(setDoc(v("plc", "member"), vote(["o0"]))); // closed
  await assertFails(setDoc(v("nope", "member"), vote(["o0"]))); // no such poll
  await assertSucceeds(setDoc(v("plm", "member"), vote(["o0", "o2"]))); // multi
  await assertFails(setDoc(v("plm", "admin"), vote(["o0", "o0"], "admin"))); // duplicate choices
  await assertFails(setDoc(v("plm", "owner"), { ...vote(["o0"], "owner"), extra: 1 }));
  await assertSucceeds(getDocs(collection(as("owner"), `circles/${C}/polls/plm/votes`)));
  await assertFails(getDocs(collection(as("stranger"), `circles/${C}/polls/plm/votes`)));
  await assertFails(setDoc(doc(as("admin"), `circles/${C}/polls/new`), { question: "x", optionIds: ["o0"], closed: false })); // polls are Worker-made
  await assertFails(updateDoc(doc(as("owner"), `circles/${C}/polls/pl1`), { closed: true }));
});

test("prayer chain slots: valid hour, open chain, claim once, release own, Worker-only chains", async () => {
  const s = (chain, hour, uid) => doc(as(uid), `circles/${C}/chains/${chain}/slots/${hour}`);
  const claim = (uid) => ({ authorUid: uid, displayName: uid, createdAt: serverTimestamp() });
  await assertSucceeds(setDoc(s("ch1", "6", "member"), claim("member")));
  await assertFails(setDoc(s("ch1", "6", "owner"), claim("owner"))); // already claimed (update denied)
  await assertFails(setDoc(s("ch1", "5", "member"), claim("member"))); // taken by admin
  await assertFails(setDoc(s("ch1", "24", "member"), claim("member"))); // bad hour
  await assertFails(setDoc(s("ch1", "07", "member"), claim("member")));
  await assertFails(setDoc(s("ch1", "8", "member"), claim("owner"))); // spoof
  await assertFails(setDoc(s("ch1", "9", "stranger"), claim("stranger")));
  await assertFails(setDoc(s("old", "1", "member"), claim("member"))); // chain ended
  await assertSucceeds(getDocs(collection(as("member"), `circles/${C}/chains/ch1/slots`)));
  await assertFails(getDocs(collection(as("stranger"), `circles/${C}/chains/ch1/slots`)));
  await assertFails(deleteDoc(s("ch1", "5", "member"))); // someone else's
  await assertSucceeds(deleteDoc(s("ch1", "6", "member")));
  await assertSucceeds(deleteDoc(s("ch1", "5", "owner"))); // admin clears
  await assertFails(setDoc(doc(as("owner"), `circles/${C}/chains/mine`), { title: "x", endsAt: new Date() }));
});
