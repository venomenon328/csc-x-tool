package de.venomenon.cscxtool.entry;

import java.time.Instant;

record ContestEntry(
        long id,
        long mottoShowId,
        String artist,
        String title,
        String youtubeUrl,
        String comment,
        boolean listened,
        boolean relisten,
        Integer rankingPosition,
        Long participantId,
        Instant createdAt,
        Instant updatedAt
) {
}
