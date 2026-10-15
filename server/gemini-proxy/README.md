# MeetingMind managed Gemini proxy

A Cloudflare Worker between the app and Google's Gemini API. It exists so that:

- the Google API key lives on the server, never in the app or its APK;
- a person's **plan** (checked with RevenueCat) decides what they may use;
- usage is **metered atomically** and refused past the plan's allowance;
- a retried request is **charged once**.

The app uses it when `GEMINI_PROXY_URL` is set at build time and the person has not entered their own key
(an own key always goes straight to Google, at their own cost).

## What it serves

| Route | Purpose | Draws on |
|---|---|---|
| `GET /usage` | the person's plan, limits and use (also sent as `X-Usage-*` headers on every reply) | nothing |
| `POST /upload/v1beta/files` | start a resumable audio upload | audio |
| `POST /upload-session?u=…` | send the audio bytes; the URL is one this proxy handed out | audio |
| `GET /v1beta/files/{id}` | wait for an upload to become ready | audio |
| `POST /v1beta/models/{model}:generateContent` | transcribe or write | audio seconds if the request carries audio or a transcription config, otherwise one action |

Everything else is a 404. Only models in `ALLOWED_MODELS` are served.

### Why `/upload-session` exists
Google's resumable-upload URL can contain the API key. The proxy never returns it: it strips the key and
returns a URL pointing back at itself, then re-adds the key when forwarding. The route refuses any target that is
not Google's upload endpoint, so it is not an open proxy.

## Who is who
- `X-Install-Id` (required): a random per-install UUID. A person without a plan is metered under it, so a reinstall
  resets a free allowance. That is accepted for a small free allowance; paid plans do not depend on it.
- `X-RC-User` (optional): the RevenueCat app user id. With it the proxy asks RevenueCat which entitlements are active
  (`byok`, `pro`, `pro_plus`, `church`; the app's `Plan` enum uses the same ids) and meters the person under that id.
  If RevenueCat cannot be reached the proxy answers **503 `entitlement_check_failed`** rather than treating a paying
  person as free.
- `X-Operation-Id` (optional): the same id on a retry is charged once. A failed upstream call is never charged.

## Allowances
`PLAN_ALLOWANCES` is a JSON object, one entry per plan, in **seconds of cloud audio** and **AI actions** per month (UTC
calendar month):

```json
{ "free":     { "audioSeconds": 0, "actions": 0 },
  "byok":     { "audioSeconds": 0, "actions": 0 },
  "pro":      { "audioSeconds": 0, "actions": 0 },
  "pro_plus": { "audioSeconds": 0, "actions": 0 },
  "church":   { "audioSeconds": 0, "actions": 0 } }
```

The zeros above are placeholders. **Set the real numbers from the cost model** (Gemini's cost per audio minute and per
summary or question, against what each plan earns), then publish exactly those numbers in the app's plan details.
Until `PLAN_ALLOWANCES` is set and valid the proxy answers 503 `not_configured`: it never guesses a limit.

Audio is charged by what Gemini billed (`usageMetadata.promptTokensDetails`, AUDIO tokens at 32 per second, rounded up).
A plan with an allowance of 0 for a meter gets **402 `upgrade_required`**; one that has used it all gets
**429 `allowance_used_up`**. Both carry `X-Allowance: exhausted`.

## Founder bonus
The first `FOUNDER_LIMIT` distinct paying people the proxy sees get `FOUNDER_MULTIPLIER`× their limits until
`FOUNDER_DAYS` after they were first seen. The end date is returned as `X-Founder-Until` so the app can show it.
It is real, not a countdown: nothing resets.

## Deploy
```
cd server/gemini-proxy
npx wrangler login
npx wrangler kv namespace create CACHE            # paste the id into wrangler.toml
npx wrangler secret put GEMINI_API_KEY
npx wrangler secret put RC_SECRET_KEY             # RevenueCat secret API key (v1, read-only is enough)
npx wrangler secret put PLAN_ALLOWANCES           # the JSON above, with the real numbers
npx wrangler deploy
```
Then build the app with `GEMINI_PROXY_URL=https://… REVENUECAT_API_KEY=… ./gradlew assembleDebug`.

## Tests
`npm test` (Node 22+) runs the pure logic and the whole request flow with an in-memory ledger and a stubbed
`fetch`. It does not call Google or RevenueCat.

## Known limits
- The usage period is the UTC calendar month, not each person's billing date.
- A request is allowed if any of that meter remains, and charged afterwards, so one request can overshoot an
  allowance by its own size.
- `X-RC-User` is asserted by the client; RevenueCat's anonymous ids are random and unguessable, which is the same trust
  RevenueCat itself relies on, but a leaked id could be used by someone else.
- A retried request whose first attempt succeeded is forwarded to Google again (so it costs us once more) but is not
  charged to the person again.
