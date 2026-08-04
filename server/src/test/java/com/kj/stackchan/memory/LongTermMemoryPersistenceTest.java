package com.kj.stackchan.memory;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class LongTermMemoryPersistenceTest {

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName
            .parse("postgres@sha256:c2d42a104eb6b37b286a2d9c5cf83f349de4d6516d513d00a2bd9610e2c2e5e4")
            .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE);

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private LongTermMemoryService memoryService;

    @Test
    void searchesWithTrigramsAndPersistsOnlyUsedMemoryIds() {
        LongTermMemoryService.MemorySnapshot coffee = memoryService.create(
                new LongTermMemoryService.MemoryCommand(
                        MemoryScopeType.GLOBAL, null, MemoryCategory.USER_PROFILE,
                        "咖啡偏好", "用户喜欢手冲咖啡", "饮品偏好", 5, false
                )
        );
        memoryService.create(new LongTermMemoryService.MemoryCommand(
                MemoryScopeType.GLOBAL, null, MemoryCategory.EVENT,
                "项目进度", "用户完成了设备联调", "项目进度", 2, false
        ));

        List<LongTermMemoryService.MemorySnapshot> selected = memoryService.loadContext(null, "手冲咖啡", 8);
        UUID turnId = UUID.randomUUID();
        memoryService.recordUsage(turnId, selected.stream().map(LongTermMemoryService.MemorySnapshot::id).toList());

        assertThat(selected).isNotEmpty();
        assertThat(selected.getFirst().id()).isEqualTo(coffee.id());
        assertThat(memoryService.usageForTurn(turnId)).containsExactlyInAnyOrderElementsOf(
                selected.stream().map(LongTermMemoryService.MemorySnapshot::id).toList()
        );
    }
}
