# Faith vertical — inventory and closing plan

Source of truth: PLAN_V3 + "Close the Faith Vertical" spec. Status against the repository as of
commit 2cebc6a. DONE = works end to end; PARTIAL = exists but short of the spec; MISSING = not
there; BROKEN = there but wrong.

## Inventory

| # | Area | Status | What exists / what's short |
|---|------|--------|----------------------------|
| 2 | Faith workflows & notebooks | DONE | `RecordingType` has SERMON, BIBLE_STUDY, DEVOTIONAL, PRAYER, PRAYER_REQUEST, TESTIMONY, GRATITUDE, REFLECTION with templates (`Workflows`); `NotebookSpace.FAITH`. Scripture as a workflow: PARTIAL (Scripture notes via collections). |
| 3 | Canonical transcript is evidence | DONE | `CanonicalTranscript` words/timings/speakers/`TranscriptSource`; Notes AI works on note blocks, never on transcript rows; excerpts copy into notes. |
| 4 | Transcription Engine 2.0 | PARTIAL | Gemini default in Internet mode, verbatim + diarization + word timestamps, compressed-audio cutting (no decode), per-part logging, Smart pass off the critical path. MISSING: PreparedAudio cache, persisted chunks, bounded concurrency, progressive transcript, TranscriptionSession/checkpoints, Local↔Gemini switching mid-run. |
| 5 | Scene intelligence | MISSING | No AcousticActivity/SemanticActivity. |
| 6 | Worship/song handling | MISSING | |
| 7 | Sermon intelligence | PARTIAL | `SermonExtraction`: outline/points/scripture/applications cited by transcript segment ids (→ timestamps); `ScriptureDetector` finds references in transcripts. MISSING: scene timeline, tappable [mm:ss] citations across the sermon view. |
| 8 | Scripture intelligence | PARTIAL | Offline Bible library (download), reader, search, verse sheet, Scripture blocks with real text, cross-refs + commentary (HelloAo, cached), collections, highlights, fixQuotedScripture guard for devotionals. MISSING: memory verses, parallel translations view, guard applied to every AI surface. |
| 9 | Bible study workspace | MISSING | Reader and notes are separate screens; no "Notes on this passage". |
| 10 | Study methods templates | PARTIAL | BIBLE_STUDY template only. MISSING: SOAP, Inductive, Book overview, Character, Word, Topical, Sermon prep, Small group. |
| 11 | Original languages | MISSING | Licence candidates: STEPBible TAHOT/TAGNT (CC BY 4.0), OSHB morphology (CC BY 4.0), SBLGNT (CC BY 4.0). Must be an optional download. |
| 12 | Faith AI assistant with tools | MISSING | Notes AI tools (summary, actions, organise, ask) exist; no tool-using assistant. |
| 13 | Theology contract | PARTIAL | Devotional contract + guard (no divine claims, no promised outcomes, verse checking, care check). Not yet applied to every Faith AI prompt; prompts are code strings, not versioned assets. |
| 14 | Devotionals 2.0 | PARTIAL | Written/classic/mine sources, several per day, angles + opening rotation, 10-day memory, feedback, voice, share cards. MISSING: archive screen (calendar/search/favourites/filters), formats, tradition presets, 14-day memory with passage/title/opening/main-point checks, format rotation, passage exclusion window. |
| 15 | Series | MISSING | |
| 16 | Morning/evening continuity | MISSING | Evening falls back to a classic. |
| 17 | Prayer | PARTIAL | Prayer requests with answered state, updates, testimony link; prayer people list (FaithStore) with daily rotation; reminders; Pray with me (live voice). MISSING: people attached to requests, recurring per-request reminders, Scripture attached to prayer. |
| 18–20 | Sermon workflow / Study this sermon / Ask Sermon | PARTIAL | Recording → transcript → sermon note with points/scripture. Ask AI on a recording exists (retrieval). MISSING: Study workspace, timestamped answers everywhere. |
| 21 | Knowledge layer | PARTIAL | scripture_refs, note_links, backlinks, related notes. |
| 22 | Search | PARTIAL | LIKE search over titles/text. MISSING: FTS, transcript + Scripture in one search, semantic search over notes. |
| 23 | Tasks | MISSING | Action items exist only on recordings. |
| 24 | People | PARTIAL | Prayer list names only (FaithStore). No general People entity. |
| 25 | Export | DONE | PDF/Word/Markdown, private sections excluded by default. Page numbers/headers: check. |
| 26 | AI images | PARTIAL | Devotional artwork (Gemini). Label: check. |
| 27–28 | Corpus + scorecard | MISSING | |
| 29 | Data safety | DONE | No destructive fallback, schemas 14–15 exported, migration tests, backup/restore incl. faith DB + highlights. |
| 31 | Model routing | PARTIAL | `DefaultAiModelRouter` holds Gemini IDs; DeepSeek ID lives in `DeepSeek`. |
| 32 | Offline states | PARTIAL | |

## Limitations known up front

- **Song identification:** no licensed song database is available offline; titles are only
  shown when Gemini names a song with a verbatim title match in the transcript and marks it
  confident. Lyrics are collapsed, never reproduced beyond what the transcript holds.
- **Transcription corpus:** no rights-cleared church audio is available in the build
  environment. The corpus layout, reference formats and scorer ship; audio files must be added
  by the team (recorded with permission). No quality numbers are claimed without them.
- **Acoustic activity:** computed locally from the decoded audio (energy, spectral flatness,
  zero-crossing rate, pitch stability) — heuristic, tuned against the corpus once it exists.
