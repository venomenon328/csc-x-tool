package de.venomenon.cscxtool.ballot;

import de.venomenon.cscxtool.participant.CountryCatalog;
import de.venomenon.cscxtool.shared.ApiConflictException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class BallotRenderer {

    private final CountryCatalog countryCatalog;

    BallotRenderer(CountryCatalog countryCatalog) {
        this.countryCatalog = countryCatalog;
    }

    String renderIfComplete(BallotSnapshot snapshot) {
        validateStructure(snapshot);
        if (snapshot.items().stream().anyMatch(item -> blank(item.participantCountryCode()))) {
            return null;
        }
        return renderComplete(snapshot);
    }

    String render(BallotSnapshot snapshot) {
        validateStructure(snapshot);
        if (snapshot.items().stream().anyMatch(item -> blank(item.participantCountryCode()))) {
            throw new ApiConflictException(
                    "BALLOT_EXPORT_REQUIRES_PARTICIPANT_ASSIGNMENTS",
                    "Für den Top-15-Export müssen allen 15 Beiträgen Teilnehmer und damit Länder zugeordnet sein."
            );
        }
        return renderComplete(snapshot);
    }

    private String renderComplete(BallotSnapshot snapshot) {
        StringBuilder text = new StringBuilder();
        List<BallotSnapshotItem> items = snapshot.items();
        for (int index = 0; index < items.size(); index++) {
            BallotSnapshotItem item = items.get(index);
            if (index > 0) {
                text.append('\n');
            }
            String countryName = countryCatalog.findRequired(item.participantCountryCode()).name();
            text.append("Platz #")
                    .append(item.rank())
                    .append(" - ")
                    .append(countryName)
                    .append(": ")
                    .append(item.artist())
                    .append(" - ")
                    .append(item.title());
        }
        return text.toString();
    }

    private static void validateStructure(BallotSnapshot snapshot) {
        List<BallotSnapshotItem> items = snapshot.items();
        if (items.size() != 15) {
            throw new IllegalStateException("A current ballot snapshot must contain exactly 15 items.");
        }
        for (int index = 0; index < items.size(); index++) {
            if (items.get(index).rank() != index + 1) {
                throw new IllegalStateException("A current ballot snapshot must have consecutive ranks from 1 to 15.");
            }
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
