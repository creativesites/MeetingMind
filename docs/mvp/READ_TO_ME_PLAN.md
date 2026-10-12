# Read to Me: repository audit and implementation brief

Status: audit and plan only. No application code, schema or docs were changed. This file is the only thing written. If approved, I suggest copying it to `docs/mvp/READ_TO_ME_PLAN.md` (a docs-only change, no app code). That is the one change I'd propose.

Branch audited: `ccr-5dc13c30-a5eqgv` (HEAD `4709602`, clean tree, DB schema version 23).

How to read this document
- **[V]** = verified by reading the checkout. **[P]** = proposal. **[A]** = assumption or not verified.
- The audit was done by three read-only Explore agents plus my own spot-checks. I personally re-checked these: `DevotionalVoice.engines()` gating, `DeviceSpeech` having no voice/pitch/chunk handling, the compiled-in `SYSTEM_GEMINI_API_KEY`, no audio attributes in `PlaybackService`, no `<queries>` in the manifest, and the editor overflow-menu location. The remaining file:line citations come from the agents' reports and should be spot-checked at implementation time.
- I ran no builds or tests. No test is claimed to pass.
- The Gemini API facts came from `ai.google.dev/gemini-api/docs/speech-generation` through a summarising fetch. Re-read the primary page in the Stage 0 spike (see Open questions).

---

## 1. Executive recommendation

**First release: "Read to Me" on any note, with two modes, built on the existing Gemini and on-device engines and the shared player.**

1. **Read Exactly** is deterministic: extract the note's blocks, convert them to speech form, synthesize, play. No AI rewriting. It ships first because it is reliable, private and testable.
2. **Smart Read** adds a validated, per-section spoken rewrite (cloud only when permitted) and a content-aware delivery. Offline, Smart Read is a deterministic "spoken form" pass only, and the UI says so.
3. **Sections are the unit of everything**: script, audio file, cache entry, chapter, resume point, partial regeneration. This gives fast time-to-first-audio, safe long notes, section navigation and cheap re-generation after edits.
4. **Privacy gate first**: one `SpeechRoute` decision (offline stays offline, private and confidential notes never go to cloud, visible disclosure). The current TTS gating does not do this (see §3).
5. **Personalisation ships last and small**: explicit choices first, a visible "what Read to Me learned" screen, and only repeated deliberate choices change defaults.

6. **Bible Study is in v1 (see §16)**: the same engine and player, plus a deterministic study-role layer, locked scripture (never sent to a rewriting model), a translation-licence gate and private-section gates in `SpeechRoute`, and a "pause after question" control.

Why this scope: the hard parts are not the model prompts. They are the gaps in the current audio stack (no queue, no resume, no audio focus, no cache key, no per-note privacy gate) and a script layer that is hard-wired to devotionals. Fixing those is what makes the feature trustworthy. The "intelligence" sits on top.

Deferred beyond v1: word-level highlighting (no timing data exists), multi-speaker rendering, voice cloning or custom voices, sleep timer, "Listen to the Essence", pronunciation dictionary UI, cross-device sync of preferences.

---

## 2. Repository audit (verified)

Paths are relative to `app/src/main/java/com/craftflowtechnologies/meetingmind/` unless noted.

### 2.1 Speech layer
| Component | Responsibility [V] | Public surface / callers | Reuse | Needs change |
|---|---|---|---|---|
| `ai/voice/Speech.kt` (167 lines) | Voice settings, spoken script, chunking | `PreacherStyle` (9 presets, each with a free-text `style`, male/female Gemini voice, tradition), `VoiceSettings(style, gender, speakPrayer, autoVoice, rate)`, `SpeechSegment`, `VoiceSection` (5 fixed devotional sections), `SpeechScript.build/spoken/clean/chunks(2400)/stripTags`, `SpeechChunk`. Callers: `SpeechEngines`, `DevotionalVoice`, `PrayWithMeScreen`, `DevotionalSettingsSheet`, `DevotionalViewModel`, `DevotionalProfile`, `DevotionalWork` | `chunks`, `stripTags`, `spoken(ref)` (scripture reference to speech), `SpeechSegment` shape | `build` and `VoiceSection` are devotional-only. `PreacherStyle.style` embeds accent text (for example a Zambian accent) in the style string, which the Gemini docs advise against (use a regional voice, not style text) |
| `ai/voice/SpeechEngines.kt` (94 lines) | `SpeechEngine.synthesize(segments, settings, onProgress): AiResult<Pcm>`; `GeminiSpeech`; `DeviceSpeech` | Constructed only in `DevotionalVoice.engines()` | The interface idea; Gemini request loop; model fallback `gemini-3.8-flash-tts` then `gemini-3.8-flash-lite-tts` | See bugs below |
| `ai/cloud/GeminiInteractions.kt` (97 lines) | HTTP to `POST {base}/v1beta/interactions`, `x-goog-api-key`; `speechRequest` builds `input[user_input].content[text + annotations[speech_metadata{style}]]`, `response_format{audio, audio/wav, 24000}`, `generation_config.speech_config[{voice}]` | `GeminiSpeech`, `ImageBackgrounds` | Reuse unchanged. It matches the doc's Interactions-API shape | No retry/backoff; `findMedia` takes the last audio item; errors flattened to a string; no typed 429 or quota error |
| `ai/voice/Wav.kt` | `Pcm`, WAV read/write, L16 decode, silence, concat, resample | Used by engines and `DevotionalVoice` | Reuse | Concatenates whole clips in memory |
| `core/devotional/DevotionalVoice.kt` (166 lines) | Orchestration: engine choice, per-section synthesis with start marks, AAC encode (`AacEncoder`), attachment + note metadata, `DevotionalVoiceWorker` (WorkManager, unique, REPLACE, one retry) | `DevotionalViewModel`, `DevotionalWork`, `DevotionalSettingsSheet` | The shape of the flow (worker, progress, play-when-ready) | Hard-wired to `DailyDevotional`, metadata keys `devotionalAudio*`, attachment caption "Listen: title" |
| `ai/cloud/GeminiCredentialStore.kt` | Key in DataStore `meetmind_credentials`; falls back to `BuildConfig.SYSTEM_GEMINI_API_KEY` | | Reuse | Class KDoc and `PrivacyNoCloudPathTest` say no key is compiled in, but `systemKey` (line 79) is a build-time key. Disclosure copy must handle "using the app's key" |

**Existing bugs and constraints [V unless noted]**
1. **Privacy gating is incomplete.** `DevotionalVoice.engines()` (line 53-57) checks only the global `processingProfile == INTERNET` plus `isConfigured()`. It does not consult `WorkPrivacy`, `Note.isPrivate` or notebook `confidential`. Applied to arbitrary notes, this would upload private notes. (Existing AI jobs already do filter: `ai/notes/NoteAiJobs.kt:220`.)
2. `DeviceSpeech` has no engine/voice discovery, no `setVoice`, no pitch, ignores `PreacherStyle` and `DevotionalProfile.language`, uses `Locale.getDefault()`, never calls `getMaxSpeechInputLength()` (limit is 4000 characters), has no chunking, **silently skips failed segments** (content lost with no error), does not override `onStop` (a stopped utterance waits out the 120 s timeout) [spot-checked: no `getVoices/setVoice/setPitch/onStop`].
3. `SpeechScript.chunks` has no hard split for a single sentence longer than `maxChars`.
4. `DevotionalVoice` wraps calls in `runCatching` (it swallows `CancellationException`) and logs nothing.
5. `GeminiSpeech.label` is a shared mutable var and there is an unchecked cast `as AiResult<Pcm>`. The `SpeechEngines` KDoc describes inline pause tags, but the code uses real silence.
6. A mid-run Gemini chunk failure aborts the whole file and hands it to the other engine, which re-renders everything. That gives a mixed-voice risk, wasted cost and no checkpoint.
7. Rate range disagreement: comment 0.8-1.2, clamp 0.7-1.3, UI 0.85/1.0/1.15.
8. No `<queries>` entry in the manifest for the TTS service [spot-checked]. Package visibility for engine enumeration on Android 11+ is **[A]**, to verify on a device.
9. `docs/DATA_SAFETY.md` still says nothing is ever uploaded. It contradicts current Gemini paths (known: `AUDIT.md` / `MVP_PLAN.md` S-9).
10. There are no generic read-aloud classes. `ReadAloud` appears only as `CompanionEvent.ReadAloudStarted/Stopped` (never emitted). Task F-7 in `docs/mvp/MVP_PLAN.md:163` specifies a shared controller. Founder decision (`BIBLE_DEVOTIONAL_V2.md:158`): **on-device voice for Free, Gemini voice for Pro**.

