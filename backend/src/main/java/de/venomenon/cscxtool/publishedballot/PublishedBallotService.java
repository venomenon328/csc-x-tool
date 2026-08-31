package de.venomenon.cscxtool.publishedballot;

import de.venomenon.cscxtool.shared.ApiBadRequestException;
import de.venomenon.cscxtool.shared.ApiConflictException;
import de.venomenon.cscxtool.shared.CscPoints;
import de.venomenon.cscxtool.participant.CountryCatalog;
import de.venomenon.cscxtool.show.ShowNotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublishedBallotService {

    private final PublishedBallotRepository repository;
    private final PublishedBallotImportParser parser;
    private final CountryCatalog countries;

    PublishedBallotService(PublishedBallotRepository repository, PublishedBallotImportParser parser, CountryCatalog countries) {
        this.repository = repository;
        this.parser = parser;
        this.countries = countries;
    }

    PublishedBallotOverviewResponse overview(long showId) {
        PublishedBallotRepository.ShowFacts facts = facts(showId);
        List<PublishedBallotParticipant> participants = repository.findParticipants(showId);
        Map<Long, PublishedBallot> ballots = ballotsByParticipation(showId);
        List<PublishedBallotParticipantState> states = participants.stream().map(participant -> {
            PublishedBallot ballot = ballots.get(participant.participationId());
            PublishedBallotStatus status = ballot == null ? PublishedBallotStatus.UNERFASST : ballot.status();
            return new PublishedBallotParticipantState(participant.participationId(), participant.participantId(), participant.displayName(),
                    participant.countryCode(), participant.countryName(), status, ballot != null, ballot == null ? null : ballot.updatedAt());
        }).toList();
        int voted = (int) states.stream().filter(state -> state.status() == PublishedBallotStatus.ABGESTIMMT).count();
        int notVoted = (int) states.stream().filter(state -> state.status() == PublishedBallotStatus.NICHT_ABGESTIMMT).count();
        return new PublishedBallotOverviewResponse(showId, facts.entryListReady(), voted, notVoted, states.size() - voted - notVoted, states);
    }

    PublishedBallotDetailResponse detail(long showId, long participationId) {
        facts(showId);
        PublishedBallotParticipant participant = participantsById(showId).get(participationId);
        if (participant == null) throw new ApiConflictException("PARTICIPANT_NOT_IN_CONTEST", "Der Teilnehmer nimmt nicht an der CSC-Ausgabe dieser Mottoshow teil.");
        List<PublishedBallotEntry> entries = repository.findEntries(showId);
        PublishedBallot ballot = repository.findBallot(showId, participationId).orElse(null);
        Map<Long, Integer> ranks = new HashMap<>();
        if (ballot != null) for (PublishedBallotPosition position : repository.findPositions(ballot.id())) ranks.put(position.entryId(), position.rank());
        List<PublishedBallotPositionResponse> positions = entries.stream().filter(entry -> ranks.containsKey(entry.id()))
                .sorted(java.util.Comparator.comparingInt(entry -> ranks.get(entry.id())))
                .map(entry -> new PublishedBallotPositionResponse(ranks.get(entry.id()), CscPoints.pointsForRank(ranks.get(entry.id())), entry.id(),
                        entry.artist(), entry.title(), entry.youtubeUrl(), entry.submitterParticipantId(), entry.submitterDisplayName(), entry.submitterCountryCode()))
                .toList();
        PublishedBallotStatus status = ballot == null ? PublishedBallotStatus.UNERFASST : ballot.status();
        List<PublishedBallotDerivedEntryResponse> derived = entries.stream().map(entry -> derived(entry, participationId, status, ranks)).toList();
        return new PublishedBallotDetailResponse(showId, participationId, participant.participantId(), participant.displayName(), participant.countryCode(),
                status, ballot != null, positions, derived);
    }

    PublishedBallotStandingsResponse standings(long showId) {
        facts(showId);
        List<PublishedBallotEntry> entries = repository.findEntries(showId);
        Map<Long, StandingAccumulator> standings = new LinkedHashMap<>();
        for (PublishedBallotEntry entry : entries) standings.put(entry.id(), new StandingAccumulator(entry));

        List<PublishedBallotParticipant> participants = repository.findParticipants(showId);
        Map<Long, PublishedBallot> ballots = ballotsByParticipation(showId);
        int voted = 0;
        int notVoted = 0;
        for (PublishedBallotParticipant participant : participants) {
            PublishedBallot ballot = ballots.get(participant.participationId());
            PublishedBallotStatus status = ballot == null ? PublishedBallotStatus.UNERFASST : ballot.status();
            if (status == PublishedBallotStatus.ABGESTIMMT) voted++;
            if (status == PublishedBallotStatus.NICHT_ABGESTIMMT) notVoted++;
        }

        for (PublishedBallot ballot : ballots.values()) {
            if (ballot.status() != PublishedBallotStatus.ABGESTIMMT) continue;
            for (PublishedBallotPosition position : repository.findPositions(ballot.id())) {
                StandingAccumulator standing = standings.get(position.entryId());
                if (standing != null) standing.add(CscPoints.pointsForRank(position.rank()));
            }
        }

        List<StandingAccumulator> ordered = new ArrayList<>(standings.values());
        // The stable encounter order only makes equal scores readable; it is not a CSC tie-break rule.
        ordered.sort(Comparator.comparingInt(StandingAccumulator::points).reversed());
        List<PublishedBallotStandingEntryResponse> responseEntries = new ArrayList<>();
        int interimRank = 0;
        Integer previousPoints = null;
        for (int index = 0; index < ordered.size(); index++) {
            StandingAccumulator standing = ordered.get(index);
            if (previousPoints == null || standing.points() != previousPoints) interimRank = index + 1;
            responseEntries.add(standing.response(interimRank, countries));
            previousPoints = standing.points();
        }
        return new PublishedBallotStandingsResponse(showId, voted, notVoted, participants.size() - voted - notVoted, List.copyOf(responseEntries));
    }

    List<PublishedBallotPreviewBlock> preview(long showId, PublishedBallotImportPreviewRequest request) {
        requireReady(showId);
        if (request == null || ((request.html() == null || request.html().isBlank()) && (request.text() == null || request.text().isBlank()))) {
            throw new ApiBadRequestException("EMPTY_BALLOT_IMPORT_PREVIEW", "Es wurde kein Zwischenablageinhalt erkannt.");
        }
        Map<Long, PublishedBallotEntry> entries = entriesById(showId);
        List<PublishedBallotPreviewBlock> parsed = parser.parse(request.html(), request.text(), repository.findParticipants(showId),
                List.copyOf(entries.values()), ballotsByParticipation(showId).keySet());
        return parsed.stream().map(block -> annotatePreview(block, entries)).toList();
    }

    @Transactional
    PublishedBallotOverviewResponse importBallots(long showId, PublishedBallotImportBatchRequest request) {
        PublishedBallotRepository.ShowFacts facts = requireReady(showId);
        if (request == null || request.ballots() == null || request.ballots().isEmpty()) {
            throw new ApiBadRequestException("EMPTY_BALLOT_IMPORT", "Wählen Sie mindestens einen vollständigen Stimmzettel für den Import aus.");
        }
        Map<Long, PublishedBallotParticipant> participants = participantsById(showId);
        Map<Long, PublishedBallotEntry> entries = entriesById(showId);
        Map<Long, PublishedBallot> existing = ballotsByParticipation(showId);
        Set<Long> submittedParticipants = new HashSet<>();
        List<ValidatedBallot> validated = new ArrayList<>();
        for (PublishedBallotImportRequest ballot : request.ballots()) {
            validated.add(validateImport(showId, ballot, participants, entries, existing, submittedParticipants));
        }
        for (ValidatedBallot ballot : validated) {
            PublishedBallot current = existing.get(ballot.participationId());
            long ballotId;
            if (current == null) ballotId = repository.createBallot(showId, facts.contestId(), ballot.participationId(), PublishedBallotStatus.ABGESTIMMT);
            else {
                ballotId = current.id();
                repository.deletePositions(ballotId);
                repository.updateStatus(ballotId, PublishedBallotStatus.ABGESTIMMT);
            }
            repository.insertPositions(ballotId, ballot.positions());
        }
        return overview(showId);
    }

    @Transactional
    void updateStatus(long showId, long participationId, UpdatePublishedBallotStatusRequest request) {
        PublishedBallotRepository.ShowFacts facts = requireReady(showId);
        if (!participantsById(showId).containsKey(participationId)) {
            throw new ApiConflictException("PARTICIPANT_NOT_IN_CONTEST", "Der Teilnehmer nimmt nicht an der CSC-Ausgabe dieser Mottoshow teil.");
        }
        PublishedBallotStatus status;
        try { status = request == null ? null : PublishedBallotStatus.valueOf(request.status()); }
        catch (IllegalArgumentException | NullPointerException exception) { throw invalidStatus(); }
        if (status == null || status == PublishedBallotStatus.ABGESTIMMT) throw invalidStatus();
        PublishedBallot current = repository.findBallot(showId, participationId).orElse(null);
        if (status == PublishedBallotStatus.UNERFASST) {
            if (current != null) {
                repository.deletePositions(current.id());
                repository.deleteBallot(current.id());
            }
            return;
        }
        if (current == null) repository.createBallot(showId, facts.contestId(), participationId, PublishedBallotStatus.NICHT_ABGESTIMMT);
        else {
            repository.deletePositions(current.id());
            repository.updateStatus(current.id(), PublishedBallotStatus.NICHT_ABGESTIMMT);
        }
    }

    public boolean hasReferencesForEntry(long entryId) { return repository.hasBallotPositionsForEntry(entryId); }
    public boolean hasBallotsForShow(long showId) { return repository.hasPublishedBallots(showId); }
    public boolean assignmentWouldMakeOwnEntry(long entryId, long participationId) {
        return repository.assignmentWouldMakeOwnEntry(entryId, participationId);
    }

    private ValidatedBallot validateImport(
            long showId, PublishedBallotImportRequest ballot, Map<Long, PublishedBallotParticipant> participants,
            Map<Long, PublishedBallotEntry> entries, Map<Long, PublishedBallot> existing, Set<Long> submittedParticipants
    ) {
        if (ballot == null || ballot.participationId() == null || !participants.containsKey(ballot.participationId())) {
            throw new ApiBadRequestException("INVALID_BALLOT_VOTER", "Der Abstimmende muss eine vorhandene Contest-Teilnahme dieser Show sein.");
        }
        if (!submittedParticipants.add(ballot.participationId())) {
            throw new ApiConflictException("DUPLICATE_BALLOT_IN_BATCH", "Ein Teilnehmer darf im selben Import nur einen Stimmzettel besitzen.");
        }
        if (existing.containsKey(ballot.participationId()) && !ballot.replaceExisting()) {
            throw new ApiConflictException("PUBLISHED_BALLOT_EXISTS", "Ein vorhandener Stimmzettel wird nur nach ausdrücklicher Ersatzbestätigung überschrieben.");
        }
        if (ballot.positions() == null || ballot.positions().size() != 15) throw invalidPositions();
        Set<Integer> ranks = new HashSet<>();
        Set<Long> entryIds = new HashSet<>();
        for (PublishedBallotPositionRequest position : ballot.positions()) {
            if (position == null || position.rank() == null || position.rank() < 1 || position.rank() > 15 || !ranks.add(position.rank())
                    || position.entryId() == null || !entryIds.add(position.entryId())) throw invalidPositions();
            PublishedBallotEntry entry = entries.get(position.entryId());
            if (entry == null || entry.mottoShowId() != showId) throw new ApiConflictException(
                    "BALLOT_ENTRY_NOT_IN_SHOW", "Ein Stimmzettel darf nur bereits vorhandene Beiträge derselben Mottoshow enthalten."
            );
            if (ballot.participationId().equals(entry.submitterParticipationId())) throw new ApiConflictException(
                    "OWN_ENTRY_IN_BALLOT", "Die eigene Einreichung ist nicht wählbar und darf nicht im veröffentlichten Stimmzettel stehen."
            );
        }
        if (ranks.size() != 15 || !ranks.containsAll(java.util.stream.IntStream.rangeClosed(1, 15).boxed().toList())) throw invalidPositions();
        return new ValidatedBallot(ballot.participationId(), List.copyOf(ballot.positions()));
    }

    private PublishedBallotPreviewBlock annotatePreview(PublishedBallotPreviewBlock block, Map<Long, PublishedBallotEntry> entriesById) {
        List<BallotImportWarning> warnings = new ArrayList<>(block.warnings());
        Set<Long> entries = new HashSet<>();
        boolean invalid = false;
        for (PublishedBallotPreviewPosition position : block.positions()) {
            if (position.entryId() != null && !entries.add(position.entryId())) {
                warnings.add(new BallotImportWarning("DUPLICATE_ENTRY", "Derselbe Beitrag kommt mehrfach im Stimmzettel vor.")); invalid = true;
            }
            if (block.participationId() != null && position.entryId() != null) {
                PublishedBallotEntry entry = entriesById.get(position.entryId());
                if (entry != null && block.participationId().equals(entry.submitterParticipationId())) {
                    warnings.add(new BallotImportWarning("OWN_ENTRY_IN_BALLOT", "Die eigene Einreichung ist nicht wählbar.")); invalid = true;
                }
            }
        }
        return new PublishedBallotPreviewBlock(block.sourcePosition(), block.participationId(), block.participantId(), block.displayName(),
                block.countryCode(), block.existingBallot(), invalid ? "INCOMPLETE" : block.status(), block.positions(), List.copyOf(warnings));
    }

    private PublishedBallotDerivedEntryResponse derived(
            PublishedBallotEntry entry, long voterParticipationId, PublishedBallotStatus status, Map<Long, Integer> ranks
    ) {
        if (status == PublishedBallotStatus.ABGESTIMMT && entry.submitterParticipationId() != null
                && voterParticipationId == entry.submitterParticipationId()) {
            return response(entry, "OWN_ENTRY", null, null);
        }
        if (status == PublishedBallotStatus.ABGESTIMMT && ranks.containsKey(entry.id())) {
            int rank = ranks.get(entry.id()); return response(entry, "RANKED", rank, CscPoints.pointsForRank(rank));
        }
        if (status == PublishedBallotStatus.ABGESTIMMT) return response(entry, "OUTSIDE_TOP_15", null, 0);
        return response(entry, status == PublishedBallotStatus.NICHT_ABGESTIMMT ? "NO_BALLOT" : "UNKNOWN", null, null);
    }

    private static PublishedBallotDerivedEntryResponse response(PublishedBallotEntry entry, String state, Integer rank, Integer points) {
        return new PublishedBallotDerivedEntryResponse(entry.id(), entry.artist(), entry.title(), entry.youtubeUrl(), entry.submitterParticipantId(),
                entry.submitterDisplayName(), entry.submitterCountryCode(), state, rank, points);
    }
    private PublishedBallotRepository.ShowFacts facts(long showId) {
        return repository.findShowFacts(showId).orElseThrow(() -> new ShowNotFoundException(showId));
    }
    private PublishedBallotRepository.ShowFacts requireReady(long showId) {
        PublishedBallotRepository.ShowFacts facts = facts(showId);
        if (!facts.entryListReady()) throw new ApiConflictException(
                "ENTRY_LIST_COMPLETION_REQUIRED", "Stimmzettel können erst nach vollständiger Bestätigung der Songliste erfasst werden."
        );
        return facts;
    }
    private Map<Long, PublishedBallotParticipant> participantsById(long showId) {
        Map<Long, PublishedBallotParticipant> values = new HashMap<>();
        for (PublishedBallotParticipant participant : repository.findParticipants(showId)) values.put(participant.participationId(), participant);
        return values;
    }
    private Map<Long, PublishedBallotEntry> entriesById(long showId) {
        Map<Long, PublishedBallotEntry> values = new HashMap<>();
        for (PublishedBallotEntry entry : repository.findEntries(showId)) values.put(entry.id(), entry);
        return values;
    }
    private Map<Long, PublishedBallot> ballotsByParticipation(long showId) {
        Map<Long, PublishedBallot> values = new HashMap<>();
        for (PublishedBallot ballot : repository.findBallots(showId)) values.put(ballot.participationId(), ballot);
        return values;
    }
    private static ApiBadRequestException invalidPositions() {
        return new ApiBadRequestException("INVALID_BALLOT_POSITIONS", "Ein abgegebener Stimmzettel benötigt atomar jeden eindeutigen Rang von 1 bis 15.");
    }
    private static ApiBadRequestException invalidStatus() {
        return new ApiBadRequestException("INVALID_PUBLISHED_BALLOT_STATUS", "Erlaubt sind nur NICHT_ABGESTIMMT und UNERFASST als bewusste Statusaktion.");
    }
    private record ValidatedBallot(long participationId, List<PublishedBallotPositionRequest> positions) { }

    private static final class StandingAccumulator {
        private final PublishedBallotEntry entry;
        private int points;
        private int mentions;

        private StandingAccumulator(PublishedBallotEntry entry) { this.entry = entry; }
        private void add(int rankPoints) { points += rankPoints; mentions++; }
        private int points() { return points; }
        private PublishedBallotStandingEntryResponse response(int interimRank, CountryCatalog countries) {
            String countryName = entry.submitterCountryCode() == null ? null : countries.findRequired(entry.submitterCountryCode()).name();
            return new PublishedBallotStandingEntryResponse(interimRank, entry.id(), entry.artist(), entry.title(), entry.youtubeUrl(),
                    entry.submitterParticipantId(), entry.submitterDisplayName(), entry.submitterCountryCode(), countryName, points, mentions);
        }
    }
}
