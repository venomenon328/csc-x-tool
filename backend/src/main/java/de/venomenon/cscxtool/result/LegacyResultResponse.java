package de.venomenon.cscxtool.result;

import java.util.List;

/** Deprecated, read-only migration view. It has no bearing on derived own results. */
record LegacyResultResponse(long mottoShowId, ResultRepository.LegacyDetails details, List<ResultRepository.LegacyScore> receivedScores) { }
