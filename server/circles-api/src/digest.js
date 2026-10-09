// Daily prayer digest (Cron Trigger). Sends counts only, never who prayed.
import { w } from "./firestore.js";
import { pushToUsers } from "./fcm.js";

export async function runDigest({ db, fcm, env }) {
  const max = Math.max(1, parseInt(env.NOTIFY_MAX || "30", 10) || 30);
  const tallies = await db.query({ collection: "prayerTally", where: [["today", ">", 0]], limit: Math.min(max, 40) });
  if (!tallies.length) return { authors: 0 };
  const auths = await db.batchGet(tallies.map((t) => `circles/${t.data.circleId}/postAuthors/${t.data.postId}`));
  const perAuthor = new Map();
  tallies.forEach((t, i) => {
    const a = auths[i]?.data.authorUid;
    if (a) perAuthor.set(a, (perAuthor.get(a) || 0) + t.data.today);
  });
  for (const [uid, n] of perAuthor) {
    await pushToUsers({ db, fcm, uids: [uid], max: 5, message: {
      title: "Prayer", body: n === 1 ? "1 person prayed for you today" : `${n} people prayed for you today`, data: { kind: "digest" } } });
  }
  // Subtract exactly what we reported; prayers that arrive meanwhile stay counted for tomorrow.
  await db.commit(tallies.map((t) => w.update(`prayerTally/${t.id}`, {}, { inc: { today: -t.data.today } })));
  return { authors: perAuthor.size };
}
