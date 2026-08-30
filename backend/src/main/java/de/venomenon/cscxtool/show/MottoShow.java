package de.venomenon.cscxtool.show;

import java.time.Instant;

record MottoShow(
        long id,
        long contestId,
        int showNumber,
        String name,
        boolean entryListComplete,
        int candidateCount,
        int contestEntryCount,
        int assessedEntryCount,
        int rankedEntryCount,
        int assignedEntryCount,
        int activeParticipantCount,
        int publishedBallotVotedCount,
        int publishedBallotNotVotedCount,
        int publishedBallotUnrecordedCount,
        Instant ballotClosedAt,
        SelectedCandidate selectedCandidate,
        Instant createdAt,
        Instant updatedAt
) {
}
