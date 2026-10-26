import test from 'node:test';
import assert from 'node:assert/strict';
import * as L from '../logic.js';

const allowances = L.parseAllowances(JSON.stringify({
  free: { audioSeconds: 0, actions: 10 },
  pro: { audioSeconds: 6000, actions: 300 },
  pro_plus: { audioSeconds: 18000, actions: 900 }
}));

test('period is the UTC calendar month and ends at the next month', () => {
  const p = L.currentPeriod(Date.UTC(2026, 9, 14, 12));
  assert.equal(p.id, '2026-10');
  assert.equal(p.endMs, Date.UTC(2026, 10, 1));
  assert.equal(L.currentPeriod(Date.UTC(2026, 11, 31, 23, 59)).endMs, Date.UTC(2027, 0, 1));
});

test('allowances: missing or malformed config is "not configured", never a guess', () => {
  assert.equal(L.parseAllowances(undefined), null);
  assert.equal(L.parseAllowances('not json'), null);
  assert.equal(L.parseAllowances(JSON.stringify({ pro: { audioSeconds: -1, actions: 1 } })), null);
  assert.equal(L.parseAllowances(JSON.stringify({ pro: { audioSeconds: 'x', actions: 1 } })), null);
  assert.deepEqual(allowances.church, { audioSeconds: 0, actions: 0 }); // plans not listed get nothing
});

test('founder bonus multiplies limits only while it runs', () => {
  const until = 1000;
  assert.deepEqual(L.limitsFor('pro', allowances, until, 999), { audioSeconds: 9000, actions: 450 });
  assert.deepEqual(L.limitsFor('pro', allowances, until, 1000), { audioSeconds: 6000, actions: 300 });
  assert.deepEqual(L.limitsFor('pro', allowances, null, 0), { audioSeconds: 6000, actions: 300 });
});

test('the upload-session route is not an open proxy', () => {
  assert.ok(L.isAllowedUploadUrl('https://generativelanguage.googleapis.com/upload/v1beta/files?upload_id=a&upload_protocol=resumable'));
  assert.ok(!L.isAllowedUploadUrl('http://generativelanguage.googleapis.com/upload/v1beta/files'));
  assert.ok(!L.isAllowedUploadUrl('https://evil.example/upload/v1beta/files'));
  assert.ok(!L.isAllowedUploadUrl('https://generativelanguage.googleapis.com.evil.example/upload/v1beta/files'));
  assert.ok(!L.isAllowedUploadUrl('https://generativelanguage.googleapis.com/v1beta/models'));
  assert.ok(!L.isAllowedUploadUrl('not a url'));
});

test('the API key is stripped from Google upload URLs and added back server-side', () => {
  const g = 'https://generativelanguage.googleapis.com/upload/v1beta/files?key=SECRET&upload_id=abc&upload_protocol=resumable';
  const out = L.rewriteUploadUrl(g, 'https://proxy.example');
  assert.ok(!out.includes('SECRET'));
  assert.ok(out.startsWith('https://proxy.example/upload-session?u='));
  const inner = decodeURIComponent(out.split('u=')[1]);
  assert.ok(L.isAllowedUploadUrl(inner));
  assert.ok(L.withKey(inner, 'NEWKEY').includes('key=NEWKEY'));
  assert.ok(L.withKey(inner, 'NEWKEY').includes('upload_id=abc'));
});

test('routes are allowlisted', () => {
  assert.equal(L.classifyRoute('GET', '/usage').name, 'usage');
  assert.equal(L.classifyRoute('POST', '/upload/v1beta/files').name, 'upload-start');
  assert.equal(L.classifyRoute('POST', '/upload-session').name, 'upload-session');
  assert.equal(L.classifyRoute('GET', '/v1beta/files/abc-123').name, 'file-status');
  assert.equal(L.classifyRoute('POST', '/v1beta/models/gemini-3.6-flash:generateContent').model, 'gemini-3.6-flash');
  assert.equal(L.classifyRoute('GET', '/v1beta/files/../models'), null);
  assert.equal(L.classifyRoute('POST', '/v1beta/models/x:streamGenerateContent'), null);
  assert.equal(L.classifyRoute('DELETE', '/v1beta/files/abc'), null);
  assert.equal(L.classifyRoute('GET', '/v1beta/models'), null);
});

test('only allowed models', () => {
  assert.ok(L.modelAllowed('gemini-3.6-flash', 'gemini-3.5-transcribe, gemini-3.6-flash'));
  assert.ok(!L.modelAllowed('gemini-ultra', 'gemini-3.5-transcribe,gemini-3.6-flash'));
  assert.ok(!L.modelAllowed('x', ''));
});

test('requests with audio or a transcription config draw on the audio meter, the rest on actions', () => {
  assert.equal(L.classifyRequest({ generationConfig: { audioTranscriptionConfig: {} } }), 'audio');
  assert.equal(L.classifyRequest({ contents: [{ parts: [{ file_data: { file_uri: 'x' } }] }] }), 'audio');
  assert.equal(L.classifyRequest({ contents: [{ parts: [{ fileData: { fileUri: 'x' } }] }] }), 'audio');
  assert.equal(L.classifyRequest({ contents: [{ parts: [{ text: 'hi' }] }] }), 'action');
  assert.equal(L.classifyRequest({}), 'action');
  assert.equal(L.classifyRequest(null), 'action');
});

