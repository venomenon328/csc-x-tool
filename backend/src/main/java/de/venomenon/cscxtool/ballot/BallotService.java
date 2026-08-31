package de.venomenon.cscxtool.ballot;

import de.venomenon.cscxtool.shared.ApiConflictException;
import de.venomenon.cscxtool.show.ShowNotFoundException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class BallotService {

    private final BallotRepository repository;
    private final BallotRenderer renderer;

    BallotService(BallotRepository repository, BallotRenderer renderer) {
        this.repository = repository;
        this.renderer = renderer;
    }

    BallotResponse find(long showId) {
        requireShow(showId);
        return response(showId);
    }

    @Transactional
    BallotRankingResponse reorder(long showId, ReorderBallotRequest request) {
        requireShow(showId);
        if (repository.isClosed(showId)) {
            throw reopenRequired();
        }

        List<Long> rankedEntryIds = request.rankedEntryIds();
        List<Long> unrankedEntryIds = request.unrankedEntryIds();
        List<Long> submittedIds = java.util.stream.Stream.concat(rankedEntryIds.stream(), unrankedEntryIds.stream()).toList();
        Set<Long> currentIds = new HashSet<>(repository.findAllEntryIds(showId));
        Set<Long> uniqueSubmittedIds = new HashSet<>(submittedIds);

        if (currentIds.size() != submittedIds.size() || uniqueSubmittedIds.size() != submittedIds.size()
                || !currentIds.equals(uniqueSubmittedIds)) {
            throw new ApiConflictException(
                    "BALLOT_REORDER_CONFLICT",
                    "Die Rangliste und der ungeordnete Pool m\u00fcssen zusammen jeden aktuellen Beitrag dieser Mottoshow genau einmal enthalten."
            );
        }
        BallotRepository.OwnEntryState ownEntry = repository.findOwnEntryState(showId);
        if (ownEntry.ownEntryId() != null && rankedEntryIds.contains(ownEntry.ownEntryId())) {
            throw ownEntryCannotBeRanked();
        }

        repository.replaceRanking(showId, rankedEntryIds);
        return new BallotRankingResponse(List.copyOf(rankedEntryIds), List.copyOf(unrankedEntryIds));
    }

    @Transactional
    BallotResponse close(long showId) {
        requireShow(showId);
        if (repository.isClosed(showId)) {
            throw new ApiConflictException("BALLOT_ALREADY_CLOSED", "Die Abstimmung ist bereits abgeschlossen.");
        }

        BallotRepository.OwnEntryState ownEntry = repository.findOwnEntryState(showId);
        validateOwnEntryResolution(ownEntry);
        List<BallotRepository.RankedEntry> rankedEntries = repository.findRankedEntries(showId);
        validateTopFifteen(rankedEntries, ownEntry.ownEntryId());
        repository.makeAllSnapshotsHistorical(showId);
        long snapshotId = repository.createSnapshot(showId, repository.nextSnapshotNumber(showId));
        repository.createSnapshotItems(snapshotId, rankedEntries.subList(0, 15));
        repository.markClosed(showId);
        return response(showId);
    }

    @Transactional
    BallotResponse reopen(long showId) {
        requireShow(showId);
        if (!repository.isClosed(showId)) {
            throw new ApiConflictException("BALLOT_NOT_CLOSED", "Die Abstimmung ist nicht abgeschlossen.");
        }
        if (repository.findCurrentSnapshot(showId).isEmpty()) {
            throw new IllegalStateException("A closed ballot must have a current snapshot.");
        }
        repository.makeAllSnapshotsHistorical(showId);
        repository.reopen(showId);
        return response(showId);
    }

    String export(long showId) {
        requireShow(showId);
        BallotSnapshot snapshot = repository.findAllSnapshots(showId).stream().filter(BallotSnapshot::current).findFirst()
                .orElseThrow(() -> new ApiConflictException(
                        "BALLOT_EXPORT_REQUIRES_CURRENT_SNAPSHOT",
                        "Eine abgeschlossene Top 15 ist f\u00fcr den Export erforderlich."
                ));
        return renderer.render(snapshot);
    }

    private BallotResponse response(long showId) {
        List<BallotSnapshot> snapshots = repository.findAllSnapshots(showId);
        BallotSnapshot currentSnapshot = snapshots.stream().filter(BallotSnapshot::current).findFirst().orElse(null);
        Instant ballotClosedAt = repository.ballotClosedAt(showId);
        if (ballotClosedAt != null && currentSnapshot == null) {
            throw new IllegalStateException("A closed ballot must have a current snapshot.");
        }
        return BallotResponse.from(ballotClosedAt, currentSnapshot, snapshots, renderer);
    }

    private void validateTopFifteen(List<BallotRepository.RankedEntry> rankedEntries, Long ownEntryId) {
        if (rankedEntries.size() < 15) {
            throw new ApiConflictException(
                    "BALLOT_CLOSE_REQUIRES_TOP_15",
                    "Zum Abschlie\u00dfen m\u00fcssen mindestens 15 Beitr\u00e4ge eindeutig gerankt sein."
            );
        }
        Set<Long> topFifteenIds = new HashSet<>();
        for (int index = 0; index < rankedEntries.size(); index++) {
            BallotRepository.RankedEntry entry = rankedEntries.get(index);
            if (entry.rankingPosition() != index + 1) {
                throw new ApiConflictException(
                        "BALLOT_CLOSE_INVALID_RANKING",
                        "Die Rangliste muss l\u00fcckenlos bei Rang 1 beginnen."
                );
            }
            if (ownEntryId != null && entry.id() == ownEntryId) {
                throw ownEntryCannotBeRanked();
            }
            if (index < 15) {
                if (!topFifteenIds.add(entry.id()) || blank(entry.artist()) || blank(entry.title()) || blank(entry.youtubeUrl())) {
                    throw new ApiConflictException(
                            "BALLOT_CLOSE_INVALID_TOP_15",
                            "Die Top 15 muss aus 15 unterschiedlichen Beitr\u00e4gen mit vollst\u00e4ndigen Pflichtdaten bestehen."
                    );
                }
            }
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private ApiConflictException reopenRequired() {
        return new ApiConflictException(
                "BALLOT_REOPEN_REQUIRED",
                "Die abgeschlossene Abstimmung muss vor einer Rang\u00e4nderung bewusst wieder ge\u00f6ffnet werden."
        );
    }

    private void validateOwnEntryResolution(BallotRepository.OwnEntryState state) {
        if (state.currentOwnParticipationId() != null && state.resolution() == de.venomenon.cscxtool.entry.OwnEntryResolution.UNRESOLVED) {
            throw new ApiConflictException(
                    "OWN_ENTRY_RESOLUTION_REQUIRED",
                    "Vor dem Abschluss muss bewusst geklÃ¤rt werden, welcher Beitrag deine eigene Einreichung ist oder dass du keinen Beitrag eingereicht hast."
            );
        }
        if (state.resolution() == de.venomenon.cscxtool.entry.OwnEntryResolution.OWN_ENTRY && state.ownEntryId() == null) {
            throw new IllegalStateException("A resolved own entry must reference an existing contest entry.");
        }
    }

    private static ApiConflictException ownEntryCannotBeRanked() {
        return new ApiConflictException(
                "OWN_ENTRY_CANNOT_BE_RANKED",
                "Die eigene Einreichung bleibt sichtbar, ist aber nicht wÃ¤hlbar und darf nicht in der Rangliste stehen."
        );
    }

    private void requireShow(long showId) {
        if (!repository.showExists(showId)) {
            throw new ShowNotFoundException(showId);
        }
    }
}
