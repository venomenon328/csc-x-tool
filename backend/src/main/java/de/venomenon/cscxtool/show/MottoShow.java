package de.venomenon.cscxtool.show;

import java.time.Instant;

record MottoShow(
        long id,
        long contestId,
        int showNumber,
        String name,
        int candidateCount,
        int contestEntryCount,
        int assessedEntryCount,
        int rankedEntryCount,
        int assignedEntryCount,
        int activeParticipantCount,
        int knownActiveResultCount,
        Instant ballotClosedAt,
        Instant resultsClosedAt,
        int calculatedTotalPoints,
        Integer officialTotalPoints,
        Integer finalPlace,
        boolean finalPlaceTied,
        SelectedCandidate selectedCandidate,
        Instant createdAt,
        Instant updatedAt
) {
}
