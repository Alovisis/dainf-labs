package br.edu.utfpr.dainf.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Ensures revinfo matches the column names Hibernate Envers 6.x's DefaultRevisionEntity
 * expects (id, timestamp, username) before Hibernate tries to INSERT into it.
 * Older Envers versions named these columns "rev" and "revtstmp"; ddl-auto:update does not
 * rename existing columns, so a revinfo table created under the old convention is left with
 * the wrong column names and INSERTs fail with "column ... does not exist".
 */
@Component
public class RevInfoSchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(RevInfoSchemaInitializer.class);

    public RevInfoSchemaInitializer(DataSource dataSource) {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("""
                    DO $$
                    BEGIN
                        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'revinfo' AND column_name = 'rev')
                           AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'revinfo' AND column_name = 'id') THEN
                            ALTER TABLE revinfo RENAME COLUMN rev TO id;
                        END IF;

                        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'revinfo' AND column_name = 'revtstmp')
                           AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'revinfo' AND column_name = 'timestamp') THEN
                            ALTER TABLE revinfo RENAME COLUMN revtstmp TO timestamp;
                        END IF;
                    END $$;
                    """);
            stmt.execute("""
                    ALTER TABLE revinfo
                    ADD COLUMN IF NOT EXISTS timestamp BIGINT DEFAULT 0 NOT NULL
                    """);
            stmt.execute("""
                    ALTER TABLE revinfo
                    ADD COLUMN IF NOT EXISTS username VARCHAR(255)
                    """);
            log.debug("revinfo schema ensured (id, timestamp, username)");
        } catch (Exception e) {
            // Ignored: table does not exist yet on a fresh start; Hibernate will create it
            // correctly (with all columns) during DDL initialization.
            log.trace("revinfo not yet present, skipping column check: {}", e.getMessage());
        }
    }
}
