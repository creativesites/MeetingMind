# Study redesign mockups (approved 2026-11-27)

Source of truth for the Study screens. Each `.dc.html` is one phone screen (390 dp wide) from the
approved canvas "MeetingMind Study redesign". Open them as reference; build with `MM` tokens and
`core/ui/mm` components, never by copying these colours or sizes.

| File | Screen | Compose |
|---|---|---|
| Main.dc.html | Study home | `feature/learn/ui/home` |
| Lesson.dc.html | Lesson page | `feature/learn/ui/lesson` |
| ConceptSheet.dc.html | Concept sheet | `feature/learn/ui/lesson` |
| Listen.dc.html | Listen (lecture replay, read-along) | `feature/learn/ui/listen` |
| Practice.dc.html | Practice: multiple choice | `feature/learn/ui/practice` |
| Recall.dc.html | Practice: recall card | `feature/learn/ui/practice` |
| ExplainAloud.dc.html | Explain it aloud (later release) | not built yet |
| RoundDone.dc.html | Round complete | `feature/learn/ui/practice` |
| LearningHome.dc.html | Learning home: the Learning space and the Learning home style | `feature/learn/ui/home` |
| Course.dc.html | Course page | `feature/learn/ui/course` |
| NewSession.dc.html | Start a study session (sheet) | `feature/learn/ui/home` |
| FormatMatch.dc.html | Practice formats: match, order, fill the gap (all ten formats use this style) | `feature/learn/ui/practice` |

Shapes and states shared by all of them: `feature/learn/LearnContract.kt`. Rules: `docs/mvp/STUDY_AUDIT.md`.

2026-11-27, second pass (founder): Learning's old screens are all replaced by this design; Learning's functionality is kept. One Learning: the `learn_study` routes are retired.