### 2.2 Playback
| Component | Facts [V] | Reuse / change |
|---|---|---|
| `core/audio/PlaybackService.kt` (47 lines) | `MediaSessionService`, one `ExoPlayer`, one `MediaSession`, manifest `foregroundServiceType="mediaPlayback"`. Notification, lock screen and Bluetooth come from Media3 defaults. **No `setAudioAttributes`, `setHandleAudioBecomingNoisy`, `setWakeMode`, `MediaSession.Callback`, `onPlaybackResumption`, artwork or `setSessionActivity`** [spot-checked] | Reuse. Add audio attributes (`CONTENT_TYPE_SPEECH`, focus on), becoming-noisy handling, wake mode, session activity, resumption callback. Affects recording playback too |
| `core/audio/PlaybackController.kt` (349 lines) | Singleton over a `MediaController`. Single-item only (`setMediaItem`, never a queue). API: `play/playAt/playUriAt/playRange/pause/stop/togglePlayPause/seekTo/seekBy/setSpeed/setLoop/setSegments`. Item identity = a `recordingId` string with prefixes `devotional:`, `preview:`, `media:`, `bible:`. 200 ms progress loop. `speed` is in memory only | Reuse everything. **Add** `playQueue(...)` additively. Existing methods stay |
| `core/audio/PlaybackState.kt` | Phases IDLE/LOADING/PLAYING/PAUSED/COMPLETED/ERROR; no speed, section or queue info | Add optional `sections`, `sectionIndex`, `readMode`, `provider` |
| `core/ui/MiniPlayerBar.kt` + `NowPlayingSheet` | The only shared player UI: pill, 10 s skip, seek slider, speed chips 0.8/1/1.25/1.5, Open, Stop. Artwork picked from the id prefix. Hard-coded colours and font sizes (against `AGENT_RULES.md`) | Extend (chapters, badge, generation progress). Add a `read:` prefix case |
| `MainActivity.kt` ~1235-1257 | Mounts the mini-player once; "Open" routes `devotional:` to devotional, `preview` to nothing, anything else to `meetingDetailRoute(id)`. A new prefix would otherwise open a meeting route with a note id | **Hot file.** Must be named in the stage that touches it |

Call sites of `PlaybackController` [V]: `MainActivity` (connect, state, mini-player), `MeetingDetailScreen` (play/playAt, setSegments), `NoteEditorScreen:592` (recording cards), `FaithScreens:879-891` (`media:`), `StudyWorkspace:188`, `BibleViewModel:305-325` (remote https via `playUriAt`), `DevotionalViewModel` (listen, playRange), `DevotionalSettingsSheet:407` (preview). Outlier: `NoteBlocks.kt:437` `AudioBlock` creates its own `MediaPlayer`, bypassing the shared player.

Other playback findings [V]:
- No position persistence anywhere (grep for resume/lastPosition: nothing). Process death empties the session. No `onPlaybackResumption`.
- `isRecordingReadyToResume` compares ids only. A regenerated reading under the same id would keep playing the old audio.
- `stopAtMs` is not cleared by `stop()`. `setLoop` is dropped by `playInternal`. A seek before the duration is known clamps to 0.
- `MiniPlayerBar.artworkFor` has no `bible:` case, so Bible audio gets the recording art and a meeting route. This is a pre-existing bug I did not confirm in a running app.
- docs claim "starting a recording stops playback", but `MeetingRecordingService` does not call it. Audio focus for recording exists; playback has none.
- Media3 = 1.11.0, only `media3-session` and `media3-exoplayer`. No `media3-test-utils`.

### 2.3 Storage
- Generated devotional audio: `filesDir/devotional_audio/<noteId>.m4a` (AAC 64 kbps mono, WAV fallback), stored as an `Attachment` row (`AttachmentEntity`, FK CASCADE to notes) plus note metadata `devotionalAudio`, `devotionalAudioVoice`, `devotionalAudioMarks` (`SECTION:ms,...`).
- Cache key = note id only. No content hash, no voice or settings in the key, no size cap, no LRU, no cleanup (other than model dirs and meeting dirs). Voice previews accumulate in `cacheDir/voice_preview_<voice>_<rate>.wav`.
- No Room table for playback progress. Included in backup when `includeDevotionalAudio` is set (`BackupManager.kt:64,206`).

### 2.4 Notes and workflows
- Room DB version 23, `exportSchema = true`, inline migrations `MIGRATION_1_2..22_23`, schemas for 14-23 exported, `DatabaseGuard` copies the file before migrating. `AGENT_RULES.md`: no schema change without migration, bumped version, exported JSON and a migration test; `MeetMindDatabase.kt` is a hot file.
- `NoteBlockType`: PARAGRAPH, HEADING_1-3, BULLET, NUMBERED, CHECKLIST, QUOTE, DIVIDER, SCRIPTURE, IMAGE, VIDEO, AUDIO, RECORDING, TRANSCRIPT_EXCERPT, NOTE_LINK, CODE, TABLE, EMBED, MARKDOWN. `isText` covers paragraph, headings, bullet, numbered, checklist, quote. There is **no** dedicated speaker block; speaker is the `PAYLOAD_SPEAKER` payload on excerpts.
- Block fields: `text`, `spans` (BOLD/ITALIC/UNDERLINE/STRIKE/HIGHLIGHT/CODE/LINK), `payloadJson`, `source` (USER, AI, TRANSCRIPT, IMPORTED, SCRIPTURE), `sourceSegmentIdsJson`, `sectionKey`, `isUserEdited`, `indent`, `checked`, `updatedAt`. `MARKDOWN` blocks hold a whole markdown source. Scripture refs store the reference only; verse text comes from the Bible library.
- **No content hash or revision on notes or blocks.** Only `updatedAt` / `isUserEdited`. `note_versions` stores gzipped snapshots (history, not a cache key).
- `NoteEntity.workflow` = `RecordingType` enum (MEETING..GENERAL, with SERMON, BIBLE_STUDY, DEVOTIONAL, PRAYER, PRAYER_REQUEST, TESTIMONY, GRATITUDE, REFLECTION, LECTURE, RESEARCH, JOURNAL, ...), plus `Workflows.space()/usesSerif/isPrivateByDefault`. Tradition is **not** a note property; it lives in `DevotionalProfile.tradition` in the `devotional_profile` JSON blob.
- Privacy bits already present: `Note.isPrivate`, `Notebook.confidential`, `WorkPrivacy` (forces OFFLINE), faith lock.
- Recordings attach to notes via `MeetingEntity.noteId` (not an FK) and a `RECORDING` block (`block_rec_<meetingId>`). Transcripts are `transcript_segments` rows by `meetingId`; excerpt blocks carry `sourceSegmentIds`.

### 2.5 Screens
- **Note editor** `feature/notes/editor/NoteEditorScreen.kt` (1130 lines): top-bar `MoreVert` menu at ~line 328. Items in order: Cover image, Assistant, AI tools (lines 334-335), Share as a picture, Tidy, Copy as…, Export & share, Move, Tags, Make private, Version history, Archive, Delete [spot-checked]. Selection model is **single block** (`Selection(blockId,start,end)`, `NoteEditorViewModel` line 98). No multi-block selection. Headings fold their section.
- Other listening UI: devotional `ListenPill` + `SectionChips` (`DevotionalScreen`), Bible `ListenRow` (pre-recorded narration, not TTS), faith attachment cards.
- No voice or read-aloud entry in Settings. Settings: one global `processing_profile` (`OFFLINE` default / `INTERNET`; parse falls back to OFFLINE). Voice settings live only inside the `devotional_profile` JSON (additive, per-field defaults).
- Design system: `core/ui/mm` (`MMButtons`, `MMChoice` `SegmentedControl/FilterChipRow`, `MMFeedback` `StatusLine/EmptyState`, `MMSurfaces`, `ListRow`) and `MM.type/space/radius/motion/colors` tokens. Much existing screen code uses literals; new code must not.
- No DI framework (manual construction, `MeetMindDatabase.setInstanceForTest` as the seam).

### 2.6 Tests that exist [V]
`ai/voice/VoiceTest` (WAV, script, chunks, request shape, settings JSON), `core/audio/PlaybackControllerLogicTest` (13 pure-function tests), `PlaybackStateTest`, `RecordingCapacityTest`, `TranscriptPlaybackSyncTest`, `PrivacyNoCloudPathTest`, `NoteRepositoryTest`, `NoteDraftTest`, `WorkflowsTest`, `BlockEditingTest`, migration tests 14->15 .. 21->22 plus `NotesMigrationTest`/`NoteAiMigrationTest` (I did not find a 22->23 file), screenshot tests (Roborazzi) and guard tests (`ColorLiteralGuardTest`, `DesignSystemGuardTest`). `androidTest` has only `ExampleInstrumentedTest`.

**Not tested at all:** `GeminiSpeech`, `DeviceSpeech`, `GeminiInteractions.media` (HTTP/error/no-key), `DevotionalVoice` (engines gating, marks, record), `DevotionalVoiceWorker`, `Wav.fromL16`, `chunks` over-long sentences, `PlaybackService`, `PlaybackController` against a controller, `MiniPlayerBar`, `AacEncoder`, any TTS-vs-`WorkPrivacy` rule.

