package de.venomenon.cscxtool.participant;

import java.time.Instant;
import java.util.List;

record Participant(
        long id,
        String displayName,
        boolean active,
        List<String> aliases,
        Instant createdAt,
        Instant updatedAt
) {
}
