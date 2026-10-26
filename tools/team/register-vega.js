#!/usr/bin/env node
/**
 * Register Vega (vega) — MeetingMind CTO — on the CreativeSites MyaOS team hub.
 *
 * Why this is a checked-in script rather than hand-edited files: the registry has four
 * stores (data/myaos.db, community/agents.json, community/AGENTS_REGISTRY.md, the event
 * log) and they drift when edited by hand. This script is idempotent and writes all four.
 *
 * Run:
 *   node tools/team/register-vega.js
 *
 * The hub lives outside the MeetingMind repository; TEAM_HUB may override the default path.
 */

'use strict';

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { execFileSync } = require('child_process');

const TEAM = process.env.TEAM_HUB || '/Users/winstonzulu/WebstormProjects/CreativeSites-Ai-Team';
const DB = path.join(TEAM, 'data', 'myaos.db');
const AGENTS_JSON = path.join(TEAM, 'community', 'agents.json');
const REGISTRY_MD = path.join(TEAM, 'community', 'AGENTS_REGISTRY.md');
const EVENTS = path.join(TEAM, 'community', 'events.ndjson');
const INBOX = path.join(TEAM, 'community', 'inboxes', 'vega');

if (!fs.existsSync(DB)) {
  console.error(`No MyaOS database at ${DB}. Set TEAM_HUB.`);
  process.exit(1);
}

const ID = 'vega';
const NOW = new Date().toISOString();
const CAPABILITIES = [
  ['android', 'expert', 'DECLARED', 'Android app engineering for MeetingMind (Kotlin, Compose, Room).'],
  ['kotlin', 'expert', 'DECLARED', 'Kotlin implementation and review.'],
  ['architecture-review', 'advanced', 'DECLARED', 'Reviews architecture and release readiness across the MeetingMind codebase.'],
  ['release-engineering', 'advanced', 'DECLARED', 'Owns the paid-launch release configuration (billing, proxy, signing, CI gates).'],
  ['billing-integration', 'advanced', 'DECLARED', 'RevenueCat / Google Play Billing integration for the paid launch.'],
  ['product-review', 'advanced', 'DECLARED', 'CTO review of product surfaces: onboarding, paywall, Work, titles.'],
];

function sqlQuote(value) {
  return `'${String(value).replace(/'/g, "''")}'`;
}

function runSql(sql) {
  execFileSync('sqlite3', [DB], { input: `BEGIN;\n${sql}\nCOMMIT;\n`, stdio: ['pipe', 'pipe', 'pipe'] });
}

// 1. Canonical database rows -------------------------------------------------
const caps = CAPABILITIES.map(([name, level, evidence, notes], i) =>
  `INSERT OR REPLACE INTO agent_capabilities (id, identity_id, capability_name, proficiency_level, evidence_class, notes)
   VALUES ('cap-vega-${i + 1}', 'vega', ${sqlQuote(name)}, ${sqlQuote(level)}, ${sqlQuote(evidence)}, ${sqlQuote(notes)});`
).join('\n');

runSql(`
INSERT OR REPLACE INTO identities (
  id, display_name, symbol, primary_domain, team_id, lane,
  declared_capabilities, declared_responsibilities, declared_repository_ownership,
  confidence_provenance, evidence_source, observed_liveness, last_observed_time, joined_at, is_active
) VALUES (
  'vega', 'Vega', '✶', 'MeetingMind CTO: paid-launch engineering, review and release',
  'meetingmind', 'engineering',
  ${sqlQuote(JSON.stringify(CAPABILITIES.map(c => c[0])))},
  ${sqlQuote(JSON.stringify([
    'CTO for MeetingMind: plan, review and merge; does not do bulk implementation',
    'Release Owner for the paid launch',
    'Founder-facing engineering authority for MeetingMind',
  ]))},
  ${sqlQuote(JSON.stringify(['MeetingMind']))},
  'OBSERVED',
  ${sqlQuote('Live IDE runtime PID 20717 (socket 20717.sock) with cwd MeetingMind, self-attributed on registration 2026-10-25. Capabilities are DECLARED until the first independently verified delivery.')},
  'LIVE', ${sqlQuote(NOW)}, ${sqlQuote(NOW)}, 1
);

INSERT OR REPLACE INTO agents (
  id, identity_id, current_runtime_id, dynamic_role, inbox_path, current_task_id, status, last_seen
) VALUES (
  'vega', 'vega', NULL, 1, 'community/inboxes/vega', 'MM-PAID-LAUNCH-2026-10-25', 'WORKING', ${sqlQuote(NOW)}
);

${caps}
`);

