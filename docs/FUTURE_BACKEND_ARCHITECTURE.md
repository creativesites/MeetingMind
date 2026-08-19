# MeetMind — Future Backend & Cloud Architecture

This document describes the planned cloud meeting agent and cross-device sync architecture for subsequent phases of MeetMind.

## 1. Architectural Philosophy
In Phase 1 (MVP), MeetMind is **100% local-first**: all audio recording, ASR transcription, speaker diarization, NLP summarization, action item extraction, embeddings, and vector RAG run on the device.

In future phases, users can optionally opt-in to cloud-assisted meeting bots and workflow sync while preserving on-device privacy controls.

## 2. Remote Meeting Flow

```
+-------------------------------------------------------------+
|                      Android Client                         |
+-------------------------------------------------------------+
                              |
                              | 1. Firebase Auth (Google Sign-In)
                              v
+-------------------------------------------------------------+
|                      Future Backend                         |
|   (Cloud Run / Microservices / Orchestration Engine)        |
+-------------------------------------------------------------+
      |                       |                       |
      v                       v                       v
+--------------+      +---------------+      +----------------+
| Calendar API |      | Meeting Agent |      |  Integrations  |
| - Google Cal |      | - Zoom Bot    |      | - Slack        |
| - Outlook    |      | - Meet Bot    |      | - Notion       |
|              |      | - Teams Bot   |      | - Jira / Asana |
+--------------+      +---------------+      +----------------+
                              |
                              | 2. Raw Stream / Encrypted WebRTC
                              v
+-------------------------------------------------------------+
|                    Cloud AI Pipeline                        |
|   - Silero VAD / Chunking                                   |
|   - Whisper Large-v3 Turbo ASR                              |
|   - PyAnnote Diarization                                    |
|   - LLM Intelligence (Summary, Decisions, Action Items)    |
|   - Dense Embeddings & Hybrid Vector Search                 |
+-------------------------------------------------------------+
                              |
                              | 3. Normalized Meeting Object
                              |    (Meeting, Segments, Summary)
                              v
+-------------------------------------------------------------+
|            Firestore Sync / Android App Ingestion           |
|            (Received as MeetingSource.REMOTE_BOT)           |
+-------------------------------------------------------------+
```

## 3. MeetingSource Polymorphism
The app data model uses `MeetingSource` to seamlessly ingest recordings without altering the UI or downstream storage:
- `LOCAL_RECORDING`: Real-time phone microphone with foreground service
- `IMPORTED_AUDIO`: Storage Access Framework MP3/WAV/M4A/AAC
- `IMPORTED_VIDEO`: Video audio track extraction
- `REMOTE_BOT`: Future cloud bot conference feed

## 4. Privacy & Zero-Knowledge Encryption
- Transcripts and audio files are stored in app-private device directories (`/files/meetings/{id}/`).
- Firebase only retains lightweight user metadata (e.g. account profile, sync timestamps) unless end-to-end encrypted backup is enabled by the user.
