package com.kj.stackchan.interaction;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class CompanionUpgradePersistenceTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName
            .parse("postgres@sha256:c2d42a104eb6b37b286a2d9c5cf83f349de4d6516d513d00a2bd9610e2c2e5e4")
            .asCompatibleSubstituteFor("postgres"));

    @Test
    void upgradesExistingV49DataWithoutResettingConversationOrMemory() throws Exception {
        var configuration = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword());
        configuration.target("49").load().migrate();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            String role = "00000000-0000-0000-0000-000000000001";
            String device = "10000000-0000-0000-0000-000000000001";
            String conversation = "20000000-0000-0000-0000-000000000001";
            jdbc.update("insert into devices(id,hardware_id,firmware_version) values (?::uuid,'upgrade-fixture','test')", device);
            jdbc.update("insert into conversations(id,title,created_at,updated_at,role_id) values (?::uuid,'旧对话',now(),now(),?::uuid)", conversation, role);
            jdbc.update("insert into conversation_messages(id,conversation_id,role,content,created_at) values (gen_random_uuid(),?::uuid,'USER','我喜欢咖啡',now())", conversation);
            jdbc.update("""
                    insert into long_term_memories(id,scope_type,device_id,category,title,content,
                        source,source_detail,confirmation_status,enabled,confirmed_at,created_at,updated_at,role_id,topic_key)
                    values(gen_random_uuid(),'DEVICE',?::uuid,'USER_PROFILE','偏好','喜欢咖啡',
                        'USER_ENTERED','upgrade-fixture','CONFIRMED',true,now(),now(),now(),?::uuid,'coffee')
                    """, device, role);
            var result = configuration.target("51").load().migrate();
            assertThat(result.migrationsExecuted).isEqualTo(2);
            assertThat(jdbc.queryForObject("select content from conversation_messages", String.class)).isEqualTo("我喜欢咖啡");
            assertThat(jdbc.queryForObject("select content from long_term_memories where enabled and confirmation_status='CONFIRMED'", String.class)).isEqualTo("喜欢咖啡");
            assertThat(jdbc.queryForObject("select voice_topic_reset_at from conversations", Object.class)).isNull();
            assertThat(jdbc.queryForObject("select count(*) from role_proactive_pauses", Integer.class)).isZero();
            jdbc.update("update conversations set voice_topic_reset_at=now() where id=?::uuid", conversation);
            jdbc.update("insert into role_proactive_pauses values (?::uuid,?::uuid,now()+interval '1 hour',now())", device, role);
            assertThat(jdbc.queryForObject("select voice_topic_reset_at is not null from conversations", Boolean.class)).isTrue();
            assertThat(configuration.target("51").load().migrate().migrationsExecuted).isZero();
            assertThat(jdbc.queryForObject("select count(*) from role_proactive_pauses", Integer.class)).isEqualTo(1);
        }
    }
}
