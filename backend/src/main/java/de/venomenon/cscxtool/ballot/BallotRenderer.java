package de.venomenon.cscxtool.ballot;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The neutral P5 renderer deliberately has no CSC-specific header or point values.
 * A later real CSC template can replace this one isolated formatter without changing snapshots.
 */
@Component
class BallotRenderer {

    String render(BallotSnapshot snapshot) {
        List<BallotSnapshotItem> items = snapshot.items();
        if (items.size() != 15) {
            throw new IllegalStateException("A current ballot snapshot must contain exactly 15 items.");
        }
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < items.size(); index++) {
            BallotSnapshotItem item = items.get(index);
            int expectedRank = index + 1;
            if (item.rank() != expectedRank) {
                throw new IllegalStateException("A current ballot snapshot must have consecutive ranks from 1 to 15.");
            }
            if (index > 0) {
                text.append('\n');
            }
            text.append(item.rank()).append(". ").append(item.artist()).append(" - ").append(item.title());
        }
        return text.toString();
    }
}
