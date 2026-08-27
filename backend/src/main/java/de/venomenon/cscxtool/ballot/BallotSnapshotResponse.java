package de.venomenon.cscxtool.ballot;

import java.time.Instant;
import java.util.List;

record BallotSnapshotResponse(
        long id,
        int snapshotNumber,
        Instant createdAt,
        boolean current,
        List<BallotSnapshotItemResponse> items
) {

    static BallotSnapshotResponse from(BallotSnapshot snapshot) {
        return new BallotSnapshotResponse(
                snapshot.id(),
                snapshot.snapshotNumber(),
                snapshot.createdAt(),
                snapshot.current(),
                snapshot.items().stream().map(BallotSnapshotItemResponse::from).toList()
        );
    }
}
