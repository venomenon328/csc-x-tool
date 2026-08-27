package de.venomenon.cscxtool.show;

import java.time.Instant;

record MottoShowResponse(
        long id,
        int showNumber,
        String name,
        int candidateCount,
        int contestEntryCount,
        int listenedEntryCount,
        int rankedEntryCount,
        Instant ballotClosedAt,
        SelectedCandidateResponse selectedCandidate
) {

    static MottoShowResponse from(MottoShow show) {
        return new MottoShowResponse(
                show.id(),
                show.showNumber(),
                show.name(),
                show.candidateCount(),
                show.contestEntryCount(),
                show.listenedEntryCount(),
                show.rankedEntryCount(),
                show.ballotClosedAt(),
                show.selectedCandidate() == null ? null : SelectedCandidateResponse.from(show.selectedCandidate())
        );
    }
}
