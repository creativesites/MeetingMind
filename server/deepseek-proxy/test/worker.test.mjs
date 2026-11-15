import { test } from 'node:test';
import assert from 'node:assert/strict';
import worker from '../worker.js';

function makeKv(initial = {}) {
  const store = new Map(Object.entries(initial));
  return {
    async get(k) { return store.has(k) ? String(store.get(k)) : null; },
    async put(k, v) { store.set(k, String(v)); },
    _store: store
  };
}

function makeCtx() {
  const pending = [];
  return {
    waitUntil(p) { pending.push(p); },
    async flush() { await Promise.all(pending); }
  };
}

test('deepseek-proxy rejects missing install id and enforces monthly quota', async () => {
  const badIdReq = new Request('https://deepseek.example/v1/chat/completions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: '{}'
  });
  const badRes = await worker.fetch(badIdReq, { USAGE: makeKv() }, makeCtx());
  assert.equal(badRes.status, 400);

  const month = new Date().toISOString().slice(0, 7);
  const installId = '11111111-2222-3333-4444-555555555555';
  const kv = makeKv({ [`usage:${month}:${installId}`]: '6000000' });
  const req = new Request('https://deepseek.example/v1/chat/completions', {
    method: 'POST',
    headers: { 'X-Install-Id': installId, 'Content-Type': 'application/json' },
    body: JSON.stringify({ model: 'deepseek-v4-flash', messages: [{ role: 'user', content: 'hi' }] })
  });
  const overRes = await worker.fetch(req, { USAGE: kv, MONTHLY_TOKENS: '6000000' }, makeCtx());
  assert.equal(overRes.status, 429);
});

test('deepseek-proxy allows deepseek-v4 models and meters streamed SSE tokens', async () => {
  const month = new Date().toISOString().slice(0, 7);
  const installId = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee';
  const kv = makeKv();
  const ctx = makeCtx();
  const origFetch = globalThis.fetch;

  let seenUpstreamBody = null;
  globalThis.fetch = async (_url, init) => {
    seenUpstreamBody = JSON.parse(init.body);
    const sse = [
      'data: {"choices":[{"delta":{"content":"Hello"}}]}\n\n',
      'data: {"choices":[{"delta":{}}],"usage":{"total_tokens":42}}\n\n',
      'data: [DONE]\n\n'
    ].join('');
    const stream = new ReadableStream({
      start(controller) {
        controller.enqueue(new TextEncoder().encode(sse));
        controller.close();
      }
    });
    return new Response(stream, {
      status: 200,
      headers: { 'Content-Type': 'text/event-stream' }
    });
  };

  try {
    const req = new Request('https://deepseek.example/v1/chat/completions', {
      method: 'POST',
      headers: { 'X-Install-Id': installId, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        model: 'deepseek-v4-pro',
        stream: true,
        max_tokens: 8192,
        messages: [{ role: 'user', content: 'Explain Romans 8' }]
      })
    });
    const res = await worker.fetch(req, { USAGE: kv, DEEPSEEK_API_KEY: 'sk-test' }, ctx);
    assert.equal(res.status, 200);
    assert.equal(seenUpstreamBody.model, 'deepseek-v4-pro');
    assert.equal(seenUpstreamBody.stream, true);
    assert.equal(seenUpstreamBody.stream_options.include_usage, true);

    const text = await res.text();
    assert.ok(text.includes('"content":"Hello"'));
    await ctx.flush();
    assert.equal(await kv.get(`usage:${month}:${installId}`), '42');
  } finally {
    globalThis.fetch = origFetch;
  }
});
