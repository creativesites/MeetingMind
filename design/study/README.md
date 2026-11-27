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

Shapes and states shared by all of them: `feature/learn/LearnContract.kt`. Rules: `docs/mvp/STUDY_AUDIT.md`.
