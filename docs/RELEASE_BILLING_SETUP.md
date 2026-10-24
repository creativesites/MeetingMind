# Paid launch: what you set up, what the code does

Founder decision, 2026-10-25: the Play launch is **paid**. This document is the checklist for the
parts only the account owner can do (Play Console, RevenueCat, Cloudflare), plus exactly what the
code now expects so the two meet on the first try.

Read §6 first if you want the short version of what is yours.

---

## 1. What the code expects (do not rename these)

The app never hardcodes a price, a period or a trial. It maps what the store returns onto three
entitlements and a fixed set of package identifiers. If a name differs by one character, the plan
simply does not appear on the paywall.

| Thing | Exact value in the app | Where it is defined |
|---|---|---|
| Entitlement, BYOK | `meetingmind_byok` | `core/billing/Plan.kt` (`Plan.BYOK`) |
| Entitlement, Pro | `meetingmind_pro` | `Plan.PRO` |
| Entitlement, Church | `meetingmind_church` | `Plan.CHURCH` |
| Package ids | `monthly`, `yearly`, `lifetime`, `byok_monthly`, `byok_yearly`, `church_monthly`, `church_yearly` | `OfferKind` |

Two consequences worth knowing before you click anything:

- **Custom package identifiers, not RevenueCat's built-ins.** RevenueCat offers `$rc_monthly`,
  `$rc_annual`, `$rc_lifetime`, `$rc_custom`. Those will **not** match. Create packages whose
  identifier is exactly `monthly`, `yearly`, `lifetime`, and so on.
- **One offering must be marked "current"**. The app reads `offerings().current`, so a draft
  offering shows nothing on the paywall.

---

## 2. Play Console (you)

1. Play Console → **Payments profile** is complete (required before any product can be created).
2. **Create the app** if it does not exist: package `com.craftflowtechnologies.meetingmind`,
   "MeetingMind: meetings, notes & faith", free app with in-app purchases.
3. **Subscriptions** (Monetise → Products → Subscriptions). Create one subscription per plan, each
   with a base plan that auto-renews:
   - `pro_monthly` — $12.99/month
   - `pro_yearly` — $99.99/year (or your chosen discount)
   - `byok_monthly` — $4.99/month
   - `byok_yearly` — $44.99/year
   - `church_monthly` — $49.99/month
   - `church_yearly` — $499.99/year
   Add a **free trial** offer on `pro_monthly` only if you want the paywall's trial line to show
   ("7-day free trial" appears only when the store actually returns a trial).
4. **One-time product** (Monetise → Products → In-app products) for the lifetime plan, e.g.
   `pro_lifetime`, price as you decide. It is sold through the `lifetime` package.
5. **License testing**: Settings → License testing → add the Google account(s) you will buy with.
   Test purchases are free and renew quickly (5 minutes for a 1-week plan).
6. **Closed testing**: create a closed track and add your testers. The same build is promoted to
   production later, so tiers are enforced in every build — testers get Pro through a RevenueCat
   **promotional entitlement**, not through an app switch.
7. Keep the **app signing key** and the **upload key** fingerprints where you can find them; RevenueCat
   and the Play service account need the app to exist first.

---

## 3. RevenueCat (you)

1. Project → **Apps → + New app → Play Store**, package `com.craftflowtechnologies.meetingmind`.
2. **Service account credentials.** RevenueCat needs a Google Cloud service account with Play
   Developer API access to import products and validate purchases:
   - Google Cloud Console → create a service account (e.g. `revenuecat-play`) → create a JSON key.
   - Play Console → Setup → API access → link that service account, grant "View financial data"
     and "Manage orders and subscriptions".
   - Upload the JSON in RevenueCat (App → Service credentials).
3. **Products:** RevenueCat → Products → Import from Play Store. Import every subscription and the
   lifetime in-app product.
4. **Entitlements:** create exactly three — `meetingmind_byok`, `meetingmind_pro`,
   `meetingmind_church` — and attach:

   | Entitlement | Products |
   |---|---|
   | `meetingmind_byok` | `byok_monthly`, `byok_yearly` |
   | `meetingmind_pro` | `pro_monthly`, `pro_yearly`, `pro_lifetime` |
   | `meetingmind_church` | `church_monthly`, `church_yearly` |

5. **Offering:** create one offering, mark it **current**, and add packages with these exact
   identifiers and products:

   | Package identifier | Product |
   |---|---|
   | `monthly` | `pro_monthly` |
   | `yearly` | `pro_yearly` |
   | `lifetime` | `pro_lifetime` |
   | `byok_monthly` | `byok_monthly` |
   | `byok_yearly` | `byok_yearly` |
   | `church_monthly` | `church_monthly` |
   | `church_yearly` | `church_yearly` |

