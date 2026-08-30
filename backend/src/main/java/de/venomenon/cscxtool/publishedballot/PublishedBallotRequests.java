package de.venomenon.cscxtool.publishedballot;

import java.util.List;

record PublishedBallotImportPreviewRequest(String html, String text) { }

record PublishedBallotPositionRequest(Long entryId, Integer rank) { }

record PublishedBallotImportRequest(
        Long participationId, List<PublishedBallotPositionRequest> positions, boolean replaceExisting
) { }

record PublishedBallotImportBatchRequest(List<PublishedBallotImportRequest> ballots) { }

record UpdatePublishedBallotStatusRequest(String status) { }
