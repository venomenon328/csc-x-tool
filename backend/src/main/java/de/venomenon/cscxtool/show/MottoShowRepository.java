package de.venomenon.cscxtool.show;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
class MottoShowRepository {

    private static final RowMapper<MottoShow> ROW_MAPPER = MottoShowRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    MottoShowRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<MottoShow> findAll() {
        return jdbcTemplate.query("""
                SELECT id, show_number, name, created_at, updated_at
                FROM motto_show
                ORDER BY show_number
                """, ROW_MAPPER);
    }

    Optional<MottoShow> findById(long id) {
        return jdbcTemplate.query("""
                SELECT id, show_number, name, created_at, updated_at
                FROM motto_show
                WHERE id = ?
                """, ROW_MAPPER, id).stream().findFirst();
    }

    boolean rename(long id, String name) {
        return jdbcTemplate.update("""
                UPDATE motto_show
                SET name = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, name, id) == 1;
    }

    private static MottoShow mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new MottoShow(
                resultSet.getLong("id"),
                resultSet.getInt("show_number"),
                resultSet.getString("name"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }
}
