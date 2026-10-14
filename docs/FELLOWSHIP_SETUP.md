# Fellowship Circles — Firebase & Security Configuration

This document covers the end-to-end encrypted (E2EE) architecture and console setup for Private Circles (Tier 2).

---

## 1. Zero-Knowledge Relay Architecture

MeetingMind Private Circles use an append-only event log stored in Cloud Firestore:

- **Path:** `circles/{circleId}/events/{eventId}`
- **Server-side Document Fields:**
  - `uid` (Firebase Anonymous Auth UID of the author)
  - `ts` (Server timestamp)
  - `iv` (Initialization vector / mode indicator)
  - `ciphertext` (AES-256-GCM encrypted event JSON)

### Cryptographic Guarantees
1. **Server Blindness:** Google Cloud / Firestore only ever sees encrypted blobs. Member names, prayer requests, testimonies, and study notes are never visible to the server or database administrators.
2. **Key Distribution:** The 256-bit AES symmetric key is shared exclusively via the out-of-band invite link (`mindcircle://join?id=...&name=...&key=...`). The server never receives or stores the encryption key.
3. **Integrity:** AES-256-GCM provides authenticated encryption with a 128-bit authentication tag. Any tampering with ciphertexts is immediately rejected by client decryption.
4. **Idempotency:** State is replayed deterministically into Room. Prayer taps and amens are keyed by distinct member UIDs; counts cannot be artificially inflated.

---

## 2. Firebase Console Checklist

1. **Authentication:**
   - In Firebase Console, go to **Authentication** -> **Sign-in method**.
   - Ensure **Anonymous** provider is enabled.
2. **Cloud Firestore:**
   - In Firebase Console, go to **Firestore Database**.
   - Ensure the database is created in Native mode.
3. **Security Rules:**
   - In Firebase Console -> **Firestore Database** -> **Rules**.
   - Deploy the rules defined in `firestore.rules` (also deployable via Firebase CLI: `firebase deploy --only firestore:rules`).
4. **Configuration File:**
   - Ensure `app/google-services.json` contains configuration entries for both:
     - `com.craftflowtechnologies.meetingmind` (Release)
     - `com.craftflowtechnologies.meetingmind.dev` (Debug)
