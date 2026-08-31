package de.venomenon.cscxtool.entry;

/**
 * Resolves only the current user's entry. A null entry is valid solely for the
 * explicit NO_OWN_ENTRY state; it never infers that state from missing data.
 */
record UpdateOwnEntryResolutionRequest(
        OwnEntryResolution resolution,
        Long entryId,
        Boolean confirmRankingRemoval
) {
    boolean confirmsRankingRemoval() {
        return Boolean.TRUE.equals(confirmRankingRemoval);
    }
}
