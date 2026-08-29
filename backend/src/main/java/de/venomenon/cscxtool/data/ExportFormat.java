package de.venomenon.cscxtool.data;

import java.util.List;

/** Stable, intentionally explicit JSON contract; no Liquibase or runtime state is exported. */
public final class ExportFormat {

    public static final String FORMAT = "csc-x-tool-full-export";
    public static final int VERSION = 2;
    public static final int LEGACY_VERSION = 1;

    private ExportFormat() { }

    public record FullExport(String format, int formatVersion, String exportedAt, String applicationVersion,
                             int schemaVersion, Data data) { }
    public record Data(List<MottoShow> mottoShows, List<Candidate> candidates, List<Participant> participants,
                       List<ParticipantAlias> participantAliases, List<ContestEntry> contestEntries,
                       List<BallotSnapshot> ballotSnapshots, List<BallotSnapshotItem> ballotSnapshotItems,
                       List<ReceivedScore> receivedScores) { }
    public record MottoShow(long id, int showNumber, String name, Long selectedCandidateId, String ballotClosedAt,
                            String resultsClosedAt, Integer finalPlace, boolean finalPlaceTied,
                            Integer officialTotalPoints, String createdAt, String updatedAt) { }
    public record Candidate(long id, long mottoShowId, String artist, String title, String youtubeUrl, String comment,
                            String status, int manualPosition, String createdAt, String updatedAt) { }
    public record Participant(long id, String displayName, String countryCode, boolean active,
                              String createdAt, String updatedAt) { }
    public record ParticipantAlias(long id, long participantId, String alias) { }
    public record ContestEntry(long id, long mottoShowId, String artist, String title, String youtubeUrl,
                               String comment, boolean listened, boolean relisten, int poolPosition, Integer rankingPosition,
                               Long participantId, String createdAt, String updatedAt) { }
    public record BallotSnapshot(long id, long mottoShowId, int snapshotNumber, String createdAt, boolean current) { }
    public record BallotSnapshotItem(long id, long ballotSnapshotId, int rank, Long contestEntryId,
                                     String artistSnapshot, String titleSnapshot, String youtubeUrlSnapshot) { }
    public record ReceivedScore(long id, long mottoShowId, long participantId, String status, Integer points,
                                String createdAt, String updatedAt) { }

    /** The explicit v1 input contract is retained solely to upgrade existing exports on import. */
    public record FullExportV1(String format, int formatVersion, String exportedAt, String applicationVersion,
                               int schemaVersion, DataV1 data) { }
    public record DataV1(List<MottoShow> mottoShows, List<Candidate> candidates, List<Participant> participants,
                         List<ParticipantAlias> participantAliases, List<ContestEntryV1> contestEntries,
                         List<BallotSnapshot> ballotSnapshots, List<BallotSnapshotItem> ballotSnapshotItems,
                         List<ReceivedScore> receivedScores) { }
    public record ContestEntryV1(long id, long mottoShowId, String artist, String title, String youtubeUrl,
                                 String comment, boolean listened, boolean relisten, Integer rankingPosition,
                                 Long participantId, String createdAt, String updatedAt) { }
}