test('audio seconds come from the audio token count, rounded up', () => {
  const usage = { promptTokensDetails: [{ modality: 'TEXT', tokenCount: 50 }, { modality: 'AUDIO', tokenCount: 3200 }] };
  assert.equal(L.audioSecondsFromUsage(usage), 100);
  assert.equal(L.audioSecondsFromUsage({ promptTokensDetails: [{ modality: 'audio', tokenCount: 33 }] }), 2);
  assert.equal(L.audioSecondsFromUsage({}), 0);
  assert.equal(L.audioSecondsFromUsage(undefined), 0);
});

test('a YouTube video is metered by its video tokens, not as one second of audio', () => {
  // 20 minutes of video at about 100 tokens a second, with no separate audio tokens counted.
  const twentyMinutes = { promptTokensDetails: [{ modality: 'VIDEO', tokenCount: 120000 }, { modality: 'TEXT', tokenCount: 300 }] };
  assert.equal(L.audioSecondsFromUsage(twentyMinutes), 1200);
  // Picture and sound of a video with an audio track are both counted.
  const withSound = { promptTokensDetails: [{ modality: 'VIDEO', tokenCount: 1000 }, { modality: 'AUDIO', tokenCount: 320 }] };
  assert.equal(L.audioSecondsFromUsage(withSound), 10 + 10);
});

test('the best active entitlement wins, expired ones are ignored', () => {
  const now = Date.parse('2026-10-14T00:00:00Z');
  const future = '2026-11-14T00:00:00Z', past = '2026-09-01T00:00:00Z';
  assert.equal(L.highestPlan({ pro: { expires_date: future }, pro_plus: { expires_date: past } }, now), 'pro');
  assert.equal(L.highestPlan({ pro: { expires_date: future }, pro_plus: { expires_date: future } }, now), 'pro_plus');
  assert.equal(L.highestPlan({ meetingmind_byok: { expires_date: future } }, now), 'byok');
  assert.equal(L.highestPlan({ meetingmind_pro: { expires_date: future }, meetingmind_byok: { expires_date: future } }, now), 'pro');
  assert.equal(L.highestPlan({ meetingmind_church: { expires_date: future }, meetingmind_pro: { expires_date: future } }, now), 'church');
  assert.equal(L.highestPlan({ pro: { expires_date: null } }, now), 'pro'); // lifetime / no end date
  assert.equal(L.highestPlan({ pro: { expires_date: past } }, now), 'free');
  assert.equal(L.highestPlan({ unknown: { expires_date: future } }, now), 'free');
  assert.equal(L.highestPlan({}, now), 'free');
  assert.equal(L.highestPlan(undefined, now), 'free');
  assert.deepEqual(L.DEFAULT_ALLOWANCES.free, { audioSeconds: 1800, actions: 30 });
});

test('refusals distinguish "needs a plan" from "ran out"', () => {
  const limits = { audioSeconds: 0, actions: 10 };
  assert.deepEqual(L.refusal('audio', limits, { audioSeconds: 0, actions: 0 }), { status: 402, error: 'upgrade_required' });
  assert.deepEqual(L.refusal('action', limits, { audioSeconds: 0, actions: 10 }), { status: 429, error: 'allowance_used_up' });
  assert.equal(L.refusal('action', limits, { audioSeconds: 0, actions: 9 }), null);
});

test('upload and file routes draw on audio; generate draws on what it carries; usage is free', () => {
  assert.equal(L.meterFor({ name: 'upload-start' }, 'action'), 'audio');
  assert.equal(L.meterFor({ name: 'file-status' }, 'action'), 'audio');
  assert.equal(L.meterFor({ name: 'generate' }, 'action'), 'action');
  assert.equal(L.meterFor({ name: 'generate' }, 'audio'), 'audio');
  assert.equal(L.meterFor({ name: 'usage' }, 'action'), null);
});

test('usage headers carry the numbers the app shows', () => {
  const h = L.usageHeaders('pro', { audioSeconds: 6000, actions: 300 }, { audioSeconds: 120, actions: 4 }, { endMs: 5 }, 99);
  assert.equal(h['X-Usage-Audio-Used'], '120');
  assert.equal(h['X-Usage-Audio-Limit'], '6000');
  assert.equal(h['X-Usage-Actions-Limit'], '300');
  assert.equal(h['X-Usage-Period-End'], '5');
  assert.equal(h['X-Founder-Until'], '99');
  assert.ok(!('X-Founder-Until' in L.usageHeaders('pro', { audioSeconds: 1, actions: 1 }, { audioSeconds: 0, actions: 0 }, { endMs: 5 }, null)));
});

test('identifiers are validated', () => {
  assert.ok(L.INSTALL_ID.test('123e4567-e89b-12d3-a456-426614174000'));
  assert.ok(!L.INSTALL_ID.test('nope'));
  assert.ok(L.RC_USER.test('$RCAnonymousID:abc123'));
  assert.ok(!L.RC_USER.test('a b'));
  assert.ok(!L.RC_USER.test('x'.repeat(200)));
});
