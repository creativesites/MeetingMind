// MeetingMind's managed Gemini access. The app calls this instead of Google when the person has a plan
// (or a free allowance). It holds the Google key, checks the plan with RevenueCat, meters usage, and
// refuses past the allowance. Counters live in a Durable Object per person, so they are atomic.
import * as L from './logic.js';

const json = (obj, status = 200, extra = {}) =>
  new Response(JSON.stringify(obj), { status, headers: { 'content-type': 'application/json', ...extra } });

const OP_ID = /^[\w-]{8,64}$/;
const MAX_BODY = 1_000_000; // generateContent bodies are small; audio goes through the upload routes

/** One person's usage for the current period. A Durable Object, so increments never race. */
export class UsageLedger {
  constructor(state) { this.state = state; }

  async snapshot(period) {
    const s = (await this.state.storage.get('usage')) ?? null;
    return s && s.period === period ? s : { period, audioSeconds: 0, actions: 0, ops: [] };
  }

  async fetch(request) {
    const path = new URL(request.url).pathname;
    const body = await request.json();
    if (path === '/get') return json(await this.snapshot(body.period));
    if (path === '/commit') {
      const s = await this.snapshot(body.period);
      // The same operation id is charged once, so a retried request is never billed twice.
      if (body.opId && s.ops.includes(body.opId)) return json({ ...s, duplicate: true });
      if (body.meter === 'audio') s.audioSeconds += body.amount; else s.actions += body.amount;
      if (body.opId) s.ops = [...s.ops, body.opId].slice(-200);
      await this.state.storage.put('usage', s);
      return json({ ...s, duplicate: false });
    }
    return json({ error: 'not_found' }, 404);
  }
}

/** The first N paying people get the founder bonus until a fixed date. One global instance. */
export class Founders {
  constructor(state) { this.state = state; }
  async fetch(request) {
    const { subject, limit, days, now } = await request.json();
    const list = (await this.state.storage.get('founders')) ?? [];
    let entry = list.find((f) => f.subject === subject);
    if (!entry && list.length < limit) {
      entry = { subject, firstSeenMs: now };
      list.push(entry);
      await this.state.storage.put('founders', list);
    }
    return json({ founderUntil: entry ? entry.firstSeenMs + days * 86_400_000 : null });
  }
}

const call = (ns, name, path, body) =>
  ns.get(ns.idFromName(name)).fetch(`https://do${path}`, { method: 'POST', body: JSON.stringify(body) }).then((r) => r.json());

/** The person's best active plan according to RevenueCat; cached for five minutes. Throws if RevenueCat can't be asked. */
async function rcPlan(rcUser, env, fetchImpl, now) {
  const cacheKey = `rc:${rcUser}`;
  const cached = env.CACHE ? await env.CACHE.get(cacheKey) : null;
  if (cached) return cached;
  const res = await fetchImpl(`https://api.revenuecat.com/v1/subscribers/${encodeURIComponent(rcUser)}`, {
    headers: { Authorization: `Bearer ${env.RC_SECRET_KEY}`, 'X-Platform': 'android' }
  });
  if (!res.ok) throw new Error(`revenuecat ${res.status}`);
  const plan = L.highestPlan((await res.json())?.subscriber?.entitlements, now);
  if (env.CACHE) await env.CACHE.put(cacheKey, plan, { expirationTtl: 300 });
  return plan;
}

