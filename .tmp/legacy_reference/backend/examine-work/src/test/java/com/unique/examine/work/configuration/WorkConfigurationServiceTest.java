package com.unique.examine.work.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.work.domain.WorkActor;
import com.unique.examine.work.domain.WorkDomainException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkConfigurationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-06T14:00:00Z");
    private final ObjectMapper json = new ObjectMapper();
    private final MemoryRepository repository = new MemoryRepository();
    private final WorkConfigurationService service = new WorkConfigurationService(
            repository, json, Clock.fixed(NOW, ZoneOffset.UTC));
    private final WorkActor admin = new WorkActor(1, 2, 3,
            Set.of(WorkConfigurationService.MANAGE, "work.task.access"));

    @Test
    void publishesImmutableRevisionAndRollbackCreatesANewPublishedRevision() {
        repository.dictionaries.add("task_status");
        var first = service.createDraft(admin, snapshot("effort"));
        var published = service.publish(admin, first.id(), first.version());

        var second = service.createDraft(admin, snapshot("estimate"));
        service.publish(admin, second.id(), second.version());
        var rollback = service.rollback(admin, first.revision());

        assertThat(published.status()).isEqualTo(WorkConfiguration.Status.PUBLISHED);
        assertThat(rollback.revision()).isEqualTo(3);
        assertThat(rollback.rollbackFromRevision()).isEqualTo(1);
        assertThat(rollback.snapshot()).isEqualTo(first.snapshot());
        assertThat(repository.active(1, 2)).contains(rollback);
        assertThat(service.history(admin)).extracting(WorkConfiguration::status)
                .containsExactly(
                        WorkConfiguration.Status.PUBLISHED,
                        WorkConfiguration.Status.RETIRED,
                        WorkConfiguration.Status.RETIRED);
    }

    @Test
    void publishedFieldsValidateDictionaryPermissionsAndDriveCardAndKanbanRuntime() {
        repository.dictionaries.add("task_status");
        repository.dictionaryItems.put("task_status", Set.of("doing", "done"));
        var fields = List.of(
                new WorkConfiguration.Field(
                        "status", "Status", WorkConfiguration.FieldType.SELECT,
                        true, "task_status", null, null, true,
                        WorkConfiguration.KanbanRole.COLUMN),
                new WorkConfiguration.Field(
                        "effort", "Effort", WorkConfiguration.FieldType.NUMBER,
                        true, null, null, "work.effort.edit", true,
                        WorkConfiguration.KanbanRole.NUMERIC));
        var draft = service.createDraft(admin, new WorkConfiguration.Snapshot(Map.of(
                WorkConfiguration.ObjectType.ORDINARY_TASK, fields)));
        service.publish(admin, draft.id(), draft.version());
        var editor = new WorkActor(1, 2, 8,
                Set.of("work.task.access", "work.effort.edit"));

        var prepared = service.prepareCreate(editor,
                WorkConfiguration.ObjectType.ORDINARY_TASK,
                tree("{\"status\":\"doing\",\"effort\":5.50}"));
        service.saveValues(editor, WorkConfiguration.ObjectType.ORDINARY_TASK,
                99, prepared);
        var runtime = service.runtimeView(editor,
                WorkConfiguration.ObjectType.ORDINARY_TASK, 99);

        assertThat(runtime.activeRevision()).isEqualTo(1);
        assertThat(runtime.values().path("status").asText()).isEqualTo("doing");
        assertThat(runtime.cardValues().path("effort").decimalValue())
                .isEqualByComparingTo("5.5");
        assertThat(runtime.kanban().columnFieldCode()).isEqualTo("status");
        assertThat(runtime.kanban().numericFieldCode()).isEqualTo("effort");
        assertThat(runtime.kanban().numericValue().decimalValue())
                .isEqualByComparingTo("5.5");

        assertThatThrownBy(() -> service.prepareUpdate(editor,
                WorkConfiguration.ObjectType.ORDINARY_TASK, 99,
                tree("{\"status\":\"disabled\"}")))
                .isInstanceOfSatisfying(WorkDomainException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("WORK_FIELD_VALUE_INVALID"));
    }

    @Test
    void fieldPermissionsHideValuesAndRejectUnauthorizedWrites() {
        var secret = new WorkConfiguration.Field(
                "private_note", "Private note", WorkConfiguration.FieldType.TEXT,
                false, null, "work.private.read", "work.private.edit", true,
                WorkConfiguration.KanbanRole.NONE);
        var draft = service.createDraft(admin, new WorkConfiguration.Snapshot(Map.of(
                WorkConfiguration.ObjectType.DAILY_REPORT, List.of(secret))));
        service.publish(admin, draft.id(), draft.version());
        var editor = new WorkActor(1, 2, 9,
                Set.of("work.report.access", "work.private.read", "work.private.edit"));
        var reader = new WorkActor(1, 2, 10, Set.of("work.report.access"));
        var prepared = service.prepareCreate(editor,
                WorkConfiguration.ObjectType.DAILY_REPORT,
                tree("{\"private_note\":\"manager only\"}"));
        service.saveValues(editor, WorkConfiguration.ObjectType.DAILY_REPORT,
                88, prepared);

        assertThat(service.runtimeView(reader,
                WorkConfiguration.ObjectType.DAILY_REPORT, 88).values().isEmpty())
                .isTrue();
        assertThatThrownBy(() -> service.prepareUpdate(reader,
                WorkConfiguration.ObjectType.DAILY_REPORT, 88,
                tree("{\"private_note\":\"overwrite\"}")))
                .isInstanceOfSatisfying(WorkDomainException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("WORK_FIELD_FORBIDDEN"));
    }

    private WorkConfiguration.Snapshot snapshot(String numericCode) {
        return new WorkConfiguration.Snapshot(Map.of(
                WorkConfiguration.ObjectType.ORDINARY_TASK, List.of(
                        new WorkConfiguration.Field(
                                "status", "Status",
                                WorkConfiguration.FieldType.SELECT,
                                false, "task_status", null, null, true,
                                WorkConfiguration.KanbanRole.COLUMN),
                        new WorkConfiguration.Field(
                                numericCode, "Numeric",
                                WorkConfiguration.FieldType.NUMBER,
                                false, null, null, null, true,
                                WorkConfiguration.KanbanRole.NUMERIC))));
    }

    private JsonNode tree(String value) {
        try {
            return json.readTree(value);
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private static final class MemoryRepository
            implements WorkConfigurationRepository {
        private final Map<Long, WorkConfiguration> configurations = new HashMap<>();
        private final Map<String, RuntimeValues> values = new HashMap<>();
        private final Set<String> dictionaries = new java.util.HashSet<>();
        private final Map<String, Set<String>> dictionaryItems = new HashMap<>();
        private long sequence = 10;

        @Override
        public long nextId() { return ++sequence; }

        @Override
        public long nextRevision(long systemId, long tenantId) {
            return configurations.values().stream()
                    .filter(value -> value.systemId() == systemId
                            && value.tenantId() == tenantId)
                    .mapToLong(WorkConfiguration::revision).max().orElse(0) + 1;
        }

        @Override
        public WorkConfiguration insert(WorkConfiguration configuration) {
            configurations.put(configuration.id(), configuration);
            return configuration;
        }

        @Override
        public WorkConfiguration update(
                WorkConfiguration configuration, long expectedVersion) {
            var current = configurations.get(configuration.id());
            if (current == null || current.version() != expectedVersion) {
                throw new AssertionError("stale fake update");
            }
            configurations.put(configuration.id(), configuration);
            return configuration;
        }

        @Override
        public Optional<WorkConfiguration> active(long systemId, long tenantId) {
            return configurations.values().stream()
                    .filter(value -> value.systemId() == systemId
                            && value.tenantId() == tenantId
                            && value.status() == WorkConfiguration.Status.PUBLISHED)
                    .findFirst();
        }

        @Override
        public Optional<WorkConfiguration> findByRevision(
                long systemId, long tenantId, long revision) {
            return configurations.values().stream()
                    .filter(value -> value.systemId() == systemId
                            && value.tenantId() == tenantId
                            && value.revision() == revision).findFirst();
        }

        @Override
        public Optional<WorkConfiguration> findById(
                long systemId, long tenantId, long id) {
            return Optional.ofNullable(configurations.get(id))
                    .filter(value -> value.systemId() == systemId
                            && value.tenantId() == tenantId);
        }

        @Override
        public List<WorkConfiguration> history(long systemId, long tenantId) {
            return configurations.values().stream()
                    .filter(value -> value.systemId() == systemId
                            && value.tenantId() == tenantId)
                    .sorted(Comparator.comparingLong(
                            WorkConfiguration::revision).reversed()).toList();
        }

        @Override
        public boolean enabledDictionary(long systemId, String dictionaryCode) {
            return dictionaries.contains(dictionaryCode);
        }

        @Override
        public boolean enabledDictionaryItems(
                long systemId, String dictionaryCode, Set<String> itemCodes) {
            return dictionaryItems.getOrDefault(dictionaryCode, Set.of())
                    .containsAll(itemCodes);
        }

        @Override
        public void saveRuntimeValues(
                long systemId, long tenantId,
                WorkConfiguration.ObjectType objectType, long objectId,
                long configurationRevision, JsonNode value, long actorId) {
            values.put(key(systemId, tenantId, objectType, objectId),
                    new RuntimeValues(configurationRevision, value.deepCopy()));
        }

        @Override
        public Optional<RuntimeValues> runtimeValues(
                long systemId, long tenantId,
                WorkConfiguration.ObjectType objectType, long objectId) {
            return Optional.ofNullable(values.get(
                    key(systemId, tenantId, objectType, objectId)));
        }

        private static String key(
                long systemId, long tenantId,
                WorkConfiguration.ObjectType type, long objectId) {
            return systemId + ":" + tenantId + ":" + type + ":" + objectId;
        }
    }
}
