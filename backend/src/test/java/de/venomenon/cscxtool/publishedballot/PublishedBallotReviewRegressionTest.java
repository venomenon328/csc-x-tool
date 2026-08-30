package de.venomenon.cscxtool.publishedballot;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class PublishedBallotReviewRegressionTest {

    private static final Path STORAGE_ROOT = temporaryStorageRoot();
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PublishedBallotRepository repository;
    @Autowired private PublishedBallotImportParser parser;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("csc-x-tool.storage.root", () -> STORAGE_ROOT.toString());
    }

    @Test
    void currentShowRequiresClosedOwnBallotAndEveryEntryAssignedBeforePublishedBallots() {
        int sequence = SEQUENCE.incrementAndGet();
        long contestId = jdbc.queryForObject("SELECT id FROM contest WHERE is_current = 1", Long.class);
        int showNumber = 50_000 + sequence;
        jdbc.update("""
                INSERT INTO motto_show (contest_id, show_number, name, created_at, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, contestId, showNumber, "Published ballot readiness " + sequence);
        long showId = jdbc.queryForObject(
                "SELECT id FROM motto_show WHERE contest_id = ? AND show_number = ?", Long.class, contestId, showNumber
        );
        jdbc.update("""
                INSERT INTO contest_entry (
                  motto_show_id, contest_id, artist, title, youtube_url, pool_position, created_at, updated_at
                ) VALUES (?, ?, 'Readiness Artist', 'Readiness Song', 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', 1,
                  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, showId, contestId);

        assertThat(repository.findShowFacts(showId).orElseThrow().entryListReady()).isFalse();

        String participantName = "Published readiness user " + sequence;
        jdbc.update("""
                INSERT INTO participant (display_name, active, created_at, updated_at)
                VALUES (?, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, participantName);
        long participantId = jdbc.queryForObject(
                "SELECT id FROM participant WHERE display_name = ?", Long.class, participantName
        );
        jdbc.update("""
                INSERT INTO contest_participation (
                  contest_id, participant_id, country_code, active, created_at, updated_at
                ) VALUES (?, ?, 'DE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, contestId, participantId);
        long participationId = jdbc.queryForObject(
                "SELECT id FROM contest_participation WHERE contest_id = ? AND participant_id = ?",
                Long.class, contestId, participantId
        );
        jdbc.update(
                "UPDATE contest_entry SET contest_participation_id = ? WHERE motto_show_id = ?",
                participationId, showId
        );

        assertThat(repository.findShowFacts(showId).orElseThrow().entryListReady()).isFalse();

        jdbc.update("UPDATE motto_show SET ballot_closed_at = CURRENT_TIMESTAMP WHERE id = ?", showId);

        assertThat(repository.findShowFacts(showId).orElseThrow().entryListReady()).isTrue();
    }

    @Test
    void contradictoryUrlTextSubmitterAndVoterSignalsRequireManualCorrection() {
        List<PublishedBallotParticipant> participants = List.of(
                participant(1, 1, "Voter", "MT"),
                participant(2, 2, "Alice", "DE", "Alice-Alt"),
                participant(3, 3, "Bob", "FI")
        );
        List<PublishedBallotEntry> entries = List.of(
                entry(10, "Artist A", "Song A", "https://source.test/a", 2, 2, "Alice", "DE"),
                entry(11, "Artist B", "Song B", "https://source.test/b", 3, 3, "Bob", "FI")
        );

        String contradictoryHtml = """
                <p>[#3] Malta - Voter</p>
                <p><a href="https://source.test/a">1 punt Finnland - Bob Artist B - Song B</a></p>
                """;
        PublishedBallotPreviewPosition sourceConflict = parser.parse(
                contradictoryHtml, "", participants, entries, Set.of()
        ).getFirst().positions().getFirst();
        assertThat(sourceConflict.entryId()).isNull();
        assertThat(sourceConflict.warnings()).extracting(BallotImportWarning::code).contains("SOURCE_CONFLICT");

        PublishedBallotPreviewPosition submitterConflict = parser.parse(
                "", "[#3] Malta - Voter\n1 punt Finnland - Bob Artist A - Song A", participants, entries, Set.of()
        ).getFirst().positions().getFirst();
        assertThat(submitterConflict.entryId()).isNull();
        assertThat(submitterConflict.warnings()).extracting(BallotImportWarning::code).contains("SUBMITTER_CONFLICT");

        PublishedBallotPreviewBlock voterConflict = parser.parse(
                "", "[#3] Deutschland - Voter\n1 punt Deutschland - Alice Artist A - Song A", participants, entries, Set.of()
        ).getFirst();
        assertThat(voterConflict.participationId()).isNull();
        assertThat(voterConflict.warnings()).extracting(BallotImportWarning::code).contains("COUNTRY_CONFLICT");
    }

    private static PublishedBallotParticipant participant(
            long participationId, long participantId, String displayName, String countryCode, String... aliases
    ) {
        return new PublishedBallotParticipant(
                participationId, participantId, displayName, countryCode, countryCode, List.of(aliases)
        );
    }

    private static PublishedBallotEntry entry(
            long id,
            String artist,
            String title,
            String url,
            long participationId,
            long participantId,
            String displayName,
            String countryCode
    ) {
        return new PublishedBallotEntry(
                id, 1, artist, title, url, participationId, participantId, displayName, countryCode
        );
    }

    private static Path temporaryStorageRoot() {
        try {
            return Files.createTempDirectory("csc-x-tool-published-ballot-review-");
        } catch (Exception exception) {
            throw new IllegalStateException("Temporäres SQLite-Testverzeichnis konnte nicht angelegt werden.", exception);
        }
    }
}
