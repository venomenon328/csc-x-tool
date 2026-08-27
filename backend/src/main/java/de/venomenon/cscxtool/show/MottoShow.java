package de.venomenon.cscxtool.show;

import java.time.Instant;

record MottoShow(
        long id,
        int showNumber,
        String name,
        int candidateCount,
        int contestEntryCount,
        int listenedEntryCount,
        int rankedEntryCount,
        Instant ballotClosedAt,
        SelectedCandidate selectedCandidate,
        Instant createdAt,
        Instant updatedAt
) {
}
