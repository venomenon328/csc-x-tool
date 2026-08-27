package de.venomenon.cscxtool.result;

import de.venomenon.cscxtool.participant.CountryCatalog;
import de.venomenon.cscxtool.participant.ParticipantNotFoundException;
import de.venomenon.cscxtool.shared.ApiBadRequestException;
import de.venomenon.cscxtool.shared.ApiConflictException;
import de.venomenon.cscxtool.shared.CscPoints;
import de.venomenon.cscxtool.show.ShowNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ResultService {

    private final ResultRepository repository;
    private final CountryCatalog countryCatalog;

    ResultService(ResultRepository repository, CountryCatalog countryCatalog) {
        this.repository = repository;
        this.countryCatalog = countryCatalog;
    }

    ResultResponse find(long showId) {
        requireShow(showId);
        return response(showId);
    }

    @Transactional
    ResultResponse updateScore(long showId, long participantId, UpdateReceivedScoreRequest request) {
        requireShow(showId);
        requireEditable(showId);
        if (!repository.participantExists(participantId)) {
            throw new ParticipantNotFoundException(participantId);
        }
        if (!repository.mayReceiveScore(showId, participantId)) {
            throw new ApiConflictException(
                    "INACTIVE_PARTICIPANT_WITHOUT_RESULT",
                    "Für einen inaktiven Teilnehmer ohne vorhandenen Ergebniseintrag kann kein neues Ergebnis angelegt werden."
            );
        }
        Integer points = validatedPoints(request.status(), request.points());
        repository.saveScore(showId, participantId, request.status(), points);
        return response(showId);
    }

    @Transactional
    ResultResponse updateDetails(long showId, UpdateResultDetailsRequest request) {
        requireShow(showId);
        requireEditable(showId);
        Integer officialTotalPoints = request.officialTotalPoints();
        Integer finalPlace = request.finalPlace();
        if (officialTotalPoints != null && officialTotalPoints < 0) {
            throw new ApiBadRequestException("INVALID_OFFICIAL_TOTAL_POINTS", "Die offizielle Gesamtpunktzahl darf nicht negativ sein.");
        }
        if (finalPlace != null && finalPlace < 1) {
            throw new ApiBadRequestException("INVALID_FINAL_PLACE", "Die Endplatzierung muss eine positive ganze Zahl sein.");
        }
        boolean finalPlaceTied = Boolean.TRUE.equals(request.finalPlaceTied());
        if (finalPlaceTied && finalPlace == null) {
            throw new ApiBadRequestException(
                    "TIED_FINAL_PLACE_REQUIRES_FINAL_PLACE",
                    "Eine geteilte Platzierung benötigt eine positive numerische Endplatzierung."
            );
        }
        repository.updateDetails(showId, officialTotalPoints, finalPlace, finalPlaceTied);
        return response(showId);
    }

    @Transactional
    ResultResponse close(long showId) {
        requireShow(showId);
        ResultRepository.ResultState state = repository.findState(showId);
        if (state.resultsClosedAt() != null) {
            throw new ApiConflictException("RESULTS_ALREADY_CLOSED", "Die Ergebniserfassung ist bereits abgeschlossen.");
        }
        requireBallotClosed(state);
        if (state.selectedCandidateId() == null) {
            throw new ApiConflictException(
                    "RESULTS_CLOSE_REQUIRES_SUBMISSION",
                    "Zum Abschließen der Ergebniserfassung muss eine eigene Einreichung gewählt sein."
            );
        }
        if (repository.hasUnknownActiveParticipant(showId)) {
            throw new ApiConflictException(
                    "RESULTS_CLOSE_REQUIRES_KNOWN_ACTIVE_SCORES",
                    "Alle aktiven Teilnehmer müssen vor dem Abschluss als abgestimmt oder nicht abgestimmt erfasst sein."
            );
        }
        if (state.finalPlace() == null || state.finalPlace() < 1) {
            throw new ApiConflictException(
                    "RESULTS_CLOSE_REQUIRES_FINAL_PLACE",
                    "Zum Abschließen der Ergebniserfassung muss eine positive Endplatzierung gepflegt sein."
            );
        }
        repository.close(showId);
        return response(showId);
    }

    @Transactional
    ResultResponse reopen(long showId) {
        requireShow(showId);
        ResultRepository.ResultState state = repository.findState(showId);
        if (state.resultsClosedAt() == null) {
            throw new ApiConflictException("RESULTS_NOT_CLOSED", "Die Ergebniserfassung ist nicht abgeschlossen.");
        }
        repository.reopen(showId);
        return response(showId);
    }

    private ResultResponse response(long showId) {
        ResultRepository.ResultState state = repository.findState(showId);
        List<ReceivedScoreLineResponse> lines = repository.findLines(showId).stream().map(line -> new ReceivedScoreLineResponse(
                line.participantId(), line.displayName(), line.countryCode(), countryCatalog.findRequired(line.countryCode()).name(),
                line.active(), line.status(), line.points(), line.persisted()
        )).toList();
        int calculatedTotalPoints = repository.calculatedTotalPoints(showId);
        return new ResultResponse(
                showId,
                state.ballotClosedAt(),
                state.resultsClosedAt(),
                repository.findSelectedCandidate(showId).orElse(null),
                lines,
                calculatedTotalPoints,
                state.officialTotalPoints(),
                state.officialTotalPoints() == null ? null : state.officialTotalPoints() - calculatedTotalPoints,
                state.finalPlace(),
                state.finalPlaceTied()
        );
    }

    private void requireEditable(long showId) {
        ResultRepository.ResultState state = repository.findState(showId);
        requireBallotClosed(state);
        if (state.resultsClosedAt() != null) {
            throw new ApiConflictException(
                    "RESULTS_REOPEN_REQUIRED",
                    "Die abgeschlossene Ergebniserfassung muss vor Änderungen bewusst wieder geöffnet werden."
            );
        }
    }

    private static Integer validatedPoints(ReceivedScoreStatus status, Integer points) {
        if (status == ReceivedScoreStatus.ABGESTIMMT) {
            if (points == null || !CscPoints.isAllowedReceivedScore(points)) {
                throw new ApiBadRequestException(
                        "INVALID_RECEIVED_SCORE_POINTS",
                        "Bei abgestimmt sind nur die CSC-Punktwerte 0, 1 bis 11, 13, 16, 20 oder 25 zulässig."
                );
            }
            return points;
        }
        if (points != null) {
            throw new ApiBadRequestException(
                    "UNEXPECTED_RECEIVED_SCORE_POINTS",
                    "Bei unbekannt oder nicht abgestimmt darf keine Punktzahl gespeichert sein."
            );
        }
        return null;
    }

    private static void requireBallotClosed(ResultRepository.ResultState state) {
        if (state.ballotClosedAt() == null) {
            throw new ApiConflictException(
                    "RESULTS_REQUIRE_CLOSED_BALLOT",
                    "Ergebnisse können erst nach dem Abschluss der eigenen Top 15 bearbeitet werden."
            );
        }
    }

    private void requireShow(long showId) {
        if (!repository.showExists(showId)) {
            throw new ShowNotFoundException(showId);
        }
    }
}
