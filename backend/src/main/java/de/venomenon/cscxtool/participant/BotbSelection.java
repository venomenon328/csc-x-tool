package de.venomenon.cscxtool.participant;

import java.time.Instant;
import java.time.LocalDate;

record BotbSelection(
        long id,
        long participantId,
        int editionNumber,
        String artist,
        LocalDate knownSince,
        Instant createdAt,
        Instant updatedAt
) {
}
