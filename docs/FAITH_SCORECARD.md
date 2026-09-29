# Faith corpus scorecard

Text-level measures over `testdata/sermons` (4 transcripts).

| Transcript | Scripture found / expected | Missed | Extra | Songs found / expected | Ask citations |
|---|---|---|---|---|---|
| spoken_references | 5/5 | — | — | 0/0 | 1/1 |
| sunday_expository | 4/4 | — | — | 0/1 | 2/2 |
| testimony_no_scripture | 0/0 | — | — | 0/0 | 1/1 |
| worship_night | 1/1 | — | — | 0/2 | 1/1 |

**Scripture references:** precision 100%, recall 100% (10 found, 0 extra, 0 missed).

**Songs from words alone:** 0 of 3 found, 0 wrongly marked. With audio the on-device detector adds the sound of music; that is not measured here (no corpus audio).

**Ask Sermon grounding:** 5 of 5 model answers keep exactly their real citations.
