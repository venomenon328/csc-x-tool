package de.venomenon.cscxtool.ballot;

import java.time.Instant;
import java.util.List;

record BallotResponse(
        Instant ballotClosedAt,
        BallotSnapshotResponse currentSnapshot,
        List<BallotSnapshotResponse> snapshots,
        String renderedText
) {

    static BallotResponse from(
            Instant ballotClosedAt,
            BallotSnapshot currentSnapshot,
            List<BallotSnapshot> snapshots,
            BallotRenderer renderer
    ) {
        return new BallotResponse(
                ballotClosedAt,
                currentSnapshot == null ? null : BallotSnapshotResponse.from(currentSnapshot),
                snapshots.stream().map(BallotSnapshotResponse::from).toList(),
                currentSnapshot == null ? null : renderer.render(currentSnapshot)
        );
    }
}