export async function handle(request, env, deps = {}) {
  const fetchImpl = deps.fetch ?? fetch;
  const now = deps.now ?? Date.now();
  const url = new URL(request.url);

  const route = L.classifyRoute(request.method, url.pathname);
  if (!route) return json({ error: 'not_found' }, 404);
  if (!env.GEMINI_API_KEY) return json({ error: 'not_configured' }, 503);
  const allowances = L.parseAllowances(env.PLAN_ALLOWANCES);
  if (!allowances) return json({ error: 'not_configured' }, 503);

  const install = request.headers.get('X-Install-Id') ?? '';
  if (!L.INSTALL_ID.test(install)) return json({ error: 'bad_install_id' }, 400);
  const rcUser = request.headers.get('X-RC-User');
  if (rcUser && !L.RC_USER.test(rcUser)) return json({ error: 'bad_user_id' }, 400);

  let plan = 'free';
  if (rcUser && env.RC_SECRET_KEY) {
    // If RevenueCat can't be asked, say so. Treating a paying person as free during an outage would be wrong.
    try { plan = await rcPlan(rcUser, env, fetchImpl, now); }
    catch { return json({ error: 'entitlement_check_failed' }, 503, { 'Retry-After': '30' }); }
  }
  const subject = plan === 'free' ? `install:${install}` : `rc:${rcUser}`;
  const period = L.currentPeriod(now);

  let founderUntil = null;
  const founderLimit = Number(env.FOUNDER_LIMIT) || 0;
  if (plan !== 'free' && founderLimit > 0 && env.FOUNDERS) {
    founderUntil = (await call(env.FOUNDERS, 'global', '/register', { subject, limit: founderLimit, days: Number(env.FOUNDER_DAYS) || 90, now })).founderUntil;
  }
  const limits = L.limitsFor(plan, allowances, founderUntil, now, Number(env.FOUNDER_MULTIPLIER) || 1.5);
  let usage = await call(env.LEDGER, subject, '/get', { period: period.id });
  const headers = () => L.usageHeaders(plan, limits, usage, period, founderUntil);

  if (route.name === 'usage') {
    return json({ plan, limits, usage: { audioSeconds: usage.audioSeconds, actions: usage.actions }, periodEnd: period.endMs, founderUntil }, 200, headers());
  }

  // What a generate call carries decides which meter it draws on.
  let bodyText = null, kind = 'action';
  if (route.name === 'generate') {
    if (!L.modelAllowed(route.model, env.ALLOWED_MODELS)) return json({ error: 'model_not_allowed' }, 403, headers());
    bodyText = await request.text();
    if (bodyText.length > MAX_BODY) return json({ error: 'too_large' }, 413, headers());
    try { kind = L.classifyRequest(JSON.parse(bodyText)); } catch { return json({ error: 'bad_request' }, 400, headers()); }
  }
  const meter = L.meterFor(route, kind);
  const refused = L.refusal(meter, limits, usage);
  if (refused) return json({ error: refused.error }, refused.status, { ...headers(), 'X-Allowance': 'exhausted' });

  const google = `https://${L.GOOGLE_HOST}`;
  const key = env.GEMINI_API_KEY;

  if (route.name === 'upload-start') {
    const up = await fetchImpl(L.withKey(`${google}/upload/v1beta/files`, key), {
      method: 'POST',
      headers: pick(request.headers, ['content-type', 'x-goog-upload-protocol', 'x-goog-upload-command', 'x-goog-upload-header-content-length', 'x-goog-upload-header-content-type']),
      body: await request.text()
    });
    const out = { ...headers() };
    const uploadUrl = up.headers.get('x-goog-upload-url');
    // Google's own upload URL can carry the API key, so the client gets one that points back here.
    if (uploadUrl) out['X-Goog-Upload-URL'] = L.rewriteUploadUrl(uploadUrl, url.origin);
    return new Response(await up.text(), { status: up.status, headers: { 'content-type': up.headers.get('content-type') ?? 'application/json', ...out } });
  }

  if (route.name === 'upload-session') {
    const target = url.searchParams.get('u') ?? '';
    if (!L.isAllowedUploadUrl(target)) return json({ error: 'bad_upload_url' }, 400);
    const up = await fetchImpl(L.withKey(target, key), {
      method: 'POST',
      headers: pick(request.headers, ['content-type', 'x-goog-upload-offset', 'x-goog-upload-command']),
      body: request.body,
      duplex: 'half'
    });
    return new Response(await up.text(), { status: up.status, headers: { 'content-type': up.headers.get('content-type') ?? 'application/json', ...headers() } });
  }

  if (route.name === 'file-status') {
    const up = await fetchImpl(L.withKey(`${google}${route.path}`, key));
    return new Response(await up.text(), { status: up.status, headers: { 'content-type': up.headers.get('content-type') ?? 'application/json', ...headers() } });
  }

  // generate
  const up = await fetchImpl(L.withKey(`${google}${route.path}`, key), { method: 'POST', headers: { 'content-type': 'application/json' }, body: bodyText });
  const text = await up.text();
  if (up.ok) {
    let amount = 1;
    if (kind === 'audio') {
      let parsed = null; try { parsed = JSON.parse(text); } catch { /* charged the minimum below */ }
      amount = Math.max(1, L.audioSecondsFromUsage(parsed?.usageMetadata));
    }
    const opId = OP_ID.test(request.headers.get('X-Operation-Id') ?? '') ? request.headers.get('X-Operation-Id') : null;
    usage = await call(env.LEDGER, subject, '/commit', { period: period.id, meter, amount, opId });
  }
  // A failed upstream call is passed on and never charged.
  return new Response(text, { status: up.status, headers: { 'content-type': up.headers.get('content-type') ?? 'application/json', ...headers() } });
}

function pick(h, names) {
  const out = {};
  for (const n of names) { const v = h.get(n); if (v) out[n] = v; }
  return out;
}

export default { fetch: (request, env) => handle(request, env) };
