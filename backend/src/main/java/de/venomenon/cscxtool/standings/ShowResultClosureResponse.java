package de.venomenon.cscxtool.standings;

import java.util.List;

public record ShowResultClosureResponse(long showId, long contestId, String status, String closedAt, List<String> reasons) { }
