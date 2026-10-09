// Minimal Firestore REST client: value codec, write builders, transactions, queries.
import { getAccessToken } from "./google.js";

// ---------- value codec ----------
export function encodeValue(v) {
  if (v === null || v === undefined) return { nullValue: null };
  if (typeof v === "string") return { stringValue: v };
  if (typeof v === "boolean") return { booleanValue: v };
  if (typeof v === "number") return Number.isInteger(v) ? { integerValue: String(v) } : { doubleValue: v };
  if (v instanceof Date) return { timestampValue: v.toISOString() };
  if (Array.isArray(v)) return { arrayValue: { values: v.map(encodeValue) } };
  if (typeof v === "object") return { mapValue: { fields: encodeFields(v) } };
  throw new Error("unsupported value");
}
export function encodeFields(obj) {
  const f = {};
  for (const [k, v] of Object.entries(obj)) if (v !== undefined) f[k] = encodeValue(v);
  return f;
}
export function decodeValue(v) {
  if ("stringValue" in v) return v.stringValue;
  if ("integerValue" in v) return Number(v.integerValue);
  if ("doubleValue" in v) return v.doubleValue;
  if ("booleanValue" in v) return v.booleanValue;
  if ("nullValue" in v) return null;
  if ("timestampValue" in v) return new Date(v.timestampValue);
  if ("arrayValue" in v) return (v.arrayValue.values || []).map(decodeValue);
  if ("mapValue" in v) return decodeFields(v.mapValue.fields || {});
  return null;
}
export function decodeFields(f) {
  const o = {};
  for (const [k, v] of Object.entries(f || {})) o[k] = decodeValue(v);
  return o;
}

// ---------- write builders (plain descriptions; the client turns them into REST writes) ----------
export const w = {
  /** Create; fails with ALREADY_EXISTS if present. */
  create: (path, data) => ({ op: "create", path, data }),
  /** Upsert, replacing the whole document. */
  set: (path, data) => ({ op: "set", path, data }),
  /** Merge top-level fields (mask = keys of data) and apply transforms. mustExist defaults true. */
  update: (path, data = {}, { inc = {}, removeFromArray = {}, mustExist = true } = {}) => ({ op: "update", path, data, inc, removeFromArray, mustExist }),
  del: (path) => ({ op: "delete", path }),
};

export class FirestoreError extends Error {
  constructor(status, code, message) { super(message); this.status = status; this.code = code; }
}

const SAFE_FIELD = /^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)*$/;

export class Firestore {
  constructor({ projectId, env, fetchImpl = fetch }) {
    this.projectId = projectId; this.env = env; this.fetch = fetchImpl;
    this.root = `projects/${projectId}/databases/(default)/documents`;
    this.base = `https://firestore.googleapis.com/v1/${this.root}`;
  }
  name(path) { return `${this.root}/${path}`; }

  async req(method, url, body) {
    const token = await getAccessToken(this.env, this.fetch);
    const res = await this.fetch(url, {
      method,
      headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
      body: body ? JSON.stringify(body) : undefined,
    });
    if (res.status === 404 && method === "GET") return null;
    const text = await res.text();
    let j = null; try { j = text ? JSON.parse(text) : null; } catch { /* ignore */ }
    if (!res.ok) {
      const e = j?.error || (Array.isArray(j) ? j[0]?.error : null) || {};
      throw new FirestoreError(res.status, e.status || "UNKNOWN", e.message || `Firestore ${res.status}`);
    }
    return j;
  }

  wrap(d) {
    const path = d.name.slice(this.root.length + 1);
    return { id: path.split("/").pop(), path, data: decodeFields(d.fields), updateTime: d.updateTime };
  }

  async get(path, tx) {
    const d = await this.req("GET", `${this.base}/${path}${tx ? `?transaction=${encodeURIComponent(tx)}` : ""}`);
    return d ? this.wrap(d) : null;
  }

  /** Returns an array aligned with paths (null where missing). */
  async batchGet(paths, tx) {
    if (!paths.length) return [];
    const r = await this.req("POST", `${this.base}:batchGet`, { documents: paths.map((p) => this.name(p)), ...(tx ? { transaction: tx } : {}) });
    const byName = new Map();
    for (const e of r) if (e.found) byName.set(e.found.name, this.wrap(e.found));
    return paths.map((p) => byName.get(this.name(p)) || null);
  }

