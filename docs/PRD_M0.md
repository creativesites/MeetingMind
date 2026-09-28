# PRD: M0, foundations (data safety, theme foundation, CI, new app identity)

Part of `docs/PLAN_V3.md` (workstreams W0 and W5, first half). Status: **approved scope, open items in §9.**

## 1. Why M0 comes first

MeetingMind is the owner's primary notes app, and every later milestone changes the database schema and every screen.
Today:

| Risk | Evidence |
|---|---|
| A missed migration **wipes every note** | `MeetMindDatabase.kt`: `.fallbackToDestructiveMigration()`, `exportSchema = false` |
| A new phone means **losing every note** | `backup_rules.xml` and `data_extraction_rules.xml` exclude `meetmind_database` from cloud backup **and** device transfer |
| No way out of the app with everything | Exports are per note or per recording. There is no whole-library export, backup or restore |
| Delete is permanent | `NoteRepository.deleteNote` hard-deletes the note, its blocks and attachment files, with no undo |
| AI, paste and restore will soon make large edits | The editor's undo only lasts while the note is open (`UndoHistory`, in memory) |
| Draft filtering scans every row | `metadataJson NOT LIKE '%"draft":"1"%'` in every list query |
| Light-only, hardcoded colours | `MeetMindTheme(darkTheme = false)`; about 400 `Color(0x…)` / `Ink*` uses in 27 files |
| The package is still a template id | `applicationId = "com.aistudio.meetmind.qxynvp"`, `namespace = "com.example"` |

## 2. Goals and non-goals

**Goals:** no data can be lost to an update, a delete, a bad edit or a new phone. Every screen draws from
theme tokens, with dark mode as the default. Every push is built and tested. The app has its real
identity, and moving to it loses nothing.

**Non-goals (later milestones):** theme presets, accent picker and fonts (M3); cloud sync (M8); merge-restore (M0 restore
replaces); per-note encryption; paging and FTS (M1).

## 3. Release sequence

The rename makes a **new app** that cannot read the old one's data, so the order matters:

| Build | Contents | Why this order |
|---|---|---|
| **v31** (old id) | Devotional/reminder timing fix (done), **M0.1 data safety** in full, including backup and restore | The installed app gains a way to back everything up first |
| **v32** (old id) | **M0.2 theme foundation**, dark by default, and **M0.3 CI** | Independent of data. Ships on the old id so screenshots can be compared like for like |
| **v33** (**new id**) | **M0.4 identity**, with a first-run screen that offers "Restore from a MeetingMind backup" | The owner makes a backup in v31/v32, installs v33 and restores. The old app is removed once the restore is checked |

## 4. M0.1: data safety

### 4.1 Migration hardening
- Turn on `exportSchema = true` (KSP arg `room.schemaLocation = app/schemas`). **First generate and commit `14.json`
  from the current schema before changing anything**, so 14 → 15 can be tested for real. Versions 1–13 have no
  schema files; their migrations stay but only 14 onward is tested (every installed copy is on 14).
- Remove `.fallbackToDestructiveMigration()`.
- **Pre-migration copy:** before Room opens the database, read `PRAGMA user_version` read-only. If it is older than
  the current version, copy the file (plus `-wal` and `-shm`) to `files/db_backups/pre-v{N}.sqlite`. Keep the last 3.
- **Recovery instead of a crash or wipe:** if opening the database throws (a missing or failed migration), show a
  recovery screen instead of crashing. It offers *Export my data* (the raw database files and attachments as a zip
  through the share sheet), *Try again* and *Contact/report*. The data is never deleted automatically.
- `MigrationTestHelper` tests for every migration from 14 onward, plus a test that seeds a v14 database with
  realistic data (notes with every block type, tags, links, attachments, meetings, scripture refs, devotional
  notes) and checks that every row survives migrating to the current version.

