package de.venomenon.cscxtool.entry;

record HistoricalImportEntryRequest(
        String artist,
        String title,
        String youtubeUrl,
        String comment,
        Long participantId,
        Long replaceEntryId
) {
}
