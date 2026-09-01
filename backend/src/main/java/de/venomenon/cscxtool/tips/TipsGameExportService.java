package de.venomenon.cscxtool.tips;

import de.venomenon.cscxtool.participant.CountryCatalog;
import de.venomenon.cscxtool.shared.ApiConflictException;
import de.venomenon.cscxtool.show.ShowNotFoundException;
import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class TipsGameExportService {

    private final TipsGameRepository repository;
    private final CountryCatalog countries;

    TipsGameExportService(TipsGameRepository repository, CountryCatalog countries) {
        this.repository = repository;
        this.countries = countries;
    }

    @Transactional(readOnly = true)
    String export(long showId) {
        TipsShowFacts facts = repository.findShowFacts(showId).orElseThrow(() -> new ShowNotFoundException(showId));
        if (!facts.currentContest()) {
            throw new ApiConflictException(
                    "TIPS_GAME_REQUIRES_CURRENT_CONTEST",
                    "Das Tippspiel ist nur für die aktuelle CSC-Ausgabe verfügbar."
            );
        }
        TipsGame game = repository.findGame(showId).orElseThrow(TipsGameExportService::incomplete);
        List<TipsEntry> entries = repository.findEntries(showId, game.id());
        if (entries.isEmpty()) throw incomplete();

        Map<Long, TipsParticipant> participants = repository.findParticipants(facts.contestId()).stream()
                .collect(Collectors.toMap(TipsParticipant::participationId, Function.identity()));
        List<ExportRow> rows = entries.stream().map(entry -> exportRow(entry, facts, participants)).toList();

        Collator german = Collator.getInstance(Locale.GERMAN);
        german.setStrength(Collator.SECONDARY);
        Comparator<ExportRow> order = Comparator
                .comparing(ExportRow::artist, german)
                .thenComparing(ExportRow::title, german)
                .thenComparingLong(ExportRow::entryId);
        return rows.stream().sorted(order).map(ExportRow::text).collect(Collectors.joining("\n"));
    }

    private ExportRow exportRow(TipsEntry entry, TipsShowFacts facts, Map<Long, TipsParticipant> participants) {
        String countryName;
        String displayName;
        if (entry.ownEntry()) {
            if (entry.actualParticipationId() == null || entry.actualDisplayName() == null || entry.actualCountryCode() == null
                    || (facts.ownParticipationId() != null && !facts.ownParticipationId().equals(entry.actualParticipationId()))) {
                throw incomplete();
            }
            countryName = countries.findRequired(entry.actualCountryCode()).name();
            displayName = entry.actualDisplayName();
        } else {
            if (entry.guessedParticipationId() == null) throw incomplete();
            TipsParticipant participant = participants.get(entry.guessedParticipationId());
            if (participant == null) throw incomplete();
            countryName = countries.findRequired(participant.countryCode()).name();
            displayName = participant.displayName();
        }
        String text = entry.artist() + " - " + entry.title() + " [" + countryName + "/" + displayName + "]";
        return new ExportRow(entry.id(), entry.artist(), entry.title(), text);
    }

    private static ApiConflictException incomplete() {
        return new ApiConflictException(
                "TIPS_GAME_EXPORT_INCOMPLETE",
                "Der Abgabeexport ist erst verfügbar, wenn alle tippbaren Beiträge gespeichert zugeordnet sind."
        );
    }

    private record ExportRow(long entryId, String artist, String title, String text) { }
}
