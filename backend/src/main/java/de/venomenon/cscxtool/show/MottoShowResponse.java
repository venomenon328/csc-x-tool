package de.venomenon.cscxtool.show;

import de.venomenon.cscxtool.entry.OwnEntryResolution;
import java.time.Instant;

record MottoShowResponse(
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
        SelectedCandidateResponse selectedCandidate
) {

    static MottoShowResponse from(MottoShow show) {
        return new MottoShowResponse(
                show.id(),
                show.contestId(),
                show.showNumber(),
                show.name(),
                show.entryListComplete(),
                show.candidateCount(),
                show.contestEntryCount(),
                show.assessedEntryCount(),
                show.rankedEntryCount(),
                show.assignedEntryCount(),
                show.activeParticipantCount(),
                show.publishedBallotVotedCount(),
                show.publishedBallotNotVotedCount(),
                show.publishedBallotUnrecordedCount(),
                show.ballotClosedAt(),
                show.ownParticipationId(),
                show.ownEntryResolution(),
                show.ownEntryId(),
                show.selectedCandidate() == null ? null : SelectedCandidateResponse.from(show.selectedCandidate())
        );
    }
}
