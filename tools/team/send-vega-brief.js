#!/usr/bin/env node
/**
 * Send Vega's first brief to the MeetingMind squad and record it on the event bus.
 *
 * Idempotent: a brief whose file already exists is skipped, so re-running never duplicates.
 * Run: node tools/team/send-vega-brief.js
 */

'use strict';

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { execFileSync } = require('child_process');

const TEAM = process.env.TEAM_HUB || '/Users/winstonzulu/WebstormProjects/CreativeSites-Ai-Team';
const DB = path.join(TEAM, 'data', 'myaos.db');
const EVENTS = path.join(TEAM, 'community', 'events.ndjson');
const NOW = new Date().toISOString();
const STAMP = NOW.replace(/[:.]/g, '-');
const TASK = 'MM-PAID-LAUNCH-2026-10-25';

const SHARED = `# Founder decisions, 2026-10-25 — paid launch starts now

Winston (founder) decided:

1. **The Play launch is paid.** RevenueCat has an account but no products, entitlements or
   offerings yet. The app has its own paywall; RevenueCat *paywalls* are not used.
2. **The managed Gemini proxy is approved** for release builds. This amends founder decision B1
   (2026-10-22, raw Gemini key in every build): Play builds route managed AI through
   \`server/gemini-proxy\` and are metered there. The raw key stays in debug builds only. The
   founder confirmation for this amendment is this message; Themis/Daedalus should record it.
3. **Work happens on a new branch**: \`release/1.0-paid-launch\`, cut from
   \`work-circles-integration\` + a checkpoint commit of the in-flight tree.

# What the CTO review found (evidence in the repo, not opinion)

- Release build hardcodes an empty RevenueCat key (\`app/build.gradle.kts\`), so the paywall can
  never sell anything, while Settings still opens it and blames the network.
- \`Capability.PRO_FEATURES\` has zero call sites: Ask across meetings, Speaker ID, Read-to-Me and
  Circles are free. Only \`BYOK_KEY\` is gated. Tiering does not exist in code, only in copy.
- \`TieringMode\` defaults to CLOSED_TESTING with a user-facing switch; \`effectiveEntitlements\` is
  never called, so the "tester unlock" unlocks nothing.
- A failed Room write at the end of a recording still clears the recovery journal and reports
  success (\`MeetingRecordingService.kt\`), so a finished recording can become unreachable.
- Titles: the title is a field in one synthesis JSON. Six failure paths fall back to
  "Meeting — Oct 24", and nothing ever retitles.
- Work has two live homes (\`ProfessionalHome\` via \`HomeStyle.PROFESSIONAL\` and
  \`WorkPageScreen\`), a shadow component library, and a design-system guard that watches only
  7 of ~28 Work UI files.

# What is being executed now

Implementation is running in isolated worktrees from \`release/1.0-paid-launch\`, in this order:
recording durability, title generation, billing/tiering/paywall, onboarding truthfulness, Work
convergence. Sentinel verifies; Gantry builds and packages; Hermes owns the proxy deploy; Ledger
records intake. Each task reports evidence (command + result), never a claim.
`;

