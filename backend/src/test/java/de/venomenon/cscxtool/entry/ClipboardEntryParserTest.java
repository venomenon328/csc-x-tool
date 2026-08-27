package de.venomenon.cscxtool.entry;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.cscxtool.song.YoutubeUrlNormalizer;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClipboardEntryParserTest {

    private final ClipboardEntryParser parser = new ClipboardEntryParser(new YoutubeUrlNormalizer());

    @Test
    void prioritizesHtmlLinksAndRecognizesTheThreeKnownExamplesWithoutDuplicatingPlaintext() {
        String html = """
                <style>.ignored { color: red; }</style><script>window.neverRun = true;</script>
                <p><a href="https://www.youtube.com/watch?v=2Dqu1Gh45qU">Imminence - Paralyzed</a></p>
                <p><a href="https://www.youtube.com/watch?v=5VWZU2SDFcY">The Killers - Read My Mind</a></p>
                <p><a href="https://www.youtube.com/watch?v=mOJEcEkR1a8">Alice In Chains - Would?</a></p>
                """;
        String text = """
                Imminence - Paralyzed -> https://www.youtube.com/watch?v=2Dqu1Gh45qU
                The Killers - Read My Mind -> https://www.youtube.com/watch?v=5VWZU2SDFcY
                Alice In Chains - Would? -> https://www.youtube.com/watch?v=mOJEcEkR1a8
                """;

        List<ImportPreviewLine> lines = parser.parse(html, text);

        assertThat(lines).hasSize(3);
        assertThat(lines).extracting(ImportPreviewLine::sourceType).containsOnly("HTML_LINK");
        assertThat(lines).extracting(ImportPreviewLine::artist).containsExactly("Imminence", "The Killers", "Alice In Chains");
        assertThat(lines).extracting(ImportPreviewLine::title).containsExactly("Paralyzed", "Read My Mind", "Would?");
        assertThat(lines).allMatch(line -> line.status() == ImportPreviewStatus.READY);
    }

    @Test
    void supportsMarkdownAndExplicitPlaintextUrlsForTheKnownExamples() {
        List<ImportPreviewLine> markdown = parser.parse("", """
                [Imminence - Paralyzed](https://youtu.be/2Dqu1Gh45qU)
                [The Killers - Read My Mind](https://youtube.com/watch?v=5VWZU2SDFcY)
                [Alice In Chains - Would?](https://youtube.com/embed/mOJEcEkR1a8)
                """);
        List<ImportPreviewLine> plaintext = parser.parse("", """
                Imminence - Paralyzed -> https://youtu.be/2Dqu1Gh45qU
                The Killers - Read My Mind https://youtube.com/watch?v=5VWZU2SDFcY
                Alice In Chains - Would? https://youtube.com/embed/mOJEcEkR1a8
                """);

        assertThat(markdown).hasSize(3).allMatch(line -> line.sourceType().equals("MARKDOWN_LINK"));
        assertThat(plaintext).hasSize(3).allMatch(line -> line.sourceType().equals("PLAINTEXT_URL"));
        assertThat(markdown).extracting(ImportPreviewLine::youtubeUrl).containsExactly(
                "https://www.youtube.com/watch?v=2Dqu1Gh45qU",
                "https://www.youtube.com/watch?v=5VWZU2SDFcY",
                "https://www.youtube.com/watch?v=mOJEcEkR1a8"
        );
    }

    @Test
    void keepsAmbiguousAndIncompleteCandidateLinesVisibleForCorrection() {
        List<ImportPreviewLine> lines = parser.parse("", """
                Jay-Z – A Question? (feat. Édith Piaf) https://youtu.be/dQw4w9WgXcQ
                Artist - Title - Remix https://youtu.be/9bZkp7q19f0
                Ohne Link – aber sichtbar
                Fremd – Link https://example.test/video
                """);

        assertThat(lines).hasSize(4);
        assertThat(lines.get(0)).satisfies(line -> {
            assertThat(line.artist()).isEqualTo("Jay-Z");
            assertThat(line.title()).isEqualTo("A Question? (feat. Édith Piaf)");
            assertThat(line.status()).isEqualTo(ImportPreviewStatus.READY);
        });
        assertThat(lines.get(1).warnings()).extracting(ImportWarning::code).contains("AMBIGUOUS_ARTIST_TITLE_SEPARATOR");
        assertThat(lines.get(2).status()).isEqualTo(ImportPreviewStatus.INCOMPLETE);
        assertThat(lines.get(3).warnings()).extracting(ImportWarning::code).contains("UNSUPPORTED_YOUTUBE_URL");
    }

    @Test
    void marksMultipleUrlsInsteadOfGuessingAndDoesNotCreatePreviewRowsForForumNoise() {
        List<ImportPreviewLine> lines = parser.parse("", """
                Artist - Title https://youtu.be/dQw4w9WgXcQ https://youtu.be/9bZkp7q19f0
                Vielen Dank fürs Zuhören!
                """);

        assertThat(lines).singleElement().satisfies(line -> {
            assertThat(line.status()).isEqualTo(ImportPreviewStatus.INCOMPLETE);
            assertThat(line.warnings()).extracting(ImportWarning::code).contains("AMBIGUOUS_YOUTUBE_URL");
        });
    }
}
