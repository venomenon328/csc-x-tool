package de.venomenon.cscxtool.entry;

import java.util.List;

/** Source text is returned only to the client preview and is never persisted. */
record AssignmentImportPreviewLine(
        int sourcePosition, String sourceText, String artist, String title, String youtubeUrl,
        String participantToken, String countryToken, Long participantId, Long participationId,
        Long entryId, Long previousParticipationId, String action, ImportPreviewStatus status,
        List<ImportWarning> warnings
) { }
