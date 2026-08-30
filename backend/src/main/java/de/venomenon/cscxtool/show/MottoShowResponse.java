package de.venomenon.cscxtool.show;

import java.time.Instant;

record MottoShowResponse(
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
        Integer officialTotalDifference,
        Integer finalPlace,
        boolean finalPlaceTied,
        SelectedCandidateResponse selectedCandidate
) {

    static MottoShowResponse from(MottoShow show) {
        return new MottoShowResponse(
                show.id(),
                show.contestId(),
                show.showNumber(),
                show.name(),
                show.candidateCount(),
                show.contestEntryCount(),
                show.assessedEntryCount(),
                show.rankedEntryCount(),
                show.assignedEntryCount(),
                show.activeParticipantCount(),
                show.knownActiveResultCount(),
                show.ballotClosedAt(),
                show.resultsClosedAt(),
                show.calculatedTotalPoints(),
                show.officialTotalPoints(),
                show.officialTotalPoints() == null ? null : show.officialTotalPoints() - show.calculatedTotalPoints(),
                show.finalPlace(),
                show.finalPlaceTied(),
                show.selectedCandidate() == null ? null : SelectedCandidateResponse.from(show.selectedCandidate())
        );
    }
}
