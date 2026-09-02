package de.venomenon.cscxtool.data;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.cscxtool.system.ApplicationStorage;
import de.venomenon.cscxtool.system.SqliteDataSourceFactory;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

class AnalysisExportServiceIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    private ApplicationStorage storage;
    private JdbcTemplate jdbc;
    private AnalysisExportService exports;

    @BeforeEach
    void setUp() throws Exception {
        Path root = temporaryDirectory.resolve("storage");
        storage = new ApplicationStorage(root, root.resolve("data"), root.resolve("data/csc-x-tool.db"),
                root.resolve("backups/automatic"), root.resolve("backups/manual"), root.resolve("exports"),
                root.resolve("logs"), root.resolve("runtime"));
        Files.createDirectories(storage.dataDirectory());
        Files.createDirectories(storage.automaticBackupsDirectory());
        Files.createDirectories(storage.manualBackupsDirectory());
        Files.createDirectories(storage.exportsDirectory());
        Files.createDirectories(storage.logsDirectory());
        Files.createDirectories(storage.runtimeDirectory());
        DataSource dataSource = SqliteDataSourceFactory.create(storage.databaseFile());
        SchemaSupport.migrate(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        exports = new AnalysisExportService(dataSource, new ObjectMapper(), storage);
    }

    @Test
    void writesAnAtomicVersionedPackageWithDistinctAssessmentStatesAndSeparateCandidates() throws Exception {
        fixture();

        AnalysisExportService.AnalysisExportRequest request = new AnalysisExportService.AnalysisExportRequest(List.of(80L), List.of(), 502L);
        AnalysisExportService.AnalysisExportPreview preview = exports.preview(request);
        AnalysisExportService.AnalysisExportResult result = exports.create(request);
        Path packageFile = exports.resolveKnownArtifact(result.filename());

        assertThat(preview.scope().mode()).isEqualTo("SELECTED");
        assertThat(preview.botbSelections()).isEqualTo(3);
        assertThat(preview.entries()).isEqualTo(17);
        assertThat(preview.votedBallots()).isEqualTo(1);
        assertThat(preview.noBallots()).isEqualTo(16);
        assertThat(preview.unknownBallots()).isEqualTo(1);
        assertThat(result.preview()).isEqualTo(preview);
        assertThat(packageFile.getParent()).isEqualTo(storage.exportsDirectory());
        assertThat(Files.list(storage.exportsDirectory()).map(path -> path.getFileName().toString()).toList())
                .containsExactly(result.filename());

        try (ZipFile zip = new ZipFile(packageFile.toFile())) {
            assertThat(zip.stream().map(entry -> entry.getName()).toList()).containsExactly(
                    "manifest.json", "README.md", "analysis.json", "analysis.md", "participants.csv", "participations.csv",
                    "botb-selections.csv", "entries.csv", "ballots.csv", "assessment-matrix.csv", "candidates.csv"
            );
            String manifest = text(zip, "manifest.json");
            String readme = text(zip, "README.md");
            String analysis = text(zip, "analysis.json");
            String markdown = text(zip, "analysis.md");
            String ballots = text(zip, "ballots.csv");
            String matrix = text(zip, "assessment-matrix.csv");
            String candidates = text(zip, "candidates.csv");
            String botbSelections = text(zip, "botb-selections.csv");

            assertThat(manifest).contains("\"format\":\"csc-x-tool-analysis\"", "\"formatVersion\":2", "assessment-matrix.csv", "botb-selections.csv");
            assertThat(analysis).contains("\"outsideTop15EntryIds\":[1016]", "\"ownEntryId\":1000", "\"status\":\"NICHT_ABGESTIMMT\"",
                    "\"status\":\"UNERFASST\"", "\"predictionCandidates\"", "\"botbSelections\"", "\"editionNumber\":9", "\"knownSince\":null");
            assertThat(analysis).contains("\"id\":910,\"participantId\":101,\"editionNumber\":9,\"artist\":\"VÖLA; \\\"Zitat\\\"\",\"knownSince\":\"2024-05-12\"")
                    .doesNotContain("Other scope BOTB");
            assertThat(analysis).contains("\"predictionContext\":{\"contest\":{\"id\":82,\"name\":\"CSC Current\",\"current\":true},"
                    + "\"show\":{\"id\":502,\"contestId\":82,\"showNumber\":1,\"name\":\"Current candidates\"}}");
            String archiveContests = analysis.substring(analysis.indexOf("\"contests\""), analysis.indexOf("\"participations\""));
            assertThat(archiveContests).doesNotContain("\"id\":82", "CSC Current");
            assertThat(analysis.substring(analysis.indexOf("\"entries\""), analysis.indexOf("\"publishedBallots\"")))
                    .doesNotContain("Prediction Artist", "Other Contest Song", "Current Entry Artist");
            assertThat(markdown).contains("Outside Top 15 (unordered set; no rank is known)", "Own non-votable entry",
                    "Status: **NO_BALLOT**", "Status: **UNKNOWN**", "Prediction candidates (separate from historic entries)",
                    "CSC Current, show 1: Current candidates", "BOTB artist selections: 3", "BOTB #9: VÖLA; \"Zitat\"");
            assertThat(readme).contains("version 2", "botb-selections.csv", "selection-model evidence event");
            assertThat(ballots).startsWith("\uFEFF").contains("RANKED", "NO_BALLOT", "UNKNOWN").contains("\r\n");
            assertThat(matrix).contains("RANKED", "OUTSIDE_TOP_15", "OWN_ENTRY", "NO_BALLOT", "UNKNOWN")
                    .contains(";0;");
            assertThat(candidates).contains("Prediction Artist", "Prediction Title").doesNotContain("Own Song");
            assertThat(botbSelections).startsWith("\uFEFFselection_id;participant_id;edition_number;artist;known_since\r\n")
                    .contains("910;101;9;\"VÖLA; \"\"Zitat\"\"\";2024-05-12\r\n", "911;101;4;Act ohne Datum;\r\n");
        }
    }

    @Test
    void exportsCanonicalP12ReadinessForARevealedCurrentShow() throws Exception {
        fixture();
        assertThat(jdbc.queryForObject("SELECT entry_list_complete FROM motto_show WHERE id=502", Boolean.class)).isFalse();

        AnalysisExportService.AnalysisExportResult result = exports.create(
                new AnalysisExportService.AnalysisExportRequest(List.of(), List.of(502L), null)
        );

        try (ZipFile zip = new ZipFile(exports.resolveKnownArtifact(result.filename()).toFile())) {
            String analysis = text(zip, "analysis.json");
            String markdown = text(zip, "analysis.md");
            assertThat(analysis).contains("\"id\":502,\"contestId\":82,\"showNumber\":1,\"name\":\"Current candidates\",\"entryListComplete\":true");
            assertThat(markdown).contains("## Show 1: Current candidates", "Song list complete: **yes**.", "Current Entry Artist - Current Entry");
        }
    }

    @Test
    void fullArchiveIncludesBotbSelectionsOfEveryIncludedParticipantIdentity() throws Exception {
        fixture();

        AnalysisExportService.AnalysisExportResult result = exports.create(
                new AnalysisExportService.AnalysisExportRequest(List.of(), List.of(), null)
        );

        assertThat(result.preview().botbSelections()).isEqualTo(4);
        try (ZipFile zip = new ZipFile(exports.resolveKnownArtifact(result.filename()).toFile())) {
            assertThat(text(zip, "analysis.json")).contains("Other scope BOTB", "\"participantId\":190");
        }
    }

    private void fixture() {
        jdbc.update("UPDATE contest SET is_current=0");
        jdbc.update("INSERT INTO contest (id,name,display_order,is_current,created_at,updated_at) VALUES (80,'CSC IX',80,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO contest (id,name,display_order,is_current,created_at,updated_at) VALUES (81,'CSC VIII',81,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO contest (id,name,display_order,is_current,created_at,updated_at) VALUES (82,'CSC Current',82,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        participant(101, "Alice; alias", "DE");
        participant(102, "Bob", "FR");
        participant(103, "Carol", "SE");
        participant(190, "Other scope participant", "NL");
        jdbc.update("INSERT INTO participant_alias (id,participant_id,alias) VALUES (901,101,'Alicia')");
        participation(201, 80, 101, "DE");
        participation(202, 80, 102, "FR");
        participation(203, 80, 103, "SE");
        participation(300, 81, 101, "RO");
        participation(400, 82, 101, "PH");
        participation(401, 81, 190, "NL");
        jdbc.update("INSERT INTO participant_botb_selection (id,participant_id,edition_number,artist,known_since,created_at,updated_at) VALUES (910,101,9,?, '2024-05-12',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                "VÖLA; \"Zitat\"");
        jdbc.update("INSERT INTO participant_botb_selection (id,participant_id,edition_number,artist,known_since,created_at,updated_at) VALUES (911,101,4,'Act ohne Datum',NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO participant_botb_selection (id,participant_id,edition_number,artist,known_since,created_at,updated_at) VALUES (912,102,2,'Zweiter Act',NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO participant_botb_selection (id,participant_id,edition_number,artist,known_since,created_at,updated_at) VALUES (913,190,1,'Other scope BOTB',NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO motto_show (id,contest_id,show_number,name,entry_list_complete,created_at,updated_at) VALUES (500,80,3,'Historical; show',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO motto_show (id,contest_id,show_number,name,entry_list_complete,created_at,updated_at) VALUES (501,81,1,'Other contest',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO motto_show (id,contest_id,show_number,name,entry_list_complete,created_at,updated_at) VALUES (502,82,1,'Current candidates',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        entry(1000, 500, 80, "Own Artist", "Own Song", 201, 1);
        for (int rank = 1; rank <= 15; rank++) {
            long participantId = 103 + rank;
            long participationId = 210 + rank;
            participant(participantId, "Ranked submitter " + rank, "US");
            participation(participationId, 80, participantId, "US");
            entry(1000 + rank, 500, 80, "Ranked Artist " + rank, "Ranked Title " + rank, participationId, rank + 1);
        }
        entry(1016, 500, 80, "Outside Artist", "Outside; title", 202, 17);
        entry(1100, 501, 81, "Other Artist", "Other Contest Song", 300, 1);
        entry(1200, 502, 82, "Current Entry Artist", "Current Entry", 400, 1);
        jdbc.update("INSERT INTO published_ballot (id,motto_show_id,contest_id,contest_participation_id,status,created_at,updated_at) VALUES (700,500,80,201,'ABGESTIMMT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        for (int rank = 1; rank <= 15; rank++) jdbc.update("INSERT INTO published_ballot_position (published_ballot_id,contest_entry_id,rank) VALUES (700,?,?)", 1000 + rank, rank);
        jdbc.update("INSERT INTO published_ballot (id,motto_show_id,contest_id,contest_participation_id,status,created_at,updated_at) VALUES (701,500,80,202,'NICHT_ABGESTIMMT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        for (int rank = 1; rank <= 15; rank++) jdbc.update("INSERT INTO published_ballot (id,motto_show_id,contest_id,contest_participation_id,status,created_at,updated_at) VALUES (?,?,80,?,'NICHT_ABGESTIMMT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                701 + rank, 500, 210 + rank);
        jdbc.update("INSERT INTO candidate (id,motto_show_id,artist,title,youtube_url,comment,status,manual_position,created_at,updated_at) VALUES (800,502,'Prediction Artist','Prediction Title','https://example.invalid/watch','An analysis candidate','FINALIST',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("UPDATE motto_show SET selected_candidate_id=800 WHERE id=502");
    }

    private void participant(long id, String name, String ignoredCountry) {
        jdbc.update("INSERT INTO participant (id,display_name,active,created_at,updated_at) VALUES (?,?,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", id, name);
    }

    private void participation(long id, long contestId, long participantId, String country) {
        jdbc.update("INSERT INTO contest_participation (id,contest_id,participant_id,country_code,active,created_at,updated_at) VALUES (?,?,?,?,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                id, contestId, participantId, country);
    }

    private void entry(long id, long showId, long contestId, String artist, String title, long submitterId, int position) {
        jdbc.update("INSERT INTO contest_entry (id,motto_show_id,contest_id,artist,title,youtube_url,pool_position,contest_participation_id,created_at,updated_at) VALUES (?,?,?,?,?,NULL,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                id, showId, contestId, artist, title, position, submitterId);
    }

    private static String text(ZipFile zip, String name) throws Exception {
        try (InputStream input = zip.getInputStream(zip.getEntry(name))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