const BRIEFS = [
  {
    to: 'themis',
    type: 'DECISION',
    priority: 'HIGH',
    subject: 'Paid launch decided; proxy approved (amends B1)',
    body: `${SHARED}
## For Themis
- Record the B1 amendment in \`agents/PRODUCT.md\` (the current text says every build carries the
  raw key; that is now debug-only, with the proxy in release).
- Guardrails needed from you before the onboarding cuts land: which steps are allowed to stay on
  the sample route, and whether "Decide later" may remain a way to finish onboarding without an
  AI mode. My proposal: welcome → companion → sample → what-do-you-capture → done (4 steps), with
  AI setup automatic and model choices moved to Settings.
- Paywall copy must stop advertising Pro features the code does not gate. Either the features get
  a gate or the table changes. Your call on which, per plan.
`,
  },
  {
    to: 'synapse',
    type: 'ROUTING',
    priority: 'HIGH',
    subject: 'Route the paid-launch work orders',
    body: `${SHARED}
## For Synapse
Work orders to route (capability-scored, per MyaOS):
1. Recording durability — Forge. Path: \`MeetingRecordingService.stopRecording\` + repository +
   failure-path test.
2. Title reliability — Forge. Dedicated title step, \`titleSource\` column + migration, content
   fallback, backfill, tests for the six failure paths.
3. Billing and tiering — Forge (code) + Gantry (release config/CI) + Sentinel (verification).
   Production key from CI only; no user-facing tier switch in release; entitlement-driven gates;
   owned/pending states; usage warnings wired.
4. Proxy deploy and release wiring — Hermes. \`PLAN_ALLOWANCES\`, secrets, \`GEMINI_PROXY_URL\` in
   the release build.
5. Onboarding truthfulness — Forge. Remove the BYOK dead end, gate the cloud promise, permissions
   on the record route, remove Learning, localize or hide the picker.
6. Work convergence — Forge. ScreenHeader, widen the guard, state parity, retire ProfessionalHome.
7. Verification — Sentinel, independently, per work order.
Note: none of the squad currently has a live MyaOS runtime. Until that changes I execute in this
session and treat your routing as the record of ownership. Reply in my inbox once you are live.
`,
  },
  {
    to: 'ledger',
    type: 'INTAKE',
    priority: 'HIGH',
    subject: 'Intake: paid-launch blockers (12 items)',
    body: `${SHARED}
## For Ledger
Please record these as intake against \`MM-PAID-LAUNCH-2026-10-25\`, deduplicated against
\`agents/STATUS.md\` and the mission audit:

P0: release billing configuration; metered cloud AI; recording durability; shipping baseline and
artifact provenance; CI gate (tests + bundleRelease + signing/R8 check).
P1: title reliability; paywall truthfulness (owned/pending/refresh/trial eligibility/hide when
unconfigured); onboarding truthfulness; Work convergence; Circles deploy + rules + partial-share
failures + 50-member cap; Play compliance (account deletion, privacy policy, Data safety);
device verification pass (mic revocation, Doze, Google Sign-In on a release build, push, R2).
P2: R8/minification and APK size; i18n of hardcoded surfaces.
`,
  },
  {
    to: 'mira',
    type: 'REVIEW_REQUEST',
    priority: 'NORMAL',
    subject: 'Independent challenge requested: paid-launch plan',
    body: `${SHARED}
## For Mira
Challenge the sequencing and the two load-bearing claims:
1. That the paid launch can ship on the proxy without the raw key in Play builds (cost, latency,
   what happens when the proxy is down mid-recording).
2. That the tiering gate work is small — my estimate is four moves (one enforcement point, one
   FeatureGate, one feature × plan table with tests, release config), not a redesign. If you think
   that estimate is wrong, say where.
Reply with objections, not agreement.
`,
  },
  {
    to: 'forge',
    type: 'TASK',
    priority: 'HIGH',
    subject: 'Work order 1: recording durability; order 2: title reliability',
    body: `${SHARED}
## For Forge
Two work orders, separate worktrees, branch \`wt-recording-durability\` and \`wt-title-reliability\`,
both cut from \`release/1.0-paid-launch\`.
1. \`stopRecording\` must not clear the recovery journal or report success when
   \`createInitialMeeting\` throws. Keep the audio recoverable and surface a truthful failure.
   Add the persistence-failure regression test that does not exist today.
2. Title reliability: dedicated title step (not a field in the synthesis JSON), \`titleSource\`
   column + Room migration, content-derived fallback rather than a date, backfill when a model or
   key becomes available, never overwrite a user rename. Tests for all six failure paths.
Report with the command you ran and its result.
`,
  },
  {
    to: 'sentinel',
    type: 'TASK',
    priority: 'HIGH',
    subject: 'Verification seat: paid-launch gates',
    body: `${SHARED}
## For Sentinel
Independent verification is required for each of these, with the command and its result recorded:
recording-durability failure path; title fallback for each failure mode; release build refuses to
bundle without a production RevenueCat key and without a proxy URL; no tier switch reachable in a
release build; PRO features actually gated; paywall hides (does not blame the network) when
billing is unconfigured. You must also attempt to break each fix, not just confirm it.
`,
  },
  {
    to: 'gantry',
    type: 'TASK',
    priority: 'HIGH',
    subject: 'Build/release config: prod key guard, proxy URL, R8, artifact provenance',
    body: `${SHARED}
## For Gantry
1. Release build: \`REVENUECAT_API_KEY\` and \`GEMINI_PROXY_URL\` come from CI secrets; the bundle
   task fails loudly when either is missing. Debug keeps the test key.
2. Decide and implement R8 (\`isMinifyEnabled = false\` today) with keep rules for Room,
   Sherpa-ONNX and MediaPipe, or record why it stays off for 1.0.
3. Artifact provenance: the APK/AAB must name the exact commit it was built from, and STATUS must
   quote that hash.
`,
  },
  {
    to: 'hermes',
    type: 'TASK',
    priority: 'HIGH',
    subject: 'Deploy the Gemini proxy and set real allowances',
    body: `${SHARED}
## For Hermes
\`server/gemini-proxy\` is approved for production. Needed: KV namespace id, \`GEMINI_API_KEY\`,
\`RC_SECRET_KEY\`, and \`PLAN_ALLOWANCES\` with real numbers (free/pro/church, audio seconds and
actions per month) derived from a cost model, not invented. Then deploy and prove it: \`/usage\`
with a free install id, a refused request past the allowance (402/429), a retry charged once.
Report the worker URL so it can go into the release build.
`,
  },
];

