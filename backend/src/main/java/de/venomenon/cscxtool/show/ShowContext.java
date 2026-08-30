package de.venomenon.cscxtool.show;

/** Small authorization context used by historical entry operations. */
public record ShowContext(long showId, long contestId, boolean currentContest, boolean entryListComplete) {
}