5b. **Test Store (needed for debug builds today).** The Test Store has its own products, separate
   from Play. RevenueCat → your app → **Test Store** → add products (`pro_monthly`, `pro_yearly`,
   `byok_monthly`, `church_monthly`, …) and an **offering marked current** with the same custom
   package identifiers from the table above. Without this, a debug build with the `test_` key
   loads no plans and the paywall honestly says so. Purchases in the Test Store are free and
   instant; use them to walk the whole flow before touching real money.

6. **API keys** (Project → API keys):
   - The **Test Store** key (`test_…`) is for debug builds. Yours is already in `local.properties`
     as `revenuecat.testApiKey` (that file is gitignored).
   - The **Play SDK key** (`goog_…`) goes into the GitHub Actions secret `REVENUECAT_API_KEY`. It
     is public-by-design, but CI is where it belongs.
   - The **secret key** (v1, read-only is enough) goes to the proxy as `RC_SECRET_KEY`. This one
     must never be in the app or the repository.

---

## 4. The managed AI proxy (you run the commands, I have prepared the config)

`server/gemini-proxy` is approved for production. It holds the Google key, checks the plan with
RevenueCat and meters each plan's monthly allowance. Without it a Play build now **fails its own
bundle task** rather than shipping unmetered AI.

```bash
cd server/gemini-proxy
npx wrangler login
npx wrangler kv namespace create CACHE        # paste the id into wrangler.toml
npx wrangler secret put GEMINI_API_KEY
npx wrangler secret put RC_SECRET_KEY
npx wrangler secret put PLAN_ALLOWANCES       # JSON, see below
npx wrangler deploy
```

Allowances are **seconds of cloud audio** and **AI actions per UTC month**. Starting point (adjust
after the first month of real cost data — the proxy refuses to serve until this is set):

```json
{ "free":     { "audioSeconds": 1800,  "actions": 30   },
  "byok":     { "audioSeconds": 0,     "actions": 0    },
  "pro":      { "audioSeconds": 72000, "actions": 1200 },
  "pro_plus": { "audioSeconds": 72000, "actions": 1200 },
  "church":   { "audioSeconds": 216000,"actions": 4000 } }
```

`free` is the 30-minute / 30-action taste the paywall promises. `byok` is 0 on purpose: a BYOK
person uses their own key and costs us nothing. Church is 60 audio hours / 4,000 actions.

Capture the deployed URL (`https://meetingmind-gemini.<account>.workers.dev`) — it becomes the
`GEMINI_PROXY_URL` secret.

---

## 5. GitHub Actions secrets (you, once)

Settings → Secrets and variables → Actions:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | the upload keystore, `base64 -w0 upload.jks` |
| `KEYSTORE_PASSWORD` | its store password |
| `KEY_PASSWORD` | the password of the `upload` key |
| `REVENUECAT_API_KEY` | the `goog_…` Play SDK key |
| `GEMINI_PROXY_URL` | the deployed proxy URL |
| `DEEPSEEK_PROXY_URL` | optional |
| `SYSTEM_GEMINI_API_KEY` | **delete this secret.** Play bundles carry no raw Google key any more |

The workflow now fails if a raw Google key is found in the bundle, and fails if either the store key
or the proxy URL is missing. That is deliberate: a release should not be buildable in a
misconfigured state.

---

## 6. The short version: what is actually yours

1. Play Console: payment profile, 6 subscriptions + 1 in-app product, license testers, closed track.
2. RevenueCat: Play app + service account, import products, 3 entitlements, 1 current offering with
   7 correctly named custom packages, Test Store key (debug), `goog_` key (CI), secret key (proxy).
3. Cloudflare: `wrangler deploy` the proxy with the three secrets and the allowance JSON.
4. GitHub: five secrets, and delete `SYSTEM_GEMINI_API_KEY`.

Everything else — the gates, the paywall states, the release configuration, the CI checks, the
proxy wiring in the app — is code I am landing on `release/1.0-paid-launch`.

---

## 7. How we prove it works, in order

1. **Debug, no account:** launch the dev build; Settings → Plan and usage should show the Test
   Store plans with Test Store prices (Test Store sales are local and free).
2. **Debug, purchase:** buy `monthly`; the entitlement must arrive, the paywall must stop offering a
   purchase and show Manage instead, and `Plan and usage` must read Pro.
3. **Release, internal track:** install from Play internal testing, buy with a license tester,
   restore on a second device, then refund/cancel and confirm the app drops back to Free after the
   period ends — not immediately.
4. **Proxy:** `curl https://<worker>/usage -H "X-Install-Id: test-install"` returns the free limits;
   a request past the allowance returns 402/429 with `X-Allowance: exhausted`; the app shows the
   usage warning instead of a silent failure.
5. **No leaks:** `unzip -p app-release.aab base/dex/* | strings | grep -E 'AIzaSy|AQ\.'` returns
   nothing.

Record the results in `agents/STATUS.md`. A step is not done because it was built; it is done when
that command was run and its output written down.