function sqlQuote(v) {
  return `'${String(v).replace(/'/g, "''")}'`;
}

const created = [];
for (const brief of BRIEFS) {
  const dir = path.join(TEAM, 'community', 'inboxes', brief.to);
  fs.mkdirSync(dir, { recursive: true });
  const file = path.join(dir, `${STAMP}_vega_${brief.subject.toLowerCase().replace(/[^a-z0-9]+/g, '_').slice(0, 40)}.md`);
  if (fs.existsSync(file)) continue;
  const header = `---\nfrom: "@Vega"\nto: "@${brief.to}"\npriority: "${brief.priority}"\ntype: "${brief.type}"\nrelated_task: "${TASK}"\n---\n\n# ${brief.subject}\n\n`;
  fs.writeFileSync(file, header + brief.body, 'utf8');

  const rel = path.relative(TEAM, file);
  const eventId = 'evt_vega_' + crypto.createHash('sha1').update(`${brief.to}:${NOW}`).digest('hex').slice(0, 12);
  const payload = { to: brief.to, type: brief.type, priority: brief.priority, subject: brief.subject, relatedTask: TASK, file: rel };
  fs.appendFileSync(EVENTS, JSON.stringify({ id: eventId, timestamp: NOW, type: 'message.sent', sender: 'Vega', payload }) + '\n', 'utf8');
  created.push([eventId, payload]);
}

if (created.length) {
  const rows = created.map(([id, p]) =>
    `INSERT INTO events (external_id, ts, type, sender, payload) VALUES (${sqlQuote(id)}, ${sqlQuote(NOW)}, 'message.sent', 'Vega', ${sqlQuote(JSON.stringify(p))});`
  ).join('\n');
  execFileSync('sqlite3', [DB], { input: `BEGIN;\n${rows}\nCOMMIT;\n`, stdio: ['pipe', 'pipe', 'pipe'] });
}

console.log(`Sent ${created.length} brief(s).`);
