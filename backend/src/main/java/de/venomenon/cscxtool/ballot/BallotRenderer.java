package de.venomenon.cscxtool.ballot;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
class BallotRenderer {

    String renderIfComplete(BallotSnapshot snapshot) {
        return render(snapshot);
    }

    String render(BallotSnapshot snapshot) {
        validateStructure(snapshot);
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
            text.append(item.rank())
                    .append(". ")
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
}
