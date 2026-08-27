package de.venomenon.cscxtool.entry;

public class ContestEntryNotFoundException extends RuntimeException {

    public ContestEntryNotFoundException(long entryId, long showId) {
        super("Contest entry %d was not found in show %d.".formatted(entryId, showId));
    }
}
