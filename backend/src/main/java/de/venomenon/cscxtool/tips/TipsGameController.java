package de.venomenon.cscxtool.tips;

import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shows/{showId}/tips")
class TipsGameController {

    private final TipsGameService service;

    TipsGameController(TipsGameService service) {
        this.service = service;
    }

    @GetMapping
    TipsGameResponse detail(@PathVariable long showId) {
        return service.detail(showId);
    }

    @PutMapping
    TipsGameResponse replace(@PathVariable long showId, @RequestBody(required = false) SaveTipsGameRequest request) {
        return service.replace(showId, request);
    }

    @PostMapping("/resolve")
    TipsGameResponse resolve(@PathVariable long showId) {
        return service.resolve(showId);
    }

    @PostMapping("/reopen")
    TipsGameResponse reopen(@PathVariable long showId) {
        return service.reopen(showId);
    }

    @GetMapping("/participants/{participationId}/history")
    TipsSubmissionHistoryResponse history(@PathVariable long showId, @PathVariable long participationId) {
        return service.history(showId, participationId);
    }
}

record SaveTipsGameRequest(List<SaveTipsAssignmentRequest> assignments) { }
record SaveTipsAssignmentRequest(Long entryId, Long guessedParticipationId, String confidence, String note) { }
record TipsGameResponse(
        long showId, long contestId, boolean persisted, TipsGameStatus status, Instant createdAt, Instant updatedAt, Instant resolvedAt,
        boolean actualAssignmentsComplete, List<TipsParticipantResponse> participants, List<TipsEntryResponse> entries,
        TipsGameStatisticsResponse statistics
) { }
record TipsParticipantResponse(long participationId, long participantId, String displayName, String countryCode, String countryName,
                               boolean active, boolean identityActive) { }
record TipsEntryResponse(long id, String artist, String title, String youtubeUrl, boolean ownEntry,
                         TipsActualAssignmentResponse actualAssignment, TipsAssignmentResponse tip) { }
record TipsActualAssignmentResponse(long participationId, long participantId, String displayName, String countryCode, String countryName) { }
record TipsAssignmentResponse(long entryId, long guessedParticipationId, TipsConfidence confidence, String note) { }
record TipsGameStatisticsResponse(int correct, int incorrect, int missing, int tipsSubmitted, Double hitRate,
                                  List<TipsConfidenceStatisticsResponse> confidence) { }
record TipsConfidenceStatisticsResponse(TipsConfidence confidence, int correct, int incorrect, int tipsSubmitted, Double hitRate) { }
record TipsSubmissionHistoryResponse(long participationId, List<TipsSubmissionHistoryResponseItem> entries,
                                     List<TipsBotbSelectionHistoryResponseItem> botbSelections) { }
record TipsSubmissionHistoryResponseItem(long entryId, long showId, int showNumber, String showName, long contestId, String contestName,
                                         boolean currentContest, String countryCode, String countryName, String artist, String title,
                                         String youtubeUrl) { }
record TipsBotbSelectionHistoryResponseItem(long id, int editionNumber, String artist, String knownSince) { }
