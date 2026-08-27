package de.venomenon.cscxtool.entry;

import java.time.Instant;

record ContestEntryResponse(
        long id,
        long mottoShowId,
        String artist,
        String title,
        String youtubeUrl,
        String comment,
        boolean listened,
        boolean relisten,
        Integer rankingPosition,
        Long participantId,
        Instant createdAt,
        Instant updatedAt
) {

    static ContestEntryResponse from(ContestEntry entry) {
        return new ContestEntryResponse(
                entry.id(),
                entry.mottoShowId(),
                entry.artist(),
                entry.title(),
                entry.youtubeUrl(),
                entry.comment(),
                entry.listened(),
                entry.relisten(),
                entry.rankingPosition(),
                entry.participantId(),
                entry.createdAt(),
                entry.updatedAt()
        );
    }
}
