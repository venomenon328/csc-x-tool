package de.venomenon.cscxtool.participant;

import java.time.Instant;
import java.util.List;

record ContestParticipantResponse(
        long participationId,
        long id,
        String displayName,
        String countryCode,
        String countryName,
        boolean active,
        boolean identityActive,
        List<String> aliases,
        int botbSelectionCount,
        Instant createdAt,
        Instant updatedAt
) {
    static ContestParticipantResponse from(ContestParticipant participant, Country country) {
        return new ContestParticipantResponse(
                participant.participationId(), participant.participantId(), participant.displayName(), participant.countryCode(),
                country.name(), participant.active(), participant.identityActive(), participant.aliases(), participant.botbSelectionCount(),
                participant.createdAt(), participant.updatedAt()
        );
    }
}
