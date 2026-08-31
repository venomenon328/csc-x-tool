package de.venomenon.cscxtool.publishedballot;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PublishedBallotHeaderVariantsIntegrationTest {

    private static final Path STORAGE_ROOT = temporaryStorageRoot();
    private static final AtomicInteger FIXTURE_SEQUENCE = new AtomicInteger();
    private static final List<Integer> DISPLAYED_POINTS = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 16, 20, 25);
    private static final List<Song> SONGS = List.of(
            new Song("Nik Kershaw", "The Riddle", "Fletcher Cox", "NR", "Nauru"),
            new Song("Eddie Murphy", "Party All The Time", "Berggorilla", "UG", "Uganda"),
            new Song("S Club", "Bring It All Back", "Jamie Hayter", "NZ", "Neuseeland"),
            new Song("The Weeknd", "Blinding Lights", "Mark Webber", "AU", "Australien"),
            new Song("IVE", "I AM", "Die Ente", "VA", "Vatikanstadt"),
            new Song("Elton John", "I'm Still Standing", "Everton", "GR", "Griechenland"),
            new Song("Millencolin", "Da Strike", "PrettyFlamingo", "CG", "Kongo"),
            new Song("Roger Whittaker", "Ein bisschen Aroma", "Contiomagus", "ZA", "Südafrika"),
            new Song("Linkin Park", "Somewhere I Belong", "Kenny Ospreay", "LU", "Luxemburg"),
            new Song("P!nk", "Get The Party Started", "Scott D'Amore", "BA", "Bosnien und Herzegowina"),
            new Song("Gloria Gaynor", "I Will Survive", "snaggletooth", "XS", "Schottland"),
            new Song("Red Hot Chilli Peppers", "One Way Traffic", "The Red-NGA Shankmos", "NG", "Nigeria"),
            new Song("Goldfinger", "Superman", "Dr. King Schultz", "KR", "Südkorea"),
            new Song("Farin Urlaub Racing Team", "Am Strand", "OMW", "WS", "Samoa"),
            new Song("Adam Green", "Emily", "Daniel.", "NL", "Niederlande")
    );

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PublishedBallotImportParser parser;
    @Autowired private PublishedBallotRepository repository;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("csc-x-tool.storage.root", () -> STORAGE_ROOT.toString());
    }

    @Test
    void recognizesMixedHeaderVariantsInPlainMarkdownAndRichHtmlAndImportsThemAsOneBatch() throws Exception {
        Fixture fixture = fixture();
        List<PublishedBallotParticipant> participants = repository.findParticipants(fixture.showId());
        List<PublishedBallotEntry> entries = repository.findEntries(fixture.showId());

        List<PublishedBallotPreviewBlock> plain = parser.parse("", mixedPaste(), participants, entries, Set.of());
        assertThat(plain).hasSize(2);
        assertThat(plain).extracting(PublishedBallotPreviewBlock::displayName)
                .containsExactly("Worm", "Serhou Guirassy");
        assertThat(plain).allSatisfy(block -> {
            assertThat(block.status()).isEqualTo("READY");
            assertThat(block.positions()).extracting(PublishedBallotPreviewPosition::rank)
                    .containsExactly(15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1);
        });

        List<PublishedBallotPreviewBlock> rich = parser.parse(richHtml(), "ignored fallback", participants, entries, Set.of());
        assertThat(rich).hasSize(2);
        assertThat(rich).extracting(PublishedBallotPreviewBlock::displayName)
                .containsExactly("Worm", "Serhou Guirassy");

        PublishedBallotPreviewBlock hyphenated = parser.parse(
                "", ballotBlock("[#15] The Red-NGA Shankmos - Nigeria"), participants, entries, Set.of()
        ).getFirst();
        assertThat(hyphenated.displayName()).isEqualTo("The Red-NGA Shankmos");

        HttpResponse<String> imported = post(
                "/api/shows/" + fixture.showId() + "/published-ballots/import",
                importRequest(plain, false)
        );
        assertThat(imported.statusCode()).isEqualTo(200);
        List<PublishedBallot> ballots = repository.findBallots(fixture.showId());
        assertThat(ballots).hasSize(2);
        assertThat(ballots.stream().mapToInt(ballot -> repository.findPositions(ballot.id()).size()).sum()).isEqualTo(30);
    }

    @Test
    void validatesTheWholeBatchBeforePersistingAnySelectedBallot() throws Exception {
        Fixture fixture = fixture();
        List<PublishedBallotPreviewBlock> blocks = parser.parse(
                "", mixedPaste(), repository.findParticipants(fixture.showId()), repository.findEntries(fixture.showId()), Set.of()
        );

        HttpResponse<String> response = post(
                "/api/shows/" + fixture.showId() + "/published-ballots/import",
                importRequest(blocks, true)
        );

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(repository.findBallots(fixture.showId())).isEmpty();
    }

    private Fixture fixture() throws Exception {
        long contestId = id(post(
                "/api/contests",
                "{\"name\":\"Header Variants " + FIXTURE_SEQUENCE.incrementAndGet() + "\"}"
        ).body(), "id");
        participant(contestId, "Worm", "DE");
        participant(contestId, "Serhou Guirassy", "JM");
        List<Participant> submitters = new ArrayList<>();
        for (Song song : SONGS) submitters.add(participant(contestId, song.submitter(), song.countryCode()));

        long showId = id(post(
                "/api/contests/" + contestId + "/shows",
                "{\"showNumber\":1,\"name\":\"Header-Test\"}"
        ).body(), "id");
        StringBuilder body = new StringBuilder("{\"entries\":[");
        for (int index = 0; index < SONGS.size(); index++) {
            if (index > 0) body.append(',');
            Song song = SONGS.get(index);
            body.append("{\"artist\":\"").append(json(song.artist()))
                    .append("\",\"title\":\"").append(json(song.title()))
                    .append("\",\"youtubeUrl\":null,\"participantId\":")
                    .append(submitters.get(index).participantId()).append('}');
        }
        body.append("]}");
        assertThat(post("/api/shows/" + showId + "/entries/historical-import", body.toString()).statusCode()).isEqualTo(200);
        assertThat(post("/api/shows/" + showId + "/entries/entry-list/complete", "").statusCode()).isEqualTo(204);
        return new Fixture(showId);
    }

    private static String mixedPaste() {
        return ballotBlock("[#13] Deutschland - Worm") + "\n\n"
                + ballotBlock("***[# 14 Jamaika–Serhou Guirassy]***");
    }

    private static String richHtml() {
        StringBuilder html = new StringBuilder();
        appendHtmlBallot(html, "[#13] Deutschland - Worm");
        appendHtmlBallot(html, "[# 14 Jamaika–Serhou Guirassy]");
        return html.toString();
    }

    private static void appendHtmlBallot(StringBuilder html, String header) {
        html.append("<p><strong>").append(header).append("</strong></p>");
        for (String line : ratingLines()) html.append("<p>").append(line).append("</p>");
    }

    private static String ballotBlock(String header) {
        return header + "\n" + String.join("\n", ratingLines());
    }

    private static List<String> ratingLines() {
        List<String> lines = new ArrayList<>();
        for (int index = 0; index < SONGS.size(); index++) {
            Song song = SONGS.get(index);
            int points = DISPLAYED_POINTS.get(index);
            lines.add(points + (points == 1 ? " Punkt " : " Punkte ") + song.countryName() + " - " + song.submitter()
                    + " " + song.artist() + " - " + song.title());
        }
        return List.copyOf(lines);
    }

    private static String importRequest(List<PublishedBallotPreviewBlock> blocks, boolean truncateSecond) {
        StringBuilder body = new StringBuilder("{\"ballots\":[");
        for (int blockIndex = 0; blockIndex < blocks.size(); blockIndex++) {
            if (blockIndex > 0) body.append(',');
            PublishedBallotPreviewBlock block = blocks.get(blockIndex);
            body.append("{\"participationId\":").append(block.participationId())
                    .append(",\"replaceExisting\":false,\"positions\":[");
            int limit = truncateSecond && blockIndex == 1 ? 14 : block.positions().size();
            for (int positionIndex = 0; positionIndex < limit; positionIndex++) {
                if (positionIndex > 0) body.append(',');
                PublishedBallotPreviewPosition position = block.positions().get(positionIndex);
                body.append("{\"entryId\":").append(position.entryId())
                        .append(",\"rank\":").append(position.rank()).append('}');
            }
            body.append("]}");
        }
        return body.append("]}").toString();
    }

    private Participant participant(long contestId, String displayName, String countryCode) throws Exception {
        HttpResponse<String> response = post(
                "/api/contests/" + contestId + "/participants",
                "{\"displayName\":\"" + json(displayName) + "\",\"countryCode\":\"" + countryCode + "\",\"active\":true}"
        );
        assertThat(response.statusCode()).isEqualTo(201);
        long participationId = id(response.body(), "participationId");
        long participantId = jdbc.queryForObject(
                "SELECT participant_id FROM contest_participation WHERE id = ?", Long.class, participationId
        );
        return new Participant(participationId, participantId);
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static long id(String body, String property) {
        int start = body.indexOf("\"" + property + "\":") + property.length() + 3;
        int end = body.indexOf(',', start);
        if (end < 0) end = body.indexOf('}', start);
        return Long.parseLong(body.substring(start, end));
    }

    private static String json(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static Path temporaryStorageRoot() {
        try {
            return Files.createTempDirectory("csc-x-tool-published-ballot-header-variants-");
        } catch (Exception exception) {
            throw new IllegalStateException("Temporäres SQLite-Testverzeichnis konnte nicht angelegt werden.", exception);
        }
    }

    private record Participant(long participationId, long participantId) { }
    private record Fixture(long showId) { }
    private record Song(String artist, String title, String submitter, String countryCode, String countryName) { }
}
