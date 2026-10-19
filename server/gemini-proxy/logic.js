// Pure logic for the Gemini proxy: no Cloudflare APIs, so it runs under `node --test`.

export const GOOGLE_HOST = 'generativelanguage.googleapis.com';
export const PLAN_RANK = { free: 0, byok: 1, pro: 2, pro_plus: 3, church: 3 };
export const ENTITLEMENT_ALIASES = {
  byok: 'byok',
  meetingmind_byok: 'byok',
  pro: 'pro',
  meetingmind_pro: 'pro',
  pro_plus: 'pro_plus',
  meetingmind_pro_plus: 'pro_plus',
  church: 'church',
  meetingmind_church: 'church',
};
export const DEFAULT_ALLOWANCES = {
  free: { audioSeconds: 1800, actions: 30 },
  byok: { audioSeconds: 0, actions: 0 },
  pro: { audioSeconds: 108000, actions: 400 },
  pro_plus: { audioSeconds: 216000, actions: 1000 },
  church: { audioSeconds: 360000, actions: 1500 },
};
/** Gemini counts audio at 32 tokens per second. */
export const AUDIO_TOKENS_PER_SECOND = 32;

/** The usage period: the UTC calendar month. (Aligning it to each person's billing date is a later refinement.) */
export function currentPeriod(nowMs) {
  const d = new Date(nowMs);
  const id = `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`;
  const end = Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + 1, 1);
  return { id, endMs: end };
}

/**
 * PLAN_ALLOWANCES is a JSON object: { "free": {"audioSeconds":1800,"actions":30}, "pro": {...}, ... }.
 * Missing or malformed means NOT CONFIGURED, and the proxy refuses to serve rather than guess a limit.
 */
export function parseAllowances(raw) {
  if (!raw) return null;
  let o;
  try { o = JSON.parse(raw); } catch { return null; }
  if (!o || typeof o !== 'object') return null;
  const out = {};
  for (const plan of Object.keys(PLAN_RANK)) {
    const a = o[plan];
    if (!a) { out[plan] = { audioSeconds: 0, actions: 0 }; continue; }
    const audioSeconds = Number(a.audioSeconds), actions = Number(a.actions);
    if (!Number.isFinite(audioSeconds) || !Number.isFinite(actions) || audioSeconds < 0 || actions < 0) return null;
    out[plan] = { audioSeconds, actions };
  }
  return out;
}

/** The plan's limits, multiplied while a founder bonus is running. */
export function limitsFor(plan, allowances, founderUntilMs, nowMs, founderMultiplier = 1.5) {
  const base = allowances[plan] ?? allowances.free;
  const bonus = founderUntilMs && nowMs < founderUntilMs ? founderMultiplier : 1;
  return { audioSeconds: Math.floor(base.audioSeconds * bonus), actions: Math.floor(base.actions * bonus) };
}

/** Only Google's resumable-upload endpoint may be reached through the upload-session route (no open proxy). */
export function isAllowedUploadUrl(value) {
  let u;
  try { u = new URL(value); } catch { return false; }
  return u.protocol === 'https:' && u.hostname === GOOGLE_HOST && u.pathname.startsWith('/upload/v1beta/files');
}

/** Google's upload URL can carry the API key; it must never reach a client. */
export function stripKey(value) {
  const u = new URL(value);
  u.searchParams.delete('key');
  return u.toString();
}

export function rewriteUploadUrl(googleUrl, proxyOrigin) {
  return `${proxyOrigin}/upload-session?u=${encodeURIComponent(stripKey(googleUrl))}`;
}

/** Re-adds the key for the server-to-Google leg. */
export function withKey(value, key) {
  const u = new URL(value);
  u.searchParams.set('key', key);
  return u.toString();
}

