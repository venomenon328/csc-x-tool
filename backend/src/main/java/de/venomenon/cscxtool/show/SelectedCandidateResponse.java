package de.venomenon.cscxtool.show;

record SelectedCandidateResponse(long id, String artist, String title, String youtubeUrl) {

    static SelectedCandidateResponse from(SelectedCandidate candidate) {
        return new SelectedCandidateResponse(candidate.id(), candidate.artist(), candidate.title(), candidate.youtubeUrl());
    }
}
