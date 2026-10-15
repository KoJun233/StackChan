package com.kj.stackchan.memory;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class TopicContinuationUpgradeTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName
            .parse("postgres@sha256:c2d42a104eb6b37b286a2d9c5cf83f349de4d6516d513d00a2bd9610e2c2e5e4")
            .asCompatibleSubstituteFor("postgres"));

    @Test void v56ConfirmedEventsRemainFactsWithoutNewPermissionAndUpgradeIsRepeatable() throws Exception {
        var configuration = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        configuration.target("56").load().migrate();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            jdbc.update("""
                    insert into long_term_memories(id,scope_type,category,title,content,source,source_detail,
                        confirmation_status,enabled,confirmed_at,created_at,updated_at,role_id,topic_key)
                    values(gen_random_uuid(),'GLOBAL','EVENT','木工','正在做书架','USER_ENTERED','用户输入',
                        'CONFIRMED',true,now(),now(),now(),'00000000-0000-0000-0000-000000000001','木工')
                    """);
            assertThat(configuration.target("57").load().migrate().migrationsExecuted).isEqualTo(1);
            assertThat(jdbc.queryForObject("select continuation_state from long_term_memories", String.class)).isEqualTo("NONE");
            assertThat(jdbc.queryForObject("select content from long_term_memories where enabled and confirmation_status='CONFIRMED'", String.class)).isEqualTo("正在做书架");
            assertThat(jdbc.queryForObject("select count(*) from continuation_daily_budget", Integer.class)).isZero();
            assertThat(configuration.target("57").load().migrate().migrationsExecuted).isZero();
        }
    }
}
