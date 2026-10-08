package de.venomenon.cscxtool.shared;

import java.util.LinkedHashMap;
import java.util.Map;

/** Competition ranks: equal totals share their full rank; following places are skipped. */
public final class CompetitionRanks {
    private CompetitionRanks() { }

    public static <K> Map<K, Integer> of(Map<K, Integer> points) {
        Map<K, Integer> ranks = new LinkedHashMap<>();
        points.forEach((key, value) -> ranks.put(key, 1 + (int) points.values().stream().filter(p -> p > value).count()));
        return ranks;
    }

    public static int contestPoints(int showRank) {
        return showRank > 15 ? 0 : CscPoints.pointsForRank(showRank);
    }
}
