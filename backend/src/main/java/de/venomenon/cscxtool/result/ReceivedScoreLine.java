package de.venomenon.cscxtool.result;

record ReceivedScoreLine(
        long participantId,
        String displayName,
        String countryCode,
        boolean active,
        ReceivedScoreStatus status,
        Integer points,
        boolean persisted
) {
}
