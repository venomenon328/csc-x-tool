package de.venomenon.cscxtool.entry;

import java.util.List;

record HistoricalImportParticipant(
        long participationId, long participantId, String displayName, String countryCode, List<String> aliases
) {
}
