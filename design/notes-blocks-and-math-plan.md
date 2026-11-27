# Notes blocks: readable Markdown, maths and chemistry, and YouTube import up front

Founder request, 2026-11-16. Checked on the S20+ (`.dev` app) by the Learning session.

## What is broken (seen on the phone)

1. **Raw JSON in notes (P0, unreadable).**
   - *Joyce Meyer Teaching on Simple Prayer* (YouTube import, sermon) has a block that starts `{ "sermon_notes": "## Main Theme\nBelievers are urged…`.
   - *Factoring Trinomials Into a Product of Two Binomials* (lecture recording) has the same fault with a different key: `{ "notes": "## Overview\n…" }`.
   - In both, the literal `\n`, `\"` and `##` show as text. The Notes list preview shows the same JSON.
   - Cause: `ai/notes/NotePolishEngine.polish` asks for "clean Markdown only", but the model sometimes wraps the Markdown in a JSON object with an invented key. `stripOuterFence` removes only a ``` fence, so `MarkdownImport.parse` turns the whole JSON string into one paragraph.
2. **Inline maths is not rendered.**
   - Text, callout ("Exam Tip") and Markdown blocks show `$(2x+1)(x+2)$` and `$2x \cdot x = 2x^2$` raw.
   - Equation blocks render well (`feature/notes/editor/AcademicBlocks.kt`: `LatexNode` parser, `parseChemical`, Copy LaTeX), but only as whole blocks.
3. **Citation markers show raw.** `[0]` and `[1]` from the grounded prompt show as text in polished notes.

## The work

### A. Never show raw JSON or escapes (P0)
- **At the source:** in `NotePolishEngine`, unwrap a JSON object reply (any key; join its string values in order) and unescape it before `MarkdownImport.parse`. Add the same guard wherever AI text becomes a `MARKDOWN` or paragraph block, including the YouTube path (`ai/cloud/GeminiYouTubeEngine`) and the sermon writer (`ai/faith/SermonNoteWriter`). Find the shared choke point rather than patching each caller.
- **Repair existing notes at render time:** if a Markdown or paragraph block's text is a JSON object that wraps Markdown, render the unwrapped Markdown. This fixes notes already on phones without a migration. Do the same in the Notes list preview text.
- **Tests:** JVM tests for unwrapping (`sermon_notes`, `notes` and unknown keys, nested escapes, fenced JSON, plain Markdown left alone, real JSON code blocks left alone).

### B. Maths and chemistry anywhere in a note (P1)
- **Inline maths:** `$…$` and `\( … \)` inline, and `$$ … $$` / `\[ … \]` as display maths, in paragraphs, lists, callouts and Markdown blocks. Reuse the `AcademicBlocks` LaTeX parser and renderer, or move to a proven renderer if that one can't cope. Weigh offline KaTeX against a native renderer for size, speed and accessibility, and write the choice down.
- **Chemistry:** `\ce{H2SO4}`, `\ce{2H2 + O2 -> 2H2O}`, charges, states and arrows. `parseChemical` is a start.
- **Fallbacks:**
  - A formula that fails to parse shows readable plain text, never broken LaTeX.
  - Screen readers get a spoken form; `ai/voice/read/smart/AcademicSpokenForm.kt` already has one.
  - Copying a formula gives back its LaTeX.
- **Prompts:** tell note-writing prompts to use `$…$` for maths and `\ce{}` for chemistry, so recordings of maths and science lectures come out renderable.

### C. Citation markers (P2)
- Turn `[n]` in polished notes into tappable chips that play that moment, using the existing `PlaybackController`/`CitationSheet`. If no source can be found, hide the marker.

### C2. YouTube notes: play the video, and credit it (P1, Notes blocks session)
- **Play the video:** "Play on YouTube" on the note's recording card opens the video in the YouTube app or browser through a plain view intent (no embedded player, no download). Timestamped moments open at `&t=<seconds>s`.
- **Credit the video:** a source block at the bottom of the note gives the title, channel or speaker, the full link, the import date, and "Notes written by AI from the video".
  - It survives re-polishing, appears in exports and shares, and is added to existing YouTube notes.
  - It must not use the YouTube logo.

### D. YouTube import, front and centre (P1, separate owner)
- It works well, and the founder calls it a killer feature, but it is hard to find. Make it obvious:
  - A "Paste a YouTube link" entry on Home and in New/Create.
  - In Learning's "New study session" (Import already covers it; name YouTube explicitly).
  - In Faith (sermons).
  - Clipboard detection: a YouTube link on the clipboard offers itself when the app opens.
- Store listing: describe it as "notes from a YouTube video". Don't use the YouTube logo or imply endorsement.

### E. Play Store readiness for AI features (P1, separate owner)
- Google Play's AI-Generated Content policy requires in-app reporting or flagging of offensive AI output. No such control was found in notes, summaries or Ask. Add a "Report this" action on AI-written blocks and answers.

## Who does what
- **A, B, C:** a new Notes rendering session. It does not run Gradle: it sends build and test requests to the Learning session, the one build runner (see `agents/TESTING.md`).
- **D:** the App home screens and feature discoverability session.
- **E:** to be assigned when the founder agrees.
- **Builds, tests and phone checks:** the Learning session, on the S20+ `.dev` app, with screenshots of the two notes above before and after.

## Done when
- Both notes above read cleanly on the phone. No JSON, `\n` or `##` shows anywhere, and the list preview is clean.
- `$…$` maths and `\ce{}` chemistry render inline in every text-bearing block, in light and dark themes. A bad formula falls back to readable text.
- Unit tests cover unwrapping and the inline maths parser, and screenshot tests cover a maths and chemistry note.