  structured({ collection, where = [], limit }) {
    const ops = { "==": "EQUAL", ">": "GREATER_THAN", in: "IN" };
    const filters = where.map(([f, op, v]) => ({ fieldFilter: { field: { fieldPath: f }, op: ops[op], value: encodeValue(v) } }));
    const q = { from: [{ collectionId: collection }] };
    if (filters.length === 1) q.where = filters[0];
    else if (filters.length > 1) q.where = { compositeFilter: { op: "AND", filters } };
    if (limit) q.limit = limit;
    return q;
  }

  async query({ parent = "", collection, where, limit }, tx) {
    const url = `${this.base}${parent ? "/" + parent : ""}:runQuery`;
    const r = await this.req("POST", url, { structuredQuery: this.structured({ collection, where, limit }), ...(tx ? { transaction: tx } : {}) });
    return r.filter((x) => x.document).map((x) => this.wrap(x.document));
  }

  async count({ parent = "", collection, where }) {
    const url = `${this.base}${parent ? "/" + parent : ""}:runAggregationQuery`;
    const r = await this.req("POST", url, { structuredAggregationQuery: { structuredQuery: this.structured({ collection, where }), aggregations: [{ alias: "n", count: {} }] } });
    return Number(r?.[0]?.result?.aggregateFields?.n?.integerValue || 0);
  }

  toRestWrite(x) {
    const name = this.name(x.path);
    if (x.op === "delete") return { delete: name };
    if (x.op === "create") return { update: { name, fields: encodeFields(x.data) }, currentDocument: { exists: false } };
    if (x.op === "set") return { update: { name, fields: encodeFields(x.data) } };
    const keys = Object.keys(x.data).filter((k) => x.data[k] !== undefined);
    for (const k of [...keys, ...Object.keys(x.inc), ...Object.keys(x.removeFromArray)]) if (!SAFE_FIELD.test(k)) throw new Error("unsafe field path");
    const updateTransforms = [
      ...Object.entries(x.inc).map(([fieldPath, by]) => ({ fieldPath, increment: { integerValue: String(by) } })),
      ...Object.entries(x.removeFromArray).map(([fieldPath, values]) => ({ fieldPath, removeAllFromArray: { values: values.map(encodeValue) } })),
    ];
    const nested = {};
    for (const k of keys) { // dotted keys become nested maps (mask path stays dotted)
      const parts = k.split("."); let o = nested;
      parts.slice(0, -1).forEach((p) => { o = o[p] = o[p] || {}; });
      o[parts[parts.length - 1]] = x.data[k];
    }
    const out = { update: { name, fields: encodeFields(nested) }, updateMask: { fieldPaths: keys } };
    if (updateTransforms.length) out.updateTransforms = updateTransforms;
    if (x.mustExist) out.currentDocument = { exists: true };
    return out;
  }

  /** Atomic multi-document write. */
  async commit(writes, tx) {
    if (!writes.length) return;
    await this.req("POST", `${this.base}:commit`, { writes: writes.map((x) => this.toRestWrite(x)), ...(tx ? { transaction: tx } : {}) });
  }

  /** fn(tx) -> writes[]; tx.get / tx.query read under the transaction. Retries on contention. */
  async runTx(fn, attempts = 4) {
    for (let i = 0; i < attempts; i++) {
      const { transaction } = await this.req("POST", `${this.base}:beginTransaction`, { options: { readWrite: {} } });
      const tx = { get: (p) => this.get(p, transaction), query: (q) => this.query(q, transaction) };
      let writes;
      try {
        writes = await fn(tx);
      } catch (e) {
        this.req("POST", `${this.base}:rollback`, { transaction }).catch(() => {});
        throw e;
      }
      try {
        await this.commit(writes || [], transaction);
        return;
      } catch (e) {
        if (e instanceof FirestoreError && (e.code === "ABORTED" || e.status === 409 && e.code !== "ALREADY_EXISTS") && i < attempts - 1) continue;
        throw e;
      }
    }
  }
}
