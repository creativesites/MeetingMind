package com.example.core.common

import com.example.core.model.TranscriptSegment

/**
 * The transcript segment currently playing at [positionMs] — the last segment whose start time is
 * at or before the current playback position ("Spotify lyrics"-style: whichever line we've most
 * recently reached). Segments are assumed sorted by [TranscriptSegment.startMs] ascending, which
 * is how they're always read from Room ([com.example.core.repository.TranscriptRepository]).
 *
 * Null before playback reaches the first segment, or when there are no segments at all — never a
 * guessed "closest" segment, since guessing which line is "current" before speech has started
 * would misrepresent what's actually playing.
 */
fun findActiveTranscriptSegment(segments: List<TranscriptSegment>, positionMs: Long): TranscriptSegment? =
    segments.lastOrNull { it.startMs <= positionMs }
