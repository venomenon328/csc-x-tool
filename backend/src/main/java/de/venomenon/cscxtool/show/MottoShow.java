package de.venomenon.cscxtool.show;

import de.venomenon.cscxtool.entry.OwnEntryResolution;
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
        Long ownParticipationId,
        OwnEntryResolution ownEntryResolution,
        Long ownEntryId,
        SelectedCandidate selectedCandidate,
        Instant createdAt,
        Instant updatedAt
) {
}
