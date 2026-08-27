package de.venomenon.cscxtool.result;

import java.time.Instant;
import java.util.List;

record ResultResponse(
        long mottoShowId,
        Instant ballotClosedAt,
        Instant resultsClosedAt,
        ResultSubmissionResponse selectedCandidate,
        List<ReceivedScoreLineResponse> lines,
        int calculatedTotalPoints,
        Integer officialTotalPoints,
        Integer officialTotalDifference,
        Integer finalPlace,
        boolean finalPlaceTied
) {
}
