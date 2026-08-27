package de.venomenon.cscxtool.candidate;

import java.time.Instant;

record CandidateResponse(
        long id,
        long mottoShowId,
        String artist,
        String title,
        String youtubeUrl,
        String comment,
        CandidateStatus status,
        int manualPosition,
        Instant createdAt,
        Instant updatedAt
) {

    static CandidateResponse from(Candidate candidate) {
        return new CandidateResponse(
                candidate.id(),
                candidate.mottoShowId(),
                candidate.artist(),
                candidate.title(),
                candidate.youtubeUrl(),
                candidate.comment(),
                candidate.status(),
                candidate.manualPosition(),
                candidate.createdAt(),
                candidate.updatedAt()
        );
    }
}