**Devotional-specific assumptions that block arbitrary notes**
1. `SpeechScript.build` takes `Devotional`, `DevotionalOrigin`, `ScriptureReference` and adds a greeting, "Go in peace" and a prayer ending.
2. `SpeechSegment.Kind` maps 1:1 to the 5-value `VoiceSection` enum; chapters are fixed, not data.
3. `VoiceSettings` is built from `PreacherStyle` (tradition flavoured presets) with male/female voice pairs, not a general voice id.
4. Output identity is the `devotionalAudio*` note metadata + `Listen:` attachment caption + `devotional:` playback id.
5. Verse text is fetched by `ScriptureService` from a `Devotional`'s reference, not from note blocks.
6. Cloud permission assumes the text is AI-generated devotional content, not user-authored private text.
7. The settings live in `DevotionalProfile`, edited in the devotional settings sheet.

---

## 3. Current-state architecture (how it works today)

```
DevotionalViewModel.listen()
   -> file missing? enqueue DevotionalVoiceWorker (unique, REPLACE)
        -> DevotionalVoice.record(daily)
             engines() = [GeminiSpeech if INTERNET && key] + DeviceSpeech
             SpeechScript.build(devotional) -> segments
             synthesizeSections(): per VoiceSection, per chunk(<=2400 chars)
                 Gemini unary WAV 24 kHz  /  TextToSpeech.synthesizeToFile
                 Wav.concat all PCM in memory -> AacEncoder -> filesDir/devotional_audio/<noteId>.m4a
             Attachment row + note metadata (audio id, voice label, section marks)
   -> PlaybackController.play/playRange("devotional:<noteId>", file)  [single MediaItem]
   -> PlaybackService (ExoPlayer, MediaSession) -> MiniPlayerBar / NowPlayingSheet
```
No resume position, no cache key, no queue, no audio focus.

## 4. Gap analysis

| Need | Exists | Missing |
|---|---|---|
| Note to speech source | Block model, `plainText`, markdown utilities | Extractor with per-type rules and block mapping |
| Exact reading | Gemini + device engines; `chunks`; `spoken(ref)` | Generic request type, hardened local engine, safe fallback |
| Smart Read | `NoteAiEngine`/`NoteAiJobs`, DeepSeek, Gemini text, local LLM (MediaPipe) [A, not audited in detail] | Script generator + validator + locked spans + mapping |
| Voice/delivery | `PreacherStyle` presets, `speech_metadata.style` | Separate voice identity / preset / content cue; capability model; precedence |
| Privacy gate | Global profile, `isPrivate`, `confidential`, `WorkPrivacy` | One route resolver for TTS and script generation; disclosure UI |
| Playback | Shared player | Queue, sections, audio focus, resume, session callbacks, metadata |
| Cache | Nothing general | Fingerprints, manifest, LRU, cleanup on note delete |
| Preferences | `devotional_profile` JSON | New versioned profile, learning rules, inspect/reset UI |
| Tests | Pure-function style tests | Everything under "not tested" above |

---

## 5. Proposed end-to-end flow

1. **Entry.** Note editor overflow item "Read to me" (next to "Assistant"/"AI tools", `NoteEditorScreen.kt:334-335`). Also "Read from here" on a heading block (sections already fold on headings). Selected text in one block uses the existing single-block selection. There is no multi-block selection, so "read a section" means heading-bounded.
2. **Read to Me sheet** (`ModalBottomSheet`, `MM` tokens, one primary button). Mode `SegmentedControl` Smart Read | Read Exactly with a one-line meaning each. Voice row showing the resolved voice and a provider badge ("Gemini voice · note text is sent to Google" or "This phone · stays on device"). Delivery chips (Auto for this note type, plus up to 4 presets). Pace. "Hear a sample" (a fixed sample line, **not** note text, so previews never upload note content). Primary: "Listen".
3. **Route decision** (`SpeechRoute`, pure): cloud only if profile is INTERNET, key configured, note not private/confidential, entitlement allows Gemini, and the user has accepted the disclosure for this provider. Offline: local only, no fallback to cloud, ever.
4. **Plan**: extract -> speech sections (deterministic) -> (Smart Read) script per section -> delivery resolved per section.
5. **Generate sections in order.** Start playback as soon as section 1 is ready. Generation continues in a WorkManager job; progress shows "Preparing 3 of 8" in the mini-player/now-playing sheet with Cancel.
6. **Play** through the shared player as a queue. Chapters = sections. Resume point saved per note.
7. **Return to the note**: mini-player "Open" navigates to the note and scrolls to the current block. The note highlights the current section or passage (never words).
8. **Change voice or delivery while listening**: applies to *not-yet-played* sections from the next boundary; the current section finishes. Already cached sections for the old voice stay valid for that voice. The sheet says "Applies from the next section".
9. **Edit during or after**: sections whose fingerprint changed show "Edited since this reading". Regenerate affects only those (Stage 5 polishes this; v1 can regenerate the whole note).
10. **Edge cases**: empty note -> `EmptyState` "Nothing to read yet". Unsupported-only note (images/embeds) -> says what was skipped. Very long note -> reads in section order, bounded concurrent generation, a soft warning above a size threshold with the estimated cloud calls. Mixed content -> per type rules in §6.A. Gemini failure -> retry, then a clear choice (below).

