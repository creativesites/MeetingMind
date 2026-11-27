# Product decisions (founder)

## Team
- Preferred model for the project: the Haiku-tier model, for the CTO and every sub-agent. Use another tier only when the founder asks for it for a specific task.

## Scope
- MVP: transcription (offline and online), workflows, the Work vertical, the Faith vertical, the Learning vertical.
- Learning is in the MVP and the first Play release (founder decision, 2026-11-16; replaces the earlier cut). Release 1 scope is in `design/learning-vertical-design-spec.md`.
- Circles: Firebase plus the Worker. Circle content lives on the server. Personal notes stay local. End-to-end encryption dropped for Circles. Cap of 50 members per circle (founder decision, 2026-10-21; matches Cloudflare Worker enforcement). First increment: creation, membership, invitations, text chat. Resource sharing later.
- Tiering & Billing (founder decision, 2026-10-22; replaces the 2026-10-21 closed-testing toggle): the release build always enforces tiers and the paywall, with no in-app switch, because Play promotes the same build from closed testing to production. Testers get Pro through RevenueCat promotional entitlements (plus Play license testers for test purchases); ending that is an account change, not an app change. The tier switch is a debug-only tool: it suppresses unasked paywalls and lets a tester use their own Gemini key without a plan. It is hidden in release, and a release build forces production tiering even if a debug value was saved. Before launch: a production RevenueCat key in release, and server-side entitlement checks on the managed AI proxy.
- **Paid launch (founder decision, 2026-10-25).** The Play launch is paid. The managed Gemini proxy (`server/gemini-proxy`) is approved for release builds and is the only AI route a Play build has; it holds the Google key and meters each plan's monthly allowance. A Play bundle carries **no raw Google key**; the CI build fails if one is found. The debug build keeps the raw key and the RevenueCat Test Store key so a fresh development install works with no setup. Release is built only with a production RevenueCat key and a proxy URL — the bundle task fails without either.
- ~~Paid plan scope for 1.0 (CTO decision, 2026-10-25): the paid plan's value is (a) the managed cloud AI allowance and (b) the right to use a personal Gemini key (BYOK). Ask across meetings, Speaker ID, Read-to-Me and Circles are free for everyone in 1.0 and are never sold. The paywall may claim only those two capabilities, and managed cloud AI is never gated to paid: free users route through the proxy, which meters the free allowance.~~ **Superseded:** paid capabilities were added from 2026-11-15 (`core/billing/Plan.kt`: CONNECTED_INTEGRATIONS, AUTOMATION_RECIPES, DEEP_SYNTHESIS, CHURCH_ORG_WORKFLOWS) and the founder set the Learning and Personal tiers on 2026-11-27 (below).
- **Tiering for Learning and Personal (founder decision, 2026-11-27): core free, extras paid.** Free, within the metered cloud allowance: lessons, all practice formats, the sky, Listen, Ask, Explain it aloud, the garden, routines, journeys, journal, ideas, keepsakes, vault sync. Paid (BYOK, Pro, Church; through `FeatureGate.can`, which honours closed testing): the exam plan and cram sheet across lectures (DEEP_SYNTHESIS), a study pack after every lecture and Personal autopilot (AUTOMATION_RECIPES), sharing a study pack to several Circles at once (CHURCH_ORG_WORKFLOWS). Studying what already exists is never blocked; a paid extra shows a calm card with See plans, never a dead end. Managed cloud AI is still never hidden from free users.
- ~~Built-in AI (founder decision B1, 2026-10-22): every build, Play included, carries the raw Gemini key, so a fresh install can record with no personal key and no downloaded model…~~ **Superseded on 2026-10-25 by the paid-launch decision above.** The raw key is debug-only; a Play build reaches AI only through the metered proxy, and a bundle containing a Google key fails its own build.
- Work UX decisions D1–D7 approved as written in `docs/mvp/WORK_UX_AUDIT.md` §7 (founder decision, 2026-10-22).
- Circles (founder decisions, 2026-10-22; plan in `docs/planning/CIRCLES_PLAN_2026-10-22_THEMIS.md`):
  - A meeting that is confidential or must stay on the device can never be shared to a Circle (MVP: no override). Only Work circles are offered for meeting shares.
  - Sharing a meeting brief to a Work circle with external (client or guest) members needs an extra confirmation.
  - Overview is the default tab of a Work circle.
  - Devotionals can be shared to Faith circles as an excerpt of about 600 characters plus a link to the reading.
  - Chat refuses `.mov` and `3gpp` videos with a clear message before upload. Converting them comes later.
