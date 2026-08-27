package de.venomenon.cscxtool.ballot;

import java.util.List;

record BallotRankingResponse(List<Long> rankedEntryIds, List<Long> unrankedEntryIds) {
}
