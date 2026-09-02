package de.venomenon.cscxtool.participant;

import java.time.Instant;
import java.time.LocalDate;

record BotbSelectionResponse(
        long id,
        long participantId,
        int editionNumber,
        String artist,
        LocalDate knownSince,
        Instant createdAt,
        Instant updatedAt
) {
    static BotbSelectionResponse from(BotbSelection selection) {
        return new BotbSelectionResponse(
                selection.id(), selection.participantId(), selection.editionNumber(), selection.artist(), selection.knownSince(),
                selection.createdAt(), selection.updatedAt()
        );
    }
}
