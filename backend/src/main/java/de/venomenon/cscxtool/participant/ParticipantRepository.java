package de.venomenon.cscxtool.participant;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
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
public class ParticipantRepository {

    private final JdbcTemplate jdbcTemplate;

    ParticipantRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<Participant> findAll(boolean includeInactive) {
        List<ParticipantRow> rows = jdbcTemplate.query("""
                SELECT participant.id, participant.display_name, participant.active, participant.created_at, participant.updated_at,
                       COUNT(botb_selection.id) AS botb_selection_count
                FROM participant
                LEFT JOIN participant_botb_selection botb_selection ON botb_selection.participant_id = participant.id
                WHERE ? OR participant.active = 1
                GROUP BY participant.id
                ORDER BY participant.display_name COLLATE NOCASE, participant.id
                """, ParticipantRepository::mapRow, includeInactive);
        return attachAliases(rows);
    }

    Optional<Participant> findById(long participantId) {
        List<ParticipantRow> rows = jdbcTemplate.query("""
                SELECT participant.id, participant.display_name, participant.active, participant.created_at, participant.updated_at,
                       COUNT(botb_selection.id) AS botb_selection_count
                FROM participant
                LEFT JOIN participant_botb_selection botb_selection ON botb_selection.participant_id = participant.id
                WHERE participant.id = ?
                GROUP BY participant.id
                """, ParticipantRepository::mapRow, participantId);
        return attachAliases(rows).stream().findFirst();
    }

    Participant create(String displayName, boolean active, List<String> aliases) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO participant (display_name, active, created_at, updated_at)
                    VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, displayName);
            statement.setBoolean(2, active);
            return statement;
        }, keyHolder);
        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("SQLite did not return an ID for the new participant.");
        }
        replaceAliases(generatedId.longValue(), aliases);
        return findById(generatedId.longValue()).orElseThrow();
    }

    boolean update(long participantId, String displayName, boolean active) {
        return jdbcTemplate.update("""
                UPDATE participant
                SET display_name = ?, active = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, displayName, active, participantId) == 1;
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

    boolean isReferencedByContestParticipation(long participantId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM contest_participation WHERE participant_id = ?)", Boolean.class, participantId
        ));
    }

    List<BotbSelection> findBotbSelections(long participantId) {
        return jdbcTemplate.query("""
                SELECT id, participant_id, edition_number, artist, known_since, created_at, updated_at
                FROM participant_botb_selection
                WHERE participant_id = ?
                ORDER BY edition_number DESC, id DESC
                """, (resultSet, rowNumber) -> new BotbSelection(
                resultSet.getLong("id"), resultSet.getLong("participant_id"), resultSet.getInt("edition_number"),
                resultSet.getString("artist"), localDate(resultSet, "known_since"),
                resultSet.getTimestamp("created_at").toInstant(), resultSet.getTimestamp("updated_at").toInstant()
        ), participantId);
    }

    void replaceBotbSelections(long participantId, List<BotbSelectionCommand> selections) {
        List<Long> retainedIds = selections.stream().map(BotbSelectionCommand::id).filter(java.util.Objects::nonNull).toList();
        if (retainedIds.isEmpty()) {
            jdbcTemplate.update("DELETE FROM participant_botb_selection WHERE participant_id = ?", participantId);
        } else {
            String placeholders = String.join(", ", java.util.Collections.nCopies(retainedIds.size(), "?"));
            List<Object> parameters = new ArrayList<>();
            parameters.add(participantId);
            parameters.addAll(retainedIds);
            jdbcTemplate.update("DELETE FROM participant_botb_selection WHERE participant_id = ? AND id NOT IN (" + placeholders + ")",
                    parameters.toArray());
        }

        long temporaryEdition = Long.MAX_VALUE;
        for (BotbSelectionCommand selection : selections) {
            if (selection.id() != null) {
                jdbcTemplate.update("UPDATE participant_botb_selection SET edition_number = ? WHERE id = ? AND participant_id = ?",
                        temporaryEdition--, selection.id(), participantId);
            }
        }
        for (BotbSelectionCommand selection : selections) {
            if (selection.id() == null) {
                jdbcTemplate.update("""
                        INSERT INTO participant_botb_selection (participant_id, edition_number, artist, known_since, created_at, updated_at)
                        VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """, participantId, selection.editionNumber(), selection.artist(), selection.knownSince());
            } else {
                jdbcTemplate.update("""
                        UPDATE participant_botb_selection
                        SET edition_number = ?, artist = ?, known_since = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE id = ? AND participant_id = ?
                        """, selection.editionNumber(), selection.artist(), selection.knownSince(), selection.id(), participantId);
            }
        }
    }

    List<ContestParticipant> findAllByContest(long contestId, boolean includeInactive) {
        List<ContestParticipantRow> rows = jdbcTemplate.query("""
                SELECT participation.id AS participation_id, participant.id, participant.display_name,
                       participant.active AS identity_active, participation.country_code, participation.active,
                       participation.created_at, participation.updated_at, COUNT(botb_selection.id) AS botb_selection_count
                FROM contest_participation participation
                JOIN participant ON participant.id = participation.participant_id
                LEFT JOIN participant_botb_selection botb_selection ON botb_selection.participant_id = participant.id
                WHERE participation.contest_id = ? AND (? OR participation.active = 1)
                GROUP BY participation.id
                ORDER BY participation.active DESC, participant.display_name COLLATE NOCASE, participant.id
                """, ParticipantRepository::mapContestParticipantRow, contestId, includeInactive);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, List<String>> aliasesByParticipant = aliasesByParticipant(rows.stream().map(ContestParticipantRow::participantId).toList());
        return rows.stream().map(row -> new ContestParticipant(
                row.participationId(), row.participantId(), row.displayName(), row.identityActive(), row.countryCode(), row.active(),
                List.copyOf(aliasesByParticipant.getOrDefault(row.participantId(), List.of())), row.botbSelectionCount(),
                row.createdAt(), row.updatedAt()
        )).toList();
    }

    private List<Participant> attachAliases(List<ParticipantRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, List<String>> aliasesByParticipant = aliasesByParticipant(rows.stream().map(ParticipantRow::id).toList());
        return rows.stream().map(row -> new Participant(
                row.id(), row.displayName(), row.active(),
                List.copyOf(aliasesByParticipant.getOrDefault(row.id(), List.of())), row.botbSelectionCount(), row.createdAt(), row.updatedAt()
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
                resultSet.getBoolean("active"),
                resultSet.getInt("botb_selection_count"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }

    private static LocalDate localDate(ResultSet resultSet, String column) throws SQLException {
        String value = resultSet.getString(column);
        return value == null ? null : LocalDate.parse(value);
    }

    private record ParticipantRow(
            long id,
            String displayName,
            boolean active,
            int botbSelectionCount,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    private static ContestParticipantRow mapContestParticipantRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ContestParticipantRow(
                resultSet.getLong("participation_id"), resultSet.getLong("id"), resultSet.getString("display_name"),
                resultSet.getBoolean("identity_active"), resultSet.getString("country_code"), resultSet.getBoolean("active"),
                resultSet.getInt("botb_selection_count"), resultSet.getTimestamp("created_at").toInstant(), resultSet.getTimestamp("updated_at").toInstant()
        );
    }

    private record ContestParticipantRow(
            long participationId, long participantId, String displayName, boolean identityActive, String countryCode,
            boolean active, int botbSelectionCount, Instant createdAt, Instant updatedAt
    ) {
    }
}
