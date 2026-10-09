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
