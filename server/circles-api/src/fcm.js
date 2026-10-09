// FCM HTTP v1 sends + fan-out helpers. Notification text never contains post bodies or, for
// anonymous posts, any hint of the author.
import { getAccessToken } from "./google.js";
import { w } from "./firestore.js";

export class Fcm {
  constructor({ projectId, env, fetchImpl = fetch }) { this.projectId = projectId; this.env = env; this.fetch = fetchImpl; }
  /** -> "ok" | "dead" (token is invalid, remove it) | "error" */
  async send(token, { title, body, data = {} }) {
    try {
      const at = await getAccessToken(this.env, this.fetch);
      const res = await this.fetch(`https://fcm.googleapis.com/v1/projects/${this.projectId}/messages:send`, {
        method: "POST",
        headers: { Authorization: `Bearer ${at}`, "Content-Type": "application/json" },
        body: JSON.stringify({ message: { token, notification: { title, body }, data, android: { priority: "NORMAL" } } }),
      });
      if (res.ok) return "ok";
      const t = await res.text();
      if (res.status === 404 || /UNREGISTERED|INVALID_ARGUMENT/.test(t)) return "dead";
      return "error";
    } catch { return "error"; }
  }
}

/** Send to the given uids' tokens (max `max` sends). Prunes dead tokens. Never throws. */
export async function pushToUsers({ db, fcm, uids, message, max }) {
  try {
    if (!uids.length) return 0;
    const docs = await db.batchGet(uids.map((u) => `users/${u}`));
    const targets = [];
    for (const d of docs) for (const t of (d?.data.fcmTokens || [])) targets.push({ uid: d.id, token: t });
    const batch = targets.slice(0, max);
    const results = await Promise.all(batch.map((t) => fcm.send(t.token, message)));
    const dead = new Map();
    results.forEach((r, i) => { if (r === "dead") dead.set(batch[i].uid, [...(dead.get(batch[i].uid) || []), batch[i].token]); });
    if (dead.size) await db.commit([...dead].map(([uid, toks]) => w.update(`users/${uid}`, {}, { removeFromArray: { fcmTokens: toks } })));
    return results.filter((r) => r === "ok").length;
  } catch { return 0; }
}
