package de.venomenon.cscxtool.ballot;

record BallotSnapshotItemResponse(
        int rank,
        Long contestEntryId,
        String artist,
        String title,
        String youtubeUrl
) {

    static BallotSnapshotItemResponse from(BallotSnapshotItem item) {
        return new BallotSnapshotItemResponse(item.rank(), item.contestEntryId(), item.artist(), item.title(), item.youtubeUrl());
    }
}