export function classifyRoute(method, pathname) {
  if (method === 'GET' && pathname === '/usage') return { name: 'usage' };
  if (method === 'POST' && pathname === '/upload/v1beta/files') return { name: 'upload-start' };
  if (method === 'POST' && pathname === '/upload-session') return { name: 'upload-session' };
  const file = pathname.match(/^\/v1beta\/files\/([A-Za-z0-9_-]{1,80})$/);
  if (method === 'GET' && file) return { name: 'file-status', path: pathname };
  const gen = pathname.match(/^\/v1beta\/models\/([A-Za-z0-9._-]{1,80}):generateContent$/);
  if (method === 'POST' && gen) return { name: 'generate', model: gen[1], path: pathname };
  return null;
}

export function modelAllowed(model, allowedCsv) {
  return String(allowedCsv || '').split(',').map((s) => s.trim()).filter(Boolean).includes(model);
}

/** A request that sends audio, or asks for a transcription, draws on the audio allowance; everything else is one action. */
export function classifyRequest(body) {
  const config = body?.generationConfig;
  if (config && config.audioTranscriptionConfig) return 'audio';
  const parts = (body?.contents ?? []).flatMap((c) => c?.parts ?? []);
  if (parts.some((p) => p && (p.file_data || p.fileData || p.inline_data || p.inlineData))) return 'audio';
  return 'action';
}

/** Whole seconds of audio Gemini billed for, from the response's per-modality token counts. */
export function audioSecondsFromUsage(usage) {
  const details = usage?.promptTokensDetails;
  if (!Array.isArray(details)) return 0;
  const tokens = details.filter((d) => String(d?.modality).toUpperCase() === 'AUDIO').reduce((n, d) => n + (Number(d.tokenCount) || 0), 0);
  return Math.ceil(tokens / AUDIO_TOKENS_PER_SECOND);
}

/** The best plan among RevenueCat's active entitlements ("free" when none). */
export function highestPlan(entitlements, nowMs) {
  let best = 'free';
  for (const [rawId, ent] of Object.entries(entitlements ?? {})) {
    const id = ENTITLEMENT_ALIASES[rawId] ?? rawId;
    if (!(id in PLAN_RANK) || id === 'free') continue;
    const exp = ent?.expires_date ? Date.parse(ent.expires_date) : null;
    if (exp !== null && !(exp > nowMs)) continue;
    if (PLAN_RANK[id] > PLAN_RANK[best]) best = id;
  }
  return best;
}

/** What is left of one meter; the unit is seconds for audio and calls for actions. */
export function remaining(limit, used) {
  return Math.max(0, limit - used);
}

/** Which meter a gated route draws on, or null when the route is free to call. */
export function meterFor(route, requestKind) {
  if (route.name === 'upload-start' || route.name === 'upload-session' || route.name === 'file-status') return 'audio';
  if (route.name === 'generate') return requestKind;
  return null;
}

/** Why a request is refused: "upgrade_required" when the plan has none of this at all, "allowance_used_up" when it ran out. */
export function refusal(meter, limits, usage) {
  const limit = meter === 'audio' ? limits.audioSeconds : limits.actions;
  const used = meter === 'audio' ? usage.audioSeconds : usage.actions;
  if (limit <= 0) return { status: 402, error: 'upgrade_required' };
  if (remaining(limit, used) <= 0) return { status: 429, error: 'allowance_used_up' };
  return null;
}

export function usageHeaders(plan, limits, usage, period, founderUntilMs) {
  const h = {
    'X-Plan': plan,
    'X-Usage-Audio-Used': String(usage.audioSeconds),
    'X-Usage-Audio-Limit': String(limits.audioSeconds),
    'X-Usage-Actions-Used': String(usage.actions),
    'X-Usage-Actions-Limit': String(limits.actions),
    'X-Usage-Period-End': String(period.endMs)
  };
  if (founderUntilMs) h['X-Founder-Until'] = String(founderUntilMs);
  return h;
}

export const INSTALL_ID = /^[0-9a-f-]{36}$/i;
export const RC_USER = /^[\w$:.\-]{1,128}$/;
