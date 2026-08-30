package de.venomenon.cscxtool.entry;

record UpdateContestEntryRequest(
        String artist,
        String title,
        String youtubeUrl,
        String comment,
        Long participantId
) {
}
