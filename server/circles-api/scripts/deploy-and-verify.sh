#!/usr/bin/env bash
set -euo pipefail

# MeetingMind Circles — Milestone 1 Backend & Rules Deployment + Verification Script
# Deploys:
#   1. Cloudflare Worker (meetingmind-circles) with R2 (meetingmind-circles-media) & KV (RATE) bindings
#   2. Firestore Security Rules (firestore.rules -> meetingmind-mobile)
#   3. Live smoke verification against https://meetingmind-circles.craftflowtechnologies.workers.dev

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
WORKER_DIR="${REPO_ROOT}/server/circles-api"
WORKER_URL="${CIRCLES_WORKER_URL:-https://meetingmind-circles.meetingmind.workers.dev}"
FIREBASE_PROJECT="${FIREBASE_PROJECT:-meetingmind-mobile}"

echo "==> [1/5] Running Circles Worker unit tests..."
npm --prefix "${WORKER_DIR}" test

echo "==> [2/5] Syncing firestore.rules into rules test directory..."
cp "${REPO_ROOT}/firestore.rules" "${WORKER_DIR}/test/rules/firestore.rules"

if [[ "${SKIP_DEPLOY:-0}" == "1" ]]; then
  echo "==> SKIP_DEPLOY=1 set; skipping wrangler and firebase deploy."
else
  echo "==> [3/5] Ensuring R2 bucket 'meetingmind-circles-media' exists..."
  (cd "${WORKER_DIR}" && npx wrangler r2 bucket create meetingmind-circles-media 2>/dev/null || true)

  echo "==> [4/5] Deploying Cloudflare Worker 'meetingmind-circles'..."
  (cd "${WORKER_DIR}" && npx wrangler deploy)

  echo "==> [5/5] Deploying Firestore security rules to '${FIREBASE_PROJECT}'..."
  (cd "${REPO_ROOT}" && npx firebase-tools deploy --only firestore:rules --project "${FIREBASE_PROJECT}")
fi

echo "==> Running live endpoint smoke checks against ${WORKER_URL}..."
HEALTH_STATUS="$(curl -s -o /dev/null -w "%{http_code}" -A "MeetingMind-DeployCheck/1.0" "${WORKER_URL}/health")"
READY_STATUS="$(curl -s -o /dev/null -w "%{http_code}" -A "MeetingMind-DeployCheck/1.0" "${WORKER_URL}/ready")"
UNAUTH_STATUS="$(curl -s -o /dev/null -w "%{http_code}" -A "MeetingMind-DeployCheck/1.0" -X POST "${WORKER_URL}/v1/createCircle" -H "Content-Type: application/json" -d '{"name":"Smoke"}')"

echo "    GET  /health          -> ${HEALTH_STATUS} (expected 200)"
echo "    GET  /ready           -> ${READY_STATUS} (expected 200)"
echo "    POST /v1/createCircle -> ${UNAUTH_STATUS} (expected 401 unauthenticated)"

if [[ "${HEALTH_STATUS}" != "200" || "${READY_STATUS}" != "200" || "${UNAUTH_STATUS}" != "401" ]]; then
  echo "ERROR: Smoke check failed!" >&2
  exit 1
fi

echo "==> All checks passed!"
