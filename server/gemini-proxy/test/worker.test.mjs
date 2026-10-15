import test from 'node:test';
import assert from 'node:assert/strict';
import { handle, UsageLedger, Founders } from '../worker.js';

const NOW = Date.UTC(2026, 9, 14, 12);
const INSTALL = '123e4567-e89b-12d3-a456-426614174000';

/** A Durable Object namespace in memory: one instance per name. */
function namespace(Cls) {
  const instances = new Map();
  return {
    idFromName: (n) => n,
    get(id) {
      if (!instances.has(id)) {
        const store = new Map();
        const state = { storage: { get: async (k) => store.get(k), put: async (k, v) => { store.set(k, structuredClone(v)); } } };
        instances.set(id, new Cls(state));
      }
      const inst = instances.get(id);
      return { fetch: (url, init) => inst.fetch(new Request(url, init)) };
    }
  };
}

function makeEnv(extra = {}) {
  return {
    GEMINI_API_KEY: 'REALKEY',
    RC_SECRET_KEY: 'rc_secret',
    ALLOWED_MODELS: 'gemini-3.5-transcribe,gemini-3.6-flash',
    PLAN_ALLOWANCES: JSON.stringify({ free: { audioSeconds: 0, actions: 2 }, pro: { audioSeconds: 100, actions: 3 } }),
    FOUNDER_LIMIT: '0',
    LEDGER: namespace(UsageLedger),
    FOUNDERS: namespace(Founders),
    ...extra
  };
}

/** Records every upstream call; answers from a route table. */
function upstream(routes = {}) {
  const calls = [];
  const fn = async (input, init = {}) => {
    const u = String(input);
    calls.push({ url: u, init });
    for (const [prefix, answer] of Object.entries(routes)) if (u.startsWith(prefix)) return typeof answer === 'function' ? answer(u, init) : answer.clone();
    return new Response('{}', { status: 200 });
  };
  fn.calls = calls;
  return fn;
}

const rcAnswer = (entitlements) => new Response(JSON.stringify({ subscriber: { entitlements } }), { status: 200 });
const proEnt = { pro: { expires_date: '2026-11-14T00:00:00Z' } };

const textBody = JSON.stringify({ contents: [{ parts: [{ text: 'summarise' }] }] });
const audioBody = JSON.stringify({ contents: [{ parts: [{ fileData: { fileUri: 'u' } }] }], generationConfig: { audioTranscriptionConfig: {} } });
const genUrl = 'https://proxy.test/v1beta/models/gemini-3.6-flash:generateContent';
const req = (url, init = {}, headers = {}) => new Request(url, { ...init, headers: { 'X-Install-Id': INSTALL, ...headers } });
const generate = (body, headers = {}) => req(genUrl, { method: 'POST', body }, headers);

test('without its secrets or allowances the proxy refuses to serve', async () => {
  assert.equal((await handle(generate(textBody), makeEnv({ GEMINI_API_KEY: '' }), { now: NOW, fetch: upstream() })).status, 503);
  assert.equal((await handle(generate(textBody), makeEnv({ PLAN_ALLOWANCES: undefined }), { now: NOW, fetch: upstream() })).status, 503);
});

test('unknown routes and bad ids are rejected before anything is spent', async () => {
  const f = upstream();
  assert.equal((await handle(req('https://proxy.test/v1beta/models'), makeEnv(), { now: NOW, fetch: f })).status, 404);
  assert.equal((await handle(new Request(genUrl, { method: 'POST', body: textBody }), makeEnv(), { now: NOW, fetch: f })).status, 400);
  assert.equal((await handle(generate(textBody, { 'X-RC-User': 'bad id!' }), makeEnv(), { now: NOW, fetch: f })).status, 400);
  assert.equal(f.calls.length, 0);
});

test('a free install cannot use audio at all, and Google is never called', async () => {
  const f = upstream();
  const res = await handle(generate(audioBody), makeEnv(), { now: NOW, fetch: f });
  assert.equal(res.status, 402);
  assert.equal((await res.json()).error, 'upgrade_required');
  assert.equal(res.headers.get('X-Allowance'), 'exhausted');
  assert.equal(f.calls.length, 0);
});