**Fallback behaviour (predictable, no silent mixing):** one provider per reading. On Gemini failure (after bounded retry and model fallback) while cloud is permitted, show "Gemini voice isn't available right now." with **[Use this phone's voice]** and **[Retry]**. Choosing the phone voice regenerates the unplayed sections only and labels the reading's provider as changed. No auto-fallback in offline mode (the route never included Gemini).

---

## 6. Intelligence pipeline [P]

### A. Extraction -> `SpeechSource`
Pure Kotlin from `NoteDocument` (blocks stay untouched). Output: ordered `SpeechUnit(blockIds, kind, text, headingPath, sectionKey, speaker?, locked, language?)`.

| Block | Read | Rule |
|---|---|---|
| Heading 1-3 | yes | Opens a section; spoken with a pause; level sets the pause length |
| Paragraph / markdown | yes | Strip markup; keep text; spans like BOLD map to emphasis cues (Smart Read only) |
| Bullet / numbered | yes | "First, ..., second, ..." or a lead-in; count announced for long lists |
| Checklist | yes | Reads items; state ("done") only if checked |
| Quote | yes, **locked** | Verbatim; attribution kept ("X said:") |
| Scripture | yes, **locked** | Text from the Bible library via `ScriptureService`, reference via `SpeechScript.spoken`. Never rewritten |
| Transcript excerpt | yes, locked | Speaker label, then text. Smart Read may group but never reword |
| Recording block | skipped by default | Announced as "a recording is attached"; transcripts only if they exist as excerpts |
| Table | simplified | Header-aware row reading; large tables summarised by count with the user option to skip |
| Code | skipped | "A code block is here" (configurable) |
| Link / embed / image / video / audio | skipped or label | Link text only; image alt text if present; never fetch remote content |
| Divider | pause | |
| Empty / malformed | skipped | Counted, reported in a "skipped" line |

Canonical note and transcripts are never modified. The spoken script is derived data.

### B. Speech intent
Deterministic first: `Workflow` -> category, heading hierarchy, block kinds, locked spans, `isPrivate`, language (`NoteBlock.language` payload where set), emphasis spans. AI analysis is optional and only in Smart Read when cloud is permitted: per-section emotional register and emphasis from a closed vocabulary (steady, reflective, building, warm, urgent...). Offline: register comes from workflow plus simple rules (for example a heading named "Application" is steady). This avoids sending text to a model when a rule suffices.

### C. Smart Read script
- Input per section: unit texts with locked spans replaced by placeholders (`⟦Q1⟧`). Output must contain each placeholder exactly once, in order.
- Allowed changes: expand lists and headings into spoken language, add short transitions ("Next, ..."), expand abbreviations and numbers, smooth repeated structure. **Not allowed**: new facts, names, numbers, dates, quotes, scripture, commitments, conclusions.
- Validation (deterministic, after the model): placeholders intact; every number, date and capitalised name in the output appears in the source section (allow-list for transition words); length ratio within bounds; no new quotes; no forbidden religious additions. On failure retry once, then fall back to the deterministic spoken form for that section, and mark it "read as written".
- Mapping: `ScriptSegment(sourceBlockIds[], text)`; segments keep block order, so playback maps to blocks (section or passage level, never word level).
- Long notes split at heading boundaries (and at paragraph boundaries inside very long sections) to keep each request small.
- Labels: Smart Read readings show a "Smart Read" badge and a link "See what was changed" (script view); Read Exactly shows "Read as written". Read Exactly never calls the script generator.

### D. Delivery resolution
Five separate inputs: voice identity, delivery preset, content cue, user preference, per-note override. Precedence (deterministic, first match wins per field):
1. Per-note explicit override.
2. Explicit category preference.
3. Explicit global preference.
4. Learned category default (only if it came from the promotion rule in §7).
5. Workflow default (neutral: sermon defaults to a warm, clear delivery; vibrant preacher is **never** a default).
6. App default.

`mode` is outside this chain: an explicit Read Exactly choice is never changed by any preference or learned default.

**Bounded instruction**: a fixed template filled from enums (preset, energy 1-5, pace, per-section cue from a closed list), under a fixed character cap, sent as `speech_metadata.style`. The verbatim script goes in the text. Do not embed stage directions in the text (docs: the transcript is verbatim). No free-form accumulating prompt. Emotional arc: the per-section cue changes along the note (for example "building" toward the end of a sermon), not one tone for all.

Gemini facts used (from the docs page, to re-verify): models `gemini-3.8-flash-tts` and `gemini-3.8-flash-lite-tts` (the repo's current pair), plus `gemini-3.1-flash-tts-preview` and `gemini-2.5-pro-preview-tts`; Interactions API; unary `audio/wav` 24 kHz mono 16-bit; streaming headerless `audio/l16`; 30 prebuilt voices plus a larger voice library via `GET /v1beta/voices`; style via `speech_metadata.style`; text in, audio out; language auto-detected. The page does **not** state input or output length limits or rate limits.

### E. Local TTS
Current behaviour is in §2.1. Plan: a hardened `LocalSpeechEngine` (adapting `DeviceSpeech`): enumerate voices (`getVoices()`), filter by language **and, in offline mode, `!isNetworkConnectionRequired`** [A, API fact to confirm in the TextToSpeech reference], `setVoice`, pitch, chunk to below `getMaxSpeechInputLength()`, report (not skip) failed segments, handle `onStop`, offer an install-voice-data intent when data is missing, add the manifest `<queries>` if device testing needs it. Local can control: voice choice, rate, pitch, pauses (our inserted silence). It cannot reproduce: energy/emotion, accent styles, emphasis, preacher cadence. The capability model (below) lets the UI say so ("This voice reads clearly but can't change its energy").

### F. Playback and lifecycle
- **Unit of audio = one file per section** (AAC m4a, same encoder), cached on disk, played as a Media3 queue. Replaces the "one big in-memory concat" pattern (memory spike, long time to first audio, all-or-nothing failure).
- First section is kept small so time to first audio is a few seconds.
- Stable voice across sections: one provider, one voice id, one delivery profile per reading; the fingerprint includes them.
- Progress: a manifest per note stores `{sectionId, positionMs}`; restore through `playQueue(startIndex, startPosition)`. Process recreation: implement `MediaSession.Callback.onPlaybackResumption` reading the manifest.
- Cancellation: cancel the worker; completed section files are kept; partial files deleted.
- Concurrency: one active reading generation (unique work per note; a new request for another note is queued; same-note requests join the running job). Bounded parallelism of 1-2 sections.
- Provider errors: typed errors (auth, quota/429, timeout, bad response, network) with bounded exponential backoff and the model fallback already in `GeminiSpeech`.
- Cleanup: LRU cap on the read-to-me directory (proposal: 200 MB), delete on note delete, "Clear Read to Me audio" in Storage settings, excluded from backup by default.
- Do not invent timestamps. Highlight at section/passage level only. Segments are synthesized separately so segment boundaries are exact.

---

## 7. On-device preference model [P]

**Storage:** a new Preferences DataStore `meetmind_read_to_me` holding one versioned JSON blob (same additive-per-field pattern as `DevotionalProfile.fromJson`). No Room schema change. Not inside `devotional_profile`. Enums and numbers only, never note text, titles, ids or transcripts.

```
ReadToMeProfile {
  version, adaptive: Boolean (default true; off = never learn),
  defaultMode: SMART | EXACT,
  global:   { voiceByProvider{gemini,local}, preset, energy 1-5, pace, preferLocal: Boolean },
  category: Map<Category, { voiceByProvider?, preset?, energy?, pace?, mode? }>,   // categories grouped from Workflow
  evidence: Map<Category, counters (ints)>        // aggregate counts only
}
```
Categories (grouped from the `RecordingType` enum): sermon/teaching, prayer/devotional, reflection/journal, meeting/work, lecture/study, other.

**Signals**
- High confidence (write immediately, source = explicit): choosing a preset or voice, changing energy/pace in the sheet ("remember for this note type" toggle), direct feedback buttons ("More energy", "Calmer", "Slower").
- Promotion: the same deliberate per-note override chosen 3 times for one category with no contrary choice promotes to that category's default, with a visible "Reflections will now start with Warm companion. Undo."
- Low confidence (aggregated counters only; **never change preferences by themselves**): skips, replays, abandonment, completions, playback speed. At most they drive a one-time *suggestion* ("You often listen at 1.25x. Make that your default?"). Abandonment alone is never a negative signal.
- Precedence with per-note overrides: overrides apply to that reading only and feed the evidence counter, never directly the profile.

**User control:** a "Read to Me" settings page: current defaults and where each came from (chosen or learned), switch "Learn from my choices", "Reset everything" and per-category reset. Survives upgrades through additive JSON parsing and a `version` field. Works fully offline. Backup: excluded from cloud Auto Backup; whether the local `BackupManager` export includes it is an open question.

Faith rules: "Vibrant preacher" is offered only when the Faith space is on, and applies only when explicitly chosen or promoted by the rule above. Tradition from `DevotionalProfile` can *suggest* a preset, never apply one. No inserting or altering scripture or prayers.

---

## 8. Caching, provenance and correctness [P]

Two caches:
- **Script cache** (Smart Read only): `scriptKey = H(schemaVer, mode, scriptPromptVer, language, normalized section source text + block kinds + locked spans, pronunciationVer)`. Voice or delivery changes reuse the script and save cloud text calls.
- **Audio cache**: `audioKey = H(scriptHash, provider, model id, voiceId, deliveryProfile hash (preset, energy, pace, cue), sampleRate/format ver)`. Playback speed is applied by the player and is **not** in the key.

Because notes have no revision field, the content hash is computed at extraction from block text, kind and payload (not `updatedAt`, which changes on folds and moves). No schema change.

**Persistence:** a sidecar manifest per note at `filesDir/read_to_me/<noteId>/manifest.json` (sections, fingerprints, durations, block ids, provider/voice labels, progress). Section audio sits beside it. Rationale: avoids a Room migration on a hot file for the first release, and the generated audio stays out of the note's `attachments` (attachments show in the note and are user content; this is derived data). Cost: a hook in `NoteRepository` note deletion for cleanup, and a loose coupling to notes. A Room table can come later if querying across notes is needed.

**Freshness:** before showing "Ready" or playing a cached reading, recompute section hashes. A mismatch shows "Edited since this reading", never plays as current. Provenance chain: audio file -> script segment -> `sourceBlockIds`. Each section lists its blocks, so the player can scroll the note.

Partial regeneration falls out of per-section keys (heading boundary sections).

---

## 9. Architecture proposal

| Component | Responsibility | Reuse / new | In -> out | Test boundary |
|---|---|---|---|---|
| `NoteSpeechExtractor` | Blocks -> `SpeechUnit`s with locks and mapping | New (pure Kotlin) | `NoteDocument` -> `SpeechSource` | JVM unit; golden fixtures per block type |
| `SpeechRoute` | Offline / privacy / entitlement / key decision, disclosure state | New (pure), reads `ProcessingProfile`, `Note.isPrivate`, notebook `confidential`, `WorkPrivacy`, `GeminiCredentialStore` | context -> `Route(provider, reason, needsDisclosure)` | JVM table tests; extends the `PrivacyNoCloudPathTest` style |
| `SmartScriptBuilder` + `ScriptValidator` | Per-section script, placeholders, checks, fallback | New; cloud text via existing `GeminiInteractions`/DeepSeek (provider choice open) | `SpeechSource` -> `Script` | Validator is JVM unit with adversarial fixtures; model calls behind a fake |
| `DeliveryResolver` | Precedence chain -> bounded `DeliverySpec` per section | New (pure) | prefs + overrides + note category + cue -> `DeliverySpec` | JVM; full precedence matrix |
| `SpeechEngine` v2 | Provider-neutral request: sections, voice id, `DeliverySpec`, language; `capabilities()` | Extend the existing interface; keep the devotional path via an adapter | `SpeechRequest` -> `Pcm`/file + typed error | Fake engine; HTTP tests with `mockwebserver` for Gemini |
| `GeminiSpeech` | Existing, plus typed errors, retry/backoff, no shared mutable label | Adapt | | MockWebServer tests (none exist today) |
| `LocalSpeechEngine` | Hardened `DeviceSpeech` (voices, chunking, error reporting, offline-only voices) | Adapt | | Robolectric `ShadowTextToSpeech`; device matrix |
| `ReadingCache` | Manifest, fingerprints, LRU, cleanup | New | | JVM with temp dirs |
| `ReadingGenerator` (WorkManager worker) | Sequential section generation, progress, cancellation | Adapt the `DevotionalVoiceWorker` pattern | | `work-testing` |
| `PlaybackController.playQueue` + `PlaybackState` sections | Queue playback, section index, restore | Extend additively | | Pure-function tests in the existing style; add `media3-test-utils` if a fake player is wanted |
| `PlaybackService` hardening | Audio attributes, noisy, wake mode, session activity, resumption | Modify | | Device checks |
| `ReadToMeProfileStore` + `LearningRules` | DataStore blob, promotion/suggestion rules | New | | JVM; rules table tests |
| Note UI (`ReadToMeSheet`, mini-player/now-playing extensions, note highlight) | | New + extend `MiniPlayerBar` | | Roborazzi screenshots, `MM` token guards |

Persistence summary: persisted = manifest + section audio, preference profile. Derived or in memory = `SpeechSource`, delivery spec, route.

---

## 10. UI and interaction plan
- Entry: overflow item "Read to me" plus "Read from here" on headings. Rule check ("Why is this here?"): the entry appears only when the note has readable text.
- Sheet as in §5. Copy uses `StatusLine` for failures with a Retry. Cloud disclosure is a persistent line in the sheet, not a one-time dialog, and names the processor and what is sent.
- Player: extend `NowPlayingSheet` with a section list, previous/next section, badge (Smart Read / Read as written), provider line, and "Open note at this passage". Speed chips extended for speech (for example 0.75-2.0). Reuse the mini-player; no competing player.
- Reduced motion / Companion: optionally emit `ReadAloudStarted/Stopped` (event exists, unused). Not required for v1.
- Errors: empty note, voice data missing (install intent), Gemini unavailable (choice), quota, offline with only network voices ("No on-device voice is installed for this language"), generation cancelled, cached reading out of date.

---

## 11. Staged roadmap

Reordering rationale: the audit shows playback queue, audio focus, resume and the privacy route are prerequisites for *any* reliable long-note reading, so they move into Stage 1. The preference **resolver** (with empty prefs) also lands in Stage 1 so Stage 4 only adds storage and learning. A short spike precedes coding.

Verification commands (discoverable): `./gradlew --no-daemon -q compileDebugKotlin testDebugUnitTest` (from `docs/mvp/AGENT_RULES.md`), `./gradlew testDebugUnitTest`, `./gradlew assembleDebug` (CI), screenshots: `-Proborazzi.test.record=true` to re-record, plus normal `testDebugUnitTest` to verify. Each stage adds tests; none are claimed passing yet.

### Stage 0: contract and spike (no app code)
- Deliverables: this brief approved; copy to `docs/mvp/READ_TO_ME_PLAN.md`; a measured spike: Gemini TTS max input size, latency per 600/1500/2400 characters, quota/429 behaviour, consistency of one voice across chunks (use a scratch script, outside the app); local TTS matrix on 2-3 devices (voices, offline voices, 4000-character limit, `<queries>`).
- Exit: a numbers table (chunk size, time to first audio, cost per 1k characters) and a go/no-go on per-section synthesis.
- Rollback: none needed.

### Stage 1: reliable general note reading (Read Exactly)
- Files: new `ai/voice/read/*` (extractor, route, resolver, cache, generator), `SpeechEngines.kt` (Gemini typed errors/backoff; local engine hardening), `GeminiInteractions.kt` (typed errors), `core/audio/PlaybackController.kt` + `PlaybackState.kt` (+ `playQueue`), `PlaybackService.kt` (audio attributes, noisy, wake mode, resumption), `MiniPlayerBar.kt` (read prefix, sections), `NoteEditorScreen.kt` (menu item, sheet), `MainActivity.kt` (open-note routing; **hot file, must be named**), `NoteRepository` delete hook.
- Prereqs: Stage 0. No DB migration.
- Tests: extractor fixtures per block type; `SpeechRoute` matrix (offline never cloud, private never cloud, no key); Gemini MockWebServer (success/429/5xx/timeout/no key); local engine chunking and failure reporting; cache fingerprint tests; queue/controller pure-function tests; progress save/restore.
- Acceptance: any note reads aloud via phone voice or Gemini where permitted; explicit offline never touches the network; no silent segment loss; section navigation, background play, lock-screen controls and Bluetooth work on a device; edit marks the reading stale.
- Risks: audio-focus change affects recording playback; queue API in a singleton.
- Rollback: feature flag around the editor menu item; additive APIs leave other callers untouched.
- Definition of done: tests green via the commands above, manual device checklist signed, `DevotionalVoice` still behaves as before.

### Stage 2: Smart Read intelligence
- Files: `SmartScriptBuilder`, `ScriptValidator`, script cache, delivery cues, sheet mode control, "what changed" view.
- Prereqs: Stage 1, decision on the text provider (Open questions).
- Tests: validator adversarial fixtures (injected number, name, quote, scripture, dropped placeholder, runaway length); Read Exactly never calls the generator (fake asserts zero calls); deterministic ordering; mapping correctness; offline Smart Read = deterministic path.
- Acceptance: facts/quotes/scripture are preserved on the sample set; unsupported output falls back per section.
- Rollback: Smart Read hidden behind a flag; Read Exactly unaffected.

### Stage 3: listening UX and lifecycle
- Files: now-playing sections UI, generation progress/cancel, note highlight and scroll, error states, speed, process-recreation tests, storage cap and cleanup UI.
- Acceptance: all states in §10 reachable and readable; resume works after process kill (device); the long-note scenario finishes without memory spikes.
- Tests: Roborazzi screenshots, lifecycle tests where possible, manual device matrix.

### Stage 4: on-device personalisation
- Files: `ReadToMeProfileStore`, `LearningRules`, settings page, feedback controls.
- Tests: precedence matrix; category isolation; adaptive off; reset; abandonment makes no negative update; promotion rule; profile contains no note text (schema-level test that the JSON only has enum/int fields).
- Rollback: delete the store; resolver falls back to the workflow default.

### Stage 5: quality and enhancements (evaluate, then decide)
Selective regeneration UI, pronunciation lexicon (scripture names, acronyms), richer table/code handling, "Listen to the Essence". **Recommend deferring** past v1: Essence mode, lexicon UI, streaming playback, multi-speaker transcript voices, custom voices, sleep timer.

---

## 12. Test matrix (planned, none run)
- Correctness: exact wording/ordering fixtures; Smart Read fact/quote/attribution/scripture checks; no-new-content validator; deterministic section order; block mapping.
- Provider/privacy: Gemini only when configured+permitted; offline makes zero network calls (fake client asserts); private/confidential never cloud; fallback choice behaviour; failure leaves note and state intact; disclosure shown.
- Personalisation: explicit beats inferred; category isolation; off and reset; abandonment alone changes nothing; no note content in the profile.
- Lifecycle: long-note chunk boundaries; cancel/retry; edit invalidates exactly the changed sections; background/interruption/process death; storage cleanup; unavailable voice/language.
- Experience/performance: time to first audio; total latency; memory (no whole-note PCM); retries on network loss; voice consistency across chunks.
- **Human listening set** (repeatable, scored 1-5 on naturalness, fit, fidelity): one expository sermon, one vibrant Pentecostal sermon (explicitly selected), one personal reflection, one meeting-notes note, one lecture/study note with a table and list, one long transcript (60+ min). Run each in Smart Read and Exact, Gemini and phone voice. Existing `testdata/sermons/*.json` can seed the sermon fixtures.

## 13. Risk register
| Risk | Impact | Mitigation |
|---|---|---|
| Private note text uploaded | Privacy breach | `SpeechRoute` hard block; tests; disclosure |
| Offline mode reaching cloud | Trust | Route excludes Gemini offline; zero-network test |
| Smart Read invents content | Fidelity/faith | Locked spans, validator, per-section fallback, visible labelling |
| Gemini limits/cost/quota unknown | Cost, latency | Stage 0 measurements, per-section caching, backoff, warning on very long notes |
| Mixed voices after fallback | Quality | One provider per reading; user-chosen switch regenerates unplayed sections |
| Local voices needing network in "offline" | Hidden egress | Filter `isNetworkConnectionRequired` [A] |
| Audio-focus change alters recording playback | Regression | Device test; scoped attributes |
| Hot-file edits (`MainActivity`, DB) | Merge conflicts | Name them in stages; avoid DB change in v1 |
| Storage growth | Disk | LRU cap, cleanup on delete, exclude from backup |
| Compiled-in system key vs "no key in APK" claim | Disclosure accuracy | Disclosure uses `usingSystemKey()`; fix docs |
| Stale `DATA_SAFETY.md` | Trust | Update with the feature |
| Preference learning surprises users | Trust | Explicit-first, promotion with Undo, visible page |

## 14. Open questions (genuine, with a proposed default so work is not blocked)
1. **Entitlement:** founder decision says Gemini voice is Pro. I did not find or audit an entitlement mechanism. Where should the check live? Default: a single `canUseGeminiVoice()` in `SpeechRoute`, stubbed true until billing exists.
2. **Smart Read text provider:** Gemini (one processor) vs DeepSeek vs local LLM. Default: Gemini text only, local deterministic path offline; a separate disclosure for any other vendor.
3. **Private/confidential notes:** confirm they never use cloud speech or cloud script generation (default yes), even in INTERNET mode.
4. **Retention and backup:** `filesDir` with an LRU cap and excluded from backup (default), versus cache that Android may purge. And should the preference profile ride in the local `prefs.json` backup (default yes, local only).
5. **Tradition source:** use `DevotionalProfile.tradition` for suggestions only (default), since it is not a note property.

## 15. Recommended next action
Approve this brief (including §16, Bible Study), then run **Stage 0** (docs copy + the Gemini and local-TTS measurement spike + the translation-licence decision in §16.10). Then start **Stage 1** with the smallest coherent slice: `NoteSpeechExtractor` (with the study-role classifier and scripture resolver) + `SpeechRoute` (with the licence and private-section gates) + hardened local engine + section cache + Read Exactly through a `playQueue` addition, behind a flag. Stages 2-4 follow in order.

---

## 16. Addendum: Bible Study integration (first-release scope)

Bible Study is not a separate Faith implementation. It is the same extractor, route, resolver, engines, cache, queue and player as §5-§9, with a **role layer** on top and three extra gates (translation licence, private sections, faith lock). Everything below was audited by reading the checkout; same legend as before ([V] verified, [P] proposal, [A] assumption). I ran no builds or tests.

### 16.1 What a "Bible Study" is today [V]
- **No dedicated block types** exist for observation, interpretation, application, commentary, cross-reference, question, reflection prompt or prayer (`core/model/Notes.kt:72-86`). Meaning is carried only by `sectionKey` + heading text + `BlockSource` (`USER/AI/TRANSCRIPT/IMPORTED/SCRIPTURE`) + `isUserEdited`.
- **Study notes come from five paths, each with its own key vocabulary:**
  1. Workflow template for `BIBLE_STUDY` (`Workflows.kt:89-95`): `passage`, `observations`, `interpretation`, `application`, `questions`.
  2. `NoteRepository.startStudy` (`:551-566`) with `StudyTemplate` (SOAP, Inductive, Book overview, Character, Word, Topical, Sermon prep, Small group): keys `study_<template>_<i>`; the role is only in the heading text.
  3. Guided session journal (`core/biblestudy/StudyJournal.kt:41-60`): `passage` (SCRIPTURE block), then per answered step a `HEADING_3` holding the **question text** plus a `PARAGRAPH` answer with `source=USER, isUserEdited=true`, `sectionKey = stepId` (`q1/q2/q3`, `observation`, `prayer`, `live`, `verse-<n>`, `reflection`). Unanswered steps leave no blocks; suggested questions and canned prayers (`QuestionBank`) are transient UI and never persisted.
  4. Sermon notes (`ai/faith/SermonNoteWriter.kt`): `main_scripture`, `key_message`, `key_points`, `quotes` (**`TRANSCRIPT_EXCERPT`**, not QUOTE), `application`, `prayer_points`, `reflection_questions`, `scripture_refs`, `sermon_transcript`, plus user sections `my_notes`, `my_response` (private), `my_prayer` (private).
  5. Devotional notes (`core/devotional/DevotionalNotes.kt:42-62`): `daily_scripture`, `daily_reflection`, `daily_application`, `daily_live`, `daily_prayer`, `daily_motivation`, `daily_insight`, `daily_question`, `daily_my_response`. Plus the small-group guide (`NoteAiApply.withStudyGuide`): `sg_header`, `sg_<key>` (my earlier prompt-level name `GroupGuideExport` does **not** exist).
- **Commentary and cross-references are not stored in notes.** They live in `BibleStore` tables (`commentary`, `crossRefs`, HelloAO data) and are shown as chips in `StudyWorkspace`. They appear in a note only when the assistant or the user inserts them as ordinary blocks.
- **SCRIPTURE blocks hold only a reference** (`payload{scriptureRefId, reference}`). Text is re-fetched at read time by `ScriptureService.passage(ref, versionId?)` using `bible_version_id` (default BSB 3034). The `ScriptureRef.versionId` is written as `null` by almost every path. `payload["userText"]/["userLabel"]` (user-typed verse text) takes precedence at render time. **The `ScriptureCard` ignores a per-ref `versionId` while export honours it**, so display and export can differ (pre-existing bug, `VerseSheet.kt:109` vs `ScriptureService.exportPassage`).
- Passages can be `Unavailable` (reasons NOT_CONFIGURED, OFFLINE, NOT_LICENSED, NOT_FOUND, ERROR); licensed translations (NIV, ESV, NLT...) are memory-only and often unavailable offline.
- **Verse numbers are not in stored text.** `ChapterVerse(number, text)` keeps the number separately; `ChapterContent.text(start,end)` (and so `Passage.text`) flattens a range into one string with `\n` turned into spaces. Whether YouVersion's single-passage `format=text` embeds numbers is unverified (fixtures contain none) [A].
- There is **no option to read verse numbers aloud**, and `SpeechScript.clean` strips numbers with a heuristic that can also strip real numbers before a capital letter (`Speech.kt:141-144`).
- Existing audio Bible is **pre-recorded narration with verse start times** (HelloAO, BSB at least): `ChapterAudio.verseAt/startOf` (`core/scripture/HelloAo.kt:37-46`), used for follow-along in `BibleViewModel.readingVerse`. YouVersion chapters have no audio. The guided session's "Listen" button uses it.
- **No section-navigation UI** exists in notes (headings only fold). There is no test for `Workflows.startingBlocks(BIBLE_STUDY)`, `StudyTemplate.blocks`, `SermonNoteBuilder` layout, or any block-to-speech extraction. `testdata/sermons/*.json` are transcript-level only (no note-block or quotation fixtures).

### 16.2 Findings that change the design [V]
1. **Translation licence is not modelled for AI or TTS.** The only licence-derived field is `BibleInfo.offlineAllowed` (`core/scripture/Bible.kt:24-36`, rule `BibleLicensing` `:80-92`: open list BSB, ASV, WEB variants, CPDV, GNV, LSV, FBV, TCENT, BBE, YLT, DRA, OEB, plus "public domain/creative commons" wording). KJV, NIV, ESV, NLT, NKJV are **not** open; `BibleStore.info()` hard-codes `offlineAllowed=true` for anything stored; `Passage` does not carry the flag. `docs/PLAN_V1.md` §7 and `PLAN_V2.md` say copyrighted translations are "licensed for on-demand display, not storage" and notes store references only.
2. **The existing devotional voice already violates that intent**: `DevotionalVoice.record` fetches the user's translation, sends it to Gemini TTS when Internet+key, and **persists the result as an audio file**, with no licence check and no attribution (`DevotionalVoice.kt:97-124`, `engines()` `:53-60`). `docs/DATA_SAFETY.md` does not mention Bible text, YouVersion or Gemini at all.
3. **Fidelity machinery exists but modifies text.** `DevotionalContract.fixQuotedScripture` (`ai/devotional/DevotionalContract.kt:235-257`) *replaces* a mismatched quote with library text or "(see Ref)" and uses an 80% bag-of-words match; `AssistantHost.scriptureSafe` swaps the model's quote block for a library block; `CreateGuards.quotesScripture` blocks. None of these detect an **unreferenced** inline quotation. The requirement here is the opposite of repair: **never silently modify a quotation to pass validation.** These helpers may be reused only as *detectors*.
4. **Section-level privacy exists only for template sections** (`Workflows.template(...).privateKeys`: `my_response`, `my_prayer`, devotional `reflection`/`prayer`, `request`, `updates`), and is applied today only to copy/export. `BIBLE_STUDY` and `SERMON` notes are **not** private by default (`isPrivateByDefault` covers JOURNAL, PRAYER, PRAYER_REQUEST, DEVOTIONAL, GRATITUDE, REFLECTION, TESTIMONY). Study-template and guided-journal answer sections are not in any `privateKeys` set even though they are personal writing. A sermon note therefore mixes cloud-eligible and private sections.
5. **No tradition-neutral delivery layer.** `PreacherStyle` presets (9, with a tradition label each, free-text style containing accent text such as a Zambian accent) are devotional-only; `DevotionalProfile.tradition` is a profile field, not a note property. Gemini docs advise using a regional voice rather than style text for immutable traits like accent.

### 16.3 Content roles (the Bible Study layer) [P]
A pure `StudyRoleClassifier` maps every `SpeechUnit` to a closed `SpeechRole`, **deterministically, with no AI**:
`SCRIPTURE, QUOTE (user/preacher), TRANSCRIPT, COMMENTARY (AI or imported prose), OBSERVATION, INTERPRETATION, APPLICATION, QUESTION, PRAYER, PERSONAL (user's own words), HEADING, OTHER`.

Resolution order: (1) block type (`SCRIPTURE`, `QUOTE`, `TRANSCRIPT_EXCERPT`); (2) `sectionKey` via an explicit table covering the vocabularies in §16.1 (including prefix rules `daily_*`, `sg_*`, `study_<t>_<i>`); (3) for `study_*` and unknown keys, the **nearest heading text** matched against a small multilingual-agnostic keyword table (Prayer, Observe/Observation, Interpret/What it means, Apply/Application, Question(s)/Discuss...); (4) `BlockSource`/`isUserEdited`: user-edited prose with no other role = `PERSONAL`; `BlockSource.AI` prose = `COMMENTARY`-flagged `aiGenerated`; (5) otherwise `OTHER` (read plainly with no label). Guided-journal pairs: the `HEADING_3` question is role `QUESTION` (read as the question), the following user-edited paragraph is `PERSONAL` ("Your answer").

Empty template blocks (`payload["hint"]` only) are skipped; hints are UI placeholders and are **never read** as content. Folded sections are read (folding is a view state), with an option later.

### 16.4 Spoken presentation: distinguishing the parts [P]
Fixed, translatable label phrases from a closed table, never AI-generated:
- Scripture: "Scripture. John chapter 3, verses 16 to 18. Berean Standard Bible." (reference via existing `SpeechScript.spoken`; translation name from `Passage.versionAbbreviation`/title, once per passage; setting *Announce translation*: always / first time / never).
- "Commentary." / "Observation." / "What it means." / "Application." / "Question for reflection." / "Your note." / "Prayer." Sections the person wrote read as "Your note"; AI-generated prose is labelled "AI-written" (matches the existing "AI-written devotional" labelling). Label style setting: *Full*, *Short* (a brief cue), *Off* (pauses and delivery cues only). Default **Full** for Read Exactly and Smart Read alike.
- Additional distinction: longer pause before/after scripture and a distinct delivery cue (Gemini only; local gets pause + label only). A second voice for scripture is deferred (one voice per reading in v1).
- The full licence attribution string is **shown** in the now-playing sheet's "Sources" line for each passage (UI display, as elsewhere in the app), not read aloud.

### 16.5 Scripture fidelity rules [P]
1. **Scripture never reaches a rewriting model.** In both modes a `SCRIPTURE` unit is a locked span: the text spoken is exactly the resolved passage text (or `userText`), only wrapped by the label and the optional verse-number tokens. Smart Read replaces it with a placeholder before any model call and re-inserts it after.
2. **Verse numbers aloud (setting `Read verse numbers`, default off).** Use verse-structured data (`BibleLibrary.chapter` → `ChapterVerse`) instead of the flattened `Passage.text`, so the numbers are exact and never guessed. When on, insert "verse N" tokens between verses; when off, speak the verse text only. The heuristic `SpeechScript.clean` is **not** used on Bible Study scripture. If only flattened text is available (a YouVersion passage fallback), the numbers option is unavailable for that passage and the sheet says so; no number-stripping heuristic is applied to scripture text.
3. **Which translation:** the same one the card would show. Proposal: `ref.versionId ?: bible_version_id`, **and fix the card/export mismatch in Stage 1** so display, export and narration agree. `userText` is read verbatim and labelled "Your text" (never presented as the translation).
4. **Unavailable passage:** the section is read as "<Reference>. This passage isn't available right now." and skipped. **Never substitute another translation silently**; the sheet offers "Use <open translation>" as an explicit, one-time choice.
5. **Quotation boundaries (inside non-scripture blocks):** extraction runs a *detector* (never a repairer) to mark inline quotations as locked spans: (a) quoted text of ≥25 characters adjacent to a parseable reference (the existing `fixQuotedScripture` regex logic, used read-only); (b) n-gram (≥6 words) overlap between the block's text and the resolved text of every passage referenced in the study. Matched spans are locked (read exactly, never rewritten), and a quotation that differs from the translation is **read as the person wrote it, not corrected**. Known limit: an unreferenced quotation of a verse not otherwise in the study cannot be detected without a full-text scan; mitigated by the strict commentary guards in §16.6. A full-translation scan using the existing `BibleStore` FTS index (open translations only) is a Stage 5 candidate.
6. **Fidelity validator rule (hard requirement):** if any check fails, the section **falls back to the as-written spoken form and is labelled so**; the validator never edits a quotation to make it pass.

### 16.6 Smart Read for Bible Study: role tiers [P]
| Role | Smart Read behaviour |
|---|---|
| SCRIPTURE, inline quotations, TRANSCRIPT, QUOTE | Locked verbatim (no model sees the text) |
| PRAYER, PERSONAL | Locked verbatim by default: no rewriting of the person's own prayers or reflections; only fixed label phrases and pauses. (Optional "smooth transitions" later) |
| QUESTION | Read as written, then the question pause (§16.7) |
| COMMENTARY, OBSERVATION, INTERPRETATION, APPLICATION | "Light" rewrite allowed: spoken flow, short transitions, emphasis cues; **no new claims** |

Extra deterministic guards for light-rewrite roles (on top of §6.C): equal counts of negations ("not/never/no/neither") and hedges ("may/might/perhaps/could/one possible"); no scripture reference, book name, proper noun, number or date that is absent from the source; no quotation marks absent from the source; tight length ratio (0.9-1.15); no phrases from the faith-contract forbidden list ("God told me", "Thus says the Lord", "I prophesy"...); attribution tokens ("According to X", "as Calvin wrote") preserved. Any failure → as-written fallback for that section and a "read as written" flag. An optional second-pass model "faithfulness judge" is advisory only and can only *reject* (never edit), cloud-permitted only. Smart Read never presents generated commentary as biblical text: the label and a distinct delivery cue prevent it, and the "See what changed" view diffs script against source.

### 16.7 Playback experience [P]
- **Entry points (reuse the shared sheet):** note editor menu "Read to me" for any Bible Study / sermon / devotional note (the whole study); `ScriptureCard`/`VerseSheet` gets a "Listen" action for one passage (the `VerseSheet` already has a "Read" action and a sermon "Play" chip, so label carefully); `StudyWorkspace` PASSAGE tab "Listen to this passage"; heading "Read from here" for a section. The Bible reader (`BibleScreen`, task B-3) and `GuidedSessionScreen` get the same engine in a later stage: the guided session's existing Listen uses pre-recorded audio that exists only for HelloAO translations (BSB); Read to Me fills the gap for every other translation.
- **Queue = study sections.** Each scripture passage and each section is a queue item titled with its reference or heading (e.g. "John 3:16-18", "Observations"), so reference/section navigation is "next/previous item" plus a jump list in the now-playing sheet. Passage and section order follows the note exactly.
- **Follow-along:** block-level highlight and scroll in the note (each queue item stores `sourceBlockIds`); scripture items map to their `SCRIPTURE` block, so passage-level highlight is reliable. Verse-level highlight is **not** promised for synthesized speech (no timestamps). It is only possible where a pre-recorded narration with `ChapterAudio.timings` is used (deferred option below).
- **Pause after a reflection question (setting):** *None / Short (5 s) / Medium (10 s) / Until I resume*. Short and Medium are real silence appended to the question's audio (`Wav.silence`, as today). *Until I resume* ends the question's queue item with a controller pause (same mechanism family as the existing `stopAtMs`/`playRange`; fix its stale-`stopAtMs` bug first). Resume continues at the next item; because progress is stored per queue item, no place is lost across app switching or process death.
- Background, lock-screen, Bluetooth and headphone controls come from the §6.F player hardening. **Faith privacy on the lock screen:** for private or faith-locked notes the notification/lock-screen title is a generic "Read to Me" (title and artwork are in `MediaMetadata`), and generation/playback requires the faith lock to be open (`FaithLock`).
- Voice and delivery overrides are the same per-note overrides as in §5/§7; changing voice mid-reading applies from the next item.

### 16.8 Privacy and licence routing [P]
`SpeechRoute` (§9) gets three more inputs, evaluated per reading:
1. **Note privacy (agreed policy):** private notes, confidential notebooks and `WorkPrivacy` OFFLINE → local only. Same as every other vertical; no Faith exception.
2. **Private and personal sections:** sections in the template `privateKeys`, and (proposal, mirrors `DevotionalProfile.sharePrivateWithCloud` which defaults to false) user-authored `PERSONAL`/`PRAYER` content, are **not** eligible for the cloud route unless the person turns on "Allow my personal notes and prayers to be read by Gemini". When a cloud route is chosen and such sections exist, the sheet says "N personal sections are excluded from the cloud voice" with choices: *Skip them*, *Read everything with this phone's voice*, or *Allow cloud for personal notes*. No mixed voices within one reading, so the choice applies to the whole reading.
3. **Translation licence gate:** cloud speech of scripture text is allowed only for translations on an explicit allowlist (the open list in `BibleLicensing`; extending it to KJV is an owner decision) and, for those, audio may be cached. For any other translation the reading uses the on-device voice, scripture audio is cached **ephemerally** (cache dir, short TTL, excluded from backup), and the sheet explains why ("NIV text is licensed for display on this device, so it's read with your phone's voice"). `Passage` must carry a license/open flag (small additive field; `BibleInfo.offlineAllowed` is today's proxy and is wrong for stored items and KJV).
4. **Offline never Gemini** and no silent cloud fallback (unchanged). Playing already-cached open-translation audio offline is fine.

Whether sending licensed text to Gemini TTS, or persisting audio of it, is permitted by the provider terms is a **legal question the repository cannot answer** (see §16.10).

### 16.9 Preferences, tradition and consistency [P]
- The same `ReadToMeProfile` (§7) serves all verticals; Faith adds only enum/boolean settings: delivery preset, verse numbers (bool), label style (enum), announce translation (enum), pause after questions (enum). **No scripture or note text, references, translation ids of specific passages, or note ids are stored.** A test asserts the serialised profile contains only enum/int/bool fields.
- **Delivery presets (explicit choice only):** *Calm teacher*, *Reflective devotional*, *Warm pastor*, *Vibrant Pentecostal preacher* join the preset list (reusing the existing preset texts where suitable, with accent text removed in favour of voice choice). Faith-flavoured presets are listed when the Faith space is on. **Never auto-applied because content is Christian; never inferred from a single note.** `DevotionalProfile.tradition` may only *suggest* a preset (one dismissible chip, never applied).
- The existing devotional voice settings are an explicit user choice. On first Read to Me use, offer "Use your devotional voice (Warm pastor, <voice>)?" as an explicit adoption; never import silently. The two rate ranges/inconsistencies (§2.1 #7) get reconciled at that point.
- Learned defaults follow §7: explicit beats learned; category keys group `SERMON`, `BIBLE_STUDY`, `DEVOTIONAL/PRAYER`, `REFLECTION/JOURNAL` etc.; abandonment never counts against a preset. No denomination or theology is inferred from content signals at any stage.

### 16.10 Caching and invalidation additions [P]
Section audio key (§8) gains: **resolved translation id**, **hash of the resolved scripture text** (so a changed translation, edited `userText` or re-fetched text invalidates), verse-numbers setting, label style, announce-translation setting, pause setting (for appended silence), role-label table version. Passages that resolved `Unavailable` are never cached as audio and are re-resolved on next play. Persistence stays in the sidecar manifest (no Room change); ephemeral licensed-translation audio lives in the cache dir.

### 16.11 Staged roadmap deltas (Bible Study is in v1)
- **Stage 0:** add (a) the licence/ToS decision for licensed translations (§16.12 Q1), (b) a spike: does YouVersion `format=text` embed verse numbers, and does the verse-structured chapter path cover every translation the user can select, and (c) a small fixture set: one real BIBLE_STUDY note per creation path (workflow template, SOAP study, guided journal, sermon note, devotional note) as `testdata/` JSON, plus passages with known verse text.
- **Stage 1 (Read Exactly + passages):** `StudyRoleClassifier`; `ScriptureResolver` (translation, `userText`, `Unavailable`, verse-structured fetch, verse-number setting); label phrases; licence + private-section + faith-lock gates in `SpeechRoute`; fix `ScriptureCard` vs export translation mismatch; `Passage` licence flag; single-passage entry from `VerseSheet`/PASSAGE tab; queue items per section/passage; pause-after-question (silence and until-resume). Files: new `ai/voice/read/*` roles/resolver; `core/scripture/ScriptureProvider.kt` (`Passage`), `Bible.kt` (`BibleInfo`/`BibleLicensing`), `feature/scripture/VerseSheet.kt`, `feature/study/StudyWorkspace.kt`, `NoteEditorScreen.kt`, `PlaybackController`/`MiniPlayerBar`.
- **Stage 2 (Smart Read):** role tiers, the commentary guards, inline-quotation detector, locked-span placeholders, as-written fallback, diff view.
- **Stage 3 (UX/lifecycle):** section/passage jump list, block highlight/scroll, lock-screen privacy title, process-death resume, error states (unavailable passage, licensed translation offline).
- **Stage 4 (prefs):** presets, faith settings, adoption of the devotional voice, suggestion chips, reset; no-text-in-profile test.
- **Stage 5 (defer unless time):** reading commentary and cross-references from `BibleStore` (attributed by name, public-domain commentaries only), recorded-narrator option for BSB with verse-level highlight (human narrator, network streaming, not Gemini; offline only if audio is downloaded, which today it is not), FTS-based quote scan, second voice for scripture, Bible reader (B-3) and guided-session integration, per-translation attribution policy refinements.
- **Migration risk:** none for the Room schema in v1 (sidecar manifest, DataStore profile). `Passage`/`BibleInfo` gain a field (source-compatible). `MainActivity` open-note routing and `MiniPlayerBar` prefix cases remain the hot-file touches.

### 16.12 Tests required by the acceptance criteria (planned, none run)
| Criterion | Tests |
|---|---|
| Scripture wording unchanged (both modes) | Golden: spoken scripture words == resolved passage words in order for each fixture passage, in Read Exactly and Smart Read (fake model that tries to paraphrase, alter punctuation, drop a verse, reorder); verse-number setting on/off differs only by inserted "verse N" tokens; text beginning with a number ("28 Come to me") stays intact; no `clean()` heuristic applied to scripture |
| Quotation boundaries | Detector marks reference-adjacent quotes and ≥6-gram overlaps as locked; a misquote in commentary is read as written (not corrected); validator failure falls back to as-written and **never edits** the quote; unreferenced quote of an unrelated verse documented as a known limit |
| Commentary vs scripture distinct | Role classifier table tests across all five creation paths (every `sectionKey` vocabulary, `study_*` via heading text, guided Q&A pairs, sermon `quotes` as TRANSCRIPT_EXCERPT); label phrases present and ordered; AI blocks labelled "AI-written"; `userText` labelled "Your text" |
| Commentary integrity | Guards: injected negation flip, dropped hedge, new proper noun/number/reference, new quotation marks, forbidden prophetic phrase, attribution loss each → as-written fallback |
| Privacy routing | Matrix: offline (never Gemini, zero-network fake client); private note; confidential notebook; `WorkPrivacy`; private template sections excluded; personal sections excluded unless allowed; licensed translation → local only + ephemeral cache; open translation → cloud allowed; faith lock closed → no generation, generic lock-screen title; unavailable passage handling; no silent fallback in offline |
| Section mapping | Queue item order == note order; every item has non-empty `sourceBlockIds`; item titles are references/headings; highlight target resolves to the right block after a reorder edit |
| Cache invalidation | Change translation; edit `userText`; edit a commentary block (only its section regenerates); change verse-numbers/label/pause setting; passage text changes upstream; manifest freshness check shows "Edited since this reading" |
| Playback resumption | Resume at queue item + position after process recreation (manifest); pause-after-question until-resume survives app backgrounding and kill; stale `stopAtMs` regression test; next/previous item navigation |
| Personalisation | Profile JSON contains no text/reference/note id (schema-level assertion); tradition never changes the applied preset; explicit overrides beat learned; abandonment adds no negative signal; reset clears Faith settings too |
Plus the human listening set (§12) extended with: one SOAP study with a licensed translation (phone voice), one Inductive study with BSB (Gemini), one guided-journal note with personal answers and prayers, one sermon note with transcript quotes, rated for scripture fidelity (word-for-word check by ear against the text), clarity of role labels, and pacing of question pauses.

### 16.13 Open questions specific to Bible Study
1. **Translation licence (blocker for the Gemini path with licensed text):** do the YouVersion and other provider terms allow (a) sending licensed verse text to a third-party speech service, and (b) storing the resulting audio? The repository has no statement either way. **Default until answered:** cloud voice and persisted audio only for open translations; licensed translations use the phone voice with an ephemeral cache. Also decide whether KJV counts as open (it is not on the current list).
2. **Personal sections and the cloud:** is excluding user-authored prayers and reflections from the cloud route by default (mirroring `sharePrivateWithCloud=false`) the intended reading of "same restrictions as all other notes"? Default: yes, with an explicit opt-in.
3. **Display/narration translation:** confirm that `ScriptureRef.versionId ?: default` should drive display, export and narration, which requires fixing the card (default: yes).
4. **Faith lock:** confirm that locked Faith notes cannot be read aloud until unlocked and show a generic lock-screen title (default: yes).
5. **Verse numbers default:** off (default) vs on for study use.

### 16.14 Risks added
| Risk | Mitigation |
|---|---|
| Licensed scripture sent to a cloud speech service or stored as audio | Licence gate in `SpeechRoute`, `Passage` licence flag, ephemeral cache, Stage 0 decision, tests |
| A quotation is altered to satisfy a validator | Detect-and-lock only; failures fall back to as-written; test with an adversarial fake model |
| Mis-classified section role mislabels content (e.g. calls a personal note "commentary") | Closed table + heading keywords; unknown roles read plainly with no label; fixtures for all creation paths |
| Private sections silently included in a cloud reading | Per-section gate and sheet disclosure; test per `privateKeys` set |
| Verse numbers wrong or heuristically stripped | Verse-structured fetch only; no heuristic on scripture; option disabled when only flattened text exists |
| Display vs narration translation disagree | Single resolver, card fix, shared test |
| Lock-screen exposes a private study title | Generic metadata for private/faith-locked notes |
| Theology drift in Smart Read commentary | Role tiers, hedge/negation guards, forbidden-phrase list, as-written fallback, visible diff |
