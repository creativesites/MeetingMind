# MeetingMind design reference

Drop this folder into the repo root as `design/`. Everything here is the visual source of truth for the redesign.

## Viewing the designs

Open `Recording Page Redesign.dc.html` in any browser — no server, no build step. It is a zoomable canvas of Android frames (412dp wide) grouped into numbered turns, **newest at the top**. Every option carries a visible id badge; append `#<id>` to the URL to jump straight to one, e.g. `…/Recording Page Redesign.dc.html#7b`.

The frames are **live**. Tap the ring, `＋ Marker`, `Note`, `Pause`, "Change workflow", "Process later", the Meeting/Sermon/Lecture chips, flagged words, citations, decisions, `⋯`, the stepper arrows, "Show original" and the cleanup modes to see each sub-state. A screenshot only shows you one of them.

`support.js` and `android-frame.jsx` are the runtime and the device bezel. Keep all three files together.

## Specs

| File | Covers |
| --- | --- |
| `capture-pipeline-implementation.md` | **Part 2** — capture → process → review. New recording, recording, markers, notes, saved, processing, AI engine, settings, home, search, import. Reconciled against the current repo; read this first for anything outside `feature/meetingdetail/`. |
| `recording-page-implementation.md` | **Part 1** — meeting detail: overview stepper, transcript states, review queue, AI toolkit, Ask AI. Largely built already. |

Part 1 §1 (colour, type, shape, spacing) is the design system for both documents.

## Screen index

| Id | Screen |
| --- | --- |
| `#7a` | New recording (quick setup) |
| `#7b` | Recording — calm · marker sheet · quick note · paused |
| `#7c` | Recording saved (process now / later) |
| `#7d` | Processing, stage list typed by recording type |
| `#6b` | Processing queue — running / waiting / finished |
| `#6c` | AI engine |
| `#6d` | Settings |
| `#5a` | Home |
| `#5b` | Search |
| `#5c` | Import audio |
| `#3a`–`#3d` | Transcript — reading, tools, editing, result review |
| `#2a`–`#2c` | Overview step 2, review queue, Ask AI |
| `#1b` | Overview stepper (chosen direction) |
| `#1a`, `#1c`, `#4a`–`#4c`, `#6a` | Rejected or superseded — **do not implement** |

Where a spec and the design file disagree, the design file wins.
