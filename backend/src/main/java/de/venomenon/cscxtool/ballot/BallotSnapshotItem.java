package de.venomenon.cscxtool.ballot;

record BallotSnapshotItem(
        int rank,
        Long contestEntryId,
        String artist,
        String title,
        String youtubeUrl,
        String participantCountryCode
) {
}
