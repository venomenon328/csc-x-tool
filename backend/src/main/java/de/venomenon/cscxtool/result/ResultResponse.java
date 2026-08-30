package de.venomenon.cscxtool.result;

import java.util.List;

/** Read model only: all current result values are derived from assigned entries and published ballots. */
record ResultResponse(
        long mottoShowId,
        String prerequisite,
        OwnParticipationResponse ownParticipation,
        OwnEntryResponse ownEntry,
        boolean selectedCandidateDiffers,
        int votedCount,
        int notVotedCount,
        int unrecordedCount,
        int derivedTotalPoints,
        List<DerivedResultLineResponse> lines
) { }

record OwnParticipationResponse(long participationId, long participantId, String displayName, String countryCode) { }

record OwnEntryResponse(long entryId, String artist, String title, String youtubeUrl) { }

record DerivedResultLineResponse(
        long participationId,
        long participantId,
        String displayName,
        String countryCode,
        String countryName,
        String ballotStatus,
        String state,
        Integer rank,
        Integer points
) { }