- Study (the approved redesign canvas, `design/study/`, `feature/learn/`) runs beside Learning on its own `learn_study` routes until the founder decides how to combine them (2026-11-27). Rules: `docs/mvp/STUDY_AUDIT.md`.

## Devotionals
- Always a reading for today, with no network and no key (Morning and Evening, bundled).
- On by default for new installs; the write runs at 00:01 local time; the notification fires at the chosen time.
- "Write one with AI" makes a separate devotional labelled "Written with AI". The plan reading stays in the archive.
- Generic by default. Personal context (profile, sermons, prayer list) only when the person turns on Personal touch.

## Moments (Zuri)
- First Keep on every recording (founder decision, 2026-11-17: "make sure it shows on every recording"): the first open of every finished recording plays it, even days later, on battery saver, on a low-end phone or after a retry; reduced motion shows its still end state. Only a running recording defers it. The chime plays only on the two once-ever moments (the hello sample note and the first real recording). Nothing else (the first-result offer included) is drawn over it.
- Unveil on every open of a note: Notes list, meeting detail, wrap-up, note editor. Every note gets its moment, every time it is opened (founder decision, 2026-10-14, overrides the earlier guards below).
- Every note gets its moment, including notes with no speech, typed notes, failed notes and sacred notes (founder decision, 2026-10-14). The earlier rule that failed and sacred notes stay plain is withdrawn.
- Reduced motion and battery saver: decided for the First Keep (above; reduced motion gets the still end state, never an animation). For the Unveil on later opens they keep their plain or nod behaviour.
- Sermons get the quiet version; a recording in progress defers the moment.
- Zuri may share the note screen during a designed moment, but never covers, edits or replaces the user's own words.

## Experience rules
- Experience first, notes after. A prominent button starts the experience its label names. Buttons that only create a note say "Write…" and are quiet.
- Simple screens with power one tap away. Never remove a capability to simplify. No Simple/Advanced switch.
- Home: Everyday is the default for new installs; Today is kept for existing users; users can pick.
- Settings: eight groups, search at the top, advanced options folded.

## Companions and names
- Zuri is the companion. Nas, Wren and Page are further forms.
- Companion voice and tone: warm, not chatty; never assumes it is morning (time-aware).

## Bible study
- One Bible place: Continue, Today's reading, How I feel, Study a passage, plans, study journal.
- Guided sessions: SOAP and verse by verse first. Zuri asks, never writes the answer.
- The study journal saves what the person types as they type; no blank note first.

## Learning Vertical (Production)
- **Student Companion**: Uses user's global selected companion (`rememberCompanionSettings()`). Socratic, adaptive tutoring across 10 academic domains (Math/Stats, Engineering, Accounting/Finance, Programming/CS, Economics, Chemistry, Biology/Medicine, Law/Humanities, High School, General).
- **Expand & Enrich**: BlockSource.ENRICHED blocks appear folded behind a 1-tap unpack pill.
- **Academic Notation**: Normalized spoken math & equations rendered via native LaTeX blocks with MathJax/KaTeX styling.
- **Active Recall & Teach-Back**: Spaced repetition grading with Leitner/SM-2, "Explain My Mistake" root misconception diagnostics, and verbal Teach-It-Back rubric evaluation.
- **Exam Readiness Matrix & 1-Page Cram Sheet**: High-yield exam prioritization, countdown timers, and shareable cram sheets to Study Circles.
- **Pricing & Billing Contract**: Strictly 1.0 pricing (Free taste + BYOK $4.99/mo + Pro $12.99/mo). Zero artificial feature locks.
