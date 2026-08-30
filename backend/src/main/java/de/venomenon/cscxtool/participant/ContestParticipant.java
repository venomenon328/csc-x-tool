package de.venomenon.cscxtool.participant;

import java.time.Instant;
import java.util.List;

record ContestParticipant(
        long participationId,
        long participantId,
        String displayName,
        boolean identityActive,
        String countryCode,
        boolean active,
        List<String> aliases,
        Instant createdAt,
        Instant updatedAt
) {
}
