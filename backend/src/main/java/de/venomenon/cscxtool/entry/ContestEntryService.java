package de.venomenon.cscxtool.entry;

import de.venomenon.cscxtool.contest.ContestRepository;
import de.venomenon.cscxtool.participant.ParticipantNotFoundException;
import de.venomenon.cscxtool.publishedballot.PublishedBallotService;
import de.venomenon.cscxtool.shared.ApiBadRequestException;
import de.venomenon.cscxtool.shared.ApiConflictException;
import de.venomenon.cscxtool.show.ShowContext;
import de.venomenon.cscxtool.show.ShowNotFoundException;
import de.venomenon.cscxtool.song.YoutubeUrlNormalizer;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ContestEntryService {

    private final ContestEntryRepository repository;
    private final ClipboardEntryParser clipboardEntryParser;
    private final YoutubeUrlNormalizer youtubeUrlNormalizer;
    private final ContestRepository contests;
    private final HistoricalEntryImportParser historicalEntryImportParser;
    private final PublishedBallotService publishedBallots;

    ContestEntryService(
            ContestEntryRepository repository,
            ClipboardEntryParser clipboardEntryParser,
            YoutubeUrlNormalizer youtubeUrlNormalizer,
            ContestRepository contests,
            HistoricalEntryImportParser historicalEntryImportParser,
            PublishedBallotService publishedBallots
    ) {
        this.repository = repository;
        this.clipboardEntryParser = clipboardEntryParser;
        this.youtubeUrlNormalizer = youtubeUrlNormalizer;
        this.contests = contests;
        this.historicalEntryImportParser = historicalEntryImportParser;
        this.publishedBallots = publishedBallots;
    }

    List<ContestEntry> findAll(long showId) {
        requireShowContext(showId);
        return repository.findAllByShowId(showId);
    }

    @Transactional
    List<ContestEntry> reorder(long showId, ReorderContestEntriesRequest request) {
        requireCurrentShow(showId);
        if (request == null || request.entryIds() == null) {
            throw poolReorderConflict();
        }
        List<Long> submittedIds = request.entryIds();
        Set<Long> currentIds = repository.findAllByShowId(showId).stream().map(ContestEntry::id)
                .collect(java.util.stream.Collectors.toSet());
        Set<Long> uniqueSubmittedIds = new HashSet<>(submittedIds);
        if (currentIds.size() != submittedIds.size() || uniqueSubmittedIds.size() != submittedIds.size()
                || !currentIds.equals(uniqueSubmittedIds)) {
            throw poolReorderConflict();
        }
        repository.replacePool(showId, submittedIds);
        return repository.findAllByShowId(showId);
    }

    @Transactional
    ContestEntry create(long showId, CreateContestEntryRequest request) {
        ShowContext context = requireShowContext(showId);
        String artist = requiredText(request.artist(), "Der Interpret darf nicht leer sein.");
        String title = requiredText(request.title(), "Der Titel darf nicht leer sein.");
        if (context.currentContest()) {
            if (request.participantId() != null) throw currentEntryParticipantAssignmentForbidden();
            return repository.create(
                    showId, artist, title, requiredYoutubeUrl(request.youtubeUrl()), optionalText(request.comment())
            );
        }
        requireHistoricalListOpen(context);
        long participationId = requiredParticipationForShow(showId, request.participantId());
        return createHistoricalEntry(
                showId, artist, title, optionalHistoricalUrl(request.youtubeUrl()), optionalText(request.comment()), participationId
        );
    }

    @Transactional
    ContestEntry update(long showId, long entryId, UpdateContestEntryRequest request) {
        ShowContext context = requireShowContext(showId);
        String artist = requiredText(request.artist(), "Der Interpret darf nicht leer sein.");
        String title = requiredText(request.title(), "Der Titel darf nicht leer sein.");
        boolean updated;
        if (context.currentContest()) {
            if (request.participantId() != null) throw currentEntryParticipantAssignmentForbidden();
            updated = repository.update(
                    entryId, showId, artist, title, requiredYoutubeUrl(request.youtubeUrl()), optionalText(request.comment())
            );
        } else {
            requireHistoricalListOpen(context);
            updated = updateHistoricalEntry(
                    entryId, showId, artist, title, optionalHistoricalUrl(request.youtubeUrl()), optionalText(request.comment()),
                    requiredParticipationForShow(showId, request.participantId())
            );
        }
        if (!updated) {
            throw new ContestEntryNotFoundException(entryId, showId);
        }
        return repository.findByIdAndShowId(entryId, showId)
                .orElseThrow(() -> new ContestEntryNotFoundException(entryId, showId));
    }

    @Transactional
    ContestEntry updateAssessment(long showId, long entryId, UpdateContestEntryAssessmentRequest request) {
        requireCurrentShow(showId);
        if (request == null || !isValidAssessmentPair(request.assessment(), request.assessmentConfidence())) {
            throw new ApiBadRequestException(
                    "INVALID_ENTRY_ASSESSMENT",
                    "Einschätzung und Sicherheit müssen gemeinsam leer sein oder jeweils zwischen 1 und 5 liegen."
            );
        }
        if (!repository.updateAssessment(entryId, showId, request.assessment(), request.assessmentConfidence())) {
            throw new ContestEntryNotFoundException(entryId, showId);
        }
        return repository.findByIdAndShowId(entryId, showId)
                .orElseThrow(() -> new ContestEntryNotFoundException(entryId, showId));
    }

    @Transactional
    ContestEntry updateParticipantAssignment(long showId, long entryId, UpdateParticipantAssignmentRequest request) {
        requireCurrentShow(showId);
        if (!repository.isBallotClosed(showId)) {
            throw new ApiConflictException(
                    "PARTICIPANT_ASSIGNMENT_REQUIRES_CLOSED_BALLOT",
                    "Teilnehmer können erst nach dem Abschluss der eigenen Top 15 zugeordnet werden."
            );
        }
        ContestEntry entry = repository.findByIdAndShowId(entryId, showId)
                .orElseThrow(() -> new ContestEntryNotFoundException(entryId, showId));
        if (entry.ownEntry()) {
            throw new ApiConflictException(
                    "OWN_ENTRY_CHANGE_REQUIRES_REOPEN",
                    "Die eigene Einreichung wird vor dem Abschluss bewusst aufgelöst. Eine Änderung erfordert zuerst das Wiederöffnen der Abstimmung."
            );
        }
        Long participantId = request == null ? null : request.participantId();
        Long participationId = null;
        if (participantId != null) {
            if (!repository.participantExists(participantId)) {
                throw new ParticipantNotFoundException(participantId);
            }
            var participation = contests.findParticipationForShow(showId, participantId).orElseThrow(() -> new ApiConflictException(
                    "PARTICIPANT_NOT_IN_CONTEST",
                    "Der Teilnehmer nimmt nicht an der CSC-Ausgabe dieser Mottoshow teil."
            ));
            ContestEntryRepository.OwnEntryState ownState = ownEntryState(showId);
            if (Long.valueOf(participation.id()).equals(ownState.currentOwnParticipationId())) {
                throw new ApiConflictException(
                        "OWN_ENTRY_CHANGE_REQUIRES_REOPEN",
                        "Die eigene Einreichung wird vor dem Abschluss bewusst aufgelöst. Eine geschlossene Abstimmung muss dafür zuerst wieder geöffnet werden."
                );
            }
            if (!participation.active() && !participantId.equals(entry.participantId())) {
                throw new ApiConflictException(
                        "INACTIVE_PARTICIPANT_CANNOT_BE_ASSIGNED",
                        "Inaktive Teilnehmer können nicht neu einem Wettbewerbsbeitrag zugeordnet werden."
                );
            }
            repository.findEntryIdByParticipation(showId, participation.id())
                    .filter(assignedEntryId -> assignedEntryId != entry.id())
                    .ifPresent(assignedEntryId -> {
                        throw duplicateParticipantAssignment();
                    });
            participationId = participation.id();
            if (publishedBallots.assignmentWouldMakeOwnEntry(entryId, participationId)) {
                throw publishedBallotOwnEntryConflict();
            }
        }
        try {
            if (!repository.updateParticipantAssignment(entryId, showId, participationId)) {
                throw new ContestEntryNotFoundException(entryId, showId);
            }
        } catch (DataIntegrityViolationException exception) {
            if (isParticipantAssignmentUniqueConstraint(exception)) {
                throw duplicateParticipantAssignment();
            }
            throw exception;
        }
        return repository.findByIdAndShowId(entryId, showId)
                .orElseThrow(() -> new ContestEntryNotFoundException(entryId, showId));
    }

    @Transactional
    void updateOwnEntryResolution(long showId, UpdateOwnEntryResolutionRequest request) {
        requireCurrentShow(showId);
        if (request == null || request.resolution() == null
                || (request.resolution() != OwnEntryResolution.OWN_ENTRY && request.resolution() != OwnEntryResolution.NO_OWN_ENTRY)) {
            throw new ApiBadRequestException(
                    "INVALID_OWN_ENTRY_RESOLUTION",
                    "Lege bewusst die eigene Einreichung fest oder bestätige, dass keine eigene Einreichung existiert."
            );
        }
        ContestEntryRepository.OwnEntryState state = ownEntryState(showId);
        Long ownParticipationId = state.currentOwnParticipationId();
        if (ownParticipationId == null) {
            throw new ApiConflictException(
                    "OWN_PARTICIPATION_REQUIRED",
                    "Markiere zuerst im Teilnehmerfeld dieser CSC-Ausgabe deine eigene Teilnahme."
            );
        }
        if (repository.isBallotClosed(showId)) {
            throw new ApiConflictException(
                    "OWN_ENTRY_CHANGE_REQUIRES_REOPEN",
                    "Die abgeschlossene Abstimmung muss vor einer Änderung der eigenen Einreichung bewusst wieder geöffnet werden."
            );
        }
        if (request.resolution() == OwnEntryResolution.NO_OWN_ENTRY) {
            if (request.entryId() != null) {
                throw new ApiBadRequestException(
                        "INVALID_OWN_ENTRY_RESOLUTION", "Die Bestätigung ohne eigene Einreichung darf keinen Beitrag enthalten."
                );
            }
            clearResolvedOwnEntry(showId, state);
            repository.clearOwnEntryParticipationAssignments(showId, ownParticipationId);
            repository.updateOwnEntryResolution(showId, OwnEntryResolution.NO_OWN_ENTRY, ownParticipationId, null);
            return;
        }

        if (request.entryId() == null) {
            throw new ApiBadRequestException(
                    "OWN_ENTRY_REQUIRED", "Wähle den vorhandenen Wettbewerbsbeitrag aus, der deine eigene Einreichung ist."
            );
        }
        ContestEntry target = repository.findByIdAndShowId(request.entryId(), showId)
                .orElseThrow(() -> new ContestEntryNotFoundException(request.entryId(), showId));
        Long targetActualParticipationId = repository.findActualParticipationId(target.id(), showId).orElse(null);
        if (targetActualParticipationId != null && !targetActualParticipationId.equals(ownParticipationId)) {
            throw new ApiConflictException(
                    "OWN_ENTRY_ALREADY_ASSIGNED_TO_OTHER_PARTICIPANT",
                    "Ein bereits anders zugeordneter Beitrag kann nicht als eigene Einreichung markiert werden."
            );
        }
        if (publishedBallots.assignmentWouldMakeOwnEntry(target.id(), ownParticipationId)) {
            throw publishedBallotOwnEntryConflict();
        }
        if (target.rankingPosition() != null && !request.confirmsRankingRemoval()) {
            throw new ApiConflictException(
                    "OWN_ENTRY_RANKING_REMOVAL_CONFIRMATION_REQUIRED",
                    "Der Beitrag ist bereits gerankt. Bestätige bewusst, dass er atomar aus deiner Rangliste entfernt wird."
            );
        }
        clearResolvedOwnEntry(showId, state);
        repository.clearOwnEntryParticipationAssignments(showId, ownParticipationId);
        repository.assignOwnEntry(showId, target.id(), ownParticipationId);
        if (target.rankingPosition() != null) {
            repository.replaceRanking(showId, repository.findRankedEntryIds(showId).stream()
                    .filter(entryId -> entryId != target.id()).toList());
        }
        repository.updateOwnEntryResolution(showId, OwnEntryResolution.OWN_ENTRY, ownParticipationId, target.id());
    }

    @Transactional
    void delete(long showId, long entryId) {
        ShowContext context = requireShowContext(showId);
        if (!context.currentContest()) requireHistoricalListOpen(context);
        ContestEntry entry = repository.findByIdAndShowId(entryId, showId)
                .orElseThrow(() -> new ContestEntryNotFoundException(entryId, showId));
        if (entry.ownEntry()) {
            throw new ApiConflictException(
                    "OWN_ENTRY_RESOLUTION_REQUIRED",
                    "Die markierte eigene Einreichung kann erst nach einer bewussten Änderung der Eigenauflösung gelöscht werden."
            );
        }
        if (entry.rankingPosition() != null && repository.isBallotClosed(showId)) {
            throw new ApiConflictException(
                    "BALLOT_REOPEN_REQUIRED",
                    "Die abgeschlossene Abstimmung muss vor einer Rangänderung bewusst wieder geöffnet werden."
            );
        }
        if (publishedBallots.hasReferencesForEntry(entryId)) {
            throw new ApiConflictException(
                    "PUBLISHED_BALLOT_ENTRY_REFERENCE",
                    "Ein in veröffentlichten Stimmzetteln verwendeter Beitrag darf nicht gelöscht werden."
            );
        }
        if (!repository.delete(entryId, showId)) {
            throw new ContestEntryNotFoundException(entryId, showId);
        }
        repository.replacePool(showId, repository.findPoolEntryIds(showId));
        if (entry.rankingPosition() != null) {
            repository.replaceRanking(showId, repository.findRankedEntryIds(showId));
        }
    }

    List<ImportPreviewLine> preview(long showId, ImportPreviewRequest request) {
        requireCurrentShow(showId);
        String html = request == null ? null : request.html();
        String text = request == null ? null : request.text();
        if ((html == null || html.isBlank()) && (text == null || text.isBlank())) {
            throw new ApiBadRequestException("EMPTY_IMPORT_PREVIEW", "Es wurde kein Zwischenablageinhalt erkannt.");
        }
        return markPossibleDuplicates(clipboardEntryParser.parse(html, text), repository.findAllByShowId(showId));
    }

    @Transactional
    List<ContestEntry> importEntries(long showId, ImportContestEntriesRequest request) {
        requireCurrentShow(showId);
        if (request == null || request.entries() == null || request.entries().isEmpty()) {
            throw new ApiBadRequestException(
                    "EMPTY_IMPORT", "Wähle mindestens einen vollständigen Beitrag für den Import aus."
            );
        }

        List<ValidatedImportEntry> validatedEntries = request.entries().stream().map(this::validateImportEntry).toList();
        for (ValidatedImportEntry entry : validatedEntries) {
            repository.create(showId, entry.artist(), entry.title(), entry.youtubeUrl(), entry.comment());
        }
        return repository.findAllByShowId(showId);
    }

    List<HistoricalImportPreviewLine> previewHistorical(long showId, ImportPreviewRequest request) {
        ShowContext context = requireShowContext(showId);
        requireHistoricalListOpen(context);
        String html = request == null ? null : request.html();
        String text = request == null ? null : request.text();
        if ((html == null || html.isBlank()) && (text == null || text.isBlank())) {
            throw new ApiBadRequestException("EMPTY_IMPORT_PREVIEW", "Es wurde kein Zwischenablageinhalt erkannt.");
        }
        return markHistoricalPossibleDuplicates(
                historicalEntryImportParser.parse(html, text, repository.findHistoricalImportParticipants(showId)),
                repository.findAllByShowId(showId)
        );
    }

    List<AssignmentImportPreviewLine> previewAssignments(long showId, ImportPreviewRequest request) {
        requireAssignmentImportOpen(showId);
        String html = request == null ? null : request.html();
        String text = request == null ? null : request.text();
        if ((html == null || html.isBlank()) && (text == null || text.isBlank())) {
            throw new ApiBadRequestException("EMPTY_IMPORT_PREVIEW", "Es wurde kein Zwischenablageinhalt erkannt.");
        }
        List<HistoricalImportParticipant> participants = repository.findHistoricalImportParticipants(showId);
        Map<Long, Long> participationByParticipant = new HashMap<>();
        participants.forEach(participant -> participationByParticipant.put(participant.participantId(), participant.participationId()));
        ContestEntryRepository.OwnEntryState own = ownEntryState(showId);
        var activeByParticipation = contests.findParticipations(own.contestId()).stream()
                .collect(java.util.stream.Collectors.toMap(participation -> participation.id(), participation -> participation.active()));
        List<ContestEntry> entries = repository.findAllByShowId(showId);
        List<AssignmentImportPreviewLine> result = new ArrayList<>();
        Set<Long> seenEntries = new HashSet<>();
        Set<Long> seenParticipations = new HashSet<>();
        for (HistoricalImportPreviewLine source : historicalEntryImportParser.parse(html, text, participants)) {
            List<ImportWarning> warnings = new ArrayList<>(source.warnings());
            List<ContestEntry> urlMatches = List.of();
            if (source.youtubeUrl() != null) {
                try {
                    String sourceUrl = youtubeUrlNormalizer.normalize(source.youtubeUrl());
                    urlMatches = entries.stream().filter(entry -> entry.youtubeUrl() != null &&
                            normalizedYoutubeUrl(entry.youtubeUrl()).equals(sourceUrl)).toList();
                } catch (ApiBadRequestException ignored) {
                    // The historical parser already reports invalid links. Text matching remains available.
                }
            }
            List<ContestEntry> textMatches = source.artist() == null || source.title() == null ? List.of() : entries.stream()
                    .filter(entry -> assignmentSongKey(entry.artist(), entry.title())
                            .equals(assignmentSongKey(source.artist(), source.title()))).toList();
            ContestEntry matched = null;
            if (urlMatches.size() > 1 || textMatches.size() > 1) {
                warnings.add(new ImportWarning("AMBIGUOUS_ENTRY", "Mehrere vorhandene Beiträge passen; bitte einen Beitrag wählen."));
            } else if (!urlMatches.isEmpty() && !textMatches.isEmpty() && urlMatches.getFirst().id() != textMatches.getFirst().id()) {
                warnings.add(new ImportWarning("ENTRY_SIGNAL_CONFLICT", "Link und Interpret/Titel zeigen auf verschiedene Beiträge."));
            } else if (!urlMatches.isEmpty()) {
                matched = urlMatches.getFirst();
            } else if (!textMatches.isEmpty()) {
                matched = textMatches.getFirst();
            } else {
                warnings.add(new ImportWarning("ENTRY_NOT_FOUND", "Kein vorhandener Beitrag passt; bitte einen Beitrag wählen."));
            }
            Long participationId = source.participantId() == null ? null : participationByParticipant.get(source.participantId());
            if (matched != null && !seenEntries.add(matched.id())) {
                warnings.add(new ImportWarning("DUPLICATE_ENTRY", "Dieser Beitrag kommt im Block mehrfach vor."));
            }
            if (participationId != null && !seenParticipations.add(participationId)) {
                warnings.add(new ImportWarning("DUPLICATE_PARTICIPATION", "Dieser Einreichende kommt im Block mehrfach vor."));
            }
            if (matched != null && participationId != null) {
                if ((matched.ownEntry() && !participationId.equals(matched.contestParticipationId()))
                        || (participationId.equals(own.currentOwnParticipationId()) && !matched.ownEntry())) {
                    warnings.add(new ImportWarning("OWN_ENTRY_CHANGE_REQUIRES_REOPEN", "Die bestätigte eigene Einreichung darf hier nicht geändert werden."));
                }
                if (Boolean.FALSE.equals(activeByParticipation.get(participationId))
                        && !participationId.equals(matched.contestParticipationId())) {
                    warnings.add(new ImportWarning("INACTIVE_PARTICIPANT_CANNOT_BE_ASSIGNED", "Inaktive Teilnehmer können nicht neu zugeordnet werden."));
                }
                if (!participationId.equals(matched.contestParticipationId())
                        && publishedBallots.assignmentWouldMakeOwnEntry(matched.id(), participationId)) {
                    warnings.add(new ImportWarning("PUBLISHED_BALLOT_OWN_ENTRY_CONFLICT", "Die Zuordnung würde einen veröffentlichten Stimmzettel ungültig machen."));
                }
            }
            String action = matched == null || participationId == null ? null
                    : matched.contestParticipationId() == null ? "NEW"
                    : matched.contestParticipationId().equals(participationId) ? "UNCHANGED" : "REPLACE";
            result.add(new AssignmentImportPreviewLine(
                    source.sourcePosition(), source.sourceText(), source.artist(), source.title(), source.youtubeUrl(),
                    source.participantToken(), source.countryToken(), source.participantId(), participationId,
                    matched == null ? null : matched.id(), matched == null ? null : matched.contestParticipationId(),
                    action, matched == null || participationId == null ? ImportPreviewStatus.INCOMPLETE
                            : warnings.isEmpty() ? ImportPreviewStatus.READY : ImportPreviewStatus.WARNING,
                    List.copyOf(warnings)
            ));
        }
        return List.copyOf(result);
    }

    @Transactional
    List<ContestEntry> importAssignments(long showId, AssignmentImportRequest request) {
        requireAssignmentImportOpen(showId);
        if (request == null || request.assignments() == null || request.assignments().isEmpty()) {
            throw new ApiBadRequestException("EMPTY_IMPORT", "Wähle mindestens eine Zuordnung für den Import aus.");
        }
        List<ContestEntry> entries = repository.findAllByShowId(showId);
        Map<Long, ContestEntry> byId = new HashMap<>();
        entries.forEach(entry -> byId.put(entry.id(), entry));
        Map<Long, Long> finalAssignments = new LinkedHashMap<>();
        entries.forEach(entry -> finalAssignments.put(entry.id(), entry.contestParticipationId()));
        var own = ownEntryState(showId);
        var participations = contests.findParticipations(own.contestId()).stream()
                .collect(java.util.stream.Collectors.toMap(participation -> participation.id(), participation -> participation));
        Set<Long> selected = new HashSet<>();
        List<AssignmentImportItem> changes = new ArrayList<>();
        for (AssignmentImportItem item : request.assignments()) {
            if (item == null || item.entryId() == null || item.participationId() == null || !selected.add(item.entryId())) {
                throw new ApiBadRequestException("INVALID_ASSIGNMENT_IMPORT", "Jeder ausgewählte Beitrag muss genau einmal mit einem Einreichenden vorkommen.");
            }
            ContestEntry entry = byId.get(item.entryId());
            if (entry == null) throw new ApiConflictException("ASSIGNMENT_ENTRY_CHANGED", "Ein ausgewählter Beitrag existiert in dieser Show nicht mehr.");
            if (!java.util.Objects.equals(entry.contestParticipationId(), item.expectedParticipationId())) {
                throw new ApiConflictException("ASSIGNMENT_IMPORT_STALE", "Eine Zuordnung hat sich seit der Vorschau geändert. Bitte den Block erneut prüfen.");
            }
            var participation = participations.get(item.participationId());
            if (participation == null) throw new ApiConflictException("PARTICIPANT_NOT_IN_CONTEST", "Ein Einreichender gehört nicht zum Teilnehmerfeld dieser CSC-Ausgabe.");
            if (entry.ownEntry() && !item.participationId().equals(entry.contestParticipationId())) {
                throw ownEntryAssignmentConflict();
            }
            if (item.participationId().equals(own.currentOwnParticipationId()) && !item.participationId().equals(entry.contestParticipationId())) {
                throw ownEntryAssignmentConflict();
            }
            if (!participation.active() && !item.participationId().equals(entry.contestParticipationId())) {
                throw new ApiConflictException("INACTIVE_PARTICIPANT_CANNOT_BE_ASSIGNED", "Inaktive Teilnehmer können nicht neu zugeordnet werden.");
            }
            if (!item.participationId().equals(entry.contestParticipationId())) {
                if (entry.contestParticipationId() != null && !item.confirmReplacement()) {
                    throw new ApiConflictException("ASSIGNMENT_REPLACEMENT_CONFIRMATION_REQUIRED", "Bestätige den Ersatz einer vorhandenen Zuordnung ausdrücklich.");
                }
                if (publishedBallots.assignmentWouldMakeOwnEntry(entry.id(), item.participationId())) throw publishedBallotOwnEntryConflict();
                changes.add(item);
            }
            finalAssignments.put(entry.id(), item.participationId());
        }
        Set<Long> usedParticipations = new HashSet<>();
        for (Long participationId : finalAssignments.values()) {
            if (participationId != null && !usedParticipations.add(participationId)) throw duplicateParticipantAssignment();
        }
        try {
            repository.clearParticipantAssignments(showId, changes.stream().map(AssignmentImportItem::entryId).toList());
            for (AssignmentImportItem change : changes) {
                if (!repository.updateParticipantAssignment(change.entryId(), showId, change.participationId())) {
                    throw new ApiConflictException("ASSIGNMENT_IMPORT_STALE", "Ein Beitrag hat sich während des Imports geändert.");
                }
            }
        } catch (DataIntegrityViolationException exception) {
            if (isParticipantAssignmentUniqueConstraint(exception)) throw duplicateParticipantAssignment();
            throw exception;
        }
        return repository.findAllByShowId(showId);
    }

    private void requireAssignmentImportOpen(long showId) {
        requireCurrentShow(showId);
        if (!repository.isBallotClosed(showId)) throw new ApiConflictException(
                "PARTICIPANT_ASSIGNMENT_REQUIRES_CLOSED_BALLOT", "Einreichende können erst nach Abschluss der eigenen Top 15 importiert werden."
        );
    }

    private String normalizedYoutubeUrl(String value) {
        try { return youtubeUrlNormalizer.normalize(value); }
        catch (ApiBadRequestException ignored) { return ""; }
    }

    private static String assignmentSongKey(String artist, String title) {
        return HistoricalEntryImportText.normalized(artist) + "\u001f" + HistoricalEntryImportText.normalized(title);
    }

    private static ApiConflictException ownEntryAssignmentConflict() {
        return new ApiConflictException("OWN_ENTRY_CHANGE_REQUIRES_REOPEN", "Die eigene Einreichung kann erst nach bewusstem Wiederöffnen geändert werden.");
    }

    @Transactional
    List<ContestEntry> importHistoricalEntries(long showId, HistoricalImportEntriesRequest request) {
        ShowContext context = requireShowContext(showId);
        requireHistoricalListOpen(context);
        if (request == null || request.entries() == null || request.entries().isEmpty()) {
            throw new ApiBadRequestException(
                    "EMPTY_IMPORT", "Wähle mindestens einen vollständigen Beitrag für den Import aus."
            );
        }
        List<ValidatedHistoricalImportEntry> entries = request.entries().stream()
                .map(entry -> validateHistoricalImportEntry(showId, entry)).toList();
        Set<Long> participantIds = new HashSet<>();
        for (ValidatedHistoricalImportEntry entry : entries) {
            if (!participantIds.add(entry.participantId())) throw duplicateParticipantAssignment();
            Optional<ContestEntry> existing = repository.findEntryIdByParticipation(showId, entry.participationId())
                    .flatMap(id -> repository.findByIdAndShowId(id, showId));
            if (entry.replaceEntryId() == null && existing.isPresent()) throw duplicateParticipantAssignment();
            if (entry.replaceEntryId() != null && (existing.isEmpty() || existing.get().id() != entry.replaceEntryId())) {
                throw new ApiConflictException(
                        "HISTORICAL_IMPORT_REPLACEMENT_CONFLICT",
                        "Ein Ersatzimport muss den bereits zugeordneten Beitrag desselben Teilnehmers ausdrücklich auswählen."
                );
            }
        }
        for (ValidatedHistoricalImportEntry entry : entries) {
            if (entry.replaceEntryId() == null) {
                createHistoricalEntry(
                        showId, entry.artist(), entry.title(), entry.youtubeUrl(), entry.comment(), entry.participationId()
                );
            } else if (!updateHistoricalEntry(
                    entry.replaceEntryId(), showId, entry.artist(), entry.title(), entry.youtubeUrl(), entry.comment(),
                    entry.participationId()
            )) {
                throw new ApiConflictException(
                        "HISTORICAL_IMPORT_REPLACEMENT_CONFLICT", "Der zu ersetzende Beitrag wurde nicht gefunden."
                );
            }
        }
        return repository.findAllByShowId(showId);
    }

    @Transactional
    void completeHistoricalEntryList(long showId) {
        ShowContext context = requireShowContext(showId);
        requireHistorical(context);
        if (repository.historicalEntryCount(showId) == 0) {
            throw new ApiConflictException(
                    "ENTRY_LIST_EMPTY", "Eine vollständige Songliste muss mindestens einen Beitrag enthalten."
            );
        }
        if (repository.unassignedHistoricalEntryCount(showId) > 0) {
            throw new ApiConflictException(
                    "ENTRY_LIST_HAS_UNASSIGNED_ENTRIES", "Alle Beiträge der Songliste benötigen eine gültige Contest-Teilnahme."
            );
        }
        repository.setEntryListComplete(showId, true);
    }

    @Transactional
    void reopenHistoricalEntryList(long showId) {
        ShowContext context = requireShowContext(showId);
        requireHistorical(context);
        if (publishedBallots.hasBallotsForShow(showId)) {
            throw new ApiConflictException(
                    "PUBLISHED_BALLOTS_EXIST",
                    "Eine Songliste mit veröffentlichten Stimmzetteln kann nicht wieder geöffnet werden."
            );
        }
        repository.setEntryListComplete(showId, false);
    }

    private List<HistoricalImportPreviewLine> markHistoricalPossibleDuplicates(
            List<HistoricalImportPreviewLine> lines, List<ContestEntry> existingEntries
    ) {
        Map<Long, ContestEntry> entriesByParticipant = existingEntries.stream()
                .filter(entry -> entry.participantId() != null)
                .collect(java.util.stream.Collectors.toMap(ContestEntry::participantId, entry -> entry));
        Set<String> existingArtistTitle = existingEntries.stream()
                .map(entry -> artistTitleKey(entry.artist(), entry.title()))
                .collect(java.util.stream.Collectors.toSet());
        Set<Long> seenParticipants = new HashSet<>();
        return lines.stream().map(line -> {
            if (line.participantId() == null) return line;
            ContestEntry existingParticipantEntry = entriesByParticipant.get(line.participantId());
            boolean duplicate = !seenParticipants.add(line.participantId()) || existingParticipantEntry != null
                    || (line.artist() != null && line.title() != null
                    && existingArtistTitle.contains(artistTitleKey(line.artist(), line.title())));
            return duplicate
                    ? line.withPossibleDuplicate(existingParticipantEntry == null ? null : existingParticipantEntry.id())
                    : line;
        }).toList();
    }

    private List<ImportPreviewLine> markPossibleDuplicates(List<ImportPreviewLine> lines, List<ContestEntry> existingEntries) {
        Set<String> existingYoutubeUrls = new HashSet<>();
        Set<String> existingArtistTitle = new HashSet<>();
        for (ContestEntry entry : existingEntries) {
            existingYoutubeUrls.add(normalized(entry.youtubeUrl()));
            existingArtistTitle.add(artistTitleKey(entry.artist(), entry.title()));
        }

        Map<String, Integer> youtubeCounts = new HashMap<>();
        Map<String, Integer> artistTitleCounts = new HashMap<>();
        for (ImportPreviewLine line : lines) {
            if (isSupportedPreviewYoutubeUrl(line.youtubeUrl())) {
                youtubeCounts.merge(normalized(line.youtubeUrl()), 1, Integer::sum);
            }
            if (line.artist() != null && line.title() != null) {
                artistTitleCounts.merge(artistTitleKey(line.artist(), line.title()), 1, Integer::sum);
            }
        }

        return lines.stream().map(line -> {
            boolean duplicate = (isSupportedPreviewYoutubeUrl(line.youtubeUrl())
                    && (existingYoutubeUrls.contains(normalized(line.youtubeUrl()))
                    || youtubeCounts.getOrDefault(normalized(line.youtubeUrl()), 0) > 1))
                    || (line.artist() != null && line.title() != null
                    && (existingArtistTitle.contains(artistTitleKey(line.artist(), line.title()))
                    || artistTitleCounts.getOrDefault(artistTitleKey(line.artist(), line.title()), 0) > 1));
            return duplicate ? line.withPossibleDuplicate() : line;
        }).toList();
    }

    private boolean isSupportedPreviewYoutubeUrl(String value) {
        if (value == null) {
            return false;
        }
        try {
            return youtubeUrlNormalizer.normalize(value).equals(value);
        } catch (ApiBadRequestException exception) {
            return false;
        }
    }

    private ValidatedImportEntry validateImportEntry(ImportContestEntryRequest entry) {
        if (entry == null) {
            throw new ApiBadRequestException("INVALID_IMPORT_ENTRY", "Ein ausgewählter Importbeitrag ist ungültig.");
        }
        return new ValidatedImportEntry(
                requiredText(entry.artist(), "Der Interpret eines Importbeitrags darf nicht leer sein."),
                requiredText(entry.title(), "Der Titel eines Importbeitrags darf nicht leer sein."),
                youtubeUrlNormalizer.normalize(entry.youtubeUrl()),
                optionalText(entry.comment())
        );
    }

    private ShowContext requireShowContext(long showId) {
        return repository.findShowContext(showId).orElseThrow(() -> new ShowNotFoundException(showId));
    }

    private void requireCurrentShow(long showId) {
        if (!requireShowContext(showId).currentContest()) {
            throw new ApiConflictException(
                    "CURRENT_SHOW_REQUIRED",
                    "Diese Funktion gehört zum aktuellen CSC-X-Workflow und ist für Archivshows nicht verfügbar."
            );
        }
    }

    private ContestEntryRepository.OwnEntryState ownEntryState(long showId) {
        return repository.findOwnEntryState(showId).orElseThrow(() -> new ShowNotFoundException(showId));
    }

    private void clearResolvedOwnEntry(long showId, ContestEntryRepository.OwnEntryState state) {
        if (state.resolution() != OwnEntryResolution.UNRESOLVED) {
            repository.updateOwnEntryResolution(showId, OwnEntryResolution.UNRESOLVED, null, null);
        }
        if (state.resolvedParticipationId() != null) {
            repository.clearOwnEntryParticipationAssignments(showId, state.resolvedParticipationId());
        }
    }

    private static void requireHistorical(ShowContext context) {
        if (context.currentContest()) {
            throw new ApiConflictException(
                    "HISTORICAL_SHOW_REQUIRED", "Diese Funktion ist ausschließlich für historische CSC-Ausgaben verfügbar."
            );
        }
    }

    private static void requireHistoricalListOpen(ShowContext context) {
        requireHistorical(context);
        if (context.entryListComplete()) {
            throw new ApiConflictException(
                    "ENTRY_LIST_REOPEN_REQUIRED",
                    "Die vollständige Songliste muss vor einer Korrektur bewusst wieder geöffnet werden."
            );
        }
    }

    private long requiredParticipationForShow(long showId, Long participantId) {
        if (participantId == null) {
            throw new ApiBadRequestException(
                    "HISTORICAL_ENTRY_PARTICIPANT_REQUIRED",
                    "Historische Beiträge benötigen einen Einreichenden aus dem Teilnehmerfeld."
            );
        }
        if (!repository.participantExists(participantId)) throw new ParticipantNotFoundException(participantId);
        return contests.findParticipationForShow(showId, participantId).orElseThrow(() -> new ApiConflictException(
                "PARTICIPANT_NOT_IN_CONTEST", "Der Teilnehmer nimmt nicht an der CSC-Ausgabe dieser Mottoshow teil."
        )).id();
    }

    private ValidatedHistoricalImportEntry validateHistoricalImportEntry(long showId, HistoricalImportEntryRequest entry) {
        if (entry == null) {
            throw new ApiBadRequestException("INVALID_IMPORT_ENTRY", "Ein ausgewählter Importbeitrag ist ungültig.");
        }
        long participantId = entry.participantId() == null ? -1 : entry.participantId();
        long participationId = requiredParticipationForShow(showId, entry.participantId());
        return new ValidatedHistoricalImportEntry(
                requiredText(entry.artist(), "Der Interpret eines Importbeitrags darf nicht leer sein."),
                requiredText(entry.title(), "Der Titel eines Importbeitrags darf nicht leer sein."),
                optionalHistoricalUrl(entry.youtubeUrl()), optionalText(entry.comment()), participantId, participationId,
                entry.replaceEntryId()
        );
    }

    private ContestEntry createHistoricalEntry(
            long showId, String artist, String title, String youtubeUrl, String comment, long participationId
    ) {
        repository.findEntryIdByParticipation(showId, participationId).ifPresent(existing -> {
            throw duplicateParticipantAssignment();
        });
        try {
            return repository.create(showId, artist, title, youtubeUrl, comment, participationId);
        } catch (DataIntegrityViolationException exception) {
            if (isParticipantAssignmentUniqueConstraint(exception)) {
                throw duplicateParticipantAssignment();
            }
            throw exception;
        }
    }

    private boolean updateHistoricalEntry(
            long entryId, long showId, String artist, String title, String youtubeUrl, String comment, long participationId
    ) {
        if (publishedBallots.assignmentWouldMakeOwnEntry(entryId, participationId)) {
            throw publishedBallotOwnEntryConflict();
        }
        repository.findEntryIdByParticipation(showId, participationId)
                .filter(existingEntryId -> existingEntryId != entryId)
                .ifPresent(existing -> {
                    throw duplicateParticipantAssignment();
                });
        try {
            return repository.updateHistorical(
                    entryId, showId, artist, title, youtubeUrl, comment, participationId
            );
        } catch (DataIntegrityViolationException exception) {
            if (isParticipantAssignmentUniqueConstraint(exception)) {
                throw duplicateParticipantAssignment();
            }
            throw exception;
        }
    }

    private String requiredYoutubeUrl(String value) {
        return youtubeUrlNormalizer.normalize(requiredText(value, "Der YouTube-Link darf nicht leer sein."));
    }

    private static String optionalHistoricalUrl(String value) {
        String normalized = optionalText(value);
        if (normalized == null) return null;
        try {
            URI uri = URI.create(normalized);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException();
            }
            return normalized;
        } catch (IllegalArgumentException exception) {
            throw new ApiBadRequestException(
                    "INVALID_HISTORICAL_SOURCE_URL", "Der optionale Quelllink muss eine HTTP- oder HTTPS-Adresse sein."
            );
        }
    }

    private static ApiConflictException publishedBallotOwnEntryConflict() {
        return new ApiConflictException(
                "PUBLISHED_BALLOT_OWN_ENTRY_CONFLICT",
                "Die Teilnehmerzuordnung würde einen bestehenden veröffentlichten Stimmzettel fachlich ungültig machen."
        );
    }

    private static ApiConflictException duplicateParticipantAssignment() {
        return new ApiConflictException(
                "PARTICIPANT_ALREADY_ASSIGNED_IN_SHOW",
                "Ein Teilnehmer kann innerhalb derselben Mottoshow nur einem Wettbewerbsbeitrag zugeordnet werden."
        );
    }

    private static ApiConflictException currentEntryParticipantAssignmentForbidden() {
        return new ApiConflictException(
                "CURRENT_ENTRY_PARTICIPANT_ASSIGNMENT_FORBIDDEN",
                "Die Teilnehmerzuordnung der aktuellen Ausgabe erfolgt weiterhin erst nach Abschluss der eigenen Abstimmung."
        );
    }

    private static ApiConflictException poolReorderConflict() {
        return new ApiConflictException(
                "POOL_REORDER_CONFLICT",
                "Die manuelle Reihenfolge muss jeden aktuellen Beitrag dieser Mottoshow genau einmal enthalten."
        );
    }

    private static boolean isParticipantAssignmentUniqueConstraint(Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && message.contains(
                    "contest_entry.motto_show_id, contest_entry.contest_participation_id"
            )) {
                return true;
            }
        }
        return false;
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
        return value.trim();
    }

    private static String normalized(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String artistTitleKey(String artist, String title) {
        return normalized(artist) + "|" + normalized(title);
    }

    private static boolean isValidAssessmentPair(Integer assessment, Integer assessmentConfidence) {
        if (assessment == null || assessmentConfidence == null) {
            return assessment == null && assessmentConfidence == null;
        }
        return assessment >= 1 && assessment <= 5 && assessmentConfidence >= 1 && assessmentConfidence <= 5;
    }

    private record ValidatedImportEntry(String artist, String title, String youtubeUrl, String comment) {
    }

    private record ValidatedHistoricalImportEntry(
            String artist, String title, String youtubeUrl, String comment, long participantId, long participationId,
            Long replaceEntryId
    ) {
    }
}