### 4.2 Schema v15
| Change | Detail |
|---|---|
| `notes.isDraft INTEGER NOT NULL DEFAULT 0` | Filled from `metadataJson LIKE '%"draft":"1"%'`. Code stops reading the draft flag from metadata (`NoteContent.DRAFT`) |
| `notes.deletedAt INTEGER` / `notebooks.deletedAt INTEGER` | Trash (§4.4) |
| Index `(deletedAt, archivedAt, isDraft, pinned, updatedAt)`, index `notebookId`, index `workflow` | Every list query moves onto these columns |
| `note_versions` table | §4.5 |

Every DAO query filters `deletedAt IS NULL AND isDraft = 0` instead of the `LIKE`.

### 4.3 Backup and restore
**Format:** `MeetingMind-YYYY-MM-DD-HHmm.mmbackup`, a zip containing:
- `manifest.json`: format version, app version, schema version, created at, device, counts per table, SHA-256 of
  every entry, and which optional parts are included.
- `db/meetmind_database`: a consistent copy made after `PRAGMA wal_checkpoint(TRUNCATE)`, taken inside a Room
  transaction so nothing writes during the copy.
- `db/faith_extras.db` (prayer list, reading plans).
- `bible/highlights.json`: highlights read out of `bible_offline.db`. Downloaded Bibles are not included; they can be
  downloaded again, and the manifest lists which were installed so restore can offer to fetch them.
- `prefs/*.json`: DataStore preferences (settings, devotional profile, identity, home, reminders). **The Gemini API key
  is left out by default**, behind a switch "Include my API key" (question M0-Q3).
- `files/notes/`, `files/profile/`, `files/backgrounds/mine/`, `files/devotional_images/`.
- Optional parts, off by default with their size shown: `files/meetings/` (recordings), `files/devotional_audio/`.
  Models are never included.

**Backup UI:** Settings → *Data & backup*.
- *Back up now* saves to a location you choose (SAF: Drive, Downloads, a USB drive) or opens the share sheet.
- **Automatic backups:** off / daily / weekly, to a chosen SAF folder, keeping the last N (default 7). Runs as a
  WorkManager job while charging or idle; a failed run shows on the settings row. The last successful backup's time
  and size are shown on the row.

**Restore:** Settings → *Data & backup* → *Restore*, or from onboarding on a fresh install.
- Pick the file. It is validated (manifest, checksums, schema version not newer than the app), and a summary is
  shown ("1,284 notes, 312 recordings (not included), 18 notebooks, backed up 28 Sep 2026 on Pixel 8").
- **Replace everything**: first a safety backup of the current data goes to `files/db_backups/`, then the databases
  are closed, the files replaced, and the app restarts. Room migrates a backup from an older schema on open.
- Merge restore is out of scope for M0 (a later milestone).

**Android's own backup rules:** include `meetmind_database` and `faith_extras.db` in **device transfer**
(phone-to-phone, not size-capped), so a new phone set up by cable or Wi-Fi keeps your notes. Cloud Auto Backup stays
off for the database: it has a 25 MB cap that fails silently, and automatic backups to Drive cover it (question M0-Q2).
Recordings stay excluded from both.

### 4.4 Markdown export of everything
Settings → *Data & backup* → *Export as Markdown*: a zip with one folder per notebook, `Note title.md` per note with
YAML front matter (`title`, `created`, `updated`, `notebook`, `tags`, `workflow`, `pinned`, and `event_date` when
set), attachments in `assets/` next to their note, scripture blocks as block quotes with the reference, transcript
excerpts with `[mm:ss]`, and links between notes as `[[Note title]]`. It opens in Obsidian as a vault.
It reuses the existing exporters (`RichText.toMarkdown`, `NoteExporter`).

### 4.5 Trash
- *Delete* moves a note or notebook to **Trash** (`deletedAt = now`). Its blocks, attachments and linked recordings
  stay as they are, so restoring gives back exactly what was there.
- The Trash view is reached from the Notes library overflow menu and from Settings. It supports *Restore*, *Delete
  forever* and *Empty trash*, and each row shows "deleted 3 days ago · 27 days left".
- A daily job purges anything deleted more than 30 days ago. Purging does what `deleteNote` does today: detaches
  recordings and deletes attachment files.
