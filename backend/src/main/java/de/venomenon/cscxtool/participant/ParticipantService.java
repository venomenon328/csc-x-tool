package de.venomenon.cscxtool.participant;

import de.venomenon.cscxtool.shared.ApiBadRequestException;
import de.venomenon.cscxtool.shared.ApiConflictException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ParticipantService {

    private final ParticipantRepository repository;
    private final CountryCatalog countryCatalog;

    ParticipantService(ParticipantRepository repository, CountryCatalog countryCatalog) {
        this.repository = repository;
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
        Country country = countryCatalog.findRequired(request.countryCode());
        Participant participant = repository.create(
                requiredText(request.displayName(), "Der Anzeigename darf nicht leer sein."),
                country.code(),
                request.active() == null || request.active(),
                normalizedAliases(request.aliases())
        );
        return response(participant);
    }

    @Transactional
    ParticipantResponse update(long participantId, UpdateParticipantRequest request) {
        Participant existing = requireParticipant(participantId);
        Country country = countryCatalog.findRequired(request.countryCode());
        List<String> aliases = request.aliasesProvided() ? normalizedAliases(request.aliases()) : existing.aliases();
        if (!repository.update(
                participantId,
                requiredText(request.displayName(), "Der Anzeigename darf nicht leer sein."),
                country.code(),
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
        if (repository.isReferencedByContestEntry(participantId)) {
            throw new ApiConflictException(
                    "PARTICIPANT_IN_USE",
                    "Der Teilnehmer kann nicht gel\u00f6scht werden, weil ihm Wettbewerbsbeitr\u00e4ge zugeordnet sind."
            );
        }
        if (!repository.delete(participantId)) {
            throw new ParticipantNotFoundException(participantId);
        }
    }

    private ParticipantResponse response(Participant participant) {
        return ParticipantResponse.from(participant, countryCatalog.findRequired(participant.countryCode()));
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
