package de.venomenon.cscxtool.contest;

import java.time.Instant;

public record Contest(
        long id,
        String name,
        int displayOrder,
        boolean current,
        int participantCount,
        int showCount,
        Long ownParticipationId,
        Instant createdAt,
        Instant updatedAt
) {
}
