# MeetingMind dev build

**MeetingMind-Dev-4ddcdd2.apk**: "MeetingMind Dev" built from commit `4ddcdd2` on `claude/meetingmind-mvp-planning-7dgowm`
(arm64-v8a). It installs beside the tester app (`com.craftflowtechnologies.meetingmind.dev`) and keeps its own data.

New in this build:
- **Devotionals:** the bundled reading loads on device; daily devotionals are on by default for new installs, with the 00:01 write.
- **Moments:** every note gets its moment, including notes with no speech: the Unveil when a note opens (Notes list, meeting detail, wrap-up), First Keep on note-ready. Failed and sacred notes keep their guards.
- **Circles:** first increment: v1 retired, creation defects fixed, diagnosable sign-in. Needs the Worker and rules redeployed.
- **Read to Me:** listen to one passage from the verse sheet and the Study workspace; Smart Read, learned defaults, settings sheet.
- **Today home:** the design update.
- **Companion states, sample-first onboarding, recording markers, migration 25.**

Install: allow installs from unknown sources, then open the APK. Only the latest build is kept on this branch.