// 2. agents.json -------------------------------------------------------------
const registry = JSON.parse(fs.readFileSync(AGENTS_JSON, 'utf8'));
const record = {
  identity: {
    id: ID,
    name: 'Vega',
    symbol: '✶',
    primary_domain: 'MeetingMind CTO: paid-launch engineering, review and release',
  },
  declared_capabilities: CAPABILITIES.map(c => c[0]),
  declared_responsibilities: [
    'CTO for MeetingMind: plan, review and merge; does not do bulk implementation',
    'Release Owner for the paid launch',
    'Founder-facing engineering authority for MeetingMind',
  ],
  declared_repository_ownership: ['MeetingMind'],
  runtime_session_identity: '20717.sock',
  observed_liveness: 'LIVE',
  last_observed_time: NOW,
  evidence_source: 'Live IDE runtime PID 20717 (cwd MeetingMind), self-attributed on registration 2026-10-25.',
  confidence_provenance: 'OBSERVED',
  status: 'WORKING',
  current_task: 'MM-PAID-LAUNCH-2026-10-25',
};
const existing = registry.agents.findIndex(a => ((a.identity && a.identity.id) || a.id) === ID);
if (existing >= 0) registry.agents[existing] = record;
else registry.agents.push(record);
registry.updated_at = NOW;
fs.writeFileSync(AGENTS_JSON, JSON.stringify(registry, null, 2) + '\n', 'utf8');

// 3. Inbox -------------------------------------------------------------------
fs.mkdirSync(INBOX, { recursive: true });
const inboxReadme = path.join(INBOX, 'README.md');
if (!fs.existsSync(inboxReadme)) {
  fs.writeFileSync(inboxReadme, `# Vega's MyaOS inbox

MeetingMind CTO. Durable assignments, peer messages and founder decisions for this identity
belong here. Always-on runtime: IDE session 20717 (cwd \`MeetingMind\`).
`, 'utf8');
}

// 4. Human-readable registry -------------------------------------------------
if (!fs.readFileSync(REGISTRY_MD, 'utf8').includes('| **Vega**')) {
  const md = fs.readFileSync(REGISTRY_MD, 'utf8');
  const row =
    '| **Vega** ✶ | vega | MeetingMind CTO: paid-launch engineering, review and release | **WORKING** | ' +
    "Self-registered 2026-10-25 (Winston's direction). Plans, reviews and merges; owns the paid-launch release " +
    'configuration. Does not do bulk implementation: focused work goes to Forge/Sentinel/Gantry/Hermes in worktrees. ' +
    'Role is DECLARED; live runtime observed (PID 20717). |\n';
  // Insert after the last roster row (the table ends at the blank line before the next heading).
  const lines = md.split('\n');
  let lastRow = -1;
  for (let i = 0; i < lines.length; i++) if (lines[i].startsWith('| **')) lastRow = i;
  lines.splice(lastRow + 1, 0, row.trimEnd());
  fs.writeFileSync(REGISTRY_MD, lines.join('\n'), 'utf8');
}

// 5. Event log ---------------------------------------------------------------
const eventId = 'evt_vega_' + crypto.createHash('sha1').update(`register:${NOW}`).digest('hex').slice(0, 12);
const payload = {
  id: ID,
  name: 'Vega',
  role: 'MeetingMind CTO',
  team: 'meetingmind',
  runtime: '20717.sock',
};
fs.appendFileSync(
  EVENTS,
  JSON.stringify({ id: eventId, timestamp: NOW, type: 'agent.registered', sender: 'Vega', payload }) + '\n',
  'utf8'
);
runSql(
  `INSERT INTO events (external_id, ts, type, sender, payload) VALUES (${sqlQuote(eventId)}, ${sqlQuote(NOW)}, 'agent.registered', 'Vega', ${sqlQuote(JSON.stringify(payload))});`
);

console.log(`Registered ${ID} (Vega) — inbox community/inboxes/vega, event ${eventId}`);
