package com.kj.stackchan.memory;

import java.time.Instant;
import java.util.UUID;

import com.kj.stackchan.device.DeviceEntity;
import com.kj.stackchan.device.DeviceRepository;
import com.kj.stackchan.role.CompanionRoleEntity;
import com.kj.stackchan.role.CompanionRoleRepository;
import com.kj.stackchan.persona.PersonaTone;
import com.kj.stackchan.persona.PersonaReplyLength;
import com.kj.stackchan.persona.PersonaProactivity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class MemoryRecallPersistenceTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName
            .parse("postgres@sha256:c2d42a104eb6b37b286a2d9c5cf83f349de4d6516d513d00a2bd9610e2c2e5e4")
            .asCompatibleSubstituteFor("postgres"));
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired LongTermMemoryRepository memories;
    @Autowired DeviceRepository devices;
    @Autowired CompanionRoleRepository roles;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    private final UUID role = CompanionRoleEntity.DEFAULT_ROLE_ID;

    @BeforeEach
    void clearIsolatedFixtures() { memories.deleteAll(); }

    @Test
    void excludesUnrelatedHighImportanceMemoriesBeforeLimitingAndSupportsExplicitRecall() {
        for (int i = 0; i < 20; i++) save(role, null, "工程项目" + i, "用户完成设备联调", 5);
        var coffee = save(role, null, "饮品偏好", "用户喜欢手冲咖啡", 1);
        assertThat(memories.searchContext(role, null, "我喜欢听音乐", 8)).isEmpty();
        assertThat(memories.searchContext(role, null, "你好", 8)).isEmpty();
        assertThat(memories.searchContext(role, null, "你记得我的咖啡偏好吗", 1))
                .extracting(LongTermMemoryEntity::getId).containsExactly(coffee.getId());
        assertThat(memories.searchContext(role, null, "你记得我什么", 8)).hasSize(8);
    }

    @Test
    void recallsBoundedSynonymsWithoutMatchingPartialEnglishWordsOrRegexSyntax() {
        var lunch = save(role, null, "午餐", "午餐通常吃面条", 3);
        var coffee = save(role, null, "饮品", "喜欢手冲咖啡", 3);
        save(role, null, "宠物", "养了一只猫", 3);
        assertThat(memories.searchContext(role, null, "午饭吃什么", 8))
                .extracting(LongTermMemoryEntity::getId).containsExactly(lunch.getId());
        assertThat(memories.searchContext(role, null, "ＣＯＦＦＥＥ", 8))
                .extracting(LongTermMemoryEntity::getId).containsExactly(coffee.getId());
        assertThat(memories.searchContext(role, null, "carpet", 8)).isEmpty();
        assertThat(memories.searchContext(role, null, ".*|[%_]", 8)).isEmpty();
    }

    @Test
    void filtersRolesDevicesAndInactiveVersionsBeforeRecall() {
        UUID device = devices.saveAndFlush(new DeviceEntity("recall-main", "1")).getId();
        UUID otherDevice = devices.saveAndFlush(new DeviceEntity("recall-other", "1")).getId();
        UUID otherRole = roles.saveAndFlush(new CompanionRoleEntity("独立伙伴", PersonaTone.CALM,
                PersonaReplyLength.SHORT, PersonaProactivity.RESERVED, "", "", "", Instant.EPOCH)).getId();
        var global = save(role, null, "咖啡", "现在喝低因咖啡", 2);
        var scoped = save(role, device, "咖啡", "这台设备只聊手冲咖啡", 2);
        save(otherRole, null, "咖啡", "另一伙伴的咖啡记忆", 5);
        save(role, otherDevice, "咖啡", "另一设备的咖啡记忆", 5);
        var disabled = save(role, null, "咖啡", "已禁用的咖啡记忆", 5);
        disabled.setEnabled(false, Instant.now()); memories.saveAndFlush(disabled);
        var superseded = save(role, null, "咖啡", "以前喝浓咖啡", 5);
        superseded.markSuperseded(global.getId(), Instant.now()); memories.saveAndFlush(superseded);
        var rejected = save(role, null, "咖啡", "拒绝的咖啡记忆", 5);
        rejected.reject(Instant.now()); memories.saveAndFlush(rejected);
        assertThat(memories.searchContext(role, device, "咖啡", 8)).extracting(LongTermMemoryEntity::getId)
                .containsExactlyInAnyOrder(global.getId(), scoped.getId());
        assertThat(memories.searchContext(role, null, "咖啡", 8)).extracting(LongTermMemoryEntity::getId)
                .containsExactly(global.getId());
    }

    @Test
    void nativeQueryFailureDoesNotPoisonTheCallingTransaction() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertThatThrownBy(() -> memories.searchRelevantContext(role, null, "咖啡", "[", false, 8))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(jdbc.queryForObject("select 1", Integer.class)).isEqualTo(1);
        });
    }

    private LongTermMemoryEntity save(UUID roleId, UUID device, String title, String content, int importance) {
        var memory = new LongTermMemoryEntity(device == null ? MemoryScopeType.GLOBAL : MemoryScopeType.DEVICE,
                device, MemoryCategory.USER_PROFILE, title, content, MemorySource.USER_ENTERED,
                LongTermMemoryService.USER_ENTERED_DETAIL, MemoryConfirmationStatus.CONFIRMED,
                title, importance, null, null, false, Instant.now());
        memory.assignRole(roleId);
        return memories.saveAndFlush(memory);
    }
}
