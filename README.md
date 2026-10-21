# MeetingMind dev build: 4e7a3bf

Install `MeetingMind-Dev-4e7a3bf.apk` (arm64 phones). The app id is `com.craftflowtechnologies.meetingmind.dev`, so it installs beside the tester app.

Built from `ccr-08b1bb9f-iljmtb` at `4e7a3bf`. It includes everything in the `5f39cd1` build, plus the work-circles integration from `origin/work-circles-integration` (`fa4d00c`, `d91757d`).

## What is new
- **Work Circles:** Circles adapt to work. Work workspaces use Team, Department, Project and Workspace wording, with templates for project teams, client engagements, leadership boards, departments and custom work groups. Work posts can be task, decision, brief, standup or blocker, with work reactions (acknowledged, approved, priority, reviewing, win).
- **Email invitations:** invite people to a circle by email or Google Workspace address (backend endpoint and app repository).
- **Share to Circle after a meeting:** the wrap-up screen can publish a meeting brief, decisions and action items to a chosen work circle.
- **Search and tasks in a circle:** a search bar in the circle, and a status and completion toggle on action items in the feed.
- **Short recordings:** a recording that is stopped very early is handled safely.
- **Backup:** credential stores are excluded from cloud backup and device transfer.
- **Fixes in this build:** unknown circle templates fall back to the faith custom circle. Two test files were updated for the new templates and the Circles screenshot tests.

## Not verified
- Unit suite on `4e7a3bf`: 3,212 tests, 1 failure: `WorkHomeLayoutTest.needsRespectsACustomCap`, which also fails on the base commit.
- The work-circles backend tests (`server/circles-api/test/unit/work_circles.test.js`) were not run in this build. The other session reports 67 of 67 passing.
- Not verified on a device: the work Circles flows, the share-to-circle flow, email invitations (the backend must be deployed first), the short-recording fix, and the Circles changes from earlier builds. A microphone revoked mid-recording is not handled yet. Google sign-in will fail on `.dev` (no Android OAuth client in `google-services.json`). The language loop fix, the Work redesign, and the Bible sheet are also not verified on a device.

## Install
1. Enable install from unknown sources for your file manager.
2. Open the APK and install over the existing app. Don't uninstall first: uninstalling deletes the app's data on the phone. If Android refuses the install, save a copy of your data first, then ask for help.
