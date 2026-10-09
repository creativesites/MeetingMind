# Rules for every MeetingMind agent

Paste this at the top of every sub-agent prompt. The task-specific part comes after it.

## Product rules

> **Every surface in MeetingMind must earn its place.**

> **Do not equate discoverability with prominence.** A feature being findable does not mean it deserves a big card.

> **A recording is the source. The Note is the workspace.** Never overwrite source audio or the original
> transcript.

**The four questions.** Every screen you touch must answer, at a glance:

1. Where am I?
2. What's important here?
3. What can I accomplish?
4. What should I do next?

**Before you add or keep a UI element,** ask the "Why is this here?" test:

1. What problem does it solve?
2. Why is it visible here?
3. Why is it this size and prominence?
4. Does the result meet the user's expectation?
5. Could it be simpler?
6. Would removing it make the screen better?

**Visual hierarchy:**

- One primary element per screen.
- Secondary features stay quiet.
- No large cards that are only gateway buttons.
- No feature inventory posing as UI.
- An empty section renders nothing, not a placeholder card.

**Honesty:**

- Never fail silently. Every fallback (no model, AI failed, offline) shows one plain line and a Retry where possible.
- Never fabricate AI output.
- Faith content follows the faith contract (`app/src/main/assets/prompts/faith_contract.md`).

## Engineering rules

**Layout.** The code is in `app/src/main/java/com/craftflowtechnologies/meetingmind/`. Read `docs/mvp/MVP_PLAN.md`
for your task ID and `docs/mvp/AUDIT.md` for context.

**Styling.** Use only design-system tokens and components (`ui/theme`, `core/ui`). Do not add:

- `fontSize = N.sp` literals
- `Color(0x…)` literals
- ad-hoc `RoundedCornerShape(N.dp)`
- ad-hoc paddings off the spacing scale

If a token is missing, say so in your report. Don't invent one.

**Hot files.** Don't touch these unless your task explicitly names them, because other agents work in parallel:

- `MainActivity.kt`
- `feature/navigation/AppNavigation.kt`
- `core/database/MeetMindDatabase.kt`

**Database.** No schema change without a Room migration, a bumped version, an exported schema JSON and a migration
test. Real users have data on schema 23.

**Main thread.** No `runBlocking` on main paths, and no unbounded `SELECT *` flows feeding UI.

**Build.** The Android SDK is at `/root/android-sdk` (`local.properties`). Run
`./gradlew --no-daemon -q compileDebugKotlin testDebugUnitTest` before you finish. If Maven returns 429, wait and
retry. That error is a rate limit, not a code problem.

**Commits.**

- Make one focused commit with a clear message.
- Don't push. The CTO reviews and merges.
- Don't put model names in commits or code.

**Report** in at most 300 words:

- what changed (files)
- what you verified (command plus result)
- anything you could not do
- anything that needs a product decision
