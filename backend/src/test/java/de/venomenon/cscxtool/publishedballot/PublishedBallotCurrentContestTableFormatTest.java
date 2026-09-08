package de.venomenon.cscxtool.publishedballot;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class PublishedBallotCurrentContestTableFormatTest {

    private static final Path STORAGE_ROOT = temporaryStorageRoot();
    private static final List<Integer> DISPLAYED_POINTS = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 16, 20, 25);
    private static final List<SourceEntry> SOURCE_ENTRIES = List.of(
            new SourceEntry("Stevie Wonder", "Isn't She Lovely", "Contiomagus", "CV", "Kap Verde"),
            new SourceEntry("Lord Huron", "Meet Me in the Woods", "Sentino", "AM", "Armenien"),
            new SourceEntry("Bronski Beat", "Smalltown Boy", "snaggletooth", "GR", "Griechenland"),
            new SourceEntry("Tears For Fears", "Shout", "Fletcher Cox", "LC", "St. Lucia"),
            new SourceEntry("Green Day", "21 Guns", "Steven_Blueheart", "UA", "Ukraine"),
            new SourceEntry("Kendrick Lamar", "Not Like Us", "Toblerone Driver", "TR", "Türkei"),
            new SourceEntry("The Notorious B.I.G.", "Hypnotize", "Mark Webber", "NZ", "Neuseeland"),
            new SourceEntry("Bruno Mars", "Locked Out Of Heaven", "Joshi Judas Zwen", "MX", "Mexiko"),
            new SourceEntry("Ed Sheeran", "Shivers", "Worm", "DE", "Deutschland"),
            new SourceEntry("Depeche Mode", "Personal Jesus", "Ratcatcher 2", "PT", "Portugal"),
            new SourceEntry("Maneskin feat. Tom Morello", "GOSSIP", "DerFalke15", "BE", "Belgien"),
            new SourceEntry("Teddy Swimms", "The Door", "George Russell", "IE", "Irland"),
            new SourceEntry("Vampire Weekend", "Cousins", "reddit-nutzer", "CZ", "Tschechien"),
            new SourceEntry("Everlast", "What It's Like", "OMW", "WS", "Samoa"),
            new SourceEntry("Talking Heads", "Psycho Killer", "Grissom", "JP", "Japan")
    );

    @Autowired private PublishedBallotImportParser parser;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("csc-x-tool.storage.root", () -> STORAGE_ROOT.toString());
    }

    @Test
    void parsesTheCurrentContestMarkdownTableFormatExactlyAsPasted() {
        PublishedBallotPreviewBlock preview = parser.parse(
                "", currentContestPaste(), participants(), entries(), Set.of()
        ).getFirst();

        assertReadyPreview(preview);
        assertThat(preview.positions().get(4).submitterDisplayName()).isEqualTo("Steven_Blueheart");
        assertThat(preview.positions().getFirst().artist()).isEqualTo("Stevie Wonder");
        assertThat(preview.positions().getFirst().rank()).isEqualTo(15);
        assertThat(preview.positions().getLast().artist()).isEqualTo("Talking Heads");
        assertThat(preview.positions().getLast().rank()).isEqualTo(1);
    }

    @Test
    void prefersAndParsesTheEquivalentRichHtmlClipboardRepresentation() {
        PublishedBallotPreviewBlock preview = parser.parse(
                richHtml(), "fallback must not be needed", participants(), entries(), Set.of()
        ).getFirst();

        assertReadyPreview(preview);
    }

    @Test
    void parsesCollapsedPlainTextWhenTheWholeBallotIsOneLogicalLine() {
        PublishedBallotPreviewBlock preview = parser.parse(
                "", "Wertung#1 Vatikanstadt - Die Ente " + collapsedRatings(), participants(), entries(), Set.of()
        ).getFirst();

        assertReadyPreview(preview);
    }

    @Test
    void parsesCollapsedRichHtmlWithHeaderAndRatingsCollapsedPerBallotForSeveralBallots() {
        String html = "<div>Wertung#1 Vatikanstadt - Die Ente</div><div>" + collapsedRatings() + "</div>"
                + "<div>Wertung#2 Südkorea - Dr. King Schultz</div><div>" + collapsedRatings() + "</div>";

        List<PublishedBallotPreviewBlock> previews = parser.parse(
                html, "ignored fallback", participants(), entries(), Set.of()
        );

        assertThat(previews).hasSize(2);
        assertReadyPreview(previews.getFirst());
        PublishedBallotPreviewBlock second = previews.get(1);
        assertThat(second.displayName()).isEqualTo("Dr. King Schultz");
        assertThat(second.countryCode()).isEqualTo("KR");
        assertReadyPositions(second);
    }

    private static void assertReadyPreview(PublishedBallotPreviewBlock preview) {
        assertThat(preview.displayName()).isEqualTo("Die Ente");
        assertThat(preview.countryCode()).isEqualTo("VA");
        assertReadyPositions(preview);
    }

    private static void assertReadyPositions(PublishedBallotPreviewBlock preview) {
        assertThat(preview.status()).isEqualTo("READY");
        assertThat(preview.positions()).hasSize(15);
        assertThat(preview.positions()).extracting(PublishedBallotPreviewPosition::rank)
                .containsExactly(15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1);
        assertThat(preview.positions()).extracting(PublishedBallotPreviewPosition::entryId)
                .containsExactly(101L, 102L, 103L, 104L, 105L, 106L, 107L, 108L, 109L, 110L, 111L, 112L, 113L, 114L, 115L);
        assertThat(preview.warnings()).noneMatch(warning -> Set.of(
                "POSITION_COUNT", "UNRESOLVED_VOTER", "AMBIGUOUS_VOTER", "COUNTRY_CONFLICT",
                "UNRECOGNIZED_POSITION_LINES", "UNRESOLVED_SONG", "AMBIGUOUS_SONG", "SOURCE_CONFLICT",
                "SUBMITTER_CONFLICT", "EXPLICIT_RANK_SEQUENCE"
        ).contains(warning.code()));
    }

    private static List<PublishedBallotParticipant> participants() {
        List<PublishedBallotParticipant> participants = new ArrayList<>();
        participants.add(new PublishedBallotParticipant(1L, 1L, "Die Ente", "VA", "Vatikanstadt", List.of()));
        for (int index = 0; index < SOURCE_ENTRIES.size(); index++) {
            SourceEntry source = SOURCE_ENTRIES.get(index);
            long id = index + 2L;
            participants.add(new PublishedBallotParticipant(
                    id, id, source.submitter(), source.countryCode(), source.countryName(), List.of()
            ));
        }
        participants.add(new PublishedBallotParticipant(
                50L, 50L, "Dr. King Schultz", "KR", "Südkorea", List.of()
        ));
        return List.copyOf(participants);
    }

    private static List<PublishedBallotEntry> entries() {
        List<PublishedBallotEntry> entries = new ArrayList<>();
        for (int index = 0; index < SOURCE_ENTRIES.size(); index++) {
            SourceEntry source = SOURCE_ENTRIES.get(index);
            long submitterId = index + 2L;
            entries.add(new PublishedBallotEntry(
                    101L + index, 1L, source.artist(), source.title(), null,
                    submitterId, submitterId, source.submitter(), source.countryCode()
            ));
        }
        return List.copyOf(entries);
    }

    private static String richHtml() {
        StringBuilder html = new StringBuilder("<p>Wertung#1</p>")
                .append("<table><tr><td>Vatikanstadt - Die Ente</td><td></td></tr></table>");
        for (int index = 0; index < SOURCE_ENTRIES.size(); index++) {
            SourceEntry source = SOURCE_ENTRIES.get(index);
            int score = DISPLAYED_POINTS.get(index);
            String label = score + (score == 1 ? " punto" : " punti");
            if (score >= 16) label = "<strong>" + label + "</strong>";
            html.append("<p>").append(label).append("</p>")
                    .append("<table><tr><td>").append(source.artist()).append(" - ").append(source.title())
                    .append("</td><td>").append(source.countryName()).append(" - ").append(source.submitter())
                    .append("</td></tr></table>");
        }
        return html.toString();
    }

    private static String collapsedRatings() {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < SOURCE_ENTRIES.size(); index++) {
            SourceEntry source = SOURCE_ENTRIES.get(index);
            int score = DISPLAYED_POINTS.get(index);
            int decorationCount = score == 16 ? 1 : score == 20 ? 2 : score == 25 ? 3 : 0;
            String decoration = "*".repeat(decorationCount);
            if (!result.isEmpty()) result.append(' ');
            result.append(decoration).append(score).append(score == 1 ? " punto" : " punti").append(decoration)
                    .append(' ').append(source.artist()).append(" - ").append(source.title())
                    .append(' ').append(source.countryName()).append(" - ").append(source.submitter());
        }
        return result.toString();
    }

    private static String currentContestPaste() {
        return """
                |    |
                | :- |

                Wertung#1

                | Vatikanstadt - Die Ente |    |
                | :---------------------- | :- |
                |                         |    |

                |    |    |
                | :- | :- |
                |    |    |

                1 punto

                | Stevie Wonder - Isn't She Lovely | Kap Verde - Contiomagus |
                | :------------------------------- | :---------------------- |
                |                                  |                         |

                2 punti

                | Lord Huron - Meet Me in the Woods | Armenien - Sentino |
                | :-------------------------------- | :----------------- |
                |                                   |                    |

                3 punti

                | Bronski Beat - Smalltown Boy | Griechenland - snaggletooth |
                | :--------------------------- | :-------------------------- |
                |                              |                             |

                4 punti

                | Tears For Fears - Shout | St. Lucia - Fletcher Cox |
                | :---------------------- | :----------------------- |
                |                         |                          |

                5 punti

                | Green Day - 21 Guns | Ukraine - Steven§_Blueheart |
                | :------------------ | :-------------------------- |
                |                     |                             |

                6 punti

                | Kendrick Lamar - Not Like Us | Türkei - Toblerone Driver |
                | :--------------------------- | :------------------------ |
                |                              |                           |

                7 punti

                | The Notorious B.I.G. - Hypnotize | Neuseeland - Mark Webber |
                | :------------------------------- | :----------------------- |
                |                                  |                          |

                8 punti

                | Bruno Mars - Locked Out Of Heaven | Mexiko - Joshi Judas Zwen |
                | :-------------------------------- | :------------------------ |
                |                                   |                           |

                9 punti

                | Ed Sheeran - Shivers | Deutschland - Worm |
                | :------------------- | :----------------- |
                |                      |                    |

                10 punti

                | Depeche Mode - Personal Jesus | Portugal - Ratcatcher 2 |
                | :---------------------------- | :---------------------- |
                |                               |                         |

                11 punti

                | Maneskin feat. Tom Morello - GOSSIP | Belgien - DerFalke15 |
                | :---------------------------------- | :------------------- |
                |                                     |                      |

                13 punti

                | Teddy Swimms - The Door | Irland - George Russell |
                | :---------------------- | :---------------------- |
                |                         |                         |

                §*16 punti§*

                | Vampire Weekend - Cousins | Tschechien - reddit-nutzer |
                | :------------------------ | :------------------------- |
                |                           |                            |

                §*§*20 punti§*§*

                | Everlast - What It's Like | Samoa - OMW |
                | :------------------------ | :---------- |
                |                           |             |

                §*§*§*25 punti§*§*§*

                | Talking Heads - Psycho Killer | Japan - Grissom |
                | :---------------------------- | :-------------- |
                """.replace("§", "\\");
    }

    private static Path temporaryStorageRoot() {
        try {
            return Files.createTempDirectory("csc-x-tool-current-ballot-table-");
        } catch (Exception exception) {
            throw new IllegalStateException("Temporäres Testverzeichnis konnte nicht angelegt werden.", exception);
        }
    }

    private record SourceEntry(String artist, String title, String submitter, String countryCode, String countryName) { }
}
