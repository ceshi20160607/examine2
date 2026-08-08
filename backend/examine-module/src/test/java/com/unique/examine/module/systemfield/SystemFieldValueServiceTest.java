package com.unique.examine.module.systemfield;

import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SystemFieldValueServiceTest {
    private final InMemoryAutoNumberSequence sequence = new InMemoryAutoNumberSequence();
    private final SystemFieldValueService service = new SystemFieldValueService(sequence);
    private final SystemFieldValueService.Scope scope = new SystemFieldValueService.Scope(10, 20, 30);
    private final LocalDateTime now = LocalDateTime.of(2026, 7, 25, 16, 30);
    private final SystemFieldValueService.ActorContext actor =
            new SystemFieldValueService.ActorContext(40, now);

    @Test
    void createsTenantNumberAndAuditValuesAndUpdatesOnlyMutableAuditValues() {
        var definitions = definitions();

        var created = service.onCreate(scope, definitions, Map.of("title", "A"), actor);
        var updatedAt = now.plusMinutes(2);
        var updated = service.onUpdate(scope, definitions, Map.of("title", "B"),
                new SystemFieldValueService.ActorContext(41, updatedAt));

        assertThat(created).containsEntry("tenant", 20L)
                .containsEntry("number", "WO-0001")
                .containsEntry("createdBy", 40L)
                .containsEntry("createdAt", now)
                .containsEntry("updatedBy", 40L)
                .containsEntry("updatedAt", now);
        assertThat(updated).containsOnlyKeys("updatedBy", "updatedAt")
                .containsEntry("updatedBy", 41L)
                .containsEntry("updatedAt", updatedAt);
    }

    @Test
    void rejectsClientSpoofingOfEverySystemManagedField() {
        assertThatThrownBy(() -> service.onCreate(scope, definitions(), Map.of("tenant", 999), actor))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("tenant");
        assertThatThrownBy(() -> service.onUpdate(scope, definitions(), Map.of("updatedBy", 999), actor))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("updatedBy");
    }

    @Test
    void allocatesUniqueMonotonicNumbersUnderConcurrency() throws Exception {
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<String>> calls = new ArrayList<>();
            for (int index = 0; index < 100; index++) {
                calls.add(() -> (String) service.onCreate(scope, definitions(), Map.of(), actor).get("number"));
            }
            var numbers = executor.invokeAll(calls).stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }).toList();

            assertThat(numbers).doesNotHaveDuplicates().hasSize(100)
                    .contains("WO-0001", "WO-0100");
        } finally {
            executor.shutdownNow();
        }
    }

    private static List<SystemFieldDefinition> definitions() {
        return List.of(
                new SystemFieldDefinition(1, "tenant", SystemFieldDefinition.Type.TENANT, null, 0),
                new SystemFieldDefinition(2, "number", SystemFieldDefinition.Type.AUTO_NUMBER, "WO-", 4),
                new SystemFieldDefinition(3, "createdBy", SystemFieldDefinition.Type.CREATED_BY, null, 0),
                new SystemFieldDefinition(4, "createdAt", SystemFieldDefinition.Type.CREATED_AT, null, 0),
                new SystemFieldDefinition(5, "updatedBy", SystemFieldDefinition.Type.UPDATED_BY, null, 0),
                new SystemFieldDefinition(6, "updatedAt", SystemFieldDefinition.Type.UPDATED_AT, null, 0)
        );
    }
}
