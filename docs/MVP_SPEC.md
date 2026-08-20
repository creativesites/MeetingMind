# MeetingMind — MVP Product Specification

This document is the reference specification the codebase is being reconciled against. It is intentionally scoped tightly — see `docs/FUTURE_BACKEND.md` for everything explicitly deferred.

## 1. Product

MeetingMind is a private, local-AI Android voice capture and understanding app — not a meeting-only
recorder. As of Phase 3A, recordings carry an explicit `RecordingType` (Meeting, Interview,
Lecture, Voice Memo, Idea, Brainstorm, Dictation, Conversation, Research, Journal, Custom,
General), chosen via a "What are you recording?" step before recording starts (with a "Quick
Record" skip). The type only ever narrows what the local Meeting Intelligence engine pays
attention to in its extraction prompt — it never changes what recording, transcription, or
storage do, and it never weakens the grounding requirement (see §2 below and
`docs/AI_ARCHITECTURE.md`). Android-only for this MVP.

```
Record / Import (any RecordingType)
        ↓
Local Audio Processing
        ↓
Local Speech Recognition
        ↓
Speaker Identification
        ↓
Transcript
        ↓
Local AI Intelligence (focus guided by RecordingType)
        ↓
Summary, Decisions, Action Items, Questions
        ↓
Local Memory
        ↓
Ask [Recording] / Share / Export
```

## 2. Core Principle: Local-First, No Backend Dependency

Once required models are downloaded, the following must work with the device fully offline:

1. Record a meeting.
2. Stop the recording.
3. Transcribe it.
4. Generate a summary.
5. Extract action items and decisions.
6. Search the meeting.
7. Ask questions about the meeting.

Firebase is used **only** for authentication and lightweight, non-content metadata (title, timestamps, participant count) — never as the transcription backend, the LLM backend, or the audio-storage backend. Meeting recordings and transcripts are not uploaded to Firebase or any other cloud service by default.

## 3. Explicitly Deferred (Not This MVP)

Google/Outlook Calendar integration, Zoom/Meet/Teams bots, Slack/Notion/Asana/Salesforce integrations, cloud meeting-agent infrastructure, remote AI meeting bots, team collaboration, full cloud transcript storage. The architecture should not block these later (see `MeetingSource.REMOTE_BOT` and `core/future/FutureMeetingInterfaces.kt`), but none of it is implemented now.

## 4. Target Feature Set

**Recording** — local mic recording, pause/resume, stop, foreground service, background/screen-off support, persistent storage.

**Import** — audio import, video import, real audio-track extraction from video.

**Local AI** — VAD, local ASR, speaker diarization (best-effort), local LLM, local embeddings — all behind swappable interfaces (`VoiceActivityDetector`, `SpeechRecognizer`, `SpeakerDiarizer`, `MeetingIntelligenceEngine`, `EmbeddingEngine`).

**Meeting Intelligence** — title, summary, topics, decisions, action items, questions, follow-ups, all derived from the actual transcript, never fabricated.

**Meeting Memory** — Room database, transcript + metadata storage, keyword search, semantic retrieval.

**Ask Meeting** — real retrieval (question → relevant transcript chunks) → local LLM → grounded answer with timestamp/source references. Not just a chat UI over the whole transcript.

**Model Management** — downloadable models, installed-state tracking, model selection, deletion, real progress, integrity verification (checksum), all persisted (survives process death).

**Authentication** — Firebase Auth with real Google Sign-In (Credential Manager), usable but not required for local-only use.

**Playback** — a single app-wide playback session (Media3 `MediaSessionService`) with real Android media-notification/lock-screen/Bluetooth controls; at most one audio stream plays at a time; a persistent mini-player shows when audio is playing and the user has navigated away from that recording's detail screen.

**Sharing** — the standard Android Sharesheet for transcript, summary, action items, and the recording's own audio file — never a hardcoded per-app integration, always an explicit user action.

**Export** — Markdown, CSV, PDF, and Word (.docx) documents for transcript/summary/action items (not every type forced into every format — CSV is tabular-only), written to a location the user picks via the system document picker — a persistent file, distinct from Sharing.

**UX** — onboarding, home, recording (with the Recording Type picker), background processing (WorkManager, survives backgrounding/lock), meeting detail, transcript, AI insights, Ask [Recording], search, settings, AI Engine (capability-first model manager), dark mode.

## 5. Meeting Source Abstraction

```kotlin
enum class MeetingSource {
    LOCAL_RECORDING,
    IMPORTED_AUDIO,
    IMPORTED_VIDEO,
    REMOTE_BOT   // architectural placeholder only — no backend implemented
}
```

## 6. Non-Goals

No microservices, no custom backend, no Kubernetes, no cloud vector database, no Redis, no message queues, no remote AI APIs as the primary path. The stack is: **Android + Firebase (auth/metadata only) + local AI**.
