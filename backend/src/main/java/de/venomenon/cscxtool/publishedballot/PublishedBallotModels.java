package de.venomenon.cscxtool.publishedballot;

import java.time.Instant;
import java.util.List;

record PublishedBallot(
        long id, long mottoShowId, long contestId, long participationId, PublishedBallotStatus status,
        Instant createdAt, Instant updatedAt
) { }

record PublishedBallotParticipant(
        long participationId, long participantId, String displayName, String countryCode, String countryName, List<String> aliases
) { }

record PublishedBallotEntry(
        long id, long mottoShowId, String artist, String title, String youtubeUrl,
        Long submitterParticipationId, Long submitterParticipantId, String submitterDisplayName, String submitterCountryCode
) { }

record PublishedBallotPosition(long ballotId, long entryId, int rank) { }

record PublishedBallotParticipantState(
        long participationId, long participantId, String displayName, String countryCode, String countryName,
        PublishedBallotStatus status, boolean ballotExists, Instant updatedAt
) { }

record PublishedBallotOverviewResponse(
        long mottoShowId, boolean entryListReady, int votedCount, int notVotedCount, int unrecordedCount,
        List<PublishedBallotParticipantState> participants
) { }

record PublishedBallotPositionResponse(
        int rank, int points, long entryId, String artist, String title, String youtubeUrl,
        Long submitterParticipantId, String submitterDisplayName, String submitterCountryCode
) { }

record PublishedBallotDerivedEntryResponse(
        long entryId, String artist, String title, String youtubeUrl, Long submitterParticipantId,
        String submitterDisplayName, String submitterCountryCode, String state, Integer rank, Integer points
) { }

record PublishedBallotDetailResponse(
        long mottoShowId, long participationId, long participantId, String displayName, String countryCode,
        PublishedBallotStatus status, boolean ballotExists, List<PublishedBallotPositionResponse> positions,
        List<PublishedBallotDerivedEntryResponse> entries
) { }

/** Read-only snapshot derived from the currently stored published ballots. */
record PublishedBallotStandingsResponse(
        long mottoShowId, int votedCount, int notVotedCount, int unrecordedCount,
        List<PublishedBallotStandingEntryResponse> entries
) { }

record PublishedBallotStandingEntryResponse(
        int interimRank, long entryId, String artist, String title, String youtubeUrl,
        Long submitterParticipantId, String submitterDisplayName, String submitterCountryCode, String submitterCountryName,
        int points, int mentions
) { }

record BallotImportWarning(String code, String message) { }

record PublishedBallotPreviewPosition(
        int sourcePosition, int rank, String sourceText, Long entryId, String artist, String title,
        Long submitterParticipantId, String submitterDisplayName, List<BallotImportWarning> warnings
) { }

record PublishedBallotPreviewBlock(
        int sourcePosition, Long participationId, Long participantId, String displayName, String countryCode,
        boolean existingBallot, String status, List<PublishedBallotPreviewPosition> positions,
        List<BallotImportWarning> warnings
) { }
