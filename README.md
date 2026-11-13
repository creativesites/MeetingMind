# MeetingMind dev build 2257d9e

- File: `MeetingMind-Dev-2257d9e.apk`
- Source: `release/1.0-paid-launch` at `2257d9e`
- SHA-256: `7f5a42f15966c123cd9c13b120ff12f652c4ec5e45eed9d9ec55eae154eb3245`
- Size: 77,282,772 bytes
- Credential scan passed (only the Firebase key).

## What is new
- Fixed Learning vertical crash on launch (`LearnViewModel` `@JvmOverloads constructor` + nullable `navArgument` defaults for `LEARN_PRACTICE` and `LEARN_EXAM`).
- Guarded `WebBlocks` span bounds (`minOf`/`maxOf`) against `IllegalArgumentException`.
- Hardened `LearningAiEngine` JSON parsing (strip markdown code fences, `optString`, concept title/id matching).
- Polished Learning UI & features: `PracticeArenaScreen` completion CTA, empty state, keyed teach-back text, `SpeechRecognizer` + `RECORD_AUDIO` Voice Teach-Back, Mistake Explanation wiring; `ExamPrepScreen` multi-goal list, delete confirmation, date presets + `DatePickerDialog`, scrollable Cram Sheet preview, `MathExpressionView` formula rendering; `LearningSessionScreen` empty/create state and `toggleSessionPause` refresh; `LearnHomeScreen` weak-concept navigation; `ShareStudyPackToCircleSheet` error handling & scrollable circle list.
- Learning, Academic, and `Migration29To30` unit tests: 34/34 passing.

## Not verified
- Not run on a physical Android device yet (`SpeechRecognizer` Voice Teach-Back, `DatePickerDialog`, and Learning navigation flows need device verification).
- Full non-learning unit suite was not run in this build (`ColorLiteralGuardTest` has a pre-existing failure in `OnboardingScreen.kt`).

## Install
Allow installs from this source on your Android device, then open the APK.
