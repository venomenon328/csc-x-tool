package de.venomenon.cscxtool.shared;

/**
 * Canonical readiness rule for using the complete song list as an input to published-ballot and derived-result flows.
 * Historical shows become ready through their explicit completion flag. For the current contest, readiness is reached
 * once the revealed entry list exists and every entry has an unambiguous contest-participation assignment. Reopening
 * the user's own outgoing Top 15 must not revoke that already established song-list completeness.
 */
public final class EntryListReadiness {

    private EntryListReadiness() { }

    public static boolean isReady(boolean explicitlyComplete, boolean currentContest, boolean hasEntries, boolean allEntriesAssigned) {
        return explicitlyComplete || (currentContest && hasEntries && allEntriesAssigned);
    }
}
