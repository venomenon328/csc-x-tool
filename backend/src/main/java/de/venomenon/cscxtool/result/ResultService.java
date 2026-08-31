package de.venomenon.cscxtool.result;

import de.venomenon.cscxtool.participant.CountryCatalog;
import de.venomenon.cscxtool.shared.CscPoints;
import de.venomenon.cscxtool.show.ShowNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ResultService {
    private final ResultRepository repository;
    private final CountryCatalog countries;

    ResultService(ResultRepository repository, CountryCatalog countries) {
        this.repository = repository;
        this.countries = countries;
    }

    ResultResponse find(long showId) {
        ResultRepository.ShowFacts show = repository.findShow(showId).orElseThrow(() -> new ShowNotFoundException(showId));
        if (show.ownParticipationId() == null) {
            return new ResultResponse(showId, "OWN_PARTICIPATION_MISSING", null, null, false, 0, 0, 0, 0, List.of());
        }
        ResultRepository.OwnParticipation ownParticipation = repository.findOwnParticipation(show.contestId(), show.ownParticipationId())
                .orElseThrow(() -> new IllegalStateException("The contest own participation must exist."));
        OwnParticipationResponse own = new OwnParticipationResponse(
                ownParticipation.id(), ownParticipation.participantId(), ownParticipation.displayName(), ownParticipation.countryCode()
        );
        if (show.currentContest() && "UNRESOLVED".equals(show.ownEntryResolution())) {
            return new ResultResponse(showId, "OWN_ENTRY_UNRESOLVED", own, null, false, 0, 0, 0, 0, List.of());
        }
        if (show.currentContest() && "NO_OWN_ENTRY".equals(show.ownEntryResolution())) {
            return new ResultResponse(showId, "OWN_ENTRY_NONE", own, null, false, 0, 0, 0, 0, List.of());
        }
        if (!show.entryListReady()) {
            return new ResultResponse(showId, "ENTRY_LIST_INCOMPLETE", own, null, false, 0, 0, 0, 0, List.of());
        }
        ResultRepository.OwnEntry ownEntry = show.currentContest()
                ? show.ownEntryId() == null ? null : repository.findEntry(showId, show.ownEntryId()).orElse(null)
                : repository.findOwnEntry(showId, ownParticipation.id()).orElse(null);
        if (ownEntry == null) {
            return new ResultResponse(showId, "OWN_ENTRY_MISSING", own, null, false, 0, 0, 0, 0, List.of());
        }
        List<DerivedResultLineResponse> lines = repository.findDerivedLines(showId, ownParticipation.id(), ownEntry.id()).stream()
                .map(line -> derive(line, ownParticipation.id())).toList();
        int derivedTotal = lines.stream().map(DerivedResultLineResponse::points).filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).sum();
        int voted = (int) lines.stream().filter(line -> "ABGESTIMMT".equals(line.ballotStatus())).count();
        int notVoted = (int) lines.stream().filter(line -> "NICHT_ABGESTIMMT".equals(line.ballotStatus())).count();
        int unrecorded = (int) lines.stream().filter(line -> "UNERFASST".equals(line.ballotStatus())).count();
        return new ResultResponse(showId, "READY", own, new OwnEntryResponse(ownEntry.id(), ownEntry.artist(), ownEntry.title(), ownEntry.youtubeUrl()),
                repository.selectedCandidateDiffers(showId, ownEntry), voted, notVoted, unrecorded, derivedTotal, lines);
    }

    LegacyResultResponse legacy(long showId) {
        repository.findShow(showId).orElseThrow(() -> new ShowNotFoundException(showId));
        ResultRepository.LegacyResult legacy = repository.findLegacy(showId);
        return new LegacyResultResponse(showId, legacy.details(), legacy.scores());
    }

    @Transactional
    void deleteLegacy(long showId) {
        repository.findShow(showId).orElseThrow(() -> new ShowNotFoundException(showId));
        repository.deleteLegacy(showId);
    }

    private DerivedResultLineResponse derive(ResultRepository.DerivedLine line, long ownParticipationId) {
        String countryName = countries.findRequired(line.countryCode()).name();
        if (line.participationId() == ownParticipationId) {
            return new DerivedResultLineResponse(line.participationId(), line.participantId(), line.displayName(), line.countryCode(), countryName,
                    "EIGENE_TEILNAHME", "OWN_ENTRY", null, null);
        }
        if (line.ballotStatus() == null) {
            return new DerivedResultLineResponse(line.participationId(), line.participantId(), line.displayName(), line.countryCode(), countryName,
                    "UNERFASST", "UNKNOWN", null, null);
        }
        if ("NICHT_ABGESTIMMT".equals(line.ballotStatus())) {
            return new DerivedResultLineResponse(line.participationId(), line.participantId(), line.displayName(), line.countryCode(), countryName,
                    line.ballotStatus(), "NO_BALLOT", null, null);
        }
        if (line.rank() != null) {
            return new DerivedResultLineResponse(line.participationId(), line.participantId(), line.displayName(), line.countryCode(), countryName,
                    line.ballotStatus(), "RANKED", line.rank(), CscPoints.pointsForRank(line.rank()));
        }
        return new DerivedResultLineResponse(line.participationId(), line.participantId(), line.displayName(), line.countryCode(), countryName,
                line.ballotStatus(), "OUTSIDE_TOP_15", null, 0);
    }
}
