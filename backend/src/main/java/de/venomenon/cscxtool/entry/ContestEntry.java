package de.venomenon.cscxtool.entry;

import java.time.Instant;

record ContestEntry(
        long id,
        long mottoShowId,
        long contestId,
        String artist,
        String title,
        String youtubeUrl,
        String comment,
        Integer assessment,
        Integer assessmentConfidence,
        int poolPosition,
        Integer rankingPosition,
        Long contestParticipationId,
        Long participantId,
        boolean ownEntry,
        Instant createdAt,
        Instant updatedAt
) {
}
