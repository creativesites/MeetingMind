# Agent rules: MeetingMind

These rules apply to every agent working in this repository, human or AI. Keep this file short. Detail lives in `agents/`.

## Team and models

- **The CTO agent (the main session) plans, reviews and merges.** It does not do bulk work itself.
- **The Haiku-tier model is the project's default, for the CTO and every sub-agent.** Use another tier only when the founder asks for it for a specific task. Set it explicitly on every sub-agent call.
- Repo files never name a model version, per the commit-attribution rule. Say "Haiku-tier" if you must refer to it.
- Sub-agents run one focused task each, in their own git worktree, and report back in at most 200 words.

## Git

- Designated branch: `claude/meetingmind-mvp-planning-7dgowm`. Work there, commit there, push there.
- Other branches holding work: `ccr-5dc13c30-a5eqgv` (companion, Read to Me, moments), `ccr-4fc36d78-nhc3lx` (Circles), `ccr-e1f66026-rff3e0` (onboarding and billing; not yet compiled, build only when asked). Merge them in; do not rebase them.
- Merge, never rebase, on any shared branch. Never force-push, except to the `apk-downloads` publish branch (see `agents/DEV_BUILDS.md`).
- Before pushing: `git fetch`, then confirm the remote is an ancestor of HEAD, then `git push -u`.
- Commit messages: one focused message, a body where it helps, and only the `Claude-Session` trailer. No model names, no `Co-Authored-By` lines naming a model.
- Do not open pull requests unless asked.
- Worktrees: `git worktree add -b wt-<task> /home/user/mm-<task> <base>`. Copy `local.properties` into each. Remove when merged.

## Work discipline

- Read `agents/TESTING.md` before running tests. It lists the gotchas that already cost us time.
- Run the tests that match your change first. Run the full suite before the APK, not for every small change.
- Gradle memory: parallel agents use `-Xmx2g --max-workers=2`. A single full run uses `-Xmx6g --max-workers=4`. The machine has about 15 GB.
- Long runs go in the background; check them with notifications, not polling loops.
- Never weaken or delete a test to get green. Fix the cause, or report it.
- Before deleting, overwriting or publishing anything, look at it first.

## Product rules that must not be broken

Full list: `agents/PRODUCT.md`. The short version:
- Generic by default. Personal context only when the person turns it on.
- Experience first, notes after: a prominent button starts the experience its label names; "Write…" buttons are quiet.
- A devotional is never empty. The bundled reading is always the fallback.
- Every note gets its moment, including notes with no speech. Failed and sacred notes stay plain.
- Circle content lives on the server. Personal notes stay local.
- Learning is out of the MVP.

## Reporting

- Report facts with the command you ran and its result. Say what you could not verify, especially on a real device.
- Update `agents/STATUS.md` when you finish a task that changes what is merged, decided or blocked.
