// In-memory stand-in for src/firestore.js (same interface) so handlers can be tested end to end.
import { FirestoreError } from "../../src/firestore.js";

export class FakeDb {
  constructor() { this.docs = new Map(); }
  seed(path, data) { this.docs.set(path, structuredClone(data)); }
  dump(path) { return this.docs.get(path); }
  wrap(path) { return this.docs.has(path) ? { id: path.split("/").pop(), path, data: structuredClone(this.docs.get(path)) } : null; }
  async get(path) { return this.wrap(path); }
  async batchGet(paths) { return paths.map((p) => this.wrap(p)); }
  async query({ parent = "", collection, where = [], limit = 1000 }) {
    const prefix = parent ? `${parent}/${collection}/` : `${collection}/`;
    const out = [];
    for (const [p, d] of this.docs) {
      if (!p.startsWith(prefix) || p.slice(prefix.length).includes("/")) continue;
      const ok = where.every(([f, op, v]) => op === "==" ? d[f] === v : op === ">" ? d[f] > v : op === "in" ? v.includes(d[f]) : false);
      if (ok) out.push(this.wrap(p));
    }
    return out.slice(0, limit);
  }
  async count(q) { return (await this.query(q)).length; }
  async commit(writes) {
    for (const x of writes) {
      const has = this.docs.has(x.path);
      if (x.op === "create" && has) throw new FirestoreError(409, "ALREADY_EXISTS", "exists");
      if (x.op === "update" && x.mustExist && !has) throw new FirestoreError(404, "NOT_FOUND", "missing");
    }
    for (const x of writes) {
      if (x.op === "delete") { this.docs.delete(x.path); continue; }
      if (x.op === "create" || x.op === "set") { this.docs.set(x.path, structuredClone(x.data)); continue; }
      const d = this.docs.get(x.path) || {};
      const setPath = (o, k, f) => { const ps = k.split("."); ps.slice(0, -1).forEach((p) => { o = o[p] = o[p] || {}; }); const l = ps.at(-1); o[l] = f(o[l]); };
      for (const [k, v] of Object.entries(x.data)) setPath(d, k, () => structuredClone(v));
      for (const [k, by] of Object.entries(x.inc)) setPath(d, k, (c) => (c || 0) + by);
      for (const [k, vals] of Object.entries(x.removeFromArray)) setPath(d, k, (c) => (c || []).filter((e) => !vals.includes(e)));
      this.docs.set(x.path, d);
    }
  }
  async runTx(fn) { await this.commit((await fn({ get: (p) => this.get(p), query: (q) => this.query(q) })) || []); }
}

export class FakeKv {
  constructor() { this.m = new Map(); }
  async get(k) { return this.m.get(k) ?? null; }
  async put(k, v) { this.m.set(k, v); }
}
export class FakeFcm {
  constructor() { this.sent = []; }
  async send(token, msg) { this.sent.push({ token, msg }); return "ok"; }
}

export function makeCtx(db, kv, fcm, uid, body, over = {}) {
  const deferred = [];
  return {
    ctx: { env: { NOTIFY_MAX: "30", SERVICE_ACCOUNT_JSON: JSON.stringify({ client_email: "x", private_key: "k-secret" }) }, db, fcm, kv, uid, body, now: new Date("2026-10-09T14:03:22Z"), ip: "1.2.3.4", defer: (p) => deferred.push(p), ...over },
    flush: () => Promise.all(deferred),
  };
}
