# Faith vertical — inventory and closing plan

Source of truth: PLAN_V3 + "Close the Faith Vertical" spec. Status against the repository as of
the Faith-vertical build (schema 16). DONE = works end to end; PARTIAL = exists but short of the spec; MISSING = not
there; BROKEN = there but wrong.

## Inventory

| # | Area | Status | What exists / what's short |
|---|------|--------|----------------------------|
| 2 | Faith workflows & notebooks | DONE | Faith `RecordingType`s with templates; `NotebookSpace.FAITH`. |
| 3 | Canonical transcript is evidence | DONE | Words/timings/speakers/source; AI output cites segments or `[mm:ss]`. |
| 4 | Transcription Engine 2.0 | PARTIAL | Gemini default in Internet mode, verbatim + diarization + word timestamps; audio cut by remuxing (no decode); two parts at a time, finished parts readable at once (provisional `partial_` segments); IPv4-first networking; copyable error log; DeepSeek fallback for text. **Not built:** switching Local↔Gemini mid-run, persisted chunk checkpoints. |
| 5 | Scene intelligence | DONE | On-device acoustic analysis → candidate windows → AI labels for those windows only; `scenes.json` beside the audio; scene strip and headers in the transcript. Tested on synthetic audio only. |
| 6 | Worship/song handling | DONE | Song scenes folded in the transcript; excluded from Scripture detection and sermon extraction; titles only when said or sung verbatim (≥2 words); lyrics never written. |
| 7 | Sermon intelligence | DONE | Outline/points/applications with tappable `[mm:ss]` evidence; Scripture list; scene timeline. |
| 8 | Scripture intelligence | DONE | Offline library, reader, search, verse sheet, cross references, commentary, collections, highlights, memory verses (masked recall), notes on a passage. Spoken references ("chapter 8 … verse 28", "verse 38 to 39" a paragraph later) are found. |
| 9 | Bible study workspace | DONE | Split passage/notes view (tabs on phones); tap a verse to add it; sermon timeline, Scripture and transcript beside the note. |
| 10 | Study method templates | DONE | SOAP, Inductive, Book overview, Character, Word, Topical, Sermon prep, Small group. |
| 11 | Original languages | MISSING | Licence candidates (STEPBible CC BY 4.0, OSHB CC BY 4.0, SBLGNT CC BY 4.0); must be an optional download. Not built. |
| 12 | Faith AI assistant with tools | DONE | Chat sheet in every note and on Faith home. Tools: read note, search notes, read transcript, get verses, cross references, commentary, compare translations, insert blocks/Scripture, replace/delete (confirmed first), create task, create note. Inserts have Undo. Needs a live model to exercise end to end. |
| 13 | Theology contract | DONE | One contract in `assets/prompts/faith_contract.md` prefixed to every Faith prompt; guards remove voice-of-God sentences (Ask Sermon, assistant, devotionals). |
| 14 | Devotionals 2.0 | DONE | 17 formats (least-recently-used rotation), tradition presets, 30-day passage exclusion, memory of passage/opening/point with novelty retry, archive (calendar, search, favourites, filters), audience, reading level, language. |
| 15 | Series | DONE | Catalog + book series with day counter and "so far". |
| 16 | Morning/evening continuity | DONE | Evening Examen written from the morning's devotional. |
| 17 | Prayer | PARTIAL | Requests, answered state, updates, testimony link, prayer list with rotation, reminders, Pray with me. Tasks of kind "Prayer" carry a person, a Scripture reference and repeating reminders. Requests themselves are not yet linked to People rows. |
| 18–20 | Sermon workflow / Study this sermon / Ask Sermon | DONE | Recording → transcript → sermon note → Study; Ask on a faith recording uses `ask_sermon` with `[mm:ss]` markers checked against the passages it was given (invented ones removed, unverified answers flagged). |
| 21 | Knowledge layer | PARTIAL | scripture_refs, note_links, backlinks, related notes, note↔person links. |
| 22 | Search | DONE | FTS4 over notes and transcripts (schema 16), Bible passages by reference, tasks; filters; Ask across everything with numbered sources checked against what search found. Semantic search stays the existing on-device hashed embedding for recordings. |
| 23 | Tasks | DONE | Tasks with due, reminder (alarm + notification with Done), repeat, kind, person, source note/recording; "Make it a task" from any note line, checklist and task stay in step; Tasks screen and To-live-out card. |
| 24 | People | DONE | People table, tasks and notes per person. |
| 25 | Export | DONE | PDF/Word/Markdown, private sections excluded by default. |
| 26 | AI images | PARTIAL | Devotional artwork (Gemini). |
| 27–28 | Corpus + scorecard | PARTIAL | Four hand-marked transcripts in `testdata/sermons`, scorer test, `docs/FAITH_SCORECARD.md`. **No audio corpus.** |
| 29 | Data safety | DONE | No destructive fallback; schemas 14–16 exported; migration tests 14→15, 15→16; backup/restore covers preferences (incl. look and home). |
| 31 | Model routing | DONE | `DefaultAiModelRouter` for Gemini; DeepSeek fallback for all text features with a 6M-token monthly meter. |
| 32 | Offline states | PARTIAL | Assistant and Ask say plainly when they need Internet mode; everything else works offline. |

## Limitations known up front

- **Song identification:** no licensed song database is available offline; titles are only
  shown when Gemini names a song with a verbatim title match in the transcript and marks it
  confident. Lyrics are collapsed, never reproduced beyond what the transcript holds.
- **Transcription corpus:** no rights-cleared church audio is available in the build
  environment. The corpus layout, reference formats and scorer ship; audio files must be added
  by the team (recorded with permission). No quality numbers are claimed without them.
- **Acoustic activity:** computed locally from the decoded audio (energy, spectral flatness,
  zero-crossing rate, pitch stability) — heuristic, tuned against the corpus once it exists.
