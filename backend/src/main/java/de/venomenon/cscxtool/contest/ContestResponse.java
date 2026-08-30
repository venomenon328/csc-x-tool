package de.venomenon.cscxtool.contest;

import java.time.Instant;

record ContestResponse(
        long id, String name, int displayOrder, boolean current, int participantCount, int showCount,
        Instant createdAt, Instant updatedAt
) {
    static ContestResponse from(Contest contest) {
        return new ContestResponse(
                contest.id(), contest.name(), contest.displayOrder(), contest.current(), contest.participantCount(),
                contest.showCount(), contest.createdAt(), contest.updatedAt()
        );
    }
}
