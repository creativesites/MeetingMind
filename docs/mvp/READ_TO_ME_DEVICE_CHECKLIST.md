# Read to Me: checks that need a real phone

Everything below was **not** tested by the developer assistant (no device in its environment). Unit tests cover the logic; these cover what only a phone can show. Use `adb logcat -s ReadToMe` while testing; it logs states, timings and error kinds (never note text or the key).

## Gemini
1. Internet mode + key set. Open a short note (3 paragraphs) → overflow → Read to me → Listen. Expect: first audio within ~10–20 s, "Preparing 2 of N" advancing, playback continues part to part with no gap longer than a second or two.
2. Long note (20+ paragraphs): first audio should still start quickly (first part is ~450 characters); the mini player shows "Preparing n more…".
3. Pull the network mid-way: the sheet shows the failure with Try again and "Finish with my phone's voice"; nothing switches by itself.
4. Wrong key: failure says to check the key. Airplane mode + Internet mode: failure, not a hang.
5. Slow response: after ~25 s on the first part the sheet offers "Use my phone's voice".
6. logcat: `gemini speak model=… ms=… -> ok|KIND http=…`. If you see `NOT_FOUND` the model id is wrong; `BAD_REQUEST` the request shape; send the lines.

## Phone voice
7. Offline mode → Read to me → phone voice, no network used. Try a language with missing voice data: "Install voice data" appears.
8. Private note, confidential notebook, Work-offline note: phone voice only; lock-screen title reads "Read to Me".

## Playback
9. Background the app, lock the screen: audio continues. Lock-screen and Bluetooth/headset play-pause, next/previous work.
10. Unplug headphones: pauses. Incoming call / another media app: pauses and resumes.
11. Swipe the app away mid-reading, reopen: "Continue where I left off" resumes near the same place.
12. Edit one paragraph, listen again: only that part is made again.

## Bible Study
13. Open a Bible study note with scripture: scripture is read word for word with its reference and translation name; commentary, questions, prayer and "your note" are labelled.
14. Reflection question with "Until I resume": playback pauses after the question and carries on when you press play.
15. Verse sheet → Listen, and Study workspace chapter → Listen headphone icon.
16. Smart Read: after a reading, "See what changed" lists reworded text and what was read as written. Scripture and your own prayers must never appear as reworded.
17. Licensed translation (e.g. NIV): read with the phone voice and the sheet says why.

## Settings
18. Read to Me settings (bottom of the sheet): learning switch, learned defaults and reset, saved audio size and Clear. Pick the same style for the same kind of note three times: "…will now start with X when set to Automatic. Undo."
