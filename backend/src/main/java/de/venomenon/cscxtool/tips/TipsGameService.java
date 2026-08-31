package de.venomenon.cscxtool.tips;

import de.venomenon.cscxtool.participant.Country;
import de.venomenon.cscxtool.participant.CountryCatalog;
import de.venomenon.cscxtool.shared.ApiBadRequestException;
import de.venomenon.cscxtool.shared.ApiConflictException;
import de.venomenon.cscxtool.show.ShowNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class TipsGameService {

    private final TipsGameRepository repository;
    private final Map<String, String> countryNames;

    TipsGameService(TipsGameRepository repository, CountryCatalog countries) {
        this.repository = repository;
        this.countryNames = countries.findAll().stream().collect(Collectors.toUnmodifiableMap(Country::code, Country::name));
    }

    TipsGameResponse detail(long showId) {
        TipsShowFacts facts = requireCurrentShow(showId);
        return response(facts, repository.findGame(showId).orElse(null));
    }

    @Transactional
    TipsGameResponse replace(long showId, SaveTipsGameRequest request) {
        TipsShowFacts facts = requireAvailableDraft(showId);
        TipsGame existing = repository.findGame(showId).orElse(null);
        if (existing != null && existing.status() == TipsGameStatus.RESOLVED) throw resolvedReadOnly();
        List<TipsAssignmentCommand> assignments = validateAssignments(facts, request);
        TipsGame game = existing == null ? repository.createGame(showId, facts.contestId()) : existing;
        repository.replaceAssignments(game.id(), assignments);
        return response(facts, repository.findGame(showId).orElseThrow());
    }

    @Transactional
    TipsGameResponse resolve(long showId) {
        TipsShowFacts facts = requireAvailableDraft(showId);
        TipsGame game = repository.findGame(showId).orElseThrow(() -> new ApiConflictException(
                "TIPS_GAME_NOT_STARTED", "Ein Tippstand muss vor der Auflösung mindestens einmal gespeichert werden."
        ));
        if (game.status() == TipsGameStatus.RESOLVED) throw new ApiConflictException(
                "TIPS_GAME_ALREADY_RESOLVED", "Der Tippstand ist bereits aufgelöst."
        );
        if (facts.unassignedEntryCount() > 0) throw new ApiConflictException(
                "ACTUAL_ASSIGNMENTS_INCOMPLETE",
                "Die Auflösung ist erst möglich, wenn alle erfassten Wettbewerbsbeiträge ihren tatsächlichen Einreichenden besitzen."
        );
        repository.resolve(game.id());
        return response(facts, repository.findGame(showId).orElseThrow());
    }

    @Transactional
    TipsGameResponse reopen(long showId) {
        TipsShowFacts facts = requireAvailableDraft(showId);
        TipsGame game = repository.findGame(showId).orElseThrow(() -> new ApiConflictException(
                "TIPS_GAME_NOT_STARTED", "Es gibt keinen gespeicherten Tippstand zum Wiederöffnen."
        ));
        if (game.status() != TipsGameStatus.RESOLVED) throw new ApiConflictException(
                "TIPS_GAME_NOT_RESOLVED", "Nur ein aufgelöster Tippstand kann bewusst wieder geöffnet werden."
        );
        repository.reopen(game.id());
        return response(facts, repository.findGame(showId).orElseThrow());
    }

    TipsSubmissionHistoryResponse history(long showId, long participationId) {
        requireCurrentShow(showId);
        if (!repository.participationBelongsToShow(showId, participationId)) throw new ApiConflictException(
                "TIP_PARTICIPANT_NOT_IN_CONTEST", "Die Recherchehilfe benötigt einen Teilnehmer derselben CSC-Ausgabe."
        );
        return new TipsSubmissionHistoryResponse(participationId, repository.findSubmissionHistory(showId, participationId).stream()
                .map(entry -> new TipsSubmissionHistoryResponseItem(entry.entryId(), entry.showId(), entry.showNumber(), entry.showName(),
                        entry.contestId(), entry.contestName(), entry.currentContest(), entry.countryCode(), countryName(entry.countryCode()),
                        entry.artist(), entry.title(), entry.youtubeUrl()))
                .toList());
    }

    private TipsGameResponse response(TipsShowFacts facts, TipsGame game) {
        List<TipsParticipantResponse> participants = repository.findParticipants(facts.contestId()).stream()
                .filter(participant -> facts.ownEntryId() == null || !java.util.Objects.equals(participant.participationId(), facts.ownParticipationId()))
                .map(participant -> new TipsParticipantResponse(participant.participationId(), participant.participantId(), participant.displayName(),
                        participant.countryCode(), countryName(participant.countryCode()), participant.active(), participant.identityActive()))
                .toList();
        List<TipsEntry> entries = repository.findEntries(facts.showId(), game == null ? null : game.id());
        boolean resolved = game != null && game.status() == TipsGameStatus.RESOLVED;
        boolean actualAssignmentsComplete = facts.entryCount() > 0 && facts.unassignedEntryCount() == 0;
        List<TipsEntryResponse> responseEntries = entries.stream().map(entry -> new TipsEntryResponse(
                entry.id(), entry.artist(), entry.title(), entry.youtubeUrl(), entry.ownEntry(), resolved && !entry.ownEntry() ? actual(entry) : null,
                entry.ownEntry() ? null : tip(entry)
        )).toList();
        return new TipsGameResponse(facts.showId(), facts.contestId(), game != null, game == null ? TipsGameStatus.DRAFT : game.status(),
                game == null ? null : game.createdAt(), game == null ? null : game.updatedAt(), game == null ? null : game.resolvedAt(),
                actualAssignmentsComplete, participants, responseEntries, resolved ? statistics(entries) : null);
    }

    private List<TipsAssignmentCommand> validateAssignments(TipsShowFacts facts, SaveTipsGameRequest request) {
        if (request == null || request.assignments() == null) throw new ApiBadRequestException(
                "INVALID_TIPS_GAME", "Die Tippzuordnungen müssen als vollständiger Entwurf übermittelt werden."
        );
        Set<Long> entryIds = repository.findEntries(facts.showId(), null).stream()
                .filter(entry -> !entry.ownEntry()).map(TipsEntry::id).collect(Collectors.toSet());
        Set<Long> participationIds = repository.findParticipants(facts.contestId()).stream()
                .filter(participant -> facts.ownEntryId() == null || !java.util.Objects.equals(participant.participationId(), facts.ownParticipationId()))
                .map(TipsParticipant::participationId).collect(Collectors.toSet());
        Set<Long> seenEntries = new HashSet<>();
        Set<Long> seenParticipations = new HashSet<>();
        List<TipsAssignmentCommand> result = new ArrayList<>();
        for (SaveTipsAssignmentRequest assignment : request.assignments()) {
            if (assignment == null || assignment.entryId() == null || assignment.guessedParticipationId() == null) {
                throw new ApiBadRequestException("INVALID_TIP_ASSIGNMENT", "Jeder Tipp benötigt einen Beitrag und eine vermutete Teilnahme.");
            }
            if (!entryIds.contains(assignment.entryId())) {
                if (java.util.Objects.equals(assignment.entryId(), facts.ownEntryId())) throw new ApiConflictException(
                        "OWN_ENTRY_CANNOT_BE_TIPPED", "Die eigene tatsächliche Einreichung darf nicht getippt werden."
                );
                throw new ApiConflictException("TIP_ENTRY_NOT_IN_SHOW", "Ein Tipp darf nur einen Wettbewerbsbeitrag derselben Mottoshow verwenden.");
            }
            if (!participationIds.contains(assignment.guessedParticipationId())) {
                if (facts.ownEntryId() != null && java.util.Objects.equals(assignment.guessedParticipationId(), facts.ownParticipationId())) throw new ApiConflictException(
                        "OWN_PARTICIPATION_CANNOT_BE_TIPPED", "Die eigene Teilnahme darf nicht als Tipp verwendet werden."
                );
                throw new ApiConflictException("TIP_PARTICIPANT_NOT_IN_CONTEST", "Ein Tipp darf nur einen Teilnehmer derselben CSC-Ausgabe verwenden.");
            }
            if (!seenEntries.add(assignment.entryId())) throw new ApiConflictException(
                    "DUPLICATE_TIP_ENTRY", "Ein Wettbewerbsbeitrag darf im Tippstand nur einmal zugeordnet werden."
            );
            if (!seenParticipations.add(assignment.guessedParticipationId())) throw new ApiConflictException(
                    "DUPLICATE_TIP_PARTICIPANT", "Ein Teilnehmer darf im Tippstand nur einem Wettbewerbsbeitrag zugeordnet werden."
            );
            result.add(new TipsAssignmentCommand(assignment.entryId(), assignment.guessedParticipationId(),
                    confidence(assignment.confidence()), note(assignment.note())));
        }
        return List.copyOf(result);
    }

    private TipsShowFacts requireCurrentShow(long showId) {
        TipsShowFacts facts = repository.findShowFacts(showId).orElseThrow(() -> new ShowNotFoundException(showId));
        if (!facts.currentContest()) throw new ApiConflictException(
                "TIPS_GAME_REQUIRES_CURRENT_CONTEST", "Das Tippspiel ist nur für die aktuelle CSC-Ausgabe verfügbar."
        );
        return facts;
    }

    private TipsShowFacts requireAvailableDraft(long showId) {
        TipsShowFacts facts = requireCurrentShow(showId);
        if (facts.entryCount() == 0) throw new ApiConflictException(
                "TIPS_GAME_ENTRIES_REQUIRED", "Ein Tippstand kann beginnen, sobald die anonyme Songliste erfasst ist."
        );
        if (facts.participationCount() == 0) throw new ApiConflictException(
                "TIPS_GAME_PARTICIPANTS_REQUIRED", "Ein Tippstand kann beginnen, sobald das Teilnehmerfeld der CSC-Ausgabe gepflegt ist."
        );
        return facts;
    }

    private TipsActualAssignmentResponse actual(TipsEntry entry) {
        return entry.actualParticipationId() == null ? null : new TipsActualAssignmentResponse(entry.actualParticipationId(),
                entry.actualParticipantId(), entry.actualDisplayName(), entry.actualCountryCode(), countryName(entry.actualCountryCode()));
    }

    private static TipsAssignmentResponse tip(TipsEntry entry) {
        return entry.tipId() == null ? null : new TipsAssignmentResponse(entry.id(), entry.guessedParticipationId(), entry.confidence(), entry.note());
    }

    private TipsGameStatisticsResponse statistics(List<TipsEntry> entries) {
        int correct = 0;
        int incorrect = 0;
        int missing = 0;
        Map<TipsConfidence, int[]> confidence = new HashMap<>();
        for (TipsEntry entry : entries) {
            if (entry.ownEntry() || entry.actualParticipationId() == null) continue;
            if (entry.guessedParticipationId() == null) {
                missing++;
                continue;
            }
            boolean hit = entry.guessedParticipationId().equals(entry.actualParticipationId());
            if (hit) correct++; else incorrect++;
            if (entry.confidence() != null) {
                int[] values = confidence.computeIfAbsent(entry.confidence(), ignored -> new int[2]);
                if (hit) values[0]++; else values[1]++;
            }
        }
        int tipsSubmitted = correct + incorrect;
        List<TipsConfidenceStatisticsResponse> confidenceStats = List.of(TipsConfidence.LOW, TipsConfidence.MEDIUM, TipsConfidence.HIGH).stream()
                .filter(confidence::containsKey)
                .map(level -> {
                    int[] values = confidence.get(level);
                    int total = values[0] + values[1];
                    return new TipsConfidenceStatisticsResponse(level, values[0], values[1], total, percentage(values[0], total));
                }).toList();
        return new TipsGameStatisticsResponse(correct, incorrect, missing, tipsSubmitted, percentage(correct, tipsSubmitted), confidenceStats);
    }

    private static TipsConfidence confidence(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return TipsConfidence.valueOf(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new ApiBadRequestException("INVALID_TIP_CONFIDENCE", "Die Tippsicherheit muss LOW, MEDIUM, HIGH oder leer sein.");
        }
    }

    private static String note(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String normalized = value.trim();
        if (normalized.length() > 2000) throw new ApiBadRequestException(
                "TIP_NOTE_TOO_LONG", "Eine Tippnotiz darf höchstens 2000 Zeichen enthalten."
        );
        return normalized;
    }

    private static Double percentage(int numerator, int denominator) {
        return denominator == 0 ? null : numerator * 100.0 / denominator;
    }

    private static ApiConflictException resolvedReadOnly() {
        return new ApiConflictException("TIPS_GAME_RESOLVED", "Der aufgelöste Tippstand ist schreibgeschützt. Öffnen Sie ihn bewusst wieder, um Tipps zu ändern.");
    }

    private String countryName(String code) {
        return countryNames.getOrDefault(code, code);
    }
}
