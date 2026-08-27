package de.venomenon.cscxtool.participant;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
class ParticipantRepository {

    private final JdbcTemplate jdbcTemplate;

    ParticipantRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<Participant> findAll(boolean includeInactive) {
        List<ParticipantRow> rows = jdbcTemplate.query("""
                SELECT id, display_name, country_code, active, created_at, updated_at
                FROM participant
                WHERE ? OR active = 1
                ORDER BY display_name COLLATE NOCASE, id
                """, ParticipantRepository::mapRow, includeInactive);
        return attachAliases(rows);
    }

    Optional<Participant> findById(long participantId) {
        List<ParticipantRow> rows = jdbcTemplate.query("""
                SELECT id, display_name, country_code, active, created_at, updated_at
                FROM participant
                WHERE id = ?
                """, ParticipantRepository::mapRow, participantId);
        return attachAliases(rows).stream().findFirst();
    }

    Participant create(String displayName, String countryCode, boolean active, List<String> aliases) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO participant (display_name, country_code, active, created_at, updated_at)
                    VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, displayName);
            statement.setString(2, countryCode);
            statement.setBoolean(3, active);
            return statement;
        }, keyHolder);
        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("SQLite did not return an ID for the new participant.");
        }
        replaceAliases(generatedId.longValue(), aliases);
        return findById(generatedId.longValue()).orElseThrow();
    }

    boolean update(long participantId, String displayName, String countryCode, boolean active) {
        return jdbcTemplate.update("""
                UPDATE participant
                SET display_name = ?, country_code = ?, active = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, displayName, countryCode, active, participantId) == 1;
    }

    void replaceAliases(long participantId, List<String> aliases) {
        jdbcTemplate.update("DELETE FROM participant_alias WHERE participant_id = ?", participantId);
        for (String alias : aliases) {
            jdbcTemplate.update("INSERT INTO participant_alias (participant_id, alias) VALUES (?, ?)", participantId, alias);
        }
    }

    boolean delete(long participantId) {
        return jdbcTemplate.update("DELETE FROM participant WHERE id = ?", participantId) == 1;
    }

    boolean isReferencedByContestEntry(long participantId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM contest_entry WHERE participant_id = ?)", Boolean.class, participantId
        ));
    }

    private List<Participant> attachAliases(List<ParticipantRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, List<String>> aliasesByParticipant = aliasesByParticipant(rows.stream().map(ParticipantRow::id).toList());
        return rows.stream().map(row -> new Participant(
                row.id(), row.displayName(), row.countryCode(), row.active(),
                List.copyOf(aliasesByParticipant.getOrDefault(row.id(), List.of())), row.createdAt(), row.updatedAt()
        )).toList();
    }

    private Map<Long, List<String>> aliasesByParticipant(Collection<Long> participantIds) {
        String placeholders = String.join(", ", java.util.Collections.nCopies(participantIds.size(), "?"));
        Map<Long, List<String>> aliases = new HashMap<>();
        jdbcTemplate.query("""
                SELECT participant_id, alias
                FROM participant_alias
                WHERE participant_id IN (""" + placeholders + ") ORDER BY id", resultSet -> {
            long participantId = resultSet.getLong("participant_id");
            aliases.computeIfAbsent(participantId, ignored -> new ArrayList<>()).add(resultSet.getString("alias"));
        }, participantIds.toArray());
        return aliases;
    }

    private static ParticipantRow mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ParticipantRow(
                resultSet.getLong("id"),
                resultSet.getString("display_name"),
                resultSet.getString("country_code"),
                resultSet.getBoolean("active"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }

    private record ParticipantRow(
            long id,
            String displayName,
            String countryCode,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