test('a free install gets its small action allowance, then 429', async () => {
  const env = makeEnv();
  const f = upstream({ 'https://generativelanguage.googleapis.com/v1beta/models': new Response('{"candidates":[]}', { status: 200 }) });
  for (let i = 0; i < 2; i++) assert.equal((await handle(generate(textBody), env, { now: NOW, fetch: f })).status, 200);
  const res = await handle(generate(textBody), env, { now: NOW, fetch: f });
  assert.equal(res.status, 429);
  assert.equal((await res.json()).error, 'allowance_used_up');
});

test('a paying person is recognised, charged per action, and sees their usage', async () => {
  const env = makeEnv();
  const f = upstream({
    'https://api.revenuecat.com': () => rcAnswer(proEnt),
    'https://generativelanguage.googleapis.com/v1beta/models': new Response('{"candidates":[]}', { status: 200 })
  });
  const res = await handle(generate(textBody, { 'X-RC-User': '$RCAnonymousID:abc' }), env, { now: NOW, fetch: f });
  assert.equal(res.status, 200);
  assert.equal(res.headers.get('X-Plan'), 'pro');
  assert.equal(res.headers.get('X-Usage-Actions-Used'), '1');
  assert.equal(res.headers.get('X-Usage-Actions-Limit'), '3');
  const usage = await (await handle(req('https://proxy.test/usage', {}, { 'X-RC-User': '$RCAnonymousID:abc' }), env, { now: NOW, fetch: f })).json();
  assert.equal(usage.plan, 'pro');
  assert.equal(usage.usage.actions, 1);
});

test('the Google key is added server-side and never sent by the client', async () => {
  const f = upstream({ 'https://generativelanguage.googleapis.com/v1beta/models': new Response('{}', { status: 200 }) });
  await handle(generate(textBody), makeEnv(), { now: NOW, fetch: f });
  const call = f.calls.find((c) => c.url.includes('generateContent'));
  assert.ok(call.url.includes('key=REALKEY'));
  assert.ok(!JSON.stringify(call.init.headers ?? {}).includes('Install'));
});

test('audio is charged by the seconds Gemini billed, once per operation id', async () => {
  const env = makeEnv();
  const answer = () => new Response(JSON.stringify({ usageMetadata: { promptTokensDetails: [{ modality: 'AUDIO', tokenCount: 1600 }] } }), { status: 200 });
  const f = upstream({
    'https://api.revenuecat.com': () => rcAnswer(proEnt),
    'https://generativelanguage.googleapis.com/v1beta/models': answer
  });
  const h = { 'X-RC-User': 'u1', 'X-Operation-Id': 'op-aaaaaaaa' };
  const first = await handle(generate(audioBody, h), env, { now: NOW, fetch: f });
  assert.equal(first.headers.get('X-Usage-Audio-Used'), '50');
  const retry = await handle(generate(audioBody, h), env, { now: NOW, fetch: f });
  assert.equal(retry.headers.get('X-Usage-Audio-Used'), '50', 'a retried operation is not charged twice');
  const next = await handle(generate(audioBody, { 'X-RC-User': 'u1', 'X-Operation-Id': 'op-bbbbbbbb' }), env, { now: NOW, fetch: f });
  assert.equal(next.headers.get('X-Usage-Audio-Used'), '100');
  const over = await handle(generate(audioBody, { 'X-RC-User': 'u1', 'X-Operation-Id': 'op-cccccccc' }), env, { now: NOW, fetch: f });
  assert.equal(over.status, 429);
});

test('a failed upstream call is passed on and not charged', async () => {
  const env = makeEnv();
  const f = upstream({ 'https://generativelanguage.googleapis.com/v1beta/models': new Response('{"error":"busy"}', { status: 503 }) });
  const res = await handle(generate(textBody), env, { now: NOW, fetch: f });
  assert.equal(res.status, 503);
  assert.equal(res.headers.get('X-Usage-Actions-Used'), '0');
});

test('models outside the allowlist are refused', async () => {
  const res = await handle(req('https://proxy.test/v1beta/models/gemini-ultra:generateContent', { method: 'POST', body: textBody }), makeEnv(), { now: NOW, fetch: upstream() });
  assert.equal(res.status, 403);
});

