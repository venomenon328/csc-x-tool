package de.venomenon.cscxtool.data;

import java.sql.Connection;
import javax.sql.DataSource;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.springframework.stereotype.Component;

@Component
public class SchemaMigrationProbe {

    public boolean hasPendingChanges(DataSource dataSource, String changeLog) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            Liquibase liquibase = new Liquibase(
                    liquibaseResource(changeLog), new ClassLoaderResourceAccessor(),
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection))
            );
            return !liquibase.listUnrunChangeSets(null, null).isEmpty();
        }
    }

    public void migrate(DataSource dataSource, String changeLog) throws Exception {
        SchemaSupport.migrate(dataSource, changeLog);
    }

    private static String liquibaseResource(String changeLog) {
        return changeLog.startsWith("classpath:/") ? changeLog.substring("classpath:/".length())
                : changeLog.startsWith("classpath:") ? changeLog.substring("classpath:".length()) : changeLog;
    }
}
