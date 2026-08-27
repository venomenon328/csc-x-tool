package de.venomenon.cscxtool.result;

record ReceivedScoreLineResponse(
        long participantId,
        String displayName,
        String countryCode,
        String countryName,
        boolean active,
        ReceivedScoreStatus status,
        Integer points,
        boolean persisted
) {
}
