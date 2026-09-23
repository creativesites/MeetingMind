# MeetingMind v19 — Notes

Debug-signed, **arm64-v8a only** (Galaxy S20 and similar). Installs over v18 and keeps everything:
the database upgrades in place, and every recording you already have gets a note of its own.

## What's new

**Notes are the heart of the app now.** The bar reads Home · Notes · **＋ New** · Search · Settings.
New opens a sheet with Record first, then Write a note, Photos & video, and Import. AI Engine moved
into Settings.

**A real editor.**
- **Inline styles**: bold, italic, underline, strikethrough, highlight, code and links.
- **Block types**: headings H1–H3, bulleted, numbered and checklist items (nest them with indent),
  quotes and dividers.
- **Markdown-style shortcuts**: typing `- `, `1. `, `[] `, `# ` or `> ` at the start of a line
  turns it into that block.
- **Enter and Backspace** behave like a word processor: split and join lines, end a list on an
  empty item.
- **Reordering and block actions**: drag a block by its handle (long-press) to move it. Tap the
  handle to turn it into another type, move, duplicate or delete it.
- **Undo/redo, autosave, word count.** A note you open and leave empty is thrown away.

**Everything can go in a note.**
- Photos and videos, from the gallery or the camera. They are copied into the note, so deleting
  them from the gallery doesn't break it.
- Audio clips, which play in place.
- **Record here**: a new recording joins this note.
- **Quote the recording**: pick a line from the transcript, and tapping it jumps to that moment.
- **Links to other notes**, with a "Linked from" list at the bottom of the target note.
- **Tags, pinning, privacy and archive.** Private notes hide their text in the list.

**Recordings and notes point at each other.** A recording's page has a **Note** button, and a
note's recording card plays the audio or opens the transcript.

**Notes library.** Filter by space (Work, Learning, Faith, Personal), notebook or tag. Search,
sort, and colour-coded notebooks with counts. Long-press anything for its options.

**Export & share** (share icon in a note):
- **PDF** is paginated, with page numbers.
- **Word** uses real headings and lists, working links and embedded photos.
- **Markdown** is also available.
- **Save to device** or **Share** straight to WhatsApp, email and so on.
- Private sections stay out unless you switch them on.

**Fixed.** In v18, Internet mode could quietly run on the on-device model even with a Gemini key
entered. Every AI stage now uses Gemini when Internet mode is on: transcription, cleanup, speaker
matching, summaries, Ask and the AI tools. If Gemini fails, the on-device model takes over rather
than leaving you with no summary.

## Try this

1. Tap **＋ New → Write a note**, type a title, then try `- ` and `[] ` shortcuts.
2. Select a word and tap **B**; tap **B** with nothing selected and keep typing.
3. **Insert → Take photo**, then long-press a block's handle and drag it.
4. Open an old recording → **Note** → add your own thoughts above the recording card.
5. Share icon → **Word** → Share, and open it in Word or Google Docs.

## Not in this build yet

The sermon workflow, live Bible verses, the Faith Notebook, calendar and note AI are the next
milestones (docs/PLAN_V1.md §10).

---

# MeetingMind v18

Debug-signed build for device testing, **arm64-v8a only** — which is what the Galaxy S20 family
and every other Android phone from roughly the last decade runs. It will not install on an x86
emulator; an arm64 emulator is fine.

You will need "install from unknown sources" enabled for whatever app you download it with.

## What to try

**Transcription.** The whole pipeline below the UI was rebuilt around words and timestamps rather
than VAD segments. The thing to look for: a sentence broken by a pause should now come out as one
paragraph, and one person talking should stay one speaker. Record something with two people
interrupting each other — that is where the old pipeline fell apart.

**AI tools** (transcript → ⋯). All 19 entries run now. Selecting text in the transcript also gives
you Fix errors / Clarity / Condense on just that paragraph. Nothing applies itself: you get a
proposal with the changes underlined, and you accept or discard it.

**Internet mode.** Settings → Privacy → turn *On-device only* off, then paste your own Gemini API
key. The key is stored on your phone and nowhere else — it is not in this APK, the source, or the
build. With it on, transcription runs through Gemini's verbatim and smart passes and the AI tools
reason with Gemini instead of the on-device model. Turning it back on makes the app fully offline
again; there is no path from offline mode to the network at all.

**Home and navigation.** Home is rebuilt against the `#5a` frame, and the navigation bar is now a
floating pill with Record in it.

## Known limitations

- **None of the transcription quality claims are measured yet.** They come from unit tests over
  synthetic data, not from word-error-rate on real recordings. This build is how that gets found
  out. If a transcript is worse than before, that is worth knowing and is the point of shipping it.
- Gemini Live (real-time voice) is not wired up — the state machine exists, the transport does not.
- The benchmark suite computes its metrics but has no reference recordings to run on.
- Several screens (Import, AI engine, the recording flow) have not yet been brought onto the
  redesign; Home, Search and Settings have.

See `docs/TRANSCRIPTION_OVERHAUL.md` for the full picture.
