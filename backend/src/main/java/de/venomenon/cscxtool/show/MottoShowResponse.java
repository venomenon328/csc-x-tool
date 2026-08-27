package de.venomenon.cscxtool.show;

record MottoShowResponse(
        long id,
        int showNumber,
        String name,
        int candidateCount,
        int contestEntryCount,
        int listenedEntryCount,
        SelectedCandidateResponse selectedCandidate
) {

    static MottoShowResponse from(MottoShow show) {
        return new MottoShowResponse(
                show.id(),
                show.showNumber(),
                show.name(),
                show.candidateCount(),
                show.contestEntryCount(),
                show.listenedEntryCount(),
                show.selectedCandidate() == null ? null : SelectedCandidateResponse.from(show.selectedCandidate())
        );
    }
}