- A snackbar "Moved to Trash · Undo" appears after every delete.
- **Exception:** an empty draft that is discarded automatically (`NoteEditorViewModel` L723/L736) is still deleted
  permanently; it never held anything.
- Deleting a notebook moves it **and its notes** to Trash together; restoring the notebook restores them.

### 4.6 Version history
**Table `note_versions`:** `id`, `noteId` (FK, cascades on permanent delete), `createdAt`, `reason` (`EDIT_SESSION`,
`BEFORE_AI`, `BEFORE_RESTORE`, `BEFORE_PASTE`, `BEFORE_IMPORT`, `MANUAL`), `label` (e.g. the AI skill's name),
`title`, `blocks` (BLOB: gzipped JSON of the full block list including payloads), `byteSize`.

**When a snapshot is taken:**
- Before any AI tool applies a change (`NoteAiApply` callers, and the M2 assistant change sets).
- Before restoring a version (so a restore can itself be undone).
- Before large pastes and imports (the hook exists for M1).
- During editing: when the editor closes, or after 10 minutes of continuous editing, if the content changed since
  the last snapshot.
- *Save version* by hand from the note menu.

**Retention:** keep everything from the last 24 hours; then at most one per hour for 7 days; one per day for 30 days;
one per week after that. Hard cap of 100 per note and a 50 MB total budget (oldest pruned first). Manual and BEFORE_*
snapshots are kept for 30 days whatever the thinning rule says.

**UI:** note menu → *Version history*: a list grouped by day, showing time, reason or label and a word-count
change. Tapping one opens a read-only preview with a block diff against the current version (added/removed/changed,
using `java-diff-utils`, which is already a dependency). Actions: *Restore this version*, and *Copy text*.

**Done when:** an AI summary applied to a note can be undone from Version history after the app restarts.

### 4.7 Acceptance for M0.1
1. Migration tests pass from 14 onward, and the seeded-v14 test shows every row intact on the current version.
2. Removing a migration from the builder makes opening fail into the **recovery screen**, not a crash or a wipe
   (tested in Robolectric).
3. Backup, then clearing app data, then restore brings back every note, block, attachment, tag, link, scripture ref,
   prayer, reading plan, highlight and setting. Checked by a JVM round-trip test on a seeded database, and by hand
   on the owner's phone.
4. A backup whose checksums don't match, or whose schema is newer than the app, is refused with a clear message.
5. Delete → Trash → Restore gives back an identical note. A purge after 30 days removes the files.
6. Every snapshot trigger writes a version, thinning follows the rules (unit-tested with a fake clock), and restoring
   a version can itself be undone.
7. The Markdown export opens as an Obsidian vault with links and images working.

## 5. M0.2: theme foundation, dark by default

- **Tokens** (`ui/theme/Tokens.kt`): an `MMColors` data class provided by `LocalMMColors`, covering background,
  surface, surfaceRaised, surfaceSunken, textPrimary/Secondary/Tertiary/Disabled, hairline, divider, accent,
  accentMuted, onAccent, success, warning, danger, selection, the 5 note highlight colours, the 6 speaker colours,
  the faith warm set (gold, parchment, ink-warm), recording, and scrim.
  There are two complete sets, **Graphite (dark, default)** and **Paper (light)**, each also mapped onto Material 3's
  `ColorScheme`, so Material components follow automatically.
- **Settings → Appearance:** *Dark* (default) / *Light* / *Match system*. Presets, accents and fonts come in M3 and
  build on these tokens.
- **Migration:** every file in the 27 moves off `Color(0x…)`, `Ink*`, `CleanMac*`, `Light*`, `Dark*` and the brush
  constants, which are deleted from `Color.kt` when the migration is complete. Gradients are removed from everyday
  UI. The ones that stay (story covers, share studio) are defined as tokens.
- **Guard test:** a unit test scans `app/src/main/java` and fails on `Color(0x` or the removed constant names outside
  `ui/theme`. Colours that are content, not UI (a user's chosen notebook colour, calendar event colours, image
  pixels), go through an allow-listed helper.
- **Screenshots:** the existing Roborazzi tests run for both Graphite and Paper, and every main screen (Today,
  Notes, editor, meeting detail, recording, devotional, Bible, settings, onboarding) has a screenshot in both.
- System bars and the splash follow the theme. Widgets get night-mode resources (`values-night`).
- **Acceptance:** the guard test passes with zero allow-list exceptions outside content colours. The owner walks the
  app in dark mode and finds no light-only surface, invisible text or wrong-contrast icon (WCAG AA contrast is checked
  for text tokens by a unit test).

## 6. M0.3: CI

A new workflow, `.github/workflows/ci.yml`, runs on every push and pull request: `testDebugUnitTest`, `lintDebug`,
`assembleDebug`, uploads the Roborazzi diff on failure, and caches Gradle. It needs no secrets: the Google services
plugin already tolerates a missing `google-services.json` (`MissingGoogleServicesStrategy`). The release workflow
stays as it is.

## 7. M0.4: new app identity

- `applicationId` changes to the chosen id (question M0-Q1). The Kotlin `namespace` changes from `com.example` to match,
  as a separate, purely mechanical commit (it touches every file; there is no behaviour change).
- **Owner actions, outside the repo:** register the new package in Firebase and download a new
  `google-services.json`; add the release and debug SHA-1 fingerprints for Google Sign-In; update the CI secret the
  release workflow uses. If the app is on Play, it becomes a new listing (question M0-Q1).
- **First run of the new app:** the onboarding screen gains "Coming from the old MeetingMind? Restore your backup" as
  its first choice.
- **The last old-id build (v32)** gets a banner in Settings → Data & backup: "Moving to the new MeetingMind: make a
  full backup". It makes a backup with recordings included and shares it straight into the new app when that's
  installed.
- The widget, shortcut, notification and FileProvider authorities follow the new `${applicationId}`, as they already
  use placeholders.
- **Acceptance:** on one phone, with v32 (old) and v33 (new) side by side, a full backup from v32 restored into v33
  shows identical counts, and a sample of 20 notes is identical. The owner then uninstalls the old app.

## 8. Test plan summary

| Area | Test |
|---|---|
| Migrations | `MigrationTestHelper` 14→15 onward; seeded-v14 integrity test; missing-migration → recovery screen |
| Pre-migration copy | Robolectric: an older `user_version` produces a copy, and only the last 3 are kept |
| Backup | JVM round trip on a seeded database and files; checksum mismatch rejected; newer schema rejected; optional parts respected; API key excluded by default |
| Trash | Soft delete, restore, purge with a fake clock, notebook cascade, empty drafts still hard-deleted |
| Versions | Each trigger; thinning with a fake clock; restoring a version is undoable; size cap |
| Markdown export | Front matter, attachments, links and scripture golden files |
| Theme | Guard test; token contrast test; Roborazzi in both themes |
| CI | Workflow green on the M0 PR |
| Device | Owner: backup on the phone → restore into the new app, then a dark-mode walk-through |

## 9. Open questions for M0

- **M0-Q1: the new application id.** It should be a reverse domain you control, e.g. `com.<yourdomain>.meetingmind` or
  `app.meetingmind`. Is the app on Google Play today (which makes the rename a new listing), or sideloaded only?
- **M0-Q2: device transfer.** Include notes in Android's phone-to-phone transfer (★ yes), and keep Google cloud Auto
  Backup off in favour of our own scheduled backups to Drive (★ yes)?
- **M0-Q3: API key in backups.** ★ Excluded by default, with an "Include my API key" switch.
- **M0-Q4: dark palette.** ★ *Graphite*: near-black `#111214` background, `#18191C` surfaces, off-white text
  `#ECECEE`, one accent. Is a slightly warm tint or a pure neutral preferred? The first screenshots will be shown
  before every screen is migrated.
- **M0-Q5: order.** Tasks (W10) was confirmed as wanted. It stays in M5 with the professional core. Pull it earlier
  (for example right after M1)?
