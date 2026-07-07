package br.edu.utfpr.dainf.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
public class RevInfoSchemaInitializer implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(RevInfoSchemaInitializer.class);

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSource) {
            DataSource dataSource = (DataSource) bean;
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
                                ALTER TABLE revinfo RENAME COLUMN revtstmp TO "timestamp";
                            END IF;
                        END $$;
                        """);
                stmt.execute("""
                        ALTER TABLE revinfo
                        ADD COLUMN IF NOT EXISTS "timestamp" BIGINT DEFAULT 0 NOT NULL
                        """);
                stmt.execute("""
                        ALTER TABLE revinfo
                        ADD COLUMN IF NOT EXISTS username VARCHAR(255)
                        """);
                log.info("revinfo schema ensured (id, timestamp, username)");
            } catch (Exception e) {
                log.error("revinfo not yet present or error occurred: {}", e.getMessage());
            }
        }
        return bean;
    }
}
