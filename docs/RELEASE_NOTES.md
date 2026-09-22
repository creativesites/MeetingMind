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
