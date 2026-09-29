# Sermon corpus

Reference transcripts for measuring the Faith features without audio: what the on-device detectors
find, and whether Ask Sermon answers stay grounded. Each `*.json` is a hand-written transcript in a
different speaking style, with what a careful listener would mark.

```
{ "id", "title", "type",
  "segments": [ {"id", "startMs", "endMs", "speaker", "text"} ],
  "expected": {
    "scriptures": ["JHN 15:5", ...],        // references a listener would list (USFM book + chapter:verse)
    "songSegments": ["s3", ...],            // segments that are congregational singing
    "asks": [ {"question", "answer", "cites": ["s2"]} ]   // a model answer and the segments it should end up citing
  } }
```

`app/src/test/.../FaithCorpusScorecardTest` scores every file and writes
`app/build/reports/faith-scorecard.md`. Real recordings can't be committed (size, and they are
people's worship); to add one, transcribe it, remove names, and write the `expected` block by hand.
Audio-level measures (scene boundaries on real recordings) need those recordings and are not
covered here.
