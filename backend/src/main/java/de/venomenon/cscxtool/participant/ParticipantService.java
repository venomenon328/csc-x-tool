package de.venomenon.cscxtool.participant;

import de.venomenon.cscxtool.contest.ContestNotFoundException;
import de.venomenon.cscxtool.contest.ContestRepository;
import de.venomenon.cscxtool.contest.CreateContestParticipationRequest;
import de.venomenon.cscxtool.contest.UpdateContestParticipationRequest;
import de.venomenon.cscxtool.shared.ApiBadRequestException;
import de.venomenon.cscxtool.shared.ApiConflictException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

@Service
class ParticipantService {

    private final ParticipantRepository repository;
    private final ContestRepository contests;
    private final CountryCatalog countryCatalog;

    ParticipantService(ParticipantRepository repository, ContestRepository contests, CountryCatalog countryCatalog) {
        this.repository = repository;
        this.contests = contests;
        this.countryCatalog = countryCatalog;
    }

    List<ParticipantResponse> findAll(String query, boolean includeInactive) {
        String normalizedQuery = optionalText(query);
        return repository.findAll(includeInactive).stream()
                .filter(participant -> matches(participant, normalizedQuery))
                .map(this::response)
                .toList();
    }

    ParticipantResponse findById(long participantId) {
        return response(requireParticipant(participantId));
    }

    @Transactional
    ParticipantResponse create(CreateParticipantRequest request) {
        Participant participant = repository.create(
                requiredText(request.displayName(), "Der Anzeigename darf nicht leer sein."),
                request.active() == null || request.active(),
                normalizedAliases(request.aliases())
        );
        return response(participant);
    }

    @Transactional
    ParticipantResponse update(long participantId, UpdateParticipantRequest request) {
        Participant existing = requireParticipant(participantId);
        List<String> aliases = request.aliasesProvided() ? normalizedAliases(request.aliases()) : existing.aliases();
        if (!repository.update(
                participantId,
                requiredText(request.displayName(), "Der Anzeigename darf nicht leer sein."),
                request.active() == null ? existing.active() : request.active()
        )) {
            throw new ParticipantNotFoundException(participantId);
        }
        if (request.aliasesProvided()) {
            repository.replaceAliases(participantId, aliases);
        }
        return response(requireParticipant(participantId));
    }

    @Transactional
    void delete(long participantId) {
        requireParticipant(participantId);
        if (repository.isReferencedByContestParticipation(participantId)) {
            throw new ApiConflictException(
                    "PARTICIPANT_IN_USE",
                    "Der Teilnehmer kann nicht gel\u00f6scht werden, weil er noch an einer CSC-Ausgabe teilnimmt."
            );
        }
        if (!repository.delete(participantId)) {
            throw new ParticipantNotFoundException(participantId);
        }
    }

    List<ContestParticipantResponse> findAllForContest(long contestId, String query, boolean includeInactive) {
        requireContest(contestId);
        String normalizedQuery = optionalText(query);
        return repository.findAllByContest(contestId, includeInactive).stream()
                .filter(participant -> matches(participant, normalizedQuery))
                .map(participant -> ContestParticipantResponse.from(participant, countryCatalog.findRequired(participant.countryCode())))
                .toList();
    }

    @Transactional
    ContestParticipantResponse createParticipation(long contestId, CreateContestParticipationRequest request) {
        requireContest(contestId);
        requireParticipant(request.participantId());
        Country country = countryCatalog.findRequired(request.countryCode());
        try {
            contests.createParticipation(contestId, request.participantId(), country.code(), request.active() == null || request.active());
        } catch (DataIntegrityViolationException exception) {
            throw new ApiConflictException("DUPLICATE_CONTEST_PARTICIPATION", "Dieser Teilnehmer nimmt bereits an dieser CSC-Ausgabe teil.");
        }
        return requireContestParticipant(contestId, request.participantId());
    }

    @Transactional
    ContestParticipantResponse updateParticipation(long contestId, long participantId, UpdateContestParticipationRequest request) {
        requireContest(contestId);
        Country country = countryCatalog.findRequired(request.countryCode());
        var existing = contests.findParticipation(contestId, participantId).orElseThrow(() -> missingParticipation(participantId));
        if (!contests.updateParticipation(contestId, participantId, country.code(), request.active() == null ? existing.active() : request.active())) {
            throw missingParticipation(participantId);
        }
        return requireContestParticipant(contestId, participantId);
    }

    @Transactional
    void deleteParticipation(long contestId, long participantId) {
        requireContest(contestId);
        if (contests.findParticipation(contestId, participantId).isEmpty()) {
            throw missingParticipation(participantId);
        }
        if (contests.participationIsReferenced(contestId, participantId)) {
            throw new ApiConflictException(
                    "CONTEST_PARTICIPATION_IN_USE",
                    "Die Teilnahme kann nicht gel\u00f6scht werden, weil sie von Wettbewerbsbeitr\u00e4gen oder Ergebnisdaten verwendet wird."
            );
        }
        if (!contests.deleteParticipation(contestId, participantId)) {
            throw missingParticipation(participantId);
        }
    }

    private ParticipantResponse response(Participant participant) {
        return ParticipantResponse.from(participant);
    }

    private Participant requireParticipant(long participantId) {
        return repository.findById(participantId).orElseThrow(() -> new ParticipantNotFoundException(participantId));
    }

    private static boolean matches(Participant participant, String query) {
        if (query == null) {
            return true;
        }
        return normalizedForSearch(participant.displayName()).contains(query)
                || participant.aliases().stream().map(ParticipantService::normalizedForSearch).anyMatch(alias -> alias.contains(query));
    }

    private static boolean matches(ContestParticipant participant, String query) {
        if (query == null) {
            return true;
        }
        return normalizedForSearch(participant.displayName()).contains(query)
                || participant.aliases().stream().map(ParticipantService::normalizedForSearch).anyMatch(alias -> alias.contains(query));
    }

    private ContestParticipantResponse requireContestParticipant(long contestId, long participantId) {
        return findAllForContest(contestId, null, true).stream()
                .filter(participant -> participant.id() == participantId)
                .findFirst()
                .orElseThrow(() -> missingParticipation(participantId));
    }

    private void requireContest(long contestId) {
        if (!contests.exists(contestId)) {
            throw new ContestNotFoundException(contestId);
        }
    }

    private static ApiConflictException missingParticipation(long participantId) {
        return new ApiConflictException(
                "PARTICIPANT_NOT_IN_CONTEST",
                "Der Teilnehmer mit der ID " + participantId + " nimmt nicht an dieser CSC-Ausgabe teil."
        );
    }

    private static List<String> normalizedAliases(List<String> aliases) {
        if (aliases == null) {
            return List.of();
        }
        Set<String> normalizedValues = new HashSet<>();
        return aliases.stream().map(alias -> requiredText(alias, "Ein Alias darf nicht leer sein.")).peek(alias -> {
            if (!normalizedValues.add(normalizedForSearch(alias))) {
                throw new ApiBadRequestException(
                        "DUPLICATE_PARTICIPANT_ALIAS",
                        "Ein Alias darf pro Teilnehmer nur einmal vergeben werden."
                );
            }
        }).toList();
    }

    private static String requiredText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new ApiBadRequestException("VALIDATION_ERROR", message);
        }
        return value.trim();
    }

    private static String optionalText(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return normalizedForSearch(value.trim());
    }

    private static String normalizedForSearch(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
