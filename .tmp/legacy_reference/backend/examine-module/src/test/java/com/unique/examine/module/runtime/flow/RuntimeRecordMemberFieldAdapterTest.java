package com.unique.examine.module.runtime.flow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.examine.core.api.RuntimeActiveMemberFacade;
import com.unique.examine.core.runtime.RuntimeRecordMemberFieldFacade;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeRecordMemberFieldAdapterTest {
    private static final long SYSTEM_ID = 11L;
    private static final long TENANT_ID = 22L;
    private static final long SCHEMA_VERSION_ID = 101L;
    private static final long MODULE_ID = 201L;
    private static final long RECORD_ID = 301L;
    private static final long FIELD_ID = 501L;
    private static final long ACTIVE_MEMBER_ID = 701L;
    private static final String MODULE_CODE = "work_order";

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void catalogExposesOnlyEnabledPublishedSingleRecordMemberFields() {
        var store = store(catalogSnapshot());
        store.fields = List.of(
                field(501L, "reviewer", "Reviewer", "MEMBER", "RECORD", false),
                field(502L, "summary", "Summary", "TEXT", "RECORD", false),
                field(503L, "observers", "Observers", "MEMBER", "RECORD", true),
                field(504L, "disabled_member", "Disabled member", "MEMBER", "RECORD", false),
                field(505L, "other_member", "Other member", "MEMBER", "RECORD", false));
        var adapter = adapter(store);

        var catalog = adapter.publishedEligibleFields(SYSTEM_ID, MODULE_CODE).orElseThrow();

        assertThat(catalog.systemId()).isEqualTo(SYSTEM_ID);
        assertThat(catalog.schemaVersionId()).isEqualTo(SCHEMA_VERSION_ID);
        assertThat(catalog.moduleSnapshotId()).isEqualTo(MODULE_ID);
        assertThat(catalog.logicalModuleId()).isEqualTo(MODULE_ID);
        assertThat(catalog.moduleCode()).isEqualTo(MODULE_CODE);
        assertThat(catalog.fields()).containsExactly(
                new RuntimeRecordMemberFieldFacade.EligibleField(
                        FIELD_ID, "reviewer", "Reviewer"));
    }

    @Test
    void currentResolutionUsesExactRecordTupleAndLocksActiveTenantMember() {
        var store = store(singleFieldSnapshot("MEMBER", false));
        store.fields = List.of(memberField());
        store.values = List.of(value(0, ACTIVE_MEMBER_ID));
        var adapter = adapter(store);

        var resolved = adapter.resolveCurrent(current(TENANT_ID, MODULE_CODE, FIELD_ID));
        var crossTenant = adapter.resolveCurrent(current(23L, MODULE_CODE, FIELD_ID));
        var wrongModule = adapter.resolveCurrent(current(TENANT_ID, "other_module", FIELD_ID));

        assertThat(resolved).isEqualTo(
                RuntimeRecordMemberFieldFacade.Resolution.resolved(ACTIVE_MEMBER_ID));
        assertThat(crossTenant.status())
                .isEqualTo(RuntimeRecordMemberFieldFacade.Status.SOURCE_MISSING);
        assertThat(wrongModule.status())
                .isEqualTo(RuntimeRecordMemberFieldFacade.Status.SOURCE_MISSING);
        assertThat(store.valueReads).isEqualTo(1);
    }

    @Test
    void currentResolutionDistinguishesEmptyInvalidAndInactiveValues() {
        var store = store(singleFieldSnapshot("MEMBER", false));
        store.fields = List.of(memberField());
        var adapter = adapter(store);

        assertThat(adapter.resolveCurrent(current(TENANT_ID, MODULE_CODE, FIELD_ID)).status())
                .isEqualTo(RuntimeRecordMemberFieldFacade.Status.SOURCE_EMPTY);

        store.values = List.of(value(0, ACTIVE_MEMBER_ID), value(1, 702L));
        assertThat(adapter.resolveCurrent(current(TENANT_ID, MODULE_CODE, FIELD_ID)).status())
                .isEqualTo(RuntimeRecordMemberFieldFacade.Status.SOURCE_INVALID);

        store.values = List.of(value(1, ACTIVE_MEMBER_ID));
        assertThat(adapter.resolveCurrent(current(TENANT_ID, MODULE_CODE, FIELD_ID)).status())
                .isEqualTo(RuntimeRecordMemberFieldFacade.Status.SOURCE_INVALID);

        store.values = List.of(value(0, 702L));
        assertThat(adapter.resolveCurrent(current(TENANT_ID, MODULE_CODE, FIELD_ID)).status())
                .isEqualTo(RuntimeRecordMemberFieldFacade.Status.MEMBER_INACTIVE);
    }

    @Test
    void currentResolutionRejectsMissingWrongTypeAndMultiValueSources() {
        var missing = store(singleFieldSnapshot("MEMBER", false));
        missing.fields = List.of();
        assertThat(adapter(missing).resolveCurrent(current(
                TENANT_ID, MODULE_CODE, FIELD_ID)).status())
                .isEqualTo(RuntimeRecordMemberFieldFacade.Status.SOURCE_MISSING);

        var wrongType = store(singleFieldSnapshot("TEXT", false));
        wrongType.fields = List.of(field(
                FIELD_ID, "reviewer", "Reviewer", "TEXT", "RECORD", false));
        assertThat(adapter(wrongType).resolveCurrent(current(
                TENANT_ID, MODULE_CODE, FIELD_ID)).status())
                .isEqualTo(RuntimeRecordMemberFieldFacade.Status.SOURCE_INVALID);

        var multiple = store(singleFieldSnapshot("MEMBER", true));
        multiple.fields = List.of(field(
                FIELD_ID, "reviewer", "Reviewer", "MEMBER", "RECORD", true));
        assertThat(adapter(multiple).resolveCurrent(current(
                TENANT_ID, MODULE_CODE, FIELD_ID)).status())
                .isEqualTo(RuntimeRecordMemberFieldFacade.Status.SOURCE_INVALID);
    }

    @Test
    void snapshotResolutionUsesCanonicalFieldCodeValueAndNeverCurrentRecordValue() {
        var store = store(singleFieldSnapshot("MEMBER", false));
        store.fields = List.of(memberField());
        store.values = List.of(value(0, 999L));
        var adapter = adapter(store);

        var text = adapter.resolveSnapshot(snapshot(Map.of("reviewer", "\"701\"")));
        var number = adapter.resolveSnapshot(snapshot(Map.of("reviewer", "701")));
        var missing = adapter.resolveSnapshot(snapshot(Map.of()));
        var explicitNull = adapter.resolveSnapshot(snapshot(Map.of("reviewer", "null")));

        assertThat(text).isEqualTo(
                RuntimeRecordMemberFieldFacade.Resolution.resolved(ACTIVE_MEMBER_ID));
        assertThat(number).isEqualTo(text);
        assertThat(missing.status())
                .isEqualTo(RuntimeRecordMemberFieldFacade.Status.SOURCE_EMPTY);
        assertThat(explicitNull.status())
                .isEqualTo(RuntimeRecordMemberFieldFacade.Status.SOURCE_EMPTY);
        assertThat(store.valueReads).isZero();
    }

    @Test
    void snapshotResolutionRejectsMalformedNonPositiveAndInactiveMembers() {
        var store = store(singleFieldSnapshot("MEMBER", false));
        store.fields = List.of(memberField());
        var adapter = adapter(store);

        for (var invalid : List.of(
                "\"\"",
                "\"0\"",
                "0",
                "-1",
                "\"not-an-id\"",
                "[]",
                "{}",
                "true",
                "701 ",
                "\"701\" trailing")) {
            assertThat(adapter.resolveSnapshot(snapshot(Map.of("reviewer", invalid))).status())
                    .as(invalid)
                    .isEqualTo(RuntimeRecordMemberFieldFacade.Status.SOURCE_INVALID);
        }
        assertThat(adapter.resolveSnapshot(snapshot(Map.of("reviewer", "\"702\""))).status())
                .isEqualTo(RuntimeRecordMemberFieldFacade.Status.MEMBER_INACTIVE);
    }

    private RuntimeRecordMemberFieldAdapter adapter(FakeStore store) {
        RuntimeActiveMemberFacade members = (systemId, tenantId, memberId) ->
                systemId == SYSTEM_ID
                        && tenantId == TENANT_ID
                        && memberId == ACTIVE_MEMBER_ID
                        ? Optional.of(new RuntimeActiveMemberFacade.ActiveMember(memberId, null))
                        : Optional.empty();
        return new RuntimeRecordMemberFieldAdapter(store, members, mapper);
    }

    private FakeStore store(String snapshot) {
        return new FakeStore(
                new RecordMemberFieldStore.PublishedSchema(
                        SYSTEM_ID,
                        SCHEMA_VERSION_ID,
                        MODULE_ID,
                        MODULE_ID,
                        MODULE_CODE,
                        snapshot),
                new RecordMemberFieldStore.LockedRecord(
                        SYSTEM_ID,
                        TENANT_ID,
                        RECORD_ID,
                        SCHEMA_VERSION_ID,
                        MODULE_ID,
                        MODULE_ID,
                        MODULE_CODE,
                        snapshot));
    }

    private static RuntimeRecordMemberFieldFacade.CurrentRecordRequest current(
            long tenantId,
            String moduleCode,
            long fieldId
    ) {
        return new RuntimeRecordMemberFieldFacade.CurrentRecordRequest(
                SYSTEM_ID, tenantId, moduleCode, RECORD_ID, fieldId);
    }

    private static RuntimeRecordMemberFieldFacade.SnapshotRequest snapshot(
            Map<String, String> values
    ) {
        return new RuntimeRecordMemberFieldFacade.SnapshotRequest(
                SYSTEM_ID, TENANT_ID, MODULE_CODE, FIELD_ID, values);
    }

    private static RecordMemberFieldStore.ProjectedField memberField() {
        return field(FIELD_ID, "reviewer", "Reviewer", "MEMBER", "RECORD", false);
    }

    private static RecordMemberFieldStore.ProjectedField field(
            long id,
            String code,
            String name,
            String type,
            String scope,
            boolean multiple
    ) {
        return new RecordMemberFieldStore.ProjectedField(
                id,
                id,
                id,
                code,
                name,
                type,
                scope,
                "{\"multiple\":" + multiple + "}");
    }

    private static RecordMemberFieldStore.ValueRow value(int ordinal, Long memberId) {
        return new RecordMemberFieldStore.ValueRow(
                ordinal, "MEMBER", "RECORD", memberId);
    }

    private String singleFieldSnapshot(String type, boolean multiple) {
        var root = root();
        root.withArray("fields").add(configField(
                FIELD_ID, MODULE_ID, "reviewer", "Reviewer", type, "ENABLED", multiple, false));
        return root.toString();
    }

    private String catalogSnapshot() {
        var root = root();
        var fields = root.withArray("fields");
        fields.add(configField(501L, MODULE_ID, "reviewer", "Reviewer",
                "MEMBER", "ENABLED", false, true));
        fields.add(configField(502L, MODULE_ID, "summary", "Summary",
                "TEXT", "ENABLED", false, false));
        fields.add(configField(503L, MODULE_ID, "observers", "Observers",
                "MEMBER", "ENABLED", true, false));
        fields.add(configField(504L, MODULE_ID, "disabled_member", "Disabled member",
                "MEMBER", "DISABLED", false, false));
        fields.add(configField(505L, 202L, "other_member", "Other member",
                "MEMBER", "ENABLED", false, false));
        return root.toString();
    }

    private ObjectNode root() {
        var root = mapper.createObjectNode();
        var modules = root.putArray("modules");
        modules.add(module(MODULE_ID, MODULE_CODE, "ENABLED"));
        modules.add(module(202L, "other_module", "ENABLED"));
        root.putArray("fields");
        return root;
    }

    private ObjectNode module(long id, String code, String status) {
        return mapper.createObjectNode()
                .put("id", Long.toString(id))
                .put("module_code", code)
                .put("desired_status", status);
    }

    private ObjectNode configField(
            long id,
            long moduleId,
            String code,
            String name,
            String type,
            String status,
            boolean multiple,
            boolean hidden
    ) {
        var field = mapper.createObjectNode()
                .put("id", Long.toString(id))
                .put("module_id", Long.toString(moduleId))
                .put("field_code", code)
                .put("field_name", name)
                .put("field_type", type)
                .put("desired_status", status)
                .put("is_hidden", hidden);
        field.putObject("property_json").put("multiple", multiple);
        return field;
    }

    private static final class FakeStore implements RecordMemberFieldStore {
        private final PublishedSchema schema;
        private final LockedRecord record;
        private List<ProjectedField> fields = new ArrayList<>();
        private List<ValueRow> values = new ArrayList<>();
        private int valueReads;

        private FakeStore(PublishedSchema schema, LockedRecord record) {
            this.schema = schema;
            this.record = record;
        }

        @Override
        public Optional<PublishedSchema> activeSchema(long systemId, String moduleCode) {
            return schema.systemId() == systemId && schema.moduleCode().equals(moduleCode)
                    ? Optional.of(schema)
                    : Optional.empty();
        }

        @Override
        public List<ProjectedField> projectedFields(
                long systemId,
                long schemaVersionId,
                long moduleSnapshotId,
                long logicalModuleId
        ) {
            return exactSchema(systemId, schemaVersionId, moduleSnapshotId, logicalModuleId)
                    ? List.copyOf(fields)
                    : List.of();
        }

        @Override
        public Optional<LockedRecord> lockRecord(
                long systemId,
                long tenantId,
                String moduleCode,
                long recordId
        ) {
            return record.systemId() == systemId
                    && record.tenantId() == tenantId
                    && record.moduleCode().equals(moduleCode)
                    && record.recordId() == recordId
                    ? Optional.of(record)
                    : Optional.empty();
        }

        @Override
        public Optional<ProjectedField> projectedField(
                long systemId,
                long schemaVersionId,
                long moduleSnapshotId,
                long logicalModuleId,
                long fieldId
        ) {
            if (!exactSchema(systemId, schemaVersionId, moduleSnapshotId, logicalModuleId)) {
                return Optional.empty();
            }
            return fields.stream()
                    .filter(field -> field.logicalFieldId() == fieldId)
                    .findFirst();
        }

        @Override
        public List<ValueRow> lockValues(
                long systemId,
                long tenantId,
                long recordId,
                long schemaVersionId,
                long moduleSnapshotId,
                long logicalModuleId,
                long fieldId
        ) {
            valueReads++;
            if (record.systemId() != systemId
                    || record.tenantId() != tenantId
                    || record.recordId() != recordId
                    || !exactSchema(systemId, schemaVersionId, moduleSnapshotId, logicalModuleId)
                    || fields.stream().noneMatch(field -> field.fieldSnapshotId() == fieldId)) {
                return List.of();
            }
            return List.copyOf(values);
        }

        private boolean exactSchema(
                long systemId,
                long schemaVersionId,
                long moduleSnapshotId,
                long logicalModuleId
        ) {
            return schema.systemId() == systemId
                    && schema.schemaVersionId() == schemaVersionId
                    && schema.moduleSnapshotId() == moduleSnapshotId
                    && schema.logicalModuleId() == logicalModuleId;
        }
    }
}
