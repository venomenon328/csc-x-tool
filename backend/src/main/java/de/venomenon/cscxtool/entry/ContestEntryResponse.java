package de.venomenon.cscxtool.entry;

import java.time.Instant;

record ContestEntryResponse(
        long id,
        long mottoShowId,
        long contestId,
        String artist,
        String title,
        String youtubeUrl,
        String comment,
        Integer assessment,
        Integer assessmentConfidence,
        int poolPosition,
        Integer rankingPosition,
        Long contestParticipationId,
        Long participantId,
        Instant createdAt,
        Instant updatedAt
) {

    static ContestEntryResponse from(ContestEntry entry) {
        return new ContestEntryResponse(
                entry.id(),
                entry.mottoShowId(),
                entry.contestId(),
                entry.artist(),
                entry.title(),
                entry.youtubeUrl(),
                entry.comment(),
                entry.assessment(),
                entry.assessmentConfidence(),
                entry.poolPosition(),
                entry.rankingPosition(),
                entry.contestParticipationId(),
                entry.participantId(),
                entry.createdAt(),
                entry.updatedAt()
        );
    }
}
