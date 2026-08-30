package de.venomenon.cscxtool.data;

import java.util.List;

/** Stable, intentionally explicit JSON contract; no Liquibase or runtime state is exported. */
public final class ExportFormat {

    public static final String FORMAT = "csc-x-tool-full-export";
    public static final int VERSION = 4;
    public static final int LEGACY_VERSION = 1;
    public static final int VERSION_2 = 2;
    public static final int VERSION_3 = 3;

    private ExportFormat() { }

    public record FullExport(String format, int formatVersion, String exportedAt, String applicationVersion,
                             int schemaVersion, Data data) { }
    public record Data(List<Contest> contests, List<MottoShow> mottoShows, List<Candidate> candidates,
                       List<Participant> participants, List<ContestParticipation> contestParticipations,
                       List<ParticipantAlias> participantAliases, List<ContestEntry> contestEntries,
                       List<BallotSnapshot> ballotSnapshots, List<BallotSnapshotItem> ballotSnapshotItems,
                       List<ReceivedScore> receivedScores) {
        /** Source-compatible helper for callers that still build an already-upgraded single-contest export. */
        public Data(List<MottoShow> mottoShows, List<Candidate> candidates, List<Participant> participants,
                    List<ParticipantAlias> participantAliases, List<ContestEntry> contestEntries,
                    List<BallotSnapshot> ballotSnapshots, List<BallotSnapshotItem> ballotSnapshotItems,
                    List<ReceivedScore> receivedScores) {
            this(List.of(new Contest(1, "CSC X", 1, true, "1970-01-01T00:00:00Z", "1970-01-01T00:00:00Z")),
                    mottoShows, candidates, participants, List.of(), participantAliases, contestEntries,
                    ballotSnapshots, ballotSnapshotItems, receivedScores);
        }
    }
    public record Contest(long id, String name, int displayOrder, boolean current, String createdAt, String updatedAt) { }
    public record MottoShow(long id, long contestId, int showNumber, String name, Long selectedCandidateId, String ballotClosedAt,
                            String resultsClosedAt, Integer finalPlace, boolean finalPlaceTied,
                            Integer officialTotalPoints, String createdAt, String updatedAt) { }
    public record Candidate(long id, long mottoShowId, String artist, String title, String youtubeUrl, String comment,
                            String status, int manualPosition, String createdAt, String updatedAt) { }
    public record Participant(long id, String displayName, boolean active, String createdAt, String updatedAt) {
        /** Legacy constructor: country codes belong to the upgraded contest participation and are ignored here. */
        public Participant(long id, String displayName, String ignoredCountryCode, boolean active, String createdAt, String updatedAt) {
            this(id, displayName, active, createdAt, updatedAt);
        }
    }
    public record ContestParticipation(long id, long contestId, long participantId, String countryCode, boolean active,
                                       String createdAt, String updatedAt) { }
    public record ParticipantAlias(long id, long participantId, String alias) { }
    public record ContestEntry(long id, long mottoShowId, long contestId, String artist, String title, String youtubeUrl,
                               String comment, Integer assessment, Integer assessmentConfidence, int poolPosition,
                               Integer rankingPosition, Long contestParticipationId, String createdAt, String updatedAt) {
        public ContestEntry(long id, long mottoShowId, String artist, String title, String youtubeUrl, String comment,
                            Integer assessment, Integer assessmentConfidence, int poolPosition, Integer rankingPosition,
                            Long participantId, String createdAt, String updatedAt) {
            this(id, mottoShowId, 1, artist, title, youtubeUrl, comment, assessment, assessmentConfidence,
                    poolPosition, rankingPosition, participantId, createdAt, updatedAt);
        }
        /** Legacy convenience only; IDs happened to match in the v1-v3 upgrade path. */
        public Long participantId() { return contestParticipationId; }
    }
    public record BallotSnapshot(long id, long mottoShowId, int snapshotNumber, String createdAt, boolean current) { }
    public record BallotSnapshotItem(long id, long ballotSnapshotId, int rank, Long contestEntryId,
                                     String artistSnapshot, String titleSnapshot, String youtubeUrlSnapshot) { }
    public record ReceivedScore(long id, long mottoShowId, long contestId, long contestParticipationId, String status,
                                Integer points, String createdAt, String updatedAt) { }

    /** The explicit v1 input contract is retained solely to upgrade existing exports on import. */
    public record FullExportV1(String format, int formatVersion, String exportedAt, String applicationVersion,
                               int schemaVersion, DataV1 data) { }
    public record DataV1(List<MottoShowV3> mottoShows, List<Candidate> candidates, List<ParticipantV3> participants,
                         List<ParticipantAlias> participantAliases, List<ContestEntryV1> contestEntries,
                         List<BallotSnapshot> ballotSnapshots, List<BallotSnapshotItem> ballotSnapshotItems,
                         List<ReceivedScoreV3> receivedScores) { }
    public record ContestEntryV1(long id, long mottoShowId, String artist, String title, String youtubeUrl,
                                 String comment, boolean listened, boolean relisten, Integer rankingPosition,
                                 Long participantId, String createdAt, String updatedAt) { }

    /** The explicit v2 input contract is retained solely to upgrade existing exports on import. */
    public record FullExportV2(String format, int formatVersion, String exportedAt, String applicationVersion,
                               int schemaVersion, DataV2 data) { }
    public record DataV2(List<MottoShowV3> mottoShows, List<Candidate> candidates, List<ParticipantV3> participants,
                         List<ParticipantAlias> participantAliases, List<ContestEntryV2> contestEntries,
                         List<BallotSnapshot> ballotSnapshots, List<BallotSnapshotItem> ballotSnapshotItems,
                         List<ReceivedScoreV3> receivedScores) { }
    public record ContestEntryV2(long id, long mottoShowId, String artist, String title, String youtubeUrl,
                                 String comment, boolean listened, boolean relisten, int poolPosition, Integer rankingPosition,
                                 Long participantId, String createdAt, String updatedAt) { }

    /** The schema-9 JSON contract, retained only as deterministic P9 import input. */
    public record FullExportV3(String format, int formatVersion, String exportedAt, String applicationVersion,
                               int schemaVersion, DataV3 data) { }
    public record DataV3(List<MottoShowV3> mottoShows, List<Candidate> candidates, List<ParticipantV3> participants,
                         List<ParticipantAlias> participantAliases, List<ContestEntryV3> contestEntries,
                         List<BallotSnapshot> ballotSnapshots, List<BallotSnapshotItem> ballotSnapshotItems,
                         List<ReceivedScoreV3> receivedScores) { }
    public record MottoShowV3(long id, int showNumber, String name, Long selectedCandidateId, String ballotClosedAt,
                              String resultsClosedAt, Integer finalPlace, boolean finalPlaceTied,
                              Integer officialTotalPoints, String createdAt, String updatedAt) { }
    public record ParticipantV3(long id, String displayName, String countryCode, boolean active,
                                String createdAt, String updatedAt) { }
    public record ContestEntryV3(long id, long mottoShowId, String artist, String title, String youtubeUrl,
                                 String comment, Integer assessment, Integer assessmentConfidence, int poolPosition,
                                 Integer rankingPosition, Long participantId, String createdAt, String updatedAt) { }
    public record ReceivedScoreV3(long id, long mottoShowId, long participantId, String status, Integer points,
                                  String createdAt, String updatedAt) { }
}
