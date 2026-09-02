package de.venomenon.cscxtool.data;

public record RestoreDataCounts(
        int mottoShows, int candidates, int participants, int contestEntries,
        int botbSelections, int ballotSnapshots, int legacyReceivedScores
) {
}
