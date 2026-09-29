# Faith vertical — inventory and closing plan

Source of truth: PLAN_V3 + "Close the Faith Vertical" spec. Status against the repository as of
the Faith-vertical build (schema 16). DONE = works end to end; PARTIAL = exists but short of the spec; MISSING = not
there; BROKEN = there but wrong.

## Inventory

| # | Area | Status | What exists / what's short |
|---|------|--------|----------------------------|
| 2 | Faith workflows & notebooks | DONE | Faith `RecordingType`s with templates; `NotebookSpace.FAITH`. |
| 3 | Canonical transcript is evidence | DONE | Words/timings/speakers/source; AI output cites segments or `[mm:ss]`. |
| 4 | Transcription Engine 2.0 | DONE | Gemini (verbatim, word timestamps, diarization) with the phone behind it. Every finished part or decode window is saved as a *region*; either engine continues from what the other finished, Gemini falls back to the phone for what's left, you can switch either way from the Processing screen (Internet mode), and a restart never resends a finished part. Two parts in flight, readable as it goes, IPv4-first networking, copyable log. Offline-only recordings keep the phone path and never reach Gemini. Parts done on the phone have no speaker labels. Tested with fakes for every path; not yet exercised on a real long recording across a real network loss. |
| 5 | Scene intelligence | DONE | On-device acoustic analysis → candidate windows → AI labels for those windows only; `scenes.json` beside the audio; scene strip and headers in the transcript. Tested on synthetic audio only. |
| 6 | Worship/song handling | DONE | Song scenes folded in the transcript; excluded from Scripture detection and sermon extraction; titles only when said or sung verbatim (≥2 words); lyrics never written. |
| 7 | Sermon intelligence | DONE | Outline/points/applications with tappable `[mm:ss]` evidence; Scripture list; scene timeline. |
| 8 | Scripture intelligence | DONE | Offline library, reader, search, verse sheet, cross references, commentary, collections, highlights, memory verses (masked recall), notes on a passage. Spoken references ("chapter 8 … verse 28", "verse 38 to 39" a paragraph later) are found. |
| 9 | Bible study workspace | DONE | Split passage/notes view (tabs on phones); tap a verse to add it; sermon timeline, Scripture and transcript beside the note. |
| 10 | Study method templates | DONE | SOAP, Inductive, Book overview, Character, Word, Topical, Sermon prep, Small group. |
| 11 | Original languages | DONE | Optional downloads (Greek NT about 35 MB, Hebrew OT about 75 MB) from STEPBible (Tyndale House, CC BY 4.0), parsed on the phone into a small database. Verse study sheet shows each word: script, transliteration, short gloss; tap for lemma, Strong's, grammar decoded by rule, and the lexicon entry. Assistant tool `get_original` reads the same data and is told to refuse to answer from memory. Removable in Settings. No AI interpretation is added. Hebrew verse numbering can differ from English (Psalms) and is noted in the UI. |
| 12 | Faith AI assistant with tools | DONE | Chat sheet in every note and on Faith home. Tools: read note, search notes, read transcript, get verses, cross references, commentary, compare translations, insert blocks/Scripture, replace/delete (confirmed first), create task, create note. Inserts have Undo. Needs a live model to exercise end to end. |
| 13 | Theology contract | DONE | One contract in `assets/prompts/faith_contract.md` prefixed to every Faith prompt; guards remove voice-of-God sentences (Ask Sermon, assistant, devotionals). |
| 14 | Devotionals 2.0 | DONE | 17 formats (least-recently-used rotation), tradition presets, 30-day passage exclusion, memory of passage/opening/point with novelty retry, archive (calendar, search, favourites, filters), audience, reading level, language. |
| 15 | Series | DONE | Catalog + book series with day counter and "so far". |
| 16 | Morning/evening continuity | DONE | Evening Examen written from the morning's devotional. |
| 17 | Prayer | DONE | Requests, answered state, updates, testimony link, prayer list with rotation, reminders, Pray with me. A note can name the people it is about; a testimony inherits its request's people; a person's page groups Praying for / Answered / Testimonies / Notes and their tasks. Prayer-list names become People. |
| 18–20 | Sermon workflow / Study this sermon / Ask Sermon | DONE | Recording → transcript → sermon note → Study; Ask on a faith recording uses `ask_sermon` with `[mm:ss]` markers checked against the passages it was given (invented ones removed, unverified answers flagged). |
| 21 | Knowledge layer | PARTIAL | scripture_refs, note_links, backlinks, related notes, note↔person links. |
| 22 | Search | DONE | FTS4 over notes and transcripts (schema 16), Bible passages by reference, tasks; filters; Ask across everything with numbered sources checked against what search found. Semantic search stays the existing on-device hashed embedding for recordings. |
| 23 | Tasks | DONE | Tasks with due, reminder (alarm + notification with Done), repeat, kind, person, source note/recording; "Make it a task" from any note line, checklist and task stay in step; Tasks screen and To-live-out card. |
| 24 | People | DONE | People table, tasks and notes per person. |
| 25 | Export | DONE | PDF/Word/Markdown, private sections excluded by default. |
| 26 | AI images | PARTIAL | Devotional artwork (Gemini). |
| 27–28 | Corpus + scorecard | PARTIAL | Text corpus (4 transcripts) with scorer and scorecard. Audio benchmark: label format, scorer (per-activity precision/recall, confusions), developer row in debug builds that scores recordings pushed to the phone (`testdata/audio/README.md`). **The recordings themselves must be collected by the team with permission; there are none in the repository, so no audio numbers are claimed.** |
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