test('if RevenueCat cannot be asked, a paying person is not treated as free', async () => {
  const f = upstream({ 'https://api.revenuecat.com': new Response('boom', { status: 500 }) });
  const res = await handle(generate(textBody, { 'X-RC-User': 'u1' }), makeEnv(), { now: NOW, fetch: f });
  assert.equal(res.status, 503);
  assert.equal((await res.json()).error, 'entitlement_check_failed');
});

test('the upload start hands the client a proxy URL with no API key in it', async () => {
  const env = makeEnv();
  const f = upstream({
    'https://api.revenuecat.com': () => rcAnswer(proEnt),
    'https://generativelanguage.googleapis.com/upload/v1beta/files': () => new Response('{}', {
      status: 200,
      headers: { 'x-goog-upload-url': 'https://generativelanguage.googleapis.com/upload/v1beta/files?key=REALKEY&upload_id=abc&upload_protocol=resumable' }
    })
  });
  const res = await handle(req('https://proxy.test/upload/v1beta/files', { method: 'POST', body: '{"file":{}}' }, { 'X-RC-User': 'u1', 'X-Goog-Upload-Command': 'start' }), env, { now: NOW, fetch: f });
  const handed = res.headers.get('X-Goog-Upload-URL');
  assert.ok(handed.startsWith('https://proxy.test/upload-session?u='));
  assert.ok(!handed.includes('REALKEY'));
});

test('the upload-session route will not reach anywhere but Google uploads', async () => {
  const f = upstream({ 'https://api.revenuecat.com': () => rcAnswer(proEnt) });
  const evil = `https://proxy.test/upload-session?u=${encodeURIComponent('https://evil.example/upload/v1beta/files')}`;
  const res = await handle(req(evil, { method: 'POST', body: 'x' }, { 'X-RC-User': 'u1' }), makeEnv(), { now: NOW, fetch: f });
  assert.equal(res.status, 400);
  assert.ok(!f.calls.some((c) => c.url.includes('evil.example')));
});

test('the first N paying people get the founder bonus, later ones do not', async () => {
  const env = makeEnv({ FOUNDER_LIMIT: '1', FOUNDER_DAYS: '90' });
  const f = upstream({
    'https://api.revenuecat.com': () => rcAnswer({ pro: { expires_date: '2027-12-01T00:00:00Z' } }),
    'https://generativelanguage.googleapis.com/v1beta/models': new Response('{}', { status: 200 })
  });
  const first = await handle(generate(textBody, { 'X-RC-User': 'early' }), env, { now: NOW, fetch: f });
  assert.equal(first.headers.get('X-Usage-Actions-Limit'), '4'); // 3 x 1.5, floored
  assert.equal(first.headers.get('X-Founder-Until'), String(NOW + 90 * 86_400_000));
  const late = await handle(generate(textBody, { 'X-RC-User': 'late' }), env, { now: NOW, fetch: f });
  assert.equal(late.headers.get('X-Usage-Actions-Limit'), '3');
  assert.equal(late.headers.get('X-Founder-Until'), null);
  // The founder keeps it on later visits, and it ends on its date.
  const again = await handle(generate(textBody, { 'X-RC-User': 'early' }), env, { now: NOW + 1000, fetch: f });
  assert.equal(again.headers.get('X-Usage-Actions-Limit'), '4');
  const ended = await handle(generate(textBody, { 'X-RC-User': 'early' }), env, { now: NOW + 91 * 86_400_000, fetch: f });
  assert.equal(ended.headers.get('X-Usage-Actions-Limit'), '3');
});

test('usage resets with the calendar month', async () => {
  const env = makeEnv();
  const f = upstream({ 'https://generativelanguage.googleapis.com/v1beta/models': new Response('{}', { status: 200 }) });
  for (let i = 0; i < 2; i++) await handle(generate(textBody), env, { now: NOW, fetch: f });
  assert.equal((await handle(generate(textBody), env, { now: NOW, fetch: f })).status, 429);
  const nextMonth = await handle(generate(textBody), env, { now: Date.UTC(2026, 10, 2), fetch: f });
  assert.equal(nextMonth.status, 200);
});
