package de.venomenon.cscxtool.ballot;

import java.time.Instant;
import java.util.List;

record BallotSnapshot(
        long id,
        int snapshotNumber,
        Instant createdAt,
        boolean current,
        List<BallotSnapshotItem> items
) {
}
