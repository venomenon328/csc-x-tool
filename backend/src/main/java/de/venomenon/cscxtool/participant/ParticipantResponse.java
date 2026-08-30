package de.venomenon.cscxtool.participant;

import java.time.Instant;
import java.util.List;

record ParticipantResponse(
        long id,
        String displayName,
        boolean active,
        List<String> aliases,
        Instant createdAt,
        Instant updatedAt
) {
    static ParticipantResponse from(Participant participant) {
        return new ParticipantResponse(
                participant.id(),
                participant.displayName(),
                participant.active(),
                participant.aliases(),
                participant.createdAt(),
                participant.updatedAt()
        );
    }
}
