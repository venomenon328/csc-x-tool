package de.venomenon.cscxtool.standings;

import de.venomenon.cscxtool.shared.CscPoints;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import static de.venomenon.cscxtool.standings.PreferenceStatisticsResponse.*;
import de.venomenon.cscxtool.standings.ContestStatisticsResponse.Direction;

final class PreferenceStatistics {
    private PreferenceStatistics() { }

    /** Raw weighted Jaccard. Null denotes a missing denominator, never measured zero. */
    static ExactRatio overlap(int[] first, int[] second, boolean normalize) {
        if (first.length != second.length) throw new IllegalArgumentException("Same comparison set required");
        long firstSum = 0, secondSum = 0;
        for (int i = 0; i < first.length; i++) { firstSum += first[i]; secondSum += second[i]; }
        if (normalize && (firstSum == 0 || secondSum == 0)) return null;
        long minimum = 0, maximum = 0;
        for (int i = 0; i < first.length; i++) {
            long a = normalize ? first[i] * secondSum : first[i];
            long b = normalize ? second[i] * firstSum : second[i];
            minimum += Math.min(a, b); maximum += Math.max(a, b);
        }
        return maximum == 0 ? null : ExactRatio.of(minimum, maximum);
    }

    static PreferenceStatisticsResponse calculate(ResultData data) {
        var shows = data.shows().stream().sorted(Comparator.comparingInt(ResultData.Show::number)).toList();
        var people = data.participants().stream().sorted(Comparator.comparingLong(ResultData.Participation::id)).toList();
        Map<Long, List<BallotPoint>> positions = new HashMap<>();
        for (var p : data.positions()) positions.computeIfAbsent(p.ballotId(), ignored -> new ArrayList<>())
                .add(new BallotPoint(p.entryId(), p.rank(), CscPoints.pointsForRank(p.rank())));
        Map<Long, List<ResultData.Entry>> entriesByShow = new HashMap<>();
        for (var e : data.entries()) entriesByShow.computeIfAbsent(e.showId(), ignored -> new ArrayList<>()).add(e);
        Map<Long, List<ResultData.Ballot>> ballotsByShow = new HashMap<>();
        for (var b : data.ballots()) ballotsByShow.computeIfAbsent(b.showId(), ignored -> new ArrayList<>()).add(b);
        Map<Long, ShowIndex> indices = new HashMap<>();
        List<ShowBasis> bases = new ArrayList<>();
        List<EntryPreference> entryValues = new ArrayList<>();
        for (var show : shows) {
            if (show.closedAt() == null) continue;
            var index = new ShowIndex(entriesByShow.getOrDefault(show.id(), List.of()),
                    ballotsByShow.getOrDefault(show.id(), List.of()), positions);
            indices.put(show.id(), index);
            bases.add(new ShowBasis(show.id(), index.ballots));
            for (var entry : index.entries) {
                List<Integer> evaluations = new ArrayList<>();
                Long exclusive = null;
                int positives = 0, sum = 0, twentyFives = 0;
                long squares = 0;
                for (var ballot : index.ballots) {
                    if (Objects.equals(entry.participationId(), ballot.participationId())) continue;
                    int points = index.points(ballot.participationId(), entry.id());
                    evaluations.add(points); sum += points; squares += (long) points * points;
                    if (points > 0) { positives++; exclusive = ballot.participationId(); }
                    if (points == CscPoints.pointsForRank(1)) twentyFives++;
                }
                int n = evaluations.size();
                List<HistogramBin> histogram = new ArrayList<>();
                histogram.add(new HistogramBin(0, (int) evaluations.stream().filter(p -> p == 0).count()));
                for (int rank = 15; rank >= 1; rank--) {
                    int point = CscPoints.pointsForRank(rank);
                    histogram.add(new HistogramBin(point, (int) evaluations.stream().filter(p -> p == point).count()));
                }
                var variance = n == 0 ? null : ExactRatio.of(n * squares - (long) sum * sum, (long) n * n);
                entryValues.add(new EntryPreference(entry.id(), n, positives, sum, twentyFives,
                        metric(n == 0 ? null : ExactRatio.of(positives, n), "Keine bekannte wählbare Bewertung"),
                        metric(variance, "Keine bekannte wählbare Bewertung"), variance == null ? null : Math.sqrt(variance.value()),
                        n >= 2, List.copyOf(histogram), positives == 1 ? exclusive : null,
                        positives == 1 ? sum : 0));
            }
        }
        List<SimilarityPair> pairs = new ArrayList<>();
        for (int a = 0; a < people.size(); a++) for (int b = a + 1; b < people.size(); b++) {
            long first = people.get(a).id(), second = people.get(b).id();
            List<PairShow> evidence = new ArrayList<>();
            List<ExactRatio> values = new ArrayList<>();
            for (var show : shows) {
                var index = indices.get(show.id());
                List<Long> excluded = index == null ? List.of() : index.entries.stream()
                        .filter(e -> Objects.equals(e.participationId(), first) || Objects.equals(e.participationId(), second))
                        .map(ResultData.Entry::id).toList();
                var comparison = index == null ? List.<ResultData.Entry>of() : index.entries.stream()
                        .filter(e -> !excluded.contains(e.id())).toList();
                ExactRatio value = index != null && index.vectors.containsKey(first) && index.vectors.containsKey(second)
                        ? overlap(index.vector(first, comparison), index.vector(second, comparison), false) : null;
                if (value != null) values.add(value);
                evidence.add(new PairShow(show.id(), metric(value, index == null ? "Show nicht gewertet"
                        : !index.vectors.containsKey(first) || !index.vectors.containsKey(second)
                        ? "Keine gemeinsame vollständige Abgabe" : "Keine positive Vergleichssumme"), comparison.size(), excluded));
            }
            pairs.add(new SimilarityPair(first, second, metric(ExactRatio.mean(values), "Keine berechenbare gemeinsame Show"),
                    values.size(), List.copyOf(evidence)));
        }
        pairs.sort(order(SimilarityPair::similarity, true).thenComparingLong(SimilarityPair::firstId).thenComparingLong(SimilarityPair::secondId));
        var parallel = pairs.stream().sorted(order(SimilarityPair::similarity, false)
                .thenComparingLong(SimilarityPair::firstId).thenComparingLong(SimilarityPair::secondId)).toList();
        List<ParticipantPreference> participants = new ArrayList<>();
        for (var person : people) {
            List<ConsensusShow> evidence = new ArrayList<>();
            List<ExactRatio> values = new ArrayList<>();
            for (var show : shows) {
                var index = indices.get(show.id());
                var ownEntry = index == null ? null : index.entries.stream().filter(e -> Objects.equals(e.participationId(), person.id())).findFirst().orElse(null);
                var comparison = index == null ? List.<ResultData.Entry>of() : index.entries.stream()
                        .filter(e -> !Objects.equals(e.participationId(), person.id())).toList();
                int[] own = index == null ? new int[0] : index.vector(person.id(), comparison);
                int[] field = new int[comparison.size()];
                int other = index == null ? 0 : index.ballots.size() - (index.vectors.containsKey(person.id()) ? 1 : 0);
                for (int i = 0; i < field.length; i++) field[i] = index.totals.getOrDefault(comparison.get(i).id(), 0) - own[i];
                ExactRatio value = index != null && index.vectors.containsKey(person.id()) && other > 0 ? overlap(own, field, true) : null;
                if (value != null) values.add(value);
                evidence.add(new ConsensusShow(show.id(), metric(value, index == null ? "Show nicht gewertet"
                        : !index.vectors.containsKey(person.id()) ? "Kein vollständiger eigener Stimmzettel"
                        : other == 0 ? "Kein anderer vollständiger Stimmzettel" : "Keine positive Vergleichssumme"),
                        other, comparison.size(), ownEntry == null ? null : ownEntry.id(), java.util.Arrays.stream(own).sum(), java.util.Arrays.stream(field).sum()));
            }
            var exclusive = entryValues.stream().filter(e -> Objects.equals(e.exclusiveGiverId(), person.id())).toList();
            participants.add(new ParticipantPreference(person.id(), metric(ExactRatio.mean(values), "Keine berechenbare Show mit übrigen Stimmen"),
                    values.size(), List.copyOf(evidence), exclusive.stream().mapToInt(EntryPreference::exclusivePoints).sum(),
                    exclusive.stream().map(EntryPreference::entryId).toList(), exclusive.stream().filter(e -> e.exclusivePoints() == CscPoints.pointsForRank(1)).map(EntryPreference::entryId).toList()));
        }
        participants.sort(order(ParticipantPreference::consensus, true).thenComparingLong(ParticipantPreference::participationId));
        var audience = entryValues.stream().sorted(order(EntryPreference::audienceRate, true).thenComparingLong(EntryPreference::entryId)).toList();
        var polarization = entryValues.stream().filter(EntryPreference::polarizationEligible)
                .sorted(order(EntryPreference::variance, true).thenComparingLong(EntryPreference::entryId)).toList();
        int exclusiveMax = participants.stream().mapToInt(ParticipantPreference::exclusivePoints).max().orElse(0);
        var records = new PreferenceRecords(winners(pairs, SimilarityPair::similarity, true).stream().map(PreferenceStatistics::direction).toList(),
                winners(parallel, SimilarityPair::similarity, false).stream().map(PreferenceStatistics::direction).toList(),
                winners(audience, EntryPreference::audienceRate, true).stream().map(EntryPreference::entryId).toList(),
                winners(polarization, EntryPreference::variance, true).stream().map(EntryPreference::entryId).toList(),
                exclusiveMax == 0 ? List.of() : participants.stream().filter(p -> p.exclusivePoints() == exclusiveMax).map(ParticipantPreference::participationId).sorted().toList(),
                winners(participants, ParticipantPreference::consensus, true).stream().map(ParticipantPreference::participationId).toList());
        return new PreferenceStatisticsResponse(List.copyOf(bases), List.copyOf(pairs), parallel.stream().map(PreferenceStatistics::direction).toList(),
                List.copyOf(entryValues), audience.stream().map(EntryPreference::entryId).toList(), polarization.stream().map(EntryPreference::entryId).toList(),
                List.copyOf(participants), participants.stream().filter(p -> p.exclusivePoints() > 0)
                .sorted(Comparator.comparingInt(ParticipantPreference::exclusivePoints).reversed().thenComparingLong(ParticipantPreference::participationId))
                .map(ParticipantPreference::participationId).toList(), records);
    }
    private static Direction direction(SimilarityPair p) { return new Direction(p.firstId(), p.secondId()); }
    static Metric metric(ExactRatio value, String reason) {
        return value == null ? new Metric(null, null, null, reason) : new Metric(value.numerator().toString(), value.denominator().toString(), value.value(), null);
    }
    private static ExactRatio exact(Metric m) { return m.value() == null ? null : new ExactRatio(new BigInteger(m.numerator()), new BigInteger(m.denominator())); }
    static <T> Comparator<T> order(Function<T, Metric> metric, boolean descending) {
        Comparator<ExactRatio> valueOrder = descending ? Comparator.reverseOrder() : Comparator.naturalOrder();
        return Comparator.comparing((T v) -> exact(metric.apply(v)), Comparator.nullsLast(valueOrder));
    }
    static <T> List<T> winners(List<T> values, Function<T, Metric> metric, boolean descending) {
        var sorted = values.stream().filter(v -> metric.apply(v).value() != null).sorted(order(metric, descending)).toList();
        if (sorted.isEmpty()) return List.of();
        var best = exact(metric.apply(sorted.getFirst()));
        return sorted.stream().filter(v -> exact(metric.apply(v)).compareTo(best) == 0).toList();
    }
    private static final class ShowIndex {
        final List<ResultData.Entry> entries;
        final List<BallotBasis> ballots;
        final Map<Long, Map<Long, Integer>> vectors = new HashMap<>();
        final Map<Long, Integer> totals = new HashMap<>();
        ShowIndex(List<ResultData.Entry> entries, List<ResultData.Ballot> ballots, Map<Long, List<BallotPoint>> positions) {
            this.entries = entries.stream().sorted(Comparator.comparingLong(ResultData.Entry::id)).toList();
            this.ballots = ballots.stream().filter(b -> "ABGESTIMMT".equals(b.status())).sorted(Comparator.comparingLong(ResultData.Ballot::participationId))
                    .map(b -> new BallotBasis(b.id(), b.participationId(), positions.getOrDefault(b.id(), List.of()).stream().sorted(Comparator.comparingInt(BallotPoint::rank)).toList())).toList();
            for (var b : this.ballots) {
                Map<Long, Integer> vector = new HashMap<>();
                for (var p : b.positions()) { vector.put(p.entryId(), p.points()); totals.merge(p.entryId(), p.points(), Integer::sum); }
                vectors.put(b.participationId(), vector);
            }
        }
        int points(long person, long entry) { return vectors.getOrDefault(person, Map.of()).getOrDefault(entry, 0); }
        int[] vector(long person, List<ResultData.Entry> comparison) { return comparison.stream().mapToInt(e -> points(person, e.id())).toArray(); }
    }
}
