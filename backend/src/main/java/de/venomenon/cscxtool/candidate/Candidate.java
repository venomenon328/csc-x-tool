package de.venomenon.cscxtool.candidate;

import java.time.Instant;

record Candidate(
        long id,
        long mottoShowId,
        String artist,
        String title,
        String youtubeUrl,
        String comment,
        CandidateStatus status,
        int manualPosition,
        Instant createdAt,
        Instant updatedAt
) {
}
