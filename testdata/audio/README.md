# Audio benchmark

Recordings can't be committed here (size, and they are people's worship and prayers). This folder
describes the benchmark; the recordings live on the team's drive with the speakers' permission.

## What to collect

Ten to twenty recordings, five to forty minutes each, from the places the app will really be used:
a Sunday service with a PA and a band, a small-group Bible study around a phone, a sermon recorded
from the back of a hall, a testimony, a prayer meeting, a call-and-response. Include bad ones —
clipping, echo, a fan, children. Get written permission; remove names if you publish any excerpt.

## Labels

Next to `name.m4a`, a `name.labels.json` written by listening:

```json
{ "segments": [
  { "start": "0:00",   "end": "4:10",  "activity": "MUSIC" },
  { "start": "4:10",   "end": "6:00",  "activity": "SPEECH" },
  { "start": "6:00",   "end": "6:25",  "activity": "CROWD" },
  { "start": "6:25",   "end": "41:00", "activity": "SPEECH" } ] }
```
`activity` is what the room sounded like: `SPEECH`, `MUSIC` (singing and instruments), `CROWD`
(applause, congregation answering), `SILENCE`, `MIXED` (speech over music). Times are `m:ss`,
`h:mm:ss` or seconds. Label whole stretches; unlabelled parts aren't scored.

## Running it

Debug builds have Settings → Developer → "Run audio benchmark". Put the audio and label files in
`Android/data/com.craftflowtechnologies.meetingmind.dev/files/benchmark/` (`adb push` works), tap the row, and read
`report.md` written beside them. The report gives overall agreement, precision and recall per
activity, and the most common mistakes. Keep each report with the build number it came from; a
change to the detector is judged against the previous one on the same recordings.

Scene *labels* (sermon, prayer, song title) come from the AI step and are judged by reading, not
by this benchmark. This covers the sound detector that decides where to look.
