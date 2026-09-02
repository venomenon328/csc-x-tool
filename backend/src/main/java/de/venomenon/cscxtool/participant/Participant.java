package de.venomenon.cscxtool.participant;

import java.time.Instant;
import java.util.List;

record Participant(
        long id,
        String displayName,
        boolean active,
        List<String> aliases,
        int botbSelectionCount,
        Instant createdAt,
        Instant updatedAt
) {
}
