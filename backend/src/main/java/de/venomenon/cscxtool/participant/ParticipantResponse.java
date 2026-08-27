package de.venomenon.cscxtool.participant;

import java.time.Instant;
import java.util.List;

record ParticipantResponse(
        long id,
        String displayName,
        String countryCode,
        String countryName,
        boolean active,
        List<String> aliases,
        Instant createdAt,
        Instant updatedAt
) {
    static ParticipantResponse from(Participant participant, Country country) {
        return new ParticipantResponse(
                participant.id(),
                participant.displayName(),
                participant.countryCode(),
                country.name(),
                participant.active(),
                participant.aliases(),
                participant.createdAt(),
                participant.updatedAt()
        );
    }
}
