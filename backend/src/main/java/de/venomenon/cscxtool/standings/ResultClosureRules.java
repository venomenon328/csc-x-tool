package de.venomenon.cscxtool.standings;

import de.venomenon.cscxtool.shared.EntryListReadiness;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Shared by live commands and both restore paths. No stored flag replaces canonical validation. */
public final class ResultClosureRules {
    private ResultClosureRules() { }

    public static List<String> reasons(ResultData data, ResultData.Show show) {
        List<String> reasons = new ArrayList<>();
        Set<Long> participants = data.participants().stream().map(ResultData.Participation::id).collect(Collectors.toSet());
        Map<Long, ResultData.Entry> entries = data.entries().stream().filter(e -> e.showId() == show.id())
                .collect(Collectors.toMap(ResultData.Entry::id, Function.identity()));
        Set<Long> assigned = new HashSet<>();
        boolean allAssigned = entries.values().stream().allMatch(e -> e.participationId() != null
                && participants.contains(e.participationId()) && assigned.add(e.participationId()));
        if (!EntryListReadiness.isReady(show.entryListComplete(), data.current(), !entries.isEmpty(), allAssigned)) {
            reasons.add("Die vollständige Songliste muss bestätigt sein.");
        }
        if (entries.isEmpty() || !allAssigned) reasons.add("Alle Beiträge benötigen eindeutige gültige Contest-Zuordnungen.");
        List<ResultData.Ballot> ballots = data.ballots().stream().filter(b -> b.showId() == show.id()).toList();
        Set<Long> voters = new HashSet<>();
        int valid = 0;
        boolean invalid = false;
        for (ResultData.Ballot ballot : ballots) {
            if (!participants.contains(ballot.participationId()) || !voters.add(ballot.participationId())) invalid = true;
            var positions = data.positions().stream().filter(p -> p.ballotId() == ballot.id()).toList();
            if ("NICHT_ABGESTIMMT".equals(ballot.status())) {
                if (!positions.isEmpty()) invalid = true;
            } else if ("ABGESTIMMT".equals(ballot.status())) {
                Set<Long> ids = new HashSet<>();
                Set<Integer> ranks = new HashSet<>();
                boolean correct = positions.size() == 15;
                for (var p : positions) {
                    var entry = entries.get(p.entryId());
                    if (p.rank() < 1 || p.rank() > 15 || !ranks.add(p.rank()) || !ids.add(p.entryId())
                            || entry == null || Long.valueOf(ballot.participationId()).equals(entry.participationId())) correct = false;
                }
                if (correct) valid++; else invalid = true;
            } else {
                voters.remove(ballot.participationId());
                if (!positions.isEmpty()) invalid = true;
            }
        }
        long unknown = participants.stream().filter(id -> !voters.contains(id)).count();
        if (unknown > 0) reasons.add(unknown + " Contest-Teilnahme(n) haben noch den Status UNERFASST.");
        if (invalid) reasons.add("Mindestens ein Stimmzettel verletzt die Rang-, Referenz- oder Selbstvoteregeln.");
        if (valid == 0) reasons.add("Mindestens ein vollständiger gültiger Stimmzettel ist erforderlich.");
        return List.copyOf(reasons);
    }

    public static void validateClosed(ResultData data) {
        for (var show : data.shows()) {
            if (show.closedAt() == null) continue;
            try { Instant.parse(show.closedAt()); }
            catch (RuntimeException invalid) { throw new IllegalArgumentException("Ungültiger Ergebnisabschlusszeitpunkt.", invalid); }
            var reasons = reasons(data, show);
            if (!reasons.isEmpty()) throw new IllegalArgumentException("Show " + show.number() + ": " + String.join(" ", reasons));
        }
    }
}
