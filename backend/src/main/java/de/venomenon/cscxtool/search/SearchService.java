package de.venomenon.cscxtool.search;

import de.venomenon.cscxtool.contest.ContestNotFoundException;
import de.venomenon.cscxtool.contest.ContestRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
class SearchService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ContestRepository contests;

    SearchService(NamedParameterJdbcTemplate jdbcTemplate, ContestRepository contests) {
        this.jdbcTemplate = jdbcTemplate;
        this.contests = contests;
    }

    List<SearchResult> search(String rawQuery, Long contestId) {
        if (rawQuery == null || rawQuery.isBlank()) return List.of();
        long resolvedContestId = contestId == null
                ? contests.findCurrent().orElseThrow(() -> new IllegalStateException("No current contest exists.")).id()
                : contestId;
        if (!contests.exists(resolvedContestId)) throw new ContestNotFoundException(resolvedContestId);
        String pattern = "%" + escapeLike(rawQuery.trim().toLowerCase(Locale.ROOT)) + "%";
        return jdbcTemplate.query("""
                WITH candidate_results AS (
                  SELECT 'CANDIDATE' AS result_type, candidate.id, motto_show.id AS show_id,
                         motto_show.show_number, motto_show.name AS show_name, candidate.artist, candidate.title
                  FROM candidate JOIN motto_show ON motto_show.id = candidate.motto_show_id
                  WHERE motto_show.contest_id = :contestId
                    AND (lower(candidate.artist) LIKE :pattern ESCAPE '\\'
                     OR lower(candidate.title) LIKE :pattern ESCAPE '\\'
                    )
                  ORDER BY candidate.artist COLLATE NOCASE, candidate.title COLLATE NOCASE
                  LIMIT 25
                ), entry_results AS (
                  SELECT 'ENTRY' AS result_type, contest_entry.id, motto_show.id AS show_id,
                         motto_show.show_number, motto_show.name AS show_name, contest_entry.artist, contest_entry.title
                  FROM contest_entry JOIN motto_show ON motto_show.id = contest_entry.motto_show_id
                  WHERE motto_show.contest_id = :contestId
                    AND (lower(contest_entry.artist) LIKE :pattern ESCAPE '\\'
                     OR lower(contest_entry.title) LIKE :pattern ESCAPE '\\'
                    )
                  ORDER BY contest_entry.artist COLLATE NOCASE, contest_entry.title COLLATE NOCASE
                  LIMIT 25
                )
                SELECT result_type, id, show_id, show_number, show_name, artist, title FROM candidate_results
                UNION ALL
                SELECT result_type, id, show_id, show_number, show_name, artist, title FROM entry_results
                ORDER BY show_number, result_type, artist COLLATE NOCASE, title COLLATE NOCASE
                """,
                new MapSqlParameterSource().addValue("pattern", pattern).addValue("contestId", resolvedContestId),
                (resultSet, row) -> new SearchResult(
                        resultSet.getString("result_type"), resultSet.getLong("id"), resultSet.getLong("show_id"),
                        resultSet.getInt("show_number"), resultSet.getString("show_name"),
                        resultSet.getString("artist"), resultSet.getString("title")
                )
        );
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
