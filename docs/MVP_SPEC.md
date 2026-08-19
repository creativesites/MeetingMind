# MeetMind — MVP Product Specification

This document is the reference specification the codebase is being reconciled against. It is intentionally scoped tightly — see `docs/FUTURE_BACKEND.md` for everything explicitly deferred.

## 1. Product

MeetMind is a privacy-first, local-AI Android meeting notetaker. Android-only for this MVP.

```
Record / Import Meeting
        ↓
Local Audio Processing
        ↓
Local Speech Recognition
        ↓
Speaker Identification
        ↓
Transcript
        ↓
Local AI Intelligence
        ↓
Summary, Decisions, Action Items, Questions
        ↓
Local Meeting Memory
        ↓
Ask Meeting
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

**UX** — onboarding, home, recording, processing, meeting detail, transcript, AI insights, Ask Meeting, search, settings, model manager, dark mode.

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
