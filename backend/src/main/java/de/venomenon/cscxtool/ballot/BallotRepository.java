package de.venomenon.cscxtool.ballot;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
class BallotRepository {

    private static final RowMapper<RankedEntry> RANKED_ENTRY_ROW_MAPPER = BallotRepository::mapRankedEntry;
    private static final RowMapper<BallotSnapshotItem> SNAPSHOT_ITEM_ROW_MAPPER = BallotRepository::mapSnapshotItem;

    private final JdbcTemplate jdbcTemplate;

    BallotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    boolean showExists(long showId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM motto_show WHERE id = ?)", Boolean.class, showId
        ));
    }

    boolean isClosed(long showId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT ballot_closed_at IS NOT NULL FROM motto_show WHERE id = ?", Boolean.class, showId
        ));
    }

    boolean resultsClosed(long showId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT results_closed_at IS NOT NULL FROM motto_show WHERE id = ?", Boolean.class, showId
        ));
    }

    Instant ballotClosedAt(long showId) {
        return jdbcTemplate.queryForObject("SELECT ballot_closed_at FROM motto_show WHERE id = ?", (resultSet, rowNumber) -> {
            java.sql.Timestamp value = resultSet.getTimestamp(1);
            return value == null ? null : value.toInstant();
        }, showId);
    }

    List<Long> findAllEntryIds(long showId) {
        return jdbcTemplate.query(
                "SELECT id FROM contest_entry WHERE motto_show_id = ? ORDER BY id",
                (resultSet, rowNumber) -> resultSet.getLong(1), showId
        );
    }

    List<RankedEntry> findRankedEntries(long showId) {
        return jdbcTemplate.query("""
                SELECT id, ranking_position, artist, title, youtube_url
                FROM contest_entry
                WHERE motto_show_id = ? AND ranking_position IS NOT NULL
                ORDER BY ranking_position
                """, RANKED_ENTRY_ROW_MAPPER, showId);
    }

    void replaceRanking(long showId, List<Long> rankedEntryIds) {
        // NULL is allowed repeatedly by SQLite's unique index. Clearing first avoids transient
        // uniqueness conflicts while positions are reassigned in a different order.
        jdbcTemplate.update("""
                UPDATE contest_entry
                SET ranking_position = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE motto_show_id = ?
                """, showId);
        for (int index = 0; index < rankedEntryIds.size(); index++) {
            int changed = jdbcTemplate.update("""
                    UPDATE contest_entry
                    SET ranking_position = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND motto_show_id = ?
                    """, index + 1, rankedEntryIds.get(index), showId);
            if (changed != 1) {
                throw new IllegalStateException("A validated contest entry disappeared during ranking replacement.");
            }
        }
    }

    int nextSnapshotNumber(long showId) {
        Integer number = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(snapshot_number), 0) + 1 FROM ballot_snapshot WHERE motto_show_id = ?",
                Integer.class,
                showId
        );
        if (number == null) {
            throw new IllegalStateException("SQLite did not calculate the next ballot snapshot number.");
        }
        return number;
    }

    void makeAllSnapshotsHistorical(long showId) {
        jdbcTemplate.update("UPDATE ballot_snapshot SET is_current = 0 WHERE motto_show_id = ? AND is_current = 1", showId);
    }

    long createSnapshot(long showId, int snapshotNumber) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO ballot_snapshot (motto_show_id, snapshot_number, created_at, is_current)
                    VALUES (?, ?, CURRENT_TIMESTAMP, 1)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, showId);
            statement.setInt(2, snapshotNumber);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("SQLite did not return an ID for the ballot snapshot.");
        }
        return key.longValue();
    }

    void createSnapshotItems(long snapshotId, List<RankedEntry> topFifteen) {
        for (RankedEntry entry : topFifteen) {
            int changed = jdbcTemplate.update("""
                    INSERT INTO ballot_snapshot_item (
                      ballot_snapshot_id, rank, contest_entry_id, artist_snapshot, title_snapshot, youtube_url_snapshot
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    """, snapshotId, entry.rankingPosition(), entry.id(), entry.artist(), entry.title(), entry.youtubeUrl());
            if (changed != 1) {
                throw new IllegalStateException("SQLite did not store a ballot snapshot item.");
            }
        }
    }

    void markClosed(long showId) {
        int changed = jdbcTemplate.update("""
                UPDATE motto_show
                SET ballot_closed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, showId);
        if (changed != 1) {
            throw new IllegalStateException("The validated motto show disappeared while closing its ballot.");
        }
    }

    void reopen(long showId) {
        int changed = jdbcTemplate.update("""
                UPDATE motto_show
                SET ballot_closed_at = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, showId);
        if (changed != 1) {
            throw new IllegalStateException("The validated motto show disappeared while reopening its ballot.");
        }
    }

    Optional<BallotSnapshot> findCurrentSnapshot(long showId) {
        List<BallotSnapshot> snapshots = jdbcTemplate.query("""
                SELECT id, snapshot_number, created_at, is_current
                FROM ballot_snapshot
                WHERE motto_show_id = ? AND is_current = 1
                """, (resultSet, rowNumber) -> mapSnapshot(resultSet), showId);
        if (snapshots.size() > 1) {
            throw new IllegalStateException("More than one current ballot snapshot exists for the same show.");
        }
        return snapshots.stream().findFirst();
    }

    List<BallotSnapshot> findAllSnapshots(long showId) {
        List<BallotSnapshot> snapshots = jdbcTemplate.query("""
                SELECT id, snapshot_number, created_at, is_current
                FROM ballot_snapshot
                WHERE motto_show_id = ?
                ORDER BY snapshot_number DESC
                """, (resultSet, rowNumber) -> mapSnapshot(resultSet), showId);
        return snapshots.stream().map(snapshot -> new BallotSnapshot(
                snapshot.id(), snapshot.snapshotNumber(), snapshot.createdAt(), snapshot.current(), findSnapshotItems(snapshot.id())
        )).toList();
    }

    private BallotSnapshot mapSnapshot(ResultSet resultSet) throws SQLException {
        return new BallotSnapshot(
                resultSet.getLong("id"),
                resultSet.getInt("snapshot_number"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getBoolean("is_current"),
                List.of()
        );
    }

    private List<BallotSnapshotItem> findSnapshotItems(long snapshotId) {
        return jdbcTemplate.query("""
                SELECT item.rank, item.contest_entry_id, item.artist_snapshot, item.title_snapshot,
                       item.youtube_url_snapshot, participation.country_code AS participant_country_code
                FROM ballot_snapshot_item item
                LEFT JOIN contest_entry entry ON entry.id = item.contest_entry_id
                LEFT JOIN contest_participation participation ON participation.id = entry.contest_participation_id
                WHERE item.ballot_snapshot_id = ?
                ORDER BY item.rank
                """, SNAPSHOT_ITEM_ROW_MAPPER, snapshotId);
    }

    private static RankedEntry mapRankedEntry(ResultSet resultSet, int rowNumber) throws SQLException {
        return new RankedEntry(
                resultSet.getLong("id"),
                resultSet.getInt("ranking_position"),
                resultSet.getString("artist"),
                resultSet.getString("title"),
                resultSet.getString("youtube_url")
        );
    }

    private static BallotSnapshotItem mapSnapshotItem(ResultSet resultSet, int rowNumber) throws SQLException {
        long entryId = resultSet.getLong("contest_entry_id");
        return new BallotSnapshotItem(
                resultSet.getInt("rank"),
                resultSet.wasNull() ? null : entryId,
                resultSet.getString("artist_snapshot"),
                resultSet.getString("title_snapshot"),
                resultSet.getString("youtube_url_snapshot"),
                resultSet.getString("participant_country_code")
        );
    }

    record RankedEntry(long id, int rankingPosition, String artist, String title, String youtubeUrl) {
    }
}
