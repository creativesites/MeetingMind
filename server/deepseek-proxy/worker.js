// MeetingMind's DeepSeek proxy (Cloudflare Worker).
//
// The app never holds the DeepSeek key: it calls this worker, which adds the key and passes the
// request on. Each install gets a monthly token allowance, counted here in KV — the one place it
// can't be tampered with. Deploy: see README.md next to this file.

const DEEPSEEK = "https://api.deepseek.com/chat/completions";
const ALLOWED_MODELS = new Set(["deepseek-flash"]);

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method !== "POST" || !url.pathname.endsWith("/chat/completions")) {
      return json({ error: { message: "Not found" } }, 404);
    }
    const install = request.headers.get("X-Install-Id") || "";
    if (!/^[0-9a-f-]{36}$/i.test(install)) return json({ error: { message: "Missing install id" } }, 400);

    const month = new Date().toISOString().slice(0, 7);
    const usageKey = `usage:${month}:${install}`;
    const used = parseInt((await env.USAGE.get(usageKey)) || "0", 10);
    const limit = parseInt(env.MONTHLY_TOKENS || "6000000", 10);
    if (used >= limit) {
      return json({ error: { message: "This month's built-in AI allowance is used up. It resets on the 1st." } }, 429);
    }

    let body;
    try { body = await request.json(); } catch { return json({ error: { message: "Bad JSON" } }, 400); }
    if (!ALLOWED_MODELS.has(body.model)) body.model = "deepseek-flash";
    body.stream = false;
    body.max_tokens = Math.min(body.max_tokens || 4096, 8192);

    const upstream = await fetch(DEEPSEEK, {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${env.DEEPSEEK_API_KEY}` },
      body: JSON.stringify(body),
    });
    const text = await upstream.text();
    if (upstream.ok) {
      try {
        const tokens = JSON.parse(text).usage?.total_tokens || 0;
        // Kept 40 days, so last month's counts expire by themselves.
        await env.USAGE.put(usageKey, String(used + tokens), { expirationTtl: 60 * 60 * 24 * 40 });
      } catch { /* the answer still goes back */ }
    }
    return new Response(text, { status: upstream.status, headers: { "Content-Type": "application/json" } });
  },
};

function json(obj, status) {
  return new Response(JSON.stringify(obj), { status, headers: { "Content-Type": "application/json" } });
}
