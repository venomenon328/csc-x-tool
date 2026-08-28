package de.venomenon.cscxtool.search;

record SearchResult(
        String type,
        long id,
        long showId,
        int showNumber,
        String showName,
        String artist,
        String title
) {
}
