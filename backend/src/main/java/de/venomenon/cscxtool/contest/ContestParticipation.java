package de.venomenon.cscxtool.contest;

import java.time.Instant;

public record ContestParticipation(
        long id,
        long contestId,
        long participantId,
        String countryCode,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
